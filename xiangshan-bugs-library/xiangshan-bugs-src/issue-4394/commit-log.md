# Commit Log
- Issue: #4394
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/4394
- Issue state: closed
- Tested RTL commit: -
- Related PR: #4394
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/4394
- Changed files: 1
- Additions: 19
- Deletions: 15

## Files
- `src/main/scala/xiangshan/cache/dcache/mainpipe/MainPipe.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/cache/dcache/mainpipe/MainPipe.scala b/src/main/scala/xiangshan/cache/dcache/mainpipe/MainPipe.scala
index 64b410e7772..01203a3f127 100644
--- a/src/main/scala/xiangshan/cache/dcache/mainpipe/MainPipe.scala
+++ b/src/main/scala/xiangshan/cache/dcache/mainpipe/MainPipe.scala
@@ -484,9 +484,7 @@ class MainPipe(implicit p: Parameters) extends DCacheModule with HasPerfEvents w
   val s3_miss_param = RegEnable(io.refill_info.bits.miss_param, s2_fire_to_s3)
   val s3_miss_dirty = RegEnable(io.refill_info.bits.miss_dirty, s2_fire_to_s3)
   val s3_tag = RegEnable(s2_tag, s2_fire_to_s3)
-  val s3_tag_error = RegEnable(s2_tag_error, s2_fire_to_s3)
   val s3_tag_match = RegEnable(s2_tag_match, s2_fire_to_s3)
-  val s3_flag_error = RegEnable(s2_flag_error, s2_fire_to_s3)
   val s3_coh = RegEnable(s2_coh, s2_fire_to_s3)
   val s3_hit = RegEnable(s2_hit, s2_fire_to_s3)
   val s3_amo_hit = RegEnable(s2_amo_hit, s2_fire_to_s3)
@@ -500,20 +498,11 @@ class MainPipe(implicit p: Parameters) extends DCacheModule with HasPerfEvents w
   val s3_data_quad_word = RegEnable(s2_data_quad_word, s2_fire_to_s3)
   val s3_data = RegEnable(s2_data, s2_fire_to_s3)
   val s3_idx = RegEnable(s2_idx, s2_fire_to_s3)
-  // data_error will be reported by data array 1 cycle after data read resp
-  val s3_l2_error = RegEnable(s2_l2_error, s2_fire)
-  val s3_data_error = Wire(Bool())
-  s3_data_error := Mux(GatedValidRegNextN(s1_fire,2), // ecc check result is generated 2 cycle after read req
-    io.readline_error_delayed && RegNext(s2_may_report_data_error),
-    RegNext(s3_data_error) // do not update s3_data_error if !s1_fire
-  )
   val s3_sc_fail  = Wire(Bool()) // miss or lr mismatch
-  // error signal for amo inst
-  // s3_error = s3_flag_error || s3_tag_error || s3_l2_error || s3_data_error
-  val s3_error = RegEnable(s2_error, 0.U.asTypeOf(s2_error), s2_fire) || s3_data_error
+  val s3_need_replacement = RegEnable(s2_need_replacement, s2_fire_to_s3)
+
   val (_, probe_shrink_param, probe_new_coh) = s3_coh.onProbe(s3_req.probe_param)
   val (_, miss_shrink_param, _) = s3_coh.onCacheControl(M_FLUSH)
-  val s3_need_replacement = RegEnable(s2_need_replacement, s2_fire_to_s3)
 
   val miss_update_meta = s3_req.miss
   val probe_update_meta = s3_req.probe && s3_tag_match && s3_coh =/= probe_new_coh
@@ -538,6 +527,21 @@ class MainPipe(implicit p: Parameters) extends DCacheModule with HasPerfEvents w
 
   val miss_new_coh = ClientMetadata(missCohGen(s3_req.cmd, s3_miss_param, s3_miss_dirty))
 
+  // report ecc error
+  val s3_tag_error = RegEnable(s2_tag_error, false.B, s2_fire)
+  // data_error will be reported by data array 1 cycle after data read resp
+  val s3_data_error = Wire(Bool())
+  s3_data_error := Mux(GatedValidRegNextN(s1_fire, 2), // ecc check result is generated 2 cycle after read req
+    io.readline_error_delayed && RegNext(s2_may_report_data_error),
+    RegNext(s3_data_error) // do not update s3_data_error if !s1_fire
+  )
+  val s3_l2_error = RegEnable(s2_l2_error, false.B, s2_fire)
+  val s3_flag_error = RegEnable(s2_flag_error, false.B, s2_fire)
+  // error signal for amo inst
+  // s3_error = s3_flag_error || s3_tag_error || s3_l2_error || s3_data_error
+  val s3_error = RegEnable(s2_error, 0.U.asTypeOf(s2_error), s2_fire) || s3_data_error
+  val s3_error_paddr = get_block_addr(RegEnable(Cat(s2_tag, get_untag(s2_req.vaddr)), s2_fire))
+
   // LR, SC and AMO
   val debug_sc_fail_addr = RegInit(0.U)
   val debug_sc_fail_cnt  = RegInit(0.U(8.W))
@@ -917,7 +921,7 @@ class MainPipe(implicit p: Parameters) extends DCacheModule with HasPerfEvents w
         io.tag_write.ready
     ) && need_wb
 
-  io.wb.bits.addr := get_block_addr(Mux(s3_tag_error, s3_req.addr, Cat(s3_tag, get_untag(s3_req.vaddr))))
+  io.wb.bits.addr := get_block_addr(Cat(s3_tag, get_untag(s3_req.vaddr)))
   io.wb.bits.param := writeback_param
   io.wb.bits.voluntary := s3_req.miss || s3_req.replace
   io.wb.bits.hasData := writeback_data && !s3_tag_error
@@ -981,7 +985,7 @@ class MainPipe(implicit p: Parameters) extends DCacheModule with HasPerfEvents w
   // only tag_error and data_error will be reported to beu
   // l2_error should not be reported (l2 will report that)
   io.error.bits.report_to_beu := (s3_tag_error || s3_data_error) && RegNext(s2_fire)
-  io.error.bits.paddr := s3_req.addr
+  io.error.bits.paddr := s3_error_paddr
   io.error.bits.source.tag := s3_tag_error
   io.error.bits.source.data := s3_data_error
   io.error.bits.source.l2 := s3_flag_error || s3_l2_error
```
