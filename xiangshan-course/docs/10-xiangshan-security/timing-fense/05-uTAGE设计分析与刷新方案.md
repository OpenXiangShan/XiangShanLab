# 05-uTAGE设计分析与刷新方案

> 本文档是 uTAGE（`MicroTage`）的**真实刷新方案**，属于 [01-BPU刷新方案概览](01-BPU刷新方案概览.md) §6.1 占位阶段之后的落地内容：实现时用 §4.6 的真实完成条件替换 `MicroTage.scala:85` 的 `io.resetDone.get := true.B` 恒真占位。
>
> 1. **接口契约**：`contextFlush`/`bpuFlushing`/`resetDone` 由 `BasePredictorIO` 以 `Option.when(HasBpuFlush)` 声明（01 §5.1.3），BPU 顶层已按本事务 `activeFlushMask` 门控后下发（01 §5.3.3）。uTAGE 无独立刷新使能位、mask 位固定为 1（01 §5.3.1），收到的均为针对自身的有效信号。
> 2. **编译开关**：本文对 `contextFlush`/`bpuFlushing` 的消费遵循 [00-编译开关实现方案](00-BPU刷新机制编译开关实现方案.md) §3.1 统一编码规则与 01 §6.2、02~04 文档同款模板：各消费模块（`MicroTage`、`MicroTageTable`、`BypassShadowBuffer`）在模块体顶部先把 Option 端口解包为中间变量（`private val contextFlush = if (HasBpuFlush) io.contextFlush.get else false.B` 等），模块内统一引用变量名，门控不直接散布 `io.contextFlush.get` / `io.bpuFlushing.get`；
> 3. **消费点裁剪方式**：刷新专用结构由完整 Scala `if/else` 块守卫，关闭配置下相应分支不参与 elaboration；内联 Scala `if` 表达式（如 `x && (if (HasBpuFlush) !bpuFlushing else true.B)`）在 elaboration 期选择 `true.B` 恒等项，新增门控随后由 Chisel/CIRCT 编译期常量折叠消除。两种写法的最终生成 RTL 等价。Option 连线与输出端口驱动仍用 `if (HasBpuFlush) { ... .get ... }`。`HasBpuFlush=false` 时应消失的结构清单见 §4.7。
> 4. **刷新窗口**：`bpuFlushing` 覆盖 `contextFlush` 单拍至聚合 `resetDone` 置位、状态机离开 `s_flushing` 的整个窗口（01 §5.2.2、§5.4）。

---

## 1. uTAGE 的预测流水线

uTAGE 顶层模块为 `MicroTage`，位于 `src/main/scala/xiangshan/frontend/bpu/utage`。

uTAGE 本质上是 **ABTB 条件分支预测结果的修正器**，不是独立发现分支的 BTB：

- ABTB 提供候选分支位置、基础 taken、是否强偏置等信息
- uTAGE 只对 ABTB 已经给出的条件分支做 taken 修正
- 如果 ABTB 没有给出某个条件分支，uTAGE 不会凭空预测该分支

---

### 第一拍：A0 读表

根据 `startPc` 和折叠路径历史 `foldedPathHist`，uTAGE 为每张表计算一个读索引，并并行访问所有表。

默认配置：

```text
2 张表 × 512 set × 1 way
每张表内部 4 bank
每个 bank 深度 = 512 / 4 = 128 行
```

一次预测会访问 2 张表，每张表根据 index 选择其中 1 个 bank。

---

### 第二拍：A1 读回并比较

`MicroTageTable` 返回 entry 后，uTAGE 对每张表读出的 entry 做以下判断：

- `valid`：entry 是否有效
- `tagHit`：entry tag 是否命中当前 PC 和历史计算出的 tag
- `posHit`：entry 的 `cfiPosition` 是否匹配 ABTB 提供的分支位置
- `taken`：entry 的 taken counter 是否为正

其中 `posHit` 来自 entry 的 `cfiPosition` 与 `abtbPosVec` 的比较。这个比较提前完成，用来减轻后一拍 provider 选择的压力。

---

### 第三拍：A2 选择 provider 并输出预测

A2 对每个 ABTB 候选条件分支做两级选择：

1. 表内：`tagHit && posHit` 才认为该 way 命中
2. 表间：若多张表命中，选择表号更高的结果，也就是历史更长的 provider

输出包括：

- `prediction.takenVec`：uTAGE 给出的 taken 修正结果
- `prediction.hitVec`：uTAGE 是否命中该 ABTB 候选分支
- `meta`：训练需要的 `readIndex/tableId/takenCtr/cfiPosition` 等信息（无 `wayId` 字段，way 数硬编码为 1）

注意：`hitVec` 只是 uTAGE 内部命中结果，最终是否使用还要结合 ABTB 的 valid。

---

### A3 override 缓存

uTAGE 会把上一拍 A2 的读结果保存到 A3，包括：

- `a3_readIndex`
- `a3_predRead`
- `a3_posHitVec`

当 `overrideValid` 有效时，uTAGE 会复用 A3 数据重新计算 `tagHit`。因此刷新时不仅要屏蔽 A2 输出，也要清掉 A3 残留，否则旧上下文读出的 entry 可能通过 override 路径重新进入预测路径。

---

## 2. uTAGE 的训练流水线

uTAGE 训练分两层：

1. `MicroTage` 顶层判断是否需要更新或分配
2. 每张 `MicroTageTable` 内部的 `BypassShadowBuffer` 合并更新、维护 useful，并延迟写回 SRAM

---

### 第一拍：T0 生成训练结果

**T0 读取训练 meta 和真实分支结果**。对每个 ABTB 候选条件分支，uTAGE 记录：

- 当时 uTAGE 是否命中
- base taken / uTAGE pred taken / actual taken
- 命中的 `tableId/takenCtr`（无 `wayId`，		way 数硬编码为 1）
- 对应的 `cfiPosition`

