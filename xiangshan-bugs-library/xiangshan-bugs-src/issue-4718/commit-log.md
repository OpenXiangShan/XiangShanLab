# Commit Log
- Issue: #4718
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/4718
- Issue state: closed
- Tested RTL commit: -
- Related PR: #4718
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/4718
- Changed files: 2
- Additions: 29
- Deletions: 22

## Files
- `src/main/scala/xiangshan/cache/dcache/mainpipe/MainPipe.scala`
- `src/main/scala/xiangshan/cache/dcache/mainpipe/MissQueue.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/cache/dcache/mainpipe/MainPipe.scala b/src/main/scala/xiangshan/cache/dcache/mainpipe/MainPipe.scala
index 49e8f0817b2..a8c580e278e 100644
--- a/src/main/scala/xiangshan/cache/dcache/mainpipe/MainPipe.scala
+++ b/src/main/scala/xiangshan/cache/dcache/mainpipe/MainPipe.scala
@@ -350,12 +350,9 @@ class MainPipe(implicit p: Parameters) extends DCacheModule with HasPerfEvents w
   val s1_tag_ecc_eq_way = wayMap((w: Int) => s1_tag_eq_way(w) && !s1_tag_errors(w)).asUInt
   val s1_tag_ecc_match_way = wayMap((w: Int) => s1_tag_ecc_eq_way(w) && s1_meta_valids(w)).asUInt
   val s1_tag_match = ParallelORR(s1_tag_ecc_match_way)
-  val s1_real_tag_match_way = Wire(UInt(nWays.W))
-  s1_real_tag_match_way := Mux(
-    GatedValidRegNext(s0_fire),
-    wayMap((w: Int) => io.tag_resp(w)(tagBits - 1, 0) === get_tag(s1_req.addr) && s1_meta_valids(w)).asUInt,
-    RegEnable(s1_real_tag_match_way, 0.U.asTypeOf(s1_real_tag_match_way.cloneType), s1_valid)
-  )
+  val s1_real_tag_eq_way = wayMap((w: Int) => io.tag_resp(w)(tagBits - 1, 0) === get_tag(s1_req.addr) && s1_meta_valids(w)).asUInt
+  val s1_has_real_tag_eq_way = ParallelORR(s1_real_tag_eq_way)
+  val s1_real_tag_match_way = PriorityEncoderOH(s1_real_tag_eq_way)
 
   val s1_hit_tag = get_tag(s1_req.addr)
   val s1_hit_coh = ClientMetadata(ParallelMux(s1_tag_ecc_match_way.asBools, (0 until nWays).map(w => meta_resp(w))))
@@ -373,19 +370,23 @@ class MainPipe(implicit p: Parameters) extends DCacheModule with HasPerfEvents w
   val s1_repl_way_en = WireInit(0.U(nWays.W))
   s1_repl_way_en := Mux(
     GatedValidRegNext(s0_fire),
-    Mux(s1_req.miss_fail_cause_evict_btot, s1_req.occupy_way, UIntToOH(io.replace_way.way)),
+    Mux(
+      io.pseudo_error.valid && s1_has_real_tag_eq_way,
+      s1_real_tag_match_way,
+      Mux(s1_req.miss_fail_cause_evict_btot, s1_req.occupy_way, UIntToOH(io.replace_way.way)
+    )),
     RegEnable(s1_repl_way_en, s1_valid)
   )
   val s1_repl_tag = ParallelMux(s1_repl_way_en.asBools, (0 until nWays).map(w => tag_resp(w)))
   val s1_repl_coh = ParallelMux(s1_repl_way_en.asBools, (0 until nWays).map(w => meta_resp(w))).asTypeOf(new ClientMetadata)
   val s1_repl_pf  = ParallelMux(s1_repl_way_en.asBools, (0 until nWays).map(w => io.extra_meta_resp(w).prefetch))
 
+  val s1_real_tag = ParallelMux(s1_repl_way_en.asBools, (0 until nWays).map(w => io.tag_resp(w)))
+
   val s1_need_replacement = s1_req.miss && !s1_tag_match
   val s1_need_eviction = s1_req.miss && !s1_tag_match && s1_repl_coh.state =/= ClientStates.Nothing
 
-  val s1_no_error_way_en = Mux(s1_need_replacement, s1_repl_way_en, s1_real_tag_match_way)
-  val s1_error_way_en = Mux(ParallelORR(s1_real_tag_match_way), s1_real_tag_match_way, s1_repl_way_en)
-  val s1_way_en = Mux(io.pseudo_error.valid, s1_error_way_en, s1_no_error_way_en)
+  val s1_way_en = Mux(io.pseudo_error.valid || s1_need_replacement, s1_repl_way_en, s1_tag_ecc_match_way)
   assert(!RegNext(s1_fire && PopCount(s1_way_en) > 1.U))
 
   val s1_tag = s1_hit_tag
@@ -405,7 +406,7 @@ class MainPipe(implicit p: Parameters) extends DCacheModule with HasPerfEvents w
   val s2_req = RegEnable(s1_req, s1_fire)
   val s2_tag_errors = RegEnable(s1_tag_errors, s1_fire)
   val s2_tag_match = RegEnable(s1_tag_match, s1_fire)
-  val s2_tag_match_way = RegEnable(s1_real_tag_match_way, s1_fire)
+  val s2_has_real_tag_eq_way = RegEnable(s1_has_real_tag_eq_way, s1_fire)
   val s2_tag_ecc_match_way = RegEnable(s1_tag_ecc_match_way, s1_fire)
   val s2_hit_coh = RegEnable(s1_hit_coh, s1_fire)
   val s2_has_permission = RegEnable(s1_has_permission, s1_fire)
@@ -415,15 +416,20 @@ class MainPipe(implicit p: Parameters) extends DCacheModule with HasPerfEvents w
   val s2_repl_tag = RegEnable(s1_repl_tag, s1_fire)
   val s2_repl_coh = RegEnable(s1_repl_coh, s1_fire)
   val s2_repl_pf  = RegEnable(s1_repl_pf, s1_fire)
-  val s2_need_replacement = RegEnable(s1_need_replacement, s1_fire)
-  val s2_need_eviction = RegEnable(s1_need_eviction, s1_fire)
+
+  val s2_has_pesudo_inj = RegEnable(io.pseudo_error.valid, false.B, s1_fire)
+  val s2_real_tag_has_error = dcacheParameters.tagCode.decode(RegEnable(s1_real_tag, s1_fire)).error
+  val s2_refill_tag_eq_way = s2_has_pesudo_inj && s2_has_real_tag_eq_way & !s2_real_tag_has_error
+
+  val s2_need_pre_replacement = RegEnable(s1_need_replacement, s1_fire)
+  val s2_need_pre_eviction = RegEnable(s1_need_eviction, s1_fire)
   val s2_need_data = RegEnable(s1_need_data, s1_fire)
   val s2_need_tag = RegEnable(s1_need_tag, s1_fire)
   val s2_idx = get_idx(s2_req.vaddr)
 
   val s2_way_en = RegEnable(s1_way_en, s1_fire)
-  val s2_tag = Mux(s2_need_replacement, s2_repl_tag, RegEnable(s1_tag, s1_fire))
-  val s2_coh = Mux(s2_need_replacement, s2_repl_coh, RegEnable(s1_coh, s1_fire))
+  val s2_tag = Mux(s2_need_pre_replacement, s2_repl_tag, RegEnable(s1_tag, s1_fire))
+  val s2_coh = Mux(s2_need_pre_replacement, s2_repl_coh, RegEnable(s1_coh, s1_fire))
   val s2_banked_store_wmask = RegEnable(s1_banked_store_wmask, s1_fire)
   val s2_flag_error = RegEnable(s1_flag_error, s1_fire)
   val s2_tag_error = WireInit(false.B)
@@ -432,7 +438,7 @@ class MainPipe(implicit p: Parameters) extends DCacheModule with HasPerfEvents w
 
   val s2_may_report_data_error = s2_need_data && s2_coh.state =/= ClientStates.Nothing
 
-  val s2_hit = s2_tag_match && s2_has_permission
+  val s2_hit = (s2_tag_match || s2_refill_tag_eq_way) && s2_has_permission
   val s2_sc = s2_req.cmd === M_XSC
   val s2_lr = s2_req.cmd === M_XLR
   val s2_amo_hit = s2_hit && !s2_req.probe && !s2_req.miss && s2_req.isAMO
@@ -440,7 +446,7 @@ class MainPipe(implicit p: Parameters) extends DCacheModule with HasPerfEvents w
   val s2_should_not_report_ecc_error = !s2_req.miss && (s2_req.isAMO && !s2_lr || s2_req.isStore)
 
   if(EnableTagEcc) {
-    s2_tag_error := s2_tag_errors.orR && s2_need_tag
+    s2_tag_error := (s2_tag_errors & s2_way_en).orR && s2_need_tag
   }
 
   s2_s0_set_conlict := s2_valid && s0_idx === s2_idx
@@ -455,12 +461,13 @@ class MainPipe(implicit p: Parameters) extends DCacheModule with HasPerfEvents w
   )
 
   // For a store req, it either hits and goes to s3, or miss and enter miss queue immediately
+  val s2_replace_block = io.replace_block && io.replace_addr.valid
   val s2_req_miss_without_data = Mux(s2_valid, s2_req.miss && !io.refill_info.valid, false.B)
   val s2_can_go_to_mq_no_data = (s2_req_miss_without_data && RegEnable(s2_req_miss_without_data && !io.mainpipe_info.s2_replay_to_mq, false.B, s2_valid)) // miss_req in s2 but refill data is invalid, can block 1 cycle
-  val s2_can_go_to_mq_evict_fail = io.replace_block // dcache and miss queue both occupy the same set, (BtoT scheme)
+  val s2_can_go_to_mq_evict_fail = s2_replace_block // dcache and miss queue both occupy the same set, (BtoT scheme)
   val s2_can_go_to_mq_replay = s2_can_go_to_mq_no_data || s2_can_go_to_mq_evict_fail
   val s2_can_go_to_mq = RegEnable(s1_pregen_can_go_to_mq, s1_fire)
-  val s2_can_go_to_s3 = (s2_sc || s2_req.replace || s2_req.probe || (s2_req.miss && io.refill_info.valid && !io.replace_block) || (s2_req.isStore || s2_req.isAMO) && s2_hit) && s3_ready
+  val s2_can_go_to_s3 = (s2_sc || s2_req.replace || s2_req.probe || (s2_req.miss && io.refill_info.valid && !s2_replace_block) || (s2_req.isStore || s2_req.isAMO) && s2_hit) && s3_ready
   assert(RegNext(!(s2_valid && s2_can_go_to_s3 && s2_can_go_to_mq && s2_can_go_to_mq_replay)))
   val s2_can_go = s2_can_go_to_s3 || s2_can_go_to_mq || s2_can_go_to_mq_replay
   val s2_fire = s2_valid && s2_can_go
@@ -532,7 +539,7 @@ class MainPipe(implicit p: Parameters) extends DCacheModule with HasPerfEvents w
   }))(s3_req.word_idx)
 
   val s3_sc_fail  = Wire(Bool()) // miss or lr mismatch
-  val s3_need_replacement = RegEnable(s2_need_replacement, s2_fire_to_s3)
+  val s3_need_replacement = RegEnable(s2_need_pre_replacement && !s2_refill_tag_eq_way, s2_fire_to_s3)
 
   val (_, probe_shrink_param, probe_new_coh) = s3_coh.onProbe(s3_req.probe_param)
   val (_, miss_shrink_param, _) = s3_coh.onCacheControl(M_FLUSH)
@@ -917,7 +924,7 @@ class MainPipe(implicit p: Parameters) extends DCacheModule with HasPerfEvents w
   XSPerfAccumulate("fake_tag_write_intend", io.tag_write_intend && !io.tag_write.valid)
   XSPerfAccumulate("mainpipe_tag_write", io.tag_write.valid)
 
-  io.replace_addr.valid := s2_valid && s2_need_eviction
+  io.replace_addr.valid := s2_valid && s2_need_pre_eviction && !s2_refill_tag_eq_way
   io.replace_addr.bits  := get_block_addr(Cat(s2_tag, get_untag(s2_req.vaddr)))
 
   io.evict_set := addr_to_dcache_set(s2_req.vaddr) // only use set index
diff --git a/src/main/scala/xiangshan/cache/dcache/mainpipe/MissQueue.scala b/src/main/scala/xiangshan/cache/dcache/mainpipe/MissQueue.scala
index 87665b8a1c0..e8f54ec0623 100644
--- a/src/main/scala/xiangshan/cache/dcache/mainpipe/MissQueue.scala
+++ b/src/main/scala/xiangshan/cache/dcache/mainpipe/MissQueue.scala
@@ -1220,7 +1220,7 @@ class MissQueue(edge: TLEdgeOut, reqNum: Int)(implicit p: Parameters) extends DC
 
   io.probe_block := Cat(probe_block_vec).orR
 
-  io.replace_block := io.replace_addr.valid && Cat(entries.map(e => e.io.req_addr.valid && e.io.req_addr.bits === io.replace_addr.bits) ++ Seq(miss_req_pipe_reg.block_match(io.replace_addr.bits))).orR
+  io.replace_block := Cat(entries.map(e => e.io.req_addr.valid && e.io.req_addr.bits === io.replace_addr.bits) ++ Seq(miss_req_pipe_reg.block_match(io.replace_addr.bits))).orR
   val btot_evict_set_hit = entries.map(e => e.io.req_isBtoT && e.io.req_vaddr.valid && addr_to_dcache_set(e.io.req_vaddr.bits) === io.evict_set) ++
     Seq(miss_req_pipe_reg.evict_set_match(io.evict_set))
   val btot_occupy_ways = entries.map(e => e.io.occupy_way) ++ Seq(miss_req_pipe_reg.req.occupy_way)
```
