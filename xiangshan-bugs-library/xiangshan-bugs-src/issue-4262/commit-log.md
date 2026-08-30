# Commit Log
- Issue: #4262
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/4262
- Issue state: closed
- Tested RTL commit: -
- Related PR: #4262
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/4262
- Changed files: 11
- Additions: 142
- Deletions: 30

## Files
- `ready-to-run`
- `src/main/scala/xiangshan/backend/fu/NewCSR/Debug.scala`
- `src/main/scala/xiangshan/mem/MemBlock.scala`
- `src/main/scala/xiangshan/mem/lsqueue/LSQWrapper.scala`
- `src/main/scala/xiangshan/mem/lsqueue/LoadQueueData.scala`
- `src/main/scala/xiangshan/mem/lsqueue/LoadQueueRAR.scala`
- `src/main/scala/xiangshan/mem/lsqueue/LoadQueueRAW.scala`
- `src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala`
- `src/main/scala/xiangshan/mem/pipeline/StoreUnit.scala`
- `src/main/scala/xiangshan/mem/vector/VecCommon.scala`
- `src/main/scala/xiangshan/package.scala`

## Diff
```diff
diff --git a/ready-to-run b/ready-to-run
index c8c11d58ed9..5cd30c9d26c 160000
--- a/ready-to-run
+++ b/ready-to-run
@@ -1 +1 @@
-Subproject commit c8c11d58ed9f3389874665673b29fad26a6be791
+Subproject commit 5cd30c9d26c12083625482e92abd08d0dbb44640
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/Debug.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/Debug.scala
index 057e6341f57..20453e91f2e 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/Debug.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/Debug.scala
@@ -3,10 +3,12 @@ package xiangshan.backend.fu.NewCSR
 import chisel3._
 import chisel3.util._
 import org.chipsalliance.cde.config.Parameters
+import xiangshan.cache.HasDCacheParameters
 import xiangshan.backend.fu.NewCSR.CSRBundles.PrivState
 import xiangshan.backend.fu.util.CSRConst
 import xiangshan.backend.fu.util.SdtrigExt
 import xiangshan._
+import utils._
 
 class Debug(implicit val p: Parameters) extends Module with HasXSParameter {
   val io = IO(new DebugIO)
@@ -213,11 +215,12 @@ class BaseTriggerIO(implicit p: Parameters) extends XSBundle{
 }
 
 
-abstract class BaseTrigger()(implicit val p: Parameters) extends Module with HasXSParameter with SdtrigExt {
+abstract class BaseTrigger()(implicit val p: Parameters) extends Module with HasXSParameter with SdtrigExt with HasDCacheParameters {
   lazy val io = IO(new BaseTriggerIO)
 
   def getTriggerHitVec(): Vec[Bool]
   def highBitsEq(): Vec[Bool]
+  def DcacheLineBitsEq(): (Bool, Vec[Bool])
 
   val tdataVec      = io.fromCsrTrigger.tdataVec
   val tEnableVec    = io.fromCsrTrigger.tEnableVec
@@ -236,6 +239,8 @@ abstract class BaseTrigger()(implicit val p: Parameters) extends Module with Has
   val isVectorStride = io.fromLoadStore.isVectorUnitStride
   val mask = io.fromLoadStore.mask
 
+  val (isCacheLine, cacheLineEq) = DcacheLineBitsEq()
+
   val highEq = highBitsEq()
 
   val lowMatch = tdataVec.map(tdata => UIntToOH(tdata.tdata2(lowBitWidth-1, 0)) & mask)
@@ -243,7 +248,8 @@ abstract class BaseTrigger()(implicit val p: Parameters) extends Module with Has
 
   val hitVecVectorStride  = VecInit(highEq.zip(lowEq).map{case(hi, lo) => hi && lo})
 
-  TriggerCheckCanFire(TriggerNum, triggerCanFireVec, Mux(isVectorStride, hitVecVectorStride, triggerHitVec), triggerTimingVec, triggerChainVec)
+  val tiggerVaddrHit = Mux(isCacheLine, cacheLineEq, Mux(isVectorStride, hitVecVectorStride, triggerHitVec))
+  TriggerCheckCanFire(TriggerNum, triggerCanFireVec, tiggerVaddrHit, triggerTimingVec, triggerChainVec)
   val triggerFireOH = PriorityEncoderOH(triggerCanFireVec)
   val triggerVaddr  = PriorityMux(triggerFireOH, VecInit(tdataVec.map(_.tdata2))).asUInt
   val triggerMask   = PriorityMux(triggerFireOH, VecInit(tdataVec.map(x => UIntToOH(x.tdata2(lowBitWidth-1, 0))))).asUInt
@@ -260,6 +266,12 @@ abstract class BaseTrigger()(implicit val p: Parameters) extends Module with Has
 
 class MemTrigger(memType: Boolean = MemType.LOAD)(override implicit val p: Parameters) extends BaseTrigger {
 
+  class MemTriggerIO extends BaseTriggerIO{
+    val isCbo = OptionWrapper(memType == MemType.STORE, Input(Bool()))
+  }
+
+  override lazy val io = IO(new MemTriggerIO)
+
   override def getTriggerHitVec(): Vec[Bool] = {
     val triggerHitVec = WireInit(VecInit(Seq.fill(TriggerNum)(false.B)))
     for (i <- 0 until TriggerNum) {
@@ -280,6 +292,18 @@ class MemTrigger(memType: Boolean = MemType.LOAD)(override implicit val p: Param
         (vaddr >> lowBitWidth) === (tdata.tdata2 >> lowBitWidth)
     })
   }
+
+  def DcacheLineBitsEq(): (Bool, Vec[Bool])= {
+    (
+    io.isCbo.getOrElse(false.B),
+    VecInit(tdataVec.zip(tEnableVec).map{ case(tdata, en) =>
+      !tdata.select && !debugMode && en &&
+        tdata.store && io.isCbo.getOrElse(false.B) &&
+        (vaddr >> DCacheLineOffset) === (tdata.tdata2 >> DCacheLineOffset)
+    })
+    )
+  }
+
 }
 
 class VSegmentTrigger(override implicit val p: Parameters) extends BaseTrigger {
@@ -310,4 +334,9 @@ class VSegmentTrigger(override implicit val p: Parameters) extends BaseTrigger {
         (vaddr >> lowBitWidth) === (tdata.tdata2 >> lowBitWidth)
     })
   }
+
+  // vector segment does not have a cbo
+  def DcacheLineBitsEq(): (Bool, Vec[Bool]) = {
+    (false.B, VecInit(Seq.fill(tdataVec.length)(false.B)))
+  }
 }
\ No newline at end of file
diff --git a/src/main/scala/xiangshan/mem/MemBlock.scala b/src/main/scala/xiangshan/mem/MemBlock.scala
index 2784c2ba747..30876bc263d 100644
--- a/src/main/scala/xiangshan/mem/MemBlock.scala
+++ b/src/main/scala/xiangshan/mem/MemBlock.scala
@@ -1291,25 +1291,35 @@ class MemBlockInlinedImp(outer: MemBlockInlined) extends LazyModuleImp(outer)
     stu.io.vec_isFirstIssue := true.B // TODO
   }
 
-  // mmio store writeback will use store writeback port 0
-  val mmioStout = WireInit(0.U.asTypeOf(lsq.io.mmioStout))
+  val sqOtherStout = WireInit(0.U.asTypeOf(DecoupledIO(new MemExuOutput)))
+  sqOtherStout.valid := lsq.io.mmioStout.valid || lsq.io.cboZeroStout.valid
+  sqOtherStout.bits  := Mux(lsq.io.cboZeroStout.valid, lsq.io.cboZeroStout.bits, lsq.io.mmioStout.bits)
+  assert(!(lsq.io.mmioStout.valid && lsq.io.cboZeroStout.valid), "Cannot writeback to mmio and cboZero at the same time.")
+
+  // Store writeback by StoreQueue:
+  //   1. cbo Zero
+  //   2. mmio
+  // Currently, the two should not be present at the same time, so simply make cbo zero a higher priority.
+  val otherStout = WireInit(0.U.asTypeOf(lsq.io.mmioStout))
   NewPipelineConnect(
-    lsq.io.mmioStout, mmioStout, mmioStout.fire,
+    sqOtherStout, otherStout, otherStout.fire,
     false.B,
-    Option("mmioStOutConnect")
+    Option("otherStoutConnect")
   )
-  mmioStout.ready := false.B
-  when (mmioStout.valid && !storeUnits(0).io.stout.valid) {
+  otherStout.ready := false.B
+  when (otherStout.valid && !storeUnits(0).io.stout.valid) {
     stOut(0).valid := true.B
-    stOut(0).bits  := mmioStout.bits
-    mmioStout.ready := true.B
+    stOut(0).bits  := otherStout.bits
+    otherStout.ready := true.B
   }
+  lsq.io.mmioStout.ready := sqOtherStout.ready
+  lsq.io.cboZeroStout.ready := sqOtherStout.ready
 
   // vec mmio writeback
   lsq.io.vecmmioStout.ready := false.B
 
   // miss align buffer will overwrite stOut(0)
-  val storeMisalignCanWriteBack = !mmioStout.valid && !storeUnits(0).io.stout.valid && !storeUnits(0).io.vecstout.valid
+  val storeMisalignCanWriteBack = !otherStout.valid && !storeUnits(0).io.stout.valid && !storeUnits(0).io.vecstout.valid
   storeMisalignBuffer.io.writeBack.ready := storeMisalignCanWriteBack
   storeMisalignBuffer.io.storeOutValid := storeUnits(0).io.stout.valid
   storeMisalignBuffer.io.storeVecOutValid := storeUnits(0).io.vecstout.valid
diff --git a/src/main/scala/xiangshan/mem/lsqueue/LSQWrapper.scala b/src/main/scala/xiangshan/mem/lsqueue/LSQWrapper.scala
index 17ee00cd3fb..14d9bbdc443 100644
--- a/src/main/scala/xiangshan/mem/lsqueue/LSQWrapper.scala
+++ b/src/main/scala/xiangshan/mem/lsqueue/LSQWrapper.scala
@@ -101,6 +101,7 @@ class LsqWrapper(implicit p: Parameters) extends XSModule with HasDCacheParamete
     val uncacheOutstanding = Input(Bool())
     val uncache = new UncacheWordIO
     val mmioStout = DecoupledIO(new MemExuOutput) // writeback uncached store
+    val cboZeroStout = DecoupledIO(new MemExuOutput)
     // TODO: implement vector store
     val vecmmioStout = DecoupledIO(new MemExuOutput(isVector = true)) // vec writeback uncached store
     val sqEmpty = Output(Bool())
@@ -178,6 +179,7 @@ class LsqWrapper(implicit p: Parameters) extends XSModule with HasDCacheParamete
   storeQueue.io.sbuffer     <> io.sbuffer
   storeQueue.io.sbufferVecDifftestInfo <> io.sbufferVecDifftestInfo
   storeQueue.io.mmioStout   <> io.mmioStout
+  storeQueue.io.cboZeroStout <> io.cboZeroStout
   storeQueue.io.vecmmioStout <> io.vecmmioStout
   storeQueue.io.rob         <> io.rob
   storeQueue.io.exceptionAddr.isStore := DontCare
diff --git a/src/main/scala/xiangshan/mem/lsqueue/LoadQueueData.scala b/src/main/scala/xiangshan/mem/lsqueue/LoadQueueData.scala
index b3faaaa33fa..2bfae5e6862 100644
--- a/src/main/scala/xiangshan/mem/lsqueue/LoadQueueData.scala
+++ b/src/main/scala/xiangshan/mem/lsqueue/LoadQueueData.scala
@@ -29,7 +29,16 @@ import utility._
 
 // Data module define
 // These raw data modules are like SyncDataModuleTemplate, but support cam-like ops
-abstract class LqRawDataModule[T <: Data] (gen: T, numEntries: Int, numRead: Int, numWrite: Int, numWBank: Int, numWDelay: Int, numCamPort: Int = 0)(implicit p: Parameters) extends XSModule {
+abstract class LqRawDataModule[T <: Data] (
+  gen: T,
+  numEntries: Int,
+  numRead: Int,
+  numWrite: Int,
+  numWBank: Int,
+  numWDelay: Int,
+  numCamPort: Int = 0,
+  enableCacheLineCheck: Boolean = false
+)(implicit p: Parameters) extends XSModule {
   val io = IO(new Bundle() {
     val ren   = Input(Vec(numRead, Bool()))
     val raddr = Input(Vec(numRead, UInt(log2Up(numEntries).W)))
@@ -39,6 +48,8 @@ abstract class LqRawDataModule[T <: Data] (gen: T, numEntries: Int, numRead: Int
     val wdata = Input(Vec(numWrite, gen))
     // violation cam: hit if addr is in the same cacheline
     val violationMdata = Input(Vec(numCamPort, gen)) // addr
+    // This `store` writes the whole `cacheline`.(cbo zero).
+    val violationCheckLine = OptionWrapper(enableCacheLineCheck, Input(Vec(numCamPort, Bool())))
     val violationMmask = Output(Vec(numCamPort, Vec(numEntries, Bool()))) // cam result mask
     // refill cam: hit if addr is in the same cacheline
     val releaseMdata = Input(Vec(numCamPort, gen))
@@ -55,6 +66,7 @@ abstract class LqRawDataModule[T <: Data] (gen: T, numEntries: Int, numRead: Int
   require((numEntries % numWBank == 0), "numEntries must be divided by numWBank!")
 
   val numEntryPerBank = numEntries / numWBank
+  val dataWidth = gen.getWidth
 
   val data = Reg(Vec(numEntries, gen))
   // read ports
@@ -128,14 +140,26 @@ class LqPAddrModule[T <: UInt](
   numWrite: Int,
   numWBank: Int,
   numWDelay: Int = 1,
-  numCamPort: Int = 1)(implicit p: Parameters) extends LqRawDataModule(gen, numEntries, numRead, numWrite, numWBank, numWDelay, numCamPort)
+  numCamPort: Int = 1,
+  enableCacheLineCheck: Boolean = false, // Check the entire cacheline. when enabled, set `paddrOffset` correctly.
+  paddrOffset: Int // The least significant `paddrOffset` bits of paddr are neglected.
+)(implicit p: Parameters) extends LqRawDataModule(gen, numEntries, numRead, numWrite, numWBank, numWDelay, numCamPort, enableCacheLineCheck)
   with HasDCacheParameters
 {
   // content addressed match
   // 128-bits aligned
+  val needCacheLineCheck = enableCacheLineCheck && DCacheLineOffset > paddrOffset
   for (i <- 0 until numCamPort) {
     for (j <- 0 until numEntries) {
-      io.violationMmask(i)(j) := io.violationMdata(i) === data(j)
+      if (needCacheLineCheck) {
+        val cacheLineOffset = DCacheLineOffset - paddrOffset
+        val cacheLineHit    = io.violationMdata(i)(dataWidth - 1, cacheLineOffset) === data(j)(dataWidth - 1, cacheLineOffset)
+        val lowAddrHit      = io.violationMdata(i)(cacheLineOffset - 1, 0) === data(j)(cacheLineOffset - 1, 0)
+        io.violationMmask(i)(j) := cacheLineHit && (io.violationCheckLine.get(i) || lowAddrHit)
+      } else {
+        io.violationMmask(i)(j) := io.violationMdata(i) === data(j)
+      }
+
     }
   }
 
diff --git a/src/main/scala/xiangshan/mem/lsqueue/LoadQueueRAR.scala b/src/main/scala/xiangshan/mem/lsqueue/LoadQueueRAR.scala
index 8bfcd3f646f..0f00f3bdb34 100644
--- a/src/main/scala/xiangshan/mem/lsqueue/LoadQueueRAR.scala
+++ b/src/main/scala/xiangshan/mem/lsqueue/LoadQueueRAR.scala
@@ -101,7 +101,9 @@ class LoadQueueRAR(implicit p: Parameters) extends XSModule
     numWrite = LoadPipelineWidth,
     numWBank = LoadQueueNWriteBanks,
     numWDelay = 2,
-    numCamPort = LoadPipelineWidth
+    numCamPort = LoadPipelineWidth,
+    enableCacheLineCheck = false, // Now `RARQueue` has no need to check cacheline.
+    paddrOffset = 0 // If you need to check cacheline, set the offset relative to the original paddr correctly.
   ))
   paddrModule.io := DontCare
   val released = RegInit(VecInit(List.fill(LoadQueueRARSize)(false.B)))
diff --git a/src/main/scala/xiangshan/mem/lsqueue/LoadQueueRAW.scala b/src/main/scala/xiangshan/mem/lsqueue/LoadQueueRAW.scala
index cc9304d00bf..c460c2fb3b6 100644
--- a/src/main/scala/xiangshan/mem/lsqueue/LoadQueueRAW.scala
+++ b/src/main/scala/xiangshan/mem/lsqueue/LoadQueueRAW.scala
@@ -55,8 +55,9 @@ class LoadQueueRAW(implicit p: Parameters) extends XSModule
   })
 
   private def PartialPAddrWidth: Int = 24
+  private def paddrOffset: Int = DCacheVWordOffset
   private def genPartialPAddr(paddr: UInt) = {
-    paddr(DCacheVWordOffset + PartialPAddrWidth - 1, DCacheVWordOffset)
+    paddr(DCacheVWordOffset + PartialPAddrWidth - 1, paddrOffset)
   }
 
   println("LoadQueueRAW: size " + LoadQueueRAWSize)
@@ -81,7 +82,9 @@ class LoadQueueRAW(implicit p: Parameters) extends XSModule
     numWrite = LoadPipelineWidth,
     numWBank = LoadQueueNWriteBanks,
     numWDelay = 2,
-    numCamPort = StorePipelineWidth
+    numCamPort = StorePipelineWidth,
+    enableCacheLineCheck = true,
+    paddrOffset = paddrOffset
   ))
   paddrModule.io := DontCare
   val maskModule = Module(new LqMaskModule(
@@ -287,6 +290,7 @@ class LoadQueueRAW(implicit p: Parameters) extends XSModule
 
   def detectRollback(i: Int) = {
     paddrModule.io.violationMdata(i) := genPartialPAddr(RegEnable(storeIn(i).bits.paddr, storeIn(i).valid))
+    paddrModule.io.violationCheckLine.get(i) := storeIn(i).bits.wlineflag
     maskModule.io.violationMdata(i) := RegEnable(storeIn(i).bits.mask, storeIn(i).valid)
 
     val addrMaskMatch = paddrModule.io.violationMmask(i).asUInt & maskModule.io.violationMmask(i).asUInt
diff --git a/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala b/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala
index 9bba68cce84..52c66eb836e 100644
--- a/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala
+++ b/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala
@@ -174,6 +174,7 @@ class StoreQueue(implicit p: Parameters) extends XSModule
     val uncacheOutstanding = Input(Bool())
     val cmoOpReq  = DecoupledIO(new CMOReq)
     val cmoOpResp = Flipped(DecoupledIO(new CMOResp))
+    val cboZeroStout = DecoupledIO(new MemExuOutput)
     val mmioStout = DecoupledIO(new MemExuOutput) // writeback uncached store
     val vecmmioStout = DecoupledIO(new MemExuOutput(isVector = true))
     val forward = Vec(LoadPipelineWidth, Flipped(new PipeLoadForwardQueryIO))
@@ -928,6 +929,23 @@ class StoreQueue(implicit p: Parameters) extends XSModule
   // as uncache op will not start in s_idle
   val cboMmioAddr = get_block_addr(cboMmioPAddr)
   val deqCanDoCbo = GatedRegNext(LSUOpType.isCbo(uop(deqPtr).fuOpType) && allocated(deqPtr) && addrvalid(deqPtr) && !hasException(deqPtr))
+
+  // RegNext(io.sbuffer(i).fire) is used to alignment timing
+  val isCboZeroToSbVec   = (0 until EnsbufferWidth).map{ i => RegNext(io.sbuffer(i).fire) && uop(deqPtrExt(i).value).fuOpType === LSUOpType.cbo_zero }
+  val cboZeroToSb        = isCboZeroToSbVec.reduce(_ || _)
+  val cboZeroFlushSb     = GatedRegNext(cboZeroToSb)
+
+  val cboZeroUop         = RegEnable(PriorityMux(isCboZeroToSbVec, deqPtrExt.map(x=>uop(x.value))), cboZeroToSb)
+  val cboZeroValid       = RegInit(false.B)
+  val cboZeroWaitFlushSb = RegInit(false.B)
+
+  assert(!(PopCount(isCboZeroToSbVec) > 1.U), "Multiple cbo zero instructions cannot be executed at the same time")
+
+  when (cboZeroToSb) {
+    cboZeroValid       := true.B
+    cboZeroWaitFlushSb := true.B
+  }
+
   when (deqCanDoCbo) {
     // disable uncache channel
     io.uncache.req.valid := false.B
@@ -949,7 +967,7 @@ class StoreQueue(implicit p: Parameters) extends XSModule
 
   io.cmoOpResp.ready := deqCanDoCbo && (mmioState === s_resp)
 
-  io.flushSbuffer.valid := deqCanDoCbo && !cboFlushedSb && (mmioState === s_req) && !io.flushSbuffer.empty
+  io.flushSbuffer.valid := deqCanDoCbo && !cboFlushedSb && (mmioState === s_req) && !io.flushSbuffer.empty || cboZeroFlushSb
 
   when(deqCanDoCbo && !cboFlushedSb && (mmioState === s_req) && io.flushSbuffer.empty) {
     cboFlushedSb := true.B
@@ -990,6 +1008,24 @@ class StoreQueue(implicit p: Parameters) extends XSModule
     allocated(deqPtr) := false.B
   }
 
+  // cbo Zero writeback to ROB
+  io.cboZeroStout.valid                := cboZeroValid && !cboZeroWaitFlushSb
+  io.cboZeroStout.bits.uop             := cboZeroUop
+  io.cboZeroStout.bits.data            := DontCare
+  io.cboZeroStout.bits.isFromLoadUnit  := DontCare
+  io.cboZeroStout.bits.debug.isMMIO    := false.B
+  io.cboZeroStout.bits.debug.isNC      := false.B
+  io.cboZeroStout.bits.debug.paddr     := DontCare
+  io.cboZeroStout.bits.debug.isPerfCnt := false.B
+  io.cboZeroStout.bits.debug.vaddr     := DontCare
+
+  when (cboZeroWaitFlushSb && io.flushSbuffer.empty) {
+    cboZeroWaitFlushSb    := false.B
+  }
+  when (io.cboZeroStout.fire) {
+    cboZeroValid := false.B
+  }
+
   exceptionBuffer.io.storeAddrIn.last.valid := io.mmioStout.fire
   exceptionBuffer.io.storeAddrIn.last.bits := DontCare
   exceptionBuffer.io.storeAddrIn.last.bits.fullva := vaddrModule.io.rdata.head
diff --git a/src/main/scala/xiangshan/mem/pipeline/StoreUnit.scala b/src/main/scala/xiangshan/mem/pipeline/StoreUnit.scala
index cd59756ab1b..ecce6da8278 100644
--- a/src/main/scala/xiangshan/mem/pipeline/StoreUnit.scala
+++ b/src/main/scala/xiangshan/mem/pipeline/StoreUnit.scala
@@ -147,20 +147,25 @@ class StoreUnit(implicit p: Parameters) extends XSModule
     )
   )
 
-    val s0_alignTpye = Mux(s0_use_flow_vec, s0_vecstin.alignedType(1,0), s0_uop.fuOpType(1, 0))
+  val s0_isCbo = s0_use_flow_rs && LSUOpType.isCboAll(s0_stin.uop.fuOpType)
+  // only simulation
+  val cbo_assert_flag = LSUOpType.isCboAll(s0_out.uop.fuOpType)
+  XSError(!s0_use_flow_rs && cbo_assert_flag && s0_valid, "cbo instruction selection error.")
+
+  val s0_alignType = Mux(s0_use_flow_vec, s0_vecstin.alignedType(1,0), s0_uop.fuOpType(1, 0))
   // exception check
-  val s0_addr_aligned = LookupTree(s0_alignTpye, List(
+  val s0_addr_aligned = LookupTree(s0_alignType, List(
     "b00".U   -> true.B,              //b
     "b01".U   -> (s0_vaddr(0) === 0.U),   //h
     "b10".U   -> (s0_vaddr(1,0) === 0.U), //w
     "b11".U   -> (s0_vaddr(2,0) === 0.U)  //d
-  ))
+  )) || s0_isCbo
   // if vector store sends 128-bit requests, its address must be 128-aligned
   XSError(s0_use_flow_vec && s0_vaddr(3, 0) =/= 0.U && s0_vecstin.alignedType(2), "unit stride 128 bit element is not aligned!")
 
   val s0_isMisalign = Mux(s0_use_non_prf_flow, (!s0_addr_aligned || s0_vecstin.uop.exceptionVec(storeAddrMisaligned) && s0_vecActive), false.B)
   val s0_addr_low = s0_vaddr(4, 0)
-  val s0_addr_Up_low = LookupTree(s0_alignTpye, List(
+  val s0_addr_Up_low = LookupTree(s0_alignType, List(
     "b00".U -> 0.U,
     "b01".U -> 1.U,
     "b10".U -> 3.U,
@@ -234,7 +239,7 @@ class StoreUnit(implicit p: Parameters) extends XSModule
   s0_out.uop          := s0_uop
   s0_out.miss         := false.B
   // For unaligned, we need to generate a base-aligned mask in storeunit and then do a shift split in StoreQueue.
-  s0_out.mask         := Mux(s0_rs_corss16Bytes && !s0_addr_aligned, genBasemask(s0_saddr,s0_alignTpye(1,0)), s0_mask)
+  s0_out.mask         := Mux(s0_rs_corss16Bytes && !s0_addr_aligned, genBasemask(s0_saddr,s0_alignType(1,0)), s0_mask)
   s0_out.isFirstIssue := s0_isFirstIssue
   s0_out.isHWPrefetch := s0_use_flow_prf
   s0_out.wlineflag    := s0_wlineflag
@@ -280,10 +285,7 @@ class StoreUnit(implicit p: Parameters) extends XSModule
   val s1_is128bit     = s1_in.is128bit
 
   // mmio cbo decoder
-  val s1_isCbo   = s1_in.uop.fuOpType === LSUOpType.cbo_clean ||
-                   s1_in.uop.fuOpType === LSUOpType.cbo_flush ||
-                   s1_in.uop.fuOpType === LSUOpType.cbo_inval ||
-                   s1_in.uop.fuOpType === LSUOpType.cbo_zero
+  val s1_isCbo   = RegEnable(s0_isCbo, s0_fire)
   val s1_vaNeedExt = io.tlb.resp.bits.excp(0).vaNeedExt
   val s1_isHyper   = io.tlb.resp.bits.excp(0).isHyper
   val s1_paddr     = io.tlb.resp.bits.paddr(0)
@@ -327,6 +329,7 @@ class StoreUnit(implicit p: Parameters) extends XSModule
   storeTrigger.io.fromLoadStore.vaddr                 := s1_in.vaddr
   storeTrigger.io.fromLoadStore.isVectorUnitStride    := s1_in.isvec && s1_in.is128bit
   storeTrigger.io.fromLoadStore.mask                  := s1_in.mask
+  storeTrigger.io.isCbo.get                           := s1_isCbo
 
   val s1_trigger_action = storeTrigger.io.toLoadStore.triggerAction
   val s1_trigger_debug_mode = TriggerAction.isDmode(s1_trigger_action)
@@ -547,7 +550,7 @@ class StoreUnit(implicit p: Parameters) extends XSModule
   val s3_exception     = RegEnable(s2_exception, s2_fire)
 
   // store misalign will not writeback to rob now
-  when (s2_fire) { s3_valid := (!s2_mmio && !s2_isCbo_noZero || s2_exception) && !s2_out.isHWPrefetch && !s2_mis_align && !s2_frm_mabuf }
+  when (s2_fire) { s3_valid := (!s2_mmio && !s2_isCbo || s2_exception) && !s2_out.isHWPrefetch && !s2_mis_align && !s2_frm_mabuf }
   .elsewhen (s3_fire) { s3_valid := false.B }
   .elsewhen (s3_kill) { s3_valid := false.B }
 
diff --git a/src/main/scala/xiangshan/mem/vector/VecCommon.scala b/src/main/scala/xiangshan/mem/vector/VecCommon.scala
index 22604fb1385..4e1e674a36a 100644
--- a/src/main/scala/xiangshan/mem/vector/VecCommon.scala
+++ b/src/main/scala/xiangshan/mem/vector/VecCommon.scala
@@ -723,7 +723,8 @@ object genVWmask128 {
       "b001".U -> 0x3.U, //0011
       "b010".U -> 0xf.U, //1111
       "b011".U -> 0xff.U, //11111111
-      "b100".U -> 0xffff.U //1111111111111111
+      "b100".U -> 0xffff.U, //1111111111111111
+      "b111".U -> 0xffff.U  //cbo
     )) << addr(3, 0)).asUInt
   }
 }
diff --git a/src/main/scala/xiangshan/package.scala b/src/main/scala/xiangshan/package.scala
index ef8f0ab9446..34559775629 100644
--- a/src/main/scala/xiangshan/package.scala
+++ b/src/main/scala/xiangshan/package.scala
@@ -589,6 +589,7 @@ package object xiangshan {
     def cbo_inval = "b1110".U
 
     def isCbo(op: UInt): Bool = op(3, 2) === "b11".U && (op(6, 4) === "b000".U)
+    def isCboAll(op: UInt): Bool = isCbo(op) || op(3,0) === cbo_zero
     def isCboClean(op: UInt): Bool = isCbo(op) && (op(3, 0) === cbo_clean)
     def isCboFlush(op: UInt): Bool = isCbo(op) && (op(3, 0) === cbo_flush)
     def isCboInval(op: UInt): Bool = isCbo(op) && (op(3, 0) === cbo_inval)
```
