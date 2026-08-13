# Commit Log
- Issue: #5332
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/5332
- Issue state: closed
- Tested RTL commit: -
- Related PR: #5332
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/5332
- Changed files: 1
- Additions: 13
- Deletions: 5

## Files
- `src/main/scala/xiangshan/frontend/bpu/Bpu.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/frontend/bpu/Bpu.scala b/src/main/scala/xiangshan/frontend/bpu/Bpu.scala
index bc890a455dc..bf81e5b14e5 100644
--- a/src/main/scala/xiangshan/frontend/bpu/Bpu.scala
+++ b/src/main/scala/xiangshan/frontend/bpu/Bpu.scala
@@ -500,11 +500,19 @@ class Bpu(implicit p: Parameters) extends BpuModule with HalfAlignHelper {
   // used for performance counters
   private val s3_condTakenMask = RegEnable(s2_condTakenMask, s2_fire)
   // see class BpuPredictionSource in bpu/Bundles.scala:137
-  private val s1_predictionSource = PriorityEncoder(Seq(
-    ubtb.io.prediction.taken, // Ubtb
-    abtb.io.prediction.taken, // Abtb
-    true.B                    // Fallthrough
-  ))
+  private val s1_predictionSource =
+    MuxCase(
+      BpuPredictionSource.Stage1.Fallthrough,
+      Seq(
+        (s1_realUbtbTaken && abtb.io.prediction.taken) -> Mux(
+          ubtb.io.prediction.cfiPosition <= abtb.io.prediction.cfiPosition,
+          BpuPredictionSource.Stage1.Ubtb,
+          BpuPredictionSource.Stage1.Abtb
+        ),
+        s1_realUbtbTaken         -> BpuPredictionSource.Stage1.Ubtb,
+        abtb.io.prediction.taken -> BpuPredictionSource.Stage1.Abtb
+      )
+    )
   private val s3_predictionSource = PriorityEncoder(Seq(
     false.B, // s3_taken && s3_firstTakenBranchIsReturn                    // RAS
     s3_taken && s3_firstTakenBranchIsIndirect && ittage.io.prediction.hit, // ITTage
```
