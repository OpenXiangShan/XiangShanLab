# Commit Log
- Issue: #4611
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/4611
- Issue state: closed
- Tested RTL commit: -
- Related PR: #4611
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/4611
- Changed files: 1
- Additions: 11
- Deletions: 3

## Files
- `src/main/scala/device/AXI4Memory.scala`

## Diff
```diff
diff --git a/src/main/scala/device/AXI4Memory.scala b/src/main/scala/device/AXI4Memory.scala
index d866b0ef5e0..f364c705f22 100644
--- a/src/main/scala/device/AXI4Memory.scala
+++ b/src/main/scala/device/AXI4Memory.scala
@@ -177,10 +177,18 @@ class AXI4MemoryImp[T <: Data](outer: AXI4Memory) extends AXI4SlaveModuleImp(out
   val pending_write_req_data  = RegEnable(in.w.bits, in.w.fire)
   val pending_write_req_ready = Wire(Bool())
   val pending_write_need_req = pending_write_req_valid.last && !pending_write_req_ready
-  val write_req_valid = pending_write_req_valid.head && (pending_write_need_req || in.w.valid && in.w.bits.last)
-  pending_write_req_ready := writeRequest(write_req_valid, pending_write_req_bits.addr, pending_write_req_bits.id)
+  val aw_and_w_last_arrive_at_same_time = in.aw.fire && in.w.fire && in.w.bits.last
+  val w_last_arrive_before_aw = in.aw.fire && pending_write_need_req
+  val aw_arrive_before_w_last = pending_write_req_valid.head && in.w.fire && in.w.bits.last
+  val aw_arrive_before_w = pending_write_req_valid.head && pending_write_need_req
+  val write_req_enq_pending = aw_arrive_before_w || aw_arrive_before_w_last
+  val write_req_enq_no_pending = aw_and_w_last_arrive_at_same_time || w_last_arrive_before_aw
+  val write_req_valid = write_req_enq_pending || write_req_enq_no_pending
+  val write_req_enq_addr = Mux(write_req_enq_pending, pending_write_req_bits.addr, in.aw.bits.addr)
+  val write_req_enq_id = Mux(write_req_enq_pending, pending_write_req_bits.id, in.aw.bits.id)
+  pending_write_req_ready := writeRequest(write_req_valid, write_req_enq_addr, write_req_enq_id)
 
-  when (in.aw.fire) {
+  when (in.aw.fire && !write_req_enq_no_pending) {
     pending_write_req_valid.head := true.B
   }.elsewhen (pending_write_req_ready) {
     pending_write_req_valid.head := false.B
```
