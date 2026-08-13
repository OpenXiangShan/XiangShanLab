# Commit Log
- Issue: #3884
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/3884
- Issue state: closed
- Tested RTL commit: -
- Related PR: #3884
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/3884
- Changed files: 1
- Additions: 6
- Deletions: 4

## Files
- `src/main/scala/xiangshan/mem/lsqueue/LoadQueueReplay.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/mem/lsqueue/LoadQueueReplay.scala b/src/main/scala/xiangshan/mem/lsqueue/LoadQueueReplay.scala
index f8c7f8f65e9..ee557cc05d3 100644
--- a/src/main/scala/xiangshan/mem/lsqueue/LoadQueueReplay.scala
+++ b/src/main/scala/xiangshan/mem/lsqueue/LoadQueueReplay.scala
@@ -277,6 +277,7 @@ class LoadQueueReplay(implicit p: Parameters) extends XSModule
   val needEnqueue = VecInit((0 until LoadPipelineWidth).map(w => {
     canEnqueue(w) && !cancelEnq(w) && needReplay(w) && !hasExceptions(w)
   }))
+  val newEnqueue = Wire(Vec(LoadPipelineWidth, Bool()))
   val canFreeVec = VecInit((0 until LoadPipelineWidth).map(w => {
     canEnqueue(w) && loadReplay(w) && (!needReplay(w) || hasExceptions(w))
   }))
@@ -381,7 +382,7 @@ class LoadQueueReplay(implicit p: Parameters) extends XSModule
   val needCancel = Wire(Vec(LoadQueueReplaySize, Bool()))
   // generate enq mask
   val enqIndexOH = Wire(Vec(LoadPipelineWidth, UInt(LoadQueueReplaySize.W)))
-  val s0_loadEnqFireMask = io.enq.map(x => x.fire && !x.bits.isLoadReplay && x.bits.rep_info.need_rep).zip(enqIndexOH).map(x => Mux(x._1, x._2, 0.U))
+  val s0_loadEnqFireMask = newEnqueue.zip(enqIndexOH).map(x => Mux(x._1, x._2, 0.U))
   val s0_remLoadEnqFireVec = s0_loadEnqFireMask.map(x => VecInit((0 until LoadPipelineWidth).map(rem => getRemBits(x)(rem))))
   val s0_remEnqSelVec = Seq.tabulate(LoadPipelineWidth)(w => VecInit(s0_remLoadEnqFireVec.map(x => x(w))))
 
@@ -603,9 +604,10 @@ class LoadQueueReplay(implicit p: Parameters) extends XSModule
   assert(freeList.io.canAllocate.reduce(_ || _) || !io.enq.map(_.valid).reduce(_ || _), s"LoadQueueReplay Overflow")
 
   // Allocate logic
-  val newEnqueue = (0 until LoadPipelineWidth).map(i => {
-    needEnqueue(i) && !io.enq(i).bits.isLoadReplay
-  })
+  needEnqueue.zip(newEnqueue).zip(io.enq).map {
+    case ((needEnq, newEnq), enq) =>
+      newEnq := needEnq && !enq.bits.isLoadReplay
+  }
 
   for ((enq, w) <- io.enq.zipWithIndex) {
     vaddrModule.io.wen(w) := false.B
```
