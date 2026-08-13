# Commit Log
- Issue: #4077
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/4077
- Issue state: closed
- Tested RTL commit: -
- Related PR: #4077
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/4077
- Changed files: 1
- Additions: 19
- Deletions: 12

## Files
- `src/main/scala/xiangshan/mem/lsqueue/StoreMisalignBuffer.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/mem/lsqueue/StoreMisalignBuffer.scala b/src/main/scala/xiangshan/mem/lsqueue/StoreMisalignBuffer.scala
index 6d6727befa0..f6481a4f52c 100644
--- a/src/main/scala/xiangshan/mem/lsqueue/StoreMisalignBuffer.scala
+++ b/src/main/scala/xiangshan/mem/lsqueue/StoreMisalignBuffer.scala
@@ -125,10 +125,19 @@ class StoreMisalignBuffer(implicit p: Parameters) extends XSModule
   val req_valid = RegInit(false.B)
   val req = Reg(new StoreMisalignBufferEntry)
 
-  val robMatch = req_valid && io.rob.pendingst && (io.rob.pendingPtr === req.uop.robIdx)
   val cross4KBPageBoundary = Wire(Bool())
   val needFlushPipe = RegInit(false.B)
 
+  // buffer control:
+  //  - s_idle:  Idle
+  //  - s_split: Split miss-aligned store into aligned stores
+  //  - s_req:   Send split store to sta and get result from sta
+  //  - s_resp:  Responds to a split store access request
+  //  - s_wb:    writeback yo rob/vecMergeBuffer
+  //  - s_block: Wait for this instr to reach the head of Rob.
+  val s_idle :: s_split :: s_req :: s_resp :: s_wb :: s_block :: Nil = Enum(6)
+  val bufferState    = RegInit(s_idle)
+
   // enqueue
   // s1:
   val s1_req = VecInit(io.req.map(_.bits))
@@ -144,6 +153,8 @@ class StoreMisalignBuffer(implicit p: Parameters) extends XSModule
   val reqRedirect = reqSelBits.uop.robIdx.needFlush(io.redirect)
 
   val canEnq = !req_valid && !reqRedirect && reqSelValid
+  val robMatch = req_valid && io.rob.pendingst && (io.rob.pendingPtr === req.uop.robIdx)
+
   when(canEnq) {
     connectSamePort(req, reqSelBits)
     req.portIndex := reqSelPort
@@ -151,7 +162,11 @@ class StoreMisalignBuffer(implicit p: Parameters) extends XSModule
   }
   val cross4KBPageEnq = WireInit(false.B)
   when (cross4KBPageBoundary && !reqRedirect) {
-    when(reqSelValid && (isAfter(req.uop.robIdx, reqSelBits.uop.robIdx) || (isNotBefore(req.uop.robIdx, reqSelBits.uop.robIdx) && req.uop.uopIdx > reqSelBits.uop.uopIdx))) {
+    when(
+      reqSelValid &&
+      (isAfter(req.uop.robIdx, reqSelBits.uop.robIdx) || (isNotBefore(req.uop.robIdx, reqSelBits.uop.robIdx) && req.uop.uopIdx > reqSelBits.uop.uopIdx)) &&
+      bufferState === s_idle
+    ) {
       connectSamePort(req, reqSelBits)
       req.portIndex := reqSelPort
       cross4KBPageEnq := true.B
@@ -168,7 +183,6 @@ class StoreMisalignBuffer(implicit p: Parameters) extends XSModule
     case (reqPort, index) => reqPort.ready := reqSelCanEnq(index) && (!req_valid || cross4KBPageBoundary && cross4KBPageEnq)
   }
 
-
   io.toVecStoreMergeBuffer.zipWithIndex.map{
     case (toStMB, index) => {
       toStMB.flush   := req_valid && cross4KBPageBoundary && cross4KBPageEnq && UIntToOH(req.portIndex)(index)
@@ -177,15 +191,7 @@ class StoreMisalignBuffer(implicit p: Parameters) extends XSModule
   }
   io.full := req_valid
 
-  // buffer control:
-  //  - s_idle:  Idle
-  //  - s_split: Split miss-aligned store into aligned stores
-  //  - s_req:   Send split store to sta and get result from sta
-  //  - s_resp:  Responds to a split store access request
-  //  - s_wb:    writeback yo rob/vecMergeBuffer
-  //  - s_block: Wait for this instr to reach the head of Rob.
-  val s_idle :: s_split :: s_req :: s_resp :: s_wb :: s_block :: Nil = Enum(6)
-  val bufferState    = RegInit(s_idle)
+  //logic
   val splitStoreReqs = RegInit(VecInit(List.fill(maxSplitNum)(0.U.asTypeOf(new LsPipelineBundle))))
   val splitStoreResp = RegInit(VecInit(List.fill(maxSplitNum)(0.U.asTypeOf(new SqWriteBundle))))
   val isCrossPage    = RegInit(false.B)
@@ -207,6 +213,7 @@ class StoreMisalignBuffer(implicit p: Parameters) extends XSModule
 
   io.sqControl.toStoreQueue.withSameUop := io.sqControl.toStoreMisalignBuffer.uop.robIdx === req.uop.robIdx && io.sqControl.toStoreMisalignBuffer.uop.uopIdx === req.uop.uopIdx && req.isvec && robMatch && isCrossPage
 
+  //state transition
   switch(bufferState) {
     is (s_idle) {
       when(cross4KBPageBoundary) {
```