如果 uTAGE 命中但预测错，或者 uTAGE 未命中但 ABTB 基础预测错，就认为该候选项需要训练。

若同一预测块内有多个条件分支错误，uTAGE 只选择 position 最靠前的错误分支进行分配。

---

### 第二拍：T1 生成 update / alloc

T1 根据 T0 结果生成两类请求。

**1. 更新已有 provider**

如果训练结果命中某张表的某个 way，并且位置校验没有失败，则对该 way 产生 `t1_update`。

`useful` 更新规则：

- provider 预测错：降低 useful
- provider 相对 base 预测对：提高 useful

**2. 分配新 entry**

发生误预测时，uTAGE 尝试在更高优先级表中分配新 entry。

分配条件：

- 当前训练确实需要分配
- 表号必须**严格高于**已有 provider（`t1_lowerFillMask = providerOH | (providerOH - 1)` 排除 provider 本身及以下；无 provider 时不受限）
- 该表在**训练 index 处**的 useful 为 0（`t1_keepUseMask(i) = t1_trainRead(i).useful =/= 0`，useful 按训练 index 寻址，不是全表扫描是否存在可用 way）
- 在候选表中选择**表号最小**的一张（`PriorityEncoderOH(t1_allocCandidateMask)` 取最低位，即候选中历史最短的一张）

当前默认 `NumWays = 1`，因此 uTAGE **没有 PLRU replacer**，分配主要由 useful 是否为 0 决定。

---

### useful 周期性衰减

如果需要分配但没有任何候选表可分配，`lowTickCounter/highTickCounter` 会递增。计数器溢出时触发 `usefulReset`，由 `BypassShadowBuffer` 对 useful 寄存器数组做批量衰减。

---

## 3. uTAGE 的整体架构

### 3.1 MicroTageEntry

```text
MicroTageEntry
├── valid:       Bool                  - 1 bit
├── tag:         UInt(MaxTagLen)        - 默认 8 bit
├── cfiPosition: UInt(CfiPositionWidth) - 默认 5 bit
├── takenCtr:    SaturateCounter        — 默认 3 bit
└── dummy:       UInt(1.W)              - 占位，恒为 0，仅用于补齐偶数位宽
```

`useful` 不在 `MicroTageEntry` 中，而是保存在 `BypassShadowBuffer` 内部的独立寄存器数组。

---

### 3.2 Entry SRAM

每张 `MicroTageTable` 内部实例化 4 个单端口 SRAM bank。默认每张表 512 set，所以每个 bank 128 行。

**读路径**

- 预测 A0 根据 `readIndex` 选择 bank
- A1 取回对应 bank 的数据
- 若 `BypassShadowBuffer` 命中同 index，则优先使用 buffer 数据

**写路径**

- `MicroTage` 顶层不直接写 SRAM
- 训练更新先进入 `BypassShadowBuffer`
- buffer 后续在 SRAM 写端口可用时写回

Entry SRAM 是单端口，读写同 bank 冲突时默认读优先；`forceWrite` 有效时允许强制写。

---

### 3.3 BypassShadowBuffer

`BypassShadowBuffer` 是 uTAGE 中最需要关注的状态结构。它负责：

1. 缓存最近训练更新，合并写入
2. 给预测读 / 训练读提供 RAW bypass
3. 维护 useful counter
4. 在后续空闲周期把 dirty entry 写回 SRAM

核心状态包括：

- `entries`：保存 buffer entry
- `statusEntries`：记录 valid / dirty 状态
- `enqPtr/deqPtr`：入队和出队指针
- `priorityMask`：同 index 多副本时选择较新的副本

如果刷新只清 SRAM、不清 buffer，会出现两个问题：

- 预测读可能命中 buffer，绕过已清零的 SRAM，读到旧 entry
- buffer 后续可能把旧 entry 重新写回 SRAM

因此 buffer 必须单独刷新。

---

### 3.4 usefulEntries

uTAGE 的 useful counter 保存在 `BypassShadowBuffer` 内：

```text
usefulEntries: numBanks × (numSets / numBanks) × numWay × UsefulWidth
默认每张表：4 bank × 128 set/bank × 1 way × 2 bit = 1024 bit
```

它直接影响新 entry 分配。若 useful 不清零，可能出现 entry 已经无效但 useful 仍非 0，导致新上下文无法分配 entry。

---

## 4. 刷新方案

### 4.1 Entry SRAM

#### 4.1.1 I/O 接口修改

Entry SRAM 的刷新依赖 `contextFlush` 与 `bpuFlushing`，**`MicroTage` 顶层**复用 `BasePredictorIO` 的既有 Option 端口（01 §5.1.3，已由 BPU 顶层按本事务 `activeFlushMask` 门控后下发，01 §5.3.3），无需新增；

**MicroTageTable** 需在 `MicroTageTableIO` 中新增两端口并向下传递。`MicroTageTableIO` 为 `MicroTageTable` 的内部类、`extends MicroTageBundle`（经 `BpuBundle` 混入 `HasXSParameter`），可直接引用 `HasBpuFlush`，无需改父类或类签名；新端口按 00 §3.1 用 `Option.when` 声明，Option 到 Option 分发连线放入 `if (HasBpuFlush)`（关闭时端口不生成，整段不连线，不经过中间变量）：

