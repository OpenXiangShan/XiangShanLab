# Commit Log
- Issue: #5405
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/5405
- Issue state: closed
- Tested RTL commit: -
- Related PR: #5405
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/5405
- Changed files: 8
- Additions: 53
- Deletions: 724

## Files
- `src/main/scala/xiangshan/Bundle.scala`
- `src/main/scala/xiangshan/backend/exu/ExeUnit.scala`
- `src/main/scala/xiangshan/backend/fu/FuncUnit.scala`
- `src/main/scala/xiangshan/backend/fu/FunctionUnit.scala`
- `src/main/scala/xiangshan/backend/fu/Radix2Divider.scala`
- `src/main/scala/xiangshan/backend/fu/SRT16Divider.scala`
- `src/main/scala/xiangshan/backend/fu/SRT4Divider.scala`
- `src/main/scala/xiangshan/backend/issue/WakeupQueue.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/Bundle.scala b/src/main/scala/xiangshan/Bundle.scala
index 7c2bda1bb1c..fe1e36e5f55 100644
--- a/src/main/scala/xiangshan/Bundle.scala
+++ b/src/main/scala/xiangshan/Bundle.scala
@@ -206,42 +206,6 @@ class LSIdx(implicit p: Parameters) extends XSBundle {
   val sqIdx = new SqPtr
 }
 
-// CfCtrl -> MicroOp at Rename Stage
-class MicroOp(implicit p: Parameters) extends CfCtrl {
-  val srcState = Vec(4, SrcState())
-  val psrc = Vec(4, UInt(PhyRegIdxWidth.W))
-  val pdest = UInt(PhyRegIdxWidth.W)
-  val robIdx = new RobPtr
-  val instrSize = UInt(log2Ceil(RenameWidth + 1).W)
-  val lqIdx = new LqPtr
-  val sqIdx = new SqPtr
-  val eliminatedMove = Bool()
-  val snapshot = Bool()
-  val debugInfo = new PerfDebugInfo
-  def needRfRPort(index: Int, isFp: Boolean, ignoreState: Boolean = true) : Bool = {
-    val stateReady = srcState(index) === SrcState.rdy || ignoreState.B
-    val readReg = if (isFp) {
-      ctrl.srcType(index) === SrcType.fp
-    } else {
-      ctrl.srcType(index) === SrcType.reg && ctrl.lsrc(index) =/= 0.U
-    }
-    readReg && stateReady
-  }
-  def srcIsReady: Vec[Bool] = {
-    VecInit(ctrl.srcType.zip(srcState).map{ case (t, s) => SrcType.isPcOrImm(t) || s === SrcState.rdy })
-  }
-  def clearExceptions(
-    exceptionBits: Seq[Int] = Seq(),
-    flushPipe: Boolean = false,
-    replayInst: Boolean = false
-  ): MicroOp = {
-    cf.exceptionVec.zipWithIndex.filterNot(x => exceptionBits.contains(x._2)).foreach(_._1 := false.B)
-    if (!flushPipe) { ctrl.flushPipe := false.B }
-    if (!replayInst) { ctrl.replayInst := false.B }
-    this
-  }
-}
-
 class XSBundleWithMicroOp(implicit p: Parameters) extends XSBundle {
   val uop = new DynInst
 }
diff --git a/src/main/scala/xiangshan/backend/exu/ExeUnit.scala b/src/main/scala/xiangshan/backend/exu/ExeUnit.scala
index 59205005580..bfe0b7076f5 100644
--- a/src/main/scala/xiangshan/backend/exu/ExeUnit.scala
+++ b/src/main/scala/xiangshan/backend/exu/ExeUnit.scala
@@ -30,7 +30,6 @@ import xiangshan.backend.fu.vector.Bundles.{VType, Vxrm}
 import xiangshan.backend.fu.fpu.Bundles.Frm
 import xiangshan.backend.fu.wrapper.{CSRInput, CSRToDecode}
 import xiangshan.backend.fu.FuConfig.{AluCfg, I2fCfg, needUncertainWakeupFuConfigs}
-import xiangshan.backend.issue.WakeupQueue
 
 class ExeUnitIO(params: ExeUnitParams)(implicit p: Parameters) extends XSBundle {
   val flush = Flipped(ValidIO(new Redirect()))
diff --git a/src/main/scala/xiangshan/backend/fu/FuncUnit.scala b/src/main/scala/xiangshan/backend/fu/FuncUnit.scala
index 3ab70f59133..ea51bd84e73 100644
--- a/src/main/scala/xiangshan/backend/fu/FuncUnit.scala
+++ b/src/main/scala/xiangshan/backend/fu/FuncUnit.scala
@@ -16,6 +16,35 @@ import xiangshan.backend.fu.vector.Bundles.VType
 import xiangshan.backend.fu.wrapper.{CSRInput, CSRToDecode}
 import xiangshan.frontend.bpu.{BranchInfo, BranchAttribute}
 
+trait HasFuLatency {
+  val latencyVal: Option[Int]
+  val extraLatencyVal: Option[Int]
+  val uncertainLatencyVal: Option[Int]
+  val uncertainEnable: Option[Int]
+  val orginLatencyVal: Option[Int]
+}
+
+case class CertainLatency(value: Int, extraValue: Int = 0) extends HasFuLatency {
+  override val latencyVal: Option[Int] = Some(value + extraValue)
+  override val extraLatencyVal: Option[Int] = Some(extraValue)
+  override val uncertainLatencyVal: Option[Int] = None
+  override val uncertainEnable: Option[Int] = None
+  override val orginLatencyVal: Option[Int] = Some(value)
+}
+
+case class UncertainLatency(value: Option[Int]) extends HasFuLatency {
+  override val latencyVal: Option[Int] = None
+  override val extraLatencyVal: Option[Int] = None
+  override val uncertainLatencyVal: Option[Int] = value
+  override val uncertainEnable: Option[Int] = Some(0) // for gate uncertain fu
+  override val orginLatencyVal: Option[Int] = None
+}
+
+object UncertainLatency {
+  def apply(): UncertainLatency = UncertainLatency(None)
+  def apply(value: Int): UncertainLatency = UncertainLatency(Some(value))
+}
+
 class FuncUnitCtrlInput(cfg: FuConfig)(implicit p: Parameters) extends XSBundle {
   val fuOpType    = FuOpType()
   val robIdx      = new RobPtr
diff --git a/src/main/scala/xiangshan/backend/fu/FunctionUnit.scala b/src/main/scala/xiangshan/backend/fu/FunctionUnit.scala
index 41c0bc73825..165ccae9897 100644
--- a/src/main/scala/xiangshan/backend/fu/FunctionUnit.scala
+++ b/src/main/scala/xiangshan/backend/fu/FunctionUnit.scala
@@ -16,67 +16,3 @@
 
 package xiangshan.backend.fu
 
-import org.chipsalliance.cde.config.Parameters
-import chisel3._
-import chisel3.util._
-import utility.XSPerfAccumulate
-import xiangshan._
-import xiangshan.backend.fu.fpu._
-
-trait HasFuLatency {
-  val latencyVal: Option[Int]
-  val extraLatencyVal: Option[Int]
-  val uncertainLatencyVal: Option[Int]
-  val uncertainEnable: Option[Int]
-  val orginLatencyVal: Option[Int]
-}
-
-case class CertainLatency(value: Int, extraValue: Int = 0) extends HasFuLatency {
-  override val latencyVal: Option[Int] = Some(value + extraValue)
-  override val extraLatencyVal: Option[Int] = Some(extraValue)
-  override val uncertainLatencyVal: Option[Int] = None
-  override val uncertainEnable: Option[Int] = None
-  override val orginLatencyVal: Option[Int] = Some(value)
-}
-
-case class UncertainLatency(value: Option[Int]) extends HasFuLatency {
-  override val latencyVal: Option[Int] = None
-  override val extraLatencyVal: Option[Int] = None
-  override val uncertainLatencyVal: Option[Int] = value
-  override val uncertainEnable: Option[Int] = Some(0) // for gate uncertain fu
-  override val orginLatencyVal: Option[Int] = None
-}
-
-object UncertainLatency {
-  def apply(): UncertainLatency = UncertainLatency(None)
-  def apply(value: Int): UncertainLatency = UncertainLatency(Some(value))
-}
-
-class FuOutput(val len: Int)(implicit p: Parameters) extends XSBundle {
-  val data = UInt(len.W)
-  val uop = new MicroOp
-}
-
-class FunctionUnitInput(val len: Int)(implicit p: Parameters) extends XSBundle {
-  val src = Vec(3, UInt(len.W))
-  val uop = new MicroOp
-}
-
-class FunctionUnitIO(val len: Int)(implicit p: Parameters) extends XSBundle {
-  val in = Flipped(DecoupledIO(new FunctionUnitInput(len)))
-
-  val out = DecoupledIO(new FuOutput(len))
-
-  val redirectIn = Flipped(ValidIO(new Redirect))
-}
-
-abstract class FunctionUnit(len: Int = 64)(implicit p: Parameters) extends XSModule {
-
-  val io = IO(new FunctionUnitIO(len))
-
-  XSPerfAccumulate("in_valid", io.in.valid)
-  XSPerfAccumulate("in_fire", io.in.fire)
-  XSPerfAccumulate("out_valid", io.out.valid)
-  XSPerfAccumulate("out_fire", io.out.fire)
-
-}
diff --git a/src/main/scala/xiangshan/backend/fu/Radix2Divider.scala b/src/main/scala/xiangshan/backend/fu/Radix2Divider.scala
deleted file mode 100644
index 6c445ac2b9d..00000000000
--- a/src/main/scala/xiangshan/backend/fu/Radix2Divider.scala
+++ /dev/null
@@ -1,108 +0,0 @@
-/***************************************************************************************
-* Copyright (c) 2020-2021 Institute of Computing Technology, Chinese Academy of Sciences
-* Copyright (c) 2020-2021 Peng Cheng Laboratory
-*
-* XiangShan is licensed under Mulan PSL v2.
-* You can use this software according to the terms and conditions of the Mulan PSL v2.
-* You may obtain a copy of Mulan PSL v2 at:
-*          http://license.coscl.org.cn/MulanPSL2
-*
-* THIS SOFTWARE IS PROVIDED ON AN "AS IS" BASIS, WITHOUT WARRANTIES OF ANY KIND,
-* EITHER EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO NON-INFRINGEMENT,
-* MERCHANTABILITY OR FIT FOR A PARTICULAR PURPOSE.
-*
-* See the Mulan PSL v2 for more details.
-***************************************************************************************/
-
-package xiangshan.backend.fu
-
-import org.chipsalliance.cde.config.Parameters
-import chisel3._
-import chisel3.util._
-import xiangshan._
-import utils._
-import utility._
-
-abstract class AbstractDivider(len: Int)(implicit p: Parameters) extends FunctionUnit(len){
-  val ctrl = IO(Input(new MulDivCtrl))
-  val sign = ctrl.sign
-}
-
-class Radix2Divider(len: Int)(implicit p: Parameters) extends AbstractDivider(len) {
-
-  def abs(a: UInt, sign: Bool): (Bool, UInt) = {
-    val s = a(len - 1) && sign
-    (s, Mux(s, -a, a))
-  }
-
-  val s_idle :: s_log2 :: s_shift :: s_compute :: s_finish :: Nil = Enum(5)
-  val state = RegInit(s_idle)
-  val newReq = (state === s_idle) && io.in.fire
-
-  val (a, b) = (io.in.bits.src(0), io.in.bits.src(1))
-  val divBy0 = b === 0.U(len.W)
-  val divBy0Reg = RegEnable(divBy0, newReq)
-
-  val shiftReg = Reg(UInt((1 + len * 2).W))
-  val hi = shiftReg(len * 2, len)
-  val lo = shiftReg(len - 1, 0)
-
-  val uop = io.in.bits.uop
-
-  val (aSign, aVal) = abs(a, sign)
-  val (bSign, bVal) = abs(b, sign)
-  val aSignReg = RegEnable(aSign, newReq)
-  val qSignReg = RegEnable((aSign ^ bSign) && !divBy0, newReq)
-  val bReg = RegEnable(bVal, newReq)
-  val aValx2Reg = RegEnable(Cat(aVal, "b0".U), newReq)
-  val ctrlReg = RegEnable(ctrl, newReq)
-  val uopReg = RegEnable(uop, newReq)
-
-  val cnt = Counter(len)
-  when (newReq && !io.in.bits.uop.robIdx.needFlush(io.redirectIn)) {
-    state := s_log2
-  } .elsewhen (state === s_log2) {
-    // `canSkipShift` is calculated as following:
-    //   bEffectiveBit = Log2(bVal, XLEN) + 1.U
-    //   aLeadingZero = 64.U - aEffectiveBit = 64.U - (Log2(aVal, XLEN) + 1.U)
-    //   canSkipShift = aLeadingZero + bEffectiveBit
-    //     = 64.U - (Log2(aVal, XLEN) + 1.U) + Log2(bVal, XLEN) + 1.U
-    //     = 64.U + Log2(bVal, XLEN) - Log2(aVal, XLEN)
-    //     = (64.U | Log2(bVal, XLEN)) - Log2(aVal, XLEN)  // since Log2(bVal, XLEN) < 64.U
-    val canSkipShift = (64.U | Log2(bReg)) - Log2(aValx2Reg)
-    // When divide by 0, the quotient should be all 1's.
-    // Therefore we can not shift in 0s here.
-    // We do not skip any shift to avoid this.
-    cnt.value := Mux(divBy0Reg, 0.U, Mux(canSkipShift >= (len-1).U, (len-1).U, canSkipShift))
-    state := s_shift
-  } .elsewhen (state === s_shift) {
-    shiftReg := aValx2Reg << cnt.value
-    state := s_compute
-  } .elsewhen (state === s_compute) {
-    val enough = hi.asUInt >= bReg.asUInt
-    shiftReg := Cat(Mux(enough, hi - bReg, hi)(len - 1, 0), lo, enough)
-    cnt.inc()
-    when (cnt.value === (len-1).U) { state := s_finish }
-  } .elsewhen (state === s_finish) {
-    when(io.out.ready){
-      state := s_idle
-    }
-  }
-
-  val kill = state=/=s_idle && uopReg.robIdx.needFlush(io.redirectIn)
-  when(kill){
-    state := s_idle
-  }
-
-  val r = hi(len, 1)
-  val resQ = Mux(qSignReg, -lo, lo)
-  val resR = Mux(aSignReg, -r, r)
-
-  val xlen = io.out.bits.data.getWidth
-  val res = Mux(ctrlReg.isHi, resR, resQ)
-  io.out.bits.data := Mux(ctrlReg.isW, SignExt(res(31,0),xlen), res)
-  io.out.bits.uop := uopReg
-
-  io.out.valid := state === s_finish
-  io.in.ready := state === s_idle
-}
diff --git a/src/main/scala/xiangshan/backend/fu/SRT16Divider.scala b/src/main/scala/xiangshan/backend/fu/SRT16Divider.scala
index bca6a7eaf65..6a3316567ae 100644
--- a/src/main/scala/xiangshan/backend/fu/SRT16Divider.scala
+++ b/src/main/scala/xiangshan/backend/fu/SRT16Divider.scala
@@ -451,3 +451,27 @@ object mLookUpTable2 {
       7.U -> "b10_10010".U(7.W)
     ))
 }
+
+class RightShifter(len: Int, lzc_width: Int) extends Module {
+  val io = IO(new Bundle() {
+    val shiftNum = Input(UInt(lzc_width.W))
+    val in = Input(UInt(len.W))
+    val msb = Input(Bool())
+    val out = Output(UInt(len.W))
+  })
+  require(len == 64 || len == 32)
+  val shift = io.shiftNum
+  val msb = io.msb
+  val s0 = Mux(shift(0), Cat(VecInit(Seq.fill(1)(msb)).asUInt, io.in(len - 1, 1)), io.in)
+  val s1 = Mux(shift(1), Cat(VecInit(Seq.fill(2)(msb)).asUInt, s0(len - 1, 2)), s0)
+  val s2 = Mux(shift(2), Cat(VecInit(Seq.fill(4)(msb)).asUInt, s1(len - 1, 4)), s1)
+  val s3 = Mux(shift(3), Cat(VecInit(Seq.fill(8)(msb)).asUInt, s2(len - 1, 8)), s2)
+  val s4 = Mux(shift(4), Cat(VecInit(Seq.fill(16)(msb)).asUInt, s3(len - 1, 16)), s3)
+  val s5 = Wire(UInt(len.W))
+  if (len == 64) {
+    s5 := Mux(shift(5), Cat(VecInit(Seq.fill(32)(msb)).asUInt, s4(len - 1, 32)), s4)
+  } else if (len == 32) {
+    s5 := s4
+  }
+  io.out := s5
+}
diff --git a/src/main/scala/xiangshan/backend/fu/SRT4Divider.scala b/src/main/scala/xiangshan/backend/fu/SRT4Divider.scala
deleted file mode 100644
index f7e715237f2..00000000000
--- a/src/main/scala/xiangshan/backend/fu/SRT4Divider.scala
+++ /dev/null
@@ -1,458 +0,0 @@
-/***************************************************************************************
-* Copyright (c) 2024 Beijing Institute of Open Source Chip (BOSC)
-* Copyright (c) 2020-2024 Institute of Computing Technology, Chinese Academy of Sciences
-* Copyright (c) 2020-2021 Peng Cheng Laboratory
-*
-* XiangShan is licensed under Mulan PSL v2.
-* You can use this software according to the terms and conditions of the Mulan PSL v2.
-* You may obtain a copy of Mulan PSL v2 at:
-*          http://license.coscl.org.cn/MulanPSL2
-*
-* THIS SOFTWARE IS PROVIDED ON AN "AS IS" BASIS, WITHOUT WARRANTIES OF ANY KIND,
-* EITHER EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO NON-INFRINGEMENT,
-* MERCHANTABILITY OR FIT FOR A PARTICULAR PURPOSE.
-*
-* See the Mulan PSL v2 for more details.
-***************************************************************************************/
-
-// The "SRT4DividerDataModule" in this file is a scala rewrite of SRT4 divider by Yifei He, see
-// https://github.com/OpenXiangShan/XS-Verilog-Library/tree/main/int_div_radix_4_v1
-// Email of original author: hyf_sysu@qq.com
-
-package xiangshan.backend.fu
-
-import org.chipsalliance.cde.config.Parameters
-import chisel3._
-import chisel3.util._
-import utility.SignExt
-import xiangshan.backend.fu.util.CSA3_2
-
-/** A Radix-4 SRT Integer Divider
-  *
-  * 2 ~ (5 + (len+3)/2) cycles are needed for each division.
-  */
-class SRT4DividerDataModule(len: Int) extends Module {
-  val io = IO(new Bundle() {
-    val src = Vec(2, Input(UInt(len.W)))
-    val valid, sign, kill_w, kill_r, isHi, isW = Input(Bool())
-    val in_ready = Output(Bool())
-    val out_valid = Output(Bool())
-    val out_data = Output(UInt(len.W))
-    val out_ready = Input(Bool())
-  })
-
-  // consts
-  val lzc_width = log2Up(len)
-  val itn_len = 1 + len + 2 + 1
-  require(lzc_width == 6)
-
-  val (a, d, sign, valid, kill_w, kill_r, isHi, isW) =
-    (io.src(0), io.src(1), io.sign, io.valid, io.kill_w, io.kill_r, io.isHi, io.isW)
-  val in_fire = valid && io.in_ready
-  val out_fire = io.out_ready && io.out_valid
-  val newReq = in_fire
-  val startHandShake = io.in_ready && valid
-  val s_idle :: s_pre_0 :: s_pre_1 :: s_iter :: s_post_0 :: s_post_1 :: s_finish :: Nil = Enum(7)
-
-  val state = RegInit(UIntToOH(s_idle, 7))
-
-  val quot_neg_2 :: quot_neg_1 :: quot_0 :: quot_pos_1 :: quot_pos_2 :: Nil = Enum(5)
-
-  val finished = state(s_finish)
-
-  // reused wire declarations
-  val aIsZero = Wire(Bool())
-  val dIsZero = Wire(Bool())
-  val aTooSmall = Wire(Bool()) // this is output of reg!
-  val noIter = Wire(Bool()) // this is output of reg!
-  val finalIter = Wire(Bool())
-  val aLZC = Wire(UInt((lzc_width + 1).W))
-  val dLZC = Wire(UInt((lzc_width + 1).W))
-  val aNormAbs = Wire(UInt((len + 1).W))
-  val dNormAbs = Wire(UInt((len + 1).W))
-  val aInverter = Wire(UInt(len.W)) // results of global inverter
-  val dInverter = Wire(UInt(len.W))
-
-  val rPreShifted = Wire(UInt((len + 1).W))
-
-  val quotIter = Wire(UInt(len.W))
-  val quotM1Iter = Wire(UInt(len.W))
-  val qIterEnd = Wire(UInt(5.W))
-
-  val rNext = Wire(UInt(itn_len.W))
-  val rNextPd = Wire(UInt(itn_len.W)) // non-redundant remainder plus d, 68, 67
-  //reused ctrl regs
-
-  //reused other regs
-  val aNormAbsReg = RegEnable(aNormAbs, startHandShake | state(s_pre_0) | state(s_post_0)) // reg for normalized a & d and rem & rem+d
-  val dNormAbsReg = RegEnable(dNormAbs, startHandShake | state(s_pre_0) | state(s_post_0))
-  val quotIterReg = RegEnable(quotIter, state(s_pre_1) | state(s_iter) | state(s_post_0))
-  val quotM1IterReg = RegEnable(quotM1Iter, state(s_pre_1) | state(s_iter) | state(s_post_0))
-
-  when(kill_r) {
-    state := UIntToOH(s_idle, 7)
-  } .elsewhen(state(s_idle) && in_fire && !kill_w) {
-    state := UIntToOH(s_pre_0, 7)
-  } .elsewhen(state(s_pre_0)) { // leading zero detection
-    state := UIntToOH(s_pre_1, 7)
-  } .elsewhen(state(s_pre_1)) { // shift a/b
-    state := Mux(dIsZero | aTooSmall | noIter, UIntToOH(s_post_0, 7), UIntToOH(s_iter, 7))
-  } .elsewhen(state(s_iter)) { // (ws[j+1], wc[j+1]) = 4(ws[j],wc[j]) - q(j+1)*d
-    state := Mux(finalIter, UIntToOH(s_post_0, 7), UIntToOH(s_iter, 7))
-  } .elsewhen(state(s_post_0)) { // if rem < 0, rem = rem + d
-    state := UIntToOH(s_post_1, 7)
-  } .elsewhen(state(s_post_1)) {
-    state := UIntToOH(s_finish, 7)
-  } .elsewhen(state(s_finish) && out_fire) {
-    state := UIntToOH(s_idle, 7)
-  } .otherwise {
-    state := state
-  }
-
-  // First cycle:
-  // State is idle, we gain absolute value of a and b, using global inverter
-
-  io.in_ready := state(s_idle)
-
-  aInverter := -Mux(state(s_idle), a, quotIterReg) // 64, 0
-  dInverter := -Mux(state(s_idle), d, quotM1IterReg) // 64, 0
-
-  val aSign = io.sign && a(len - 1) // 1
-  val dSign = io.sign && d(len - 1)
-
-  val aAbs = Mux(aSign, aInverter, a) // 64, 0
-  val dAbs = Mux(dSign, dInverter, d)
-  val aNorm = (aNormAbsReg(len - 1, 0) << aLZC(lzc_width - 1, 0))(len - 1, 0) // 64, 65
-  val dNorm = (dNormAbsReg(len - 1, 0) << dLZC(lzc_width - 1, 0))(len - 1, 0)
-
-  aNormAbs := Mux1H(Seq(
-    state(s_idle) -> Cat(0.U(1.W), aAbs), // 65, 0
-    state(s_pre_0) -> Cat(0.U(1.W), aNorm), // 65, 0
-    state(s_post_0) -> rNext(len + 3, 3) // remainder 65, 64. highest is sign bit
-  ))
-  dNormAbs := Mux1H(Seq(
-    state(s_idle) -> Cat(0.U(1.W), dAbs),
-    state(s_pre_0) -> Cat(0.U(1.W), dNorm),
-    state(s_post_0) -> rNextPd(len + 3, 3)
-    ))
-
-  // Second cycle, state is pre_0
-  // calculate lzc and move div* and lzc diff check if no_iter_needed
-
-  aLZC := PriorityEncoder(aNormAbsReg(len - 1, 0).asBools.reverse)
-  dLZC := PriorityEncoder(dNormAbsReg(len - 1, 0).asBools.reverse)
-  val aLZCReg = RegEnable(aLZC, state(s_pre_0)) // 7, 0
-  val dLZCReg = RegEnable(dLZC, state(s_pre_0))
-
-
-
-  val lzcWireDiff = Cat(0.U(1.W), dLZC(lzc_width - 1, 0)) - Cat(0.U(1.W), aLZC(lzc_width - 1, 0)) // 7, 0
-  val lzcRegDiff = Cat(0.U(1.W), dLZCReg(lzc_width - 1, 0)) - Cat(0.U(1.W), aLZCReg(lzc_width - 1, 0))
-  val lzcDiff = Mux(state(s_pre_0), lzcWireDiff, lzcRegDiff)
-  aIsZero := aLZC(lzc_width) // this is state pre_0
-  dIsZero := dLZCReg(lzc_width) // this is pre_1 and all stages after
-  val dIsOne = dLZC(lzc_width - 1, 0).andR // this is pre_0
-  val noIterReg = RegEnable(dIsOne & aNormAbsReg(len - 1), state(s_pre_0)) // This means dividend has lzc 0 so iter is 17
-  noIter := noIterReg
-  val aTooSmallReg = RegEnable(aIsZero | lzcDiff(lzc_width), state(s_pre_0)) // a is zero or a smaller than d
-  aTooSmall := aTooSmallReg
-
-  val quotSign = Mux(state(s_idle), aSign ^ dSign, true.B) // if not s_idle then must be s_pre_1 & dIsZero, and that we have
-  val rSign = aSign
-  val quotSignReg = RegEnable(quotSign, startHandShake | (state(s_pre_1) & dIsZero))
-  val rSignReg = RegEnable(rSign, startHandShake)
-
-  val rShift = lzcDiff(0) // odd lzc diff, for SRT4
-  val rightShifted = Wire(UInt(len.W))
-  val rSumInit = Mux(aTooSmallReg | aIsZero, Cat(0.U(1.W), rightShifted, 0.U(3.W)), // right shift the dividend (which is already l-shifted)
-                    Mux(noIterReg, 0.U(itn_len.W), //
-                      Cat(0.U(3.W),
-                          Mux(rShift, Cat(0.U(1.W), aNormAbsReg(len - 1, 0)), Cat(aNormAbsReg(len - 1, 0), 0.U(1.W)))
-                        ) // Normal init value. 68, 67; For even lzcDiff, 0.001xxx0; for odd lzcDiff 0.0001xxx
-                      )
-                    ) // state is s_pre_1
-  val rCarryInit = 0.U(itn_len.W)
-
-  val rightShifter = Module(new RightShifter(len, lzc_width))
-  rightShifter.io.in := Mux(state(s_pre_1), aNormAbsReg(len - 1, 0), rPreShifted(len - 1, 0))
-  rightShifter.io.shiftNum := Mux(state(s_pre_1), aLZCReg,
-                                  Mux(aTooSmallReg | dIsZero, 0.U(lzc_width.W), dLZCReg))
-  rightShifter.io.msb := state(s_post_1) & rSignReg & rPreShifted(len)
-  rightShifted := rightShifter.io.out
-
-  // obtaining 1st quotient
-  val rSumInitTrunc = Cat(0.U(1.W), rSumInit(itn_len - 4, itn_len - 4 - 4 + 1)) // 0.00___
-  val mInitPos1 = MuxLookup(dNormAbsReg(len - 2, len - 2 - 3 + 1), "b00100".U(5.W))(
-    Seq(
-      0.U -> "b00100".U(5.W),
-      1.U -> "b00100".U(5.W),
-      2.U -> "b00100".U(5.W),
-      3.U -> "b00110".U(5.W),
-      4.U -> "b00110".U(5.W),
-      5.U -> "b00110".U(5.W),
-      6.U -> "b00110".U(5.W),
-      7.U -> "b01000".U(5.W),
-    )
-  )
-  val mInitPos2 = MuxLookup(dNormAbsReg(len - 2, len - 2 - 3 + 1), "b01100".U(5.W))(
-    Seq(
-      0.U -> "b01100".U(5.W),
-      1.U -> "b01110".U(5.W),
-      2.U -> "b01111".U(5.W),
-      3.U -> "b10000".U(5.W),
-      4.U -> "b10010".U(5.W),
-      5.U -> "b10100".U(5.W),
-      6.U -> "b10110".U(5.W),
-      7.U -> "b10110".U(5.W),
-    )
-  )
-  val initCmpPos1 = rSumInitTrunc >= mInitPos1
-  val initCmpPos2 = rSumInitTrunc >= mInitPos2
-  val qInit = Mux(initCmpPos2, UIntToOH(quot_pos_2, 5), Mux(initCmpPos1, UIntToOH(quot_pos_1, 5), UIntToOH(quot_0, 5)))
-  val qPrev = Mux(state(s_pre_1), qInit, qIterEnd)
-  val qPrevReg = RegEnable(qPrev, state(s_pre_1) | state(s_iter))
-  val specialDivisorReg = RegEnable(dNormAbsReg(len - 2, len - 2 - 3 + 1) === 0.U, state(s_pre_1)) // d=0.1000xxx
-  // rCarry and rSum in Iteration
-  val qXd = Mux1H(Seq(
-    qPrevReg(quot_neg_2) -> Cat(dNormAbsReg(len - 1, 0), 0.U(4.W)), // 68, 67 1.xxxxx0000
-    qPrevReg(quot_neg_1) -> Cat(0.U(1.W), dNormAbsReg(len - 1, 0), 0.U(3.W)), // 0.1xxxxx000
-    qPrevReg(quot_0)     -> 0.U(itn_len.W),
-    qPrevReg(quot_pos_1) -> ~Cat(0.U(1.W), dNormAbsReg(len - 1, 0), 0.U(3.W)), // don't forget to plus 1 later
-    qPrevReg(quot_pos_2) -> ~Cat(dNormAbsReg(len - 1, 0), 0.U(4.W))  // don't forget to plus 1 later
-  ))
-  val csa = Module(new CSA3_2(itn_len))
-
-  val rSumIter = csa.io.out(0)
-  val rCarryIter = Cat(csa.io.out(1)(itn_len - 2, 0), qPrevReg(quot_pos_1) | qPrevReg(quot_pos_2))
-  val rSumReg = RegEnable(Mux(state(s_pre_1), rSumInit, rSumIter), state(s_pre_1) | state(s_iter)) // 68, 67
-  val rCarryReg = RegEnable(Mux(state(s_pre_1), rCarryInit, rCarryIter), state(s_pre_1) | state(s_iter))
-  csa.io.in(0) := rSumReg << 2
-  csa.io.in(1) := rCarryReg << 2
-  csa.io.in(2) := qXd
-
-  val qds = Module(new SRT4QDS(len, itn_len))
-  qds.io.remSum := rSumReg
-  qds.io.remCarry := rCarryReg
-  qds.io.d := dNormAbsReg(len - 1, 0) // Maybe optimize here to lower power consumption?
-  qds.io.specialDivisor := specialDivisorReg
-  qds.io.qPrev := qPrevReg
-  qIterEnd := qds.io.qIterEnd
-
-  //on the fly conversion
-  val quotIterNext = Wire(UInt(len.W))
-  val quotIterM1Next = Wire(UInt(len.W))
-  quotIterNext := Mux1H(Seq(
-    qPrevReg(quot_pos_2) -> (quotIterReg << 2 | "b10".U),
-    qPrevReg(quot_pos_1) -> (quotIterReg << 2 | "b01".U),
-    qPrevReg(quot_0)     -> (quotIterReg << 2 | "b00".U),
-    qPrevReg(quot_neg_1) -> (quotM1IterReg << 2 | "b11".U),
-    qPrevReg(quot_neg_2) -> (quotM1IterReg << 2 | "b10".U)
-  ))
-  quotIterM1Next := Mux1H(Seq(
-    qPrevReg(quot_pos_2) -> (quotIterReg << 2 | "b01".U),
-    qPrevReg(quot_pos_1) -> (quotIterReg << 2 | "b00".U),
-    qPrevReg(quot_0)     -> (quotM1IterReg << 2 | "b11".U),
-    qPrevReg(quot_neg_1) -> (quotM1IterReg << 2 | "b10".U),
-    qPrevReg(quot_neg_2) -> (quotM1IterReg << 2 | "b01".U)
-  ))
-
-
-  quotIter := Mux(state(s_pre_1),
-                      Mux(dIsZero, VecInit(Seq.fill(len)(true.B)).asUInt,
-                        Mux(noIterReg, aNormAbsReg(len - 1, 0), 0.U(len.W))),
-                      Mux(state(s_iter), quotIterNext,
-                        Mux(quotSignReg, aInverter, quotIterReg)))
-  quotM1Iter := Mux(state(s_pre_1),
-                        0.U(len.W), Mux(state(s_iter), quotIterM1Next,
-                          Mux(quotSignReg, dInverter, quotM1IterReg)))
-
-
-  // iter num
-  val iterNum = Wire(UInt((lzc_width - 1).W))
-  val iterNumReg = RegEnable(iterNum, state(s_pre_1) | state(s_iter))
-
-  iterNum := Mux(state(s_pre_1), lzcDiff(lzc_width - 1, 1) +% lzcDiff(0), iterNumReg -% 1.U)
-  finalIter := iterNumReg === 0.U
-
-  // Post Process
-
-  when(rSignReg) {
-    rNext := ~rSumReg + ~rCarryReg + 2.U
-    rNextPd := ~rSumReg + ~rCarryReg + ~Cat(0.U(1.W), dNormAbsReg(len - 1, 0), 0.U(3.W)) + 3.U
-  } .otherwise {
-    rNext := rSumReg + rCarryReg
-    rNextPd := rSumReg + rCarryReg + Cat(0.U(1.W), dNormAbsReg(len - 1, 0), 0.U(3.W))
-  }
-
-  val r = aNormAbsReg
-  val rPd = dNormAbsReg
-  val rIsZero = ~(r.orR)
-  val needCorr = (~dIsZero & ~noIterReg) & Mux(rSignReg, ~r(len) & ~rIsZero, r(len)) // when we get pos rem for d<0 or neg rem for d>0
-  rPreShifted := Mux(needCorr, rPd, r)
-  val rFinal = RegEnable(rightShifted, state(s_post_1))// right shifted remainder. shift by the number of bits divisor is shifted
-  val qFinal = Mux(needCorr, quotM1IterReg, quotIterReg)
-  val res = Mux(isHi, rFinal, qFinal)
-  io.out_data := Mux(isW,
-    SignExt(res(31, 0), len),
-    res
-  )
-  io.in_ready := state(s_idle)
-  io.out_valid := state(s_finish) // state === s_finish
-}
-
-class RightShifter(len: Int, lzc_width: Int) extends Module {
-  val io = IO(new Bundle() {
-    val shiftNum = Input(UInt(lzc_width.W))
-    val in = Input(UInt(len.W))
-    val msb = Input(Bool())
-    val out = Output(UInt(len.W))
-  })
-  require(len == 64 || len == 32)
-  val shift = io.shiftNum
-  val msb = io.msb
-  val s0 = Mux(shift(0), Cat(VecInit(Seq.fill(1)(msb)).asUInt, io.in(len - 1, 1)), io.in)
-  val s1 = Mux(shift(1), Cat(VecInit(Seq.fill(2)(msb)).asUInt, s0(len - 1, 2)), s0)
-  val s2 = Mux(shift(2), Cat(VecInit(Seq.fill(4)(msb)).asUInt, s1(len - 1, 4)), s1)
-  val s3 = Mux(shift(3), Cat(VecInit(Seq.fill(8)(msb)).asUInt, s2(len - 1, 8)), s2)
-  val s4 = Mux(shift(4), Cat(VecInit(Seq.fill(16)(msb)).asUInt, s3(len - 1, 16)), s3)
-  val s5 = Wire(UInt(len.W))
-  if (len == 64) {
-    s5 := Mux(shift(5), Cat(VecInit(Seq.fill(32)(msb)).asUInt, s4(len - 1, 32)), s4)
-  } else if (len == 32) {
-    s5 := s4
-  }
-  io.out := s5
-}
-
-object mLookUpTable {
-  // Usage :
-  // result := decoder(QMCMinimizer, index, mLookupTable.xxx)
-  val minus_m = Seq(
-    Seq( // -m[-1]
-      0.U -> "b00_11010".U,
-      1.U -> "b00_11110".U,
-      2.U -> "b01_00000".U,
-      3.U -> "b01_00100".U,
-      4.U -> "b01_00110".U,
-      5.U -> "b01_01010".U,
-      6.U -> "b01_01100".U,
-      7.U -> "b01_10000".U
-    ),
-    Seq( // -m[0]
-      0.U -> "b000_0101".U,
-      1.U -> "b000_0110".U,
-      2.U -> "b000_0110".U,
-      3.U -> "b000_0110".U,
-      4.U -> "b000_1001".U,
-      5.U -> "b000_1000".U,
-      6.U -> "b000_1000".U,
-      7.U -> "b000_1000".U
-    ),
-    Seq( //-m[1]
-      0.U -> "b111_1101".U,
-      1.U -> "b111_1100".U,
-      2.U -> "b111_1100".U,
-      3.U -> "b111_1100".U,
-      4.U -> "b111_1011".U,
-      5.U -> "b111_1010".U,
-      6.U -> "b111_1010".U,
-      7.U -> "b111_1010".U
-    ),
-    Seq( //-m[2]
-      0.U -> "b11_01000".U,
-      1.U -> "b11_00100".U,
-      2.U -> "b11_00010".U,
-      3.U -> "b10_11110".U,
-      4.U -> "b10_11100".U,
-      5.U -> "b10_11000".U,
-      6.U -> "b10_10110".U,
-      7.U -> "b10_10010".U
-    ))
-}
-
-class SRT4QDS(len: Int, itn_len: Int) extends Module {
-  // srt4 quotientr digit selection
-  val io = IO(new Bundle() {
-    val remSum = Input(UInt(itn_len.W)) // 68, 67
-    val remCarry = Input(UInt(itn_len.W))
-    val d = Input(UInt(len.W)) // 64, 64
-    val specialDivisor = Input(Bool())
-    val qPrev = Input(UInt(5.W))
-    val qIterEnd = Output(UInt(5.W))
-  })
-  val remSumX16 = io.remSum << 4 // 72, 67 Top 2 bits unused
-  val remCarryX16 = io.remCarry << 4
-  def trunc25(rem: UInt): UInt = {rem(itn_len, itn_len - 7 + 1)}
-  def trunc34(rem: UInt): UInt = {rem(itn_len + 1, itn_len + 1 - 7 + 1)}
-
-  val quot_neg_2 :: quot_neg_1 :: quot_0 :: quot_pos_1 :: quot_pos_2 :: Nil = Enum(5)
-
-  val d = Cat(0.U(1.W), io.d, 0.U(3.W)) // 68, 67
-  val (dX4, dX8, dXNeg4, dXNeg8) = (d << 2, d(itn_len - 2, 0) << 3, ~(d << 2), ~(d(itn_len - 2, 0) << 3)) // 70, 67
-  val dForLookup = io.d(len - 2, len - 2 - 3 + 1)
-
-  val dXq = Mux1H(Seq(
-    io.qPrev(quot_neg_2) -> dX8,
-    io.qPrev(quot_neg_1) -> dX4,
-    io.qPrev(quot_0) -> 0.U((itn_len + 2).W),
-    io.qPrev(quot_pos_1) -> dXNeg4,
-    io.qPrev(quot_pos_2) -> dXNeg8
-  ))
-  val signs = VecInit(Seq.tabulate(4){ // -1 0 1 2
-    i => {
-      val csa1 = Module(new CSA3_2(7))
-      val csa2 = Module(new CSA3_2(7))
-      if (i == 1 || i == 2) {
-        csa1.io.in(0) := trunc34(remSumX16)
-        csa1.io.in(1) := trunc34(remCarryX16)
-        csa2.io.in(2) := trunc34(dXq)
-      } else {
-        csa1.io.in(0) := trunc25(remSumX16)
-        csa1.io.in(1) := trunc25(remCarryX16)
-        csa2.io.in(2) := trunc25(dXq)
-      }
-      csa1.io.in(2) := MuxLookup(dForLookup, "b0000000".U)(mLookUpTable.minus_m(i))
-      csa2.io.in(0) := csa1.io.out(0)
-      csa2.io.in(1) := csa1.io.out(1)(5, 0) << 1
-      (csa2.io.out(0) + (csa2.io.out(1)(5, 0) << 1))(6)
-    }
-  })
-  val qVec = Wire(Vec(5, Bool()))
-  qVec(quot_neg_2) := signs(0) && signs(1) && signs(2)
-  qVec(quot_neg_1) := ~signs(0) && signs(1) && signs(2)
-  qVec(quot_0) := signs(2) && ~signs(1)
-  qVec(quot_pos_1) := signs(3) && ~signs(2) && ~signs(1)
-  qVec(quot_pos_2) := ~signs(3) && ~signs(2) && ~signs(1)
-  io.qIterEnd := qVec.asUInt
-  // assert(PopCount(qVec) === 1.U)
-}
-
-
-class SRT4Divider(len: Int)(implicit p: Parameters) extends AbstractDivider(len) {
-
-  val newReq = io.in.fire
-
-  val uop = io.in.bits.uop
-  val uopReg = RegEnable(uop, newReq)
-  val ctrlReg = RegEnable(ctrl, newReq)
-
-  val divDataModule = Module(new SRT4DividerDataModule(len))
-
-  val kill_w = uop.robIdx.needFlush(io.redirectIn)
-  val kill_r = !divDataModule.io.in_ready && uopReg.robIdx.needFlush(io.redirectIn)
-
-  divDataModule.io.src(0) := io.in.bits.src(0)
-  divDataModule.io.src(1) := io.in.bits.src(1)
-  divDataModule.io.valid := io.in.valid
-  divDataModule.io.sign := sign
-  divDataModule.io.kill_w := kill_w
-  divDataModule.io.kill_r := kill_r
-  divDataModule.io.isHi := ctrlReg.isHi
-  divDataModule.io.isW := ctrlReg.isW
-  divDataModule.io.out_ready := io.out.ready
-
-  io.in.ready := divDataModule.io.in_ready
-  io.out.valid := divDataModule.io.out_valid
-  io.out.bits.data := divDataModule.io.out_data
-  io.out.bits.uop := uopReg
-}
diff --git a/src/main/scala/xiangshan/backend/issue/WakeupQueue.scala b/src/main/scala/xiangshan/backend/issue/WakeupQueue.scala
deleted file mode 100644
index 0827b4f5397..00000000000
--- a/src/main/scala/xiangshan/backend/issue/WakeupQueue.scala
+++ /dev/null
@@ -1,57 +0,0 @@
-/***************************************************************************************
-* Copyright (c) 2020-2021 Institute of Computing Technology, Chinese Academy of Sciences
-* Copyright (c) 2020-2021 Peng Cheng Laboratory
-*
-* XiangShan is licensed under Mulan PSL v2.
-* You can use this software according to the terms and conditions of the Mulan PSL v2.
-* You may obtain a copy of Mulan PSL v2 at:
-*          http://license.coscl.org.cn/MulanPSL2
-*
-* THIS SOFTWARE IS PROVIDED ON AN "AS IS" BASIS, WITHOUT WARRANTIES OF ANY KIND,
-* EITHER EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO NON-INFRINGEMENT,
-* MERCHANTABILITY OR FIT FOR A PARTICULAR PURPOSE.
-*
-* See the Mulan PSL v2 for more details.
-***************************************************************************************/
-
-package xiangshan.backend.issue
-
-import org.chipsalliance.cde.config.Parameters
-import chisel3._
-import chisel3.util._
-import xiangshan._
-import utils._
-import utility._
-
-class WakeupQueue(number: Int)(implicit p: Parameters) extends XSModule {
-  val io = IO(new Bundle {
-    val in  = Flipped(ValidIO(new MicroOp))
-    val out = ValidIO(new MicroOp)
-    val redirect = Flipped(ValidIO(new Redirect))
-  })
-  if (number < 0) {
-    io.out.valid := false.B
-    io.out.bits := DontCare
-  } else if(number == 0) {
-    io.in <> io.out
-    io.out.valid := io.in.valid
-    // NOTE: no delay bypass don't care redirect
-  } else {
-    val queue = Seq.fill(number)(RegInit(0.U.asTypeOf(new Bundle{
-      val valid = Bool()
-      val bits = new MicroOp
-    })))
-    queue(0).valid := io.in.valid && !io.in.bits.robIdx.needFlush(io.redirect)
-    queue(0).bits  := io.in.bits
-    (0 until (number-1)).map{i =>
-      queue(i+1) := queue(i)
-      queue(i+1).valid := queue(i).valid && !queue(i).bits.robIdx.needFlush(io.redirect)
-    }
-    io.out.valid := queue(number-1).valid
-    io.out.bits := queue(number-1).bits
-    for (i <- 0 until number) {
-      XSDebug(queue(i).valid, p"BPQue(${i.U}): pc:${Hexadecimal(queue(i).bits.cf.pc)} robIdx:${queue(i).bits.robIdx}" +
-        p" pdest:${queue(i).bits.pdest} rfWen:${queue(i).bits.ctrl.rfWen} fpWen:${queue(i).bits.ctrl.fpWen} vecWen:${queue(i).bits.ctrl.vecWen}\n")
-    }
-  }
-}
```
