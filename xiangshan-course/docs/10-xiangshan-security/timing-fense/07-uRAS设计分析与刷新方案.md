# 07-uRAS设计分析与刷新方案

> **本文的刷新编译开关约定**
>
> 1. **接口契约**：`contextFlush`/`bpuFlushing`/`resetDone` 由 `BasePredictorIO` 以 `Option.when(HasBpuFlush)` 声明（01 §5.1.3），BPU 顶层已按本事务 `activeFlushMask` 门控后下发（01 §5.3.3）。uRAS 无独立的逐预测器 CSR 位，`getSubFlushEnable` 对 `MicroRas` 固定返回 `true.B`，因此它在 sticky 总使能 `BPU_FLUSH_EN` 开启后参与刷新。
> 2. **编译开关**：本文所有寄存器清零块、预测输出门控及 `resetDone` 生成，均遵循 [00-BPU刷新机制编译开关实现方案](00-BPU刷新机制编译开关实现方案.md) §3.1/§3.4 和 01 §6.2，受 elaboration-time `HasBpuFlush` 统一裁剪。
> 3. **编码规则**：参考 `sx_fire`/`sx_flush` 范式，只读的可选刷新输入先赋给 `MicroRas` 内部 `Wire`，清零、完成和输出门控逻辑只使用该 `Wire`，不直接使用 `io.contextFlush`/`io.bpuFlushing` 参与门控。`.get` 仅在 `if (HasBpuFlush)` 内用于驱动 `Wire`；刷新专用清零 `when` 和完成逻辑仍放入 Scala `if (HasBpuFlush)`；对既有输出数据通路追加门控时，`else` 分支必须恢复刷新引入前的原表达式。禁止用 `getOrElse(false.B)`/`getOrElse(true.B)` 代替 Scala 守卫。
> 4. **关闭语义**：`HasBpuFlush=false` 时，uRAS 的刷新端口和消费逻辑不参与 elaboration，`MicroRas` 原有 S1–S3 跟踪、override/redirect 处理及 `specOut` 数据通路必须与未引入刷新机制的基线一致；完整裁剪清单见 §4.5。

## 1. uRAS 的预测流水线

uRAS（MicroRas）是一个**推测微 RAS**，运行在 BPU 的 S1–S3 流水线中。它的作用是：在主 RAS（`Ras.scala`，运行在 S3）更新栈顶之前，提前预测 `ret` 指令的返回目标。uRAS **不维护自己的返回地址栈**，而是依赖主 RAS 的 `fullRetAddr`（`ras.io.topRetAddr`）作为基准，然后根据 S1–S3 流水线中 in-flight 的 call/ret 操作修正这个基准值，从而在 S1 就给出预测目标，而不是等到 S3。

uRAS 的预测输出由寄存器保存：在 `s1_fire`、override 或 redirect 等会触发主 `when` 链的拍，组合逻辑根据当拍输入和 in-flight 的 push/pop 状态更新 `isCanUse` 及（在有赋值的分支中）`topRetAddr`，新值从下一拍开始通过 `io.specOut` 可见；当主 `when` 链未命中或某分支未对 `topRetAddr` 赋值时，相应寄存器保持原值。

**输入与译码**：`io.specIn` 携带 S1 级 CFI 信息（`startPc`、`cfiPosition`、`attribute`），`attribute.isCall`/`isReturn` 标识当前指令是 call 还是 ret：

```scala
// MicroRas.scala:86-89
private val specPush = io.specIn.attribute.isCall
private val specPop  = io.specIn.attribute.isReturn
private val specPushAddr = getCfiPcFromPosition(io.specIn.startPc, io.specIn.cfiPosition) + 2.U
```

其中 `getCfiPcFromPosition` 由取指块起始 PC 与指令槽位置算出 CFI 的 PC，再加 2 得到 call 的返回地址；`isCall`/`isReturn` 由 `BranchAttribute.rasAction` 判定。需注意，当 `rasAction` 为 `PopAndPush` 时，`isCall` 和 `isReturn` 都为 false，当前 uRAS 会将该类型按非 call/ret 处理。

**栈顶动作与输出计算**：uRAS 的主 `when` 链按 `hasRedirect` > `hasOverride` > S1 call > S1 ret > 其他 `s1_fire` 的优先级，综合 specIn、S2/S3 跟踪寄存器、`fullRetAddr` 和 `redirectDelay1` 更新 `isCanUse`/`topRetAddr` 寄存器。寄存器的当前值由 `io.specOut` 直接输出：

```scala
// MicroRas.scala:210-211
io.specOut.isCanUse  := isCanUse
io.specOut.retTarget := topRetAddr
```

因此，uRAS 当拍使用的是 S2/S3 跟踪寄存器的旧值，同一时钟沿上的更新值从下一拍生效。源码没有说明采用寄存输出的设计原因，因而不将其归因于避免组合环。

**预测结果消费**：`io.specOut` 被 BPU 顶层在 S1 级消费，当 uBTB/aBTB 预测出 `isReturn` 且 `uras.io.specOut.isCanUse` 为真时，用 `uras.io.specOut.retTarget` 覆盖 ret 目标：

```scala
// Bpu.scala:557-565
when(s1_ubtbPredWithURas.valid && s1_ubtbPredWithURas.bits.attribute.isReturn && uras.io.specOut.isCanUse) {
  s1_ubtbPredWithURas.bits.target := uras.io.specOut.retTarget
}
s1_abtbPredWithURas.foreach {
  case p => when(p.valid && p.bits.attribute.isReturn && uras.io.specOut.isCanUse) {
      p.bits.target := uras.io.specOut.retTarget
  }
}
```

可见 `isCanUse` 是 uRAS 输出的有效性闸门，`retTarget` 是被直接采信的返回目标——二者是 uRAS 唯一对外的预测接口，也是刷新方案必须重点保护的对象。

---

## 2. uRAS 的状态更新路径

