# Commit Log
- Issue: #4753
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/4753
- Issue state: closed
- Tested RTL commit: -
- Related PR: #4753
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/4753
- Changed files: 1
- Additions: 11
- Deletions: 8

## Files
- `src/main/scala/xiangshan/cache/dcache/mainpipe/MainPipe.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/cache/dcache/mainpipe/MainPipe.scala b/src/main/scala/xiangshan/cache/dcache/mainpipe/MainPipe.scala
index 9c11eb80a5d..30de764a2cc 100644
--- a/src/main/scala/xiangshan/cache/dcache/mainpipe/MainPipe.scala
+++ b/src/main/scala/xiangshan/cache/dcache/mainpipe/MainPipe.scala
@@ -425,15 +425,15 @@ class MainPipe(implicit p: Parameters) extends DCacheModule with HasPerfEvents w
   val s2_real_tag_has_error = dcacheParameters.tagCode.decode(RegEnable(s1_real_tag, s1_fire)).error
   val s2_refill_tag_eq_way = s2_has_pesudo_inj && s2_has_real_tag_eq_way & !s2_real_tag_has_error
 
-  val s2_need_pre_replacement = RegEnable(s1_need_replacement, s1_fire)
-  val s2_need_pre_eviction = RegEnable(s1_need_eviction, s1_fire)
+  val s2_need_replacement = RegEnable(s1_need_replacement, s1_fire)
+  val s2_need_eviction = RegEnable(s1_need_eviction, s1_fire)
   val s2_need_data = RegEnable(s1_need_data, s1_fire)
   val s2_need_tag = RegEnable(s1_need_tag, s1_fire)
   val s2_idx = get_idx(s2_req.vaddr)
 
   val s2_way_en = RegEnable(s1_way_en, s1_fire)
-  val s2_tag = Mux(s2_need_pre_replacement, s2_repl_tag, RegEnable(s1_tag, s1_fire))
-  val s2_coh = Mux(s2_need_pre_replacement, s2_repl_coh, RegEnable(s1_coh, s1_fire))
+  val s2_tag = Mux(s2_need_replacement, s2_repl_tag, RegEnable(s1_tag, s1_fire))
+  val s2_coh = Mux(s2_need_replacement, s2_repl_coh, RegEnable(s1_coh, s1_fire))
   val s2_banked_store_wmask = RegEnable(s1_banked_store_wmask, s1_fire)
   val s2_flag_error = RegEnable(s1_flag_error, s1_fire)
   val s2_tag_error = WireInit(false.B)
@@ -441,7 +441,7 @@ class MainPipe(implicit p: Parameters) extends DCacheModule with HasPerfEvents w
   val s2_error = s2_flag_error || s2_tag_error || s2_l2_error // data_error not included
 
   val s2_may_report_data_error = s2_need_data && s2_coh.state =/= ClientStates.Nothing
- 
+
   val s2_hit = (s2_tag_match || s2_refill_tag_eq_way) && s2_has_permission && !s2_refill_with_tag_error
   val s2_sc = s2_req.cmd === M_XSC
   val s2_lr = s2_req.cmd === M_XLR
@@ -450,7 +450,10 @@ class MainPipe(implicit p: Parameters) extends DCacheModule with HasPerfEvents w
   val s2_should_not_report_ecc_error = !s2_req.miss && (s2_req.isAMO && !s2_lr || s2_req.isStore)
 
   if(EnableTagEcc) {
-    s2_tag_error := (s2_tag_errors & s2_way_en).orR && s2_need_tag
+    val s2_probe_or_atomic = (s2_req.probe || s2_req.isAMO && !s2_sc) && !s2_req.miss
+    val s2_probe_atomic_tag_error = s2_probe_or_atomic && !s2_tag_match && s2_tag_errors.orR
+    val s2_evict_tag_error = !s2_probe_or_atomic && (s2_tag_errors & s2_way_en).orR
+    s2_tag_error := (s2_probe_atomic_tag_error || s2_evict_tag_error) && s2_need_tag
   }
 
   s2_s0_set_conlict := s2_valid && s0_idx === s2_idx
@@ -543,7 +546,7 @@ class MainPipe(implicit p: Parameters) extends DCacheModule with HasPerfEvents w
   }))(s3_req.word_idx)
 
   val s3_sc_fail  = Wire(Bool()) // miss or lr mismatch
-  val s3_need_replacement = RegEnable(s2_need_pre_replacement && !s2_refill_tag_eq_way, s2_fire_to_s3)
+  val s3_need_replacement = RegEnable(s2_need_replacement && !s2_refill_tag_eq_way, s2_fire_to_s3)
 
   val (_, probe_shrink_param, probe_new_coh) = s3_coh.onProbe(s3_req.probe_param)
   val (_, miss_shrink_param, _) = s3_coh.onCacheControl(M_FLUSH)
@@ -935,7 +938,7 @@ class MainPipe(implicit p: Parameters) extends DCacheModule with HasPerfEvents w
   XSPerfAccumulate("fake_tag_write_intend", io.tag_write_intend && !io.tag_write.valid)
   XSPerfAccumulate("mainpipe_tag_write", io.tag_write.valid)
 
-  io.replace_addr.valid := s2_valid && s2_need_pre_eviction && !s2_refill_tag_eq_way
+  io.replace_addr.valid := s2_valid && s2_need_eviction && !s2_refill_tag_eq_way
   io.replace_addr.bits  := get_block_addr(Cat(s2_tag, get_untag(s2_req.vaddr)))
 
   io.evict_set := addr_to_dcache_set(s2_req.vaddr) // only use set index
```
