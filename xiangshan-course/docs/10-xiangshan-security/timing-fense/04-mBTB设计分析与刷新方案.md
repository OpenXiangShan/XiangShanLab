# 04-mBTB设计分析与刷新方案

> 本文档是 mBTB（`MainBtb`）的**真实刷新方案**，属于 [01-BPU刷新方案概览](01-BPU刷新方案概览.md) §6.1 占位阶段之后的落地内容：实现时用 §4.9 的真实完成条件替换 `MainBtb.scala` 中的 `io.resetDone.get := true.B` 恒真占位。
>
> 1. **接口契约**：`contextFlush`/`bpuFlushing`/`resetDone` 由 `BasePredictorIO` 以 `Option.when(HasBpuFlush)` 声明（01 §5.1.3），BPU 顶层已按本事务 `activeFlushMask` 门控后下发（01 §5.3.3），mBTB 收到的均为针对自身的有效信号。
> 2. **编译开关**：本文对 `contextFlush`/`bpuFlushing` 的消费遵循 [00-编译开关实现方案](00-BPU刷新机制编译开关实现方案.md) §3.1 统一编码规则与 01 §6.2、03 文档同款模板：各消费模块（`MainBtb`、`MainBtbAlignBank`、`MainBtbInternalBank`、`MainBtbReplacer`）在模块体顶部先把 Option 端口解包为中间变量（`private val contextFlush = if (HasBpuFlush) io.contextFlush.get else false.B` 等，`WriteBuffer`/`ReplacerState` 以自身 `hasContextFlush` 参数为条件），模块内统一引用变量名，门控不直接散布 `io.contextFlush.get` / `io.bpuFlushing.get`。刷新专用结构由 Scala `if` 在 elaboration 期裁剪；内联 Scala `if`（如 `x && (if (HasBpuFlush) !bpuFlushing else true.B)`）在关闭配置下选择 `true.B` 恒等项，新增门控由 Chisel/CIRCT 编译期常量折叠消除，最终生成 RTL 与整式 `if/else` 写法等价。Option 连线与输出端口驱动仍用 `if (HasBpuFlush) { ... .get ... }`。`HasBpuFlush=false` 时应消失的结构清单见 §4.10。
> 3. **刷新窗口**：`bpuFlushing` 覆盖 `contextFlush` 单拍至聚合 `resetDone` 置位、状态机离开 `s_flushing` 的整个窗口（01 §5.2.2、§5.4）。

---

## 1. mBTB 预测流水线

mBTB 预测流水线分 S0/S1/S2/S3 四拍：

- **S0 阶段**：mBTB 顶层由 startPc 计算出每个 AlignBank 的子起始地址，连同 `posHigherBits`、`crossPage` 一起向 AlignBank 发起读请求；AlignBank 内部根据 startPc 算出 `setIdx` 与 `internalBankIdx`，仅向命中的 InternalBank 发起读。InternalBank 把这份读请求同时广播给其下 4 个 EntrySRAM 与 1 个 CounterSRAM（5 个 SRAM 同拍并行启动）。
- **S1 阶段**：InternalBank 返回 4 路 entry 与 4 路 counter，AlignBank 通过 Mux1H 选中本拍命中 InternalBank 的读出数据；同时把每路 entry 的 position 与 `posHigherBits` 拼成完整 cfiPosition，===送出 `io.s1_positions` 给 TAGE==，用于 TAGE 的时序优化。
  - 由于 EntrySRAM 启用了 `holdRead = true`，无新读请求时输出端保持上次值。
- **S2 阶段**：对选中的 InternalBank 返回的 4 路读数据逐路做命中判定。命中的 way 直接消费对应 counter 的 `isPositive` 作为 `taken`。==命中结果连同 attribute、counter 等打包成 `metas`，随预测数据一并返回 mBTB 顶层==。S2 同时做 multi-hit 检测：若同一 set 内多路 `hitMask && position` 一致，则挑出多余的一路经 `flush.req` 向 EntryWriteBuffer 注入写零，把重复表项擦除。
- **S3 阶段**：用融合 mbtb + tage + sc 后的最终 `s3_takenMask` 触摸 Replacer——仅当存在任一实际跳转 way 时才触发 touch，把"实际跳转的命中 way"标记为最近使用；未跳转的条件分支 way 不参与 touch，全不跳转时整拍不 touch。

预测结果输出

```scala
pred.valid            := hit
pred.bits.cfiPosition := Cat(s2_posHigherBits, e.position)
pred.bits.target      := getFullTarget(s2_startPc, e.targetLowerBits, e.targetCarry)
pred.bits.attribute   := e.attribute
pred.bits.taken       := c.isPositive
```

---

## 2. mBTB 训练流水线

mBTB 训练流水线只有 T0/T1 两拍，涉及对 EntrySRAM / CounterSRAM / Replacer 的更新：

1. **T0 阶段**：接收训练请求，并提前一拍把训练 PC 的 `replacerSetIdx` 送进 Replacer 启动 victim 选择，供 T1 分配新表项时使用。
2. **T1 阶段**：以 `t1_train`（`RegEnable(t0_train, t0_fire)`）为核心数据源，生成三类写请求，互相独立、可同拍发起：
   - **Entry 写** — 条件：训练分支被识别为 mispredict 且属于本 AlignBank（`t1_needWrite && t1_mispredictInfo.valid`），并满足以下三种之一：
     - **分配新表项** — 训练分支在表中未命中（`!t1_hit`），用 Replacer 提供的 victim way 写入。
     - **修正间接跳转目标** — 训练分支命中且是 OtherIndirect 类型（`attribute.needIttage`），用命中 way 覆盖 target。
     - **修正属性变更** — 训练分支命中但 attribute 与预测快照不一致（疑似软件自修改代码），用命中 way 覆盖。
   - **Counter 写** — 覆盖的 way 来自两类互不冲突、可重叠的来源：
     - **伴随 Entry 覆盖复位** — 当某 way 被 Entry 写覆盖时，其 counter 重置为 `WeakPositive`（值 2）。
     - **条件分支结果训练** — 当某 way 是已解析的条件分支（`isConditional`）且其 position 与训练分支匹配时，按实际跳转结果 `meta.counter.getUpdate(actualTaken)` 增减。
   - **Replacer 写** — 条件：本拍真的发了 Entry 写（`t1_fire && t1_entryNeedWrite`），把命中/被替换的 way 标记为最近使用。
3. **WriteBuffer 出队**：Entry 写经自定义 **Entry WriteBuffer**（入口前打 1 拍）排队，Counter 写经标准 **Counter WriteBuffer**（`Queue(pipe=true, flow=true)`，入口前不打拍）排队。两者均在对应 SRAM 读端口空闲拍出队写入，==时机是可变延迟的==。

---

## 3. mBTB 整体架构

mBTB 采用**三维切分**：`AlignBank(2) × InternalBank(4) × Way(4)`，共 `8192` 个 entry，分布在 `NumSets = 8192 / 4 / 4 / 2 = 256` 个 set 上。每个 InternalBank 内部含 5 个独立 SRAM 实例（4 EntrySRAM + 1 CounterSRAM）与 1 个 EntryWriteBuffer + 1 个 CounterWriteBuffer；每个 AlignBank 顶层含 1 个 Replacer。层次如下：

```
mBTB (8192 entries)
 ├── AlignBank[0]                         ← 第1维：AlignBank (2个)
 │    ├── InternalBank[0]                 ← 第2维：InternalBank (4个，解决读写冲突)
 │    │    ├── EntrySRAM[way0]            ← 第3维：4 个独立 1-way EntrySRAM
 │    │    ├── EntrySRAM[way1]
 │    │    ├── EntrySRAM[way2]
 │    │    ├── EntrySRAM[way3]
 │    │    └── CounterSRAM (4-way)        ← 1 个，与 4 路 EntrySRAM 一一对应
 │    ├── InternalBank[1]                 ← 同 InternalBank[0]
 │    │    └── (4 × EntrySRAM + 1 × CounterSRAM)
 │    ├── InternalBank[2]
 │    ├── InternalBank[3]
 │    └── MainBtbReplacer                ← 每个 AlignBank 一个替换器
 │        └── ReplacerState                      ← 寄存器阵列，非 SRAM
 └── AlignBank[1]                         ← 同 AlignBank[0]
```