```scala
// MicroTage.scala -- 顶层模块体顶部：中间变量（00 §3.1 / 01 §6.2），
// 后续 MicroTage 对两个门控信号的消费统一引用变量名（消费点仍处于 if 内）；
// else false.B 仅为 Scala 类型占位，关闭时不进入硬件
private val contextFlush = if (HasBpuFlush) io.contextFlush.get else false.B
private val bpuFlushing  = if (HasBpuFlush) io.bpuFlushing.get  else false.B

// MicroTage.scala -- 分发至 MicroTageTable
if (HasBpuFlush) {
  tables.foreach { t =>
    t.contextFlush.get := contextFlush
    t.bpuFlushing.get  := bpuFlushing
  }
}

// MicroTageTable.scala
class MicroTageTableIO extends MicroTageBundle {
  ...  // 原有端口
  val contextFlush: Option[Bool] = Option.when(HasBpuFlush)(Input(Bool()))
  val bpuFlushing:  Option[Bool] = Option.when(HasBpuFlush)(Input(Bool()))
}

val io = IO(new MicroTageTableIO)
// MicroTageTable 解包中间变量，供 §4.1.3 / §4.2.3 / §4.4 消费
private val contextFlush = if (HasBpuFlush) io.contextFlush.get else false.B
private val bpuFlushing  = if (HasBpuFlush) io.bpuFlushing.get  else false.B
```

> **uTAGE 无独立刷新使能位**：`getSubFlushEnable` 中 `MicroTage` 命中显式分支 `case _: MicroTage => true.B`（01 §5.3.1）；`BpuCtrl` 无 `utageFlushEnable` 字段；`currentFlushMask` 中 uTAGE 的 mask 位（bit 3）固定为 1；故**仅受全局 `bpuFlushEn` 门控、恒随全局刷新**，且其 resetDone 恒参与顶层完成聚合（01 §5.3.4）。

#### 4.1.2 清空 Entry SRAM

启用 `SRAMTemplate` 的 `extraReset` 端口，由 `contextFlush` 触发逐行写 0：

```scala
// MicroTageTable.scala 修改
private val entrySram = Seq.tabulate(numBanks) { bankIdx =>
  Module(new SRAMTemplate(
    ...
    extraReset  = HasBpuFlush,  // false 时 extra_reset 端口不生成
    ...
  )).suggestName(s"utage_entry_sram_bank${bankIdx}")
}

if (HasBpuFlush) {
  entrySram.foreach { bank => bank.extra_reset.get := contextFlush }
}
```

#### 4.1.3 刷新期间的读写门控

Entry SRAM 自身复位只负责两件事：一是 `extraReset` 触发后由 `SRAMTemplate` 自主按 `resetSet` 逐行写零；二是在 reset 扫描期间拉低读写请求 `ready`，使外部读写请求不会被 SRAM 接收。它不能自动清除 SRAM 外部已经存在的残留状态，也不能保证 `io.r.resp.data` 在 reset 写模式下具备合法读响应语义。

因此，刷新期间的污染防护不是只靠 Entry SRAM 自身完成，而是由以下四个层面共同保证。本节只给出全局视图，具体实现放在对应章节中展开。

**层面一：清空两个存储结构本身。** 这是刷新的基础动作，清除 T 拍之前已存入的旧上下文数据：

- Entry SRAM array 由 §4.1.2 启用 `extraReset`，复用 `SRAMTemplate` 的逐行清零机制；清零期间 SRAM 自身拉低读写 `ready`，隔绝外部新读写请求。
- `BypassShadowBuffer` 由 §4.2 在 `contextFlush` 当拍清空 `entries/statusEntries/enqPtr/deqPtr/priorityMask/a1_*` 等状态，使预测旁路命中和延迟写回中的旧 entry 一并失效。

但清空本身不阻断刷新窗口内的数据通路，需以下三层配合。

**层面二：阻止 Entry SRAM 非合法读出数据进入预测路径。** 复位期间，`SRAMTemplate` 不接受读请求。因此，`io.r.resp.data` 虽然仍然有信号输出，但它不是一次合法读请求的响应，该值不具备读响应语义，`io.r.resp.data.valid` 位也不能用于证明该响应有效。§4.4 通过**禁止刷新窗口内发起新的 SRAM 读请求意图**、**将 SRAM 读出路径强制置零**，阻断非合法 SRAM 读出数据和旧预测寄存器继续污染预测决策。

**层面三：阻止刷新期间对 BypassShadowBuffer 的入队。** 刷新窗口内的入队请求（写请求）会污染 buffer 的清零结果，刷新结束后进而污染 SRAM。uTAGE 的 entry 侧只有一个写来源（训练写），在 buffer 写请求 valid 的生成点与训练入口上用 `!bpuFlushing` 门控（§4.2.3、§4.5 策略二）。

**层面四：出队方向不设门控，由扫描覆盖、复位反压与空 buffer 保证。** 刷新期间不要求在出队方向额外增加安全门控，而是依赖“触发拍残留写回可覆盖 + 入队门控完备 + 复位反压 + buffer 已清空”保证安全：

- **触发拍残留写回可覆盖**：T 拍 `contextFlush` 拉高时，`SRAMTemplate` 内部 `resetState` 要到时钟沿后才生效，因此当拍组合路径上可能仍有一次旧 `tryWrite` 写入 Entry SRAM。该写入发生在 reset 扫描开始之前，后续逐行写零会覆盖其写入地址。
- **复位期反压**：T+1 起 Entry SRAM 处于 reset 扫描态，`SRAMTemplate` 拉低写请求 `ready`，并由 reset 写口接管 SRAM array，外部 `setIdx/data/waymask` 不会进入 SRAM。
- **窗口内 buffer 为空**：层面一在 T 拍清空既有 buffer 状态，层面三拦截窗口内新入队写；刷新结束写口恢复时，buffer 不存在旧 dirty entry 可出队。

四个层面合并后的时序覆盖如下：

