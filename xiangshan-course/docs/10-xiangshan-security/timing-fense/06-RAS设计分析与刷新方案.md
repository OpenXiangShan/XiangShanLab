# 06-RAS设计分析与刷新方案

> **本文的刷新编译开关约定**
>
> 1. **接口契约**：`contextFlush`/`bpuFlushing`/`resetDone` 由 `BasePredictorIO` 以 `Option.when(HasBpuFlush)` 声明（01 §5.1.3），BPU 顶层已按本事务 `activeFlushMask` 门控后下发（01 §5.3.3）。RAS 对应 `rasFlushEnable`，因此收到的均为针对自身的有效刷新信号。
> 2. **编译开关**：本文所有 RasStack 刷新端口与连线、寄存器清零块、延迟写入阻断、spec/commit/redirect 门控、预测输出置零及 `resetDone` 生成，均遵循 [00-BPU刷新机制编译开关实现方案](00-BPU刷新机制编译开关实现方案.md) §3.1/§3.4 和 01 §6.2，受 elaboration-time `HasBpuFlush` 统一裁剪。
> 3. **编码规则**：可选端口使用 `Option.when(HasBpuFlush)`；只读的可选刷新 I/O 在模块顶部通过 `if (HasBpuFlush) io.<port>.get else false.B` 解包为内部别名，模块内不再散布 `.get`；只有存在默认赋值和条件覆盖的派生门控信号才使用 `Wire`（与 `sx_fire`/`sx_flush` 范式一致）。刷新专用连线、清零 `when` 和完成逻辑放入 Scala `if (HasBpuFlush)`；对既有数据通路追加门控时，关闭分支必须恢复刷新引入前的原表达式。禁止用 `getOrElse(false.B)`/`getOrElse(true.B)` 代替刷新专用结构的 Scala 守卫。
> 4. **关闭语义**：`HasBpuFlush=false` 时，RAS 刷新专用端口和消费逻辑不参与 elaboration，`Ras`/`RasStack` 原有预测、spec、commit 与 redirect 数据通路必须与未引入刷新机制的基线一致；完整裁剪清单见 §4.5。

## 1. RAS 的预测流水线

RAS（Return Address Stack）是 BPU 顶层 S3 级的返回地址预测器：`call` 使 RAS 压入返回地址，`ret` 才使用 RAS 的栈顶作为跳转预测目标，call 自身的跳转目标由 BTB 等其他预测器提供。当前 S3 拍直接使用已寄存在 `timingTop` 中的当前栈顶；同拍的 `io.specIn` 参与计算并寄存更新后的栈顶，供后续拍的预测使用。

**输入与判定**：`io.specIn.valid` 即 `s3_fire`，其 `attribute` 字段标识当前指令是 `isCall` 还是 `isReturn`：

```scala
// Ras.scala:69
// io.specIn.valid = s3_fire
private val stackNearOverflow = stack.specNearOverflow
private val specPush          = io.specIn.valid && io.specIn.bits.attribute.isCall
private val specPop           = io.specIn.valid && io.specIn.bits.attribute.isReturn
```

**栈顶动作**：`specPush`（call 且未 nearOverflow）将返回地址压入推测队列；`specPop`（ret 且未 nearOverflow）弹出当前栈顶并更新后续状态。当前 ret 的预测目标是执行 pop 前已由 `timingTop` 保存的栈顶：

```scala
// Ras.scala:76-80
stack.spec.pushValid := specPush && !stackNearOverflow
stack.spec.popValid  := specPop && !stackNearOverflow
stack.spec.pushAddr := PrunedAddrInit(specPushAddr)
stack.spec.fire     := io.specIn.valid
```

**预测结果输出**：RAS 的预测目标直接由 `timingTop.retAddr` 寄存器驱动：

```scala
// Ras.scala:96
io.topRetAddr   := stack.spec.popAddr

// RasStack.scala:323
io.spec.popAddr := timingTop.retAddr
```

`timingTop` 是一个 `RegInit` 寄存器，其当前值保存本拍可使用的栈顶返回地址。之所以用寄存器而非组合直读，是因为 `ret` 的预测目标是 pop 前的当前栈顶，而 `specPop` 又会修改 `tosr` 等指针，若直接组合读栈顶会形成"读栈顶->修改指针->读栈顶"的组合环。因此 RAS 根据当拍的 push/pop/redirect 等事件预先计算更新后的栈顶并写入 `timingTop`，供后续拍使用，从而打断组合环。

除预测目标外，RAS 还向 FTQ 输出两份元数据，用于后续提交与误预测恢复：

- `io.redirectMeta`：携带 `ssp/sctr/tosw/tosr/nos/topRetAddr`。其中 `ssp/sctr/tosw/tosr/nos` 供 RAS 在误预测时恢复推测状态，`topRetAddr` 由 FTQ 用于 IFU redirect 的返回目标；
- `io.commitMeta`：携带 `ssp/tosw`，供指令提交时定位 commit 栈与推测队列位置。

---

## 2. RAS 的状态更新路径

RAS 没有传统意义上的"训练流水线"，而是由 **spec（推测）、commit（提交）、redirect（恢复）** 三条状态更新路径共同维护双栈状态。三条路径的触发源、延迟与作用对象如下表：

| 路径     | 触发源                               | 延迟                                   | 主要作用对象                                  |
| :------- | :----------------------------------- | :------------------------------------- | :-------------------------------------------- |
| spec     | `io.specIn.valid`（= `s3_fire`） | 0 拍更新指针 + 1 拍`realPush` 写队列 | `tosr/tosw/ssp/sctr`、`specQueue/specNos` |
| commit   | `io.commit.valid`（来自 FTQ）      | 1 拍（`RegNext`）                    | `commitStack/nsp/bos`                       |
| redirect | `io.redirect.valid`（来自 FTQ）    | 1 拍（`RegNextWithEnable`）          | `tosr/tosw/ssp/sctr`、`specQueue/specNos` |

### 2.1 spec 路径（推测 push/pop）

spec 路径由 BPU 顶层 S3 预测结果驱动，是 RAS 状态更新最频繁的来源。

- **spec push**：当 `io.specIn.bits.attribute.isCall` 且未 nearOverflow 时，调用 `specPush()` 更新 `tosr/tosw/ssp/sctr`。若新地址与当前栈顶相同且 `sctr` 未溢出，仅递增 `sctr`（合并相同地址的连续 call，避免重复压栈）；否则递增 `ssp` 并将 `sctr` 清零。

```scala
// RasStack.scala:132-149
def specPush(retAddr, currSsp, currSctr, currTosr, currTosw, topEntry): Unit = {
  tosr := currTosw
  tosw := specPtrInc(currTosw)
  when(topEntry.retAddr === retAddr && currSctr < StackCounterMax.U) {
    sctr := currSctr + 1.U
  }.otherwise {
    ssp  := ptrInc(currSsp)
    sctr := 0.U
  }
}
```

- **spec pop**：当 `io.specIn.bits.attribute.isReturn` 且未 nearOverflow 时，调用 `specPop()` 更新 `tosr/ssp/sctr`。
- **关键延迟**：`specQueue/specNos` 的实际写入并非当拍完成，而是通过 `realPush`（`RegNext` 延迟 1 拍）在下一拍写入。指针更新（`tosr/tosw/ssp/sctr`）当拍生效，但数据写入延迟 1 拍，二者之间靠 `writeBypass` 旁路弥合。

```scala
// RasStack.scala:305-313
realPush := RegNext(io.spec.pushValid, init = false.B) || RegNext(
  io.redirect.valid && io.redirect.isCall,
  init = false.B
)

when(realPush) {
  specQueue(realWriteAddr.value) := realWriteEntry
  specNos(realWriteAddr.value)   := realNos
}
```

### 2.2 commit 路径（提交 push/pop）

commit 路径维护 RAS 的"权威状态"`commitStack` 与 `nsp`，是推测结果落地的最终归属。

- **触发延迟**：`io.commit.valid` 来自 FTQ，在 RAS 顶层经 `RegNext` 延迟 1 拍变为 `commitValid`。

