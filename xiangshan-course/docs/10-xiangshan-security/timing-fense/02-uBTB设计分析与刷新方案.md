# 02-uBTB设计分析与刷新方案

> 本文档是 uBTB（`MicroBtb`）的**真实刷新方案**，属于 [01-BPU刷新方案概览](01-BPU刷新方案概览.md) §6.1 占位阶段之后的落地内容：实现时用 §4.5 的真实完成条件替换 `MicroBtb.scala` 中的 `io.resetDone.get := true.B` 恒真占位。
>
> 1. **接口契约**：`contextFlush`/`bpuFlushing`/`resetDone` 由 `BasePredictorIO` 以 `Option.when(HasBpuFlush)` 声明（01 §5.1.3），BPU 顶层已按本事务 `activeFlushMask` 门控后下发（01 §5.3.3），uBTB 收到的均为针对自身的有效信号。
> 2. **编译开关**：本文对 `contextFlush`/`bpuFlushing` 的消费遵循 [00-编译开关实现方案](00-BPU刷新机制编译开关实现方案.md) §3.1 统一编码规则与 03 文档同款模板：`MicroBtb`/`MicroBtbReplacer` 模块体顶部把 Option 端口解包为中间变量（`private val contextFlush = if (HasBpuFlush) io.contextFlush.get else false.B` 等），模块内统一引用变量名。刷新专用结构由 Scala `if (HasBpuFlush)` 在 elaboration 期裁剪；既有数据通路中的内联门控在关闭分支选择 `true.B` 恒等项，再由 Chisel/CIRCT 编译期常量折叠恢复原表达式。Option 连线与输出端口驱动仍用 `if (HasBpuFlush) { ... .get ... }`。`HasBpuFlush=false` 时应消失的结构清单见 §4.6。
> 3. **刷新窗口**：`bpuFlushing` 覆盖 `contextFlush` 单拍至聚合 `resetDone` 置位、状态机离开 `s_flushing` 的整个窗口（01 §5.2.2、§5.4）。

---

## 1. uBTB 的预测流水线

**第一拍：** 接收 `startPC`，并与 uBTB 所有的表项（entries）并行比较，得出命中向量。

**第二拍：**

1. **缓冲与解码**：根据上一拍得到的命中向量 `s1_hitIdx` 读 uBTB Entry 对应的表项 `s1_hitEntry`
2. **生成预测**：
   - uBTB 认为一旦命中即认为该分支且预测结果位 taken
   - 然后把表项中缓存的 target、CFI Position (指令槽位置)、分支属性等打出到 `io.prediction` 端口
3. **更新替换策略**：将命中的线索发送给 `replacer`，以保证该活跃表项不被过早驱逐

预测结果输出

```scala
  io.prediction.valid            := s1_hit
  io.prediction.bits.taken       := s1_hit
  io.prediction.bits.cfiPosition := s1_hitEntry.slot1.position
  io.prediction.bits.target      := getFullTarget(s1_startPc, 
												  s1_hitEntry.slot1.target,
												  s1_hitEntry.slot1.targetCarry)
  io.prediction.bits.attribute   := s1_hitEntry.slot1.attribute
```

---

## 2. uBTB 的训练流水线

uBTB 的训练本质是对寄存器表项（Entry）的更新，整个过程分为两拍执行：

**T0 阶段：读取与匹配**

**1. 获取训练数据**：默认接收 `fastTrain`（S3预测结果）或由 FTQ 转发的真实训练数据。
**2. 并行匹配**：提取 `t0_tag` 并与全相连 `entries` 进行并行比对产生 `t0_hitOH`，得到基础命中情况。
**3. 冲突旁路**：若当前命中的表项正被 T1 驱逐，则视为“未命中”；若正被 T1 更新，则直接截获（Bypass）T1 尚未写回的最新数据。
**4. 状态预判**：
	- 计算当前 entry 的置信度（Useful）是否见底；
	- 验证命中的表项内部记录的详细字段（`t0_hitPositionSame / t0_hitAttributeSame / t0_hitTargetSame`）和本次训练真实的表现是否完全一致。