- T 拍，`contextFlush` 触发 Entry SRAM `extraReset`，触发 `BypassShadowBuffer` 和预测/训练相关残留寄存器清空；组合路径上若仍有一次旧 buffer 写回，其写入早于 reset 扫描并会被后续覆盖；buffer 入队侧开始拦截窗口内写请求，T0/T1 训练源头被门控。
- T+1 起，Entry SRAM 处于 reset 扫描态并隔绝外部读写；`BypassShadowBuffer`被清空；SRAM 读出路径与 A1/A2/A3 预测残留被清空（已清空完成）；buffer 保持为空。
- Entry SRAM `resetDone` 置位后，写口恢复；由于 buffer 已清空且窗口内无新入队，不存在旧 dirty entry 可出队污染 SRAM。
- 聚合 `resetDone` 之后 `bpuFlushing` 撤销，各门控恢复，新上下文的预测读和训练写恢复正常流动。

---

### 4.2 BypassShadowBuffer

`BypassShadowBuffer` 不只是普通写缓冲，还参与预测读和训练读的旁路选择：buffer 中若有与当前 readIndex 相同的旧 entry，uTAGE 会优先使用 buffer 数据而非 SRAM 数据。因此只清 Entry SRAM 不清 buffer 会留下两个污染源：

- **预测旁路污染**：buffer 仍命中同 index，把旧 entry 旁路给预测路径；
- **延迟写回污染**：dirty 旧 entry 在刷新后继续写回 SRAM，把已清掉的表重新写脏，buffer 必须单独刷新。

#### 4.2.1 I/O 接口修改

BypassShadowBuffer 的刷新依赖 `contextFlush` 与 `bpuFlushing`：

* `MicroTageTable` 已在 §4.1.1 中新增并接收这两个信号，本节只需将其向下传递至 `BypassShadowBuffer`；
* `BypassShadowBuffer` 当前未提供 `contextFlush`/`bpuFlushing` 端口，**需新增**。

`BypassBufferIO` 同 §4.1.1：为 `BypassShadowBuffer` 的内部类、`extends MicroTageBundle`，可直接引用 `HasBpuFlush`，新端口按 00 §3.1 用 `Option.when` 声明，连线放入 `if (HasBpuFlush)`：

```scala
// MicroTageTable.scala -- 分发至 BypassShadowBuffer
if (HasBpuFlush) {
  wbuffer.io.contextFlush.get := contextFlush
  wbuffer.io.bpuFlushing.get  := bpuFlushing
}

// BypassShadowBuffer.scala
class BypassBufferIO extends MicroTageBundle {
  ...  // 原有端口
  val contextFlush: Option[Bool] = Option.when(HasBpuFlush)(Input(Bool()))
  val bpuFlushing:  Option[Bool] = Option.when(HasBpuFlush)(Input(Bool()))
}

// BypassShadowBuffer 模块体顶部解包中间变量，供 §4.2.2 / §4.2.3 / §4.3 消费
private val contextFlush = if (HasBpuFlush) io.contextFlush.get else false.B
private val bpuFlushing  = if (HasBpuFlush) io.bpuFlushing.get  else false.B
```

#### 4.2.2 清空 BypassShadowBuffer

buffer 的刷新在 `contextFlush` 当拍一次性清空以下状态：`entries` 使旧 entry 失效，避免继续被预测读或训练读旁路命中；`statusEntries.valid/dirty` 与 `enqPtr/deqPtr` 使队列恢复为空；`priorityMask/a1_chosenFirstMask/a1_firstHit/a1_entryHit` 清除命中选择流水残留；buffer 内部 T1 训练读 payload 寄存器清除已进入 T0->T1 路径、后续会参与 useful 更新和 entry 合并的旧状态。`t0_fire/t1_fire` 不作为 payload 清零，而是按 §4.5 在整个 `bpuFlushing` 窗口组合门控为 0。

```scala
// BypassShadowBuffer.scala
// 在原有 entries/statusEntries/enqPtr/deqPtr/priorityMask/a1_*/t1_*/forceWrite 的正常赋值逻辑之后，末尾追加此块
if (HasBpuFlush) {
  when(contextFlush) {
    entries       := 0.U.asTypeOf(entries)      // clear entries
    for (i <- 0 until numEntry) {               // clear valid and dirty
      statusEntries(i).valid := false.B
      statusEntries(i).dirty := false.B
    }
    enqPtr        := 0.U.asTypeOf(enqPtr)       // clear enqPtr and deqPtr
    deqPtr        := 0.U.asTypeOf(deqPtr)
    priorityMask       := 0.U.asTypeOf(priorityMask)   // clear hit-selection state

    a1_chosenFirstMask := 0.U.asTypeOf(a1_chosenFirstMask)
    a1_firstHit        := 0.U.asTypeOf(a1_firstHit)
    a1_entryHit        := 0.U.asTypeOf(a1_entryHit)

    t1_trainIndex     := 0.U.asTypeOf(t1_trainIndex)
    t1_hasHit         := false.B
    t1_microTageHit   := false.B
    t1_trainReadEntry := 0.U.asTypeOf(t1_trainReadEntry)
    t1_cleanId        := 0.U.asTypeOf(t1_cleanId)

    forceWrite         := false.B  // clear forceWrite (otherwise self-clears via enqPtr=deqPtr, but explicit is safer)
  }
}
// 原有正常出队、入队、dirty 清理、写回等逻辑保持原位不变（写请求 valid 按 §4.2.3 增加 bpuFlushing 门控）
```

#### 4.2.3 刷新期间的读写污染

`contextFlush` 只清当拍既有状态，窗口内仍需**阻止训练写重新入队**。`t1_update.valid` 与 `t1_alloc.valid` 是顶层送入 buffer 的两类写请求，`BypassShadowBuffer` 将二者合并为统一的 `writeBufferValid`；在该 valid 生成点追加 `!bpuFlushing`，即可使所有入队状态更新共享同一个门控结果，避免在三个状态写 `when` 上重复与运算。关闭开关时 `bpuFlushing=false.B`，经常量折叠恢复原始 valid；T0/T1 源头门控见 §4.5。

**出队方向不新增门控**，T 拍可能残留一次出队，其数据由 §4.1.3 的 Entry SRAM reset 扫描覆盖，且 T+1 起 buffer 已空、无新 dirty entry。

