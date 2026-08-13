# Commit Log
- Issue: #5568
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/5568
- Issue state: closed
- Tested RTL commit: -
- Related PR: #5568
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/5568
- Changed files: 3
- Additions: 10
- Deletions: 4

## Files
- `src/main/scala/xiangshan/frontend/Bundles.scala`
- `src/main/scala/xiangshan/frontend/bpu/Bpu.scala`
- `src/main/scala/xiangshan/frontend/bpu/Bundles.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/frontend/Bundles.scala b/src/main/scala/xiangshan/frontend/Bundles.scala
index 85954181098..7f504112ccf 100644
--- a/src/main/scala/xiangshan/frontend/Bundles.scala
+++ b/src/main/scala/xiangshan/frontend/Bundles.scala
@@ -36,6 +36,7 @@ import xiangshan.frontend.bpu.BpuRedirect
 import xiangshan.frontend.bpu.BpuTrain
 import xiangshan.frontend.bpu.BranchAttribute
 import xiangshan.frontend.bpu.BranchInfo
+import xiangshan.frontend.bpu.mbtb.MainBtbMeta
 import xiangshan.frontend.ibuffer.IBufPtr
 import xiangshan.frontend.icache.ICacheCacheLineHelper
 import xiangshan.frontend.icache.ICachePerfInfo
@@ -368,15 +369,18 @@ object BlameBpuSource {
 
   def apply(perf: BpuPerfMeta, branch: BranchInfo): UInt = {
     import BlameType.{BTB, TAGE, RAS, ITTAGE, SC}
-    val src  = perf.bpSource
-    val pred = perf.bpPred
-    val attr = branch.attribute
+    val src         = perf.bpSource
+    val pred        = perf.bpPred
+    val attr        = branch.attribute
+    val isHitInMbtb = perf.mbtbMeta.entries.flatten.map(_.hit(branch)).reduce(_ || _)
 
     // Check mispredict type
     val onlyDirectionWrong = branch.taken =/= pred.taken && branch.cfiPosition === pred.cfiPosition
     val blame              = WireInit(BTB) // Default to BTB
 
-    when(src.s3Ras) {
+    when(!isHitInMbtb) {
+      blame := BTB
+    }.elsewhen(src.s3Ras) {
       when(attr.isConditional) {
         // If cond before, TAGE mispredicts
         // If cond after, should trigger assertion, TODO
diff --git a/src/main/scala/xiangshan/frontend/bpu/Bpu.scala b/src/main/scala/xiangshan/frontend/bpu/Bpu.scala
index 649f16bb764..fc06e4acc49 100644
--- a/src/main/scala/xiangshan/frontend/bpu/Bpu.scala
+++ b/src/main/scala/xiangshan/frontend/bpu/Bpu.scala
@@ -524,6 +524,7 @@ class Bpu(implicit p: Parameters) extends BpuModule with HalfAlignHelper {
   s3_perfMeta.bpSource.s1Source   := s3_s1PredictionSource
   s3_perfMeta.bpSource.s3Source   := s3_predictionSource
   s3_perfMeta.bpSource.s3Override := s3_override
+  s3_perfMeta.mbtbMeta            := RegEnable(mbtb.io.meta, s2_fire)
 
   io.toFtq.perfMeta := s3_perfMeta
   // TODO: override reason and redirect reason
diff --git a/src/main/scala/xiangshan/frontend/bpu/Bundles.scala b/src/main/scala/xiangshan/frontend/bpu/Bundles.scala
index c3262012301..37b63e6797c 100644
--- a/src/main/scala/xiangshan/frontend/bpu/Bundles.scala
+++ b/src/main/scala/xiangshan/frontend/bpu/Bundles.scala
@@ -290,6 +290,7 @@ class BpuPerfMeta(implicit p: Parameters) extends BpuBundle {
   val startPc:      PrunedAddr          = new PrunedAddr(VAddrBits)
   val s1Prediction: Prediction          = new Prediction
   val s3Prediction: Prediction          = new Prediction
+  val mbtbMeta:     MainBtbMeta         = new MainBtbMeta
   val bpSource:     BpuPredictionSource = new BpuPredictionSource
 
   def bpPred: Prediction = Mux(bpSource.s3Override, s3Prediction, s1Prediction)
```