```scala
// Ras.scala:111-119
private val commitValid    = RegNext(io.commit.valid, init = false.B)
private val commitInfo     = RegEnable(io.commit.bits, io.commit.valid)
stack.commit.valid     := commitValid
stack.commit.pushValid := commitValid && commitInfo.attribute.isCall
stack.commit.popValid  := commitValid && commitInfo.attribute.isReturn
```

- **commit push**：当提交 call 时，更新 `commitStack` 与 `nsp`。若栈顶地址与提交地址相同且 `ctr` 未满，仅递增 `ctr`；否则递增 `nsp` 并写入新地址。
- **commit pop**：当提交 ret 时，若 `ctr > 0` 则递减 `ctr`，否则递减 `nsp`。
- **维护 bos**：commit 路径还更新 `bos`（bottom-of-spec），用于推断推测队列是否接近溢出。

### 2.3 redirect 路径（误预测恢复）

redirect 路径在误预测发生时，用 redirect 携带的旧上下文 RAS 快照恢复推测状态。

- **触发延迟**：`io.redirect.valid` 来自 FTQ，经 `RegNextWithEnable` 延迟 1 拍。恢复时从 `redirect.bits.meta.ras` 读取旧 `tosr/tosw/ssp/sctr`。

```scala
// RasStack.scala:387-412
when(io.redirect.valid) {
  tosr := io.redirect.meta.tosr
  tosw := io.redirect.meta.tosw
  ssp  := io.redirect.meta.ssp
  sctr := io.redirect.meta.sctr

  when(io.redirect.isCall) {
    specPush(io.redirect.callAddr, io.redirect.meta.ssp, ...)
  }
  when(io.redirect.isRet) {
    specPop(io.redirect.meta.ssp, io.redirect.meta.sctr, ...)
  }
}
```

- **重做语义**：若 redirect 自身是误预测的 call，则额外执行一次 `specPush`（用 `redirect.callAddr`）；若是误预测的 ret，则额外执行一次 `specPop`。redirect 携带的 meta 是**旧上下文的 RAS 状态快照**，这一点对刷新方案至关重要。

---

## 3. RAS 的整体架构

RAS 采用**提交栈 + 推测队列**双栈架构（基于 Persistent Stack 论文），而非传统单栈。`commitStack` 保存已提交的权威状态，`specQueue` 保存推测路径上 in-flight 的 push 操作用于快速恢复，`specNos` 保存推测队列每项的"下一栈指针"用于 pop 回溯。全部存储均为寄存器实现（无 SRAM）。下表容量、位宽和具体初值按默认参数 `CommitStackSize=16`、`SpecQueueSize=32`、`StackCounterWidth=3` 展开；其他配置会随 `RasParameters` 变化。

存储结构总表如下：

| 存储结构               | 实现方式                                  | 容量                | 上电初始值                       | 写入来源                                                                             |
| :--------------------- | :---------------------------------------- | :------------------ | :------------------------------- | :----------------------------------------------------------------------------------- |
| `commitStack`        | `RegInit(Vec)`                          | 16 项`RasEntry`   | `retAddr=0, ctr=0`             | commit 路径                                                                          |
| `specQueue`          | `RegInit(Vec)`                          | 32 项`RasEntry`   | `retAddr=0, ctr=0`             | `realPush`（spec/redirect 延迟）                                                   |
| `specNos`            | `RegInit(Vec)`                          | 32 项`RasPtr`     | `flag=false, value=0`          | `realPush`                                                                         |
| `nsp`                | `RegInit(UInt)`                         | 4 bit               | 0                                | commit 路径                                                                          |
| `ssp`                | `RegInit(UInt)`                         | 4 bit               | 0                                | spec/redirect 路径                                                                   |
| `sctr`               | `RegInit(UInt)`                         | 3 bit               | 0                                | spec/redirect 路径                                                                   |
| `tosr`               | `RegInit(RasPtr)`                       | 6 bit               | `flag=true, value=31`          | spec/redirect 路径                                                                   |
| `tosw`               | `RegInit(RasPtr)`                       | 6 bit               | `flag=false, value=0`          | spec/redirect 路径                                                                   |
| `bos`                | `RegInit(RasPtr)`                       | 6 bit               | `flag=false, value=0`          | commit 路径                                                                          |
| `specNearOverflowed` | `RegInit(Bool)`                         | 1 bit               | `false`                        | 每拍寄存`distanceBetween` 组合判断的结果                                           |
| `writeBypassEntry`   | `Reg(RasEntry)`                         | 1 项                | 未定义（X 态）                   | spec/redirect push                                                                   |
| `writeBypassNos`     | `Reg(RasPtr)`                           | 1 项                | 未定义（X 态）                   | spec/redirect push                                                                   |
| `writeBypassValid`   | `RegInit(Bool)`                         | 1 bit               | `false`                        | spec/redirect/fire                                                                   |
| `timingTop`          | `RegInit(RasEntry)`                     | 1 项                | 0                                | when 链（综合各路径）                                                                |
| `timingNos`          | `RegInit(RasPtr)`                       | 1 项                | 0                                | when 链（push 分支）                                                                 |
| `realPush`           | `Wire(Bool())`，值来自 `RegNext` 组合 | 1 bit               | `false`（`RegNext` 的 init） | `realPush := RegNext(spec.pushValid) \|\| RegNext(redirect.valid && isCall)`         |
| `realWriteEntry`     | `Wire(RasEntry)`，值来自 `RegEnable`  | 1 项                | 未定义（X 态）                   | `RegEnable(writeEntry, spec.fire \|\| redirect.isCall)`                              |
| `realWriteAddr`      | `RegEnable`                             | 1 项                | 未定义（X 态）                   | `RegEnable(Mux(redirect.valid && redirect.isCall, redirect.meta.tosw, tosw), ...)` |
| `realNos`            | `RegEnable`                             | 1 项                | 未定义（X 态）                   | `RegEnable(Mux(redirect.valid && redirect.isCall, redirect.meta.tosr, tosr), ...)` |
| `redirect`           | `RegNextWithEnable(io.redirect)`        | 1 项`BpuRedirect` | `valid=false`，`bits` 未定义 | `valid` 每拍延迟，`bits` 仅在 FTQ redirect 有效时采样                            |
| `commitValid`        | `RegNext(Bool)`                         | 1 bit               | `false`                        | `RegNext(io.commit.valid)`                                                         |
| `commitInfo`         | `RegEnable(BpuCommit)`                  | 1 项`BpuCommit`   | 未定义（X 态）                   | `RegEnable(io.commit.bits, io.commit.valid)`                                       |

默认参数定义为：`CommitStackSize=16`、`SpecQueueSize=32`、`StackCounterWidth=3`（`StackCounterMax=7`）；具体配置可以覆盖这些值。

下文按"栈数据与指针 / 写相关 / 读相关 / 流水线暂存"四类功能归类，与 §4 刷新方案一一对应。

### 3.1 栈数据与指针

本节汇总 RAS 的核心栈数据（`commitStack`/`specQueue`/`specNos`）与全部指针计数器（`nsp/ssp/sctr/tosr/tosw/bos/specNearOverflowed`），它们构成双栈架构的存储与控制核心。

#### commitStack

`commitStack` 默认是 16 项的 `RasEntry` 寄存器数组，实际项数由 `CommitStackSize` 决定。它保存已提交的返回地址栈，是 RAS 的权威状态。每个 `RasEntry` 含 `retAddr`（`PrunedAddr(VAddrBits)`）与 `ctr`（`UInt(StackCounterWidth.W)`，默认 3 bit，合并相同地址的连续 call）。

```scala
// RasStack.scala:67
private val commitStack = RegInit(VecInit(Seq.fill(CommitStackSize)(RasEntry(PrunedAddrInit(0.U(VAddrBits.W)), 0.U))))

// Bundles.scala:27-30
class RasEntry(implicit p: Parameters) extends RasBundle {
  val retAddr: PrunedAddr = PrunedAddr(VAddrBits)
  val ctr:     UInt       = UInt(StackCounterWidth.W)
}
```

**读写端口**

