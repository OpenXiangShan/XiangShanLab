# Commit Log
- Issue: #4774
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/4774
- Issue state: closed
- Tested RTL commit: -
- Related PR: #4774
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/4774
- Changed files: 1
- Additions: 1
- Deletions: 1

## Files
- `src/main/scala/xiangshan/mem/lsqueue/LoadQueueUncache.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/mem/lsqueue/LoadQueueUncache.scala b/src/main/scala/xiangshan/mem/lsqueue/LoadQueueUncache.scala
index 516d95bfd81..4ecb4eddb9d 100644
--- a/src/main/scala/xiangshan/mem/lsqueue/LoadQueueUncache.scala
+++ b/src/main/scala/xiangshan/mem/lsqueue/LoadQueueUncache.scala
@@ -227,7 +227,7 @@ class UncacheEntry(entryIndex: Int)(implicit p: Parameters) extends XSModule
 
   io.exception.valid := writeback
   io.exception.bits := req
-  io.exception.bits.uop.exceptionVec(loadAccessFault) := nderr
+  io.exception.bits.uop.exceptionVec(hardwareError) := nderr
 
   /* debug log */
   XSDebug(io.uncache.req.fire,
```
