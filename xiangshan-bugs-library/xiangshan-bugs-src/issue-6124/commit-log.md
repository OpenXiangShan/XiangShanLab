# Commit Log
- Issue: #6124
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/6124
- Issue state: closed
- Tested RTL commit: -
- Related PR: #6124
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/6124
- Changed files: 14
- Additions: 539
- Deletions: 95

## Files
- `XSCache`
- `src/main/scala/top/Configs.scala`
- `src/main/scala/xiangshan/L2Top.scala`
- `src/main/scala/xiangshan/Parameters.scala`
- `src/main/scala/xiangshan/XSCore.scala`
- `src/main/scala/xiangshan/XSTile.scala`
- `src/main/scala/xiangshan/cache/dcache/DCacheWrapper.scala`
- `src/main/scala/xiangshan/cache/dcache/mainpipe/MissQueue.scala`
- `src/main/scala/xiangshan/cache/dcache/mainpipe/WritebackQueue.scala`
- `src/main/scala/xiangshan/mem/MemBlock.scala`
- `src/main/scala/xiangshan/mem/lsqueue/LSQWrapper.scala`
- `src/main/scala/xiangshan/mem/lsqueue/LoadQueue.scala`
- `src/main/scala/xiangshan/mem/lsqueue/LoadQueueReplay.scala`
- `src/main/scala/xiangshan/mem/pipeline/NewLoadUnit.scala`