uRAS 没有传统意义上的"训练流水线"（它不接收 FTQ 的 commit/train 数据，也不向 FTQ 回写 meta）。其内部寄存器更新可分为三条事件路径：正常预测路径由 **specIn、stageCtrl 和 fullRetAddr** 共同驱动，另外两条分别由 **overrideData** 和 **hasRedirect** 驱动。各路径的触发源、延迟与作用对象如下表：

| 路径 | 触发源 | 延迟 | 主要作用对象 |
| :--- | :--- | :--- | :--- |
| 正常预测 | `io.stageCtrl.s1_fire/s2_fire/s3_fire`；`io.specIn`、`io.fullRetAddr` | 当拍时钟沿更新寄存器 | 采样/推移/清除 S2/S3 跟踪寄存器；计算 `isCanUse/topRetAddr` |
| overrideData | `io.overrideData.valid`（= `s3_override`，S3 晚到覆盖） | 0 拍 | 清零 `s2/s3 hasPush/hasPop`；用 `s3_realPush/s3_realPop` 计算 `isCanUse/topRetAddr` |
| hasRedirect | `io.hasRedirect`（= `redirect.valid`，重定向恢复） | 当拍时钟沿清零 + 1 拍 `redirectDelay1` | 清零 `s2/s3 hasPush/hasPop`；`isCanUse=false`；`redirectDelay1` 下一拍拉高 |

### 2.1 specIn 路径（S1 CFI 信息）

specIn 路径是 uRAS 最频繁的状态更新来源，由 BPU 顶层 S1 流水控制驱动。

- **S2 跟踪采样**：当 `io.stageCtrl.s1_fire` 为真时，将当拍 `specPush`/`specPop` 采入 `s2_hasPush`/`s2_hasPop`；当 `s1_fire && specPush` 时，将 `specPushAddr` 采入 `s2_retAddr`。

```scala
// MicroRas.scala:102-107
}.elsewhen(io.stageCtrl.s1_fire) {
  s2_hasPush := specPush
  s2_hasPop  := specPop
```

- **S3 跟踪推移**：当 `s2_fire` 时，S2 的 push/pop 与返回地址移入 S3。
- **参与输出计算**：specIn 当拍即参与 `isCanUse/topRetAddr` 的 when 链计算。

### 2.2 overrideData 路径（S3 override）

overrideData 路径处理 S3 级晚到预测对早期预测的覆盖。BPU 顶层在 `s3_valid` 且 taken、cfiPosition、attribute 或 target 任一结果与 S1 预测不同时产生 `s3_override`。

- **丢弃早期跟踪状态**：`hasOverride` 为真时清零已寄存的 `s2_hasPush/s2_hasPop` 以及 `s3_hasPush/s3_hasPop`；同时由于它在主 `when` 链中优先于 S1 分支，当拍 S1 操作不参与输出寄存器的计算。S2 清零部分如下：

```scala
// MicroRas.scala:102-105
  // -------------------------------
  when(hasOverride || io.hasRedirect) {
    s2_hasPush := false.B
    s2_hasPop  := false.B
  }.elsewhen(io.stageCtrl.s1_fire) {
```

S3 部分如下：

```scala
// MicroRas.scala:116-120
  when(hasOverride || io.hasRedirect) {
    // Redirect flushes entire pipeline, including S3
    s3_hasPush := false.B
    s3_hasPop  := false.B
```
- **用 S3 真实操作计算输出**：从 `io.overrideData.bits.attribute` 解码 `s3_realPush/s3_realPop`，并算出 `s3_realPushAddr`，据此计算 `isCanUse/topRetAddr`。

```scala
// MicroRas.scala:148-156
}.elsewhen(hasOverride) {
  isCanUse := !s3_realPop
  topRetAddr :=
    Mux(s3_realPop, 0.U.asTypeOf(PrunedAddr(VAddrBits)), Mux(s3_realPush, s3_realPushAddr, io.fullRetAddr))
}
```

### 2.3 hasRedirect 路径（重定向恢复）

hasRedirect 路径在重定向发生时丢弃 uRAS 已跟踪的 S2/S3 操作，并通过本地的 `redirectDelay1` 记录 redirect 后一拍。

- **清除跟踪状态**：`io.hasRedirect` 为真时清零 `s2_hasPush/s2_hasPop/s3_hasPush/s3_hasPop`；它在两组跟踪寄存器和输出寄存器的 `when` 链中都具有最高优先级。
- **置 isCanUse=false**：主 RAS 正在恢复，无有效预测。
- **redirectDelay1 下一拍拉高**：`redirectDelay1 = RegNext(io.hasRedirect)`，用于在随后一拍检测"主 RAS 恢复期"，抑制输出对 `fullRetAddr` 的采信。

**关键事实**：`hasRedirect` 只清零 `s2/s3 hasPush/hasPop` 与 `isCanUse`，**不清零** `s2_retAddr`、`s3_retAddr`、`topRetAddr`、`redirectDelay1`。这一不对称是刷新方案必须重点补足的地方（详见第 4 节）。

---

## 3. uRAS 的整体架构

uRAS 全部存储均为寄存器实现（`RegInit`/`RegNext`，无 SRAM），没有类似 aBTB 的 SRAM 阵列，也没有类似 uBTB 的替换矩阵。其核心是一组 S2/S3 跟踪寄存器加上两个输出寄存器，外加一个 redirect 延迟寄存器。参与预测功能的寄存器总表如下：

