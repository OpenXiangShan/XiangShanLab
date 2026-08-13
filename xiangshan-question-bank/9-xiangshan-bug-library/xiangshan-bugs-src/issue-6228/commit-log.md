# Commit Log
- Issue: #6228
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/6228
- Issue state: closed
- Tested RTL commit: -
- Related PR: #6228
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/6228
- Changed files: 1
- Additions: 12
- Deletions: 6

## Files
- `src/main/scala/xiangshan/mem/lsqueue/NewStoreQueue.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/mem/lsqueue/NewStoreQueue.scala b/src/main/scala/xiangshan/mem/lsqueue/NewStoreQueue.scala
index 8f88aa04e1d..42239ad2d3a 100644
--- a/src/main/scala/xiangshan/mem/lsqueue/NewStoreQueue.scala
+++ b/src/main/scala/xiangshan/mem/lsqueue/NewStoreQueue.scala
@@ -1102,6 +1102,7 @@ abstract class PhysicalStoreQueueBase(implicit p: Parameters) extends LSQModule
     private val writeSbufferPaddr = Wire(Vec(EnsbufferWidth , UInt(PAddrBits.W)))
     private val writeSbufferVaddr = Wire(Vec(EnsbufferWidth , UInt(VAddrBits.W)))
     private val headCross16B      = headCtrlEntry.cross16Byte
+    private val headIsCboZero     = headCtrlEntry.isCbo && isCboZero(headDataEntry.cboType)
     private val headCrossPage     = headrdataPtr === io.fromUnalignQueue.bits.sqIdx && io.fromUnalignQueue.valid
     private val diffIsHighPart    = Wire(Vec(EnsbufferWidth, Bool())) //only for difftest
 
@@ -1161,7 +1162,8 @@ abstract class PhysicalStoreQueueBase(implicit p: Parameters) extends LSQModule
 
       if(i == 0) {
         uncacheStall(i) := !isCacheable(dataEntry.memoryType)
-        cboStall(i)     := ctrlEntry.isCbo && !isCboZero(dataEntry.cboType)
+        // sq need to write zero to sbuffer && dataQueue is empty => write zero
+        cboStall(i)     := ctrlEntry.isCbo && !(cboState === CboState.writeZero && dataQueue.io.empty)
       }
       else {
         uncacheStall(i) := !isCacheable(dataEntry.memoryType) || uncacheStall(i - 1)
@@ -1221,8 +1223,10 @@ abstract class PhysicalStoreQueueBase(implicit p: Parameters) extends LSQModule
       port.bits.wline    := ctrlEntry.isCbo && isCboZero(dataEntry.cboType)
       port.bits.prefetch := dataEntry.prefetch
       port.bits.vecValid := true.B
-      if (i == 0) { // if cross16B, only port 1 deqPtr move, else both port 0 and port 1 deqPtr move.
-        port.bits.deqPtrMove := !headCross16B
+      if (i == 0) {
+        // if cross16B, only port 1 deqPtr move, else both port 0 and port 1 deqPtr move.
+        // if is cbo.zero, shouldn't move Ptr when enter sbuffer.
+        port.bits.deqPtrMove := !headCross16B && !headIsCboZero
       }
       else {
         port.bits.deqPtrMove := true.B
@@ -1270,9 +1274,11 @@ abstract class PhysicalStoreQueueBase(implicit p: Parameters) extends LSQModule
     io.sqDeqCnt := PopCount(VecInit(deqCount).asUInt)
     io.deqPtrExtNext := io.deqPtrExt.map(_ + io.sqDeqCnt)
 
-    private val pipelineConnectFireNum = Mux(headCross16B,
-      Cat(writeSbufferWire.head.fire, 0.U),
-      Cat(writeSbufferWire.map(_.fire)))
+    // TODO: cbo.zereo shouldn't write zero to sbuffer, it should be managed by next level cache.
+    private val pipelineConnectFireNum = Mux(headIsCboZero, 0.U,
+      Mux(headCross16B,
+        Cat(writeSbufferWire.head.fire, 0.U),
+        Cat(writeSbufferWire.map(_.fire))))
     // nc/mmio/cbo deq
     val ncMove = uncacheState === UncacheState.waitReqAck &&
       io.toUncacheBuffer.idResp.valid && !io.toUncacheBuffer.idResp.bits.is2lq
```
