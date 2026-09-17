# 10-ITTAGE 设计分析与刷新方案
> 本文档是 ITTAGE（`ITTAGE`）的**真实刷新方案**，属于 [01-BPU刷新方案概览](01-BPU刷新方案概览.md) §6.1 占位阶段之后的落地内容：实现时用 §4.4 的真实完成条件替换 `Ittage.scala` 中的 `io.resetDone.get := true.B` 恒真占位。
>
> 1. **接口契约**：`contextFlush`/`bpuFlushing`/`resetDone` 由 `BasePredictorIO` 以 `Option.when(HasBpuFlush)` 声明（01 §5.1.3），BPU 顶层已按本事务 `activeFlushMask` 门控后下发（01 §5.3.3）。ITTAGE 对应 `ittageFlushEnable`，因此收到的均为针对自身的有效刷新信号。
> 2. **编译开关**：本文所有 ITTAGE 刷新端口与连线、`IttageTable` SRAM `extraReset`、WriteBuffer context-flush 清空、`RegionWays` valid 清零、预测/训练流水线有效令牌清理、读写门控及 `resetDone` 生成，均遵循 [00-BPU刷新机制编译开关实现方案](00-BPU刷新机制编译开关实现方案.md) §3.1/§3.4 和 01 §6.2，受 elaboration-time `HasBpuFlush` 统一裁剪。
> 3. **编码规则**：可选端口使用 `Option.when(HasBpuFlush)`；`Ittage`、`IttageTable` 与 `RegionWays` 的只读可选刷新 I/O 均在模块顶部通过 `if (HasBpuFlush) io.<port>.get else false.B` 解包为内部别名，模块内不再散布 `.get`，预测、训练和写路径统一使用该别名参与门控。只有存在默认赋值和条件覆盖的派生门控信号才使用 `Wire`（与 `sx_fire`/`sx_flush` 范式一致）。刷新专用连线、清零 `when`、SRAM/WriteBuffer 刷新配置和完成逻辑放入 Scala `if (HasBpuFlush)`；对既有数据通路追加门控时，关闭分支必须恢复刷新引入前的原表达式。禁止用 `getOrElse(false.B)`/`getOrElse(true.B)` 代替刷新专用结构的 Scala 守卫。
> 4. **关闭语义**：`HasBpuFlush=false` 时，ITTAGE 刷新专用端口和消费逻辑不参与 elaboration，`Ittage`、`IttageTable`、`RegionWays` 及其正常预测、训练、SRAM 和 WriteBuffer 数据通路必须与未引入刷新机制的基线一致；双配置 elaboration 与关闭配置 RTL 检查遵循 00 §5。

## 1. ITTAGE 的预测流水线

ITTAGE 顶层模块为 `Ittage`，位于 `src/main/scala/xiangshan/frontend/bpu/ittage`。

ITTAGE 是间接跳转目标预测器。它不独立发现分支，而是在 BPU S3 阶段，当 mBTB 给出的第一条 taken 分支属性满足 `needIttage` 且 ITTAGE 命中时，用 ITTAGE 目标地址覆盖 mBTB 目标地址：

- mBTB 负责发现间接跳转指令，并给出基础 target
- ITTAGE 根据 start PC 和 PHR 折叠历史读取多张 tagged table
- 多张表命中时，选择历史最长表作为 provider，次长命中表作为 alternate provider
- provider counter 为强/弱正时使用 provider target；provider counter 为饱和负且存在 alternate 时使用 alternate target
- 若 ITTAGE 未命中，下游继续使用 mBTB 目标

---

### 第一拍：S1 读表

ITTAGE 顶层在 `s1_fire && s1_isIndirect` （当前 s1_isIndirect 恒为 true）时向每张 `IttageTable` 发起读请求。请求内容包括：

- `startPc`
- `s1_foldedPhr`

每张 `IttageTable` 内部根据 `startPc >> instOffsetBits` 和折叠历史计算：

- `bankIdx`
- `setIdx`
- `tag`

随后只读取由 `bankIdx` 选中的一个 bank。每个 bank 是 single-port `FoldedSRAMTemplate`，读出的表项为 `IttageEntry`。

默认配置：

源码位置：`src/main/scala/xiangshan/frontend/bpu/ittage/Parameters.scala:19-47`。

```text
5 张 ITTAGE 表
TableInfos = (256,4), (256,8), (512,13), (512,16), (512,32)
每张表 2 bank
TagWidth = 9 bit
ConfidenceCntWidth = 2 bit
UsefulCntWidth = 1 bit
TargetOffsetWidth = 20 bit
RegionNums = 16
```

这里的“默认配置”指 `IttageParameters` 中的默认值。具体 SoC 配置仍可覆盖这些参数；例如当前 `top/Configs.scala` 的 `MinimalConfig` 将 ITTAGE 覆写为 3 张表 `(256,4), (256,8), (512,16)`，`TagWidth = 7`。

---

### 第二拍：S2 选择 provider / alternate

S2 接收各表读回的响应。每张表的命中条件为：

源码位置：`src/main/scala/xiangshan/frontend/bpu/ittage/IttageTable.scala:163-165`、`src/main/scala/xiangshan/frontend/bpu/ittage/Ittage.scala:206-224`。

```text
tableReadData.valid && tableReadData.tag === s1_tag
```

当多张表同时命中时，源码对 `s2_resps.reverse` 执行 `ParallelSelectTwo`，因此优先选择序号更高的表。当前默认 `TableInfos` 的历史长度依次为 `4/8/13/16/32`，所以表序号越高，历史长度确定越长：

- 第一命中项：provider
- 第二命中项：alternate provider

ITTAGE 表项不直接保存完整 target，而是保存 `targetOffset`：

源码位置：`src/main/scala/xiangshan/frontend/bpu/ittage/Bundles.scala:51-59`、`src/main/scala/xiangshan/frontend/bpu/ittage/Ittage.scala:226-256`。

```text
targetOffset
├── offset:      target 低 TargetOffsetWidth bit 的 PrunedAddr 表示
├── pointer:     RegionWays 指针
└── usePcRegion: 是否使用当前 PC region
```

S2 通过 `RegionWays` 把 region 和 offset 拼成完整预测目标：

- 若 `RegionWays` 中 pointer 命中且 `usePcRegion = false`，使用 `RegionWays` 读出的 region
- 否则使用当前 `startPc` 的 region

最终选择逻辑：

源码位置：`src/main/scala/xiangshan/frontend/bpu/ittage/Ittage.scala:250-266`。

```text
provider 存在且 provider counter 不是饱和负数：使用 provider target
provider counter 饱和负数且 alternate 存在：使用 alternate target
provider counter 饱和负数且没有 alternate：target 为 0
```

注意最后一种情况并不会让 `io.prediction.hit` 变成 false。provider 存在、provider counter 饱和负且没有 alternate 时，`s2_ittageTarget` 确定取默认值 0，而 `s3_provided` 仍为 true；因此在有效的 `s3_fire` 拍，ITTAGE 确定输出 `hit = true`、`target = 0`。BPU 顶层在 `s3_taken && needIttage && hit` 成立时确定选择该 ITTAGE target。源码里有一条 `target =/= 0` 的断言，但当前被注释掉。

---

### 第三拍：S3 输出预测和 meta

S3 寄存 S2 的 provider/alternate 信息并输出：

- `io.prediction.hit := s3_fire && s3_provided`
- `io.prediction.target := s3_ittageTarget`
- `io.meta`

`io.meta` 保存训练需要的信息：

- provider / alternate provider 表号
- provider / alternate counter
- provider useful counter
- provider / alternate target
- 是否存在可分配表项

注意：`io.prediction.hit` 只要求 provider 存在，不重新检查最终 target 是否为 0。BPU 顶层是否采用 ITTAGE 目标还会结合 mBTB 分支属性：

源码位置：`src/main/scala/xiangshan/frontend/bpu/ittage/Ittage.scala:103-112, 250-266`。

```scala
private val s3_useIttage = s3_firstTakenBranch.bits.attribute.needIttage && ittage.io.prediction.hit
```

---

## 2. ITTAGE 的训练流水线

ITTAGE 训练主要由 T0/T1 和后一拍 table update 组成。与 TAGE 不同，当前 ITTAGE 训练不重新读表，而是直接使用预测阶段保存的 `IttageMeta`。

### T0：保存训练请求和 meta

T0 在 `io.enable && io.stageCtrl.t0_fire` 时保存：

- `io.train`
- `io.train.meta.ittage`
- `trainFoldedPhr`

源码只选择 taken 的间接跳转分支进行训练：

源码位置：`src/main/scala/xiangshan/frontend/bpu/ittage/Ittage.scala:156-165`。

```scala
b.valid && b.bits.attribute.needIttage && b.bits.taken
```

并断言同一训练包中最多只有一个这样的分支。

---

### T1：生成更新意图

T1 根据训练分支、预测 meta 和真实 target 生成每张表的更新信息。

主要更新规则：

1. 若 provider 存在，则更新 provider：
   - 更新 confidence counter
   - 当 provider target 与 alternate target 不同时，根据 provider target 是否正确更新 useful
   - 当旧 counter 为饱和负或分配新项时，写入真实 target offset