```scala
// BypassShadowBuffer.scala
private val doAlloc  = io.train.t1_alloc.valid
private val doUpdate = io.train.t1_update.valid
writeBufferValid := (doAlloc || doUpdate) && !bpuFlushing		// new: add flush

private val t1_hasWrite = writeBufferValid

when(t1_hasWrite) {
  entries(enqPtr.value) := newBufferEntry
  enqPtr                := enqPtr + 1.U
  priorityMask          := Fill(numEntry, 1.U(1.W)) >> ~enqPtr.value
}

// 标记新槽 valid/dirty（保持原式）
when(t1_hasWrite) {
  statusEntries(enqPtr.value).valid := true.B
  statusEntries(enqPtr.value).dirty := true.B
}

// 同 index 写合并清旧槽 dirty（保持原式）
when(t1_hasWrite && t1_hasHit) {
  statusEntries(t1_cleanId).dirty := false.B
}

// 写回方向保持原式，不增加 !bpuFlushing 门控；
// T 拍残留写回由后续 Entry SRAM reset 扫描覆盖
io.tryWrite.valid := statusEntries(deqPtr.value).valid && statusEntries(deqPtr.value).dirty
```

> **门控范围**：`newBufferEntry.entryData.valid`、`t1_hasWrite`、`needBypass` 与写请求性能计数器均直接或间接使用 `writeBufferValid`。刷新窗口内写请求 valid 为 0 后，这些写请求消费者自然不活动；并未对各消费者额外添加 `!bpuFlushing`。`usefulEntries` 更新不经过 `writeBufferValid`，由 §4.3.3 单独门控；`io.tryWrite.valid` 是 buffer 向 SRAM 的出队写回 valid，保持原式。

---

### 4.3 usefulEntries

`usefulEntries` 保存在 `BypassShadowBuffer` 内部的**寄存器数组**中，直接影响新 entry 分配。其残留值不会独立产生错误预测，但若 entry 已无效而 useful 仍非 0，新上下文将无法分配 entry，因此只清 Entry SRAM 不清 useful 会留下分配污染源，**useful 必须单独刷新**。

`usefulEntries` 与 `BypassShadowBuffer` 同处一个模块，共享相同的 I/O 接口，且本身是寄存器数组，可 1 拍清零。

#### 4.3.1 I/O 接口修改

与 §4.2.1 **完全一致**，无需额外新增。

#### 4.3.2 清空 usefulEntries

`usefulEntries` 是寄存器数组，`contextFlush` 当拍 1 拍清零。清零块放在 BypassShadowBuffer 模块体里、所有可能写 usefulEntries 的正常逻辑之后，利用 last-connect 优先级覆盖同拍可能存在的 useful 更新或衰减赋值：

```scala
// BypassShadowBuffer.scala
if (HasBpuFlush) {
  when(contextFlush) {                              // 新增 contextFlush 当拍清零
    usefulEntries := 0.U.asTypeOf(usefulEntries)
  }
}
```

#### 4.3.3 刷新期间的读写污染

`contextFlush` 当拍清零了 `usefulEntries`，但 Entry SRAM 的 `extraReset` 长达 128 拍；在整个刷新窗口内，训练 useful 更新和 `usefulReset` 衰减仍可能重新改写已清零的寄存器数组。因此，`usefulEntries` 的两条写路径均需用 `!bpuFlushing` 门控：

- **训练 useful 更新**：窗口内训练路径可能产生 useful 增减，把旧上下文的 useful 写回已清零的寄存器。由训练更新块的 `!bpuFlushing` 门控项屏蔽。
- **`usefulReset` 衰减**：窗口内若 `usefulReset` 触发，周期性衰减逻辑可能改写已清零的 `usefulEntries`。由 usefulReset 衰减块的 `!bpuFlushing` 门控项屏蔽。

```scala
// BypassShadowBuffer.scala
// 保留原两独立 when 块，仅在各自条件追加 !bpuFlushing；
// 关闭 HasBpuFlush 时 bpuFlushing=false.B，恢复原数据通路。

// 训练 useful 更新（块体不变，条件追加门控）
when((doAlloc || (io.train.t1_update.valid && io.train.t1_update.bits.usefulValid)) && !bpuFlushing) {
  t1_trainReadUseful := newUseful
}

// usefulReset 衰减（块体不变，条件追加门控）
when(io.usefulReset && !bpuFlushing) {
  for (bankIdx <- 0 until numBanks) {
    for (setIdx <- 0 until numSets / numBanks) {
      val entry = usefulEntries(bankIdx)(setIdx)
      if (tableId < NumTables / 2) {
        usefulEntries(bankIdx)(setIdx).value :=
          Mux(entry.value === 0.U, 0.U, entry.value - 1.U)
      } else {
        usefulEntries(bankIdx)(setIdx).value := entry.value >> 1.U
      }
    }
  }
}
```

---

### 4.4 防止预测路径污染刷新结果

uTAGE 预测侧是 ahead pipeline：顶层 A0 为每张表计算 `a0_readIndex`，当前代码固定 `t.req.valid := true.B`；`MicroTageTable` 内部又直接用 `bankOH` 发起 bank 读，未消费 `io.req.valid`。读回后 `readEntry` 在 buffer 命中时优先取 `BypassShadowBuffer`，否则取 SRAM 路径。

T 拍旧 A2/A3 结果至多残留一拍；T+1 起需切断新的旧信息输入，避免 SRAM reset 期间非合法 `io.r.resp.data` 或 buffer 旁路旧值继续进入 A2。

