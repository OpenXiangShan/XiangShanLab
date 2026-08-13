# Commit Log
- Issue: #3499
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/3499
- Issue state: closed
- Tested RTL commit: -
- Related PR: #3499
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/3499
- Changed files: 1
- Additions: 16
- Deletions: 13

## Files
- `src/main/scala/xiangshan/frontend/NewFtq.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/frontend/NewFtq.scala b/src/main/scala/xiangshan/frontend/NewFtq.scala
index 32cc149b87a..45516914aa1 100644
--- a/src/main/scala/xiangshan/frontend/NewFtq.scala
+++ b/src/main/scala/xiangshan/frontend/NewFtq.scala
@@ -784,8 +784,10 @@ class Ftq(implicit p: Parameters) extends XSModule with HasCircularQueuePtrHelpe
 
   val toICachePcBundle = Wire(Vec(copyNum,new Ftq_RF_Components))
   val toICacheEntryToSend = Wire(Vec(copyNum,Bool()))
-  val toPrefetchPcBundle = Wire(new Ftq_RF_Components)
-  val toPrefetchEntryToSend = Wire(Bool())
+  val nextCycleToPrefetchPcBundle = Wire(new Ftq_RF_Components)
+  val nextCycleToPrefetchEntryToSend = Wire(Bool())
+  val toPrefetchPcBundle = RegNext(nextCycleToPrefetchPcBundle)
+  val toPrefetchEntryToSend = RegNext(nextCycleToPrefetchEntryToSend)
   val toIfuPcBundle = Wire(new Ftq_RF_Components)
   val entry_is_to_send = WireInit(entry_fetch_status(ifuPtr.value) === f_to_send)
   val entry_ftq_offset = WireInit(cfiIndex_vec(ifuPtr.value))
@@ -811,17 +813,18 @@ class Ftq(implicit p: Parameters) extends XSModule with HasCircularQueuePtrHelpe
     }
   }
 
-  when(last_cycle_bpu_in && bpu_in_bypass_ptr === pfPtr){
-    toPrefetchPcBundle      := bpu_in_bypass_buf
-    toPrefetchEntryToSend   := true.B
-  }.elsewhen(last_cycle_to_pf_fire){
-    toPrefetchPcBundle      := RegNext(ftq_pc_mem.io.pfPtrPlus1_rdata)
-    toPrefetchEntryToSend   := RegNext(entry_fetch_status(pfPtrPlus1.value) === f_to_send) ||
-                               RegNext(last_cycle_bpu_in && bpu_in_bypass_ptr === (pfPtrPlus1))
-  }.otherwise{
-    toPrefetchPcBundle      := RegNext(ftq_pc_mem.io.pfPtr_rdata)
-    toPrefetchEntryToSend   := RegNext(entry_fetch_status(pfPtr.value) === f_to_send) ||
-                               RegNext(last_cycle_bpu_in && bpu_in_bypass_ptr === pfPtr) // reduce potential bubbles
+  // Calculate requests sent to prefetcher one cycle in advance to cut critical path
+  when(bpu_in_fire && bpu_in_resp_ptr === pfPtr_write) {
+    nextCycleToPrefetchPcBundle    := ftq_pc_mem.io.wdata
+    nextCycleToPrefetchEntryToSend := true.B
+  }.elsewhen(io.toPrefetch.req.fire) {
+    nextCycleToPrefetchPcBundle    := ftq_pc_mem.io.pfPtrPlus1_rdata
+    nextCycleToPrefetchEntryToSend := entry_fetch_status(pfPtrPlus1.value) === f_to_send ||
+      last_cycle_bpu_in && bpu_in_bypass_ptr === pfPtrPlus1
+  }.otherwise {
+    nextCycleToPrefetchPcBundle    := ftq_pc_mem.io.pfPtr_rdata
+    nextCycleToPrefetchEntryToSend := entry_fetch_status(pfPtr.value) === f_to_send ||
+      last_cycle_bpu_in && bpu_in_bypass_ptr === pfPtr // reduce potential bubbles
   }
 
   // TODO: reconsider target address bypass logic
```