- **AlignBank 维**：处理器每次取指是一个 FetchBlock（64字节），而 mBTB 需要在一个 FetchBlock 内检测多条分支指令。为了支持同周期内对不对齐 PC 的并发读取和预测，模块将 FetchBlock 按 FetchBlockAlignSize（32字节）分成 2 个 AlignBank，**每个 AlignBank 独立并行查找**，可以在一个周期内同时查找 FetchBlock 中不同位置的分支。
- **InternalBank 维**：解决读写冲突和降低 SRAM 功耗。预测流水线需要读 SRAM，训练流水线需要写 SRAM，二者可能同时访问同一组。4 个 InternalBank 可以使得不同地址的读写大概率落在不同 bank 上，减少冲突。同时，每次预测只需激活 1/4 的 SRAM，降低功耗。
- **第3维（每个 InternalBank 内部）**：**每个 InternalBank 同时包含两类 SRAM**——**4 个独立的 1-way EntrySRAM**（每路独立读写端口）与 **1 个 4-way CounterSRAM**（4 路共用同一读写端口，通过 waymask 选通）。EntrySRAM 存储 Entry 表项（tag/属性/目标地址等），CounterSRAM 存储 4 路 Entry 各自对应的 2-bit 饱和计数器。两者**共享同一读请求**（同一 setIdx 同时送入 5 个 SRAM），但在写路径与写缓冲上各自独立。

| 资源                           | 单 InternalBank | 单 AlignBank | mBTB 顶层 |
| :----------------------------- | :-------------: | :----------: | :-------: |
| EntrySRAM (1-way)              |        4        |      16      |    32    |
| CounterSRAM (4-way)            |        1        |      4      |     8     |
| EntryWriteBuffer               |        1        |      4      |     8     |
| CounterWriteBuffer (`Queue`) |        1        |      4      |     8     |
| Replacer                       |       —       |      1      |     2     |

下面对每个存储结构按"数据结构 / 读写端口 / 复位机制"三段展开。

### 3.1 EntrySRAM

**EntrySRAM 的数据结构**

```scala
// MainBtbInternalBank.scala:90-105
new SRAMTemplate(
  new MainBtbEntry,
  set = NumSets,         // 256
  way = 1,               // 每路独立 SRAM
  singlePort = true,     // 单端口：读写互斥
  shouldReset = true,    // 上电逐行清零
  holdRead = true,       // 读数据保持到下次读
  withClockGate = true,  // 时钟门控省功耗
)
```

每个 1-way SRAM 中都包含 256 行，每一行都对应一个 mBTB Entry 表项，位宽为 **51 bits**。

```
|     tag     | attribute | position | targetCarry | targetLowerBits |
|   16 bits   |  8 bits   |  4 bits  |   3 bits    |    20 bits      |
```

| 字段            | 位宽      | 含义                                                                                     |
| --------------- | --------- | ---------------------------------------------------------------------------------------- |
| valid           | 0（派生） | 不占存储位，由`def valid = !attribute.isNone` 派生，`branchType === None` 即表项无效 |
| tag             | 16        | PC 的高位标签，与预测时的 getTag(startPc) 比较判断是否命中                               |
| attribute       | 8         | 分支属性，包含 branchType(4b) + rasAction(4b)，branchType=None 兼任无效标志              |
| position        | 4         | 分支在其所在 AlignBank 内的相对位置                                                      |
| targetCarry     | 3         | 目标地址的进/借位标志，`Fit=0`、`Overflow=1`、`Underflow=2`                        |
| targetLowerBits | 20        | 目标地址的低位部分                                                                       |

**读写端口**

- **读**：S0 拍由 AlignBank 经 `s0_internalBankMask` 选中一个 InternalBank，把 `s0_setIdx` 同时送进该 InternalBank 的 4 个 EntrySRAM 与 1 个 CounterSRAM（共 5 个 SRAM 同拍读）。EntrySRAM 启用 `holdRead = true`，无新读请求时输出端保持上次值。
- **写**：经 EntryWriteBuffer 在读端口空闲拍出队，每个 EntrySRAM 是单端口、读优先。三类写场景：

| 写场景                      | 入口条件                                                                    | 写入内容                                       |
| :-------------------------- | :-------------------------------------------------------------------------- | :--------------------------------------------- |
| 分配新表项                  | `t1_fire && t1_entryNeedWrite && !t1_hit`（`t1_entryWayMask = victim`） | 新 entry → victim way                         |
| 修正间接跳转目标 / 属性变更 | `t1_fire && t1_entryNeedWrite && t1_hit`                                  | 新 entry → 命中 way                           |
| Multi-Hit 清零              | `s2_fire && s2_multiHitMask.orR`（**不依赖 t1_fire**）              | `0.U.asTypeOf(MainBtbEntry)` → multiHit way |

**复位机制**

- 每个 EntrySRAM 实例化时 `shouldReset = true`，上电后由 `SRAMTemplate` 的 `_resetState/_resetSet` 自动逐行写零，**256 拍**完成（NumSets=256，每拍写一行）。复位期间 `io.r.req.ready = false`、`resetDone = false`。
- 当前**未启用 `extraReset`**，运行时无法二次清零。这是 4.2 节刷新方案要做的核心修改。

### 3.2 CounterSRAM

**CounterSRAM 的数据结构**

每个 InternalBank 仅有 1 个 4-way `SRAMTemplate`，行数同样是 `NumSets = 256`。每个表项是 `Vec(NumWay, TakenCounter)` = 4 × 2-bit = 8 bit 一行（实际由 SRAM 的 waymask 切成 4 个独立的 2-bit "数据列"）：

```scala
// MainBtbInternalBank.scala:108-119
private val counterSram = Module(new SRAMTemplate(
  TakenCounter(),
  set = NumSets,
  way = NumWay,
  singlePort  = true,
  shouldReset = true,
  holdRead    = true,
  withClockGate = true,
  ...
)).suggestName(s"mbtb_sram_counter_align${alignIdx}_bank${bankIdx}")
```

**读写端口**

- **读**：与 EntrySRAM 共享同一份 `read.req`（`(entrySrams :+ counterSram).foreach`），同拍输出全 4 way 的 counter。
- **写**：经 CounterWriteBuffer 在读端口空闲拍出队，使用 SRAM 的 `waymask` 选通本拍要更新的 way：

```scala
// MainBtbInternalBank.scala:156-160
counterSram.io.w.req.valid            := counterWriteBuffer.io.deq.valid && !counterSram.io.r.req.valid
counterSram.io.w.req.bits.data        := counterWriteBuffer.io.deq.bits.counters
counterSram.io.w.req.bits.setIdx      := counterWriteBuffer.io.deq.bits.setIdx
counterSram.io.w.req.bits.waymask.get := counterWriteBuffer.io.deq.bits.wayMask
```

**EntrySRAM 与 CounterSRAM 的差异速查**

| 维度                   | EntrySRAM                              | CounterSRAM                     |
| :--------------------- | :------------------------------------- | :------------------------------ |
| 实例数 / InternalBank  | 4 个 1-way                             | 1 个 4-way                      |
| 写选通                 | 哪个 SRAM 实例对应被写 way             | 同 SRAM 内`waymask` 选通      |
| 写场景数               | 3（含 Multi-Hit 清零）                 | 1（仅训练写）                   |
| 写缓冲类型             | 自定义`WriteBuffer`（入口前打 1 拍） | `Queue(pipe=true, flow=true)` |
| flush（multi-hit）入口 | 有，复用入队路径                       | 无                              |

**复位机制**

与 EntrySRAM 完全一致：上电 256 拍逐行清零；当前未启用 `extraReset`。

### 3.3 Entry WriteBuffer

**Entry WriteBuffer 的数据结构**

