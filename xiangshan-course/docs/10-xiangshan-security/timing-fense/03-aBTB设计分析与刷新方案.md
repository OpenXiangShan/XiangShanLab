# 03-aBTB设计分析与刷新方案

> 本文档是 aBTB（`AheadBtb`）的**真实刷新方案**，属于 [01-BPU刷新方案概览](01-BPU刷新方案概览.md) §6.1 占位阶段之后的落地内容：实现时用 §4.8 的真实完成条件替换 `AheadBtb.scala` 中的 `io.resetDone.get := true.B` 恒真占位。
>
> 1. **接口契约**：`contextFlush`/`bpuFlushing`/`resetDone` 由 `BasePredictorIO` 以 `Option.when(HasBpuFlush)` 声明（01 §5.1.3），BPU 顶层已按本事务 `activeFlushMask` 门控后下发（01 §5.3.3），aBTB 收到的均为针对自身的有效信号。
> 2. **编译开关**：本文对 `contextFlush`/`bpuFlushing` 的消费遵循 [00-编译开关实现方案](00-BPU刷新机制编译开关实现方案.md) §3.1 统一编码规则与 01 §6.2 模板：各消费模块（`AheadBtb`、`AheadBtbBank`、`WriteBuffer`、`ReplacerState`、`AheadBtbReplacer`）在模块体顶部先把 Option 端口解包为中间变量（`private val contextFlush = if (HasBpuFlush) io.contextFlush.get else false.B` 等，`WriteBuffer`/`ReplacerState` 以自身 `hasContextFlush` 参数为条件），模块内统一引用变量名；所有消费点仍统一落在 `if (HasBpuFlush)`（含内联 `if` 表达式）内，关闭时结构性消失。`HasBpuFlush=false` 时应消失的结构清单见 §4.9。`WriteBuffer` 与 `ReplacerState` 为 mBTB 共用模块，其刷新端口经 `hasContextFlush` 实例化参数裁剪，在 aBTB 实例点与 `HasBpuFlush` 绑定（§4.3.1、§4.5.2）。
> 3. **刷新窗口**：`bpuFlushing` 覆盖 `contextFlush` 单拍至聚合 `resetDone` 置位、状态机离开 `s_flushing` 的整个窗口（01 §5.2.2、§5.4）。

---

## 1. aBTB 预测流水线

假设预测块 A->B->C->D

第一拍：由 A 的 startPC 计算 index1，并读 SRAM

第二拍：
1）根据上一拍得到的 index1 读到 SRAM 中的数据，保存 B 的 startPC
2）由 B 的 startPC 计算 index2，并读 SRAM

第三拍：
1）用 B 的 tag 和从 SRAM 读出的 4 个 tag 进行比较，得到几个以 B 为 start 的 branch；比较 branch 的 position，选择最靠前的且跳转的 branch 的 target，即 B 的预测结果 D
2）根据上一拍得到的 index2 读到 SRAM 中的数据，保存 C 的 startPC
3）由 C 的 startPC 计算 index3，并读 SRAM

第四拍：
1）用 C 的 tag 和从 SRAM 读出的 4 个 tag 进行比较，得到几个以 C 为 start 的 branch；比较 branch 的 position，选择最靠前的且跳转的 branch 的 target，即 C 的预测结果 E
2）根据上一拍得到的 index3 读到 SRAM 中的数据，保存 D 的 startPC
3）由 D 的 startPC 计算 index4，并读 SRAM

对于 aBTB 的预测流水线而言，其主体只读 SRAM（S0 发起读、S1 取回数据、S2/S3 tag 比较与 target 输出），但存在一项副作用写：S2 阶段若检测到多命中（`s2_multiHit`），会向 bank 发起一次"写零无效化"请求（详见第 2 章 T2 阶段的第三种写场景）。因此预测流水线对刷新结果的污染风险有两条路径：一是预测结果本身可能基于残留旧数据，二是 `s2_multiHit` 触发的写请求会污染 WriteBuffer / EntrySRAM。刷新方案必须同时处理这两条路径（既无效预测结果，也阻断 multi-hit 写），详见 4.6 节。

预测结果输出

```scala
  io.prediction.zipWithIndex.foreach { case (pred, i) =>
    pred.valid            := s2_valid && s2_hitMask(i)
    pred.bits.taken       := s2_ctrResult(i)
    pred.bits.cfiPosition := s2_entries(i).position
    pred.bits.attribute   := s2_entries(i).attribute
    pred.bits.target      := getFullTarget( s2_startPc, 
										    s2_entries(i).targetLowerBits, 
										    s2_entries(i).targetCarry)
  }
```

---

## 2. aBTB 训练流水线

对于 aBTB 的训练流水线而言，涉及对 SRAM 的更新操作，因此我们需要详细分析其行为：

1. **T0 阶段**：接收训练请求
2. **T1 阶段**：执行以下两项操作：
   - **更新 taken counter（selfDecrease/selfIncrease）** — 条件：训练请求有效（`t1_fire`）且地址匹配（训练请求所属的 bank 和 set）
     - `selfDecrease`：条件分支不该跳转时，饱和递减
     - `selfIncrease`：条件分支确实跳转时，饱和递增
     - 这两项由 `t1_fire` 控制，在 T1 阶段**立刻执行**（`AheadBtb.scala:286-306`）。
   - **生成写请求字段**：`t1_needWriteNewEntry`/`t1_needCorrectTarget`/`t1_hitMaskOH`/`t1_writeEntry` 等（`:308-330`），并向 replacer 发起 victim 查询（`:332`）。本阶段**不**直接向 bank 发写请求，上述字段全部打入 t2 寄存器组（`:335-342`）。
3. **T2 阶段**：由 t2 寄存器组驱动 `io.writeReq` 向 WriteBuffer 提交写请求（`:344-367`）。以下三种写场景互斥（优先级从高到低）：
   - **写入新表项** — 条件：`t2_fire && t2_needWriteNewEntry && t2_bankMask(i)`
     当训练分支在表中未命中（没有匹配的 position 和 attribute）时，需要写入新表项。
   - **修正间接跳转目标** — 条件：`t2_fire && t2_needCorrectTarget && t2_bankMask(i)`
     当训练分支命中且是间接跳转且目标地址不同时，修正目标。
   - **Multi-Hit 无效化** — 条件：`s2_valid && s2_multiHit && s2_bankMask(i)`（不依赖训练请求有效！）
     复用了预测流水线 S2 阶段的冲突异常（`s2_multiHit`），如果预测时发现同一个 PC 匹配到多个表项时，这里会发出一个写零（即 Invalid 无效化）的请求将其中一个清空。它与训练写共享同一个 `when/elsewhen` 仲裁链和同一个 `io.writeReq` 端口，但**与 t2_fire 无关**，只要 `s2_valid && s2_multiHit` 为真就可能触发。
4. **WriteBuffer 出队**：WriteBuffer 在 SRAM 读端口空闲时将写请求出队写入 SRAM，==时机是可变延迟的==。出队完成后产生 `writeResp`，触发以下两项操作：
   - **更新 taken counter（needReset）** — 条件：WriteBuffer 出队完成且该写请求标记了需要重置计数器，且当前遍历的 bank/set/way 与出队请求的地址匹配
     将被写入 way 的计数器重置为 weak positive（2）。**不受 t1_fire/t2_fire 控制**，由 WriteBuffer 出队事件驱动。
   - **更新 ReplacerState** — 条件：WriteBuffer 出队完成
     将新写入 entry 所在 way 标记为"最近使用"。

> **拆分说明**：needReset 和 ReplacerState 更新虽然与训练流程相关，但其触发条件是 `writeResp.valid`（即 WriteBuffer 出队完成），而非 `t1_fire`/`t2_fire`。由于 WriteBuffer 出队时机是可变延迟的，这两项操作可能在 T2 之后若干拍才发生，因此不能归类为固定流水级的行为。

---

## 3. aBTB 的存储结构

aBTB 预测依赖 三个 数据结构：**Entry SRAM**（Bank 内）、**TakenCounter 寄存器数组**（顶层）、**ReplacerState 寄存器数组**（Replacer 内）。下面逐一说明。

### 3.1 EntrySRAM

**Entry SRAM 的数据结构**

aBTB 顶层实例化 4 个 Bank，每个 Bank 中采用 4 way + 64 Set 的设计。由于**物理 SRAM 宏**的宽度和深度有上限，因此将每个 Bank 拆成 2 个 64×2 的小 SRAM，从而获得更优的时序和面积。

```text
 AheadBtb
  ├── banks(0): AheadBtbBank
  │   └── sram: SplittedSRAMTemplate
  │       ├── array(0)(0)(0): SRAMTemplate  set=64, way=2  → way 0~1
  │       ├── array(0)(1)(0): SRAMTemplate  set=64, way=2  → way 2~3
  ├── banks(1): AheadBtbBank  （同上结构）
  ├── banks(2): AheadBtbBank  （同上结构）
  └── banks(3): AheadBtbBank  （同上结构）
```

