# Commit Log
- Issue: #5740
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/5740
- Issue state: closed
- Tested RTL commit: -
- Related PR: #5740
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/5740
- Changed files: 6
- Additions: 34
- Deletions: 29

## Files
- `src/main/scala/xiangshan/frontend/bpu/Bpu.scala`
- `src/main/scala/xiangshan/frontend/bpu/mbtb/MainBtbAlignBank.scala`
- `src/main/scala/xiangshan/frontend/bpu/sc/Sc.scala`
- `src/main/scala/xiangshan/frontend/bpu/utage/MicroTage.scala`
- `src/main/scala/xiangshan/frontend/ftq/Ftq.scala`
- `utility`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/frontend/bpu/Bpu.scala b/src/main/scala/xiangshan/frontend/bpu/Bpu.scala
index 41f7fcdcc7e..92ac36f443c 100644
--- a/src/main/scala/xiangshan/frontend/bpu/Bpu.scala
+++ b/src/main/scala/xiangshan/frontend/bpu/Bpu.scala
@@ -24,6 +24,7 @@ import utility.DelayN
 import utility.XSError
 import utility.XSPerfAccumulate
 import utility.XSPerfHistogram
+import utility.XSPerfSeqAccumulate
 import xiangshan.frontend.BpuToFtqIO
 import xiangshan.frontend.FrontendTopDownBundle
 import xiangshan.frontend.FtqToBpuIO
@@ -599,7 +600,7 @@ class Bpu(implicit p: Parameters) extends BpuModule with HalfAlignHelper {
     0,
     FetchBlockInstNum + 1
   )
