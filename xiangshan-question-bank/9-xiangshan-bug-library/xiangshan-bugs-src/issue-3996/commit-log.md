# Commit Log
- Issue: #3996
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/3996
- Issue state: closed
- Tested RTL commit: -
- Related PR: #3996
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/3996
- Changed files: 3
- Additions: 7
- Deletions: 2

## Files
- `src/main/scala/xiangshan/frontend/Frontend.scala`
- `src/main/scala/xiangshan/frontend/icache/ICache.scala`
- `src/main/scala/xiangshan/frontend/icache/IPrefetch.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/frontend/Frontend.scala b/src/main/scala/xiangshan/frontend/Frontend.scala
index 5957649b5e8..e28b44c50ae 100644
--- a/src/main/scala/xiangshan/frontend/Frontend.scala
+++ b/src/main/scala/xiangshan/frontend/Frontend.scala
@@ -146,7 +146,7 @@ class FrontendInlinedImp(outer: FrontendInlined) extends LazyModuleImp(outer)
   itlb.io.requestor.last <> ifu.io.iTLBInter // mmio may need re-tlb, blocked
   itlb.io.hartId := io.hartId
   itlb.io.base_connect(sfence, tlbCsr)
-  itlb.io.flushPipe.map(_ := needFlush)
+  itlb.io.flushPipe.foreach(_ := icache.io.itlbFlushPipe)
   itlb.io.redirect := DontCare // itlb has flushpipe, don't need redirect signal
 
   val itlb_ptw = Wire(new VectorTlbPtwIO(coreParams.itlbPortNum))
diff --git a/src/main/scala/xiangshan/frontend/icache/ICache.scala b/src/main/scala/xiangshan/frontend/icache/ICache.scala
index 7520361f3e7..781b8698e34 100644
--- a/src/main/scala/xiangshan/frontend/icache/ICache.scala
+++ b/src/main/scala/xiangshan/frontend/icache/ICache.scala
@@ -524,7 +524,8 @@ class ICacheIO(implicit p: Parameters) extends ICacheBundle {
   // PMP: mainPipe & prefetchPipe need PortNumber each
   val pmp: Vec[ICachePMPBundle] = Vec(2 * PortNumber, new ICachePMPBundle)
   // iTLB
-  val itlb: Vec[TlbRequestIO] = Vec(PortNumber, new TlbRequestIO)
+  val itlb:          Vec[TlbRequestIO] = Vec(PortNumber, new TlbRequestIO)
+  val itlbFlushPipe: Bool              = Bool()
   // backend/BEU
   val error: Valid[L1CacheErrorInfo] = ValidIO(new L1CacheErrorInfo)
   // backend/CSR
@@ -655,6 +656,7 @@ class ICacheImp(outer: ICache) extends LazyModuleImp(outer) with HasICacheParame
 
   io.itlb(0) <> prefetcher.io.itlb(0)
   io.itlb(1) <> prefetcher.io.itlb(1)
+  io.itlbFlushPipe := prefetcher.io.itlbFlushPipe
 
   // notify IFU that Icache pipeline is available
   io.toIFU    := mainPipe.io.fetch.req.ready
diff --git a/src/main/scala/xiangshan/frontend/icache/IPrefetch.scala b/src/main/scala/xiangshan/frontend/icache/IPrefetch.scala
index e88bf06dbfe..42bc036248f 100644
--- a/src/main/scala/xiangshan/frontend/icache/IPrefetch.scala
+++ b/src/main/scala/xiangshan/frontend/icache/IPrefetch.scala
@@ -61,6 +61,7 @@ class IPrefetchIO(implicit p: Parameters) extends IPrefetchBundle {
   val req:            DecoupledIO[IPrefetchReq]  = Flipped(Decoupled(new IPrefetchReq))
   val flushFromBpu:   BpuFlushInfo               = Flipped(new BpuFlushInfo)
   val itlb:           Vec[TlbRequestIO]          = Vec(PortNumber, new TlbRequestIO)
+  val itlbFlushPipe:  Bool                       = Bool()
   val pmp:            Vec[ICachePMPBundle]       = Vec(PortNumber, new ICachePMPBundle)
   val metaRead:       ICacheMetaReqBundle        = new ICacheMetaReqBundle
   val MSHRReq:        DecoupledIO[ICacheMissReq] = DecoupledIO(new ICacheMissReq)
@@ -460,6 +461,8 @@ class IPrefetchPipe(implicit p: Parameters) extends IPrefetchModule {
   /** Stage 1 control */
   from_bpu_s1_flush := s1_valid && !s1_isSoftPrefetch && io.flushFromBpu.shouldFlushByStage3(s1_req_ftqIdx)
   s1_flush          := io.flush || from_bpu_s1_flush
+  // when s1 is flushed, itlb pipeline should also be flushed
+  io.itlbFlushPipe := s1_flush
 
   s1_ready := next_state === m_idle
   s1_fire  := (next_state === m_idle) && s1_valid && !s1_flush // used to clear s1_valid & itlb_valid_latch
```