> 在 aBTB 的实现中，SRAM 行数等于 Set 数，每一行 SRAM 中都包含 2 个表项。每个 SRAMTemplate 子实例存储 64×2 = 128 个 60-bit 表项。整个 aBTB 共 4×2 = 8 个 SRAMTemplate 子实例，存储 8×128 = 1024 个表项。

每个 AheadBtbEntry 结构如下所示：

```text
AheadBtbEntry (60 bits)
├── valid:           Bool            — 1 bit    表项有效位
├── tag:             UInt(24)        — 24 bits  虚拟地址 tag
├── position:        UInt(5)         — 5 bits   CFI 在取指块内位置
├── attribute:       BranchAttribute — 8 bits  分支类型 + RAS 动作
├── targetLowerBits: UInt(22)        — 22 bits  部分跳转目标
└── targetCarry:     None            — 0 bits   (EnableTargetFix=false)
```

**读写端口**

**读操作**发生在预测流水线 S0 阶段，只要 `predictReqValid`（即 `io.stageCtrl.s0_fire`）为真，就对 EntrySRAM 发起读请求，读请求直接传递给 SplittedSRAMTemplate。注意此处驱动读请求的是 `predictReqValid` 而非 `s0_fire`（`s0_fire = io.enable && predictReqValid`），即读请求不受 `io.enable` 门控。

S1 阶段取回读数据，通过 one-hot Mux 选择对应 bank 的结果。S1 阶段同时还进行 tag 比较和读取 takencounter，但 ==tag 比较的结果在 S2 才使用==。SRAM 输出端有锁存寄存器，读出数据保持到下一次读请求到来，在无新读请求时保持上一次读结果不变。

> 每个 SplittedSRAMTemplate 在物理上被拆分为 2 个 SRAMTemplate 子实例，每个 SRAMTemplate 存储 2 个 way。**读时 2 个子实例同时被选中，返回 4 个 way 的数据**，最终由 SplittedSRAMTemplate 聚合为 Vec(4, AheadBtbEntry) 输出。

EntrySRAM 是单端口实现，SRAM 不能同时读写。当读写冲突时，读优先级更高，**写请求**只能在无读请求的空闲拍才真正写入 SRAM。

aBTB 在 T0 阶段接收训练请求，T1 阶段生成写请求字段，T2 阶段向 bank 发送写请求；在 bank 内部，写请求不直接写 SRAM，而是先进入 WriteBuffer，因此实际写入 SRAM 的时机由 WriteBuffer 控制，==是可变延迟的==。WriteBuffer 的详细行为见[[WriteBuffer设计分析]]

对于 EntrySRAM 而言，一共有三种写 SRAM 场景：

| 写场景           | 条件                                      | 写入内容                 |
| :--------------- | :---------------------------------------- | :----------------------- |
| 写新表项         | t2_fire && t2_needWriteNewEntry           | 新 entry → victim way   |
| 修正间接跳转目标 | t2_fire && t2_needCorrectTarget           | 新 target → 命中 way    |
| Multi-Hit 无效化 | s2_valid && s2_multiHit（不依赖 t2_fire） | 0（清零）→ multiHit way |

> **关键**：Multi-Hit 写不受 t2_fire 控制，只要 s2_valid && s2_multiHit 就可能触发写。

**复位机制**

1. 作为单端口 SRAM，每周期仅支持一次写操作，只能逐行写零。
2. `SRAMTemplate` 内部维护 `_resetState`（`RegInit(true.B)`）和行计数器 `_resetSet`，上电后自动进入复位状态：每拍对 `_resetSet` 指向的 set 写入全零（写掩码覆盖所有 way），计数器自增；计数到 63 时 `resetFinish = true`，下一拍 `_resetState := false.B`，共 64 拍完成清零。
3. 复位期间读端口被阻塞（`io.r.req.ready = false`），`resetDone` 为低。8 个子实例各自独立并行执行，顶层 `resetDone` 由所有子实例的同名信号 `reduce(_ && _)` 汇聚产生。
4. 此外，`SRAMTemplate` 提供 `extraReset` 端口，拉高一拍可重新触发 `_resetState := true.B`，在运行时再次执行逐行清零。当前 aBTB 实例化时 `extraReset = false`，未启用该功能。

---

### 3.2 TakenCounter

**TakenCounter 的数据结构**

takenCounter 采用三维寄存器数组设计，4 Bank × 64 Set × 4 Way，共计 1024 个 2-bit 饱和计数器。每个计数器是一个 2bit 饱和计数器。

takenCounter 是在 aBTB 模块实现的**寄存器数组**，可在内部直接访问。

```scala
// AheadBtb.scala:63-69
private val takenCounter = RegInit(
  VecInit.fill(NumBanks)(
    VecInit.fill(NumSets)(
      VecInit.fill(NumWays)(TakenCounter.Zero)
    )
  )
)
```

**读写端口**

aBTB 在预测流水线和训练流水线中都会**读** takenCounter 的值：

1. **预测 S1 阶段**：无条件读。通过 `takenCounter(bankIdx)(setIdx)` 索引到一个 Set 的 4 Way，并一次性全部读出。
2. **训练 T1 阶段**：通过调用`selfDecrease()/selfIncrease()` 方法，先判断 takencounter 当前值是否等于 0 或饱和，然后按需对 takencounter ± 1.

> 注意：`selfDecrease/selfIncrease` 的触发条件 `isCond/posBefore/posEqual` 均依赖 `e.hit`（entry 命中状态），entry 未命中时不会触发。

aBTB 一共有三类**写** takenCounter 的情况，但它们不在同一个固定流水级触发：

- `needDecrease` / `needIncrease` 由 T1 阶段的 `t1_fire` 触发；
- `needReset` 由 WriteBuffer 出队后的 `writeResp` 触发，出队延迟可变；
- 当多个条件同拍作用于同一个 counter 时，既有仲裁优先级为 `needReset > needDecrease > needIncrease`。

1. **`needReset`--纯写，重置计数器值**：
   - 条件：WriteBuffer 出队写 SRAM 当拍，`writeResp.valid` 且 `writeResp.bits.needResetCtr=true`
   - 含义：新 entry 刚写入某个 way，该 way 的 ctr 需要初始化为 2（weak positive）
   - 作用范围：仅被写入的那一个 way
   - 时序：不由 `t1_fire` 直接触发，而是延迟到 WriteBuffer 成功出队时触发
2. **`needDecrease`--读写，递减**：
   - 条件：T1 训练请求有效（`t1_fire`）且地址匹配，该 way 的 entry 是条件分支（`isCond`），且以下两种情况之一成立：
     - 训练结果为未跳转（`!taken`）：该条件分支本不该跳转，减弱跳转倾向
     - 训练结果为跳转，但该条件分支的位置在训练分支之前（`posBefore`）：训练分支才是真正跳转的分支，前面的条件分支不应跳转
   - 作用范围：一个 Set 内所有满足条件的 way
3. **`needIncrease`--读写，递增**：
   - 条件：T1 训练请求有效（`t1_fire`）且地址匹配，该 way 的 entry 是条件分支（`isCond`），训练结果为跳转（`taken`），且该条件分支的位置与训练分支位置相同（`posEqual`）
        - 含义：该条件分支确实跳转了，增强跳转倾向
        - 作用范围：一个 Set 内所有满足条件的 way

**复位机制**

1024 个计数器**上电复位**为全 0（SaturateNegative），无额外复位端口。

`takenCounter` 仅通过 `needReset` 信号触发 **`resetWeakPositive()`**（设为 2）。触发场景仅限：

1. **新项分配**：WriteBuffer 出队完成写入时。
2. ==**多命中无效化**：重置重复项对应 Way 的计数器。==

---

### 3.3 ReplacerState

**ReplacerState 的数据结构**

aBTB 为每个 EntrySRAM Bank 都实例化了 1 个 AheadBtbReplacer。每个 `AheadBtbReplacer` 内部包含：

```text
AheadBtb
  ├── replacers(0): AheadBtbReplacer    → 对应 banks(0)
  ├── replacers(1): AheadBtbReplacer    → 对应 banks(1)
  ├── replacers(2): AheadBtbReplacer    → 对应 banks(2)
  └── replacers(3): AheadBtbReplacer    → 对应 banks(3)
```

每个 ReplacerState 内部具体被实现为一个 64 行的**寄存器数组**，每行包含 3 bit，即每个 set 用 3 位 PLRU 二叉树表示 4 路替换状态。4 个 ReplacerState 共 4×64×3 = 768 bits。核心存储是 `ReplacerState` 中的 `states` 寄存器数组：

```scala
private val states = 
			RegInit(VecInit(Seq.fill(NumSets)(0.U.asTypeOf(UInt(StateBits.W)))))

3-bit PLRU Tree for 4-way:
              bit[2]: ways 3-2 older than ways 1-0
              /                          \
    bit[1]: way 3>2                bit[0]: way 1>0
```