> [!note] ✏️ **为什么要旁路？**
> 假设周期 1 来了一个训练请求 A 修改了 Entry 3，紧跟着周期 2 来了一个请求 B 也访问 Entry 3。此时 A 的修改还在 T1 阶段的手里，还没写回到 `entries` 阵列里。此时 B 在 T0 去读 `entries` 就会读到旧的错误数据（Data Hazard）。

**T1 阶段：决策与写回**

T0 的信号打拍进入 T1，核心任务是制定更新策略并执行写入。

**1. 状态决策**：T1 阶段并非直接就去修改底层存储器，而是先将即将被写回的数据存入 `t1_updatedEntry`：
	- **未命中**：初始化一个全新表项，但只有实际 Taken 它才会被最终写入。
	- **命中但不匹配**：若置信度耗尽则直接覆盖；否则仅扣减置信度，不立即替换。
	- **命中且完全匹配**：增加该表项的置信度以作保护。
**2. 物理写回**：
	- 确定写入位置（命中则“原地更新”，未命中则索取伪 LRU 分配的 `victim` 位置）。
	- **仅当命中，或未命中但实际发生跳转时**，才将最新数据写入寄存器阵列。
**3. 维护替换矩阵**：将本次更新的位置发送给 Replacer 模块，更新状态树。

**3. 数据写回**

计算最终要写入哪一个槽位 `t1_updateIdx`：

- 如果命中了（`t1_hit` 是 true），就在原本属于它的位置更新 `t1_hitIdx`
- 如果没有命中（`t1_hit` 为 false），需要新建。这时候去查替换算法模块（Replacer）给出的伪 LRU 牺牲者是谁（`replacer.io.victim`），去顶替那个最没用的老表项

执行写回：当【命中】或【未命中但实际 taken】时，才将 `t1_updatedEntry` 更新到 entry 寄存器里。

**4. 更新替换矩阵**

将本次访问（或修改）的位置 `t1_updateIdx` 告知替换算法模块。替换模块会据此更新其内部的伪 LRU（近期最少使用）树。

---

## 3. uBTB 的整体架构

### 3.1 uBTB Entry

**数据结构**

uBTB 采用 32 项全相联结构，用 RegInit 寄存器数组实现（非 SRAM）。每个 MicroBtbEntry 的结构如下：

```
MicroBtbEntry (97 bits) 
	├── tag: UInt(22) — 22 bits 虚拟地址高位 tag 
	├── usefulCnt: SaturateCounter(2) — 2 bits 有用性饱和计数器 
	├── slot1: Slot1 — 36 bits 主分支槽 
	│     ├── position: UInt(5) — 5 bits 分支在取指块内位置 
	│     ├── attribute: BranchAttribute — 8 bits 分支类型 + RAS 动作 
	│     ├── target: UInt(22) — 22 bits 部分跳转目标 
	│     └── isStaticTarget: Bool — 1 bit 目标是否为静态 
	│
	└── slot2: Slot2 — 37 bits 第二分支槽 
		├── position: UInt(5) — 5 bits 
		├── attribute: BranchAttribute — 8 bits 
		├── target: UInt(22) — 22 bits 
		├── valid: Bool — 1 bit slot2 是否有效 
		└── taken: Bool — 1 bit slot2 是否 taken
```

uBTB 没有独立的 valid 位，**表项的有效性由 usefulCnt 隐含表示**：usefulCnt 为 0 时表项无效。

**读写端口**

由于是寄存器数组而非 SRAM，同周期内读和写可以同时进行，因此没有端口冲突问题。但由于写操作在下个时钟沿才生效，同一周期内读同一索引会读到旧值。通过旁路逻辑解决此数据冒险：

- t0 命中的 entry 正被 t1 更新，直接截获 t1 算出的 t1_updatedEntry，跳过 entries
- t0 命中的 entry 正被t1 替换（驱逐），视为"未命中"

**写只在 t1 阶段进行**，当且仅当（`t1_fire` 为真）且 {（命中已有表项）或（未命中但实际 taken 需分配新表项）} 时，将更新 entries 阵列。

