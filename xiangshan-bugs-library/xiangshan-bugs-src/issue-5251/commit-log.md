# Commit Log
- Issue: #5251
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/5251
- Issue state: closed
- Tested RTL commit: -
- Related PR: #5251
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/5251
- Changed files: 1
- Additions: 1
- Deletions: 1

## Files
- `src/main/scala/xiangshan/frontend/bpu/tage/Bundles.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/frontend/bpu/tage/Bundles.scala b/src/main/scala/xiangshan/frontend/bpu/tage/Bundles.scala
index 0230da453d5..fed463f705c 100644
--- a/src/main/scala/xiangshan/frontend/bpu/tage/Bundles.scala
+++ b/src/main/scala/xiangshan/frontend/bpu/tage/Bundles.scala
@@ -131,5 +131,5 @@ class ConditionalBranchTrace(implicit p: Parameters) extends TageBundle {
 }
 
 class TageTrace(implicit p: Parameters) extends TageBundle {
-  val condTrace: Vec[Valid[ConditionalBranchTrace]] = Vec(NumBtbResultEntries, Valid(new ConditionalBranchTrace))
+  val condTrace: Vec[Valid[ConditionalBranchTrace]] = Vec(ResolveEntryBranchNumber, Valid(new ConditionalBranchTrace))
 }
```