- **读**：`getCommitTop(currSsp)` 按 `ssp` 索引读栈顶，供 `getTop` 在推测队列为空时回退读取。
- **写**：仅 commit 路径写入。commit push 命中相同地址时原地递增 `ctr`，否则在 `ptrInc(nsp)` 处写入新地址；commit pop 命中时原地递减 `ctr`。由于是寄存器数组，读写同周期无端口冲突。

**结论：必须刷新**（否则新上下文首次 ret 读到旧残留返回地址；commit 路径会错误递增旧 `ctr` 使脏数据"复活"并长期传播）。

#### specQueue

`specQueue` 是 `SpecQueueSize` 项（默认 32 项）的 `RasEntry` 寄存器数组，保存推测路径上 in-flight 的 push 操作，用于 `ret` 时快速恢复返回地址。

```scala
// RasStack.scala:68
private val specQueue = RegInit(VecInit(Seq.fill(SpecQueueSize)(RasEntry(PrunedAddrInit(0.U(VAddrBits.W)), 0.U))))
```

**读写端口**

- **读**：`getTop` 在 `tosrInRange(currTosr, currTosw)` 成立时按 `tosr.value` 读栈顶；`specPop` 在回溯时按 `currTopNos.value` 读 `ctr`。
- **写**：仅 `realPush` 写入，写入地址为 `realWriteAddr.value`。由于 `realPush` 是 `RegNext` 延迟 1 拍，写操作在 push 请求的下一拍才生效，期间靠 `writeBypass` 旁路弥合 RAW 冒险。

**结论：必须刷新**（`ret` 预测目标的主要来源；`realPush` 延迟 1 拍，刷新当拍的残留写入会落在清零之后，且 `specPop` 回溯时会读旧残留 `ctr` 污染 `sctr`）。

#### specNos

`specNos` 是 `SpecQueueSize` 项（默认 32 项）的 `RasPtr` 寄存器数组，保存 `specQueue` 中每项的"下一栈指针"（Next-of-Stack），供 `specPop` 回溯 `tosr`。`RasPtr` 是循环队列指针，含 `flag` 与 `value` 两字段，总位宽为 `1 + log2Up(SpecQueueSize)`。

```scala
// RasStack.scala:69
private val specNos = RegInit(VecInit(Seq.fill(SpecQueueSize)(RasPtr(false.B, 0.U))))

// Bundles.scala:41-43
class RasPtr(implicit p: Parameters) extends CircularQueuePtr[RasPtr](p =>
  p(XSCoreParamsKey).frontendParameters.bpuParameters.rasParameters.SpecQueueSize
) {}
```

**读写端口**

- **读**：`getTopNos` 按 `tosr.value` 读 `specNos`，输出当前栈顶的 nos。`specPop` 用 `currTopNos` 更新 `tosr` 并回溯 `ctr`。
- **写**：与 `specQueue` 同步，由 `realPush` 写入。

**结论：必须刷新，且必须与 `specQueue` 同步清零**（`specPop` 用 `specNos` 更新 `tosr`，旧值会引导 `tosr` 指向旧残留项，连续读出旧返回地址）。

#### 指针与计数器

RAS 的指针与计数器全部为 `RegInit` 寄存器，是双栈架构的控制核心：

```scala
// RasStack.scala:71-79
private val nsp = RegInit(0.U(log2Up(CommitStackSize).W))   // commit 栈指针
private val ssp = RegInit(0.U(log2Up(CommitStackSize).W))   // spec 栈指针

private val tosr = RegInit(RasPtr(true.B, (SpecQueueSize - 1).U)) // top-of-spec-read
private val tosw = RegInit(RasPtr(false.B, 0.U))            // top-of-spec-write
private val bos  = RegInit(RasPtr(false.B, 0.U))            // bottom-of-spec
private val specNearOverflowed = RegInit(false.B)           // 推测队列接近溢出标志

private val sctr = RegInit(0.U(StackCounterWidth.W))        // 栈计数器
```

各寄存器含义：

| 寄存器                 | 初始值                                          | 含义                                              | 维护路径                         |
| :--------------------- | :---------------------------------------------- | :------------------------------------------------ | :------------------------------- |
| `nsp`                | 0                                               | commit 栈指针，指向`commitStack` 栈顶           | commit                           |
| `ssp`                | 0                                               | spec 栈指针，与`commitStack` 索引对齐           | spec/redirect                    |
| `sctr`               | 0                                               | 栈计数器，合并相同地址的连续 call                 | spec/redirect                    |
| `tosr`               | `flag=true, value=SpecQueueSize-1`（默认 31） | 推测读指针，初始在`tosw` 之前表示 spec 队列为空 | spec/redirect                    |
| `tosw`               | `flag=false, value=0`                         | 推测写指针                                        | spec/redirect                    |
| `bos`                | `flag=false, value=0`                         | 推测队列底部，用于溢出检测                        | commit                           |
| `specNearOverflowed` | `false`                                       | 推测队列接近溢出标志                              | 寄存更新，下一状态由组合比较产生 |

**读写端口**

- `tosr/tosw` 经 `specPtrInc`/`specPtrDec`增减，`ssp/nsp` 经 `ptrInc`/`ptrDec`增减。
- `specNearOverflowed` 是寄存器；每拍把组合条件 `distanceBetween(tosw, bos) > (SpecQueueSize - 2).U` 的判断结果写入该寄存器，因此它是 `tosw/bos` 状态的寄存后结果，而非纯组合输出。

**结论：必须全部刷新，且必须恢复到与上电一致的初始值**（尤其 `tosr` 必须为 `RasPtr(true.B, (SpecQueueSize - 1).U)` 以表达"spec 队列为空"；否则新上下文首次预测即读旧指针指向的旧项，`specNearOverflowed` 残留为真会抑制 push/pop 导致 RAS"哑火"）。

### 3.2 状态更新通路

本节汇总与"写入通道"相关的寄存器与线网：`writeBypass` 旁路、`realPush`/`realWriteEntry`/`realWriteAddr`/`realNos` 延迟写入通道、`redirect` 重定向寄存器、`commitValid`/`commitInfo` 提交寄存器。它们共同决定 spec/commit/redirect 三条路径如何写入栈数据与指针。

#### writeBypass 寄存器

`writeBypass` 用于解决 `realPush` 延迟写入造成的 RAW 冒险：push 当拍 `tosw` 已推进但数据要下一拍才入队，期间 `getTop` 需旁路读取刚 push 的 entry。

```scala
// RasStack.scala:81-85
private val writeBypassEntry = Reg(new RasEntry)        // 注意：Reg 非 RegInit，上电为 X 态
private val writeBypassNos   = Reg(new RasPtr)          // 同上
private val writeBypassValid     = RegInit(0.B)         // 旁路有效标志
private val writeBypassValidWire = Wire(Bool())         // 当拍组合旁路标志
```

**读写端口**

- **写**：`writeBypassValid` 由 spec/redirect/fire 的优先级链驱动；`writeBypassEntry/writeBypassNos` 在 `io.spec.pushValid || (io.redirect.valid && io.redirect.isCall)` 时写入。
- **读**：`getTop`/`getTopNos` 在 `allowBypass=true` 且 `writeBypassValid` 时优先读旁路。

**结论：必须刷新**（`writeBypassValid` 必须清为 `false`，`writeBypassEntry/Nos` 一并清零以消除 X 态残留；否则新上下文首次预测会读到旧旁路 entry 的返回地址）。

#### realPush / realWriteEntry / realWriteAddr / realNos

`realPush` 是 `Wire(Bool())`，是 `specQueue/specNos` 的实际写入闸门：

```scala
// RasStack.scala:218
private val realPush       = Wire(Bool())
```

其值由 `RegNext` 组合驱动：

```scala
// RasStack.scala:305-308
realPush := RegNext(io.spec.pushValid, init = false.B) || RegNext(
  io.redirect.valid && io.redirect.isCall,
  init = false.B
)
```

`realWriteEntry` 也是 `Wire(new RasEntry)`：

```scala
// RasStack.scala:219
private val realWriteEntry = Wire(new RasEntry)
```

被赋值为 `RegEnable` 的输出：

```scala
// RasStack.scala:293
realWriteEntry := RegEnable(writeEntry, io.spec.fire || io.redirect.isCall)
```

