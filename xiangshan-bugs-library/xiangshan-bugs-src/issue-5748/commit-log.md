# Commit Log
- Issue: #5748
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/5748
- Issue state: closed
- Tested RTL commit: -
- Related PR: #5748
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/5748
- Changed files: 1
- Additions: 2
- Deletions: 2

## Files
- `src/main/scala/xiangshan/mem/lsqueue/NewStoreQueue.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/mem/lsqueue/NewStoreQueue.scala b/src/main/scala/xiangshan/mem/lsqueue/NewStoreQueue.scala
index c4f3cdece84..7af712e3a4c 100644
--- a/src/main/scala/xiangshan/mem/lsqueue/NewStoreQueue.scala
+++ b/src/main/scala/xiangshan/mem/lsqueue/NewStoreQueue.scala
@@ -1516,8 +1516,8 @@ class NewStoreQueue(implicit p: Parameters) extends NewStoreQueueBase with HasPe
       ptr.value === i.U && sqDeqCnt > j.U
     }).asUInt.orR
 
-    val handleFinishSet = rdataPtrExt.head.value === i.U &&
-      (io.writeBack.fire || io.toUncacheBuffer.req.fire && isPbmtNC(dataEntries(i).memoryType))
+    val ncFinish = io.toUncacheBuffer.idResp.valid && !io.toUncacheBuffer.idResp.bits.is2lq && isPbmtNC(dataEntries(i).memoryType)
+    val handleFinishSet = rdataPtrExt.head.value === i.U && (io.writeBack.fire || ncFinish)
 
     when (entryCanEnq) {
       connectSamePort(dataEntries(i).uop, selectBits.uop) //TODO: will be remove in the future.
```