2. 若预测实际使用 alternate 且发生误预测，则惩罚 alternate provider
3. 若发生误预测，且不是“provider target 正确但因低置信使用 alternate”的情况，则尝试分配新表项
4. 当无法分配新项导致 `tickCnt` 饱和时，触发所有表的 useful bit 清零扫描

分配候选来自预测阶段保存的 `ittageMeta.allocate`。其生成规则是：

源码位置：`src/main/scala/xiangshan/frontend/bpu/ittage/Ittage.scala:296-304`。

```text
未命中该表 && usefulCnt 为饱和负数 && 表历史长度长于当前 provider
```

如果没有 provider，则候选可以来自所有表。

---

### T2：向各 IttageTable 发 update

顶层对每张表打一拍寄存后驱动 `tables(i).io.update`：

源码位置：`src/main/scala/xiangshan/frontend/bpu/ittage/Ittage.scala:401-413`。

```scala
tables(i).io.update.valid          := RegNext(updateMask(i), init = false.B)
tables(i).io.update.resetUsefulCnt := RegNext(updateResetUsefulCnt, init = false.B)
```

每个 `IttageTable` 根据 update PC 和 folded history 重新计算 bank/set/tag，并把写请求送入目标 bank 的 `WriteBuffer`。写缓冲后续在 SRAM 读端口空闲时写回 SRAM。

---

### RegionWays 更新

ITTAGE target 的高位 region 不存在 table entry 中，而是保存在顶层 `RegionWays` 寄存器数组里。

训练时若真实 target region 与当前 PC region 不同，并且本次确实分配了新 entry：

源码位置：`src/main/scala/xiangshan/frontend/bpu/ittage/Ittage.scala:306-320`。

```scala
rTable.io.writeValid := !updateRealUsePCRegion && updateAlloc.reduce(_ || _)
```

则向 `RegionWays` 写入真实 target region，并把返回的 pointer 写入新分配的 ITTAGE entry。

---

## 3. ITTAGE 与上下文刷新相关的整体架构

本方案采用以下限定威胁模型：**上下文切换后，旧上下文训练或遗留的 target 不得被新上下文命中、输出，或者在存储清零后重新写回。** 本方案不把预测准确率、替换顺序、性能干扰和严格微架构非干扰作为安全目标。

本节只分析上下文刷新需要识别的存储状态和状态传播路径，不完整展开 ITTAGE 的正常预测、provider/alternate 选择和训练算法。判断一项状态是否需要安全刷新，只看它是否用于回答以下问题：

1. 是否保存了旧上下文状态；
2. 状态在什么条件下写入；
3. 旧状态能够通过哪条路径继续影响预测、训练或存储器；
4. 它能否授权旧 target 输出或写回；若不能，则只记录而不纳入必要刷新集合。

具体清零方式、门控信号和刷新时序统一放在第 4 节。

源码位置：`src/main/scala/xiangshan/frontend/bpu/ittage/Ittage.scala:44-80, 191-195, 306-414`、`src/main/scala/xiangshan/frontend/bpu/ittage/IttageTable.scala:34-228`、`src/main/scala/xiangshan/frontend/bpu/ittage/RegionWays.scala:25-90`。

| 状态类别 | 实际存储 | 旧状态可能产生的影响 | 对应小节 |
| --- | --- | --- | --- |
| table entry | `FoldedSRAMTemplate` | 旧 valid/tag 重新命中，旧 target/counter 参与预测和训练 | 3.1 |
| 延迟写请求 | WriteBuffer 寄存器和替换状态 | 在 SRAM 清理后重新写回旧 entry | 3.2 |
| useful 老化任务 | `needReset/resetSet` | 刷新后继续产生 useful-only 写入，但不能写 valid/tag/target | 3.3 |
| target 高位 | `regions` 和 PLRU 状态 | `regions.valid/region` 可参与 target 重建；PLRU 只影响 victim | 3.4 |
| 老化策略 | `tickCnt` | 旧计数只会提前触发 useful 扫描 | 3.5 |
| 预测流水线 | S1/S2/S3 寄存器 | SRAM 清理后仍输出旧 target/meta | 3.6 |
| 训练流水线 | T1/T2 寄存器 | 刷新后继续生成 table/RegionWays 写入 | 3.7 |

与刷新规模直接相关的默认参数为：

源码位置：`src/main/scala/xiangshan/frontend/bpu/ittage/Parameters.scala:19-47`。

这些是 `IttageParameters` 默认值，最终数量以实际 `XSCoreParamsKey` 配置为准。

### 3.1 IttageTable entry SRAM

#### 3.1.1 SRAM 存储内容

`IttageEntry` 是单个 SRAM entry 的 Bundle，而非独立寄存器；它共同保存 `valid/tag`、`confidenceCnt`、`targetOffset(offset/pointer/usePcRegion)` 和 `usefulCnt`。完整 entry 清零即可消除旧命中、旧 target、旧置信与 old useful 状态。源码位置：`src/main/scala/xiangshan/frontend/bpu/ittage/Bundles.scala:42-59`。

`valid/tag` 在 `IttageTable` 内部被消耗，顶层 `io.resp.bits` 只得到 confidence、useful 和 targetOffset。即使 `io.resp.valid = false`，SRAM payload 仍可能被 allocation 逻辑读取，因此正常无命中和刷新期间的响应屏蔽不能混为一谈。

源码位置：`src/main/scala/xiangshan/frontend/bpu/ittage/Bundles.scala:42-55`、`src/main/scala/xiangshan/frontend/bpu/ittage/IttageTable.scala:160-168`、`src/main/scala/xiangshan/frontend/bpu/ittage/Ittage.scala:206-239, 295-304`。

#### 3.1.2 bank 组织与清零规模

默认有 5 张 `IttageTable`，每张表实例化 2 个 SRAM bank，共 10 个 bank。一次预测同时查询 5 张表，但每张表只访问其两个 bank 中被 hash 选中的一个。

`foldedWidth` 是 SRAM 宏内部的物理折叠，不增加 bank 数或替换 way。256-entry 表每 bank 有 128 个逻辑 set、`foldedWidth = 1`；512-entry 表每 bank 有 256 个逻辑 set、`foldedWidth = 2`。`FoldedSRAMTemplate` 使用 `nRows = set / width`，因此物理组织为：

| 表规模 | 每 bank 物理 row 数 |
| --- | ---: |
| 256-entry / 512-entry | 128 |

`extraReset` 由底层 SRAM reset 状态机按折叠后的物理 row 推进，所以所有表、所有 bank 都确定扫描 128 个物理 row。各 bank 同时接收 `extra_reset` 并行清零，SRAM 主体清零耗时为 128 个物理 row 写周期。与之不同，ITTAGE 原有的 useful 老化扫描使用 `Counter(usefulCanReset, NumSetsPerBank)` 生成 `resetSet`，按逻辑 set 推进：256-entry 表扫描 128 个逻辑 set，512-entry 表扫描 256 个逻辑 set。两种扫描不能混为一谈。


#### 3.1.3 读端口和旧响应路径

预测和训练使用同一个 hash 函数计算 bank、set 和 tag；刷新分析只需确认二者访问同一份 entry，不需要展开完整 hash 公式。

顶层和 SRAM bank 的读条件为：

源码位置：`src/main/scala/xiangshan/frontend/bpu/ittage/Ittage.scala:190-195`、`src/main/scala/xiangshan/frontend/bpu/ittage/IttageTable.scala:120-161`。

顶层条件为 `s1_fire && s1_isIndirect`；选中 bank 的条件为 `io.req.fire && s0_bankMask(bankIdx)`。

SRAM 读出后，命中条件为：

源码位置：`src/main/scala/xiangshan/frontend/bpu/ittage/IttageTable.scala:163-165`。

命中为 `tableReadData.valid && tagHit`，响应有效还需 `s1_valid`。

`holdRead = true` 会在没有新请求时保留上一次 SRAM 输出。因此禁止新读请求并不等于旧响应已经消失，旧 payload 和 response valid 是两条需要分别关注的传播路径。

IttageTable 内部还保存 `s1_setIdx/s1_tag/s1_bankMask/s1_valid`。这些是 SRAM 读流水线寄存器，不属于 entry SRAM；清理 entry 不会自动清理这些寄存器。


#### 3.1.4 写端口和旧数据回写路径

训练 update 不直接写 SRAM，而是先进入目标 bank 的 WriteBuffer：

源码位置：`src/main/scala/xiangshan/frontend/bpu/ittage/IttageTable.scala:198-228`。

真正驱动 SRAM 写端口的条件为：

源码位置：`src/main/scala/xiangshan/frontend/bpu/ittage/IttageTable.scala:221-228`。

写回条件为 `writeBufferRead.valid && !bank.io.r.req.valid`；`writeBufferRead.ready` 还要求 `bank.io.w.req.ready`。

每个 bank 是 single-port SRAM，同一 bank 冲突时预测读优先，WriteBuffer 中的请求继续等待；另一个 bank 仍可并行写入。因此旧 update 可能在触发刷新时仍停留在 WriteBuffer，而不是已经进入 SRAM。

SRAM 使用 bitmask 支持三类写入：

| 写入来源 | entry 修改范围 |
| --- | --- |
| 正常 update 且 `usefulCntValid` | 完整 entry |
| 正常 update 且 `!usefulCntValid` | 除 useful 外的字段 |
| `usefulCanReset` | 只写 useful |

