# Commit Log
- Issue: #5321
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/5321
- Issue state: closed
- Tested RTL commit: -
- Related PR: #5321
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/5321
- Changed files: 3
- Additions: 6
- Deletions: 7

## Files
- `src/main/scala/xiangshan/frontend/bpu/Bpu.scala`
- `src/main/scala/xiangshan/frontend/bpu/ras/Ras.scala`
- `src/main/scala/xiangshan/frontend/ftq/IfuRedirectReceiver.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/frontend/bpu/Bpu.scala b/src/main/scala/xiangshan/frontend/bpu/Bpu.scala
index bc890a455dc..917823abb11 100644
--- a/src/main/scala/xiangshan/frontend/bpu/Bpu.scala
+++ b/src/main/scala/xiangshan/frontend/bpu/Bpu.scala
@@ -95,7 +95,7 @@ class Bpu(implicit p: Parameters) extends BpuModule with HalfAlignHelper {
     tage.io.enable   := Mux(constCtrl(0), constCtrl(4), ctrl.tageEnable)
     sc.io.enable     := Mux(constCtrl(0), constCtrl(5), ctrl.scEnable)
     ittage.io.enable := Mux(constCtrl(0), constCtrl(6), ctrl.ittageEnable)
-    ras.io.enable    := Mux(constCtrl(0), constCtrl(7), false.B)
+    ras.io.enable    := Mux(constCtrl(0), constCtrl(7), ctrl.rasEnable)
   } else {
     ubtb.io.enable   := ctrl.ubtbEnable
     abtb.io.enable   := ctrl.abtbEnable
@@ -103,7 +103,7 @@ class Bpu(implicit p: Parameters) extends BpuModule with HalfAlignHelper {
     tage.io.enable   := ctrl.tageEnable
     sc.io.enable     := ctrl.scEnable
     ittage.io.enable := ctrl.ittageEnable
-    ras.io.enable    := false.B
+    ras.io.enable    := ctrl.rasEnable
   }
   // For some reason s0 stalled, usually FTQ Full
   private val s0_stall = Wire(Bool())
@@ -354,7 +354,7 @@ class Bpu(implicit p: Parameters) extends BpuModule with HalfAlignHelper {
     MuxCase(
       s3_fallThroughPrediction.target,
       Seq(
-//        (s3_taken && s3_firstTakenBranchIsReturn)                               -> ras.io.topRetAddr,
+        (s3_taken && s3_firstTakenBranchIsReturn)                               -> ras.io.topRetAddr,
         (s3_taken && s3_firstTakenBranchIsIndirect && ittage.io.prediction.hit) -> ittage.io.prediction.target,
         s3_taken                                                                -> s3_firstTakenBranch.bits.target
       )
diff --git a/src/main/scala/xiangshan/frontend/bpu/ras/Ras.scala b/src/main/scala/xiangshan/frontend/bpu/ras/Ras.scala
index 60744b4ef05..1922297c17b 100644
--- a/src/main/scala/xiangshan/frontend/bpu/ras/Ras.scala
+++ b/src/main/scala/xiangshan/frontend/bpu/ras/Ras.scala
@@ -64,7 +64,7 @@ class Ras(implicit p: Parameters) extends BasePredictor with HasRasParameters wi
 
   private val specIn       = io.specIn.bits
   private val specAlignPc  = specIn.startPc & alignMask
-  private val specPushAddr = specAlignPc + specIn.cfiPosition + 2.U
+  private val specPushAddr = specAlignPc + (specIn.cfiPosition << 1.U) + 2.U
   stack.spec.pushValid := specPush && !stackNearOverflow
   stack.spec.popValid  := specPop && !stackNearOverflow
 
diff --git a/src/main/scala/xiangshan/frontend/ftq/IfuRedirectReceiver.scala b/src/main/scala/xiangshan/frontend/ftq/IfuRedirectReceiver.scala
index 527b2d3dd50..bde969d329a 100644
--- a/src/main/scala/xiangshan/frontend/ftq/IfuRedirectReceiver.scala
+++ b/src/main/scala/xiangshan/frontend/ftq/IfuRedirectReceiver.scala
@@ -35,9 +35,8 @@ trait IfuRedirectReceiver extends HasFtqParameters {
     redirect.bits.isRVC     := wbRedirect.bits.isRVC
     redirect.bits.attribute := wbRedirect.bits.attribute
     redirect.bits.pc        := wbRedirect.bits.pc
-    // redirect.bits.target    := Mux(pdWb.bits.attribute.isReturn, specTopAddr, pdWb.bits.target.toUInt)
-    redirect.bits.target := wbRedirect.bits.target
-    redirect.bits.taken  := wbRedirect.bits.taken
+    redirect.bits.target    := Mux(wbRedirect.bits.attribute.isReturn, specTopAddr, wbRedirect.bits.target)
+    redirect.bits.taken     := wbRedirect.bits.taken
     redirect
   }
 }
```