`realWriteAddr`/`realNos` 则是 `RegEnable` 返回的寄存器本身：

```scala
// RasStack.scala:295-302
private val realWriteAddr = RegEnable(
  Mux(io.redirect.valid && io.redirect.isCall, io.redirect.meta.tosw, tosw),
  io.spec.fire || (io.redirect.valid && io.redirect.isCall)
)

private val realNos = RegEnable(
  Mux(io.redirect.valid && io.redirect.isCall, io.redirect.meta.tosr, tosr),
  io.spec.fire || (io.redirect.valid && io.redirect.isCall)
)
```

三者的消费侧是 `when(realPush)` 块：

```scala
// RasStack.scala:310-312
when(realPush) {
  specQueue(realWriteAddr.value) := realWriteEntry
  specNos(realWriteAddr.value)   := realNos
```

**结论：`realPush` 必须在 contextFlush 末尾覆盖为 `false.B`**；`realWriteEntry/Addr/Nos` 无需显式清零（只要 `realPush` 被覆盖且其产生端被完整门控，残留数据不被消费）。

#### redirect（Ras.scala 顶层）

`redirect` 是 RAS 顶层（Ras.scala）管理的寄存器，延迟 1 拍缓存 FTQ 发来的重定向请求，供 RasStack 恢复旧状态。

```scala
// Ras.scala:98
private val redirect = RegNextWithEnable(io.redirect)
```

`RegNextWithEnable` 对 `Valid` 的两部分采用不同时序：`valid` 由 `GatedValidRegNext(data.valid, false.B)` 实现，每拍采样输入 `valid`，因此输入无效时下一拍会变为 `false`；`bits` 由 `RegEnable(data.bits, data.valid)` 实现，只在输入 `valid=true` 时采样，否则保持旧值。

**读写端口**

- **读**：`redirect.valid` 经 `isBefore` 判定后驱动 `stack.redirect.valid`（Ras.scala:104），`redirect.bits.meta.ras` 提供 `tosr/tosw/ssp/sctr` 旧快照供 RasStack 恢复，`redirect.bits.attribute` 判定 isCall/isRet 触发额外 push/pop。
- **写**：`redirect.valid` 每拍更新为 `io.redirect.valid` 的延迟值；`redirect.bits` 仅在 `io.redirect.valid` 为真时采样。

**结论：刷新时必须阻断新 redirect 在窗口内被采样，并保证已在途的一拍 `redirect.valid` 不被 RasStack 消费**。但不需要为了防止“旧 valid 在刷新后复活”而额外清零该寄存器，因为 `redirect.valid` 在输入无效时会于下一拍自动变为 `false`；保留旧值的只是不会在 `valid=false` 时被消费的 `redirect.bits`。

#### commitValid / commitInfo（Ras.scala 顶层）

`commitValid` 与 `commitInfo` 是 RAS 顶层管理的两个寄存器，延迟 1 拍缓存 FTQ 发来的提交信息，驱动 RasStack 的 commit 路径。

```scala
// Ras.scala:111-112
private val commitValid = RegNext(io.commit.valid, init = false.B)
private val commitInfo  = RegEnable(io.commit.bits, io.commit.valid)
```

- `commitValid`：`RegNext`，输入为假时次拍输出 `false`（init 值），**不保持真值**。
- `commitInfo`：`RegEnable`，使能信号为 `io.commit.valid`（**当前未门控**），使能为真时采样 `io.commit.bits`，为假时保持上一拍值。

**读写端口**

- **读**：`commitValid` 驱动 `stack.commit.valid`（Ras.scala:114）；`commitInfo.attribute` 判定 isCall/isRet 驱动 `stack.commit.pushValid/popValid`（Ras.scala:115-116）；`commitInfo.meta.ras.tosw` 驱动 `stack.commit.metaTosw`（Ras.scala:118），commit push 据此从 `specQueue(io.commit.metaTosw.value)` 读返回地址（RasStack.scala:352）。
- **写**：`commitValid` 由 `RegNext` 更新；`commitInfo` 由 `RegEnable` 在 `io.commit.valid` 为真时更新。

**结论：`commitValid` 必须使用门控后的内部 wire 作为 `RegNext` 输入（见 §4.2）；`commitInfo` 的 `RegEnable` 应复用同一 `commitInValid` wire**，无需 contextFlush 末尾清零（门控使能后 bpuFlushing 期间不采样，且 `commitValid` 门控为假不消费）。

### 3.3 预测输出通路

RAS 的预测目标输出路径为：`timingTop.retAddr`（RasStack 内寄存器）-> `io.spec.popAddr`（RasStack 输出）-> `io.topRetAddr`（RAS 顶层输出）。

```scala
// Ras.scala:96
io.topRetAddr   := stack.spec.popAddr

// RasStack.scala:323
io.spec.popAddr := timingTop.retAddr
```

正常路径下 `io.spec.popAddr` 直接取 `timingTop.retAddr`。为保证 `contextFlush` 当拍（T 拍）即输出 0（而非等寄存器写入沿），输出端需追加一个组合门控 Mux（`contextFlush` 时输出 0），具体代码与说明见 §4.3。该门控只增加一个 2 选 1 Mux，不构成关键路径。

### 3.4 流水线暂存寄存器

`timingTop` 的当前值保存本拍可使用的栈顶返回地址，直接驱动 `io.spec.popAddr` 预测输出；当拍计算的更新值在时钟沿后供后续拍使用。`timingNos` 仅在 `writeBypassValidWire` 分支中随 `timingTop` 写入，当前 Chisel 中没有任何读取者；`io.meta.nos` 实际由 `topNos` 驱动。

```scala
// RasStack.scala:220-221
private val timingTop = RegInit(0.U.asTypeOf(new RasEntry))
private val timingNos = RegInit(0.U.asTypeOf(new RasPtr))
```

**读写端口**

- **写**：`timingTop` 由一个多分支 `when` 链驱动，综合当拍的 spec push/pop、redirect、`realPush`、`writeBypassValidWire` 等所有事件，预先计算更新后的栈顶。其优先级为：writeBypass（push）-> redirect ret -> redirect 其它 -> spec pop -> realPush -> otherwise（稳态读栈顶）。`timingNos` 只在第一个 writeBypass 分支中写入，其他分支保持原值，且当前未被消费。
- **读**：`io.spec.popAddr := timingTop.retAddr`。

**为什么用寄存器而非组合直读**：`ret` 的预测目标是 pop 前的当前栈顶，而 `specPop` 又会修改 `tosr` 等指针，若直接组合读栈顶会形成"读栈顶->修改指针->读栈顶"的组合环。因此 RAS 根据当拍的 push/pop/redirect 等事件预先计算更新后的栈顶并写入 `timingTop`，其当前寄存值则继续供本拍 S3 预测使用，从而打断组合环。

**结论：必须刷新**（`timingTop.retAddr` 即 `io.spec.popAddr` 即 `io.topRetAddr`，是 RAS 唯一的预测目标输出；若不清零，刷新后第一拍预测输出仍是旧上下文的栈顶地址，直接污染新上下文的取指流）。

---

## 4. 刷新方案

RAS 全部存储均为寄存器实现（无 SRAM），因此 `contextFlush` 当拍触发的寄存器清零在下一拍即全部生效，刷新耗时 **1 cycle**。`contextFlush`/`bpuFlushing`/`resetDone` 已由 `BasePredictorIO` 按 `Option.when(HasBpuFlush)` 统一声明，RAS 无需重复新增顶层端口；当前占位代码 `if (HasBpuFlush) { io.resetDone.get := true.B }` 需在真实刷新实现时替换为 §6.2 的完成逻辑。`io.sramResetDone := true.B` 保持不变，与上下文刷新解耦。

RAS 的刷新方案使用**两类信号协同**：