**复位机制**

- 上电时硬件复位，将 Reg 中的值的全部清零
- 关键：usefulCnt=0 → isSaturateNegative=true → valid=false，所以复位后所有表项无效
- 无额外复位端口：没有像 SRAMTemplate 的 extraReset 机制

---

### 3.2 Replacer

**具体实现**

replacer 本质上是一个 31 bit 的寄存器，初始值为 31'b0：

```scala
// MicroBtb.scala:55
private val replacer = Module(new MicroBtbReplacer)

// MicroBtbReplacer.scala:38
private val replacer = ReplacementPolicy.fromString(Replacer, NumEntries)
// Replacer = "plru",NumEntries = 32 → 实际创建 PseudoLRU(32)

// rocket-chip Replacement.scala:168
protected val state_reg = if (nBits == 0) Reg(UInt(0.W)) else RegInit(0.U(nBits.W))
```

uBTB 利用该 32 位寄存器，以二叉树的结构记录所有 uBTB 表项（共 32 项）的替换状态：

![[Pasted image 20260602115645.png]]

每个 bit 表示二叉树节点的方向：

- bit=0：左子树更"老"（下次替换优先选左）
- bit=1：右子树更"老"（下次替换优先选右）

**读写端口**

只要这两个 `Valid` 信号中有任一个 valid，PLRU 树状态就会更新：

- **predTouch**：预测命中时触发（预测流水线访问到某个 entry）
- **trainTouch**：训练写入时触发（训练流水线更新某个 entry）
- 如果同一周期两个都 valid，trainTouch 优先级更高（后应用的覆盖先应用的效果）。

uBTB 中对于 replacer 的读操作仅局限于获取 victim。

| 端口       | 方向               | 用途             | 说明                       |
| ---------- | ------------------ | ---------------- | -------------------------- |
| predTouch  | Input(Valid[UInt]) | 预测命中时 touch | 命中的 entry 索引          |
| trainTouch | Input(Valid[UInt]) | 训练更新时 touch | 更新/分配的 entry 索引     |
| usefulCnt  | Input(Vec[32])     | 读取有用度       | 用于 notUseful 优先选择    |
| victim     | Output(UInt)       | 输出牺牲者       | 两级选择：notUseful > PLRU |

**复位机制**

上电时，将整个寄存器初始化为 31'b0，即：所有 way 均未访问，victim 选 way 0。
无运行时复位机制。

---

## 4. 刷新方案

### 4.1 Entry 刷新方案

#### 4.1.1 I/O 接口与信号传递

`contextFlush` 与 `bpuFlushing` 由 BPU 顶层经 `BasePredictorIO` 的 Option 端口接入 uBTB（01 §5.1.3），并已按本事务 `activeFlushMask` 门控（01 §5.3.3），**无需额外 I/O 通路**。

uBTB 内部按 00 §3.1 / 03 同款模板在模块体顶部解包中间变量，后续所有消费统一引用变量名（`contextFlush` 直接驱动 entries 清零（§4.1.2），`bpuFlushing` 用于门控 `t0_fire`（§4.1.3、§4.4.2）与 `predTouch`/`trainTouch`（§4.2.3））。清零等刷新专用结构放入 Scala `if (HasBpuFlush)`，关闭时不参与 elaboration；对既有表达式追加的门控采用内联 Scala `if`，关闭分支返回恒等项并在 Chisel/CIRCT 编译期消除：

```scala
// bpu/ubtb/MicroBtb.scala -- 模块体顶部（首个消费点之前）
// type placeholder: all consumers below live inside if (HasBpuFlush) guards.
private val contextFlush = if (HasBpuFlush) io.contextFlush.get else false.B
private val bpuFlushing  = if (HasBpuFlush) io.bpuFlushing.get  else false.B
```

#### 4.1.2 存储结构刷新

uBTB 的 entry 表项**只在训练流水线中更新**，预测流水线不写 entry。因此，我们只需考虑刷新操作与 t1 阶段的写操作同时触发的问题。只需**让刷新的优先级高于 t1 写回**即可。entries 是寄存器数组，`contextFlush` 当拍清零、次拍生效：