## Diff
```diff
diff --git a/XSCache b/XSCache
index 54a2a7527f7..d6072bc6beb 160000
--- a/XSCache
+++ b/XSCache
@@ -1 +1 @@
-Subproject commit 54a2a7527f7366e3cb3fe378b1f1e92f18cbf33f
+Subproject commit d6072bc6bebd04b06209c603df67fd3fb64df217
diff --git a/src/main/scala/top/Configs.scala b/src/main/scala/top/Configs.scala
index 771119e44a5..9880ee5de71 100644
--- a/src/main/scala/top/Configs.scala
+++ b/src/main/scala/top/Configs.scala
@@ -272,7 +272,7 @@ class MinimalConfig(n: Int = 1) extends Config(
     })
 )
 
-case class WithNKBL1D(n: Int, ways: Int = 8) extends Config((site, here, up) => {
+case class WithNKBL1D(n: Int, ways: Int = 8, numMemChannels: Int = 1) extends Config((site, here, up) => {
   case XSTileKey =>
     val sets = n * 1024 / ways / 64
     up(XSTileKey).map(_.copy(
@@ -286,6 +286,7 @@ case class WithNKBL1D(n: Int, ways: Int = 8) extends Config((site, here, up) =>
         nProbeEntries = 8,
         nReleaseEntries = 18,
         nMaxPrefetchEntry = 6,
+        numMemChannels = numMemChannels,
         enableTagEcc = true,
         enableDataEcc = true,
         cacheCtrlAddressOpt = Some(AddressSet(0x38022000, 0x7f))
@@ -533,7 +534,7 @@ class FuzzConfig(dummy: Int = 0) extends Config(
 class DefaultConfig(n: Int = 1) extends Config(
   OpenLLCConfig("16MB", ways = 16, banks = 4)
     ++ L2CacheConfig("2MB", inclusive = true, banks = 4, tp = false)
-    ++ WithNKBL1D(64, ways = 4)
+    ++ WithNKBL1D(64, ways = 4, numMemChannels = 2)
     ++ new BaseConfig(n)
 )
 
diff --git a/src/main/scala/xiangshan/L2Top.scala b/src/main/scala/xiangshan/L2Top.scala
index d4f20cb5cd4..2a16c71835a 100644
--- a/src/main/scala/xiangshan/L2Top.scala
+++ b/src/main/scala/xiangshan/L2Top.scala
@@ -108,12 +108,20 @@ class L2TopInlined()(implicit p: Parameters) extends LazyModule
 
   println(s"enableCHI: ${enableCHI}")
   val l2cache = if (enableL2) {
+    val sliceCoherentClientMap =
+      if (coreParams.dcacheParametersOpt.exists(p => p.numMemChannels == 2 && p.channelSelByAddr) &&
+          coreParams.L2NBanks % 2 == 0) {
+        Some(Seq.tabulate(coreParams.L2NBanks)(i => i % 2))
+      } else {
+        None
+      }
     val config = new Config((_, _, _) => {
       case L2ParamKey => coreParams.L2CacheParamsOpt.get.copy(
         hartId = p(XSCoreParamsKey).HartId,
         FPGAPlatform = debugOpts.FPGAPlatform,
         hasMbist = hasMbist,
-        PrivateClintRange = if(UsePrivateClint) Some(TIMERRange) else None
+        PrivateClintRange = if(UsePrivateClint) Some(TIMERRange) else None,
+        sliceCoherentClientMap = sliceCoherentClientMap
       )
       case CHIIssue => p(CHIIssue)
       case CHIAddrWidthKey => p(CHIAddrWidthKey)
@@ -223,7 +231,7 @@ class L2TopInlined()(implicit p: Parameters) extends LazyModule
       val pfCtrlFromCore = Input(new PrefetchCtrlFromCore)
       val l2_tlb_req = new TlbRequestIO(nRespDups = 2)
       val l2_pmp_resp = Flipped(new PMPRespBundle)
-      val l2_hint = ValidIO(new L2ToL1Hint())
+      val l2_hint = Vec(numMemChannelsFromDcache, ValidIO(new L2ToL1Hint()))
       val perfEvents = Output(Vec(numPCntHc * coreParams.L2NBanks + 1, new PerfEvent))
       val l2_flush_en = Option.when(EnablePowerDown) (Input(Bool()))
       val l2_flush_done = Option.when(EnablePowerDown) (Output(Bool()))
diff --git a/src/main/scala/xiangshan/Parameters.scala b/src/main/scala/xiangshan/Parameters.scala
index 93f318173bd..ec6cc266826 100644
--- a/src/main/scala/xiangshan/Parameters.scala
+++ b/src/main/scala/xiangshan/Parameters.scala
@@ -794,6 +794,7 @@ trait HasXSParameter {
   def icacheCtrlAddress = coreParams.frontendParameters.icacheParameters.ctrlUnitParameters.Address // valid only when icacheCtrlEnabled is true
 
   def dcacheParameters = coreParams.dcacheParametersOpt.getOrElse(DCacheParameters())
+  def numMemChannelsFromDcache = coreParams.dcacheParametersOpt.map(_.numMemChannels).getOrElse(1)
 
   // dcache block cacheline when lr for LRSCCycles - LRSCBackOff cycles
   // for constrained LR/SC loop
diff --git a/src/main/scala/xiangshan/XSCore.scala b/src/main/scala/xiangshan/XSCore.scala
index f0647fc7237..6210ee646aa 100644
--- a/src/main/scala/xiangshan/XSCore.scala
+++ b/src/main/scala/xiangshan/XSCore.scala
@@ -101,7 +101,7 @@ class XSCoreImp(outer: XSCoreBase) extends LazyModuleImp(outer)
     val l2PfCtrl = Output(new PrefetchCtrlFromCore)
     val perfEvents = Input(Vec(numPCntHc * coreParams.L2NBanks + 1, new PerfEvent))
     val beu_errors = Output(new XSL1BusErrors())
-    val l2_hint = Input(Valid(new L2ToL1Hint()))
+    val l2_hint = Input(Vec(numMemChannelsFromDcache, Valid(new L2ToL1Hint())))
     val l2_tlb_req = Flipped(new TlbRequestIO(nRespDups = 2))
     val l2_pmp_resp = new PMPRespBundle
     val l2PfqBusy = Input(Bool())
@@ -220,11 +220,9 @@ class XSCoreImp(outer: XSCoreBase) extends LazyModuleImp(outer)
   memBlock.io.ooo_to_mem.isVlsException         := backend.io.mem.isVlsException
 
   memBlock.io.fetch_to_mem.itlb <> frontend.io.ptw
-  memBlock.io.l2_hint.valid := io.l2_hint.valid
-  memBlock.io.l2_hint.bits.sourceId := io.l2_hint.bits.sourceId
+  memBlock.io.l2_hint <> io.l2_hint
   memBlock.io.l2_tlb_req <> io.l2_tlb_req
   memBlock.io.l2_pmp_resp <> io.l2_pmp_resp
-  memBlock.io.l2_hint.bits.isKeyword := io.l2_hint.bits.isKeyword
   memBlock.io.l2PfqBusy := io.l2PfqBusy
 
   // if l2 prefetcher use stream prefetch, it should be placed in XSCore
diff --git a/src/main/scala/xiangshan/XSTile.scala b/src/main/scala/xiangshan/XSTile.scala
index 15ee0a6ffea..67297a8b853 100644
--- a/src/main/scala/xiangshan/XSTile.scala
+++ b/src/main/scala/xiangshan/XSTile.scala
@@ -59,9 +59,17 @@ class XSTile()(implicit p: Parameters) extends LazyModule
 
   // =========== Components' Connection ============
   // L1 to l1_xbar
-  coreParams.dcacheParametersOpt.map { _ =>
+  coreParams.dcacheParametersOpt.map { params =>
     l2top.inner.misc_l2_pmu := l2top.inner.l1d_logger := memBlock.dcache_port :=
       memBlock.l1d_to_l2_buffer.node := memBlock.dcache.clientNode
+
+    if (params.numMemChannels > 1 &&
+        memBlock.dcache_port_1.isDefined &&
+        memBlock.l1d_to_l2_buffer_1.isDefined &&
+        memBlock.dcache.clientNode_1.isDefined) {
+      l2top.inner.misc_l2_pmu := memBlock.dcache_port_1.get :=
+        memBlock.l1d_to_l2_buffer_1.get.node := memBlock.dcache.clientNode_1.get
+    }
   }
 
   l2top.inner.misc_l2_pmu := l2top.inner.l1i_logger := memBlock.frontendBridge.icache_node
@@ -180,9 +188,7 @@ class XSTile()(implicit p: Parameters) extends LazyModule
       l2top.module.io.pfCtrlFromCore := core.module.io.l2PfCtrl
 
       l2top.module.io.beu_errors.l2 <> 0.U.asTypeOf(l2top.module.io.beu_errors.l2)
-      core.module.io.l2_hint.bits.sourceId := l2top.module.io.l2_hint.bits.sourceId
-      core.module.io.l2_hint.bits.isKeyword := l2top.module.io.l2_hint.bits.isKeyword
-      core.module.io.l2_hint.valid := l2top.module.io.l2_hint.valid
+      core.module.io.l2_hint <> l2top.module.io.l2_hint
 
       core.module.io.l2PfqBusy := false.B
       core.module.io.debugTopDown.l2MissMatch := l2top.module.io.debugTopDown.l2MissMatch
@@ -196,9 +202,7 @@ class XSTile()(implicit p: Parameters) extends LazyModule
     } else {
 
       l2top.module.io.beu_errors.l2 <> 0.U.asTypeOf(l2top.module.io.beu_errors.l2)
-      core.module.io.l2_hint.bits.sourceId := l2top.module.io.l2_hint.bits.sourceId
-      core.module.io.l2_hint.bits.isKeyword := l2top.module.io.l2_hint.bits.isKeyword
-      core.module.io.l2_hint.valid := l2top.module.io.l2_hint.valid
+      core.module.io.l2_hint <> l2top.module.io.l2_hint
 
       core.module.io.l2PfqBusy := false.B
       core.module.io.debugTopDown.l2MissMatch := false.B
diff --git a/src/main/scala/xiangshan/cache/dcache/DCacheWrapper.scala b/src/main/scala/xiangshan/cache/dcache/DCacheWrapper.scala
index 2e97e43e917..904192a7863 100644
--- a/src/main/scala/xiangshan/cache/dcache/DCacheWrapper.scala
+++ b/src/main/scala/xiangshan/cache/dcache/DCacheWrapper.scala
@@ -58,6 +58,17 @@ case class DCacheParameters
   enableDataEcc: Boolean = false,
   enableTagEcc: Boolean = false,
   cacheCtrlAddressOpt: Option[AddressSet] = None,
+
+  // ========== Dual-channel support ==========
+  // Number of memory channels for L1-L2 interface
+  // 1 = single channel (default)
+  // 2 = dual channel (2x bandwidth potential)
+  numMemChannels: Int = 1,
+
+  // Channel selection strategy
+  // true = select by address set低位
+  // false = select by MSHR ID
+  channelSelByAddr: Boolean = true
 ) extends L1CacheParameters {
   // if sets * blockBytes > 4KB(page size),
   // cache alias will happen,
@@ -137,6 +148,13 @@ trait HasDCacheParameters
   val EnableDataEcc = cacheParams.enableDataEcc
   val EnableTagEcc = cacheParams.enableTagEcc
 
+  // ========== Dual-channel support ==========
+  val numMemChannels = cacheParams.numMemChannels
+  val memChannelBits = log2Up(numMemChannels max 2)
+  val channelSelByAddr = cacheParams.channelSelByAddr
+  val hasDualChannel = numMemChannels > 1
+  require(numMemChannels <= 2, s"DCache currently supports at most 2 memory channels, got $numMemChannels")
+
   // banked dcache support
   val DCacheSetDiv = 1
   val DCacheSets = cacheParams.nSets
@@ -729,11 +747,11 @@ class StorePrefetchReq(implicit p: Parameters) extends DCacheBundle {
 class DCacheToLsuIO(implicit p: Parameters) extends DCacheBundle {
   val load  = Vec(LoadPipelineWidth, Flipped(new DCacheLoadIO)) // for speculative load
   val sta   = Vec(StorePipelineWidth, Flipped(new DCacheStoreIO)) // for non-blocking store
-  val loadWakeup = ValidIO(new DCacheLoadWakeup())
+  val loadWakeup = Vec(cfg.numMemChannels, ValidIO(new DCacheLoadWakeup()))
   val store = new DCacheToSbufferIO // for sbuffer
   val atomics  = Flipped(new AtomicWordIO)  // atomics reqs
   val release = ValidIO(new Release) // cacheline release hint for ld-ld violation check
-  val forward_D = Flipped(Vec(LoadPipelineWidth, new DCacheForward))
+  val forward_D = Flipped(Vec(LoadPipelineWidth, Vec(cfg.numMemChannels, new DCacheForward)))
   val forward_mshr = Flipped(Vec(LoadPipelineWidth, new DCacheForward))
   // If a store is miss and accepted by mshr, Sbuffer releases the entry and mshr provides corresponding st-ld forwarding data.
   val forward_mshrStData = Flipped(Vec(LoadPipelineWidth, new SbufferForwardReq))
@@ -760,7 +778,7 @@ class DCacheIO(implicit p: Parameters) extends DCacheBundle {
   val sms_agt_evict_req = DecoupledIO(new AGTEvictReq)
   val debugTopDown = new DCacheTopDownIO
   val debugRolling = Flipped(new RobDebugRollingIO)
-  val l2_hint = Input(Valid(new L2ToL1Hint()))
+  val l2_hint = Vec(cfg.numMemChannels, Input(Valid(new L2ToL1Hint())))
   val cmoOpReq = Flipped(DecoupledIO(new CMOReq))
   val cmoOpResp = DecoupledIO(new CMOResp)
   val l1Miss = Output(Bool())
@@ -869,6 +887,11 @@ class DCache()(implicit p: Parameters) extends LazyModule with HasDCacheParamete
     Seq(TLMasterParameters.v1(
       name = "dcache",
       sourceId = IdRange(0, nEntries + 1),
+      visibility =
+        if (numMemChannels == 2 && channelSelByAddr)
+          Seq(AddressSet(0x0, ~BigInt(cfg.blockBytes)))
+        else
+          Seq(AddressSet.everything),
       supportsProbe = TransferSizes(cfg.blockBytes)
     )),
     requestFields = reqFields,
@@ -876,6 +899,25 @@ class DCache()(implicit p: Parameters) extends LazyModule with HasDCacheParamete
   )
 
   val clientNode = TLClientNode(Seq(clientParameters))
+
+  // ========== Dual-channel support ==========
+  // Second clientNode for dual-channel L1-L2 interface
+  val clientNode_1 = if (numMemChannels > 1) {
+    Some(TLClientNode(Seq(clientParameters.v1copy(
+      clients = Seq(TLMasterParameters.v1(
+        name = "dcache_ch1",
+        // node_1 is an independent TL input, so source IDs remain local to the channel.
+        sourceId = IdRange(0, nEntries + 1),
+        visibility =
+          if (channelSelByAddr)
+            Seq(AddressSet(cfg.blockBytes, ~BigInt(cfg.blockBytes)))
+          else
+            Seq(AddressSet.everything),
+        supportsProbe = TransferSizes(cfg.blockBytes)
+      ))
+    ))))
+  } else None
+
   val cacheCtrlOpt = cacheCtrlParamsOpt.map(params => LazyModule(new CtrlUnit(params)))
 
   lazy val module = new DCacheImp(this)
@@ -890,6 +932,13 @@ class DCacheImp(outer: DCache) extends LazyModuleImp(outer) with HasDCacheParame
   val (bus, edge) = outer.clientNode.out.head
   require(bus.d.bits.data.getWidth == l1BusDataWidth, "DCache: tilelink width does not match")
 
+  // ========== Dual-channel support ==========
+  val (bus_ch1, edge_ch1) = if (numMemChannels > 1 && outer.clientNode_1.isDefined) {
+    val (bus, edge) = outer.clientNode_1.get.out.head
+    (Some(bus), Some(edge))
+  } else (None, None)
+
+
   println("DCache:")
   println("  DCacheSets: " + DCacheSets)
   println("  DCacheSetDiv: " + DCacheSetDiv)
@@ -1228,7 +1277,7 @@ class DCacheImp(outer: DCache) extends LazyModuleImp(outer) with HasDCacheParame
     ldu(i).io.bank_conflict_slow := bankedDataArray.io.bank_conflict_slow(i)
   })
 
-  io.lsu.forward_D.zipWithIndex.foreach { case (forward, i) =>
+  def processChannel(forward: DCacheForward, bus: TLBundle, i: Int): Unit = {
     val s0ReqValid = forward.s0Req.valid
     val s0Req = forward.s0Req.bits
     val s1ReqValid = RegNext(s0ReqValid)
@@ -1254,10 +1303,33 @@ class DCacheImp(outer: DCache) extends LazyModuleImp(outer) with HasDCacheParame
     s2Resp.bits.denied := RegEnable(bus.d.bits.denied, s1ReqValid)
     s2Resp.bits.corrupt := RegEnable(bus.d.bits.corrupt, s1ReqValid)
   }
+
+  io.lsu.forward_D.zipWithIndex.foreach { case (forwards, i) =>
+    processChannel(forwards(0), bus, i)
+  
+    if (hasDualChannel && bus_ch1.isDefined) {
+      processChannel(forwards(1), bus_ch1.get, i)
+    }
+  }
+
   // tl D channel wakeup
-  io.lsu.loadWakeup.valid := (bus.d.bits.opcode === TLMessages.GrantData || bus.d.bits.opcode === TLMessages.Grant) &&
-    bus.d.valid
-  io.lsu.loadWakeup.bits.mshrId := bus.d.bits.source
+  val loadWakeups = Wire(chiselTypeOf(io.lsu.loadWakeup))
+  loadWakeups.foreach { wakeup =>
+    wakeup.valid := false.B
+    wakeup.bits := 0.U.asTypeOf(wakeup.bits)
+  }
+
+  when (bus.d.bits.opcode === TLMessages.GrantData || bus.d.bits.opcode === TLMessages.Grant) {
+    loadWakeups(0).valid := bus.d.valid
+    loadWakeups(0).bits.mshrId := bus.d.bits.source(log2Up(cfg.nMissEntries) - 1, 0)
+  }
+  if (hasDualChannel && bus_ch1.isDefined) {
+    when (bus_ch1.get.d.bits.opcode === TLMessages.GrantData || bus_ch1.get.d.bits.opcode === TLMessages.Grant) {
+      loadWakeups(1).valid := bus_ch1.get.d.valid
+      loadWakeups(1).bits.mshrId := bus_ch1.get.d.bits.source(log2Up(cfg.nMissEntries) - 1, 0)
+    }
+  }
+  io.lsu.loadWakeup := loadWakeups
   mainPipe.io.force_write <> io.force_write
 
   /** dwpu */
@@ -1446,21 +1518,34 @@ class DCacheImp(outer: DCache) extends LazyModuleImp(outer) with HasDCacheParame
   // tilelink stuff
   bus.a <> missQueue.io.mem_acquire
   bus.e <> missQueue.io.mem_finish
+
+  // ========== Dual-channel support ==========
+  // Connect second TL interface when dual-channel is enabled
+  if (hasDualChannel && bus_ch1.isDefined) {
+    bus_ch1.get.a <> missQueue.io.mem_acquire_1.get
+    bus_ch1.get.e <> missQueue.io.mem_finish_1.get
+  }
+
   missQueue.io.evict_set := mainPipe.io.evict_set
   missQueue.io.btot_ways_for_set <> mainPipe.io.btot_ways_for_set
   missQueue.io.replace <> mainPipe.io.replace
-  missQueue.io.probe.req.valid := bus.b.valid
-  missQueue.io.probe.req.bits.addr := bus.b.bits.address
+  val probeArb = Module(new RRArbiter(new TLBundleB(edge.bundle), if (hasDualChannel) 2 else 1))
+  probeArb.io.in.head <> bus.b
+  if (hasDualChannel && bus_ch1.isDefined) {
+    probeArb.io.in(1) <> bus_ch1.get.b
+  }
+  missQueue.io.probe.req.valid := probeArb.io.out.valid
+  missQueue.io.probe.req.bits.addr := probeArb.io.out.bits.address
   if(DCacheAboveIndexOffset > DCacheTagOffset) {
     // have alias problem, extra alias bits needed for index
-    val alias_addr_frag = bus.b.bits.data(2, 1)
+    val alias_addr_frag = probeArb.io.out.bits.data(2, 1)
     missQueue.io.probe.req.bits.vaddr := Cat(
-      bus.b.bits.address(PAddrBits - 1, DCacheAboveIndexOffset), // dontcare
+      probeArb.io.out.bits.address(PAddrBits - 1, DCacheAboveIndexOffset), // dontcare
       alias_addr_frag(DCacheAboveIndexOffset - DCacheTagOffset - 1, 0), // index
-      bus.b.bits.address(DCacheTagOffset - 1, 0)                 // index & others
+      probeArb.io.out.bits.address(DCacheTagOffset - 1, 0)                 // index & others
     )
   } else { // no alias problem
-    missQueue.io.probe.req.bits.vaddr := bus.b.bits.address
+    missQueue.io.probe.req.bits.vaddr := probeArb.io.out.bits.address
   }
 
   missQueue.io.main_pipe_resp.valid := RegNext(mainPipe.io.atomic_resp.valid)
@@ -1469,7 +1554,7 @@ class DCacheImp(outer: DCache) extends LazyModuleImp(outer) with HasDCacheParame
   //----------------------------------------
   // probe
   // probeQueue.io.mem_probe <> bus.b
-  block_decoupled(bus.b, probeQueue.io.mem_probe, missQueue.io.probe.block)
+  block_decoupled(probeArb.io.out, probeQueue.io.mem_probe, missQueue.io.probe.block)
   probeQueue.io.lrsc_locked_block <> mainPipe.io.lrsc_locked_block
   probeQueue.io.update_resv_set <> mainPipe.io.update_resv_set
 
@@ -1506,7 +1591,12 @@ class DCacheImp(outer: DCache) extends LazyModuleImp(outer) with HasDCacheParame
   // wb
   // add a queue between MainPipe and WritebackUnit to reduce MainPipe stalls due to WritebackUnit busy
   wb.io.req <> mainPipe.io.wb
-  bus.c     <> wb.io.mem_release
+  if (hasDualChannel && bus_ch1.isDefined) {
+    bus.c <> wb.io.mem_release
+    bus_ch1.get.c <> wb.io.mem_release_1.get
+  } else {
+    bus.c <> wb.io.mem_release
+  }
   // wb.io.release_wakeup := refillPipe.io.release_wakeup
   // wb.io.release_update := mainPipe.io.release_update
   //wb.io.probe_ttob_check_req <> mainPipe.io.probe_ttob_check_req
@@ -1527,14 +1617,45 @@ class DCacheImp(outer: DCache) extends LazyModuleImp(outer) with HasDCacheParame
   wb.io.mem_grant.valid := false.B
   wb.io.mem_grant.bits  := DontCare
 
-  // in L1DCache, we ony expect Grant[Data] and ReleaseAck
-  bus.d.ready := false.B
-  when (bus.d.bits.opcode === TLMessages.Grant || bus.d.bits.opcode === TLMessages.GrantData || bus.d.bits.opcode === TLMessages.CBOAck) {
-    missQueue.io.mem_grant <> bus.d
-  } .elsewhen (bus.d.bits.opcode === TLMessages.ReleaseAck) {
-    wb.io.mem_grant <> bus.d
-  } .otherwise {
-    assert (!bus.d.fire)
+  // ========== Dual-channel support ==========
+  if (hasDualChannel && bus_ch1.isDefined) {
+    missQueue.io.mem_grant_1.get.valid := false.B
+    missQueue.io.mem_grant_1.get.bits := DontCare
+    wb.io.mem_grant_1.get.valid := false.B
+    wb.io.mem_grant_1.get.bits := DontCare
+
+    val busGrant = bus.d.valid && (bus.d.bits.opcode === TLMessages.Grant ||
+      bus.d.bits.opcode === TLMessages.GrantData || bus.d.bits.opcode === TLMessages.CBOAck)
+    val busReleaseAck = bus.d.valid && bus.d.bits.opcode === TLMessages.ReleaseAck
+    val busCh1Grant = bus_ch1.get.d.valid && (bus_ch1.get.d.bits.opcode === TLMessages.Grant ||
+      bus_ch1.get.d.bits.opcode === TLMessages.GrantData || bus_ch1.get.d.bits.opcode === TLMessages.CBOAck)
+    val busCh1ReleaseAck = bus_ch1.get.d.valid && bus_ch1.get.d.bits.opcode === TLMessages.ReleaseAck
+
+    missQueue.io.mem_grant.valid := busGrant
+    missQueue.io.mem_grant.bits := bus.d.bits
+    missQueue.io.mem_grant_1.get.valid := busCh1Grant
+    missQueue.io.mem_grant_1.get.bits := bus_ch1.get.d.bits
+
+    wb.io.mem_grant.valid := busReleaseAck
+    wb.io.mem_grant.bits := bus.d.bits
+    wb.io.mem_grant_1.get.valid := busCh1ReleaseAck
+    wb.io.mem_grant_1.get.bits := bus_ch1.get.d.bits
+
+    bus.d.ready := Mux(busGrant, missQueue.io.mem_grant.ready, Mux(busReleaseAck, wb.io.mem_grant.ready, false.B))
+    bus_ch1.get.d.ready := Mux(busCh1Grant, missQueue.io.mem_grant_1.get.ready, Mux(busCh1ReleaseAck, wb.io.mem_grant_1.get.ready, false.B))
+
+    assert(!(bus.d.fire && !busGrant && !busReleaseAck))
+    assert(!(bus_ch1.get.d.fire && !busCh1Grant && !busCh1ReleaseAck))
+  } else {
+    // in L1DCache, we ony expect Grant[Data] and ReleaseAck
+    bus.d.ready := false.B
+    when (bus.d.bits.opcode === TLMessages.Grant || bus.d.bits.opcode === TLMessages.GrantData || bus.d.bits.opcode === TLMessages.CBOAck) {
+      missQueue.io.mem_grant <> bus.d
+    } .elsewhen (bus.d.bits.opcode === TLMessages.ReleaseAck) {
+      wb.io.mem_grant <> bus.d
+    } .otherwise {
+      assert (!bus.d.fire)
+    }
   }
 
   //----------------------------------------
@@ -1649,17 +1770,17 @@ class DCacheImp(outer: DCache) extends LazyModuleImp(outer) with HasDCacheParame
   }
   XSPerfAccumulate("grant_data_fire", grant_data_fire)
 
-  val hint_source = io.l2_hint.bits.sourceId
+  val hint_source = io.l2_hint(0).bits.sourceId
 
   val grant_data_source = bus.d.bits.source
 
   val hintPipe2 = Module(new Pipeline(UInt(32.W), 3))
-  hintPipe2.io.in.valid := io.l2_hint.valid
+  hintPipe2.io.in.valid := io.l2_hint(0).valid
   hintPipe2.io.in.bits := hint_source
   hintPipe2.io.out.ready := true.B
 
   val hintPipe1 = Module(new Pipeline(UInt(32.W), 2))
-  hintPipe1.io.in.valid := io.l2_hint.valid
+  hintPipe1.io.in.valid := io.l2_hint(0).valid
   hintPipe1.io.in.bits := hint_source
   hintPipe1.io.out.ready := true.B
 
@@ -1675,6 +1796,40 @@ class DCacheImp(outer: DCache) extends LazyModuleImp(outer) with HasDCacheParame
   XSPerfAccumulate("grant_without_hint", grant_without_hint)
   XSPerfAccumulate("hint_grant_unmatch", hint_grant_unmatch)
 
+  // dual-port has another bus
+  if(hasDualChannel) {
+    val grant_data_fire_1 = {
+      val (first, last, done, count) = edge.count(bus_ch1.get.d)
+      bus_ch1.get.d.fire && first && bus_ch1.get.d.bits.opcode === GrantData
+    }
+    XSPerfAccumulate("grant_data_fire_1", grant_data_fire_1)
+
+    val hint_source_1 = io.l2_hint(1).bits.sourceId
+
+    val grant_data_source_1 = bus_ch1.get.d.bits.source
+
+    val hintPipe2_1 = Module(new Pipeline(UInt(32.W), 3))
+    hintPipe2_1.io.in.valid := io.l2_hint(1).valid
+    hintPipe2_1.io.in.bits := hint_source_1
+    hintPipe2_1.io.out.ready := true.B
+
+    val hintPipe1_1 = Module(new Pipeline(UInt(32.W), 2))
+    hintPipe1_1.io.in.valid := io.l2_hint(1).valid
+    hintPipe1_1.io.in.bits := hint_source_1
+    hintPipe1_1.io.out.ready := true.B
+
+    val accurateHint_1 = grant_data_fire_1 && hintPipe2_1.io.out.valid && hintPipe2_1.io.out.bits === grant_data_source_1
+    XSPerfAccumulate("accurate3Hints_1", accurateHint_1)
+
+    val okHint_1 = grant_data_fire_1 && hintPipe1_1.io.out.valid && hintPipe1_1.io.out.bits === grant_data_source_1
+    XSPerfAccumulate("ok2Hints_1", okHint_1)
+    val hint_without_grant_1 = hintPipe2_1.io.out.valid && !grant_data_fire_1
+    val grant_without_hint_1 = !hintPipe2_1.io.out.valid && grant_data_fire_1
+    val hint_grant_unmatch_1 = hintPipe2_1.io.out.valid && grant_data_fire_1 && (hintPipe2_1.io.out.bits =/= grant_data_source_1)
+    XSPerfAccumulate("hint_without_grant_1", hint_without_grant_1)
+    XSPerfAccumulate("grant_without_hint_1", grant_without_hint_1)
+    XSPerfAccumulate("hint_grant_unmatch_1", hint_grant_unmatch_1)
+  }
 
   val perfEvents = (Seq(wb, mainPipe, missQueue, probeQueue) ++ ldu).flatMap(_.getPerfEvents)
   generatePerfEvent()
@@ -1698,9 +1853,15 @@ class DCacheWrapper()(implicit p: Parameters) extends LazyModule
 
   val useDcache = coreParams.dcacheParametersOpt.nonEmpty
   val clientNode = if (useDcache) TLIdentityNode() else null
+  // ========== Dual-channel support ==========
+  // Second TLIdentityNode for dual-channel L1-L2 interface
+  val clientNode_1 = if (useDcache && numMemChannels > 1) Some(TLIdentityNode()) else None
+
   val dcache = if (useDcache) LazyModule(new DCache()) else null
   if (useDcache) {
     clientNode := dcache.clientNode
+    // Connect second clientNode to DCache's second clientNode
+    clientNode_1.foreach(_ := dcache.clientNode_1.get)
   }
   val uncacheNode = OptionWrapper(cacheCtrlParamsOpt.isDefined, TLIdentityNode())
   require(
diff --git a/src/main/scala/xiangshan/cache/dcache/mainpipe/MissQueue.scala b/src/main/scala/xiangshan/cache/dcache/mainpipe/MissQueue.scala
index c545f1d1d82..1c5bcce1c5e 100644
--- a/src/main/scala/xiangshan/cache/dcache/mainpipe/MissQueue.scala
+++ b/src/main/scala/xiangshan/cache/dcache/mainpipe/MissQueue.scala
@@ -343,6 +343,7 @@ class CMOUnit(edge: TLEdgeOut)(implicit p: Parameters) extends DCacheModule {
     val resp_chanD = Flipped(DecoupledIO(new TLBundleD(edge.bundle)))
     val resp_to_lsq = DecoupledIO(new CMOResp)
     val wfi = Flipped(new WfiReqBundle)
+    val channel_sel = Output(UInt(memChannelBits.W))  // Channel selection for dual-channel support
   })
 
   val s_idle :: s_sreq :: s_wresp :: s_lsq_resp :: Nil = Enum(4)
@@ -387,6 +388,17 @@ class CMOUnit(edge: TLEdgeOut)(implicit p: Parameters) extends DCacheModule {
     }
   }
 
+  // Channel selection based on request address (same as MissEntry)
+  private def selectChannel(addr: UInt): UInt = {
+    if (hasDualChannel) {
+      if (channelSelByAddr) get_block(addr)(memChannelBits - 1, 0)
+      else 0.U(memChannelBits.W)
+    } else {
+      0.U(memChannelBits.W)
+    }
+  }
+  io.channel_sel := selectChannel(req.address)
+
   io.req.ready := state === s_idle
 
   io.req_chanA.valid := state === s_sreq && !io.wfi.wfiReq
@@ -438,7 +450,10 @@ class MissEntry(edge: TLEdgeOut, reqNum: Int)(implicit p: Parameters) extends DC
     val mem_grant = Flipped(DecoupledIO(new TLBundleD(edge.bundle)))
     val mem_finish = DecoupledIO(new TLBundleE(edge.bundle))
 
-    // client requests, queryME receive all miss_req now
+    // ========== Dual-channel support ==========
+    // Channel selection output
+    val channel_sel = Output(UInt(memChannelBits.W))
+
     val queryME = Vec(reqNum, Flipped(new DCacheMEQueryIOBundle))
 
     // output the signals to avoid redundant computation
@@ -530,6 +545,20 @@ class MissEntry(edge: TLEdgeOut, reqNum: Int)(implicit p: Parameters) extends DC
   // initial keyword
   val isKeyword = RegInit(false.B)
 
+  // Route each MSHR to a fixed L1-L2 channel so A/D/E traffic stays on one path.
+  private def selectChannel(addr: UInt, mshrId: UInt): UInt = {
+    if (hasDualChannel) {
+      if (channelSelByAddr) get_block(addr)(memChannelBits - 1, 0)
+      else mshrId(memChannelBits - 1, 0)
+    } else {
+      0.U(memChannelBits.W)
+    }
+  }
+  val channel_sel = selectChannel(req.addr, io.id)
+
+  // Output channel selection
+  io.channel_sel := channel_sel
+
   val miss_req_pipe_reg_bits = io.miss_req_pipe_reg.req
 
   val signals_vec = WireInit(VecInit(Seq.fill(reqNum)(0.U.asTypeOf(new MatchSignals))))
@@ -1150,7 +1179,13 @@ class MissQueue(edge: TLEdgeOut, reqNum: Int)(implicit p: Parameters) extends DC
     val mem_grant = Flipped(DecoupledIO(new TLBundleD(edge.bundle)))
     val mem_finish = DecoupledIO(new TLBundleE(edge.bundle))
 
-    val l2_hint = Input(Valid(new L2ToL1Hint())) // Hint from L2 Cache
+    // ========== Dual-channel support ==========
+    // Second set of TL channels for dual-channel L1-L2 interface
+    val mem_acquire_1 = if (hasDualChannel) Some(DecoupledIO(new TLBundleA(edge.bundle))) else None
+    val mem_grant_1 = if (hasDualChannel) Some(Flipped(DecoupledIO(new TLBundleD(edge.bundle)))) else None
+    val mem_finish_1 = if (hasDualChannel) Some(DecoupledIO(new TLBundleE(edge.bundle))) else None
+
+    val l2_hint = Input(Vec(cfg.numMemChannels, Valid(new L2ToL1Hint()))) // Hint from L2 Cache
 
     val main_pipe_req = DecoupledIO(new MainPipeReq)
     val main_pipe_resp = Flipped(ValidIO(new MainPipeResp))
@@ -1207,6 +1242,7 @@ class MissQueue(edge: TLEdgeOut, reqNum: Int)(implicit p: Parameters) extends DC
 
   // Parallel pipeline registers for queryMQ path (reqNum ports)
   val parallel_pipe_regs = RegInit(VecInit(Seq.fill(reqNum)(0.U.asTypeOf(new MissReqPipeRegBundle(edge)))))
+  val parallel_regs_channel = WireInit(VecInit(Seq.fill(reqNum)(0.U(memChannelBits.W))))
 
   val acquire_from_pipereg_vec = Wire(Vec(reqNum, chiselTypeOf(io.mem_acquire)))
 
@@ -1668,7 +1704,33 @@ class MissQueue(edge: TLEdgeOut, reqNum: Int)(implicit p: Parameters) extends DC
     assert(!RegNext(out.valid && PopCount(Cat(in.map(_.valid))) > 1.U))
   }
 
+  private def selectChannel(addr: UInt, mshrId: UInt): UInt = {
+    if (hasDualChannel) {
+      if (channelSelByAddr) get_block(addr)(memChannelBits - 1, 0)
+      else mshrId(memChannelBits - 1, 0)
+    } else {
+      0.U(memChannelBits.W)
+    }
+  }
+
+  private def splitDecoupledByChannel[T <: Data](source: DecoupledIO[T], channel: UInt): (DecoupledIO[T], DecoupledIO[T]) = {
+    val ch0 = Wire(Decoupled(chiselTypeOf(source.bits)))
+    val ch1 = Wire(Decoupled(chiselTypeOf(source.bits)))
+
+    ch0.valid := source.valid && channel === 0.U
+    ch0.bits := source.bits
+
+    ch1.valid := source.valid && channel === 1.U
+    ch1.bits := source.bits
+
+    source.ready := Mux(channel === 1.U, ch1.ready, ch0.ready)
+    (ch0, ch1)
+  }
+
   io.mem_grant.ready := false.B
+  if (hasDualChannel) {
+    io.mem_grant_1.get.ready := false.B
+  }
 
   val nMaxPrefetchEntry = Constantin.createRecord(s"nMaxPrefetchEntry${p(XSCoreParamsKey).HartId}", initValue = cfg.nMissEntries - 2)
   entries.zipWithIndex.foreach {
@@ -1693,8 +1755,17 @@ class MissQueue(edge: TLEdgeOut, reqNum: Int)(implicit p: Parameters) extends DC
 
       e.io.mem_grant.valid := false.B
       e.io.mem_grant.bits := DontCare
-      when (io.mem_grant.bits.source === i.U) {
-        e.io.mem_grant <> io.mem_grant
+      if (hasDualChannel) {
+        when ((io.mem_grant.bits.source === i.U) && io.mem_grant.valid) {
+          e.io.mem_grant <> io.mem_grant
+        }
+        when ((io.mem_grant_1.get.bits.source === i.U) && io.mem_grant_1.get.valid) {
+          e.io.mem_grant <> io.mem_grant_1.get
+        }
+      } else {
+        when (io.mem_grant.bits.source === i.U) {
+          e.io.mem_grant <> io.mem_grant
+        }
       }
 
       e.io.miss_req_pipe_reg       := DontCare
@@ -1731,8 +1802,12 @@ class MissQueue(edge: TLEdgeOut, reqNum: Int)(implicit p: Parameters) extends DC
 
       e.io.l2_hint.valid := false.B
       e.io.l2_hint.bits := DontCare
-      when(io.l2_hint.bits.sourceId === i.U) {
-        e.io.l2_hint <> io.l2_hint
+      val entryHintMatches = io.l2_hint.map(hint => hint.valid && hint.bits.sourceId === i.U)
+      assert(PopCount(VecInit(entryHintMatches)) <= 1.U, "multiple l2_hint hits for one miss entry")
+      for (ch <- 0 until cfg.numMemChannels) {
+        when(entryHintMatches(ch)) {
+          e.io.l2_hint <> io.l2_hint(ch)
+        }
       }
 
       e.io.wfi.wfiReq := io.wfi.wfiReq
@@ -1741,11 +1816,21 @@ class MissQueue(edge: TLEdgeOut, reqNum: Int)(implicit p: Parameters) extends DC
   cmo_unit.io.wfi.wfiReq := io.wfi.wfiReq
   cmo_unit.io.req <> io.cmo_req
   io.cmo_resp <> cmo_unit.io.resp_to_lsq
-  when (io.mem_grant.valid && io.mem_grant.bits.opcode === TLMessages.CBOAck) {
-    cmo_unit.io.resp_chanD <> io.mem_grant
-  } .otherwise {
+  if (hasDualChannel) {
     cmo_unit.io.resp_chanD.valid := false.B
     cmo_unit.io.resp_chanD.bits := DontCare
+    when (io.mem_grant.valid && io.mem_grant.bits.opcode === TLMessages.CBOAck) {
+      cmo_unit.io.resp_chanD <> io.mem_grant
+    }
+    when (io.mem_grant_1.get.valid && io.mem_grant_1.get.bits.opcode === TLMessages.CBOAck) {
+      cmo_unit.io.resp_chanD <> io.mem_grant_1.get
+    }
+  } else {
+    cmo_unit.io.resp_chanD.valid := false.B
+    cmo_unit.io.resp_chanD.bits := DontCare
+    when (io.mem_grant.valid && io.mem_grant.bits.opcode === TLMessages.CBOAck) {
+      cmo_unit.io.resp_chanD <> io.mem_grant
+    }
   }
   io.wfi.wfiSafe := (Seq(cmo_unit.io.wfi.wfiSafe) ++ entries.map(_.io.wfi.wfiSafe)).reduce(_&&_)
 
@@ -1767,9 +1852,32 @@ class MissQueue(edge: TLEdgeOut, reqNum: Int)(implicit p: Parameters) extends DC
     XSPerfAccumulate(s"parallel_pipe_regs_valid_$i", parallel_pipe_regs(i).reg_valid())
   }
 
-  val acquire_sources = Seq(cmo_unit.io.req_chanA) ++ acquire_from_pipereg_vec ++ entries.map(_.io.mem_acquire)
-  TLArbiter.lowest(edge, io.mem_acquire, acquire_sources:_*)
-  TLArbiter.lowest(edge, io.mem_finish, entries.map(_.io.mem_finish):_*)
+  for(i <- 0 until reqNum) {
+    parallel_regs_channel(i) := selectChannel(parallel_pipe_regs(i).req.addr, parallel_pipe_regs(i).mshr_id)
+  }
+
+  if (hasDualChannel) {
+    val splitPipeAcquires = acquire_from_pipereg_vec.zipWithIndex.map {
+      case (aq, i) => splitDecoupledByChannel(aq, parallel_regs_channel(i))
+    }
+    val splitEntryAcquires = entries.map(e => splitDecoupledByChannel(e.io.mem_acquire, e.io.channel_sel))
+    val splitEntryFinishes = entries.map(e => splitDecoupledByChannel(e.io.mem_finish, e.io.channel_sel))
+    val splitCmoAcquire = splitDecoupledByChannel(cmo_unit.io.req_chanA, cmo_unit.io.channel_sel)
+
+    val (pipeAcquireCh0, pipeAcquireCh1) = splitPipeAcquires.unzip
+    val (entryAcquireCh0, entryAcquireCh1) = splitEntryAcquires.unzip
+    val (finishCh0, finishCh1) = splitEntryFinishes.unzip
+    val (cmoAcquireCh0, cmoAcquireCh1) = (splitCmoAcquire._1, splitCmoAcquire._2)
+
+    TLArbiter.lowest(edge, io.mem_acquire, (Seq(cmoAcquireCh0) ++ pipeAcquireCh0 ++ entryAcquireCh0):_*)
+    TLArbiter.lowest(edge, io.mem_acquire_1.get, (Seq(cmoAcquireCh1) ++ pipeAcquireCh1 ++ entryAcquireCh1):_*)
+    TLArbiter.lowest(edge, io.mem_finish, finishCh0:_*)
+    TLArbiter.lowest(edge, io.mem_finish_1.get, finishCh1:_*)
+  } else {
+    val acquire_sources = Seq(cmo_unit.io.req_chanA) ++ acquire_from_pipereg_vec ++ entries.map(_.io.mem_acquire)
+    TLArbiter.lowest(edge, io.mem_acquire, acquire_sources:_*)
+    TLArbiter.lowest(edge, io.mem_finish, entries.map(_.io.mem_finish):_*)
+  }
 
   // amo's main pipe req out
   arbiter(entries.map(_.io.main_pipe_req), io.main_pipe_req, Some("main_pipe_req"))
@@ -1852,13 +1960,62 @@ class MissQueue(edge: TLEdgeOut, reqNum: Int)(implicit p: Parameters) extends DC
 
   // Difftest
   if (env.EnableDifftest) {
+    val refillDiffValidVec = VecInit(entries.map(e =>
+      e.io.refill_to_ldq.valid &&
+      e.io.refill_to_ldq.bits.hasdata &&
+      e.io.refill_to_ldq.bits.refill_done
+    ))
+    val refillDiffMask = refillDiffValidVec.asUInt
+    val refillDiffFirstOH = PriorityEncoderOH(refillDiffMask)
+    val refillDiffRemainMask = refillDiffMask & ~refillDiffFirstOH
+    val refillDiffSecondOH = PriorityEncoderOH(refillDiffRemainMask)
+    val refillDiffFirstValid = refillDiffMask.orR
+    val refillDiffSecondValid = refillDiffRemainMask.orR
+
+    class RefillDiffEntry extends Bundle {
+      val addr = UInt(PAddrBits.W)
+      val data = Vec(8, UInt((cfg.blockBytes).W))
+      val mask = UInt((cfg.blockBytes / 8).W)
+    }
+
+    val refillDiffFirstEntry = WireDefault(0.U.asTypeOf(new RefillDiffEntry))
+    val refillDiffSecondEntry = WireDefault(0.U.asTypeOf(new RefillDiffEntry))
+    when(refillDiffFirstValid) {
+      refillDiffFirstEntry.addr := Mux1H(refillDiffFirstOH, entries.map(_.io.refill_to_ldq.bits.addr))
+      refillDiffFirstEntry.data := Mux1H(
+        refillDiffFirstOH,
+        entries.map(_.io.refill_to_ldq.bits.data_raw.asTypeOf(refillDiffFirstEntry.data))
+      )
+      refillDiffFirstEntry.mask := VecInit.fill(refillDiffFirstEntry.mask.getWidth)(true.B).asUInt
+    }
+    when(refillDiffSecondValid) {
+      refillDiffSecondEntry.addr := Mux1H(refillDiffSecondOH, entries.map(_.io.refill_to_ldq.bits.addr))
+      refillDiffSecondEntry.data := Mux1H(
+        refillDiffSecondOH,
+        entries.map(_.io.refill_to_ldq.bits.data_raw.asTypeOf(refillDiffSecondEntry.data))
+      )
+      refillDiffSecondEntry.mask := VecInit.fill(refillDiffSecondEntry.mask.getWidth)(true.B).asUInt
+    }
+
+    val refillDiffQueue = Module(new Queue(new RefillDiffEntry, entries = cfg.nMissEntries, flow = false, pipe = false))
+    val queueHasPending = refillDiffQueue.io.deq.valid
+    val emitCurrent = refillDiffFirstValid && (!queueHasPending || refillDiffSecondValid)
+    val emitQueued = queueHasPending && !emitCurrent
+    val enqueueSecondCurrent = emitCurrent && refillDiffSecondValid
+    val enqueueFirstCurrent = emitQueued && refillDiffFirstValid
+
+    refillDiffQueue.io.deq.ready := emitQueued
+    refillDiffQueue.io.enq.valid := enqueueSecondCurrent || enqueueFirstCurrent
+    refillDiffQueue.io.enq.bits := Mux(enqueueSecondCurrent, refillDiffSecondEntry, refillDiffFirstEntry)
+    assert(!refillDiffQueue.io.enq.valid || refillDiffQueue.io.enq.ready, "refill difftest queue overflow")
+
     val difftest = DifftestModule(new DiffRefillEvent, dontCare = true)
     difftest.coreid := io.hartId
     difftest.index := 1.U
-    difftest.valid := io.refill_to_ldq.valid && io.refill_to_ldq.bits.hasdata && io.refill_to_ldq.bits.refill_done
-    difftest.addr := io.refill_to_ldq.bits.addr
-    difftest.data := io.refill_to_ldq.bits.data_raw.asTypeOf(difftest.data)
-    difftest.mask := VecInit.fill(difftest.mask.getWidth)(true.B).asUInt
+    difftest.valid := emitCurrent || emitQueued
+    difftest.addr := Mux(emitQueued, refillDiffQueue.io.deq.bits.addr, refillDiffFirstEntry.addr)
+    difftest.data := Mux(emitQueued, refillDiffQueue.io.deq.bits.data, refillDiffFirstEntry.data)
+    difftest.mask := Mux(emitQueued, refillDiffQueue.io.deq.bits.mask, refillDiffFirstEntry.mask)
   }
 
   if (env.EnableDifftest) {
@@ -1891,6 +2048,11 @@ class MissQueue(edge: TLEdgeOut, reqNum: Int)(implicit p: Parameters) extends DC
   XSPerfAccumulate("memSetPattenDetected", memSetPattenDetected)
   XSPerfAccumulate("no_free_entry", !ParallelORR(Cat(entries.map(e => e.io.primary_ready))))
   XSPerfAccumulate("free_entry_less_reqNum", PopCount(entries.map(e => e.io.primary_ready)) < reqNum.U)
+  if (hasDualChannel) {
+    XSPerfAccumulate("dual_channle_acquire", io.mem_acquire.valid && io.mem_acquire_1.get.valid)
+    XSPerfAccumulate("dual_channle_grant", io.mem_grant.valid && io.mem_grant_1.get.valid)
+    XSPerfAccumulate("dual_channle_grantAck", io.mem_finish.valid && io.mem_finish_1.get.valid)
+  }
   val max_inflight = RegInit(0.U((log2Up(cfg.nMissEntries) + 1).W))
   val num_valids = PopCount(~Cat(primary_ready_vec).asUInt)
   when (num_valids > max_inflight) {
diff --git a/src/main/scala/xiangshan/cache/dcache/mainpipe/WritebackQueue.scala b/src/main/scala/xiangshan/cache/dcache/mainpipe/WritebackQueue.scala
index d11985353fa..bfdc15d0e9d 100644
--- a/src/main/scala/xiangshan/cache/dcache/mainpipe/WritebackQueue.scala
+++ b/src/main/scala/xiangshan/cache/dcache/mainpipe/WritebackQueue.scala
@@ -315,6 +315,8 @@ class WritebackQueue(edge: TLEdgeOut)(implicit p: Parameters) extends DCacheModu
     val req_ready_dup = Vec(nDupWbReady, Output(Bool()))
     val mem_release = DecoupledIO(new TLBundleC(edge.bundle))
     val mem_grant = Flipped(DecoupledIO(new TLBundleD(edge.bundle)))
+    val mem_release_1 = if (hasDualChannel) Some(DecoupledIO(new TLBundleC(edge.bundle))) else None
+    val mem_grant_1 = if (hasDualChannel) Some(Flipped(DecoupledIO(new TLBundleD(edge.bundle)))) else None
 
     //val probe_ttob_check_req = Flipped(ValidIO(new ProbeToBCheckReq))
     //val probe_ttob_check_resp = ValidIO(new ProbeToBCheckResp)
@@ -339,6 +341,11 @@ class WritebackQueue(edge: TLEdgeOut)(implicit p: Parameters) extends DCacheModu
   io.mem_release.valid := false.B
   io.mem_release.bits  := DontCare
   io.mem_grant.ready   := false.B
+  if (hasDualChannel) {
+    io.mem_release_1.get.valid := false.B
+    io.mem_release_1.get.bits := DontCare
+    io.mem_grant_1.get.ready := false.B
+  }
 
   // delay data write in writeback req for 1 cycle
   val req_data = RegEnable(io.req.bits.toWritebackReqData(), io.req.valid)
@@ -366,18 +373,26 @@ class WritebackQueue(edge: TLEdgeOut)(implicit p: Parameters) extends DCacheModu
         !former_primary_ready &&
         entry.io.primary_ready
 
-      entry.io.mem_grant.valid := (entry_id === grant_source) && io.mem_grant.valid
-      entry.io.mem_grant.bits  := io.mem_grant.bits
-      //when (i.U === io.mem_grant.bits.source) {
-      //  io.mem_grant.ready := entry.io.mem_grant.ready
-      //}
+      entry.io.mem_grant.valid := false.B
+      entry.io.mem_grant.bits  := DontCare
+      if (hasDualChannel) {
+        when ((entry_id === io.mem_grant.bits.source) && io.mem_grant.valid) {
+          entry.io.mem_grant <> io.mem_grant
+        }
+        when ((entry_id === io.mem_grant_1.get.bits.source) && io.mem_grant_1.get.valid) {
+          entry.io.mem_grant <> io.mem_grant_1.get
+        }
+      } else {
+        when ((entry_id === io.mem_grant.bits.source) && io.mem_grant.valid) {
+          entry.io.mem_grant <> io.mem_grant
+        }
+      }
   }
 
   io.req_ready_dup.zipWithIndex.foreach { case (rdy, i) =>
     rdy := Cat(entries.map(_.io.primary_ready_dup(i))).orR && !block_conflict
   }
 
-  io.mem_grant.ready := true.B
   block_conflict := VecInit(entries.map(e => e.io.block_addr.valid && e.io.block_addr.bits === io.req.bits.addr)).asUInt.orR
   val miss_req_conflict = io.miss_req_conflict_check.map{ r =>
     VecInit(entries.map(e => e.io.block_addr.valid && e.io.block_addr.bits === r.bits)).asUInt.orR
@@ -386,7 +401,34 @@ class WritebackQueue(edge: TLEdgeOut)(implicit p: Parameters) extends DCacheModu
     blk := io.miss_req_conflict_check(i).valid && miss_req_conflict(i)
   }
 
-  TLArbiter.lowest(edge, io.mem_release, entries.map(_.io.mem_release):_*)
+  private def selectChannel(addr: UInt): UInt = {
+    if (hasDualChannel) {
+      if (channelSelByAddr) get_block(addr)(memChannelBits - 1, 0)
+      else 0.U(memChannelBits.W)
+    } else {
+      0.U(memChannelBits.W)
+    }
+  }
+
+  private def splitDecoupledByChannel[T <: Data](source: DecoupledIO[T], channel: UInt): (DecoupledIO[T], DecoupledIO[T]) = {
+    val ch0 = Wire(Decoupled(chiselTypeOf(source.bits)))
+    val ch1 = Wire(Decoupled(chiselTypeOf(source.bits)))
+    ch0.valid := source.valid && channel === 0.U
+    ch0.bits := source.bits
+    ch1.valid := source.valid && channel === 1.U
+    ch1.bits := source.bits
+    source.ready := Mux(channel === 1.U, ch1.ready, ch0.ready)
+    (ch0, ch1)
+  }
+
+  if (hasDualChannel) {
+    val splitEntryReleases = entries.map(e => splitDecoupledByChannel(e.io.mem_release, selectChannel(e.io.block_addr.bits)))
+    val (releaseCh0, releaseCh1) = splitEntryReleases.unzip
+    TLArbiter.robin(edge, io.mem_release, releaseCh0:_*)
+    TLArbiter.robin(edge, io.mem_release_1.get, releaseCh1:_*)
+  } else {
+    TLArbiter.robin(edge, io.mem_release, entries.map(_.io.mem_release):_*)
+  }
 
   // sanity check
   // print all input/output requests for debug purpose
@@ -394,6 +436,9 @@ class WritebackQueue(edge: TLEdgeOut)(implicit p: Parameters) extends DCacheModu
   io.req.bits.dump(io.req.fire)
 
   io.mem_grant.bits.dump(io.mem_grant.fire)
+  if (hasDualChannel) {
+    io.mem_grant_1.get.bits.dump(io.mem_grant_1.get.fire)
+  }
 
   // XSDebug(io.miss_req.valid, "miss_req: addr: %x\n", io.miss_req.bits)
   // XSDebug(io.block_miss_req, "block_miss_req\n")
@@ -403,6 +448,10 @@ class WritebackQueue(edge: TLEdgeOut)(implicit p: Parameters) extends DCacheModu
   for(i <- 0 until MissReqPortCount) {
     XSPerfAccumulate(s"block_miss_req_$i", io.block_miss_req(i))
   }
+  if (hasDualChannel) {
+    XSPerfAccumulate("dual_channle_release", io.mem_release.valid && io.mem_release_1.get.valid)
+    XSPerfAccumulate("dual_channle_releaseAck", io.mem_grant.valid && io.mem_grant_1.get.valid)
+  }
 
   val perfValidCount = RegNext(PopCount(entries.map(e => e.io.block_addr.valid)))
   val perfEvents = Seq(
@@ -415,4 +464,3 @@ class WritebackQueue(edge: TLEdgeOut)(implicit p: Parameters) extends DCacheModu
   generatePerfEvent()
 
 }
-
diff --git a/src/main/scala/xiangshan/mem/MemBlock.scala b/src/main/scala/xiangshan/mem/MemBlock.scala
index 9602fed94ac..b5b0cacd534 100644
--- a/src/main/scala/xiangshan/mem/MemBlock.scala
+++ b/src/main/scala/xiangshan/mem/MemBlock.scala
@@ -322,6 +322,16 @@ class MemBlockInlined()(implicit p: Parameters) extends LazyModule
   val ptw_to_l2_buffer = if (!coreParams.softPTW) LazyModule(new TLBuffer) else null
   val l1d_to_l2_buffer = if (coreParams.dcacheParametersOpt.nonEmpty) LazyModule(new TLBuffer) else null
   val dcache_port = TLNameNode("dcache_client") // to keep dcache-L2 port name
+
+  // ========== Dual-channel support ==========
+  // Second buffer and port for dual-channel L1-L2 interface
+  val l1d_to_l2_buffer_1 = if (numMemChannelsFromDcache > 1)
+    Some(LazyModule(new TLBuffer))
+  else None
+  val dcache_port_1 = if (numMemChannelsFromDcache > 1)
+    Some(TLNameNode("dcache_client_ch1"))
+  else None
+
   // NOTE: we currently only use one output port to L2 and L3 prefetch sender respectively
   val l2_pf_sender_opt = if (coreParams.prefetcher.nonEmpty)
     Some(BundleBridgeSource(() => new PrefetchRecv)) else None
@@ -375,7 +385,7 @@ class MemBlockInlinedImp(outer: MemBlockInlined) extends LazyModuleImp(outer)
       val dcacheMSHRFull = Output(Bool())
     }
     val debug_ls = new DebugLSIO
-    val l2_hint = Input(Valid(new L2ToL1Hint()))
+    val l2_hint = Input(Vec(numMemChannelsFromDcache, Valid(new L2ToL1Hint())))
     val l2PfqBusy = Input(Bool())
     val l2_tlb_req = Flipped(new TlbRequestIO(nRespDups = 2))
     val l2_pmp_resp = new PMPRespBundle
@@ -931,9 +941,7 @@ class MemBlockInlinedImp(outer: MemBlockInlined) extends LazyModuleImp(outer)
 
     // passdown to lsq (load s3)
     lsq.io.ldu.ldin(i) <> newLoadUnits(i).io.lqWrite
-    lsq.io.l2_hint.valid := l2_hint.valid
-    lsq.io.l2_hint.bits.sourceId := l2_hint.bits.sourceId
-    lsq.io.l2_hint.bits.isKeyword := l2_hint.bits.isKeyword
+    lsq.io.l2_hint <> l2_hint
 
     lsq.io.tlb_hint <> dtlbRepeater.io.hint.get
 
diff --git a/src/main/scala/xiangshan/mem/lsqueue/LSQWrapper.scala b/src/main/scala/xiangshan/mem/lsqueue/LSQWrapper.scala
index 4862b556313..1d235b72925 100644
--- a/src/main/scala/xiangshan/mem/lsqueue/LSQWrapper.scala
+++ b/src/main/scala/xiangshan/mem/lsqueue/LSQWrapper.scala
@@ -98,7 +98,10 @@ class LsqWrapper(implicit p: Parameters) extends XSModule
     // mdp train io
     val mdpTrain        = ValidIO(new Redirect)
     val release = Flipped(Valid(new Release))
-    val loadWakeup = Flipped(ValidIO(new DCacheLoadWakeup()))
+    val loadWakeup = Flipped(Vec(cfg.numMemChannels, ValidIO(new DCacheLoadWakeup())))
+   // val refill = Flipped(Valid(new Refill))
+    // val tl_d_channel  = Input(Vec(cfg.numMemChannels, new DcacheToLduForwardIO))
+    // val maControl     = Flipped(new StoreMaBufToSqControlIO)
     val uncacheOutstanding = Input(Bool())
     val uncache = new UncacheWordIO
     val mmioStout = DecoupledIO(new MemToRob(staParams.head)) // writeback uncached store
@@ -115,7 +118,7 @@ class LsqWrapper(implicit p: Parameters) extends XSModule
     val lqDeqPtr = Output(new LqPtr)
     val sqDeqPtr = Output(new SqPtr)
     val issuePtrExt = Output(new SqPtr)
-    val l2_hint = Input(Valid(new L2ToL1Hint()))
+    val l2_hint = Input(Vec(cfg.numMemChannels, Valid(new L2ToL1Hint())))
     val tlb_hint = Flipped(new TlbHintIO)
     val cmoOpReq  = DecoupledIO(new CMOReq)
     val cmoOpResp = Flipped(DecoupledIO(new CMOResp))
diff --git a/src/main/scala/xiangshan/mem/lsqueue/LoadQueue.scala b/src/main/scala/xiangshan/mem/lsqueue/LoadQueue.scala
index 1ccd732657e..12a854a5be2 100644
--- a/src/main/scala/xiangshan/mem/lsqueue/LoadQueue.scala
+++ b/src/main/scala/xiangshan/mem/lsqueue/LoadQueue.scala
@@ -185,7 +185,7 @@ class LoadQueue(implicit p: Parameters) extends XSModule
     }
     val bypass = Flipped(Vec(LoadPipelineWidth, new UncacheBypass))
     val replay = Vec(LoadPipelineWidth, Decoupled(new LoadReplayIO))
-    val loadWakeup  = Flipped(ValidIO(new DCacheLoadWakeup()))
+    val loadWakeup  = Flipped(Vec(cfg.numMemChannels, ValidIO(new DCacheLoadWakeup())))
     val release = Flipped(Valid(new Release))
     val nuke_rollback = Vec(StorePipelineWidth, Output(Valid(new Redirect)))
     val nack_rollback = Vec(1, Output(Valid(new Redirect))) // uncachebuffer
@@ -197,7 +197,7 @@ class LoadQueue(implicit p: Parameters) extends XSModule
     val lqCancelCnt = Output(UInt(log2Up(VirtualLoadQueueSize+1).W))
     val lq_rep_full = Output(Bool())
     val tlbReplayDelayCycleCtrl = Vec(4, Input(UInt(ReSelectLen.W)))
-    val l2_hint = Input(Valid(new L2ToL1Hint()))
+    val l2_hint = Input(Vec(cfg.numMemChannels, Valid(new L2ToL1Hint())))
     val tlb_hint = Flipped(new TlbHintIO)
     val lqEmpty = Output(Bool())
 
diff --git a/src/main/scala/xiangshan/mem/lsqueue/LoadQueueReplay.scala b/src/main/scala/xiangshan/mem/lsqueue/LoadQueueReplay.scala
index 4d7c9e3ff92..3164d8b7ca7 100644
--- a/src/main/scala/xiangshan/mem/lsqueue/LoadQueueReplay.scala
+++ b/src/main/scala/xiangshan/mem/lsqueue/LoadQueueReplay.scala
@@ -190,7 +190,7 @@ class LoadQueueReplay(implicit p: Parameters) extends XSModule
     // queue-based replay
     val replay = Vec(LoadPipelineWidth, Decoupled(new LoadReplayIO))
 
-    val loadWakeup = Flipped(ValidIO(new DCacheLoadWakeup()))
+    val loadWakeup = Flipped(Vec(cfg.numMemChannels, ValidIO(new DCacheLoadWakeup())))
 
     // from StoreQueue
     val stAddrReadySqPtr = Input(new SqPtr)
@@ -208,7 +208,7 @@ class LoadQueueReplay(implicit p: Parameters) extends XSModule
     val ldWbPtr = Input(new LqPtr)
     val rarFull = Input(Bool())
     val rawFull = Input(Bool())
-    val l2_hint  = Input(Valid(new L2ToL1Hint()))
+    val l2_hint  = Input(Vec(cfg.numMemChannels, Valid(new L2ToL1Hint())))
     val tlb_hint = Flipped(new TlbHintIO)
     val tlbReplayDelayCycleCtrl = Vec(4, Input(UInt(ReSelectLen.W)))
 
@@ -346,6 +346,37 @@ class LoadQueueReplay(implicit p: Parameters) extends XSModule
   val lqIdxMatchNc = VecInit((0 until LoadQueueReplaySize).map { i =>
     io.ncWakeup.valid && io.ncWakeup.bits === uop(i).lqIdx
   })
+  private def tlDChannelHit(mshrId: UInt): Bool = {
+    VecInit(io.loadWakeup.map(ch => ch.valid && ch.bits.mshrId === mshrId)).asUInt.orR
+  }
+
+  private val tlDChannelLastCycleValid = RegNext(
+    VecInit(io.loadWakeup.map(_.valid)),
+    0.U.asTypeOf(Vec(cfg.numMemChannels, Bool()))
+  )
+  private val tlDChannelLastCycleMshrId = RegNext(
+    VecInit(io.loadWakeup.map(_.bits.mshrId)),
+    0.U.asTypeOf(Vec(cfg.numMemChannels, UInt(log2Up(cfg.nMissEntries).W)))
+  )
+
+  private def tlDChannelHitLastCycle(mshrId: UInt): Bool = {
+    VecInit((0 until cfg.numMemChannels).map(i => tlDChannelLastCycleValid(i) && tlDChannelLastCycleMshrId(i) === mshrId)).asUInt.orR
+  }
+
+  private def l2HintMatchVec(mshrId: UInt): Seq[Bool] = {
+    io.l2_hint.map(hint => hint.valid && hint.bits.sourceId === mshrId)
+  }
+
+  private def l2HintHit(mshrId: UInt): Bool = {
+    VecInit(l2HintMatchVec(mshrId)).asUInt.orR
+  }
+
+  private def l2HintIsKeyword(mshrId: UInt): Bool = {
+    val matchVec = l2HintMatchVec(mshrId)
+    assert(PopCount(VecInit(matchVec)) <= 1.U, "multiple l2_hint hits for one replay entry")
+    Mux(VecInit(matchVec).asUInt.orR, Mux1H(matchVec.zip(io.l2_hint.map(_.bits.isKeyword))), false.B)
+  }
+
   // update blocking condition
   (0 until LoadQueueReplaySize).map(i => {
     // case C_MA
@@ -364,7 +395,7 @@ class LoadQueueReplay(implicit p: Parameters) extends XSModule
     }
     // case C_DM
     when (cause(i)(LoadReplayCauses.C_DM)) {
-      blocking(i) := Mux(io.loadWakeup.valid && io.loadWakeup.bits.mshrId === missMSHRId(i), false.B, blocking(i))
+      blocking(i) := Mux(tlDChannelHit(missMSHRId(i)), false.B, blocking(i))
     }
     // case C_RAR
     when (cause(i)(LoadReplayCauses.C_RAR)) {
@@ -421,15 +452,17 @@ class LoadQueueReplay(implicit p: Parameters) extends XSModule
   // l2 hint wakes up cache missed load
   // l2 will send GrantData in next 2/3 cycle, wake up the missed load early and sent them to load pipe, so them will hit the data in D channel or mshr in load S1
   val s0_loadHintWakeMask = VecInit((0 until LoadQueueReplaySize).map(i => {
-    allocated(i) && !scheduled(i) && cause(i)(LoadReplayCauses.C_DM) && blocking(i) && missMSHRId(i) === io.l2_hint.bits.sourceId && io.l2_hint.valid
+    allocated(i) && !scheduled(i) && cause(i)(LoadReplayCauses.C_DM) && blocking(i) && l2HintHit(missMSHRId(i))
   })).asUInt
   // l2 will send 2 beats data in 2 cycles, so if data needed by this load is in first beat, select it this cycle, otherwise next cycle
   // when isKeyword = 1, s0_loadHintSelMask need overturn
-    val s0_loadHintSelMask = Mux(
-     io.l2_hint.bits.isKeyword,
-     s0_loadHintWakeMask & dataInLastBeatReg.asUInt,
-     s0_loadHintWakeMask & VecInit(dataInLastBeatReg.map(!_)).asUInt
-     )
+    val s0_loadHintSelMask = VecInit((0 until LoadQueueReplaySize).map(i => {
+      s0_loadHintWakeMask(i) && Mux(
+        l2HintIsKeyword(missMSHRId(i)),
+        dataInLastBeatReg(i),
+        !dataInLastBeatReg(i)
+      )
+    })).asUInt
   val s0_remLoadHintSelMask = VecInit((0 until LoadPipelineWidth).map(rem => getRemBits(s0_loadHintSelMask)(rem)))
   val s0_remHintSelValidVec = VecInit((0 until LoadPipelineWidth).map(rem => ParallelORR(s0_remLoadHintSelMask(rem))))
   val s0_hintSelValid = ParallelORR(s0_loadHintSelMask)
@@ -492,6 +525,9 @@ class LoadQueueReplay(implicit p: Parameters) extends XSModule
   val s0_remOldestSelOH = (0 until LoadPipelineWidth).map(rem =>
     Mux(s0_remOldestMatch(rem), PriorityEncoderOH(s0_remOldestMatchMask(rem)), PriorityEncoderOH(s0_remOlderMatchMask(rem)))
   )
+  val s0_remOldestHintSelVec = s0_remOldestSelVec.zip(s0_remLoadHintSelMask).map {
+    case(oldestVec, hintVec) => oldestVec & hintVec
+  }
 
   // select oldest logic
   s0_oldestSel := VecInit((0 until LoadPipelineWidth).map(rport => {
@@ -502,9 +538,9 @@ class LoadQueueReplay(implicit p: Parameters) extends XSModule
     val ageOldestIndexOH = ageOldest.bits
 
     // select program order oldest
-    val l2HintFirst = io.l2_hint.valid && s0_remOldestHintSel(rport)
-    val issOldestValid = l2HintFirst || s0_remOldestMatch(rport) || s0_remOlderMatch(rport)
-    val issOldestIndexOH = Mux(l2HintFirst, s0_remOldestHintSelOH(rport), s0_remOldestSelOH(rport))
+    val l2HintFirst = ParallelORR(s0_remOldestHintSelVec(rport))
+    val issOldestValid = l2HintFirst || ParallelORR(s0_remOldestSelVec(rport))
+    val issOldestIndexOH = Mux(l2HintFirst, PriorityEncoderOH(s0_remOldestHintSelVec(rport)), PriorityEncoderOH(s0_remOldestSelVec(rport)))
 
     val oldest = Wire(Valid(UInt()))
     val oldestSel = Mux(issOldestValid, issOldestIndexOH, ageOldestIndexOH)
@@ -733,9 +769,12 @@ class LoadQueueReplay(implicit p: Parameters) extends XSModule
 
       // special case: dcache miss
       when (replayInfo.cause(LoadReplayCauses.C_DM) && enq.bits.handledByMSHR) {
+        val tlDHitThisCycle = tlDChannelHit(replayInfo.mshr_id)
+        val tlDHitLastCycle = tlDChannelHitLastCycle(replayInfo.mshr_id)
         blocking(enqIndex) := !replayInfo.full_fwd && //  dcache miss
-                              !(io.loadWakeup.valid && io.loadWakeup.bits.mshrId === replayInfo.mshr_id) && // no refill in this cycle
-                              !(RegNext(io.loadWakeup.valid) && RegNext(io.loadWakeup.bits.mshrId) === replayInfo.mshr_id) // no refill in last cycle
+                              !tlDHitThisCycle && // no refill in this cycle
+                              !tlDHitLastCycle // not refill in last cycle
+
       }
 
       // extra info
@@ -866,7 +905,7 @@ class LoadQueueReplay(implicit p: Parameters) extends XSModule
   XSPerfAccumulate("replay_forward_fail", replayForwardFailCount)
   XSPerfAccumulate("replay_dcache_miss", replayDCacheMissCount)
   XSPerfAccumulate("replay_hint_wakeup", s0_hintSelValid)
-  XSPerfAccumulate("replay_hint_priority_beat1", io.l2_hint.valid && io.l2_hint.bits.isKeyword)
+  XSPerfAccumulate("replay_hint_priority_beat1", PopCount(VecInit(io.l2_hint.map(hint => hint.valid && hint.bits.isKeyword))))
   XSPerfAccumulate("replay_storeQueue_multi_match", replayMultiMatchCount)
 
   // replay counter
diff --git a/src/main/scala/xiangshan/mem/pipeline/NewLoadUnit.scala b/src/main/scala/xiangshan/mem/pipeline/NewLoadUnit.scala
index a60db461e0f..4be9346ec3e 100644
--- a/src/main/scala/xiangshan/mem/pipeline/NewLoadUnit.scala
+++ b/src/main/scala/xiangshan/mem/pipeline/NewLoadUnit.scala
@@ -1799,6 +1799,7 @@ class LoadUnitDataPath(val param: ExeUnitParams)(implicit p: Parameters) extends
 }
 
 class LoadUnitIO(val param: ExeUnitParams)(implicit p: Parameters) extends XSBundle {
+  private val numMemChannels = p(XSCoreParamsKey).dcacheParametersOpt.get.numMemChannels
   val redirect = Flipped(ValidIO(new Redirect))
   // Request sources
   val ldin = Flipped(DecoupledIO(new ExuInput(param, hasCopySrc = true)))
@@ -1825,7 +1826,7 @@ class LoadUnitIO(val param: ExeUnitParams)(implicit p: Parameters) extends XSBun
   val sbufferForward = new SbufferForward
   val uncacheForward = new UncacheForward
   val mshrForward = new DCacheForward
-  val tldForward = new DCacheForward
+  val tldForward = Vec(numMemChannels, new DCacheForward)
   val uncacheBypass = new UncacheBypass
   // Nuke check with StoreUnit
   val staNukeQueryReq = Flipped(Vec(StorePipelineWidth, ValidIO(new StoreNukeQueryReq)))
@@ -1871,6 +1872,14 @@ class NewLoadUnit(val param: ExeUnitParams)(implicit p: Parameters) extends XSMo
   s2.io.kill := false.B
   s3.io.kill := false.B
   dataPath.io.s1Meta := s1.io.dataPathMeta
+  val tldForwardRespValids = io.tldForward.map(_.s2Resp.valid)
+  val tldForwardRespMerged = Wire(ValidIO(new DCacheForwardResp))
+  tldForwardRespMerged.valid := tldForwardRespValids.reduce(_ || _)
+  tldForwardRespMerged.bits := Mux1H(
+    tldForwardRespValids :+ !tldForwardRespMerged.valid,
+    io.tldForward.map(_.s2Resp.bits) :+ 0.U.asTypeOf(new DCacheForwardResp)
+  )
+  assert(PopCount(tldForwardRespValids) <= 1.U, "multiple tldForward responses for one load unit")
 
   // IO wiring
   // S0
@@ -1887,7 +1896,7 @@ class NewLoadUnit(val param: ExeUnitParams)(implicit p: Parameters) extends XSMo
   io.sbufferForward.s0Req := s0.io.sqSbForwardReq
   io.uncacheForward.s0Req := s0.io.uncacheForwardReq
   io.mshrForward.s0Req := s0.io.mshrForwardReq
-  io.tldForward.s0Req := s0.io.tldForwardReq
+  io.tldForward.foreach(_.s0Req := s0.io.tldForwardReq)
   io.uncacheBypass.s0Req := s0.io.uncacheBypassReq
   io.wakeup := s0.io.wakeup
 
@@ -1908,8 +1917,10 @@ class NewLoadUnit(val param: ExeUnitParams)(implicit p: Parameters) extends XSMo
   io.uncacheForward.s1Kill := s1.io.uncacheForwardKill
   io.mshrForward.s1Req := s1.io.mshrForwardReq
   io.mshrForward.s1Kill := s1.io.mshrForwardKill
-  io.tldForward.s1Req := s1.io.tldForwardReq
-  io.tldForward.s1Kill := s1.io.tldForwardKill
+  io.tldForward.foreach { forward =>
+    forward.s1Req := s1.io.tldForwardReq
+    forward.s1Kill := s1.io.tldForwardKill
+  }
   s1.io.uncacheBypassResp := io.uncacheBypass.s1Resp
   s1.io.staNukeQueryReq := io.staNukeQueryReq
   io.prefetchTrainHintS1 := s1.io.prefetchTrainHint
@@ -1928,7 +1939,7 @@ class NewLoadUnit(val param: ExeUnitParams)(implicit p: Parameters) extends XSMo
   s2.io.sbufferForwardResp := io.sbufferForward.s2Resp
   s2.io.uncacheForwardResp := io.uncacheForward.s2Resp
   s2.io.mshrForwardResp := io.mshrForward.s2Resp
-  s2.io.tldForwardResp := io.tldForward.s2Resp
+  s2.io.tldForwardResp := tldForwardRespMerged
   s2.io.uncacheBypassResp := io.uncacheBypass.s2Resp
   s2.io.staNukeQueryReq := io.staNukeQueryReq
   io.rarNukeQuery.req <> s2.io.rarNukeQueryReq
@@ -1959,7 +1970,7 @@ class NewLoadUnit(val param: ExeUnitParams)(implicit p: Parameters) extends XSMo
   dataPath.io.s2SbufferForwardResp := io.sbufferForward.s2Resp
   dataPath.io.s2UncacheForwardResp := io.uncacheForward.s2Resp
   dataPath.io.s2MSHRForwardResp := io.mshrForward.s2Resp
-  dataPath.io.s2TLDForwardResp := io.tldForward.s2Resp
+  dataPath.io.s2TLDForwardResp := tldForwardRespMerged
   dataPath.io.s2UncacheBypassResp := io.uncacheBypass.s2Resp
   dataPath.io.s2DCacheResp.valid := io.dcache.resp.valid
   dataPath.io.s2DCacheResp.bits := io.dcache.resp.bits
```