预测路径的刷新判断边界是：`contextFlush` 当拍允许旧上下文的预测结果在组合输出上短暂可见，关键要求是这些旧信息不能写回 uTAGE 的 Entry SRAM、`BypassShadowBuffer`、`usefulEntries` 等预测器存储结构，也不能在拍末后继续保留为可被新上下文消费的 uTAGE 内部状态。因此本节只清理/阻断会跨拍保留或可能间接驱动后续存储写入的预测侧残留。T 拍组合输出上的旧结果由同拍 BPU 顶层 redirect flush 丢弃（`utage.io.redirectValid := redirect.valid`，`Bpu.scala:262`；`s3/s2/s1_flush`，`Bpu.scala:305-307`），故无需额外 Mux `io.prediction` / `io.meta`；但 uTAGE 自身的清零不依赖该同拍关系（见策略一）。

**策略一：清空流水线中的旧信息**

在 `MicroTage` 内 `contextFlush` 拍末清零 A1 readIndex、A2 数据寄存器和 A3 override 缓存。`contextFlush` 不应依赖与 `redirectValid` 同拍才能清空 uTAGE 残留；因此 `a2_abtbHitVec` 必须由 `contextFlush` 显式清零，且清零优先级高于 `overrideValid` / `a1_fire` 的正常更新。

```scala
// MicroTage.scala
if (HasBpuFlush) { when(contextFlush) {
  a1_readIndex         := 0.U.asTypeOf(a1_readIndex)

  a2_readIndex         := 0.U.asTypeOf(a2_readIndex)
  a2_predRead          := 0.U.asTypeOf(a2_predRead)
  a2_posHitVec         := 0.U.asTypeOf(a2_posHitVec)
  a2_foldedPathHist    := 0.U.asTypeOf(a2_foldedPathHist)
  a2_fromAbtbPos       := 0.U.asTypeOf(a2_fromAbtbPos)
  a2_abtbUseTableIDVec := 0.U.asTypeOf(a2_abtbUseTableIDVec)
  a2_abtbTakenVec      := 0.U.asTypeOf(a2_abtbTakenVec)
  a2_abtbHitVec        := 0.U.asTypeOf(a2_abtbHitVec)

  a3_predRead          := 0.U.asTypeOf(a3_predRead)
  a3_readIndex         := 0.U.asTypeOf(a3_readIndex)
  a3_posHitVec         := 0.U.asTypeOf(a3_posHitVec)
} }
```

**策略二：阻断旧信息进入流水线**

窗口内禁止顶层发起新的 table 读，并让 `MicroTageTable` 的 bank 读请求真正受 `io.req.valid` 约束；同时把 SRAM 读出路径置零。buffer 旁路路径由 §4.2.2 清空 buffer、§4.2.3 门控入队保证为空。

```scala
// MicroTage.scala -- 原 t.req.valid := true.B
tables.zipWithIndex.foreach {
  case (t, idx) =>
    t.req.valid          := !bpuFlushing
    t.req.bits.readIndex := a0_readIndex(idx)
    ...
}

// MicroTageTable.scala -- 原 bank.io.r.req.valid := bankOH(bankIdx)
entrySram.zipWithIndex.foreach { case (bank, bankIdx) =>
  bank.io.r.req.valid       := bankOH(bankIdx) && io.req.valid
  bank.io.r.req.bits.setIdx := bankReadInnerIndex
}

private val sramReadEntry =
  if (HasBpuFlush) Mux(bpuFlushing, 
                       0.U.asTypeOf(new MicroTageEntry), 
                       bankReadEntries.asTypeOf(new MicroTageEntry))
  else bankReadEntries.asTypeOf(new MicroTageEntry)
```

`bpuFlushing` 在 `HasBpuFlush=false` 时为 `false.B`，上述门控与 Mux 经常量折叠恢复原始数据通路。

### 4.5 防止训练路径污染刷新结果

训练路径 `T0 -> T1 update/alloc -> BypassShadowBuffer -> Entry SRAM` 会写 uTAGE 状态。buffer 内部 T1 残留已由 §4.2.2 清空，buffer 入队与 useful 写已分别由 §4.2.3、§4.3.3 门控；本节处理 `MicroTage` 顶层 T1 payload、周期衰减计数器、顶层训练入口，以及 `BypassShadowBuffer` 内部训练 `_fire`。

**策略一：清空训练流水线中的旧信息**

`contextFlush` 当拍清零 `lowTickCounter/highTickCounter`，避免旧计数值在窗口内触发 `usefulReset`。`bpuFlushing` 全窗口清零 8 个会驱动 `t1_update/t1_alloc` 的顶层 T1 payload 寄存器；`t1_fire` 改由策略二的组合门控保证窗口内为 0。仅用于性能计数的 T1 payload 寄存器不写存储结构，保持原式。

```scala
// MicroTage.scala
if (HasBpuFlush) { when(contextFlush) {
  lowTickCounter  := 0.U
  highTickCounter := 0.U
} }

if (HasBpuFlush) { when(bpuFlushing) {
  t1_foldedPathHistForTrain := 0.U.asTypeOf(t1_foldedPathHistForTrain)
  t1_trainRead              := 0.U.asTypeOf(t1_trainRead)
  t1_trainResult            := 0.U.asTypeOf(t1_trainResult)
  t1_misPredProviderOH      := 0.U.asTypeOf(t1_misPredProviderOH)
  t1_needAlloc              := false.B
  t1_allocTaken             := false.B
  t1_allocCfiPosition       := 0.U.asTypeOf(t1_allocCfiPosition)
  t1_trainStartPc           := 0.U.asTypeOf(t1_trainStartPc)
} }
```

**策略二：阻断刷新窗口内的有效训练事务继续推进**

`MicroTage` 顶层将 `io.fastTrain.valid` 寄存一拍生成 T0 有效位；在 `RegNext` 输出侧追加 `!bpuFlushing` 组合门控得到 `t0_fire`，统一控制顶层 T1 装载并驱动各 table 的 `t0_trainIndex.valid`。`t1_fire` 同样在 `RegNext(t0_fire)` 输出侧追加门控。这样包括刷新触发拍在内，整个 `bpuFlushing` 窗口的 `t0_fire/t1_fire` 均为 0。