`AheadBtbReplacer` 内部还包含 PLRU 状态计算逻辑（`PlruStateGen`），负责从 `states` 读出旧状态、根据 touch way 信息计算新状态、写回 `states`。这些对 aBTB 顶层完全透明——aBTB 顶层只通过 `ReplacerIO` 端口驱动 replacer，不直接调用内部计算逻辑。

**读写端口**

aBTB 顶层通过 `ReplacerIO` 与每个 `AheadBtbReplacer` 交互，`AheadBtbReplacer` 接收到 IO 输入后，内部自动完成"读出旧 PLRU 状态 → 计算新状态 → 写回"的流程，aBTB 顶层无需关心。

```scala
class ReplacerIO(implicit p: Parameters) extends AheadBtbBundle {
  val readValid:   Bool      = Input(Bool())       // 预测路径：是否触发 touch
  val readSetIdx:  UInt      = Input(UInt(SetIdxWidth.W))    // 预测路径：set 索引
  val readWayMask: Vec[Bool] = Input(Vec(NumWays, Bool())) // 预测路径：命中的 way 掩码
  
  val writeValid:  Bool = Input(Bool())             // 训练路径：是否触发 touch
  val writeSetIdx: UInt = Input(UInt(SetIdxWidth.W))          // 训练路径：set 索引
  val writeWayIdx: UInt = Input(UInt(WayIdxWidth.W))          // 训练路径：写入的 way 索引

  val replaceSetIdx: UInt = Input(UInt(SetIdxWidth.W))        // victim 查询：set 索引
  val victimWayIdx:  UInt = Output(UInt(WayIdxWidth.W))       // victim 查询：最不常用的 way
}
```

`states` 是寄存器数组，**读**为组合逻辑（无读延迟、无读使能）。从 aBTB 顶层的视角，有三类读场景：

1. **Victim way 查询**：miss 分配新表项时，需要知道替换哪个 way。`replaceSetIdx` 始终连接 `t1_setIdx`（`AheadBtb.scala:332`），replacer 通过额外读端口读出该 set 的 PLRU 状态，计算最不常用的 way 输出为 `victimWayIdx`；该输出在 T1 末被无条件打入 `t2_victimWayIdx`（`:336`），到 T2 才在 `t2_needWriteNewEntry` 成立时被消费。
2. **预测路径状态读（内部）**：预测 S2 阶段命中时，replacer 内部读出命中 set 的 PLRU 旧状态，用于计算 touch 后的新状态。这是内部读-改-写的一部分，aBTB 顶层不直接执行。
3. **训练路径状态读（内部）**：WriteBuffer 出队时，replacer 内部读出写入 set 的 PLRU 旧状态，用于计算 touch后的新状态。同样是内部读-改-写的一部分，aBTB 顶层不直接看执行。

PLRU 更新必须"读出旧状态 → 计算新状态 → 写回"，因为 touch 某个 way 会翻转从根到该 way 路径上所有节点的方向，新状态取决于旧状态和 touch way 的共同作用。`AheadBtbReplacer` 内部自动完成此流程，**aBTB 顶层只需驱动写触发条件**。aBTB 顶层只有两类写来源，均为读-改-写（PLRU 新状态取决于旧状态和 touch way 的共同作用）：

1. **预测路径写**——将命中 way 标记为"最近使用"
   - 条件：`s2_valid && s2_hit && s2_bankMask(i)`（`AheadBtb.scala:244-248`）
   - 作用范围：仅命中 set 的 PLRU 状态被更新
2. **训练路径写**——将写入 way 标记为"最近使用"
   - 条件：`b.io.writeResp.valid`，即 WriteBuffer 出队、写请求成功写入 SRAM（`AheadBtb.scala:369-373`）
   - 作用范围：仅写入 set 的 PLRU 状态被更新

两路写同一个 set 时由 `ReplacerState` 内部的端口物理序决定优先级：`trainWrite` 物理 idx 更高，因此**训练路径优先**，预测路径的写被丢弃（`ReplacerState.scala:61-69`）。

**复位机制**

`states` 通过 `RegInit` 初始化为全 0，上电复位后所有 set 的 PLRU 状态为 0。复位在 **1 个时钟周期**内完成（寄存器数组同时初始化，无需逐行清零）。

当前 ReplacerState **没有运行时复位端口**（无 extraReset、无 flush 信号），上电后 PLRU 状态仅通过预测/训练两路写操作更新。

---

## 4. 刷新方案

| 模块                      | 是否刷新 | 清空机制                                                        | 读写阻塞逻辑                                                                  |
| :------------------------ | :------: | :-------------------------------------------------------------- | :---------------------------------------------------------------------------- |
| **EntrySRAM**       |    是    | 复用`extraReset` 机制                                         | SRAM 复位天然阻塞读写                                                         |
| **WriteBuffer**     |    是    | 新增`hasContextFlush`，清 `dirty`+`shadowValid`           | `!io.bpuFlushing` 门控入队                                                  |
| **TakenCounter**    |    否    | 不需要主动清空                                                  | /                                                                             |
| **Replacer (PLRU)** |    是    | 新增`contextFlush` 端口，一拍清零 `states`                  | `!io.bpuFlushing` 门控读写（顶层）                                          |
| **预测流水线**      |    -    | 清零 s2/s3 寄存器 +`s2_jumpValidVec`/`s2_conditionValidVec` | `Mux(io.bpuFlushing, 0, …)` 置零 `s1_entries`/`s1_ctrVec`              |
| **训练流水线**      |    -    | 策略二：`contextFlush` 当拍清零 `t1_train` + T2 寄存器组    | 策略一：`!bpuFlushing` 门控 `t0_fire`/`t1_fire`/`t2_fire`，整窗 kill（§4.7） |

### 4.1 刷新接口生命周期

aBTB 的刷新接口与生命周期遵循 BPU 顶层约定：`contextFlush`/`bpuFlushing`/`resetDone` 三信号的定义与通用时序框架详见 01 §2.3.1、§2.3.2，`sramResetDone` 语义详见 01 §5.3.4。三信号均为 `BasePredictorIO` 的 `Option.when(HasBpuFlush)` 端口（01 §5.1.3）。aBTB 内部（含 bank、WriteBuffer、replacer 分发）对两个门控信号的消费统一经模块体顶部的中间变量引用（00 §3.1、01 §6.2），每一处消费仍按 00 §3.1 以 `if (HasBpuFlush)` 守卫（结构性裁剪），裁剪清单见 §4.9。

aBTB 专属：4 bank × 2 子实例 SRAM 经 `extraReset` 逐行清零 64 拍，`sramResetDone` 与 `resetDone` 同拍置位于 T+65（即 01 §2.3.2 中的 k=65）；具体信号生成逻辑与逐拍时序详见 §4.8。

### 4.2 EntrySRAM

> [!abstract] 结论
> EntrySRAM 需要刷新。启用 SRAMTemplate 的 `extraReset` 端口，由 `io.contextFlush` 触发 64 拍逐行清零；清零窗口内 SRAM 自身的 `ready` 反压隔绝新读写，无需额外门控。

#### 4.2.1 I/O 接口修改

EntrySRAM 位于 `AheadBtbBank` 内，刷新所需的 `io.contextFlush`（驱动 `extraReset`，§4.2.2）与 `io.bpuFlushing`（门控 bank 内 WriteBuffer 入/出队，§4.3.3）均来自 aBTB 顶层。两个信号继承自 `BasePredictorIO`（01 §5.1.3，已由 01 顶层 `abtbFlushEnable` 门控），aBTB 顶层直接消费并分发到 4 个 bank。后续 §4.2~§4.7 在 bank 内使用 `io.contextFlush`/`io.bpuFlushing`，前提是先打通这条信号通路。`BankIO` 继承 `AheadBtbBundle`（-> `BpuBundle` -> `HasBpuParameters` -> `HasFrontendParameters` -> `HasXSParameter`），可直接访问 `HasBpuFlush`，新端口按 00 §3.1 用 `Option.when` 声明，分发连线放入 `if (HasBpuFlush)`：

```scala
  //AheadBtbBank.scala
  class BankIO(implicit p: Parameters) extends AheadBtbBundle {
    ...
    val contextFlush: Option[Bool] = Option.when(HasBpuFlush)(Input(Bool()))
    val bpuFlushing:  Option[Bool] = Option.when(HasBpuFlush)(Input(Bool()))
  }

  // AheadBtb.scala -- 顶层模块体顶部：中间变量（00 §3.1 / 01 §6.2），
  // 后续顶层对两个门控信号的消费统一引用变量名（消费点仍处于 if 内）；
  // else false.B 仅为 Scala 类型占位，关闭时不进入硬件
  private val contextFlush = if (HasBpuFlush) io.contextFlush.get else false.B
  private val bpuFlushing  = if (HasBpuFlush) io.bpuFlushing.get  else false.B

  // 顶层分发（Option 到 Option 连线：关闭时端口不生成，整段不连线，不经过中间变量）
  if (HasBpuFlush) {
    banks.foreach { b =>
      b.io.contextFlush.get := io.contextFlush.get
      b.io.bpuFlushing.get  := io.bpuFlushing.get
    }
  }

  // AheadBtbBank.scala -- bank 模块体顶部：bank 级中间变量（消费 bank 内 WriteBuffer 门控等）
  private val contextFlush = if (HasBpuFlush) io.contextFlush.get else false.B
  private val bpuFlushing  = if (HasBpuFlush) io.bpuFlushing.get  else false.B
```

