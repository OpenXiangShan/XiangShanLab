# Commit Log
- Issue: #3496
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/3496
- Issue state: closed
- Tested RTL commit: -
- Related PR: #3496
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/3496
- Changed files: 7
- Additions: 37
- Deletions: 11

## Files
- `difftest`
- `src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/CSREvent.scala`
- `src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/TrapEntryVSEvent.scala`
- `src/main/scala/xiangshan/backend/fu/NewCSR/InterruptBundle.scala`
- `src/main/scala/xiangshan/backend/fu/NewCSR/InterruptFilter.scala`
- `src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala`
- `src/main/scala/xiangshan/backend/fu/NewCSR/TrapHandleModule.scala`

## Diff
```diff
diff --git a/difftest b/difftest
index c66788be320..8aff29bc32f 160000
--- a/difftest
+++ b/difftest
@@ -1 +1 @@
-Subproject commit c66788be3206b685e9241a6fa526bcc2e294438a
+Subproject commit 8aff29bc32fab3e21590896d4ab35c6f8ca92424
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/CSREvent.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/CSREvent.scala
index a1a52ce2ac8..9d6b0d5a76d 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/CSREvent.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/CSREvent.scala
@@ -134,4 +134,6 @@ class TrapEntryEventInput(implicit val p: Parameters) extends Bundle with HasXSP
   // from mem
   val memExceptionVAddr = Input(UInt(VAddrBits.W))
   val memExceptionGPAddr = Input(UInt(GPAddrBits.W))
+  val virtualInterruptIsHvictlInject = Input(Bool())
+  val hvictlIID = Input(UInt(HIIDWidth.W))
 }
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/TrapEntryVSEvent.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/TrapEntryVSEvent.scala
index 7eefeef89a0..0be004a03dc 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/TrapEntryVSEvent.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/TrapEntryVSEvent.scala
@@ -47,10 +47,12 @@ class TrapEntryVSEventModule(implicit val p: Parameters) extends Module with CSR
   private val trapCode = in.causeNO.ExceptionCode.asUInt
   private val isException = !in.causeNO.Interrupt.asBool
   private val isInterrupt = in.causeNO.Interrupt.asBool
+  private val virtualInterruptIsHvictlInject = in.virtualInterruptIsHvictlInject
+  private val hvictlIID = in.hvictlIID
 
-  when(valid && isInterrupt) {
+  when(valid && isInterrupt && !virtualInterruptIsHvictlInject) {
     assert(
-      (InterruptNO.getVS ++ InterruptNO.getHS).map(_.U === trapCode).reduce(_ || _),
+      (InterruptNO.getVS ++ InterruptNO.getLocal).map(_.U === trapCode).reduce(_ || _),
       "The VS mode can only handle VSEI, VSTI, VSSI and local interrupts"
     )
   }
@@ -125,7 +127,7 @@ class TrapEntryVSEventModule(implicit val p: Parameters) extends Module with CSR
   // SPVP is not PrivMode enum type, so asUInt and shrink the width
   out.vsepc.bits.epc             := trapPC(63, 1)
   out.vscause.bits.Interrupt     := isInterrupt
-  out.vscause.bits.ExceptionCode := highPrioTrapNO
+  out.vscause.bits.ExceptionCode := Mux(virtualInterruptIsHvictlInject, hvictlIID, highPrioTrapNO)
   out.vstval.bits.ALL            := tval
   out.targetPc.bits              := in.pcFromXtvec
 
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/InterruptBundle.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/InterruptBundle.scala
index 14704fc9d06..a688446995b 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/InterruptBundle.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/InterruptBundle.scala
@@ -386,6 +386,10 @@ object InterruptNO {
     SEI, VSEI, MEI,
     SGEI
   )
+
+  def getLocal = localHighGroup ++ localLowGroup ++
+                 customHighestGroup ++ customMiddleHighGroup ++
+                 customMiddleLowGroup ++ customLowestGroup ++ Seq(COI)
 }
 
 trait HasIpIeBundle { self: CSRModule[_] =>
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/InterruptFilter.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/InterruptFilter.scala
index 86476daa368..d1742d2ccf8 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/InterruptFilter.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/InterruptFilter.scala
@@ -300,18 +300,26 @@ class InterruptFilter extends Module {
 
   val normalIntrVec = mIRVec | hsIRVec | vsMapHostIRVec | debugInterupt
   val intrVec = VecInit(Mux(io.in.nmi, io.in.nmiVec, normalIntrVec).asBools.map(IR => IR && !disableInterrupt)).asUInt
+
+  // virtual interrupt with hvictl injection
+  val vsIRModeCond = privState.isModeVS && vsstatusSIE || privState < PrivState.ModeVS
+  val SelectCandidate5 = Candidate123LowCandidate45 && Candidate5
   // delay at least 6 cycles to maintain the atomic of sret/mret
   // 65bit indict current interrupt is NMI
   val intrVecReg = RegInit(0.U(64.W))
   val nmiReg = RegInit(false.B)
+  val viIsHvictlInjectReg = RegInit(false.B)
   intrVecReg := intrVec
   nmiReg := io.in.nmi
+  viIsHvictlInjectReg := vsIRModeCond && SelectCandidate5
   val delayedIntrVec = DelayN(intrVecReg, 5)
   val delayedNMI = DelayN(nmiReg, 5)
+  val delayedVIIsHvictlInjectReg = DelayN(viIsHvictlInjectReg, 5)
 
-  io.out.interruptVec.valid := delayedIntrVec.orR
+  io.out.interruptVec.valid := delayedIntrVec.orR || delayedVIIsHvictlInjectReg
   io.out.interruptVec.bits := delayedIntrVec
   io.out.nmi := delayedNMI
+  io.out.virtualInterruptIsHvictlInject := delayedVIIsHvictlInjectReg & !delayedNMI
 
   dontTouch(hsip)
   dontTouch(hsie)
@@ -361,5 +369,6 @@ class InterruptFilterIO extends Bundle {
     val mtopi  = new TopIBundle
     val stopi  = new TopIBundle
     val vstopi = new TopIBundle
+    val virtualInterruptIsHvictlInject = Bool()
   })
 }
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala
index 92806fc3988..33d75880cb3 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala
@@ -313,6 +313,7 @@ class NewCSR(implicit val p: Parameters) extends Module
   }
   val intrVec = RegEnable(intrMod.io.out.interruptVec.bits, 0.U, intrMod.io.out.interruptVec.valid)
   val nmi = RegEnable(intrMod.io.out.nmi, false.B, intrMod.io.out.interruptVec.valid)
+  val virtualInterruptIsHvictlInject = RegEnable(intrMod.io.out.virtualInterruptIsHvictlInject, false.B, intrMod.io.out.interruptVec.valid)
 
   val trapHandleMod = Module(new TrapHandleModule)
 
@@ -331,6 +332,7 @@ class NewCSR(implicit val p: Parameters) extends Module
   trapHandleMod.io.in.mtvec := mtvec.regOut
   trapHandleMod.io.in.stvec := stvec.regOut
   trapHandleMod.io.in.vstvec := vstvec.regOut
+  trapHandleMod.io.in.virtualInterruptIsHvictlInject := virtualInterruptIsHvictlInject
 
   val entryPrivState = trapHandleMod.io.out.entryPrivState
   val entryDebugMode = WireInit(false.B)
@@ -622,10 +624,10 @@ class NewCSR(implicit val p: Parameters) extends Module
     println(mod.dumpFields)
   }
   
-  trapEntryMEvent .valid  := hasTrap && entryPrivState.isModeM && !entryDebugMode  && !debugMode && !nmi
-  trapEntryMNEvent .valid := hasTrap && nmi && !debugMode
-  trapEntryHSEvent.valid  := hasTrap && entryPrivState.isModeHS && !entryDebugMode && !debugMode
-  trapEntryVSEvent.valid  := hasTrap && entryPrivState.isModeVS && !entryDebugMode && !debugMode
+  trapEntryMEvent.valid  := hasTrap && entryPrivState.isModeM && !entryDebugMode  && !debugMode && !nmi
+  trapEntryMNEvent.valid := hasTrap && nmi && !debugMode
+  trapEntryHSEvent.valid := hasTrap && entryPrivState.isModeHS && !entryDebugMode && !debugMode
+  trapEntryVSEvent.valid := hasTrap && entryPrivState.isModeVS && !entryDebugMode && !debugMode
 
   Seq(trapEntryMEvent, trapEntryMNEvent, trapEntryHSEvent, trapEntryVSEvent, trapEntryDEvent).foreach { eMod =>
     eMod.in match {
@@ -657,6 +659,9 @@ class NewCSR(implicit val p: Parameters) extends Module
 
         in.memExceptionVAddr := io.fromMem.excpVA
         in.memExceptionGPAddr := io.fromMem.excpGPA
+
+        in.virtualInterruptIsHvictlInject := virtualInterruptIsHvictlInject
+        in.hvictlIID := hvictl.regOut.IID.asUInt
     }
   }
 
@@ -1138,7 +1143,7 @@ class NewCSR(implicit val p: Parameters) extends Module
   if (env.AlwaysBasicDiff || env.EnableDifftest) {
     val hartId = io.fromTop.hartId
     val trapValid = io.fromRob.trap.valid
-    val trapNO = trapHandleMod.io.out.causeNO.ExceptionCode.asUInt
+    val trapNO = Mux(virtualInterruptIsHvictlInject && hasTrap, hvictl.regOut.IID.asUInt, trapHandleMod.io.out.causeNO.ExceptionCode.asUInt)
     val interrupt = trapHandleMod.io.out.causeNO.Interrupt.asBool
     val hasNMI = nmi && hasTrap
     val interruptNO = Mux(interrupt, trapNO, 0.U)
@@ -1167,6 +1172,7 @@ class NewCSR(implicit val p: Parameters) extends Module
     diffArchEvent.exception := exceptionNO
     diffArchEvent.exceptionPC := exceptionPC
     diffArchEvent.hasNMI := hasNMI
+    diffArchEvent.virtualInterruptIsHvictlInject := virtualInterruptIsHvictlInject && hasTrap
     if (env.EnableDifftest) {
       diffArchEvent.exceptionInst := io.fromRob.trap.bits.instr
     }
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/TrapHandleModule.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/TrapHandleModule.scala
index c8ef673e32b..ac431ce859e 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/TrapHandleModule.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/TrapHandleModule.scala
@@ -19,6 +19,7 @@ class TrapHandleModule extends Module {
   private val hedeleg = io.in.hedeleg.asUInt
   private val mvien = io.in.mvien.asUInt
   private val hvien = io.in.hvien.asUInt
+  private val virtualInterruptIsHvictlInject = io.in.virtualInterruptIsHvictlInject
 
   private val hasTrap = trapInfo.valid
   private val hasNMI = hasTrap && trapInfo.bits.nmi
@@ -97,7 +98,7 @@ class TrapHandleModule extends Module {
   // nmi handle in MMode only and default handler is mtvec
   private val  mHasIR =  mIRVec.orR
   private val hsHasIR = hsIRVec.orR & !hasNMI
-  private val vsHasIR = vsIRVec.orR & !hasNMI
+  private val vsHasIR = (vsIRVec.orR || hasIR && virtualInterruptIsHvictlInject) & !hasNMI
 
   private val  mHasEX =  mEXVec.orR
   private val hsHasEX = hsEXVec.orR
@@ -182,6 +183,8 @@ class TrapHandleIO extends Bundle {
     val mtvec = Input(new XtvecBundle)
     val stvec = Input(new XtvecBundle)
     val vstvec = Input(new XtvecBundle)
+    // virtual interrupt is hvictl inject
+    val virtualInterruptIsHvictlInject = Input(Bool())
   })
 
   val out = new Bundle {
```
