# Commit Log
- Issue: #5680
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/5680
- Issue state: closed
- Tested RTL commit: -
- Related PR: #5680
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/5680
- Changed files: 2
- Additions: 31
- Deletions: 14

## Files
- `src/main/scala/xiangshan/frontend/bpu/Bpu.scala`
- `src/main/scala/xiangshan/frontend/bpu/ras/MicroRas.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/frontend/bpu/Bpu.scala b/src/main/scala/xiangshan/frontend/bpu/Bpu.scala
index 4218b92c03a..b5ae0c39107 100644
--- a/src/main/scala/xiangshan/frontend/bpu/Bpu.scala
+++ b/src/main/scala/xiangshan/frontend/bpu/Bpu.scala
@@ -210,12 +210,15 @@ class Bpu(implicit p: Parameters) extends BpuModule with HalfAlignHelper {
   utage.io.redirectValid          := redirect.valid
 
   // uras
-  uras.io.specIn.startPc     := s1_startPc
-  uras.io.specIn.cfiPosition := s1_prediction.cfiPosition
-  uras.io.specIn.attribute   := s1_prediction.attribute
-  uras.io.hasRedirect        := redirect.valid
-  uras.io.hasOverride        := s3_override
-  uras.io.fullRetAddr        := ras.io.topRetAddr
+  uras.io.specIn.startPc                := s1_startPc
+  uras.io.specIn.cfiPosition            := s1_prediction.cfiPosition
+  uras.io.specIn.attribute              := s1_prediction.attribute
+  uras.io.hasRedirect                   := redirect.valid
+  uras.io.overrideData.valid            := s3_override
+  uras.io.overrideData.bits.startPc     := s3_startPc.toUInt
+  uras.io.overrideData.bits.attribute   := s3_prediction.attribute
+  uras.io.overrideData.bits.cfiPosition := s3_prediction.cfiPosition
+  uras.io.fullRetAddr                   := ras.io.topRetAddr
 
   ras.io.redirect                := redirect
   ras.io.commit                  := commit
diff --git a/src/main/scala/xiangshan/frontend/bpu/ras/MicroRas.scala b/src/main/scala/xiangshan/frontend/bpu/ras/MicroRas.scala
index fc69c0c7f83..0df4f05f2ff 100644
--- a/src/main/scala/xiangshan/frontend/bpu/ras/MicroRas.scala
+++ b/src/main/scala/xiangshan/frontend/bpu/ras/MicroRas.scala
@@ -56,9 +56,9 @@ class MicroRas(implicit p: Parameters) extends BasePredictor with HasRasParamete
     }
     val specIn:      MicroRasSpecIn  = Input(new MicroRasSpecIn)
     val specOut:     MicroRasSpecOut = Output(new MicroRasSpecOut)
-    val hasRedirect: Bool            = Input(Bool())                // Global redirect signal (e.g., misprediction)
-    val hasOverride: Bool            = Input(Bool())                // S3-level override (flushes S1–S2)
-    val fullRetAddr: PrunedAddr      = Input(PrunedAddr(VAddrBits)) // Current top of the main RAS
+    val hasRedirect: Bool            = Input(Bool()) // Global redirect signal (e.g., misprediction)
+    val overrideData: Valid[MicroRasSpecIn] = Input(Valid(new MicroRasSpecIn)) // S3-level override (flushes S1–S2)
+    val fullRetAddr:  PrunedAddr            = Input(PrunedAddr(VAddrBits))     // Current top of the main RAS
   }
   val io = IO(new MicroRasIO)
   io.resetDone  := true.B
@@ -86,11 +86,16 @@ class MicroRas(implicit p: Parameters) extends BasePredictor with HasRasParamete
 
   // One-cycle delayed version of redirect; used to detect RAS recovery state
   private val redirectDelay1 = RegNext(io.hasRedirect, init = false.B)
+  private val hasOverride    = io.overrideData.valid
+  private val s3_realPush    = io.overrideData.bits.attribute.isCall
+  private val s3_realPop     = io.overrideData.bits.attribute.isReturn
+  private val s3_realPushAddr =
+    getCfiPcFromPosition(io.overrideData.bits.startPc, io.overrideData.bits.cfiPosition) + 2.U
 
   // -------------------------------
   // Pipeline control for S2 stage
   // -------------------------------
-  when(io.hasOverride || io.hasRedirect) {
+  when(hasOverride || io.hasRedirect) {
     s2_hasPush := false.B
     s2_hasPop  := false.B
   }.elsewhen(io.stageCtrl.s1_fire) {
@@ -104,7 +109,7 @@ class MicroRas(implicit p: Parameters) extends BasePredictor with HasRasParamete
   // -------------------------------
   // Pipeline control for S3 stage
   // -------------------------------
-  when(io.hasRedirect) {
+  when(hasOverride || io.hasRedirect) {
     // Redirect flushes entire pipeline, including S3
     s3_hasPush := false.B
     s3_hasPop  := false.B
@@ -136,14 +141,15 @@ class MicroRas(implicit p: Parameters) extends BasePredictor with HasRasParamete
     // On redirect, the main RAS state is being restored,
     // and the S1–S3 pipeline is flushed → no valid prediction.
     isCanUse := false.B
-  }.elsewhen(io.hasOverride) {
+  }.elsewhen(hasOverride) {
     // On S3 override (e.g., late redirect), S1–S2 are flushed.
     // Only S3’s operation affects the RAS state seen by the next S1:
     // - If S3 pops, the main RAS top changes → prediction invalid.
     // - If S3 pushes, the new top is s3_retAddr.
     // - If S3 does nothing, the top remains io.fullRetAddr (main RAS).
-    isCanUse   := Mux(s3_hasPop, false.B, Mux(s3_hasPush, true.B, true.B))
-    topRetAddr := Mux(s3_hasPop, 0.U.asTypeOf(PrunedAddr(VAddrBits)), Mux(s3_hasPush, s3_retAddr, io.fullRetAddr))
+    isCanUse := !s3_realPop // Without popping, stack top data can be fetched from s3 or Main RAS.
+    topRetAddr :=
+      Mux(s3_realPop, 0.U.asTypeOf(PrunedAddr(VAddrBits)), Mux(s3_realPush, s3_realPushAddr, io.fullRetAddr))
   }.elsewhen(io.stageCtrl.s1_fire && specPush) {
     // S1 sees a CALL → next cycle’s RAS top will be the return address of this CALL.
     isCanUse   := true.B
@@ -199,4 +205,12 @@ class MicroRas(implicit p: Parameters) extends BasePredictor with HasRasParamete
 
   io.specOut.isCanUse  := isCanUse
   io.specOut.retTarget := topRetAddr
+
+  private val s3_overrideHasRasAction     = hasOverride && (s3_realPush || s3_realPop)
+  private val s3_overrideHasRasActionNext = RegNext(s3_overrideHasRasAction, false.B)
+  XSPerfAccumulate("s3_override_has_rasAction", s3_overrideHasRasAction)
+  XSPerfAccumulate(
+    "s1_has_return_after_override_has_action",
+    s3_overrideHasRasActionNext && io.stageCtrl.s1_fire && specPop
+  )
 }
```
