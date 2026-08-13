# Commit Log
- Issue: #4660
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/4660
- Issue state: closed
- Tested RTL commit: -
- Related PR: #4660
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/4660
- Changed files: 1
- Additions: 1
- Deletions: 1

## Files
- `src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala b/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala
index 0d1e24f1f45..db0cf577ded 100644
--- a/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala
+++ b/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala
@@ -1418,7 +1418,7 @@ class StoreQueue(implicit p: Parameters) extends XSModule
   // misprediction recovery / exception redirect
   // invalidate sq term using robIdx
   for (i <- 0 until StoreQueueSize) {
-    needCancel(i) := uop(i).robIdx.needFlush(io.brqRedirect) && allocated(i) && !committed(i)
+    needCancel(i) := (uop(i).robIdx.needFlush(io.brqRedirect) & !isVec(i) || isAfter(uop(i).robIdx, io.brqRedirect.bits.robIdx) && io.brqRedirect.valid && isVec(i)) && allocated(i) && !committed(i)
     when (needCancel(i)) {
       allocated(i) := false.B
       completed(i) := false.B
```