每个 InternalBank 对应一个 WriteBuffer，且该 WriteBuffer 实例化时规定：`numEntries = 4`、`numPorts = NumWay = 4`。因此，在 WriteBuffer 内部为每个 port 维护 `dirty / shadowValid / entries` 三个 `4 × 4` 二维寄存器数组，支持 hit-merge / re-write 等写比较逻辑。其余内容详见[[WriteBuffer设计分析]]

**读写端口**

**入队**发生在训练 T1 阶段，训练写与 Multi-Hit 清零写共用同一入队口。两者按 `wayMask` 路由到对应 port：训练写带真实 entry，清零写带全零 entry。若同拍同 set 冲突，训练写优先、清零写丢弃。

```scala
// MainBtbInternalBank.scala:164-190
entryWriteBuffer.io.write.zipWithIndex.foreach { case (bufWrite, i) =>
  val writeValid = writeEntry.req.valid && writeEntry.req.bits.wayMask(i)
  val flushValid = flush.req.valid && flush.req.bits.wayMask(i) && !conflict
  val valid      = writeValid || flushValid
  bufWrite.valid := RegNext(valid, false.B)                       // 入口 1 拍延迟
  bufWrite.bits.setIdx := RegEnable(Mux(writeValid, writeEntry.req.bits.setIdx, flush.req.bits.setIdx), valid)
  bufWrite.bits.entry  := RegEnable(Mux(writeValid, writeEntry.req.bits.entry,  0.U.asTypeOf(new MainBtbEntry)), valid)
}
```

==每个 port 的入队信号在进入 buffer 前先打 1 拍寄存器==（`RegNext` / `RegEnable`），这是 Entry WriteBuffer 与 Counter WriteBuffer 的关键时序差异。

**出队**在每个 EntrySRAM 读端口空闲的拍触发，把 buffer 中最早的有效请求写入对应 way 的 SRAM。出队时机可变延迟，详见 3.1 写路径。

**复位机制**

`dirty / shadowValid / entries` 均为 `RegInit` 寄存器数组，上电后 1 拍内全部清零。通用 `WriteBuffer` 在引入 03 §4.3 的 `hasContextFlush` 扩展前，仅有可选 `hasFlush` 清空 `dirty` 的路径；当前 mBTB 实例点既未绑定 `hasFlush`，也未绑定 `hasContextFlush`，因此该实例在基线中没有任何运行时清空端口。刷新方案只在实例点绑定 `hasContextFlush = HasBpuFlush`，不改变 `hasFlush` 的既有语义。

### 3.4 Counter WriteBuffer

**Counter WriteBuffer 的数据结构**

每个 InternalBank 一个 Counter WriteBuffer，是一个单入单出的 4 项 FIFO，所有 counter 写请求按到达顺序排队，先进先出。内部结构是"一个 RAM 数组 + 读写指针"：

  ┌──────────────────────────────────────────┐
  │  ram[0]  ram[1]  ram[2]  ram[3]          │  ← 存储体（Reg/Mem）
  │                                          │
  │  enq_ptr ──► 写入位置                     │
  │  deq_ptr ──► 读出位置                     │
  │  maybe_full ──► 区分队列空/满（ptr 相等时）│
  └──────────────────────────────────────────┘

- enq_ptr 指向下一个写入槽位，每入队一项自增（模 4）。
- deq_ptr 指向下一个读出槽位，每出队一项自增。
- 当 enq_ptr == deq_ptr 时，用 maybe_full 标志区分"空"和"满"。

每个表项是一个 `MainBtbCounterSramWriteReq`，包含三字段：

| 字段         | 位宽 | 含义                   |
| :----------- | :--: | :--------------------- |
| `setIdx`   |  8  | 写哪个 set             |
| `wayMask`  |  4  | 写哪些 way（可多选）   |
| `counters` | 4×2 | 各 way 的新 counter 值 |

Entry WriteBuffer 的表项：每项里装一个 MainBtbEntry（一个 way 的数据）。要写哪个 way，靠 wayMask 选 port——wayMask(i)=1 就把这项送进 port i 对应的 EntrySRAM i。所以一项只写一个 way。

Counter WriteBuffer 的表项：每项里装**Vec(4, TakenCounter)**——四个 way 的 counter 值全在里面。wayMask 是多选位掩码，比如 wayMask = 0b1010 表示同拍把 way 1 和 way 3 的 counter 都更新。所以一项可以一次写多个 way。

**空队列下入队数据可在当拍直达出队端**，入口无打拍——这是与 Entry WriteBuffer"入口前打 1 拍"的关键时序差异。

**读写端口**

- **入队**（无 1 拍延迟）：T1 阶段 counter 写请求直接进 `enq` 口，不经 `RegNext`。
- **出队**：CounterSRAM 无读请求时由 `deq.valid` 触发写，按 FIFO 顺序先进先出。出队时机可变延迟。

**复位机制**

`Queue` 的内部 `ram` 与读/写指针均为 `RegInit(0)`，上电 1 拍清零；标准 `Queue` 可经构造参数 `hasFlush` 启用 `flush` 端口，但当前 mBTB 实例未绑定，无运行时清空通道。这点与 EntryWriteBuffer 不同，详见 4.5 节。

### 3.5 Replacer

**数据结构**

每个 AlignBank 一个 `MainBtbReplacer`，由参数 `Replacer ∈ {"Lru", "Plru"}` 选择算法（默认 `"Lru"`）。

核心存储是其内部的 `ReplacerState`，主体仅是一个 `RegInit` 寄存器数组（每个 set 一项）：

```scala
private val stateBank       = Module(new ReplacerState(NumSets, predictStateGen.StateWidth))

// ReplacerState.scala:61
private val states = RegInit(VecInit(Seq.fill(NumSets)(0.U.asTypeOf(UInt(StateBits.W)))))
```

> **PLRU 与 LRU 的位宽差异**：
>
> - `PlruStateGen.StateWidth = NumWay - 1 = 3`
> - `LruStateGen.StateWidth = NumWay × (NumWay - 1) / 2 = 6`（三角矩阵，记录每对 way 的新旧次序）
>
> 但两者都是普通寄存器；区别仅在于"是否能直接 `:=  0.U` 清零"。**LRU 的 0 状态语义合法**：0 状态表示 way 0 最新、way 3 最旧，victim 选择退化为 way 3，与刚清空的 entry 配合无副作用；**PLRU 的 0 状态在 PLRU 树中表达"victim = way 0"**，与刚刚清空的 4 路 entry 共同作用时，刷新后第一次替换会偏向 way 0，这是可接受的退化（uBTB 4.2 节有相同结论）。

**读写端口**

- **预测路径**（S3）：存在实际跳转 way 时，把跳转 way 写回 `states`；训练 touch 与之冲突时训练优先。
- **训练路径**（T1）：发了 Entry 写时，把命中/被替换 way 写回 `states`。
- **victim 查询**（T0 提前读）：按训练 setIdx 读 `states` 选最不常用 way，打一拍供 T1 分配时用；若 T1/predict 同拍写同 set，T0 直消费其新状态避免 RAW。

**复位机制**

`states` 用 `RegInit` 初始化为 0，上电 1 拍生效。`ReplacerState` 基类已具备 `hasContextFlush` 扩展（03 §4.5 部署于基类），但 mBTB 实例点未绑定，当前无运行时清空通道；绑定方案见 §4.6。

---

## 4. 刷新方案

mBTB 涉及的存储结构多达 6 类，刷新窗口远比 aBTB（64 拍）与 uBTB（1 拍）长，mBTB 自身也达到 256 拍。mBTB 顶层的刷新流程由 BPU 下发的控制信号驱动、完成后回送握手，信号清单与生命周期详见 §4.1。各存储结构/流水线的清空机制概览如下：