这些写入都使用同一份 entry SRAM，不存在独立的 valid SRAM、target SRAM 或 useful SRAM。


#### 3.1.5 SRAM 与非 SRAM 状态边界

| 对象 | 类型 | 是否属于 entry SRAM |
| --- | --- | :---: |
| `tables(bank)` 中的 `IttageEntry` | SRAM 存储位 | 是 |
| `s1_setIdx/s1_tag/s1_bankMask/s1_valid` | 读流水线寄存器 | 否 |
| `updateWdata/updateBitmask/updateBankMask` | 组合 Wire | 否 |
| WriteBuffer 的 `entries/valids/needWrite/replacer` | 独立寄存器状态 | 否 |
| `needReset/resetSet` | useful 老化控制状态 | 否 |
| debug `valids` | 仅调试统计寄存器，不参与命中 | 否 |

当前 SRAM 设置 `shouldReset = true`，只提供上电清零；`io.sramResetDone` 是所有 bank `resetDone` 的与。现有 RTL 尚未启用上下文刷新所需的 runtime `extraReset`，相关修改放在第 4.1 节。

### 3.2 IttageTable WriteBuffer

每张表的每个 bank 都有一个 4-entry WriteBuffer。其待写请求包含：

源码位置：`src/main/scala/xiangshan/frontend/bpu/ittage/Bundles.scala:82-85`、`src/main/scala/xiangshan/frontend/bpu/ittage/IttageTable.scala:202-217`。

实际寄存器状态包括：

| 状态 | 写入条件 | 旧状态出口 |
| --- | --- | --- |
| `entries` | 正常 table update 或 useful-only 请求进入缓冲 | 作为后续 SRAM 写数据 |
| `valids` | 分配或合并缓冲项时置位 | 参与后续请求的命中、合并和替换 |
| `needWrite` | 缓冲项需要写回时置位，成功出队时清除 | 产生 `read.valid` 并驱动 SRAM 写回 |
| replacer 状态 | WriteBuffer 项被选择或更新时改变 | 决定后续缓冲 victim |

WriteBuffer 有两个输入来源：

源码位置：`src/main/scala/xiangshan/frontend/bpu/ittage/IttageTable.scala:212-217`。

入队条件是对应 bank 的正常 `io.update.valid`，或 `usefulCanReset` 产生的 useful-only 请求。

只有对应 bank 没有预测读且 SRAM 写口 ready 时，待写项才会出队。预测读路径没有对 WriteBuffer 做旁路，因此缓冲中的 update 在写回前对预测不可见，却仍可能在之后改变 SRAM。

对本方案的安全目标而言，只有 `needWrite` 能直接授权旧 entry 出队并重新写入 SRAM，必须清除。`entries/valids/replacer` 会影响刷新后的槽位命中或 victim 选择，但在 `needWrite = false` 且新请求完整覆盖命中槽位的前提下，不能重新写入旧 target。

源码位置：`src/main/scala/xiangshan/frontend/bpu/ittage/IttageTable.scala:198-229`、`src/main/scala/xiangshan/frontend/bpu/WriteBuffer.scala:84-86, 145-257`。

### 3.3 useful 老化控制状态

useful 数据位于 `IttageEntry` SRAM 中；另外，每张 `IttageTable` 还保存 useful 老化任务的控制状态：

| 状态 | 更新条件 | 旧状态出口 |
| --- | --- | --- |
| `needReset` | `resetUsefulCnt` 到达时置位，扫描结束时清除 | 允许继续生成 useful-only 请求 |
| `resetSet` | `usefulCanReset` 时推进 | 决定下一次 useful-only 写入的 set |

扫描请求条件为：

源码位置：`src/main/scala/xiangshan/frontend/bpu/ittage/IttageTable.scala:183-190`。

条件为无读、无正常 update 且 `needReset` 有效。

当条件满足时，同一个 `resetSet` 被送往所有 bank 的 WriteBuffer，只修改 useful 位。因而“SRAM 中的 useful 数据”和“尚未完成的 useful 扫描任务”是两类不同状态：前者属于 entry，后者属于寄存器控制状态。

源码位置：`src/main/scala/xiangshan/frontend/bpu/ittage/IttageTable.scala:177-196, 211-217`。

### 3.4 RegionWays 状态

RegionWays 保存 target 高位，包含两类实际状态：

源码位置：`src/main/scala/xiangshan/frontend/bpu/ittage/RegionWays.scala:39-49, 82-90`。

`regions` 的组合读结果与 SRAM entry 的 `targetOffset.offset` 拼接形成完整 target。因而旧 region 是旧 target 的一部分；`RegionEntry` 只是 Bundle 类型，真正的存储是 `regions` 寄存器数组。

写入条件为：

源码位置：`src/main/scala/xiangshan/frontend/bpu/ittage/Ittage.scala:307-320`。

即 `!updateRealUsePCRegion && updateAlloc.reduce(_ || _)`。

`writePointer` 是组合 Wire，不保存状态。它按以下顺序选择位置：

1. 已存在相同 region 时选择命中项；
2. 存在无效项时优先选择无效项；
3. 全部有效时使用 PLRU victim。

每次 `io.writeValid` 都会 touch replacer。由于 regions 全部无效时不会使用旧 PLRU victim，重新填充又会持续更新 PLRU，这一结构事实决定了“regions 数据”和“PLRU 历史”在第 4 节可以采用不同的刷新要求。

| 对象 | 类型 | 旧状态影响 |
| --- | --- | --- |
| `regions(i).valid/region` | 寄存器 | 旧 target 高位可能参与 target 重建 |
| replacer 内部状态 | 寄存器 | 所有 region 有效时影响后续 victim |
| `writePointer/replacerTouchWays` | 组合 Wire | 不跨周期保存，无需作为独立状态处理 |

源码位置：`src/main/scala/xiangshan/frontend/bpu/ittage/RegionWays.scala`、`src/main/scala/xiangshan/frontend/bpu/ittage/Ittage.scala:226-239, 307-332`。

### 3.5 顶层策略寄存器

顶层与刷新相关的策略状态为：

| 状态 | 正常更新条件 | 旧状态出口 |
| --- | --- | --- |
| `tickCnt` | 需要分配时按是否存在可分配项饱和加减；饱和后写零 | 饱和时触发所有表的 useful 扫描 |
| `useAltOnNa` | 当前源码只有初始化，没有更新 | 当前不参与预测和训练，没有功能性旧状态出口 |

`tickCnt.resetZero()` 和 `tickCnt.selfUpdate(...)` 是写同一个 `tickCnt` 的方法，不是额外寄存器。刷新分析需要关注的是 `tickCnt` 保存的累计结果，而不是方法名。

`useAltOnNa` 虽然是寄存器，但当前 RTL 没有使用它。将其列在本节只是为了说明它不会产生旧上下文副作用，不需要展开 TAGE 中同名策略的工作原理。

源码位置：`src/main/scala/xiangshan/frontend/bpu/ittage/Ittage.scala:77-78, 379-399`、`src/main/scala/xiangshan/frontend/bpu/ittage/Bundles.scala:27-40`。

### 3.6 预测流水线寄存器

预测路径中需要区分组合 S2 结果和真正跨周期保存的寄存器。

#### 3.6.1 实际寄存器

| 位置 | 寄存器 | 写入条件 | 旧状态出口 |
| --- | --- | --- | --- |
| IttageTable S1 | `s1_setIdx/s1_tag/s1_bankMask` | `io.req.fire` | 选择并比较 SRAM 旧响应 |
| IttageTable S1 | `s1_valid` | 每拍捕获 `io.req.valid` | 产生 table `resp.valid` |
| 顶层 S1 | `s1_startPc` | `s0_fire` | 形成 table 读请求 |
| 顶层 S2 | `s2_startPc` | `s1_fire` | RegionWays 未命中时提供 PC region |
| 顶层 S3 | `s3_ittageTarget` | `s2_fire` | 驱动 `io.prediction.target` |
| 顶层 S3 | provider/alternate 的 valid、表号、target、confidence、useful | `s2_fire` | 驱动 prediction hit 和训练 meta |
| 顶层 S3 | allocation valid/bits | `s2_fire` | 进入训练 meta，决定后续 table allocation |

S3 provider/alternate 状态包括：

源码位置：`src/main/scala/xiangshan/frontend/bpu/ittage/Ittage.scala:103-112`。

当前 allocation 状态由匿名 `RegEnable` 直接驱动 `ittageMeta.allocate`，架构上仍属于 S3 寄存器状态。

#### 3.6.2 无需清零的组合状态

`s2_ittageTarget`、provider/alternate 选择、RegionWays 读取和 target 拼接都是 Wire。它们不跨周期保存，因此不是独立刷新对象；但它们的输入可能来自旧 SRAM、旧 RegionWays 或旧流水线寄存器，所以刷新窗口仍需要阻断其输出有效性。

`io.prediction` 和 `io.meta` 是接口，不是额外存储。旧结果是否能够离开 ITTAGE，取决于 S3 寄存器和输出 valid 门控。

源码位置：`src/main/scala/xiangshan/frontend/bpu/ittage/IttageTable.scala:120-168`、`src/main/scala/xiangshan/frontend/bpu/ittage/Ittage.scala:58-112, 190-305`。

### 3.7 训练流水线寄存器

