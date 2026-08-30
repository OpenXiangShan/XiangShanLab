# Commit Log
- Issue: #3787
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/3787
- Issue state: closed
- Tested RTL commit: -
- Related PR: #3787
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/3787
- Changed files: 9
- Additions: 97
- Deletions: 107

## Files
- `src/main/scala/xiangshan/Bundle.scala`
- `src/main/scala/xiangshan/backend/Bundles.scala`
- `src/main/scala/xiangshan/frontend/FrontendBundle.scala`
- `src/main/scala/xiangshan/frontend/IBuffer.scala`
- `src/main/scala/xiangshan/frontend/IFU.scala`
- `src/main/scala/xiangshan/frontend/NewFtq.scala`
- `src/main/scala/xiangshan/frontend/icache/ICache.scala`
- `src/main/scala/xiangshan/frontend/icache/ICacheMainPipe.scala`
- `src/main/scala/xiangshan/frontend/icache/IPrefetch.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/Bundle.scala b/src/main/scala/xiangshan/Bundle.scala
index 16422a482fb..3e2c915dc86 100644
--- a/src/main/scala/xiangshan/Bundle.scala
+++ b/src/main/scala/xiangshan/Bundle.scala
@@ -145,7 +145,7 @@ class CtrlFlow(implicit p: Parameters) extends XSBundle {
   val pc = UInt(VAddrBits.W)
   val foldpc = UInt(MemPredPCWidth.W)
   val exceptionVec = ExceptionVec()
-  val exceptionFromBackend = Bool()
+  val backendException = Bool()
   val trigger = TriggerAction()
   val pd = new PreDecodeInfo
   val pred_taken = Bool()
diff --git a/src/main/scala/xiangshan/backend/Bundles.scala b/src/main/scala/xiangshan/backend/Bundles.scala
index 14c33b423a3..5d9c865355a 100644
--- a/src/main/scala/xiangshan/backend/Bundles.scala
+++ b/src/main/scala/xiangshan/backend/Bundles.scala
@@ -58,7 +58,7 @@ object Bundles {
       this.pc               := source.pc
       this.foldpc           := source.foldpc
       this.exceptionVec     := source.exceptionVec
-      this.isFetchMalAddr   := source.exceptionFromBackend
+      this.isFetchMalAddr   := source.backendException
       this.trigger          := source.trigger
       this.preDecodeInfo    := source.pd
       this.pred_taken       := source.pred_taken
diff --git a/src/main/scala/xiangshan/frontend/FrontendBundle.scala b/src/main/scala/xiangshan/frontend/FrontendBundle.scala
index 5c4231aeb43..d425dc06bd4 100644
--- a/src/main/scala/xiangshan/frontend/FrontendBundle.scala
+++ b/src/main/scala/xiangshan/frontend/FrontendBundle.scala
@@ -88,11 +88,9 @@ class IFUICacheIO(implicit p: Parameters) extends XSBundle with HasICacheParamet
 }
 
 class FtqToICacheRequestBundle(implicit p: Parameters) extends XSBundle with HasICacheParameters {
-  val pcMemRead   = Vec(5, new FtqICacheInfo)
-  val readValid   = Vec(5, Bool())
-  val backendIpf  = Bool()
-  val backendIgpf = Bool()
-  val backendIaf  = Bool()
+  val pcMemRead        = Vec(5, new FtqICacheInfo)
+  val readValid        = Vec(5, Bool())
+  val backendException = Bool()
 }
 
 class PredecodeWritebackBundle(implicit p: Parameters) extends XSBundle {
@@ -119,6 +117,8 @@ object ExceptionType {
   def af:    UInt = "b11".U // instruction access fault
   def width: Int  = 2
 
+  def hasException(e: UInt): Bool = e =/= none
+
   def fromOH(has_pf: Bool, has_gpf: Bool, has_af: Bool): UInt = {
     assert(
       PopCount(VecInit(has_pf, has_gpf, has_af)) <= 1.U,
@@ -138,14 +138,6 @@ object ExceptionType {
     )
   }
 
-  // raise pf/gpf/af according to ftq(backend) request
-  def fromFtq(req: FtqToICacheRequestBundle): UInt =
-    fromOH(
-      req.backendIpf,
-      req.backendIgpf,
-      req.backendIaf
-    )
-
   // raise pf/gpf/af according to itlb response
   def fromTlbResp(resp: TlbResp, useDup: Int = 0): UInt = {
     require(useDup >= 0 && useDup < resp.excp.length)
@@ -242,18 +234,18 @@ object ExceptionType {
 }
 
 class FetchToIBuffer(implicit p: Parameters) extends XSBundle {
-  val instrs               = Vec(PredictWidth, UInt(32.W))
-  val valid                = UInt(PredictWidth.W)
-  val enqEnable            = UInt(PredictWidth.W)
-  val pd                   = Vec(PredictWidth, new PreDecodeInfo)
-  val foldpc               = Vec(PredictWidth, UInt(MemPredPCWidth.W))
-  val ftqOffset            = Vec(PredictWidth, ValidUndirectioned(UInt(log2Ceil(PredictWidth).W)))
-  val exceptionFromBackend = Vec(PredictWidth, Bool())
-  val exceptionType        = Vec(PredictWidth, UInt(ExceptionType.width.W))
-  val crossPageIPFFix      = Vec(PredictWidth, Bool())
-  val illegalInstr         = Vec(PredictWidth, Bool())
-  val triggered            = Vec(PredictWidth, TriggerAction())
-  val isLastInFtqEntry     = Vec(PredictWidth, Bool())
+  val instrs           = Vec(PredictWidth, UInt(32.W))
+  val valid            = UInt(PredictWidth.W)
+  val enqEnable        = UInt(PredictWidth.W)
+  val pd               = Vec(PredictWidth, new PreDecodeInfo)
+  val foldpc           = Vec(PredictWidth, UInt(MemPredPCWidth.W))
+  val ftqOffset        = Vec(PredictWidth, ValidUndirectioned(UInt(log2Ceil(PredictWidth).W)))
+  val backendException = Vec(PredictWidth, Bool())
+  val exceptionType    = Vec(PredictWidth, UInt(ExceptionType.width.W))
+  val crossPageIPFFix  = Vec(PredictWidth, Bool())
+  val illegalInstr     = Vec(PredictWidth, Bool())
+  val triggered        = Vec(PredictWidth, TriggerAction())
+  val isLastInFtqEntry = Vec(PredictWidth, Bool())
 
   val pc           = Vec(PredictWidth, UInt(VAddrBits.W))
   val ftqPtr       = new FtqPtr
diff --git a/src/main/scala/xiangshan/frontend/IBuffer.scala b/src/main/scala/xiangshan/frontend/IBuffer.scala
index 29acb23e107..87f50addf72 100644
--- a/src/main/scala/xiangshan/frontend/IBuffer.scala
+++ b/src/main/scala/xiangshan/frontend/IBuffer.scala
@@ -49,17 +49,17 @@ class IBufferIO(implicit p: Parameters) extends XSBundle {
 }
 
 class IBufEntry(implicit p: Parameters) extends XSBundle {
-  val inst                 = UInt(32.W)
-  val pc                   = UInt(VAddrBits.W)
-  val foldpc               = UInt(MemPredPCWidth.W)
-  val pd                   = new PreDecodeInfo
-  val pred_taken           = Bool()
-  val ftqPtr               = new FtqPtr
-  val ftqOffset            = UInt(log2Ceil(PredictWidth).W)
-  val exceptionType        = IBufferExceptionType()
-  val exceptionFromBackend = Bool()
-  val triggered            = TriggerAction()
-  val isLastInFtqEntry     = Bool()
+  val inst             = UInt(32.W)
+  val pc               = UInt(VAddrBits.W)
+  val foldpc           = UInt(MemPredPCWidth.W)
+  val pd               = new PreDecodeInfo
+  val pred_taken       = Bool()
+  val ftqPtr           = new FtqPtr
+  val ftqOffset        = UInt(log2Ceil(PredictWidth).W)
+  val exceptionType    = IBufferExceptionType()
+  val backendException = Bool()
+  val triggered        = TriggerAction()
+  val isLastInFtqEntry = Bool()
 
   def fromFetch(fetch: FetchToIBuffer, i: Int): IBufEntry = {
     inst       := fetch.instrs(i)
@@ -74,9 +74,9 @@ class IBufEntry(implicit p: Parameters) extends XSBundle {
       fetch.crossPageIPFFix(i),
       fetch.illegalInstr(i)
     )
-    exceptionFromBackend := fetch.exceptionFromBackend(i)
-    triggered            := fetch.triggered(i)
-    isLastInFtqEntry     := fetch.isLastInFtqEntry(i)
+    backendException := fetch.backendException(i)
+    triggered        := fetch.triggered(i)
+    isLastInFtqEntry := fetch.isLastInFtqEntry(i)
     this
   }
 
@@ -90,7 +90,7 @@ class IBufEntry(implicit p: Parameters) extends XSBundle {
     cf.exceptionVec(instrGuestPageFault) := IBufferExceptionType.isGPF(this.exceptionType)
     cf.exceptionVec(instrAccessFault)    := IBufferExceptionType.isAF(this.exceptionType)
     cf.exceptionVec(EX_II)               := IBufferExceptionType.isRVCII(this.exceptionType)
-    cf.exceptionFromBackend              := exceptionFromBackend
+    cf.backendException                  := backendException
     cf.trigger                           := triggered
     cf.pd                                := pd
     cf.pred_taken                        := pred_taken
diff --git a/src/main/scala/xiangshan/frontend/IFU.scala b/src/main/scala/xiangshan/frontend/IFU.scala
index ea81c57a855..59e78641a6e 100644
--- a/src/main/scala/xiangshan/frontend/IFU.scala
+++ b/src/main/scala/xiangshan/frontend/IFU.scala
@@ -389,8 +389,8 @@ class NewIFU(implicit p: Parameters) extends XSModule
     .elsewhen(f1_fire && !f1_flush)(f2_valid := true.B)
     .elsewhen(f2_fire)(f2_valid := false.B)
 
-  val f2_exception          = VecInit((0 until PortNumber).map(i => fromICache(i).bits.exception))
-  val f2_except_fromBackend = fromICache(0).bits.exceptionFromBackend
+  val f2_exception        = VecInit((0 until PortNumber).map(i => fromICache(i).bits.exception))
+  val f2_backendException = fromICache(0).bits.backendException
   // paddr and gpaddr of [startAddr, nextLineAddr]
   val f2_paddrs            = VecInit((0 until PortNumber).map(i => fromICache(i).bits.paddr))
   val f2_gpaddr            = fromICache(0).bits.gpaddr
@@ -520,9 +520,9 @@ class NewIFU(implicit p: Parameters) extends XSModule
 
   val f3_cut_data = RegEnable(f2_cut_data, f2_fire)
 
-  val f3_exception          = RegEnable(f2_exception, f2_fire)
-  val f3_mmio               = RegEnable(f2_mmio, f2_fire)
-  val f3_except_fromBackend = RegEnable(f2_except_fromBackend, f2_fire)
+  val f3_exception        = RegEnable(f2_exception, f2_fire)
+  val f3_mmio             = RegEnable(f2_mmio, f2_fire)
+  val f3_backendException = RegEnable(f2_backendException, f2_fire)
 
   val f3_instr = RegEnable(f2_instr, f2_fire)
 
@@ -866,11 +866,11 @@ class NewIFU(implicit p: Parameters) extends XSModule
   }
   io.toIbuffer.bits.foldpc        := f3_foldpc
   io.toIbuffer.bits.exceptionType := ExceptionType.merge(f3_exception_vec, f3_crossPage_exception_vec)
-  // exceptionFromBackend only needs to be set for the first instruction.
+  // backendException only needs to be set for the first instruction.
   // Other instructions in the same block may have pf or af set,
   // which is a side effect of the first instruction and actually not necessary.
-  io.toIbuffer.bits.exceptionFromBackend := (0 until PredictWidth).map {
-    case 0 => f3_except_fromBackend
+  io.toIbuffer.bits.backendException := (0 until PredictWidth).map {
+    case 0 => f3_backendException
     case _ => false.B
   }
   io.toIbuffer.bits.crossPageIPFFix := f3_crossPage_exception_vec.map(_ =/= ExceptionType.none)
diff --git a/src/main/scala/xiangshan/frontend/NewFtq.scala b/src/main/scala/xiangshan/frontend/NewFtq.scala
index c41c6cc5ad0..453830d9084 100644
--- a/src/main/scala/xiangshan/frontend/NewFtq.scala
+++ b/src/main/scala/xiangshan/frontend/NewFtq.scala
@@ -210,8 +210,9 @@ class FtqToICacheIO(implicit p: Parameters) extends XSBundle {
 }
 
 class FtqToPrefetchIO(implicit p: Parameters) extends XSBundle {
-  val req          = Decoupled(new FtqICacheInfo)
-  val flushFromBpu = new BpuFlushInfo
+  val req              = Decoupled(new FtqICacheInfo)
+  val flushFromBpu     = new BpuFlushInfo
+  val backendException = UInt(ExceptionType.width.W)
 }
 
 trait HasBackendRedirectInfo extends HasXSParameter {
@@ -558,23 +559,22 @@ class Ftq(implicit p: Parameters) extends XSModule with HasCircularQueuePtrHelpe
   // raises IPF or IAF, which is ifuWbPtr_write or IfuPtr_write.
   // Only when IFU has written back that FTQ entry can backendIpf and backendIaf be false because this
   // makes sure that IAF and IPF are correctly raised instead of being flushed by redirect requests.
-  val backendIpf        = RegInit(false.B)
-  val backendIgpf       = RegInit(false.B)
-  val backendIaf        = RegInit(false.B)
+  val backendException  = RegInit(ExceptionType.none)
   val backendPcFaultPtr = RegInit(FtqPtr(false.B, 0.U))
   when(fromBackendRedirect.valid) {
-    backendIpf  := fromBackendRedirect.bits.cfiUpdate.backendIPF
-    backendIgpf := fromBackendRedirect.bits.cfiUpdate.backendIGPF
-    backendIaf  := fromBackendRedirect.bits.cfiUpdate.backendIAF
+    backendException := ExceptionType.fromOH(
+      has_pf = fromBackendRedirect.bits.cfiUpdate.backendIPF,
+      has_gpf = fromBackendRedirect.bits.cfiUpdate.backendIGPF,
+      has_af = fromBackendRedirect.bits.cfiUpdate.backendIAF
+    )
     when(
-      fromBackendRedirect.bits.cfiUpdate.backendIPF || fromBackendRedirect.bits.cfiUpdate.backendIGPF || fromBackendRedirect.bits.cfiUpdate.backendIAF
+      fromBackendRedirect.bits.cfiUpdate.backendIPF || fromBackendRedirect.bits.cfiUpdate.backendIGPF ||
+        fromBackendRedirect.bits.cfiUpdate.backendIAF
     ) {
       backendPcFaultPtr := ifuWbPtr_write
     }
   }.elsewhen(ifuWbPtr =/= backendPcFaultPtr) {
-    backendIpf  := false.B
-    backendIgpf := false.B
-    backendIaf  := false.B
+    backendException := ExceptionType.none
   }
 
   // **********************************************************************
@@ -899,13 +899,12 @@ class Ftq(implicit p: Parameters) extends XSModule with HasCircularQueuePtrHelpe
     copy.fromFtqPcBundle(toICachePcBundle(i))
     copy.ftqIdx := ifuPtr
   }
-  io.toICache.req.bits.backendIpf  := backendIpf && backendPcFaultPtr === ifuPtr
-  io.toICache.req.bits.backendIgpf := backendIgpf && backendPcFaultPtr === ifuPtr
-  io.toICache.req.bits.backendIaf  := backendIaf && backendPcFaultPtr === ifuPtr
+  io.toICache.req.bits.backendException := ExceptionType.hasException(backendException) && backendPcFaultPtr === ifuPtr
 
   io.toPrefetch.req.valid := toPrefetchEntryToSend && pfPtr =/= bpuPtr
   io.toPrefetch.req.bits.fromFtqPcBundle(toPrefetchPcBundle)
-  io.toPrefetch.req.bits.ftqIdx := pfPtr
+  io.toPrefetch.req.bits.ftqIdx  := pfPtr
+  io.toPrefetch.backendException := Mux(backendPcFaultPtr === pfPtr, backendException, ExceptionType.none)
   // io.toICache.req.bits.bypassSelect := last_cycle_bpu_in && bpu_in_bypass_ptr === ifuPtr
   // io.toICache.req.bits.bpuBypassWrite.zipWithIndex.map{case(bypassWrtie, i) =>
   //   bypassWrtie.startAddr := bpu_in_bypass_buf.tail(i).startAddr
diff --git a/src/main/scala/xiangshan/frontend/icache/ICache.scala b/src/main/scala/xiangshan/frontend/icache/ICache.scala
index 957cb5777fb..7f4b595a120 100644
--- a/src/main/scala/xiangshan/frontend/icache/ICache.scala
+++ b/src/main/scala/xiangshan/frontend/icache/ICache.scala
@@ -574,9 +574,10 @@ class ICacheImp(outer: ICache) extends LazyModuleImp(outer) with HasICacheParame
   private val ftqPrefetch = WireInit(0.U.asTypeOf(new IPrefetchReq))
   ftqPrefetch.fromFtqICacheInfo(io.ftqPrefetch.req.bits)
   // software prefetch has higher priority
-  prefetcher.io.req.valid  := softPrefetchValid || io.ftqPrefetch.req.valid
-  prefetcher.io.req.bits   := Mux(softPrefetchValid, softPrefetch, ftqPrefetch)
-  io.ftqPrefetch.req.ready := prefetcher.io.req.ready && !softPrefetchValid
+  prefetcher.io.req.valid                 := softPrefetchValid || io.ftqPrefetch.req.valid
+  prefetcher.io.req.bits                  := Mux(softPrefetchValid, softPrefetch, ftqPrefetch)
+  prefetcher.io.req.bits.backendException := io.ftqPrefetch.backendException
+  io.ftqPrefetch.req.ready                := prefetcher.io.req.ready && !softPrefetchValid
 
   missUnit.io.hartId := io.hartId
   missUnit.io.fencei := io.fencei
diff --git a/src/main/scala/xiangshan/frontend/icache/ICacheMainPipe.scala b/src/main/scala/xiangshan/frontend/icache/ICacheMainPipe.scala
index 43e33ddaf7a..e3a25dab386 100644
--- a/src/main/scala/xiangshan/frontend/icache/ICacheMainPipe.scala
+++ b/src/main/scala/xiangshan/frontend/icache/ICacheMainPipe.scala
@@ -37,15 +37,15 @@ class ICacheMainPipeReq(implicit p: Parameters) extends ICacheBundle {
 }
 
 class ICacheMainPipeResp(implicit p: Parameters) extends ICacheBundle {
-  val vaddr                = UInt(VAddrBits.W)
-  val data                 = UInt(blockBits.W)
-  val paddr                = UInt(PAddrBits.W)
-  val gpaddr               = UInt(GPAddrBits.W)
-  val isForVSnonLeafPTE    = Bool()
-  val exception            = UInt(ExceptionType.width.W)
-  val pmp_mmio             = Bool()
-  val itlb_pbmt            = UInt(Pbmt.width.W)
-  val exceptionFromBackend = Bool()
+  val vaddr             = UInt(VAddrBits.W)
+  val data              = UInt(blockBits.W)
+  val paddr             = UInt(PAddrBits.W)
+  val gpaddr            = UInt(GPAddrBits.W)
+  val isForVSnonLeafPTE = Bool()
+  val exception         = UInt(ExceptionType.width.W)
+  val pmp_mmio          = Bool()
+  val itlb_pbmt         = UInt(Pbmt.width.W)
+  val backendException  = Bool()
 }
 
 class ICacheMainPipeBundle(implicit p: Parameters) extends ICacheBundle {
@@ -167,8 +167,7 @@ class ICacheMainPipe(implicit p: Parameters) extends ICacheModule {
   val s0_req_vSetIdx = s0_req_vSetIdx_all.last
   val s0_doubleline  = s0_doubleline_all.last
 
-  val s0_ftq_exception    = VecInit((0 until PortNumber).map(i => ExceptionType.fromFtq(fromFtq.bits)))
-  val s0_excp_fromBackend = fromFtq.bits.backendIaf || fromFtq.bits.backendIpf || fromFtq.bits.backendIgpf
+  val s0_backendException = fromFtq.bits.backendException
 
   /**
     ******************************************************************************
@@ -197,11 +196,6 @@ class ICacheMainPipe(implicit p: Parameters) extends ICacheModule {
     )
   }
 
-  val s0_exception_out = ExceptionType.merge(
-    s0_ftq_exception, // backend-requested exception has the highest priority
-    s0_itlb_exception
-  )
-
   /**
     ******************************************************************************
     * data SRAM request
@@ -237,8 +231,8 @@ class ICacheMainPipe(implicit p: Parameters) extends ICacheModule {
   val s1_req_isForVSnonLeafPTE = RegEnable(s0_req_isForVSnonLeafPTE, 0.U.asTypeOf(s0_req_isForVSnonLeafPTE), s0_fire)
   val s1_doubleline            = RegEnable(s0_doubleline, 0.U.asTypeOf(s0_doubleline), s0_fire)
   val s1_SRAMhits              = RegEnable(s0_hits, 0.U.asTypeOf(s0_hits), s0_fire)
-  val s1_itlb_exception        = RegEnable(s0_exception_out, 0.U.asTypeOf(s0_exception_out), s0_fire)
-  val s1_excp_fromBackend      = RegEnable(s0_excp_fromBackend, false.B, s0_fire)
+  val s1_itlb_exception        = RegEnable(s0_itlb_exception, 0.U.asTypeOf(s0_itlb_exception), s0_fire)
+  val s1_backendException      = RegEnable(s0_backendException, false.B, s0_fire)
   val s1_itlb_pbmt             = RegEnable(s0_itlb_pbmt, 0.U.asTypeOf(s0_itlb_pbmt), s0_fire)
   val s1_waymasks              = RegEnable(s0_waymasks, 0.U.asTypeOf(s0_waymasks), s0_fire)
   val s1_meta_codes            = RegEnable(s0_meta_codes, 0.U.asTypeOf(s0_meta_codes), s0_fire)
@@ -343,7 +337,7 @@ class ICacheMainPipe(implicit p: Parameters) extends ICacheModule {
   val s2_doubleline            = RegEnable(s1_doubleline, 0.U.asTypeOf(s1_doubleline), s1_fire)
   val s2_exception =
     RegEnable(s1_exception_out, 0.U.asTypeOf(s1_exception_out), s1_fire) // includes itlb/pmp/meta exception
-  val s2_excp_fromBackend = RegEnable(s1_excp_fromBackend, false.B, s1_fire)
+  val s2_backendException = RegEnable(s1_backendException, false.B, s1_fire)
   val s2_pmp_mmio         = RegEnable(s1_pmp_mmio, 0.U.asTypeOf(s1_pmp_mmio), s1_fire)
   val s2_itlb_pbmt        = RegEnable(s1_itlb_pbmt, 0.U.asTypeOf(s1_itlb_pbmt), s1_fire)
 
@@ -505,10 +499,10 @@ class ICacheMainPipe(implicit p: Parameters) extends ICacheModule {
       toIFU(i).bits.itlb_pbmt := Mux(s2_doubleline, s2_itlb_pbmt(i), Pbmt.pma)
       toIFU(i).bits.data      := DontCare
     }
-    toIFU(i).bits.exceptionFromBackend := s2_excp_fromBackend
-    toIFU(i).bits.vaddr                := s2_req_vaddr(i)
-    toIFU(i).bits.paddr                := s2_req_paddr(i)
-    toIFU(i).bits.gpaddr := s2_req_gpaddr // Note: toIFU(1).bits.gpaddr is actually DontCare in current design
+    toIFU(i).bits.backendException := s2_backendException
+    toIFU(i).bits.vaddr            := s2_req_vaddr(i)
+    toIFU(i).bits.paddr            := s2_req_paddr(i)
+    toIFU(i).bits.gpaddr           := s2_req_gpaddr // Note: toIFU(1).bits.gpaddr is actually DontCare in current design
     toIFU(i).bits.isForVSnonLeafPTE := s2_req_isForVSnonLeafPTE
   }
 
diff --git a/src/main/scala/xiangshan/frontend/icache/IPrefetch.scala b/src/main/scala/xiangshan/frontend/icache/IPrefetch.scala
index 2048cf23cb7..800a493ed98 100644
--- a/src/main/scala/xiangshan/frontend/icache/IPrefetch.scala
+++ b/src/main/scala/xiangshan/frontend/icache/IPrefetch.scala
@@ -35,11 +35,12 @@ abstract class IPrefetchBundle(implicit p: Parameters) extends ICacheBundle
 abstract class IPrefetchModule(implicit p: Parameters) extends ICacheModule
 
 class IPrefetchReq(implicit p: Parameters) extends IPrefetchBundle {
-  val startAddr:      UInt   = UInt(VAddrBits.W)
-  val nextlineStart:  UInt   = UInt(VAddrBits.W)
-  val ftqIdx:         FtqPtr = new FtqPtr
-  val isSoftPrefetch: Bool   = Bool()
-  def crossCacheline: Bool   = startAddr(blockOffBits - 1) === 1.U
+  val startAddr:        UInt   = UInt(VAddrBits.W)
+  val nextlineStart:    UInt   = UInt(VAddrBits.W)
+  val ftqIdx:           FtqPtr = new FtqPtr
+  val isSoftPrefetch:   Bool   = Bool()
+  val backendException: UInt   = UInt(ExceptionType.width.W)
+  def crossCacheline:   Bool   = startAddr(blockOffBits - 1) === 1.U
 
   def fromFtqICacheInfo(info: FtqICacheInfo): IPrefetchReq = {
     this.startAddr      := info.startAddr
@@ -104,11 +105,12 @@ class IPrefetchPipe(implicit p: Parameters) extends IPrefetchModule {
     * receive ftq req
     ******************************************************************************
     */
-  val s0_req_vaddr      = VecInit(Seq(io.req.bits.startAddr, io.req.bits.nextlineStart))
-  val s0_req_ftqIdx     = io.req.bits.ftqIdx
-  val s0_isSoftPrefetch = io.req.bits.isSoftPrefetch
-  val s0_doubleline     = io.req.bits.crossCacheline
-  val s0_req_vSetIdx    = s0_req_vaddr.map(get_idx)
+  val s0_req_vaddr        = VecInit(Seq(io.req.bits.startAddr, io.req.bits.nextlineStart))
+  val s0_req_ftqIdx       = io.req.bits.ftqIdx
+  val s0_isSoftPrefetch   = io.req.bits.isSoftPrefetch
+  val s0_doubleline       = io.req.bits.crossCacheline
+  val s0_req_vSetIdx      = s0_req_vaddr.map(get_idx)
+  val s0_backendException = VecInit(Seq.fill(PortNumber)(io.req.bits.backendException))
 
   from_bpu_s0_flush := !s0_isSoftPrefetch && (io.flushFromBpu.shouldFlushByStage2(s0_req_ftqIdx) ||
     io.flushFromBpu.shouldFlushByStage3(s0_req_ftqIdx))
@@ -130,11 +132,12 @@ class IPrefetchPipe(implicit p: Parameters) extends IPrefetchModule {
     */
   val s1_valid = generatePipeControl(lastFire = s0_fire, thisFire = s1_fire, thisFlush = s1_flush, lastFlush = false.B)
 
-  val s1_req_vaddr      = RegEnable(s0_req_vaddr, 0.U.asTypeOf(s0_req_vaddr), s0_fire)
-  val s1_isSoftPrefetch = RegEnable(s0_isSoftPrefetch, 0.U.asTypeOf(s0_isSoftPrefetch), s0_fire)
-  val s1_doubleline     = RegEnable(s0_doubleline, 0.U.asTypeOf(s0_doubleline), s0_fire)
-  val s1_req_ftqIdx     = RegEnable(s0_req_ftqIdx, 0.U.asTypeOf(s0_req_ftqIdx), s0_fire)
-  val s1_req_vSetIdx    = VecInit(s1_req_vaddr.map(get_idx))
+  val s1_req_vaddr        = RegEnable(s0_req_vaddr, 0.U.asTypeOf(s0_req_vaddr), s0_fire)
+  val s1_isSoftPrefetch   = RegEnable(s0_isSoftPrefetch, 0.U.asTypeOf(s0_isSoftPrefetch), s0_fire)
+  val s1_doubleline       = RegEnable(s0_doubleline, 0.U.asTypeOf(s0_doubleline), s0_fire)
+  val s1_req_ftqIdx       = RegEnable(s0_req_ftqIdx, 0.U.asTypeOf(s0_req_ftqIdx), s0_fire)
+  val s1_req_vSetIdx      = VecInit(s1_req_vaddr.map(get_idx))
+  val s1_backendException = RegEnable(s0_backendException, 0.U.asTypeOf(s0_backendException), s0_fire)
 
   val m_idle :: m_itlbResend :: m_metaResend :: m_enqWay :: m_enterS2 :: Nil = Enum(5)
   val state                                                                  = RegInit(m_idle)
@@ -396,6 +399,7 @@ class IPrefetchPipe(implicit p: Parameters) extends IPrefetchModule {
   // merge s1 itlb/pmp exceptions, itlb has the highest priority, pmp next
   // for timing consideration, meta_corrupt is not merged, and it will NOT cancel prefetch
   val s1_exception_out = ExceptionType.merge(
+    s1_backendException,
     s1_itlb_exception,
     s1_pmp_exception
   )
```