1. **`contextFlush`（1-cycle 脉冲）**：触发寄存器清零。利用 last-connect 语义，在所有 `when`/`MuxCase` 之后追加 `when(contextFlush)` 块，以最高优先级覆盖三条路径的所有寄存器写入。该信号仅在 `s_waiting -> s_flushing` 迁移的那 1 拍拉高。
2. **`bpuFlushing`（刷新窗口信号）**：在 BPU 顶层状态机处于 `s_flushing` 期间持续为真（从 `contextFlush` 拉高当拍起，直到聚合 `resetDone` 置位、状态机迁入 `s_done`）。该信号用于**阻塞刷新窗口内继续到达的旧上下文训练/更新**。由于 `contextFlush` 仅 1 拍，而 `s_flushing` 窗口可能持续多拍（等待 SRAM 型预测器清零完成），窗口内仍可能有旧上下文的 commit、redirect、甚至 spec 更新到达。若不阻塞，这些旧上下文写入会污染已清零的 RAS 寄存器。

两类信号的协作关系：`contextFlush` 负责"清零"，`bpuFlushing` 负责"防护"--`bpuFlushing` 持续期间门控 spec/commit/redirect 三条路径的输入，阻断旧上下文更新；`contextFlush` 当拍在清零块中以 last-connect 覆盖所有寄存器。BPU 顶层将二者一并分发给 RAS：

```scala
// Bpu.scala：该段已由 01 §5.3.3 统一定义，必须与 BpuFlushCtrl 一起裁剪
if (HasBpuFlush) {
  predictors.zipWithIndex.foreach { case (p, i) =>
    p.io.contextFlush.get := fc.io.contextFlush && fc.io.activeFlushMask(i)
    p.io.bpuFlushing.get  := fc.io.bpuFlushing  && fc.io.activeFlushMask(i)
  }
}
```

RAS 顶层需将 `contextFlush` 传入内部 RasStack 模块，供其末尾清零块使用。RasStackIO 需新增端口：

```scala
// RasStack.scala RasStackIO 内（建议新增）
val contextFlush: Option[Bool] = Option.when(HasBpuFlush)(Input(Bool()))
```

RAS 顶层接入：

```scala
// Ras.scala：只读可选 I/O 用 elaboration-time Scala if 统一解包
private val contextFlush = if (HasBpuFlush) io.contextFlush.get else false.B
private val bpuFlushing  = if (HasBpuFlush) io.bpuFlushing.get  else false.B

if (HasBpuFlush) {
  stack.contextFlush.get := contextFlush
}

// RasStack.scala：内部使用同样的解包方式
private val contextFlush = if (HasBpuFlush) io.contextFlush.get else false.B
```

刷新耗时总表：

| 存储结构类别                                | 实现方式                                                | 刷新耗时                                         |
| :------------------------------------------ | :------------------------------------------------------ | :----------------------------------------------- |
| 全部存储结构（数组/指针/旁路/timing/real*） | 寄存器（`Reg`/`RegInit`/`RegNext`/`RegEnable`） | 1 cycle（清零）；窗口防护持续至聚合`resetDone` |

下文按"栈数据与指针 / 写相关 / 读相关 / 流水线暂存"四类与 §3 一一对应，分别给出 contextFlush 末尾清零与 bpuFlushing 输入门控代码。

### 4.1 栈数据与指针刷新

`commitStack`/`specQueue`/`specNos` 与全部指针计数器在 contextFlush 末尾统一清零，恢复上电初始值。利用 Chisel 的 last-connect 语义，将清零块置于 commit/`realPush`/`specPush`/`specPop`/redirect 等所有写入块之后，以最高优先级覆盖三条路径当拍的写入（含 `realPush` 延迟写入与指针修改）。注意 `tosr` 必须恢复为 `RasPtr(true.B, (SpecQueueSize-1).U)`，否则无法表达"spec 队列为空"（`tosrInRange` 判定依赖 `tosr` 严格在 `tosw` 之前）。

```scala
// RasStack.scala 末尾（建议新增，覆盖 spec/commit/redirect 对栈数据与指针的全部写入）
if (HasBpuFlush) {
  when(contextFlush) {
    // 栈数据
    commitStack.foreach { e =>
      e.retAddr := PrunedAddrInit(0.U(VAddrBits.W))
      e.ctr     := 0.U
    }
    specQueue.foreach { e =>
      e.retAddr := PrunedAddrInit(0.U(VAddrBits.W))
      e.ctr     := 0.U
    }
    specNos.foreach { p =>
      p.flag  := false.B
      p.value := 0.U
    }
    // 指针与计数器
    nsp := 0.U
    ssp := 0.U
    sctr := 0.U
    tosr := RasPtr(true.B, (SpecQueueSize - 1).U)
    tosw := RasPtr(false.B, 0.U)
    bos  := RasPtr(false.B, 0.U)
    specNearOverflowed := false.B
  }
}
```

`specNearOverflowed` 虽由组合逻辑驱动，但其本身是寄存器，contextFlush 当拍清零后，下一拍其组合输入 `tosw/bos` 已是初始值，`distanceBetween` 为 0，自然保持 `false`。

### 4.2 状态更新通路刷新

状态更新通路刷新包含两部分：**contextFlush 末尾清零**（覆盖当拍残留写入）与**输入端门控**（阻断 `bpuFlushing` 窗口内的旧上下文更新）。二者协同：门控封堵 T+1 拍起的窗口内污染，末尾清零覆盖 T 拍当拍来自 T-1 拍的残留。

#### writeBypass 清零

`writeBypassValid` 必须清为 `false`，否则刷新后 `getTop` 仍会采信旧旁路 entry；`writeBypassEntry/writeBypassNos` 一并清零以消除 X 态残留：

```scala
// RasStack.scala 末尾（建议新增，覆盖 writeBypass 优先级链）
if (HasBpuFlush) {
  when(contextFlush) {
    writeBypassEntry := 0.U.asTypeOf(new RasEntry)
    writeBypassNos   := 0.U.asTypeOf(new RasPtr)
    writeBypassValid := false.B
  }
}
```

`writeBypassValidWire` 是 `Wire`，其值由优先级链组合决定，无需也无法寄存器清零；只要 `writeBypassValid`（寄存器）清零且 spec/redirect 被门控（见下文 spec/redirect 输入门控），`writeBypassValidWire` 在刷新当拍及之后自然为 `false`。

#### realPush 覆盖（阻断延迟写入通道）

`realPush` 是 `Wire`，`when(contextFlush) { realPush := false.B }` 利用 last-connect 语义覆盖其当拍的组合赋值，使 T 拍 `realPush` 为 `false.B`，阻断 T 拍（来自 T-1 拍 `RegNext` 输出）对 `specQueue/specNos` 的写入：

```scala
// RasStack.scala 末尾（建议新增，阻断延迟写入通道）
if (HasBpuFlush) {
  when(contextFlush) {
    realPush := false.B
  }
}
```

T+1 拍 contextFlush 已过，`realPush` 恢复为 `RegNext` 组合的输出，但由于 `bpuFlushing` 仍有效，`spec.pushValid` 与 `redirect.valid` 均被门控为假（见下文 spec/redirect 输入门控），`RegNext` 输出为假，`realPush` 自然为 `false.B`。

`realWriteEntry/Addr/Nos` 无需显式清零：三者唯一功能消费侧是 `when(realPush)` 块，`realPush` 被覆盖且其产生端被完整门控后，残留数据不会写入 `specQueue/specNos`。其中 `realWriteAddr/realNos` 的 `RegEnable` 使能含有效 redirect 条件，窗口内不采样；`realWriteEntry` 的现有使能表达式是 `io.spec.fire || io.redirect.isCall`（第二项未与 `io.redirect.valid`），因此可能采样无效 payload，但在 `realPush=false` 时不会被消费。

#### redirect 延迟脉冲防护

`redirect` 是 `RegNextWithEnable(io.redirect)`。如 §3.2 所述，其 `valid` 是 `io.redirect.valid` 的一拍延迟，输入无效时下一拍自动回到 `false`；只有 `bits` 在输入无效时保留旧值。因此不需新增刷新专用的 `redirect.valid` 清零赋值。

T 拍同时到达的 redirect 可能使 T+1 拍 `redirect.valid=true`，但 §4.2 的 `stack.redirect.valid` 在整个 `bpuFlushing` 窗口内被组合门控为假，该脉冲不会被 RasStack 消费；再下一拍 `redirect.valid` 自动清零，不会在刷新结束后复活。`HasBpuFlush=false` 时不生成该门控，`redirect` 寄存器本身完全保持基线实现。