训练路径由 T1 状态、T1 组合更新意图和 T2 table update 寄存器组成：

源码位置：`src/main/scala/xiangshan/frontend/bpu/ittage/Ittage.scala:117-178, 401-413`、`src/main/scala/xiangshan/frontend/bpu/ittage/IttageTable.scala:171-228`。

#### 3.7.1 T1 状态

| T1 状态 | 写入条件 | 旧状态出口 |
| --- | --- | --- |
| `t1_train` 底层 `RegEnable` | `t0_fire` | 提供旧分支、真实 target 和 start PC |
| `t1_trainFoldedPhr` | `t0_fire` | 计算 table update 的 bank/set/tag |
| `t1_meta` 普通字段 | `t0_fire` | 提供 provider、counter、useful 和 allocation 信息 |
| provider/alternate 表号和 target 等细粒度 `RegEnable` | 对应 meta valid 且 `t0_fire` | 参与 table 和 RegionWays 更新 |
| `RegNext(t0_fire)` | 每拍捕获 `t0_fire` | 与 `hasTrainBranch` 共同形成 `updateValid` |

`t1_train` 和 `t1_meta` 虽然外层声明为 Wire，右侧的 `RegEnable` 仍生成实际寄存器。刷新分析必须跟踪这些底层寄存器，不能因为外层类型是 Wire 而忽略。

#### 3.7.2 T1 组合更新意图

以下信号由 T1 寄存器组合生成，本身不保存状态：

源码位置：`src/main/scala/xiangshan/frontend/bpu/ittage/Ittage.scala:170-178, 334-389`。

它们无需作为寄存器清零，但可能在刷新触发当拍把旧 T1 状态送入 T2、RegionWays 或 `tickCnt`，因此属于需要门控的副作用路径。

#### 3.7.3 T2 table update 寄存器

每张 `IttageTable` 都有一组独立的 T2 update 寄存器：

| T2 状态 | 写入条件 | 旧状态出口 |
| --- | --- | --- |
| `update.valid` | `RegNext(updateMask(i))` | 向对应表产生正常 WriteBuffer 请求 |
| `update.resetUsefulCnt` | `RegNext(updateResetUsefulCnt)` | 启动对应表的 useful 扫描 |
| `correct/alloc/oldCnt` | `updateMask(i)` | 构造 confidence 和 allocation 写数据 |
| `targetOffset/oldTargetOffset` | `updateMask(i)` | 构造 target 写数据 |
| `usefulCntValid/usefulCnt` | `updateMask(i)` | 决定 useful 是否随 entry 更新 |
| `startPc/foldedHist` | `updateMask(i)` | 计算写入 bank、set 和 tag |

T2 接口本身不直接写 SRAM，而是先形成 IttageTable 的 WriteBuffer 入队请求。只禁止新的 `t0_fire` 不能消除已经位于 T1/T2 中的旧训练状态，这就是训练流水线必须纳入刷新架构分析的原因。


---

## 4. 刷新方案

ITTAGE 的刷新不是单纯屏蔽预测输出。根据 [[01-BPU刷新方案概览]] 的顶层契约，BPU 顶层会向各子预测器分发两个刷新相关输入：

源码位置：`src/main/scala/xiangshan/frontend/bpu/Bpu.scala:232-245`。

其中：

- `contextFlush` 是单拍清零脉冲，负责清理不能自然排空的安全有效状态，并启动 SRAM runtime reset；
- `bpuFlushing` 是 BPU 整体刷新窗口电平，从 `contextFlush` 当拍持续到所有子预测器 `resetDone` 聚合完成，负责阻断预测流水推进、响应捕获、训练和写入；
- `resetDone` 只表示 ITTAGE 自身清零完成，不表示 ITTAGE 可以恢复工作。预测和训练恢复由 `bpuFlushing` 拉低决定。

### 4.0 统一分析标准

第 4 章每个模块都按以下三个方面分析：

1. **IO 接口与信号传递（包括门控）**：说明 `contextFlush`/`bpuFlushing`/完成信号以及正常 req/resp/update/write 信号的来源、去向和优先级。
2. **存储结构的刷新**：列出实际 SRAM、`RegInit/RegEnable` 寄存器、WriteBuffer 和 replacer 状态，分别说明是否清零及清零方式。Wire 只需分析其输入和副作用，不把 Wire 当作刷新对象。
3. **`bpuFlushing` 期间的污染防控**：既禁止新请求，也禁止已在流水线、WriteBuffer 或组合路径中的旧状态产生写入和有效输出。

各模块的完成条件和时序不在每个小节重复展开，统一在第 4.7 节区分单拍寄存器清零和多拍 SRAM reset，说明是否产生独立完成信号、何时报告 `resetDone`以及是否改变原流水级。

后文中标注为 **[时序修改]** 的 Scala 片段会改变原有寄存器写入条件、捕获时刻或多拍控制状态；仅增加当拍组合门控的片段不改变寄存器的原有时序。

第 4 节后续内容是对 ITTAGE 内部的待实现修改，而不是新增 BPU 顶层协议。

#### 4.0.1 `HasBpuFlush` 的统一解包与门控规则

`Ittage` 从 `BasePredictorIO` 接收的三个刷新端口均为 `Option`。其中 `contextFlush`/`bpuFlushing` 是只读输入，`resetDone` 是输出；`IttageTable` 新增两个可选输入，`RegionWays` 只新增可选 `contextFlush` 输入。各模块均先在模块体顶部将其只读端口解包为内部别名；后续业务门控、`when` 条件和完成条件只引用别名，不直接引用 `io.contextFlush.get` 或 `io.bpuFlushing.get`。

```scala
// Ittage.scala / IttageTable.scala / RegionWays.scala：各模块的首个刷新消费点之前
// HasBpuFlush=false 时 else 仅为 Scala 类型占位；刷新专用消费逻辑仍由下文的 if 守卫裁剪。
private val contextFlush = if (HasBpuFlush) io.contextFlush.get else false.B

// 仅 Ittage.scala / IttageTable.scala：RegionWays 不接收 bpuFlushing。
private val bpuFlushing  = if (HasBpuFlush) io.bpuFlushing.get  else false.B
```

`s0_fire`、`s1_fire`、`t0_fire` 等基于上述别名导出的门控可保持为普通表达式；只有像 `sx_fire`/`sx_flush` 那样需要默认赋值并由多个条件后续覆盖的派生控制信号才声明为 `Wire`。可选端口的跨模块连接和 `resetDone` 输出仍必须放入 `if (HasBpuFlush)`，在该条件块内通过 `.get` 访问，避免关闭配置下建立不存在的端口或刷新专用结构。

本方案只要求完成以下操作：

1. 使所有 `IttageTable` entry 不可命中；当前 SRAM 没有独立 valid array，因此仍使用整行 `extraReset`；
2. 清除 WriteBuffer 的 `needWrite`，阻止旧完整 entry 在 SRAM 清零后重新写回；
3. 清除 `RegionWays.regions.valid`，阻止旧 target 高位参与 target 重建；
4. 清除 S3 预测有效令牌，并通过窗口门控排空 T1/T2 更新有效令牌；payload 寄存器不要求清零；
5. 在整个 `bpuFlushing` 窗口内禁止预测输出、训练更新、WriteBuffer 出入队和 SRAM/RegionWays 写入。

`needReset/resetSet/tickCnt/resetUsefulCnt`、两类 PLRU、WriteBuffer `valids/entries`、流水线 payload 和无效 entry 的数据位都不具备旧 target 输出或写回能力，不纳入必要刷新集合。其中 WriteBuffer `valids/entries/PLRU` 会因完整复用既有方案而附带清零。

实现中必须区分以下信号：

1. `contextFlush`：单拍清零触发，同时启动 SRAM runtime reset；
2. `io.sramResetDone`：所有 ITTAGE table/bank 的 SRAM 清零完成汇总，直接参与 `resetDone` 生成；
3. `bpuFlushing`：BPU 顶层刷新窗口，统一阻断预测流水推进、响应捕获、训练和写入。

预测推进、训练和写路径通过各模块内部的 `bpuFlushing` 别名统一门控；Table response valid 不单独门控，由顶层 `s2_fire` 阻断捕获。业务路径不使用 `io.sramResetDone` 做门控，也不新增本地业务窗口别名。

| 第三章对应内容 | 刷新对象 | 是否刷新 | 核心处理 |
| --- | --- | :---: | --- |
| 3.1 | IttageTable entry SRAM | 是 | `contextFlush` 启动 `extraReset`，清零整个 entry |
| 3.1、3.6 | IttageTable S1 读流水线 | 部分 | `s1_valid` 在窗口首拍排空；set/tag/bank payload 无需清零 |
| 3.2 | IttageTable WriteBuffer 数据状态 | 是 | 完整复用 `hasContextFlush`；安全关键状态是 `needWrite` |
| 3.2 | WriteBuffer PLRU 状态 | 附带刷新 | 只影响缓冲 victim，不是安全必要状态 |
| 3.3 | useful 数据及老化控制状态 | 否 | 只能修改 useful，不能写入或授权旧 target |
| 3.4 | RegionWays 数据 | 部分 | 只清 `regions.valid`，`region` payload 无需清零 |
| 3.4 | RegionWays PLRU 状态 | 否 | 全部 region 无效后不会使用旧 victim |
| 3.5 | `tickCnt` | 否 | 只改变 useful 扫描时机 |
| 3.5 | `useAltOnNa` | 否 | 当前只有初始化、没有更新和功能出口，不保存上下文训练结果 |
| 3.6 | 顶层 S1/S2/S3 预测流水线 | 部分 | 清 `s3_provided`，其余 payload 保留；窗口内屏蔽 prediction/meta |
| 3.7 | T1/T2 训练流水线 | 部分 | 由窗口门控排空 update valid，payload 保留；阻断所有副作用 |
| 3.1.5 | debug `valids` | 否 | 不参与功能命中；仅在要求调试统计按上下文隔离时清零 |

