# Commit Log
- Issue: #4914
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/4914
- Issue state: closed
- Tested RTL commit: -
- Related PR: #4914
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/4914
- Changed files: 10
- Additions: 54
- Deletions: 46

## Files
- `src/main/scala/xiangshan/backend/Backend.scala`
- `src/main/scala/xiangshan/backend/datapath/NewPipelineConnect.scala`
- `src/main/scala/xiangshan/mem/MemBlock.scala`
- `src/main/scala/xiangshan/mem/lsqueue/LSQWrapper.scala`
- `src/main/scala/xiangshan/mem/lsqueue/StoreMisalignBuffer.scala`
- `src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala`
- `src/main/scala/xiangshan/mem/pipeline/StoreUnit.scala`
- `src/main/scala/xiangshan/mem/vector/VMergeBuffer.scala`
- `src/main/scala/xiangshan/mem/vector/VSplit.scala`
- `src/main/scala/xiangshan/mem/vector/VecBundle.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/backend/Backend.scala b/src/main/scala/xiangshan/backend/Backend.scala
index 2a20de3f1da..345ab3cac92 100644
--- a/src/main/scala/xiangshan/backend/Backend.scala
+++ b/src/main/scala/xiangshan/backend/Backend.scala
@@ -719,15 +719,22 @@ class BackendInlinedImp(override val wrapper: BackendInlined)(implicit p: Parame
   for (i <- toMem.indices) {
     for (j <- toMem(i).indices) {
       val shouldLdCancel = LoadShouldCancel(bypassNetwork.io.toExus.mem(i)(j).bits.loadDependency, io.mem.ldCancel)
-      val needIssueTimeout = memExuBlocksHasLDU(i)(j) || memExuBlocksHasVecLoad(i)(j)
+      val needIssueTimeout = memExuBlocksHasVecLoad(i)(j)
+      val olderUopComing =
+        if (needIssueTimeout && bypassNetwork.io.toExus.mem(i)(j).bits.lqIdx.nonEmpty)
+          bypassNetwork.io.toExus.mem(i)(j).valid && toMem(i)(j).valid && !toMem(i)(j).fire &&
+            (bypassNetwork.io.toExus.mem(i)(j).bits.lqIdx.get < toMem(i)(j).bits.lqIdx.get || bypassNetwork.io.toExus.mem(i)(j).bits.sqIdx.get < toMem(i)(j).bits.sqIdx.get) // Older inst come from iq.
+        else
+          false.B
+
       val issueTimeout =
         if (needIssueTimeout)
-          Counter(0 until 16, toMem(i)(j).valid && !toMem(i)(j).fire, bypassNetwork.io.toExus.mem(i)(j).fire)._2
+          Counter(0 until 14, toMem(i)(j).valid && !toMem(i)(j).fire, bypassNetwork.io.toExus.mem(i)(j).fire)._2
         else
           false.B
 
       if (memScheduler.io.loadFinalIssueResp(i).nonEmpty && memExuBlocksHasLDU(i)(j)) {
-        memScheduler.io.loadFinalIssueResp(i)(j).valid := issueTimeout
+        memScheduler.io.loadFinalIssueResp(i)(j).valid := issueTimeout || olderUopComing
         memScheduler.io.loadFinalIssueResp(i)(j).bits.fuType := toMem(i)(j).bits.fuType
         memScheduler.io.loadFinalIssueResp(i)(j).bits.resp := RespType.block
         memScheduler.io.loadFinalIssueResp(i)(j).bits.robIdx := toMem(i)(j).bits.robIdx
@@ -737,7 +744,7 @@ class BackendInlinedImp(override val wrapper: BackendInlined)(implicit p: Parame
       }
 
       if (memScheduler.io.vecLoadFinalIssueResp(i).nonEmpty && memExuBlocksHasVecLoad(i)(j)) {
-        memScheduler.io.vecLoadFinalIssueResp(i)(j).valid := issueTimeout
+        memScheduler.io.vecLoadFinalIssueResp(i)(j).valid := issueTimeout || olderUopComing
         memScheduler.io.vecLoadFinalIssueResp(i)(j).bits.fuType := toMem(i)(j).bits.fuType
         memScheduler.io.vecLoadFinalIssueResp(i)(j).bits.resp := RespType.block
         memScheduler.io.vecLoadFinalIssueResp(i)(j).bits.robIdx := toMem(i)(j).bits.robIdx
@@ -753,7 +760,8 @@ class BackendInlinedImp(override val wrapper: BackendInlined)(implicit p: Parame
           bypassNetwork.io.toExus.mem(i)(j).bits.robIdx.needFlush(ctrlBlock.io.toExuBlock.flush) || shouldLdCancel,
           toMem(i)(j).bits.robIdx.needFlush(ctrlBlock.io.toExuBlock.flush) || issueTimeout
         ),
-        Option("bypassNetwork2toMemExus")
+        Option("bypassNetwork2toMemExus"),
+        isOlder = olderUopComing
       )
 
       if (memScheduler.io.memAddrIssueResp(i).nonEmpty && memExuBlocksHasLDU(i)(j)) {
diff --git a/src/main/scala/xiangshan/backend/datapath/NewPipelineConnect.scala b/src/main/scala/xiangshan/backend/datapath/NewPipelineConnect.scala
index 955896649ad..64d97fed4dc 100644
--- a/src/main/scala/xiangshan/backend/datapath/NewPipelineConnect.scala
+++ b/src/main/scala/xiangshan/backend/datapath/NewPipelineConnect.scala
@@ -25,9 +25,10 @@ class NewPipelineConnectPipe[T <: Data](gen: T) extends Module {
     val out = DecoupledIO(gen.cloneType)
     val rightOutFire = Input(Bool())
     val isFlush = Input(Bool())
+    val isOlder = Input(Bool())
   })
 
-  NewPipelineConnect.connect(io.in, io.out, io.rightOutFire, io.isFlush)
+  NewPipelineConnect.connect(io.in, io.out, io.rightOutFire, io.isFlush, io.isOlder)
 }
 
 object NewPipelineConnect {
@@ -35,11 +36,12 @@ object NewPipelineConnect {
                           left: DecoupledIO[T],
                           right: DecoupledIO[T],
                           rightOutFire: Bool,
-                          isFlush: Bool
+                          isFlush: Bool,
+                          isOlder: Bool
                         ): T = {
     val valid = RegInit(false.B)
 
-    left.ready := right.ready || !valid
+    left.ready := right.ready || !valid || isOlder
     val data = RegEnable(left.bits, left.fire)
 
     when (rightOutFire) { valid := false.B }
@@ -57,7 +59,8 @@ object NewPipelineConnect {
                         right: DecoupledIO[T],
                         rightOutFire: Bool,
                         isFlush: Bool,
-                        moduleName: Option[String] = None
+                        moduleName: Option[String] = None,
+                        isOlder: Bool = false.B
                       ): Option[T] = {
     if (moduleName.isDefined) {
       val pipeline = Module(new NewPipelineConnectPipe(left.bits))
@@ -65,13 +68,14 @@ object NewPipelineConnect {
       pipeline.io.in <> left
       pipeline.io.rightOutFire := rightOutFire
       pipeline.io.isFlush := isFlush
+      pipeline.io.isOlder := isOlder
       pipeline.io.out <> right
       pipeline.io.out.ready := right.ready
       None
     }
     else {
       // do not use module here to please DCE
-      Some(connect(left, right, rightOutFire, isFlush))
+      Some(connect(left, right, rightOutFire, isFlush, isOlder))
     }
   }
 }
\ No newline at end of file
diff --git a/src/main/scala/xiangshan/mem/MemBlock.scala b/src/main/scala/xiangshan/mem/MemBlock.scala
index 4c9e8ee3c04..9a5d51aa5ea 100644
--- a/src/main/scala/xiangshan/mem/MemBlock.scala
+++ b/src/main/scala/xiangshan/mem/MemBlock.scala
@@ -1249,6 +1249,9 @@ class MemBlockInlinedImp(outer: MemBlockInlined) extends LazyModuleImp(outer)
     // dtlb
     stu.io.tlb          <> dtlb_st.head.requestor(i)
     stu.io.pmp          <> pmp_check(LduCnt + HyuCnt + 1 + i).resp
+    stu.io.sqDeqPtr     <> lsq.io.sqDeqPtr
+    stu.io.sqDeqUopIdx  <> lsq.io.sqDeqUopIdx
+    stu.io.sqDeqRobIdx  <> lsq.io.sqDeqRobIdx
 
     // -------------------------
     // Store Triggers
@@ -1576,8 +1579,8 @@ class MemBlockInlinedImp(outer: MemBlockInlined) extends LazyModuleImp(outer)
     vsMergeBuffer(i).io.fromPipeline := DontCare
     vsMergeBuffer(i).io.fromSplit := DontCare
 
-    vsMergeBuffer(i).io.fromMisalignBuffer.get.flush := storeMisalignBuffer.io.toVecStoreMergeBuffer(i).flush
-    vsMergeBuffer(i).io.fromMisalignBuffer.get.mbIndex := storeMisalignBuffer.io.toVecStoreMergeBuffer(i).mbIndex
+//    vsMergeBuffer(i).io.fromMisalignBuffer.get.flush := storeMisalignBuffer.io.toVecStoreMergeBuffer(i).flush
+//    vsMergeBuffer(i).io.fromMisalignBuffer.get.mbIndex := storeMisalignBuffer.io.toVecStoreMergeBuffer(i).mbIndex
   }
 
   (0 until VstuCnt).foreach{i =>
@@ -1596,7 +1599,7 @@ class MemBlockInlinedImp(outer: MemBlockInlined) extends LazyModuleImp(outer)
     vsSplit(i).io.vstdMisalign.get.storeMisalignBufferEmpty  := storeMisalignBuffer.io.toVecSplit.empty
     vsSplit(i).io.vstdMisalign.get.storeMisalignBufferRobIdx := storeMisalignBuffer.io.toVecSplit.robIdx
     vsSplit(i).io.vstdMisalign.get.storeMisalignBufferUopIdx := storeMisalignBuffer.io.toVecSplit.uopIdx
-    vsSplit(i).io.vstdMisalign.get.storePipeEmpty := !storeUnits(i).io.s0_s1_s2_valid
+    vsSplit(i).io.vstdMisalign.get.storePipeEmpty := !storeUnits.map(_.io.s0_s1_s2_valid).reduce(_||_)
 
   }
   (0 until VlduCnt).foreach{i =>
diff --git a/src/main/scala/xiangshan/mem/lsqueue/LSQWrapper.scala b/src/main/scala/xiangshan/mem/lsqueue/LSQWrapper.scala
index 88eeedf1de5..0c51a6b7bab 100644
--- a/src/main/scala/xiangshan/mem/lsqueue/LSQWrapper.scala
+++ b/src/main/scala/xiangshan/mem/lsqueue/LSQWrapper.scala
@@ -22,15 +22,15 @@ import chisel3.util._
 import utils._
 import utility._
 import xiangshan._
-import xiangshan.backend.Bundles.{DynInst, MemExuOutput}
+import xiangshan.backend.Bundles.{DynInst, MemExuOutput, UopIdx}
 import xiangshan.backend._
-import xiangshan.backend.rob.RobLsqIO
+import xiangshan.backend.rob.{RobLsqIO, RobPtr}
 import xiangshan.backend.fu.FuType
 import xiangshan.mem.Bundles._
 import xiangshan.cache._
-import xiangshan.cache.{DCacheWordIO, DCacheLineIO, MemoryOpConstants}
+import xiangshan.cache.{DCacheLineIO, DCacheWordIO, MemoryOpConstants}
 import xiangshan.cache.{CMOReq, CMOResp}
-import xiangshan.cache.mmu.{TlbRequestIO, TlbHintIO}
+import xiangshan.cache.mmu.{TlbHintIO, TlbRequestIO}
 
 class ExceptionAddrIO(implicit p: Parameters) extends XSBundle {
   val isStore = Input(Bool())
@@ -115,6 +115,8 @@ class LsqWrapper(implicit p: Parameters) extends XSModule with HasDCacheParamete
     val sqCanAccept = Output(Bool())
     val lqDeqPtr = Output(new LqPtr)
     val sqDeqPtr = Output(new SqPtr)
+    val sqDeqUopIdx = Output(UopIdx())
+    val sqDeqRobIdx = Output(new RobPtr)
     val exceptionAddr = new ExceptionAddrIO
     val loadMisalignFull = Input(Bool())
     val misalignAllowSpec = Input(Bool())
@@ -158,6 +160,8 @@ class LsqWrapper(implicit p: Parameters) extends XSModule with HasDCacheParamete
   storeQueue.io.enq.lqCanAccept := loadQueue.io.enq.canAccept
   io.lqDeqPtr := loadQueue.io.lqDeqPtr
   io.sqDeqPtr := storeQueue.io.sqDeqPtr
+  io.sqDeqRobIdx := storeQueue.io.sqDeqRobIdx
+  io.sqDeqUopIdx := storeQueue.io.sqDeqUopIdx
   io.rarValidCount := loadQueue.io.rarValidCount
   for (i <- io.enq.req.indices) {
     loadQueue.io.enq.needAlloc(i)      := io.enq.needAlloc(i)(0)
diff --git a/src/main/scala/xiangshan/mem/lsqueue/StoreMisalignBuffer.scala b/src/main/scala/xiangshan/mem/lsqueue/StoreMisalignBuffer.scala
index 7a832655be1..1da04a0786e 100644
--- a/src/main/scala/xiangshan/mem/lsqueue/StoreMisalignBuffer.scala
+++ b/src/main/scala/xiangshan/mem/lsqueue/StoreMisalignBuffer.scala
@@ -118,7 +118,6 @@ class StoreMisalignBuffer(implicit p: Parameters) extends XSModule
     })
     val sqControl       = new StoreMaBufToSqControlIO
 
-    val toVecStoreMergeBuffer = Vec(VecStorePipelineWidth, new StoreMaBufToVecStoreMergeBufferIO)
     val toVecSplit = Output(new MisBuffertoVecSplitIO) // robIdx in misalignedBuffer
   })
 
@@ -197,13 +196,6 @@ class StoreMisalignBuffer(implicit p: Parameters) extends XSModule
     case (reqPort, index) => reqPort.req.ready := reqSelCanEnq(index) && (!req_valid || cross4KBPageBoundary && cross4KBPageEnq)
   }
 
-  io.toVecStoreMergeBuffer.zipWithIndex.map{
-    case (toStMB, index) => {
-      toStMB.flush   := req_valid && cross4KBPageBoundary && cross4KBPageEnq && UIntToOH(req.portIndex)(index)
-      toStMB.mbIndex := req.mbIndex
-    }
-  }
-
   io.toVecSplit.empty  := !req_valid
   io.toVecSplit.robIdx := req.uop.robIdx
   io.toVecSplit.uopIdx := req.uop.uopIdx
@@ -303,7 +295,6 @@ class StoreMisalignBuffer(implicit p: Parameters) extends XSModule
           globalException := false.B
           globalUncache := false.B
           isCrossPage := false.B
-          needFlushPipe := false.B
 
           globalMMIO := false.B
           globalNC   := false.B
@@ -320,7 +311,6 @@ class StoreMisalignBuffer(implicit p: Parameters) extends XSModule
           globalException := false.B
           globalUncache := false.B
           isCrossPage := false.B
-          needFlushPipe := false.B
 
           globalMMIO := false.B
           globalNC   := false.B
@@ -344,7 +334,6 @@ class StoreMisalignBuffer(implicit p: Parameters) extends XSModule
         globalException := false.B
         globalUncache := false.B
         isCrossPage := false.B
-        needFlushPipe := false.B
 
         globalMMIO := false.B
         globalNC   := false.B
@@ -612,7 +601,7 @@ class StoreMisalignBuffer(implicit p: Parameters) extends XSModule
   io.writeBack.bits.uop := req.uop
   io.writeBack.bits.uop.exceptionVec := DontCare
   StaCfg.exceptionOut.map(no => io.writeBack.bits.uop.exceptionVec(no) := (globalUncache || globalException) && exceptionVec(no))
-  io.writeBack.bits.uop.flushPipe := needFlushPipe
+  io.writeBack.bits.uop.flushPipe := false.B
   io.writeBack.bits.uop.replayInst := false.B
   io.writeBack.bits.data := DontCare
   io.writeBack.bits.isFromLoadUnit := DontCare
@@ -654,18 +643,13 @@ class StoreMisalignBuffer(implicit p: Parameters) extends XSModule
 
   when (flush || s2_needRevoke) {
     bufferState := s_idle
-    req_valid := Mux(
-      cross4KBPageEnq && cross4KBPageBoundary && !reqRedirect && !s2_needRevoke,
-      req_valid, // when s2_needRevoke is true, previous request is valid, so req_valid = true
-      false.B
-    )
+    req_valid := false.B
     curPtr := 0.U
     unSentStores := 0.U
     unWriteStores := 0.U
     globalException := false.B
     globalUncache := false.B
     isCrossPage := false.B
-    needFlushPipe := false.B
 
     globalMMIO := false.B
     globalNC   := false.B
diff --git a/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala b/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala
index c6bd077d33a..dc1da3c384d 100644
--- a/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala
+++ b/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala
@@ -26,7 +26,7 @@ import xiangshan._
 import xiangshan.ExceptionNO._
 import xiangshan.backend._
 import xiangshan.backend.rob.{RobLsqIO, RobPtr}
-import xiangshan.backend.Bundles.{DynInst, MemExuOutput}
+import xiangshan.backend.Bundles.{DynInst, MemExuOutput, UopIdx}
 import xiangshan.backend.decode.isa.bitfield.{Riscv32BitInst, XSInstBitFields}
 import xiangshan.backend.fu.FuConfig._
 import xiangshan.backend.fu.FuType
@@ -194,6 +194,8 @@ class StoreQueue(implicit p: Parameters) extends XSModule
     val stDataReadyVec = Output(Vec(StoreQueueSize, Bool()))
     val stIssuePtr = Output(new SqPtr)
     val sqDeqPtr = Output(new SqPtr)
+    val sqDeqUopIdx = Output(UopIdx())
+    val sqDeqRobIdx = Output(new RobPtr)
     val sqFull = Output(Bool())
     val sqCancelCnt = Output(UInt(log2Up(StoreQueueSize + 1).W))
     val sqDeq = Output(UInt(log2Ceil(EnsbufferWidth + 1).W))
@@ -498,6 +500,8 @@ class StoreQueue(implicit p: Parameters) extends XSModule
   io.stDataReadySqPtr := dataReadyPtrExt
   io.stIssuePtr := enqPtrExt(0)
   io.sqDeqPtr := deqPtrExt(0)
+  io.sqDeqUopIdx := uop(deqPtrExt(0).value).uopIdx
+  io.sqDeqRobIdx := uop(deqPtrExt(0).value).robIdx
 
   /**
     * Writeback store from store units
diff --git a/src/main/scala/xiangshan/mem/pipeline/StoreUnit.scala b/src/main/scala/xiangshan/mem/pipeline/StoreUnit.scala
index ecca7b756ea..6f2cd26a7fe 100644
--- a/src/main/scala/xiangshan/mem/pipeline/StoreUnit.scala
+++ b/src/main/scala/xiangshan/mem/pipeline/StoreUnit.scala
@@ -23,12 +23,13 @@ import utils._
 import utility._
 import xiangshan._
 import xiangshan.ExceptionNO._
-import xiangshan.backend.Bundles.{MemExuInput, MemExuOutput, connectSamePort}
+import xiangshan.backend.Bundles.{MemExuInput, MemExuOutput, connectSamePort, UopIdx}
 import xiangshan.backend.fu.PMPRespBundle
 import xiangshan.backend.fu.FuConfig._
 import xiangshan.backend.fu.FuType._
 import xiangshan.backend.ctrlblock.DebugLsInfoBundle
 import xiangshan.backend.fu.NewCSR._
+import xiangshan.backend.rob.RobPtr
 import xiangshan.mem.Bundles._
 import xiangshan.cache.mmu.{Pbmt, TlbCmd, TlbReq, TlbRequestIO, TlbResp}
 import xiangshan.cache.{DCacheStoreIO, DcacheStoreRequestIO, HasDCacheParameters, MemoryOpConstants, StorePrefetchReq}
@@ -70,6 +71,9 @@ class StoreUnit(implicit p: Parameters) extends XSModule
     val misalign_enq = new MisalignBufferEnqIO
     // trigger
     val fromCsrTrigger = Input(new CsrTriggerBundle)
+    val sqDeqPtr       = Input(new SqPtr)
+    val sqDeqUopIdx    = Input(UopIdx())
+    val sqDeqRobIdx    = Input(new RobPtr())
 
     val s0_s1_s2_valid = Output(Bool())
   })
@@ -176,6 +180,7 @@ class StoreUnit(implicit p: Parameters) extends XSModule
   )) + s0_addr_low
   val s0_rs_corss16Bytes = s0_addr_Up_low(4) =/= s0_addr_low(4)
   val s0_misalignWith16Byte = !s0_rs_corss16Bytes && !s0_addr_aligned && !s0_use_flow_prf
+  val s0_misalignNeedReplay = (s0_use_flow_vec || s0_rs_corss16Bytes) && !(s0_uop.sqIdx === io.sqDeqPtr || s0_uop.robIdx === io.sqDeqRobIdx && s0_uop.uopIdx === io.sqDeqUopIdx)
   s0_is128bit := Mux(s0_use_flow_ma, io.misalign_stin.bits.is128bit, is128Bit(s0_vecstin.alignedType) || s0_misalignWith16Byte)
 
   s0_fullva := Mux(
@@ -300,6 +305,7 @@ class StoreUnit(implicit p: Parameters) extends XSModule
   val s1_pbmt      = Mux(s1_tlb_hit, io.tlb.resp.bits.pbmt.head, 0.U(Pbmt.width.W))
   val s1_exception = ExceptionNO.selectByFu(s1_out.uop.exceptionVec, StaCfg).asUInt.orR
   val s1_isvec     = RegEnable(s0_out.isvec, false.B, s0_fire)
+  val s1_misalignNeedReplay = RegEnable(s0_misalignNeedReplay, false.B, s0_fire)
   //We don't want `StoreUnit` to have an additional effect on the Store of vector from a `misalignBuffer,`
   //But there are places where a marker bit is needed to enable additional processing of vector instructions.
   //For example: `StoreQueue` is exceptionBuffer
@@ -420,7 +426,7 @@ class StoreUnit(implicit p: Parameters) extends XSModule
   val s1_toMisalignBufferValid = s1_valid && !s1_tlb_miss && !s1_in.isHWPrefetch &&
     !s1_frm_mabuf && !s1_isCbo && s1_in.isMisalign && !s1_in.misalignWith16Byte &&
     GatedValidRegNext(io.csrCtrl.hd_misalign_st_enable)
-  io.misalign_enq.req.valid := s1_toMisalignBufferValid
+  io.misalign_enq.req.valid := s1_toMisalignBufferValid && !s1_misalignNeedReplay
   io.misalign_enq.req.bits.fromLsPipelineBundle(s1_in)
 
   // Pipeline
@@ -491,7 +497,7 @@ class StoreUnit(implicit p: Parameters) extends XSModule
   // goto misalignBuffer
   io.misalign_enq.revoke := s2_exception
   val s2_misalignBufferNack = !io.misalign_enq.revoke &&
-    RegEnable(s1_toMisalignBufferValid && !io.misalign_enq.req.ready, false.B, s1_fire)
+    RegEnable(s1_toMisalignBufferValid && (!io.misalign_enq.req.ready || s1_misalignNeedReplay), false.B, s1_fire)
 
   // feedback tlb miss to RS in store_s2
   val feedback_slow_valid = WireInit(false.B)
diff --git a/src/main/scala/xiangshan/mem/vector/VMergeBuffer.scala b/src/main/scala/xiangshan/mem/vector/VMergeBuffer.scala
index 3866a1a6bb4..060c3518376 100644
--- a/src/main/scala/xiangshan/mem/vector/VMergeBuffer.scala
+++ b/src/main/scala/xiangshan/mem/vector/VMergeBuffer.scala
@@ -478,9 +478,4 @@ class VSMergeBufferImp(implicit p: Parameters) extends BaseVMergeBuffer(isVStore
     sink.vecDebug.get     := DontCare
     sink
   }
-
-  // from misalignBuffer flush
-  when(io.fromMisalignBuffer.get.flush){
-    needRSReplay(io.fromMisalignBuffer.get.mbIndex) := true.B
-  }
 }
diff --git a/src/main/scala/xiangshan/mem/vector/VSplit.scala b/src/main/scala/xiangshan/mem/vector/VSplit.scala
index 3604e4098ea..5f035dfa0ca 100644
--- a/src/main/scala/xiangshan/mem/vector/VSplit.scala
+++ b/src/main/scala/xiangshan/mem/vector/VSplit.scala
@@ -472,7 +472,7 @@ class VSSplitBufferImp(implicit p: Parameters) extends VSplitBuffer(isVStore = t
 
   // send data to sq
   val vstd = io.vstd.get
-  vstd.valid := issueValid && (vecActive || !issuePreIsSplit)
+  vstd.valid := io.out.valid
   vstd.bits.uop := issueUop
   vstd.bits.uop.sqIdx := sqIdx
   vstd.bits.uop.fuType := FuType.vstu.U
diff --git a/src/main/scala/xiangshan/mem/vector/VecBundle.scala b/src/main/scala/xiangshan/mem/vector/VecBundle.scala
index 34065f1e794..a12afef06c0 100644
--- a/src/main/scala/xiangshan/mem/vector/VecBundle.scala
+++ b/src/main/scala/xiangshan/mem/vector/VecBundle.scala
@@ -261,7 +261,7 @@ class VMergeBufferIO(isVStore : Boolean=false)(implicit p: Parameters) extends V
   val toLsq               = if(isVStore) Vec(VSUopWritebackWidth, ValidIO(new FeedbackToLsqIO)) else Vec(VLUopWritebackWidth, ValidIO(new FeedbackToLsqIO)) // for lsq deq
   val feedback            = if(isVStore) Vec(VSUopWritebackWidth, ValidIO(new RSFeedback(isVector = true))) else Vec(VLUopWritebackWidth, ValidIO(new RSFeedback(isVector = true)))//for rs replay
 
-  val fromMisalignBuffer  = OptionWrapper(isVStore, Flipped(new StoreMaBufToVecStoreMergeBufferIO))
+//  val fromMisalignBuffer  = OptionWrapper(isVStore, Flipped(new StoreMaBufToVecStoreMergeBufferIO))
 }
 
 class VSegmentUnitIO(implicit p: Parameters) extends VLSUBundle{
```
