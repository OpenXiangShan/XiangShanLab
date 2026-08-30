# Commit Log
- Issue: #3396
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/3396
- Issue state: closed
- Tested RTL commit: -
- Related PR: #3396
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/3396
- Changed files: 5
- Additions: 134
- Deletions: 55

## Files
- `src/main/scala/xiangshan/XSCore.scala`
- `src/main/scala/xiangshan/frontend/Frontend.scala`
- `src/main/scala/xiangshan/frontend/NewFtq.scala`
- `src/main/scala/xiangshan/frontend/icache/ICache.scala`
- `src/main/scala/xiangshan/frontend/icache/IPrefetch.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/XSCore.scala b/src/main/scala/xiangshan/XSCore.scala
index 0e6b24ce247..85b8433b9c5 100644
--- a/src/main/scala/xiangshan/XSCore.scala
+++ b/src/main/scala/xiangshan/XSCore.scala
@@ -105,6 +105,7 @@ class XSCoreImp(outer: XSCoreBase) extends LazyModuleImp(outer)
 
   frontend.io.hartId := memBlock.io.inner_hartId
   frontend.io.reset_vector := memBlock.io.inner_reset_vector
+  frontend.io.softPrefetch <> memBlock.io.IfetchPrefetch
   frontend.io.backend <> backend.io.frontend
   frontend.io.sfence <> backend.io.frontendSfence
   frontend.io.tlbCsr <> backend.io.frontendTlbCsr
diff --git a/src/main/scala/xiangshan/frontend/Frontend.scala b/src/main/scala/xiangshan/frontend/Frontend.scala
index 17f7763b284..ac8891a7428 100644
--- a/src/main/scala/xiangshan/frontend/Frontend.scala
+++ b/src/main/scala/xiangshan/frontend/Frontend.scala
@@ -47,6 +47,7 @@ class FrontendImp (outer: Frontend) extends LazyModuleImp(outer)
     val fencei = Input(Bool())
     val ptw = new TlbPtwIO()
     val backend = new FrontendToCtrlIO
+    val softPrefetch = Vec(backendParams.LduCnt, Flipped(Valid(new SoftIfetchPrefetchBundle)))
     val sfence = Input(new SfenceBundle)
     val tlbCsr = Input(new TlbCsrBundle)
     val csrCtrl = Input(new CustomCSRCtrlIO)
@@ -120,8 +121,8 @@ class FrontendImp (outer: Frontend) extends LazyModuleImp(outer)
   val itlbRepeater1 = PTWFilter(itlbParams.fenceDelay, itlb_ptw, sfence, tlbCsr, l2tlbParams.ifilterSize)
   val itlbRepeater2 = PTWRepeaterNB(passReady = false, itlbParams.fenceDelay, itlbRepeater1.io.ptw, io.ptw, sfence, tlbCsr)
 
-  icache.io.prefetch <> ftq.io.toPrefetch
-
+  icache.io.ftqPrefetch <> ftq.io.toPrefetch
+  icache.io.softPrefetch <> io.softPrefetch
 
   //IFU-Ftq
   ifu.io.ftqInter.fromFtq <> ftq.io.toIfu
diff --git a/src/main/scala/xiangshan/frontend/NewFtq.scala b/src/main/scala/xiangshan/frontend/NewFtq.scala
index 905729154ae..32cc149b87a 100644
--- a/src/main/scala/xiangshan/frontend/NewFtq.scala
+++ b/src/main/scala/xiangshan/frontend/NewFtq.scala
@@ -184,40 +184,34 @@ class FtqToBpuIO(implicit p: Parameters) extends XSBundle {
   val redirctFromIFU = Output(Bool())
 }
 
-class FtqToIfuIO(implicit p: Parameters) extends XSBundle with HasCircularQueuePtrHelper {
+class BpuFlushInfo(implicit p: Parameters) extends XSBundle with HasCircularQueuePtrHelper {
+  // when ifu pipeline is not stalled,
+  // a packet from bpu s3 can reach f1 at most
+  val s2 = Valid(new FtqPtr)
+  val s3 = Valid(new FtqPtr)
+  def shouldFlushBy(src: Valid[FtqPtr], idx_to_flush: FtqPtr) = {
+    src.valid && !isAfter(src.bits, idx_to_flush)
+  }
+  def shouldFlushByStage2(idx: FtqPtr) = shouldFlushBy(s2, idx)
+  def shouldFlushByStage3(idx: FtqPtr) = shouldFlushBy(s3, idx)
+}
+
+class FtqToIfuIO(implicit p: Parameters) extends XSBundle {
   val req = Decoupled(new FetchRequestBundle)
   val redirect = Valid(new BranchPredictionRedirect)
   val topdown_redirect = Valid(new BranchPredictionRedirect)
-  val flushFromBpu = new Bundle {
-    // when ifu pipeline is not stalled,
-    // a packet from bpu s3 can reach f1 at most
-    val s2 = Valid(new FtqPtr)
-    val s3 = Valid(new FtqPtr)
-    def shouldFlushBy(src: Valid[FtqPtr], idx_to_flush: FtqPtr) = {
-      src.valid && !isAfter(src.bits, idx_to_flush)
-    }
-    def shouldFlushByStage2(idx: FtqPtr) = shouldFlushBy(s2, idx)
-    def shouldFlushByStage3(idx: FtqPtr) = shouldFlushBy(s3, idx)
-  }
+  val flushFromBpu = new BpuFlushInfo
 }
 
-class FtqToICacheIO(implicit p: Parameters) extends XSBundle with HasCircularQueuePtrHelper {
+class FtqToICacheIO(implicit p: Parameters) extends XSBundle {
   //NOTE: req.bits must be prepare in T cycle
   // while req.valid is set true in T + 1 cycle
   val req = Decoupled(new FtqToICacheRequestBundle)
 }
 
-class FtqToPrefetchIO(implicit p: Parameters) extends XSBundle with HasCircularQueuePtrHelper {
+class FtqToPrefetchIO(implicit p: Parameters) extends XSBundle {
   val req = Decoupled(new FtqICacheInfo)
-  val flushFromBpu = new Bundle {
-    val s2 = Valid(new FtqPtr)
-    val s3 = Valid(new FtqPtr)
-    def shouldFlushBy(src: Valid[FtqPtr], idx_to_flush: FtqPtr) = {
-      src.valid && !isAfter(src.bits, idx_to_flush)
-    }
-    def shouldFlushByStage2(idx: FtqPtr) = shouldFlushBy(s2, idx)
-    def shouldFlushByStage3(idx: FtqPtr) = shouldFlushBy(s3, idx)
-  }
+  val flushFromBpu = new BpuFlushInfo
 }
 
 trait HasBackendRedirectInfo extends HasXSParameter {
diff --git a/src/main/scala/xiangshan/frontend/icache/ICache.scala b/src/main/scala/xiangshan/frontend/icache/ICache.scala
index 375eb73ac08..f36975176d9 100644
--- a/src/main/scala/xiangshan/frontend/icache/ICache.scala
+++ b/src/main/scala/xiangshan/frontend/icache/ICache.scala
@@ -447,7 +447,8 @@ class ICacheReplacer(implicit p: Parameters) extends ICacheModule {
 class ICacheIO(implicit p: Parameters) extends ICacheBundle
 {
   val hartId      = Input(UInt(hartIdLen.W))
-  val prefetch    = Flipped(new FtqToPrefetchIO)
+  val ftqPrefetch  = Flipped(new FtqToPrefetchIO)
+  val softPrefetch = Vec(backendParams.LduCnt, Flipped(Valid(new SoftIfetchPrefetchBundle)))
   val stop        = Input(Bool())
   val fetch       = new ICacheMainPipeBundle
   val toIFU       = Output(Bool())
@@ -517,8 +518,34 @@ class ICacheImp(outer: ICache) extends LazyModuleImp(outer) with HasICacheParame
   prefetcher.io.flush             := io.flush
   prefetcher.io.csr_pf_enable     := io.csr_pf_enable
   prefetcher.io.csr_parity_enable := io.csr_parity_enable
-  prefetcher.io.ftqReq            <> io.prefetch
   prefetcher.io.MSHRResp          := missUnit.io.fetch_resp
+  prefetcher.io.flushFromBpu      := io.ftqPrefetch.flushFromBpu
+  // cache softPrefetch
+  private val softPrefetchValid = RegInit(false.B)
+  private val softPrefetch = RegInit(0.U.asTypeOf(new IPrefetchReq))
+  /* FIXME:
+   * If there is already a pending softPrefetch request, it will be overwritten.
+   * Also, if there are multiple softPrefetch requests in the same cycle, only the first one will be accepted.
+   * We should implement a softPrefetchQueue (like ibuffer, multi-in, single-out) to solve this.
+   * However, the impact on performance still needs to be assessed.
+   * Considering that the frequency of prefetch.i may not be high, let's start with a temporary dummy solution.
+   */
+  when (io.softPrefetch.map(_.valid).reduce(_||_)) {
+    softPrefetchValid := true.B
+    softPrefetch.fromSoftPrefetch(MuxCase(
+      0.U.asTypeOf(new SoftIfetchPrefetchBundle),
+      io.softPrefetch.map(req => (req.valid -> req.bits))
+    ))
+  }.elsewhen (prefetcher.io.req.fire) {
+    softPrefetchValid := false.B
+  }
+  // pass ftqPrefetch
+  private val ftqPrefetch = WireInit(0.U.asTypeOf(new IPrefetchReq))
+  ftqPrefetch.fromFtqICacheInfo(io.ftqPrefetch.req.bits)
+  // software prefetch has higher priority
+  prefetcher.io.req.valid := softPrefetchValid || io.ftqPrefetch.req.valid
+  prefetcher.io.req.bits  := Mux(softPrefetchValid, softPrefetch, ftqPrefetch)
+  io.ftqPrefetch.req.ready := prefetcher.io.req.ready && !softPrefetchValid
 
   missUnit.io.hartId            := io.hartId
   missUnit.io.fencei            := io.fencei
@@ -574,6 +601,10 @@ class ICacheImp(outer: ICache) extends LazyModuleImp(outer) with HasICacheParame
   io.error.bits <> RegEnable(Mux1H(errors.map(e => e.valid -> e.bits)), 0.U.asTypeOf(errors(0).bits), errors_valid)
   io.error.valid := RegNext(errors_valid, false.B)
 
+  XSPerfAccumulate("softPrefetch_drop_not_ready", io.softPrefetch.map(_.valid).reduce(_||_) && softPrefetchValid && !prefetcher.io.req.fire)
+  XSPerfAccumulate("softPrefetch_drop_multi_req", PopCount(io.softPrefetch.map(_.valid)) > 1.U)
+  XSPerfAccumulate("softPrefetch_block_ftq", softPrefetchValid && io.ftqPrefetch.req.valid)
+
   val perfEvents = Seq(
     ("icache_miss_cnt  ", false.B),
     ("icache_miss_penalty", BoolStopWatch(start = false.B, stop = false.B || false.B, startHighPriority = true)),
diff --git a/src/main/scala/xiangshan/frontend/icache/IPrefetch.scala b/src/main/scala/xiangshan/frontend/icache/IPrefetch.scala
index 1c22c4ea069..5497a396f72 100644
--- a/src/main/scala/xiangshan/frontend/icache/IPrefetch.scala
+++ b/src/main/scala/xiangshan/frontend/icache/IPrefetch.scala
@@ -27,18 +27,44 @@ import xiangshan.frontend._
 import xiangshan.backend.fu.{PMPReqBundle, PMPRespBundle}
 import huancun.PreferCacheKey
 import xiangshan.XSCoreParamsKey
+import xiangshan.SoftIfetchPrefetchBundle
 import utility._
 
 abstract class IPrefetchBundle(implicit p: Parameters) extends ICacheBundle
 abstract class IPrefetchModule(implicit p: Parameters) extends ICacheModule
 
+class IPrefetchReq(implicit p: Parameters) extends IPrefetchBundle {
+  val startAddr     : UInt   = UInt(VAddrBits.W)
+  val nextlineStart : UInt   = UInt(VAddrBits.W)
+  val ftqIdx        : FtqPtr = new FtqPtr
+  val isSoftPrefetch: Bool   = Bool()
+  def crossCacheline: Bool   = startAddr(blockOffBits - 1) === 1.U
+
+  def fromFtqICacheInfo(info: FtqICacheInfo): IPrefetchReq = {
+    this.startAddr := info.startAddr
+    this.nextlineStart := info.nextlineStart
+    this.ftqIdx := info.ftqIdx
+    this.isSoftPrefetch := false.B
+    this
+  }
+
+  def fromSoftPrefetch(req: SoftIfetchPrefetchBundle): IPrefetchReq = {
+    this.startAddr := req.vaddr
+    this.nextlineStart := req.vaddr + (1 << blockOffBits).U
+    this.ftqIdx := DontCare
+    this.isSoftPrefetch := true.B
+    this
+  }
+}
+
 class IPrefetchIO(implicit p: Parameters) extends IPrefetchBundle {
   // control
   val csr_pf_enable     = Input(Bool())
   val csr_parity_enable = Input(Bool())
   val flush             = Input(Bool())
 
-  val ftqReq            = Flipped(new FtqToPrefetchIO)
+  val req               = Flipped(Decoupled(new IPrefetchReq))
+  val flushFromBpu      = Flipped(new BpuFlushInfo)
   val itlb              = Vec(PortNumber, new TlbRequestIO)
   val pmp               = Vec(PortNumber, new ICachePMPBundle)
   val metaRead          = new ICacheMetaReqBundle
@@ -51,7 +77,6 @@ class IPrefetchPipe(implicit p: Parameters) extends  IPrefetchModule
 {
   val io: IPrefetchIO = IO(new IPrefetchIO)
 
-  val fromFtq = io.ftqReq
   val (toITLB,  fromITLB) = (io.itlb.map(_.req), io.itlb.map(_.resp))
   val (toPMP,  fromPMP)   = (io.pmp.map(_.req), io.pmp.map(_.resp))
   val (toMeta,  fromMeta) = (io.metaRead.toIMeta,  io.metaRead.fromIMeta)
@@ -72,24 +97,25 @@ class IPrefetchPipe(implicit p: Parameters) extends  IPrefetchModule
     * - 3. send req to Meta SRAM
     ******************************************************************************
     */
-  val s0_valid  = fromFtq.req.valid
+  val s0_valid  = io.req.valid
 
   /**
     ******************************************************************************
     * receive ftq req
     ******************************************************************************
     */
-  val s0_req_vaddr    = VecInit(Seq(fromFtq.req.bits.startAddr, fromFtq.req.bits.nextlineStart))
-  val s0_req_ftqIdx   = fromFtq.req.bits.ftqIdx
-  val s0_doubleline   = fromFtq.req.bits.crossCacheline
+  val s0_req_vaddr    = VecInit(Seq(io.req.bits.startAddr, io.req.bits.nextlineStart))
+  val s0_req_ftqIdx   = io.req.bits.ftqIdx
+  val s0_isSoftPrefetch = io.req.bits.isSoftPrefetch
+  val s0_doubleline   = io.req.bits.crossCacheline
   val s0_req_vSetIdx  = s0_req_vaddr.map(get_idx)
 
-  from_bpu_s0_flush := fromFtq.flushFromBpu.shouldFlushByStage2(s0_req_ftqIdx) ||
-                       fromFtq.flushFromBpu.shouldFlushByStage3(s0_req_ftqIdx)
+  from_bpu_s0_flush := !s0_isSoftPrefetch && (io.flushFromBpu.shouldFlushByStage2(s0_req_ftqIdx) ||
+                                              io.flushFromBpu.shouldFlushByStage3(s0_req_ftqIdx))
   s0_flush := io.flush || from_bpu_s0_flush || s1_flush
 
   val s0_can_go = s1_ready && toITLB(0).ready && toITLB(1).ready && toMeta.ready
-  fromFtq.req.ready := s0_can_go
+  io.req.ready := s0_can_go
 
   s0_fire := s0_valid && s0_can_go && !s0_flush
 
@@ -105,6 +131,7 @@ class IPrefetchPipe(implicit p: Parameters) extends  IPrefetchModule
   val s1_valid = generatePipeControl(lastFire = s0_fire, thisFire = s1_fire, thisFlush = s1_flush, lastFlush = false.B)
 
   val s1_req_vaddr    = RegEnable(s0_req_vaddr, 0.U.asTypeOf(s0_req_vaddr), s0_fire)
+  val s1_isSoftPrefetch = RegEnable(s0_isSoftPrefetch, 0.U.asTypeOf(s0_isSoftPrefetch), s0_fire)
   val s1_doubleline   = RegEnable(s0_doubleline, 0.U.asTypeOf(s0_doubleline), s0_fire)
   val s1_req_ftqIdx   = RegEnable(s0_req_ftqIdx, 0.U.asTypeOf(s0_req_ftqIdx), s0_fire)
   val s1_req_vSetIdx  = VecInit(s1_req_vaddr.map(get_idx))
@@ -256,7 +283,8 @@ class IPrefetchPipe(implicit p: Parameters) extends  IPrefetchModule
     ******** **********************************************************************
     */
   // Disallow enqueuing wayLookup when SRAM write occurs.
-  toWayLookup.valid             := ((state === m_enqWay) || ((state === m_idle) && itlb_finish)) && !s1_flush && !fromMSHR.valid
+  toWayLookup.valid             := ((state === m_enqWay) || ((state === m_idle) && itlb_finish)) &&
+    !s1_flush && !fromMSHR.valid && !s1_isSoftPrefetch  // do not enqueue soft prefetch
   toWayLookup.bits.vSetIdx      := s1_req_vSetIdx
   toWayLookup.bits.waymask      := s1_waymasks
   toWayLookup.bits.ptag         := s1_req_ptags
@@ -309,27 +337,43 @@ class IPrefetchPipe(implicit p: Parameters) extends  IPrefetchModule
 
   switch(state) {
     is(m_idle) {
-      when(s1_valid && !itlb_finish) {
-        next_state := m_itlbResend
-      }.elsewhen(s1_valid && itlb_finish && !toWayLookup.fire) {
-        next_state := m_enqWay
-      }.elsewhen(s1_valid && itlb_finish && toWayLookup.fire && !s2_ready) {
-        next_state := m_enterS2
-      }
+      when(s1_valid) {
+        when(!itlb_finish) {
+          next_state := m_itlbResend
+        }.elsewhen(!toWayLookup.fire && !s1_isSoftPrefetch) {  // itlb_finish
+          next_state := m_enqWay
+        }.elsewhen(!s2_ready) { // itlb_finish && (toWayLookup.fire || s1_isSoftPrefetch)
+          next_state := m_enterS2
+        } // .otherwise { next_state := m_idle }
+      } // .otherwise { next_state := m_idle }  // !s1_valid
     }
     is(m_itlbResend) {
-      when(itlb_finish && !toMeta.ready) {
-        next_state := m_metaResend
-      }.elsewhen(itlb_finish && toMeta.ready) {
-        next_state := m_enqWay
-      }
+      when(itlb_finish) {
+        when(!toMeta.ready) {
+          next_state := m_metaResend
+        }.elsewhen(!s1_isSoftPrefetch) { // toMeta.ready
+          next_state := m_enqWay
+        }.elsewhen(!s2_ready) { // toMeta.ready && s1_isSoftPrefetch
+          next_state := m_enterS2
+        }.otherwise { // toMeta.ready && s1_isSoftPrefetch && s2_ready
+          next_state := m_idle
+        }
+      } // .otherwise { next_state := m_itlbResend }  // !itlb_finish
     }
     is(m_metaResend) {
       when(toMeta.ready) {
-        next_state := m_enqWay
-      }
+        when (!s1_isSoftPrefetch) {
+          next_state := m_enqWay
+        }.elsewhen(!s2_ready) { // s1_isSoftPrefetch
+          next_state := m_enterS2
+        }.otherwise { // s1_isSoftPrefetch && s2_ready
+          next_state := m_idle
+        }
+      } // .otherwise { next_state := m_metaResend }  // !toMeta.ready
     }
     is(m_enqWay) {
+      // sanity check
+      assert(!s1_isSoftPrefetch, "Soft prefetch enters m_enqWay")
       when(toWayLookup.fire && !s2_ready) {
         next_state := m_enterS2
       }.elsewhen(toWayLookup.fire && s2_ready) {
@@ -348,7 +392,7 @@ class IPrefetchPipe(implicit p: Parameters) extends  IPrefetchModule
   }
 
   /** Stage 1 control */
-  from_bpu_s1_flush := s1_valid && fromFtq.flushFromBpu.shouldFlushByStage3(s1_req_ftqIdx)
+  from_bpu_s1_flush := s1_valid && !s1_isSoftPrefetch && io.flushFromBpu.shouldFlushByStage3(s1_req_ftqIdx)
   s1_flush := io.flush || from_bpu_s1_flush
 
   s1_ready      := next_state === m_idle
@@ -365,6 +409,7 @@ class IPrefetchPipe(implicit p: Parameters) extends  IPrefetchModule
   val s2_valid  = generatePipeControl(lastFire = s1_real_fire, thisFire = s2_fire, thisFlush = s2_flush, lastFlush = false.B)
 
   val s2_req_vaddr    = RegEnable(s1_req_vaddr,     0.U.asTypeOf(s1_req_vaddr),     s1_real_fire)
+  val s2_isSoftPrefetch = RegEnable(s1_isSoftPrefetch, 0.U.asTypeOf(s1_isSoftPrefetch), s1_real_fire)
   val s2_doubleline   = RegEnable(s1_doubleline,    0.U.asTypeOf(s1_doubleline),    s1_real_fire)
   val s2_req_paddr    = RegEnable(s1_req_paddr,     0.U.asTypeOf(s1_req_paddr),     s1_real_fire)
   val s2_exception    = RegEnable(s1_exception_out, 0.U.asTypeOf(s1_exception_out), s1_real_fire)  // includes itlb/pmp/meta exception
@@ -436,10 +481,17 @@ class IPrefetchPipe(implicit p: Parameters) extends  IPrefetchModule
   s2_fire       := s2_valid && s2_finish && !s2_flush
 
   /** PerfAccumulate */
-  // the number of prefetch request received from ftq
-  XSPerfAccumulate("prefetch_req_receive", fromFtq.req.fire)
+  // the number of bpu flush
+  XSPerfAccumulate("bpu_s0_flush", from_bpu_s0_flush)
+  XSPerfAccumulate("bpu_s1_flush", from_bpu_s1_flush)
+  // the number of prefetch request received from ftq or backend (software prefetch)
+//  XSPerfAccumulate("prefetch_req_receive", io.req.fire)
+  XSPerfAccumulate("prefetch_req_receive_hw", io.req.fire && !io.req.bits.isSoftPrefetch)
+  XSPerfAccumulate("prefetch_req_receive_sw", io.req.fire && io.req.bits.isSoftPrefetch)
   // the number of prefetch request sent to missUnit
-  XSPerfAccumulate("prefetch_req_send", toMSHR.fire)
+//  XSPerfAccumulate("prefetch_req_send", toMSHR.fire)
+  XSPerfAccumulate("prefetch_req_send_hw", toMSHR.fire && !s2_isSoftPrefetch)
+  XSPerfAccumulate("prefetch_req_send_sw", toMSHR.fire && s2_isSoftPrefetch)
   XSPerfAccumulate("to_missUnit_stall", toMSHR.valid && !toMSHR.ready)
   /**
     * Count the number of requests that are filtered for various reasons.
```
