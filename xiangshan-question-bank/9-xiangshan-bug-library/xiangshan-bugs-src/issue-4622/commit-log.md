# Commit Log
- Issue: #4622
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/4622
- Issue state: closed
- Tested RTL commit: -
- Related PR: #4622
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/4622
- Changed files: 5
- Additions: 100
- Deletions: 15

## Files
- `src/main/scala/xiangshan/cache/dcache/DCacheWrapper.scala`
- `src/main/scala/xiangshan/cache/dcache/mainpipe/MainPipe.scala`
- `src/main/scala/xiangshan/cache/dcache/mainpipe/MissQueue.scala`
- `src/main/scala/xiangshan/cache/dcache/mainpipe/Probe.scala`
- `src/main/scala/xiangshan/mem/pipeline/AtomicsUnit.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/cache/dcache/DCacheWrapper.scala b/src/main/scala/xiangshan/cache/dcache/DCacheWrapper.scala
index 214bd6c9c3d..d3829138376 100644
--- a/src/main/scala/xiangshan/cache/dcache/DCacheWrapper.scala
+++ b/src/main/scala/xiangshan/cache/dcache/DCacheWrapper.scala
@@ -1516,6 +1516,8 @@ class DCacheImp(outer: DCache) extends LazyModuleImp(outer) with HasDCacheParame
   bus.e <> missQueue.io.mem_finish
   missQueue.io.probe_addr := bus.b.bits.address
   missQueue.io.replace_addr := mainPipe.io.replace_addr
+  missQueue.io.evict_set := mainPipe.io.evict_set
+  missQueue.io.btot_ways_for_set <> mainPipe.io.btot_ways_for_set
 
   missQueue.io.main_pipe_resp.valid := RegNext(mainPipe.io.atomic_resp.valid)
   missQueue.io.main_pipe_resp.bits := RegEnable(mainPipe.io.atomic_resp.bits, mainPipe.io.atomic_resp.valid)
diff --git a/src/main/scala/xiangshan/cache/dcache/mainpipe/MainPipe.scala b/src/main/scala/xiangshan/cache/dcache/mainpipe/MainPipe.scala
index 4682ddf4926..49e8f0817b2 100644
--- a/src/main/scala/xiangshan/cache/dcache/mainpipe/MainPipe.scala
+++ b/src/main/scala/xiangshan/cache/dcache/mainpipe/MainPipe.scala
@@ -34,6 +34,8 @@ class MainPipeReq(implicit p: Parameters) extends DCacheBundle {
   val miss_id = UInt(log2Up(cfg.nMissEntries).W)
   val miss_param = UInt(TLPermissions.bdWidth.W)
   val miss_dirty = Bool()
+  val occupy_way = UInt(nWays.W)
+  val miss_fail_cause_evict_btot = Bool()
 
   val probe = Bool()
   val probe_param = UInt(TLPermissions.bdWidth.W)
@@ -95,6 +97,7 @@ class MainPipeReq(implicit p: Parameters) extends DCacheBundle {
     req.replace := false.B
     req.error := false.B
     req.id := store.id
+    req.miss_fail_cause_evict_btot := false.B
     req
   }
 }
@@ -108,6 +111,8 @@ class MainPipeInfoToMQ(implicit p:Parameters) extends DCacheBundle {
   val s2_valid = Bool()
   val s2_miss_id = UInt(log2Up(cfg.nMissEntries).W) // For refill data selection
   val s2_replay_to_mq = Bool()
+  val s2_evict_BtoT_way = Bool()
+  val s2_next_evict_way = UInt(nWays.W)
   val s3_valid = Bool()
   val s3_miss_id = UInt(log2Up(cfg.nMissEntries).W) // For mshr release
   val s3_refill_resp = Bool()
@@ -173,6 +178,9 @@ class MainPipe(implicit p: Parameters) extends DCacheModule with HasPerfEvents w
     // find the way to be replaced
     val replace_way = new ReplacementWayReqIO
 
+    val evict_set = Output(UInt())
+    val btot_ways_for_set = Input(UInt(nWays.W))
+
     // writeback addr to be replaced
     val replace_addr = ValidIO(UInt(PAddrBits.W))
     val replace_block = Input(Bool())
@@ -365,16 +373,13 @@ class MainPipe(implicit p: Parameters) extends DCacheModule with HasPerfEvents w
   val s1_repl_way_en = WireInit(0.U(nWays.W))
   s1_repl_way_en := Mux(
     GatedValidRegNext(s0_fire),
-    UIntToOH(io.replace_way.way),
+    Mux(s1_req.miss_fail_cause_evict_btot, s1_req.occupy_way, UIntToOH(io.replace_way.way)),
     RegEnable(s1_repl_way_en, s1_valid)
   )
   val s1_repl_tag = ParallelMux(s1_repl_way_en.asBools, (0 until nWays).map(w => tag_resp(w)))
   val s1_repl_coh = ParallelMux(s1_repl_way_en.asBools, (0 until nWays).map(w => meta_resp(w))).asTypeOf(new ClientMetadata)
   val s1_repl_pf  = ParallelMux(s1_repl_way_en.asBools, (0 until nWays).map(w => io.extra_meta_resp(w).prefetch))
 
-  val s1_repl_way_raw = WireInit(0.U(log2Up(nWays).W))
-  s1_repl_way_raw := Mux(GatedValidRegNext(s0_fire), io.replace_way.way, RegEnable(s1_repl_way_raw, s1_valid))
-
   val s1_need_replacement = s1_req.miss && !s1_tag_match
   val s1_need_eviction = s1_req.miss && !s1_tag_match && s1_repl_coh.state =/= ClientStates.Nothing
 
@@ -389,9 +394,11 @@ class MainPipe(implicit p: Parameters) extends DCacheModule with HasPerfEvents w
   XSPerfAccumulate("store_has_invalid_way_but_select_valid_way", io.replace_way.set.valid && wayMap(w => !meta_resp(w).asTypeOf(new Meta).coh.isValid()).asUInt.orR && s1_need_replacement && s1_repl_coh.isValid())
   XSPerfAccumulate("store_using_replacement", io.replace_way.set.valid && s1_need_replacement)
 
-  val s1_has_permission = s1_hit_coh.onAccess(s1_req.cmd)._1
+  val (s1_has_permission, s1_shrink_perm, s1_new_hit_coh) = s1_hit_coh.onAccess(s1_req.cmd)
   val s1_hit = s1_tag_match && s1_has_permission
-  val s1_pregen_can_go_to_mq = !s1_req.replace && !s1_req.probe && !s1_req.miss && (s1_req.isStore || s1_req.isAMO && s1_req.cmd =/= M_XSC) && !s1_hit
+  val s1_store_or_amo = !s1_req.replace && !s1_req.probe && !s1_req.miss && (s1_req.isStore || s1_req.isAMO && s1_req.cmd =/= M_XSC)
+  val s1_pregen_can_go_to_mq = s1_store_or_amo && !s1_hit
+  val s1_grow_perm = s1_shrink_perm === BtoT && !s1_has_permission
 
   // s2: select data, return resp if this is a store miss
   val s2_valid = RegInit(false.B)
@@ -399,8 +406,11 @@ class MainPipe(implicit p: Parameters) extends DCacheModule with HasPerfEvents w
   val s2_tag_errors = RegEnable(s1_tag_errors, s1_fire)
   val s2_tag_match = RegEnable(s1_tag_match, s1_fire)
   val s2_tag_match_way = RegEnable(s1_real_tag_match_way, s1_fire)
+  val s2_tag_ecc_match_way = RegEnable(s1_tag_ecc_match_way, s1_fire)
   val s2_hit_coh = RegEnable(s1_hit_coh, s1_fire)
-  val (s2_has_permission, _, s2_new_hit_coh) = s2_hit_coh.onAccess(s2_req.cmd)
+  val s2_has_permission = RegEnable(s1_has_permission, s1_fire)
+  val s2_new_hit_coh = RegEnable(s1_new_hit_coh, s1_fire)
+  val s2_grow_perm = RegEnable(s1_grow_perm, s1_fire) && s2_tag_match
 
   val s2_repl_tag = RegEnable(s1_repl_tag, s1_fire)
   val s2_repl_coh = RegEnable(s1_repl_coh, s1_fire)
@@ -436,9 +446,19 @@ class MainPipe(implicit p: Parameters) extends DCacheModule with HasPerfEvents w
   s2_s0_set_conlict := s2_valid && s0_idx === s2_idx
   s2_s0_set_conlict_store := s2_valid && store_idx === s2_idx
 
+  // Grow permission fail
+  // Only in case BtoT will both cache and missqueue be occupied
+  val s2_has_more_then_3_ways_BtoT = PopCount(io.btot_ways_for_set) > (nWays-2).U
+  val s2_grow_perm_fail = s2_has_more_then_3_ways_BtoT && s2_grow_perm
+  XSError(s2_valid && s2_grow_perm && io.btot_ways_for_set.andR,
+    "BtoT grow permission, but all ways are BtoT\n"
+  )
+
   // For a store req, it either hits and goes to s3, or miss and enter miss queue immediately
   val s2_req_miss_without_data = Mux(s2_valid, s2_req.miss && !io.refill_info.valid, false.B)
-  val s2_can_go_to_mq_replay = (s2_req_miss_without_data && RegEnable(s2_req_miss_without_data && !io.mainpipe_info.s2_replay_to_mq, false.B, s2_valid)) || io.replace_block // miss_req in s2 but refill data is invalid, can block 1 cycle
+  val s2_can_go_to_mq_no_data = (s2_req_miss_without_data && RegEnable(s2_req_miss_without_data && !io.mainpipe_info.s2_replay_to_mq, false.B, s2_valid)) // miss_req in s2 but refill data is invalid, can block 1 cycle
+  val s2_can_go_to_mq_evict_fail = io.replace_block // dcache and miss queue both occupy the same set, (BtoT scheme)
+  val s2_can_go_to_mq_replay = s2_can_go_to_mq_no_data || s2_can_go_to_mq_evict_fail
   val s2_can_go_to_mq = RegEnable(s1_pregen_can_go_to_mq, s1_fire)
   val s2_can_go_to_s3 = (s2_sc || s2_req.replace || s2_req.probe || (s2_req.miss && io.refill_info.valid && !io.replace_block) || (s2_req.isStore || s2_req.isAMO) && s2_hit) && s3_ready
   assert(RegNext(!(s2_valid && s2_can_go_to_s3 && s2_can_go_to_mq && s2_can_go_to_mq_replay)))
@@ -799,14 +819,16 @@ class MainPipe(implicit p: Parameters) extends DCacheModule with HasPerfEvents w
   miss_req.amo_cmp  := s2_req.amo_cmp
   miss_req.req_coh := s2_hit_coh
   miss_req.id := s2_req.id
-  miss_req.cancel := false.B
+  miss_req.cancel := s2_grow_perm_fail
   miss_req.pc := DontCare
   miss_req.full_overwrite := s2_req.isStore && s2_req.store_mask.andR
+  miss_req.isBtoT := s2_grow_perm
+  miss_req.occupy_way := s2_tag_ecc_match_way
 
   io.wbq_conflict_check.valid := s2_valid && s2_can_go_to_mq
   io.wbq_conflict_check.bits := s2_req.addr
 
-  io.store_replay_resp.valid := s2_valid && s2_can_go_to_mq && replay && s2_req.isStore
+  io.store_replay_resp.valid := s2_valid && (s2_can_go_to_mq && replay || s2_grow_perm_fail) && s2_req.isStore
   io.store_replay_resp.bits.data := DontCare
   io.store_replay_resp.bits.miss := true.B
   io.store_replay_resp.bits.replay := true.B
@@ -837,7 +859,7 @@ class MainPipe(implicit p: Parameters) extends DCacheModule with HasPerfEvents w
   atomic_replay_resp.ack_miss_queue := false.B
   atomic_replay_resp.id := DontCare
 
-  val atomic_replay_resp_valid = s2_valid && s2_can_go_to_mq && replay && s2_req.isAMO
+  val atomic_replay_resp_valid = s2_valid && (s2_can_go_to_mq && replay || s2_grow_perm_fail) && s2_req.isAMO
   val atomic_hit_resp_valid = s3_valid && (s3_amo_can_go || s3_miss_can_go && s3_req.isAMO)
 
   io.atomic_resp.valid := atomic_replay_resp_valid || atomic_hit_resp_valid
@@ -898,6 +920,8 @@ class MainPipe(implicit p: Parameters) extends DCacheModule with HasPerfEvents w
   io.replace_addr.valid := s2_valid && s2_need_eviction
   io.replace_addr.bits  := get_block_addr(Cat(s2_tag, get_untag(s2_req.vaddr)))
 
+  io.evict_set := addr_to_dcache_set(s2_req.vaddr) // only use set index
+
   assert(!RegNext(io.tag_write.valid && !io.tag_write_intend))
 
   io.data_write.valid := s3_valid && s3_update_data_cango && update_data
@@ -986,10 +1010,13 @@ class MainPipe(implicit p: Parameters) extends DCacheModule with HasPerfEvents w
 
   io.mainpipe_info.s2_valid := s2_valid && s2_req.miss
   io.mainpipe_info.s2_miss_id := s2_req.miss_id
-  io.mainpipe_info.s2_replay_to_mq := s2_valid && s2_can_go_to_mq_replay
+  io.mainpipe_info.s2_replay_to_mq := s2_can_go_to_mq_no_data
+  io.mainpipe_info.s2_evict_BtoT_way := s2_can_go_to_mq_evict_fail
+  io.mainpipe_info.s2_next_evict_way := PriorityEncoderOH(~io.btot_ways_for_set)
   io.mainpipe_info.s3_valid := s3_valid
   io.mainpipe_info.s3_miss_id := s3_req.miss_id
   io.mainpipe_info.s3_refill_resp := RegNext(s2_valid && s2_req.miss && s2_fire_to_s3)
+  XSError(s2_valid && s2_way_en.andR, "s2_way_en should not be all 1")
 
   // report error to beu and csr, 1 cycle after read data resp
   io.error := 0.U.asTypeOf(ValidIO(new L1CacheErrorInfo))
diff --git a/src/main/scala/xiangshan/cache/dcache/mainpipe/MissQueue.scala b/src/main/scala/xiangshan/cache/dcache/mainpipe/MissQueue.scala
index ea01f540059..ae8513136c5 100644
--- a/src/main/scala/xiangshan/cache/dcache/mainpipe/MissQueue.scala
+++ b/src/main/scala/xiangshan/cache/dcache/mainpipe/MissQueue.scala
@@ -65,6 +65,17 @@ class MissReqWoStoreData(implicit p: Parameters) extends DCacheBundle {
   val req_coh = new ClientMetadata
   val id = UInt(reqIdWidth.W)
 
+  /**
+    * isBtoT is used to mark whether the current request requires BtoT permission.
+    * If so, other requests for BtoT in the same set are blocked. Otherwise,
+    * they are not blocked.
+    */
+  val isBtoT = Bool()
+  /**
+    * The way isBtoT requests to occupy
+    */
+  val occupy_way = UInt(nWays.W)
+
   // For now, miss queue entry req is actually valid when req.valid && !cancel
   // * req.valid is fast to generate
   // * cancel is slow to generate, it will not be used until the last moment
@@ -271,6 +282,10 @@ class MissReqPipeRegBundle(edge: TLEdgeOut)(implicit p: Parameters) extends DCac
   def block_match(release_addr: UInt): Bool = {
     reg_valid() && get_block(req.addr) === get_block(release_addr)
   }
+
+  def evict_set_match(evict_set: UInt): Bool = {
+    reg_valid() && req.isBtoT && addr_to_dcache_set(req.vaddr) === evict_set
+  }
 }
 
 class CMOUnit(edge: TLEdgeOut)(implicit p: Parameters) extends DCacheModule {
@@ -374,13 +389,18 @@ class MissEntry(edge: TLEdgeOut, reqNum: Int)(implicit p: Parameters) extends DC
     val main_pipe_resp = Input(Bool())
     val main_pipe_refill_resp = Input(Bool())
     val main_pipe_replay = Input(Bool())
+    val main_pipe_evict_BtoT_way = Input(Bool())
+    val main_pipe_next_evict_way = Input(UInt(nWays.W))
 
     // for main pipe s2
     val refill_info = ValidIO(new MissQueueRefillInfo)
 
     val block_addr = ValidIO(UInt(PAddrBits.W))
+    val occupy_way = Output(UInt(nWays.W))
 
     val req_addr = ValidIO(UInt(PAddrBits.W))
+    val req_vaddr = ValidIO(UInt(VAddrBits.W))
+    val req_isBtoT = Output(Bool())
 
     val req_handled_by_this_entry = Output(Bool())
 
@@ -428,6 +448,7 @@ class MissEntry(edge: TLEdgeOut, reqNum: Int)(implicit p: Parameters) extends DC
   val req_store_mask = Reg(UInt(cfg.blockBytes.W))
   val req_valid = RegInit(false.B)
   val set = addr_to_dcache_set(req.vaddr)
+  val evict_BtoT_way = RegInit(false.B)
   // initial keyword
   val isKeyword = RegInit(false.B)
 
@@ -501,8 +522,11 @@ class MissEntry(edge: TLEdgeOut, reqNum: Int)(implicit p: Parameters) extends DC
     req_valid := true.B
 
     req := miss_req_pipe_reg_bits.toMissReqWoStoreData()
-    req_primary_fire := miss_req_pipe_reg_bits.toMissReqWoStoreData()
+    req.isBtoT := miss_req_pipe_reg_bits.isBtoT
+    req.occupy_way := miss_req_pipe_reg_bits.occupy_way
     req.addr := get_block_addr(miss_req_pipe_reg_bits.addr)
+    req_primary_fire := miss_req_pipe_reg_bits.toMissReqWoStoreData()
+    evict_BtoT_way := false.B
     //only  load miss need keyword
     isKeyword := Mux(miss_req_pipe_reg_bits.isFromLoad, miss_req_pipe_reg_bits.vaddr(5).asBool,false.B)
 
@@ -553,6 +577,9 @@ class MissEntry(edge: TLEdgeOut, reqNum: Int)(implicit p: Parameters) extends DC
 
     when (miss_req_pipe_reg_bits.isFromStore) {
       req := miss_req_pipe_reg_bits
+      req.isBtoT := miss_req_pipe_reg_bits.isBtoT
+      req.occupy_way := miss_req_pipe_reg_bits.occupy_way
+      evict_BtoT_way := false.B
       req.addr := get_block_addr(miss_req_pipe_reg_bits.addr)
       req_store_mask := miss_req_pipe_reg_bits.store_mask
       for (i <- 0 until blockRows) {
@@ -637,9 +664,16 @@ class MissEntry(edge: TLEdgeOut, reqNum: Int)(implicit p: Parameters) extends DC
     mainpipe_req_fired := true.B
   }
 
-  when (io.main_pipe_replay) {
+  when (io.main_pipe_replay || io.main_pipe_evict_BtoT_way) {
     s_mainpipe_req := false.B
   }
+  when (io.main_pipe_replay) {
+    evict_BtoT_way := false.B
+  } .elsewhen (io.main_pipe_evict_BtoT_way) {
+    evict_BtoT_way := true.B
+    req.occupy_way := io.main_pipe_next_evict_way
+  }
+  XSError(req_valid && req.isBtoT && io.main_pipe_evict_BtoT_way, "BtoT request will never evict a way")
 
   when (io.main_pipe_resp) {
     w_mainpipe_resp := true.B
@@ -818,12 +852,19 @@ class MissEntry(edge: TLEdgeOut, reqNum: Int)(implicit p: Parameters) extends DC
   io.main_pipe_req.bits.id := req.id
   io.main_pipe_req.bits.pf_source := req.pf_source
   io.main_pipe_req.bits.access := access
+  io.main_pipe_req.bits.occupy_way := req.occupy_way
+  io.main_pipe_req.bits.miss_fail_cause_evict_btot := evict_BtoT_way
 
   io.block_addr.valid := req_valid && w_grantlast
   io.block_addr.bits := req.addr
 
   io.req_addr.valid := req_valid
-  io.req_addr.bits := req.addr
+  io.req_addr.bits:= req.addr
+  io.req_vaddr.valid := req_valid
+  io.req_vaddr.bits := req.vaddr
+  io.req_isBtoT := req.isBtoT
+
+  io.occupy_way := req.occupy_way
 
   io.refill_info.valid := req_valid && w_grantlast
   io.refill_info.bits.store_data := refill_and_store_data.asUInt
@@ -835,6 +876,7 @@ class MissEntry(edge: TLEdgeOut, reqNum: Int)(implicit p: Parameters) extends DC
   XSPerfAccumulate("miss_refill_mainpipe_req", io.main_pipe_req.fire)
   XSPerfAccumulate("miss_refill_without_hint", io.main_pipe_req.fire && !mainpipe_req_fired && !w_l2hint)
   XSPerfAccumulate("miss_refill_replay", io.main_pipe_replay)
+  XSPerfAccumulate("miss_refill_evict_BtoT_way", io.main_pipe_evict_BtoT_way)
 
   val w_grantfirst_forward_info = Mux(isKeyword, w_grantlast, w_grantfirst)
   val w_grantlast_forward_info = Mux(isKeyword, w_grantfirst, w_grantlast)
@@ -928,6 +970,10 @@ class MissQueue(edge: TLEdgeOut, reqNum: Int)(implicit p: Parameters) extends DC
     val replace_addr = Flipped(ValidIO(UInt(PAddrBits.W)))
     val replace_block = Output(Bool())
 
+    // block all way for set to BtoT
+    val evict_set = Input(UInt())
+    val btot_ways_for_set = Output(UInt(nWays.W))
+
     // req blocked by wbq
     val wbq_block_miss_req = Input(Bool())
 
@@ -1098,6 +1144,8 @@ class MissQueue(edge: TLEdgeOut, reqNum: Int)(implicit p: Parameters) extends DC
 
       e.io.main_pipe_resp := io.main_pipe_resp.valid && io.main_pipe_resp.bits.ack_miss_queue && io.main_pipe_resp.bits.miss_id === i.U
       e.io.main_pipe_replay := io.mainpipe_info.s2_valid && io.mainpipe_info.s2_replay_to_mq && io.mainpipe_info.s2_miss_id === i.U
+      e.io.main_pipe_evict_BtoT_way := io.mainpipe_info.s2_valid && io.mainpipe_info.s2_evict_BtoT_way && io.mainpipe_info.s2_miss_id === i.U
+      e.io.main_pipe_next_evict_way := io.mainpipe_info.s2_next_evict_way
       e.io.main_pipe_refill_resp := io.mainpipe_info.s3_valid && io.mainpipe_info.s3_refill_resp && io.mainpipe_info.s3_miss_id === i.U
 
       e.io.memSetPattenDetected := memSetPattenDetected
@@ -1150,6 +1198,12 @@ class MissQueue(edge: TLEdgeOut, reqNum: Int)(implicit p: Parameters) extends DC
   io.probe_block := Cat(probe_block_vec).orR
 
   io.replace_block := io.replace_addr.valid && Cat(entries.map(e => e.io.req_addr.valid && e.io.req_addr.bits === io.replace_addr.bits) ++ Seq(miss_req_pipe_reg.block_match(io.replace_addr.bits))).orR
+  val btot_evict_set_hit = entries.map(e => e.io.req_isBtoT && e.io.req_vaddr.valid && addr_to_dcache_set(e.io.req_vaddr.bits) === io.evict_set) ++
+    Seq(miss_req_pipe_reg.evict_set_match(io.evict_set))
+  val btot_occupy_ways = entries.map(e => e.io.occupy_way) ++ Seq(miss_req_pipe_reg.req.occupy_way)
+  io.btot_ways_for_set := btot_evict_set_hit.zip(btot_occupy_ways).map {
+    case (hit, way) => Fill(nWays, hit) & way
+  }.reduce(_|_)
 
   io.full := ~Cat(entries.map(_.io.primary_ready)).andR
 
diff --git a/src/main/scala/xiangshan/cache/dcache/mainpipe/Probe.scala b/src/main/scala/xiangshan/cache/dcache/mainpipe/Probe.scala
index c09ca5781a1..8fa316a0c43 100644
--- a/src/main/scala/xiangshan/cache/dcache/mainpipe/Probe.scala
+++ b/src/main/scala/xiangshan/cache/dcache/mainpipe/Probe.scala
@@ -105,6 +105,7 @@ class ProbeEntry(implicit p: Parameters) extends DCacheModule {
     pipe_req.probe_need_data := req.needData
     pipe_req.error := false.B
     pipe_req.id := io.id
+    pipe_req.miss_fail_cause_evict_btot := false.B
 
     when (io.pipe_req.fire) {
       state := s_wait_resp
diff --git a/src/main/scala/xiangshan/mem/pipeline/AtomicsUnit.scala b/src/main/scala/xiangshan/mem/pipeline/AtomicsUnit.scala
index 97e2c6e888f..cdc194012ee 100644
--- a/src/main/scala/xiangshan/mem/pipeline/AtomicsUnit.scala
+++ b/src/main/scala/xiangshan/mem/pipeline/AtomicsUnit.scala
@@ -536,6 +536,7 @@ class AtomicsUnit(implicit p: Parameters) extends XSModule
   pipe_req.amo_data := genWdataAMO(rs2, uop.fuOpType)
   pipe_req.amo_mask := genWmaskAMO(paddr, uop.fuOpType)
   pipe_req.amo_cmp  := genWdataAMO(rd, uop.fuOpType)
+  pipe_req.miss_fail_cause_evict_btot := false.B
 
   if (env.EnableDifftest) {
     val difftest = DifftestModule(new DiffAtomicEvent)
```