| 模块                                       | 是否刷新 | 清空机制                                                                                                                                                                  |
| :----------------------------------------- | :------: | :------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| **EntrySRAM**                        |    是    | 启用`extraReset`，由 `contextFlush` 触发 256  拍逐行清零                                                                                                             |
| **CounterSRAM**                      |    是    | 启用`extraReset`，同上                                                                                                                                                  |
| **Entry WriteBuffer**                |    是    | 扩展`hasContextFlush`，`contextFlush` 当拍同时清 `dirty` 与 `shadowValid`；合并点 `valid` 以 `!bpuFlushing` 门控入队（出队不设门控，§4.2.3 层面四、§4.4.3） |
| **Counter WriteBuffer**（`Queue`） |    是    | 保留`Queue`，新增 `contextFlush` 端口清空 `enq_ptr/deq_ptr/maybe_full`；`enq.valid` 以 `!bpuFlushing` 门控入队（§4.5.3）                                       |
| **Replacer**                         |    是    | ReplacerState 开启`hasContextFlush`，`contextFlush` 1 拍清零 `states`                                                                                               |
| **预测流水线**                       |    -    | `contextFlush` 拍末清零 S1/S2/S3 寄存器                                                                                                                                 |
| **训练流水线**                       |    -    | 策略一：`!bpuFlushing` 门控 `t0_fire`/`t1_fire`，整窗 kill；策略二：`contextFlush` 当拍清零 4 个 T1 数据寄存器（MainBtb + AlignBank）（§4.8）                                           |

### 4.1 刷新接口生命周期

mBTB 的刷新接口与生命周期遵循 BPU 顶层约定：

- `contextFlush`/`bpuFlushing`/`resetDone` 三信号的定义与通用时序框架详见 01 §2.3.1、§2.3.2，三信号均为 `Option.when(HasBpuFlush)` 端口（01 §5.1.3）；
- mBTB 内部对三信号的消费遵循头注 2 的约定（模块体顶部中间变量 + `if (HasBpuFlush)` 守卫），裁剪清单见 §4.10。
- `sramResetDone` 语义详见 01 §5.3.4。；

mBTB 专属：40 个 SRAM 经 `extraReset` 逐行清零 **256 拍**

- T 拍 `contextFlush` 拉高后经 SRAM 内部 1 拍寄存器于 T+1 起进入复位态，256 拍扫描完成后 `sramResetDone` 于 T+257 拉高；
- 组合逻辑 `sramResetDone && !contextFlush` 在 T+257 同拍置位 `resetDone`（即 256 + 1 = 257，对应 01 §2.3.2 中的 k=257，与 aBTB 的"64 + 1 = 65"同构）；
- 具体信号生成逻辑与逐拍时序详见 §4.9。

---

### 4.2 EntrySRAM

#### 4.2.1 I/O 接口修改

Entry SRAM 的刷新依赖 `contextFlush` 与 `bpuFlushing`，`MainBtbAlignBank` 与 `MainBtbInternalBank` 需新增端口并逐层传递。两个信号继承自 `BasePredictorIO`（01 §5.1.3，已由 01 顶层 `mbtbFlushEnable` 门控）。

`MainBtbAlignBankIO` / `MainBtbInternalBankIO` 为模块内部类、直接 `extends Bundle`，**无需改父类或类签名**：

- 内部类可直接引用外部模块参数链上的 `HasBpuFlush`（经 `MainBtbModule` -> `BpuModule` -> `HasXSParameter` 混入，与既有端口位宽 `SetIdxLen`/`NumWay` 的引用方式同构）；
- 新端口按 00 §3.1 用 `Option.when` 声明，各级 Option 到 Option 分发连线放入 `if (HasBpuFlush)`（关闭时端口不生成，整段不连线，不经过中间变量）；
- 同时各消费模块在模块体顶部把 Option 端口解包为中间变量，**后续所有门控统一引用变量名**（同 `s0_fire`/`s2_flush` 等模块内信号的引用方式）：

```scala
// MainBtb.scala -- 顶层模块体顶部：中间变量（00 §3.1 / 01 §6.2），
// 后续 MainBtb 对两个门控信号的消费统一引用变量名（消费点仍处于 if 内）；
// else false.B 仅为 Scala 类型占位，关闭时不进入硬件
private val contextFlush = if (HasBpuFlush) io.contextFlush.get else false.B
private val bpuFlushing  = if (HasBpuFlush) io.bpuFlushing.get  else false.B

// MainBtb.scala -- 顶层分发至 AlignBank（Option 到 Option 连线）
if (HasBpuFlush) {
  alignBanks.foreach { b =>
    b.io.contextFlush.get := io.contextFlush.get
    b.io.bpuFlushing.get  := io.bpuFlushing.get
  }
}

// MainBtbAlignBank.scala -- IO 新增 + 模块体顶部中间变量 + 分发至 InternalBank
class MainBtbAlignBankIO extends Bundle {
  ...  // 原有端口
  val contextFlush: Option[Bool] = Option.when(HasBpuFlush)(Input(Bool()))
  val bpuFlushing:  Option[Bool] = Option.when(HasBpuFlush)(Input(Bool()))
}

private val contextFlush = if (HasBpuFlush) io.contextFlush.get else false.B
private val bpuFlushing  = if (HasBpuFlush) io.bpuFlushing.get  else false.B

if (HasBpuFlush) {
  internalBanks.foreach { b =>
    b.io.contextFlush.get := io.contextFlush.get
    b.io.bpuFlushing.get  := io.bpuFlushing.get
  }
}

// MainBtbInternalBank.scala -- IO 同样新增两个 Option 端口（叶节点，无需传递）；
// 模块体顶部同样解包中间变量，供 §4.2.2 / §4.3.2 / §4.4.3 / §4.5.3 消费
class MainBtbInternalBankIO extends Bundle {
  ...  // 原有端口
  val contextFlush: Option[Bool] = Option.when(HasBpuFlush)(Input(Bool()))
  val bpuFlushing:  Option[Bool] = Option.when(HasBpuFlush)(Input(Bool()))
}

private val contextFlush = if (HasBpuFlush) io.contextFlush.get else false.B
private val bpuFlushing  = if (HasBpuFlush) io.bpuFlushing.get  else false.B
```

#### 4.2.2 清空 EntrySRAM

启用 `SRAMTemplate` 的 `extraReset` 端口，由 `contextFlush` 触发 256 拍逐行清零：

```scala
// MainBtbInternalBank.scala 修改
private val entrySrams = Seq.tabulate(NumWay) { wayIdx =>
  Module(
    new SRAMTemplate(
     new MainBtbEntry,
      ...
      shouldReset = true,
      holdRead = true,
      extraReset  = HasBpuFlush,  // elaboration 期随编译开关裁剪：false 时 extra_reset 端口不生成
      ...
    )
  ).suggestName(s"mbtb_sram_entry_align${alignIdx}_bank${bankIdx}_way${wayIdx}")
}

// Option 端口驱动需 if；条件引用 InternalBank 顶部中间变量（§4.2.1）
if (HasBpuFlush) {
  entrySrams.foreach { s => s.extra_reset.get := contextFlush }
}
```

> `contextFlush` 仅持续 1 拍，而清零窗口长达 256 拍。后续门控使用 `bpuFlushing`。

#### 4.2.3 刷新期间的读写污染

> [!abstract] 与 aBTB 刷新方案比较
> 框架与 aBTB（03 §4.2.3）同构，二者写来源也相同（训练写 + multi-hit 零写），出队方向同样都不设门控（见层面四）。唯一结构差异在合并点位置：aBTB 的两写源在顶层就合并为单一 `io.writeReq`，bank 侧只能整体端口门控；mBTB 的两写源在 InternalBank 内部、入口寄存器之前合并，合并点可见，门控直接加在合并后的 `valid` 上。

EntrySRAM 与 EntryWriteBuffer 是 mBTB entry 侧的两个核心存储结构（CounterSRAM / CounterWriteBuffer 见 §4.3、§4.5，ReplacerState 见 §4.6）。刷新方案从四个层面保证二者在刷新窗口（T ~ `聚合resetDone`）内**不接收旧上下文数据、不向外流出旧上下文数据**：

**层面一：清空两个存储结构本身。** 这是刷新的基础动作，清除 T 拍之前**已存入的旧上下文数据**：