| 寄存器 | 实现方式 | 初始值 | 含义 | 写入来源 | hasRedirect 是否清零 | hasOverride 是否清零 |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| `s2_hasPush` | `RegInit(false.B)` | false | S2 级是否有 pending push | specIn(`s1_fire`) | 是 | 是 |
| `s2_hasPop` | `RegInit(false.B)` | false | S2 级是否有 pending pop | specIn(`s1_fire`) | 是 | 是 |
| `s3_hasPush` | `RegInit(false.B)` | false | S3 级是否有 pending push | `s2_fire` | 是 | 是 |
| `s3_hasPop` | `RegInit(false.B)` | false | S3 级是否有 pending pop | `s2_fire` | 是 | 是 |
| `s2_retAddr` | `RegInit(0.U)` | 0 | S2 级 pending push 的返回地址 | `s1_fire && specPush` | 否 | 否 |
| `s3_retAddr` | `RegInit(0.U)` | 0 | S3 级 pending push 的返回地址 | `s2_fire` | 否 | 否 |
| `isCanUse` | `RegInit(false.B)` | false | 输出：预测是否可用 | when 链 | 是（置 false） | 否（按 s3_realPop 计算） |
| `topRetAddr` | `RegInit(0.U)` | 0 | 输出：预测的返回目标 | when 链 | 否 | 否（根据 s3_realPop/s3_realPush/fullRetAddr 计算） |
| `redirectDelay1` | `RegNext(io.hasRedirect, false.B)` | false | redirect 延迟 1 拍，检测 RAS 恢复期 | `RegNext(hasRedirect)` | 否（本身就是延迟） | 否 |

> 说明：`s3_overrideHasRasActionNext` 是 `s3_overrideHasRasAction` 的一拍延迟，仅参与性能计数条件，不参与预测功能逻辑，故未列入上表。它保存的是上一拍的事件标志，而非累积计数值；如果上下文切换恰好发生在该事件的下一拍，它最多可能使当拍的 `s1_has_return_after_override_has_action` 计数条件跨越上下文边界，随后会被新的每拍输入覆盖。

由上表可见一个关键不对称：`hasRedirect` 与 `hasOverride` 都清零 `s2_hasPush/s2_hasPop/s3_hasPush/s3_hasPop`，但都不清零 `s2_retAddr/s3_retAddr/topRetAddr/redirectDelay1`。这四类寄存器是刷新方案必须显式覆盖的重点。

下文按"流水线跟踪寄存器 / 写相关 / 读相关 / 流水线暂存"四类功能归类，与 §4 刷新方案一一对应。

### 3.1 流水线跟踪寄存器（s2/s3 hasPush/hasPop/retAddr）

uRAS 无独立返回地址栈，而是用 S2/S3 跟踪寄存器记录流水线中 in-flight 的 push/pop 操作；这些寄存器只跟踪两个流水级的操作及 push 地址，并不构成另一个栈。`s2_hasPush`/`s2_hasPop`/`s3_hasPush`/`s3_hasPop` 各 1 bit `RegInit(false.B)`，记录 S2/S3 级是否存在 pending push/pop；`s2_retAddr`/`s3_retAddr` 是 `PrunedAddr(VAddrBits)` 宽的 `RegInit(0.U)`，保存对应级 pending push 的返回地址。

```scala
// MicroRas.scala:72-79
private val s2_hasPush = RegInit(false.B)
private val s2_hasPop  = RegInit(false.B)
private val s3_hasPush = RegInit(false.B)
private val s3_hasPop  = RegInit(false.B)
private val s2_retAddr = RegInit(0.U.asTypeOf(PrunedAddr(VAddrBits)))
private val s3_retAddr = RegInit(0.U.asTypeOf(PrunedAddr(VAddrBits)))
```

**读写端口**

- **写**：`s2_hasPush/s2_hasPop` 由三路优先级更新--`hasOverride || hasRedirect` 清零、`s1_fire` 采样、`s2_fire` 清零：

```scala
// MicroRas.scala:102-111
when(hasOverride || io.hasRedirect) {
  s2_hasPush := false.B
  s2_hasPop  := false.B
}.elsewhen(io.stageCtrl.s1_fire) {
  s2_hasPush := specPush
  s2_hasPop  := specPop
}.elsewhen(io.stageCtrl.s2_fire) {
  s2_hasPush := false.B
  s2_hasPop  := false.B
}
```

  `s3_hasPush/s3_hasPop` 由 `hasOverride || hasRedirect` 清零、`s2_fire` 推移、`s3_fire` 清零三路更新（MicroRas.scala:116-128）。`s2_retAddr` 仅在 `s1_fire && specPush` 时写入（MicroRas.scala:130-133），`s3_retAddr` 仅在 `s2_fire` 时从 `s2_retAddr` 推移（MicroRas.scala:135-138），二者**均无显式清零分支**。
- **读**：`s2/s3 hasPush/hasPop` 在输出 when 链中决定 push/pop 配对关系；`s2/s3_retAddr` 在对应 `hasPush` 为真时驱动 `topRetAddr`。

**结论：**`s2_retAddr`/`s3_retAddr` 无任何清零分支，只能靠 `s1_fire && specPush`/`s2_fire` 覆盖，因而旧返回地址可以长期驻留。在当前功能逻辑中，它们只在对应 `hasPush` 为真时被选中，而正常的 push 会同步更新对应地址，因此不能将残留值本身表述为必然的功能错误；若上下文刷新目标包含清除所有地址残留，则需要显式清零。

### 3.2 状态更新通路

uRAS 没有独立的写入旁路或延迟写入通道（如 RAS 的 writeBypass/realPush）。正常路径下，`io.stageCtrl` 决定 S1 信息何时采入 S2、S2 何时推移到 S3 以及各级何时清除；`io.specIn` 提供 S1 CFI 信息，`io.fullRetAddr` 提供主 RAS 栈顶。`io.overrideData` 和 `io.hasRedirect` 则以更高优先级修正或使预测状态失效。

**specIn**：携带 S1 级 `startPc`/`cfiPosition`/`attribute`，`attribute.isCall`/`isReturn` 标识 call/ret：

```scala
// MicroRas.scala:86-89
private val specPush = io.specIn.attribute.isCall
private val specPop  = io.specIn.attribute.isReturn
private val specPushAddr = getCfiPcFromPosition(io.specIn.startPc, io.specIn.cfiPosition) + 2.U
```

**overrideData**：`io.overrideData.valid` 即 `hasOverride`（= `s3_override`），从 `io.overrideData.bits` 解码出 `s3_realPush/s3_realPop/s3_realPushAddr`（MicroRas.scala:93-97），为真时清除 S2/S3 跟踪标志并重算输出（MicroRas.scala:148-156）。

