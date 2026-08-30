# Commit Log
- Issue: #5055
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/5055
- Issue state: closed
- Tested RTL commit: -
- Related PR: #5055
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/5055
- Changed files: 5
- Additions: 59
- Deletions: 29

## Files
- `src/main/scala/xiangshan/frontend/icache/Bundles.scala`
- `src/main/scala/xiangshan/frontend/icache/ICacheImp.scala`
- `src/main/scala/xiangshan/frontend/icache/ICacheMainPipe.scala`
- `src/main/scala/xiangshan/frontend/icache/ICachePrefetchPipe.scala`
- `src/main/scala/xiangshan/frontend/icache/ICacheWayLookup.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/frontend/icache/Bundles.scala b/src/main/scala/xiangshan/frontend/icache/Bundles.scala
index 99c8a5ea8c9..05a3a35c3bb 100644
--- a/src/main/scala/xiangshan/frontend/icache/Bundles.scala
+++ b/src/main/scala/xiangshan/frontend/icache/Bundles.scala
@@ -248,6 +248,10 @@ class WayLookupBundle(implicit p: Parameters) extends ICacheBundle {
   def isForVSnonLeafPTE: Bool          = gpf.isForVSnonLeafPTE
 }
 
+class WayLookupWriteBundle(implicit p: Parameters) extends WayLookupBundle {
+  val ftqIdx: FtqPtr = new FtqPtr
+}
+
 /* ***** Miss ***** */
 // ICacheMainPipe / ICachePrefetchPipe -> MissUnit
 class MissReqBundle(implicit p: Parameters) extends ICacheBundle {
diff --git a/src/main/scala/xiangshan/frontend/icache/ICacheImp.scala b/src/main/scala/xiangshan/frontend/icache/ICacheImp.scala
index a116277f7f5..d35c63267d9 100644
--- a/src/main/scala/xiangshan/frontend/icache/ICacheImp.scala
+++ b/src/main/scala/xiangshan/frontend/icache/ICacheImp.scala
@@ -173,15 +173,17 @@ class ICacheImp(outer: ICache) extends LazyModuleImp(outer) with HasICacheParame
   missUnit.io.memGrant.bits  := DontCare
   missUnit.io.memGrant <> bus.d
 
-  mainPipe.io.flush     := io.fromFtq.redirectFlush
-  mainPipe.io.respStall := io.fromIfu.stall
-  mainPipe.io.eccEnable := eccEnable
-  mainPipe.io.hartId    := io.hartId
-  mainPipe.io.missResp  := missUnit.io.resp
+  mainPipe.io.flush        := io.fromFtq.redirectFlush
+  mainPipe.io.flushFromBpu := io.fromFtq.flushFromBpu
+  mainPipe.io.respStall    := io.fromIfu.stall
+  mainPipe.io.eccEnable    := eccEnable
+  mainPipe.io.hartId       := io.hartId
+  mainPipe.io.missResp     := missUnit.io.resp
   mainPipe.io.req <> io.fromFtq.fetchReq
   mainPipe.io.wayLookupRead <> wayLookup.io.read
 
-  wayLookup.io.flush := io.fromFtq.redirectFlush
+  wayLookup.io.flush        := io.fromFtq.redirectFlush
+  wayLookup.io.flushFromBpu := io.fromFtq.flushFromBpu
   wayLookup.io.write <> prefetcher.io.wayLookupWrite
   wayLookup.io.update := missUnit.io.resp
 
diff --git a/src/main/scala/xiangshan/frontend/icache/ICacheMainPipe.scala b/src/main/scala/xiangshan/frontend/icache/ICacheMainPipe.scala
index f419e16cf37..55bd72a6824 100644
--- a/src/main/scala/xiangshan/frontend/icache/ICacheMainPipe.scala
+++ b/src/main/scala/xiangshan/frontend/icache/ICacheMainPipe.scala
@@ -30,6 +30,7 @@ import xiangshan.cache.mmu.TlbCmd
 import xiangshan.cache.mmu.ValidHoldBypass
 import xiangshan.frontend.ExceptionType
 import xiangshan.frontend.FtqFetchRequest
+import xiangshan.frontend.ftq.BpuFlushInfo
 
 class ICacheMainPipe(implicit p: Parameters) extends ICacheModule
     with ICacheEccHelper
@@ -51,8 +52,9 @@ class ICacheMainPipe(implicit p: Parameters) extends ICacheModule
 
     /* *** outside interface *** */
     // Ftq
-    val req:   DecoupledIO[FtqFetchRequest] = Flipped(DecoupledIO(new FtqFetchRequest))
-    val flush: Bool                         = Input(Bool())
+    val req:          DecoupledIO[FtqFetchRequest] = Flipped(DecoupledIO(new FtqFetchRequest))
+    val flush:        Bool                         = Input(Bool())
+    val flushFromBpu: BpuFlushInfo                 = Input(new BpuFlushInfo)
     // Pmp
     val pmp: PmpCheckBundle = new PmpCheckBundle
     // Ifu
@@ -104,6 +106,7 @@ class ICacheMainPipe(implicit p: Parameters) extends ICacheModule
   private val fromFtqReq = fromFtq.bits
   private val s0_valid   = fromFtq.valid
 
+  private val s0_ftqIdx    = fromFtqReq.ftqIdx
   private val s0_vAddr     = VecInit(Seq(fromFtqReq.startVAddr, fromFtqReq.nextCachelineVAddr))
   private val s0_vSetIdx   = VecInit(s0_vAddr.map(get_idx))
   private val s0_blkOffset = fromFtqReq.startVAddr(blockOffBits - 1, 0)
@@ -154,7 +157,7 @@ class ICacheMainPipe(implicit p: Parameters) extends ICacheModule
   toData.bits.waymask      := s0_waymasks
 
   private val s0_canGo = toData.ready && fromWayLookup.valid && s1_ready
-  s0_flush := io.flush
+  s0_flush := io.flush || io.flushFromBpu.shouldFlushByStage3(s0_ftqIdx)
   s0_fire  := s0_valid && s0_canGo && !s0_flush
 
   fromFtq.ready := s0_canGo
@@ -169,6 +172,7 @@ class ICacheMainPipe(implicit p: Parameters) extends ICacheModule
    */
   private val s1_valid = ValidHold(s0_fire, s1_fire, s1_flush)
 
+  private val s1_ftqIdx = RegEnable(s0_ftqIdx, 0.U.asTypeOf(s0_ftqIdx), s0_fire)
   private val s1_vAddr  = RegEnable(s0_vAddr, 0.U.asTypeOf(s0_vAddr), s0_fire)
   private val s1_pTag   = RegEnable(s0_pTag, 0.U(tagBits.W), s0_fire)
   private val s1_gpAddr = RegEnable(s0_gpAddr, 0.U.asTypeOf(s0_gpAddr), s0_fire)
@@ -374,7 +378,7 @@ class ICacheMainPipe(implicit p: Parameters) extends ICacheModule
   toIfu.bits.gpAddr            := s1_gpAddr
   toIfu.bits.isForVSnonLeafPTE := s1_isForVSnonLeafPTE
 
-  s1_flush := io.flush
+  s1_flush := io.flush || io.flushFromBpu.shouldFlushByStage3(s1_ftqIdx)
   s1_ready := (s1_fetchFinish && !io.respStall) || !s1_valid
   s1_fire  := s1_valid && s1_fetchFinish && !io.respStall && !s1_flush
 
diff --git a/src/main/scala/xiangshan/frontend/icache/ICachePrefetchPipe.scala b/src/main/scala/xiangshan/frontend/icache/ICachePrefetchPipe.scala
index 6d368ee0f42..d4511122f39 100644
--- a/src/main/scala/xiangshan/frontend/icache/ICachePrefetchPipe.scala
+++ b/src/main/scala/xiangshan/frontend/icache/ICachePrefetchPipe.scala
@@ -42,15 +42,15 @@ class ICachePrefetchPipe(implicit p: Parameters) extends ICacheModule
     val eccEnable:   Bool = Input(Bool())
     val flush:       Bool = Input(Bool())
 
-    val req:            DecoupledIO[PrefetchReqBundle] = Flipped(Decoupled(new PrefetchReqBundle))
-    val flushFromBpu:   BpuFlushInfo                   = Flipped(new BpuFlushInfo)
-    val itlb:           TlbRequestIO                   = new TlbRequestIO
-    val itlbFlushPipe:  Bool                           = Output(Bool())
-    val pmp:            PmpCheckBundle                 = new PmpCheckBundle
-    val metaRead:       MetaReadBundle                 = new MetaReadBundle
-    val missReq:        DecoupledIO[MissReqBundle]     = DecoupledIO(new MissReqBundle)
-    val missResp:       Valid[MissRespBundle]          = Flipped(ValidIO(new MissRespBundle))
-    val wayLookupWrite: DecoupledIO[WayLookupBundle]   = DecoupledIO(new WayLookupBundle)
+    val req:            DecoupledIO[PrefetchReqBundle]    = Flipped(Decoupled(new PrefetchReqBundle))
+    val flushFromBpu:   BpuFlushInfo                      = Flipped(new BpuFlushInfo)
+    val itlb:           TlbRequestIO                      = new TlbRequestIO
+    val itlbFlushPipe:  Bool                              = Output(Bool())
+    val pmp:            PmpCheckBundle                    = new PmpCheckBundle
+    val metaRead:       MetaReadBundle                    = new MetaReadBundle
+    val missReq:        DecoupledIO[MissReqBundle]        = DecoupledIO(new MissReqBundle)
+    val missResp:       Valid[MissRespBundle]             = Flipped(ValidIO(new MissRespBundle))
+    val wayLookupWrite: DecoupledIO[WayLookupWriteBundle] = DecoupledIO(new WayLookupWriteBundle)
 
     val perf: PrefetchPipePerfInfo = Output(new PrefetchPipePerfInfo)
   }
@@ -90,11 +90,8 @@ class ICachePrefetchPipe(implicit p: Parameters) extends ICacheModule
   private val s0_vSetIdx          = VecInit(s0_vAddr.map(get_idx))
   private val s0_backendException = io.req.bits.backendException
 
-  fromBpuS0Flush := !s0_isSoftPrefetch && (
-    io.flushFromBpu.shouldFlushByStage2(s0_ftqIdx) ||
-      io.flushFromBpu.shouldFlushByStage3(s0_ftqIdx)
-  )
-  s0_flush := io.flush || fromBpuS0Flush || s1_flush
+  fromBpuS0Flush := !s0_isSoftPrefetch && io.flushFromBpu.shouldFlushByStage3(s0_ftqIdx)
+  s0_flush       := io.flush || fromBpuS0Flush || s1_flush
 
   private val s0_canGo = s1_ready && toItlb.ready && toMeta.ready
   io.req.ready := s0_canGo
@@ -271,6 +268,7 @@ class ICachePrefetchPipe(implicit p: Parameters) extends ICacheModule
     (s1_state === S1FsmState.EnqWay) ||
       ((s1_state === S1FsmState.Idle) && tlbFinish)
   ) && !s1_flush && !fromMiss.valid && !s1_isSoftPrefetch // do not enqueue soft prefetch
+  toWayLookup.bits.ftqIdx            := s1_ftqIdx
   toWayLookup.bits.vSetIdx           := s1_vSetIdx
   toWayLookup.bits.waymask           := s1_waymasks
   toWayLookup.bits.pTag              := s1_pTag
diff --git a/src/main/scala/xiangshan/frontend/icache/ICacheWayLookup.scala b/src/main/scala/xiangshan/frontend/icache/ICacheWayLookup.scala
index 88e4d4041c2..92b6af48166 100644
--- a/src/main/scala/xiangshan/frontend/icache/ICacheWayLookup.scala
+++ b/src/main/scala/xiangshan/frontend/icache/ICacheWayLookup.scala
@@ -21,16 +21,19 @@ import org.chipsalliance.cde.config.Parameters
 import utility.CircularQueuePtr
 import utility.HasCircularQueuePtrHelper
 import utility.XSPerfHistogram
+import xiangshan.frontend.ftq.BpuFlushInfo
+import xiangshan.frontend.ftq.FtqPtr
 
 class ICacheWayLookup(implicit p: Parameters) extends ICacheModule
     with ICacheMissUpdateHelper
     with HasCircularQueuePtrHelper {
 
   class ICacheWayLookupIO(implicit p: Parameters) extends ICacheBundle {
-    val flush:  Bool                         = Input(Bool())
-    val read:   DecoupledIO[WayLookupBundle] = DecoupledIO(new WayLookupBundle)
-    val write:  DecoupledIO[WayLookupBundle] = Flipped(DecoupledIO(new WayLookupBundle))
-    val update: Valid[MissRespBundle]        = Flipped(ValidIO(new MissRespBundle))
+    val flush:        Bool                              = Input(Bool())
+    val flushFromBpu: BpuFlushInfo                      = Input(new BpuFlushInfo)
+    val read:         DecoupledIO[WayLookupBundle]      = DecoupledIO(new WayLookupBundle)
+    val write:        DecoupledIO[WayLookupWriteBundle] = Flipped(DecoupledIO(new WayLookupWriteBundle))
+    val update:       Valid[MissRespBundle]             = Flipped(ValidIO(new MissRespBundle))
 
     val perf: WayLookupPerfInfo = Output(new WayLookupPerfInfo)
   }
@@ -51,12 +54,23 @@ class ICacheWayLookup(implicit p: Parameters) extends ICacheModule
   private val readPtr  = RegInit(ICacheWayLookupPtr(false.B, 0.U))
   private val writePtr = RegInit(ICacheWayLookupPtr(false.B, 0.U))
 
+  private val tailFtqIdx = RegInit(0.U.asTypeOf(new FtqPtr))
+
   private val empty = readPtr === writePtr
   private val full  = (readPtr.value === writePtr.value) && (readPtr.flag ^ writePtr.flag)
 
+  // NOTE: May be unportable, we have bp3 == pf2 now, and WayLookup is written in pf1,
+  // so the tailing 0 (already bypassed to if1) or 1 (if1 stall, stored here) entries might be flushed by bp3,
+  // therefore, when shouldFlushByStage3, we need to move back writePtr by 0 (empty) or 1.
+  // If in future we have bp4 (or even more) flush, this might not be enough.
+  private val bpuS3FlushValid = !empty && io.flushFromBpu.shouldFlushByStage3(tailFtqIdx)
+  private val bpuS3FlushPtr   = writePtr - 1.U
+
   when(io.flush) {
     writePtr.value := 0.U
     writePtr.flag  := false.B
+  }.elsewhen(bpuS3FlushValid) {
+    writePtr := bpuS3FlushPtr
   }.elsewhen(io.write.fire) {
     writePtr := writePtr + 1.U
   }
@@ -68,11 +82,19 @@ class ICacheWayLookup(implicit p: Parameters) extends ICacheModule
     readPtr := readPtr + 1.U
   }
 
+  when(io.flush) {
+    tailFtqIdx.value := 0.U
+    tailFtqIdx.flag  := false.B
+  }.elsewhen(io.write.fire) {
+    tailFtqIdx := io.write.bits.ftqIdx
+  }
+
   private val gpfEntry = RegInit(0.U.asTypeOf(Valid(new WayLookupGpfEntry)))
   private val gpfPtr   = RegInit(ICacheWayLookupPtr(false.B, 0.U))
   private val gpfHit   = gpfPtr === readPtr && gpfEntry.valid
 
-  when(io.flush) {
+  when(io.flush || bpuS3FlushValid && gpfPtr === bpuS3FlushPtr) {
+    // When flushed by bp3
     // we don't need to reset gpfPtr, since the valid is actually gpf_entries.excp_tlb_gpf
     gpfEntry.valid := false.B
     gpfEntry.bits  := 0.U.asTypeOf(new WayLookupGpfEntry)
```
