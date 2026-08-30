# Commit Log
- Issue: #4139
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/4139
- Issue state: closed
- Tested RTL commit: -
- Related PR: #4139
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/4139
- Changed files: 1
- Additions: 1
- Deletions: 2

## Files
- `src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala b/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala
index f12f7ff308d..62cd8f05156 100644
--- a/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala
+++ b/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala
@@ -1375,8 +1375,7 @@ class StoreQueue(implicit p: Parameters) extends XSModule
   // misprediction recovery / exception redirect
   // invalidate sq term using robIdx
   for (i <- 0 until StoreQueueSize) {
-    needCancel(i) := uop(i).robIdx.needFlush(io.brqRedirect) && allocated(i) && !committed(i) &&
-      (!isVec(i) || !(uop(i).robIdx === io.brqRedirect.bits.robIdx))
+    needCancel(i) := uop(i).robIdx.needFlush(io.brqRedirect) && allocated(i) && !committed(i)
     when (needCancel(i)) {
       allocated(i) := false.B
     }
```
