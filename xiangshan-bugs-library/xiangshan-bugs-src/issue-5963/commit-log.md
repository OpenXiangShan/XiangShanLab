# Commit Log
- Issue: #5963
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/5963
- Issue state: closed
- Tested RTL commit: -
- Related PR: #5963
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/5963
- Changed files: 10
- Additions: 132
- Deletions: 66

## Files
- `src/main/scala/xiangshan/mem/lsqueue/ExceptionInfoGen.scala`
- `src/main/scala/xiangshan/mem/lsqueue/FreeList.scala`
- `src/main/scala/xiangshan/mem/lsqueue/LoadQueueRAR.scala`
- `src/main/scala/xiangshan/mem/lsqueue/LoadQueueReplay.scala`
- `src/main/scala/xiangshan/mem/lsqueue/LoadQueueUncache.scala`
- `src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala`
- `src/main/scala/xiangshan/mem/pipeline/NewLoadUnit.scala`
- `src/main/scala/xiangshan/mem/prefetch/L1PrefetchComponent.scala`
- `src/main/scala/xiangshan/mem/prefetch/SMSPrefetcher.scala`
- `utility`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/mem/lsqueue/ExceptionInfoGen.scala b/src/main/scala/xiangshan/mem/lsqueue/ExceptionInfoGen.scala
index 7be0b1a86c0..97f6d1b849d 100644
--- a/src/main/scala/xiangshan/mem/lsqueue/ExceptionInfoGen.scala
+++ b/src/main/scala/xiangshan/mem/lsqueue/ExceptionInfoGen.scala
@@ -64,7 +64,6 @@ class ExceptionInfoGen(implicit p: Parameters) extends XSModule with HasCircular
   private def isOlder(left: MemExceptionInfo, right: MemExceptionInfo): Bool = {
     isBefore(left.robIdx, right.robIdx) || (left.robIdx === right.robIdx && left.uopIdx < right.uopIdx)
   }
-  val selectOldestModule = Module(new SelectOldest(new MemExceptionInfo, enqPortNum, isOlder))
 
   private def GenExceptionVa(
                                 mode: UInt, isVirt: Bool, vaNeedExt: Bool,
@@ -134,7 +133,7 @@ class ExceptionInfoGen(implicit p: Parameters) extends XSModule with HasCircular
     port.valid && !port.bits.robIdx.needFlush(io.redirect)
   }
   /*===================================================== s1 stage ===================================================*/
-  // select an oldest enq exception, compare the current exception.
+  // select an oldest enq exception on group
   private val s1Valid = s0Valid.map(x => RegNext(x))
   private val s1Bits  = io.req.map(x => RegNext(x.bits)) // for timing, don't use RegEnable
 
@@ -143,15 +142,46 @@ class ExceptionInfoGen(implicit p: Parameters) extends XSModule with HasCircular
     v && p.hasException && !p.robIdx.needFlush(io.redirect)
   } // for timing, generate selectValid here
 