-  XSPerfAccumulate(
+  XSPerfSeqAccumulate(
     "s1_use",
     io.toFtq.prediction.fire,
     Seq(
@@ -614,13 +615,13 @@ class Bpu(implicit p: Parameters) extends BpuModule with HalfAlignHelper {
   XSPerfAccumulate("s3_use_ittage", s3_fire && s3_taken && !s3_useRas && s3_useIttage)
   XSPerfAccumulate("s3_use_mbtb_tage", s3_fire && s3_prediction.attribute.isConditional)
 
-  XSPerfAccumulate(
+  XSPerfSeqAccumulate(
     "finalPred_s1",
     s3_fire && !s3_override,
     BpuPredictionSource.Stage1.getValidSeq(s3_perfMeta.bpSource.s1Source)
   )
 
-  XSPerfAccumulate(
+  XSPerfSeqAccumulate(
     "finalPred_s3",
     s3_fire && s3_override,
     BpuPredictionSource.Stage3.getValidSeq(s3_perfMeta.bpSource.s3Source)
@@ -640,20 +641,20 @@ class Bpu(implicit p: Parameters) extends BpuModule with HalfAlignHelper {
     thisPrefix = "s3"
   )
 
-  XSPerfAccumulate(
+  XSPerfSeqAccumulate(
     s"s3Override_takenMismatch_s1fall",
     io.toFtq.prediction.fire && s3_override && s3_perfMeta.bpSource.s1Fallthrough,
     perf_s3TakenSourceVec
   )
 
-  XSPerfAccumulate(
+  XSPerfSeqAccumulate(
     s"s3Override_takenMismatch_s3fall",
     io.toFtq.prediction.fire && s3_override && s3_perfMeta.bpSource.s3Fallthrough,
     perf_s1TakenSourceVec
   )
 
   // position mismatch
-  XSPerfAccumulate(
+  XSPerfSeqAccumulate(
     s"s3Override_positionMismatch",
     io.toFtq.prediction.fire && s3_override &&
       s3_prediction.taken && s3_s1Prediction.taken &&
@@ -662,7 +663,7 @@ class Bpu(implicit p: Parameters) extends BpuModule with HalfAlignHelper {
   )
 
   // attribute mismatch
-  XSPerfAccumulate(
+  XSPerfSeqAccumulate(
     s"s3Override_attributeMismatch",
     io.toFtq.prediction.fire && s3_override &&
       s3_prediction.taken && s3_s1Prediction.taken &&
@@ -680,7 +681,7 @@ class Bpu(implicit p: Parameters) extends BpuModule with HalfAlignHelper {
     thisPrefix = "s3"
   )
 
-  XSPerfAccumulate(
+  XSPerfSeqAccumulate(
     s"s3Override_targetMismatch",
     io.toFtq.prediction.fire && s3_override &&
       s3_prediction.taken && s3_s1Prediction.taken &&
@@ -695,7 +696,7 @@ class Bpu(implicit p: Parameters) extends BpuModule with HalfAlignHelper {
   private val t0_branches         = train.branches
   private val t0_mbtbHit          = t0_mbtbMeta.entries.flatten.map(_.hit(t0_mispredictBranch.bits)).reduce(_ || _)
 
-  XSPerfAccumulate(
+  XSPerfSeqAccumulate(
     "train",
     io.fromFtq.train.fire,
     Seq(
@@ -703,7 +704,7 @@ class Bpu(implicit p: Parameters) extends BpuModule with HalfAlignHelper {
       ("stall", !io.fromFtq.train.ready)
     )
   )
-  XSPerfAccumulate(
+  XSPerfSeqAccumulate(
     "train_branch",
     io.fromFtq.train.fire,
     Seq(
@@ -715,7 +716,7 @@ class Bpu(implicit p: Parameters) extends BpuModule with HalfAlignHelper {
       ("conditional", true.B, PopCount(t0_branches.map(b => b.valid && b.bits.attribute.isConditional)))
     )
   )
-  XSPerfAccumulate(
+  XSPerfSeqAccumulate(
     "train_mispredict",
     io.fromFtq.train.fire && t0_mispredictBranch.valid,
     Seq(
diff --git a/src/main/scala/xiangshan/frontend/bpu/mbtb/MainBtbAlignBank.scala b/src/main/scala/xiangshan/frontend/bpu/mbtb/MainBtbAlignBank.scala
index b5315ffa9e3..361c940806e 100644
--- a/src/main/scala/xiangshan/frontend/bpu/mbtb/MainBtbAlignBank.scala
+++ b/src/main/scala/xiangshan/frontend/bpu/mbtb/MainBtbAlignBank.scala
@@ -20,6 +20,7 @@ import chisel3.util._
 import org.chipsalliance.cde.config.Parameters
 import utility.XSPerfAccumulate
 import utility.XSPerfHistogram
+import utility.XSPerfSeqAccumulate
 import xiangshan.frontend.PrunedAddr
 import xiangshan.frontend.bpu.BranchInfo
 import xiangshan.frontend.bpu.Prediction
@@ -288,7 +289,7 @@ class MainBtbAlignBank(
   io.trace.entry     := t1_entry
   XSPerfHistogram("multihit_count", PopCount(s2_multiHitMask), s2_fire, 0, NumWay)
 
-  XSPerfAccumulate(
+  XSPerfSeqAccumulate(
     "", // no common prefix is needed
     t1_fire && t1_mispredictInfo.valid,
     Seq(
diff --git a/src/main/scala/xiangshan/frontend/bpu/sc/Sc.scala b/src/main/scala/xiangshan/frontend/bpu/sc/Sc.scala
index 8e001ad5142..d6e70b45c5f 100644
--- a/src/main/scala/xiangshan/frontend/bpu/sc/Sc.scala
+++ b/src/main/scala/xiangshan/frontend/bpu/sc/Sc.scala
@@ -23,6 +23,7 @@ import utility.ChiselDB
 import utility.ParallelSingedExpandingAdd
 import utility.XSError
 import utility.XSPerfAccumulate
+import utility.XSPerfSeqAccumulate
 import xiangshan.frontend.bpu.BasePredictor
 import xiangshan.frontend.bpu.BasePredictorIO
 import xiangshan.frontend.bpu.FoldedHistoryInfo
@@ -719,7 +720,7 @@ class Sc(implicit p: Parameters) extends BasePredictor with HasScParameters with
     XSPerfAccumulate(s"sc_train${i}", t1_writeValid && changeVec(i))
   }
 
-  XSPerfAccumulate(
+  XSPerfSeqAccumulate(
     "total",
     t1_writeValid,
     Seq(
diff --git a/src/main/scala/xiangshan/frontend/bpu/utage/MicroTage.scala b/src/main/scala/xiangshan/frontend/bpu/utage/MicroTage.scala
index b99644e261d..cd8009341f8 100644
--- a/src/main/scala/xiangshan/frontend/bpu/utage/MicroTage.scala
+++ b/src/main/scala/xiangshan/frontend/bpu/utage/MicroTage.scala
@@ -21,6 +21,7 @@ import org.chipsalliance.cde.config.Parameters
 import scala.math.min
 import utility.ChiselDB
 import utility.XSPerfAccumulate
+import utility.XSPerfSeqAccumulate
 import xiangshan.frontend.PrunedAddr
 import xiangshan.frontend.bpu.BasePredictor
 import xiangshan.frontend.bpu.BasePredictorIO
@@ -377,7 +378,7 @@ class MicroTage(implicit p: Parameters) extends BasePredictor with HasMicroTageP
     )
   }
 
-  XSPerfAccumulate(
+  XSPerfSeqAccumulate(
     "total_br",
     t1_fire,
     Seq(
diff --git a/src/main/scala/xiangshan/frontend/ftq/Ftq.scala b/src/main/scala/xiangshan/frontend/ftq/Ftq.scala
index 8075e210020..50d8fbbe386 100644
--- a/src/main/scala/xiangshan/frontend/ftq/Ftq.scala
+++ b/src/main/scala/xiangshan/frontend/ftq/Ftq.scala
@@ -30,7 +30,7 @@ import utility.UIntToMask
 import utility.XSError
 import utility.XSPerfAccumulate
 import utility.XSPerfHistogram
-import utility.XSPerfPriorityAccumulate
+import utility.XSPerfSeqAccumulate
 import xiangshan.RedirectLevel
 import xiangshan.TopDownCounters
 import xiangshan.backend.CtrlToFtqIO
@@ -408,7 +408,7 @@ class Ftq(implicit p: Parameters) extends FtqModule
   private val redirectPerfMeta = perfQueue(backendRedirectFtqIdx.bits.value).bpuPerf
   private val commitPerfMeta   = perfQueue(commitPtr(0).value)
 
-  XSPerfPriorityAccumulate(
+  XSPerfSeqAccumulate(
     "squash_cycles_bp_wrong_redirect",
     backendRedirect.valid && backendRedirect.bits.isMisPred,
     Seq(
@@ -416,10 +416,11 @@ class Ftq(implicit p: Parameters) extends FtqModule
       ("wrong_position", redirectCfiOffset =/= redirectPerfMeta.bpPred.cfiPosition),
       ("wrong_attribute", !(redirect.bits.attribute === redirectPerfMeta.bpPred.attribute)),
       ("wrong_target", redirect.bits.target =/= redirectPerfMeta.bpPred.target.toUInt)
-    )
+    ),
+    withPriority = true
   )
 
-  XSPerfAccumulate(
+  XSPerfSeqAccumulate(
     "squash_cycles_bp_wrong_redirect_wrong_target",
     backendRedirect.valid && backendRedirect.bits.isMisPred &&
       redirect.bits.taken === redirectPerfMeta.bpPred.taken &&
@@ -437,13 +438,13 @@ class Ftq(implicit p: Parameters) extends FtqModule
   private val perf_mispredS1SourceVec = BpuPredictionSource.Stage1.getValidSeq(redirectPerfMeta.bpSource.s1Source)
   private val perf_mispredS3SourceVec = BpuPredictionSource.Stage3.getValidSeq(redirectPerfMeta.bpSource.s3Source)
 
-  XSPerfAccumulate(
+  XSPerfSeqAccumulate(
     "resolve_branch_mispredicts_s1_source",
     backendRedirect.valid && backendRedirect.bits.isMisPred && !redirectPerfMeta.bpSource.s3Override,
     perf_mispredS1SourceVec
   )
 
-  XSPerfAccumulate(
+  XSPerfSeqAccumulate(
     "resolve_branch_mispredicts_s3_source",
     backendRedirect.valid && backendRedirect.bits.isMisPred && redirectPerfMeta.bpSource.s3Override,
     perf_mispredS3SourceVec
@@ -453,7 +454,7 @@ class Ftq(implicit p: Parameters) extends FtqModule
   XSPerfAccumulate("resolve_other_redirects", backendRedirect.valid && !backendRedirect.bits.isMisPred)
 
   // Commit-time statistics, should be correct-path only
-  XSPerfAccumulate(
+  XSPerfSeqAccumulate(
     "commit_branch",
     commit,
     Seq(
@@ -461,7 +462,7 @@ class Ftq(implicit p: Parameters) extends FtqModule
       ("mispredicts", true.B, commitPerfMeta.mispredict)
     )
   )
-  XSPerfAccumulate(
+  XSPerfSeqAccumulate(
     "commit_branch_type",
     commit,
     Seq(
@@ -482,22 +483,22 @@ class Ftq(implicit p: Parameters) extends FtqModule
   private val perf_commitHasMispredictConditional =
     perf_commitHasMispredict && commitPerfMeta.mispredictBranchInfo.attribute.isConditional
 
-  XSPerfAccumulate(
+  XSPerfSeqAccumulate(
     "commit_branch_mispredicts_s1_mispred_s1_source",
     perf_commitHasMispredict && !commitPerfMeta.bpuPerf.bpSource.s3Override,
     BpuPredictionSource.Stage1.getValidSeq(commitPerfMeta.bpuPerf.bpSource.s1Source)
   )
-  XSPerfAccumulate(
+  XSPerfSeqAccumulate(
     "commit_branch_mispredicts_s1_source",
     perf_commitHasMispredict,
     BpuPredictionSource.Stage1.getValidSeq(commitPerfMeta.bpuPerf.bpSource.s1Source)
   )
-  XSPerfAccumulate(
+  XSPerfSeqAccumulate(
     "commit_branch_mispredicts_s3_source",
     perf_commitHasMispredict,
     BpuPredictionSource.Stage3.getValidSeq(commitPerfMeta.bpuPerf.bpSource.s3Source)
   )
-  XSPerfAccumulate(
+  XSPerfSeqAccumulate(
     "commit_branch_mispredicts_reason",
     perf_commitHasMispredict,
     BlameBpuSource.BlameType.getValidSeq(BlameBpuSource(
@@ -506,7 +507,7 @@ class Ftq(implicit p: Parameters) extends FtqModule
       commitPerfMeta.mispredictBranchInfo
     ))
   )
-  XSPerfAccumulate(
+  XSPerfSeqAccumulate(
     "commit_conditional_branch_mispredicts_reason",
     perf_commitHasMispredictConditional,
     BlameBpuSource.BlameType.getValidSeq(BlameBpuSource(
@@ -515,7 +516,7 @@ class Ftq(implicit p: Parameters) extends FtqModule
       commitPerfMeta.mispredictBranchInfo
     ))
   )
-  XSPerfAccumulate(
+  XSPerfSeqAccumulate(
     "commit_branch_mispredicts_type",
     perf_commitHasMispredict,
     Seq(
diff --git a/utility b/utility
index 58d7554e333..0afa3dcbc4c 160000
--- a/utility
+++ b/utility
@@ -1 +1 @@
-Subproject commit 58d7554e333a9a01c49e06398de681c91d9a72b3
+Subproject commit 0afa3dcbc4c03910aa258d7664fdd9cb173732f3
```