```scala
// bpu/ubtb/MicroBtb.scala（引用 §4.1.1 中间变量；清零与原始写回整段放入 if (HasBpuFlush)，
// 关闭时结构性消失，else 分支保留原始写回）
if (HasBpuFlush) {
	when(contextFlush) {                                  // 优先级最高：清零
		entries.foreach(_ := 0.U.asTypeOf(new MicroBtbEntry))
	}.elsewhen(t1_fire && (t1_hit || t1_allocate)) {      // 刷新当拍阻塞 t1 写回
		entries(t1_updateIdx) := t1_updatedEntry
	}
} else {
	when(t1_fire && (t1_hit || t1_allocate)) {            // 原始写回
		entries(t1_updateIdx) := t1_updatedEntry
	}
}
```

#### 4.1.3 `bpuFlushing` 期间的污染

uBTB 没有 SRAM 和 buffer，Entry 存储是寄存器数组。因此本节不套用 SRAM/buffer 型预测器的防污染模板，而只处理 uBTB 自身可能残留或重新写入旧上下文数据的路径：

1. **清空 entries**：`contextFlush` 当拍通过 §4.1.2 的高优先级分支清空所有 `entries`，并阻止同拍 t1 写回覆盖清零结果。
2. **阻止预测输出旧 entry**：`s1_hitOH` 是 T 拍对清零前 entries 的比较结果，需由 §4.3.1 在 `contextFlush` 当拍安排清零、T+1 起生效，以消除残留命中；T 拍当拍可能输出的一拍旧预测由 redirect 冲刷语义作废，且 `predTouch` 已由 §4.2.3 门控。
3. **阻止训练数据进入流水线**：`bpuFlushing` 窗口内门控 `t0_fire`（§4.4.2），新训练数据无法进入 T0/T1，从源头避免后续生成 entry 写回。
4. **阻止数据写入 entries**：uBTB 的 entry 写回只来自 t1 阶段寄存器路径。T 拍写回由 §4.1.2 的清零优先级阻断；T 拍起 `t1_fire` 也被门控归零；因此 T 拍起 entries 写回条件持续不成立。

`bpuFlushing` 由 BPU 顶层状态机维持至聚合 `resetDone` 置位、迁入 `s_done` 才拉低（01 §5.2.2、§5.4）。在该窗口内，上述措施保证刷新前 entries 和流水线残留不会继续被消费或重新写回。

---

### 4.2 Replacer 刷新方案

#### 4.2.1 I/O 接口与信号传递

`contextFlush` 由 `MicroBtb` 传入 `MicroBtbReplacer`。`MicroBtbReplacerIO` 需新增 `contextFlush` 接口：

```scala
class MicroBtbReplacerIO extends Bundle {
  ...
  val contextFlush: Option[Bool] = Option.when(HasBpuFlush)(Input(Bool()))
  ...
}
```

`MicroBtbReplacerIO` 是 `MicroBtbReplacer` 的成员内部类，`HasBpuFlush` 通过外部实例的 trait 链（`MicroBtbModule` -> `HasBpuParameters` -> `HasXSParameter`）访问，与同文件中 `NumEntries`、`UsefulCounter()` 的访问方式一致，无需修改类声明或混入额外 trait。

`MicroBtb` 顶层连线（00 §3.1 的 `if + .get`，关闭时整段不生成）：

```scala
// bpu/ubtb/MicroBtb.scala
if (HasBpuFlush) { 
	replacer.io.contextFlush.get := io.contextFlush.get 
}
```

replacer 不必接入 `bpuFlushing`，T+1 之后的污染防御在 `MicroBtb` 顶层用 `bpuFlushing` 门控 `predTouch`/`trainTouch` 的 `valid`（§4.2.3）。

#### 4.2.2 存储结构刷新

uBTB 的 PLRU 状态寄存器（`state_reg`）是 `PseudoLRU` 类的 `protected` 成员，`MicroBtbReplacer` 无法直接访问并清零，唯一的写入接口是 `access()` 方法。

