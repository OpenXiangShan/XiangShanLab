# Commit Log
- Issue: #3758
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/3758
- Issue state: closed
- Tested RTL commit: -
- Related PR: #3758
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/3758
- Changed files: 1
- Additions: 6
- Deletions: 6

## Files
- `src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala b/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala
index e7ffd496eb6..5256736be19 100644
--- a/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala
+++ b/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala
@@ -942,20 +942,20 @@ class StoreQueue(implicit p: Parameters) extends XSModule
   dontTouch(commitVec)
   // TODO: Deal with vector store mmio
   for (i <- 0 until CommitWidth) {
-    when (allocated(cmtPtrExt(i).value) && isNotAfter(uop(cmtPtrExt(i).value).robIdx, GatedRegNext(io.rob.pendingPtr)) && !needCancel(cmtPtrExt(i).value) && (!waitStoreS2(cmtPtrExt(i).value) || isVec(cmtPtrExt(i).value))) {
-      // don't commit while doing misalign
+    // don't mark misalign store as committed
+    when (allocated(cmtPtrExt(i).value) && !unaligned(cmtPtrExt(i).value) && isNotAfter(uop(cmtPtrExt(i).value).robIdx, GatedRegNext(io.rob.pendingPtr)) && !needCancel(cmtPtrExt(i).value) && (!waitStoreS2(cmtPtrExt(i).value) || isVec(cmtPtrExt(i).value))) {
       if (i == 0){
         // TODO: fixme for vector mmio
         when ((uncacheState === s_idle) || (uncacheState === s_wait && scommit > 0.U)){
           when ((isVec(cmtPtrExt(i).value) && vecMbCommit(cmtPtrExt(i).value)) || !isVec(cmtPtrExt(i).value)) {
-            committed(cmtPtrExt(0).value) := Mux(misalignBlock, false.B, true.B)
-            commitVec(0) := Mux(misalignBlock, false.B, true.B)
+            committed(cmtPtrExt(0).value) := true.B
+            commitVec(0) := true.B
           }
         }
       } else {
         when ((isVec(cmtPtrExt(i).value) && vecMbCommit(cmtPtrExt(i).value)) || !isVec(cmtPtrExt(i).value)) {
-          committed(cmtPtrExt(i).value) := Mux(misalignBlock, false.B, commitVec(i - 1) || committed(cmtPtrExt(i).value))
-          commitVec(i) := Mux(misalignBlock, false.B, commitVec(i - 1))
+          committed(cmtPtrExt(i).value) := commitVec(i - 1) || committed(cmtPtrExt(i).value)
+          commitVec(i) := commitVec(i - 1)
         }
       }
     }
```
