# 08-TAGE设计分析与刷新方案

> 本文档是 TAGE（`Tage`）的**真实刷新方案**，属于 [01-BPU刷新方案概览](01-BPU刷新方案概览.md) §6.1 占位阶段之后的落地内容：实现时用 §4.6 的真实完成条件替换 `Tage.scala` 中的 `io.resetDone.get := true.B` 恒真占位。
>
> 1. **接口契约**：`contextFlush`/`bpuFlushing`/`resetDone` 由 `BasePredictorIO` 以 `Option.when(HasBpuFlush)` 声明（01 §5.1.3），BPU 顶层已按本事务 `activeFlushMask` 门控后下发（01 §5.3.3）。TAGE 对应 `tageFlushEnable`，因此收到的均为针对自身的有效信号。
> 2. **编译开关**：本文所有刷新端口、`extraReset` 绑定、完成归约、写缓冲清空、流水残留清理、预测读请求/训练/写缓冲入队门控、读响应零化及 `resetDone` 生成，均遵循 [00-编译开关实现方案](00-BPU刷新机制编译开关实现方案.md) §3.1 和 01 §6.2，受 elaboration-time `HasBpuFlush` 统一裁剪。
> 3. **编码规则**：可选端口用 `Option.when(HasBpuFlush)`；端口连线、寄存器、`when` 和完成逻辑放入 Scala `if (HasBpuFlush)`；修改既有数据通路时必须提供恢复原表达式的 `else` 分支。禁止用 `getOrElse(false.B)` 将可选刷新端口变成常驻硬件后仅依赖综合优化。
> 4. **关闭语义**：`HasBpuFlush=false` 时，TAGE 刷新专用端口和消费逻辑不参与 elaboration，原有周期性 useful reset 等共享基础设施保持不变；完整裁剪清单见 §4.7。

## 1. TAGE 的预测流水线

TAGE 顶层模块为 `Tage`，位于 `src/main/scala/xiangshan/frontend/bpu/tage`。TAGE 是 mBTB 条件分支预测结果的方向修正器：

- mBTB 提供候选分支、基础 taken 结果和分支位置
- TAGE 根据 PC 和折叠历史读取多张带 tag 的历史表，随后对每个 mBTB 候选分支结合 `cfiPosition` 计算 tag 并比较命中
- 若命中 provider，则用命中表中历史最长表的 counter 修正基础预测
- 若没有 provider，则下游继续使用 mBTB/base 预测

### 第一拍：S0 读表

S0 根据 `startPc` 和 `foldedPathHist` 为每张 TAGE 表计算 set index，并根据 PC 计算 bank。

默认配置：

```text
8 张表；每张表 Size = 4096；每张表 4 bank × 2 way；每个 bank/way 深度 = 4096 / 4 / 2 = 512 行
```

每张表会并行读一个 bank，读出的内容包括：

- `entrySram`：保存 `valid/tag/takenCtr`
- `usefulCtrSram`：保存 useful counter

### 第二拍：S1 读回并计算 tag

S1 保存 S0 的 PC 和历史信息，并接收各表 SRAM 读回的数据。TAGE 对每个 mBTB 候选条件分支单独计算 tag。tag 与 `cfiPosition` 有关，因此同一个取指块内不同条件分支会对应不同 tag。

### 第三拍：S2 选择 provider / alternate

S2 对每个 mBTB 候选分支执行：

1. 在每张表内比较 `entry.valid && entry.tag === tag`
2. 多张表命中时，选择历史最长的表作为 provider
3. 次长命中的表作为 alternate
4. 若 `useAltOnNa` 生效且 provider counter 较弱，则使用 alternate/base

输出包括：

- `io.prediction.takenVec(i).valid`：当前分支位置是否有可用的 TAGE 方向结果，实际赋值为 `useProvider || hasAlt`
- `io.prediction.takenVec(i).bits`：使用 provider 时输出 provider counter 方向，否则输出 alternate counter 方向
- `io.toSc.providerTakenCtrVec(i)`：向 SC 输出 provider counter，仅在存在 provider 且对应 mBTB branch 有效时置 `valid`
- `io.meta.entries(i)`：保存 `useProvider`、provider 的 table/way/counter/useful 以及 `altOrBasePred`，供后续训练使用

`io.prediction` 不直接暴露 provider/alternate 的分项结果，而是每个分支位置输出一个 `Valid[Bool]`。BPU 顶层将 `takenVec(i).valid` 转换为 `useTage`；对有效条件分支，方向选择优先级为 SC、TAGE、mBTB/base，因此 TAGE 结果只在未被 SC 覆盖且 `takenVec(i).valid` 为真时使用。`io.meta.entries` 没有独立 valid，其训练有效性由 mBTB 命中、分支类型和训练流水控制共同决定。

## 2. TAGE 的训练流水线

TAGE 训练从 T0 到 T3，共四拍。

### T0：判断是否需要读表

T0 根据训练请求、mBTB meta、TAGE meta 判断是否需要重新读 TAGE 表。对所有 mBTB 命中的条件分支，只有当预测时使用了 provider 且本次没有 mispredict 时，`t0_useMeta` 才保持为真。

如果上述所有相关分支都可以直接使用预测时保存的 meta 训练，则不重新读表；否则向每张 TAGE 表的训练读端口发读请求。

TAGE 表有两个读来源：

- 预测读：`readReq(0)`
- 训练读：`readReq(1)`

同 bank 同周期只能允许一个读请求，因此 T0 在“有条件分支 + 需要重读 + 预测侧 `s0_fire` + 训练/预测 bank 相同”时置位 `t0_readBankConflict`，并以 `io.trainReady := !t0_readBankConflict` 反压训练。

### T1 / T2：生成训练信息

T1 保存训练请求、set index、bank mask、历史信息和读回数据。T2 生成每个条件分支的 `TrainInfo`。如果 `t2_useMeta` 为真，源码直接使用预测时保存的 TAGE meta 训练；否则才根据训练读回的 SRAM 数据重新找 provider / alternate。`TrainInfo` 包括：

- provider 是否存在
- provider/alternate counter 是否需要更新
- useful 是否需要更新
- 是否需要分配新 entry
- 是否更新 `useAltOnNa`

### T3：生成写请求

T3 根据 `TrainInfo` 生成每张表的 `TableWriteReq`。

写请求可能包含：

- 更新 provider taken counter
- 更新 provider useful counter
- 更新 alternate taken counter
- 分配新 entry

TAGE 表内部不会直接写 SRAM，而是先进入写缓冲：

- `entryWriteBuffers`：缓存 entry SRAM 写请求
- `usefulCtrWriteBuffers`：缓存 useful counter SRAM 写请求

写缓冲后续在 SRAM 读端口空闲时再写回 SRAM。

### useful 周期性清零