`contextFlush` 通过 `access()` 互斥调用完成清零：按需访问 W1、W3、W5...，利用 PLRU 树的更新特性一次性将所有 bit 归零（==证明详见==[[PLRU刷新方案证明]]）。

`MicroBtbReplacer` 同样在模块体顶部解包自己的中间变量（00 §3.1 / 03 同款模板；`access()` 是对 `state_reg` 的寄存器连接，须 `when/otherwise` 保证互斥，`flushTouches` 组合链放入 `if (HasBpuFlush)` 实现结构性裁剪）：

```scala
// bpu/ubtb/MicroBtbReplacer.scala -- 模块体顶部
private val contextFlush = if (HasBpuFlush) io.contextFlush.get else false.B

// touch Plru
if (HasBpuFlush) {
	// flushTouches 为 16 级组合链（重型结构）：HasBpuFlush=false 时不参与 elaboration，结构性消失
	val flushTouches = (0 until NumEntries / 2).map { i =>
		val v = Wire(Valid(UInt(log2Up(NumEntries).W)))   // 注意参数类型和位宽
		v.valid := contextFlush
		v.bits  := (2 * i + 1).U
		v
	}

	when(contextFlush) {
		replacer.access(flushTouches)
	}.otherwise {
		replacer.access(Seq(io.predTouch, io.trainTouch))
	}
} else {
	replacer.access(Seq(io.predTouch, io.trainTouch))
}
```

> **时序提示（待实验验证）**：`access(flushTouches)` 经 `foldLeft` 将 16 个 touch（W1, W3, …, W31）串成组合链依次计算 `get_next_state`，链长达 16 级；正常路径 `access(Seq(predTouch, trainTouch))` 仅 2 级。该深链仅在 `contextFlush` 当拍出现（低频），不影响日常预测/训练路径，但是否影响时序收敛需待后续综合/时序报告反馈；若违例可考虑分拍清零等优化。

#### 4.2.3 `bpuFlushing` 期间的污染

replacer 有两个写来源：`predTouch`（预测）与 `trainTouch`（训练）。

- **T 拍**：通过优先级限制 `predTouch`/`trainTouch` 对 `MicroBtbReplacer` 的更新，不存在污染问题（§4.2.2 中实现）。T 拍生成的 `predTouch`/`trainTouch` 将在 §4.3.1、§4.4.1 中被清理。
- **T+1 ~ 聚合 `resetDone` 置位**：在 `MicroBtb` 顶层用 `bpuFlushing` 门控 `predTouch`/`trainTouch` 的 `valid`，使刷新窗口期内 replacer 不发生任何写：

```scala
// bpu/ubtb/MicroBtb.scala（引用 §4.1.1 中间变量；门控项以 Scala if 表达式内联，关闭时折叠为常量 true.B）
replacer.io.predTouch.valid  := s1_hit && s1_fire && (if (HasBpuFlush) !bpuFlushing else true.B)
replacer.io.predTouch.bits   := s1_hitIdx

replacer.io.trainTouch.valid := t1_fire && (if (HasBpuFlush) !bpuFlushing else true.B)
replacer.io.trainTouch.bits  := t1_updateIdx
```

---

### 4.3 如何防止预测路径污染 entry 和 replacer

预测路径不写 entry，对存储结构的唯一写来源是 `predTouch`，**防止其污染的主要措施是 §4.2.3 的 `!bpuFlushing` 门控**。

§4.3.1 清空 `s1_hitOH` 的作用是消除预测流水线中残留的旧上下文信息；§4.3.2 论证清零后 entries 不会被读出旧上下文的分支历史信息，因此无需增加请求门控。

#### 4.3.1 清空预测流水线残留数据

清空 `s1_hitOH` 的价值在于消除 **T+1 拍的残留命中**：`s1_hitOH` 在 T 拍经 `RegEnable(s0_hitOH, s0_fire)` 加载的是对**清零前** entries 的比较结果，T+1 拍仍为旧上下文独热码，而 `s1_hit = s1_hitOH.orR` 直接驱动 `io.prediction.valid`。因此只需清空 `s1_hitOH`，即可在 T+1 拍同时消除假预测输出与 `predTouch` 隐患。