`resetDone` 在 `HasBpuFlush` 分支内由 `!contextFlush && io.sramResetDone` 生成：`contextFlush` 当拍强制报告未完成，SRAM 完成后由 `io.sramResetDone` 报告 ITTAGE 自身完成。该信号不参与正常预测和训练。

### 4.1 IttageTable SRAM 刷新方案

#### 4.1.1 IO 接口与信号传递（包括门控）

`IttageTable` 需要直接接收单拍清零触发和完整刷新窗口。`contextFlush` 用于启动 SRAM reset，`bpuFlushing` 用于禁止刷新窗口内的正常访问：

源码位置（待修改）：`src/main/scala/xiangshan/frontend/bpu/ittage/IttageTable.scala:42-75`、`Ittage.scala:68-75`。建议代码：

```scala
// IttageTableIO：HasBpuFlush=false 时两个端口均不生成
val contextFlush: Option[Bool] = Option.when(HasBpuFlush)(Input(Bool()))
val bpuFlushing:  Option[Bool] = Option.when(HasBpuFlush)(Input(Bool()))
// 现有 sramResetDone 输出保持不变

// Ittage.scala：contextFlush/bpuFlushing 为 §4.0.1 的顶层内部别名。
// Option 到 Option 的连线必须位于 if 内；关闭时既不访问 .get，也不生成连线。
if (HasBpuFlush) {
  tables.foreach { table =>
    table.io.contextFlush.get := contextFlush
    table.io.bpuFlushing.get  := bpuFlushing
  }
}

// IttageTable.scala：模块内业务逻辑只使用别名。
private val contextFlush = if (HasBpuFlush) io.contextFlush.get else false.B
private val bpuFlushing  = if (HasBpuFlush) io.bpuFlushing.get  else false.B
```

#### 4.1.2 存储结构的刷新

ITTAGE provider 命中依赖 `IttageEntry.valid && tagHit`。`valid/tag/confidence/useful/targetOffset` 位于同一个 SRAM entry 中，没有独立 valid 或 useful SRAM。运行时整行写 0 可同时清除命中状态、target 和训练计数器。

**[时序修改：新增多拍 SRAM runtime reset 过程]**

`extraReset` 逐行写 0 整个 entry，关键效果是 `valid = 0`，同时也清除 tag、target 和计数器：

源码位置（待修改）：`src/main/scala/xiangshan/frontend/bpu/ittage/IttageTable.scala:135-152` 的 `FoldedSRAMTemplate` 实例化和 bank reset 连接。

建议代码：

```scala
private val tables = Seq.tabulate(NumBanks) { bankIdx =>
  Module(new FoldedSRAMTemplate(
    new IttageEntry(tagLen),
    setSplit = 1,
    waySplit = 1,
    dataSplit = dataSplit,
    set = NumSetsPerBank,
    width = foldedWidth,
    shouldReset = true,
    holdRead = true,
    singlePort = true,
    useBitmask = true,
    withClockGate = true,
    hasMbist = hasMbist,
    hasSramCtl = hasSramCtl,
    extraReset = HasBpuFlush,
    suffix = Option(s"bpu_ittage_bank$bankIdx")
  )).suggestName(s"ittage_table_bank$bankIdx")
}

if (HasBpuFlush) {
  tables.foreach { bank =>
    bank.extra_reset.get := contextFlush
  }
}
io.sramResetDone := tables.map(_.io.resetDone).reduce(_ && _)
```

整行清零会附带清除 tag、target、confidence 和 useful；从安全语义看关键效果只有 `valid = 0`。若未来增加独立 valid array，可以只清 valid array，不必扫描 payload SRAM。

debug `valids` 仅在 `debug` 条件编译分支中统计曾经写入的表项，不参与 SRAM 命中、分配或写回。功能刷新可以保留它；如果调试统计也要求按上下文重新开始，则在同一个 `contextFlush` 分支中将其清零。

#### 4.1.3 `bpuFlushing` 期间的污染防控

SRAM 是 single-port 结构，读请求必须在完整刷新窗口阻断，避免与 runtime reset 竞争。Table 输出端不需要再用 `bpuFlushing` 门控 `io.resp.valid`：触发拍顶层 `s2_fire = false`，不会捕获当拍旧响应；触发边沿后 `s1_valid` 由已门控的 `io.req.valid` 自然写成 false。即使 `holdRead` 保留旧 payload，也没有有效令牌把它送入 S3。

源码位置（待修改）：`src/main/scala/xiangshan/frontend/bpu/ittage/IttageTable.scala:156-161` 的 bank 读请求赋值。

建议代码：

```scala
tables.zipWithIndex.foreach { case (bank, idx) =>
  bank.io.r.req.valid :=
    io.req.fire && s0_bankMask(idx) && (if (HasBpuFlush) !bpuFlushing else true.B)
  bank.io.r.req.bits.setIdx := s0_setIdx
}

// 保持原响应流水；io.req.valid 已在顶层被 bpuFlushing 门控。
private val s1_valid = RegNext(s0_valid)
io.resp.valid := s1_valid && s1_reqReadHit
io.resp.bits.cnt          := tableReadData.confidenceCnt
io.resp.bits.usefulCnt    := tableReadData.usefulCnt
io.resp.bits.targetOffset := tableReadData.targetOffset
```

因此，接收 SRAM 响应处的附加门控对当前整体方案是冗余防护，不是安全边界。该判断依赖第 4.5 节同时门控 `s2_fire` 和最终 `prediction.hit/meta.valid`；若未来允许刷新窗口内推进 S2，则必须恢复 response valid 门控。实际失效的响应寄存器只有 `s1_valid`，它在触发边沿自然排空；`s1_setIdx/s1_tag/s1_bankMask` 和响应 payload 不清零。

---

### 4.2 WriteBuffer 刷新方案

#### 4.2.1 IO 接口与信号传递（包括门控）

ITTAGE 的每个 SRAM bank 都使用通用 `WriteBuffer`，结构和 ABTB 已实现方案一致。本方案直接完整复用 ABTB 的处理：实例化时将 `hasContextFlush` 绑定为 elaboration-time `HasBpuFlush`，在开启分支连接 `contextFlush`，并在整个 `bpuFlushing` 窗口门控入队、出队和 SRAM write valid。

#### 4.2.2 存储结构的刷新

`hasContextFlush` 在触发拍清除 `needWrite/valids/entries` 和内部 replacer 状态。其中阻止旧 target 写回的关键状态是 `needWrite`；`valids/entries/replacer` 只影响后续缓冲槽位命中和 victim 选择，其清零是复用通用方案的附带行为。这里不再为 ITTAGE 单独裁剪 WriteBuffer 刷新逻辑。

源码依据：`src/main/scala/xiangshan/frontend/bpu/WriteBuffer.scala:54-73, 256-270`；复用参考：`src/main/scala/xiangshan/frontend/bpu/abtb/AheadBtbBank.scala:81-100`。

**[时序修改：完整复用 WriteBuffer context-flush 接口]**

源码位置（待修改）：`src/main/scala/xiangshan/frontend/bpu/ittage/IttageTable.scala:202-217` 的 WriteBuffer 实例化及入队连接。

建议代码：

```scala
private val writeBuffers = Seq.tabulate(NumBanks) { bankIdx =>
  val buffer = Module(new WriteBuffer(
    gen = new IttageWriteReq(tagLen, NumSetsPerBank, ittageEntrySz),
    numEntries = TableWriteBufferSize,
    numPorts = 1,
    hasContextFlush = HasBpuFlush,
    nameSuffix = s"ittageTable${tableIdx}_bank$bankIdx"
  )).suggestName(s"ittage_write_buffer_bank$bankIdx")

  if (HasBpuFlush) {
    buffer.io.contextFlush.get := contextFlush
  }
  buffer
}
```

#### 4.2.3 `bpuFlushing` 期间的污染防控

仅禁止新训练进入 WriteBuffer 不能阻止已经存在的 entry 出队，因此入队 valid、出队 ready 和 SRAM write valid 都必须使用 `bpuFlushing` 门控：

源码位置（待修改）：`src/main/scala/xiangshan/frontend/bpu/ittage/IttageTable.scala:212-228` 的 WriteBuffer 入队、出队和 SRAM 写回连接。

```scala
writePort.valid :=
  ((io.update.valid && updateBankMask(bankIdx)) || usefulCanReset) &&
  (if (HasBpuFlush) !bpuFlushing else true.B)

val writeValid =
  readPort.valid &&
  !bank.io.r.req.valid &&
  (if (HasBpuFlush) !bpuFlushing else true.B)

bank.io.w.apply(
  writeValid,
  writeEntry,
  writeSetIdx,
  true.B,
  writeBitMask
)

readPort.ready :=
  bank.io.w.req.ready &&
  !bank.io.r.req.valid &&
  (if (HasBpuFlush) !bpuFlushing else true.B)
```

