# Commit Log
- Issue: #5370
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/5370
- Issue state: closed
- Tested RTL commit: -
- Related PR: #5370
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/5370
- Changed files: 2
- Additions: 6
- Deletions: 6

## Files
- `src/main/scala/xiangshan/frontend/bpu/Bpu.scala`
- `src/main/scala/xiangshan/frontend/ftq/Ftq.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/frontend/bpu/Bpu.scala b/src/main/scala/xiangshan/frontend/bpu/Bpu.scala
index baeb6c95459..9075c10a4cc 100644
--- a/src/main/scala/xiangshan/frontend/bpu/Bpu.scala
+++ b/src/main/scala/xiangshan/frontend/bpu/Bpu.scala
@@ -664,19 +664,19 @@ class Bpu(implicit p: Parameters) extends BpuModule with HalfAlignHelper {
   XSPerfAccumulate(
     s"s3Override_takenMismatch_s1fall",
     io.toFtq.prediction.fire && s3_override && s3_perfMeta.bpSource.s1Fallthrough,
-    perf_s1TakenSourceVec
+    perf_s3TakenSourceVec
   )
 
   XSPerfAccumulate(
     s"s3Override_takenMismatch_s3fall",
     io.toFtq.prediction.fire && s3_override && s3_perfMeta.bpSource.s3Fallthrough,
-    perf_s3TakenSourceVec
+    perf_s1TakenSourceVec
   )
 
   XSPerfAccumulate(
     s"s3Override_takenMismatch_s3fallTage",
     io.toFtq.prediction.fire && s3_override && s3_perfMeta.bpSource.s3FallthroughTage,
-    perf_s3TakenSourceVec
+    perf_s1TakenSourceVec
   )
 
   // position mismatch
@@ -729,7 +729,7 @@ class Bpu(implicit p: Parameters) extends BpuModule with HalfAlignHelper {
     Seq(
       ("total", true.B, PopCount(t0_branches.map(_.valid))),
       ("direct", true.B, PopCount(t0_branches.map(b => b.valid && b.bits.attribute.isDirect))),
-      ("indirect", true.B, PopCount(t0_branches.map(b => b.valid && b.bits.attribute.isOtherIndirect))),
+      ("otherIndirect", true.B, PopCount(t0_branches.map(b => b.valid && b.bits.attribute.isOtherIndirect))),
       ("call", true.B, PopCount(t0_branches.map(b => b.valid && b.bits.attribute.isCall))),
       ("return", true.B, PopCount(t0_branches.map(b => b.valid && b.bits.attribute.isReturn))),
       ("conditional", true.B, PopCount(t0_branches.map(b => b.valid && b.bits.attribute.isConditional)))
@@ -741,7 +741,7 @@ class Bpu(implicit p: Parameters) extends BpuModule with HalfAlignHelper {
     Seq(
       ("total", true.B),
       ("direct", t0_mispredictBranch.bits.attribute.isDirect),
-      ("indirect", t0_mispredictBranch.bits.attribute.isOtherIndirect),
+      ("otherIndirect", t0_mispredictBranch.bits.attribute.isOtherIndirect),
       ("call", t0_mispredictBranch.bits.attribute.isCall),
       ("return", t0_mispredictBranch.bits.attribute.isReturn),
       ("conditional", t0_mispredictBranch.bits.attribute.isConditional),
diff --git a/src/main/scala/xiangshan/frontend/ftq/Ftq.scala b/src/main/scala/xiangshan/frontend/ftq/Ftq.scala
index 9f34f3a5903..1d23f77fd99 100644
--- a/src/main/scala/xiangshan/frontend/ftq/Ftq.scala
+++ b/src/main/scala/xiangshan/frontend/ftq/Ftq.scala
@@ -392,7 +392,7 @@ class Ftq(implicit p: Parameters) extends FtqModule
   io.ControlBTBMissBubble := false.B // TODO: add more info to distinguish
   io.TAGEMissBubble       := RegNext(backendRedirect.valid && backendRedirect.bits.attribute.isConditional)
   io.SCMissBubble         := false.B // TODO: add SC info
-  io.ITTAGEMissBubble     := RegNext(backendRedirect.valid && backendRedirect.bits.attribute.isOtherIndirect)
+  io.ITTAGEMissBubble     := RegNext(backendRedirect.valid && backendRedirect.bits.attribute.needIttage)
   io.RASMissBubble        := RegNext(backendRedirect.valid && backendRedirect.bits.attribute.isReturn)
 
   val perfEvents: Seq[(String, UInt)] = Seq()
```