- EntrySRAM 复用 `extraReset` 机制，由 `io.contextFlush` 触发 256 拍逐行清零（§4.2.2），清零期间 SRAM 天然隔绝新读写；
- EntryWriteBuffer 由 `io.contextFlush` 一拍清空 `dirty` + `shadowValid`（§4.4.2），滞留的残留写请求随之全部失效。

但清空本身不阻断窗口内的数据通路，需以下三层配合。

**层面二：阻止 EntrySRAM `holdRead` 旧值流出。** EntrySRAM 输出寄存器中的旧表项若放任流出，会在 S2 拍虚假命中。§4.7 策略二以**禁止发起读请求** + **S1 数据强制置零**，阻断了 holdRead 旧值流入预测决策。

**层面三：阻止刷新期间对 EntryWriteBuffer 的入队。** 窗口内入队的旧写请求会污染 buffer 的清零结果，刷新结束后进而污染 SRAM。两个写来源（训练写 + multi-hit 零写）在合并点 `valid`（入口寄存器之前）用 `!io.bpuFlushing` 统一门控（§4.4.3）：

- **训练写**：真实 entry，写入已清零 SRAM 直接污染清零结果；训练写的生成源头由 §4.8 双策略阻断（T+1 起不再产生新训练写）。
- **multi-hit 零写**：写入 SRAM 的数据虽与清零幂等，但**入队本身不是无操作**--它会占用 slot、置位 `dirty`/`shadowValid`、touch 内部 PLRU，这些微架构状态跨刷新窗口存活，与其逐一论证残留无害，不如统一拦截；丢弃零写无功能损失，multi-hit 修复所针对的重复表项正是本次清零要清除的对象。

门控位于入口寄存器`RegNext` 之前，T 拍源端拦截后 T+1 也不会入队；T-1 拍发起、T 拍到达入队口的写由 `contextFlush` 当拍清空覆盖（03 §4.3.2）。

**层面四：出队方向不设门控，由复位反压与空 buffer 保证。** mBTB 的出队路径（`bufRead.ready`，读优先单端口）不新增门控，aBTB 亦然（03 §4.2.3 层面四），两方案统一以"入队门控完备 + 复位反压 + 扫描覆盖"保证出队安全：

- **复位期反压**：T+1 ~ T+256 EntrySRAM 处于复位态，写口 `ready` 为低，buffer 无从出队；
- **窗口内 buffer 为空**：层面一清空 + 层面三在合并点拦截全部写来源，T+257 写口恢复时无数据可出队。T 拍当拍（清空生效前）的残留排空写在逐行清零开始前提交，必被 T+1 起的扫描覆盖。

四个层面合并后的时序覆盖：

- T 拍，合并点门控统一拦截当拍残留写（训练写 / multi-hit 零写），`contextFlush` 当拍清空 EntryWriteBuffer 与 S1/S2/S3 寄存器；
- T+1 起，EntrySRAM 复位态隔绝读写，holdRead 出口被封死，`t1_train` 清零使训练写源头断绝，S1/S2 清零与 S0 读门控使 multi-hit 不再产生，buffer 保持为空；
- T+257 `sramResetDone` 置位、写口恢复，buffer 无数据可出队；
- `聚合resetDone` 之后 `bpuFlushing` 撤销，各门控恢复，新上下文的读写恢复正常流动。

门控不区分数据的上下文归属，窗口内到达的所有写请求（含新上下文的早期训练）一律被丢弃，这是刷新窗口的固有代价。

---

### 4.3 CounterSRAM

CounterSRAM 需要刷新，其残留值不会独立产生错误预测（受 EntrySRAM 命中门控），但仍需清空以防 EntrySRAM 中新分配表项后立即读到旧计数器值。

CounterSRAM 与 EntrySRAM 同处一个 `MainBtbInternalBank`，共享 §4.2.1 传递的 `contextFlush`/`bpuFlushing`、共享同一份 `read.req`、`NumSets` 同为 256，因此**刷新机制与 §4.2 完全一致**，下文仅述差异。

#### 4.3.1 I/O 接口修改

与 §4.2.1 **完全一致**（共享同一 `MainBtbInternalBank` 的 `contextFlush`/`bpuFlushing` 端口），无需额外新增。

#### 4.3.2 清空 CounterSRAM

同 §4.2.2：**新增`extraReset = HasBpuFlush`**，由 InternalBank 顶部中间变量 `contextFlush` 触发 256 拍逐行清零，仅 SRAM 参数不同，代码不重复。

清零窗口与 EntrySRAM 严格对齐；`counterSram.io.resetDone` 一并纳入顶层 `sramResetDone` 归约（§4.9）。

#### 4.3.3 刷新期间的读写污染

> [!abstract] 与 entry 侧（§4.2.3）比较
> 四层面框架相同，出队方向同样不设门控。差异有三：
>
> - **写来源单一** ：仅训练 counter 写，无 multi-hit 零写路径（multi-hit 修复只写 entry，counter 命中与否由 entry 侧控制）；
> - **buffer 类型不同** ：标准 `Queue` 以 `hasFlush` 清内部指针（§4.5.2），而非 `WriteBuffer` 基类的 `dirty`/`shadowValid`；
> - **无合并点**：单一写来源直达 `enq` 口，门控直接加在 `enq.valid` 上。

CounterSRAM 与 CounterWriteBuffer 是 mBTB counter 侧的两个核心存储结构。刷新方案从四个层面保证二者在刷新窗口（T ~ `聚合resetDone`）内**不接收旧上下文数据、不向外流出旧上下文数据**：

**层面一：清空两个存储结构本身。**

- CounterSRAM 复用 `extraReset`，由 `contextFlush` 触发 256 拍逐行清零（§4.3.2），清零窗口与 EntrySRAM 严格对齐，`counterSram.io.resetDone` 一并纳入 `sramResetDone` 归约；
- CounterWriteBuffer 由 `contextFlush` 一拍清空（`hasFlush` 复位内部指针），队列回到空状态，`ram` 残留被空状态封锁（§4.5.2）。

**层面二：阻止 CounterSRAM `holdRead` 旧值流出。** CounterSRAM 与 EntrySRAM 共享同一份 `read.req`，输出寄存器同样存在 holdRead 旧值问题。§4.7 策略二的 S0 读门控与 S1 置零同时覆盖 entry 与 counter（`s1_rawCounters` 一并强制置零），本层面与 entry 侧完全一致，无需差异处理。

**层面三：阻止刷新期间对 CounterWriteBuffer 的入队。** counter 写只有一个来源--训练路径的 `t1_counterNeedWrite`，无合并点，门控直接加在 `enq.valid` 上（§4.5.3）：

- T 拍 `t1_train` 尚未被清零（§4.8 在 T+1 生效），当拍残留 counter 写由 `!bpuFlushing` 在 `enq.valid` 拦截；即使漏拦，`hasFlush` 的 "先入队再清零" 语义也会将同拍入队一并丢弃（§4.5.2），门控使 "窗口内 buffer 零入队" 成为全窗口硬保证；
- T+1 起 `t1_train` 已被 §4.8 策略一清零、策略二又封死装载，`t1_counterNeedWrite` 必为 false，不再产生新 counter 写（§4.8）。

**层面四：出队方向不设门控，由复位反压与空 buffer 保证。** counter 出队口（`counterWriteBuffer.io.deq`，读优先单端口）不新增门控，与 entry 侧同理：T+1 ~ T+256 CounterSRAM 处于复位态，写口 `ready` 为低，buffer 无从出队；T 拍当拍（清空生效前）的残留排空写在逐行清零开始前提交，必被 T+1 起的扫描覆盖；T+257 写口恢复时，层面一清空 + 层面三封死入队使 buffer 为空，无数据可出队。`flow=true` 的空队列旁路亦无从发生--旁路的前提是当拍有入队，而入队已被层面三拦截。

四个层面合并后的时序覆盖与 §4.2.3 完全一致（T 拍拦截与清空 / T+1 起复位态隔绝与源头断绝 / T+257 写口恢复时空 buffer / `聚合resetDone` 后各门控恢复），此处不重复。