```scala
// 保持原声明不变（RegEnable 展开为 when(s0_fire) 的默认连接）
private val s1_hitOH = RegEnable(s0_hitOH, s0_fire)
// 在声明之后追加：后连接覆盖默认连接，contextFlush 当拍清零、T+1 拍生效
//（引用 §4.1.1 中间变量；整段放入 if (HasBpuFlush)，关闭时结构性消失）
if (HasBpuFlush) {
	when(contextFlush) {
		s1_hitOH := 0.U(NumEntries.W)
	}
}
```

**T+1 拍之后**：清零仅在 T 拍生效，T+1 拍起 `s1_hitOH` 会通过默认连接 `when(s0_fire)` 从 `s0_hitOH` 重新加载。由于 entries 已在 T 拍被 §4.1 清空，且刷新窗口期内训练路径被 §4.4 门控不再写入新 entry，entries 全程为零，故 `s0_hitOH = 0`、`s1_hitOH` 自然保持全 0。

**结论**：清空 `s1_hitOH` 一次即可，后续依赖 entries 清空的连锁效应，无需额外操作。

> 注意：T 拍（`contextFlush` 当拍）`s1_hitOH` 清零尚未生效，`io.prediction` 仍可能输出一拍旧上下文预测；该拍恰为 phase-2 redirect 到达拍，旧预测由既有 redirect 冲刷语义作废（与普通误预测恢复一致），且 `predTouch` 已被 §4.2.3 门控，无需额外处理。

#### 4.3.2 阻止预测流水线读取 entry 中的旧值

uBTB entries 是寄存器数组，1 周期清零。刷新结束后 entries 已全零，因此读 entries 只能得到 `s0_hitOH = 0` → `s1_hit = false` → `predTouch` 自然无效。无需额外措施。

> 本节覆盖的是 **s0 级读取**（自 T+1 拍起比较对象已全零）；T+1 拍的 **s1 级残留命中**（`s1_hitOH` 为 T 拍对旧 entries 的比较结果）不在此连锁效应覆盖范围内，由 §4.3.1 的显式清零消除。

---

### 4.4 如何防止训练路径污染 entry 和 replacer

刷新当拍的训练写已被 §4.1、§4.2 阻断，但仍有两类污染风险：T+1 拍 t1 寄存器残留旧值、T+1 之后旧上下文训练数据持续流入。§4.4.1 清理前者，§4.4.2 阻断后者。

#### 4.4.1 清理训练流水线残留数据

**问题**：T 拍 `contextFlush` 时，t1 仍持有 T-1 拍旧数据。

**方案**：保持 `RegEnable` 声明不变，在 `contextFlush` 当拍用 `when` 覆盖清零（Chisel 后赋值覆盖先赋值）。由于 §4.4.2 已门控 `t0_fire`，刷新窗口内 `RegEnable` 本就不加载，无需 `elsewhen(t0_fire)` 分支。

> 注意：T 拍起 `t1_fire` 恒为 false，写回与 `trainTouch` 实际不会再发生。本节清零属防御性措施：使 t1 级不残留旧上下文数据。
```scala
  // 保持原 RegEnable 声明不变
  private val t1_actualTaken = RegEnable(t0_actualTaken, t0_fire)
  private val t1_position    = RegEnable(t0_position,    t0_fire)
  private val t1_target      = RegEnable(t0_target,      t0_fire)
  private val t1_attribute   = RegEnable(t0_attribute,   t0_fire)
  private val t1_targetCarry = t0_targetCarry.map(w => RegEnable(w, t0_fire)) // if (EnableTargetFix)
  private val t1_hit         = RegEnable(t0_hit,         t0_fire)
  private val t1_hitIdx      = RegEnable(t0_hitIdx,      t0_fire)
  // ... 其余 hit 标志位同理

  // contextFlush 当拍清零（后赋值覆盖 RegEnable；引用 §4.1.1 中间变量，
  // 整段放入 if (HasBpuFlush)，关闭时结构性消失）
  if (HasBpuFlush) { when(contextFlush) {
    t1_tag               := 0.U
    t1_actualTaken       := false.B
    t1_position          := 0.U
    t1_target            := 0.U
    t1_attribute         := 0.U.asTypeOf(t1_attribute)
    t1_targetCarry.foreach(_ := 0.U.asTypeOf(t1_targetCarry.get))
    t1_hit               := false.B
    t1_hitIdx            := 0.U
    t1_hitEntry          := 0.U.asTypeOf(t1_hitEntry)
    t1_hitNotUseful      := false.B
    t1_hitPositionSame   := false.B
    t1_hitAttributeSame  := false.B
    t1_hitTargetSame     := false.B
  } }
```

