# Commit Log
- Issue: #3467
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/3467
- Issue state: closed
- Tested RTL commit: -
- Related PR: #3467
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/3467
- Changed files: 20
- Additions: 594
- Deletions: 273

## Files
- `src/main/scala/xiangshan/backend/MemBlock.scala`
- `src/main/scala/xiangshan/cache/dcache/DCacheWrapper.scala`
- `src/main/scala/xiangshan/cache/dcache/data/BankedDataArray.scala`
- `src/main/scala/xiangshan/cache/dcache/loadpipe/LoadPipe.scala`
- `src/main/scala/xiangshan/cache/dcache/mainpipe/MainPipe.scala`
- `src/main/scala/xiangshan/cache/dcache/mainpipe/MissQueue.scala`
- `src/main/scala/xiangshan/cache/dcache/mainpipe/WritebackQueue.scala`
- `src/main/scala/xiangshan/cache/dcache/meta/AsynchronousMetaArray.scala`
- `src/main/scala/xiangshan/cache/mmu/MMUBundle.scala`
- `src/main/scala/xiangshan/cache/mmu/TLB.scala`
- `src/main/scala/xiangshan/mem/MemCommon.scala`
- `src/main/scala/xiangshan/mem/lsqueue/LoadQueue.scala`
- `src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala`
- `src/main/scala/xiangshan/mem/lsqueue/VirtualLoadQueue.scala`
- `src/main/scala/xiangshan/mem/pipeline/HybridUnit.scala`
- `src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala`
- `src/main/scala/xiangshan/mem/vector/VMergeBuffer.scala`
- `src/main/scala/xiangshan/mem/vector/VSegmentUnit.scala`
- `src/main/scala/xiangshan/mem/vector/VSplit.scala`
- `src/main/scala/xiangshan/mem/vector/VecCommon.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/backend/MemBlock.scala b/src/main/scala/xiangshan/backend/MemBlock.scala
index e3a33271283..e0e9bed5bd2 100644
--- a/src/main/scala/xiangshan/backend/MemBlock.scala
+++ b/src/main/scala/xiangshan/backend/MemBlock.scala
@@ -599,7 +599,7 @@ class MemBlockImp(outer: MemBlock) extends LazyModuleImp(outer)
   dtlb.map(_.sfence := sfence)
   dtlb.map(_.csr := tlbcsr)
   dtlb.map(_.flushPipe.map(a => a := false.B)) // non-block doesn't need
-  dtlb.map(_.redirect := io.redirect)
+  dtlb.map(_.redirect := redirect)
   if (refillBothTlb) {
     require(ldtlbParams.outReplace == sttlbParams.outReplace)
     require(ldtlbParams.outReplace == hytlbParams.outReplace)
@@ -805,7 +805,7 @@ class MemBlockImp(outer: MemBlock) extends LazyModuleImp(outer)
       val vsegmentDtlbReqValid = vSegmentUnit.io.dtlb.req.valid // segment tlb resquest need to delay 1 cycle
       dtlb_reqs.take(LduCnt)(i).req.valid := loadUnits(i).io.tlb.req.valid || RegNext(vsegmentDtlbReqValid)
       vSegmentUnit.io.dtlb.req.ready      := dtlb_reqs.take(LduCnt)(i).req.ready
-      dtlb_reqs.take(LduCnt)(i).req.bits  := Mux1H(Seq(
+      dtlb_reqs.take(LduCnt)(i).req.bits  := ParallelPriorityMux(Seq(
         RegNext(vsegmentDtlbReqValid)     -> RegEnable(vSegmentUnit.io.dtlb.req.bits, vsegmentDtlbReqValid),
         loadUnits(i).io.tlb.req.valid     -> loadUnits(i).io.tlb.req.bits
       ))
@@ -1259,11 +1259,17 @@ class MemBlockImp(outer: MemBlock) extends LazyModuleImp(outer)
   }
 
   // mmio store writeback will use store writeback port 0
-  lsq.io.mmioStout.ready := false.B
-  when (lsq.io.mmioStout.valid && !storeUnits(0).io.stout.valid) {
+  val mmioStout = WireInit(0.U.asTypeOf(lsq.io.mmioStout))
+  NewPipelineConnect(
+    lsq.io.mmioStout, mmioStout, mmioStout.fire,
+    false.B,
+    Option("mmioStOutConnect")
+  )
+  mmioStout.ready := false.B
+  when (mmioStout.valid && !storeUnits(0).io.stout.valid) {
     stOut(0).valid := true.B
-    stOut(0).bits  := lsq.io.mmioStout.bits
-    lsq.io.mmioStout.ready := true.B
+    stOut(0).bits  := mmioStout.bits
+    mmioStout.ready := true.B
   }
   // vec mmio writeback
   lsq.io.vecmmioStout.ready := false.B
@@ -1466,9 +1472,7 @@ class MemBlockImp(outer: MemBlock) extends LazyModuleImp(outer)
     vsSplit(i).io.toMergeBuffer <> vsMergeBuffer(i).io.fromSplit.head
     NewPipelineConnect(
       vsSplit(i).io.out, storeUnits(i).io.vecstin, storeUnits(i).io.vecstin.fire,
-      Mux(vsSplit(i).io.out.fire,
-          vsSplit(i).io.out.bits.uop.robIdx.needFlush(io.redirect),
-          storeUnits(i).io.vecstin.bits.uop.robIdx.needFlush(io.redirect)),
+      vsSplit(i).io.out.bits.uop.robIdx.needFlush(io.redirect),
       Option("VsSplitConnectStu")
     )
     vsSplit(i).io.vstd.get := DontCare // Todo: Discuss how to pass vector store data
@@ -1482,9 +1486,7 @@ class MemBlockImp(outer: MemBlock) extends LazyModuleImp(outer)
     vlSplit(i).io.toMergeBuffer <> vlMergeBuffer.io.fromSplit(i)
     NewPipelineConnect(
       vlSplit(i).io.out, loadUnits(i).io.vecldin, loadUnits(i).io.vecldin.fire,
-      Mux(vlSplit(i).io.out.fire,
-          vlSplit(i).io.out.bits.uop.robIdx.needFlush(io.redirect),
-          loadUnits(i).io.vecldin.bits.uop.robIdx.needFlush(io.redirect)),
+      vlSplit(i).io.out.bits.uop.robIdx.needFlush(io.redirect),
       Option("VlSplitConnectLdu")
     )
 
@@ -1738,7 +1740,7 @@ class MemBlockImp(outer: MemBlock) extends LazyModuleImp(outer)
   vSegmentUnit.io.dtlb.resp.valid <> dtlb_reqs.take(LduCnt).head.resp.valid
   vSegmentUnit.io.pmpResp <> pmp_check.head.resp
   vSegmentUnit.io.flush_sbuffer.empty := stIsEmpty
-  vSegmentUnit.io.redirect <> io.redirect
+  vSegmentUnit.io.redirect <> redirect
   vSegmentUnit.io.rdcache.resp.bits := dcache.io.lsu.load(0).resp.bits
   vSegmentUnit.io.rdcache.resp.valid := dcache.io.lsu.load(0).resp.valid
   vSegmentUnit.io.rdcache.s2_bank_conflict := dcache.io.lsu.load(0).s2_bank_conflict
diff --git a/src/main/scala/xiangshan/cache/dcache/DCacheWrapper.scala b/src/main/scala/xiangshan/cache/dcache/DCacheWrapper.scala
index e44a94d5af3..cc598874376 100644
--- a/src/main/scala/xiangshan/cache/dcache/DCacheWrapper.scala
+++ b/src/main/scala/xiangshan/cache/dcache/DCacheWrapper.scala
@@ -581,11 +581,12 @@ class AtomicWordIO(implicit p: Parameters) extends DCacheBundle
 class DCacheLoadIO(implicit p: Parameters) extends DCacheWordIO
 {
   // kill previous cycle's req
-  val s1_kill  = Output(Bool())
-  val s2_kill  = Output(Bool())
-  val s0_pc = Output(UInt(VAddrBits.W))
-  val s1_pc = Output(UInt(VAddrBits.W))
-  val s2_pc = Output(UInt(VAddrBits.W))
+  val s1_kill_data_read = Output(Bool()) // only kill bandedDataRead at s1
+  val s1_kill           = Output(Bool()) // kill loadpipe req at s1
+  val s2_kill           = Output(Bool())
+  val s0_pc             = Output(UInt(VAddrBits.W))
+  val s1_pc             = Output(UInt(VAddrBits.W))
+  val s2_pc             = Output(UInt(VAddrBits.W))
   // cycle 0: load has updated replacement before
   val replacementUpdated = Output(Bool())
   val is128Req = Bool()
@@ -785,6 +786,92 @@ class DCacheIO(implicit p: Parameters) extends DCacheBundle {
   val l2_hint = Input(Valid(new L2ToL1Hint()))
 }
 
+private object ArbiterCtrl {
+  def apply(request: Seq[Bool]): Seq[Bool] = request.length match {
+    case 0 => Seq()
+    case 1 => Seq(true.B)
+    case _ => true.B +: request.tail.init.scanLeft(request.head)(_ || _).map(!_)
+  }
+}
+
+class TreeArbiter[T <: MissReqWoStoreData](val gen: T, val n: Int) extends Module{
+  val io = IO(new ArbiterIO(gen, n))
+
+  def selectTree(in: Vec[Valid[T]], sIdx: UInt): Tuple2[UInt, T] = {
+    if (in.length == 1) {
+      (sIdx, in(0).bits)
+    } else if (in.length == 2) {
+      (
+        Mux(in(0).valid, sIdx, sIdx + 1.U),
+        Mux(in(0).valid, in(0).bits, in(1).bits)
+      )
+    } else {
+      val half = in.length / 2
+      val leftValid = in.slice(0, half).map(_.valid).reduce(_ || _)
+      val (leftIdx, leftSel) = selectTree(VecInit(in.slice(0, half)), sIdx)
+      val (rightIdx, rightSel) = selectTree(VecInit(in.slice(half, in.length)), sIdx + half.U)
+      (
+        Mux(leftValid, leftIdx, rightIdx),
+        Mux(leftValid, leftSel, rightSel)
+      )
+    }
+  }
+  val ins = Wire(Vec(n, Valid(gen)))
+  for (i <- 0 until n) {
+    ins(i).valid := io.in(i).valid
+    ins(i).bits  := io.in(i).bits
+  }
+  val (idx, sel) = selectTree(ins, 0.U)
+  // NOTE: io.chosen is very slow, dont use it
+  io.chosen := idx
+  io.out.bits := sel
+
+  val grant = ArbiterCtrl(io.in.map(_.valid))
+  for ((in, g) <- io.in.zip(grant))
+    in.ready := g && io.out.ready
+  io.out.valid := !grant.last || io.in.last.valid
+}
+
+class DCacheMEQueryIOBundle(implicit p: Parameters) extends DCacheBundle
+{
+  val req              = ValidIO(new MissReqWoStoreData)
+  val primary_ready    = Input(Bool())
+  val secondary_ready  = Input(Bool())
+  val secondary_reject = Input(Bool())
+}
+
+class DCacheMQQueryIOBundle(implicit p: Parameters) extends DCacheBundle
+{
+  val req    = ValidIO(new MissReq)
+  val ready  = Input(Bool())
+}
+
+class MissReadyGen(val n: Int)(implicit p: Parameters) extends XSModule {
+  val io = IO(new Bundle {
+    val in = Vec(n, Flipped(DecoupledIO(new MissReq)))
+    val queryMQ = Vec(n, new DCacheMQQueryIOBundle)
+  })
+
+  val mqReadyVec = io.queryMQ.map(_.ready)
+
+  io.queryMQ.zipWithIndex.foreach{
+    case (q, idx) => {
+      q.req.valid := io.in(idx).valid
+      q.req.bits  := io.in(idx).bits
+    }
+  }
+  io.in.zipWithIndex.map {
+    case (r, idx) => {
+      if (idx == 0) {
+        r.ready := mqReadyVec(idx)
+      } else {
+        r.ready := mqReadyVec(idx) && !Cat(io.in.slice(0, idx).map(_.valid)).orR
+      }
+    }
+  }
+
+}
+
 class DCache()(implicit p: Parameters) extends LazyModule with HasDCacheParameters {
   override def shouldBeInlined: Boolean = false
 
@@ -870,13 +957,23 @@ class DCacheImp(outer: DCache) extends LazyModuleImp(outer) with HasDCacheParame
   val counterFilter = Module(new CounterFilter)
   bankedDataArray.dump()
 
+  //----------------------------------------
+  // miss queue
+  // missReqArb port:
+  // enableStorePrefetch: main pipe * 1 + load pipe * 2 + store pipe * 1 +
+  // hybrid * 1; disable: main pipe * 1 + load pipe * 2 + hybrid * 1
+  // higher priority is given to lower indices
+  val MissReqPortCount = if(StorePrefetchL1Enabled) 1 + backendParams.LduCnt + backendParams.StaCnt + backendParams.HyuCnt else 1 + backendParams.LduCnt + backendParams.HyuCnt
+  val MainPipeMissReqPort = 0
+  val HybridMissReqBase = MissReqPortCount - backendParams.HyuCnt
+
   //----------------------------------------
   // core modules
   val ldu = Seq.tabulate(LoadPipelineWidth)({ i => Module(new LoadPipe(i))})
   val stu = Seq.tabulate(StorePipelineWidth)({ i => Module(new StorePipe(i))})
   val mainPipe     = Module(new MainPipe)
   // val refillPipe   = Module(new RefillPipe)
-  val missQueue    = Module(new MissQueue(edge))
+  val missQueue    = Module(new MissQueue(edge, MissReqPortCount))
   val probeQueue   = Module(new ProbeQueue(edge))
   val wb           = Module(new WritebackQueue(edge))
 
@@ -1266,27 +1363,26 @@ class DCacheImp(outer: DCache) extends LazyModuleImp(outer) with HasDCacheParame
   // atomicsReplayUnit.io.pipe_resp := RegNext(mainPipe.io.atomic_resp)
   // atomicsReplayUnit.io.block_lr <> mainPipe.io.block_lr
 
-  //----------------------------------------
-  // miss queue
-  // missReqArb port:
-  // enableStorePrefetch: main pipe * 1 + load pipe * 2 + store pipe * 1 +
-  // hybrid * 1; disable: main pipe * 1 + load pipe * 2 + hybrid * 1
-  // higher priority is given to lower indices
-  val MissReqPortCount = if(StorePrefetchL1Enabled) 1 + backendParams.LduCnt + backendParams.StaCnt + backendParams.HyuCnt else 1 + backendParams.LduCnt + backendParams.HyuCnt
-  val MainPipeMissReqPort = 0
-  val HybridMissReqBase = MissReqPortCount - backendParams.HyuCnt
-
   // Request
-  val missReqArb = Module(new ArbiterFilterByCacheLineAddr(new MissReq, MissReqPortCount, blockOffBits, PAddrBits))
+  val missReqArb = Module(new TreeArbiter(new MissReq, MissReqPortCount))
+  // seperately generating miss queue enq ready for better timeing
+  val missReadyGen = Module(new MissReadyGen(MissReqPortCount))
 
   missReqArb.io.in(MainPipeMissReqPort) <> mainPipe.io.miss_req
-  for (w <- 0 until backendParams.LduCnt)  { missReqArb.io.in(w + 1) <> ldu(w).io.miss_req }
+  missReadyGen.io.in(MainPipeMissReqPort) <> mainPipe.io.miss_req
+  for (w <- 0 until backendParams.LduCnt) {
+    missReqArb.io.in(w + 1) <> ldu(w).io.miss_req
+    missReadyGen.io.in(w + 1) <> ldu(w).io.miss_req
+  }
 
   for (w <- 0 until LoadPipelineWidth) { ldu(w).io.miss_resp := missQueue.io.resp }
   mainPipe.io.miss_resp := missQueue.io.resp
 
   if(StorePrefetchL1Enabled) {
-    for (w <- 0 until backendParams.StaCnt) { missReqArb.io.in(1 + backendParams.LduCnt + w) <> stu(w).io.miss_req }
+    for (w <- 0 until backendParams.StaCnt) {
+      missReqArb.io.in(1 + backendParams.LduCnt + w) <> stu(w).io.miss_req
+      missReadyGen.io.in(1 + backendParams.LduCnt + w) <> stu(w).io.miss_req
+    }
   }else {
     for (w <- 0 until backendParams.StaCnt) { stu(w).io.miss_req.ready := false.B }
   }
@@ -1302,24 +1398,31 @@ class DCacheImp(outer: DCache) extends LazyModuleImp(outer) with HasDCacheParame
     if (StorePrefetchL1Enabled) {
       when (ldu(HybridLoadReqPort).io.miss_req.valid) {
         missReqArb.io.in(HybridMissReqPort) <> ldu(HybridLoadReqPort).io.miss_req
+        missReadyGen.io.in(HybridMissReqPort) <> ldu(HybridLoadReqPort).io.miss_req
       } .otherwise {
         missReqArb.io.in(HybridMissReqPort) <> stu(HybridStoreReqPort).io.miss_req
+        missReadyGen.io.in(HybridMissReqPort) <> stu(HybridStoreReqPort).io.miss_req
       }
     } else {
       missReqArb.io.in(HybridMissReqPort) <> ldu(HybridLoadReqPort).io.miss_req
+      missReadyGen.io.in(HybridMissReqPort) <> ldu(HybridLoadReqPort).io.miss_req
     }
   }
 
+  for(w <- 0 until LoadPipelineWidth) {
+    wb.io.miss_req_conflict_check(w) := ldu(w).io.wbq_conflict_check
+    ldu(w).io.wbq_block_miss_req     := wb.io.block_miss_req(w)
+  }
 
-  wb.io.miss_req.valid := missReqArb.io.out.valid
-  wb.io.miss_req.bits  := missReqArb.io.out.bits.addr
+  wb.io.miss_req_conflict_check(3) := mainPipe.io.wbq_conflict_check
+  mainPipe.io.wbq_block_miss_req   := wb.io.block_miss_req(3)
+  
+  wb.io.miss_req_conflict_check(4).valid := missReqArb.io.out.valid
+  wb.io.miss_req_conflict_check(4).bits  := missReqArb.io.out.bits.addr
+  missQueue.io.wbq_block_miss_req := wb.io.block_miss_req(4)
 
-  // block_decoupled(missReqArb.io.out, missQueue.io.req, wb.io.block_miss_req)
   missReqArb.io.out <> missQueue.io.req
-  when(wb.io.block_miss_req) {
-    missQueue.io.req.bits.cancel := true.B
-    missReqArb.io.out.ready := false.B
-  }
+  missReadyGen.io.queryMQ <> missQueue.io.queryMQ
 
   for (w <- 0 until LoadPipelineWidth) { ldu(w).io.mq_enq_cancel := missQueue.io.mq_enq_cancel }
 
diff --git a/src/main/scala/xiangshan/cache/dcache/data/BankedDataArray.scala b/src/main/scala/xiangshan/cache/dcache/data/BankedDataArray.scala
index 13f86d0db67..795edbe1982 100644
--- a/src/main/scala/xiangshan/cache/dcache/data/BankedDataArray.scala
+++ b/src/main/scala/xiangshan/cache/dcache/data/BankedDataArray.scala
@@ -750,7 +750,7 @@ class BankedDataArray(implicit p: Parameters) extends AbstractBankedDataArray {
   (0 until LoadPipelineWidth).map(rport_index => {
     div_addrs(rport_index) := addr_to_dcache_div(io.read(rport_index).bits.addr)
     bank_addrs(rport_index)(0) := addr_to_dcache_bank(io.read(rport_index).bits.addr)
-    bank_addrs(rport_index)(1) := Mux(io.is128Req(rport_index), bank_addrs(rport_index)(0) + 1.U, DCacheBanks.asUInt)
+    bank_addrs(rport_index)(1) := Mux(io.is128Req(rport_index), bank_addrs(rport_index)(0) + 1.U, bank_addrs(rport_index)(0))
     set_addrs(rport_index) := addr_to_dcache_div_set(io.read(rport_index).bits.addr)
     set_addrs_reg(rport_index) := RegEnable(addr_to_dcache_div_set(io.read(rport_index).bits.addr), io.read(rport_index).valid)
 
diff --git a/src/main/scala/xiangshan/cache/dcache/loadpipe/LoadPipe.scala b/src/main/scala/xiangshan/cache/dcache/loadpipe/LoadPipe.scala
index 4ff4d442e74..703de9bb255 100644
--- a/src/main/scala/xiangshan/cache/dcache/loadpipe/LoadPipe.scala
+++ b/src/main/scala/xiangshan/cache/dcache/loadpipe/LoadPipe.scala
@@ -66,6 +66,10 @@ class LoadPipe(id: Int)(implicit p: Parameters) extends DCacheModule with HasPer
     val miss_req    = DecoupledIO(new MissReq)
     val miss_resp   = Input(new MissResp)
 
+    // send miss request to wbq
+    val wbq_conflict_check = Valid(UInt())
+    val wbq_block_miss_req = Input(Bool())
+
     // update state vec in replacement algo
     val replace_access = ValidIO(new ReplacementAccessBundle)
     // find the way to be replaced
@@ -278,7 +282,7 @@ class LoadPipe(id: Int)(implicit p: Parameters) extends DCacheModule with HasPer
   val s1_will_send_miss_req = s1_valid && !s1_nack && !s1_hit
 
   // data read
-  io.banked_data_read.valid := s1_fire && !s1_nack && !io.lsu.s1_kill && !s1_is_prefetch
+  io.banked_data_read.valid := s1_fire && !s1_nack && !io.lsu.s1_kill_data_read && !s1_is_prefetch
   io.banked_data_read.bits.addr := s1_vaddr
   io.banked_data_read.bits.way_en := s1_pred_tag_match_way_dup_dc
   io.banked_data_read.bits.bankMask := s1_bank_oh
@@ -295,6 +299,7 @@ class LoadPipe(id: Int)(implicit p: Parameters) extends DCacheModule with HasPer
 
   // val s2_valid = RegEnable(next = s1_valid && !io.lsu.s1_kill, init = false.B, enable = s1_fire)
   val s2_valid = RegInit(false.B)
+  val s2_valid_dup = RegInit(false.B)
   val s2_req = RegEnable(s1_req, s1_fire)
   val s2_load128Req = RegEnable(s1_load128Req, s1_fire)
   val s2_paddr = RegEnable(s1_paddr_dup_dcache, s1_fire)
@@ -311,8 +316,14 @@ class LoadPipe(id: Int)(implicit p: Parameters) extends DCacheModule with HasPer
 
   val s2_fire = s2_valid
 
-  when (s1_fire) { s2_valid := !io.lsu.s1_kill }
-  .elsewhen(io.lsu.resp.fire) { s2_valid := false.B }
+  when (s1_fire) {
+    s2_valid := !io.lsu.s1_kill
+    s2_valid_dup := !io.lsu.s1_kill
+  }
+  .elsewhen(io.lsu.resp.fire) {
+    s2_valid := false.B
+    s2_valid_dup := false.B
+  }
 
   dump_pipeline_reqs("LoadPipe s2", s2_valid, s2_req)
 
@@ -322,6 +333,13 @@ class LoadPipe(id: Int)(implicit p: Parameters) extends DCacheModule with HasPer
   val s2_tag_match_way = RegEnable(s1_tag_match_way_dup_dc, s1_fire)
   val s2_tag_match = RegEnable(s1_tag_match_dup_dc, s1_fire)
 
+  val s2_can_send_miss_req = RegEnable(s1_will_send_miss_req, s1_fire)
+  val s2_can_send_miss_req_dup = RegEnable(s1_will_send_miss_req, s1_fire)
+
+  val s2_miss_req_valid     = s2_valid && s2_can_send_miss_req
+  val s2_miss_req_valid_dup = s2_valid_dup && s2_can_send_miss_req_dup
+  val s2_miss_req_fire      = s2_miss_req_valid_dup && io.miss_req.ready
+
   // lsu side tag match
   val s2_hit_dup_lsu = RegNext(s1_tag_match_dup_lsu)
 
@@ -338,12 +356,14 @@ class LoadPipe(id: Int)(implicit p: Parameters) extends DCacheModule with HasPer
   // nacked or not
   val s2_nack_hit = RegEnable(s1_nack, s1_fire)
   // can no allocate mshr for load miss
-  val s2_nack_no_mshr = io.miss_req.valid && !io.miss_req.ready
+  val s2_nack_no_mshr = s2_miss_req_valid_dup && !io.miss_req.ready
+  // block with a wbq valid req
+  val s2_nack_wbq_conflict = s2_miss_req_valid_dup && io.wbq_block_miss_req
   // Bank conflict on data arrays
   val s2_nack_data = RegEnable(!io.banked_data_read.ready, s1_fire)
-  val s2_nack = s2_nack_hit || s2_nack_no_mshr || s2_nack_data
+  val s2_nack = s2_nack_hit || s2_nack_no_mshr || s2_nack_data || s2_nack_wbq_conflict
   // s2 miss merged
-  val s2_miss_merged = io.miss_req.fire && !io.mq_enq_cancel && io.miss_resp.merged
+  val s2_miss_merged = s2_miss_req_fire && !io.mq_enq_cancel && !io.wbq_block_miss_req && io.miss_resp.merged
 
   val s2_bank_addr = addr_to_dcache_bank(s2_paddr)
   dontTouch(s2_bank_addr)
@@ -359,7 +379,7 @@ class LoadPipe(id: Int)(implicit p: Parameters) extends DCacheModule with HasPer
   val s2_hit = s2_tag_match && s2_has_permission && s2_hit_coh === s2_new_hit_coh && !s2_wpu_pred_fail
 
   val s2_data128bit = Cat(io.banked_data_resp(1).raw_data, io.banked_data_resp(0).raw_data)
-  val s2_data64bit = Fill(2, io.banked_data_resp(0).raw_data)
+  val s2_resp_data  = s2_data128bit
 
   // only dump these signals when they are actually valid
   dump_pipeline_valids("LoadPipe s2", "s2_hit", s2_valid && s2_hit)
@@ -367,8 +387,6 @@ class LoadPipe(id: Int)(implicit p: Parameters) extends DCacheModule with HasPer
   dump_pipeline_valids("LoadPipe s2", "s2_nack_hit", s2_valid && s2_nack_hit)
   dump_pipeline_valids("LoadPipe s2", "s2_nack_no_mshr", s2_valid && s2_nack_no_mshr)
 
-  val s2_can_send_miss_req = RegEnable(s1_will_send_miss_req, s1_fire)
-
   if(EnableTagEcc) {
     s2_tag_error := dcacheParameters.tagCode.decode(s2_encTag).error // error reported by tag ecc check
   }else {
@@ -376,7 +394,7 @@ class LoadPipe(id: Int)(implicit p: Parameters) extends DCacheModule with HasPer
   }
 
   // send load miss to miss queue
-  io.miss_req.valid := s2_valid && s2_can_send_miss_req
+  io.miss_req.valid := s2_miss_req_valid
   io.miss_req.bits := DontCare
   io.miss_req.bits.source := s2_instrtype
   io.miss_req.bits.pf_source := RegNext(RegNext(io.lsu.pf_source))  // TODO: clock gate
@@ -387,6 +405,11 @@ class LoadPipe(id: Int)(implicit p: Parameters) extends DCacheModule with HasPer
   io.miss_req.bits.cancel := io.lsu.s2_kill || s2_tag_error
   io.miss_req.bits.pc := io.lsu.s2_pc
   io.miss_req.bits.lqIdx := io.lsu.req.bits.lqIdx
+
+  //send load miss to wbq
+  io.wbq_conflict_check.valid := s2_miss_req_valid_dup
+  io.wbq_conflict_check.bits := get_block_addr(s2_paddr)
+
   // send back response
   val resp = Wire(ValidIO(new DCacheWordResp))
   resp.valid := s2_valid
@@ -403,16 +426,17 @@ class LoadPipe(id: Int)(implicit p: Parameters) extends DCacheModule with HasPer
 
   resp.bits.real_miss := real_miss
   resp.bits.miss := real_miss
+  resp.bits.data := s2_resp_data
   io.lsu.s2_first_hit := s2_req.isFirstIssue && s2_hit
   // load pipe need replay when there is a bank conflict or wpu predict fail
-  resp.bits.replay := (resp.bits.miss && (!io.miss_req.fire || s2_nack || io.mq_enq_cancel)) || io.bank_conflict_slow || s2_wpu_pred_fail
-  resp.bits.replayCarry.valid := (resp.bits.miss && (!io.miss_req.fire || s2_nack || io.mq_enq_cancel)) || io.bank_conflict_slow || s2_wpu_pred_fail
+  resp.bits.replay := (resp.bits.miss && (!s2_miss_req_fire || s2_nack || io.mq_enq_cancel)) || io.bank_conflict_slow || s2_wpu_pred_fail
+  resp.bits.replayCarry.valid := (resp.bits.miss && (!s2_miss_req_fire || s2_nack || io.mq_enq_cancel)) || io.bank_conflict_slow || s2_wpu_pred_fail
   resp.bits.replayCarry.real_way_en := s2_real_way_en
   resp.bits.meta_prefetch := s2_hit_prefetch
   resp.bits.meta_access := s2_hit_access
   resp.bits.tag_error := s2_tag_error // report tag_error in load s2
   resp.bits.mshr_id := io.miss_resp.id
-  resp.bits.handled := io.miss_req.fire && !io.mq_enq_cancel && io.miss_resp.handled
+  resp.bits.handled := s2_miss_req_fire && !io.mq_enq_cancel && !io.wbq_block_miss_req && io.miss_resp.handled
   resp.bits.debug_robIdx := s2_req.debug_robIdx
   // debug info
   io.lsu.s2_first_hit := s2_req.isFirstIssue && s2_hit
@@ -438,7 +462,7 @@ class LoadPipe(id: Int)(implicit p: Parameters) extends DCacheModule with HasPer
   val late_hit_prefetch = s2_valid && s2_hit && (s2_req.instrtype === DCACHE_PREFETCH_SOURCE.U)
   val late_load_hit = s2_valid && s2_hit && (s2_req.instrtype === DCACHE_PREFETCH_SOURCE.U) && !isFromL1Prefetch(s2_hit_prefetch)
   val late_prefetch_hit = s2_valid && s2_hit && (s2_req.instrtype === DCACHE_PREFETCH_SOURCE.U) && isFromL1Prefetch(s2_hit_prefetch)
-  val useless_prefetch = io.miss_req.valid && io.miss_req.ready && (s2_req.instrtype === DCACHE_PREFETCH_SOURCE.U)
+  val useless_prefetch = s2_miss_req_fire && (s2_req.instrtype === DCACHE_PREFETCH_SOURCE.U)
   val useful_prefetch = s2_valid && (s2_req.instrtype === DCACHE_PREFETCH_SOURCE.U) && resp.bits.handled && !io.miss_resp.merged
 
   val prefetch_hit = s2_valid && (s2_req.instrtype =/= DCACHE_PREFETCH_SOURCE.U) && s2_hit && isFromL1Prefetch(s2_hit_prefetch) && s2_req.isFirstIssue
@@ -466,7 +490,7 @@ class LoadPipe(id: Int)(implicit p: Parameters) extends DCacheModule with HasPer
   io.lsu.s1_disable_fast_wakeup := io.disable_ld_fast_wakeup
   io.lsu.s2_bank_conflict := io.bank_conflict_slow
   io.lsu.s2_wpu_pred_fail := s2_wpu_pred_fail_and_real_hit
-  io.lsu.s2_mq_nack       := (resp.bits.miss && (!io.miss_req.fire || s2_nack_no_mshr || io.mq_enq_cancel))
+  io.lsu.s2_mq_nack       := (resp.bits.miss && (!s2_miss_req_fire || s2_nack_no_mshr || io.mq_enq_cancel || io.wbq_block_miss_req))
   assert(RegNext(s1_ready && s2_ready), "load pipeline should never be blocked")
 
   // --------------------------------------------------------------------------------
@@ -483,7 +507,7 @@ class LoadPipe(id: Int)(implicit p: Parameters) extends DCacheModule with HasPer
   val s3_req_instrtype = RegEnable(s2_req.instrtype, s2_fire)
   val s3_is_prefetch = s3_req_instrtype === DCACHE_PREFETCH_SOURCE.U
 
-  val s3_banked_data_resp_word = RegEnable(Mux(s2_hit, Mux(s2_load128Req, s2_data128bit, s2_data64bit), 0.U), s2_fire)
+  val s3_banked_data_resp_word = RegEnable(s2_resp_data, s2_fire)
   val s3_data_error = Mux(s3_load128Req, io.read_error_delayed.asUInt.orR, io.read_error_delayed(0)) && s3_hit
   val s3_tag_error = RegEnable(s2_tag_error, s2_fire)
   val s3_flag_error = RegEnable(s2_flag_error, s2_fire)
@@ -506,12 +530,6 @@ class LoadPipe(id: Int)(implicit p: Parameters) extends DCacheModule with HasPer
   // report tag error / l2 corrupted to CACHE_ERROR csr
   io.error.valid := s3_error && s3_valid
 
-  // update plru in s3
-  val s3_miss_merged = RegNext(s2_miss_merged)
-  val first_update = RegNext(RegNext(RegNext(!io.lsu.replacementUpdated)))
-  val hit_update_replace_en  = RegNext(s2_valid) && RegNext(!resp.bits.miss)
-  val miss_update_replace_en = RegNext(io.miss_req.fire) && RegNext(!io.mq_enq_cancel) && RegNext(io.miss_resp.handled)
-
   io.replace_access.valid := s3_valid && s3_hit
   io.replace_access.bits.set := RegNext(RegNext(get_idx(s1_req.vaddr)))
   io.replace_access.bits.way := RegNext(RegNext(OHToUInt(s1_tag_match_way_dup_dc)))
diff --git a/src/main/scala/xiangshan/cache/dcache/mainpipe/MainPipe.scala b/src/main/scala/xiangshan/cache/dcache/mainpipe/MainPipe.scala
index 970f7fa4bce..57d2f40c83f 100644
--- a/src/main/scala/xiangshan/cache/dcache/mainpipe/MainPipe.scala
+++ b/src/main/scala/xiangshan/cache/dcache/mainpipe/MainPipe.scala
@@ -118,6 +118,9 @@ class MainPipe(implicit p: Parameters) extends DCacheModule with HasPerfEvents w
     val miss_req = DecoupledIO(new MissReq)
     val miss_resp = Input(new MissResp) // miss resp is used to support plru update
     val refill_req = Flipped(DecoupledIO(new MainPipeReq))
+    // send miss request to wbq
+    val wbq_conflict_check = Valid(UInt())
+    val wbq_block_miss_req = Input(Bool())
     // store buffer
     val store_req = Flipped(DecoupledIO(new DCacheLineReq))
     val store_replay_resp = ValidIO(new DCacheLineResp)
@@ -442,7 +445,7 @@ class MainPipe(implicit p: Parameters) extends DCacheModule with HasPerfEvents w
     s2_valid_dup_for_status.foreach(_ := false.B)
   }
   s2_ready := !s2_valid_dup(3) || s2_can_go
-  val replay = !io.miss_req.ready
+  val replay = !io.miss_req.ready || io.wbq_block_miss_req
 
   val data_resp = Wire(io.data_resp.cloneType)
   data_resp := Mux(GatedValidRegNext(s1_fire), io.data_resp, RegEnable(data_resp, s2_valid))
@@ -1448,6 +1451,9 @@ class MainPipe(implicit p: Parameters) extends DCacheModule with HasPerfEvents w
   miss_req.pc := DontCare
   miss_req.full_overwrite := s2_req.isStore && s2_req.store_mask.andR
 
+  io.wbq_conflict_check.valid := s2_valid_dup(4) && s2_can_go_to_mq_dup(0)
+  io.wbq_conflict_check.bits := s2_req.addr
+
   io.store_replay_resp.valid := s2_valid_dup(5) && s2_can_go_to_mq_dup(1) && replay && s2_req.isStore
   io.store_replay_resp.bits.data := DontCare
   io.store_replay_resp.bits.miss := true.B
@@ -1603,8 +1609,9 @@ class MainPipe(implicit p: Parameters) extends DCacheModule with HasPerfEvents w
   io.replace_way.dmWay := s1_dmWay_dup_for_replace_way
 
   // send evict hint to sms
-  io.sms_agt_evict_req.valid := s2_valid && s2_req.miss && s2_fire_to_s3
-  io.sms_agt_evict_req.bits.vaddr := Cat(s2_repl_tag(tagBits - 1, 2), s2_req.vaddr(13,12), 0.U((VAddrBits - tagBits).W))
+  val sms_agt_evict_valid = s2_valid && s2_req.miss && s2_fire_to_s3
+  io.sms_agt_evict_req.valid := GatedValidRegNext(sms_agt_evict_valid)
+  io.sms_agt_evict_req.bits.vaddr := RegEnable(Cat(s2_repl_tag(tagBits - 1, 2), s2_req.vaddr(13,12), 0.U((VAddrBits - tagBits).W)), sms_agt_evict_valid)
 
   // TODO: consider block policy of a finer granularity
   io.status.s0_set.valid := req.valid
diff --git a/src/main/scala/xiangshan/cache/dcache/mainpipe/MissQueue.scala b/src/main/scala/xiangshan/cache/dcache/mainpipe/MissQueue.scala
index 67e1ba9bb1b..f943d572a05 100644
--- a/src/main/scala/xiangshan/cache/dcache/mainpipe/MissQueue.scala
+++ b/src/main/scala/xiangshan/cache/dcache/mainpipe/MissQueue.scala
@@ -155,6 +155,7 @@ class MissReqPipeRegBundle(edge: TLEdgeOut)(implicit p: Parameters) extends DCac
   val merge         = Bool()
   // this request is about to allocate a new mshr
   val alloc         = Bool()
+  val cancel        = Bool()
   val mshr_id       = UInt(log2Up(cfg.nMissEntries).W)
 
   def reg_valid(): Bool = {
@@ -277,7 +278,7 @@ class MissReqPipeRegBundle(edge: TLEdgeOut)(implicit p: Parameters) extends DCac
   }
 }
 
-class MissEntry(edge: TLEdgeOut)(implicit p: Parameters) extends DCacheModule 
+class MissEntry(edge: TLEdgeOut, reqNum: Int)(implicit p: Parameters) extends DCacheModule 
   with HasCircularQueuePtrHelper
  {
   val io = IO(new Bundle() {
@@ -287,6 +288,7 @@ class MissEntry(edge: TLEdgeOut)(implicit p: Parameters) extends DCacheModule
     // client requests
     // MSHR update request, MSHR state and addr will be updated when req.fire
     val req = Flipped(ValidIO(new MissReqWoStoreData))
+    val wbq_block_miss_req = Input(Bool())
     // pipeline reg
     val miss_req_pipe_reg = Input(new MissReqPipeRegBundle(edge))
     // allocate this entry for new req
@@ -303,6 +305,8 @@ class MissEntry(edge: TLEdgeOut)(implicit p: Parameters) extends DCacheModule
     val mem_grant = Flipped(DecoupledIO(new TLBundleD(edge.bundle)))
     val mem_finish = DecoupledIO(new TLBundleE(edge.bundle))
 
+    val queryME = Vec(reqNum, Flipped(new DCacheMEQueryIOBundle))
+
     // send refill info to load queue, useless now
     val refill_to_ldq = ValidIO(new Refill)
 
@@ -413,11 +417,13 @@ class MissEntry(edge: TLEdgeOut)(implicit p: Parameters) extends DCacheModule
   val refill_data_raw = Reg(Vec(blockBytes/beatBytes, UInt(beatBits.W)))
 
   // allocate current miss queue entry for a miss req
-  val primary_fire = WireInit(io.req.valid && io.primary_ready && io.primary_valid && !io.req.bits.cancel)
+  val primary_fire = WireInit(io.req.valid && io.primary_ready && io.primary_valid && !io.req.bits.cancel && !io.wbq_block_miss_req)
+  val primary_accept = WireInit(io.req.valid && io.primary_ready && io.primary_valid && !io.req.bits.cancel)
   // merge miss req to current miss queue entry
-  val secondary_fire = WireInit(io.req.valid && io.secondary_ready && !io.req.bits.cancel)
+  val secondary_fire = WireInit(io.req.valid && io.secondary_ready && !io.req.bits.cancel && !io.wbq_block_miss_req)
+  val secondary_accept = WireInit(io.req.valid && io.secondary_ready && !io.req.bits.cancel)
 
-  val req_handled_by_this_entry = primary_fire || secondary_fire
+  val req_handled_by_this_entry = primary_accept || secondary_accept
 
   // for perf use
   val secondary_fired = RegInit(false.B)
@@ -433,7 +439,7 @@ class MissEntry(edge: TLEdgeOut)(implicit p: Parameters) extends DCacheModule
     req_valid := false.B
   }
 
-  when (io.miss_req_pipe_reg.alloc) {
+  when (io.miss_req_pipe_reg.alloc && !io.miss_req_pipe_reg.cancel) {
     assert(RegNext(primary_fire), "after 1 cycle of primary_fire, entry will be allocated")
     req_valid := true.B
 
@@ -475,7 +481,7 @@ class MissEntry(edge: TLEdgeOut)(implicit p: Parameters) extends DCacheModule
     secondary_fired := false.B
   }
 
-  when (io.miss_req_pipe_reg.merge) {
+  when (io.miss_req_pipe_reg.merge && !io.miss_req_pipe_reg.cancel) {
     assert(RegNext(secondary_fire) || RegNext(RegNext(primary_fire)), "after 1 cycle of secondary_fire or 2 cycle of primary_fire, entry will be merged")
     assert(miss_req_pipe_reg_bits.req_coh.state <= req.req_coh.state || (prefetch && !access))
     assert(!(miss_req_pipe_reg_bits.isFromAMO || req.isFromAMO))
@@ -591,11 +597,16 @@ class MissEntry(edge: TLEdgeOut)(implicit p: Parameters) extends DCacheModule
   }
 
   def before_req_sent_can_merge(new_req: MissReqWoStoreData): Bool = {
-    acquire_not_sent && (req.isFromLoad || req.isFromPrefetch) && (new_req.isFromLoad || new_req.isFromStore)
+    // acquire_not_sent && (new_req.isFromLoad || new_req.isFromStore)
+
+    // Since most acquire requests have been issued from pipe_reg,
+    // the number of such merge situations is currently small,
+    // So dont Merge anything for better timing.
+    false.B
   }
 
   def before_data_refill_can_merge(new_req: MissReqWoStoreData): Bool = {
-    data_not_refilled && (req.isFromLoad || req.isFromStore || req.isFromPrefetch) && new_req.isFromLoad
+    data_not_refilled && new_req.isFromLoad
   }
   
   // Note that late prefetch will be ignored
@@ -652,6 +663,18 @@ class MissEntry(edge: TLEdgeOut)(implicit p: Parameters) extends DCacheModule
   }
   io.secondary_ready := should_merge(io.req.bits)
   io.secondary_reject := should_reject(io.req.bits)
+
+  // generate primary_ready & secondary_(ready | reject) for each miss request
+  for (i <- 0 until reqNum) {
+    when(GatedValidRegNext(io.id >= ((cfg.nMissEntries).U - io.nMaxPrefetchEntry))) {
+      io.queryME(i).primary_ready := !req_valid && !GatedValidRegNext(primary_fire)
+    }.otherwise {
+      io.queryME(i).primary_ready := !req_valid && !GatedValidRegNext(primary_fire) &&
+                                    (!io.queryME(i).req.bits.isFromPrefetch || io.memSetPattenDetected)
+    }
+    io.queryME(i).secondary_ready  := should_merge(io.queryME(i).req.bits)
+    io.queryME(i).secondary_reject := should_reject(io.queryME(i).req.bits)
+  }
   
   // should not allocate, merge or reject at the same time
   assert(RegNext(PopCount(Seq(io.primary_ready, io.secondary_ready, io.secondary_reject)) <= 1.U || !io.req.valid))
@@ -673,7 +696,7 @@ class MissEntry(edge: TLEdgeOut)(implicit p: Parameters) extends DCacheModule
 
   // if the entry has a pending merge req, wait for it
   // Note: now, only wait for store, because store may acquire T
-  io.mem_acquire.valid := !s_acquire && !(io.miss_req_pipe_reg.merge && miss_req_pipe_reg_bits.isFromStore) 
+  io.mem_acquire.valid := !s_acquire && !(io.miss_req_pipe_reg.merge && !io.miss_req_pipe_reg.cancel && miss_req_pipe_reg_bits.isFromStore) 
   val grow_param = req.req_coh.onAccess(req.cmd)._2
   val acquireBlock = edge.AcquireBlock(
     fromSource = io.id,
@@ -788,7 +811,7 @@ class MissEntry(edge: TLEdgeOut)(implicit p: Parameters) extends DCacheModule
   XSPerfAccumulate("penalty_waiting_for_channel_E", io.mem_finish.valid && !io.mem_finish.ready)
   XSPerfAccumulate("prefetch_req_primary", primary_fire && io.req.bits.source === DCACHE_PREFETCH_SOURCE.U)
   XSPerfAccumulate("prefetch_req_merged", secondary_fire && io.req.bits.source === DCACHE_PREFETCH_SOURCE.U)
-  XSPerfAccumulate("can_not_send_acquire_because_of_merging_store", !s_acquire && io.miss_req_pipe_reg.merge && miss_req_pipe_reg_bits.isFromStore)
+  XSPerfAccumulate("can_not_send_acquire_because_of_merging_store", !s_acquire && io.miss_req_pipe_reg.merge && io.miss_req_pipe_reg.cancel && miss_req_pipe_reg_bits.isFromStore)
 
   val (mshr_penalty_sample, mshr_penalty) = TransactionLatencyCounter(GatedValidRegNextN(primary_fire, 2), release_entry)
   XSPerfHistogram("miss_penalty", mshr_penalty, mshr_penalty_sample, 0, 20, 1, true, true)
@@ -805,7 +828,7 @@ class MissEntry(edge: TLEdgeOut)(implicit p: Parameters) extends DCacheModule
   XSPerfHistogram("a_to_d_penalty", a_to_d_penalty, a_to_d_penalty_sample, 20, 100, 10, true, false)
 }
 
-class MissQueue(edge: TLEdgeOut)(implicit p: Parameters) extends DCacheModule 
+class MissQueue(edge: TLEdgeOut, reqNum: Int)(implicit p: Parameters) extends DCacheModule 
   with HasPerfEvents 
   {
   val io = IO(new Bundle {
@@ -814,6 +837,8 @@ class MissQueue(edge: TLEdgeOut)(implicit p: Parameters) extends DCacheModule
     val resp = Output(new MissResp)
     val refill_to_ldq = ValidIO(new Refill)
 
+    val queryMQ = Vec(reqNum, Flipped(new DCacheMQQueryIOBundle))
+
     val mem_acquire = DecoupledIO(new TLBundleA(edge.bundle))
     val mem_grant = Flipped(DecoupledIO(new TLBundleD(edge.bundle)))
     val mem_finish = DecoupledIO(new TLBundleE(edge.bundle))
@@ -834,6 +859,9 @@ class MissQueue(edge: TLEdgeOut)(implicit p: Parameters) extends DCacheModule
     val replace_addr = Flipped(ValidIO(UInt(PAddrBits.W)))
     val replace_block = Output(Bool())
 
+    // req blocked by wbq
+    val wbq_block_miss_req = Input(Bool())
+
     val full = Output(Bool())
 
     // forward missqueue
@@ -862,7 +890,7 @@ class MissQueue(edge: TLEdgeOut)(implicit p: Parameters) extends DCacheModule
 
   // 128KBL1: FIXME: provide vaddr for l2
 
-  val entries = Seq.fill(cfg.nMissEntries)(Module(new MissEntry(edge)))
+  val entries = Seq.fill(cfg.nMissEntries)(Module(new MissEntry(edge, reqNum)))
 
   val miss_req_pipe_reg = RegInit(0.U.asTypeOf(new MissReqPipeRegBundle(edge)))
   val acquire_from_pipereg = Wire(chiselTypeOf(io.mem_acquire))
@@ -877,6 +905,19 @@ class MissQueue(edge: TLEdgeOut)(implicit p: Parameters) extends DCacheModule
   val alloc = !reject && !merge && ParallelORR(Cat(primary_ready_vec))
   val accept = alloc || merge
 
+  // generate req_ready for each miss request for better timing
+  for (i <- 0 until reqNum) {
+    val _primary_ready_vec = entries.map(_.io.queryME(i).primary_ready)
+    val _secondary_ready_vec = entries.map(_.io.queryME(i).secondary_ready)
+    val _secondary_reject_vec = entries.map(_.io.queryME(i).secondary_reject)
+    val _merge = ParallelORR(Cat(_secondary_ready_vec ++ Seq(miss_req_pipe_reg.merge_req(io.queryMQ(i).req.bits))))
+    val _reject = ParallelORR(Cat(_secondary_reject_vec ++ Seq(miss_req_pipe_reg.reject_req(io.queryMQ(i).req.bits))))
+    val _alloc = !_reject && !_merge && ParallelORR(Cat(_primary_ready_vec))
+    val _accept = _alloc || _merge
+
+    io.queryMQ(i).ready := _accept
+  }
+
   val req_mshr_handled_vec = entries.map(_.io.req_handled_by_this_entry)
   // merged to pipeline reg
   val req_pipeline_reg_handled = miss_req_pipe_reg.merge_req(io.req.bits) && io.req.valid
@@ -893,8 +934,9 @@ class MissQueue(edge: TLEdgeOut)(implicit p: Parameters) extends DCacheModule
     miss_req_pipe_reg.req     := io.req.bits
   }
   // miss_req_pipe_reg.req     := io.req.bits
-  miss_req_pipe_reg.alloc   := alloc && io.req.valid && !io.req.bits.cancel
-  miss_req_pipe_reg.merge   := merge && io.req.valid && !io.req.bits.cancel
+  miss_req_pipe_reg.alloc   := alloc && io.req.valid && !io.req.bits.cancel && !io.wbq_block_miss_req
+  miss_req_pipe_reg.merge   := merge && io.req.valid && !io.req.bits.cancel && !io.wbq_block_miss_req
+  miss_req_pipe_reg.cancel  := io.wbq_block_miss_req
   miss_req_pipe_reg.mshr_id := io.resp.id
 
   assert(PopCount(Seq(alloc && io.req.valid, merge && io.req.valid)) <= 1.U, "allocate and merge a mshr in same cycle!")
@@ -960,6 +1002,7 @@ class MissQueue(edge: TLEdgeOut)(implicit p: Parameters) extends DCacheModule
       e.io.id := i.U
       e.io.l2_pf_store_only := io.l2_pf_store_only
       e.io.req.valid := io.req.valid
+      e.io.wbq_block_miss_req := io.wbq_block_miss_req
       e.io.primary_valid := io.req.valid &&
         !merge &&
         !reject &&
@@ -992,6 +1035,11 @@ class MissQueue(edge: TLEdgeOut)(implicit p: Parameters) extends DCacheModule
 
       e.io.main_pipe_req.ready := io.main_pipe_req.ready
 
+      for (j <- 0 until reqNum) {
+        e.io.queryME(j).req.valid := io.queryMQ(j).req.valid
+        e.io.queryME(j).req.bits  := io.queryMQ(j).req.bits.toMissReqWoStoreData()
+      }
+
       when(io.l2_hint.bits.sourceId === i.U) {
         e.io.l2_hint <> io.l2_hint
       } .otherwise {
diff --git a/src/main/scala/xiangshan/cache/dcache/mainpipe/WritebackQueue.scala b/src/main/scala/xiangshan/cache/dcache/mainpipe/WritebackQueue.scala
index 9685f3c5baa..7d64a9cb3a3 100644
--- a/src/main/scala/xiangshan/cache/dcache/mainpipe/WritebackQueue.scala
+++ b/src/main/scala/xiangshan/cache/dcache/mainpipe/WritebackQueue.scala
@@ -315,8 +315,9 @@ class WritebackQueue(edge: TLEdgeOut)(implicit p: Parameters) extends DCacheModu
     //val probe_ttob_check_req = Flipped(ValidIO(new ProbeToBCheckReq))
     //val probe_ttob_check_resp = ValidIO(new ProbeToBCheckResp)
 
-    val miss_req = Flipped(Valid(UInt()))
-    val block_miss_req = Output(Bool()) 
+    // 5 miss_req to check: 3*LoadPipe + 1*MainPipe + 1*missReqArb_out
+    val miss_req_conflict_check = Vec(LoadPipelineWidth + 2, Flipped(Valid(UInt())))
+    val block_miss_req = Vec(LoadPipelineWidth + 2, Output(Bool()))
   })
 
   require(cfg.nReleaseEntries > cfg.nMissEntries)
@@ -373,8 +374,12 @@ class WritebackQueue(edge: TLEdgeOut)(implicit p: Parameters) extends DCacheModu
 
   io.mem_grant.ready := true.B
   block_conflict := VecInit(entries.map(e => e.io.block_addr.valid && e.io.block_addr.bits === io.req.bits.addr)).asUInt.orR
-  val miss_req_conflict = VecInit(entries.map(e => e.io.block_addr.valid && e.io.block_addr.bits === io.miss_req.bits)).asUInt.orR
-  io.block_miss_req := io.miss_req.valid && miss_req_conflict
+  val miss_req_conflict = io.miss_req_conflict_check.map{ r =>
+    VecInit(entries.map(e => e.io.block_addr.valid && e.io.block_addr.bits === r.bits)).asUInt.orR
+  }
+  io.block_miss_req.zipWithIndex.foreach{ case(blk, i) =>
+    blk := io.miss_req_conflict_check(i).valid && miss_req_conflict(i)
+  }
 
   TLArbiter.robin(edge, io.mem_release, entries.map(_.io.mem_release):_*)
 
@@ -389,13 +394,13 @@ class WritebackQueue(edge: TLEdgeOut)(implicit p: Parameters) extends DCacheModu
     io.mem_grant.bits.dump
   }
 
-  when (io.miss_req.valid) {
-    XSDebug("miss_req: addr: %x\n", io.miss_req.bits)
-  }
+  // when (io.miss_req.valid) {
+  //   XSDebug("miss_req: addr: %x\n", io.miss_req.bits)
+  // }
 
-  when (io.block_miss_req) {
-    XSDebug("block_miss_req\n")
-  }
+  // when (io.block_miss_req) {
+  //   XSDebug("block_miss_req\n")
+  // }
 
   // performance counters
   XSPerfAccumulate("wb_req", io.req.fire)
diff --git a/src/main/scala/xiangshan/cache/dcache/meta/AsynchronousMetaArray.scala b/src/main/scala/xiangshan/cache/dcache/meta/AsynchronousMetaArray.scala
index 0e190c7d99c..834483f46ab 100644
--- a/src/main/scala/xiangshan/cache/dcache/meta/AsynchronousMetaArray.scala
+++ b/src/main/scala/xiangshan/cache/dcache/meta/AsynchronousMetaArray.scala
@@ -48,7 +48,7 @@ class FlagMetaWriteReq(implicit p: Parameters) extends MetaReadReq {
   val flag = Bool()
 }
 
-class L1CohMetaArray(readPorts: Int, writePorts: Int)(implicit p: Parameters) extends DCacheModule {
+class L1CohMetaArray(readPorts: Int, writePorts: Int, bypassRead: Boolean = false)(implicit p: Parameters) extends DCacheModule {
   val io = IO(new Bundle() {
     val read = Vec(readPorts, Flipped(DecoupledIO(new MetaReadReq)))
     val resp = Output(Vec(readPorts, Vec(nWays, new Meta)))
@@ -79,11 +79,15 @@ class L1CohMetaArray(readPorts: Int, writePorts: Int)(implicit p: Parameters) ex
             bypass_data := s1_way_wdata(way)(wport)
           }
         )
-        resp(way) := Mux(
-          RegEnable(read_way_bypass, read.valid),
-          RegEnable(bypass_data, read_way_bypass),
-          RegEnable(meta_array(read.bits.idx)(way), read.valid)
-        )
+        if (bypassRead) {
+          resp(way) := Mux(
+            RegEnable(read_way_bypass, read.valid),
+            RegEnable(bypass_data, read_way_bypass),
+            RegEnable(meta_array(read.bits.idx)(way), read.valid)
+          )
+        } else {
+          resp(way) := meta_array(RegEnable(read.bits.idx, read.valid))(way)
+        }
       })
   }
 
diff --git a/src/main/scala/xiangshan/cache/mmu/MMUBundle.scala b/src/main/scala/xiangshan/cache/mmu/MMUBundle.scala
index 0d4f523080b..7f5a064a611 100644
--- a/src/main/scala/xiangshan/cache/mmu/MMUBundle.scala
+++ b/src/main/scala/xiangshan/cache/mmu/MMUBundle.scala
@@ -537,6 +537,7 @@ class TlbResp(nDups: Int = 1)(implicit p: Parameters) extends TlbBundle {
   val gpaddr = Vec(nDups, Output(UInt(GPAddrBits.W)))
   val pbmt = Vec(nDups, Output(UInt(ptePbmtLen.W)))
   val miss = Output(Bool())
+  val fastMiss = Output(Bool())
   val excp = Vec(nDups, new Bundle {
     val gpf = new TlbExceptionBundle()
     val pf = new TlbExceptionBundle()
diff --git a/src/main/scala/xiangshan/cache/mmu/TLB.scala b/src/main/scala/xiangshan/cache/mmu/TLB.scala
index 1767e566f9b..332bb1e9303 100644
--- a/src/main/scala/xiangshan/cache/mmu/TLB.scala
+++ b/src/main/scala/xiangshan/cache/mmu/TLB.scala
@@ -140,11 +140,9 @@ class TLB(Width: Int, nRespDups: Int = 1, Block: Seq[Boolean], q: TLBParameters)
   // check pmp use paddr (for timing optization, use pmp_addr here)
   // check permisson
   (0 until Width).foreach{i =>
-    when (RegNext(req(i).bits.no_translate)) {
-      pmp_check(req(i).bits.pmp_addr, req_out(i).size, req_out(i).cmd, i)
-    } .otherwise {
-      pmp_check(pmp_addr(i), req_out(i).size, req_out(i).cmd, i)
-    }
+    val noTranslateReg = RegNext(req(i).bits.no_translate)
+    val addr = Mux(noTranslateReg, req(i).bits.pmp_addr, pmp_addr(i))
+    pmp_check(addr, req_out(i).size, req_out(i).cmd, noTranslateReg, i)
     for (d <- 0 until nRespDups) {
       pbmt_check(i, d, pbmt(i)(d), g_pbmt(i)(d), req_out_s2xlate(i))
       perm_check(perm(i)(d), req_out(i).cmd, i, d, g_perm(i)(d), req_out(i).hlvx, req_out_s2xlate(i))
@@ -203,6 +201,7 @@ class TLB(Width: Int, nRespDups: Int = 1, Block: Seq[Boolean], q: TLBParameters)
     resp(i).bits.miss := miss
     resp(i).bits.ptwBack := ptw.resp.fire
     resp(i).bits.memidx := RegEnable(req_in(i).bits.memidx, req_in(i).valid)
+    resp(i).bits.fastMiss := !hit && enable
 
     val ppn = WireInit(VecInit(Seq.fill(nRespDups)(0.U(ppnLen.W))))
     val pbmt = WireInit(VecInit(Seq.fill(nRespDups)(0.U(ptePbmtLen.W))))
@@ -253,8 +252,8 @@ class TLB(Width: Int, nRespDups: Int = 1, Block: Seq[Boolean], q: TLBParameters)
     )
   }
 
-  def pmp_check(addr: UInt, size: UInt, cmd: UInt, idx: Int): Unit = {
-    pmp(idx).valid := resp(idx).valid
+  def pmp_check(addr: UInt, size: UInt, cmd: UInt, noTranslate: Bool, idx: Int): Unit = {
+    pmp(idx).valid := resp(idx).valid || noTranslate
     pmp(idx).bits.addr := addr
     pmp(idx).bits.size := size
     pmp(idx).bits.cmd := cmd
@@ -404,7 +403,7 @@ class TLB(Width: Int, nRespDups: Int = 1, Block: Seq[Boolean], q: TLBParameters)
         pbmt_check(idx, d, io.ptw.resp.bits.s1.entry.pbmt, io.ptw.resp.bits.s2.entry.pbmt, s2xlate)
         perm_check(stage1, req_out(idx).cmd, idx, d, stage2, req_out(idx).hlvx, s2xlate)
       }
-      pmp_check(resp(idx).bits.paddr(0), req_out(idx).size, req_out(idx).cmd, idx)
+      pmp_check(resp(idx).bits.paddr(0), req_out(idx).size, req_out(idx).cmd, false.B, idx)
 
       // NOTE: the unfiltered req would be handled by Repeater
     }
diff --git a/src/main/scala/xiangshan/mem/MemCommon.scala b/src/main/scala/xiangshan/mem/MemCommon.scala
index e75773181aa..a412810ca70 100644
--- a/src/main/scala/xiangshan/mem/MemCommon.scala
+++ b/src/main/scala/xiangshan/mem/MemCommon.scala
@@ -150,6 +150,8 @@ class LsPipelineBundle(implicit p: Parameters) extends XSBundle
   val ldCancel = ValidUndirectioned(UInt(log2Ceil(LoadPipelineWidth).W))
   // loadQueueReplay index.
   val schedIndex = UInt(log2Up(LoadQueueReplaySize).W)
+  // hardware prefetch and fast replay no need to query tlb
+  val tlbNoQuery = Bool()
 }
 
 class LdPrefetchTrainBundle(implicit p: Parameters) extends LsPipelineBundle {
@@ -178,6 +180,7 @@ class LdPrefetchTrainBundle(implicit p: Parameters) extends LsPipelineBundle {
     if (latch) hasROBEntry := RegEnable(input.hasROBEntry, enable) else hasROBEntry := input.hasROBEntry
     if (latch) dcacheRequireReplay := RegEnable(input.dcacheRequireReplay, enable) else dcacheRequireReplay := input.dcacheRequireReplay
     if (latch) schedIndex := RegEnable(input.schedIndex, enable) else schedIndex := input.schedIndex
+    if (latch) tlbNoQuery := RegEnable(input.tlbNoQuery, enable) else tlbNoQuery := input.tlbNoQuery
     if (latch) isvec               := RegEnable(input.isvec, enable)               else isvec               := input.isvec
     if (latch) isLastElem          := RegEnable(input.isLastElem, enable)          else isLastElem          := input.isLastElem
     if (latch) is128bit            := RegEnable(input.is128bit, enable)            else is128bit            := input.is128bit
@@ -427,19 +430,26 @@ class LoadDataFromDcacheBundle(implicit p: Parameters) extends DCacheBundle {
 
   val forward_result_valid = Bool()
 
-  def dcacheData(): UInt = {
-    // old dcache
-    // val dcache_data = Mux1H(bank_oh, bankedDcacheData)
-    // new dcache
+  def mergeTLData(): UInt = {
+    // merge TL D or MSHR data at load s2
     val dcache_data = respDcacheData
     val use_D = forward_D && forward_result_valid
     val use_mshr = forward_mshr && forward_result_valid
-    Mux(use_D, forwardData_D.asUInt, Mux(use_mshr, forwardData_mshr.asUInt, dcache_data))
+    Mux(
+      use_D || use_mshr,
+      Mux(
+        use_D,
+        forwardData_D.asUInt,
+        forwardData_mshr.asUInt
+      ),
+      dcache_data
+    )
   }
 
-  def mergedData(): UInt = {
+  def mergeLsqFwdData(dcacheData: UInt): UInt = {
+    // merge dcache and lsq forward data at load s3
     val rdataVec = VecInit((0 until VLEN / 8).map(j =>
-      Mux(forwardMask(j), forwardData(j), dcacheData()(8*(j+1)-1, 8*j))
+      Mux(forwardMask(j), forwardData(j), dcacheData(8*(j+1)-1, 8*j))
     ))
     rdataVec.asUInt
   }
diff --git a/src/main/scala/xiangshan/mem/lsqueue/LoadQueue.scala b/src/main/scala/xiangshan/mem/lsqueue/LoadQueue.scala
index 3e9d714fc63..a6297550617 100644
--- a/src/main/scala/xiangshan/mem/lsqueue/LoadQueue.scala
+++ b/src/main/scala/xiangshan/mem/lsqueue/LoadQueue.scala
@@ -111,8 +111,8 @@ trait HasLoadHelper { this: XSModule =>
   }
 
   def genDataSelectByOffset(addrOffset: UInt): Vec[Bool] = {
-    require(addrOffset.getWidth == 4)
-    VecInit((0 until 16).map{ case i =>
+    require(addrOffset.getWidth == 3)
+    VecInit((0 until 8).map{ case i =>
       addrOffset === i.U
     })
   }
diff --git a/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala b/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala
index bd18c1c6379..4a2f85659f2 100644
--- a/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala
+++ b/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala
@@ -83,21 +83,20 @@ class StoreExceptionBuffer(implicit p: Parameters) extends XSModule with HasCirc
   // enqueue
   // S1:
   val s1_req = VecInit(io.storeAddrIn.map(_.bits))
-  val s1_valid = VecInit(io.storeAddrIn.map(_.valid))
+  val s1_valid = VecInit(io.storeAddrIn.map(x =>
+      x.valid && !x.bits.uop.robIdx.needFlush(io.redirect) && ExceptionNO.selectByFu(x.bits.uop.exceptionVec, StaCfg).asUInt.orR
+  ))
 
   // S2: delay 1 cycle
   val s2_req = (0 until StorePipelineWidth * 2 + VecStorePipelineWidth).map(i =>
     RegEnable(s1_req(i), s1_valid(i)))
   val s2_valid = (0 until StorePipelineWidth * 2 + VecStorePipelineWidth).map(i =>
-    RegNext(s1_valid(i)) &&
-      !s2_req(i).uop.robIdx.needFlush(RegNext(io.redirect)) &&
-      !s2_req(i).uop.robIdx.needFlush(io.redirect)
+    RegNext(s1_valid(i)) && !s2_req(i).uop.robIdx.needFlush(io.redirect)
   )
-  val s2_has_exception = s2_req.map(x => ExceptionNO.selectByFu(x.uop.exceptionVec, StaCfg).asUInt.orR)
 
   val s2_enqueue = Wire(Vec(StorePipelineWidth * 2 + VecStorePipelineWidth, Bool()))
   for (w <- 0 until StorePipelineWidth * 2 + VecStorePipelineWidth) {
-    s2_enqueue(w) := s2_valid(w) && s2_has_exception(w)
+    s2_enqueue(w) := s2_valid(w)
   }
 
   when (req_valid && req.uop.robIdx.needFlush(io.redirect)) {
@@ -347,7 +346,7 @@ class StoreQueue(implicit p: Parameters) extends XSModule
   val canEnqueue = io.enq.req.map(_.valid)
   val enqCancel = io.enq.req.map(_.bits.robIdx.needFlush(io.brqRedirect))
   val vStoreFlow = io.enq.req.map(_.bits.numLsElem)
-  val validVStoreFlow = vStoreFlow.zipWithIndex.map{case (vLoadFlowNumItem, index) => Mux(!RegNext(io.brqRedirect.valid) && io.enq.canAccept && io.enq.lqCanAccept && canEnqueue(index), vLoadFlowNumItem, 0.U)}
+  val validVStoreFlow = vStoreFlow.zipWithIndex.map{case (vLoadFlowNumItem, index) => Mux(!RegNext(io.brqRedirect.valid) && canEnqueue(index), vLoadFlowNumItem, 0.U)}
   val validVStoreOffset = vStoreFlow.zip(io.enq.needAlloc).map{case (flow, needAllocItem) => Mux(needAllocItem, flow, 0.U)}
   val validVStoreOffsetRShift = 0.U +: validVStoreOffset.take(vStoreFlow.length - 1)
 
diff --git a/src/main/scala/xiangshan/mem/lsqueue/VirtualLoadQueue.scala b/src/main/scala/xiangshan/mem/lsqueue/VirtualLoadQueue.scala
index 89f933366d4..9a73d38b4ec 100644
--- a/src/main/scala/xiangshan/mem/lsqueue/VirtualLoadQueue.scala
+++ b/src/main/scala/xiangshan/mem/lsqueue/VirtualLoadQueue.scala
@@ -106,7 +106,7 @@ class VirtualLoadQueue(implicit p: Parameters) extends XSModule
 
   // update enqueue pointer
   val vLoadFlow = io.enq.req.map(_.bits.numLsElem)
-  val validVLoadFlow = vLoadFlow.zipWithIndex.map{case (vLoadFlowNumItem, index) => Mux(io.enq.canAccept && io.enq.sqCanAccept && canEnqueue(index), vLoadFlowNumItem, 0.U)}
+  val validVLoadFlow = vLoadFlow.zipWithIndex.map{case (vLoadFlowNumItem, index) => Mux(canEnqueue(index), vLoadFlowNumItem, 0.U)}
   val validVLoadOffset = vLoadFlow.zip(io.enq.needAlloc).map{case (flow, needAllocItem) => Mux(needAllocItem, flow, 0.U)}
   val validVLoadOffsetRShift = 0.U +: validVLoadOffset.take(validVLoadFlow.length - 1)
 
diff --git a/src/main/scala/xiangshan/mem/pipeline/HybridUnit.scala b/src/main/scala/xiangshan/mem/pipeline/HybridUnit.scala
index 0f575b581ac..821fb971aae 100644
--- a/src/main/scala/xiangshan/mem/pipeline/HybridUnit.scala
+++ b/src/main/scala/xiangshan/mem/pipeline/HybridUnit.scala
@@ -594,6 +594,7 @@ class HybridUnit(implicit p: Parameters) extends XSModule
   io.ldu_io.dcache.s1_paddr_dup_lsu    <> s1_paddr_dup_lsu
   io.ldu_io.dcache.s1_paddr_dup_dcache <> s1_paddr_dup_dcache
   io.ldu_io.dcache.s1_kill             := s1_kill || s1_tlb_miss || s1_exception
+  io.ldu_io.dcache.s1_kill_data_read   := s1_kill || s1_tlb_miss
 
   // store to load forwarding
   io.ldu_io.sbuffer.valid := s1_valid && !(s1_exception || s1_tlb_miss || s1_kill || s1_fast_rep_kill || s1_prf || !s1_ld_flow)
@@ -1107,12 +1108,6 @@ class HybridUnit(implicit p: Parameters) extends XSModule
   val s3_isvec        = RegNext(s2_isvec)
   s3_ready := !s3_valid || s3_kill || sx_can_go
 
-  // forwrad last beat
-  val (s3_fwd_frm_d_chan, s3_fwd_data_frm_d_chan) = io.ldu_io.tl_d_channel.forward(s2_valid && s2_out.forward_tlDchannel, s2_out.mshrid, s2_out.paddr)
-  val s3_fwd_data_valid = RegEnable(s2_fwd_data_valid, false.B, s2_valid)
-  val s3_fwd_frm_d_chan_valid = (s3_fwd_frm_d_chan && s3_fwd_data_valid) && s3_ld_flow
-
-
   // s3 load fast replay
   io.ldu_io.fast_rep_out.valid := s3_valid &&
                                   s3_fast_rep &&
@@ -1127,7 +1122,7 @@ class HybridUnit(implicit p: Parameters) extends XSModule
                               !s3_in.lateKill &&
                               s3_ld_flow
   io.ldu_io.lsq.ldin.bits := s3_in
-  io.ldu_io.lsq.ldin.bits.miss := s3_in.miss && !s3_fwd_frm_d_chan_valid
+  io.ldu_io.lsq.ldin.bits.miss := s3_in.miss
 
   /* <------- DANGEROUS: Don't change sequence here ! -------> */
   io.ldu_io.lsq.ldin.bits.data_wen_dup := s3_ld_valid_dup.asBools
@@ -1151,7 +1146,7 @@ class HybridUnit(implicit p: Parameters) extends XSModule
       RegNext(io.csrCtrl.ldld_vio_check_enable)
 
   val s3_rep_info = WireInit(s3_in.rep_info)
-  s3_rep_info.dcache_miss   := s3_in.rep_info.dcache_miss && !s3_fwd_frm_d_chan_valid && s3_troublem
+  s3_rep_info.dcache_miss   := s3_in.rep_info.dcache_miss && s3_troublem
   val s3_rep_frm_fetch = s3_vp_match_fail
   val s3_flushPipe = s3_ldld_rep_inst
   val s3_sel_rep_cause = PriorityEncoderOH(s3_rep_info.cause.asUInt)
@@ -1226,18 +1221,20 @@ class HybridUnit(implicit p: Parameters) extends XSModule
 
   // data from dcache hit
   val s3_ld_raw_data_frm_cache = Wire(new LoadDataFromDcacheBundle)
-  s3_ld_raw_data_frm_cache.respDcacheData       := io.ldu_io.dcache.resp.bits.data_delayed
+  s3_ld_raw_data_frm_cache.respDcacheData       := io.ldu_io.dcache.resp.bits.data
+  s3_ld_raw_data_frm_cache.forward_D            := s2_fwd_frm_d_chan
+  s3_ld_raw_data_frm_cache.forwardData_D        := s2_fwd_data_frm_d_chan
+  s3_ld_raw_data_frm_cache.forward_mshr         := s2_fwd_frm_mshr
+  s3_ld_raw_data_frm_cache.forwardData_mshr     := s2_fwd_data_frm_mshr
+  s3_ld_raw_data_frm_cache.forward_result_valid := s2_fwd_data_valid
+
   s3_ld_raw_data_frm_cache.forwardMask          := RegEnable(s2_fwd_mask, s2_valid)
   s3_ld_raw_data_frm_cache.forwardData          := RegEnable(s2_fwd_data, s2_valid)
   s3_ld_raw_data_frm_cache.uop                  := RegEnable(s2_out.uop, s2_valid)
   s3_ld_raw_data_frm_cache.addrOffset           := RegEnable(s2_out.paddr(3, 0), s2_valid)
-  s3_ld_raw_data_frm_cache.forward_D            := RegEnable(s2_fwd_frm_d_chan, false.B, s2_valid) || s3_fwd_frm_d_chan_valid
-  s3_ld_raw_data_frm_cache.forwardData_D        := Mux(s3_fwd_frm_d_chan_valid, s3_fwd_data_frm_d_chan, RegEnable(s2_fwd_data_frm_d_chan, s2_valid))
-  s3_ld_raw_data_frm_cache.forward_mshr         := RegEnable(s2_fwd_frm_mshr, false.B, s2_valid)
-  s3_ld_raw_data_frm_cache.forwardData_mshr     := RegEnable(s2_fwd_data_frm_mshr, s2_valid)
-  s3_ld_raw_data_frm_cache.forward_result_valid := RegEnable(s2_fwd_data_valid, false.B, s2_valid)
 
-  val s3_merged_data_frm_cache = s3_ld_raw_data_frm_cache.mergedData()
+  val s3_merged_data_frm_tlD   = RegEnable(s3_ld_raw_data_frm_cache.mergeTLData(), s2_valid)
+  val s3_merged_data_frm_cache = s3_ld_raw_data_frm_cache.mergeLsqFwdData(s3_merged_data_frm_tlD)
   val s3_picked_data_frm_cache = LookupTree(s3_ld_raw_data_frm_cache.addrOffset, List(
     "b0000".U -> s3_merged_data_frm_cache(63,    0),
     "b0001".U -> s3_merged_data_frm_cache(63,    8),
@@ -1398,8 +1395,6 @@ class HybridUnit(implicit p: Parameters) extends XSModule
   XSPerfAccumulate("s2_successfully_forward_channel_D", s2_fire && s2_fwd_frm_d_chan && s2_fwd_data_valid)
   XSPerfAccumulate("s2_successfully_forward_mshr",      s2_fire && s2_fwd_frm_mshr && s2_fwd_data_valid)
 
-  XSPerfAccumulate("s3_fwd_frm_d_chan",            s3_valid && s3_fwd_frm_d_chan_valid)
-
   XSPerfAccumulate("load_to_load_forward",                      s1_try_ptr_chasing && !s1_ptr_chasing_canceled)
   XSPerfAccumulate("load_to_load_forward_try",                  s1_try_ptr_chasing)
   XSPerfAccumulate("load_to_load_forward_fail",                 s1_cancel_ptr_chasing)
diff --git a/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala b/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala
index 6b934e8c97a..1db669020d5 100644
--- a/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala
+++ b/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala
@@ -212,6 +212,9 @@ class LoadUnit(implicit p: Parameters) extends XSModule
   val s0_fire          = s0_valid && s0_can_go
   val s0_mmio_fire     = s0_mmio_select && s0_can_go
   val s0_out           = Wire(new LqWriteBundle)
+  val s0_tlb_valid     = Wire(Bool())
+  val s0_tlb_hlv       = Wire(Bool())
+  val s0_tlb_hlvx      = Wire(Bool())
   val s0_tlb_vaddr     = Wire(UInt(VAddrBits.W))
   val s0_dcache_vaddr  = Wire(UInt(VAddrBits.W))
 
@@ -233,8 +236,6 @@ class LoadUnit(implicit p: Parameters) extends XSModule
     val prf_wr        = Bool()
     val prf_i         = Bool()
     val sched_idx     = UInt(log2Up(LoadQueueReplaySize+1).W)
-    val hlv           = Bool()
-    val hlvx          = Bool()
     // Record the issue port idx of load issue queue. This signal is used by load cancel.
     val deqPortIdx    = UInt(log2Ceil(LoadPipelineWidth).W)
     val frm_mabuf     = Bool()
@@ -302,6 +303,7 @@ class LoadUnit(implicit p: Parameters) extends XSModule
   dontTouch(s0_src_ready_vec)
   dontTouch(s0_src_select_vec)
 
+  val s0_tlb_no_query = s0_hw_prf_select || s0_src_select_vec(fast_rep_idx) || s0_src_select_vec(mmio_idx) || s0_sel_src.prf_i
   s0_valid := (
     s0_src_valid_vec(mab_idx) ||
     s0_src_valid_vec(super_rep_idx) ||
@@ -316,6 +318,16 @@ class LoadUnit(implicit p: Parameters) extends XSModule
 
   s0_mmio_select := s0_src_select_vec(mmio_idx) && !s0_kill
 
+   // if is hardware prefetch or fast replay, don't send valid to tlb
+  s0_tlb_valid := (
+    s0_src_valid_vec(mab_idx) ||
+    s0_src_valid_vec(super_rep_idx) ||
+    s0_src_valid_vec(lsq_rep_idx) ||
+    s0_src_valid_vec(vec_iss_idx) ||
+    s0_src_valid_vec(int_iss_idx) ||
+    s0_src_valid_vec(l2l_fwd_idx)
+  ) && io.dcache.req.ready
+
   // which is S0's out is ready and dcache is ready
   val s0_try_ptr_chasing      = s0_src_select_vec(l2l_fwd_idx)
   val s0_do_try_ptr_chasing   = s0_try_ptr_chasing && s0_can_go && io.dcache.req.ready
@@ -328,21 +340,21 @@ class LoadUnit(implicit p: Parameters) extends XSModule
   io.canAcceptHighConfPrefetch := s0_src_ready_vec(high_pf_idx) && io.dcache.req.ready
 
   // query DTLB
-  io.tlb.req.valid                   := s0_valid && !s0_hw_prf_select && !s0_sel_src.prf_i  // if is hardware prefetch, don't send valid to tlb, but need no_translate
+  io.tlb.req.valid                   := s0_tlb_valid
   io.tlb.req.bits.cmd                := Mux(s0_sel_src.prf,
                                          Mux(s0_sel_src.prf_wr, TlbCmd.write, TlbCmd.read),
                                          TlbCmd.read
                                        )
   io.tlb.req.bits.vaddr              := s0_tlb_vaddr
-  io.tlb.req.bits.hyperinst          := s0_sel_src.hlv
-  io.tlb.req.bits.hlvx               := s0_sel_src.hlvx
+  io.tlb.req.bits.hyperinst          := s0_tlb_hlv
+  io.tlb.req.bits.hlvx               := s0_tlb_hlvx
   io.tlb.req.bits.size               := Mux(s0_sel_src.isvec, s0_sel_src.alignedType(2,0), LSUOpType.size(s0_sel_src.uop.fuOpType))
-  io.tlb.req.bits.kill               := s0_kill
+  io.tlb.req.bits.kill               := s0_kill || s0_tlb_no_query // if does not need to be translated, kill it
   io.tlb.req.bits.memidx.is_ld       := true.B
   io.tlb.req.bits.memidx.is_st       := false.B
   io.tlb.req.bits.memidx.idx         := s0_sel_src.uop.lqIdx.value
   io.tlb.req.bits.debug.robIdx       := s0_sel_src.uop.robIdx
-  io.tlb.req.bits.no_translate       := s0_hw_prf_select  // hw b.reqetch addr does not need to be translated, need this signal for pmp check
+  io.tlb.req.bits.no_translate       := s0_tlb_no_query  // hardware prefetch and fast replay does not need to be translated, need this signal for pmp check
   io.tlb.req.bits.debug.pc           := s0_sel_src.uop.pc
   io.tlb.req.bits.debug.isFirstIssue := s0_sel_src.isFirstIssue
 
@@ -391,8 +403,6 @@ class LoadUnit(implicit p: Parameters) extends XSModule
     out.isvec         := false.B
     out.is128bit      := src.is128bit
     out.vecActive     := true.B
-    out.hlv           := LSUOpType.isHlv(src.uop.fuOpType)
-    out.hlvx          := LSUOpType.isHlvx(src.uop.fuOpType)
     out
   }
 
@@ -425,8 +435,6 @@ class LoadUnit(implicit p: Parameters) extends XSModule
     out.elemIdx       := src.elemIdx
     out.elemIdxInsideVd := src.elemIdxInsideVd
     out.alignedType   := src.alignedType
-    out.hlv           := LSUOpType.isHlv(src.uop.fuOpType)
-    out.hlvx          := LSUOpType.isHlvx(src.uop.fuOpType)
     out
   }
 
@@ -449,8 +457,6 @@ class LoadUnit(implicit p: Parameters) extends XSModule
     out.prf_wr        := false.B
     out.prf_i         := false.B
     out.sched_idx     := 0.U
-    out.hlv           := LSUOpType.isHlv(src.uop.fuOpType)
-    out.hlvx          := LSUOpType.isHlvx(src.uop.fuOpType)
     out.vecActive     := true.B
     out
   }
@@ -484,8 +490,6 @@ class LoadUnit(implicit p: Parameters) extends XSModule
     out.elemIdx       := src.elemIdx
     out.elemIdxInsideVd := src.elemIdxInsideVd
     out.alignedType   := src.alignedType
-    out.hlv           := LSUOpType.isHlv(src.uop.fuOpType)
-    out.hlvx          := LSUOpType.isHlvx(src.uop.fuOpType)
     out
   }
 
@@ -549,8 +553,6 @@ class LoadUnit(implicit p: Parameters) extends XSModule
     out.elemIdx             := src.elemIdx
     out.elemIdxInsideVd     := src.elemIdxInsideVd
     out.alignedType         := src.alignedType
-    out.hlv                 := false.B
-    out.hlvx                := false.B
     out
   }
 
@@ -573,8 +575,6 @@ class LoadUnit(implicit p: Parameters) extends XSModule
     out.prf_wr        := src.uop.fuOpType === LSUOpType.prefetch_w
     out.prf_i         := src.uop.fuOpType === LSUOpType.prefetch_i
     out.sched_idx     := 0.U
-    out.hlv           := LSUOpType.isHlv(src.uop.fuOpType)
-    out.hlvx          := LSUOpType.isHlvx(src.uop.fuOpType)
     out.vecActive     := true.B // true for scala load
     out
   }
@@ -602,8 +602,6 @@ class LoadUnit(implicit p: Parameters) extends XSModule
     out.prf_wr             := false.B
     out.prf_i              := false.B
     out.sched_idx          := 0.U
-    out.hlv                := LSUOpType.isHlv(out.uop.fuOpType)
-    out.hlvx               := LSUOpType.isHlvx(out.uop.fuOpType)
     out
   }
 
@@ -624,26 +622,54 @@ class LoadUnit(implicit p: Parameters) extends XSModule
   )
   s0_sel_src := ParallelPriorityMux(s0_src_selector, s0_src_format)
 
-  val s0_addr_selector = Seq(
+  // fast replay and hardware prefetch don't need to query tlb
+  val int_issue_vaddr = io.ldin.bits.src(0) + SignExt(io.ldin.bits.uop.imm(11, 0), VAddrBits)
+  val int_vec_vaddr = Mux(s0_src_valid_vec(vec_iss_idx), io.vecldin.bits.vaddr, int_issue_vaddr)
+  s0_tlb_vaddr := Mux(
     s0_src_valid_vec(mab_idx),
-    s0_src_valid_vec(super_rep_idx),
-    s0_src_valid_vec(fast_rep_idx),
-    s0_src_valid_vec(lsq_rep_idx),
-    s0_src_valid_vec(vec_iss_idx),
-    s0_src_valid_vec(int_iss_idx),
-    (if (EnableLoadToLoadForward) s0_src_valid_vec(l2l_fwd_idx) else false.B),
-  )
-  val s0_addr_format = Seq(
     io.misalign_ldin.bits.vaddr,
-    io.replay.bits.vaddr,
+    Mux(
+      s0_src_valid_vec(super_rep_idx) || s0_src_valid_vec(lsq_rep_idx),
+      io.replay.bits.vaddr,
+      int_vec_vaddr
+    )
+  )
+  s0_dcache_vaddr := Mux(
+    s0_src_select_vec(fast_rep_idx),
     io.fast_rep_in.bits.vaddr,
-    io.replay.bits.vaddr,
-    io.vecldin.bits.vaddr,
-    io.ldin.bits.src(0) + SignExt(io.ldin.bits.uop.imm(11, 0), VAddrBits),
-    (if (EnableLoadToLoadForward) Cat(io.l2l_fwd_in.data(XLEN-1, 6), s0_ptr_chasing_vaddr(5,0)) else 0.U(VAddrBits.W)),
+    Mux(
+      s0_hw_prf_select,
+      io.prefetch_req.bits.getVaddr(),
+      s0_tlb_vaddr
+    )
+  )
+
+  s0_tlb_hlv := Mux(
+    s0_src_valid_vec(mab_idx),
+    LSUOpType.isHlv(io.misalign_ldin.bits.uop.fuOpType),
+    Mux(
+      s0_src_valid_vec(super_rep_idx) || s0_src_valid_vec(lsq_rep_idx),
+      LSUOpType.isHlv(io.replay.bits.uop.fuOpType),
+      Mux(
+        s0_src_valid_vec(int_iss_idx),
+        LSUOpType.isHlv(io.ldin.bits.uop.fuOpType),
+        false.B
+      )
+    )
+  )
+  s0_tlb_hlvx := Mux(
+    s0_src_valid_vec(mab_idx),
+    LSUOpType.isHlvx(io.misalign_ldin.bits.uop.fuOpType),
+    Mux(
+      s0_src_valid_vec(super_rep_idx) || s0_src_valid_vec(lsq_rep_idx),
+      LSUOpType.isHlvx(io.replay.bits.uop.fuOpType),
+      Mux(
+        s0_src_valid_vec(int_iss_idx),
+        LSUOpType.isHlvx(io.ldin.bits.uop.fuOpType),
+        false.B
+      )
+    )
   )
-  s0_tlb_vaddr := ParallelPriorityMux(s0_addr_selector, s0_addr_format)
-  s0_dcache_vaddr := Mux(s0_hw_prf_select, io.prefetch_req.bits.getVaddr(), s0_tlb_vaddr)
 
   // address align check
   val s0_addr_aligned = LookupTree(Mux(s0_sel_src.isvec, s0_sel_src.alignedType(1,0), s0_sel_src.uop.fuOpType(1, 0)), List(
@@ -672,7 +698,8 @@ class LoadUnit(implicit p: Parameters) extends XSModule
   s0_out.is128bit        := s0_sel_src.is128bit
   s0_out.isFrmMisAlignBuf    := s0_sel_src.frm_mabuf
   s0_out.uop_unit_stride_fof := s0_sel_src.uop_unit_stride_fof
-  s0_out.paddr         := io.prefetch_req.bits.paddr // only for prefetch
+  s0_out.paddr         := Mux(s0_src_valid_vec(fast_rep_idx), io.fast_rep_in.bits.paddr, io.prefetch_req.bits.paddr) // only for prefetch and fast_rep
+  s0_out.tlbNoQuery    := s0_tlb_no_query
   // s0_out.rob_idx_valid   := s0_rob_idx_valid
   // s0_out.inner_idx       := s0_inner_idx
   // s0_out.rob_idx         := s0_rob_idx
@@ -784,6 +811,7 @@ class LoadUnit(implicit p: Parameters) extends XSModule
   val s1_paddr_dup_dcache = Wire(UInt())
   val s1_exception        = ExceptionNO.selectByFu(s1_out.uop.exceptionVec, LduCfg).asUInt.orR   // af & pf exception were modified below.
   val s1_tlb_miss         = io.tlb.resp.bits.miss && io.tlb.resp.valid && s1_valid
+  val s1_tlb_fast_miss    = io.tlb.resp.bits.fastMiss && io.tlb.resp.valid && s1_valid
   val s1_pbmt             = Mux(io.tlb.resp.valid, io.tlb.resp.bits.pbmt(0), 0.U(2.W))
   val s1_prf              = s1_in.isPrefetch
   val s1_hw_prf           = s1_in.isHWPrefetch
@@ -793,9 +821,9 @@ class LoadUnit(implicit p: Parameters) extends XSModule
   s1_vaddr_hi         := s1_in.vaddr(VAddrBits - 1, 6)
   s1_vaddr_lo         := s1_in.vaddr(5, 0)
   s1_vaddr            := Cat(s1_vaddr_hi, s1_vaddr_lo)
-  s1_paddr_dup_lsu    := Mux(s1_hw_prf, s1_in.paddr, io.tlb.resp.bits.paddr(0))
-  s1_paddr_dup_dcache := Mux(s1_hw_prf, s1_in.paddr, io.tlb.resp.bits.paddr(1))
-  s1_gpaddr_dup_lsu   := Mux(s1_hw_prf, s1_in.paddr, io.tlb.resp.bits.gpaddr(0))
+  s1_paddr_dup_lsu    := Mux(s1_in.tlbNoQuery, s1_in.paddr, io.tlb.resp.bits.paddr(0))
+  s1_paddr_dup_dcache := Mux(s1_in.tlbNoQuery, s1_in.paddr, io.tlb.resp.bits.paddr(1))
+  s1_gpaddr_dup_lsu   := Mux(s1_in.isFastReplay, s1_in.paddr, io.tlb.resp.bits.gpaddr(0))
 
   when (s1_tlb_memidx.is_ld && io.tlb.resp.valid && !s1_tlb_miss && s1_tlb_memidx.idx === s1_in.uop.lqIdx.value) {
     // printf("load idx = %d\n", s1_tlb_memidx.idx)
@@ -809,6 +837,7 @@ class LoadUnit(implicit p: Parameters) extends XSModule
   io.dcache.s1_paddr_dup_lsu    <> s1_paddr_dup_lsu
   io.dcache.s1_paddr_dup_dcache <> s1_paddr_dup_dcache
   io.dcache.s1_kill             := s1_kill || s1_dly_err || s1_tlb_miss || s1_exception
+  io.dcache.s1_kill_data_read   := s1_kill || s1_dly_err || s1_tlb_fast_miss
 
   // store to load forwarding
   io.sbuffer.valid := s1_valid && !(s1_exception || s1_tlb_miss || s1_kill || s1_dly_err || s1_prf)
@@ -854,9 +883,10 @@ class LoadUnit(implicit p: Parameters) extends XSModule
   when (!s1_dly_err) {
     // current ori test will cause the case of ldest == 0, below will be modifeid in the future.
     // af & pf exception were modified
-    s1_out.uop.exceptionVec(loadPageFault)   := io.tlb.resp.bits.excp(0).pf.ld && s1_vecActive && !s1_tlb_miss
-    s1_out.uop.exceptionVec(loadGuestPageFault)   := io.tlb.resp.bits.excp(0).gpf.ld && !s1_tlb_miss
-    s1_out.uop.exceptionVec(loadAccessFault) := io.tlb.resp.bits.excp(0).af.ld && s1_vecActive && !s1_tlb_miss
+    // if is tlbNoQuery request, don't trigger exception from tlb resp
+    s1_out.uop.exceptionVec(loadPageFault)   := io.tlb.resp.bits.excp(0).pf.ld && s1_vecActive && !s1_tlb_miss && !s1_in.tlbNoQuery
+    s1_out.uop.exceptionVec(loadGuestPageFault)   := io.tlb.resp.bits.excp(0).gpf.ld && !s1_tlb_miss && !s1_in.tlbNoQuery
+    s1_out.uop.exceptionVec(loadAccessFault) := io.tlb.resp.bits.excp(0).af.ld && s1_vecActive && !s1_tlb_miss && !s1_in.tlbNoQuery
   } .otherwise {
     s1_out.uop.exceptionVec(loadPageFault)      := false.B
     s1_out.uop.exceptionVec(loadGuestPageFault) := false.B
@@ -958,7 +988,7 @@ class LoadUnit(implicit p: Parameters) extends XSModule
   val s2_vecActive = RegEnable(s1_out.vecActive, true.B, s1_fire)
   val s2_isvec  = RegEnable(s1_out.isvec, false.B, s1_fire)
   val s2_data_select  = genRdataOH(s2_out.uop)
-  val s2_data_select_by_offset = genDataSelectByOffset(s2_out.paddr(3, 0))
+  val s2_data_select_by_offset = genDataSelectByOffset(s2_out.paddr(2, 0))
   val s2_frm_mabuf = s2_in.isFrmMisAlignBuf
   val s2_pbmt = RegEnable(s1_pbmt, s1_fire)
 
@@ -992,6 +1022,7 @@ class LoadUnit(implicit p: Parameters) extends XSModule
     s2_exception_vec := 0.U.asTypeOf(s2_exception_vec.cloneType)
   }
   val s2_exception = ExceptionNO.selectByFu(s2_exception_vec, LduCfg).asUInt.orR && s2_vecActive
+  val s2_mis_align = s2_valid && s2_exception_vec(loadAddrMisaligned) && GatedValidRegNext(io.csrCtrl.hd_misalign_ld_enable) && !s2_in.isvec
 
   val (s2_fwd_frm_d_chan, s2_fwd_data_frm_d_chan) = io.tl_d_channel.forward(s1_valid && s1_out.forward_tlDchannel, s1_out.mshrid, s1_out.paddr)
   val (s2_fwd_data_valid, s2_fwd_frm_mshr, s2_fwd_data_frm_mshr) = io.forward_mshr.forward()
@@ -1088,6 +1119,10 @@ class LoadUnit(implicit p: Parameters) extends XSModule
 
   val s2_data_fwded = s2_dcache_miss && (s2_full_fwd || s2_cache_tag_error)
 
+  val s2_vp_match_fail = (io.lsq.forward.matchInvalid || io.sbuffer.matchInvalid) && s2_troublem
+  val s2_safe_wakeup = !s2_out.rep_info.need_rep && !s2_mmio && !s2_mis_align && !s2_exception // don't need to replay and is not a mmio and misalign
+  val s2_safe_writeback = s2_exception || s2_safe_wakeup || s2_vp_match_fail
+
   // ld-ld violation require
   io.lsq.ldld_nuke_query.req.valid           := s2_valid && s2_can_query
   io.lsq.ldld_nuke_query.req.bits.uop        := s2_in.uop
@@ -1248,6 +1283,16 @@ class LoadUnit(implicit p: Parameters) extends XSModule
   val s3_mmio         = Wire(Valid(new MemExuOutput))
   val s3_data_select  = RegEnable(s2_data_select, 0.U(s2_data_select.getWidth.W), s2_fire)
   val s3_data_select_by_offset = RegEnable(s2_data_select_by_offset, 0.U.asTypeOf(s2_data_select_by_offset), s2_fire)
+  val s3_dly_ld_err   =
+      if (EnableAccurateLoadError) {
+        io.dcache.resp.bits.error_delayed && GatedValidRegNext(io.csrCtrl.cache_error_enable) && s3_troublem
+      } else {
+        WireInit(false.B)
+      }
+  val s3_safe_wakeup  = RegEnable(s2_safe_wakeup, s2_fire)
+  val s3_safe_writeback = RegEnable(s2_safe_writeback, s2_fire) || s3_dly_ld_err
+  val s3_exception = RegEnable(s2_exception, s2_fire)
+  val s3_mis_align = RegEnable(s2_mis_align, s2_fire)
   // TODO: Fix vector load merge buffer nack
   val s3_vec_mb_nack  = Wire(Bool())
   s3_vec_mb_nack     := false.B
@@ -1258,9 +1303,6 @@ class LoadUnit(implicit p: Parameters) extends XSModule
   s3_mmio.bits  := RegNextN(io.lsq.uncache.bits, 3)
 
   // forwrad last beat
-  val (s3_fwd_frm_d_chan, s3_fwd_data_frm_d_chan) = io.tl_d_channel.forward(s2_valid && s2_out.forward_tlDchannel, s2_out.mshrid, s2_out.paddr)
-  val s3_fwd_data_valid = RegEnable(s2_fwd_data_valid, false.B, s2_valid)
-  val s3_fwd_frm_d_chan_valid = (s3_fwd_frm_d_chan && s3_fwd_data_valid && s3_in.handledByMSHR)
   val s3_fast_rep_canceled = io.replay.valid && io.replay.bits.forward_tlDchannel || io.misalign_ldin.valid || !io.dcache.req.ready
 
   // s3 load fast replay
@@ -1271,10 +1313,10 @@ class LoadUnit(implicit p: Parameters) extends XSModule
   // TODO: check this --by hx
   // io.lsq.ldin.valid := s3_valid && (!s3_fast_rep || !io.fast_rep_out.ready) && !s3_in.feedbacked && !s3_in.lateKill
   io.lsq.ldin.bits := s3_in
-  io.lsq.ldin.bits.miss := s3_in.miss && !s3_fwd_frm_d_chan_valid
+  io.lsq.ldin.bits.miss := s3_in.miss
 
   // connect to misalignBuffer
-  io.misalign_buf.valid := io.lsq.ldin.valid && io.csrCtrl.hd_misalign_ld_enable && !io.lsq.ldin.bits.isvec
+  io.misalign_buf.valid := io.lsq.ldin.valid && GatedValidRegNext(io.csrCtrl.hd_misalign_ld_enable) && !io.lsq.ldin.bits.isvec
   io.misalign_buf.bits  := s3_in
 
   /* <------- DANGEROUS: Don't change sequence here ! -------> */
@@ -1282,12 +1324,6 @@ class LoadUnit(implicit p: Parameters) extends XSModule
   io.lsq.ldin.bits.replacementUpdated := io.dcache.resp.bits.replacementUpdated
   io.lsq.ldin.bits.missDbUpdated := GatedValidRegNext(s2_fire && s2_in.hasROBEntry && !s2_in.tlbMiss && !s2_in.missDbUpdated)
 
-  val s3_dly_ld_err =
-    if (EnableAccurateLoadError) {
-      io.dcache.resp.bits.error_delayed && GatedValidRegNext(io.csrCtrl.cache_error_enable) && s3_troublem
-    } else {
-      WireInit(false.B)
-    }
   io.s3_dly_ld_err := false.B // s3_dly_ld_err && s3_valid
   io.lsq.ldin.bits.dcacheRequireReplay  := s3_dcache_rep
   io.fast_rep_out.bits.delayedLoadError := s3_dly_ld_err
@@ -1301,11 +1337,8 @@ class LoadUnit(implicit p: Parameters) extends XSModule
   val s3_flushPipe = s3_ldld_rep_inst
 
   val s3_rep_info = WireInit(s3_in.rep_info)
-  s3_rep_info.dcache_miss   := s3_in.rep_info.dcache_miss && !s3_fwd_frm_d_chan_valid
   val s3_sel_rep_cause = PriorityEncoderOH(s3_rep_info.cause.asUInt)
 
-  val s3_exception = ExceptionNO.selectByFu(s3_in.uop.exceptionVec, LduCfg).asUInt.orR && s3_vecActive
-  val s3_mis_align = s3_valid && s3_in.uop.exceptionVec(loadAddrMisaligned) && io.csrCtrl.hd_misalign_ld_enable && !s3_in.isvec
   when (s3_exception || s3_dly_ld_err || s3_rep_frm_fetch) {
     io.lsq.ldin.bits.rep_info.cause := 0.U.asTypeOf(s3_rep_info.cause.cloneType)
   } .otherwise {
@@ -1313,7 +1346,7 @@ class LoadUnit(implicit p: Parameters) extends XSModule
   }
 
   // Int load, if hit, will be writebacked at s3
-  s3_out.valid                := s3_valid && !io.lsq.ldin.bits.rep_info.need_rep && !s3_in.mmio
+  s3_out.valid                := s3_valid && s3_safe_writeback
   s3_out.bits.uop             := s3_in.uop
   s3_out.bits.uop.fpWen       := s3_in.uop.fpWen && !s3_exception
   s3_out.bits.uop.exceptionVec(loadAccessFault) := (s3_dly_ld_err || s3_in.uop.exceptionVec(loadAccessFault)) && s3_vecActive
@@ -1361,7 +1394,7 @@ class LoadUnit(implicit p: Parameters) extends XSModule
   io.lsq.stld_nuke_query.revoke := s3_revoke
 
   // feedback slow
-  s3_fast_rep := GatedValidRegNext(s2_fast_rep)
+  s3_fast_rep := RegNext(s2_fast_rep)
 
   val s3_fb_no_waiting = !s3_in.isLoadReplay &&
                         (!(s3_fast_rep && !s3_fast_rep_canceled)) &&
@@ -1378,11 +1411,8 @@ class LoadUnit(implicit p: Parameters) extends XSModule
   io.feedback_slow.bits.sourceType       := RSFeedbackType.lrqFull
   io.feedback_slow.bits.dataInvalidSqIdx := DontCare
 
-  io.ldCancel.ld2Cancel := s3_valid && (
-    io.lsq.ldin.bits.rep_info.need_rep ||                       // exe fail or
-    s3_in.mmio                         ||                       // is mmio
-    s3_mis_align                                                // misalign
-  ) && !s3_isvec && !s3_frm_mabuf
+  // TODO: vector wakeup?
+  io.ldCancel.ld2Cancel := s3_valid && !s3_safe_wakeup && !s3_isvec && !s3_frm_mabuf
 
   val s3_ld_wb_meta = Mux(s3_valid, s3_out.bits, s3_mmio.bits)
 
@@ -1403,46 +1433,76 @@ class LoadUnit(implicit p: Parameters) extends XSModule
 
   // data from dcache hit
   val s3_ld_raw_data_frm_cache = Wire(new LoadDataFromDcacheBundle)
-  s3_ld_raw_data_frm_cache.respDcacheData       := io.dcache.resp.bits.data_delayed
+  s3_ld_raw_data_frm_cache.respDcacheData       := io.dcache.resp.bits.data
+  s3_ld_raw_data_frm_cache.forward_D            := s2_fwd_frm_d_chan
+  s3_ld_raw_data_frm_cache.forwardData_D        := s2_fwd_data_frm_d_chan
+  s3_ld_raw_data_frm_cache.forward_mshr         := s2_fwd_frm_mshr
+  s3_ld_raw_data_frm_cache.forwardData_mshr     := s2_fwd_data_frm_mshr
+  s3_ld_raw_data_frm_cache.forward_result_valid := s2_fwd_data_valid
+
   s3_ld_raw_data_frm_cache.forwardMask          := RegEnable(s2_fwd_mask, s2_valid)
   s3_ld_raw_data_frm_cache.forwardData          := RegEnable(s2_fwd_data, s2_valid)
   s3_ld_raw_data_frm_cache.uop                  := RegEnable(s2_out.uop, s2_valid)
   s3_ld_raw_data_frm_cache.addrOffset           := RegEnable(s2_out.paddr(3, 0), s2_valid)
-  s3_ld_raw_data_frm_cache.forward_D            := RegEnable(s2_fwd_frm_d_chan, false.B, s2_valid) || s3_fwd_frm_d_chan_valid
-  s3_ld_raw_data_frm_cache.forwardData_D        := Mux(s3_fwd_frm_d_chan_valid, s3_fwd_data_frm_d_chan, RegEnable(s2_fwd_data_frm_d_chan, s2_valid))
-  s3_ld_raw_data_frm_cache.forward_mshr         := RegEnable(s2_fwd_frm_mshr, false.B, s2_valid)
-  s3_ld_raw_data_frm_cache.forwardData_mshr     := RegEnable(s2_fwd_data_frm_mshr, s2_valid)
-  s3_ld_raw_data_frm_cache.forward_result_valid := RegEnable(s2_fwd_data_valid, false.B, s2_valid)
-
-  val s3_merged_data_frm_cache = s3_ld_raw_data_frm_cache.mergedData()
-  val s3_data_frm_cache = Seq(
-    s3_merged_data_frm_cache(63,    0),
-    s3_merged_data_frm_cache(63,    8),
-    s3_merged_data_frm_cache(63,   16),
-    s3_merged_data_frm_cache(63,   24),
-    s3_merged_data_frm_cache(63,   32),
-    s3_merged_data_frm_cache(63,   40),
-    s3_merged_data_frm_cache(63,   48),
-    s3_merged_data_frm_cache(63,   56),
-    s3_merged_data_frm_cache(127,  64),
-    s3_merged_data_frm_cache(127,  72),
-    s3_merged_data_frm_cache(127,  80),
-    s3_merged_data_frm_cache(127,  88),
-    s3_merged_data_frm_cache(127,  96),
-    s3_merged_data_frm_cache(127, 104),
-    s3_merged_data_frm_cache(127, 112),
-    s3_merged_data_frm_cache(127, 120)
-  )
-  val s3_picked_data_frm_cache = Mux1H(s3_data_select_by_offset, s3_data_frm_cache)
-  val s3_ld_data_frm_cache = newRdataHelper(s3_data_select, s3_picked_data_frm_cache)
+
+  val s3_merged_data_frm_tlD   = RegEnable(s3_ld_raw_data_frm_cache.mergeTLData(), s2_valid)
+  val s3_merged_data_frm_cache = s3_ld_raw_data_frm_cache.mergeLsqFwdData(s3_merged_data_frm_tlD)
+
+  // duplicate reg for ldout and vecldout
+  private val LdDataDup = 3
+  require(LdDataDup >= 2)
+  // truncate forward data and cache data to XLEN width to writeback
+  val s3_fwd_mask_clip = VecInit(List.fill(LdDataDup)(
+    RegEnable(Mux(
+      s2_out.paddr(3),
+      (s2_fwd_mask.asUInt)(VLEN / 8 - 1, 8),
+      (s2_fwd_mask.asUInt)(7, 0)
+    ).asTypeOf(Vec(XLEN / 8, Bool())), s2_valid)
+  ))
+  val s3_fwd_data_clip = VecInit(List.fill(LdDataDup)(
+    RegEnable(Mux(
+      s2_out.paddr(3),
+      (s2_fwd_data.asUInt)(VLEN - 1, 64),
+      (s2_fwd_data.asUInt)(63, 0)
+    ).asTypeOf(Vec(XLEN / 8, UInt(8.W))), s2_valid)
+  ))
+  val s3_merged_data_frm_tld_clip = VecInit(List.fill(LdDataDup)(
+    RegEnable(Mux(
+      s2_out.paddr(3),
+      s3_ld_raw_data_frm_cache.mergeTLData()(VLEN - 1, 64),
+      s3_ld_raw_data_frm_cache.mergeTLData()(63, 0)
+    ).asTypeOf(Vec(XLEN / 8, UInt(8.W))), s2_valid)
+  ))
+  val s3_merged_data_frm_cache_clip = VecInit((0 until LdDataDup).map(i => {
+    VecInit((0 until XLEN / 8).map(j =>
+      Mux(s3_fwd_mask_clip(i)(j), s3_fwd_data_clip(i)(j), s3_merged_data_frm_tld_clip(i)(j))
+    )).asUInt
+  }))
+
+  val s3_data_frm_cache = VecInit((0 until LdDataDup).map(i => {
+    VecInit(Seq(
+      s3_merged_data_frm_cache_clip(i)(63,    0),
+      s3_merged_data_frm_cache_clip(i)(63,    8),
+      s3_merged_data_frm_cache_clip(i)(63,   16),
+      s3_merged_data_frm_cache_clip(i)(63,   24),
+      s3_merged_data_frm_cache_clip(i)(63,   32),
+      s3_merged_data_frm_cache_clip(i)(63,   40),
+      s3_merged_data_frm_cache_clip(i)(63,   48),
+      s3_merged_data_frm_cache_clip(i)(63,   56),
+    ))
+  }))
+  val s3_picked_data_frm_cache = VecInit((0 until LdDataDup).map(i => {
+    Mux1H(s3_data_select_by_offset, s3_data_frm_cache(i))
+  }))
+  val s3_ld_data_frm_cache = newRdataHelper(s3_data_select, s3_picked_data_frm_cache(0))
 
   // FIXME: add 1 cycle delay ?
   // io.lsq.uncache.ready := !s3_valid
   val s3_outexception = ExceptionNO.selectByFu(s3_out.bits.uop.exceptionVec, LduCfg).asUInt.orR && s3_vecActive
   io.ldout.bits        := s3_ld_wb_meta
   io.ldout.bits.data   := Mux(s3_valid, s3_ld_data_frm_cache, s3_ld_data_frm_uncache)
-  io.ldout.valid       := ((s3_out.valid && !s3_vecout.isvec && !s3_mis_align && !s3_frm_mabuf) || 
-                           (s3_mmio.valid && !s3_valid))
+  io.ldout.valid       := (s3_mmio.valid ||
+                          (s3_out.valid && !s3_vecout.isvec && !s3_mis_align && !s3_frm_mabuf))
   io.ldout.bits.uop.exceptionVec := ExceptionNO.selectByFu(s3_ld_wb_meta.uop.exceptionVec, LduCfg)
 
   // TODO: check this --hx
@@ -1464,7 +1524,7 @@ class LoadUnit(implicit p: Parameters) extends XSModule
   // vec feedback
   io.vecldout.bits.vecFeedback := vecFeedback
   // TODO: VLSU, uncache data logic
-  val vecdata = rdataVecHelper(s3_vec_alignedType(1,0), s3_picked_data_frm_cache)
+  val vecdata = rdataVecHelper(s3_vec_alignedType(1,0), s3_picked_data_frm_cache(1))
   io.vecldout.bits.vecdata.get := Mux(s3_in.is128bit, s3_merged_data_frm_cache, vecdata)
   io.vecldout.bits.isvec := s3_vecout.isvec
   io.vecldout.bits.elemIdx := s3_vecout.elemIdx
@@ -1487,7 +1547,7 @@ class LoadUnit(implicit p: Parameters) extends XSModule
 
   io.misalign_ldout.valid     := s3_valid && (!s3_fast_rep || s3_fast_rep_canceled) && s3_frm_mabuf
   io.misalign_ldout.bits      := io.lsq.ldin.bits
-  io.misalign_ldout.bits.data := Mux(s3_in.is128bit, s3_merged_data_frm_cache, s3_picked_data_frm_cache)
+  io.misalign_ldout.bits.data := Mux(s3_in.is128bit, s3_merged_data_frm_cache, s3_picked_data_frm_cache(2))
 
   // fast load to load forward
   if (EnableLoadToLoadForward) {
@@ -1597,9 +1657,6 @@ class LoadUnit(implicit p: Parameters) extends XSModule
   XSPerfAccumulate("s2_successfully_forward_channel_D", s2_fire && s2_fwd_frm_d_chan && s2_fwd_data_valid)
   XSPerfAccumulate("s2_successfully_forward_mshr",      s2_fire && s2_fwd_frm_mshr && s2_fwd_data_valid)
 
-  XSPerfAccumulate("s3_fwd_frm_d_chan",            s3_valid && s3_fwd_frm_d_chan_valid)
-  XSPerfAccumulate("s3_frm_mabuf",                 s3_valid && s3_frm_mabuf)
-
   XSPerfAccumulate("load_to_load_forward",                      s1_try_ptr_chasing && !s1_ptr_chasing_canceled)
   XSPerfAccumulate("load_to_load_forward_try",                  s1_try_ptr_chasing)
   XSPerfAccumulate("load_to_load_forward_fail",                 s1_cancel_ptr_chasing)
diff --git a/src/main/scala/xiangshan/mem/vector/VMergeBuffer.scala b/src/main/scala/xiangshan/mem/vector/VMergeBuffer.scala
index 5d6209ebf3e..c48f329dbae 100644
--- a/src/main/scala/xiangshan/mem/vector/VMergeBuffer.scala
+++ b/src/main/scala/xiangshan/mem/vector/VMergeBuffer.scala
@@ -309,15 +309,19 @@ abstract class BaseVMergeBuffer(isVStore: Boolean=false)(implicit p: Parameters)
     lsqport.bits := ToLsqConnect(selEntry) // when uopwriteback, free MBuffer entry, write to lsq
     lsqport.valid:= selFire && selAllocated && !needRSReplay(entryIdx)
     //to RS
-    io.feedback(i).valid                 := selFire && selAllocated
-    io.feedback(i).bits.hit              := !needRSReplay(entryIdx)
-    io.feedback(i).bits.robIdx           := selEntry.uop.robIdx
-    io.feedback(i).bits.sourceType       := selEntry.sourceType
-    io.feedback(i).bits.flushState       := selEntry.flushState
-    io.feedback(i).bits.dataInvalidSqIdx := DontCare
-    io.feedback(i).bits.sqIdx            := selEntry.uop.sqIdx
-    io.feedback(i).bits.lqIdx            := selEntry.uop.lqIdx
-    // pipeline connect
+    val feedbackOut                       = WireInit(0.U.asTypeOf(io.feedback(i).bits)).suggestName(s"feedbackOut_${i}")
+    val feedbackValid                     = selFire && selAllocated
+    feedbackOut.hit                      := !needRSReplay(entryIdx)
+    feedbackOut.robIdx                   := selEntry.uop.robIdx
+    feedbackOut.sourceType               := selEntry.sourceType
+    feedbackOut.flushState               := selEntry.flushState
+    feedbackOut.dataInvalidSqIdx         := DontCare
+    feedbackOut.sqIdx                    := selEntry.uop.sqIdx
+    feedbackOut.lqIdx                    := selEntry.uop.lqIdx
+
+    io.feedback(i).valid                 := RegNext(feedbackValid)
+    io.feedback(i).bits                  := RegEnable(feedbackOut, feedbackValid)
+
     NewPipelineConnect(
       port, writeBackOut(i), writeBackOut(i).fire,
       Mux(port.fire,
diff --git a/src/main/scala/xiangshan/mem/vector/VSegmentUnit.scala b/src/main/scala/xiangshan/mem/vector/VSegmentUnit.scala
index ac1d2817b65..5223aac572f 100644
--- a/src/main/scala/xiangshan/mem/vector/VSegmentUnit.scala
+++ b/src/main/scala/xiangshan/mem/vector/VSegmentUnit.scala
@@ -488,6 +488,7 @@ class VSegmentUnit (implicit p: Parameters) extends VLSUModule
   io.rdcache.s1_paddr_dup_lsu       := instMicroOp.paddr
   io.rdcache.s1_paddr_dup_dcache    := instMicroOp.paddr
   io.rdcache.s1_kill                := false.B
+  io.rdcache.s1_kill_data_read      := false.B
   io.rdcache.s2_kill                := false.B
   if (env.FPGAPlatform){
     io.rdcache.s0_pc                := DontCare
@@ -593,7 +594,7 @@ class VSegmentUnit (implicit p: Parameters) extends VLSUModule
   }
 
   //update deqPtr
-  when(io.uopwriteback.fire){
+  when((state === s_finish) && !isEmpty(enqPtr, deqPtr)){
     deqPtr := deqPtr + 1.U
   }
 
@@ -609,31 +610,44 @@ class VSegmentUnit (implicit p: Parameters) extends VLSUModule
   when(stateNext === s_idle){
     instMicroOpValid := false.B
   }
-  io.uopwriteback.valid               := (state === s_finish) && !isEmpty(enqPtr, deqPtr)
-  io.uopwriteback.bits.uop            := uopq(deqPtr.value).uop
-  io.uopwriteback.bits.uop.vpu        := instMicroOp.uop.vpu
-  io.uopwriteback.bits.uop.exceptionVec := instMicroOp.uop.exceptionVec
-  io.uopwriteback.bits.mask.get       := instMicroOp.mask
-  io.uopwriteback.bits.data           := data(deqPtr.value)
-  io.uopwriteback.bits.vdIdx.get      := vdIdxInField
-  io.uopwriteback.bits.uop.vpu.vl     := instMicroOp.vl
-  io.uopwriteback.bits.uop.vpu.vstart := instMicroOp.vstart
-  io.uopwriteback.bits.uop.vpu.vmask  := maskUsed
-  io.uopwriteback.bits.uop.vpu.vuopIdx  := uopq(deqPtr.value).uop.vpu.vuopIdx
-  io.uopwriteback.bits.debug          := DontCare
-  io.uopwriteback.bits.vdIdxInField.get := vdIdxInField
-  io.uopwriteback.bits.uop.robIdx     := instMicroOp.uop.robIdx
-  io.uopwriteback.bits.uop.fuOpType   := instMicroOp.uop.fuOpType
+  // writeback to backend
+  val writebackOut                     = WireInit(io.uopwriteback.bits)
+  val writebackValid                   = (state === s_finish) && !isEmpty(enqPtr, deqPtr)
+  writebackOut.uop                    := uopq(deqPtr.value).uop
+  writebackOut.uop.vpu                := instMicroOp.uop.vpu
+  writebackOut.uop.exceptionVec       := instMicroOp.uop.exceptionVec
+  writebackOut.mask.get               := instMicroOp.mask
+  writebackOut.data                   := data(deqPtr.value)
+  writebackOut.vdIdx.get              := vdIdxInField
+  writebackOut.uop.vpu.vl             := instMicroOp.vl
+  writebackOut.uop.vpu.vstart         := instMicroOp.vstart
+  writebackOut.uop.vpu.vmask          := maskUsed
+  writebackOut.uop.vpu.vuopIdx        := uopq(deqPtr.value).uop.vpu.vuopIdx
+  writebackOut.debug                  := DontCare
+  writebackOut.vdIdxInField.get       := vdIdxInField
+  writebackOut.uop.robIdx             := instMicroOp.uop.robIdx
+  writebackOut.uop.fuOpType           := instMicroOp.uop.fuOpType
+
+  io.uopwriteback.valid               := RegNext(writebackValid)
+  io.uopwriteback.bits                := RegEnable(writebackOut, writebackValid)
+
+  dontTouch(writebackValid)
 
   //to RS
-  io.feedback.valid                   := state === s_finish && !isEmpty(enqPtr, deqPtr)
-  io.feedback.bits.hit                := true.B
-  io.feedback.bits.robIdx             := instMicroOp.uop.robIdx
-  io.feedback.bits.sourceType         := DontCare
-  io.feedback.bits.flushState         := DontCare
-  io.feedback.bits.dataInvalidSqIdx   := DontCare
-  io.feedback.bits.sqIdx              := uopq(deqPtr.value).uop.sqIdx
-  io.feedback.bits.lqIdx              := uopq(deqPtr.value).uop.lqIdx
+  val feedbackOut                      = WireInit(0.U.asTypeOf(io.feedback.bits))
+  val feedbackValid                    = state === s_finish && !isEmpty(enqPtr, deqPtr)
+  feedbackOut.hit                     := true.B
+  feedbackOut.robIdx                  := instMicroOp.uop.robIdx
+  feedbackOut.sourceType              := DontCare
+  feedbackOut.flushState              := DontCare
+  feedbackOut.dataInvalidSqIdx        := DontCare
+  feedbackOut.sqIdx                   := uopq(deqPtr.value).uop.sqIdx
+  feedbackOut.lqIdx                   := uopq(deqPtr.value).uop.lqIdx
+
+  io.feedback.valid                   := RegNext(feedbackValid)
+  io.feedback.bits                    := RegEnable(feedbackOut, feedbackValid)
+
+  dontTouch(feedbackValid)
 
   // exception
   io.exceptionInfo                    := DontCare
diff --git a/src/main/scala/xiangshan/mem/vector/VSplit.scala b/src/main/scala/xiangshan/mem/vector/VSplit.scala
index 300eabac5c2..68e6cbf7382 100644
--- a/src/main/scala/xiangshan/mem/vector/VSplit.scala
+++ b/src/main/scala/xiangshan/mem/vector/VSplit.scala
@@ -483,8 +483,10 @@ class VLSplitImp(implicit p: Parameters) extends VLSUModule{
   splitPipeline.io.redirect <> io.redirect
   io.toMergeBuffer <> splitPipeline.io.toMergeBuffer
 
+  // skid buffer
+  skidBuffer(splitPipeline.io.out, splitBuffer.io.in, splitBuffer.io.in.bits.uop.robIdx.needFlush(io.redirect), "VLSplitSkidBuffer")
+
   // Split Buffer
-  splitBuffer.io.in <> splitPipeline.io.out
   splitBuffer.io.redirect <> io.redirect
   io.out <> splitBuffer.io.out
 }
@@ -498,8 +500,10 @@ class VSSplitImp(implicit p: Parameters) extends VLSUModule{
   splitPipeline.io.redirect <> io.redirect
   io.toMergeBuffer <> splitPipeline.io.toMergeBuffer
 
+  // skid buffer
+  skidBuffer(splitPipeline.io.out, splitBuffer.io.in, splitBuffer.io.in.bits.uop.robIdx.needFlush(io.redirect),"VSSplitSkidBuffer")
+
   // Split Buffer
-  splitBuffer.io.in <> splitPipeline.io.out
   splitBuffer.io.redirect <> io.redirect
   io.out <> splitBuffer.io.out
   io.vstd.get <> splitBuffer.io.vstd.get
diff --git a/src/main/scala/xiangshan/mem/vector/VecCommon.scala b/src/main/scala/xiangshan/mem/vector/VecCommon.scala
index 0db6d43c2d1..a56f035be79 100644
--- a/src/main/scala/xiangshan/mem/vector/VecCommon.scala
+++ b/src/main/scala/xiangshan/mem/vector/VecCommon.scala
@@ -852,3 +852,54 @@ object genVFirstUnmask extends VLSUConstants {
   }
 }
 
+class skidBufferConnect[T <: Data](gen: T) extends Module {
+  val io = IO(new Bundle() {
+    val in = Flipped(DecoupledIO(gen.cloneType))
+    val flush = Input(Bool())
+    val out = DecoupledIO(gen.cloneType)
+  })
+
+  skidBuffer.connect(io.in, io.out, io.flush)
+}
+
+object skidBuffer{
+  /*
+  * Skid Buffer used to break timing path of ready
+  * */
+  def connect[T <: Data](
+                          in: DecoupledIO[T],
+                          out: DecoupledIO[T],
+                          flush: Bool
+                        ): T = {
+    val empty :: skid :: Nil = Enum(2)
+    val state      = RegInit(empty)
+    val stateNext  = WireInit(empty)
+    val dataBuffer = RegEnable(in.bits, (!out.ready && in.fire))
+
+    when(state === empty){
+      stateNext := Mux(!out.ready && in.fire && !flush, skid, empty)
+    }.elsewhen(state === skid){
+      stateNext := Mux(out.ready || flush, empty, skid)
+    }
+    state     := stateNext
+
+    in.ready  := state === empty
+    out.bits  := Mux(state === skid, dataBuffer, in.bits)
+    out.valid := in.valid || (state === skid)
+
+    dataBuffer
+  }
+  def apply[T <: Data](
+                        in: DecoupledIO[T],
+                        out: DecoupledIO[T],
+                        flush: Bool,
+                        moduleName: String
+                      ) {
+    val buffer = Module(new skidBufferConnect(in.bits))
+    buffer.suggestName(moduleName)
+    buffer.io.in <> in
+    buffer.io.flush := flush
+    out <> buffer.io.out
+  }
+}
+
```
