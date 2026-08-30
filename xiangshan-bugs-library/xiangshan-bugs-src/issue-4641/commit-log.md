# Commit Log
- Issue: #4641
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/4641
- Issue state: closed
- Tested RTL commit: -
- Related PR: #4641
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/4641
- Changed files: 1
- Additions: 27
- Deletions: 22

## Files
- `src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala b/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala
index f5934a7f7df..0a3b226afa1 100644
--- a/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala
+++ b/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala
@@ -253,6 +253,7 @@ class StoreQueue(implicit p: Parameters) extends XSModule
 
   // state & misc
   val allocated = RegInit(VecInit(List.fill(StoreQueueSize)(false.B))) // sq entry has been allocated
+  val completed = RegInit(VecInit(List.fill(StoreQueueSize)(false.B))) 
   val addrvalid = RegInit(VecInit(List.fill(StoreQueueSize)(false.B)))
   val datavalid = RegInit(VecInit(List.fill(StoreQueueSize)(false.B)))
   val allvalid  = VecInit((0 until StoreQueueSize).map(i => addrvalid(i) && datavalid(i)))
@@ -320,24 +321,21 @@ class StoreQueue(implicit p: Parameters) extends XSModule
   )
 
   // deqPtrExtNext traces which inst is about to leave store queue
-  //
-  // io.sbuffer(i).fire is RegNexted, as sbuffer data write takes 2 cycles.
-  // Before data write finish, sbuffer is unable to provide store to load
-  // forward data. As an workaround, deqPtrExt and allocated flag update
-  // is delayed so that load can get the right data from store queue.
-  //
-  // Modify deqPtrExtNext and io.sqDeq with care!
   val deqPtrExtNext = Wire(Vec(EnsbufferWidth, new SqPtr))
-  // Only sqNeedDeq can move the ptr
-  deqPtrExtNext := deqPtrExt.map(i =>  i +
-    RegNext(PopCount(VecInit(io.sbuffer.map(x=> x.fire && x.bits.sqNeedDeq)))) +
-    PopCount(ncDeqTrigger || io.mmioStout.fire || io.vecmmioStout.fire)
-  )
-
-  io.sqDeq := RegNext(
-    RegNext(PopCount(VecInit(io.sbuffer.map(x=> x.fire && x.bits.sqNeedDeq)))) +
-    PopCount(ncDeqTrigger || io.mmioStout.fire || io.vecmmioStout.fire)
-  )
+  val sqDeqCnt = WireInit(0.U(log2Ceil(EnsbufferWidth + 1).W))
+  val readyDeqVec = WireInit(VecInit((0 until EnsbufferWidth).map(i => 
+    allocated(deqPtrExt(i).value) && completed(deqPtrExt(i).value)
+  )))
+  for (i <- 0 until EnsbufferWidth) {
+    val ptr = deqPtrExt(i).value
+    when(readyDeqVec.take(i + 1).reduce(_ && _)) {
+      sqDeqCnt := (i + 1).U
+      allocated(ptr) := false.B
+      completed(ptr) := false.B
+    }
+  }
+  deqPtrExtNext := deqPtrExt.map(_ + sqDeqCnt)
+  io.sqDeq := RegNext(sqDeqCnt)
 
   assert(!RegNext(RegNext(io.sbuffer(0).fire) && (io.mmioStout.fire || io.vecmmioStout.fire)))
 
@@ -384,6 +382,7 @@ class StoreQueue(implicit p: Parameters) extends XSModule
         vecLastFlow(i) := Mux(0.U === selectUpBound.value, selectBits.lastUop, false.B) else
         vecLastFlow(i) := Mux((i + 1).U === selectUpBound.value, selectBits.lastUop, false.B)
       allocated(i) := true.B
+      completed(i) := false.B
       datavalid(i) := false.B
       addrvalid(i) := false.B
       unaligned(i) := false.B
@@ -925,7 +924,7 @@ class StoreQueue(implicit p: Parameters) extends XSModule
   ncResp.valid := io.uncache.resp.fire && io.uncache.resp.bits.nc
   ncResp.bits <> io.uncache.resp.bits
   when (ncDeqTrigger) {
-    allocated(ncPtr) := false.B
+    completed(ncPtr) := true.B
   }
   XSDebug(ncDeqTrigger,"nc fire: ptr %d\n", ncPtr)
 
@@ -1017,7 +1016,7 @@ class StoreQueue(implicit p: Parameters) extends XSModule
   // Remove MMIO inst from store queue after MMIO request is being sent
   // That inst will be traced by uncache state machine
   when (io.mmioStout.fire) {
-    allocated(deqPtr) := false.B
+    completed(deqPtr) := true.B
   }
 
   // cbo Zero writeback to ROB
@@ -1060,7 +1059,7 @@ class StoreQueue(implicit p: Parameters) extends XSModule
   // Remove MMIO inst from store queue after MMIO request is being sent
   // That inst will be traced by uncache state machine
   when (io.vecmmioStout.fire) {
-    allocated(deqPtr) := false.B
+    completed(deqPtr) := true.B
   }
 
   /**
@@ -1291,9 +1290,14 @@ class StoreQueue(implicit p: Parameters) extends XSModule
     // Before data write finish, sbuffer is unable to provide store to load
     // forward data. As an workaround, deqPtrExt and allocated flag update
     // is delayed so that load can get the right data from store queue.
+    // ---
+    // Only sqNeedDeq can move the ptr.
+    // ---
+    // however, `completed` is register, when it turn true, the data has already been written to sbuffer    
     val ptr = dataBuffer.io.deq(i).bits.sqPtr.value
-    when (RegNext(io.sbuffer(i).fire && io.sbuffer(i).bits.sqNeedDeq)) {
-      allocated(RegEnable(ptr, io.sbuffer(i).fire)) := false.B
+    when (io.sbuffer(i).fire && io.sbuffer(i).bits.sqNeedDeq) {
+
+      completed(ptr) := true.B
     }
     XSDebug(RegNext(io.sbuffer(i).fire && io.sbuffer(i).bits.sqNeedDeq), "sbuffer "+i+" fire: ptr %d\n", ptr)
   }
@@ -1431,6 +1435,7 @@ class StoreQueue(implicit p: Parameters) extends XSModule
     needCancel(i) := uop(i).robIdx.needFlush(io.brqRedirect) && allocated(i) && !committed(i)
     when (needCancel(i)) {
       allocated(i) := false.B
+      completed(i) := false.B
     }
   }
```
