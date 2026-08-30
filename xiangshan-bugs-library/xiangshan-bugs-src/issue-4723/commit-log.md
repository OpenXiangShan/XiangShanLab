# Commit Log
- Issue: #4723
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/4723
- Issue state: closed
- Tested RTL commit: -
- Related PR: #4723
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/4723
- Changed files: 1
- Additions: 1
- Deletions: 1

## Files
- `src/main/scala/xiangshan/backend/fu/NewCSR/StateEnBundle.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/StateEnBundle.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/StateEnBundle.scala
index 32f503b1f18..1cc7bfb73fe 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/StateEnBundle.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/StateEnBundle.scala
@@ -39,7 +39,7 @@ class Mstateen0Bundle extends Hstateen0Bundle {
 
 class SstateenNonZeroBundle extends CSRBundle {  // for sstateen[1|2|3]
   override val len = 32
-  val ALL = RO(31, 0).withReset(0.U)
+  val ALL = RO(31, 0)
 }
 
 class HstateenNonZeroBundle extends CSRBundle {  // for hstateen[1|2|3]
```