```scala
// MicroTage.scala
private val t0_fire = RegNext(io.fastTrain.get.valid, false.B) &&
  (if (HasBpuFlush) !bpuFlushing else true.B)

private val t1_fire = RegNext(t0_fire, false.B) && (if (HasBpuFlush) !bpuFlushing else true.B)

// BypassShadowBuffer.scala：模块边界再次门控，不仅依赖上游契约
private val t0_fire = io.train.t0_trainIndex.valid && (if (HasBpuFlush) !bpuFlushing else true.B)
private val t1_fire = RegNext(t0_fire, false.B) && (if (HasBpuFlush) !bpuFlushing else true.B)
```

这里阻断的是**有效训练事务及其状态副作用**，不是要求所有数据位停止翻转：`t0_train` 以及 `BypassShadowBuffer` 内部无使能的训练数据 `RegNext` 仍可能装载无效 bits，但顶层和 buffer 内部的 `t0_fire/t1_fire` 在窗口内均为 false，这些数据没有有效消费者，不会产生训练写，也不会触发由这些 `_fire` 驱动的性能计数或 trace。

T 拍 `bpuFlushing` 与 `contextFlush` 同时有效，因此四个训练 `_fire`——顶层 T0/T1 与 buffer 内部 T0/T1——当拍即组合为 0，并在 `bpuFlushing` 撤销前持续保持为 0；写侧 §4.2.3/§4.3.3 门控仍作为最终防线保留。uTAGE 训练路径只有 T0/T1，没有 `t2_fire`。`HasBpuFlush=false` 时所有门控项在 elaboration 期选择为 `true.B`，经常量折叠恢复原始数据通路和时序。

---

### 4.6 刷新完成信号 `resetDone`

uTAGE 顶层将 8 个 Entry SRAM 的 `resetDone` 组合相与得到 `io.sramResetDone`（现有代码），作为 uTAGE 向 BPU 顶层输出的完成信号 `io.resetDone` 的直接驱动源之一。

```scala
// MicroTage.scala
if (HasBpuFlush) {
  io.resetDone.get := io.sramResetDone && !contextFlush
}
```

`resetDone` 赋值为纯组合逻辑，随整块落入 `if (HasBpuFlush)`：关闭时 `resetDone` 端口不生成，无需 else 分支（00 §3.1）。`!contextFlush` 项天然屏蔽 T 拍 `sramResetDone` 的旧值高电平；T+1 起 SRAMTemplate 进入 reset 扫描，`io.sramResetDone` 拉低，直到 8 个 Entry SRAM 全部完成逐行清零。

与 BPU 顶层 `BpuFlushCtrl` 的契约对齐（01 §5.2.2、§5.3.4）：

- BPU 顶层在聚合阶段对各子预测器做 `!activeFlushMask(i) || p.io.resetDone.get` 与归约；uTAGE 的 mask 位固定为 1（§4.1.1），其 `resetDone` 恒参与聚合。
- 本预测器只需在收到有效 `contextFlush` 后拉低、本地清零完成后置位并保持至下一次有效 `contextFlush`（01 §5.1.3）。

逐拍时序（默认配置，2 表 × 4 bank = 8 个 SRAM 并行清零；每个 SRAM 的 `set = numSets / numBanks = 128`）：

- T 拍 `contextFlush` 拉高，`resetDone` 经 `!contextFlush` 项组合拉低（此拍 `io.sramResetDone` 仍可能为旧值高）；
- T+1 起 `io.sramResetDone = false`，8 个 Entry SRAM 并行逐行清零；
- T+129 `io.sramResetDone` 拉高（128 拍清零完成，`_resetState` 次拍转 `false`）；
- T+129 当拍 `resetDone` 组合拉高，BPU 顶层可在同拍完成聚合并迁出 `s_flushing`。

---

> **uTAGE 属 SRAM 型**：默认配置下 uTAGE Entry SRAM 每 bank 需 128 拍清零，`resetDone` 由 SRAM 完成信号主导。其寄存器结构（BypassShadowBuffer、usefulEntries、流水线寄存器）虽 1 拍清零，但聚合完成需等待 Entry SRAM 清零结束，因此 uTAGE 可能是聚合 `resetDone` 的最慢瓶颈（默认配置约 129 拍，见本节 §4.6；顶层聚合时序见 01 §2.3.2）。

---

### 4.7 编译开关裁剪清单

按 00 §3.4 的要求，`HasBpuFlush=false` 时以下 uTAGE 刷新专用结构必须从生成 RTL 中结构性消失（双配置 elaboration 见 00 §5.1，关闭配置 RTL 检查见 00 §5.2）：