**hasRedirect**：`io.hasRedirect`（= `redirect.valid`），为真时清零 S2/S3 的 hasPush/hasPop 并置 `isCanUse=false`，同时 `redirectDelay1` 下一拍拉高。

**关键不对称**：`hasRedirect` 只清零 `s2/s3 hasPush/hasPop` 与 `isCanUse`，**不清零** `s2_retAddr`/`s3_retAddr`/`topRetAddr`/`redirectDelay1`。这四类寄存器是刷新方案必须显式补足的重点（详见 §4.1、§4.4）。

**结论**：当前 uRAS 不使用 `contextFlush`/`bpuFlushing` 对上述输入或状态更新进行门控；§4.2 说明刷新方案为何不需要输入门控。

### 3.3 预测输出通路

uRAS 的预测结果输出路径为：`isCanUse`/`topRetAddr`（内部寄存器）-> `io.specOut`（模块输出）-> BPU 顶层 S1 级消费。

```scala
// MicroRas.scala:210-211
io.specOut.isCanUse  := isCanUse
io.specOut.retTarget := topRetAddr
```

当前代码中 `io.specOut` 始终直接取内部寄存器值，未使用已由 `BasePredictorIO` 提供的 `bpuFlushing` 输入。`io.specOut` 被 BPU 顶层在 S1 级消费，当 uBTB/aBTB 预测出 `isReturn` 且 `isCanUse` 为真时用 `retTarget` 覆盖 ret 目标（Bpu.scala:557-566）。为保证 `bpuFlushing` 窗口内 BPU 顶层不采信 uRAS 预测，输出端需追加组合门控（`bpuFlushing` 时 `isCanUse=false`、`retTarget=0`），具体代码与说明见 §4.3。

### 3.4 流水线暂存寄存器

`isCanUse`/`topRetAddr` 保存"下一拍的预测可用性与返回目标"，直接驱动 `io.specOut`；`redirectDelay1` 是 `hasRedirect` 延迟 1 拍的版本，用于检测"主 RAS 恢复期"。

```scala
// MicroRas.scala:82-83, 92
private val isCanUse   = RegInit(false.B)
private val topRetAddr = RegInit(0.U.asTypeOf(PrunedAddr(VAddrBits)))
private val redirectDelay1 = RegNext(io.hasRedirect, init = false.B)
```

**读写端口**

- **写**：`isCanUse`/`topRetAddr` 由一个多分支 `when` 链驱动，综合当拍的 hasRedirect、hasOverride、`s1_fire && specPush`、`s1_fire && specPop`、`s1_fire`（非 call/ret）五类事件（MicroRas.scala:144-208）。该 `when` 链**没有顶层 `.otherwise`**：当既无 hasRedirect、hasOverride，也无 `s1_fire` 时，二者**保持原寄存器值不变**；此外，redirect 以及部分 pop 分支只对 `isCanUse` 赋值，这些分支中 `topRetAddr` 也保持原值。`redirectDelay1` 由 `RegNext` 自动延迟 `io.hasRedirect`，无其他写入分支。
- **读**：`io.specOut.isCanUse := isCanUse`、`io.specOut.retTarget := topRetAddr`；`redirectDelay1` 仅在 `s1_fire` 非 call/ret 且 S2/S3 均 neutral 的内层 `.otherwise` 分支被消费：

```scala
// MicroRas.scala:203-206
}.otherwise {
  isCanUse   := Mux(redirectDelay1, false.B, true.B)
  topRetAddr := Mux(redirectDelay1, 0.U.asTypeOf(PrunedAddr(VAddrBits)), io.fullRetAddr)
}
```

**寄存时序**：`topRetAddr` 的计算读取 S2/S3 跟踪寄存器与 `fullRetAddr` 的当前值，计算结果在时钟沿写入输出寄存器并从下一拍可见。这是源码能够直接确定的时序关系；源码未给出使用该寄存结构的设计原因。

**结论：必须刷新**，且 `isCanUse` 与 `topRetAddr` 必须**同时**清零--单独清 `isCanUse` 无法消除 `topRetAddr` 的粘滞残留（when 链无 `.otherwise`，旧值会保持）；`redirectDelay1` 必须用 `when(contextFlush)` 覆盖 `RegNext` 的默认赋值，否则 T+1 拍它会无条件等于 T 拍的 `hasRedirect`（ifence redirect 与 contextFlush 同拍到达，输入端无法区分）。

---

## 4. 刷新方案

uRAS 全部为寄存器实现（无 SRAM），刷新耗时 **1 cycle**，与 uBTB、RAS 一致。`contextFlush`/`bpuFlushing`/`resetDone` 已由 `BasePredictorIO` 按 `Option.when(HasBpuFlush)` 统一声明，uRAS 无需重复新增顶层端口；当前占位代码 `if (HasBpuFlush) { io.resetDone.get := true.B }` 需在真实刷新实现时替换为 §6.2 的完成逻辑。`io.sramResetDone := true.B`（uRAS 无 SRAM，上电即就绪）保持不变，与上下文刷新解耦。

`MicroRas` 顶部先声明两个模块内部 `Wire`，默认驱动为 `false.B`；开关打开时再在 Scala `if` 中将可选 IO 的值赋给对应 `Wire`。后续清零、完成及输出门控仅使用 `contextFlush`/`bpuFlushing` 这两个内部 `Wire`：

```scala
// MicroRas.scala（建议新增）
private val contextFlush = Wire(Bool())
private val bpuFlushing  = Wire(Bool())
contextFlush := false.B
bpuFlushing  := false.B
if (HasBpuFlush) {
  contextFlush := io.contextFlush.get
  bpuFlushing  := io.bpuFlushing.get
}
```

`Wire(Bool())` 加默认赋值的写法与 `sx_fire`/`sx_flush` 先声明、后连线的风格一致，并保证关闭配置下内部信号有明确默认值；对可选 IO 的 `.get` 只会在 `HasBpuFlush=true` 时 elaboration。刷新专用的清零 `when` 与完成逻辑仍必须放在 `if (HasBpuFlush)` 内，不得只依赖 `Wire` 的常量传播裁剪。关闭配置下，两个恒为 false 的内部 `Wire` 不得在生成 RTL 中留下刷新门控。

