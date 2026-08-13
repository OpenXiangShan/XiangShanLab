# Commit Log
- Issue: #3635
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/3635
- Issue state: closed
- Tested RTL commit: -
- Related PR: #3635
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/3635
- Changed files: 1
- Additions: 1
- Deletions: 1

## Files
- `src/main/scala/xiangshan/frontend/FrontendBundle.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/frontend/FrontendBundle.scala b/src/main/scala/xiangshan/frontend/FrontendBundle.scala
index f7a7da29317..77e2b5a9780 100644
--- a/src/main/scala/xiangshan/frontend/FrontendBundle.scala
+++ b/src/main/scala/xiangshan/frontend/FrontendBundle.scala
@@ -603,7 +603,7 @@ class FullBranchPrediction(implicit p: Parameters) extends XSBundle with HasBPUC
   // the vec indicating if ghr should shift on each branch
   def shouldShiftVec =
     VecInit(br_valids.zipWithIndex.map{ case (v, i) =>
-      v && !real_br_taken_mask().take(i).reduceOption(_||_).getOrElse(false.B)})
+      v && hit && !real_br_taken_mask().take(i).reduceOption(_||_).getOrElse(false.B)})
 
   def lastBrPosOH =
     VecInit((!hit || !br_valids.reduce(_||_)) +: // not hit or no brs in entry
```