`readPort.ready` 门控不足以阻止已经有效的出队请求写入 SRAM，因此 `writeValid` 也必须使用内部 `bpuFlushing` 别名加入同一门控。

Useful 老化请求与正常训练请求共用 WriteBuffer。上述入队、出队和 SRAM 写使能门控已阻止刷新窗口内的 useful-only 写入，因此 `needReset/resetSet/usefulCanReset` 不需修改。刷新后旧扫描继续执行只可能清零新表项的 useful 位，影响替换和预测准确率，但不会恢复、构造或授权旧 target。

---

### 4.3 RegionWays 刷新方案

#### 4.3.1 IO 接口与信号传递（包括门控）

`RegionWays` 增加可选的 `contextFlush`，由 ITTAGE 顶层在 `HasBpuFlush` 分支中传入。正常 `writeValid/writeRegion` 来自 T1 训练组合结果，`writeValid` 在顶层通过内部 `bpuFlushing` 别名叠加门控。读端口为组合读，输出有效性由第 4.5 节 prediction/meta 门控保证。

#### 4.3.2 存储结构的刷新

RegionWays 的持久状态包含 `regions` 数据寄存器和 replacer 内部 PLRU 状态。安全刷新只需清除 16 个 `RegionEntry.valid`：`valid = false` 后旧 `region` payload 不能参与 target 重建；全部 region 无效时分配优先选择无效项，不会读取旧 PLRU victim。因此 `region` payload 和 PLRU 均无需清零。

**[时序修改：增加 `regions` 最高优先级清零分支]**

源码位置（待修改）：`src/main/scala/xiangshan/frontend/bpu/ittage/RegionWays.scala:26-35, 52-90`。

```scala
// RegionWays.scala
class RegionWaysIO extends Bundle {
  ...
  val contextFlush: Option[Bool] = Option.when(HasBpuFlush)(Input(Bool()))
}

// Ittage.scala
if (HasBpuFlush) {
  rTable.io.contextFlush.get := contextFlush
}

// RegionWays.scala
private val contextFlush = if (HasBpuFlush) io.contextFlush.get else false.B

// 原有写回保持在原位置。
when(io.writeValid) {
  when(!regions(writePointer).valid) {
    regions(writePointer).valid := true.B
  }
  regions(writePointer).region := io.writeRegion
}

// 置于既有 writeValid 写回之后，last-connect 赋予刷新最高优先级。
if (HasBpuFlush) {
  when(contextFlush) {
    for (i <- 0 until RegionNums) {
      regions(i).valid := false.B
    }
  }
}
```

正常 `replacer.access(replacerTouchWays)` 保持不变；不增加 flush touch，也不要求确定化 PLRU 状态。

#### 4.3.3 `bpuFlushing` 期间的污染防控

RegionWays 写入由训练路径产生。ITTAGE 自身 SRAM 提前完成清零后，BPU 可能仍在等待其他预测器，因此必须使用完整的 `bpuFlushing` 窗口阻断写入和 replacer touch：

源码位置（待修改）：`src/main/scala/xiangshan/frontend/bpu/ittage/Ittage.scala:318` 的 `rTable.io.writeValid` 赋值。

```scala
// 既有功能正确性前提：未分配的 table 必须给出确定的 false
private val updateAlloc = WireInit(0.U.asTypeOf(Vec(NumTables, Bool())))
// 删除原 updateAlloc := DontCare

rTable.io.writeValid :=
  updateValid &&
  !updateRealUsePCRegion &&
  updateAlloc.reduce(_ || _) &&
  (if (HasBpuFlush) !bpuFlushing else true.B)
```

`!bpuFlushing` 是通过模块内部别名施加的上下文刷新新增门控；`updateValid` 和 `updateAlloc` 的确定默认值是既有功能正确性前提。仅增加 `updateValid` 仍不足够：有效训练但本次不分配时，未赋值的 `updateAlloc` 元素仍会参与 `reduce`。因此不能保留 `updateAlloc := DontCare` 后再直接对它做无条件归约。

---

### 4.4 完成信号生成

#### 4.4.1 生成逻辑

`contextFlush` 启动 ITTAGE 本次刷新。每张 table 的 `sramResetDone` 在 ITTAGE 顶层直接相与后赋给 `io.sramResetDone`，`resetDone` 在 `HasBpuFlush` 分支内使用 `!contextFlush && io.sramResetDone`。`bpuFlushing` 不参与 ITTAGE `resetDone` 的组合生成，避免完成信号与 BPU 聚合窗口形成控制环。

#### 4.4.2 建议代码

ITTAGE 不新增完成握手寄存器。`io.sramResetDone` 由所有 table 的 SRAM 完成信号相与生成；`io.resetDone.get` 仅在 `HasBpuFlush` 分支内取 `!contextFlush && io.sramResetDone`。

SRAM reset 是多拍操作，`contextFlush` 只有一拍。根据 `FoldedSRAMTemplate` 的同步 reset 语义，`contextFlush` 当拍通过 `!contextFlush` 强制 `resetDone` 为低；触发边沿后 `io.sramResetDone` 在清零期间为低，完成后拉高。

**[时序修改：直接使用 SRAM 完成信号生成 `resetDone`]**

源码位置（待修改）：`src/main/scala/xiangshan/frontend/bpu/ittage/Ittage.scala:74-75` 的 `io.sramResetDone/io.resetDone` 生成区域。同步 resetDone 语义依据为 `utility/src/main/scala/utility/sram/SRAMTemplate.scala:274-287, 499`。

```scala
io.sramResetDone := tables.map(_.io.sramResetDone).reduce(_ && _)
if (HasBpuFlush) {
  io.resetDone.get := !contextFlush && io.sramResetDone
}
```

`FoldedSRAMTemplate extraReset` 的既有接口契约保证：收到 `contextFlush` 后，`resetDone` 在清零过程中为低，完成后恢复为高。触发拍通过 `!contextFlush` 避免使用刷新前的瞬时高电平；`resetDone` 只反映 ITTAGE 自身清零完成，不能由 `bpuFlushing` 拉低。

#### 4.4.3 与 `bpuFlushing` 的关系

`io.sramResetDone` 不参与预测、训练、WriteBuffer 或 SRAM 端口门控。ITTAGE 的所有业务路径统一使用内部 `bpuFlushing` 别名；即使 ITTAGE 自身提前报告完成，只要 BPU 仍在等待其他预测器，该别名仍会继续阻断 ITTAGE。`resetDone` 不能由 `bpuFlushing` 生成，否则会与 BPU 顶层对各预测器 `resetDone` 的归约形成等待环路。

---

### 4.5 顶层预测流水线刷新方案

#### 4.5.1 IO 接口与信号传递（包括门控）

顶层 `s0_fire/s1_fire/s2_fire` 推进 S1/S2/S3，table `req/resp` 连接 S1/S2，`prediction/meta` 由 S3 输出。`contextFlush` 只显式清除保持型预测有效寄存器 `s3_provided`；`bpuFlushing` 门控流水推进、table 请求和最终有效输出 `prediction.hit/meta.valid`。

最终输出门控不会削弱安全性，反而负责覆盖 `contextFlush` 触发拍到寄存器清零生效前的组合窗口。但它不能替代 `s3_provided` 清零：若只门控输出，`bpuFlushing` 拉低后，保持不变的旧 `s3_provided/s3_ittageTarget` 仍可能与新的 `s3_fire` 组合。因此安全条件是“窗口内门控最终 valid + 触发拍清 `s3_provided`”，两者缺一不可。

#### 4.5.2 存储结构的刷新

**[时序修改：只清预测有效令牌]**

S3 的 `s3_ittageTarget` 保存旧 target，但真正授权输出的是 `s3_provided`，因为 `prediction.hit = s3_fire && s3_provided`。因此预测流水中显式清零的寄存器名称只有：

```text
s3_provided
```

`s1_valid` 在触发边沿自然排空，不设置独立 reset。`s1_startPc/s2_startPc`、`s1_setIdx/s1_tag/s1_bankMask`、`s3_ittageTarget`、`s3_providerTarget/s3_altProviderTarget`、provider/alternate 表号、counter、useful 和 allocation 寄存器均不清零。

源码位置（待修改）：`src/main/scala/xiangshan/frontend/bpu/ittage/Ittage.scala:103-112`。建议代码：

```scala
private val s3_provided = RegEnable(s2_provided, s2_fire)

// 放在 RegEnable 连接之后，last-connect 使 contextFlush 优先级最高
if (HasBpuFlush) {
  when(contextFlush) {
    s3_provided := false.B
  }
}

// 其余 S3 payload 不增加 context reset
private val s3_ittageTarget = RegEnable(s2_ittageTarget, s2_fire)
private val s3_provider     = RegEnable(s2_provider, s2_fire)
// s3_altProvided/s3_altProvider/counter/useful/target 同样保持原结构
```

Table 内 `s1_valid` 的窗口排空代码已在第 4.1.3 节给出。`s1_setIdx/s1_tag/s1_bankMask` 不清零。

#### 4.5.3 `bpuFlushing` 期间的污染防控

**[时序修改：门控流水推进、请求和有效输出]**