#### spec 路径输入门控（specInValid）

spec 路径污染有两个时间维度：T 拍当拍的指针修改（`specPush`/`specPop` 直接改指针）与 T+1 拍的 `realPush` 延迟写入，且 `bpuFlushing` 窗口内仍可能有旧上下文 spec 更新到达。在 RAS 顶层先用 `specInValidRaw/specIsCall/specIsReturn` 三个内部 wire 承接 `io.specIn` 的相应字段，再用 `!bpuFlushing` 生成门控后的 `specInValid`；后续门控不再直接引用 `io.specIn.*`。这使整个刷新窗口内 spec 路径完全不触发：

```scala
// Ras.scala（建议新增）
private val specInValidRaw = Wire(Bool())
private val specIsCall     = Wire(Bool())
private val specIsReturn   = Wire(Bool())
specInValidRaw := io.specIn.valid
specIsCall     := io.specIn.bits.attribute.isCall
specIsReturn   := io.specIn.bits.attribute.isReturn

private val specInValid = Wire(Bool())
specInValid := specInValidRaw
if (HasBpuFlush) {
  specInValid := specInValidRaw && !bpuFlushing
}

// 修改前（Ras.scala:70-71）
// private val specPush = io.specIn.valid && io.specIn.bits.attribute.isCall
// private val specPop  = io.specIn.valid && io.specIn.bits.attribute.isReturn
// 修改后
private val specPush = specInValid && specIsCall   // 建议修改
private val specPop  = specInValid && specIsReturn // 建议修改

// stack.spec.fire 同样改用 specInValid（Ras.scala:80）
stack.spec.fire := specInValid   // 建议修改
```

门控后 `bpuFlushing` 窗口内 `specPush/specPop` 为假，指针不被修改；`io.spec.pushValid` 为假，经 `RegNext` 传至下一拍 `realPush` 为假。配合 §4.1 的 contextFlush 末尾清零覆盖 T 拍当拍残留（`realPush` 写入与指针修改），二者协同封堵当拍、下一拍及整个刷新窗口的 spec 污染。

#### commit 路径输入门控（commitValid / commitInfo）

commit 路径经 `RegNext` 延迟 1 拍，T 拍 `commitValid`（来自 T-1 拍）可能为真触发 commit 写入；`bpuFlushing` 窗口内若旧上下文 `io.commit.valid` 到达，未门控则后续拍 `commitValid` 仍可能为真。先用 `commitInValidRaw` 内部 wire 承接 `io.commit.valid`，再用 `!bpuFlushing` 生成 `commitInValid`，`RegNext` 和 `RegEnable` 均只使用内部 wire：

```scala
// Ras.scala:111
// 修改前
// private val commitValid = RegNext(io.commit.valid, init = false.B)
// 修改后
private val commitInValidRaw = Wire(Bool())
commitInValidRaw := io.commit.valid

private val commitInValid = Wire(Bool())
commitInValid := commitInValidRaw
if (HasBpuFlush) {
  commitInValid := commitInValidRaw && !bpuFlushing
}
private val commitValid = RegNext(commitInValid, init = false.B)  // 建议修改
```

同时门控 `commitInfo` 的 `RegEnable` 使能（原使能 `io.commit.valid` 当前未门控，bpuFlushing 期间会残留旧上下文 commit bits）：

```scala
// Ras.scala:112
private val commitInfo     = RegEnable(io.commit.bits, io.commit.valid)
```

```scala
// Ras.scala:112
// 修改前
// private val commitInfo = RegEnable(io.commit.bits, io.commit.valid)
// 修改后
private val commitInfo = RegEnable(io.commit.bits, commitInValid)  // 建议修改
```

门控后 bpuFlushing 期间 `commitValid` 输入恒假、`commitInfo` 不采样，commit 路径不触发。配合 §4.1 的 contextFlush 末尾清零覆盖 T 拍当拍 commit 写入，二者协同封堵 T 拍与整个刷新窗口的 commit 污染。

#### redirect 路径输入门控（stack.redirect.valid）

redirect 路径是最复杂的冲突点：contextFlush（由 ifence 触发）与 ifence 的 redirect 信号同拍到达，redirect 携带的 `meta.ras` 是**旧上下文的 RAS 状态快照**，若允许恢复等于把旧状态重新写回刚清空的 RAS，刷新彻底失效。redirect 污染有三个时间维度：T 拍指针恢复、T 拍 realPush（redirect 分支）、T+1 拍 realPush（redirect 分支）。在 RAS 顶层用 `!bpuFlushing` 门控传入 RasStack 的 `stack.redirect.valid`：

```scala
// Ras.scala:104
// 修改前
// stack.redirect.valid := redirect.valid && (isBefore(redirectTOSW, stackTOSW) || !stackNearOverflow)
// 修改后
private val rawRedirectValid = redirect.valid && (isBefore(redirectTOSW, stackTOSW) || !stackNearOverflow)
stack.redirect.valid :=
  if (HasBpuFlush) rawRedirectValid && !bpuFlushing
  else rawRedirectValid
```

门控后 `bpuFlushing` 窗口内 RasStack 内 `when(io.redirect.valid)` 块不执行，旧 meta 不被恢复，额外 `specPush`/`specPop` 不触发。配合 §4.1 的 contextFlush 末尾清零覆盖 T 拍 redirect 对 `specQueue/specNos` 与指针的残留写入，以及上文对一拍延迟 `redirect.valid` 的窗口隔离，可封堵 redirect 路径的全部时间维度，并保证旧上下文 meta 不被写回。`HasBpuFlush=false` 时 `else` 分支恢复原始 `rawRedirectValid` 表达式。

### 4.3 预测输出通路刷新

寄存器清零在 T 拍末写入沿才生效，T 拍当拍 `timingTop.retAddr` 仍输出 T-1 拍锁存的旧值。虽 §4.2 的输入端门控保证 T 拍 RAS 不被 specPop 触发（指针不被修改），且 BPU 顶层在 `bpuFlushing` 期间不消费 RAS 预测（见 §5），旧值不产生功能副作用，但若要求 T 拍当拍 `io.spec.popAddr` 即输出 0（而非依赖下游不消费），可在输出端追加组合门控：

```scala
// RasStack.scala:323（建议修改）
// 修改前
// io.spec.popAddr := timingTop.retAddr
// 修改后
io.spec.popAddr :=
  if (HasBpuFlush) Mux(contextFlush, PrunedAddrInit(0.U(VAddrBits.W)), timingTop.retAddr)
  else timingTop.retAddr
```

- `contextFlush=true`（T 拍）：组合输出 0，无需等寄存器写入沿；
- `contextFlush=false`（其余拍）：输出 `timingTop.retAddr`，正常路径不受影响。

该门控只增加一个 2 选 1 Mux（`contextFlush` 来自状态机寄存器输出，时序路径很短），不构成关键路径。它与 §4.4 的寄存器清零、§4.2 的输入端门控三者协同，构成完整防护：

| 防护层             | 位置                                        | 作用                                |
| :----------------- | :------------------------------------------ | :---------------------------------- |
| §4.2 输入门控     | `specInValid = s3_fire && !bpuFlushing`   | T 拍不触发 specPop，指针不被修改    |
| §4.4 寄存器清零   | `when(contextFlush) { timingTop := 0.U }` | T 拍末 timingTop 清零，T+1 拍起干净 |
| 输出端门控（本节） | `Mux(contextFlush, 0, timingTop.retAddr)` | T 拍当拍组合输出 0，不留旧值窗口    |

注意门控信号用 `contextFlush` 而非 `bpuFlushing`：T+1 拍起 `timingTop` 已在 T 拍末被清零为 0，`Mux(false, 0, 0)` 仍为 0，无需延长门控至整个刷新窗口；用 `contextFlush` 更精确，只干预真正需要的那 1 拍。

### 4.4 流水线暂存寄存器刷新

`timingTop` 的 `when` 链依赖多个未清零前的旧信号，刷新当拍其计算结果不可信。直接清零以消除不确定性：

