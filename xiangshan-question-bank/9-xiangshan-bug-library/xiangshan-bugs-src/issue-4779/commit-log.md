# Commit Log
- Issue: #4779
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/4779
- Issue state: closed
- Tested RTL commit: -
- Related PR: #4779
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/4779
- Changed files: 6
- Additions: 110
- Deletions: 112

## Files
- `src/main/scala/xiangshan/cache/dcache/DCacheWrapper.scala`
- `src/main/scala/xiangshan/mem/Bundles.scala`
- `src/main/scala/xiangshan/mem/MemBlock.scala`
- `src/main/scala/xiangshan/mem/lsqueue/LSQWrapper.scala`
- `src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala`
- `src/main/scala/xiangshan/mem/sbuffer/Sbuffer.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/cache/dcache/DCacheWrapper.scala b/src/main/scala/xiangshan/cache/dcache/DCacheWrapper.scala
index 4d556437a07..39419693598 100644
--- a/src/main/scala/xiangshan/cache/dcache/DCacheWrapper.scala
+++ b/src/main/scala/xiangshan/cache/dcache/DCacheWrapper.scala
@@ -33,9 +33,8 @@ import xiangshan._
 import xiangshan.backend.Bundles.DynInst
 import xiangshan.backend.rob.{RobDebugRollingIO, RobPtr}
 import xiangshan.cache.wpu._
-import xiangshan.mem.{AddPipelineReg, HasL1PrefetchSourceParameter}
+import xiangshan.mem.{AddPipelineReg, DataBufferEntry, HasL1PrefetchSourceParameter, LqPtr}
 import xiangshan.mem.prefetch._
-import xiangshan.mem.LqPtr
 
 // DCache specific parameters
 case class DCacheParameters
@@ -411,6 +410,20 @@ class DCacheWordReqWithVaddrAndPfFlag(implicit p: Parameters) extends DCacheWord
   val vecValid = Bool()
   val sqNeedDeq = Bool()
 
+  def fromDataBufferEntry(src: DataBufferEntry, cmd: UInt) = {
+    this := DontCare
+    this := DontCare
+    this.cmd := cmd
+    this.addr := src.addr
+    this.vaddr := src.vaddr
+    this.data := src.data
+    this.mask := src.mask
+    this.wline := src.wline && src.vecValid
+    this.prefetch := src.prefetch
+    this.vecValid := src.vecValid
+    this.sqNeedDeq := src.sqNeedDeq
+  }
+
   def toDCacheWordReqWithVaddr() = {
     val res = Wire(new DCacheWordReqWithVaddr)
     res.vaddr := vaddr
diff --git a/src/main/scala/xiangshan/mem/Bundles.scala b/src/main/scala/xiangshan/mem/Bundles.scala
index 38de8ff4c2f..87233e7d126 100644
--- a/src/main/scala/xiangshan/mem/Bundles.scala
+++ b/src/main/scala/xiangshan/mem/Bundles.scala
@@ -376,4 +376,10 @@ class ToSbufferDifftestInfoBundle(implicit p: Parameters) extends XSBundle{
 class VecMissalignedDebugBundle (implicit p: Parameters) extends XSBundle {
   val start      = UInt(log2Up(XLEN).W) // indicate first byte position of first unit-stride's element when unaligned
   val offset     = UInt(log2Up(XLEN).W) // indicate byte offset of unit-stride's element when unaligned
+}
+
+class DiffStoreIO(implicit p: Parameters) extends XSBundle{
+  val diffInfo = Vec(EnsbufferWidth, Flipped(new ToSbufferDifftestInfoBundle()))
+  val pmaStore = Vec(EnsbufferWidth, Flipped(Valid(new DCacheWordReqWithVaddrAndPfFlag())))
+  val ncStore = Flipped(Valid(new UncacheWordReq()))
 }
\ No newline at end of file
diff --git a/src/main/scala/xiangshan/mem/MemBlock.scala b/src/main/scala/xiangshan/mem/MemBlock.scala
index 367f5137cb6..fa2c06563eb 100644
--- a/src/main/scala/xiangshan/mem/MemBlock.scala
+++ b/src/main/scala/xiangshan/mem/MemBlock.scala
@@ -1505,7 +1505,6 @@ class MemBlockInlinedImp(outer: MemBlockInlined) extends LazyModuleImp(outer)
 
   // LSQ to store buffer
   lsq.io.sbuffer        <> sbuffer.io.in
-  lsq.io.generateFromSBuffer <> sbuffer.io.generateToSQ
   sbuffer.io.in(0).valid := lsq.io.sbuffer(0).valid || vSegmentUnit.io.sbuffer.valid
   sbuffer.io.in(0).bits  := Mux1H(Seq(
     vSegmentUnit.io.sbuffer.valid -> vSegmentUnit.io.sbuffer.bits,
@@ -1516,21 +1515,30 @@ class MemBlockInlinedImp(outer: MemBlockInlined) extends LazyModuleImp(outer)
   dcache.io.force_write := lsq.io.force_write
 
   // Initialize when unenabled difftest.
-  sbuffer.io.vecDifftestInfo      := DontCare
-  lsq.io.sbufferVecDifftestInfo   := DontCare
+  sbuffer.io.diffStore := DontCare
+  lsq.io.diffStore := DontCare
   vSegmentUnit.io.vecDifftestInfo := DontCare
+  io.mem_to_ooo.storeDebugInfo := DontCare
+  // store event difftest information
   if (env.EnableDifftest) {
-    sbuffer.io.vecDifftestInfo .zipWithIndex.map{ case (sbufferPort, index) =>
-      if (index == 0) {
-        val vSegmentDifftestValid = vSegmentUnit.io.vecDifftestInfo.valid
-        sbufferPort.valid := Mux(vSegmentDifftestValid, vSegmentUnit.io.vecDifftestInfo.valid, lsq.io.sbufferVecDifftestInfo(0).valid)
-        sbufferPort.bits  := Mux(vSegmentDifftestValid, vSegmentUnit.io.vecDifftestInfo.bits, lsq.io.sbufferVecDifftestInfo(0).bits)
-
-        vSegmentUnit.io.vecDifftestInfo.ready  := sbufferPort.ready
-        lsq.io.sbufferVecDifftestInfo(0).ready := sbufferPort.ready
-      } else {
-         sbufferPort <> lsq.io.sbufferVecDifftestInfo(index)
+    // diffStoreEvent for vSegment, pmaStore and ncStore
+    (0 until EnsbufferWidth).foreach{i =>
+      if(i == 0) {
+        when(vSegmentUnit.io.sbuffer.valid) {
+          sbuffer.io.diffStore.diffInfo(0) := vSegmentUnit.io.vecDifftestInfo.bits
+          sbuffer.io.diffStore.pmaStore(0).valid := vSegmentUnit.io.sbuffer.fire
+          sbuffer.io.diffStore.pmaStore(0).bits := vSegmentUnit.io.sbuffer.bits
+        }.otherwise{
+          sbuffer.io.diffStore.diffInfo(0) := lsq.io.diffStore.diffInfo(0)
+          sbuffer.io.diffStore.pmaStore(0) := lsq.io.diffStore.pmaStore(0)
+        }
+      }else{
+        sbuffer.io.diffStore.diffInfo(i) := lsq.io.diffStore.diffInfo(i)
+        sbuffer.io.diffStore.pmaStore(i) := lsq.io.diffStore.pmaStore(i)
       }
+      sbuffer.io.diffStore.ncStore := lsq.io.diffStore.ncStore
+      io.mem_to_ooo.storeDebugInfo(i).robidx := sbuffer.io.diffStore.diffInfo(i).uop.robIdx
+      sbuffer.io.diffStore.diffInfo(i).uop.pc := io.mem_to_ooo.storeDebugInfo(i).pc
     }
   }
 
@@ -2076,15 +2084,6 @@ class MemBlockInlinedImp(outer: MemBlockInlined) extends LazyModuleImp(outer)
     ) << instOffsetBits)
   }
 
-  io.mem_to_ooo.storeDebugInfo := DontCare
-  // store event difftest information
-  if (env.EnableDifftest) {
-    (0 until EnsbufferWidth).foreach{i =>
-        io.mem_to_ooo.storeDebugInfo(i).robidx := sbuffer.io.vecDifftestInfo(i).bits.uop.robIdx
-        sbuffer.io.vecDifftestInfo(i).bits.uop.pc := io.mem_to_ooo.storeDebugInfo(i).pc
-    }
-  }
-
   // top-down info
   dcache.io.debugTopDown.robHeadVaddr := io.debugTopDown.robHeadVaddr
   dtlbRepeater.io.debugTopDown.robHeadVaddr := io.debugTopDown.robHeadVaddr
diff --git a/src/main/scala/xiangshan/mem/lsqueue/LSQWrapper.scala b/src/main/scala/xiangshan/mem/lsqueue/LSQWrapper.scala
index cd891d09cd4..88eeedf1de5 100644
--- a/src/main/scala/xiangshan/mem/lsqueue/LSQWrapper.scala
+++ b/src/main/scala/xiangshan/mem/lsqueue/LSQWrapper.scala
@@ -89,7 +89,6 @@ class LsqWrapper(implicit p: Parameters) extends XSModule with HasDCacheParamete
     val ncOut = Vec(LoadPipelineWidth, DecoupledIO(new LsPipelineBundle))
     val replay = Vec(LoadPipelineWidth, Decoupled(new LsPipelineBundle))
     val sbuffer = Vec(EnsbufferWidth, Decoupled(new DCacheWordReqWithVaddrAndPfFlag))
-    val sbufferVecDifftestInfo = Vec(EnsbufferWidth, Decoupled(new ToSbufferDifftestInfoBundle)) // for vector store difftest
     val forward = Vec(LoadPipelineWidth, Flipped(new PipeLoadForwardQueryIO))
     val rob = Flipped(new RobLsqIO)
     val nuke_rollback = Vec(StorePipelineWidth, Output(Valid(new Redirect)))
@@ -133,7 +132,7 @@ class LsqWrapper(implicit p: Parameters) extends XSModule with HasDCacheParamete
     val debugTopDown = new LoadQueueTopDownIO
     val noUopsIssued = Input(Bool())
 
-    val generateFromSBuffer = Input(new GenerateInfoFromSBuffer)
+    val diffStore = Flipped(new DiffStoreIO)
   })
 
   val loadQueue = Module(new LoadQueue)
@@ -183,7 +182,6 @@ class LsqWrapper(implicit p: Parameters) extends XSModule with HasDCacheParamete
   storeQueue.io.storeDataIn <> io.std.storeDataIn // from store_s0
   storeQueue.io.storeMaskIn <> io.sta.storeMaskIn // from store_s0
   storeQueue.io.sbuffer     <> io.sbuffer
-  storeQueue.io.sbufferVecDifftestInfo <> io.sbufferVecDifftestInfo
   storeQueue.io.mmioStout   <> io.mmioStout
   storeQueue.io.cboZeroStout <> io.cboZeroStout
   storeQueue.io.vecmmioStout <> io.vecmmioStout
@@ -199,7 +197,7 @@ class LsqWrapper(implicit p: Parameters) extends XSModule with HasDCacheParamete
   storeQueue.io.cmoOpResp    <> io.cmoOpResp
   storeQueue.io.flushSbuffer <> io.flushSbuffer
   storeQueue.io.maControl    <> io.maControl
-  storeQueue.io.generateFromSBuffer := io.generateFromSBuffer
+  io.diffStore := storeQueue.io.diffStore
 
   /* <------- DANGEROUS: Don't change sequence here ! -------> */
 
diff --git a/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala b/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala
index 5198b2c3dcc..412e7c56d6d 100644
--- a/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala
+++ b/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala
@@ -174,7 +174,6 @@ class StoreQueue(implicit p: Parameters) extends XSModule
     val storeDataIn = Vec(StorePipelineWidth, Flipped(Valid(new MemExuOutput(isVector = true)))) // store data, send to sq from rs
     val storeMaskIn = Vec(StorePipelineWidth, Flipped(Valid(new StoreMaskBundle))) // store mask, send to sq from rs
     val sbuffer = Vec(EnsbufferWidth, Decoupled(new DCacheWordReqWithVaddrAndPfFlag)) // write committed store to sbuffer
-    val sbufferVecDifftestInfo = Vec(EnsbufferWidth, Decoupled(new ToSbufferDifftestInfoBundle)) // The vector store difftest needs is, write committed store to sbuffer
     val uncacheOutstanding = Input(Bool())
     val cmoOpReq  = DecoupledIO(new CMOReq)
     val cmoOpResp = Flipped(DecoupledIO(new CMOResp))
@@ -201,7 +200,7 @@ class StoreQueue(implicit p: Parameters) extends XSModule
     val force_write = Output(Bool())
     val maControl   = Flipped(new StoreMaBufToSqControlIO)
     val wfi = Flipped(new WfiReqBundle)
-    val generateFromSBuffer = Input(new GenerateInfoFromSBuffer)
+    val diffStore = Flipped(new DiffStoreIO)
   })
 
   println("StoreQueue: size:" + StoreQueueSize)
@@ -233,7 +232,6 @@ class StoreQueue(implicit p: Parameters) extends XSModule
   ))
   vaddrModule.io := DontCare
   val dataBuffer = Module(new DatamoduleResultBuffer(new DataBufferEntry))
-  val difftestBuffer = if (env.EnableDifftest) Some(Module(new DatamoduleResultBuffer(new ToSbufferDifftestInfoBundle))) else None
   val exceptionBuffer = Module(new StoreExceptionBuffer)
   exceptionBuffer.io.redirect := io.brqRedirect
   exceptionBuffer.io.exceptionAddr.isStore := DontCare
@@ -1303,16 +1301,7 @@ class StoreQueue(implicit p: Parameters) extends XSModule
   for (i <- 0 until EnsbufferWidth) {
     io.sbuffer(i).valid := dataBuffer.io.deq(i).valid
     dataBuffer.io.deq(i).ready := io.sbuffer(i).ready
-    io.sbuffer(i).bits := DontCare
-    io.sbuffer(i).bits.cmd   := MemoryOpConstants.M_XWR
-    io.sbuffer(i).bits.addr  := dataBuffer.io.deq(i).bits.addr
-    io.sbuffer(i).bits.vaddr := dataBuffer.io.deq(i).bits.vaddr
-    io.sbuffer(i).bits.data  := dataBuffer.io.deq(i).bits.data
-    io.sbuffer(i).bits.mask  := dataBuffer.io.deq(i).bits.mask
-    io.sbuffer(i).bits.wline := dataBuffer.io.deq(i).bits.wline && dataBuffer.io.deq(i).bits.vecValid
-    io.sbuffer(i).bits.prefetch := dataBuffer.io.deq(i).bits.prefetch
-    io.sbuffer(i).bits.vecValid := dataBuffer.io.deq(i).bits.vecValid
-    io.sbuffer(i).bits.sqNeedDeq := dataBuffer.io.deq(i).bits.sqNeedDeq
+    io.sbuffer(i).bits.fromDataBufferEntry(dataBuffer.io.deq(i).bits, MemoryOpConstants.M_XWR)
     // io.sbuffer(i).fire is RegNexted, as sbuffer data write takes 2 cycles.
     // Before data write finish, sbuffer is unable to provide store to load
     // forward data. As an workaround, deqPtrExt and allocated flag update
@@ -1390,46 +1379,34 @@ class StoreQueue(implicit p: Parameters) extends XSModule
   // If an assert appears and you confirm that it is not a Bug: Increase the timeout or remove the assert.
   TimeOutAssert(vecExceptionFlag.valid, 3000, "vecExceptionFlag timeout, Plase check for bugs or add timeouts.")
 
+  /* difftest */
   // Initialize when unenabled difftest.
-  for (i <- 0 until EnsbufferWidth) {
-    io.sbufferVecDifftestInfo(i) := DontCare
-  }
+  io.diffStore := DontCare
   // Consistent with the logic above.
   // Only the vector store difftest required signal is separated from the rtl code.
   if (env.EnableDifftest) {
-    for (i <- 0 until EnsbufferWidth) {
-      val ptr = dataBuffer.io.enq(i).bits.sqPtr.value
-      difftestBuffer.get.io.enq(i).valid := dataBuffer.io.enq(i).valid
-      difftestBuffer.get.io.enq(i).bits.uop := uop(ptr)
-      difftestBuffer.get.io.enq(i).bits.start  := debug_vec_unaligned_start(ptr)
-      difftestBuffer.get.io.enq(i).bits.offset := debug_vec_unaligned_offset(ptr)
-    }
-    for (i <- 0 until EnsbufferWidth) {
-      io.sbufferVecDifftestInfo(i).valid := difftestBuffer.get.io.deq(i).valid
-      difftestBuffer.get.io.deq(i).ready := io.sbufferVecDifftestInfo(i).ready
-
-      io.sbufferVecDifftestInfo(i).bits := difftestBuffer.get.io.deq(i).bits
-    }
-
     // commit cbo.inval to difftest
     val cmoInvalEvent = DifftestModule(new DiffCMOInvalEvent)
     cmoInvalEvent.coreid := io.hartId
-    cmoInvalEvent.valid  := io.mmioStout.fire && deqCanDoCbo && LSUOpType.isCboInval(uop(deqPtr).fuOpType)
-    cmoInvalEvent.addr   := cboMmioAddr
-
-    // the event that nc store to main memory
-    val ncmmStoreEvent = DifftestModule(new DiffStoreEvent, delay = 2, dontCare = true)
-    val dataMask = Cat((0 until DCacheWordBytes).reverse.map(i => Fill(8, ncReq.bits.mask(i))))
-    ncmmStoreEvent.coreid := io.hartId
-    ncmmStoreEvent.index := io.generateFromSBuffer.diffStoreEventCount
-    ncmmStoreEvent.valid := ncReq.fire && ncReq.bits.memBackTypeMM
-    ncmmStoreEvent.addr := Cat(ncReq.bits.addr(PAddrBits-1, DCacheWordOffset), 0.U(DCacheWordOffset.W)) // aligned to 8 bytes
-    ncmmStoreEvent.data := ncReq.bits.data & dataMask // data align
-    ncmmStoreEvent.mask := ncReq.bits.mask
-    ncmmStoreEvent.pc := uop(rptr0).pc
-    ncmmStoreEvent.robidx := uop(rptr0).robIdx.value
+    cmoInvalEvent.valid := io.mmioStout.fire && deqCanDoCbo && LSUOpType.isCboInval(uop(deqPtr).fuOpType)
+    cmoInvalEvent.addr := cboMmioAddr
+
+    // DiffStoreEvent happens when rdataPtr moves.
+    // That is, pmsStore enter dataBuffer or ncStore enter Ubuffer
+    (0 until EnsbufferWidth).foreach { i =>
+      // when i = 0, the sqPtr is rdataPtr(0), which is rdataPtrExt(0), so it applies to NC as well.
+      val ptr = dataBuffer.io.enq(i).bits.sqPtr.value
+      io.diffStore.diffInfo(i).uop := uop(ptr)
+      io.diffStore.diffInfo(i).start := debug_vec_unaligned_start(ptr)
+      io.diffStore.diffInfo(i).offset := debug_vec_unaligned_offset(ptr)
+      io.diffStore.pmaStore(i).valid := dataBuffer.io.enq(i).fire
+      io.diffStore.pmaStore(i).bits.fromDataBufferEntry(dataBuffer.io.enq(i).bits, MemoryOpConstants.M_XWR)
+    }
+    io.diffStore.ncStore.valid := ncReq.fire && ncReq.bits.memBackTypeMM
+    io.diffStore.ncStore.bits := ncReq.bits
   }
 
+
   (1 until EnsbufferWidth).foreach(i => when(io.sbuffer(i).fire) { assert(io.sbuffer(i - 1).fire) })
   if (coreParams.dcacheParametersOpt.isEmpty) {
     for (i <- 0 until EnsbufferWidth) {
diff --git a/src/main/scala/xiangshan/mem/sbuffer/Sbuffer.scala b/src/main/scala/xiangshan/mem/sbuffer/Sbuffer.scala
index 3fed5a0f4ce..1e07b8889c9 100644
--- a/src/main/scala/xiangshan/mem/sbuffer/Sbuffer.scala
+++ b/src/main/scala/xiangshan/mem/sbuffer/Sbuffer.scala
@@ -194,7 +194,6 @@ class Sbuffer(implicit p: Parameters)
   val io = IO(new Bundle() {
     val hartId = Input(UInt(hartIdLen.W))
     val in = Vec(EnsbufferWidth, Flipped(Decoupled(new DCacheWordReqWithVaddrAndPfFlag)))  //Todo: store logic only support Width == 2 now
-    val vecDifftestInfo = Vec(EnsbufferWidth, Flipped(Decoupled(new ToSbufferDifftestInfoBundle)))
     val dcache = Flipped(new DCacheToSbufferIO)
     val forward = Vec(LoadPipelineWidth, Flipped(new LoadForwardQueryIO))
     val sqempty = Input(Bool())
@@ -204,8 +203,7 @@ class Sbuffer(implicit p: Parameters)
     val store_prefetch = Vec(StorePipelineWidth, DecoupledIO(new StorePrefetchReq)) // to dcache
     val memSetPattenDetected = Input(Bool())
     val force_write = Input(Bool())
-
-    val generateToSQ = Output(new GenerateInfoFromSBuffer)
+    val diffStore = Input(new DiffStoreIO)
   })
 
   val dataModule = Module(new SbufferData)
@@ -878,13 +876,10 @@ class Sbuffer(implicit p: Parameters)
   *                     --------------                     *
   **********************************************************
   */
-  // Initialize when unenabled difftest.
-  for (i <- 0 until EnsbufferWidth) {
-    io.vecDifftestInfo(i) := DontCare
-  }
 
-  io.generateToSQ.diffStoreEventCount := 0.U // initializtion
+  io.diffStore := DontCare
   if (env.EnableDifftest) {
+    var diffStoreEventCount: Int = 0
     val VecMemFLOWMaxNumber = 16
     val WlineMaxNumber = blockWords
 
@@ -906,17 +901,14 @@ class Sbuffer(implicit p: Parameters)
     // To align with 'nemu', we need:
     //  For 'unit-store' and 'whole' vector store instr, we re-split here,
     //  and for the res, we do nothing.
-    var diffStoreEventCount: Int = 0
-    io.generateToSQ.diffStoreEventCount := diffStoreEventCount.U
     for (i <- 0 until EnsbufferWidth) {
-      io.vecDifftestInfo(i).ready := io.in(i).ready
 
-      val uop              = io.vecDifftestInfo(i).bits.uop
+      val uop              = io.diffStore.diffInfo(i).uop
 
-      val unaligned_start       = io.vecDifftestInfo(i).bits.start
-      val unaligned_offset      = io.vecDifftestInfo(i).bits.offset
-      val unaligned_start_bits  = (io.vecDifftestInfo(i).bits.start << 3.U).asUInt
-      val unaligned_offset_bits = (io.vecDifftestInfo(i).bits.offset << 3.U).asUInt
+      val unaligned_start       = io.diffStore.diffInfo(i).start
+      val unaligned_offset      = io.diffStore.diffInfo(i).offset
+      val unaligned_start_bits  = (io.diffStore.diffInfo(i).start << 3.U).asUInt
+      val unaligned_offset_bits = (io.diffStore.diffInfo(i).offset << 3.U).asUInt
 
       val isVse           = isVStore(uop.fuType) && LSUOpType.isUStride(uop.fuOpType)
       val isVsm           = isVStore(uop.fuType) && VstuType.isMasked(uop.fuOpType)
@@ -931,7 +923,7 @@ class Sbuffer(implicit p: Parameters)
 
       val isSegment       = nf =/= 0.U && !isVsm
       val isVSLine        = (isVse || isVsm || isVsr) && !isSegment
-      val isWline         = io.in(i).bits.wline
+      val isWline         = io.diffStore.pmaStore(i).bits.wline
 
       // The number of stores generated by a uop theroy.
       // No other vector instructions need to be considered.
@@ -941,9 +933,9 @@ class Sbuffer(implicit p: Parameters)
                               0.U
                             )
 
-      val rawData         = io.in(i).bits.data
-      val rawMask         = io.in(i).bits.mask
-      val rawAddr         = io.in(i).bits.addr
+      val rawData         = io.diffStore.pmaStore(i).bits.data
+      val rawMask         = io.diffStore.pmaStore(i).bits.mask
+      val rawAddr         = io.diffStore.pmaStore(i).bits.addr
 
       // A common difftest interface for scalar and vector instr
       val difftestCommon = DifftestModule(new DiffStoreEvent, delay = 2, dontCare = true)
@@ -955,7 +947,7 @@ class Sbuffer(implicit p: Parameters)
                                    ((EEB + unaligned_offset) << 3.U).asUInt - 1.U)// unit-stride second write request
         val splitMask         = UIntSlice(rawMask, upper, unaligned_start)(7,0)  // Byte
         val splitData         = UIntSlice(rawData, upperBits, unaligned_start_bits)(63,0) // Double word
-        val storeCommit       = io.in(i).fire && splitMask.orR && io.in(i).bits.vecValid
+        val storeCommit       = io.diffStore.pmaStore(i).fire && splitMask.orR && io.diffStore.pmaStore(i).bits.vecValid
         // align with ref
         val waddr             = Mux(unaligned_offset =/= 0.U && rawAddr(3), ZeroExt(Cat(rawAddr(PAddrBits - 1, 3), 0.U(3.W)), 64), rawAddr)
         val wmask             = Mux(unaligned_offset =/= 0.U && rawAddr(3), 0.U, splitMask << unaligned_offset)
@@ -967,45 +959,45 @@ class Sbuffer(implicit p: Parameters)
         difftestCommon.addr   := waddr
         difftestCommon.data   := wdata
         difftestCommon.mask   := wmask
-        difftestCommon.robidx := io.vecDifftestInfo(i).bits.uop.robIdx.value
-        difftestCommon.pc     := io.vecDifftestInfo(i).bits.uop.pc
+        difftestCommon.robidx := io.diffStore.diffInfo(i).uop.robIdx.value
+        difftestCommon.pc     := io.diffStore.diffInfo(i).uop.pc
 
       } .elsewhen (!isWline) {
-        val storeCommit       = io.in(i).fire
-        val waddr             = ZeroExt(Cat(io.in(i).bits.addr(PAddrBits - 1, 3), 0.U(3.W)), 64)
-        val sbufferMask       = shiftMaskToLow(io.in(i).bits.addr, io.in(i).bits.mask)
-        val sbufferData       = shiftDataToLow(io.in(i).bits.addr, io.in(i).bits.data)
+        val storeCommit       = io.diffStore.pmaStore(i).fire
+        val waddr             = ZeroExt(Cat(io.diffStore.pmaStore(i).bits.addr(PAddrBits - 1, 3), 0.U(3.W)), 64)
+        val sbufferMask       = shiftMaskToLow(io.diffStore.pmaStore(i).bits.addr, io.diffStore.pmaStore(i).bits.mask)
+        val sbufferData       = shiftDataToLow(io.diffStore.pmaStore(i).bits.addr, io.diffStore.pmaStore(i).bits.data)
         val wmask             = sbufferMask
         val wdata             = sbufferData & MaskExpand(sbufferMask)
 
         difftestCommon.coreid := io.hartId
         difftestCommon.index  := (i*VecMemFLOWMaxNumber).U
-        difftestCommon.valid  := storeCommit && io.in(i).bits.vecValid
+        difftestCommon.valid  := storeCommit && io.diffStore.pmaStore(i).bits.vecValid
         difftestCommon.addr   := waddr
         difftestCommon.data   := wdata
         difftestCommon.mask   := wmask
-        difftestCommon.robidx := io.vecDifftestInfo(i).bits.uop.robIdx.value
-        difftestCommon.pc     := io.vecDifftestInfo(i).bits.uop.pc
+        difftestCommon.robidx := io.diffStore.diffInfo(i).uop.robIdx.value
+        difftestCommon.pc     := io.diffStore.diffInfo(i).uop.pc
       }
 
       for (index <- 0 until WlineMaxNumber) {
         val difftest = DifftestModule(new DiffStoreEvent, delay = 2, dontCare = true)
         diffStoreEventCount += 1
 
-        val storeCommit = io.in(i).fire && io.in(i).bits.vecValid
-        val blockAddr = get_block_addr(io.in(i).bits.addr)
+        val storeCommit = io.diffStore.pmaStore(i).fire && io.diffStore.pmaStore(i).bits.vecValid
+        val blockAddr = get_block_addr(io.diffStore.pmaStore(i).bits.addr)
 
         when (isWline) {
           difftest.coreid := io.hartId
           difftest.index  := (i*VecMemFLOWMaxNumber + index).U
           difftest.valid  := storeCommit
           difftest.addr   := blockAddr + (index.U << wordOffBits)
-          difftest.data   := io.in(i).bits.data
+          difftest.data   := io.diffStore.pmaStore(i).bits.data
           difftest.mask   := ((1 << wordBytes) - 1).U
-          difftest.robidx := io.vecDifftestInfo(i).bits.uop.robIdx.value
-          difftest.pc     := io.vecDifftestInfo(i).bits.uop.pc
+          difftest.robidx := io.diffStore.diffInfo(i).uop.robIdx.value
+          difftest.pc     := io.diffStore.diffInfo(i).uop.pc
 
-          assert(!storeCommit || (io.in(i).bits.data === 0.U), "wline only supports whole zero write now")
+          assert(!storeCommit || (io.diffStore.pmaStore(i).bits.data === 0.U), "wline only supports whole zero write now")
         }
       }
 
@@ -1026,7 +1018,7 @@ class Sbuffer(implicit p: Parameters)
           val shiftBits   = shiftBytes << 3.U
           val splitMask   = UIntSlice(rawMask, (EEB*(index+1).U - 1.U) + unaligned_offset, EEB*index.U + unaligned_offset)(7,0)  // Byte
           val splitData   = UIntSlice(rawData, (EEWBits*(index+1).U - 1.U) + unaligned_offset_bits, EEWBits*index.U + unaligned_offset_bits)(63,0) // Double word
-          val storeCommit = io.in(i).fire && splitMask.orR  && io.in(i).bits.vecValid
+          val storeCommit = io.diffStore.pmaStore(i).fire && splitMask.orR  && io.diffStore.pmaStore(i).bits.vecValid
           val waddr       = Mux(unaligned_offset =/= 0.U && shiftIndex(3), Cat(rawAddr(PAddrBits - 1, 4),  0.U(4.W)),Cat(rawAddr(PAddrBits - 1, 4), Cat(shiftIndex(3), 0.U(3.W))))
           val wmask       = Mux(unaligned_offset =/= 0.U && shiftIndex(3), 0.U,splitMask << (shiftBytes + unaligned_offset))
           val wdata       = Mux(unaligned_offset =/= 0.U && shiftIndex(3), 0.U,(splitData & MaskExpand(splitMask)) << (shiftBits.asUInt + unaligned_offset_bits))
@@ -1037,14 +1029,27 @@ class Sbuffer(implicit p: Parameters)
           difftest.addr   := waddr
           difftest.data   := wdata
           difftest.mask   := wmask
-          difftest.robidx := io.vecDifftestInfo(i).bits.uop.robIdx.value
-          difftest.pc     := io.vecDifftestInfo(i).bits.uop.pc
+          difftest.robidx := io.diffStore.diffInfo(i).uop.robIdx.value
+          difftest.pc     := io.diffStore.diffInfo(i).uop.pc
         }
       }
     }
-    println("SBuffer: diffStoreEventCount = " + diffStoreEventCount)
+    println("PMA Store: diffStoreEventCount = " + diffStoreEventCount)
+
+    // the event that nc store to main memory
+    val ncmmStoreEvent = DifftestModule(new DiffStoreEvent, delay = 2, dontCare = true)
+    val dataMask = Cat((0 until DCacheWordBytes).reverse.map(i => Fill(8, io.diffStore.ncStore.bits.mask(i))))
+    ncmmStoreEvent.coreid := io.hartId
+    ncmmStoreEvent.index := diffStoreEventCount.U
+    ncmmStoreEvent.valid := io.diffStore.ncStore.valid && io.diffStore.ncStore.bits.memBackTypeMM
+    ncmmStoreEvent.addr := Cat(io.diffStore.ncStore.bits.addr(PAddrBits - 1, DCacheWordOffset), 0.U(DCacheWordOffset.W)) // aligned to 8 bytes
+    ncmmStoreEvent.data := io.diffStore.ncStore.bits.data & dataMask // data align
+    ncmmStoreEvent.mask := io.diffStore.ncStore.bits.mask
+    ncmmStoreEvent.pc := io.diffStore.diffInfo(0).uop.pc
+    ncmmStoreEvent.robidx := io.diffStore.diffInfo(0).uop.robIdx.value
   }
 
+
   val perf_valid_entry_count = RegNext(PopCount(VecInit(stateVec.map(s => !s.isInvalid())).asUInt))
   XSPerfHistogram("util", perf_valid_entry_count, true.B, 0, StoreBufferSize, 1)
   XSPerfAccumulate("sbuffer_req_valid", PopCount(VecInit(io.in.map(_.valid)).asUInt))
```