uRAS 的刷新方案使用**两类信号协同**，核心策略是"**寄存器清零 + 输出门控**"，**不阻塞预测流水线**：

1. **`contextFlush`（1-cycle 脉冲）**：触发寄存器清零。利用 last-connect 语义，在所有 `when` 块之后追加受 `if (HasBpuFlush)` 守卫的 `when(contextFlush)` 块，以最高优先级覆盖三类输入驱动的所有寄存器写入，并补足 hasRedirect 不覆盖的 `topRetAddr`/`redirectDelay1`/`s2_retAddr`/`s3_retAddr`。该信号仅在 `s_waiting -> s_flushing` 迁移的那 1 拍拉高。

2. **`bpuFlushing`（刷新窗口信号）**：在 BPU 顶层状态机处于 `s_flushing` 期间持续为真（从 `contextFlush` 拉高当拍起，直到本事务选中预测器的聚合 `resetDone` 为真、状态机迁入 `s_done`）。该信号用于**门控 uRAS 输出**--在 `bpuFlushing` 持续期间强制 `io.specOut.isCanUse=false`、`io.specOut.retTarget=0`，使 BPU 顶层不采信 uRAS 预测。**不门控任何输入**：`s1_fire`/`s2_fire`/`s3_fire`/`hasOverride`/`hasRedirect` 均保持原始语义正常流动，预测流水线不阻塞。

两类信号的协作关系：`contextFlush` 负责"T 拍寄存器清零"，`bpuFlushing` 负责"刷新窗口内输出隔离"。`bpuFlushing` 持续期间，uRAS 内部寄存器由 when 链正常计算（新上下文数据随流水线自然流入并更新寄存器），但输出被强制为无效；`bpuFlushing` 拉低后，寄存器已持有新上下文数据，输出立即恢复有效。BPU 顶层将二者一并分发给 uRAS：

```scala
// Bpu.scala：由 01 §5.3.3 统一定义，与 BpuFlushCtrl 一起裁剪
if (HasBpuFlush) {
  predictors.zipWithIndex.foreach { case (p, i) =>
    p.io.contextFlush.get := fc.io.contextFlush && fc.io.activeFlushMask(i)
    p.io.bpuFlushing.get  := fc.io.bpuFlushing  && fc.io.activeFlushMask(i)
  }
}
```

刷新耗时总表：

| 存储结构类别 | 实现方式 | 刷新耗时 |
| :--- | :--- | :--- |
| 全部寄存器（S2/S3 跟踪、输出、redirectDelay1） | 寄存器（`RegInit`/`RegNext`） | 1 cycle |

下文按"流水线跟踪寄存器 / 写相关 / 读相关 / 流水线暂存"四类与 §3 一一对应，分别给出 contextFlush 末尾清零与 bpuFlushing 输出门控代码。

### 4.1 流水线跟踪寄存器刷新

利用 Chisel 的 last-connect 语义，在 S2/S3 跟踪寄存器的所有 `when` 块之后追加 `when(contextFlush)` 块，清零全部 6 个跟踪寄存器，覆盖 specIn/override/redirect 三类输入的当拍写入，并补足 `s2_retAddr`/`s3_retAddr`（它们无任何清零分支，详见 §3.1）：

```scala
// MicroRas.scala 末尾（建议新增，覆盖 S2/S3 跟踪寄存器 when 链）
if (HasBpuFlush) {
  when(contextFlush) {
    s2_hasPush := false.B
    s2_hasPop  := false.B
    s3_hasPush := false.B
    s3_hasPop  := false.B
    s2_retAddr := 0.U.asTypeOf(PrunedAddr(VAddrBits))
    s3_retAddr := 0.U.asTypeOf(PrunedAddr(VAddrBits))
  }
}
```

T 拍末写入沿清零生效，T+1 拍起 S2/S3 跟踪寄存器为干净初值。即便 T 拍 `s1_fire`/`hasOverride`/`hasRedirect` 触发了上述寄存器的写入，contextFlush 清零也会以 last-connect 最终生效。

### 4.2 状态更新通路刷新

uRAS 的刷新方案**不门控任何输入**（`s1_fire`/`s2_fire`/`s3_fire`/`hasOverride`/`hasRedirect` 均保持原始语义正常流动），预测流水线不阻塞。刷新靠 contextFlush 末尾清零覆盖三类输入在 T 拍的当拍写入（§4.1 清零跟踪寄存器、§4.4 清零输出寄存器），并靠 `bpuFlushing` 期间输出门控（§4.3）隔离消费端。

**为什么不需要输入门控**（原输入门控方案 `s1Fire = io.stageCtrl.s1_fire && !bpuFlushing` 会阻断流水线采样，导致 bpuFlushing 拉低后 uRAS 需预热）：