> 该通路同时服务于 §4.3 WriteBuffer：WriteBuffer 复用 bank 的 `io.contextFlush`/`io.bpuFlushing`。

#### 4.2.2 清空 EntrySRAM

根据 3.1 中的分析，我们计划在 aBTB 实例化时启用该端口，并由 `io.contextFlush` 驱动。`io.contextFlush` 继承自 `BasePredictorIO`（01 §5.1.3），已由 01 顶层 `abtbFlushEnable` 门控（01 §5.3.1），aBTB 内部直接消费即可。`extraReset` 参数绑定 elaboration 期常量 `HasBpuFlush`：开关关闭时 `extra_reset` 端口本身不生成，连接语句放入 `if (HasBpuFlush)`：

```scala
// AheadBtbBank.scala
private val sram = Module(new SplittedSRAMTemplate(
  new AheadBtbEntry,
  ...
  shouldReset = true,
  extraReset  = HasBpuFlush,   // elaboration 期随编译开关裁剪：false 时 extra_reset 端口不生成
  ...
))

if (HasBpuFlush) {
  sram.extra_reset.get := contextFlush   // Option 端口驱动需 if；条件引用 bank 级中间变量
}
```

#### 4.2.3 刷新期间的读写污染

EntrySRAM 与 WriteBuffer 是 aBTB 的两个核心存储结构，刷新方案从四个层面保证二者在刷新窗口（T ~ `聚合resetDone`）内不接收旧上下文数据、不向外流出旧上下文数据：

**层面一：清空两个存储结构本身。** 这是刷新的基础动作，清除 T 拍之前**已存入的旧上下文数据**：

- EntrySRAM 复用 `extraReset` 机制，由 `io.contextFlush` 触发 64 拍逐行清零（§4.2.2），清零期间 SRAM 天然隔绝新读写；
- WriteBuffer 由 `io.contextFlush` 一拍清空 `dirty` + `shadowValid`（§4.3.2），滞留的残留写请求随之全部失效。

清空动作本身并不阻止刷新窗口内的数据通路继续搬运旧上下文数据，这需要以下三个层面配合。

**层面二：阻止 EntrySRAM `holdRead` 旧值流出。** `holdRead` 机制使 EntrySRAM 的输出寄存器中保存旧上下文的表项，若放任流出会在 S2 拍产生虚假命中。§4.6 策略二通过**禁止预测流水线发起读请求**（`!bpuFlushing` 门控 s0 `readReq.valid`）、**将读出数据强制置零**（`Mux(bpuFlushing, 0, …)` 封死 `s1_entries`/`s1_ctrVec`），阻断了 holdRead 旧值流入 S2。

**层面三：阻止刷新期间对 WriteBuffer 的入队。** 若刷新窗口内训练/multi-hit 路径仍向 WriteBuffer 入队，新入队的旧上下文写请求会污染 WriteBuffer 的清零结果，并在刷新结束之后污染 SRAM 的刷新结果。§4.3.3 用 `!io.bpuFlushing` 门控 WriteBuffer 入队端口，两个写来源被统一拦截：

- **训练写**：
  - T 拍起：§4.7 策略一用 `!bpuFlushing` 同时门控 `t0_fire`/`t1_fire`/`t2_fire`，从源头阻断 T1 counter 更新和 T2 训练写请求；
  - `contextFlush` 同拍清零 `t1_train` 与 T2 寄存器组，WriteBuffer 入队门控继续作为存储边界保护；
- **multi-hit 写**：
  - T 拍：与训练写共享同一 `io.writeReq` 端口，T 拍被入队门控拦截；
  - T+1 拍起：S2 寄存器被 §4.6 策略一清零、S0 读被策略二门控，不再产生新 multi-hit（完整轨迹见 §4.3.3）。

**层面四：出队方向不设门控，由复位反压与空 buffer 保证**。 出队口（io.read.head.ready，读优先单端口）不新增门控（§4.3.3）。穷举窗口内出队口可能出现的数据，不存在任何一拍是门控能改变结局的：

- **T 拍**`contextFlush` 清零 T 拍末才生效，当拍旧 dirty 表项仍可出队，但该写提交于清零扫描开始前，必被 T+1 起的扫描覆盖；
- **T+1 ~ T+64**：EntrySRAM 处于复位态，写口 `ready` 为低（`io.w.req.ready := !resetHold`），buffer 物理上无从出队；
- **T+65 起**：层面一已清空 buffer、层面三又封死唯一入队口（aBTB 两写源顶层合并为单一 io.writeReq，端口级门控天然完备），无数据可出队。

四个层面合并后的时序覆盖：

* T 拍，入队门控拦截当拍残留写，`contextFlush` 当拍清空 WriteBuffer 与流水线寄存器；
* T+1 起，SRAM 复位态隔绝读写，holdRead 出口被封死，写口反压与干净的 Writebuffer 使 buffer 无出队数据；
* `io.resetDone` 之后 `bpuFlushing` 撤销，入队门控恢复，新上下文的读写恢复正常流动（buffer 为空，无残留出队）。

---

### 4.3 WriteBuffer

> [!abstract] 结论
> WriteBuffer 需要刷新。为 WriteBuffer 增加 `hasContextFlush` 刷新模式，由 `io.contextFlush` 触发一拍清空 `dirty` + `shadowValid`。

#### 4.3.1 I/O 接口修改

WriteBuffer 与 EntrySRAM 同处 `AheadBtbBank` 内，直接复用 §4.2.1 打通的 bank 级 `io.contextFlush`/`io.bpuFlushing`，无需顶层另开通路。

WriteBuffer 自身需新增 `hasContextFlush` 参数与 `contextFlush` 可选端口，作为比既有 `flush` 更强的清空入口。
`hasContextFlush` 是 elaboration 期常量，aBTB 实例点绑定 `HasBpuFlush`：开关关闭时端口不生成；模块内部以 `hasContextFlush` 为条件在模块体顶部解包中间变量（与 `HasBpuFlush` 中间变量同范式，00 §3.1 / 01 §6.2），不直接引用 `HasBpuFlush`，因此不影响 mBTB 等其他实例：

```scala
// WriteBuffer.scala -- 新增 hasContextFlush 参数与 contextFlush 端口
class WriteBuffer[T <: WriteReqBundle](
    gen:             T,
    numEntries:      Int = 1,
    numPorts:        Int = 1,
    numWays:         Int = 1,
    hasCnt:          Boolean = false,
    hasWayMask:      Boolean = false,
    hasFlush:        Boolean = false,
    hasContextFlush: Boolean = false,  // new
    nameSuffix:      String = ""
)(implicit p: Parameters) extends XSModule {

  class WriteBufferIO extends Bundle {
    val write:        Vec[ValidIO[T]]     = Vec(numPorts, Flipped(Valid(gen)))   // 无反压：满时覆盖脏槽
    val read:         Vec[DecoupledIO[T]] = Vec(numPorts, DecoupledIO(gen))
    val full:         Vec[Bool]           = Output(Vec(numPorts, Bool()))
    val overwrite:    Vec[Bool]           = Output(Vec(numPorts, Bool()))
    val takenMask:    Option[Vec[Bool]]   = Option.when(hasCnt)(Vec(numPorts, Input(Bool())))
    val flush:        Option[Bool]        = Option.when(hasFlush)(Input(Bool()))
    val contextFlush: Option[Bool]        = Option.when(hasContextFlush)(Input(Bool()))  // new port
  }
  val io: WriteBufferIO = IO(new WriteBufferIO)

  private val flush = io.flush.getOrElse(false.B)        // 既有 hasFlush 逻辑，保持不变
  // 新增：contextFlush 中间变量（00 §3.1 / 01 §6.2，条件为模块自身的 hasContextFlush 参数）；
  // 后续消费统一引用变量名，禁止在别处散布 io.contextFlush.get / getOrElse，清空逻辑见 §4.3.2
  private val contextFlush = if (hasContextFlush) io.contextFlush.get else false.B
}

// AheadBtbBank.scala -- 实例化时绑定编译开关并接线（关闭时端口不生成，整段不连线）
private val writeBuffer = Module(new WriteBuffer(
    new BankWriteReq,
    WriteBufferSize,
    numPorts          = 1,
    hasContextFlush   = HasBpuFlush,
    nameSuffix        = s"abtbBank$bandIdx"
))
if (HasBpuFlush) {
  writeBuffer.io.contextFlush.get := io.contextFlush.get   // Option 到 Option 连线，不可别名化
}
```

