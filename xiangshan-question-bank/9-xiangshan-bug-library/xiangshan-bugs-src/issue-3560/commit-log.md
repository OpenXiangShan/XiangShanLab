# Commit Log
- Issue: #3560
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/3560
- Issue state: closed
- Tested RTL commit: -
- Related PR: #3560
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/3560
- Changed files: 9
- Additions: 48
- Deletions: 22

## Files
- `src/main/scala/xiangshan/mem/lsqueue/LoadMisalignBuffer.scala`
- `src/main/scala/xiangshan/mem/lsqueue/LoadQueueReplay.scala`
- `src/main/scala/xiangshan/mem/lsqueue/StoreMisalignBuffer.scala`
- `src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala`
- `src/main/scala/xiangshan/mem/pipeline/AtomicsUnit.scala`
- `src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala`
- `src/main/scala/xiangshan/mem/pipeline/StoreUnit.scala`
- `src/main/scala/xiangshan/mem/vector/VSegmentUnit.scala`
- `src/main/scala/xiangshan/mem/vector/VSplit.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/mem/lsqueue/LoadMisalignBuffer.scala b/src/main/scala/xiangshan/mem/lsqueue/LoadMisalignBuffer.scala
index 75501339841..ea0ecbea4f8 100644
--- a/src/main/scala/xiangshan/mem/lsqueue/LoadMisalignBuffer.scala
+++ b/src/main/scala/xiangshan/mem/lsqueue/LoadMisalignBuffer.scala
@@ -23,6 +23,7 @@ import utils._
 import utility._
 import xiangshan._
 import xiangshan.backend.fu.FuConfig._
+import xiangshan.backend.fu.FuType
 import xiangshan.backend.fu.fpu.FPU
 import xiangshan.backend.rob.RobLsqIO
 import xiangshan.cache.mmu.HasTlbConst
@@ -493,7 +494,7 @@ class LoadMisalignBuffer(implicit p: Parameters) extends XSModule
     splitLoadResp(curPtr) := io.splitLoadResp.bits
     when (isMMIO) {
       unSentLoads := 0.U
-      exceptionVec := 0.U.asTypeOf(ExceptionVec())
+      exceptionVec := ExceptionNO.selectByFu(0.U.asTypeOf(exceptionVec.cloneType), LduCfg)
       // delegate to software
       exceptionVec(loadAddrMisaligned) := true.B
     } .elsewhen (hasException) {
@@ -558,6 +559,7 @@ class LoadMisalignBuffer(implicit p: Parameters) extends XSModule
   io.writeBack.bits.uop := req.uop
   io.writeBack.bits.uop.exceptionVec := DontCare
   LduCfg.exceptionOut.map(no => io.writeBack.bits.uop.exceptionVec(no) := (globalMMIO || globalException) && exceptionVec(no))
+  io.writeBack.bits.uop.fuType := FuType.ldu.U
   io.writeBack.bits.uop.flushPipe := Mux(globalMMIO || globalException, false.B, true.B)
   io.writeBack.bits.uop.replayInst := false.B
   io.writeBack.bits.data := combinedData
diff --git a/src/main/scala/xiangshan/mem/lsqueue/LoadQueueReplay.scala b/src/main/scala/xiangshan/mem/lsqueue/LoadQueueReplay.scala
index a6e7b147e28..f8c7f8f65e9 100644
--- a/src/main/scala/xiangshan/mem/lsqueue/LoadQueueReplay.scala
+++ b/src/main/scala/xiangshan/mem/lsqueue/LoadQueueReplay.scala
@@ -535,6 +535,7 @@ class LoadQueueReplay(implicit p: Parameters) extends XSModule
     replay_req(i).valid             := s2_oldestSel(i).valid
     replay_req(i).bits              := DontCare
     replay_req(i).bits.uop          := s2_replayUop
+    replay_req(i).bits.uop.exceptionVec(loadAddrMisaligned) := false.B
     replay_req(i).bits.isvec        := s2_vecReplay.isvec
     replay_req(i).bits.isLastElem   := s2_vecReplay.isLastElem
     replay_req(i).bits.is128bit     := s2_vecReplay.is128bit
diff --git a/src/main/scala/xiangshan/mem/lsqueue/StoreMisalignBuffer.scala b/src/main/scala/xiangshan/mem/lsqueue/StoreMisalignBuffer.scala
index aa6df2fd04c..f4585d78c6e 100644
--- a/src/main/scala/xiangshan/mem/lsqueue/StoreMisalignBuffer.scala
+++ b/src/main/scala/xiangshan/mem/lsqueue/StoreMisalignBuffer.scala
@@ -456,7 +456,7 @@ class StoreMisalignBuffer(implicit p: Parameters) extends XSModule
     when (isMMIO) {
       unWriteStores := 0.U
       unSentStores := 0.U
-      exceptionVec := 0.U.asTypeOf(exceptionVec.cloneType)
+      exceptionVec := ExceptionNO.selectByFu(0.U.asTypeOf(exceptionVec.cloneType), StaCfg)
       // delegate to software
       exceptionVec(storeAddrMisaligned) := true.B
     } .elsewhen (hasException) {
diff --git a/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala b/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala
index e4c82d3b9ec..59988259ec3 100644
--- a/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala
+++ b/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala
@@ -892,6 +892,7 @@ class StoreQueue(implicit p: Parameters) extends XSModule
   // (4) scalar store: writeback to ROB (and other units): mark as writebacked
   io.mmioStout.valid := uncacheState === s_wb && !isVec(deqPtr)
   io.mmioStout.bits.uop := uncacheUop
+  io.mmioStout.bits.uop.exceptionVec := ExceptionNO.selectByFu(uncacheUop.exceptionVec, StaCfg)
   io.mmioStout.bits.uop.sqIdx := deqPtrExt(0)
   io.mmioStout.bits.uop.flushPipe := deqCanDoCbo // flush Pipeline to keep order in CMO
   io.mmioStout.bits.data := shiftDataToLow(paddrModule.io.rdata(0), dataModule.io.rdata(0).data) // dataModule.io.rdata.read(deqPtr)
diff --git a/src/main/scala/xiangshan/mem/pipeline/AtomicsUnit.scala b/src/main/scala/xiangshan/mem/pipeline/AtomicsUnit.scala
index bae2fc44470..d5f6c346c99 100644
--- a/src/main/scala/xiangshan/mem/pipeline/AtomicsUnit.scala
+++ b/src/main/scala/xiangshan/mem/pipeline/AtomicsUnit.scala
@@ -27,6 +27,7 @@ import xiangshan.cache.mmu.{TlbCmd, TlbRequestIO}
 import difftest._
 import xiangshan.ExceptionNO._
 import xiangshan.backend.fu.PMPRespBundle
+import xiangshan.backend.fu.FuType
 import xiangshan.backend.Bundles.{MemExuInput, MemExuOutput}
 import xiangshan.backend.fu.NewCSR.TriggerUtil
 import xiangshan.backend.fu.util.SdtrigExt
@@ -394,6 +395,7 @@ class AtomicsUnit(implicit p: Parameters) extends XSModule
   io.out.bits.uop := in.uop
   io.out.bits.uop.exceptionVec := exceptionVec
   io.out.bits.uop.trigger := trigger
+  io.out.bits.uop.fuType := FuType.mou.U
   io.out.bits.data := resp_data
   io.out.bits.debug.isMMIO := is_mmio
   io.out.bits.debug.paddr := paddr
diff --git a/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala b/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala
index 555a6906f16..d20121f6fff 100644
--- a/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala
+++ b/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala
@@ -26,6 +26,7 @@ import xiangshan._
 import xiangshan.backend.Bundles.{DynInst, MemExuInput, MemExuOutput}
 import xiangshan.backend.fu.PMPRespBundle
 import xiangshan.backend.fu.FuConfig._
+import xiangshan.backend.fu.FuType
 import xiangshan.backend.ctrlblock.{DebugLsInfoBundle, LsTopdownInfo}
 import xiangshan.backend.rob.RobPtr
 import xiangshan.backend.ctrlblock.DebugLsInfoBundle
@@ -1570,6 +1571,11 @@ class LoadUnit(implicit p: Parameters) extends XSModule
                           (s3_out.valid && !s3_vecout.isvec && !s3_mis_align && !s3_frm_mabuf))
   io.ldout.bits.uop.exceptionVec := ExceptionNO.selectByFu(s3_ld_wb_meta.uop.exceptionVec, LduCfg)
   io.ldout.bits.isFromLoadUnit := true.B
+  io.ldout.bits.uop.fuType := Mux(
+                                  s3_valid && s3_isvec,
+                                  FuType.vldu.U,
+                                  FuType.ldu.U
+  )
 
   // TODO: check this --hx
   // io.ldout.valid       := s3_out.valid && !s3_out.bits.uop.robIdx.needFlush(io.redirect) && !s3_vecout.isvec ||
@@ -1612,9 +1618,10 @@ class LoadUnit(implicit p: Parameters) extends XSModule
   io.vecldout.bits.vstart := s3_vecout.vstart
   io.vecldout.bits.vecTriggerMask := s3_vecout.vecTriggerMask
 
-  io.vecldout.valid := s3_out.valid && !s3_out.bits.uop.robIdx.needFlush(io.redirect) && s3_vecout.isvec ||
+  io.vecldout.valid := s3_out.valid && !s3_out.bits.uop.robIdx.needFlush(io.redirect) && s3_vecout.isvec //||
   // TODO: check this, why !io.lsq.uncache.bits.isVls before?
-    io.lsq.uncache.valid && !io.lsq.uncache.bits.uop.robIdx.needFlush(io.redirect) && !s3_out.valid && io.lsq.uncache.bits.isVls
+  // Now vector instruction don't support mmio. 
+    // io.lsq.uncache.valid && !io.lsq.uncache.bits.uop.robIdx.needFlush(io.redirect) && !s3_out.valid && io.lsq.uncache.bits.isVls
     //io.lsq.uncache.valid && !io.lsq.uncache.bits.uop.robIdx.needFlush(io.redirect) && !s3_out.valid && !io.lsq.uncache.bits.isVls
 
   io.misalign_ldout.valid     := s3_valid && (!s3_fast_rep || s3_fast_rep_canceled) && s3_frm_mabuf
diff --git a/src/main/scala/xiangshan/mem/pipeline/StoreUnit.scala b/src/main/scala/xiangshan/mem/pipeline/StoreUnit.scala
index 661f2ab984f..57c821eb091 100644
--- a/src/main/scala/xiangshan/mem/pipeline/StoreUnit.scala
+++ b/src/main/scala/xiangshan/mem/pipeline/StoreUnit.scala
@@ -126,7 +126,7 @@ class StoreUnit(implicit p: Parameters) extends XSModule
   val s0_vecBaseVaddr = s0_vecstin.basevaddr
 
   // generate addr
-  val s0_saddr = s0_stin.src(0) + SignExt(s0_uop.imm(11,0), VAddrBits)
+  val s0_saddr = s0_stin.src(0) + SignExt(s0_stin.uop.imm(11,0), VAddrBits)
   val s0_fullva = Wire(UInt(XLEN.W))
   val s0_vaddr = Mux(
     s0_use_flow_ma,
@@ -143,7 +143,7 @@ class StoreUnit(implicit p: Parameters) extends XSModule
   )
   s0_fullva := Mux(
     s0_use_flow_rs,
-    s0_stin.src(0) + SignExt(s0_uop.imm(11,0), XLEN),
+    s0_stin.src(0) + SignExt(s0_stin.uop.imm(11,0), XLEN),
     Mux(
       s0_use_flow_vec,
       s0_vecstin.vaddr,
@@ -511,6 +511,7 @@ class StoreUnit(implicit p: Parameters) extends XSModule
   val sx_valid = Wire(Vec(TotalDelayCycles + 1, Bool()))
   val sx_ready = Wire(Vec(TotalDelayCycles + 1, Bool()))
   val sx_in    = Wire(Vec(TotalDelayCycles + 1, new VecMemExuOutput(isVector = true)))
+  val sx_in_vec = Wire(Vec(TotalDelayCycles +1, Bool()))
 
   // backward ready signal
   s3_ready := sx_ready.head
@@ -530,6 +531,7 @@ class StoreUnit(implicit p: Parameters) extends XSModule
       sx_in(i).gpaddr      := s3_in.gpaddr
       sx_in(i).isForVSnonLeafPTE     := s3_in.isForVSnonLeafPTE
       sx_in(i).vecTriggerMask := s3_in.vecTriggerMask
+      sx_in_vec(i)         := s3_in.isvec          
       sx_ready(i) := !s3_valid(i) || sx_in(i).output.uop.robIdx.needFlush(io.redirect) || (if (TotalDelayCycles == 0) io.stout.ready else sx_ready(i+1))
     } else {
       val cur_kill   = sx_in(i).output.uop.robIdx.needFlush(io.redirect)
@@ -541,18 +543,20 @@ class StoreUnit(implicit p: Parameters) extends XSModule
       val sx_valid_can_go = prev_fire || cur_fire || cur_kill
       sx_valid(i) := RegEnable(Mux(prev_fire, true.B, false.B), false.B, sx_valid_can_go)
       sx_in(i) := RegEnable(sx_in(i-1), prev_fire)
+      sx_in_vec(i) := RegEnable(sx_in_vec(i-1), prev_fire)
     }
   }
   val sx_last_valid = sx_valid.takeRight(1).head
   val sx_last_ready = sx_ready.takeRight(1).head
   val sx_last_in    = sx_in.takeRight(1).head
+  val sx_last_in_vec = sx_in_vec.takeRight(1).head
   sx_last_ready := !sx_last_valid || sx_last_in.output.uop.robIdx.needFlush(io.redirect) || io.stout.ready
 
-  io.stout.valid := sx_last_valid && !sx_last_in.output.uop.robIdx.needFlush(io.redirect) && isStore(sx_last_in.output.uop.fuType)
+  io.stout.valid := sx_last_valid && !sx_last_in.output.uop.robIdx.needFlush(io.redirect) && !sx_last_in_vec //isStore(sx_last_in.output.uop.fuType)
   io.stout.bits := sx_last_in.output
   io.stout.bits.uop.exceptionVec := ExceptionNO.selectByFu(sx_last_in.output.uop.exceptionVec, StaCfg)
 
-  io.vecstout.valid := sx_last_valid && !sx_last_in.output.uop.robIdx.needFlush(io.redirect) && isVStore(sx_last_in.output.uop.fuType)
+  io.vecstout.valid := sx_last_valid && !sx_last_in.output.uop.robIdx.needFlush(io.redirect) && sx_last_in_vec //isVStore(sx_last_in.output.uop.fuType)
   // TODO: implement it!
   io.vecstout.bits.mBIndex := sx_last_in.mbIndex
   io.vecstout.bits.hit := sx_last_in.vecFeedback
diff --git a/src/main/scala/xiangshan/mem/vector/VSegmentUnit.scala b/src/main/scala/xiangshan/mem/vector/VSegmentUnit.scala
index ab409256a84..33fae96f5be 100644
--- a/src/main/scala/xiangshan/mem/vector/VSegmentUnit.scala
+++ b/src/main/scala/xiangshan/mem/vector/VSegmentUnit.scala
@@ -47,6 +47,8 @@ class VSegmentBundle(implicit p: Parameters) extends VLSUBundle
   val vl               = UInt(elemIdxBits.W)
   val uopFlowNum       = UInt(elemIdxBits.W)
   val uopFlowNumMask   = UInt(elemIdxBits.W)
+  val isVSegLoad       = Bool()
+  val isVSegStore      = Bool()
   // for exception
   val vstart           = UInt(elemIdxBits.W)
   val exceptionVaddr   = UInt(XLEN.W)
@@ -191,6 +193,8 @@ class VSegmentUnit (implicit p: Parameters) extends VLSUModule
   val baseVaddr                       = instMicroOp.baseVaddr
   val alignedType                     = instMicroOp.alignedType
   val fuType                          = instMicroOp.uop.fuType
+  val isVSegLoad                      = instMicroOp.isVSegLoad
+  val isVSegStore                     = instMicroOp.isVSegStore
   val mask                            = instMicroOp.mask
   val exceptionVec                    = instMicroOp.uop.exceptionVec
   val issueEew                        = instMicroOp.uop.vpu.veew
@@ -236,7 +240,6 @@ class VSegmentUnit (implicit p: Parameters) extends VLSUModule
   val state             = RegInit(s_idle)
   val stateNext         = WireInit(s_idle)
   val sbufferEmpty      = io.flush_sbuffer.empty
-  val isVSegLoad        = FuType.isVSegLoad(instMicroOp.uop.fuType)
   val isEnqfof          = io.in.bits.uop.fuOpType === VlduType.vleff && io.in.valid
   val isEnqFixVlUop     = isEnqfof && io.in.bits.uop.vpu.lastUop
 
@@ -357,6 +360,8 @@ class VSegmentUnit (implicit p: Parameters) extends VLSUModule
     instMicroOp.exceptionVl.bits      := io.in.bits.src_vl.asTypeOf(VConfig()).vl
     segmentOffset                     := 0.U
     instMicroOp.isFof                 := (fuOpType === VlduType.vleff) && FuType.isVSegLoad(io.in.bits.uop.fuType)
+    instMicroOp.isVSegLoad            := FuType.isVSegLoad(io.in.bits.uop.fuType)
+    instMicroOp.isVSegStore           := FuType.isVSegStore(io.in.bits.uop.fuType)
   }
   // latch data
   when(io.in.fire && !isEnqFixVlUop){
@@ -396,13 +401,13 @@ class VSegmentUnit (implicit p: Parameters) extends VLSUModule
   io.dtlb.req                         := DontCare
   io.dtlb.resp.ready                  := true.B
   io.dtlb.req.valid                   := state === s_tlb_req && segmentActive
-  io.dtlb.req.bits.cmd                := Mux(FuType.isVLoad(fuType), TlbCmd.read, TlbCmd.write)
+  io.dtlb.req.bits.cmd                := Mux(isVSegLoad, TlbCmd.read, TlbCmd.write)
   io.dtlb.req.bits.vaddr              := vaddr(VAddrBits - 1, 0)
   io.dtlb.req.bits.fullva             := vaddr
   io.dtlb.req.bits.checkfullva        := true.B
   io.dtlb.req.bits.size               := instMicroOp.alignedType(2,0)
-  io.dtlb.req.bits.memidx.is_ld       := FuType.isVLoad(fuType)
-  io.dtlb.req.bits.memidx.is_st       := FuType.isVStore(fuType)
+  io.dtlb.req.bits.memidx.is_ld       := isVSegLoad
+  io.dtlb.req.bits.memidx.is_st       := isVSegStore
   io.dtlb.req.bits.debug.robIdx       := instMicroOp.uop.robIdx
   io.dtlb.req.bits.no_translate       := false.B
   io.dtlb.req.bits.debug.pc           := instMicroOp.uop.pc
@@ -451,9 +456,8 @@ class VSegmentUnit (implicit p: Parameters) extends VLSUModule
       "b11".U   -> (vaddr(2, 0) === 0.U)  //d
     ))
     val missAligned = !addr_aligned
-    exceptionVec(loadAddrMisaligned)  := missAligned && FuType.isVSegLoad(fuType)  && canTriggerException
-    exceptionVec(storeAddrMisaligned) := missAligned && FuType.isVSegStore(fuType) && canTriggerException
-
+    exceptionVec(loadAddrMisaligned)  := missAligned && isVSegLoad  && canTriggerException
+    exceptionVec(storeAddrMisaligned) := missAligned && isVSegStore && canTriggerException
     exception_va  := exceptionVec(storePageFault) || exceptionVec(loadPageFault) ||
                      exceptionVec(storeAccessFault) || exceptionVec(loadAccessFault) ||
                      triggerBreakpoint || triggerDebugMode || missAligned
@@ -464,14 +468,14 @@ class VSegmentUnit (implicit p: Parameters) extends VLSUModule
     instMicroOp.exception_va  := exception_va
     instMicroOp.exception_gpa := exception_gpa
     // update storeAccessFault bit. Currently, we don't support vector MMIO
-    exceptionVec(loadAccessFault)  := (exceptionVec(loadAccessFault) || pmp.ld || pmp.mmio)   && FuType.isVSegLoad(fuType)  && canTriggerException
-    exceptionVec(storeAccessFault) := (exceptionVec(storeAccessFault) || pmp.st || pmp.mmio)  && FuType.isVSegStore(fuType) && canTriggerException
+    exceptionVec(loadAccessFault)  := (exceptionVec(loadAccessFault) || pmp.ld || pmp.mmio)   && isVSegLoad  && canTriggerException
+    exceptionVec(storeAccessFault) := (exceptionVec(storeAccessFault) || pmp.st || pmp.mmio)  && isVSegStore && canTriggerException
     exceptionVec(breakPoint)       := triggerBreakpoint && canTriggerException
 
-    exceptionVec(storePageFault)      := exceptionVec(storePageFault)      && FuType.isVSegStore(fuType) && canTriggerException
-    exceptionVec(loadPageFault)       := exceptionVec(loadPageFault)       && FuType.isVSegLoad(fuType)  && canTriggerException
-    exceptionVec(storeGuestPageFault) := exceptionVec(storeGuestPageFault) && FuType.isVSegStore(fuType) && canTriggerException
-    exceptionVec(loadGuestPageFault)  := exceptionVec(loadGuestPageFault)  && FuType.isVSegLoad(fuType)  && canTriggerException
+    exceptionVec(storePageFault)      := exceptionVec(storePageFault)      && isVSegStore && canTriggerException
+    exceptionVec(loadPageFault)       := exceptionVec(loadPageFault)       && isVSegLoad  && canTriggerException
+    exceptionVec(storeGuestPageFault) := exceptionVec(storeGuestPageFault) && isVSegStore && canTriggerException
+    exceptionVec(loadGuestPageFault)  := exceptionVec(loadGuestPageFault)  && isVSegLoad  && canTriggerException
 
     when(exception_va || exception_gpa || exception_pa) {
       when(canTriggerException) {
@@ -541,7 +545,7 @@ class VSegmentUnit (implicit p: Parameters) extends VLSUModule
    * rdcache req, write request don't need to query dcache, because we write element to sbuffer
    */
   io.rdcache.req                    := DontCare
-  io.rdcache.req.valid              := state === s_cache_req && FuType.isVLoad(fuType)
+  io.rdcache.req.valid              := state === s_cache_req && isVSegLoad
   io.rdcache.req.bits.cmd           := MemoryOpConstants.M_XRD
   io.rdcache.req.bits.vaddr         := latchVaddr
   io.rdcache.req.bits.mask          := mask
diff --git a/src/main/scala/xiangshan/mem/vector/VSplit.scala b/src/main/scala/xiangshan/mem/vector/VSplit.scala
index 5722fe2c23b..4d973d77055 100644
--- a/src/main/scala/xiangshan/mem/vector/VSplit.scala
+++ b/src/main/scala/xiangshan/mem/vector/VSplit.scala
@@ -28,6 +28,7 @@ import xiangshan.backend.Bundles._
 import xiangshan.mem._
 import xiangshan.backend.fu.vector.Bundles._
 import xiangshan.backend.fu.FuConfig._
+import xiangshan.backend.fu.FuType
 
 
 class VSplitPipeline(isVStore: Boolean = false)(implicit p: Parameters) extends VLSUModule{
@@ -141,6 +142,7 @@ class VSplitPipeline(isVStore: Boolean = false)(implicit p: Parameters) extends
   s0_out := DontCare
   s0_out match {case x =>
     x.uop := io.in.bits.uop
+    x.uop.imm := 0.U
     x.uop.vpu.vl := evl
     x.uop.uopIdx := uopIdx
     x.uop.numUops := numUops
@@ -367,6 +369,7 @@ abstract class VSplitBuffer(isVStore: Boolean = false)(implicit p: Parameters) e
   // data
   io.out.bits match { case x =>
     x.uop                   := issueUop
+    x.uop.imm               := 0.U
     x.uop.exceptionVec      := ExceptionNO.selectByFu(issueUop.exceptionVec, fuCfg)
     x.vaddr                 := Mux(!issuePreIsSplit, usSplitVaddr, vaddr)
     x.basevaddr             := issueBaseAddr
@@ -452,6 +455,7 @@ class VSSplitBufferImp(implicit p: Parameters) extends VSplitBuffer(isVStore = t
   vstd.valid := issueValid && (vecActive || !issuePreIsSplit)
   vstd.bits.uop := issueUop
   vstd.bits.uop.sqIdx := sqIdx
+  vstd.bits.uop.fuType := FuType.vstu.U
   vstd.bits.data := Mux(!issuePreIsSplit, usSplitData, flowData)
   vstd.bits.debug := DontCare
   vstd.bits.vdIdx.get := DontCare
@@ -464,6 +468,7 @@ class VSSplitBufferImp(implicit p: Parameters) extends VSplitBuffer(isVStore = t
 class VLSplitBufferImp(implicit p: Parameters) extends VSplitBuffer(isVStore = false){
   io.out.bits.uop.lqIdx := issueUop.lqIdx + splitIdx
   io.out.bits.uop.exceptionVec(loadAddrMisaligned) := !addrAligned && !issuePreIsSplit && io.out.bits.mask.orR
+  io.out.bits.uop.fuType := FuType.vldu.U
 }
 
 class VSSplitPipelineImp(implicit p: Parameters) extends VSplitPipeline(isVStore = true){
```
