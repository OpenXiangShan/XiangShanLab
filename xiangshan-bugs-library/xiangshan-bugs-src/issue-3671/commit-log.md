# Commit Log
- Issue: #3671
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/3671
- Issue state: closed
- Tested RTL commit: -
- Related PR: #3671
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/3671
- Changed files: 1
- Additions: 39
- Deletions: 12

## Files
- `src/main/scala/xiangshan/frontend/SC.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/frontend/SC.scala b/src/main/scala/xiangshan/frontend/SC.scala
index 477a0a14fee..f615967c589 100644
--- a/src/main/scala/xiangshan/frontend/SC.scala
+++ b/src/main/scala/xiangshan/frontend/SC.scala
@@ -100,14 +100,6 @@ class SCTable(val nRows: Int, val ctrBits: Int, val histLen: Int)(implicit p: Pa
   table.io.r.req.valid := io.req.valid
   table.io.r.req.bits.setIdx := s0_idx
 
-  val per_br_ctrs_unshuffled = table.io.r.resp.data.sliding(2,2).toSeq.map(VecInit(_))
-  val per_br_ctrs = VecInit((0 until numBr).map(i => Mux1H(
-    UIntToOH(get_phy_br_idx(s1_unhashed_idx, i), numBr),
-    per_br_ctrs_unshuffled
-  )))
-
-  io.resp.ctrs := per_br_ctrs
-
   val update_wdata = Wire(Vec(numBr, SInt(ctrBits.W))) // correspond to physical bridx
   val update_wdata_packed = VecInit(update_wdata.map(Seq.fill(2)(_)).reduce(_++_))
   val updateWayMask = Wire(Vec(2*numBr, Bool())) // correspond to physical bridx
@@ -128,11 +120,46 @@ class SCTable(val nRows: Int, val ctrBits: Int, val histLen: Int)(implicit p: Pa
   }
   val update_idx = getIdx(io.update.pc, update_folded_hist)
 
+  //SCTable dual port SRAM reads and writes to the same address processing
+  val conflict_buffer_valid   = RegInit(false.B)
+  val conflict_buffer_data    = RegInit(0.U.asTypeOf(update_wdata_packed))
+  val conflict_buffer_idx     = RegInit(0.U.asTypeOf(update_idx))
+  val conflict_buffer_waymask = RegInit(0.U.asTypeOf(updateWayMask))
+
+  val write_conflict = update_idx === s0_idx && io.update.mask.reduce(_||_) && io.req.valid
+  val can_write = (conflict_buffer_idx =/= s0_idx || !io.req.valid) && conflict_buffer_valid
+
+  when(write_conflict){
+    conflict_buffer_valid   := true.B
+    conflict_buffer_data    := update_wdata_packed
+    conflict_buffer_idx     := update_idx
+    conflict_buffer_waymask := updateWayMask
+  }
+  when(can_write){
+    conflict_buffer_valid   := false.B
+  }
+
+  //Using buffer data for prediction
+  val use_conflict_data = conflict_buffer_valid && conflict_buffer_idx === s1_idx
+  val conflict_data_bypass = conflict_buffer_data.zip(conflict_buffer_waymask).map {case (data, mask) => Mux(mask, data, 0.U.asTypeOf(data))}
+  val conflict_prediction_data = conflict_data_bypass.sliding(2,2).toSeq.map(VecInit(_))
+  val per_br_ctrs_unshuffled = table.io.r.resp.data.sliding(2,2).toSeq.map(VecInit(_))
+  val per_br_ctrs = VecInit((0 until numBr).map(i => Mux1H(
+    UIntToOH(get_phy_br_idx(s1_unhashed_idx, i), numBr),
+    per_br_ctrs_unshuffled
+  )))
+  val conflict_br_ctrs = VecInit((0 until numBr).map(i => Mux1H(
+    UIntToOH(get_phy_br_idx(s1_unhashed_idx, i), numBr),
+    conflict_prediction_data
+  )))
+
+  io.resp.ctrs := Mux(use_conflict_data, conflict_br_ctrs, per_br_ctrs)
+
   table.io.w.apply(
-    valid = io.update.mask.reduce(_||_),
-    data = update_wdata_packed,
-    setIdx = update_idx,
-    waymask = updateWayMask.asUInt
+    valid = (io.update.mask.reduce(_||_) && !write_conflict) || can_write,
+    data = Mux(can_write, conflict_buffer_data, update_wdata_packed),
+    setIdx = Mux(can_write, conflict_buffer_idx, update_idx),
+    waymask = Mux(can_write, conflict_buffer_waymask.asUInt, updateWayMask.asUInt)
   )
 
   val wrBypassEntries = 16
```
