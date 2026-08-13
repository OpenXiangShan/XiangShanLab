# Commit Log
- Issue: #5952
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/5952
- Issue state: closed
- Tested RTL commit: -
- Related PR: #5952
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/5952
- Changed files: 4
- Additions: 27
- Deletions: 16

## Files
- `src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/TrapEntryDEvent.scala`
- `src/main/scala/xiangshan/backend/fu/NewCSR/DebugLevel.scala`
- `src/main/scala/xiangshan/backend/fu/NewCSR/InterruptFilter.scala`
- `src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/TrapEntryDEvent.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/TrapEntryDEvent.scala
index 36528441ba6..fea0102d3c5 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/TrapEntryDEvent.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/TrapEntryDEvent.scala
@@ -54,9 +54,9 @@ class TrapEntryDEventModule(implicit val p: Parameters) extends Module with CSRE
   private val isFetchMalAddr               = in.isFetchMalAddr
 
   private val hasExceptionInDmode = debugMode && hasTrap
-  val causeIntr = DcsrCause.Haltreq.asUInt
-  val causeExp = MuxCase(DcsrCause.None.asUInt, Seq(
+  val cause = MuxCase(DcsrCause.None.asUInt, Seq(
     criticalErrorStateEnterDebug -> DcsrCause.Other.asUInt,
+    hasDebugIntr                 -> DcsrCause.Haltreq.asUInt,
     triggerEnterDebugMode        -> DcsrCause.Trigger.asUInt,
     hasDebugEbreakException      -> DcsrCause.Ebreak.asUInt,
     hasSingleStep                -> DcsrCause.Step.asUInt
@@ -89,7 +89,7 @@ class TrapEntryDEventModule(implicit val p: Parameters) extends Module with CSRE
 
   out.dcsr.bits.V             := current.privState.V.asUInt
   out.dcsr.bits.PRV           := current.privState.PRVM.asUInt
-  out.dcsr.bits.CAUSE         := Mux(hasDebugIntr, causeIntr, causeExp)
+  out.dcsr.bits.CAUSE         := cause
   out.dpc.bits.epc            := Mux(isFetchMalAddr, in.fetchMalTval(63, 1), trapPC(63, 1))
 
   out.targetPc.bits.pc        := RegEnable(debugPc, valid || hasExceptionInDmode)
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/DebugLevel.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/DebugLevel.scala
index 0434309d27f..afbf2a37317 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/DebugLevel.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/DebugLevel.scala
@@ -57,9 +57,7 @@ trait DebugLevel { self: NewCSR =>
     .setAddr(CSRs.tinfo)
 
   val dcsr = Module(new CSRModule("Dcsr", new DcsrBundle) with TrapEntryDEventSinkBundle with DretEventSinkBundle with HasNmipBundle {
-    when(nmip){
-      reg.NMIP := nmip
-    }
+    regOut.NMIP := nmip
   })
     .setAddr(CSRs.dcsr)
 
@@ -292,12 +290,17 @@ class Tdata2Bundle extends CSRBundle {
 
 // Tinfo
 class TinfoBundle extends CSRBundle{
-  val VERSION     = RO(31, 24).withReset(0.U)
-    .withDescription("Trigger-information format version field. XiangShan reports version 0, matching the Debug Spec 0.13-style encoding.")
+  val VERSION     = TriggerVer(31, 24).withReset(TriggerVer.Spec_1dot0)
+    .withDescription("Trigger-information format version field. XiangShan reports version 1, matching the ratified Debug Spec 1.0 trigger encoding.")
   val MCONTROL6EN = RO(6).withReset(1.U)
     .withDescription("Indicates that the mcontrol6 trigger format is supported.")
 }
 
+object TriggerVer extends CSREnum with ROApply {
+  val Spec_2302  = Value(0.U)
+  val Spec_1dot0 = Value(1.U)
+}
+
 // Dscratch
 class DscratchBundle extends OneFieldBundle(Some("Debug scratch register."))
 
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/InterruptFilter.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/InterruptFilter.scala
index 9c2c99aca2c..50dfcc5bf16 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/InterruptFilter.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/InterruptFilter.scala
@@ -548,7 +548,7 @@ class InterruptFilter extends Module {
                          C1C5EnableReg && (iprioC1 === iprioC2C5 && !hvictlReg.DPR.asBool || iprioC1 > iprioC2C5)
   val viIsHvictlInjectReg = RegNext(vsIRModeCond && SelectCandidate5 && io.in.mnstatusNMIE, false.B)
 
-  io.out.interruptVec.valid := intrVecReg.orR || debugIntrReg || viIsHvictlInjectReg
+  io.out.interruptVec.valid := intrVecReg.orR || debugIntrReg
   io.out.interruptVec.bits := intrVecReg
   io.out.debug := debugIntrReg
   io.out.nmi := nmiReg
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala
index 5ae3a3b44ff..64d6fc7bd2a 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala
@@ -117,6 +117,7 @@ class NewCSR(implicit val p: Parameters) extends Module
   with CSRDocDump
   with HasCriticalErrors
   with IpIeAliasConnect
+  with DebugMMIO
 {
 
   import CSRConfig._
@@ -290,7 +291,9 @@ class NewCSR(implicit val p: Parameters) extends Module
   // error will result in an immediate re-entry into Debug Mode due to the critical error.
   // Ensure that dpc remains unchanged when criticalErrorState causes a re-entry into dmode,
   // since the PC fetched from pcmem for updating dpc is random in this case.
-  val holdDpc = RegEnable(criticalErrorState && dcsr.regOut.CETRIG, false.B, dretEvent.valid)
+  // This re-entry into Debug Mode will preempts the normal dret redirect.
+  val ceReEntryDmode = criticalErrorState && dcsr.regOut.CETRIG
+  val holdDpc = RegEnable(ceReEntryDmode, false.B, dretEvent.valid)
 
   private val privState = Wire(new PrivState)
   privState.PRVM := PRVM
@@ -1178,13 +1181,18 @@ class NewCSR(implicit val p: Parameters) extends Module
 
   private val xretTargetUpdate = mnretEvent.out.targetPc.valid || mretEvent.out.targetPc.valid || sretEvent.out.targetPc.valid || dretEvent.out.targetPc.valid
   io.xretTargetPc.valid := xretTargetUpdate
+  private val targetCeReEntryDmode = WireInit(0.U.asTypeOf(trapEntryDEvent.out.targetPc.bits))
+  targetCeReEntryDmode.pc := DebugEntry.U
+
   io.xretTargetPc.bits := DataHoldBypass(
-    Mux1H(Seq(
-      mnretEvent.out.targetPc.valid -> mnretEvent.out.targetPc.bits,
-      mretEvent.out.targetPc.valid  -> mretEvent.out.targetPc.bits,
-      sretEvent.out.targetPc.valid  -> sretEvent.out.targetPc.bits,
-      dretEvent.out.targetPc.valid  -> dretEvent.out.targetPc.bits,
-    )),
+    Mux(ceReEntryDmode,
+      targetCeReEntryDmode,
+      Mux1H(Seq(
+        mnretEvent.out.targetPc.valid -> mnretEvent.out.targetPc.bits,
+        mretEvent.out.targetPc.valid  -> mretEvent.out.targetPc.bits,
+        sretEvent.out.targetPc.valid  -> sretEvent.out.targetPc.bits,
+        dretEvent.out.targetPc.valid  -> dretEvent.out.targetPc.bits,
+      ))),
     xretTargetUpdate
   )
```
