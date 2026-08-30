# Commit Log
- Issue: #4876
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/4876
- Issue state: closed
- Tested RTL commit: -
- Related PR: #4876
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/4876
- Changed files: 1
- Additions: 2
- Deletions: 7

## Files
- `src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala b/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala
index 4b2356c3a89..c6bd077d33a 100644
--- a/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala
+++ b/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala
@@ -1361,14 +1361,9 @@ class StoreQueue(implicit p: Parameters) extends XSModule
   )
   val onlyCommit0 = dataBuffer.io.enq(0).fire && !dataBuffer.io.enq(1).fire
 
-  /**
-   * If rdataPtr(0) is misaligned and Cross16Byte, this store request will fill two ports of rdataBuffer,
-   * Therefore, the judgement of vecCommitLastFlow should't to use rdataPtr(1)
-   * */
-  val firstSplit = canDeqMisaligned && firstWithMisalign && firstWithCross16Byte
   val vecCommitLastFlow =
     // robidx equal => check if 1 is last flow
-    robidxEQ && vecCommitHasExceptionLastFlow(1) && !firstSplit ||
+    robidxEQ && vecCommitHasExceptionLastFlow(1) ||
     // robidx not equal => 0 must be the last flow, just check if 1 is last flow when 1 has exception
     robidxNE && (vecCommitHasExceptionValid(1) && vecCommitHasExceptionLastFlow(1) || !vecCommitHasExceptionValid(1)) ||
     onlyCommit0 && vecCommitHasExceptionLastFlow(0)
@@ -1377,7 +1372,7 @@ class StoreQueue(implicit p: Parameters) extends XSModule
   val vecExceptionFlagCancel  = (0 until EnsbufferWidth).map{ i =>
     val ptr = rdataPtrExt(i).value
     val vecLastFlowCommit = vecLastFlow(ptr) && (uop(ptr).robIdx === vecExceptionFlag.bits.robIdx) &&
-                            dataBuffer.io.enq(i).fire && !firstSplit
+                            dataBuffer.io.enq(i).fire && dataBuffer.io.enq(i).bits.sqNeedDeq
     vecLastFlowCommit
   }.reduce(_ || _)
```