#### 4.3.2 清空 WriteBuffer

SRAM 清零扫描期间反压写端口（`SRAMTemplate` 的 `io.w.req.ready := !resetHold && !conflictStallWrite`，清零期间 `resetHold` 为高使 ready 为低），WriteBuffer 中 dirty=true 的旧写请求（`io.contextFlush` 之前已入队）滞留无法出队。扫描结束后它们会写回已清零的 SRAM，且 dirty 仅在出队成功时清除，不会自行丢弃——因此必须同时刷新 WriteBuffer。

WriteBuffer 的现有刷新策略只清 `dirty`、保留 `shadowValid`+`entries`，其语义是 “这条缓存已写回 SRAM，SRAM 里存储的现在就是这份数据”。但上下文切换后 SRAM 已清零：若新上下文写同一条分支且数据相同，WriteBuffer 判定命中且一致，便丢弃写请求，而 SRAM 实际为空，训练记录丢失。因此上下文切换必须**同时清 dirty 与 shadowValid**，使缓冲整体无效。残留的 entries 因命中比对检查 shadowValid 而不会误命中，功能上等价于空缓冲。

```scala
// WriteBuffer.scala -- 类内清空逻辑（flush 为既有 getOrElse 解包，保持不变；
// contextFlush 引用 §4.3.1 中间变量；消费整体落入 if (hasContextFlush)，
// 关闭分支恢复仅有 flush 的原始路径，00 §3.1；
// 本块必须位于写端口 next-state 逻辑之后，依赖 last-connect 覆盖写端口对同一 slot 的置位）
  for (nRows <- 0 until numPorts) {
    // ...

    if (hasContextFlush) {
      when(flush || contextFlush) {
        for (i <- 0 until numEntries) {
          nextDirty(nRows)(i) := false.B
        }
      }
      when(contextFlush) {
        for (i <- 0 until numEntries) {
          nextShadowValid(nRows)(i) := false.B
        }
      }
    } else {
      when(flush) {
        for (i <- 0 until numEntries) {
          nextDirty(nRows)(i) := false.B
        }
      }
    }
  }
```

> [!abstract] WriteBuffer 内部 PLRU
> WriteBuffer 每个写口内部还有一个 fully-associative PLRU，用于在所有 slot 均 dirty 时选择 victim。该 PLRU **不需要为 contextFlush 增加显式清零端口**。
> 原因如下：`contextFlush` 清空 `dirty` 后，只要存在 `dirty=false` 的 slot，victim 就由 `PriorityEncoder(!dirty)` 选择，不消费内部 PLRU；由于 `shadowValid` 也已清空，新写入不会先命中旧 shadow entry。内部 PLRU 只有在所有 slot 均为 dirty 时才参与 victim 选择。**从全 dirty=false 变为全 dirty=true 的过程中，每个 slot 都必然被新上下文写入并 touch**；4-way tree-PLRU 的所有状态位均会被这些 touch 重写。因此首次可能消费该 PLRU 时，其状态已完全由新上下文 touch 序列决定，不再依赖旧上下文状态。
> 该结论以当前 victim 仲裁顺序和 `WriteBufferSize=4` 为前提。若未来修改为在存在 clean slot 时也消费 PLRU，或改变 buffer 深度 / 替换策略实现，则需重新评估是否需要显式刷新内部 PLRU。

#### 4.3.3 刷新期间的读写污染

WriteBuffer 在刷新窗口（T ~ `聚合resetDone`）内的污染防护由**入队门控**承担：门控 `io.write.head.valid`，阻止窗口内训练/multi-hit 路径的旧上下文写请求进入 buffer。出队方向不设门控，由复位反压与空 buffer 保证（§4.2.3 层面四）。

```scala
// AheadBtbBank.scala
writeBuffer.io.write.head.valid := io.writeReq.valid && (if (HasBpuFlush) !bpuFlushing else true.B)
```

aBTB 的两个写来源共享同一 `io.writeReq` 端口，被统一拦截：

- **训练写**：
  - T 拍：t2 旧值驱动的残留写到达入队口，`bpuFlushing` 在 T 拍已拉高，被当拍拦截，如上所示；
  - T+1 起：写请求的生成源头由 §4.7 双策略阻断，不再产生新训练写。
- **multi-hit 写**：
  - T 拍：multi-hit 使用 T-1 拍注册的旧 S2 数据触发，零写请求到达入队口即被拦截，如上所示；
  - T+1 起：S2 寄存器被 §4.6 策略一清零、S0 读被策略二门控，不再产生新 multi-hit。

门控不区分数据的上下文归属，窗口内到达的所有写请求（含新上下文的早期训练）一律被丢弃，这是刷新窗口的固有代价。

---

### 4.4 TakenCounter

> [!abstract] 结论
> takenCounter 无需独立刷新。

**I/O 接口**：takenCounter 是 aBTB 顶层的 `RegInit` 寄存器数组，无独立模块边界，不新增任何 I/O 端口，也不直接消费 `io.contextFlush`/`io.bpuFlushing`；刷新窗口内其读出口 `s1_ctrVec` 由 §4.6 策略二的 `Mux(io.bpuFlushing, …)` 在消费侧门控，故 takenCounter 自身无需刷新握手。

判断一个存储结构是否需要刷新，核心问题是：**上下文切换后，残留数据能是否会持续产生错误影响？** 对此从消费端和生产端分别分析。

**消费端。** `takenCounter` 通过 `isPositive`/`isSaturate` 参与预测决策，但这些值受 `hitMask` 门控——只有命中的 Way 才会消费 takenCounter 的输出。EntrySRAM 刷新后所有 `valid` 位清零，预测时不可能命中，`hitMask` 恒为全零，残留值不会被任何逻辑消费。

**生产端。** 训练通路对 takenCounter 有两类写操作，逐一分析：

1. **selfDecrease / selfIncrease**：这两种操作由 T1 阶段的 `t1_fire` 触发；§4.7 已在整个 `bpuFlushing` 窗口门控 `t1_fire`，刷新期间不会执行。EntrySRAM 刷新后不存在有效条目，新上下文训练请求必然 miss，也不会消费旧 counter。
   - 当新上下文的训练数据到来时，miss 分配新条目，WriteBuffer 写请求携带 `needResetCtr = true`，出队后自动触发 `resetWeakPositive()`，将对应 takenCounter 重置为弱正（值 2）。
2. **resetWeakPositive**：由 WriteBuffer 出队的 `writeResp` 触发，且仅当 `needResetCtr = true` 时执行。该触发点是可变延迟的 WriteBuffer 出队，而不是固定 T1 阶段；这正是新条目分配后的行为，不需要额外处理。

**时序安全性**。WriteBuffer 出队时，SRAM 写入与 `writeResp` 触发 needReset 在**同一拍**发生。下一拍 SRAM 条目可读时，takenCounter 已经是重置后的值。不存在"条目有效但计数器仍是旧值"的窗口。

**前提条件。** 上述论证依赖 EntrySRAM 刷新正确（保证 `hitMask` 全零）以及 WriteBuffer 的 `needResetCtr` 标志位不被破坏，这两点由 EntrySRAM 与 WriteBuffer 各自的刷新方案负责保证，不属于 `takenCounter` 自身需要解决的问题。

---

### 4.5 Replacer

> [!abstract] 结论
> ReplacerState 需要刷新，方法为：给 ReplacerState 加 `io.contextFlush` 端口，同时阻断预测路径和训练路径的残留写。

#### 4.5.1 I/O 接口修改

`replacers` 在 `AheadBtb` 顶层实例化（不在 bank 内），PLRU 清零（§4.5.2）需单独打通 `contextFlush` 通路。`bpuFlushing` 在顶层直接门控 replacer 的 `readValid`/`writeValid`（§4.5.3），不下发到 replacer。

`ReplacerIO` 继承 `AheadBtbBundle`，可直接访问 `HasBpuFlush`，新端口按 00 §3.1 用 `Option.when` 声明；`AheadBtbReplacer` 实例化 `ReplacerState` 时将 `hasContextFlush` 绑定 `HasBpuFlush`（§4.5.2），两级连线均放入 `if (HasBpuFlush)`：

```scala
// Bundles.scala -- ReplacerIO 新增端口
class ReplacerIO(implicit p: Parameters) extends AheadBtbBundle {
  ...
  val contextFlush: Option[Bool] = Option.when(HasBpuFlush)(Input(Bool()))  // 新增：T 拍单脉冲，触发 PLRU 状态清零
}

// AheadBtb.scala -- 顶层分发（关闭时端口不生成，整段不连线）
if (HasBpuFlush) {
  replacers.foreach { r =>
    r.io.contextFlush.get := io.contextFlush.get
  }
}

// AheadBtbReplacer.scala -- 透传到 ReplacerState（详见 §4.5.2；关闭时整段不生成）
if (HasBpuFlush) {
  states.io.contextFlush.get := io.contextFlush.get
}
```