```scala
// RasStack.scala 末尾（建议新增，覆盖 timingTop when 链）
if (HasBpuFlush) {
  when(contextFlush) {
    timingTop := 0.U.asTypeOf(new RasEntry)
    timingNos := 0.U.asTypeOf(new RasPtr)
  }
}
```

T 拍末写入沿清零生效，T+1 拍起 `timingTop`/`timingNos` 为干净初值（0）。配合 §4.3 的输出端门控，T 拍当拍组合输出 0、T+1 拍起寄存器输出 0，预测目标输出全程不残留旧值。

### 4.5 `HasBpuFlush` 编译裁剪清单

`HasBpuFlush` 是 elaboration-time Scala `Boolean`，不是运行时硬件信号。RAS 按 00 §3.1/§3.4 和 01 §6.2 使用“可选 I/O + Scala 条件生成”实现刷新机制。

| 位置                  | `HasBpuFlush=true`                                                                                               | `HasBpuFlush=false`                                                      |
| :-------------------- | :----------------------------------------------------------------------------------------------------------------- | :------------------------------------------------------------------------- |
| `BasePredictorIO`   | 生成`contextFlush`/`bpuFlushing`/`resetDone`                                                                 | 三个端口均不生成                                                           |
| `RasStackIO`        | 生成可选`contextFlush` 输入并由 `Ras` 连接                                                                     | 端口与连线均不生成                                                         |
| 可选 I/O 解包         | `contextFlush/bpuFlushing` 由 Scala `if (HasBpuFlush) io.<port>.get else false.B` 定义为只读内部别名           | 可选刷新 I/O 不生成，别名为`false.B`                                     |
| 派生门控 wire         | `specInValid`/`commitInValid` 使用 `Wire` 表达“默认原值 + 刷新条件覆盖”                                    | 条件覆盖不生成，wire 保持 raw 输入语义                                     |
| 栈数据与指针          | 生成`contextFlush` 末尾清零赋值                                                                                  | 清零`when`、foreach 赋值不参与 elaboration                               |
| write bypass / timing | 生成`writeBypass*`、`timingTop/Nos` 清零赋值                                                                   | 刷新赋值不生成，原寄存器及优先级链保持不变                                 |
| `realPush`          | 生成 context-flush 当拍的 last-connect 阻断                                                                        | 额外赋值不生成，恢复原`RegNext` 组合结果                                 |
| spec 路径             | I/O 先接入`specInValidRaw/specIsCall/specIsReturn` wire，再生成 `specInValid = specInValidRaw && !bpuFlushing` | `specInValid = specInValidRaw`，门控后表达式不直接引用 `io.*`          |
| commit 路径           | I/O 先接入`commitInValidRaw` wire，再生成 `commitInValid = commitInValidRaw && !bpuFlushing`                   | `commitInValid = commitInValidRaw`，`RegNext`/`RegEnable` 恢复原使能 |
| redirect 路径         | `stack.redirect.valid` 追加 `!bpuFlushing`                                                                     | 恢复原`rawRedirectValid`，不生成额外寄存器                               |
| 预测输出              | 生成`contextFlush` 组合置零 Mux                                                                                  | 直接输出`timingTop.retAddr`，额外 Mux 不生成                             |
| 完成握手              | 生成`io.resetDone.get := !contextFlush`                                                                          | `resetDone` 端口和赋值均不生成                                           |

`HasBpuFlush=false` 时必须保留的基线结构包括 `commitStack/specQueue/specNos`、全部指针与计数器、`writeBypass*`、`realPush/realWrite*`、`timingTop/Nos`、`redirect`、`commitValid/commitInfo` 以及恒为 `true.B` 的 `io.sramResetDone`；它们都是 RAS 原有功能数据通路，不属于刷新裁剪对象。

验收时必须分别 elaboration `HasBpuFlush=true` 和 `HasBpuFlush=false` 两种配置：两者均不得出现 `None.get`、未连接端口或 FIRRTL 错误；关闭配置的生成 RTL 中不得出现 RAS context-flush 端口、清零赋值、`resetDone` 逻辑及 spec/commit/redirect/output 的刷新门控，RAS 正常预测与状态更新行为应与未引入刷新机制的基线一致。

---

## 5. 应用刷新机制之后的预测流水线

逐拍追踪，假设 `HasBpuFlush=true`、`contextFlush` 在 T 拍有效（1 cycle 脉冲），`bpuFlushing` 从 T 拍起持续为真直到聚合 `resetDone` 置位（T+k+1 拍），且之前流水线正常工作。RAS 顶层在 `bpuFlushing` 窗口内对三条路径施加 `!bpuFlushing` 门控，RasStack 末尾 `contextFlush` 清零块覆盖所有寄存器。`HasBpuFlush=false` 时本节所述刷新时序不存在，RAS 保持基线行为。

- **T-1 拍**（contextFlush 未到）

  - spec 路径：`io.specIn.valid` 可能为真，`specPush`/`specPop` 正常更新 `tosr/tosw/ssp/sctr`；`realPush` 在本拍由 T-2 拍 `spec.pushValid` 决定，可能写 `specQueue/specNos`。
  - commit 路径：`io.commit.valid` 可能为真，T 拍将生成 `commitValid=true`。
  - redirect 路径：`io.redirect.valid` 可能为真，T 拍将生成 `redirect.valid=true`，且 `stack.redirect.valid` 在 T 拍为真。
  - `timingTop` 持有旧上下文栈顶地址，`io.topRetAddr` 输出旧预测目标（此时仍正确）。
- **T 拍**（contextFlush 有效，bpuFlushing 有效）

  - **spec 路径**：`specInValidRaw` 承接 `io.specIn.valid`，门控后 `specInValid = specInValidRaw && !bpuFlushing = false`，`specPush`/`specPop` 为假，`specPush()`/`specPop()` 不调用，指针不被修改；`stack.spec.fire` 为假。T 拍 `realPush`（来自 T-1 拍 `spec.pushValid`）可能为真，但其对 `specQueue/specNos` 的写入被 contextFlush 末尾清零覆盖。
  - **commit 路径**：T 拍 `commitValid`（来自 T-1 拍 `commitInValidRaw`）可能为真，commit 块可能尝试写 `commitStack/nsp/bos`，但被 contextFlush 末尾清零覆盖。`commitValid` 的 `RegNext` 输入是门控后的 `commitInValid = commitInValidRaw && !bpuFlushing`，故 T+1 拍 `commitValid=false`。
  - **redirect 路径**：T 拍 `stack.redirect.valid` 被 `!bpuFlushing` 门控为假，RasStack 内 `when(io.redirect.valid)` 块不执行，旧 meta 不被恢复，额外 `specPush`/`specPop` 不触发。T 拍 `realPush`（redirect 分支，来自 T-1 拍 `stack.redirect.valid`）可能为真，其写入被 contextFlush 末尾清零覆盖。若同拍顶层 `io.redirect.valid=true`，延迟后的 `redirect.valid` 在 T+1 拍仍由 `bpuFlushing` 门控而不被消费，之后随无效输入自动回到 `false`。
  - **存储清零时机**：contextFlush 末尾清零块在 T 拍末写入沿生效：`commitStack`（`CommitStackSize` 项，默认 16）、`specQueue/specNos`（`SpecQueueSize` 项，默认 32）全部清零；指针 `nsp/ssp/sctr` 清零，`tosr` 恢复为 `RasPtr(true.B, (SpecQueueSize - 1).U)`，`tosw/bos` 恢复为 `RasPtr(false.B,0)`，`specNearOverflowed=false`；`writeBypassEntry/Nos` 清零、`writeBypassValid=false`；`timingTop/Nos` 清零；`realPush` 组合写闸在当拍被覆盖为 `false`。
  - **预测输出**：T 拍 `io.topRetAddr` 由输出端组合门控（§4.3）强制为 0--`Mux(contextFlush, PrunedAddrInit(0.U(VAddrBits.W)), timingTop.retAddr)` 在 T 拍当拍即输出 0，无需等寄存器写入沿。虽 `timingTop` 寄存器本身仍持有 T-1 拍锁存的旧值（要等 T 拍末写入沿才清零），但输出端 Mux 已将其隔离。BPU 顶层在 `bpuFlushing` 期间整体刷新握手未完成，亦不消费 RAS 预测，该 0 值不产生副作用。
