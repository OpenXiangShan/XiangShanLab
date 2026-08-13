# Commit Log
- Issue: #4731
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/4731
- Issue state: closed
- Tested RTL commit: -
- Related PR: #4731
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/4731
- Changed files: 10
- Additions: 85
- Deletions: 30

## Files
- `src/main/scala/xiangshan/backend/Bundles.scala`
- `src/main/scala/xiangshan/mem/Bundles.scala`
- `src/main/scala/xiangshan/mem/MemBlock.scala`
- `src/main/scala/xiangshan/mem/lsqueue/LSQWrapper.scala`
- `src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala`
- `src/main/scala/xiangshan/mem/sbuffer/Sbuffer.scala`
- `src/main/scala/xiangshan/mem/vector/VMergeBuffer.scala`
- `src/main/scala/xiangshan/mem/vector/VSegmentUnit.scala`
- `src/main/scala/xiangshan/mem/vector/VSplit.scala`
- `src/main/scala/xiangshan/mem/vector/VecBundle.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/backend/Bundles.scala b/src/main/scala/xiangshan/backend/Bundles.scala
index d392341c7a9..2b7e0facd47 100644
--- a/src/main/scala/xiangshan/backend/Bundles.scala
+++ b/src/main/scala/xiangshan/backend/Bundles.scala
@@ -21,6 +21,7 @@ import xiangshan.backend.regfile.{RfReadPortWithConfig, RfWritePortWithConfig}
 import xiangshan.backend.rob.RobPtr
 import xiangshan.frontend._
 import xiangshan.mem.{LqPtr, SqPtr}
+import xiangshan.mem.{VecMissalignedDebugBundle}
 import yunsuan.vector.VIFuParam
 import xiangshan.backend.trace._
 import utility._
@@ -967,6 +968,7 @@ object Bundles {
     val vdIdxInField = if (isVector) Some(UInt(3.W)) else None
     val isFromLoadUnit = Bool()
     val debug = new DebugBundle
+    val vecDebug = if (isVector) Some(new VecMissalignedDebugBundle) else None
 
     def isVls = FuType.isVls(uop.fuType)
   }
diff --git a/src/main/scala/xiangshan/mem/Bundles.scala b/src/main/scala/xiangshan/mem/Bundles.scala
index 19bee84a186..38de8ff4c2f 100644
--- a/src/main/scala/xiangshan/mem/Bundles.scala
+++ b/src/main/scala/xiangshan/mem/Bundles.scala
@@ -364,3 +364,16 @@ object Bundles {
   }
 
 }
+
+// for vector difftest store event
+class ToSbufferDifftestInfoBundle(implicit p: Parameters) extends XSBundle{
+  val uop        = new DynInst
+  val start      = UInt(log2Up(XLEN).W) // indicate first byte position of first unit-stride's element when unaligned
+  val offset     = UInt(log2Up(XLEN).W) // indicate byte offset of unit-stride's element when unaligned
+}
+
+
+class VecMissalignedDebugBundle (implicit p: Parameters) extends XSBundle {
+  val start      = UInt(log2Up(XLEN).W) // indicate first byte position of first unit-stride's element when unaligned
+  val offset     = UInt(log2Up(XLEN).W) // indicate byte offset of unit-stride's element when unaligned
+}
\ No newline at end of file
diff --git a/src/main/scala/xiangshan/mem/MemBlock.scala b/src/main/scala/xiangshan/mem/MemBlock.scala
index 2c695d3f90c..4537296a1e7 100644
--- a/src/main/scala/xiangshan/mem/MemBlock.scala
+++ b/src/main/scala/xiangshan/mem/MemBlock.scala
@@ -1281,6 +1281,7 @@ class MemBlockInlinedImp(outer: MemBlockInlined) extends LazyModuleImp(outer)
         lsq.io.std.storeDataIn(i).bits.mask.map(_ := 0.U)
         lsq.io.std.storeDataIn(i).bits.vdIdx.map(_ := 0.U)
         lsq.io.std.storeDataIn(i).bits.vdIdxInField.map(_ := 0.U)
+        lsq.io.std.storeDataIn(i).bits.vecDebug.map(_ := DontCare)
         stData(i).ready := true.B
       }
     } else {
@@ -1290,6 +1291,7 @@ class MemBlockInlinedImp(outer: MemBlockInlined) extends LazyModuleImp(outer)
         lsq.io.std.storeDataIn(i).bits.mask.map(_ := 0.U)
         lsq.io.std.storeDataIn(i).bits.vdIdx.map(_ := 0.U)
         lsq.io.std.storeDataIn(i).bits.vdIdxInField.map(_ := 0.U)
+        lsq.io.std.storeDataIn(i).bits.vecDebug.map(_ := DontCare)
         stData(i).ready := true.B
     }
     lsq.io.std.storeDataIn.map(_.bits.debug := 0.U.asTypeOf(new DebugBundle))
@@ -2068,8 +2070,8 @@ class MemBlockInlinedImp(outer: MemBlockInlined) extends LazyModuleImp(outer)
   // store event difftest information
   if (env.EnableDifftest) {
     (0 until EnsbufferWidth).foreach{i =>
-        io.mem_to_ooo.storeDebugInfo(i).robidx := sbuffer.io.vecDifftestInfo(i).bits.robIdx
-        sbuffer.io.vecDifftestInfo(i).bits.pc := io.mem_to_ooo.storeDebugInfo(i).pc
+        io.mem_to_ooo.storeDebugInfo(i).robidx := sbuffer.io.vecDifftestInfo(i).bits.uop.robIdx
+        sbuffer.io.vecDifftestInfo(i).bits.uop.pc := io.mem_to_ooo.storeDebugInfo(i).pc
     }
   }
 
diff --git a/src/main/scala/xiangshan/mem/lsqueue/LSQWrapper.scala b/src/main/scala/xiangshan/mem/lsqueue/LSQWrapper.scala
index 3443913df1c..b2e4ac2848f 100644
--- a/src/main/scala/xiangshan/mem/lsqueue/LSQWrapper.scala
+++ b/src/main/scala/xiangshan/mem/lsqueue/LSQWrapper.scala
@@ -89,7 +89,7 @@ class LsqWrapper(implicit p: Parameters) extends XSModule with HasDCacheParamete
     val ncOut = Vec(LoadPipelineWidth, DecoupledIO(new LsPipelineBundle))
     val replay = Vec(LoadPipelineWidth, Decoupled(new LsPipelineBundle))
     val sbuffer = Vec(EnsbufferWidth, Decoupled(new DCacheWordReqWithVaddrAndPfFlag))
-    val sbufferVecDifftestInfo = Vec(EnsbufferWidth, Decoupled(new DynInst)) // The vector store difftest needs is
+    val sbufferVecDifftestInfo = Vec(EnsbufferWidth, Decoupled(new ToSbufferDifftestInfoBundle)) // for vector store difftest
     val forward = Vec(LoadPipelineWidth, Flipped(new PipeLoadForwardQueryIO))
     val rob = Flipped(new RobLsqIO)
     val nuke_rollback = Vec(StorePipelineWidth, Output(Valid(new Redirect)))
diff --git a/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala b/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala
index 85afb150c80..07913dc743f 100644
--- a/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala
+++ b/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala
@@ -174,7 +174,7 @@ class StoreQueue(implicit p: Parameters) extends XSModule
     val storeDataIn = Vec(StorePipelineWidth, Flipped(Valid(new MemExuOutput(isVector = true)))) // store data, send to sq from rs
     val storeMaskIn = Vec(StorePipelineWidth, Flipped(Valid(new StoreMaskBundle))) // store mask, send to sq from rs
     val sbuffer = Vec(EnsbufferWidth, Decoupled(new DCacheWordReqWithVaddrAndPfFlag)) // write committed store to sbuffer
-    val sbufferVecDifftestInfo = Vec(EnsbufferWidth, Decoupled(new DynInst)) // The vector store difftest needs is, write committed store to sbuffer
+    val sbufferVecDifftestInfo = Vec(EnsbufferWidth, Decoupled(new ToSbufferDifftestInfoBundle)) // The vector store difftest needs is, write committed store to sbuffer
     val uncacheOutstanding = Input(Bool())
     val cmoOpReq  = DecoupledIO(new CMOReq)
     val cmoOpResp = Flipped(DecoupledIO(new CMOResp))
@@ -232,7 +232,7 @@ class StoreQueue(implicit p: Parameters) extends XSModule
   ))
   vaddrModule.io := DontCare
   val dataBuffer = Module(new DatamoduleResultBuffer(new DataBufferEntry))
-  val difftestBuffer = if (env.EnableDifftest) Some(Module(new DatamoduleResultBuffer(new DynInst))) else None
+  val difftestBuffer = if (env.EnableDifftest) Some(Module(new DatamoduleResultBuffer(new ToSbufferDifftestInfoBundle))) else None
   val exceptionBuffer = Module(new StoreExceptionBuffer)
   exceptionBuffer.io.redirect := io.brqRedirect
   exceptionBuffer.io.exceptionAddr.isStore := DontCare
@@ -255,6 +255,8 @@ class StoreQueue(implicit p: Parameters) extends XSModule
   val debug_paddr = Reg(Vec(StoreQueueSize, UInt((PAddrBits).W)))
   val debug_vaddr = Reg(Vec(StoreQueueSize, UInt((VAddrBits).W)))
   val debug_data = Reg(Vec(StoreQueueSize, UInt((XLEN).W)))
+  val debug_vec_unaligned_start = Reg(Vec(StoreQueueSize, UInt((log2Up(XLEN)).W))) // only use for unit-stride difftest
+  val debug_vec_unaligned_offset = Reg(Vec(StoreQueueSize, UInt((log2Up(XLEN)).W))) // only use for unit-stride difftest
 
   // state & misc
   val allocated = RegInit(VecInit(List.fill(StoreQueueSize)(false.B))) // sq entry has been allocated
@@ -600,6 +602,8 @@ class StoreQueue(implicit p: Parameters) extends XSModule
       dataModule.io.data.wen(i) := true.B
 
       debug_data(dataModule.io.data.waddr(i)) := dataModule.io.data.wdata(i)
+      debug_vec_unaligned_start(dataModule.io.data.waddr(i)) := io.storeDataIn(i).bits.vecDebug.get.start
+      debug_vec_unaligned_offset(dataModule.io.data.waddr(i)) := io.storeDataIn(i).bits.vecDebug.get.offset
     }
     XSInfo(io.storeDataIn(i).fire,
       "store data write to sq idx %d pc 0x%x data %x -> %x\n",
@@ -1344,9 +1348,14 @@ class StoreQueue(implicit p: Parameters) extends XSModule
   )
   val onlyCommit0 = dataBuffer.io.enq(0).fire && !dataBuffer.io.enq(1).fire
 
+  /**
+   * If rdataPtr(0) is misaligned and Cross16Byte, this store request will fill two ports of rdataBuffer,
+   * Therefore, the judgement of vecCommitLastFlow should't to use rdataPtr(1)
+   * */
+  val firstSplit = canDeqMisaligned && firstWithMisalign && firstWithCross16Byte
   val vecCommitLastFlow =
     // robidx equal => check if 1 is last flow
-    robidxEQ && vecCommitHasExceptionLastFlow(1) ||
+    robidxEQ && vecCommitHasExceptionLastFlow(1) && !firstSplit ||
     // robidx not equal => 0 must be the last flow, just check if 1 is last flow when 1 has exception
     robidxNE && (vecCommitHasExceptionValid(1) && vecCommitHasExceptionLastFlow(1) || !vecCommitHasExceptionValid(1)) ||
     onlyCommit0 && vecCommitHasExceptionLastFlow(0)
@@ -1354,7 +1363,8 @@ class StoreQueue(implicit p: Parameters) extends XSModule
 
   val vecExceptionFlagCancel  = (0 until EnsbufferWidth).map{ i =>
     val ptr = rdataPtrExt(i).value
-    val vecLastFlowCommit = vecLastFlow(ptr) && (uop(ptr).robIdx === vecExceptionFlag.bits.robIdx) && dataBuffer.io.enq(i).fire
+    val vecLastFlowCommit = vecLastFlow(ptr) && (uop(ptr).robIdx === vecExceptionFlag.bits.robIdx) &&
+                            dataBuffer.io.enq(i).fire && !firstSplit
     vecLastFlowCommit
   }.reduce(_ || _)
 
@@ -1382,7 +1392,9 @@ class StoreQueue(implicit p: Parameters) extends XSModule
     for (i <- 0 until EnsbufferWidth) {
       val ptr = dataBuffer.io.enq(i).bits.sqPtr.value
       difftestBuffer.get.io.enq(i).valid := dataBuffer.io.enq(i).valid
-      difftestBuffer.get.io.enq(i).bits := uop(ptr)
+      difftestBuffer.get.io.enq(i).bits.uop := uop(ptr)
+      difftestBuffer.get.io.enq(i).bits.start  := debug_vec_unaligned_start(ptr)
+      difftestBuffer.get.io.enq(i).bits.offset := debug_vec_unaligned_offset(ptr)
     }
     for (i <- 0 until EnsbufferWidth) {
       io.sbufferVecDifftestInfo(i).valid := difftestBuffer.get.io.deq(i).valid
diff --git a/src/main/scala/xiangshan/mem/sbuffer/Sbuffer.scala b/src/main/scala/xiangshan/mem/sbuffer/Sbuffer.scala
index 3dcb86cfe38..3fed5a0f4ce 100644
--- a/src/main/scala/xiangshan/mem/sbuffer/Sbuffer.scala
+++ b/src/main/scala/xiangshan/mem/sbuffer/Sbuffer.scala
@@ -194,7 +194,7 @@ class Sbuffer(implicit p: Parameters)
   val io = IO(new Bundle() {
     val hartId = Input(UInt(hartIdLen.W))
     val in = Vec(EnsbufferWidth, Flipped(Decoupled(new DCacheWordReqWithVaddrAndPfFlag)))  //Todo: store logic only support Width == 2 now
-    val vecDifftestInfo = Vec(EnsbufferWidth, Flipped(Decoupled(new DynInst)))
+    val vecDifftestInfo = Vec(EnsbufferWidth, Flipped(Decoupled(new ToSbufferDifftestInfoBundle)))
     val dcache = Flipped(new DCacheToSbufferIO)
     val forward = Vec(LoadPipelineWidth, Flipped(new LoadForwardQueryIO))
     val sqempty = Input(Bool())
@@ -911,7 +911,12 @@ class Sbuffer(implicit p: Parameters)
     for (i <- 0 until EnsbufferWidth) {
       io.vecDifftestInfo(i).ready := io.in(i).ready
 
-      val uop             = io.vecDifftestInfo(i).bits
+      val uop              = io.vecDifftestInfo(i).bits.uop
+
+      val unaligned_start       = io.vecDifftestInfo(i).bits.start
+      val unaligned_offset      = io.vecDifftestInfo(i).bits.offset
+      val unaligned_start_bits  = (io.vecDifftestInfo(i).bits.start << 3.U).asUInt
+      val unaligned_offset_bits = (io.vecDifftestInfo(i).bits.offset << 3.U).asUInt
 
       val isVse           = isVStore(uop.fuType) && LSUOpType.isUStride(uop.fuOpType)
       val isVsm           = isVStore(uop.fuType) && VstuType.isMasked(uop.fuOpType)
@@ -944,12 +949,17 @@ class Sbuffer(implicit p: Parameters)
       val difftestCommon = DifftestModule(new DiffStoreEvent, delay = 2, dontCare = true)
       diffStoreEventCount += 1
       when (isVSLine) {
-        val splitMask         = UIntSlice(rawMask, EEB - 1.U, 0.U)(7,0)  // Byte
-        val splitData         = UIntSlice(rawData, EEWBits - 1.U, 0.U)(63,0) // Double word
+        val upper             = Mux(unaligned_start === 0.U && unaligned_offset =/= 0.U, unaligned_offset, EEB - 1.U + unaligned_offset) // unit-stride second write request
+        val upperBits         = Mux(unaligned_start === 0.U && unaligned_offset =/= 0.U,
+                                   (unaligned_offset << 3.U).asUInt - 1.U,
+                                   ((EEB + unaligned_offset) << 3.U).asUInt - 1.U)// unit-stride second write request
+        val splitMask         = UIntSlice(rawMask, upper, unaligned_start)(7,0)  // Byte
+        val splitData         = UIntSlice(rawData, upperBits, unaligned_start_bits)(63,0) // Double word
         val storeCommit       = io.in(i).fire && splitMask.orR && io.in(i).bits.vecValid
-        val waddr             = rawAddr
-        val wmask             = splitMask
-        val wdata             = splitData & MaskExpand(splitMask)
+        // align with ref
+        val waddr             = Mux(unaligned_offset =/= 0.U && rawAddr(3), ZeroExt(Cat(rawAddr(PAddrBits - 1, 3), 0.U(3.W)), 64), rawAddr)
+        val wmask             = Mux(unaligned_offset =/= 0.U && rawAddr(3), 0.U, splitMask << unaligned_offset)
+        val wdata             = Mux(unaligned_offset =/= 0.U && rawAddr(3), 0.U, (splitData & MaskExpand(splitMask)) << unaligned_offset_bits)
 
         difftestCommon.coreid := io.hartId
         difftestCommon.index  := (i*VecMemFLOWMaxNumber).U
@@ -957,8 +967,8 @@ class Sbuffer(implicit p: Parameters)
         difftestCommon.addr   := waddr
         difftestCommon.data   := wdata
         difftestCommon.mask   := wmask
-        difftestCommon.robidx := io.vecDifftestInfo(i).bits.robIdx.value
-        difftestCommon.pc     := io.vecDifftestInfo(i).bits.pc
+        difftestCommon.robidx := io.vecDifftestInfo(i).bits.uop.robIdx.value
+        difftestCommon.pc     := io.vecDifftestInfo(i).bits.uop.pc
 
       } .elsewhen (!isWline) {
         val storeCommit       = io.in(i).fire
@@ -974,8 +984,8 @@ class Sbuffer(implicit p: Parameters)
         difftestCommon.addr   := waddr
         difftestCommon.data   := wdata
         difftestCommon.mask   := wmask
-        difftestCommon.robidx := io.vecDifftestInfo(i).bits.robIdx.value
-        difftestCommon.pc     := io.vecDifftestInfo(i).bits.pc
+        difftestCommon.robidx := io.vecDifftestInfo(i).bits.uop.robIdx.value
+        difftestCommon.pc     := io.vecDifftestInfo(i).bits.uop.pc
       }
 
       for (index <- 0 until WlineMaxNumber) {
@@ -992,8 +1002,8 @@ class Sbuffer(implicit p: Parameters)
           difftest.addr   := blockAddr + (index.U << wordOffBits)
           difftest.data   := io.in(i).bits.data
           difftest.mask   := ((1 << wordBytes) - 1).U
-          difftest.robidx := io.vecDifftestInfo(i).bits.robIdx.value
-          difftest.pc     := io.vecDifftestInfo(i).bits.pc
+          difftest.robidx := io.vecDifftestInfo(i).bits.uop.robIdx.value
+          difftest.pc     := io.vecDifftestInfo(i).bits.uop.pc
 
           assert(!storeCommit || (io.in(i).bits.data === 0.U), "wline only supports whole zero write now")
         }
@@ -1014,12 +1024,12 @@ class Sbuffer(implicit p: Parameters)
           val shiftFlag   = shiftIndex(2,0).orR // Double word Flag
           val shiftBytes  = Mux(shiftFlag, shiftIndex(2,0), 0.U)
           val shiftBits   = shiftBytes << 3.U
-          val splitMask   = UIntSlice(rawMask, (EEB*(index+1).U - 1.U), EEB*index.U)(7,0)  // Byte
-          val splitData   = UIntSlice(rawData, (EEWBits*(index+1).U - 1.U), EEWBits*index.U)(63,0) // Double word
+          val splitMask   = UIntSlice(rawMask, (EEB*(index+1).U - 1.U) + unaligned_offset, EEB*index.U + unaligned_offset)(7,0)  // Byte
+          val splitData   = UIntSlice(rawData, (EEWBits*(index+1).U - 1.U) + unaligned_offset_bits, EEWBits*index.U + unaligned_offset_bits)(63,0) // Double word
           val storeCommit = io.in(i).fire && splitMask.orR  && io.in(i).bits.vecValid
-          val waddr       = Cat(rawAddr(PAddrBits - 1, 4), Cat(shiftIndex(3), 0.U(3.W)))
-          val wmask       = splitMask << shiftBytes
-          val wdata       = (splitData & MaskExpand(splitMask)) << shiftBits
+          val waddr       = Mux(unaligned_offset =/= 0.U && shiftIndex(3), Cat(rawAddr(PAddrBits - 1, 4),  0.U(4.W)),Cat(rawAddr(PAddrBits - 1, 4), Cat(shiftIndex(3), 0.U(3.W))))
+          val wmask       = Mux(unaligned_offset =/= 0.U && shiftIndex(3), 0.U,splitMask << (shiftBytes + unaligned_offset))
+          val wdata       = Mux(unaligned_offset =/= 0.U && shiftIndex(3), 0.U,(splitData & MaskExpand(splitMask)) << (shiftBits.asUInt + unaligned_offset_bits))
 
           difftest.coreid := io.hartId
           difftest.index  := (i*VecMemFLOWMaxNumber+index).U
@@ -1027,8 +1037,8 @@ class Sbuffer(implicit p: Parameters)
           difftest.addr   := waddr
           difftest.data   := wdata
           difftest.mask   := wmask
-          difftest.robidx := io.vecDifftestInfo(i).bits.robIdx.value
-          difftest.pc     := io.vecDifftestInfo(i).bits.pc
+          difftest.robidx := io.vecDifftestInfo(i).bits.uop.robIdx.value
+          difftest.pc     := io.vecDifftestInfo(i).bits.uop.pc
         }
       }
     }
diff --git a/src/main/scala/xiangshan/mem/vector/VMergeBuffer.scala b/src/main/scala/xiangshan/mem/vector/VMergeBuffer.scala
index 472baee9ab6..32467a1ec1c 100644
--- a/src/main/scala/xiangshan/mem/vector/VMergeBuffer.scala
+++ b/src/main/scala/xiangshan/mem/vector/VMergeBuffer.scala
@@ -477,6 +477,7 @@ class VSMergeBufferImp(implicit p: Parameters) extends BaseVMergeBuffer(isVStore
     sink.vdIdx.get        := DontCare
     sink.isFromLoadUnit   := DontCare
     sink.uop.vpu.vstart   := source.vstart
+    sink.vecDebug.get     := DontCare
     sink
   }
 
diff --git a/src/main/scala/xiangshan/mem/vector/VSegmentUnit.scala b/src/main/scala/xiangshan/mem/vector/VSegmentUnit.scala
index 308ebccb7d7..6eac9441c56 100644
--- a/src/main/scala/xiangshan/mem/vector/VSegmentUnit.scala
+++ b/src/main/scala/xiangshan/mem/vector/VSegmentUnit.scala
@@ -750,7 +750,9 @@ class VSegmentUnit (implicit p: Parameters) extends VLSUModule
   )
 
   io.vecDifftestInfo.valid         := io.sbuffer.valid
-  io.vecDifftestInfo.bits          := uopq(deqPtr.value).uop
+  io.vecDifftestInfo.bits.uop      := uopq(deqPtr.value).uop
+  io.vecDifftestInfo.bits.start    := 0.U // only use in no-segment unit-stride
+  io.vecDifftestInfo.bits.offset   := 0.U
 
   /**
    * update ptr
diff --git a/src/main/scala/xiangshan/mem/vector/VSplit.scala b/src/main/scala/xiangshan/mem/vector/VSplit.scala
index 53108614ba1..2f148cbf809 100644
--- a/src/main/scala/xiangshan/mem/vector/VSplit.scala
+++ b/src/main/scala/xiangshan/mem/vector/VSplit.scala
@@ -474,11 +474,24 @@ class VSSplitBufferImp(implicit p: Parameters) extends VSplitBuffer(isVStore = t
   vstd.bits.uop.fuType := FuType.vstu.U
   vstd.bits.data := Mux(!issuePreIsSplit, usSplitData, flowData)
   vstd.bits.debug := DontCare
+  vstd.bits.vecDebug.get := DontCare // maybe assign later
   vstd.bits.vdIdx.get := DontCare
   vstd.bits.vdIdxInField.get := DontCare
   vstd.bits.isFromLoadUnit   := DontCare
   vstd.bits.mask.get := Mux(!issuePreIsSplit, usSplitMask, mask)
 
+  if(env.EnableDifftest){
+    val usVaddrOffset   = LookupTree(issueEew, List(
+      "b00".U -> 0.U,
+      "b01".U -> vaddr(0),
+      "b10".U -> vaddr(1, 0),
+      "b11".U -> vaddr(2, 0)
+    ))
+
+    vstd.bits.vecDebug.get.start  := Mux(splitIdx === 0.U, usVaddrOffset, 0.U)// for unaligned store event
+    vstd.bits.vecDebug.get.offset := usVaddrOffset
+  }
+
 }
 
 class VLSplitBufferImp(implicit p: Parameters) extends VSplitBuffer(isVStore = false){
diff --git a/src/main/scala/xiangshan/mem/vector/VecBundle.scala b/src/main/scala/xiangshan/mem/vector/VecBundle.scala
index cda3e7d4db6..6115b07be8e 100644
--- a/src/main/scala/xiangshan/mem/vector/VecBundle.scala
+++ b/src/main/scala/xiangshan/mem/vector/VecBundle.scala
@@ -267,7 +267,7 @@ class VSegmentUnitIO(implicit p: Parameters) extends VLSUBundle{
   val uopwriteback        = DecoupledIO(new MemExuOutput(isVector = true)) // writeback data
   val rdcache             = new DCacheLoadIO // read dcache port
   val sbuffer             = Decoupled(new DCacheWordReqWithVaddrAndPfFlag)
-  val vecDifftestInfo     = Decoupled(new DynInst) // to sbuffer
+  val vecDifftestInfo     = Decoupled(new ToSbufferDifftestInfoBundle) // to sbuffer
   val dtlb                = new TlbRequestIO(2)
   val pmpResp             = Flipped(new PMPRespBundle())
   val flush_sbuffer       = new SbufferFlushBundle
```