#### 4.5.2 清空 Replacer

EntrySRAM 刷新后所有表项无效，训练请求必然 miss，触发写新表项。写新表项时需要 replacer 提供 victim way，victim way 决定新 entry 写入哪个 way，直接影响新上下文的替换策略。

如果 PLRU 状态残留旧上下文的访问历史，新上下文的 victim 选择会被旧模式影响，因此 replacer 必须刷新。`ReplacerState` 内部的 `states` 是 `RegInit` 寄存器数组（`ReplacerState.scala:58`），加 `contextFlush` 输入端口后 1 拍清零所有 set 的 PLRU 状态。`ReplacerState` 被 aBTB 和 mBTB 共用（`MainBtbReplacer.scala:51`），且是**不带 implicit p 的普通 `Module`**，无法直接访问 `HasBpuFlush`，因此端口仿 WriteBuffer 的 `hasFlush` 做成参数化可选（`hasContextFlush`），由 `AheadBtbReplacer` 实例点绑定 `HasBpuFlush`；模块内部以 `hasContextFlush` 为条件解包中间变量、消费落入 `if (hasContextFlush)`，禁止 `getOrElse(false.B)` 常驻 `Bool`（00 §3.1），避免破坏 mBTB。

```scala
// AheadBtbReplacer.scala -- 实例点绑定编译开关（关闭时端口不生成）
private val states = Module(new ReplacerState(
  NumSets,
  NumWays - 1,
  NumExtraReadPort = 1,
  hasContextFlush  = HasBpuFlush
))

// ReplacerState.scala
class ReplacerState(
    NumSets:           Int,
    StateBits:         Int,
    NumExtraReadPort:  Int = 0,
    NumExtraWritePort: Int = 0,
    hasContextFlush:   Boolean = false  // 新增
) extends Module {
  class ReplacerStateIO extends Bundle {
    ...
    val contextFlush: Option[Bool] = Option.when(hasContextFlush)(Input(Bool()))  // 新增
  }

  // 模块体顶部：中间变量（00 §3.1；else false.B 仅为类型占位，关闭时消费点均在 if 内不生成）
  private val contextFlush = if (hasContextFlush) io.contextFlush.get else false.B

  private val states = RegInit(VecInit(Seq.fill(NumSets)(0.U.asTypeOf(UInt(StateBits.W)))))

  io.write.foreach { port =>
    when(port.valid) { states(port.bits.setIdx) := port.bits.state }
  }
  // last-connect 覆盖：contextFlush 拉高时强制清零所有 set；
  // 消费落入 if (hasContextFlush)，关闭时清零逻辑整块不生成
  if (hasContextFlush) {
    when(contextFlush) { states.foreach(_ := 0.U) }
  }
}
```

> `AheadBtbReplacer` 透传 `states.io.contextFlush.get := io.contextFlush.get`，见 §4.5.1。

**影子 PLRU 清零**

`AheadBtbReplacer` 在 `if (EnableCommitGHistDiff)` 块内额外实例化了一个 `SetAssocLRU`（`AheadBtbReplacer.scala:65`），作为功能态 `states` 的一致性校验参考。其内部 `state_vec` 是独立的 PLRU 存储，刷新后必须与 `states`（全 0）一致，否则 `assert(genReplaceWay === replacerWay)`（`AheadBtbReplacer.scala:84-94`）会挂。

采用 uBTB 同款 `access()` 公开 API 清零：`get_next_state` 对 `Seq[Valid]` 用 `foldLeft` 依次应用 touch（`rocket-chip/src/main/scala/util/Replacement.scala:21`），特定序列可把 PLRU 驱动到全 0。4-way PLRU 的清零序列为 `[1, 3]`--touch 1 后 `(bit[2],bit[1],bit[0])=(1,?,0)`，touch 3 后 `(0,0,0)`（bit[0] 沿用上一步的 0）。64 个 set 各自独立，每 set 喂 `[1, 3]`。`AheadBtbReplacer` 模块体顶部同样先解包中间变量（00 §3.1 / 01 §6.2），该块的 `contextFlush` 消费嵌套在既有 `if (EnableCommitGHistDiff)` 内的 `if (HasBpuFlush)` 中，清零路径以 `when(contextFlush).elsewhen(readWriteConflict).otherwise(...)` 链式仲裁并入（`contextFlush` 优先级最高，当拍并发读写让位于清零），关闭时恢复原始仲裁路径（00 §3.1）：

```scala
// AheadBtbReplacer.scala -- 模块体顶部
// else false.B 仅为 Scala 类型占位：关闭时所有消费点均在 if 内不生成
private val contextFlush = if (HasBpuFlush) io.contextFlush.get else false.B

// AheadBtbReplacer.scala -- if (EnableCommitGHistDiff) 块内
if (EnableCommitGHistDiff) {
  val replacer = ReplacementPolicy.fromString(Some("setplru"), NumWays, NumSets)
  val readWriteConflict = io.readValid && io.writeValid && (io.readSetIdx === io.writeSetIdx)

  if (HasBpuFlush) {
    // 4-way PLRU 清零：每 set 依次 touch way 1、way 3，foldLeft 收敛到全 0
    val flushSets = (0 until NumSets).flatMap(s => Seq(s.U, s.U))
    val flushTouches = (0 until NumSets).flatMap { _ =>
      Seq(1.U, 3.U).map { w =>
        val v = Wire(Valid(UInt(WayIdxWidth.W)))
        v.valid := contextFlush
        v.bits  := w
        v
      }
    }

    when(contextFlush) {
      replacer.access(flushSets, flushTouches)
    }.elsewhen(readWriteConflict) {
      replacer.access(io.writeSetIdx, io.writeWayIdx)
    }.otherwise {
      when(io.writeValid) {
        replacer.access(io.writeSetIdx, io.writeWayIdx)
      }
      when(io.readValid) {
        // touchSets/touchWays 保持原位置（:75-76），块内局部声明，见代码块后说明
        val touchSets = Seq.fill(NumWays)(io.readSetIdx)
        val touchWays = Seq.fill(NumWays)(Wire(Valid(UInt(WayIdxWidth.W))))
        touchWays.zip(io.readWayMask).zipWithIndex.foreach { case ((t, r), i) =>
          t.valid := r
          t.bits  := i.U
        }
        replacer.access(touchSets, touchWays)
      }
    }
  } else {
    // 关闭分支逐字恢复原始仲裁路径
    when(readWriteConflict) {
      replacer.access(io.writeSetIdx, io.writeWayIdx)
    }.otherwise {
      when(io.writeValid) {
        replacer.access(io.writeSetIdx, io.writeWayIdx)
      }
      when(io.readValid) {
        val touchSets = Seq.fill(NumWays)(io.readSetIdx)
        val touchWays = Seq.fill(NumWays)(Wire(Valid(UInt(WayIdxWidth.W))))
        touchWays.zip(io.readWayMask).zipWithIndex.foreach { case ((t, r), i) =>
          t.valid := r
          t.bits  := i.U
        }
        replacer.access(touchSets, touchWays)
      }
    }
  }
  // ... replacerWay / assert 部分不变
}
```

**`touchSets`/`touchWays` 的作用域处理**：现有代码中二者声明在 `when(io.readValid)` 块内（`AheadBtbReplacer.scala:75-76`），受 Scala `val` 块级作用域限制，无法提升引用到 `when(contextFlush)` 重构后的仲裁链之外。因此上例让**两个分支各自在块内局部声明**这组 wire：源码层面重复了声明与驱动各一份，但 `if (HasBpuFlush)` 是 elaboration 期常量，任一配置只生成其中一个分支，**生成 RTL 无任何重复**；关闭分支即为现网代码的逐字副本，天然满足 00 §3.1 恢复原式的要求，无需额外的行为等价性论证。

`flushSets` 与 `flushTouches` 各 128 项（64 set × 2 touch），`SetAssocLRU.access` 内部对每 set 过滤出 2 个 touch，foldLeft 先 1 后 3 收敛到全 0。代价是 128 touch 的硬件开销，但只用公开 `access()` API，不依赖 `state_vec` 内部结构。

#### 4.5.3 刷新期间的读写污染

`io.contextFlush` 信号拉高的当拍，预测流水线与训练流水线可能同时向 replacer 发起读/写请求，与刷新操作产生结构冲突。此外，replacer 刷新完成后、aBTB 主体 SRAM 仍处于清零窗口期间（T+1 ~ T+64），乃至 SRAM 清零完成后 ~ `聚合resetDone` 的残留窗口期间（T+65 ~ `聚合resetDone`），上述两条流水线仍可能对已清零的 replacer 状态发起写操作，造成污染。