- **T+1 拍**（contextFlush 已过，bpuFlushing 仍有效）

  - `commitStack/specQueue/specNos` 已全零（T 拍末写入生效）。
  - 指针已恢复初始值：`ssp=0, nsp=0, sctr=0, tosr=RasPtr(true.B,(SpecQueueSize-1).U), tosw=RasPtr(false.B,0), bos=RasPtr(false.B,0)`，`specNearOverflowed=false`。
  - `writeBypassValid=false`，`getTop`/`getTopNos` 不采信旁路，回退读 `commitStack(0)=0`。
  - `timingTop=0`，`io.topRetAddr=0`（无效预测目标）。
  - **spec 路径**：`specInValid` 仍被 `!bpuFlushing` 门控为假，不触发 spec 更新。
  - **commit 路径**：`commitValid=false`（T 拍门控生效），不写 `commitStack`。
  - **redirect 路径**：`stack.redirect.valid` 仍被 `!bpuFlushing` 门控为假，不执行 redirect 恢复。`redirect.valid` 仅是 `io.redirect.valid` 的一拍延迟；若后续输入无效，它会自动变为 `false`，不会在 bpuFlushing 拉低后触发旧 redirect 恢复。
- **T+2 至 T+k 拍**（bpuFlushing 持续有效，等待 SRAM 型预测器清零完成）

  - RAS 寄存器保持清零状态，三条路径均被 `!bpuFlushing` 门控阻断，旧上下文的 commit/redirect/spec 更新无法到达 RAS。
  - `io.resetDone.get` 自 T+1 拍起即为真（寄存器型 1-cycle 清零完成），但 BPU 顶层的聚合 `resetDone` 需等待本事务 `activeFlushMask` 选中的所有预测器（含 SRAM 型）完成后才置位。
- **T+k+1 拍**（聚合 `resetDone` 置位，bpuFlushing 拉低，状态机迁入 `s_done`）

  - `bpuFlushing` 拉低，三条路径的门控恢复跟随各自输入。
  - 若新上下文有 call/ret，RAS 从空栈开始正常累积。`timingTop` 从 0 开始，新上下文首次 ret 预测目标为 0（无效），BPU 顶层按未命中处理。

总结：`contextFlush` 有效时，三条路径在 T 拍的当拍写入被末尾清零覆盖；`bpuFlushing` 窗口内（T+1 至 T+k 拍）的旧上下文更新被输入门控阻断；输出端组合门控（§4.3）使 T 拍当拍 `io.topRetAddr` 即输出 0，`timingTop` 寄存器在 T 拍末清零使 T+1 拍起预测输出持续为 0。RAS 在 T+1 拍即进入干净的空栈状态，并在整个 `bpuFlushing` 窗口内保持该状态，可安全服务新上下文。

| 时刻          | T-1   | T                     | T+1    | T+2~T+k  | T+k+1                  |
| ------------- | ----- | --------------------- | ------ | -------- | ---------------------- |
| contextFlush  | false | true (脉冲)           | false  | false    | false                  |
| bpuFlushing   | false | true                  | true   | true     | false                  |
| 三路门控      | 正常  | 全阻断                | 全阻断 | 全阻断   | 恢复                   |
| 寄存器        | 旧值  | 末尾清零生效          | 已清零 | 保持清零 | 开始累积新值           |
| io.topRetAddr | 旧值  | **0(组合门控)** | 0      | 0        | 0/新值                 |
| resetDone     | true  | false                 | true   | true     | true                   |
| 聚合resetDone | true  | false                 | false  | false    | true (RAS就绪但等SRAM) |

---

## 6. 刷新完成信号 `resetDone`

RAS 的所有存储（`commitStack`/`specQueue`/`specNos` 数组、全部指针计数器、`writeBypass`、`timingTop/Nos`、`realPush`/`realWrite*`）均为寄存器，`contextFlush` 当拍触发的清零在下一拍即全部生效，刷新耗时 1 cycle。01 文档 §2.3.2/§6.2 规定寄存器型预测器在清零次拍即可报告 `resetDone`。本节逻辑仅在 `HasBpuFlush=true` 时生成。

### 6.1 `sramResetDone` 与 `resetDone` 的职责区分

当前 RAS 代码中 `io.sramResetDone := true.B`：

```scala
// Ras.scala:56
io.sramResetDone := true.B
```

且 RAS 无 SRAM。根据 01 文档 §5.3.4 的明确区分：

- **`sramResetDone`**：用于**上电 SRAM 初始化完成**并门控 `s0_fire`：

```scala
// Bpu.scala:313-317
private val sramResetDone = RegInit(false.B)
when(predictors.map(_.io.sramResetDone).reduce(_ && _)) {
  sramResetDone := true.B
}
s0_fire := s1_ready && sramResetDone
```

RAS 无 SRAM，`sramResetDone` 应**保持 `true.B` 不变**，表示上电即就绪，与上下文刷新完全解耦。`sramResetDone` 不受 `contextFlush` 影响，也不参与 BPU flush FSM 的聚合 `resetDone`。

- **`resetDone`**：用于**上下文切换刷新完成握手**。这是 01 文档 §2.2.1 定义的可选接口，BPU 顶层在 `if (HasBpuFlush)` 内对本事务 `activeFlushMask` 选中的预测器完成电平做与归约，直接驱动 `BpuFlushCtrl.io.resetDone`，决定何时从 `s_flushing` 迁入 `s_done`。`resetDone` 不参与 `s0_fire` 门控。

### 6.2 `resetDone` 实现

RAS 为寄存器型，1-cycle 清零完成。`resetDone` 的语义为：`contextFlush` 有效当拍为假（正在清零），其余时刻为真（清零完成或尚未触发刷新）。采用 `!contextFlush` 即可满足：

```scala
// Ras.scala：用真实完成条件替换当前 io.resetDone.get := true.B 占位
if (HasBpuFlush) {
  io.resetDone.get := !contextFlush // contextFlush 当拍为 false，其余为 true
}
```

`BasePredictorIO` 中的 `resetDone` 类型为 `Option[Bool] = Option.when(HasBpuFlush)(Output(Bool()))`（01 §5.1.3）。因此赋值必须放在 Scala `if (HasBpuFlush)` 中并通过 `.get` 访问；关闭时端口和赋值均不生成。

时序行为（假设 T 拍 `contextFlush` 为单拍脉冲）：

| 时间段     | `contextFlush` | `resetDone` | 含义                            |
| :--------- | :--------------: | :-----------: | :------------------------------ |
| T 拍       |       true       |     false     | contextFlush 当拍，清零正在执行 |
| T+1 拍起   |      false      |     true     | 清零已生效，向上层报告就绪      |
| 下次 T' 拍 |       true       |     false     | 新一次 flush，接口暂关          |
| T'+1 拍起  |      false      |     true     | 新一次清零生效，接口重新使能    |

### 6.3 `sramResetDone` 保持不变

```scala
// Ras.scala（保持不变）
io.sramResetDone := true.B   // RAS 无 SRAM，上电即就绪；与上下文刷新解耦
```

### 6.4 BPU 顶层聚合

BPU flush FSM 等待的是本事务 `activeFlushMask` 选中的各预测器 `resetDone` 的组合与归约，而非 `sramResetDone`：

```scala
// Bpu.scala（01 §5.3.4）：与 BpuFlushCtrl 一起受 Scala if 裁剪
if (HasBpuFlush) {
  fc.io.resetDone := predictors.zipWithIndex.map { case (p, i) =>
    !fc.io.activeFlushMask(i) || p.io.resetDone.get
  }.reduce(_ && _)
}
```

`HasBpuFlush=true` 时，RAS 的 `io.resetDone.get` 自 T+1 拍起即为真，是寄存器型预测器中最快就绪的，不会成为聚合完成条件的瓶颈。`HasBpuFlush=false` 时整段聚合不生成。`sramResetDone` 聚合（现有逻辑，门控 `s0_fire`）不受影响。

假设 contextFlush 在 T 拍有效，bpuFlushing 从 T 拍持续到 T+k+1 拍的时序图：