源码位置（待修改）：`Ittage.scala:59-65, 191-195, 265-294`。建议代码：

```scala
private val s0_fire = io.stageCtrl.s0_fire && io.enable && (if (HasBpuFlush) !bpuFlushing else true.B)
private val s1_fire = io.stageCtrl.s1_fire && io.enable && (if (HasBpuFlush) !bpuFlushing else true.B)
private val s2_fire = io.stageCtrl.s2_fire && io.enable && (if (HasBpuFlush) !bpuFlushing else true.B)
private val s3_fire = io.stageCtrl.s3_fire && io.enable && (if (HasBpuFlush) !bpuFlushing else true.B)

tables.foreach { table =>
  table.io.req.valid           := s1_fire && s1_isIndirect
  table.io.req.bits.startPc    := s1_startPc
  table.io.req.bits.foldedHist := io.s1_foldedPhr
}

io.prediction.hit := s3_fire && s3_provided
io.prediction.target := s3_ittageTarget

ittageMeta.valid := s3_fire
io.meta := ittageMeta
```

只门控 `prediction.hit` 和 `ittageMeta.valid` 即可；`prediction.target` 和 meta payload 在 valid 为 false 时无需置零。若验证环境要求无效 payload 也为零，可以额外组合覆盖，但该操作不增加 target 注入防护能力。

---

### 4.6 顶层训练流水线刷新方案

#### 4.6.1 IO 接口与信号传递（包括门控）

`io.train/trainFoldedPhr/meta` 经 `t0_fire` 进入 T1，T1 组合产生 `updateMask` 等更新意图，再经 T2 寄存器送到各 `IttageTable.io.update`。T1 更新令牌和 T2 `update.valid` 都是每拍更新的 `RegNext`，由 `bpuFlushing` 把其输入压低即可在触发边沿排空；`resetUsefulCnt` 只需在窗口内关闭输出。这些状态都不需要额外的 `contextFlush` 复位，T1/T2 payload 和 `tickCnt` 也不清零。

**[时序修改：收紧 T0 捕获使能，不增加流水级]**

`io.trainReady` 在刷新窗口内保持为 true，`t0_fire` 使用 `bpuFlushing` 门控，使输入训练包被消费但不进入 T1：

源码位置（待修改）：`src/main/scala/xiangshan/frontend/bpu/ittage/Ittage.scala:56-61, 117`。

```scala
io.trainReady := true.B

private val t0_fire =
  io.enable &&
  io.stageCtrl.t0_fire &&
  (if (HasBpuFlush) !bpuFlushing else true.B)
```

#### 4.6.2 存储结构的刷新

按“T1 更新授权 → T2 table update 授权”的顺序清除 valid。所有 payload 都必须由对应 valid 支配。

训练流水线没有使用 `contextFlush` 显式复位宽 payload。刷新窗口内失去对外授权的 valid/control 状态为：

```text
updateValid 内部的 RegNext(t0_fire)
tables(i).io.update.valid 内部的 RegNext(updateMask(i))
tables(i).io.update.resetUsefulCnt 内部的 RegNext(updateResetUsefulCnt)
```

其中 `RegNext(t0_fire)` 和 `RegNext(updateMask(i))` 在刷新触发边沿自然排空。`RegNext(updateResetUsefulCnt)` 可能因旧 `tickCnt` 在首拍产生 useful 扫描脉冲，但它不承载 target，且对外输出在整个窗口内被门控，因此不要求在触发边沿清零其内部寄存器。

**T1 训练寄存器**

**[组合修改：用刷新窗口排空 T1 更新有效令牌]**

`t1_train/t1_meta/t1_trainFoldedPhr` 保持当前 `RegEnable`，不增加 context reset。原 `RegNext(t0_fire)` 保持每拍更新；由于 `t0_fire` 已包含基于内部 `bpuFlushing` 别名的门控，它会在刷新触发边沿自然写入 false：

源码位置（待修改）：`Ittage.scala:117-168`。建议代码：

```scala
private val updateValid =
  hasTrainBranch &&
  RegNext(t0_fire, init = false.B) &&
  (if (HasBpuFlush) !bpuFlushing else true.B)
```

`updateValid` 仍保留源码中的名称和结构；其中匿名 `RegNext(t0_fire)` 是真正的 T1 有效令牌寄存器，不新增中间信号。

`updateMask/updateAlloc/RegionWays.writeValid/tickCnt` 等所有 T1 副作用必须以 `updateValid` 为前提。这样 T1 payload 即使保留旧 target，也不能生成更新。

**T2 table update 寄存器**

**[组合修改：刷新窗口内关闭 T2 update 输出]**

每张表的 T2 valid 保持源码中的匿名 `RegNext` 结构，不新增中间信号。`updateMask(i)` 由已门控的 `updateValid` 生成，刷新触发边沿会自然使 `RegNext(updateMask(i))` 排空；输出端通过内部 `bpuFlushing` 别名叠加门控以保持接口静默。`resetUsefulCnt` 不承载 target，但同样在输出端门控，避免刷新期间启动无效扫描。其他 T2 `RegEnable` payload 保持原状。

源码位置（待修改）：`Ittage.scala:401-413`。建议代码：

```scala
for (i <- 0 until NumTables) {
  tables(i).io.update.valid :=
    RegNext(updateMask(i), init = false.B) &&
      (if (HasBpuFlush) !bpuFlushing else true.B)
  tables(i).io.update.resetUsefulCnt :=
    RegNext(updateResetUsefulCnt, init = false.B) &&
      (if (HasBpuFlush) !bpuFlushing else true.B)

  tables(i).io.update.correct         := RegEnable(updateCorrect(i), updateMask(i))
  tables(i).io.update.alloc           := RegEnable(updateAlloc(i), updateMask(i))
  tables(i).io.update.oldCnt          := RegEnable(updateOldCnt(i), updateMask(i))
  tables(i).io.update.targetOffset    := RegEnable(updateTargetOffset(i), updateMask(i))
  tables(i).io.update.oldTargetOffset := RegEnable(updateOldTargetOffset(i), updateMask(i))
  tables(i).io.update.usefulCntValid  := RegEnable(updateUsefulCntMask(i), false.B, updateMask(i))
  tables(i).io.update.usefulCnt       := RegEnable(updateUsefulCnt(i), updateMask(i))
  tables(i).io.update.startPc         := RegEnable(updateStartPc, updateMask(i))
  tables(i).io.update.foldedHist      := RegEnable(updateFoldedPhr, updateMask(i))
}
```

这里的安全不变量是：任何 T2 payload 进入 WriteBuffer 时，`tables(i).io.update.valid` 必须为真。该接口 valid 和 WriteBuffer 接收端门控共同保证刷新窗口内不会提交训练写。

#### 4.6.3 `bpuFlushing` 期间的污染防控

**[时序修改：所有副作用由 valid 和刷新窗口共同授权]**

T-1 拍形成的 update 可能在 T 拍到达 table。该请求已经在 IttageTable 的 WriteBuffer 入队端被以下条件阻断：

```scala
writePort.valid :=
  ((io.update.valid && updateBankMask(bankIdx)) || usefulCanReset) &&
  (if (HasBpuFlush) !bpuFlushing else true.B)
```

因此，对于“旧 target 不得写入 SRAM”这一目标，WriteBuffer 入队门控是 table update 路径的最终安全执行点；顶层 `tables(i).io.update.valid` 再与内部 `bpuFlushing` 别名相与属于发送端重复门控。方案保留发送端门控，使接口在刷新窗口保持静默，并与 RegionWays 这条不经过 WriteBuffer 的更新路径采用一致规则：

源码位置（待修改）：`src/main/scala/xiangshan/frontend/bpu/ittage/Ittage.scala:168-178, 401-413`、`src/main/scala/xiangshan/frontend/bpu/ittage/RegionWays.scala:82-90`。

```scala
private val updateValid =
  hasTrainBranch &&
  RegNext(t0_fire, init = false.B) &&
  (if (HasBpuFlush) !bpuFlushing else true.B)

// updateMask/updateAlloc 的赋值只存在于 when(updateValid) 内。
rTable.io.writeValid :=
  updateValid &&
  !updateRealUsePCRegion &&
  updateAlloc.reduce(_ || _) &&
  (if (HasBpuFlush) !bpuFlushing else true.B)
```

各副作用路径的门控层次如下：

源码位置：`src/main/scala/xiangshan/frontend/bpu/ittage/Ittage.scala:318, 401-413`、`src/main/scala/xiangshan/frontend/bpu/ittage/IttageTable.scala:183-228`、`src/main/scala/xiangshan/frontend/bpu/ittage/RegionWays.scala:82-90`。

```text
tables(i).io.update.valid
tables(i).io.update.resetUsefulCnt
WriteBuffer 入队             // table update 的最终接收门控
WriteBuffer 出队/SRAM 写回   // 防止刷新前已入队请求提交
RegionWays 写入              // 不经过 WriteBuffer，必须独立门控
```

`usefulCanReset` 和两类 replacer touch 不需要作为 target 注入安全状态单独清零；WriteBuffer 的正常 touch 会随入队门控自然停止，RegionWays 的正常 touch 会随 `writeValid` 停止。
---

### 4.7 统一完成状态与时序汇总