-  selectOldestModule.io.in.zipWithIndex.map{case (sink, i) =>
-    sink.valid := selectValid(i)
-    sink.bits := s1Bits(i)
+  private val groupSize = 4
+  private val s1SelectValidGroups = selectValid.grouped(groupSize).toSeq
+  private val s1BitsGroups = s1Bits.grouped(groupSize).toSeq
+  private val numSelectGroups = s1SelectValidGroups.length
+
+  private val s1Oldest = s1SelectValidGroups.zip(s1BitsGroups).zipWithIndex.map { case ((vg, bg), g) =>
+    val m = Module(
+      new SelectOldest(new MemExceptionInfo, bg.length, isOlder)
+        .suggestName(s"s1SelectOldestGroup_$g")
+    )
+    m.io.in.zipWithIndex.foreach { case (sink, i) =>
+      sink.valid := vg(i)
+      sink.bits  := bg(i)
+    }
+    (m.io.out.valid, m.io.out.bits)
+  }
+
+  /*===================================================== s2 stage ===================================================*/
+  // select an oldest enq exception, compare the current exception.
+  private val s2Valid = s1Oldest.map { case (v, _) => RegNext(v) }
+  private val s2Bits  = s1Oldest.map { case (_, b) => RegNext(b) }
+  private val s2SelectValid = s2Valid.zip(s2Bits).map{case (v, p) =>
+    v && p.hasException && !p.robIdx.needFlush(io.redirect)
+  } 
+
+  private val s2SelectOldestModule = Module(
+    new SelectOldest(new MemExceptionInfo, numSelectGroups, isOlder)
+      .suggestName("s2SelectOldest")
+  )
+
+  s2SelectOldestModule.io.in.zipWithIndex.foreach{case (sink, i) =>
+    sink.valid := s2SelectValid(i)
+    sink.bits := s2Bits(i)
   }
-  private val oldestBits = selectOldestModule.io.out.bits
-  private val s1OutValid = selectOldestModule.io.out.valid
+
+  private val oldestBits = s2SelectOldestModule.io.out.bits
+  private val outValid = s2SelectOldestModule.io.out.valid
 
   when(currentValid) {
-    when(s1OutValid) {
+    when(outValid) {
       when(currentExcp.robIdx > oldestBits.robIdx || oldestBits.robIdx === currentExcp.robIdx && currentExcp.uopIdx > oldestBits.uopIdx) {
         currentExcp := oldestBits
       }
@@ -160,9 +190,9 @@ class ExceptionInfoGen(implicit p: Parameters) extends XSModule with HasCircular
     currentExcp  := oldestBits
   }
 
-  when(!currentValid && s1OutValid) { // TODO: need valid ? maby for debug.
+  when(!currentValid && outValid) { // TODO: need valid ? maby for debug.
     currentValid := true.B
-  }.elsewhen(currentValid && currentExcp.robIdx.needFlush(io.redirect) && !s1OutValid) {
+  }.elsewhen(currentValid && currentExcp.robIdx.needFlush(io.redirect) && !outValid) {
     currentValid := false.B
   }
 
diff --git a/src/main/scala/xiangshan/mem/lsqueue/FreeList.scala b/src/main/scala/xiangshan/mem/lsqueue/FreeList.scala
index 4861e70bc88..30c00c7ec1c 100644
--- a/src/main/scala/xiangshan/mem/lsqueue/FreeList.scala
+++ b/src/main/scala/xiangshan/mem/lsqueue/FreeList.scala
@@ -126,7 +126,7 @@ class FreeList(size: Int, allocWidth: Int, freeWidth: Int, enablePreAlloc: Boole
 
   headPtrNext := headPtr + numAllocate
   headPtr := Mux(doAllocate, headPtrNext, headPtr)
-  freeSlotCnt := distanceBetween(tailPtrNext, headPtrNext)
+  freeSlotCnt := freeSlotCnt + PopCount(freeReq) - numAllocate
 
   io.empty := freeSlotCnt === 0.U
   io.validCount := size.U - freeSlotCnt
diff --git a/src/main/scala/xiangshan/mem/lsqueue/LoadQueueRAR.scala b/src/main/scala/xiangshan/mem/lsqueue/LoadQueueRAR.scala
index d8af855cd1e..5926b12d2d6 100644
--- a/src/main/scala/xiangshan/mem/lsqueue/LoadQueueRAR.scala
+++ b/src/main/scala/xiangshan/mem/lsqueue/LoadQueueRAR.scala
@@ -163,7 +163,7 @@ class LoadQueueRAR(implicit p: Parameters) extends XSModule
     enq.ready := Mux(needEnqueue(w), canAccept, true.B)
 
     enqIndexVec(w) := enqIndex
-    when (needEnqueue(w) && enq.ready) {
+    when (needEnqueue(w) && canAccept) {
       acceptedVec(w) := true.B
 
       freeList.io.doAllocate(w) := true.B
diff --git a/src/main/scala/xiangshan/mem/lsqueue/LoadQueueReplay.scala b/src/main/scala/xiangshan/mem/lsqueue/LoadQueueReplay.scala
index 58200f5b80b..4d7c9e3ff92 100644
--- a/src/main/scala/xiangshan/mem/lsqueue/LoadQueueReplay.scala
+++ b/src/main/scala/xiangshan/mem/lsqueue/LoadQueueReplay.scala
@@ -277,6 +277,8 @@ class LoadQueueReplay(implicit p: Parameters) extends XSModule
    */
   val canEnqueue = io.enq.map(_.valid)
   val cancelEnq = io.enq.map(enq => enq.bits.uop.robIdx.needFlush(io.redirect))
+  // Use the producer-side need_rep directly so replay admission does not
+  // need to re-derive "has any replay cause" from rep_info.cause on this path.
   val needReplay = io.enq.map(enq => enq.bits.rep_info.need_rep)
   val loadReplay = io.enq.map(enq => enq.bits.isLoadReplay)
   val needEnqueue = VecInit((0 until LoadPipelineWidth).map(w => {
@@ -654,6 +656,24 @@ class LoadQueueReplay(implicit p: Parameters) extends XSModule
       allocated(enqIndex) && !enq.bits.isLoadReplay,
       p"LoadQueueReplay: can not accept more load, check: ldu $w, robIdx $debug_robIdx!")
 
+    val enqFireBase = enq.fire && !cancelEnq(w)
+    val replayInfo = enq.bits.rep_info
+    val isMA = replayInfo.cause(LoadReplayCauses.C_MA)
+    val isFF = replayInfo.cause(LoadReplayCauses.C_FF)
+    val nextBlockSqIdx = Mux(isMA, replayInfo.addr_inv_sq_idx, replayInfo.data_inv_sq_idx)
+
+    // special case: st-ld violation
+    when (enqFireBase && (isMA || isFF)) {
+      blockSqIdx(enqIndex) := nextBlockSqIdx
+    }
+
+    // special case: data forward fail
+    when (enqFireBase && isMA) {
+      strict(enqIndex) := enq.bits.uop.loadWaitStrict
+    }.otherwise{
+      strict(enqIndex) := false.B
+    }
+
     when (needEnqueue(w) && enq.ready) {
       freeList.io.doAllocate(w) := !enq.bits.isLoadReplay
 
@@ -693,7 +713,6 @@ class LoadQueueReplay(implicit p: Parameters) extends XSModule
 
       // init
       blocking(enqIndex)     := true.B
-      strict(enqIndex)       := false.B
 
       // update blocking pointer
       when (replayInfo.cause(LoadReplayCauses.C_BC) ||
@@ -719,16 +738,6 @@ class LoadQueueReplay(implicit p: Parameters) extends XSModule
                               !(RegNext(io.loadWakeup.valid) && RegNext(io.loadWakeup.bits.mshrId) === replayInfo.mshr_id) // no refill in last cycle
       }
 
-      // special case: st-ld violation
-      when (replayInfo.cause(LoadReplayCauses.C_MA)) {
-        blockSqIdx(enqIndex) := replayInfo.addr_inv_sq_idx
-        strict(enqIndex) := enq.bits.uop.loadWaitStrict
-      }
-
-      // special case: data forward fail
-      when (replayInfo.cause(LoadReplayCauses.C_FF)) {
-        blockSqIdx(enqIndex) := replayInfo.data_inv_sq_idx
-      }
       // extra info
       replayCarryReg(enqIndex) := replayInfo.rep_carry
       replacementUpdated(enqIndex) := enq.bits.replacementUpdated
diff --git a/src/main/scala/xiangshan/mem/lsqueue/LoadQueueUncache.scala b/src/main/scala/xiangshan/mem/lsqueue/LoadQueueUncache.scala
index de0611093c2..3ef3206dd3e 100644
--- a/src/main/scala/xiangshan/mem/lsqueue/LoadQueueUncache.scala
+++ b/src/main/scala/xiangshan/mem/lsqueue/LoadQueueUncache.scala
@@ -387,7 +387,7 @@ class LoadQueueUncache(implicit p: Parameters) extends XSModule
   val mmioSelect = entries.map(e => e.io.mmioSelect).reduce(_ || _)
   val mmioReq = Wire(DecoupledIO(io.uncache.req.bits.cloneType))
   // TODO lyq: It's best to choose in robIdx order / the order in which they enter
-  val ncReqArb = Module(new RRArbiterInit(io.uncache.req.bits.cloneType, LoadUncacheBufferSize))
+  val ncReqArb = Module(new TwoLevelRRArbiter(io.uncache.req.bits.cloneType, LoadUncacheBufferSize))
 
   val ncOutValidVec = VecInit(entries.map(e => e.io.ncOut.valid))
   val ncOutValidVecRem = SubVec.getMaskRem(ncOutValidVec, NC_WB_MOD)
diff --git a/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala b/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala
index 37db1b14eaa..4b4d9044b60 100644
--- a/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala
+++ b/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala
@@ -55,6 +55,8 @@ class LoadToLsqReplayIO(implicit p: Parameters) extends XSBundle
   val rep_carry       = new ReplayCarry(nWays)
   // data in last beat
   val last_beat       = Bool()
+  // whether any replay cause is active for this request
+  val need_rep        = Bool()
   // replay cause
   val cause           = Vec(LoadReplayCauses.allCauses, Bool())
   // performance debug information
@@ -77,5 +79,4 @@ class LoadToLsqReplayIO(implicit p: Parameters) extends XSBundle
   def nuke          = cause(LoadReplayCauses.C_NK)
   def mmioOrNc      = cause(LoadReplayCauses.C_UNCACHE)
   def storeMultiFwd = cause(LoadReplayCauses.C_SMF)
-  def need_rep      = cause.asUInt.orR
 }
diff --git a/src/main/scala/xiangshan/mem/pipeline/NewLoadUnit.scala b/src/main/scala/xiangshan/mem/pipeline/NewLoadUnit.scala
index e4f1d8675c2..a60db461e0f 100644
--- a/src/main/scala/xiangshan/mem/pipeline/NewLoadUnit.scala
+++ b/src/main/scala/xiangshan/mem/pipeline/NewLoadUnit.scala
@@ -593,7 +593,16 @@ class LoadUnitS1(param: ExeUnitParams)(
   val redirectNext = Wire(redirect.cloneType)
   redirectNext.valid := GatedValidRegNext(redirect.valid)
   redirectNext.bits := RegEnable(redirect.bits, redirect.valid)
-  val kill = io.kill || isSwInstrPrefetch || robIdx.needFlush(redirect) || robIdx.needFlush(redirectNext) || !pipeIn.valid
+
+  val redirectNextNext = Wire(redirect.cloneType)
+  redirectNextNext.valid := GatedValidRegNext(redirectNext.valid)
+  redirectNextNext.bits := RegEnable(redirectNext.bits, redirectNext.valid)
+  
+  val isUnalignTail = LoadEntrance.isUnalignTail(entrance)
+
+  val kill = !pipeIn.valid || io.kill || isSwInstrPrefetch ||
+             robIdx.needFlush(redirect) || robIdx.needFlush(redirectNext) || 
+             (robIdx.needFlush(redirectNextNext) && isUnalignTail)
 
   /**
     * Tlb & DCache
@@ -649,7 +658,7 @@ class LoadUnitS1(param: ExeUnitParams)(
   /**
     * Unalign tail inject to s0
     */
-  val unalignTailInjectValid = pipeIn.valid && !kill && in.unalignHead.get
+  val unalignTailInjectValid = pipeIn.valid && in.unalignHead.get
   val unalignTail = Wire(io.unalignTail.bits.cloneType)
   connectSamePort(unalignTail, in)
   unalignTail.entrance := LoadEntrance.unalignTail.U
@@ -1040,7 +1049,7 @@ class LoadUnitS2(param: ExeUnitParams)(
     * For timing considerations, violation check requests issued in s2 do not need to be accurate. But MUST ensure that
     * accurate `revoke` signals are given in s3 to withdraw requests that do not require violation check.
     */
-  val nukeQueryReqValid = troubleMaker && !(prevStageNuke || cause(C_BC))
+  val nukeQueryReqValid = troubleMaker && !prevStageNuke
   val nukeQueryReq = Wire(new LoadNukeQueryReq)
   nukeQueryReq.robIdx := robIdx
   nukeQueryReq.paddr := paddr
@@ -1444,6 +1453,7 @@ class LoadUnitS3(param: ExeUnitParams)(
   val lqWriteValid = pipeIn.valid && !doFastReplay && endPipe
   val lqWriteReady = io.lqWrite.ready
   val lqWriteCause = Mux(s4HeadValid && s4HeadShouldReplay, s4HeadReplayCause, cause)
+  val lqWriteNeedReplay = lqWriteCause.asUInt.orR
   val lqWriteCauseOH = PriorityEncoderOH(lqWriteCause)
   val lqWrite = Wire(new LqWriteBundle)
   val lqWriteMshrId = Mux(s4HeadCacheMiss && s4HeadValid, s4HeadMshrId, in.mshrId.get)
@@ -1485,6 +1495,7 @@ class LoadUnitS3(param: ExeUnitParams)(
   lqWrite.rep_info.addr_inv_sq_idx := in.addrInvalidSqIdx.get
   lqWrite.rep_info.rep_carry := DontCare
   lqWrite.rep_info.last_beat := paddr(log2Up(refillBytes))
+  lqWrite.rep_info.need_rep := lqWriteNeedReplay
   lqWrite.rep_info.cause := lqWriteCauseOH
   lqWrite.rep_info.debug := uop.perfDebugInfo
   lqWrite.rep_info.tlb_id := in.tlbId.get
diff --git a/src/main/scala/xiangshan/mem/prefetch/L1PrefetchComponent.scala b/src/main/scala/xiangshan/mem/prefetch/L1PrefetchComponent.scala
index 031cd926459..06c442e03e1 100644
--- a/src/main/scala/xiangshan/mem/prefetch/L1PrefetchComponent.scala
+++ b/src/main/scala/xiangshan/mem/prefetch/L1PrefetchComponent.scala
@@ -141,10 +141,12 @@ class TrainFilter(size: Int, name: String, hasLoadTrain: Boolean=true, hasStoreT
   val stReorderOpt = io.stTrainOpt.map { stTrain =>
     HwSort(VecInit(stTrain.map { case x => DataWithPtr(x.valid, x.bits, x.bits.robIdx) }))
   }
-  val reqs = ldReorderOpt.map(_.map(_.bits)).getOrElse(Seq.empty) ++
-    stReorderOpt.map(_.map(_.bits)).getOrElse(Seq.empty)
-  val reqsValid = ldReorderOpt.map(_.map(_.valid)).getOrElse(Seq.empty) ++
-    stReorderOpt.map(_.map(_.valid)).getOrElse(Seq.empty)
+  val ldReorderBufferedOpt = ldReorderOpt.map(reorder => RegNext(reorder, 0.U.asTypeOf(reorder)))
+  val stReorderBufferedOpt = stReorderOpt.map(reorder => RegNext(reorder, 0.U.asTypeOf(reorder)))
+  val reqs = ldReorderBufferedOpt.map(_.map(_.bits)).getOrElse(Seq.empty) ++
+    stReorderBufferedOpt.map(_.map(_.bits)).getOrElse(Seq.empty)
+  val reqsValid = ldReorderBufferedOpt.map(_.map(_.valid)).getOrElse(Seq.empty) ++
+    stReorderBufferedOpt.map(_.map(_.valid)).getOrElse(Seq.empty)
 
   val needAlloc = Wire(Vec(enqLen, Bool()))
   val canAlloc = Wire(Vec(enqLen, Bool()))
@@ -360,12 +362,13 @@ class MutiLevelPrefetchFilter(implicit p: Parameters) extends XSModule with HasL
 
   val l1_replacement = new ValidPseudoLRU(MLP_L1_SIZE)
   val l2_replacement = new ValidPseudoLRU(MLP_L2L3_SIZE)
-  val tlb_req_arb = Module(new RRArbiterInit(new TlbReq, MLP_SIZE))
-  val l1_pf_req_arb = Module(new RRArbiterInit(new Bundle {
+  val l1_tlb_req_arb = Module(new RRArbiterInit(new TlbReq, MLP_L1_SIZE))
+  val l2_tlb_req_arb = Module(new RRArbiterInit(new TlbReq, MLP_L2L3_SIZE))
+  val l1_pf_req_arb = Module(new TwoLevelRRArbiter(new Bundle {
     val req = new L1PrefetchReq
     val debug_vaddr = UInt(VAddrBits.W)
   }, MLP_L1_SIZE))
-  val l2_pf_req_arb = Module(new RRArbiterInit(new Bundle {
+  val l2_pf_req_arb = Module(new TwoLevelRRArbiter(new Bundle {
     val req = new L2PrefetchReq
     val debug_vaddr = UInt(VAddrBits.W)
   }, MLP_L2L3_SIZE))
@@ -535,38 +538,50 @@ class MutiLevelPrefetchFilter(implicit p: Parameters) extends XSModule with HasL
 
   // tlb req
   // s0: arb all tlb reqs
-  val s0_tlb_fire_vec = VecInit((0 until MLP_SIZE).map{case i => tlb_req_arb.io.in(i).fire})
+  val s0_tlb_fire_vec = VecInit(l1_tlb_req_arb.io.in.map(_.fire) ++ l2_tlb_req_arb.io.in.map(_.fire))
   val s1_tlb_fire_vec = GatedValidRegNext(s0_tlb_fire_vec)
   val s2_tlb_fire_vec = GatedValidRegNext(s1_tlb_fire_vec)
   val s3_tlb_fire_vec = GatedValidRegNext(s2_tlb_fire_vec)
   val not_tlbing_vec = VecInit((0 until MLP_SIZE).map{case i =>
     !s1_tlb_fire_vec(i) && !s2_tlb_fire_vec(i) && !s3_tlb_fire_vec(i)
   })
-
-  for(i <- 0 until MLP_SIZE) {
+  for(i <- 0 until MLP_L1_SIZE) {
     val l1_evict = s1_l1_alloc && (s1_l1_index === i.U)
-    val l2_evict = s1_l2_alloc && ((s1_l2_index + MLP_L1_SIZE.U) === i.U)
-    if(i < MLP_L1_SIZE) {
-      tlb_req_arb.io.in(i).valid := l1_valids(i) && l1_array(i).is_vaddr && not_tlbing_vec(i) && !l1_evict
-      tlb_req_arb.io.in(i).bits.vaddr := l1_array(i).get_tlb_va()
-    }else {
-      tlb_req_arb.io.in(i).valid := l2_valids(i - MLP_L1_SIZE) && l2_array(i - MLP_L1_SIZE).is_vaddr && not_tlbing_vec(i) && !l2_evict
-      tlb_req_arb.io.in(i).bits.vaddr := l2_array(i - MLP_L1_SIZE).get_tlb_va()
-    }
-    tlb_req_arb.io.in(i).bits.cmd := TlbCmd.read
-    tlb_req_arb.io.in(i).bits.isPrefetch := true.B
-    tlb_req_arb.io.in(i).bits.size := 3.U
-    tlb_req_arb.io.in(i).bits.kill := false.B
-    tlb_req_arb.io.in(i).bits.no_translate := false.B
-    tlb_req_arb.io.in(i).bits.fullva := 0.U
-    tlb_req_arb.io.in(i).bits.checkfullva := false.B
-    tlb_req_arb.io.in(i).bits.memidx := DontCare
-    tlb_req_arb.io.in(i).bits.debug := DontCare
-    tlb_req_arb.io.in(i).bits.hlvx := DontCare
-    tlb_req_arb.io.in(i).bits.hyperinst := DontCare
-    tlb_req_arb.io.in(i).bits.pmp_addr  := DontCare
+    l1_tlb_req_arb.io.in(i).valid := l1_valids(i) && l1_array(i).is_vaddr && not_tlbing_vec(i) && !l1_evict
+    l1_tlb_req_arb.io.in(i).bits.vaddr := l1_array(i).get_tlb_va()
+    l1_tlb_req_arb.io.in(i).bits.cmd := TlbCmd.read
+    l1_tlb_req_arb.io.in(i).bits.isPrefetch := true.B
+    l1_tlb_req_arb.io.in(i).bits.size := 3.U
+    l1_tlb_req_arb.io.in(i).bits.kill := false.B
+    l1_tlb_req_arb.io.in(i).bits.no_translate := false.B
+    l1_tlb_req_arb.io.in(i).bits.fullva := 0.U
+    l1_tlb_req_arb.io.in(i).bits.checkfullva := false.B
+    l1_tlb_req_arb.io.in(i).bits.memidx := DontCare
+    l1_tlb_req_arb.io.in(i).bits.debug := DontCare
+    l1_tlb_req_arb.io.in(i).bits.hlvx := DontCare
+    l1_tlb_req_arb.io.in(i).bits.hyperinst := DontCare
+    l1_tlb_req_arb.io.in(i).bits.pmp_addr  := DontCare
   }
-
+  for(i <- 0 until MLP_L2L3_SIZE) {
+    val l2_evict = s1_l2_alloc && (s1_l2_index === i.U)
+    l2_tlb_req_arb.io.in(i).valid := l2_valids(i) && l2_array(i).is_vaddr && not_tlbing_vec(i + MLP_L1_SIZE) && !l2_evict
+    l2_tlb_req_arb.io.in(i).bits.vaddr := l2_array(i).get_tlb_va()
+    l2_tlb_req_arb.io.in(i).bits.cmd := TlbCmd.read
+    l2_tlb_req_arb.io.in(i).bits.isPrefetch := true.B
+    l2_tlb_req_arb.io.in(i).bits.size := 3.U
+    l2_tlb_req_arb.io.in(i).bits.kill := false.B
+    l2_tlb_req_arb.io.in(i).bits.no_translate := false.B
+    l2_tlb_req_arb.io.in(i).bits.fullva := 0.U
+    l2_tlb_req_arb.io.in(i).bits.checkfullva := false.B
+    l2_tlb_req_arb.io.in(i).bits.memidx := DontCare
+    l2_tlb_req_arb.io.in(i).bits.debug := DontCare
+    l2_tlb_req_arb.io.in(i).bits.hlvx := DontCare
+    l2_tlb_req_arb.io.in(i).bits.hyperinst := DontCare
+    l2_tlb_req_arb.io.in(i).bits.pmp_addr  := DontCare
+  }
+  val tlb_req_arb = Module(new RRArbiterInit(new TlbReq, 2))
+  tlb_req_arb.io.in(0) <> l1_tlb_req_arb.io.out
+  tlb_req_arb.io.in(1) <> l2_tlb_req_arb.io.out
   assert(PopCount(s0_tlb_fire_vec) <= 1.U, "s0_tlb_fire_vec should be one-hot or empty")
 
   // s1: send out the req
@@ -694,8 +709,8 @@ class MutiLevelPrefetchFilter(implicit p: Parameters) extends XSModule with HasL
 
   // l2 pf
   // s0: generate prefetch req paddr per entry, arb them, sent out
-  io.l2_pf_addr.valid := l2_pf_req_arb.io.out.valid
-  io.l2_pf_addr.bits := l2_pf_req_arb.io.out.bits.req
+  io.l2_pf_addr.valid := RegNext(l2_pf_req_arb.io.out.valid)
+  io.l2_pf_addr.bits := RegEnable(l2_pf_req_arb.io.out.bits.req, l2_pf_req_arb.io.out.valid)
 
   l2_pf_req_arb.io.out.ready := io.l2_pf_addr.ready
 
@@ -739,8 +754,8 @@ class MutiLevelPrefetchFilter(implicit p: Parameters) extends XSModule with HasL
 
   // last level cache pf
   // s0: generate prefetch req paddr per entry, arb them, sent out
-  io.l3_pf_addr.valid := l3_pf_req_arb.io.out.valid
-  io.l3_pf_addr.bits := l3_pf_req_arb.io.out.bits
+  io.l3_pf_addr.valid := RegNext(l3_pf_req_arb.io.out.valid)
+  io.l3_pf_addr.bits := RegEnable(l3_pf_req_arb.io.out.bits, l3_pf_req_arb.io.out.valid)
 
   l3_pf_req_arb.io.out.ready := io.l3_pf_addr.ready
 
diff --git a/src/main/scala/xiangshan/mem/prefetch/SMSPrefetcher.scala b/src/main/scala/xiangshan/mem/prefetch/SMSPrefetcher.scala
index cea89db3c3f..29bdf63a0ba 100644
--- a/src/main/scala/xiangshan/mem/prefetch/SMSPrefetcher.scala
+++ b/src/main/scala/xiangshan/mem/prefetch/SMSPrefetcher.scala
@@ -941,8 +941,8 @@ class PrefetchFilter()(implicit p: Parameters) extends XSModule with HasSMSModul
   val prev_valid = GatedValidRegNext(io.gen_req.valid, false.B)
   val prev_gen_req = RegEnable(io.gen_req.bits, io.gen_req.valid)
 
-  val tlb_req_arb = Module(new RRArbiterInit(new TlbReq, smsParams.pf_filter_size))
-  val pf_req_arb = Module(new RRArbiterInit(UInt(PAddrBits.W), smsParams.pf_filter_size))
+  val tlb_req_arb = Module(new TwoLevelRRArbiter(new TlbReq, smsParams.pf_filter_size))
+  val pf_req_arb = Module(new TwoLevelRRArbiter(UInt(PAddrBits.W), smsParams.pf_filter_size))
 
   io.l2_pf_addr <> pf_req_arb.io.out
   io.pf_alias_bits := Mux1H(entries.zipWithIndex.map({
diff --git a/utility b/utility
index bae2aefdcc2..bab34605bab 160000
--- a/utility
+++ b/utility
@@ -1 +1 @@
-Subproject commit bae2aefdcc23e0cfa883a53a4c0c56cfa11be30a
+Subproject commit bab34605babce74732f7f441c5d126a0869285bd
```