- 清空后 T+1 拍 `t1_hit = false`、`t1_allocate = false`，entries 写回条件不满足。
- T+1 ~ 聚合 `resetDone` 置位期间，t1 各级保持无旧数据由 §4.4.2 保证（`t0_fire` 恒为 false）：寄存器直连信号保持清零值；`t1_tag`/`t1_hitEntry` 为 Wire（因被 t0 级前瞻引用而先声明、由匿名 `RegEnable` 驱动），`:= 0.U` 仅在当拍掩盖 Wire 取值，底层匿名寄存器未被清零、T+1 拍起恢复旧值，但有效 `t1_fire` 在整个窗口内恒为 false，旧值无任何可观测消费者。
- T 拍有效 `t1_fire` 由 `!bpuFlushing` 组合拉低，其内部 `RegNext(t0_fire)` 则从 T+1 拍起随门控后的 `t0_fire` 自然归零，无需显式清零 fire 寄存器。
- 窗口结束后首个新训练：底层匿名寄存器在 `t0_fire` 拍结束的时钟沿加载新值，与 `t1_fire` 置位同沿，故 `t1_fire = 1` 的拍读到的一定是新数据，旧值同样不可见。

#### 4.4.2 阻止训练数据进入训练流水线

为 `t0_fire` 增加门控，使训练数据在 BPU 整体刷新窗口内无法进入 t1 阶段。利用 `io.bpuFlushing` 以阻断 T+1 之后到达的训练流入：

```scala
// bpu/ubtb/MicroBtb.scala（引用 §4.1.1 中间变量；门控项以 Scala if 表达式内联，关闭时折叠为常量 true.B）
if (UseFastTrain) {
  t0_fire := io.fastTrain.get.valid && io.enable && (if (HasBpuFlush) !bpuFlushing else true.B)
  // ... 其余 t0 信号赋值不变
} else {
  t0_fire := io.stageCtrl.t0_fire && io.train.mispredictBranch.valid && io.enable && (if (HasBpuFlush) !bpuFlushing else true.B)
  // ... 其余 t0 信号赋值不变
}
```

> [!important]**全局统一设计模式说明**
> 现有 uBTB 已通过 `contextFlush` 高优先级阻断 entry 写回、`!bpuFlushing` 门控 `trainTouch`，并在 T0 阻断新训练流入，能够完整避免刷新期间的功能状态污染。
> 在此基础上，为统一 BPU 子预测器的训练流水 kill 语义，进一步规定 `bpuFlushing` 期间所有训练级有效 fire 均为 false，因此还需门控 `t1_fire`：
>
> ```scala
> t1_fire := RegNext(t0_fire, false.B) && (if (HasBpuFlush) !bpuFlushing else true.B)
> ```
>
> `RegNext` 无需单独命名或显式清零；`t0_fire=0` 会使其在下一拍自然归零。现有 entry、`trainTouch` 和 T1 payload 的刷新保护保持不变。

---

### 4.5 刷新完成信号 `resetDone`

