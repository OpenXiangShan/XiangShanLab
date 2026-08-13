# Commit Log
- Issue: #5855
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/5855
- Issue state: closed
- Tested RTL commit: -
- Related PR: #5855
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/5855
- Changed files: 1
- Additions: 18
- Deletions: 12

## Files
- `src/main/scala/xiangshan/mem/lsqueue/NewStoreQueue.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/mem/lsqueue/NewStoreQueue.scala b/src/main/scala/xiangshan/mem/lsqueue/NewStoreQueue.scala
index 7293e90fcc3..2719ddc57f3 100644
--- a/src/main/scala/xiangshan/mem/lsqueue/NewStoreQueue.scala
+++ b/src/main/scala/xiangshan/mem/lsqueue/NewStoreQueue.scala
@@ -157,6 +157,7 @@ class WriteToSbufferReqEntry(implicit p: Parameters) extends MemBlockBundle {
   val vaddr        = UInt(VAddrBits.W)
   val data         = UInt(VLEN.W)
   val mask         = UInt((VLEN/8).W)
+  val deqPtrMove   = Bool()
 }
 
 abstract class NewStoreQueueBase(implicit p: Parameters) extends LSQModule {
@@ -655,6 +656,7 @@ abstract class NewStoreQueueBase(implicit p: Parameters) extends LSQModule {
     val io = IO(new Bundle {
       val fromDeqModule = Vec(EnsbufferWidth, Flipped(DecoupledIO(new WriteToSbufferReqEntry)))
       val toSbuffer     = new SbufferWriteIO
+      val deqPtrMove    = Vec(EnsbufferWidth, Output(Bool()))
       val empty         = Output(Bool())
       val full          = Output(Bool())
       val freeCount     = Output(UInt(log2Ceil(EnsbufferWidth + 1).W))
@@ -758,6 +760,9 @@ abstract class NewStoreQueueBase(implicit p: Parameters) extends LSQModule {
     io.freeCount := PopCount((~allocated.asUInt).asUInt)
     io.empty     := empty
     io.full      := full
+    io.deqPtrMove.zipWithIndex.foreach{case (sink, i) =>
+      sink := entries(deqPtrVec(i).value).deqPtrMove && io.toSbuffer.req(i).fire
+    }
 
     if(debugEn) {
       dontTouch(deqSameCycle)
@@ -1132,8 +1137,6 @@ abstract class NewStoreQueueBase(implicit p: Parameters) extends LSQModule {
     private val unalignStall     = Wire(Vec(EnsbufferWidth, Bool()))
     private val cboStall         = Wire(Vec(EnsbufferWidth, Bool()))
     private val toSbufferValid   = Wire(Vec(EnsbufferWidth, Bool()))
-    // cross16B will occupy two write port, so only need to use port 0 fire.
-    private val cross16BDeqReg   = RegEnable(headCross16B, writeSbufferWire(0).fire)
 
     // when deq is MMIO/NC/CMO request, don't need to write sbuffer.
     for (i <- 0 until EnsbufferWidth) {
@@ -1175,9 +1178,9 @@ abstract class NewStoreQueueBase(implicit p: Parameters) extends LSQModule {
         // Regarding writing to port 1's Sbuffer, only the following two scenarios permit writing:
         //  1. Port 0 write a unaligned request cross 16 bytes, preempting port 1's write port.
         //  2. Port 0 is ready, and the Sbuffer can process two write requests simultaneously.
-        toSbufferValid(i) := !uncacheStall(i) && !cboStall(i) && ctrlEntry.committed &&
+        toSbufferValid(i) := !uncacheStall(i) && !cboStall(i) && !unalignStall(i) &&  ctrlEntry.committed &&
           !(ctrlEntry.vecMbCommit && !ctrlEntry.allValid || ctrlEntry.vecInactive) && //TODO: vecMbCommit will be remove in the future
-          toSbufferValid(i - 1) || (headCross16B && toSbufferValid(0)) && !unalignStall(i)
+          toSbufferValid(i - 1) || (headCross16B && toSbufferValid(0))
         // [NOTE]: entry.committed contains entry.allocated && entry.allValid && !entry.hasException && isRobHead.
 
         unalignStall(i) := ctrlEntry.cross16Byte && !headCross16B
@@ -1205,6 +1208,12 @@ abstract class NewStoreQueueBase(implicit p: Parameters) extends LSQModule {
       port.bits.wline    := ctrlEntry.isCbo && isCboZero(dataEntry.cboType)
       port.bits.prefetch := dataEntry.prefetch
       port.bits.vecValid := true.B
+      if (i == 0) { // if cross16B, only port 1 deqPtr move, else both port 0 and port 1 deqPtr move.
+        port.bits.deqPtrMove := !headCross16B
+      }
+      else {
+        port.bits.deqPtrMove := true.B
+      }
       port.valid         := toSbufferValid(i)
 
       XSError(ctrlEntry.vecInactive && !ctrlEntry.isVec, s"inactive element must be vector! ${i}")
@@ -1220,15 +1229,12 @@ abstract class NewStoreQueueBase(implicit p: Parameters) extends LSQModule {
 
     /*============================================ deqPtr generate ===================================================*/
     /*
-    * NOTE: Only when port 0 and port 1 are ready can write cross16B request, so only use io.writeToSbuffer.req.head.fire
-    *       to calculate sbufferFireNum.
+    * NOTE: A write request only contributes a SQ dequeue credit after it actually fires from EnterSbufferQueue to sbuffer.
     * deqPtr will move when [write to sbuffer / writeback / vector inactive element]
     * rdataPtr will move when [nc request fire / write to SQ2SBPipelineConnect_i / vector inactive element]
     * NOTE: when deq mmio/cbo, rdataPtr === deqPtr, because mmio/cbo need to execute at head of StoreQueue.
     * */
-    private val sbufferFireNum = Mux(cross16BDeqReg,
-      Cat(RegNext(io.writeToSbuffer.req.head.fire), 0.U),
-      Cat(io.writeToSbuffer.req.map{case p => RegNext(p.fire)}))
+    private val deqPtrMoveFromSbuffer = RegNext(dataQueue.io.deqPtrMove)
 
     // [NOTE]: when point a inactive entry, move pointer.
     private val deqPtrVectorInactiveValid = WireInit(VecInit(Seq.fill(EnsbufferWidth)(false.B)))
@@ -1240,13 +1246,13 @@ abstract class NewStoreQueueBase(implicit p: Parameters) extends LSQModule {
 
     private val deqPtrVectorInactiveMove = Cat(deqPtrVectorInactiveValid.zipWithIndex.map{case (v, i) =>
       if(i == 0) v
-      else v && (deqPtrVectorInactiveValid(i - 1) || sbufferFireNum(i - 1).asBool)
+      else v && (deqPtrVectorInactiveValid(i - 1) || deqPtrMoveFromSbuffer(i - 1).asBool)
     })
 
     private val uncacheMove = VecInit(deqCtrlEntries.map(x => x.allocated && x.handleFinish && x.committed)).asUInt
 
-    // sbufferFireNum need to RegNext, because write to sbuffer need 2 cycle, storeQueue need to forward 1 more cycle
-    val deqCount = Cat(sbufferFireNum, deqPtrVectorInactiveMove, uncacheMove) // timing is ok ?
+    // The credit vector needs one more cycle because storeQueue forwarding observes the entry for one additional cycle.
+    val deqCount = Cat(deqPtrMoveFromSbuffer.asUInt, deqPtrVectorInactiveMove, uncacheMove) // timing is ok ?
 
     io.sqDeqCnt := PopCount(VecInit(deqCount).asUInt)
     io.deqPtrExtNext := io.deqPtrExt.map(_ + io.sqDeqCnt)
```