当某个分支需要分配新 entry，但源码选出的候选表/way 集合为空时，`t3_canAllocate` 为 false；在 `t3_fire && t3_needAllocate && !t3_canAllocate && !usefulResetInFlight` 时 `usefulResetCtr` 增加。源码的 way 候选优先级是 invalid 优先，其次 weak 且 useful=0，最后 useful=0。这里的候选表集合不是固定所有表：如果已有 provider，只考虑比 provider 历史更长的表；如果没有 provider，才考虑所有表。当计数器已饱和、当拍 `t3_fire` 且当前没有 useful reset 在进行时，`t3_usefulResetStart` 触发，每张 TAGE 表逐 set 把 useful counter 写成 0。

这套机制的实际实现分为两层：

- `Tage.scala`（[Tage.scala:522](../../../src/main/scala/xiangshan/frontend/bpu/tage/Tage.scala#L522)）生成 `t3_usefulResetStart`，并在 [Tage.scala:584](../../../src/main/scala/xiangshan/frontend/bpu/tage/Tage.scala#L584) 附近将脉冲广播给所有表、置位顶层 `usefulResetInFlight` 并清零 `usefulResetCtr`；所有表清零完成后顶层 in-flight 清零。
- `TageTable.scala`（[TageTable.scala:83](../../../src/main/scala/xiangshan/frontend/bpu/tage/TageTable.scala#L83)）为每个 bank 保存 `usefulResetInFlightMask` 和 `usefulResetSetIdx`。清零期间关闭该 bank 的 useful SRAM 读，两个 way 每周期向当前 set 写入 `UsefulCounter.Zero`，只有 SRAM 写请求真正 `fire` 后才递增 set index；所有 bank 完成后 `io.usefulResetInFlight` 才拉低。清零期间发出的读请求，其返回的 useful counter 被强制为 0，但 entry SRAM 仍可读。

因此，**可以复用这套逐 set 写零过程作为 context flush 的 useful SRAM 清零阶段**，但不能只复用原有的饱和计数器触发条件：context flush 需要额外把 `contextFlush` 接入同一个 `usefulResetStart` 触发路径，并把所有表的 `usefulResetInFlight` 纳入 `resetDone` 完成握手。它仍然不等同于完整 context flush；entry SRAM、两类写缓冲、顶层策略寄存器和流水线残留数据必须单独处理，刷新期间也必须阻断训练写入。

## 3. TAGE 的整体架构

本节只列 TAGE 内部需要刷新关注的存储结构，不展开读写请求、预测输出和 meta 这类接口结构。

### 3.1 Entry SRAM

**Entry SRAM 的数据结构**

从逻辑组织看，一共有 8 张 TAGE 表。每张表包含 4 个 bank，每个 bank 有 512 个 set，每个 set 有 2 个 way，因此每张表共有 `4 × 512 × 2 = 4096` 个 entry。

从 SRAM 实例看，**每个 bank 的两个 way 分别由两个独立的 1-way SRAM 存储**，因此每张表共有 `4 bank × 2 way = 8` 个 Entry SRAM。每个 SRAM 有 512 行，第 `s` 行保存逻辑 set `s` 的其中一个 way；同一逻辑 set 的两个 entry 分别位于两个 way SRAM 的同一行。每行保存一个 **17-bit** `TageEntry`。

```text
TageEntry (17 bits)：
	├── valid: Bool — 1 bit 表项是否有效；
	├── tag: UInt(13) — 13 bits 历史 + PC 生成的 tag；
	└── takenCtr: SaturateCounter(3) — 3 bits 方向饱和计数器
```

Entry SRAM 是单端口 SRAM，实例化时 `shouldReset = true`，当前未启用运行时 `extraReset`。

### 3.2 usefulCtrSram

**usefulCtrSram 的数据结构**

`useful` 不在 `TageEntry` 中，而是独立保存在 `usefulCtrSram`。**每一个 Entry SRAM 表项都对应一个 useful counter**，因此 usefulCtrSram 也是一共有 8 张表，每张表有 4096 项 UsefulCounter，每项 UsefulCounter 都占 **2-bit**：

```text
UsefulCounter (2 bits)：
	└── usefulCtr: SaturateCounter(2) — 2 bits 替换/分配有用性计数器
```

源码中 useful SRAM 使用 `FoldedSRAMTemplate`，因此**其物理结构与 Entry SRAM 略有不同**：

- Entry SRAM 的物理结构是 8 张表，每张表 4 个 bank，每个 bank 有 2 个 SRAM，每个 SRAM 包括 512 物理行，每一行表示一个 entry。
- usefulCtrSram 的物理结构是 8 张表，每张表 4 个 bank，每个 bank 有 2 个 SRAM，每个 SRAM 包括 64 物理行，每一行中有 8 个 useful couter。

### 3.3 entryWriteBuffers

**entryWriteBuffers 的数据结构**

每张 TAGE 表的每个 bank 有 1 个 `WriteBuffer[EntrySramWriteReq]`，因此每张表有 4 个，8 张表共有 `8 × 4 = 32` 个 entry write buffer。每个 buffer 以 `numPorts = NumWays = 2`、`numEntries = WriteBufferSize = 4` 实例化；内部每个写端口对应一行 4 槽状态，因此单个 buffer 共有 `2 × 4` 个 entry 槽。

写缓冲项为 `EntrySramWriteReq`：

```text
EntrySramWriteReq (26 bits)：
	├── setIdx: UInt(9) — 9 bits 写回 set；
	└── entry: TageEntry — 17 bits 写回 entry
```

单个 bank 的 entry write buffer 内部核心状态：

```text
entries: 2 row × 4 entry × 26 bits；
dirty: 2 row × 4 entry × 1 bit；
shadowValid: 2 row × 4 entry × 1 bit；
PLRU state: 2 row × (4 - 1) bits
```

`dirty` 表示该槽仍需写回 SRAM；`shadowValid` 表示 `entries` 中的 shadow 数据可参与同 set/tag 命中与合并，即使对应 `dirty` 已在写回后清零。每行还有一个 4-way `PseudoLRU`：当没有同 set 位置可优先覆盖且所有槽都处于 dirty 时，用它选择 victim。如果刷新只清 SRAM、不清 entry write buffer，旧 `dirty` entry 可能在刷新后继续写回 SRAM，旧 `shadowValid/entries` 也可能参与后续同 set/tag 的合并判断。

### 3.4 usefulCtrWriteBuffers

**usefulCtrWriteBuffers 的数据结构**

每张 TAGE 表的每个 bank/way 有 1 个 useful 写队列：

```text
每张表：4 bank × 2 way = 8 个 Queue[UsefulCtrSramWriteReq]
8 张表：8 × 4 × 2 = 64 个 Queue[UsefulCtrSramWriteReq]
每个 Queue 深度 = 4
```

队列项为 `UsefulCtrSramWriteReq`：

```text
UsefulCtrSramWriteReq (11 bits)：├── setIdx: UInt(9) — 9 bits 写回 set；└── usefulCtr: SaturateCounter(2) — 2 bits 写回 useful counter
```

如果刷新只清 useful SRAM、不清 useful 写队列，旧 useful 更新可能在刷新后重新写回。

### 3.5 顶层寄存器状态

TAGE 顶层还有几类由训练历史累积出来的状态：

| 状态                    | 类型                            |    默认 bit 数 | 作用                                      |
| ----------------------- | ------------------------------- | -------------: | ----------------------------------------- |
| `useAltOnNaVec`       | `Vec(128, UseAltOnNaCounter)` | 128 × 7 = 896 | 控制弱 provider 时是否使用 alternate/base |
| `usefulResetCtr`      | `UsefulResetCounter`          |              8 | useful reset 触发计数器                   |
| `usefulResetInFlight` | `Bool`                        |              1 | 顶层 useful reset 是否正在进行            |

每个 `TageTable` 内部还有 useful reset 清零状态：

| 状态                        | 类型                                   | 默认 bit 数 | 作用                            |
| --------------------------- | -------------------------------------- | ----------: | ------------------------------- |
| `usefulResetInFlightMask` | `Vec(NumBanks, Bool)`                |           4 | 每个 bank 是否处于 useful reset |
| `usefulResetSetIdx`       | `Vec(NumBanks, UInt(SetIdxWidth.W))` | 4 × 9 = 36 | 每个 bank 当前 reset 到哪个 set |

实际代码中，`useAltOnNaVec` 由 T3 训练更新，`usefulResetCtr`/`usefulResetInFlight` 与表内两类清零进度寄存器共同管理周期性 useful reset。后续 context flush 方案需使两个策略 counter 恢复初值，并使表级 useful 清零从 set 0 重新启动、保持 busy 直到清零完成；顶层 `usefulResetInFlight` 保留原有周期性清零语义。

---

## 4. 刷新方案

### 4.1 Entry SRAM

#### 4.1.1 I/O 接口修改

TAGE 是 **SRAM 型**子预测器，其多拍清零与全窗口阻断时序遵循 01 §2.3.1、§5.4。`Tage` 顶层复用 `BasePredictorIO` 已按 `Option.when(HasBpuFlush)` 声明的三个握手端口，**无需新增**：

| 端口                    | 方向 / 类型                                     | 作用                                           |
| :---------------------- | :---------------------------------------------- | :--------------------------------------------- |
| `io.contextFlush.get` | `Option[Bool]`（Input），1-cycle 脉冲         | 触发存储结构和寄存器清零                       |
| `io.bpuFlushing.get`  | `Option[Bool]`（Input），T ~ BPU 聚合 `resetDone` 使状态机迁出 `s_flushing` | 门控预测读请求、写缓冲入队和训练，选择零读响应 |
| `io.resetDone.get`    | `Option[Bool]`（Output）                      | 表示 TAGE 自身清零完成                         |

`Tage` 模块体顶部统一解包输入端口：

```scala
// Tage.scala -- false.B 仅作 Scala 分支的类型占位，关闭时不生成刷新端口或逻辑。
private val contextFlush = if (HasBpuFlush) io.contextFlush.get else false.B
private val bpuFlushing  = if (HasBpuFlush) io.bpuFlushing.get  else false.B
```

`TageTable` 增加两个刷新输入，并增加一个只表示 Entry SRAM 清零完成的输出：

```scala
// TageTable.scala
class TageTableIO extends TageBundle {
  ... // 原有端口
  val contextFlush: Option[Bool] = Option.when(HasBpuFlush)(Input(Bool()))
  val bpuFlushing:  Option[Bool] = Option.when(HasBpuFlush)(Input(Bool()))
  // 仅归约 Entry SRAM 的 resetDone；与既有 sramResetDone 分离。
  val entryResetDone: Option[Bool] = Option.when(HasBpuFlush)(Output(Bool()))
}

private val contextFlush = if (HasBpuFlush) io.contextFlush.get else false.B
private val bpuFlushing  = if (HasBpuFlush) io.bpuFlushing.get  else false.B

// Tage.scala：直接向各表下发 BPU 整体刷新窗口，不新增 TAGE 本地状态寄存器。
if (HasBpuFlush) {
  tables.foreach { table =>
    table.io.contextFlush.get := contextFlush
    table.io.bpuFlushing.get  := bpuFlushing
  }
}
```

#### 4.1.2 清空 Entry SRAM

provider 命中依赖 `TageEntry.valid && tagHit`，故刷新关键语义是**清除所有 entry 的 valid 位**。`valid/tag/takenCtr` 同存一个 SRAM entry、无独立 valid 写端口，不能像寄存器数组 1 拍只清 valid，故**用 `SRAMTemplate extraReset` 逐行写 0**。在 `HasBpuFlush=true` 时为 `entrySram` 打开 `extraReset`，由 `contextFlush` 触发逐行写 0：

```scala
// TageTable.scala
private val entrySram = Seq.tabulate(NumBanks, NumWays) { (bankIdx, wayIdx) =>
  Module(new SRAMTemplate(
    new TageEntry,
    set = NumSets,
    way = 1,
    singlePort = true,
    shouldReset = true,
    extraReset = HasBpuFlush,
    ...
  ))
}

// 放置位置：entrySram 定义之后、usefulCtrSram 定义之前。
if (HasBpuFlush) {
  entrySram.flatten.foreach { sram =>
    sram.extra_reset.get := contextFlush
  }

  // context flush 只使用 Entry SRAM 的完成状态。
  io.entryResetDone.get := entrySram.flatten.map(_.io.resetDone).reduce(_ && _)
}
```

默认配置下，各 bank/way SRAM 并行清零，每个 SRAM 的逐行清零耗时为 512 个时钟周期。

既有 `io.sramResetDone` 继续保持实际代码的原语义：归约本表全部 Entry SRAM 和 useful SRAM 的原生 `resetDone`，供上电 SRAM 初始化完成判断使用。context flush 不复用该混合信号，而使用新增的 `io.entryResetDone.get`，避免把 useful SRAM 的原生 reset 状态混入 Entry context-reset 完成条件。

#### 4.1.3 刷新期间的读写污染

Entry SRAM 自身复位只负责两件事：一是 `extraReset` 触发后由 `SRAMTemplate` 按 `resetSet` 自主逐行写零；二是清零期间以 `resetHold` 拉低读、写请求的 `ready`，使外部请求不能握手。它不会自动清除 SRAM 外部的预测流水线和写缓冲状态，也不能保证 reset 写模式下的 `io.r.resp.data` 具有合法读响应语义。因此，参考 05 §4.1.3，TAGE 采用以下四层防护，而不是在 `TageTable` 的 Entry SRAM 读端口和写缓冲出队端口额外串接 `bpuFlushing`。

**层面一：清空 Entry SRAM 和 entryWriteBuffers。** §4.1.2 用 `extraReset` 清空 Entry SRAM array；§4.3 在 `contextFlush` 当拍清除 `entryWriteBuffers` 的 `dirty/shadowValid`。前者删除已经写入 array 的旧 entry，后者同时消除尚未写回以及可能参与 shadow 命中的旧 entry。

**层面二：阻止 Entry SRAM 非合法读出数据进入预测路径。** `SRAMTemplate` 在 reset 清零期间不接受读请求，故此时 SRAM 数据输出不能视为有效响应：

* 预测侧在 `Tage` 顶层阻断 table-read 请求、整窗零化 table read response 并清除 S1/S2 旧上下文内容，**具体见 §4.4**；训练读随训练 fire 一并阻断，**具体见 §4.5**。
* 两类请求均在源头关闭，`TageTable` 内部共享的 Entry SRAM 读仲裁保持实际代码原表达式，不重复增加窗口门控。

**层面三：阻止刷新期间重新写入 entryWriteBuffers。** `TageTable` 的 `writeReqValid` 比顶层 `io.writeReq.valid` 延迟一拍，故仅门控训练源头仍可能漏过一拍残留请求。§4.3.3 在 buffer 入队条件上再次使用 `!bpuFlushing`，与 §4.5 的训练流水线阻断机制共同保证 buffer 清空后不再产生新 dirty entry。

**层面四：出队方向不设门控，由后续清零覆盖、复位反压与空 buffer 保证。** Entry buffer 的出队/写回保持实际代码原有的 `valid/ready` 仲裁，不额外增加 `bpuFlushing`。刷新安全依赖 “**触发拍残留写回可覆盖 + 入队门控完备 + 复位反压 + buffer 已清空**” 共同保证：

- **触发拍残留写回可覆盖**：T 拍 `contextFlush` 拉高时，`SRAMTemplate` 内部 `resetState` 要到时钟沿后才生效，因此当拍组合路径上可能仍有一次旧 `bufferOut` 写入 Entry SRAM。该写入发生在 reset 清零开始之前，后续逐行写零一定会覆盖其写入 set。
- **复位期反压**：T+1 起 Entry SRAM 进入 reset 清零态，`SRAMTemplate` 拉低写请求 `ready`，并由 reset 地址、零数据和全 way mask 接管 SRAM array；外部 `setIdx/entry/waymask` 不会写入 array。`bufferOut.ready` 随 SRAM 写端 `ready` 拉低，出队请求不会完成握手。
- **窗口内 buffer 为空**：层面一在 T 拍清除 entryWriteBuffers 的 `dirty/shadowValid`，层面三拦截窗口内的新入队请求。Entry SRAM 写口恢复时，buffer 中不存在旧 dirty entry 可出队，因此不会把已清零的 SRAM 重新写脏。

由此，Entry buffer 无需等待出队排空，也无需提供 `writeBuffersEmpty` 完成接口；`resetDone` 只等待 Entry SRAM 和 useful 清零完成，见 §4.6。

四层防护合并后的完整逐拍时序见 §5。

这种划分与实际 `TageTable`/`SRAMTemplate` 的接口语义一致，也避免在 single-port SRAM 的写缓冲出队侧加入多余门控。`HasBpuFlush=false` 时，上述顶层读请求、读响应零化、buffer 入队和训练门控均不生成，`TageTable` 的 Entry SRAM 读写表达式保持原实现不变。

---

### 4.2 usefulCtrSram

`usefulCtrSram` 独立于 Entry SRAM，直接影响新 entry 分配。若只清 Entry SRAM 不清 useful，则 `entry.valid = 0` 但 useful 仍非 0；虽因分配策略优先 invalid entry 不会阻止分配，但旧 useful 属旧上下文替换状态，会污染新上下文的替换/老化行为。

#### 4.2.1 I/O 接口修改

与 §4.1.1 相同，`usefulCtrSram` 复用 `TageTableIO.contextFlush`，**无需增加独立清零端口**。

#### 4.2.2 清空 usefulCtrSram

直接复用 TAGE 已有 useful reset 的**逐 set 清零和 SRAM 写端口仲裁**，不启用 useful SRAM 的 `extraReset` 端口。context flush 作为新的 `usefulResetStart` 触发源，与原有 `t3_usefulResetStart` 合并：

```scala
// Tage.scala：context flush 触发和训练门控只在开关打开时生成。
private val usefulResetStart = if (HasBpuFlush) {
  contextFlush || (t3_usefulResetStart && !bpuFlushing)
} else {
  t3_usefulResetStart // 逐字恢复原有周期性 useful reset 触发
}

tables.zipWithIndex.foreach { case (table, tableIdx) =>
  ...	// 保持原有设计
  table.io.usefulResetStart := usefulResetStart
}

when(t3_usefulResetStart) {
  usefulResetInFlight := true.B
}.elsewhen(usefulResetInFlight && !tables.map(_.io.usefulResetInFlight).reduce(_ || _)) {
  usefulResetInFlight := false.B
}

when(t3_usefulResetStart) {
  usefulResetCtr.resetZero()
}.elsewhen(t3_fire && t3_needAllocate && !t3_canAllocate && !usefulResetInFlight) {
  usefulResetCtr.selfIncrease()
}
```

> `t3_usefulResetStart` 已由 §4.5 中经 `bpuFlushing` 门控的 `t3_fire` 派生，因此上式中的 `&& !bpuFlushing` 在当前方案下是逻辑冗余的。此处有意保留作为防御性门控，使周期性 useful reset 的 context-flush 隔离不依赖 `t3_usefulResetStart` 内部对 `t3_fire` 的间接约束；即使后续重构该信号的生成条件，刷新窗口内仍不会由训练路径重复触发 useful reset。

复用 useful reset 不只增加上述触发条件，还依赖以下配套设计共同**保证旧 useful 状态不会被读取或重新写回**：

- **清空并隔离** `usefulCtrWriteBuffers`（§4.3.1～§4.3.3、§4.5）：
  - `contextFlush` 当拍清空 `usefulCtrWriteBuffers`。
  - `bpuFlushing` 刷新窗口期禁止普通入队；普通出队/写回保持原 `!usefulResetValid` 仲裁，触发拍旧写回由后续逐 set 写零覆盖。
  - `usefulResetCtr` 和 `useAltOnNaVec` 仍须在 `contextFlush` 时恢复初值。
- **隔离预测路径**（§4.4）：阻断预测 table-read 请求、将 SRAM 读响应置零，并清除 S1/S2 中保存的旧上下文内容。
- **隔离训练路径**（§4.5）：整窗组合屏蔽 T0～T3 有效 fire，清除 T1/T2/T3 payload，且**阻止 `t3_usefulResetStart` 干扰本次 context flush（§4.2.2）**。
- **完成握手**（§4.6）：`resetDone` 同时等待 Entry SRAM `extraReset` 和所有 `table.io.usefulResetInFlight` 结束，不能由 `bpuFlushing` 直接生成。

> **为什么不启用 `extraReset`：**
>
> - `FoldedSRAMTemplate` 虽然支持 `extraReset`，且默认配置下可按 64 个物理行清零 folded useful SRAM，但 TAGE 的 context flush 仍需等待深度为 512 的 Entry SRAM 完成 `extraReset`，因此把 useful 清零从 512 个扫描周期缩短到约 64 个扫描周期不会缩短 TAGE 的总体刷新时间。
> - 与此同时，TAGE 还必须保留原有周期性 useful reset；若再增加一条 context `extraReset` 路径，就需要额外处理两种清零机制重叠时的端口接管、既有 `usefulResetInFlight/usefulResetInFlightMask/usefulResetSetIdx` 状态处置以及完成条件归约。
> - 复用现有 useful reset 则可直接沿用已经存在的逐 set 写零、写端口优先级、读屏蔽、反压推进和完成指示，只需增加 context 触发及其外围隔离，修改范围更小，且与 Entry SRAM 并行执行时仍保持 512 个扫描周期、接口约 T+513 完成的总体时序。

#### 4.2.3 刷新期间的读写污染

现有 useful-reset 机制已经处理了逐 set 清零期间的 SRAM 端口冲突：

- 当某个 bank 的 `usefulResetInFlightMask` 已置位时，该 bank 的 useful SRAM 读请求被屏蔽。若表级读请求仍然到达，该请求对应的 `usefulCtrs` 响应被置为 0。
- `usefulResetInFlightMask` 同时作为清零写的有效信号。清零期间，SRAM 写口选择 `usefulResetSetIdx` 和 `UsefulCounter.Zero`；普通 useful 写队列停止出队，因而不会与清零写竞争端口。
- `usefulResetSetIdx` 只在清零写实际完成握手后递增，SRAM 未接受写请求时保持当前 set 不变。

这些行为均为当前周期性 useful reset 已有的机制，不是 context flush 新增逻辑。不过，现有机制只负责清零进行期间的端口仲裁，并不提供 context 隔离：它**不会清空 useful 写队列，也不会阻止新的普通写请求入队**。对于 context flush，普通入队和预测/训练读请求可能携带旧上下文状态，必须额外阻断；触发拍已经到达的普通出队写回发生在逐 set 写零开始前，由后续 useful reset 覆盖，详见 §4.3.3。

因此，context flush 需要在现有机制之外补充以下隔离：

- `contextFlush` 当拍清空 useful 写队列，见 §4.3.2；
- `bpuFlushing` 阻断刷新窗口期间普通 useful 写入队；普通 useful 出队和 SRAM 写回保持原 `!usefulResetValid` 仲裁，触发拍旧写回由后续逐 set 写零覆盖，见 §4.3.3；
- 预测和训练的读请求分别在 TAGE 顶层阻断，见 §4.4、§4.5；

---

### 4.3 写缓冲

TAGE 训练写先进入 `entryWriteBuffers` 或 `usefulCtrWriteBuffers`，SRAM 端口空闲时再写回。若只清 SRAM 不清写缓冲，旧请求会延迟写回污染 SRAM；entry buffer 中旧 `entries/shadowValid/dirty` 还会参与后续同 set/tag 的命中、合并和写回判断。

#### 4.3.1 I/O 接口修改

两类写缓冲共用 §4.1.1 已下发到 `TageTable` 的 `contextFlush` 和 `bpuFlushing`，**无需新增 I/O 端口**：

- `WriteBuffer[EntrySramWriteReq]` 通过 `hasContextFlush = HasBpuFlush` 生成可选 `contextFlush` 端口；
- `Queue[UsefulCtrSramWriteReq]` 通过 `hasFlush = HasBpuFlush` 生成可选 `flush` 端口；
- 两类缓冲均由 `contextFlush` 同步清空，并由 `bpuFlushing` 阻止重新入队；

#### 4.3.2 清空写缓冲

`entryWriteBuffers` 复用 03-aBTB 已部署的 `WriteBuffer` context-flush 实现（03 §4.3.1-§4.3.2）。TAGE 只在 `TageTable.scala` 的实例点将 `hasContextFlush` 绑定为 `HasBpuFlush` 并连接 `contextFlush`，不修改共享 `WriteBuffer` 及 `ReplacementPolicy`/`PseudoLRU`；context flush 仅通过清除 `dirty/shadowValid` 使旧槽失效，不清零 `entries` 或 replacement/PLRU 状态。因此旧槽既不能继续写回，也不能参与 shadow 命中/合并；残留的 data 和 PLRU bit 不再构成可见的 buffer 项。

`usefulCtrWriteBuffers` 复用标准 `Queue` 的 `hasFlush` 机制（04 §4.5.2）：通过 `hasFlush` 清除队列有效状态，使队列在刷新后为空；残留 data bit 没有有效队列状态引用，不会出队。

```scala
// TageTable.scala
private val entryWriteBuffers = Seq.tabulate(NumBanks) { bankIdx =>
  Module(new WriteBuffer(
    new EntrySramWriteReq,
    ... // 保持原端口不变
    hasContextFlush = HasBpuFlush	// new
  ))
}

private val usefulCtrWriteBuffers = Seq.tabulate(NumBanks, NumWays) { (bankIdx, wayIdx) =>
  Module(new Queue(
    new UsefulCtrSramWriteReq,
    entries = WriteBufferSize,
    pipe = true,
    flow = true,
    hasFlush = HasBpuFlush
  )).suggestName(s"tage_useful_write_buffer_bank${bankIdx}_way${wayIdx}")
}

if (HasBpuFlush) {
  entryWriteBuffers.foreach(_.io.contextFlush.get := contextFlush)
  usefulCtrWriteBuffers.flatten.foreach(_.io.flush.get := contextFlush)
}
```

#### 4.3.3 刷新期间的读写污染

T 拍同时清空两类写缓冲。两类**写缓冲的入队口直接用 `bpuFlushing` 门控**从而阻断刷新窗口内针对 `entryWriteBuffers` 和 `usefulCtrWriteBuffers` 的入队请求。

**Entry buffer 出队不增加门控**，依赖 §4.1.3 所述的后续清零覆盖、SRAM reset 反压和空 buffer，确保 Entry SRAM 不被污染。

**Useful buffer 出队不增加 context 门控**，正常 usefulbuffer 出队只在 `!usefulResetValid` 时发生；T 拍可能发生的一次旧 useful 写回会被随后逐 set 写零覆盖，因此出队侧不新增 context 门控。

- §4.2 只为现有 useful-reset 机制新增 `contextFlush` 触发源，不改变 useful-reset 的执行时序：
  - T 拍 `contextFlush` 置位 `usefulResetInFlightMask` 并清 `usefulResetSetIdx`；
  - T+1 起由原逻辑逐 set 写零，T+2 拍零值被写入 SRAM；
  - T+513 拍最后一个零值被写入 SRAM，`usefulResetInFlightMask` 被置为 false，表示完成 useful-reset。
- `usefulResetValid` 仍保持原语义，即 `usefulResetInFlightMask(bankIdx)`，表示 useful-reset 已进入逐 set 写零阶段。

```scala
// Entry write buffer：入队口门控表内延迟一拍的 writeReqValid。
entryWriteBuffers.zipWithIndex.foreach { case (buffer, bankIdx) =>
  buffer.io.write.zipWithIndex.foreach { case (bufferIn, wayIdx) =>
    val writeValid =
      writeReqValid && writeReq.bankMask(bankIdx) && writeReq.wayMask(wayIdx) && writeReq.writeEntryEn(wayIdx)
    bufferIn.valid       := (if (HasBpuFlush) writeValid && !bpuFlushing else writeValid)		// new: add Flush
    bufferIn.bits.setIdx := writeReq.setIdx
    bufferIn.bits.entry  := writeReq.entries(wayIdx)
  }
  buffer.io.takenMask.get := writeReq.actualTakenMask
}


// Useful write buffer 入队门控
usefulCtrWriteBuffers.zipWithIndex.foreach { case (bankBuffer, bankIdx) =>
  bankBuffer.zipWithIndex.foreach { case (wayBuffer, wayIdx) =>
    val writeValid =
      writeReqValid && writeReq.bankMask(bankIdx) && writeReq.wayMask(wayIdx) && writeReq.writeUsefulEn(wayIdx)
    wayBuffer.io.enq.valid          := (if (HasBpuFlush) writeValid && !bpuFlushing else writeValid) // new: add Flush
    wayBuffer.io.enq.bits.setIdx    := writeReq.setIdx
    wayBuffer.io.enq.bits.usefulCtr := writeReq.usefulCtrs(wayIdx)
  }
}
```

---

### 4.4 防止预测路径污染刷新结果

context flush 当拍，S1/S2 中可能仍保存旧上下文的 PC、折叠历史、tag 和 Entry SRAM 读回。刷新窗口内 SRAM reset 又使端口输出不具备合法读响应语义。如果让这些内容继续参与 tag match，可能产生旧 provider/alternate。预测路径的处理顺序是：**先清空已经保存的旧流水寄存器，再阻断新的预测读请求，并把刷新窗口内的读响应强制为 0**。这样流水线中不会继续携带旧上下文命中信息。

**策略一：清空预测流水线中的寄存器**

`contextFlush` 以最高优先级清除 `s1_startPc`、`s1_foldedHist`，以及 `s2_startPc`、`s2_tag`、`s2_readResp`。

`s1_readResp` 本身不是可清零的寄存器，因此将 `DataHoldBypass` 展开，显式得到 `s1_readRespValid` 和 `s1_readRespHold`，再由 `contextFlush` 清除 `s1_readRespHold`（具体展开见策略二）。

```scala
// Tage.scala
// 放在 S2 寄存器定义之后、s2_branches tag match/provider 选择逻辑之前。
if (HasBpuFlush) {
  when(contextFlush) {
    s1_startPc        := 0.U.asTypeOf(s1_startPc)
    s1_foldedHist     := 0.U.asTypeOf(s1_foldedHist)

    s2_startPc        := 0.U.asTypeOf(s2_startPc)
    s2_tag            := 0.U.asTypeOf(s2_tag)
    s2_readResp       := 0.U.asTypeOf(s2_readResp)
  }
}
```

**策略二：阻断旧信息进入流水线**

刷新窗口内保持 `s0_fire/s1_fire/s2_fire` 及其外部 stage control 不变。策略二只负责两件事：**阻塞预测读请求**，**强制读响应数据为零**。

- `bpuFlushing` 阻断整个刷新窗口期内的预测读请求。
- 读响应置零由本策略中 `bpuFlushing` 对 `s1_readResp` 的组合 Mux 置零实现；`contextFlush` 对 `s1_readRespHold` 的 last-connect 清零也在下方展开代码中实现。
- `HasBpuFlush=false` 时，读请求恢复为 `s0_fire`，且不生成读响应置零逻辑。

```scala
// Tage.scala
private val s0_sramReadValid = s0_fire && (if (HasBpuFlush) !bpuFlushing else true.B)   // T 拍：false
tables.zipWithIndex.foreach { case (table, tableIdx) =>
  table.io.readReq(0).valid         := s0_sramReadValid
  table.io.readReq(0).bits.setIdx   := s0_setIdx(tableIdx)
  table.io.readReq(0).bits.bankMask := s0_bankMask
}

private val s1_rawReadResp   = VecInit(tables.map(_.io.readResp(0)))
private val s1_readRespValid = RegNext(s0_sramReadValid, init = false.B)        // T 拍： true，T+1 拍：false
private val s1_readRespHold  = RegEnable(s1_rawReadResp, s1_readRespValid)      // T 拍：先赋值为 s1_rawReadResp，后被覆盖为零值
if (HasBpuFlush) {                                                              // T+1 拍起：s1_readRespValid = false，不被更新
  when(contextFlush) {
    s1_readRespHold := 0.U.asTypeOf(s1_readRespHold)
  }
}

private val s1_heldReadResp = Mux(s1_readRespValid, s1_rawReadResp, s1_readRespHold)  // T 拍：s1_rawReadResp，T+1 拍：s1_readRespHold
private val s1_readResp = if (HasBpuFlush) {
    Mux(bpuFlushing, 0.U.asTypeOf(s1_heldReadResp), s1_heldReadResp)                  // T 拍起：零值
  } else {
    s1_heldReadResp
  }
```

---

### 4.5 防止训练路径污染刷新结果

§4.1～§4.3 已负责清空 SRAM 和写缓冲、阻断刷新窗口内的新入队，并处理触发拍残留写回，因此本节不再重复处理写缓冲污染。训练路径还需处理两类 buffer 之外的旧状态：①阻断刷新当拍的新请求进入 T0，并使已在 T1/T2/T3 的旧请求失效，避免其继续产生 buffer 之外的训练副作用；②清除顶层训练策略寄存器中保存的旧上下文状态。

**策略一：清空训练流水线中的 payload 和策略状态**

实际代码中，T0 直接使用 `io.train`、`io.train.meta` 和训练用折叠历史计算，不保存独立的 T0 payload；跨拍 payload 从 T1 开始。策略二已经用 `bpuFlushing` 组合拉低 `t0_fire`～`t3_fire`，在刷新窗口内建立“所有训练 fire 均为 false”的集中式流水 kill，不需要再用 `contextFlush` 显式复位 fire 背后的 `RegNext`。**策略一只清除仍会保存旧上下文内容的 payload 和顶层训练策略状态**（`usefulResetCtr` 和 `useAltOnNaVec`）。

下列清零块应放在 T3 写请求、`usefulResetCtr` 和 `useAltOnNaVec` 的正常更新逻辑之后，使 `contextFlush` 通过 Chisel last-connect 取得最高赋值优先级：

```scala
// Tage.scala
// [新增] contextFlush 时钟沿清除训练流水 payload 和策略状态。
if (HasBpuFlush) {
  when(contextFlush) {
    t1_startPc     := 0.U.asTypeOf(t1_startPc)
    t1_branches    := 0.U.asTypeOf(t1_branches)
    t1_setIdx      := 0.U.asTypeOf(t1_setIdx)
    t1_bankMask    := 0.U.asTypeOf(t1_bankMask)
    t1_useMeta     := false.B
    t1_meta        := 0.U.asTypeOf(t1_meta)
    t1_basePred    := 0.U.asTypeOf(t1_basePred)
    t1_mbtbHitMask := 0.U.asTypeOf(t1_mbtbHitMask)
    t1_foldedHist  := 0.U.asTypeOf(t1_foldedHist)

    t2_branches    := 0.U.asTypeOf(t2_branches)
    t2_startPc     := 0.U.asTypeOf(t2_startPc)
    t2_setIdx      := 0.U.asTypeOf(t2_setIdx)
    t2_bankMask    := 0.U.asTypeOf(t2_bankMask)
    t2_rawTag      := 0.U.asTypeOf(t2_rawTag)
    t2_readResp    := 0.U.asTypeOf(t2_readResp)
    t2_useMeta     := false.B
    t2_meta        := 0.U.asTypeOf(t2_meta)
    t2_basePred    := 0.U.asTypeOf(t2_basePred)
    t2_mbtbHitMask := 0.U.asTypeOf(t2_mbtbHitMask)

    t3_branches            := 0.U.asTypeOf(t3_branches)
    t3_startPc             := 0.U.asTypeOf(t3_startPc)
    t3_setIdx              := 0.U.asTypeOf(t3_setIdx)
    t3_bankMask            := 0.U.asTypeOf(t3_bankMask)
    t3_rawTag              := 0.U.asTypeOf(t3_rawTag)
    t3_readResp            := 0.U.asTypeOf(t3_readResp)
    t3_useMeta             := false.B
    t3_mbtbHitMask         := 0.U.asTypeOf(t3_mbtbHitMask)
    t3_cfiUseAltOnNaIdxVec := 0.U.asTypeOf(t3_cfiUseAltOnNaIdxVec)
    t3_trainInfoVec        := 0.U.asTypeOf(t3_trainInfoVec)

    usefulResetCtr.resetZero()
    useAltOnNaVec.foreach(_.resetZero())
  }
}
```

> - `t1_rawTag`、`t1_readResp`、`t2_cfiUseAltOnNaIdxVec`、`t2_trainInfoVec` 以及 T3 的分配/更新结果都是由上述寄存器派生的组合逻辑，不保存跨拍状态，无需单独清零。
> - `usefulResetInFlight` 不在策略一中清零：它保留原有周期性 useful reset 的跟踪语义；context flush 的 useful 清零进度由 §4.6 直接归约各表的 `io.usefulResetInFlight`。
> - 调试、性能计数和 Trace 不参与预测或训练功能状态更新，也不纳入 context flush 的功能状态清理范围。

**策略二：阻塞训练流水线推进**

训练通路门控统一用 `bpuFlushing`，核心是在源头门控 `t0_fire` 。T1/T2/T3 的 fire 继续由前一级 fire 经原有 `RegNext` 派生，并在各级 `RegNext` 输出端补充 `!bpuFlushing`，使刷新窗口内所有有效 fire 立即为 false。

由于训练 table-read 请求保持 `t0_fire && t0_needRead`，门控 `t0_fire` 后，刷新窗口内训练读请求的 valid 自然为 false；训练写请求由 `t3_fire` 派生，也会同步关闭。写缓冲自身的清理、入队门控和写回仲裁仍由 §4.3 负责。§4.5 的训练路径门控用于处理 buffer 之外的训练副作用。

> **只门控 `t0_fire`** 虽然可以依靠清空流水级 payload、阻断写缓冲及复位相关状态等措施保证刷新正确性，但其正确性依赖多个分散条件，维护和验证成本较高。
> 相比之下，**对 `t0~t3_fire` 统一进行 `bpuFlushing` 门控**，可以直接建立“刷新期间所有流水级均无有效事务”的强不变量，使所有依赖 `t*_fire` 的训练和副作用天然停止，逻辑更清晰、鲁棒性也更强。

```scala
// Tage.scala
// [修改] 整个刷新窗口禁止训练推进。
private val t0_fire = if (HasBpuFlush) {
  io.stageCtrl.t0_fire && t0_hasCond && io.enable && !bpuFlushing
} else {
  io.stageCtrl.t0_fire && t0_hasCond && io.enable
}
private val t1_fire = RegNext(t0_fire, init = false.B) && (if (HasBpuFlush) !bpuFlushing else true.B)
private val t2_fire = RegNext(t1_fire, init = false.B) && (if (HasBpuFlush) !bpuFlushing else true.B)
private val t3_fire = RegNext(t2_fire, init = false.B) && (if (HasBpuFlush) !bpuFlushing else true.B)
```

---

### 4.6 刷新完成信号 resetDone

TAGE 的 context flush 只等待两类多拍清零：Entry SRAM `extraReset` 和 useful SRAM 逐 set 清零。表级完成信号分别来自：

- **Entry SRAM**：§4.1.2 新增 `io.entryResetDone.get`，归约本表 4 bank × 2 way 的全部 Entry SRAM `resetDone`。
- **useful-reset**：复用现有 `io.usefulResetInFlight`，其来源是本表 `usefulResetInFlightMask` 的 OR 归约；对它取反即表示本表 useful-reset 完成。

表级信号生成代码如下：

```scala
// TageTable.scala
// [新增，见 §4.1.2] 仅表示本表 Entry SRAM extraReset 完成。
if (HasBpuFlush) {
  entrySram.flatten.foreach { sram => sram.extra_reset.get := contextFlush }
  io.entryResetDone.get := entrySram.flatten.map(_.io.resetDone).reduce(_ && _)
}

// [现有代码] 任一 bank 仍在扫描，本表 useful reset 即为 in-flight。
io.usefulResetInFlight := usefulResetInFlightMask.reduce(_ || _)
```

`Tage` 顶层先归约两个信号，再生成本地 `resetDone`：

```scala
// Tage.scala
/* *** reset *** */
io.sramResetDone := tables.map(_.io.sramResetDone).reduce(_ && _)

// [替换原 io.resetDone.get := true.B 占位逻辑]
if (HasBpuFlush) {
  val allEntryResetDone  = tables.map(_.io.entryResetDone.get).reduce(_ && _)
  val allUsefulResetDone = !tables.map(_.io.usefulResetInFlight).reduce(_ || _)
  val contextStorageDone = allEntryResetDone && allUsefulResetDone

  io.resetDone.get := !bpuFlushing || (!contextFlush && contextStorageDone)
}
```

其中 `!contextFlush` 用于屏蔽 T 拍尚未响应本次刷新、可能仍为高的旧完成值；非刷新期间则由 `!bpuFlushing` 使 `resetDone` 保持为高。普通周期性 useful reset 不会影响该空闲值。

完成信号与 Entry/useful 清零过程的完整逐拍时序见 §5。

---

### 4.7 编译开关裁剪清单

按 00 §3.4 的要求，`HasBpuFlush=false` 时以下 TAGE 刷新专用结构必须从生成 RTL 中结构性消失（双配置 elaboration 见 00 §5.1，关闭配置 RTL 检查见 00 §5.2）：

| 类别                    | 应消失的对象                                                                                                                                                                         | 对应小节                |
| :---------------------- | :----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | :---------------------- |
| 端口与连线              | `BasePredictorIO` 的 `contextFlush`/`bpuFlushing`/`resetDone`（01 声明）；`TageTableIO` 的 `contextFlush`/`bpuFlushing`/`entryResetDone` 及顶层分发                  | §4.1.1、§4.6          |
| Entry SRAM 清零         | `extraReset = HasBpuFlush` 绑定下的 `extra_reset` 端口及连线                                                                                                                     | §4.1.2                 |
| context useful 清零触发 | `contextFlush` 与 `t3_usefulResetStart` 的合并支路和 `bpuFlushing` 门控；原有周期性 useful reset 保留                                                                          | §4.2.2、§4.2.3        |
| 写缓冲清空              | `WriteBuffer(hasContextFlush = HasBpuFlush)` 和 `Queue(hasFlush = HasBpuFlush)` 产生的可选端口、清空逻辑与连线                                                                   | §4.3.1、§4.3.2        |
| 本地完成归约            | 新增的表内 Entry-only `entryResetDone`，以及顶层 `allEntryResetDone`、`allUsefulResetDone`、`contextStorageDone`；不复用混合 `sramResetDone` | §4.1.2、§4.2.2、§4.6 |
| 顶层状态清零            | `useAltOnNaVec`/`usefulResetCtr` 的 context flush 清零分支                                                                                                                       | §4.5                   |
| 预测内容隔离            | 预测 table-read 请求门控、读响应整窗置零及 S1/S2 旧上下文寄存器清零                                                                                                                  | §4.4                   |
| 训练残留与写入门控      | T0～T3 有效 fire 整窗组合门控，T1/T2/T3 payload 及顶层策略寄存器清零，写缓冲入队门控；fire 的`RegNext` 不增加显式清零；Entry 出队/写回保持原 ready 仲裁                            | §4.1.3、§4.3、§4.5   |
| 完成信号                | `io.resetDone.get` 赋值及 `contextFlush` 对旧完成值的屏蔽逻辑                                                                                                                    | §4.6                   |

现有 `io.usefulResetInFlight := usefulResetInFlightMask.reduce(_ || _)` 是周期性 useful reset 的原有功能，不属于编译开关裁剪对象，`HasBpuFlush=false` 时必须保留。

关闭后的数据通路必须与未引入 TAGE 刷新的原始实现一致：Entry SRAM 保持原参数，table 读、写缓冲入队/出队、T0~T3 fire 与周期性 useful reset 均恢复原表达式，不保留额外寄存器、Mux、门控或默认值。

---

## 5. 应用刷新机制之后的预测流水线

逐拍时序（默认配置，8 表 × 4 bank × 2 way = 64 个 Entry SRAM 并行清零，每个 Entry SRAM 的 `set = 4096 / 4 / 2 = 512`；32 个 useful bank 并行扫描，每个 bank 的两个 way 同拍写零）：

- **T 拍，触发与组合阻断**：`contextFlush` 和 `bpuFlushing` 拉高。`resetDone` 由 `!contextFlush` 项组合拉低，屏蔽此拍可能仍为高的 `allEntryResetDone/allUsefulResetDone` 旧值。预测 table-read 请求和写缓冲入队被关闭，预测读响应被组合置零，`t0_fire`～`t3_fire` 全部为 false，因而不再产生训练读、训练写和策略状态更新。由于 Entry SRAM 的 `resetState` 尚未置位，当拍组合路径上至多仍可能完成一次旧 entry-buffer 写回；useful buffer 也可能完成一次旧写回，两者都会被后续清零覆盖。
- **T 拍时钟沿，刷新状态生效**：Entry SRAM 的 `extraReset` 进入复位状态；各表的 `usefulResetInFlightMask` 全部置位，`usefulResetSetIdx` 清为 0；`entryWriteBuffers` 的 `dirty/shadowValid` 和 `usefulCtrWriteBuffers` 的队列有效状态被清除。同一时钟沿清除 S1/S2 预测流水旧 payload、T1/T2/T3 训练流水 payload、`usefulResetCtr` 和 `useAltOnNaVec`；fire 的 `RegNext` 由前级 false fire 自然装入 0，不额外显式清零。
- **T+1 拍，并行清零开始**：64 个 Entry SRAM 的 `resetState=true`，读/写 `ready` 拉低，reset 地址、零数据和全 way mask 接管 array 并开始逐行清零；`bufferOut.ready` 随 SRAM 写端反压，不能完成普通出队。32 个 useful bank 的 `usefulResetInFlightMask=true`，从 set 0 开始逐 set 发出写零，普通 useful-buffer 写回被原有 `!usefulResetValid` 仲裁阻断。此拍 `allEntryResetDone=false`、`allUsefulResetDone=false`。
- **T+2 拍**：首个 useful set 的零值写入 SRAM。Entry/useful 清零继续并行推进；后续扫描到 T 拍残留写回的 set 时，将其覆盖为 0。
- **T+2 ～ T+512 拍，清零窗口**：Entry SRAM 持续逐行清零，useful reset 持续逐 set 写零。`bpuFlushing` 持续关闭预测读请求和两类写缓冲入队，并将预测读响应置零；预测的 `s0_fire/s1_fire/s2_fire` 保持原有推进语义，训练的 `t0_fire`～`t3_fire` 则持续为 false。因两类写缓冲已经清空且无新入队，不会产生刷新后回灌。
- **T+513 拍，TAGE 本地完成**：最后一个 useful set 的零值写入 SRAM；512 个扫描周期完成后，Entry `_resetState` 和 useful `usefulResetInFlightMask` 在完成沿后转为空闲。`allEntryResetDone` 和 `allUsefulResetDone` 同拍拉高，使 `contextStorageDone=true`、TAGE `resetDone=true`，Entry SRAM 普通端口恢复 `ready`。由于 `bpuFlushing` 尚由 BPU 聚合状态机保持，TAGE 不会在本地完成拍提前发出新请求。
- **T+513 ～ T+ka 拍，等待 BPU 聚合（仅当 `ka > 513`）**：TAGE 存储和寄存器状态已完成清理，`resetDone` 保持为高，但预测读、buffer 入队和训练仍由 `bpuFlushing` 阻断，预测流水继续使用零响应。`ka` 表示 BPU 所有被选中子预测器的聚合完成延迟，默认情况下 `ka ≥ 513`。
- **T+ka 拍，BPU 聚合完成**：所有被选中子预测器的 `resetDone` 均为高，BPU 顶层在当拍完成归约，并在时钟沿迁出 `s_flushing`。
- **T+ka+1 拍起，新上下文恢复**：`bpuFlushing` 拉低，预测 table-read 请求、两类写缓冲入队和 T0～T3 训练流水恢复；预测流水在正常 SRAM 读延迟后开始使用已清零的 TAGE 表内容。
