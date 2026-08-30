# Commit Log
- Issue: #5536
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/5536
- Issue state: closed
- Tested RTL commit: -
- Related PR: #5536
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/5536
- Changed files: 2
- Additions: 7
- Deletions: 1

## Files
- `src/main/scala/xiangshan/frontend/Bundles.scala`
- `src/main/scala/xiangshan/frontend/ftq/Ftq.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/frontend/Bundles.scala b/src/main/scala/xiangshan/frontend/Bundles.scala
index d4368449568..85954181098 100644
--- a/src/main/scala/xiangshan/frontend/Bundles.scala
+++ b/src/main/scala/xiangshan/frontend/Bundles.scala
@@ -381,7 +381,7 @@ object BlameBpuSource {
         // If cond before, TAGE mispredicts
         // If cond after, should trigger assertion, TODO
         blame := TAGE
-      }.elsewhen(attr.isReturn) {
+      }.elsewhen(attr.isReturn && pred.cfiPosition === branch.cfiPosition) {
         blame := RAS
       }.otherwise {
         // Other branch type mismatch
diff --git a/src/main/scala/xiangshan/frontend/ftq/Ftq.scala b/src/main/scala/xiangshan/frontend/ftq/Ftq.scala
index 34c32d58f31..922fa6bcbe8 100644
--- a/src/main/scala/xiangshan/frontend/ftq/Ftq.scala
+++ b/src/main/scala/xiangshan/frontend/ftq/Ftq.scala
@@ -477,6 +477,12 @@ class Ftq(implicit p: Parameters) extends FtqModule
       ("ret", commitPerfMeta.mispredictBranchInfo.attribute.isReturn)
     )
   )
+
+  XSPerfAccumulate(
+    "commit_branch_mispredicts_s1_mispred_s1_source",
+    commit && commitPerfMeta.mispredict && !commitPerfMeta.bpuPerf.bpSource.s3Override,
+    BpuPredictionSource.Stage1.getValidSeq(commitPerfMeta.bpuPerf.bpSource.s1Source)
+  )
   XSPerfAccumulate(
     "commit_branch_mispredicts_s1_source",
     commit && commitPerfMeta.mispredict,
```
