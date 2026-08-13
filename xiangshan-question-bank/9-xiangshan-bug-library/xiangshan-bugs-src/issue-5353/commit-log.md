# Commit Log
- Issue: #5353
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/5353
- Issue state: closed
- Tested RTL commit: -
- Related PR: #5353
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/5353
- Changed files: 2
- Additions: 8
- Deletions: 8

## Files
- `src/main/scala/xiangshan/frontend/bpu/Bpu.scala`
- `src/main/scala/xiangshan/frontend/bpu/abtb/AheadBtb.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/frontend/bpu/Bpu.scala b/src/main/scala/xiangshan/frontend/bpu/Bpu.scala
index baeb6c95459..1d1e34f6c3a 100644
--- a/src/main/scala/xiangshan/frontend/bpu/Bpu.scala
+++ b/src/main/scala/xiangshan/frontend/bpu/Bpu.scala
@@ -518,7 +518,7 @@ class Bpu(implicit p: Parameters) extends BpuModule with HalfAlignHelper {
       )
     )
   private val s3_predictionSource = PriorityEncoder(Seq(
-    false.B, // s3_taken && s3_firstTakenBranchIsReturn                    // RAS
+    s3_taken && s3_firstTakenBranchIsReturn,                               // RAS
     s3_taken && s3_firstTakenBranchNeedIttage && ittage.io.prediction.hit, // ITTage
     s3_taken && s3_firstTakenBranch.bits.attribute.isConditional,          // MbtbTage
     s3_taken,                                                              // Mbtb
diff --git a/src/main/scala/xiangshan/frontend/bpu/abtb/AheadBtb.scala b/src/main/scala/xiangshan/frontend/bpu/abtb/AheadBtb.scala
index b361dc614cc..a0bc9315096 100644
--- a/src/main/scala/xiangshan/frontend/bpu/abtb/AheadBtb.scala
+++ b/src/main/scala/xiangshan/frontend/bpu/abtb/AheadBtb.scala
@@ -391,13 +391,13 @@ class AheadBtb(implicit p: Parameters) extends BasePredictor with Helpers {
     t1_predictTargetLowerBits === t1_trainTargetLowerBits
 
   XSPerfAccumulate("predict_req_num", predictReqValid)
-  XSPerfAccumulate("predict_num", s2_valid)
-  XSPerfAccumulate("predict_hit", s2_valid && s2_hit)
-  XSPerfAccumulate("predict_miss", s2_valid && !s2_hit)
-  XSPerfAccumulate("predict_hit_entry_num", Mux(s2_valid, PopCount(s2_hitMask), 0.U))
-  XSPerfAccumulate("predict_taken", s2_valid && s2_taken)
-  XSPerfAccumulate("predict_not_taken", s2_valid && s2_hit && !s2_taken)
-  XSPerfAccumulate("predict_multi_hit", s2_valid && s2_multiHit)
+  XSPerfAccumulate("predict_num", s2_fire)
+  XSPerfAccumulate("predict_hit", s2_fire && s2_hit)
+  XSPerfAccumulate("predict_miss", s2_fire && !s2_hit)
+  XSPerfAccumulate("predict_hit_entry_num", Mux(s2_fire, PopCount(s2_hitMask), 0.U))
+  XSPerfAccumulate("predict_taken", s2_fire && s2_taken)
+  XSPerfAccumulate("predict_not_taken", s2_fire && s2_hit && !s2_taken)
+  XSPerfAccumulate("predict_multi_hit", s2_fire && s2_multiHit)
 
   XSPerfAccumulate("train_req_num", io.fastTrain.get.valid)
   XSPerfAccumulate("train_num", t1_valid)
```