| 类别                          | 应消失的对象                                                                                                                                                                | 对应小节         |
| :---------------------------- | :-------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | :--------------- |
| 端口                          | `BasePredictorIO` 的 `contextFlush`/`bpuFlushing`/`resetDone`（01 声明）；`MicroTageTableIO` 与 `BypassBufferIO` 的 `contextFlush`/`bpuFlushing`            | §4.1.1、§4.2.1 |
| 各级中间变量                  | `MicroTage`/`MicroTageTable`/`BypassShadowBuffer` 模块体顶部的 `contextFlush`/`bpuFlushing` 解包（关闭时退化为 Scala 常量 `false.B`，不生成硬件）               | §4.1.1、§4.2.1 |
| Entry SRAM 清零               | `extraReset = HasBpuFlush` 绑定下的 `extra_reset` 端口及 `entrySram` 的连线                                                                                           | §4.1.2          |
| Entry SRAM 读请求与读路径 Mux | table`req.valid` / bank `r.req.valid` 的刷新窗口门控，以及 `sramReadEntry` 的刷新 Mux（关闭时恢复原始读请求与 `bankReadEntries.asTypeOf(new MicroTageEntry)` 原式） | §4.4 策略二     |
| BypassShadowBuffer 清零       | `entries`/`statusEntries`/`enqPtr`/`deqPtr`/`priorityMask`/`a1_*`/buffer 内部 T1 payload/`forceWrite` 的 `when(contextFlush)` 清零块                    | §4.2.2          |
| buffer 入队门控               | `writeBufferValid := (doAlloc \|\| doUpdate) && !bpuFlushing` 中的门控项；三个入队状态写 `when` 与 `io.tryWrite.valid` 保持原式                                         | §4.2.3          |
| usefulEntries 清零与门控      | `usefulEntries` 的 `when(contextFlush)` 清零块；训练更新 / `usefulReset` 衰减块中的 `!bpuFlushing` 门控项                                                           | §4.3.2、§4.3.3 |
| 预测残留清理                  | A1 readIndex 及 A2/A3 寄存器的`when(contextFlush)` 清零块                                                                                                                 | §4.4 策略一     |
| 训练残留清理                  | 策略一：衰减计数器和顶层 T1 payload 清零；策略二：顶层 `t0_fire/t1_fire` 及 buffer 内部 T0/T1 `_fire` 均在寄存器输出侧做全窗口门控                         | §4.5            |
| 完成信号                      | `io.resetDone.get := io.sramResetDone && !contextFlush` 组合赋值                                                                                                          | §4.6            |

关闭后的数据通路必须与未引入刷新机制的原始 uTAGE 完全一致：`sramReadEntry = bankReadEntries.asTypeOf(new MicroTageEntry)`、`writeBufferValid` 经 `bpuFlushing=false.B` 常量折叠恢复 `doAlloc || doUpdate`，三个入队状态写 `when` 与 `io.tryWrite.valid` 保持原式、useful 更新与 `usefulReset` 衰减条件恢复原式、`io.sramResetDone` 聚合不变，不保留额外门控、延迟或默认值（00 §3.1）。

## 5. 应用刷新机制之后的预测流水线

逐拍追踪，假设 `contextFlush` 在 T 拍有效（1 cycle 脉冲），且之前 uTAGE 预测流水线正常工作。

- **T-1 拍**（`contextFlush` 未到）

  - A0 正常根据旧上下文 PC/历史读 Entry SRAM
  - A1/A2/A3 中可能保存旧上下文读出的 entry
  - 若 tag 和 position 命中，uTAGE 可能正常输出预测
- **T 拍**（`contextFlush` 有效）

  - Entry SRAM extraReset 被触发，但 SRAM 清零是逐行过程，当前拍读出的组合结果仍可能来自旧 entry
  - `SRAMTemplate.extra_reset` 在当拍置位内部 reset 状态；reset 写口从下一拍起接管 SRAM array 并 backpressure 读写请求
  - `BypassShadowBuffer` 在当拍清空；清空生效前若组合路径上仍有一次旧 `tryWrite` 写回，该写入会被后续 Entry SRAM reset 扫描覆盖
  - `usefulEntries` 当拍清零
  - A2/A3 寄存器在 T 拍末由 `contextFlush` 清零（§4.4 策略一），其中 `a2_abtbHitVec` 不依赖 `redirectValid`，而是与其它 A2 残留寄存器一同由 `contextFlush` 显式清零，不要求 `contextFlush` 与 `redirect.valid` 同拍
  - A3 override 缓存同步清零，旧读结果不会在后续 override 路径中复用
  - `lowTickCounter`/`highTickCounter` 在 T 拍末由 §4.5 策略一清零；`MicroTage` 顶层 T1 payload 由 §4.5 策略一清零，`BypassShadowBuffer` 内部训练读 T1 payload 由 §4.2.2 清零；顶层和 buffer 内部的 T0/T1 `_fire` 由 §4.5 策略二在 T 拍即门控为 0，写侧 §4.2.3/§4.3.3 门控继续作为最终防线
- **T+1 到 reset 完成前**

  - Entry SRAM 正在逐行清零，`sramResetDone = false`
  - `bpuFlushing = true`
  - 前端 PC 仍可以推进，但 table 读请求意图由 `!bpuFlushing` 门控为 false；SRAMTemplate reset 扫描态同时持续 backpressure，预测输出持续无效
  - `MicroTage` 顶层 T1 payload 由 §4.5 策略一持续清零；§4.5 策略二使顶层和 buffer 内部的 T0/T1 `_fire` 在整个窗口保持 0，各 table 不再收到有效 `t0_trainIndex`，不产生新的 buffer 写或 SRAM 写
  - `usefulReset` 和 useful 正常更新持续被屏蔽，`usefulEntries` 保持清零状态
- **reset 完成后**

  - Entry SRAM 中 entry 的 `valid` 已被清零
  - `BypassShadowBuffer` 为空，`usefulEntries` 为 0
  - 新上下文重新开始访问 uTAGE 时，旧 entry 不会命中；后续 entry 只能由新上下文训练重新分配

总结：刷新期间预测流水线的旧数据由 §4.4 策略一（`contextFlush` 清 A2/A3 寄存器）在 T 拍末清除，T+1 起的 SRAM 非合法读出值由 §4.4 策略二（`bpuFlushing` Mux SRAM 读响应）阻断；训练路径旧 payload 由 §4.5 策略一清顶层 T1、§4.2.2 清 buffer 内部 T1 共同处理，顶层和 buffer 内部全部 T0/T1 `_fire` 由 §4.5 策略二在包含 T 拍的整个刷新窗口门控为 0，计数器残留由 §4.5 策略一在 T 拍末清除；同时写侧最终门控、A3 override 和 buffer bypass 均保持有效，因此旧上下文不会污染刷新后的 uTAGE 状态。