---

### 4.4 Entry WriteBuffer

`contextFlush` 之前已经进入 EntryWriteBuffer 且 `dirty = true` 的写请求，会在 SRAM 清零完成后陆续出队，把旧 entry 写回已清零的 SRAM。这会污染清零结果，因此 **buffer 残留必须清空**。

**mBTB 的 EntryWriteBuffer 与 aBTB 的 WriteBuffer 使用同一个 `WriteBuffer` 基类**，`hasContextFlush` 参数、`contextFlush` 端口、清空逻辑均完全一致，详见 03 SPEC §4.3.1-§4.3.2。

本节仅述 mBTB 的差异点。

#### 4.4.1 I/O 通路

`EntryWriteBuffer` 与 `EntrySRAM` 同处一个 `MainBtbInternalBank` 内，直接复用 §4.2.1 打通的 bank 级 `io.contextFlush`，**无需再新增 IO 端口**。

`EntryWriteBuffer` **通过 `hasContextFlush` 参数开启刷新机制**，该参数在 `WriteBuffer` 基类内完成（03 §4.3.1 已部署），此处直接使用即可。`hasContextFlush` 为 elaboration 期常量，在 mBTB 实例化处绑定 `HasBpuFlush`，模块内部以该参数为条件解包中间变量、不直接引用 `HasBpuFlush`，因此不影响其他实例（同 03 §4.3.1）：

```scala
// MainBtbInternalBank.scala
private val entryWriteBuffer = Module(new WriteBuffer(
  new MainBtbEntrySramWriteReq,
  ...
  hasContextFlush = HasBpuFlush,  // 关闭时端口不生成
  ...
))
if (HasBpuFlush) {
  entryWriteBuffer.io.contextFlush.get := contextFlush
}
```

#### 4.4.2 清空逻辑

现有 `flush` 端口只清 `dirty` 不清 `shadowValid`，专为 `mispredict` 场景的 "复用 SRAM 数据" 而设计。上下文切换场景下 EntrySRAM 已全清零，若 `shadowValid` 残留会让新上下文的写请求命中残留缓存且数据匹配，WriteBuffer 会误判 "无需回写" 导致丢失更新，因此 `contextFlush` 必须**同时清 `dirty` 与 `shadowValid`**。

**清空逻辑与 aBTB 完全一致**（03 SPEC §4.3.2），此处不重复。

#### 4.4.3 刷新期间的读写污染

`contextFlush` 已清空 buffer 内残留，但它是单拍脉冲，无法阻止窗口内写路径重新入队，因此**入队需用 `!bpuFlushing` 门控**。mBTB 的两个写来源（训练写 + multi-hit 零写）在合并点汇合，门控直接加在合并后的 `valid` 上，一个与项统一拦截：

```scala
// MainBtbInternalBank.scala 修改
val writeValid = writeEntry.req.valid && writeEntry.req.bits.wayMask(i)
val flushValid = flush.req.valid && flush.req.bits.wayMask(i) && !conflict
val valid      = (writeValid || flushValid) && (if (HasBpuFlush) !bpuFlushing else true.B)  // 合并点统一门控：训练写 + multi-hit 写
```

两写来源为何都必须拦截、拦截轨迹与出队方向不设门控的分析见 §4.2.3 层面三与层面四，此处不重复。

---

### 4.5 Counter WriteBuffer

`contextFlush` 之前进入 CounterWriteBuffer 的 counter 写请求，会在 SRAM 清零完成后陆续出队，把旧 counter 值写回已清零的 SRAM，污染清零结果，因此**buffer 残留必须清空**。

CounterWriteBuffer 是标准 `Queue`，与 EntryWriteBuffer有三点差异，决定了其刷新方案不同：

- 无内建 `contextFlush` 端口，改用 `Queue` 自带的 `hasFlush` 构造参数（Chisel 7.13.0）；
- 入口前无 `RegNext` 打拍（§3.4）；
- `flow=true` 允许空队列零延迟旁路（§3.4）。

#### 4.5.1 I/O 接口与信号传递

CounterWriteBuffer 的 `contextFlush` 与 EntryWriteBuffer 共享同一通路，无需新增 I/O 通路，仅需启用 `Queue` 的 `hasFlush` 端口：

```scala
// MainBtbInternalBank.scala 修改
private val counterWriteBuffer = Module(new Queue(
  new MainBtbCounterSramWriteReq,
  WriteBufferSize,
  pipe = true,
  flow = true,
  hasFlush = HasBpuFlush  // elaboration 期随编译开关裁剪：false 时 flush 端口不生成
))

if (HasBpuFlush) {
  counterWriteBuffer.io.flush.get := contextFlush
}
```

#### 4.5.2 CounterWriteBuffer 清空机制

Queue 内部以 `enq_ptr`、`deq_ptr`（各为内含 `RegInit` 寄存器的 `Counter`）与 `maybe_full`（`RegInit(false.B)`）三个寄存器维护状态。`hasFlush = true` 启用后，Queue 内部会在对应的 `flush` 信号拉高时将三个寄存器全部复位到初始值。全程**零库修改**，仅在实例化处绑定编译开关（§4.5.1）。

与 EntryWriteBuffer 的机制差异：EntryWriteBuffer 扩展 `WriteBuffer` 基类清 `dirty + shadowValid`，CounterWriteBuffer 用 `Queue` 内建 `hasFlush`；效果一致，均 1 拍清除残留。

**同拍入队语义**：Queue 源码注释明确 "Semantically, any enqueues happen before the flush"，且 flush 块位于入队块之后（last-connect 生效）--`io.enq.fire` 与 `flush` 同拍时，本拍入队的数据会被一并丢弃。因此 `contextFlush` 当拍同拍入队的旧上下文 counter 写也由 flush 覆盖，`enq.valid` 路径无需额外门控 `!contextFlush`；T+1 之后的入队由 §4.5.3 的 `!bpuFlushing` 阻断。

#### 4.5.3 刷新期间的读写冲突

T 拍 `t1_train` 尚未被清零（§4.8 在 T+1 才生效），T1 路径仍可能发出 counter 写；T 拍已入队残留（含同拍入队）已由 §4.5.2 的 `hasFlush` 清空。为拦截 T 拍残留写并提供全窗口硬保证，用 `!bpuFlushing` 门控入队：

```scala
// MainBtbInternalBank.scala 修改
counterWriteBuffer.io.enq.valid := writeCounter.req.valid && (if (HasBpuFlush) !bpuFlushing else true.B)
```

---

### 4.6 Replacer

EntrySRAM 清空后所有表项 `valid = 0`，训练请求必然 miss，触发新表项分配。victim 选择由 `stateBank` 提供，若 PLRU/LRU 状态残留旧上下文，新上下文第一次分配会受影响，因此必须刷新。

#### 4.6.1 I/O 接口与信号传递

`MainBtbAlignBank` 的 `contextFlush` / `bpuFlushing` 端口已在 §4.2.1 中新增并由 `MainBtb` 顶层传递。

本节只需在 `MainBtbReplacer` **新增 `contextFlush` 端口**，并由 `AlignBank` 传递至 `Replacer` 再到 `ReplacerState`。`bpuFlushing` 不下发到 `Replacer`，留在 `AlignBank` 层用于门控 touch 使能（见 §4.6.3）。

`MainBtbReplacerIO` 同 §4.2.1：为模块内部类、直接 `extends Bundle`，无需改父类或类签名，内部类直接引用 `HasBpuFlush`，新端口按 00 §3.1 用 `Option.when` 声明；两级连线均放入 `if (HasBpuFlush)`，`MainBtbReplacer` 模块体顶部同样解包中间变量：