为此，使用 `bpuFlushing` 信号对读写使能进行门控：在 BPU 整体刷新窗口内（T ~ `聚合resetDone`）强制屏蔽 replacer 的读写请求。引用 §4.2.1 处 `AheadBtb` 模块顶部的中间变量，门控项用 if 表达式内联，关闭时恢复原式（00 §3.1 / 01 §6.2 模板 (b)）。以下两段修改 `AheadBtb.scala` 已有的 replacer 接线（分别对应原 `replacers.zipWithIndex.foreach` 读路径块和 `replacers.zip(banks).foreach` 写路径块）：

```scala
// AheadBtb.scala -- 读路径（原 replacers.zipWithIndex.foreach 块）
replacers.zipWithIndex.foreach { case (r, i) =>
	r.io.readValid   := s2_valid && s2_hit && s2_bankMask(i) && (if (HasBpuFlush) !bpuFlushing else true.B)
	r.io.readSetIdx  := s2_setIdx
	r.io.readWayMask := s2_hitMask
}
...
// AheadBtb.scala -- 写路径（原 replacers.zip(banks).foreach 块）
replacers.zip(banks).foreach { case (r, b) =>
	r.io.writeValid  := b.io.writeResp.valid && (if (HasBpuFlush) !bpuFlushing else true.B)
	r.io.writeSetIdx := b.io.writeResp.bits.setIdx
	r.io.writeWayIdx := b.io.writeResp.bits.wayIdx
}
```

---

### 4.6 如何防止预测路径污染刷新结果

预测流水线通过两种写行为污染刷新结果：s2 多路命中零写 WriteBuffer、s2 命中 touch 写 Replacer；二者均依赖旧数据（寄存器残留、SRAM holdRead、override 旁路）流入流水线产生命中。

策略一用 `contextFlush` 清空 s1/s2/s3 中的非控制数据与索引寄存器，策略二用 `bpuFlushing` 门控 s0 读与 s1 组合逻辑阻断旧数据流入。`fire`、`valid` 等流水线控制信号不纳入显式清零；清零后 `hitMask` / 前置控制向量为 0，使残留控制信号无法携带旧数据产生有效输出或写请求。

> **跨模块说明**：aBTB 经 `io.abtbPos`/`io.abtbResult` 送往 uTAGE 的信号不在 aBTB 侧额外加门控；uTAGE 侧对自身 A2/A3 残留状态的刷新要求见 05 §4.4。

**策略一：清空预测流水线中的旧信息**

清空预测流水线中的非控制数据、索引与 mask 寄存器。清零块整块落入 `if (HasBpuFlush)`（01 §6.2 模板 (a)，关闭时不生成。`contextFlush` 为 §4.2.1 处 `AheadBtb` 顶部中间变量）。依赖 Chisel **后赋值覆盖先赋值**，因此必须置于被清寄存器的全部既有赋值**之后**：s1 组的 `RegEnable`（`AheadBtb.scala:134-136`）、s3 组的 `RegInit` 与 `when(s2_fire)`（`:152-180`）、s2 组的 `RegEnable`（`:163-170`）、`s2_jumpValidVec`/`s2_conditionValidVec` 的 `when(s1_fire)...elsewhen(s2_flush || s2_fire)`（`:193-203`），建议整块放在 `:203` 之后、预测结果输出（`:205`）之前。训练流水线的独立清零块见 §4.7。

```scala
// 位于 AheadBtb.scala:203（s2_jumpValidVec/s2_conditionValidVec 既有赋值块）之后、
// 预测结果输出（:205）之前；后赋值覆盖上述 RegInit / RegEnable / when 块（01 §6.2 模板 (a)）
if (HasBpuFlush) { when(contextFlush) {
    // s1 索引 / bank 状态
    s1_setIdx   := 0.U
    s1_bankIdx  := 0.U
    s1_bankMask := 0.U

    // s2 数据寄存器
    s2_setIdx     := 0.U
    s2_bankIdx    := 0.U
    s2_bankMask   := 0.U
    s2_entries    := 0.U.asTypeOf(s2_entries)
    s2_hitMask    := 0.U.asTypeOf(s2_hitMask)
    s2_startPc    := 0.U.asTypeOf(s2_startPc)
    s2_ctrResult  := 0.U.asTypeOf(s2_ctrResult)
    s2_strongBias := 0.U.asTypeOf(s2_strongBias)
    // io.predCtrl 前置控制向量：直出 BPU 顶层，不被 pred.valid/hitMask 封死，必须显式清零
    s2_jumpValidVec      := VecInit.fill(NumAheadBtbPredictionEntries)(false.B)
    s2_conditionValidVec := VecInit.fill(NumAheadBtbPredictionEntries)(false.B)
    // s3 数据寄存器
    s3_setIdx     := 0.U
    s3_bankIdx    := 0.U
    s3_bankMask   := 0.U
    s3_entries    := 0.U.asTypeOf(s3_entries)
    s3_startPc    := 0.U.asTypeOf(s3_startPc)
    s3_ctrResult  := 0.U.asTypeOf(s3_ctrResult)
    s3_strongBias := 0.U.asTypeOf(s3_strongBias)
  }
}
```

**策略二：阻断旧信息进入流水线**

在整个 BPU 刷新窗口（T ~ `聚合resetDone`）内禁止 aBTB 在 s0 阶段发送 SRAM 读请求，也禁止 EntrySRAM 中保持的最后一次读数据进入预测流水线。读请求门控引用 `AheadBtb` 顶部中间变量、用 if 表达式内联（模板 (b)），s1 两处 Mux 按 01 §6.2 模板 (c) 整式放入 `if`、`else` 给无门控原式：

```scala
banks.zipWithIndex.foreach { case (b, i) =>
    b.io.readReq.valid := predictReqValid && s0_bankMask(i) && (if (HasBpuFlush) !bpuFlushing else true.B)
}

private val s1_entries = if (HasBpuFlush) Mux(bpuFlushing,
    0.U.asTypeOf(Vec(NumWays, new AheadBtbEntry)),
    Mux1H(s1_bankMask, banks.map(_.io.readResp.entries))
) else Mux1H(s1_bankMask, banks.map(_.io.readResp.entries))

private val s1_ctrVec = if (HasBpuFlush) Mux(bpuFlushing,
    VecInit.fill(NumWays)(TakenCounter.Zero),
    takenCounter(s1_bankIdx)(s1_setIdx)
) else takenCounter(s1_bankIdx)(s1_setIdx)
```

---

### 4.7 如何防止训练路径污染刷新结果

与预测流水线的防御措施类似，训练流水线部分也从两方面入手解决训练路径污染的问题，并遵循 02 §4.4 与 TAGE 统一采用的训练流水 kill 范式：

1. **策略一（阻断推进）**：用 `!bpuFlushing` 门控 `t0_fire`、`t1_fire` 和 `t2_fire`，使刷新窗口内所有训练级有效 fire 均为 false；
2. **策略二（清残留）**：用 `contextFlush` 当拍清零训练流水线中的非控制寄存器 `t1_train` 与 t2 数据寄存器组。

aBTB 的训练流水线为三级：T0 接收训练请求，T1 更新 `takenCounter` 并生成写请求字段，T2 根据寄存后的 bank、way 和写控制信息驱动 `io.writeReq`。其中 `t0_train` 是输入 Wire alias，不是寄存器；`t1_train` 和 T2 数据组保存跨拍旧上下文内容，仍需由策略二清零。

现有方案通过 T0 入口门控、T1/T2 payload 清零和 WriteBuffer 入队门控，已经能够避免功能状态污染：T 拍残留的 T2 写请求会在 bank 入队侧被 `!bpuFlushing` 拦截，T+1 起 T2 写控制又已清零。在此基础上增加 T1/T2 fire 门控，是为了建立统一的集中式 kill 不变量，直接阻止 T1 counter 更新和 T2 bank 写请求，不再依赖逐项枚举末级副作用。原有 payload 清零和存储边界门控继续保留。

门控使用覆盖 T ~ 聚合完成的 `bpuFlushing` 电平，而不是单拍 `contextFlush`，从而使整个刷新窗口都满足：

```text
bpuFlushing = true => t0_fire = t1_fire = t2_fire = false
```

**策略一：阻断旧信息进入流水线**

三个有效 fire 均直接在原表达式上追加同型门控；`if-else` 保证关闭开关时恢复原式（01 §6.2 模板 (b)）：

```scala
private val t0_fire = io.enable && io.fastTrain.get.valid && t0_train.abtbMeta.valid && (if (HasBpuFlush) !bpuFlushing else true.B)

private val t1_fire = RegNext(t0_fire, init = false.B) && (if (HasBpuFlush) !bpuFlushing else true.B)

private val t2_fire = RegNext(t1_fire, init = false.B) && (if (HasBpuFlush) !bpuFlushing else true.B)
```