1. **输出门控已隔离消费端**：`bpuFlushing` 期间 `io.specOut.isCanUse=false`，即便内部寄存器存在旧上下文残留，也不会被 BPU 顶层消费。输出门控是"消费端隔离"，与输入门控的"生产端阻断"在功能上等价，但不阻塞流水线。
2. **redirect 后流水线已被冲刷**：T 拍 `redirect.valid=true` 触发 `s3_flush/s2_flush/s1_flush`（[Bpu.scala:302-304](file:///z:/home/zmj/XiangShan/src/main/scala/xiangshan/frontend/bpu/Bpu.scala#L302-L304)），`s1_valid/s2_valid/s3_valid` 在 T 拍末置 false。T+1 拍起新取指从 redirect 目标开始，specIn 携带的是**新上下文**的 CFI 信息，bpuFlushing 窗口内的 specIn 采样是安全的。
3. **T 拍的旧上下文采样被 contextFlush 覆盖**：T 拍 `hasRedirect=true` 在 S2/S3 跟踪 when 链中优先级高于 `s1_fire`（[MicroRas.scala:99-102](file:///z:/home/zmj/XiangShan/src/main/scala/xiangshan/frontend/bpu/ras/MicroRas.scala#L99-L102)），清零 `s2_hasPush/s2_hasPop` 而非采样 specIn；末尾 `when(contextFlush)` 块再以 last-connect 覆盖全部寄存器。
4. **hasRedirect 方向安全**：uRAS 的 `hasRedirect` 只做冲刷（清零 s2/s3 hasPush/hasPop）与置 false（`isCanUse=false`），方向与 contextFlush 一致，不会把旧上下文状态写回。与 RAS 的 redirect 路径不同（RAS redirect 携带旧 meta 会恢复旧状态，必须门控，详见 06-RAS 第 4.2 节），uRAS **不需要**门控 `hasRedirect` 输入。

**hasRedirect 路径的寄存器补足**：`hasRedirect` 在 T 拍清零 `s2/s3 hasPush/hasPop` 并置 `isCanUse=false`，但**不清零** `topRetAddr`/`redirectDelay1`/`s2_retAddr`/`s3_retAddr`。这四类寄存器由 §4.1（`s2_retAddr`/`s3_retAddr`）与 §4.4（`topRetAddr`/`redirectDelay1`）的 contextFlush 末尾清零块补足。

**不阻塞流水线的优势**：`s1_fire`/`s2_fire`/`s3_fire` 在 `bpuFlushing` 窗口内正常流动，redirect 后的新上下文指令随流水线自然流入 S1->S2->S3，uRAS 的跟踪寄存器和输出寄存器被新上下文数据正常更新。`bpuFlushing` 拉低时，寄存器已持有新上下文状态，uRAS **无需预热**即可立即服务新上下文。

### 4.3 预测输出通路刷新

在 `bpuFlushing` 持续期间，强制 uRAS 输出为无效，使 BPU 顶层不采信 uRAS 预测。门控信号用 `bpuFlushing`（覆盖整个刷新窗口，而非仅 `contextFlush` 1 拍），因为 T+1 拍起内部寄存器虽已清零，但新上下文数据尚未流入，输出仍不应被消费：

```scala
// MicroRas.scala（建议修改 io.specOut 赋值处，MicroRas.scala:207-208）
// 修改前
// io.specOut.isCanUse  := isCanUse
// io.specOut.retTarget := topRetAddr
// 修改后
io.specOut.isCanUse :=
  if (HasBpuFlush) isCanUse && !bpuFlushing else isCanUse
io.specOut.retTarget :=
  if (HasBpuFlush) Mux(bpuFlushing, 0.U.asTypeOf(PrunedAddr(VAddrBits)), topRetAddr)
  else topRetAddr
```

门控后：`HasBpuFlush=true` 且 `bpuFlushing` 有效的窗口内（T 拍至 T+k 拍），`io.specOut.isCanUse=false`、`io.specOut.retTarget=0`，BPU 顶层的 ret 目标覆盖逻辑（Bpu.scala:557-566）因 `isCanUse=false` 不触发，uRAS 预测不被消费。`bpuFlushing` 拉低后（T+k+1 拍），输出恢复为 `isCanUse`/`topRetAddr` 的寄存器值。`HasBpuFlush=false` 时，Scala `if` 直接选择原始的 `isCanUse`/`topRetAddr` 表达式，不生成新增 Mux 或与门。

### 4.4 流水线暂存寄存器刷新

`isCanUse`/`topRetAddr` 的 `when` 链依赖多个未清零前的旧信号，且无顶层 `.otherwise`（粘滞保持），刷新当拍其计算结果不可信也不自动归零。`redirectDelay1` 是 `RegNext(io.hasRedirect)`，T 拍 `hasRedirect=true` 会使 T+1 拍 `redirectDelay1=true`，必须用 `when(contextFlush)` 覆盖 `RegNext` 的默认赋值。直接清零以消除不确定性：

```scala
// MicroRas.scala 末尾（建议新增，覆盖 isCanUse/topRetAddr when 链与 redirectDelay1 的 RegNext）
if (HasBpuFlush) {
  when(contextFlush) {
    isCanUse       := false.B
    topRetAddr     := 0.U.asTypeOf(PrunedAddr(VAddrBits))
    redirectDelay1 := false.B
  }
}
```

T 拍末写入沿清零生效，T+1 拍起 `isCanUse`/`topRetAddr`/`redirectDelay1` 为干净初值。配合 §4.3 的输出端门控，T 拍当拍组合输出 0、T+1 拍起寄存器输出 0/false，预测输出全程不残留旧值。

**与主 RAS 的协同**：uRAS 的 `fullRetAddr` 输入直接来自主 RAS 的 `topRetAddr` 输出。contextFlush 信号由 BPU 顶层统一分发，主 RAS 与 uRAS **同拍**收到，二者均为寄存器实现，清零窗口均为 1 cycle，T 拍末写入沿同步完成清零。T+1 拍起主 RAS 的 `timingTop` 已清零（详见 06-RAS 第 4.4 节），故 `ras.io.topRetAddr=0` 即 uRAS 的 `fullRetAddr=0`；uRAS 的 `isCanUse=false`、`topRetAddr=0`。二者状态一致，uRAS 无需等待主 RAS 的 `sramResetDone`（详见 §5、§6）。

### 4.5 `HasBpuFlush` 编译裁剪清单

按 00 §3.1/§3.4 的契约，`HasBpuFlush=false` 时以下 uRAS 刷新专用结构必须从生成 RTL 中消失：

- `BasePredictorIO` 中 uRAS 实例的 `contextFlush`/`bpuFlushing`/`resetDone` 端口；
- `MicroRas` 内部承接刷新输入的 `contextFlush`/`bpuFlushing` `Wire`，在关闭配置下不得使生成 RTL 留下任何刷新组合逻辑；
- `MicroRas` 内对 6 个 S2/S3 跟踪寄存器和 3 个流水暂存寄存器的 `contextFlush` 清零分支；
- `specOut.isCanUse` 上的 `bpuFlushing` 与门和 `specOut.retTarget` 上的置零 Mux；
- `resetDone` 完成逻辑及 BPU 顶层中对 uRAS 的刷新分发/聚合连线。

关闭分支必须保留原有 `io.specOut.isCanUse := isCanUse` 和 `io.specOut.retTarget := topRetAddr`，不得引入额外延迟、寄存器或常驻门控。需按 00 §5.1/§5.2 分别完成 `HasBpuFlush=true` 与 `false` 的 elaboration，并对比关闭配置与未引入刷新功能的基线 RTL，不能只依赖信号名搜索判定零面积。

---

## 5. 应用刷新机制之后的预测流水线

本章时序描述 `HasBpuFlush=true && BPU_FLUSH_EN=1` 时的刷新行为。逐拍追踪，假设 `contextFlush` 在 T 拍有效（1 cycle 脉冲），`bpuFlushing` 从 T 拍起持续为真直到 BPU 顶层聚合的 `resetDone` 置位（T+k+1 拍），且之前流水线正常工作。uRAS **不门控任何输入**，预测流水线正常流动；`contextFlush` 在 T 拍末尾清零全部 9 个寄存器（§4.1、§4.4 节）；`bpuFlushing` 期间输出被门控为无效（§4.3 节）。`HasBpuFlush=false` 时不存在下述刷新时序，uRAS 按原始数据通路运行。

- **T-1 拍**（contextFlush 未到）
  - specIn 路径：`s1_fire` 可能为真，`specPush`/`specPop` 正常采入 `s2_hasPush/s2_hasPop/s2_retAddr`。
  - overrideData 路径：`s3_override` 可能为真，`hasOverride` 正常清零 S2/S3 并按 S3 操作计算输出。
  - hasRedirect 路径：`io.hasRedirect` 可能为真（旧上下文的误预测恢复），T 拍将使 `when(hasRedirect)` 分支执行。
  - `isCanUse`/`topRetAddr` 持有旧上下文的有效预测，`io.specOut` 输出旧预测目标（此时仍正确）。`redirectDelay1` 持有 T-2 拍 `hasRedirect` 的延迟值。

- **T 拍**（contextFlush 有效，bpuFlushing 有效，且与 ifence redirect 同拍到达）
  - **specIn 路径**：`s1_fire` 可能为真（来自 T-1 拍锁存的 `s1_valid`），但 `hasRedirect=true` 在 S2/S3 跟踪 when 链中优先级高于 `s1_fire`，清零 `s2_hasPush/s2_hasPop` 而非采样 specIn。末尾 `when(contextFlush)` 块再以 last-connect 覆盖全部寄存器。
  - **overrideData 路径**：`hasOverride` 可能为真，但末尾 `when(contextFlush)` 块以 last-connect 覆盖其输出计算结果。
  - **hasRedirect 路径**：`io.hasRedirect=true`（ifence redirect），`when(io.hasRedirect)` 分支执行，置 `isCanUse:=false.B`。末尾清零块补足清零 `topRetAddr`/`redirectDelay1`/`s2_retAddr`/`s3_retAddr`。
  - **存储清零时机**：末尾 `when(contextFlush)` 块在 T 拍末写入沿生效：全部 9 个寄存器清零。
  - **预测输出**：`bpuFlushing=true` 使 `io.specOut.isCanUse=false`、`io.specOut.retTarget=0`（输出门控，§4.3 节）。T 拍期间 BPU 顶层不消费 uRAS 预测。

- **T+1 拍**（contextFlush 已过，bpuFlushing 仍有效）
  - 全部 9 个寄存器在 T 拍末已被清零。T+1 拍 `contextFlush=false`，寄存器由 when 链正常重新计算。
  - **流水线恢复**：T 拍 `redirect.valid` 触发的 `s1_flush/s2_flush/s3_flush` 已在 T 拍末将 `s1_valid/s2_valid/s3_valid` 置 false。T+1 拍起新取指从 redirect 目标开始，`s0_fire` 重新拉高，新上下文指令开始流入 S1。
  - **specIn 路径**：若 `s1_fire` 为真，specIn 携带**新上下文** CFI 信息（redirect 目标后的指令），正常采入 `s2_hasPush/s2_hasPop/s2_retAddr`。这是新上下文的正确跟踪数据，无需阻断。
  - **redirectDelay1**：T 拍末尾清零块已将其覆盖为 `false.B`（覆盖 `RegNext` 默认赋值），故 T+1 拍 `redirectDelay1=false`。
  - **isCanUse/topRetAddr 重新计算**：when 链根据新上下文的 specIn 和已清零的 S2/S3 跟踪寄存器计算。若无 `s1_fire`（流水线尚未恢复），when 链无分支匹配，`isCanUse`/`topRetAddr` 保持 T 拍末清零值（`false`/`0`）。若有 `s1_fire`，按新上下文 CFI 正常计算。
  - **预测输出**：`bpuFlushing=true` 使 `io.specOut.isCanUse=false`、`io.specOut.retTarget=0`（输出门控）。内部寄存器值不被消费。

- **T+2 至 T+k 拍**（bpuFlushing 持续有效，等待 SRAM 型预测器清零完成）
  - **流水线正常流动**：新上下文指令持续流入 S1->S2->S3，uRAS 的 S2/S3 跟踪寄存器和输出寄存器被新上下文数据正常更新。`s2_hasPush`/`s2_hasPop` 随新上下文的 call/ret 采样，`s3_hasPush`/`s3_hasPop` 随 `s2_fire` 推移，`isCanUse`/`topRetAddr` 随 when 链计算。
  - **预测输出**：`bpuFlushing=true` 使输出持续为无效（`isCanUse=false`、`retTarget=0`）。内部寄存器虽被正常更新，但不被 BPU 顶层消费。
  - **与主 RAS 的基准对齐**：主 RAS 的 `timingTop` 已在 T 拍末清零（详见 06-RAS设计分析与刷新方案 第 5 节），T+1 拍起随新上下文 call/ret 正常更新。uRAS 的 `fullRetAddr` 跟随主 RAS 的 `topRetAddr` 正常变化，基准-修正关系自然重建。
  - `io.resetDone.get` 自 T+1 拍起即为真（寄存器型 1-cycle 清零完成），但 BPU 顶层传给 `BpuFlushCtrl` 的聚合 `resetDone` 需等待本事务选中的所有预测器（含 SRAM 型）均完成后才为真。

- **T+k+1 拍**（聚合 `resetDone` 为真，bpuFlushing 拉低，状态机迁入 `s_done`）
  - `bpuFlushing` 拉低，输出门控解除：`io.specOut.isCanUse := isCanUse`、`io.specOut.retTarget := topRetAddr`。
  - **寄存器已就绪**：由于 bpuFlushing 期间流水线正常流动，S2/S3 跟踪寄存器和输出寄存器已持有新上下文的跟踪数据。uRAS **无需预热**，输出立即有效。
  - 若新上下文已有 call/ret 流过流水线，uRAS 的 S2/S3 跟踪已建立，`isCanUse`/`topRetAddr` 反映新上下文的栈顶状态，可正常服务 ret 预测。

总结：`contextFlush` 在 T 拍末尾清零全部 9 个寄存器（含补足 hasRedirect 遗漏的 4 项）；`bpuFlushing` 期间输出被门控为无效，但**预测流水线不阻塞**，新上下文数据随流水线自然流入并更新寄存器。`bpuFlushing` 拉低后，uRAS 寄存器已持有新上下文状态，输出立即恢复有效，**无需额外预热周期**。

---

## 6. 刷新完成信号 `resetDone`

uRAS 的所有存储（S2/S3 跟踪寄存器、输出寄存器、`redirectDelay1`）均为寄存器，`contextFlush` 当拍触发的清零在下一拍即全部生效，刷新耗时 1 cycle。01 文档 §5.3.2 指出寄存器型预测器"次拍 resetDone 即置位（1-cycle 窗口）"。

### 6.1 `sramResetDone` 与 `resetDone` 的职责区分

当前 uRAS 代码中 `io.sramResetDone := true.B`：

```scala
// MicroRas.scala:64
  io.sramResetDone := true.B
```

且 uRAS 无 SRAM。根据 01 文档 §5.3.2 的明确区分：

- **`sramResetDone`**：用于**上电 SRAM 初始化完成**并门控 `s0_fire`：

```scala
// Bpu.scala:270-274
  when(predictors.map(_.io.sramResetDone).reduce(_ && _)) {
    sramResetDone := true.B
  }
  s0_fire := s1_ready && sramResetDone
  s1_fire := s1_valid && s2_ready && io.toFtq.prediction.ready
```

uRAS 无 SRAM，`sramResetDone` 应**保持 `true.B` 不变**，表示上电即就绪，与上下文刷新完全解耦。若将 `sramResetDone` 改为"等第一次 flush 后才 true"，则首次 flush 前 `sramResetDone` 为假，BPU 无法开始取指（`s0_fire` 被门控），自然也等不到后续 flush。`sramResetDone` 不受 `contextFlush` 影响，也不参与 BPU flush FSM 的上下文刷新完成聚合。

- **`resetDone`**：用于**上下文切换刷新完成握手**。这是 `BasePredictorIO` 中受 `HasBpuFlush` 控制的 `Option[Bool]` 输出，BPU 顶层对本事务未选中的预测器直接视为完成，对选中预测器的 `resetDone.get` 做与归约，并将聚合结果送入 `BpuFlushCtrl`，决定何时从 `s_flushing` 迁入 `s_done`。`resetDone` 不参与 `s0_fire` 门控。

### 6.2 `resetDone` 实现

uRAS 为寄存器型，1-cycle 清零完成。`resetDone` 的语义为：`contextFlush` 有效当拍为假（正在清零），其余时刻为真（清零完成或尚未触发刷新）。采用 `!contextFlush` 即可满足：

```scala
// MicroRas.scala：替换当前的 resetDone := true.B 占位
if (HasBpuFlush) {
  io.resetDone.get := !contextFlush // 寄存器型：contextFlush 当拍为 false，其余为 true
}
```

`BasePredictorIO` 中的声明为 `resetDone: Option[Bool] = Option.when(HasBpuFlush)(Output(Bool()))`。因此只有开关打开时才在 Scala `if` 内通过 `.get` 驱动；开关关闭时端口与上述赋值均不生成。

时序行为（假设 T 拍 `contextFlush` 为单拍脉冲）：

| 时间段 | `contextFlush` | `resetDone` | 含义 |
| :--- | :---: | :---: | :--- |
| T 拍 | true | false | contextFlush 当拍，清零正在执行 |
| T+1 拍起 | false | true | 清零已生效，向上层报告就绪 |
| 下次 T' 拍 | true | false | 新一次 flush，接口暂关 |
| T'+1 拍起 | false | true | 新一次清零生效，接口重新使能 |

### 6.3 `sramResetDone` 保持不变

```scala
// MicroRas.scala（保持不变）
io.sramResetDone := true.B   // uRAS 无 SRAM，上电即就绪；与上下文刷新解耦
```

### 6.4 BPU 顶层聚合

BPU flush FSM 等待的是本事务选中预测器的 `resetDone`，而非 `sramResetDone`：

```scala
// Bpu.scala：整段与 BpuFlushCtrl 实例一起位于 if (HasBpuFlush) 内
fc.io.resetDone := predictors.zipWithIndex.map { case (p, i) =>
  !fc.io.activeFlushMask(i) || p.io.resetDone.get
}.reduce(_ && _)
```

`HasBpuFlush=true` 时，uRAS 的 `io.resetDone.get` 自 T+1 拍起即为真，与 uBTB、RAS 同为寄存器型预测器中最快就绪的一档，不会成为聚合完成的瓶颈。又因 uRAS 与主 RAS 的清零窗口完全相同（均 1 cycle、同拍触发），二者的 `resetDone` 同拍就绪，无需相互等待。`HasBpuFlush=false` 时不存在该端口及聚合路径。`sramResetDone` 聚合（现有逻辑，门控 `s0_fire`）不受影响。