```scala
// MainBtbAlignBank.scala -- 分发 contextFlush 至 Replacer（Option 到 Option 连线）
if (HasBpuFlush) {
  replacer.io.contextFlush.get := io.contextFlush.get
}

// MainBtbReplacer.scala -- IO 新增 contextFlush
class MainBtbReplacerIO extends Bundle {
  ...  // 原有端口
  val contextFlush: Option[Bool] = Option.when(HasBpuFlush)(Input(Bool()))
}

// 模块体顶部中间变量（同 §4.2.1 范式）
private val contextFlush = if (HasBpuFlush) io.contextFlush.get else false.B

// 传递至 ReplacerState
if (HasBpuFlush) {
  stateBank.io.contextFlush.get := contextFlush
}
```

#### 4.6.2 Replacer 刷新机制

mBTB 与 aBTB 共用 `ReplacerState`，其 `hasContextFlush` 扩展（参数 + 端口 + 1 拍清零所有 set 的 `states`）由 03 SPEC §4.5.1-§4.5.2 统一部署，**04-mBTB 直接复用**，仅在实例点绑定编译开关：

```scala
// MainBtbReplacer.scala
private val stateBank = Module(new ReplacerState(NumSets, predictStateGen.StateWidth, hasContextFlush = HasBpuFlush))
```

#### 4.6.3 刷新期间的防污染策略

`contextFlush` 在 T 拍清零 `states`（T+1 生效）。窗口内（T ~ `聚合resetDone`）predict/train 流水线中在途的请求仍可能产生 touch，把旧上下文的访问历史写入已清零的 `states`，污染新上下文的 victim 选择。**用 `bpuFlushing` 门控 predict/train 的 touch 使能**即可：

```scala
// MainBtbAlignBank.scala 修改
replacer.io.predict.touch.valid := s3_fire && s3_takenMask.reduce(_ || _) && (if (HasBpuFlush) !bpuFlushing else true.B)
replacer.io.train.t1_touch.valid := t1_fire && t1_entryNeedWrite && (if (HasBpuFlush) !bpuFlushing else true.B)
```

**T0 victim 读无需门控**：`replacer.io.train.t0_setIdx` / `t0_fire` 只是读 `states` 选 victim（`MainBtbReplacer.scala:78, 97`），不写状态。`t0_fire` 被 §4.8 策略二门控后，t0 读出的 victim 不会被 t1 装载消费，读出的旧上下文 victim 无害，故 t0 的 replacer 读通路不动。

### 4.7 如何防止预测路径污染刷新结果

预测路径有两类副作用：S3 Replacer touch（已由 §4.6.3 门控）与 S2 Multi-Hit 清零写（已由 §4.4.3 的合并点门控拦截），T 拍用旧 S2 数据各至多触发一次。T+1 起需切断数据输入，防止残留数据继续产生 touch 与 multi-hit：

**策略一：清空流水线中的旧信息**

在 `MainBtbAlignBank` 内 `contextFlush` 拍末清零 S1/S2/S3 寄存器，下一拍起无残留。清零块整块落入 `if (HasBpuFlush)`（01 §6.2 模板 (a)，关闭时不生成；`contextFlush`/`bpuFlushing` 为 §4.2.1 AlignBank 顶部中间变量）。s1_startPc / s2_startPc 无需清零。

```scala
if (HasBpuFlush) { when(contextFlush) {
  // S1
  s1_posHigherBits    := 0.U               // UInt(AlignBankIdxLen.W)
  s1_crossPage        := false.B           // Bool
  s1_internalBankMask := 0.U               // UInt(NumInternalBanks.W)
  // S2
  s2_posHigherBits    := 0.U               // UInt(AlignBankIdxLen.W)
  s2_crossPage        := false.B           // Bool
  s2_internalBankMask := 0.U               // UInt(NumInternalBanks.W)
  s2_rawEntries       := 0.U.asTypeOf(s2_rawEntries)   // Vec(NumWay, MainBtbEntry)
  s2_rawCounters      := 0.U.asTypeOf(s2_rawCounters)  // Vec(NumWay, SaturateCounter)
  // S3
  s3_replacerSetIdx   := 0.U               // UInt(SetIdxLen.W)
} }
```

**策略二：阻断旧信息进入流水线**

窗口内禁止 S0 发读请求、并把 S1 Mux1H 输出强制置零，杜绝 holdRead 旧值流入（门控均引用 §4.2.1 AlignBank 顶部中间变量，模板 (b)/(c)，关闭时恢复原式）：

```scala
// MainBtbAlignBank.scala 修改
internalBanks.zipWithIndex.foreach { case (b, i) =>
  b.io.read.req.valid := s0_fire && s0_internalBankMask(i) && (if (HasBpuFlush) !bpuFlushing else true.B)
}

private val s1_rawEntries = if (HasBpuFlush) Mux(bpuFlushing,
  0.U.asTypeOf(Vec(NumWay, new MainBtbEntry)),
  Mux1H(s1_internalBankMask, internalBanks.map(_.io.read.resp.entries))
) else Mux1H(s1_internalBankMask, internalBanks.map(_.io.read.resp.entries))

private val s1_rawCounters = if (HasBpuFlush) Mux(bpuFlushing,
  VecInit.fill(NumWay)(TakenCounter.Zero),
  Mux1H(s1_internalBankMask, internalBanks.map(_.io.read.resp.counters))
) else Mux1H(s1_internalBankMask, internalBanks.map(_.io.read.resp.counters))
// 后续 s2_rawEntries <= RegEnable(s1_rawEntries, s1_fire) 等
```

注：`s1_internalBankMask` 与其他 S1 寄存器一样只在 `contextFlush` 拍清零；刷新窗口内每拍仍会由 `s0_fire` 重装载。由于策略二已将 S1 Mux 输出置零，窗口内该 mask 不会消费旧 SRAM 行。`bpuFlushing` 释放拍，Mux 放开而 SRAM `holdRead` 仍保持刷新前最后一行，可能形成一拍“新 mask + 旧行”的错配并造成一次误预测；本方案接受该边界误预测。下一拍起，mask 与新读响应重新对齐。

### 4.8 如何防止训练路径污染刷新结果

与 uBTB（02 §4.4）/ aBTB（03 §4.7）一致，双策略：

1. **策略一（清残留）**：用 `contextFlush` 当拍清零训练流水线中的 4 个 T1 数据寄存器。
2. **策略二（阻断推进）**：用 `!bpuFlushing` 门控 `t0_fire` 和 `t1_fire`，使刷新窗口内所有训练级有效 fire 均为 false；

**策略一：清空训练流水线中的旧信息**

`contextFlush` 当拍清零 t1 全部 4 个数据寄存器：1）MainBtb 顶层 `t1_train`/`t1_rotator`/`t1_startPcVec`（`MainBtb.scala:148-151`）；2）MainBtbAlignBank 的 `t1_victimMask`（`MainBtbAlignBank.scala:221`，其余 `t1_*` 均为线网，无需处理）。

清零块位于 `RegEnable` 声明之后，last-connect 覆盖装载：

```scala
// MainBtb.scala
if (HasBpuFlush) { when(contextFlush) {
  t1_train      := 0.U.asTypeOf(t1_train)
  t1_rotator    := 0.U.asTypeOf(t1_rotator)
  t1_startPcVec := 0.U.asTypeOf(t1_startPcVec)
} }

// MainBtbAlignBank.scala
if (HasBpuFlush) { when(contextFlush) {
  t1_victimMask := 0.U.asTypeOf(t1_victimMask)
} }
```

**策略二：阻断旧信息进入流水线**

`!bpuFlushing` 同时门控 `MainBtb` 顶层的本地 `t0_fire` Wire 与 `t1_fire`，保证整个刷新窗口内两个训练级有效 fire 均为 false：

```scala
// MainBtb.scala 修改
t0_fire := io.stageCtrl.t0_fire && io.enable && (if (HasBpuFlush) !bpuFlushing else true.B)

private val t1_fire = RegNext(t0_fire, init = false.B) && io.enable && (if (HasBpuFlush) !bpuFlushing else true.B)
```