无需把两个 `RegNext` 单独命名或用 `contextFlush` 显式清零。刷新 T 拍，`!bpuFlushing` 立即组合屏蔽其旧输出；同一时钟沿，T1 内部寄存器装入门控后的 `t0_fire=0`，T2 内部寄存器装入门控后的 `t1_fire=0`，因此二者从 T+1 起自然保持为 0。

**策略二：`contextFlush` 当拍清零训练数据寄存器**

清零块置于 t2 寄存器组声明（`:335-342`）之后、bank 写请求分发（`:344`）之前，依赖后赋值覆盖 `RegNext`（01 §6.2 模板 (a)）：

```scala
if (HasBpuFlush) {
  when(contextFlush) {
    // t1_fire/t2_fire 已由 bpuFlushing 整窗门控，不纳入显式清零。
    t1_train := 0.U.asTypeOf(t1_train)
    t2_victimWayIdx      := 0.U.asTypeOf(t2_victimWayIdx)
    t2_setIdx            := 0.U
    t2_bankMask          := 0.U
    t2_hitMaskOH         := 0.U.asTypeOf(t2_hitMaskOH)
    t2_needWriteNewEntry := false.B
    t2_needCorrectTarget := false.B
    t2_writeEntry        := 0.U.asTypeOf(t2_writeEntry)
  }
}
```

两策略合并后，T 拍起 `t0_fire/t1_fire/t2_fire` 同时为 false，T1 不再更新 counter，T2 不再产生训练写请求；`contextFlush` 在 T 拍时钟沿进一步清除 T1/T2 payload。WriteBuffer 入队和 replacer 读写仍由各自的 `!bpuFlushing` 门控保护到聚合刷新结束；随后 `bpuFlushing` 撤销，新上下文训练按正常流水延迟恢复。

---

### 4.8 刷新完成信号 `resetDone`

aBTB 顶层向 BPU 顶层输出 `resetDone`，直接由 `io.sramResetDone` 组合生成：

```scala
io.sramResetDone := banks.map(_.io.sramResetDone).reduce(_ && _)  // 既有赋值，保持不变

if (HasBpuFlush) {
  io.resetDone.get := io.sramResetDone && !contextFlush
}
```

`resetDone` 赋值为纯组合逻辑，随整块落入 `if (HasBpuFlush)`：关闭时该赋值不生成，`resetDone` 端口本身为 `None`（`BasePredictorIO` 的 Option 声明，01 §5.1.3），无需 else 分支（00 §3.1）。

逐拍时序（`abtbFlushEnable = true`）：

- T 拍 `contextFlush` 拉高，`resetDone` 经 `!contextFlush` 项组合拉低（此拍 `io.sramResetDone` 仍为旧值高，被该项屏蔽）
- T+1~T+64 `_resetState = true`，`io.sramResetDone = false`
- T+65 `io.sramResetDone` 拉高，`resetDone` 同拍组合拉高
- T+65 当拍聚合 `resetDone = 1`，状态机迁出 `s_flushing`，T+66 进入 `s_done`

---

### 4.9 编译开关裁剪清单

按 00 §3.4 的要求，`HasBpuFlush=false` 时以下 aBTB 刷新专用结构必须从生成 RTL 中结构性消失（验收标准见 00 §5.2）：

| 类别                 | 应消失的对象                                                                                                                                                                                                                       | 对应小节                           |
| -------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ---------------------------------- |
| 端口                 | `BasePredictorIO` 的 `contextFlush`/`bpuFlushing`/`resetDone`（01 声明）；`BankIO` 的 `contextFlush`/`bpuFlushing`；`WriteBufferIO` 的 `contextFlush`；`ReplacerIO` 与 `ReplacerStateIO` 的 `contextFlush` | §4.2.1、§4.3.1、§4.5.1、§4.5.2 |
| EntrySRAM 清零       | `extraReset = HasBpuFlush` 绑定下的 `extra_reset` 端口及 `sram.extra_reset.get` 连线                                                                                                                                         | §4.2.2                            |
| WriteBuffer 清零     | `if (hasContextFlush)` 内的 `dirty`/`shadowValid` 清零写（关闭分支仅保留既有 `flush` 路径）                                                                                                                                | §4.3.2                            |
| WriteBuffer 入队门控 | `io.write.head.valid` 中的 `!bpuFlushing` 门控项（引用模块顶部中间变量）                                                                                                                                                       | §4.3.3                            |
| Replacer 清零        | `ReplacerState` 的 `when(contextFlush)` 整表清零写；影子 PLRU 的 `flushSets`/`flushTouches`（64 set × 2 touch 组合链）与 `access(flushSets, flushTouches)` 分支                                                         | §4.5.2                            |
| Replacer 读写门控    | `readValid`/`writeValid` 中的 `!bpuFlushing` 门控项                                                                                                                                                                          | §4.5.3                            |
| 预测残留清理         | s1/s2/s3 非控制数据与索引寄存器、`s2_jumpValidVec`/`s2_conditionValidVec`（io.predCtrl 前置控制向量）的`when(contextFlush)` 清零块；`fire`/`valid` 控制信号不清零                                                        | §4.6 策略一                       |
| 预测流入门控         | s0 读请求`!bpuFlushing` 门控项；`s1_entries`/`s1_ctrVec` 的刷新 Mux（关闭时直接取 `Mux1H`/`takenCounter` 原式）                                                                                                          | §4.6 策略二                       |
| 训练残留清理         | 策略二：`when(contextFlush)` 的 `t1_train` + T2 数据寄存器组清零块（fire 控制寄存器不显式清零）；策略一：`t0_fire`/`t1_fire`/`t2_fire` 的 `!bpuFlushing` 门控项                                              | §4.7                              |
| 完成信号             | `io.resetDone.get := io.sramResetDone && !contextFlush` 组合赋值（无新增寄存器）                                                                                                                                                 | §4.8                              |
| PHR 隔离             | `phrIsolated` 状态寄存器、`contextFlush` 置位逻辑，以及 `s0_simpleHash` 的 PC-only Mux                                                                                                                                       | §4.10                             |

关闭后的数据通路必须与未引入刷新机制的原始 aBTB 完全一致：`readReq`/`writeReq`/`readValid`/`writeValid`、`s1_entries = Mux1H(...)`、`s1_ctrVec = takenCounter(...)`、`s0_simpleHash = PHR fold`、WriteBuffer 仅有 `flush` 的清零路径、影子 PLRU 的原始读写仲裁、`io.sramResetDone` 聚合均恢复原式，不保留额外门控、延迟或默认值（00 §3.1）。

> `WriteBuffer` 与 `ReplacerState` 为 mBTB 共用模块（04 文档同样涉及）：其 `hasContextFlush=false`（默认）实例不得因本方案生成任何刷新端口或清零逻辑，即裁剪以实例化参数为粒度，与 `HasBpuFlush` 的绑定仅发生在 aBTB 实例点。

---

### 4.10 aBTB 内部针对 PHR 的隔离方案

aBTB 的 S0 index 由 PC 与 PHR fold 共同产生：`s0_hashIndex = startPc(...) ^ s0_simpleHash`。PHR 当前尚未纳入顶层 contextFlush 方案；若直接继续使用旧 PHR，EntrySRAM 虽已清空，新上下文的 bank/set 分配足迹仍会受旧上下文路径历史影响。

在 PHR 顶层刷新方案落地前，aBTB 内部使用 sticky PC-only index 隔离该依赖。`contextFlush` 当拍组合屏蔽 PHR hash，并在次拍置位 `phrIsolated`；该状态一旦置位，在本方案内保持为 true，使后续 aBTB index 永久退化为 PC-only：

```scala
private val phrIsolated =
  if (HasBpuFlush) RegInit(false.B) else false.B

if (HasBpuFlush) {
  when(contextFlush) {
    phrIsolated := true.B
  }
}

private val s0_phrHash = io.normalPathHist
  .getHistWithInfo(AbtbHashFhInfo)
  .foldedHist(AheadBtbHashBitWidth - 1, 0)

private val s0_simpleHash =
  if (HasBpuFlush) {
    Mux(phrIsolated || contextFlush, 0.U, s0_phrHash)
  } else {
    s0_phrHash
  }
```

该方案不能只在 `bpuFlushing` 期间短暂置零：刷新窗口结束后旧 PHR 仍会继续影响新上下文 entry 的 bank/set 分配，因此必须使用 sticky 状态。其代价是首次 contextFlush 后 aBTB 失去基于 PHR 的 index 分散能力，alias / conflict 可能增加。

未来 PHR 刷新方案落地后，不能直接清除 `phrIsolated` 恢复 PHR index；PC-only 期间分配的表项会因 index 变化而失联。恢复必须通过显式握手完成：先确认 PHR 已进入新上下文的干净状态，再对 aBTB 执行一次新的清零，使 EntrySRAM 与后续 index 方式一致后，才可退出隔离模式。
