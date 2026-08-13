# Commit Log
- Issue: #5269
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/5269
- Issue state: closed
- Tested RTL commit: -
- Related PR: #5269
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/5269
- Changed files: 12
- Additions: 182
- Deletions: 45

## Files
- `src/main/scala/xiangshan/cache/dcache/DCacheWrapper.scala`
- `src/main/scala/xiangshan/cache/dcache/Uncache.scala`
- `src/main/scala/xiangshan/cache/dcache/loadpipe/LoadPipe.scala`
- `src/main/scala/xiangshan/cache/dcache/mainpipe/MainPipe.scala`
- `src/main/scala/xiangshan/cache/dcache/mainpipe/MissQueue.scala`
- `src/main/scala/xiangshan/cache/dcache/meta/AsynchronousMetaArray.scala`
- `src/main/scala/xiangshan/mem/lsqueue/LoadQueueUncache.scala`
- `src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala`
- `src/main/scala/xiangshan/mem/pipeline/AtomicsUnit.scala`
- `src/main/scala/xiangshan/mem/pipeline/HybridUnit.scala`
- `src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala`
- `src/main/scala/xiangshan/mem/vector/VSegmentUnit.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/cache/dcache/DCacheWrapper.scala b/src/main/scala/xiangshan/cache/dcache/DCacheWrapper.scala
index 3816a4e210d..e2cefafca7d 100644
--- a/src/main/scala/xiangshan/cache/dcache/DCacheWrapper.scala
+++ b/src/main/scala/xiangshan/cache/dcache/DCacheWrapper.scala
@@ -354,7 +354,7 @@ class ReplacementWayReqIO(implicit p: Parameters) extends DCacheBundle {
 
 class DCacheExtraMeta(implicit p: Parameters) extends DCacheBundle
 {
-  val error = Bool() // cache line has been marked as corrupted by l2 / ecc error detected when store
+  val error = new TLError() // cache line has been marked as denieded/corrupted by
   val prefetch = UInt(L1PfSourceBits.W) // cache line is first required by prefetch
   val access = Bool() // cache line has been accessed by load / store
 
@@ -472,6 +472,7 @@ class DCacheWordResp(implicit p: Parameters) extends BaseDCacheWordResp
   val real_miss = Bool()
   // s3: 1 cycle after data resp
   val error_delayed = Bool() // all kinds of errors, include tag error
+  val tl_error_delayed = new TLError()
   val replacementUpdated = Bool()
 }
 
@@ -571,6 +572,8 @@ class UncacheWordResp(implicit p: Parameters) extends DCacheBundle
   val tag_error = Bool()
   val error     = Bool()
   val nderr     = Bool()
+  val denied    = Bool()
+  val corrupt   = Bool()
   val replayCarry = new ReplayCarry(nWays)
   val mshr_id = UInt(log2Up(cfg.nMissEntries).W)  // FIXME: why uncacheWordResp is not merged to baseDcacheResp
 
@@ -596,6 +599,7 @@ class MainPipeResp(implicit p: Parameters) extends DCacheBundle {
   val miss_id = UInt(log2Up(cfg.nMissEntries).W)
   val replay  = Bool()
   val error   = Bool()
+  val tl_error = new TLError()
 
   val ack_miss_queue = Bool()
 
@@ -620,6 +624,8 @@ class CMOReq(implicit p: Parameters) extends Bundle {
 class CMOResp(implicit p: Parameters) extends Bundle {
   val address = UInt(64.W)
   val nderr   = Bool()
+  val denied  = Bool()
+  val corrupt = Bool()
 }
 
 // used by load unit
@@ -683,6 +689,7 @@ class DcacheToLduForwardIO(implicit p: Parameters) extends DCacheBundle {
   val data = UInt(l1BusDataWidth.W)
   val mshrid = UInt(log2Up(cfg.nMissEntries).W)
   val last = Bool()
+  val denied = Bool()
   val corrupt = Bool()
 
   def apply(d: DecoupledIO[TLBundleD], edge: TLEdgeOut) = {
@@ -692,7 +699,8 @@ class DcacheToLduForwardIO(implicit p: Parameters) extends DCacheBundle {
     data := d.bits.data
     mshrid := d.bits.source
     last := isKeyword ^ done
-    corrupt := d.bits.corrupt || d.bits.denied
+    denied := d.bits.denied
+    corrupt := d.bits.corrupt
   }
 
   def dontCare() = {
@@ -700,6 +708,7 @@ class DcacheToLduForwardIO(implicit p: Parameters) extends DCacheBundle {
     data := DontCare
     mshrid := DontCare
     last := DontCare
+    denied := false.B
     corrupt := false.B
   }
 
@@ -709,6 +718,7 @@ class DcacheToLduForwardIO(implicit p: Parameters) extends DCacheBundle {
                 req_paddr(log2Up(refillBytes)) === last
     val forward_D = RegInit(false.B)
     val forwardData = RegInit(VecInit(List.fill(VLEN/8)(0.U(8.W))))
+    val forwardDenied = RegInit(false.B)
     val forwardCorrupt = RegInit(false.B)
 
     val block_idx = req_paddr(log2Up(refillBytes) - 1, 3)
@@ -726,10 +736,11 @@ class DcacheToLduForwardIO(implicit p: Parameters) extends DCacheBundle {
       }
     }
     when (all_match) {
+      forwardDenied := denied
       forwardCorrupt := corrupt
     }
 
-    (forward_D, forwardData, forwardCorrupt)
+    (forward_D, forwardData, forwardDenied, forwardCorrupt)
   }
 }
 
@@ -739,6 +750,7 @@ class MissEntryForwardIO(implicit p: Parameters) extends DCacheBundle {
   val raw_data = Vec(blockRows, UInt(rowBits.W))
   val firstbeat_valid = Bool()
   val lastbeat_valid = Bool()
+  val denied = Bool()
   val corrupt = Bool()
 
   // check if we can forward from mshr or D channel
@@ -779,6 +791,7 @@ class LduToMissqueueForwardIO(implicit p: Parameters) extends DCacheBundle {
   val forward_mshr = Output(Bool())
   val forwardData = Output(Vec(VLEN/8, UInt(8.W)))
   val forward_result_valid = Output(Bool())
+  val denied = Output(Bool())
   val corrupt = Output(Bool())
 
   // Why? What is the purpose of `connect`???
@@ -789,11 +802,12 @@ class LduToMissqueueForwardIO(implicit p: Parameters) extends DCacheBundle {
     forward_mshr := sink.forward_mshr
     forwardData := sink.forwardData
     forward_result_valid := sink.forward_result_valid
+    denied := sink.denied
     corrupt := sink.corrupt
   }
 
   def forward() = {
-    (forward_result_valid, forward_mshr, forwardData, corrupt)
+    (forward_result_valid, forward_mshr, forwardData, denied, corrupt)
   }
 }
 
@@ -1004,7 +1018,7 @@ class DCacheImp(outer: DCache) extends LazyModuleImp(outer) with HasDCacheParame
   // core data structures
   val bankedDataArray = if(dwpuParam.enWPU) Module(new SramedDataArray) else Module(new BankedDataArray)
   val metaArray = Module(new L1CohMetaArray(readPorts = LoadPipelineWidth + 1, writePorts = 1))
-  val errorArray = Module(new L1FlagMetaArray(readPorts = LoadPipelineWidth + 1, writePorts = 1, enableBypass = true))
+  val errorArray = Module(new L1ErrorMetaArray(readPorts = LoadPipelineWidth + 1, writePorts = 1, enableBypass = true))
   val prefetchArray = Module(new L1PrefetchSourceArray(readPorts = PrefetchArrayReadPort, writePorts = 1 + LoadPipelineWidth)) // prefetch flag array
   val accessArray = Module(new L1FlagMetaArray(readPorts = AccessArrayReadPort, writePorts = LoadPipelineWidth + 1))
   val tagArray = Module(new DuplicatedTagArray(readPorts = TagReadPort))
diff --git a/src/main/scala/xiangshan/cache/dcache/Uncache.scala b/src/main/scala/xiangshan/cache/dcache/Uncache.scala
index 6436dcb2d7b..d17ac1b3e37 100644
--- a/src/main/scala/xiangshan/cache/dcache/Uncache.scala
+++ b/src/main/scala/xiangshan/cache/dcache/Uncache.scala
@@ -65,6 +65,9 @@ class UncacheEntry(implicit p: Parameters) extends UncacheBundle {
 
   val resp_nderr = Bool()
 
+  val resp_denied = Bool()
+  val resp_corrupt = Bool()
+
   /* NOTE: if it support the internal forward logic, here can uncomment */
   // val fwd_data = UInt(XLEN.W)
   // val fwd_mask = UInt(DataBytes.W)
@@ -99,6 +102,8 @@ class UncacheEntry(implicit p: Parameters) extends UncacheBundle {
       data := x.data
     }
     resp_nderr := x.denied || x.corrupt
+    resp_denied := x.denied
+    resp_corrupt := x.corrupt
   }
 
   // def update(forwardData: UInt, forwardMask: UInt): Unit = {
@@ -116,6 +121,8 @@ class UncacheEntry(implicit p: Parameters) extends UncacheBundle {
     r.data := resp_fwd_data
     r.id := eid
     r.nderr := resp_nderr
+    r.denied := resp_denied
+    r.corrupt := resp_corrupt
     r.nc := nc
     r.is2lq := cmd === MemoryOpConstants.M_XRD
     r.miss := false.B
diff --git a/src/main/scala/xiangshan/cache/dcache/loadpipe/LoadPipe.scala b/src/main/scala/xiangshan/cache/dcache/loadpipe/LoadPipe.scala
index 3c3eb7787f9..a5c9dd7ff96 100644
--- a/src/main/scala/xiangshan/cache/dcache/loadpipe/LoadPipe.scala
+++ b/src/main/scala/xiangshan/cache/dcache/loadpipe/LoadPipe.scala
@@ -316,8 +316,8 @@ class LoadPipe(id: Int)(implicit p: Parameters) extends DCacheModule with HasPer
   io.banked_data_read.bits.lqIdx := s1_req.lqIdx
   io.is128Req := s1_load128Req
 
-  // check ecc error
-  val s1_flag_error = Mux(s1_need_replacement, false.B, s1_hit_error) // error reported by exist dcache error bit
+  // check tl error
+  val s1_tl_error = Mux(s1_need_replacement, 0.U.asTypeOf(new TLError()), s1_hit_error) // error reported by exist dcache error bit
 
   // --------------------------------------------------------------------------------
   // stage 2
@@ -407,7 +407,7 @@ class LoadPipe(id: Int)(implicit p: Parameters) extends DCacheModule with HasPer
   val s2_instrtype = s2_req.instrtype
 
   val s2_tag_error = WireInit(false.B)
-  val s2_flag_error = RegEnable(s1_flag_error, s1_fire)
+  val s2_tl_error = RegEnable(s1_tl_error, s1_fire)
 
   val s2_hit_prefetch = RegEnable(s1_hit_prefetch, s1_fire)
   val s2_hit_access = RegEnable(s1_hit_access, s1_fire)
@@ -547,12 +547,15 @@ class LoadPipe(id: Int)(implicit p: Parameters) extends DCacheModule with HasPer
   val s3_banked_data_resp_word = RegEnable(s2_resp_data, s2_fire)
   val s3_data_error = Mux(s3_load128Req, io.read_error_delayed.asUInt.orR, io.read_error_delayed(0)) && s3_hit
   val s3_tag_error = RegEnable(s2_tag_error, s2_fire)
-  val s3_flag_error = RegEnable(s2_flag_error, s2_fire)
+  val s3_tl_error = RegEnable(s2_tl_error, s2_fire)
+  val s3_flag_error = s3_tl_error.asUInt.orR
   val s3_hit_prefetch = RegEnable(s2_hit_prefetch, s2_fire)
   val s3_error = s3_tag_error || s3_flag_error || s3_data_error
 
   // error_delayed signal will be used to update uop.exception 1 cycle after load writeback
   resp.bits.error_delayed := s3_error && (s3_hit || s3_tag_error) && s3_valid
+  resp.bits.tl_error_delayed.tl_denied := s3_tl_error.tl_denied & s3_valid
+  resp.bits.tl_error_delayed.tl_corrupt := s3_tl_error.tl_corrupt & s3_valid
   resp.bits.data_delayed := s3_banked_data_resp_word
   resp.bits.replacementUpdated := io.replace_access.valid
 
diff --git a/src/main/scala/xiangshan/cache/dcache/mainpipe/MainPipe.scala b/src/main/scala/xiangshan/cache/dcache/mainpipe/MainPipe.scala
index dfc1abd8d87..12cce063380 100644
--- a/src/main/scala/xiangshan/cache/dcache/mainpipe/MainPipe.scala
+++ b/src/main/scala/xiangshan/cache/dcache/mainpipe/MainPipe.scala
@@ -163,7 +163,7 @@ class MainPipe(implicit p: Parameters) extends DCacheModule with HasPerfEvents w
     val meta_resp = Input(Vec(nWays, new Meta))
     val meta_write = DecoupledIO(new CohMetaWriteReq)
     val extra_meta_resp = Input(Vec(nWays, new DCacheExtraMeta))
-    val error_flag_write = DecoupledIO(new FlagMetaWriteReq)
+    val error_flag_write = DecoupledIO(new ErrorMetaWriteReq)
     val prefetch_flag_write = DecoupledIO(new SourceMetaWriteReq)
     val access_flag_write = DecoupledIO(new FlagMetaWriteReq)
 
@@ -439,8 +439,8 @@ class MainPipe(implicit p: Parameters) extends DCacheModule with HasPerfEvents w
   val s2_banked_store_wmask = RegEnable(s1_banked_store_wmask, s1_fire)
   val s2_flag_error = RegEnable(s1_flag_error, s1_fire)
   val s2_tag_error = WireInit(false.B)
-  val s2_l2_error = Mux(io.refill_info.valid, io.refill_info.bits.error, s2_req.error)
-  val s2_error = s2_flag_error || s2_tag_error || s2_l2_error // data_error not included
+  val s2_l2_error = Mux(io.refill_info.valid, io.refill_info.bits.error, 0.U.asTypeOf(new TLError()))
+  val s2_error = s2_flag_error.asUInt.orR || s2_tag_error || s2_l2_error.asUInt.orR // data_error not included
 
   val s2_may_report_data_error = s2_need_data && s2_coh.state =/= ClientStates.Nothing
 
@@ -878,6 +878,7 @@ class MainPipe(implicit p: Parameters) extends DCacheModule with HasPerfEvents w
   atomic_hit_resp.miss := false.B
   atomic_hit_resp.miss_id := s3_req.miss_id
   atomic_hit_resp.error := s3_error_wb
+  atomic_hit_resp.tl_error := (s3_l2_error_wb.asUInt | s3_flag_error_beu.asUInt).asTypeOf(new TLError())
   atomic_hit_resp.replay := false.B
   atomic_hit_resp.ack_miss_queue := s3_req.miss
   atomic_hit_resp.id := lrsc_valid
@@ -887,6 +888,7 @@ class MainPipe(implicit p: Parameters) extends DCacheModule with HasPerfEvents w
   atomic_replay_resp.miss := true.B
   atomic_replay_resp.miss_id := DontCare
   atomic_replay_resp.error := false.B
+  atomic_replay_resp.tl_error := 0.U.asTypeOf(new TLError())
   atomic_replay_resp.replay := true.B
   atomic_replay_resp.ack_miss_queue := false.B
   atomic_replay_resp.id := DontCare
@@ -905,10 +907,10 @@ class MainPipe(implicit p: Parameters) extends DCacheModule with HasPerfEvents w
   io.meta_write.bits.way_en := s3_way_en
   io.meta_write.bits.meta.coh := new_coh
 
-  io.error_flag_write.valid := s3_fire && update_meta && (s3_l2_error_wb || s3_req.miss)
+  io.error_flag_write.valid := s3_fire && update_meta && (s3_l2_error_wb.asUInt.orR || s3_req.miss)
   io.error_flag_write.bits.idx := s3_idx
   io.error_flag_write.bits.way_en := s3_way_en
-  io.error_flag_write.bits.flag := s3_l2_error_wb
+  io.error_flag_write.bits.error := s3_l2_error_wb
 
   // if we use (prefetch_flag && meta =/= ClientStates.Nothing) for prefetch check
   // prefetch_flag_write can be omited
@@ -1061,7 +1063,7 @@ class MainPipe(implicit p: Parameters) extends DCacheModule with HasPerfEvents w
   io.error.bits.paddr := s3_error_paddr_beu
   io.error.bits.source.tag := s3_tag_error_beu
   io.error.bits.source.data := s3_data_error_beu
-  io.error.bits.source.l2 := s3_flag_error_beu || s3_l2_error_beu
+  io.error.bits.source.l2 := s3_flag_error_beu.asUInt.orR || s3_l2_error_beu.asUInt.orR
   io.error.bits.opType.store := RegEnable(s2_req.isStore && !s2_req.probe, s2_fire)
   io.error.bits.opType.probe := RegEnable(s2_req.probe, s2_fire)
   io.error.bits.opType.release := RegEnable(s2_req.replace, s2_fire)
diff --git a/src/main/scala/xiangshan/cache/dcache/mainpipe/MissQueue.scala b/src/main/scala/xiangshan/cache/dcache/mainpipe/MissQueue.scala
index fd6bc2f643e..f0064b4baf8 100644
--- a/src/main/scala/xiangshan/cache/dcache/mainpipe/MissQueue.scala
+++ b/src/main/scala/xiangshan/cache/dcache/mainpipe/MissQueue.scala
@@ -108,7 +108,7 @@ class MissQueueRefillInfo(implicit p: Parameters) extends MissReqStoreData {
   // refill_info for mainpipe req awake
   val miss_param = UInt(TLPermissions.bdWidth.W)
   val miss_dirty = Bool()
-  val error      = Bool()
+  val error      = new TLError()
 }
 
 class MissReq(implicit p: Parameters) extends MissReqWoStoreData {
@@ -310,6 +310,8 @@ class CMOUnit(edge: TLEdgeOut)(implicit p: Parameters) extends DCacheModule {
   val state_next = WireInit(state)
   val req = RegEnable(io.req.bits, io.req.fire)
   val nderr = RegInit(false.B)
+  val denied = RegInit(false.B)
+  val corrupt = RegInit(false.B)
   val no_pending = RegInit(true.B)
 
   state := state_next
@@ -319,6 +321,8 @@ class CMOUnit(edge: TLEdgeOut)(implicit p: Parameters) extends DCacheModule {
       when (io.req.fire) {
         state_next := s_sreq
         nderr := false.B
+        denied := false.B
+        corrupt := false.B
       }
     }
     is(s_sreq) {
@@ -331,6 +335,8 @@ class CMOUnit(edge: TLEdgeOut)(implicit p: Parameters) extends DCacheModule {
       when (io.resp_chanD.fire) {
         state_next := s_lsq_resp
         nderr := io.resp_chanD.bits.denied || io.resp_chanD.bits.corrupt
+        denied := io.resp_chanD.bits.denied
+        corrupt := io.resp_chanD.bits.corrupt
         no_pending := true.B
       }
     }
@@ -357,6 +363,8 @@ class CMOUnit(edge: TLEdgeOut)(implicit p: Parameters) extends DCacheModule {
   io.resp_to_lsq.valid := state === s_lsq_resp
   io.resp_to_lsq.bits.address := req.address
   io.resp_to_lsq.bits.nderr   := nderr
+  io.resp_to_lsq.bits.denied  := denied
+  io.resp_to_lsq.bits.corrupt := corrupt
 
   assert(!(state =/= s_idle && io.req.valid))
   assert(!(state =/= s_wresp && io.resp_chanD.valid))
@@ -495,7 +503,8 @@ class MissEntry(edge: TLEdgeOut, reqNum: Int)(implicit p: Parameters) extends DC
   val acquire_not_sent = !s_acquire && !io.mem_acquire.ready
   val data_not_refilled = !w_grantfirst
 
-  val error = RegInit(false.B)
+  val denied = RegInit(false.B)
+  val corrupt = RegInit(false.B)
   val prefetch = RegInit(false.B)
   val access = RegInit(false.B)
 
@@ -580,7 +589,8 @@ class MissEntry(edge: TLEdgeOut, reqNum: Int)(implicit p: Parameters) extends DC
     }
 
     should_refill_data_reg := miss_req_pipe_reg_bits.isFromLoad
-    error := false.B
+    denied := false.B
+    corrupt := false.B
     prefetch := input_req_is_prefetch && !io.miss_req_pipe_reg.prefetch_late_en(io.req.bits, io.req.valid)
     access := false.B
     secondary_fired := false.B
@@ -677,7 +687,8 @@ class MissEntry(edge: TLEdgeOut, reqNum: Int)(implicit p: Parameters) extends DC
       hasData := false.B
     }
 
-    error := io.mem_grant.bits.denied || io.mem_grant.bits.corrupt || error
+    denied := io.mem_grant.bits.denied || denied
+    corrupt := io.mem_grant.bits.corrupt || corrupt
 
     refill_data_raw(refill_count ^ isKeyword) := io.mem_grant.bits.data
     isDirty := io.mem_grant.bits.echo.lift(DirtyKey).getOrElse(false.B)
@@ -908,7 +919,8 @@ class MissEntry(edge: TLEdgeOut, reqNum: Int)(implicit p: Parameters) extends DC
   io.refill_info.bits.store_mask := ~0.U(blockBytes.W)
   io.refill_info.bits.miss_param := grant_param
   io.refill_info.bits.miss_dirty := isDirty
-  io.refill_info.bits.error      := error
+  io.refill_info.bits.error.tl_denied  := denied
+  io.refill_info.bits.error.tl_corrupt := corrupt
 
   XSPerfAccumulate("miss_refill_mainpipe_req", io.main_pipe_req.fire)
   XSPerfAccumulate("miss_refill_without_hint", io.main_pipe_req.fire && !mainpipe_req_fired && !w_l2hint)
@@ -922,7 +934,8 @@ class MissEntry(edge: TLEdgeOut, reqNum: Int)(implicit p: Parameters) extends DC
   io.forwardInfo.raw_data := refill_and_store_data
   io.forwardInfo.firstbeat_valid := w_grantfirst_forward_info
   io.forwardInfo.lastbeat_valid := w_grantlast_forward_info
-  io.forwardInfo.corrupt := error
+  io.forwardInfo.denied := denied
+  io.forwardInfo.corrupt := corrupt
 
   io.matched := req_valid && (get_block(req.addr) === get_block(io.req.bits.addr)) && !prefetch
   io.prefetch_info.late_prefetch := io.req.valid && !(io.req.bits.isFromPrefetch) && req_valid && (get_block(req.addr) === get_block(io.req.bits.addr)) && prefetch
@@ -1127,6 +1140,7 @@ class MissQueue(edge: TLEdgeOut, reqNum: Int)(implicit p: Parameters) extends DC
     io.forward(i).forward_mshr := forward_mshr
     io.forward(i).forwardData := forwardData
     io.forward(i).corrupt := RegNext(forwardInfo_vec(id).corrupt)
+    io.forward(i).denied := RegNext(forwardInfo_vec(id).denied)
   })
 
   assert(RegNext(PopCount(secondary_ready_vec) <= 1.U || !io.req.valid))
diff --git a/src/main/scala/xiangshan/cache/dcache/meta/AsynchronousMetaArray.scala b/src/main/scala/xiangshan/cache/dcache/meta/AsynchronousMetaArray.scala
index 7c4059e3d58..40629a5bf54 100644
--- a/src/main/scala/xiangshan/cache/dcache/meta/AsynchronousMetaArray.scala
+++ b/src/main/scala/xiangshan/cache/dcache/meta/AsynchronousMetaArray.scala
@@ -20,7 +20,7 @@ import freechips.rocketchip.tilelink.ClientMetadata
 import org.chipsalliance.cde.config.Parameters
 import chisel3._
 import chisel3.util._
-import xiangshan.L1CacheErrorInfo
+import xiangshan.{L1CacheErrorInfo}
 import xiangshan.cache.CacheInstrucion._
 
 class Meta(implicit p: Parameters) extends DCacheBundle {
@@ -35,6 +35,11 @@ object Meta {
   }
 }
 
+class TLError(implicit p: Parameters) extends DCacheBundle {
+  val tl_denied = Bool()
+  val tl_corrupt = Bool()
+}
+
 class MetaReadReq(implicit p: Parameters) extends DCacheBundle {
   val idx = UInt(idxBits.W)
   val way_en = UInt(nWays.W)
@@ -48,6 +53,10 @@ class FlagMetaWriteReq(implicit p: Parameters) extends MetaReadReq {
   val flag = Bool()
 }
 
+class ErrorMetaWriteReq(implicit p: Parameters) extends MetaReadReq {
+  val error = new TLError()
+}
+
 class L1CohMetaArray(readPorts: Int, writePorts: Int, bypassRead: Boolean = true)(implicit p: Parameters) extends DCacheModule {
   val io = IO(new Bundle() {
     val read = Vec(readPorts, Flipped(DecoupledIO(new MetaReadReq)))
@@ -185,6 +194,74 @@ class L1FlagMetaArray(readPorts: Int, writePorts: Int, enableBypass: Boolean = f
   }
 }
 
+class L1ErrorMetaArray(readPorts: Int, writePorts: Int, enableBypass: Boolean = true)(implicit p: Parameters) extends DCacheModule {
+  val io = IO(new Bundle() {
+    val read = Vec(readPorts, Flipped(DecoupledIO(new MetaReadReq)))
+    val resp = Output(Vec(readPorts, Vec(nWays, new TLError())))
+    val write = Vec(writePorts, Flipped(DecoupledIO(new ErrorMetaWriteReq())))
+  })
+
+  val meta_array = RegInit(
+    VecInit(Seq.fill(nSets)(
+      VecInit(Seq.fill(nWays)(0.U.asTypeOf(new TLError())))
+    ))
+  )
+
+  val s0_way_wen = Wire(Vec(nWays, Vec(writePorts, Bool())))
+  val s0_way_waddr = Wire(Vec(nWays, Vec(writePorts, UInt(idxBits.W))))
+  val s0_way_wdata = Wire(Vec(nWays, Vec(writePorts, new TLError())))
+  val s1_way_wen = Wire(Vec(nWays, Vec(writePorts, Bool())))
+  val s1_way_waddr = Wire(Vec(nWays, Vec(writePorts, UInt(idxBits.W))))
+  val s1_way_wdata = Wire(Vec(nWays, Vec(writePorts, new TLError())))
+
+  (io.read.zip(io.resp)).zipWithIndex.foreach {
+    case ((read, resp), i) =>
+      read.ready := true.B
+      (0 until nWays).map(way => {
+        val read_way_bypass = WireInit(false.B)
+        val bypass_data = Wire(new TLError())
+        bypass_data := DontCare
+        (0 until writePorts).map { wport =>
+          when(s1_way_wen(way)(wport) && s1_way_waddr(way)(wport) === read.bits.idx) {
+            read_way_bypass := true.B
+            bypass_data := s1_way_wdata(way)(wport)
+          }
+          when(s0_way_wen(way)(wport) && s0_way_waddr(way)(wport) === read.bits.idx) {
+            read_way_bypass := true.B
+            bypass_data := s0_way_wdata(way)(wport)
+          }
+        }
+
+        if (enableBypass) {
+          resp(way) := Mux(
+            RegEnable(read_way_bypass, read.valid),
+            RegEnable(bypass_data, read_way_bypass),
+            meta_array(RegEnable(read.bits.idx, read.valid))(way)
+          )
+        } else {
+          resp(way) := meta_array(RegEnable(read.bits.idx, read.valid))(way)
+        }
+      })
+  }
+
+  io.write.zipWithIndex.foreach {
+    case (write, wport) =>
+      write.ready := true.B
+      write.bits.way_en.asBools.zipWithIndex.foreach {
+        case (wen, way) =>
+          s0_way_wen(way)(wport) := write.valid && wen
+          s0_way_waddr(way)(wport) := write.bits.idx
+          s0_way_wdata(way)(wport) := write.bits.error
+          s1_way_wen(way)(wport) := RegNext(s0_way_wen(way)(wport))
+          s1_way_waddr(way)(wport) := RegEnable(s0_way_waddr(way)(wport), s0_way_wen(way)(wport))
+          s1_way_wdata(way)(wport) := RegEnable(s0_way_wdata(way)(wport), s0_way_wen(way)(wport))
+          when (s1_way_wen(way)(wport)) {
+            meta_array(s1_way_waddr(way)(wport))(way) := s1_way_wdata(way)(wport)
+          }
+      }
+  }
+}
+
 class SourceMetaWriteReq(implicit p: Parameters) extends MetaReadReq {
   val source = UInt(L1PfSourceBits.W)
 }
diff --git a/src/main/scala/xiangshan/mem/lsqueue/LoadQueueUncache.scala b/src/main/scala/xiangshan/mem/lsqueue/LoadQueueUncache.scala
index 4ecb4eddb9d..bdf1ac9a16e 100644
--- a/src/main/scala/xiangshan/mem/lsqueue/LoadQueueUncache.scala
+++ b/src/main/scala/xiangshan/mem/lsqueue/LoadQueueUncache.scala
@@ -69,6 +69,8 @@ class UncacheEntry(entryIndex: Int)(implicit p: Parameters) extends XSModule
   val uncacheState = RegInit(s_idle)
   val uncacheData = Reg(io.uncache.resp.bits.data.cloneType)
   val nderr = RegInit(false.B)
+  val denied = RegInit(false.B)
+  val corrupt = RegInit(false.B)
 
   val writeback = Mux(req.nc, io.ncOut.fire, io.mmioOut.fire)
   val slaveAck = req_valid && io.uncache.idResp.valid && io.uncache.idResp.bits.mid === entryIndex.U
@@ -97,6 +99,8 @@ class UncacheEntry(entryIndex: Int)(implicit p: Parameters) extends XSModule
     slaveAccept := false.B
     req := io.req.bits
     nderr := false.B
+    denied := false.B
+    corrupt := false.B
   } .elsewhen(slaveAck) {
     slaveAccept := true.B
     slaveId := io.uncache.idResp.bits.sid
@@ -185,6 +189,8 @@ class UncacheEntry(entryIndex: Int)(implicit p: Parameters) extends XSModule
   when (io.uncache.resp.fire) {
     uncacheData := io.uncache.resp.bits.data
     nderr := io.uncache.resp.bits.nderr
+    denied := io.uncache.resp.bits.denied
+    corrupt := io.uncache.resp.bits.corrupt
   }
 
   /* uncahce writeback */
@@ -199,7 +205,8 @@ class UncacheEntry(entryIndex: Int)(implicit p: Parameters) extends XSModule
     io.ncOut.bits := DontCare
     io.ncOut.bits.uop := req.uop
     io.ncOut.bits.uop.lqIdx := req.uop.lqIdx
-    io.ncOut.bits.uop.exceptionVec(hardwareError) := nderr
+    io.ncOut.bits.uop.exceptionVec(hardwareError) := corrupt && !denied
+    io.ncOut.bits.uop.exceptionVec(loadAccessFault) := denied
     io.ncOut.bits.data := uncacheData
     io.ncOut.bits.paddr := req.paddr
     io.ncOut.bits.vaddr := req.vaddr
@@ -214,7 +221,8 @@ class UncacheEntry(entryIndex: Int)(implicit p: Parameters) extends XSModule
     io.mmioOut.bits := DontCare
     io.mmioOut.bits.uop := req.uop
     io.mmioOut.bits.uop.lqIdx := req.uop.lqIdx
-    io.mmioOut.bits.uop.exceptionVec(hardwareError) := nderr
+    io.mmioOut.bits.uop.exceptionVec(hardwareError) := corrupt && !denied
+    io.mmioOut.bits.uop.exceptionVec(loadAccessFault) := denied
     io.mmioOut.bits.data := uncacheData
     io.mmioOut.bits.debug.isMMIO := true.B
     io.mmioOut.bits.debug.isNCIO := false.B
@@ -227,7 +235,8 @@ class UncacheEntry(entryIndex: Int)(implicit p: Parameters) extends XSModule
 
   io.exception.valid := writeback
   io.exception.bits := req
-  io.exception.bits.uop.exceptionVec(hardwareError) := nderr
+  io.exception.bits.uop.exceptionVec(hardwareError) := corrupt && !denied
+  io.exception.bits.uop.exceptionVec(loadAccessFault) := denied
 
   /* debug log */
   XSDebug(io.uncache.req.fire,
diff --git a/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala b/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala
index dc1da3c384d..541b9913918 100644
--- a/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala
+++ b/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala
@@ -872,14 +872,19 @@ class StoreQueue(implicit p: Parameters) extends XSModule
         noPending := true.B
         mmioState := s_wb
 
-        when (io.uncache.resp.bits.nderr || io.cmoOpResp.bits.nderr) {
+        when (io.uncache.resp.bits.denied || io.cmoOpResp.bits.denied) {
+          uncacheUop.exceptionVec(storeAccessFault) := true.B
+        }
+
+        when (io.uncache.resp.bits.corrupt && !io.uncache.resp.bits.denied ||
+              io.cmoOpResp.bits.corrupt && !io.cmoOpResp.bits.denied) {
           uncacheUop.exceptionVec(hardwareError) := true.B
         }
       }
     }
     is(s_wb) {
       when (io.mmioStout.fire || io.vecmmioStout.fire) {
-        when (uncacheUop.exceptionVec(hardwareError)) {
+        when (ExceptionNO.selectByFu(uncacheUop.exceptionVec, StaCfg).asUInt.orR) {
           mmioState := s_idle
         }.otherwise {
           mmioState := s_wait
diff --git a/src/main/scala/xiangshan/mem/pipeline/AtomicsUnit.scala b/src/main/scala/xiangshan/mem/pipeline/AtomicsUnit.scala
index cdc194012ee..4c2104361dc 100644
--- a/src/main/scala/xiangshan/mem/pipeline/AtomicsUnit.scala
+++ b/src/main/scala/xiangshan/mem/pipeline/AtomicsUnit.scala
@@ -30,7 +30,7 @@ import xiangshan.backend.fu.NewCSR.TriggerUtil
 import xiangshan.backend.fu.util.SdtrigExt
 import xiangshan.mem.Bundles._
 import xiangshan.cache.mmu.Pbmt
-import xiangshan.cache.{AtomicWordIO, HasDCacheParameters, MemoryOpConstants}
+import xiangshan.cache.{AtomicWordIO, HasDCacheParameters, MemoryOpConstants, TLError}
 import xiangshan.cache.mmu.{TlbCmd, TlbRequestIO}
 import difftest._
 
@@ -342,7 +342,7 @@ class AtomicsUnit(implicit p: Parameters) extends XSModule
 
   val dcache_resp_data  = Reg(UInt())
   val dcache_resp_id    = Reg(UInt())
-  val dcache_resp_error = Reg(Bool())
+  val dcache_resp_tl_error = Reg(new TLError())
 
   when (state === s_cache_resp) {
     // when not miss
@@ -362,7 +362,7 @@ class AtomicsUnit(implicit p: Parameters) extends XSModule
       }.otherwise {
         dcache_resp_data := io.dcache.resp.bits.data
         dcache_resp_id := io.dcache.resp.bits.id
-        dcache_resp_error := io.dcache.resp.bits.error
+        dcache_resp_tl_error := io.dcache.resp.bits.tl_error
         state := s_cache_resp_latch
       }
     }
@@ -387,11 +387,10 @@ class AtomicsUnit(implicit p: Parameters) extends XSModule
       ))
     )
 
-    when (dcache_resp_error && io.csrCtrl.cache_error_enable) {
-      exceptionVec(loadAccessFault)  := isLr
-      exceptionVec(storeAccessFault) := !isLr
-      assert(!exceptionVec(loadAccessFault))
-      assert(!exceptionVec(storeAccessFault))
+    when (dcache_resp_tl_error.asUInt.orR && io.csrCtrl.cache_error_enable) {
+      exceptionVec(loadAccessFault)  := isLr && dcache_resp_tl_error.tl_denied
+      exceptionVec(storeAccessFault) := !isLr && dcache_resp_tl_error.tl_denied
+      exceptionVec(hardwareError)    := dcache_resp_tl_error.tl_corrupt && !dcache_resp_tl_error.tl_denied
     }
 
     resp_data := resp_data_wire
diff --git a/src/main/scala/xiangshan/mem/pipeline/HybridUnit.scala b/src/main/scala/xiangshan/mem/pipeline/HybridUnit.scala
index 8b71b842052..69ca933e939 100644
--- a/src/main/scala/xiangshan/mem/pipeline/HybridUnit.scala
+++ b/src/main/scala/xiangshan/mem/pipeline/HybridUnit.scala
@@ -860,8 +860,8 @@ class HybridUnit(implicit p: Parameters) extends XSModule
   val s2_st_exception = ExceptionNO.selectByFu(s2_exception_vec, StaCfg).asUInt.orR && !s2_ld_flow
   val s2_exception    = s2_ld_exception || s2_st_exception
 
-  val (s2_fwd_frm_d_chan, s2_fwd_data_frm_d_chan, s2_d_corrupt) = io.ldu_io.tl_d_channel.forward(s1_valid && s1_out.forward_tlDchannel, s1_out.mshrid, s1_out.paddr)
-  val (s2_fwd_data_valid, s2_fwd_frm_mshr, s2_fwd_data_frm_mshr, s2_mshr_corrupt) = io.ldu_io.forward_mshr.forward()
+  val (s2_fwd_frm_d_chan, s2_fwd_data_frm_d_chan, s2_d_denied, s2_d_corrupt) = io.ldu_io.tl_d_channel.forward(s1_valid && s1_out.forward_tlDchannel, s1_out.mshrid, s1_out.paddr)
+  val (s2_fwd_data_valid, s2_fwd_frm_mshr, s2_fwd_data_frm_mshr, s2_mshr_denied, s2_mshr_corrupt) = io.ldu_io.forward_mshr.forward()
   val s2_fwd_frm_d_chan_or_mshr = s2_fwd_data_valid && (s2_fwd_frm_d_chan || s2_fwd_frm_mshr)
 
   // writeback access fault caused by ecc error / bus error
diff --git a/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala b/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala
index 96f561f4181..3641f7231f0 100644
--- a/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala
+++ b/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala
@@ -1228,8 +1228,8 @@ class LoadUnit(implicit p: Parameters) extends XSModule
                     (s2_trigger_debug_mode || ExceptionNO.selectByFu(s2_exception_vec, LduCfg).asUInt.orR)
   val s2_mis_align = s2_valid && GatedValidRegNext(io.csrCtrl.hd_misalign_ld_enable) &&
                      s2_out.isMisalign && !s2_in.misalignWith16Byte && !s2_exception_vec(breakPoint) && !s2_trigger_debug_mode && !s2_uncache
-  val (s2_fwd_frm_d_chan, s2_fwd_data_frm_d_chan, s2_d_corrupt) = io.tl_d_channel.forward(s1_valid && s1_out.forward_tlDchannel, s1_out.mshrid, s1_out.paddr)
-  val (s2_fwd_data_valid, s2_fwd_frm_mshr, s2_fwd_data_frm_mshr, s2_mshr_corrupt) = io.forward_mshr.forward()
+  val (s2_fwd_frm_d_chan, s2_fwd_data_frm_d_chan, s2_d_denied, s2_d_corrupt) = io.tl_d_channel.forward(s1_valid && s1_out.forward_tlDchannel, s1_out.mshrid, s1_out.paddr)
+  val (s2_fwd_data_valid, s2_fwd_frm_mshr, s2_fwd_data_frm_mshr, s2_mshr_denied, s2_mshr_corrupt) = io.forward_mshr.forward()
   val s2_fwd_frm_d_chan_or_mshr = s2_fwd_data_valid && (s2_fwd_frm_d_chan || s2_fwd_frm_mshr)
 
   // writeback access fault caused by ecc error / bus error
@@ -1333,8 +1333,12 @@ class LoadUnit(implicit p: Parameters) extends XSModule
   val s2_real_exceptionVec = WireInit(s2_exception_vec)
   s2_real_exceptionVec(loadAddrMisaligned) := (s2_out.isMisalign || s2_out.isFrmMisAlignBuf) && s2_uncache && !s2_isvec
   s2_real_exceptionVec(loadAccessFault) := s2_exception_vec(loadAccessFault) ||
-    s2_fwd_frm_d_chan && s2_d_corrupt ||
-    s2_fwd_data_valid && s2_fwd_frm_mshr && s2_mshr_corrupt
+    s2_fwd_frm_d_chan && s2_d_denied ||
+    s2_fwd_data_valid && s2_fwd_frm_mshr && s2_mshr_denied
+  s2_real_exceptionVec(hardwareError) := s2_exception_vec(hardwareError) ||
+    s2_fwd_frm_d_chan && s2_d_corrupt && !s2_d_denied ||
+    s2_fwd_data_valid && s2_fwd_frm_mshr && s2_mshr_corrupt && !s2_mshr_denied
+
   val s2_real_exception = s2_vecActive &&
     (s2_trigger_debug_mode || ExceptionNO.selectByFu(s2_real_exceptionVec, LduCfg).asUInt.orR)
 
@@ -1616,8 +1620,9 @@ class LoadUnit(implicit p: Parameters) extends XSModule
   s3_out.valid                := s3_valid && s3_safe_writeback && !toMisalignBufferValid
   s3_out.bits.uop             := s3_in.uop
   s3_out.bits.uop.fpWen       := s3_in.uop.fpWen
-  s3_out.bits.uop.exceptionVec(loadAccessFault) := s3_in.uop.exceptionVec(loadAccessFault) && s3_vecActive
-  s3_out.bits.uop.exceptionVec(hardwareError) := (s3_in.uop.exceptionVec(hardwareError) || s3_hw_err) && s3_vecActive
+  s3_out.bits.uop.exceptionVec(loadAccessFault) := (s3_in.uop.exceptionVec(loadAccessFault) || io.dcache.resp.bits.tl_error_delayed.tl_denied) && s3_vecActive
+  s3_out.bits.uop.exceptionVec(hardwareError) := (s3_in.uop.exceptionVec(hardwareError) || s3_hw_err ||
+                                                 io.dcache.resp.bits.tl_error_delayed.tl_corrupt && !io.dcache.resp.bits.tl_error_delayed.tl_denied) && s3_vecActive
   s3_out.bits.uop.flushPipe   := false.B
   s3_out.bits.uop.replayInst  := false.B
   s3_out.bits.data            := s3_in.data
diff --git a/src/main/scala/xiangshan/mem/vector/VSegmentUnit.scala b/src/main/scala/xiangshan/mem/vector/VSegmentUnit.scala
index 46a543f751a..d62bf08d509 100644
--- a/src/main/scala/xiangshan/mem/vector/VSegmentUnit.scala
+++ b/src/main/scala/xiangshan/mem/vector/VSegmentUnit.scala
@@ -591,16 +591,18 @@ class VSegmentUnit (implicit p: Parameters) extends VLSUModule
 
 
   // HardwareError response will be one beat late
+  val rdcache_resp_error = if (EnableAccurateLoadError) io.rdcache.resp.bits.error_delayed else io.rdcache.resp.bits.tl_error_delayed.asUInt.orR
   when(
     (state === s_latch_and_merge_data || state === s_misalign_merge_data) &&
-    io.rdcache.resp.bits.error_delayed && GatedValidRegNext(io.csrCtrl.cache_error_enable) &&
+      rdcache_resp_error && GatedValidRegNext(io.csrCtrl.cache_error_enable) &&
     segmentActive
   ) {
     exception_pa := true.B
     instMicroOp.exception_pa := true.B
 
     when(canTriggerException) {
-      exceptionVec(hardwareError) := true.B
+      exceptionVec(hardwareError) := io.rdcache.resp.bits.tl_error_delayed.tl_corrupt && !io.rdcache.resp.bits.tl_error_delayed.tl_denied || EnableAccurateLoadError.B
+      exceptionVec(loadAccessFault)  := io.rdcache.resp.bits.tl_error_delayed.tl_denied && isVSegLoad
       instMicroOp.exceptionVstart := segmentIdx // for exception
     }.otherwise {
       instMicroOp.exceptionVl.valid := true.B
```
