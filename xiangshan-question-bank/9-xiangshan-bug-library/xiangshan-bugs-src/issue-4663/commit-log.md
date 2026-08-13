# Commit Log
- Issue: #4663
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/4663
- Issue state: closed
- Tested RTL commit: -
- Related PR: #4663
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/4663
- Changed files: 1
- Additions: 1
- Deletions: 1

## Files
- `src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala b/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala
index 0d1e24f1f45..11308ba5cf4 100644
--- a/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala
+++ b/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala
@@ -939,7 +939,7 @@ class StoreQueue(implicit p: Parameters) extends XSModule
 
   // RegNext(io.sbuffer(i).fire) is used to alignment timing
   val isCboZeroToSbVec = (0 until EnsbufferWidth).map{ i =>
-    RegNext(io.sbuffer(i).fire) && uop(deqPtrExt(i).value).fuOpType === LSUOpType.cbo_zero && allocated(deqPtrExt(i).value)
+    RegNext(io.sbuffer(i).fire && io.sbuffer(i).bits.vecValid) && uop(deqPtrExt(i).value).fuOpType === LSUOpType.cbo_zero && allocated(deqPtrExt(i).value)
   }
   val cboZeroToSb        = isCboZeroToSbVec.reduce(_ || _)
   val cboZeroFlushSb     = GatedRegNext(cboZeroToSb)
```
