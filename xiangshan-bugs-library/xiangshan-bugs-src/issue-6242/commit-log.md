# Commit Log
- Issue: #6242
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/6242
- Issue state: closed
- Tested RTL commit: -
- Related PR: #6242
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/6242
- Changed files: 6
- Additions: 128
- Deletions: 6

## Files
- `src/main/scala/xiangshan/cache/dcache/DCacheWrapper.scala`
- `src/main/scala/xiangshan/cache/dcache/data/BankedDataArray.scala`
- `src/main/scala/xiangshan/cache/dcache/loadpipe/LoadPipe.scala`
- `src/main/scala/xiangshan/mem/MemBlock.scala`
- `src/main/scala/xiangshan/mem/pipeline/Bundles.scala`
- `src/main/scala/xiangshan/mem/pipeline/NewLoadUnit.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/cache/dcache/DCacheWrapper.scala b/src/main/scala/xiangshan/cache/dcache/DCacheWrapper.scala
index e7d4e6b015c..a002c91d4e1 100644
--- a/src/main/scala/xiangshan/cache/dcache/DCacheWrapper.scala
+++ b/src/main/scala/xiangshan/cache/dcache/DCacheWrapper.scala
@@ -698,6 +698,7 @@ class DCacheLoadIO(implicit p: Parameters) extends DCacheWordIO
   val s2_hit = Input(Bool()) // hit signal for lsu,
   val s2_first_hit = Input(Bool())
   val s2_bank_conflict = Input(Bool())
+  val s2_rr_bank_conflict = Input(Bool())
   val s2_wpu_pred_fail = Input(Bool())
   val s2_mq_nack = Input(Bool())
 
@@ -1356,6 +1357,7 @@ class DCacheImp(outer: DCache) extends LazyModuleImp(outer) with HasDCacheParame
     ldu(i).io.banked_data_resp := bankedDataArray.io.read_resp(i)
 
     ldu(i).io.bank_conflict_slow := bankedDataArray.io.bank_conflict_slow(i)
+    ldu(i).io.rr_bank_conflict_slow := bankedDataArray.io.rr_bank_conflict_slow(i)
   })
 
   def processChannel(forward: DCacheForward, bus: TLBundle, i: Int): Unit = {
diff --git a/src/main/scala/xiangshan/cache/dcache/data/BankedDataArray.scala b/src/main/scala/xiangshan/cache/dcache/data/BankedDataArray.scala
index d40938b2352..22a04e640a6 100644
--- a/src/main/scala/xiangshan/cache/dcache/data/BankedDataArray.scala
+++ b/src/main/scala/xiangshan/cache/dcache/data/BankedDataArray.scala
@@ -271,6 +271,7 @@ abstract class AbstractBankedDataArray(implicit p: Parameters) extends DCacheMod
     // val errors = Output(Vec(LoadPipelineWidth + 1, ValidIO(new L1CacheErrorInfo))) // read ports + readline port
     // when bank_conflict, read (1) port should be ignored
     val bank_conflict_slow = Output(Vec(LoadPipelineWidth, Bool()))
+    val rr_bank_conflict_slow = Output(Vec(LoadPipelineWidth, Bool()))
     val disable_ld_fast_wakeup = Output(Vec(LoadPipelineWidth, Bool()))
     val pseudo_error = Flipped(DecoupledIO(Vec(DCacheBanks, new CtrlUnitSignalingBundle)))
   })
@@ -460,9 +461,10 @@ class SramedDataArray(implicit p: Parameters) extends AbstractBankedDataArray {
   val perf_multi_read = PopCount(io.read.map(_.valid)) >= 2.U
   val bank_conflict_fast = Wire(Vec(LoadPipelineWidth, Bool()))
   (0 until LoadPipelineWidth).foreach(i => {
-    bank_conflict_fast(i) := wr_bank_conflict(i) || rrl_bank_conflict(i) ||
-    rr_bank_conflict_oldest(i)
+    bank_conflict_fast(i) :=
+      wr_bank_conflict(i) || rrl_bank_conflict(i) || rr_bank_conflict_oldest(i)
     io.bank_conflict_slow(i) := RegNext(bank_conflict_fast(i))
+    io.rr_bank_conflict_slow(i) := RegNext(rr_bank_conflict_oldest(i))
     io.disable_ld_fast_wakeup(i) := wr_bank_conflict(i) || rrl_bank_conflict_intend(i) ||
       (if (i == 0) 0.B else (0 until i).map(rr_bank_conflict(_)(i)).reduce(_ || _))
   })
@@ -791,9 +793,12 @@ class BankedDataArray(implicit p: Parameters) extends AbstractBankedDataArray {
   val perf_multi_read = PopCount(io.read.map(_.valid)) >= 2.U
   (0 until LoadPipelineWidth).foreach(i => {
     // remove fake rr_bank_conflict situation in s2
-    val real_other_bank_conflict_reg = RegNext(wr_bank_conflict(i) || rrl_bank_conflict(i))
+    val real_wr_bank_conflict_reg = RegNext(wr_bank_conflict(i))
+    val real_rrl_bank_conflict_reg = RegNext(rrl_bank_conflict(i))
     val real_rr_bank_conflict_reg = RegNext(rr_bank_conflict_oldest(i))
-    io.bank_conflict_slow(i) := real_other_bank_conflict_reg || real_rr_bank_conflict_reg
+    io.bank_conflict_slow(i) :=
+      real_wr_bank_conflict_reg || real_rrl_bank_conflict_reg || real_rr_bank_conflict_reg
+    io.rr_bank_conflict_slow(i) := real_rr_bank_conflict_reg
 
     // get result in s1
     io.disable_ld_fast_wakeup(i) := wr_bank_conflict(i) || rrl_bank_conflict_intend(i) ||
diff --git a/src/main/scala/xiangshan/cache/dcache/loadpipe/LoadPipe.scala b/src/main/scala/xiangshan/cache/dcache/loadpipe/LoadPipe.scala
index e41e7e1f063..52363245a55 100644
--- a/src/main/scala/xiangshan/cache/dcache/loadpipe/LoadPipe.scala
+++ b/src/main/scala/xiangshan/cache/dcache/loadpipe/LoadPipe.scala
@@ -60,6 +60,7 @@ class LoadPipe(id: Int)(implicit p: Parameters) extends DCacheModule with HasPer
 
     // banked data read conflict
     val bank_conflict_slow = Input(Bool())
+    val rr_bank_conflict_slow = Input(Bool())
 
     // send miss request to miss queue
     val miss_req    = DecoupledIO(new MissReq)
@@ -513,6 +514,7 @@ class LoadPipe(id: Int)(implicit p: Parameters) extends DCacheModule with HasPer
   io.lsu.debug_s1_hit_way := s1_tag_match_way_dup_dc
   io.lsu.s1_disable_fast_wakeup := io.disable_ld_fast_wakeup
   io.lsu.s2_bank_conflict := io.bank_conflict_slow
+  io.lsu.s2_rr_bank_conflict := io.rr_bank_conflict_slow
   io.lsu.s2_wpu_pred_fail := s2_wpu_pred_fail_and_real_hit
   io.lsu.s2_mq_nack       := (resp.bits.miss && (s2_nack_no_mshr || io.miss_req.bits.cancel || io.wbq_block_miss_req ) || s2_btot_occupy_fail)
   assert(RegNext(s1_ready && s2_ready), "load pipeline should never be blocked")
diff --git a/src/main/scala/xiangshan/mem/MemBlock.scala b/src/main/scala/xiangshan/mem/MemBlock.scala
index c2da414c34c..3249fd55b7f 100644
--- a/src/main/scala/xiangshan/mem/MemBlock.scala
+++ b/src/main/scala/xiangshan/mem/MemBlock.scala
@@ -857,9 +857,22 @@ class MemBlockInlinedImp(outer: MemBlockInlined) extends LazyModuleImp(outer)
     vSegmentFlag := false.B
   }
 
+  val rrBankConflictFastReplay = newLoadUnits.map(_.io.rrBankConflictFastReplay)
+  val rrBankConflictFastReplayCandidates = rrBankConflictFastReplay.map(_.candidate)
+  val rrBankConflictFastReplayArb = Module(new RRArbiterInit(Bool(), LduCnt))
+  rrBankConflictFastReplayArb.suggestName("rr_bank_conflict_fast_replay_arb")
+  rrBankConflictFastReplayArb.io.out.ready := true.B
+  rrBankConflictFastReplayArb.io.in.zip(rrBankConflictFastReplayCandidates).foreach { case (in, candidate) =>
+    in.valid := candidate
+    in.bits := true.B
+  }
+  val rrBankConflictFastReplayGrant = rrBankConflictFastReplayArb.io.in.map(in => in.valid && in.ready)
+  XSError(PopCount(rrBankConflictFastReplayGrant) > 1.U, "only one rr bank conflict fast replay grant is allowed")
+
   // LoadUnit
   for (i <- 0 until LduCnt) {
     newLoadUnits(i).io.redirect <> redirect
+    rrBankConflictFastReplay(i).grant := rrBankConflictFastReplayGrant(i)
 
     // get input form dispatch
     newLoadUnits(i).io.ldin <> issueLda(i)
@@ -1582,11 +1595,42 @@ class MemBlockInlinedImp(outer: MemBlockInlined) extends LazyModuleImp(outer)
   val ldDeqCount = PopCount(issueLda.map(_.valid))
   val stDeqCount = PopCount(issueSta.take(StaCnt).map(_.valid))
   val iqDeqCount = ldDeqCount +& stDeqCount
+  val s2LoadRRBankConflictCount = PopCount(dcache.io.lsu.load.map(_.s2_rr_bank_conflict))
+  val rrBankConflictFastReplayPerfStatus = rrBankConflictFastReplay.map(_.perfStatus)
+  val rrBankConflictFastReplayS3Denied =
+    rrBankConflictFastReplayPerfStatus.map(s => s.s3Candidate && !s.s3Grant)
+  val rrBankConflictFastReplayS3GrantedNotFire =
+    rrBankConflictFastReplayPerfStatus.map(s => s.s3Candidate && s.s3Grant && !s.s3Fire)
+  val rrBankConflictFastReplayS3DeniedS0Ready =
+    rrBankConflictFastReplayS3Denied.zip(rrBankConflictFastReplayPerfStatus).map {
+      case (denied, s) => denied && s.s0Ready
+    }
+
+  val rrBankConflictFastReplayPerfEvents = Seq(
+    ("s2_candidate", rrBankConflictFastReplayPerfStatus.map(_.s3Candidate), true), // s2 generate, s3 use
+    ("s2_arbiter_grant", rrBankConflictFastReplayPerfStatus.map(_.s3Grant), true), // s2 generate, s3 use
+    ("s2_arbiter_denied", rrBankConflictFastReplayS3Denied, false), // s2 generate, s3 use
+    ("s3_fire", rrBankConflictFastReplayPerfStatus.map(_.s3Fire), true),
+    ("s3_granted_not_fire", rrBankConflictFastReplayS3GrantedNotFire, true),
+    ("s3_arbiter_denied_s0_ready", rrBankConflictFastReplayS3DeniedS0Ready, false)
+  )
   XSPerfAccumulate("load_iq_deq_count", ldDeqCount)
   XSPerfHistogram("load_iq_deq_count", ldDeqCount, true.B, 0, LdExuCnt + 1)
   XSPerfAccumulate("store_iq_deq_count", stDeqCount)
   XSPerfHistogram("store_iq_deq_count", stDeqCount, true.B, 0, StAddrCnt + 1)
   XSPerfAccumulate("ls_iq_deq_count", iqDeqCount)
+  XSPerfAccumulate("s2_load_rr_bank_conflict_ge2", s2LoadRRBankConflictCount > 1.U)
+  XSPerfAccumulate("s2_load_rr_bank_conflict_eq2", s2LoadRRBankConflictCount === 2.U)
+  XSPerfAccumulate("s2_load_rr_bank_conflict_eq3", s2LoadRRBankConflictCount === 3.U)
+
+  rrBankConflictFastReplayPerfEvents.foreach { case (name, events, hasTotal) =>
+    if (hasTotal) {
+      XSPerfAccumulate(s"rr_bank_conflict_fast_replay_$name", PopCount(events))
+    }
+    events.zipWithIndex.foreach { case (event, i) =>
+      XSPerfAccumulate(s"rr_bank_conflict_fast_replay_${name}_$i", event)
+    }
+  }
 
   val perfMdpAddr = newLoadUnits.map(_.io.perfMdpAddr)
   val perfLoadUnitMdpNonStrictAddrHit = PopCount(perfMdpAddr.map(_.loadUnitNonStrictHit))
diff --git a/src/main/scala/xiangshan/mem/pipeline/Bundles.scala b/src/main/scala/xiangshan/mem/pipeline/Bundles.scala
index ff9d0b941c8..ed13eafe0fb 100644
--- a/src/main/scala/xiangshan/mem/pipeline/Bundles.scala
+++ b/src/main/scala/xiangshan/mem/pipeline/Bundles.scala
@@ -48,6 +48,20 @@ sealed trait HasLoadPipeBundleParam {
 }
 case class DefaultLoadPipeBundleParam() extends HasLoadPipeBundleParam
 
+class RRBankConflictFastReplayPerfStatus extends Bundle {
+  val s3Candidate = Bool()
+  val s3Grant = Bool()
+  val s0Ready = Bool()
+  val s3Fire = Bool()
+  val s3Denied = Bool()
+}
+
+class RRBankConflictFastReplayIO extends Bundle {
+  val candidate = Output(Bool())
+  val grant = Input(Bool())
+  val perfStatus = Output(new RRBankConflictFastReplayPerfStatus)
+}
+
 class LoadPipeBundle(
   param: HasLoadPipeBundleParam = DefaultLoadPipeBundleParam()
 )(
@@ -124,6 +138,8 @@ class LoadPipeBundle(
   val shouldFastReplay = Option.when(param.hasS2PreProcess)(Bool())
   // S2 -> S3
   val troubleMaker = Option.when(param.hasS3PreProcess)(Bool())
+  val rrBankConflictFastReplay = Option.when(param.hasS3PreProcess)(Bool())
+  val rrBankConflictFastReplayGrant = Option.when(param.hasS3PreProcess)(Bool())
   val matchInvalid = Option.when(param.hasS3PreProcess)(Bool())
   val shouldWakeup = Option.when(param.hasS3PreProcess)(Bool())
   val shouldWriteback = Option.when(param.hasS3PreProcess)(Bool())
diff --git a/src/main/scala/xiangshan/mem/pipeline/NewLoadUnit.scala b/src/main/scala/xiangshan/mem/pipeline/NewLoadUnit.scala
index 4b43a95e1ef..e9b16440bea 100644
--- a/src/main/scala/xiangshan/mem/pipeline/NewLoadUnit.scala
+++ b/src/main/scala/xiangshan/mem/pipeline/NewLoadUnit.scala
@@ -51,6 +51,7 @@ class LoadUnitS0(param: ExeUnitParams)(
     val unalignTail = Flipped(DecoupledIO(new LoadStageIO))
     val replay = Flipped(DecoupledIO(new LoadReplayIO))
     val fastReplay = Flipped(DecoupledIO(new FastReplayIO))
+    val fastReplayCanAccept = Output(Bool())
     // TODO: canAcceptHigh/LowConfPrefetch
     val prefetchReq = Flipped(DecoupledIO(new L1PrefetchReq))
     val vecldin = Flipped(DecoupledIO(new VectorLoadIn))
@@ -238,6 +239,9 @@ class LoadUnitS0(param: ExeUnitParams)(
   pipeIn.valid := sink.valid && io.dcacheReq.ready
   sink.ready := pipeIn.ready && io.dcacheReq.ready
   connectSamePort(pipeIn.bits, sink.bits)
+  io.fastReplayCanAccept :=
+    pipeIn.ready && io.dcacheReq.ready && !unalignTail.valid && !replayHiPrio.valid
+  XSError(fastReplay.valid && fastReplay.ready =/= io.fastReplayCanAccept, "fast replay canAccept must match arbiter ready")
 
   // alias for arbitration result
   val uop = sink.bits.uop
@@ -833,8 +837,13 @@ class LoadUnitS2(param: ExeUnitParams)(
     val dcacheResp = Flipped(DecoupledIO(new DCacheWordResp))
     // TODO: move this inside of dcacheResp
     val dcacheBankConflict = Input(Bool())
+    val dcacheRRBankConflict = Input(Bool())
     val dcacheMSHRNack = Input(Bool())
 
+    // Global rr bank-conflict fast replay arbitration in S2.
+    val rrBankConflictFastReplayCandidate = Output(Bool())
+    val rrBankConflictFastReplayGrant = Input(Bool())
+
     /**
       * Data forward response
       */
@@ -1147,7 +1156,17 @@ class LoadUnitS2(param: ExeUnitParams)(
   stageInfo.perfWaitStoreRetired.get := io.sqForwardResp.valid && io.sqForwardResp.bits.perfWaitStoreRetired
   stageInfo.perfIsCmaReplay.get := perfIsCmaReplay
   // Pre-process for s3
+  val rrBankConflictFastReplay =
+    !kill && fastReplay && fastReplayBankConflict && io.dcacheRRBankConflict && !exception
+  val rrBankConflictFastReplayCandidate = pipeIn.fire && rrBankConflictFastReplay
+  io.rrBankConflictFastReplayCandidate := rrBankConflictFastReplayCandidate
+  XSError(
+    io.rrBankConflictFastReplayGrant && !rrBankConflictFastReplayCandidate,
+    "rr bank conflict fast replay grant without candidate in s2"
+  )
   stageInfo.troubleMaker.get := troubleMaker
+  stageInfo.rrBankConflictFastReplay.get := rrBankConflictFastReplay
+  stageInfo.rrBankConflictFastReplayGrant.get := io.rrBankConflictFastReplayGrant
   stageInfo.shouldFastReplay.get := in.shouldFastReplay.get || fastReplay && !exception
   stageInfo.matchInvalid.get := matchInvalid && troubleMaker
   stageInfo.shouldWakeup.get := shouldWakeup
@@ -1259,6 +1278,11 @@ class LoadUnitS3(param: ExeUnitParams)(
 
     // Fast replay
     val fastReplay = DecoupledIO(new FastReplayIO)
+    // Registered S2 arbitration result used by S3 and performance counters.
+    val rrBankConflictFastReplayCandidate = Output(Bool())
+    val rrBankConflictFastReplayGrant = Output(Bool())
+    val rrBankConflictFastReplayFire = Output(Bool())
+    val rrBankConflictFastReplayDenied = Output(Bool())
 
     // RAR / RAW revoke and RAR response
     val rarNukeQueryResp = Flipped(ValidIO(new LoadNukeQueryResp))
@@ -1397,7 +1421,16 @@ class LoadUnitS3(param: ExeUnitParams)(
     * Fast replay
     */
   val shouldFastReplay = in.shouldFastReplay.get
-  val allowFastReplay = io.fastReplay.ready
+  val rrBankConflictFastReplay = in.rrBankConflictFastReplay.get
+  val rrBankConflictFastReplayCandidate =
+    shouldFastReplay && rrBankConflictFastReplay
+  val rrBankConflictFastReplayGrant = in.rrBankConflictFastReplayGrant.get
+  XSError(
+    pipeIn.valid && rrBankConflictFastReplayGrant && !rrBankConflictFastReplay,
+    "rr bank conflict fast replay grant without rr marker in s3"
+  )
+  val allowRRBankConflictFastReplay = !rrBankConflictFastReplayCandidate || rrBankConflictFastReplayGrant
+  val allowFastReplay = io.fastReplay.ready && allowRRBankConflictFastReplay
   val doFastReplay = shouldFastReplay && allowFastReplay
   val fastReplay = Wire(new FastReplayIO)
   connectSamePort(fastReplay, in)
@@ -1658,8 +1691,14 @@ class LoadUnitS3(param: ExeUnitParams)(
   io.vecldout.valid := vecldoutValid
   io.vecldout.bits := vecldout
 
-  io.fastReplay.valid := pipeIn.valid && shouldFastReplay
+  io.fastReplay.valid := pipeIn.valid && !kill && shouldFastReplay && allowRRBankConflictFastReplay
   io.fastReplay.bits := fastReplay
+  io.rrBankConflictFastReplayCandidate := rrBankConflictFastReplayCandidate
+  io.rrBankConflictFastReplayGrant := rrBankConflictFastReplayCandidate && rrBankConflictFastReplayGrant
+  io.rrBankConflictFastReplayFire :=
+    rrBankConflictFastReplayCandidate && rrBankConflictFastReplayGrant && io.fastReplay.fire
+  io.rrBankConflictFastReplayDenied :=
+    rrBankConflictFastReplayCandidate && !rrBankConflictFastReplayGrant && io.lqWrite.fire
 
   io.revokeLastCycle := revokeLastCycle
   io.revokeLastLastCycle := revokeLastLastCycle
@@ -1905,6 +1944,8 @@ class LoadUnitIO(val param: ExeUnitParams)(implicit p: Parameters) extends XSBun
   val perfLqHeadPtr = Input(new LqPtr)
   val perfLqFull = Input(Bool())
   val perfMdpAddr = Output(new PerfMdpAddr)
+  // S2 arbitration and S3 execution status for rr bank-conflict fast replay
+  val rrBankConflictFastReplay = new RRBankConflictFastReplayIO
   // Exception info
   val exceptionInfo = ValidIO(new MemExceptionInfo)
   // Data forwarding and bypass
@@ -2020,6 +2061,7 @@ class NewLoadUnit(val param: ExeUnitParams)(implicit p: Parameters) extends XSMo
   io.dcache.s2_kill := s2.io.dcacheKill
   s2.io.dcacheResp <> io.dcache.resp
   s2.io.dcacheBankConflict := io.dcache.s2_bank_conflict
+  s2.io.dcacheRRBankConflict := io.dcache.s2_rr_bank_conflict
   s2.io.dcacheMSHRNack := io.dcache.s2_mq_nack
   s2.io.sqForwardResp := io.sqForward.s2Resp
   s2.io.sbufferForwardResp := io.sbufferForward.s2Resp
@@ -2068,6 +2110,17 @@ class NewLoadUnit(val param: ExeUnitParams)(implicit p: Parameters) extends XSMo
   io.ldout.toIntRf.foreach(_.bits.data := dataPath.io.s3ShiftAndExtData(io.ldout.toIntRf.get.bits.data.getWidth - 1, 0))
   io.vecldout.bits.vecdata.get := dataPath.io.s3ShiftData
 
+  // rr bank-conflict fast replay arbiter
+  val rrBankConflictFastReplay = io.rrBankConflictFastReplay
+  val rrBankConflictFastReplayPerfStatus = rrBankConflictFastReplay.perfStatus
+  rrBankConflictFastReplay.candidate := s2.io.rrBankConflictFastReplayCandidate
+  s2.io.rrBankConflictFastReplayGrant := rrBankConflictFastReplay.grant
+  rrBankConflictFastReplayPerfStatus.s3Candidate := s3.io.rrBankConflictFastReplayCandidate
+  rrBankConflictFastReplayPerfStatus.s3Grant := s3.io.rrBankConflictFastReplayGrant
+  rrBankConflictFastReplayPerfStatus.s0Ready := s0.io.fastReplayCanAccept
+  rrBankConflictFastReplayPerfStatus.s3Fire := s3.io.rrBankConflictFastReplayFire
+  rrBankConflictFastReplayPerfStatus.s3Denied := s3.io.rrBankConflictFastReplayDenied
+
   // Debug info
   io.debugInfo.s1_isTlbFirstMiss := s1.io.debugInfo.isTlbFirstMiss
   io.debugInfo.s1_isLoadToLoadForward := s1.io.debugInfo.isLoadToLoadForward
```