各模块不再分别设置“完成状态与时序”小节。本节统一说明局部完成条件、是否产生独立完成信号、刷新耗时和对原流水级的影响。

实际清零、在刷新触发边沿排空，或在窗口内失去输出授权的状态如下：

| 模块 | 状态名称 | 处理方式 |
| --- | --- | --- |
| IttageTable SRAM | 每个 bank、每个 row 的完整 `IttageEntry`：`valid/tag/confidenceCnt/targetOffset/usefulCnt/paddingBit` | `extraReset` 逐行写零；安全关键字段是 `valid` |
| WriteBuffer | `needWrite/valids/entries` 和内部 replacer 状态 | 完整复用 `hasContextFlush` 清零 |
| RegionWays | `regions(i).valid`，`i = 0 .. RegionNums-1` | `contextFlush` 显式清零；`regions(i).region` 不清 |
| Table 响应流水 | `s1_valid` | `io.req.valid = false` 后在触发边沿自然写零 |
| 顶层预测流水 | `s3_provided` | `contextFlush` 显式清零 |
| T1 训练流水 | `updateValid` 内部的匿名 `RegNext(t0_fire)` | `t0_fire = false` 后在触发边沿自然写零 |
| T2 训练流水 | `tables(i).io.update.valid/resetUsefulCnt` 内部的匿名 `RegNext` | `update.valid` 的寄存器在触发边沿排空；`resetUsefulCnt` 只要求窗口内关闭输出 |

除此之外的预测和训练 payload 寄存器保持原结构，不增加 context reset。

| 模块或修改点 | 状态更新与完成条件 | 独立完成信号 | 局部耗时 | 对原流水级的影响 | 需要保证的性质 |
| --- | --- | :---: | --- | --- | --- |
| IttageTable SRAM `extraReset` | 每个 bank 按物理 row 写 0；本 table 等待所有 bank `resetDone` | 有：逐级聚合到 `io.sramResetDone` | 128 个物理 row 写周期；端到端精确拍数待仿真 | 新增 SRAM 内部多拍 reset 过程，不增加预测流水级 | `contextFlush` 只启动一次；清零期间 done 为低，所有 table 和 bank 并行刷新 |
| WriteBuffer 状态 | `contextFlush` 经 `hasContextFlush` 清全部缓冲状态 | 无 | 1 cycle，包含在 SRAM 窗口内 | 复用已有方案，不增加流水级 | 安全关键状态是 `needWrite`；入队、出队和 SRAM 写使能在窗口内为低 |
| useful 数据和老化控制 | 不执行独立安全刷新；entry useful 随整行 `extraReset` 附带清零 | 无 | 不增加独立耗时 | 保持原 `Counter` | useful-only 写不能修改 valid/tag/target |
| RegionWays valid | `contextFlush` 边沿只清 `regions.valid` | 无 | 1 cycle | 改变 valid 写优先级，不增加流水级 | region payload 和 PLRU 不清；窗口内禁止写入 |
| 顶层 `resetDone` | `if (HasBpuFlush) { io.resetDone.get := !contextFlush && io.sramResetDone }` | 有：`io.resetDone.get` | SRAM 完成后直接报告 | 不新增完成握手寄存器，不改变预测和训练级数 | `contextFlush` 当拍强制为低；`resetDone` 不依赖 `bpuFlushing` |
| S1/S2/S3 预测流水 | table `s1_valid` 在首个边沿排空；显式清顶层 `s3_provided` | 无 | 1 cycle | 只为保持型 S3 valid 增加清零 | payload 不清；窗口内阻断推进和有效输出 |
| T0/T1/T2 训练流水 | `bpuFlushing` 使 RegNext 输入为 false，首个边沿排空 update valid | 无 | 1 cycle | 不增加独立 reset | payload 和 `tickCnt` 不清；所有副作用受 valid 支配 |
| req/update/write valid 和最终 output valid | 在 `bpuFlushing` 窗口内保持阻断；table resp 依靠下游 `s2_fire` 阻断 | 无 | 跟随 BPU 整体刷新窗口 | 仅增加组合门控，不增加寄存器级 | 检查 `bpuFlushing` 扇出和组合时序，并确保所有状态改变路径均被覆盖 |

实现后必须做三类验证：

1. 定向仿真检查 Entry/Region valid、WriteBuffer `needWrite`、S3 prediction valid 的清零优先级，table/T1/T2 target-update valid 在触发边沿的排空、`resetUsefulCnt` 在窗口内无效，以及 `bpuFlushing` 窗口内无 target 输出和写回。
2. 注入非零旧 payload，证明对应 valid 清零后旧 target 无法到达 `prediction.target`、RegionWays 写口、WriteBuffer 入队口和 SRAM 写口。
3. 综合/时序检查 `bpuFlushing` 扇出；本方案不向宽 T1/T2 payload 增加 reset，应避免由宽 Bundle reset 引入的面积和时序代价。

### 4.8 `HasBpuFlush` 裁剪清单与验收

`HasBpuFlush=false` 时，以下仅服务于 ITTAGE 上下文刷新的对象必须不参与 elaboration 或在常量传播后从生成 RTL 消失：

| 范围 | 应消失对象 | 关闭配置保留对象 |
| --- | --- | --- |
| `Ittage` 顶层 | `contextFlush`/`bpuFlushing`/`resetDone` 端口消费、S3 `s3_provided` 清零块、刷新窗口的预测/训练/RegionWays 写门控 | 正常 S0-S3/T0-T2 流水线、`sramResetDone` 汇总 |
| `IttageTable` | 两个可选刷新端口、SRAM `extra_reset` 端口及 runtime reset 状态机、WriteBuffer `contextFlush` 端口和清空逻辑 | 上电 `shouldReset`、正常读写、useful 老化和 WriteBuffer 基本功能 |
| `RegionWays` | 可选 `contextFlush` 端口和 `regions.valid` 清零块 | 正常 region 读写和 replacer 状态更新 |
| 既有数据通路 | 由 `bpuFlushing` 增加的读请求、入队/出队、SRAM 写、预测输出和训练更新门控 | 刷新引入前的原始表达式；关闭分支的 `&& true.B` 必须由 Chisel/CIRCT 折叠 |

验收必须分别完成 `HasBpuFlush=true` 和 `HasBpuFlush=false` 的 elaboration。关闭配置不得出现 `None.get`、未连接端口或 FIRRTL 错误；生成 RTL 中不得出现 ITTAGE 刷新专用端口、`extra_reset`、WriteBuffer context-flush 清空、`s3_provided` 刷新清零或新增的刷新门控。`io.sramResetDone` 和 SRAM 上电 reset 属于原有基础设施，关闭配置必须保留。

---

## 5. 应用刷新机制之后的预测流水线

假设 `contextFlush` 在 T 拍有效。

默认 `TableSramSize = 128`。`FoldedSRAMTemplate` 的 reset counter 从 `resetSet = 0` 扫到 `127`，每拍写一个物理 row；5 张表、每表 2 个 bank 均并行执行，因此 ITTAGE SRAM 清零固定为 **128 个写拍**，而不是 `5 × 2 × 128` 拍。

| 时刻 | SRAM reset 状态 | 当拍完成的操作 | `resetDone` |
| --- | --- | --- | --- |
| T | `contextFlush = 1`，触发 `_resetState := true` | 清 WriteBuffer `needWrite`、`regions.valid` 和 S3 valid；table/T1/T2 target-update valid 在此边沿排空，useful 控制输出被门控；所有业务路径被阻断 | 由 `!contextFlush` 强制为 0 |
| T+1 | `resetSet = 0` | 写零第 0 个物理 row | 0 |
| T+2 ～ T+127 | `resetSet = 1 ～ 126` | 每拍写零一个物理 row | 0 |
| T+128 | `resetSet = 127` | 写零最后一个物理 row；`Counter` 完成并清除 `_resetState` | 所有 bank done 在此边沿后为 1，ITTAGE `resetDone` 变为 1 |
| T+129 起 | SRAM 已完成 | 若其他预测器未完成，仍由 `bpuFlushing` 阻断；否则恢复正常工作 | ITTAGE 保持 1 |

因此，从 `contextFlush` 触发边沿到 ITTAGE 本地 `resetDone` 重新为 1 的延迟为 **128 个周期**；其中 SRAM 实际写零发生在 T+1 至 T+128 的 128 拍。BPU 整体 `bpuFlushing` 的结束时间还取决于其他预测器的 `resetDone` 聚合，不能只由 ITTAGE 固定。

源码依据：`utility/sram/SRAMTemplate.scala:274-287, 305-338, 495-499` 的 `_resetState/resetSet/Counter` 与 reset 写端口逻辑；`IttageTable.scala:81, 133-152` 的 `foldedWidth`、bank 实例和完成信号聚合。

---

## 6. 刷新耗时总结

一拍清零项都被 SRAM 多拍 reset 覆盖，整体临界路径是 SRAM。`FoldedSRAMTemplate extraReset` 按折叠后的物理 row 扫描；所有 table/bank 并行，因此 256-entry 与 512-entry 表的 SRAM 主体清零均为 **128 个物理 row 写周期**，本地 `contextFlush → resetDone` 延迟也为 **128 个周期**。useful 老化的逻辑 set 扫描是另一条路径，不计入本次 reset。BPU 整体窗口的实际结束拍数仍取决于所有预测器。
