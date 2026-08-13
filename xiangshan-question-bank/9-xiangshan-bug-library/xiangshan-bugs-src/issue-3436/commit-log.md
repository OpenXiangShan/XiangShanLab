# Commit Log
- Issue: #3436
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/3436
- Issue state: closed
- Tested RTL commit: -
- Related PR: #3436
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/3436
- Changed files: 1
- Additions: 5
- Deletions: 3

## Files
- `src/main/scala/xiangshan/mem/lsqueue/LoadQueueReplay.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/mem/lsqueue/LoadQueueReplay.scala b/src/main/scala/xiangshan/mem/lsqueue/LoadQueueReplay.scala
index 77b5f5f2f2a..23183b4220d 100644
--- a/src/main/scala/xiangshan/mem/lsqueue/LoadQueueReplay.scala
+++ b/src/main/scala/xiangshan/mem/lsqueue/LoadQueueReplay.scala
@@ -598,12 +598,15 @@ class LoadQueueReplay(implicit p: Parameters) extends XSModule
   // init
   freeMaskVec.map(e => e := false.B)
 
+  // LoadQueueReplay can't backpressure.
+  // We think LoadQueueReplay can always enter, as long as it is the same size as VirtualLoadQueue.
+  assert(freeList.io.canAllocate.reduce(_ || _) || !io.enq.map(_.valid).reduce(_ || _), s"LoadQueueReplay Overflow")
+
   // Allocate logic
   val newEnqueue = (0 until LoadPipelineWidth).map(i => {
     needEnqueue(i) && !io.enq(i).bits.isLoadReplay
   })
 
-  val canAcceptCount = PopCount(freeList.io.canAllocate)
   for ((enq, w) <- io.enq.zipWithIndex) {
     vaddrModule.io.wen(w) := false.B
     freeList.io.doAllocate(w) := false.B
@@ -612,10 +615,9 @@ class LoadQueueReplay(implicit p: Parameters) extends XSModule
 
     //  Allocated ready
     val offset = PopCount(newEnqueue.take(w))
-    val canAccept = canAcceptCount >= (w+1).U
     val enqIndex = Mux(enq.bits.isLoadReplay, enq.bits.schedIndex, freeList.io.allocateSlot(offset))
     enqIndexOH(w) := UIntToOH(enqIndex)
-    enq.ready := Mux(enq.bits.isLoadReplay, true.B, canAccept)
+    enq.ready := true.B
 
     when (needEnqueue(w) && enq.ready) {
```