uBTB 的所有存储均为寄存器，`contextFlush` 当拍触发的清零在下一拍全部生效，不存在类似 aBTB EntrySRAM 的多拍清零窗口。各存储结构清零窗口一致（均 1 cycle），因此 uBTB 无需内部刷新完成聚合逻辑，按 01 §5.1.3、§5.3.4 对寄存器型预测器的接口契约（收到有效 `contextFlush` 当拍拉低，本地清零完成后置位并保持至下一次有效 `contextFlush`），**完成信号直接由 `contextFlush` 的否定给出**：

```scala
// MicroBtb.scala：替换 01 §6.1 的恒真占位（输出端口驱动仍需 if：关闭时端口为 None，
// 00 §3.1；条件引用 §4.1.1 中间变量）
if (HasBpuFlush) {
  io.resetDone.get := !contextFlush
}
```

- T 拍（有效 `contextFlush`）为低，T+1 拍起置位并保持，满足 01 §2.3.2 的完成电平契约；顶层聚合 `resetDone` 由 `activeFlushMask` 选中的预测器与归约得到（01 §5.3.4）。
- uBTB 未被本事务 `activeFlushMask` 选中时，收到的 `contextFlush` 恒为 0，`resetDone` 恒为 1，不阻塞顶层完成聚合（01 §5.3.4）。
- 该信号仅反映本预测器自身清零完成，可以早于 `bpuFlushing` 窗口结束；训练阻塞由 `bpuFlushing` 负责，二者职责解耦（01 §5.4）。

> `io.sramResetDone` 的赋值保持不变，与上下文刷新完成信号 `resetDone` 相互独立。

---

### 4.6 编译开关裁剪清单

按 00 §3.4 的要求，`HasBpuFlush=false` 时以下 uBTB 刷新专用结构必须从生成 RTL 中消失：完整 Scala `if` 分支在 elaboration 期不生成；内联 Scala `if` 在 elaboration 期选择 `true.B` 恒等项，随后由 Chisel/CIRCT 编译期常量折叠消除新增门控。验收标准见 00 §5.2 双配置 RTL 对比。

| 类别          | 应消失的对象                                                                                                        | 对应小节         |
| ------------- | ------------------------------------------------------------------------------------------------------------------- | ---------------- |
| 端口          | `BasePredictorIO` 的 `contextFlush`/`bpuFlushing`/`resetDone`；`MicroBtbReplacerIO` 的 `contextFlush`   | §4.1.1、§4.2.1 |
| 中间变量定义  | `contextFlush`/`bpuFlushing` 两行中间变量与 `MicroBtbReplacer` 的 `contextFlush` 中间变量（仅为类型占位，无硬件代价） | §4.1.1、§4.2.2 |
| entries 清零  | `when(contextFlush)` 整表清零的 `if (HasBpuFlush)` 分支（结构性消失，else 分支保留原始写回）                      | §4.1.2          |
| replacer 清零 | `flushTouches` 16 项组合链与 `access(flushTouches)` 的 `if (HasBpuFlush)` 分支（结构性消失）                      | §4.2.2          |
| 预测残留清理  | `s1_hitOH` 清零的 `if (HasBpuFlush) { when(contextFlush) }` 块（结构性消失）                                        | §4.3.1          |
| 训练残留清理  | t1 寄存器组清零的 `if (HasBpuFlush) { when(contextFlush) }` 块（结构性消失）                                        | §4.4.1          |
| 训练流水门控  | `t0_fire` 和有效 `t1_fire` 中的内联 `if` 门控项（elaboration 期选择 `true.B`，经 Chisel/CIRCT 常量折叠后消失）        | §4.4.2          |
| touch 门控    | `predTouch`/`trainTouch` valid 中的内联 `if` 门控项（elaboration 期选择 `true.B`，经 Chisel/CIRCT 常量折叠后消失）    | §4.2.3          |
| 完成信号      | `io.resetDone.get := !contextFlush` 赋值（结构性消失）                                                              | §4.5            |

关闭后的数据通路必须与未引入刷新机制的原始 uBTB 完全一致：entries 正常写回、`replacer.access(Seq(io.predTouch, io.trainTouch))`、`t0_fire`/`t1_fire`、`s0_fire`/`s1_fire` 均恢复原式，不保留额外门控、延迟或默认值（00 §3.1）。