注意这里的 `io.stageCtrl.t0_fire` 是 `MainBtb` 顶层输入；被门控的是本地 Wire `t0_fire`。该 Wire 随后下发为 `MainBtbAlignBank.io.stageCtrl.t0_fire`，因此 `MainBtbReplacer` 内部的 `RegEnable(trainState, io.train.t0_fire)` 在刷新窗口内不会装载旧 T0 状态。`MainBtb.t1_fire` 在 `RegNext(t0_fire)` 的输出上追加同型组合门控：刷新 T 拍立即屏蔽内部寄存器的旧输出，且该寄存器在同一时钟沿装入门控后的 `t0_fire=0`，从 T+1 起自然保持为 0，无需显式清零 fire 寄存器。

两策略合并后，T 拍起 `t0_fire=t1_fire=false`，T1 不再生成 Entry/Counter 写请求或 replacer touch；`contextFlush` 在 T 拍时钟沿进一步清除 T1 payload。Entry/Counter WriteBuffer 入队与 replacer touch 的既有 `!bpuFlushing` 门控继续作为存储边界保护。首个刷新后 T0 到来时，`ReplacerState.states` 已清零，相关寄存器重新装载新上下文数据。

### 4.9 刷新完成信号 `resetDone`

mBTB 顶层将 40 个 SRAM（32 EntrySRAM + 8 CounterSRAM）的 `resetDone` 组合相与得到 `io.sramResetDone`，作为 mBTB 向 BPU 顶层输出的完成信号 `io.resetDone` 的直接驱动源之一。

```scala
// MainBtb.scala 修改（
if (HasBpuFlush) {
  io.resetDone.get := io.sramResetDone && !contextFlush
}
```

`resetDone` 赋值为纯组合逻辑，随整块落入 `if (HasBpuFlush)`：关闭时该赋值不生成，`resetDone` 端口本身为 `None`（`BasePredictorIO` 的 Option 声明，01 §5.1.3），无需 else 分支（00 §3.1）。

与 BPU 顶层 `BpuFlushCtrl` 的契约对齐（01 §5.2 line 391, §5.2.2 line 419-420）：

- BPU 顶层在聚合阶段对各子预测器 `p.io.resetDone.get` 做 `!activeFlushMask(i) || p.io.resetDone.get` 归约；
- 本预测器只需在收到有效 `contextFlush` 后拉低、本地清零完成后置位并保持至下一次有效 `contextFlush`（01 §5.1.3 line 335）。
- `contextFlush` 由 01 §5.2 line 398 保证为单拍脉冲，`!contextFlush` 项天然屏蔽 T 拍 `sramResetDone` 的旧值高电平，无需本地寄存器保存。

逐拍时序（`mbtbFlushEnable = true`）：

- T 拍 `contextFlush` 拉高，`resetDone` 经 `!contextFlush` 项组合拉低（此拍 `io.sramResetDone` 仍为旧值高）；
- T+1~T+256 `_resetState = true`，40 个 SRAM 逐行扫描清零中，`io.sramResetDone = false`，`resetDone = false`；
- T+257 `_resetState` 走完最后一行，所有 SRAM 同拍拉高 `resetDone`，`io.sramResetDone` 组合拉高，`resetDone` 同拍组合拉高；
- T+257 当拍 BPU 顶层聚合 `resetDone = 1`（假设仅 mBTB 被 `activeFlushMask` 选中；否则与最慢子预测器同拍），状态机迁出 `s_flushing`，T+258 进入 `s_done`。

---

### 4.10 编译开关裁剪清单

按 00 §3.4 的要求，`HasBpuFlush=false` 时以下 mBTB 刷新专用结构必须从生成 RTL 中结构性消失（验收标准见 00 §5.2 双配置 RTL 对比）：

| 类别                           | 应消失的对象                                                                                                                                                                                                                                                                                                                  | 对应小节                           |
| :----------------------------- | :---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | :--------------------------------- |
| 端口                           | `BasePredictorIO` 的 `contextFlush`/`bpuFlushing`/`resetDone`（01 声明）；`MainBtbAlignBankIO`/`MainBtbInternalBankIO` 的 `contextFlush`/`bpuFlushing`；`MainBtbReplacerIO` 的 `contextFlush`；`WriteBufferIO` 的 `contextFlush`（经 `hasContextFlush`）；`Queue` 的 `flush`（经 `hasFlush`） | §4.2.1、§4.4.1、§4.5.1、§4.6.1 |
| 各级中间变量                   | `MainBtb`/`MainBtbAlignBank`/`MainBtbInternalBank`/`MainBtbReplacer` 模块体顶部的 `contextFlush`/`bpuFlushing` 解包（关闭时退化为 Scala 常量 `false.B`，不生成硬件）                                                                                                                                            | §4.2.1、§4.6.1                   |
| EntrySRAM / CounterSRAM 清零   | `extraReset = HasBpuFlush` 绑定下的 `extra_reset` 端口及 `entrySrams`/`counterSram` 的连线                                                                                                                                                                                                                            | §4.2.2、§4.3.2                   |
| Entry WriteBuffer 清零         | `hasContextFlush = HasBpuFlush` 绑定下的 `contextFlush` 端口及连线（清零写本身由 03 §4.3.2 部署）                                                                                                                                                                                                                        | §4.4.1                            |
| Entry WriteBuffer 入队门控     | 合并点`valid`（`writeValid \|\| flushValid`）中的 `!bpuFlushing` 门控项                                                                                                                                                                                                                                                   | §4.4.3                            |
| Counter WriteBuffer 清空与门控 | `hasFlush = HasBpuFlush` 绑定下的 `flush` 端口及连线；`enq.valid` 中的 `!bpuFlushing` 门控项                                                                                                                                                                                                                          | §4.5.1、§4.5.3                   |
| Replacer 清零                  | `ReplacerState` 的 `hasContextFlush = HasBpuFlush` 实例点绑定与 `when(contextFlush)` 整表清零写                                                                                                                                                                                                                         | §4.6.2                            |
| Replacer touch 门控            | `predict.touch.valid`/`train.t1_touch.valid` 中的 `!bpuFlushing` 门控项                                                                                                                                                                                                                                                 | §4.6.3                            |
| 预测残留清理                   | S1/S2/S3 寄存器的`when(contextFlush)` 清零块                                                                                                                                                                                                                                                                                | §4.7 策略一                       |
| 预测流入门控                   | S0 读请求中的`!bpuFlushing` 门控项；`s1_rawEntries`/`s1_rawCounters` 的刷新 Mux（关闭时直接取 `Mux1H` 原式）                                                                                                                                                                                                          | §4.7 策略二                       |
| 训练残留清理                   | 策略二：`when(contextFlush)` 清零块（MainBtb：`t1_train`/`t1_rotator`/`t1_startPcVec`；MainBtbAlignBank：`t1_victimMask`；均位于 `RegEnable` 声明之后，last-connect 覆盖装载）；策略一：`t0_fire`/`t1_fire` 的 `!bpuFlushing` 内联门控项（elaboration 期选择 `true.B`，经 Chisel/CIRCT 常量折叠后消失）                       | §4.8                              |
| 完成信号                       | `io.resetDone.get := io.sramResetDone && !contextFlush` 组合赋值                                                                                                                                                                                                                                                            | §4.9                              |

关闭后的数据通路必须与未引入刷新机制的原始 mBTB 完全一致：`writeValid`/`flushValid`/`enq.valid`/`read.req.valid`/`touch.valid`、`s1_rawEntries = Mux1H(...)`、`s1_rawCounters = Mux1H(...)`、WriteBuffer 恢复原有可选 `hasFlush` 语义（当前 mBTB 实例未绑定该参数，因此无运行时清零端口）、`io.sramResetDone` 聚合均恢复原式，不保留额外门控、延迟或默认值（00 §3.1）。

> `WriteBuffer` 与 `ReplacerState` 为 aBTB/mBTB 共用模块：其 `hasContextFlush=false`（默认）实例不得因本方案生成任何刷新端口或清零逻辑，即裁剪以实例化参数为粒度，与 `HasBpuFlush` 的绑定仅发生在 aBTB（03 §4.3.1、§4.5.2）与 mBTB（本文 §4.4.1、§4.6.2）实例点。
