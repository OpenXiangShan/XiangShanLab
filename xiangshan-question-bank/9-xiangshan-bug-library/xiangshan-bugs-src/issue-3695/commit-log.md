# Commit Log
- Issue: #3695
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/3695
- Issue state: closed
- Tested RTL commit: -
- Related PR: #3695
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/3695
- Changed files: 12
- Additions: 86
- Deletions: 50

## Files
- `src/main/scala/xiangshan/backend/CtrlBlock.scala`
- `src/main/scala/xiangshan/backend/datapath/VldMergeUnit.scala`
- `src/main/scala/xiangshan/backend/fu/vector/Bundles.scala`
- `src/main/scala/xiangshan/backend/fu/wrapper/VFALU.scala`
- `src/main/scala/xiangshan/backend/fu/wrapper/VIAluFix.scala`
- `src/main/scala/xiangshan/backend/issue/EntryBundles.scala`
- `src/main/scala/xiangshan/backend/rob/Rob.scala`
- `src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala`
- `src/main/scala/xiangshan/mem/vector/VMergeBuffer.scala`
- `src/main/scala/xiangshan/mem/vector/VSegmentUnit.scala`
- `src/main/scala/xiangshan/mem/vector/VfofBuffer.scala`
- `src/main/scala/xiangshan/package.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/backend/CtrlBlock.scala b/src/main/scala/xiangshan/backend/CtrlBlock.scala
index 5dbde77f94c..fd40b84233f 100644
--- a/src/main/scala/xiangshan/backend/CtrlBlock.scala
+++ b/src/main/scala/xiangshan/backend/CtrlBlock.scala
@@ -427,9 +427,8 @@ class CtrlBlockImp(
   }
 
   private val decodePipeRename = Wire(Vec(RenameWidth, DecoupledIO(new DecodedInst)))
-  private val vecExcpModBusy = io.fromVecExcpMod.busy
   for (i <- 0 until RenameWidth) {
-    PipelineConnect(decode.io.out(i), decodePipeRename(i), rename.io.in(i).ready && !vecExcpModBusy,
+    PipelineConnect(decode.io.out(i), decodePipeRename(i), rename.io.in(i).ready,
       s1_s3_redirect.valid || s2_s4_pendingRedirectValid, moduleName = Some("decodePipeRenameModule"))
 
     decodePipeRename(i).ready := rename.io.in(i).ready
@@ -589,6 +588,7 @@ class CtrlBlockImp(
   rob.io.writebackNums := VecInit(delayedNotFlushedWriteBackNums)
   rob.io.writebackNeedFlush := delayedNotFlushedWriteBackNeedFlush
   rob.io.readGPAMemData := gpaMem.io.exceptionReadData
+  rob.io.fromVecExcpMod.busy := io.fromVecExcpMod.busy
 
   io.redirect := s1_s3_redirect
 
diff --git a/src/main/scala/xiangshan/backend/datapath/VldMergeUnit.scala b/src/main/scala/xiangshan/backend/datapath/VldMergeUnit.scala
index 23d834ddf10..149db61b249 100644
--- a/src/main/scala/xiangshan/backend/datapath/VldMergeUnit.scala
+++ b/src/main/scala/xiangshan/backend/datapath/VldMergeUnit.scala
@@ -39,7 +39,8 @@ class VldMergeUnit(val params: ExeUnitParams)(implicit p: Parameters) extends XS
   mgu.io.in.info.dstMask := false.B // vlm need not mask
   mgu.io.in.isIndexedVls := wbReg.bits.vls.get.isIndexed
 
-  vdAfterMerge := mgu.io.out.vd
+  //For the uop whose vl is modified by first-only-fault, the data written back can be used directly
+  vdAfterMerge := Mux(wbReg.bits.vlWen.getOrElse(false.B), wbReg.bits.data(0), mgu.io.out.vd)
 
   io.writebackAfterMerge.valid := wbReg.valid
   io.writebackAfterMerge.bits := wbReg.bits
diff --git a/src/main/scala/xiangshan/backend/fu/vector/Bundles.scala b/src/main/scala/xiangshan/backend/fu/vector/Bundles.scala
index 32816c8aa84..f1a451a101b 100644
--- a/src/main/scala/xiangshan/backend/fu/vector/Bundles.scala
+++ b/src/main/scala/xiangshan/backend/fu/vector/Bundles.scala
@@ -82,6 +82,14 @@ object Bundles {
       res.vlmul := 0.U
       res
     }
+
+    def mu: UInt = 0.U(1.W)
+
+    def ma: UInt = 1.U(1.W)
+
+    def tu: UInt = 0.U(1.W)
+
+    def ta: UInt = 1.U(1.W)
   }
 
   object VsetVType {
@@ -123,11 +131,6 @@ object Bundles {
     }
   }
 
-  def mu: UInt = 0.U(1.W)
-  def ma: UInt = 1.U(1.W)
-  def tu: UInt = 0.U(1.W)
-  def ta: UInt = 1.U(1.W)
-
   // modify the width when support more vector data width
   object VSew extends NamedUInt(2) {
     def e8  : UInt = "b000".U(width.W)
diff --git a/src/main/scala/xiangshan/backend/fu/wrapper/VFALU.scala b/src/main/scala/xiangshan/backend/fu/wrapper/VFALU.scala
index 4a52c28e27f..81333abcf21 100644
--- a/src/main/scala/xiangshan/backend/fu/wrapper/VFALU.scala
+++ b/src/main/scala/xiangshan/backend/fu/wrapper/VFALU.scala
@@ -5,7 +5,7 @@ import chisel3._
 import chisel3.util._
 import utility.XSError
 import xiangshan.backend.fu.FuConfig
-import xiangshan.backend.fu.vector.Bundles.{VLmul, VSew, ma}
+import xiangshan.backend.fu.vector.Bundles.{VLmul, VSew}
 import xiangshan.backend.fu.vector.utils.VecDataSplitModule
 import xiangshan.backend.fu.vector.{Mgu, Mgtu, VecInfo, VecPipedFuncUnit}
 import xiangshan.ExceptionNO
diff --git a/src/main/scala/xiangshan/backend/fu/wrapper/VIAluFix.scala b/src/main/scala/xiangshan/backend/fu/wrapper/VIAluFix.scala
index d11a094b50e..75dd676a396 100644
--- a/src/main/scala/xiangshan/backend/fu/wrapper/VIAluFix.scala
+++ b/src/main/scala/xiangshan/backend/fu/wrapper/VIAluFix.scala
@@ -6,7 +6,7 @@ import chisel3.util._
 import chisel3.util.experimental.decode.{QMCMinimizer, TruthTable, decoder}
 import utility.{DelayN, XSError}
 import xiangshan.XSCoreParamsKey
-import xiangshan.backend.fu.vector.Bundles.{VConfig, VSew, ma}
+import xiangshan.backend.fu.vector.Bundles.{VConfig, VSew}
 import xiangshan.backend.fu.vector.{Mgu, Mgtu, VecPipedFuncUnit}
 import xiangshan.backend.fu.vector.Utils.VecDataToMaskDataVec
 import xiangshan.backend.fu.vector.utils.VecDataSplitModule
diff --git a/src/main/scala/xiangshan/backend/issue/EntryBundles.scala b/src/main/scala/xiangshan/backend/issue/EntryBundles.scala
index dab06d8b3aa..a71bc55340d 100644
--- a/src/main/scala/xiangshan/backend/issue/EntryBundles.scala
+++ b/src/main/scala/xiangshan/backend/issue/EntryBundles.scala
@@ -316,7 +316,7 @@ object EntryBundles extends HasCircularQueuePtrHelper {
           * 2. when vl = 0, we cannot set the srctype to imm because the vd keep the old value
           * 3. when vl = vlmax, we can set srctype to imm when vta is not set
           */
-        ignoreOldVd := srcIsVec && vlIsNonZero && !isDependOldvd && (ignoreTail || ignoreWhole)
+        ignoreOldVd := !VlduType.isFof(entryReg.payload.fuOpType) && srcIsVec && vlIsNonZero && !isDependOldvd && (ignoreTail || ignoreWhole)
       } else {
         ignoreOldVd := false.B
       }
diff --git a/src/main/scala/xiangshan/backend/rob/Rob.scala b/src/main/scala/xiangshan/backend/rob/Rob.scala
index a43922a767d..1820677af88 100644
--- a/src/main/scala/xiangshan/backend/rob/Rob.scala
+++ b/src/main/scala/xiangshan/backend/rob/Rob.scala
@@ -85,6 +85,9 @@ class RobImp(override val wrapper: Rob)(implicit p: Parameters, params: BackendP
         val hasVsetvl = Output(Bool())
       }
     }
+    val fromVecExcpMod = Input(new Bundle {
+      val busy = Bool()
+    })
     val readGPAMemAddr = ValidIO(new Bundle {
       val ftqPtr = new FtqPtr()
       val ftqOffset = UInt(log2Up(PredictWidth).W)
@@ -152,7 +155,7 @@ class RobImp(override val wrapper: Rob)(implicit p: Parameters, params: BackendP
   val deqPtr = deqPtrVec(0)
   val walkPtr = walkPtrVec(0)
   val allocatePtrVec = VecInit((0 until RenameWidth).map(i => enqPtrVec(PopCount(io.enq.req.take(i).map(req => req.valid && req.bits.firstUop)))))
-  io.enq.canAccept := allowEnqueue && !hasBlockBackward && rab.io.canEnq && vtypeBuffer.io.canEnq
+  io.enq.canAccept := allowEnqueue && !hasBlockBackward && rab.io.canEnq && vtypeBuffer.io.canEnq && !io.fromVecExcpMod.busy
   io.enq.resp := allocatePtrVec
   val canEnqueue = VecInit(io.enq.req.map(req => req.valid && req.bits.firstUop && io.enq.canAccept))
   val timer = GTimer()
@@ -807,7 +810,7 @@ class RobImp(override val wrapper: Rob)(implicit p: Parameters, params: BackendP
 
   val enqPtrGenModule = Module(new RobEnqPtrWrapper)
   enqPtrGenModule.io.redirect := io.redirect
-  enqPtrGenModule.io.allowEnqueue := allowEnqueue && rab.io.canEnq
+  enqPtrGenModule.io.allowEnqueue := allowEnqueue && rab.io.canEnq && !io.fromVecExcpMod.busy
   enqPtrGenModule.io.hasBlockBackward := hasBlockBackward
   enqPtrGenModule.io.enq := VecInit(io.enq.req.map(req => req.valid && req.bits.firstUop))
   enqPtrVec := enqPtrGenModule.io.out
@@ -941,6 +944,7 @@ class RobImp(override val wrapper: Rob)(implicit p: Parameters, params: BackendP
   val uopEnqValidSeq = io.enq.req.map(req => io.enq.canAccept && req.valid)
   val instEnqValidSeq = io.enq.req.map(req => io.enq.canAccept && req.valid && req.bits.firstUop)
   val enqNeedWriteRFSeq = io.enq.req.map(_.bits.needWriteRf)
+  val enqHasExcpSeq = io.enq.req.map(_.bits.hasException)
   val enqRobIdxSeq = io.enq.req.map(req => req.bits.robIdx.value)
   val enqUopNumVec = VecInit(io.enq.req.map(req => req.bits.numUops))
   val enqWBNumVec = VecInit(io.enq.req.map(req => req.bits.numWB))
@@ -957,10 +961,12 @@ class RobImp(override val wrapper: Rob)(implicit p: Parameters, params: BackendP
     val uopCanEnqSeq = uopEnqValidSeq.zip(robIdxMatchSeq).map { case (valid, isMatch) => valid && isMatch }
     val instCanEnqSeq = instEnqValidSeq.zip(robIdxMatchSeq).map { case (valid, isMatch) => valid && isMatch }
     val instCanEnqFlag = Cat(instCanEnqSeq).orR
+    val hasExcpSeq = enqHasExcpSeq.lazyZip(robIdxMatchSeq).lazyZip(uopEnqValidSeq).map { case (excp, isMatch, valid) => excp && isMatch && valid }
+    val hasExcpFlag = Cat(hasExcpSeq).orR
     val isFirstEnq = !robEntries(i).valid && instCanEnqFlag
     val realDestEnqNum = PopCount(enqNeedWriteRFSeq.zip(uopCanEnqSeq).map { case (writeFlag, valid) => writeFlag && valid })
     when(isFirstEnq){
-      robEntries(i).realDestSize := realDestEnqNum
+      robEntries(i).realDestSize := Mux(hasExcpFlag, 0.U, realDestEnqNum)
     }.elsewhen(robEntries(i).valid && Cat(uopCanEnqSeq).orR){
       robEntries(i).realDestSize := robEntries(i).realDestSize + realDestEnqNum
     }
diff --git a/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala b/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala
index a0339b49253..9541c561bf9 100644
--- a/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala
+++ b/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala
@@ -1043,7 +1043,16 @@ class StoreQueue(implicit p: Parameters) extends XSModule
   // Just select the last Uop tah has an exception.
   val vecCommitHasExceptionSelectUop  = ParallelPosteriorityMux(vecCommitHasExceptionValid, vecCommitHasExceptionUop)
   // If the last flow with an exception is the LastFlow of this instruction, the flag is not set.
-  val vecCommitLastFlow =  ParallelPosteriorityMux(vecCommitHasExceptionValid, vecCommitHasExceptionLastFlow)
+  // compare robidx to select the last flow
+  require(EnsbufferWidth == 2, "The vector store exception handle process only support EnsbufferWidth == 2 yet.")
+  val robidxEQ = uop(rdataPtrExt(0).value).robIdx === uop(rdataPtrExt(1).value).robIdx
+
+  val vecCommitLastFlow = 
+    // robidx equal => check if 1 is last flow
+    robidxEQ && vecCommitHasExceptionLastFlow(1) || 
+    // robidx not equal => 0 must be the last flow, just check if 1 is last flow when 1 has exception
+    !robidxEQ && vecCommitHasExceptionValid(1) && vecCommitHasExceptionLastFlow(1)
+  
 
   val vecExceptionFlagCancel  = (0 until EnsbufferWidth).map{ i =>
     val ptr                   = rdataPtrExt(i).value
diff --git a/src/main/scala/xiangshan/mem/vector/VMergeBuffer.scala b/src/main/scala/xiangshan/mem/vector/VMergeBuffer.scala
index 7ef2ae98e4e..24cd5509e7c 100644
--- a/src/main/scala/xiangshan/mem/vector/VMergeBuffer.scala
+++ b/src/main/scala/xiangshan/mem/vector/VMergeBuffer.scala
@@ -30,6 +30,7 @@ import xiangshan.backend.fu.FuType
 import xiangshan.backend.fu.FuConfig._
 import xiangshan.backend.datapath.NewPipelineConnect
 import freechips.rocketchip.diplomacy.BufferParams
+import xiangshan.backend.fu.vector.Bundles.VType
 
 class MBufferBundle(implicit p: Parameters) extends VLSUBundle{
   val data             = UInt(VLEN.W)
@@ -270,6 +271,7 @@ abstract class BaseVMergeBuffer(isVStore: Boolean=false)(implicit p: Parameters)
         entry.gpaddr       := selPort(0).gpaddr
         entry.isForVSnonLeafPTE := selPort(0).isForVSnonLeafPTE
       }.otherwise{
+        entry.uop.vpu.vta  := VType.tu
         entry.vl           := vstart
       }
     }
diff --git a/src/main/scala/xiangshan/mem/vector/VSegmentUnit.scala b/src/main/scala/xiangshan/mem/vector/VSegmentUnit.scala
index 2cfa0b15c91..1031ff0c377 100644
--- a/src/main/scala/xiangshan/mem/vector/VSegmentUnit.scala
+++ b/src/main/scala/xiangshan/mem/vector/VSegmentUnit.scala
@@ -32,7 +32,7 @@ import xiangshan.cache._
 import xiangshan.cache.wpu.ReplayCarry
 import xiangshan.backend.fu.util.SdtrigExt
 import xiangshan.ExceptionNO._
-import xiangshan.backend.fu.vector.Bundles.VConfig
+import xiangshan.backend.fu.vector.Bundles.{VConfig, VType}
 import xiangshan.backend.datapath.NewPipelineConnect
 import xiangshan.backend.fu.NewCSR._
 import xiangshan.backend.fu.vector.Utils.VecDataToMaskDataVec
@@ -56,7 +56,8 @@ class VSegmentBundle(implicit p: Parameters) extends VLSUBundle
   val exception_gpa    = Bool()
   val exception_pa     = Bool()
   val exceptionVstart  = UInt(elemIdxBits.W)
-  val exceptionVl      = UInt(elemIdxBits.W)
+  // valid: have fof exception but can not trigger, need update all writebacked uop.vl with exceptionVl
+  val exceptionVl      = ValidIO(UInt(elemIdxBits.W))
   val isFof            = Bool()
 }
 
@@ -352,7 +353,8 @@ class VSegmentUnit (implicit p: Parameters) extends VLSUModule
     instMicroOp.uopFlowNum            := uopFlowNum
     instMicroOp.uopFlowNumMask        := GenVlMaxMask(uopFlowNum, elemIdxBits) // for merge data
     instMicroOp.vl                    := io.in.bits.src_vl.asTypeOf(VConfig()).vl
-    instMicroOp.exceptionVl           := io.in.bits.src_vl.asTypeOf(VConfig()).vl
+    instMicroOp.exceptionVl.valid     := false.B
+    instMicroOp.exceptionVl.bits      := io.in.bits.src_vl.asTypeOf(VConfig()).vl
     segmentOffset                     := 0.U
     instMicroOp.isFof                 := (fuOpType === VlduType.vleff) && FuType.isVLoad(fuType)
   }
@@ -476,7 +478,8 @@ class VSegmentUnit (implicit p: Parameters) extends VLSUModule
         instMicroOp.exceptionVaddr  := vaddr
         instMicroOp.exceptionVstart := segmentIdx // for exception
       }.otherwise {
-        instMicroOp.exceptionVl     := segmentIdx
+        instMicroOp.exceptionVl.valid := true.B
+        instMicroOp.exceptionVl.bits := segmentIdx
       }
     }
 
@@ -693,8 +696,8 @@ class VSegmentUnit (implicit p: Parameters) extends VLSUModule
 
   when(fofFixVlValid) {
     writebackOut.uop                    := fofBuffer
-    writebackOut.uop.vpu.vl             := instMicroOp.exceptionVl
-    writebackOut.data                   := instMicroOp.exceptionVl
+    writebackOut.uop.vpu.vl             := instMicroOp.exceptionVl.bits
+    writebackOut.data                   := instMicroOp.exceptionVl.bits
     writebackOut.mask.get               := Fill(VLEN, 1.U)
     writebackOut.uop.vpu.vmask          := Fill(VLEN, 1.U)
   }.otherwise{
@@ -704,10 +707,12 @@ class VSegmentUnit (implicit p: Parameters) extends VLSUModule
     writebackOut.mask.get               := instMicroOp.mask
     writebackOut.data                   := data(deqPtr.value)
     writebackOut.vdIdx.get              := vdIdxInField
-    writebackOut.uop.vpu.vl             := instMicroOp.vl
+    writebackOut.uop.vpu.vl             := Mux(instMicroOp.exceptionVl.valid, instMicroOp.exceptionVl.bits, instMicroOp.vl)
     writebackOut.uop.vpu.vstart         := Mux(instMicroOp.uop.exceptionVec.asUInt.orR, instMicroOp.exceptionVstart, instMicroOp.vstart)
     writebackOut.uop.vpu.vmask          := maskUsed
     writebackOut.uop.vpu.vuopIdx        := uopq(deqPtr.value).uop.vpu.vuopIdx
+    // when exception updates vl, should use vtu strategy.
+    writebackOut.uop.vpu.vta            := Mux(instMicroOp.exceptionVl.valid, VType.tu, instMicroOp.uop.vpu.vta)
     writebackOut.debug                  := DontCare
     writebackOut.vdIdxInField.get       := vdIdxInField
     writebackOut.uop.robIdx             := instMicroOp.uop.robIdx
@@ -743,7 +748,7 @@ class VSegmentUnit (implicit p: Parameters) extends VLSUModule
   io.exceptionInfo.bits.vaddr         := instMicroOp.exceptionVaddr
   io.exceptionInfo.bits.gpaddr        := instMicroOp.exceptionGpaddr
   io.exceptionInfo.bits.isForVSnonLeafPTE := instMicroOp.exceptionIsForVSnonLeafPTE
-  io.exceptionInfo.bits.vl            := instMicroOp.exceptionVl
+  io.exceptionInfo.bits.vl            := instMicroOp.exceptionVl.bits
   io.exceptionInfo.valid              := (state === s_finish) && instMicroOp.uop.exceptionVec.asUInt.orR && !isEmpty(enqPtr, deqPtr)
 }
 
diff --git a/src/main/scala/xiangshan/mem/vector/VfofBuffer.scala b/src/main/scala/xiangshan/mem/vector/VfofBuffer.scala
index 64eecb07792..16213b7ceb5 100644
--- a/src/main/scala/xiangshan/mem/vector/VfofBuffer.scala
+++ b/src/main/scala/xiangshan/mem/vector/VfofBuffer.scala
@@ -31,7 +31,7 @@ import xiangshan.backend.fu.vector.Bundles._
 class VfofDataBundle(implicit p: Parameters) extends VLSUBundle{
   val uop              = new DynInst
   val vl               = UInt(elemIdxBits.W)
-  val vuopIdx          = UopIdx()
+  val hasException     = Bool()
 }
 
 
@@ -54,14 +54,14 @@ class VfofBuffer(implicit p: Parameters) extends VLSUModule{
   val enqNeedCancel = enqBits.uop.robIdx.needFlush(io.redirect)
   val enqIsFixVl = enqBits.uop.vpu.isVleff && enqBits.uop.vpu.lastUop
 
-  XSError(entries.uop.robIdx.value =/= enqBits.uop.robIdx.value && valid && enqValid, "There should be no new fof instrction coming in\n")
-  XSError(entriesIsFixVl && valid && enqValid, "There should not new uop enqueue\n")
+  XSError(entries.uop.robIdx.value =/= enqBits.uop.robIdx.value && valid && enqValid, "There should be no new fof instrction coming in!\n")
+  XSError(entriesIsFixVl && valid && enqValid, "There should not new uop enqueue!\n")
 
   when(enqValid && !enqNeedCancel) {
     when(!valid){
-      entries.uop     := enqBits.uop
-      entries.vl      := 0.U
-      entries.vuopIdx := 0.U
+      entries.uop           := enqBits.uop
+      entries.vl            := enqBits.src_vl.asTypeOf(VConfig()).vl
+      entries.hasException  := false.B
     }.elsewhen(valid && enqIsFixVl){
       entries.uop     := enqBits.uop
     }
@@ -70,16 +70,18 @@ class VfofBuffer(implicit p: Parameters) extends VLSUModule{
   //Control Signal
   val needRedirect = entries.uop.robIdx.needFlush(io.redirect)
 
-  when(enqValid && !enqNeedCancel) {
-    valid := true.B  //Enq
+
+  when(io.uopWriteback.fire) {
+    valid := false.B  //Deq
   }.elsewhen(needRedirect) {
     valid := false.B //Redirect
-  }.elsewhen(io.uopWriteback.fire) {
-    valid := false.B //Deq
+  }.elsewhen(enqValid && !enqNeedCancel) {
+    valid := true.B //Enq
   }
 
+
   //Gather writeback information
-  val wbIsfof = io.mergeUopWriteback.map{ x => x.valid && x.bits.uop.robIdx.value === entries.uop.robIdx.value }
+  val wbIsfof = io.mergeUopWriteback.map{ x => x.valid && x.bits.uop.robIdx === entries.uop.robIdx }
 
   def getOldest(valid: Seq[Bool], bits: Seq[DynInst]): DynInst = {
     def getOldest_recursion[T <: Data](valid: Seq[Bool], bits: Seq[DynInst]): (Seq[Bool], Seq[DynInst]) = {
@@ -92,10 +94,13 @@ class VfofBuffer(implicit p: Parameters) extends VLSUModule{
           res(i).valid := valid(i)
           res(i).bits := bits(i)
         }
+        val withExcep0 = bits(0).exceptionVec.asUInt.orR
+        val withExcep1 = bits(1).exceptionVec.asUInt.orR
+        XSError(this.valid && withExcep0 && withExcep1 && valid(0) && valid(1), "Writeback to multiple Uop with exceptions at the same time!\n")
         val oldest = Mux(
-          !valid(1) || (bits(1).vpu.vuopIdx > bits(0).vpu.vuopIdx),
-          res(0),
-          res(1)
+          valid(0) && valid(1),
+          Mux((bits(1).vpu.vl > bits(0).vpu.vl || withExcep0) && !withExcep1, res(0), res(1)),
+          Mux(valid(0) && !valid(1), res(0), res(1))
         )
         (Seq(oldest.valid), Seq(oldest.bits))
       } else {
@@ -109,23 +114,27 @@ class VfofBuffer(implicit p: Parameters) extends VLSUModule{
 
   //Update uop vl
   io.mergeUopWriteback.map{_.ready := true.B}
-  val wbUpdateBits  = getOldest(wbIsfof, io.mergeUopWriteback.map(_.bits.uop))
-  val wbUpdateValid = wbIsfof.reduce(_ || _) && (wbUpdateBits.vpu.vuopIdx <= entries.vuopIdx) && valid && !needRedirect
+  val wbBits          = getOldest(wbIsfof, io.mergeUopWriteback.map(_.bits.uop))
+  val wbValid         = wbIsfof.reduce(_ || _)
+  val wbHasException  = wbBits.exceptionVec.asUInt.orR
+  val wbUpdateValid = wbValid && (wbBits.vpu.vl < entries.vl || wbHasException) && valid && !needRedirect && !entries.hasException
+
+  XSError(wbValid && wbHasException && valid && entries.hasException, "The same instruction triggers an exception multiple times!\n")
 
   when(wbUpdateValid) {
-    entries.vl       := wbUpdateBits.vpu.vl
-    entries.vuopIdx  := wbUpdateBits.vpu.vuopIdx
+    entries.vl                    := wbBits.vpu.vl
+    entries.hasException          := wbHasException
   }
 
   //Deq
-  io.uopWriteback.bits               := 0.U.asTypeOf(new MemExuOutput(isVector = true))
-  io.uopWriteback.bits.uop           := entries.uop
-  io.uopWriteback.bits.data          := entries.vl
-  io.uopWriteback.bits.uop.vpu.vl    := entries.vl
-  io.uopWriteback.bits.mask.get      := Fill(VLEN, 1.U)
-  io.uopWriteback.bits.uop.vpu.vmask := Fill(VLEN, 1.U)
-  io.uopWriteback.valid              := valid && entries.uop.vpu.lastUop && entries.uop.vpu.isVleff && !needRedirect
-
-  when(io.uopWriteback.fire) { valid   := false.B }
+  io.uopWriteback.bits                  := 0.U.asTypeOf(new MemExuOutput(isVector = true))
+  io.uopWriteback.bits.uop              := entries.uop
+  io.uopWriteback.bits.uop.exceptionVec := 0.U.asTypeOf(ExceptionVec())
+  io.uopWriteback.bits.data             := entries.vl
+  io.uopWriteback.bits.uop.vpu.vl       := entries.vl
+  io.uopWriteback.bits.mask.get         := Fill(VLEN, 1.U)
+  io.uopWriteback.bits.uop.vpu.vmask    := Fill(VLEN, 1.U)
+  io.uopWriteback.valid                 := valid && entries.uop.vpu.lastUop && entries.uop.vpu.isVleff && !needRedirect
+
 
 }
diff --git a/src/main/scala/xiangshan/package.scala b/src/main/scala/xiangshan/package.scala
index ab69709d95e..4b1d3a4ef09 100644
--- a/src/main/scala/xiangshan/package.scala
+++ b/src/main/scala/xiangshan/package.scala
@@ -115,6 +115,7 @@ package object xiangshan {
     def isStrided(fuOpType: UInt): Bool = fuOpType(6, 5) === "b10".U && (fuOpType(8) ^ fuOpType(7))
     def isIndexed(fuOpType: UInt): Bool = fuOpType(5) && (fuOpType(8) ^ fuOpType(7))
     def isVecLd  (fuOpType: UInt): Bool = fuOpType(8, 7) === "b01".U
+    def isFof    (fuOpType: UInt): Bool = isVecLd(fuOpType) && fuOpType(4)
   }
 
   object VstuType {
```
