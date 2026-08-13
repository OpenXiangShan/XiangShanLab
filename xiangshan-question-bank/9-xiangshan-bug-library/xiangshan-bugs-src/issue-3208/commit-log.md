# Commit Log
- Issue: #3208
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/3208
- Issue state: closed
- Tested RTL commit: -
- Related PR: #3208
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/3208
- Changed files: 17
- Additions: 190
- Deletions: 95

## Files
- `src/main/scala/xiangshan/cache/dcache/DCacheWrapper.scala`
- `src/main/scala/xiangshan/cache/dcache/data/BankedDataArray.scala`
- `src/main/scala/xiangshan/cache/dcache/loadpipe/LoadPipe.scala`
- `src/main/scala/xiangshan/cache/mmu/MMUBundle.scala`
- `src/main/scala/xiangshan/cache/mmu/TLB.scala`
- `src/main/scala/xiangshan/cache/mmu/TLBStorage.scala`
- `src/main/scala/xiangshan/frontend/IFU.scala`
- `src/main/scala/xiangshan/mem/lsqueue/LoadQueue.scala`
- `src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala`
- `src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala`
- `src/main/scala/xiangshan/mem/pipeline/StoreUnit.scala`
- `src/main/scala/xiangshan/mem/prefetch/L1PrefetchComponent.scala`
- `src/main/scala/xiangshan/mem/prefetch/SMSPrefetcher.scala`
- `src/main/scala/xiangshan/mem/vector/VSegmentUnit.scala`
- `src/main/scala/xiangshan/mem/vector/VSplit.scala`
- `src/main/scala/xiangshan/mem/vector/VecBundle.scala`
- `src/main/scala/xiangshan/mem/vector/VecCommon.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/cache/dcache/DCacheWrapper.scala b/src/main/scala/xiangshan/cache/dcache/DCacheWrapper.scala
index e688a3bbea3..0b51f39acba 100644
--- a/src/main/scala/xiangshan/cache/dcache/DCacheWrapper.scala
+++ b/src/main/scala/xiangshan/cache/dcache/DCacheWrapper.scala
@@ -1122,7 +1122,7 @@ class DCacheImp(outer: DCache) extends LazyModuleImp(outer) with HasDCacheParame
     bankedDataArray.io.is128Req(i) <> ldu(i).io.is128Req
     bankedDataArray.io.read_error_delayed(i) <> ldu(i).io.read_error_delayed
 
-    ldu(i).io.banked_data_resp := bankedDataArray.io.read_resp_delayed(i)
+    ldu(i).io.banked_data_resp := bankedDataArray.io.read_resp(i)
 
     ldu(i).io.bank_conflict_slow := bankedDataArray.io.bank_conflict_slow(i)
   })
diff --git a/src/main/scala/xiangshan/cache/dcache/data/BankedDataArray.scala b/src/main/scala/xiangshan/cache/dcache/data/BankedDataArray.scala
index cc2e7b6cfb9..13f86d0db67 100644
--- a/src/main/scala/xiangshan/cache/dcache/data/BankedDataArray.scala
+++ b/src/main/scala/xiangshan/cache/dcache/data/BankedDataArray.scala
@@ -256,7 +256,7 @@ abstract class AbstractBankedDataArray(implicit p: Parameters) extends DCacheMod
     // data for readline and loadpipe
     val readline_resp = Output(Vec(DCacheBanks, new L1BankedDataReadResult()))
     val readline_error_delayed = Output(Bool())
-    val read_resp_delayed = Output(Vec(LoadPipelineWidth, Vec(VLEN/DCacheSRAMRowBits, new L1BankedDataReadResult())))
+    val read_resp          = Output(Vec(LoadPipelineWidth, Vec(VLEN/DCacheSRAMRowBits, new L1BankedDataReadResult())))
     val read_error_delayed = Output(Vec(LoadPipelineWidth,Vec(VLEN/DCacheSRAMRowBits, Bool())))
     // val nacks = Output(Vec(LoadPipelineWidth, Bool()))
     // val errors = Output(Vec(LoadPipelineWidth + 1, ValidIO(new L1CacheErrorInfo))) // read ports + readline port
@@ -305,8 +305,8 @@ abstract class AbstractBankedDataArray(implicit p: Parameters) extends DCacheMod
     XSDebug(s"DataArray ReadeResp channel:\n")
     (0 until LoadPipelineWidth) map { r =>
       XSDebug(s"cycle: $r data: %x\n", Mux(io.is128Req(r),
-        Cat(io.read_resp_delayed(r)(1).raw_data,io.read_resp_delayed(r)(0).raw_data),
-        io.read_resp_delayed(r)(0).raw_data))
+        Cat(io.read_resp(r)(1).raw_data,io.read_resp(r)(0).raw_data),
+        io.read_resp(r)(0).raw_data))
     }
   }
 
@@ -506,15 +506,19 @@ class SramedDataArray(implicit p: Parameters) extends AbstractBankedDataArray {
   XSPerfAccumulate("data_read_counter", PopCount(Cat(data_read_oh)))
 
   // read result: expose banked read result
+  // TODO: clock gate
   (0 until LoadPipelineWidth).map(i => {
     // io.read_resp(i) := read_result(RegNext(bank_addrs(i)))(RegNext(OHToUInt(way_en(i))))
     val r_read_fire = RegNext(io.read(i).fire)
+    val r_div_addr  = RegEnable(div_addrs(i), io.read(i).fire)
+    val r_bank_addr = RegEnable(bank_addrs(i), io.read(i).fire)
+    val r_way_addr  = RegNext(OHToUInt(way_en(i)))
     val rr_read_fire = RegNext(RegNext(io.read(i).fire))
     val rr_div_addr = RegEnable(RegEnable(div_addrs(i), io.read(i).fire), r_read_fire)
     val rr_bank_addr = RegEnable(RegEnable(bank_addrs(i), io.read(i).fire), r_read_fire)
     val rr_way_addr = RegEnable(RegEnable(OHToUInt(way_en(i)), io.read(i).fire), r_read_fire)
     (0 until VLEN/DCacheSRAMRowBits).map( j =>{
-      io.read_resp_delayed(i)(j) := read_result_delayed(rr_div_addr)(rr_bank_addr(j))(rr_way_addr)
+      io.read_resp(i)(j) := read_result(r_div_addr)(r_bank_addr(j))(r_way_addr)
       // error detection
       // normal read ports
       io.read_error_delayed(i)(j) := rr_read_fire && read_error_delayed_result(rr_div_addr)(rr_bank_addr(j))(rr_way_addr) && !RegNext(io.bank_conflict_slow(i))
@@ -901,12 +905,14 @@ class BankedDataArray(implicit p: Parameters) extends AbstractBankedDataArray {
 
   (0 until LoadPipelineWidth).map(i => {
     val r_read_fire = RegNext(io.read(i).fire)
+    val r_div_addr = RegEnable(div_addrs(i), io.read(i).fire)
+    val r_bank_addr = RegEnable(bank_addrs(i), io.read(i).fire)
     val rr_read_fire = RegNext(r_read_fire)
     val rr_div_addr = RegEnable(RegEnable(div_addrs(i), io.read(i).fire), r_read_fire)
     val rr_bank_addr = RegEnable(RegEnable(bank_addrs(i), io.read(i).fire), r_read_fire)
     val rr_way_addr = RegEnable(RegEnable(OHToUInt(way_en(i)), io.read(i).fire), r_read_fire)
     (0 until VLEN/DCacheSRAMRowBits).map( j =>{
-      io.read_resp_delayed(i)(j) := bank_result_delayed(rr_div_addr)(rr_bank_addr(j))
+      io.read_resp(i)(j)          := bank_result(r_div_addr)(r_bank_addr(j))
       // error detection
       io.read_error_delayed(i)(j) := rr_read_fire && read_bank_error_delayed(rr_div_addr)(rr_bank_addr(j)) && !RegNext(io.bank_conflict_slow(i))
     })
diff --git a/src/main/scala/xiangshan/cache/dcache/loadpipe/LoadPipe.scala b/src/main/scala/xiangshan/cache/dcache/loadpipe/LoadPipe.scala
index 309a3d771cb..4ff4d442e74 100644
--- a/src/main/scala/xiangshan/cache/dcache/loadpipe/LoadPipe.scala
+++ b/src/main/scala/xiangshan/cache/dcache/loadpipe/LoadPipe.scala
@@ -358,6 +358,9 @@ class LoadPipe(id: Int)(implicit p: Parameters) extends DCacheModule with HasPer
 
   val s2_hit = s2_tag_match && s2_has_permission && s2_hit_coh === s2_new_hit_coh && !s2_wpu_pred_fail
 
+  val s2_data128bit = Cat(io.banked_data_resp(1).raw_data, io.banked_data_resp(0).raw_data)
+  val s2_data64bit = Fill(2, io.banked_data_resp(0).raw_data)
+
   // only dump these signals when they are actually valid
   dump_pipeline_valids("LoadPipe s2", "s2_hit", s2_valid && s2_hit)
   dump_pipeline_valids("LoadPipe s2", "s2_nack", s2_valid && s2_nack)
@@ -480,9 +483,7 @@ class LoadPipe(id: Int)(implicit p: Parameters) extends DCacheModule with HasPer
   val s3_req_instrtype = RegEnable(s2_req.instrtype, s2_fire)
   val s3_is_prefetch = s3_req_instrtype === DCACHE_PREFETCH_SOURCE.U
 
-  val s3_data128bit = Cat(io.banked_data_resp(1).raw_data, io.banked_data_resp(0).raw_data)
-  val s3_data64bit = Fill(2, io.banked_data_resp(0).raw_data)
-  val s3_banked_data_resp_word = Mux(s3_load128Req, s3_data128bit, s3_data64bit)
+  val s3_banked_data_resp_word = RegEnable(Mux(s2_hit, Mux(s2_load128Req, s2_data128bit, s2_data64bit), 0.U), s2_fire)
   val s3_data_error = Mux(s3_load128Req, io.read_error_delayed.asUInt.orR, io.read_error_delayed(0)) && s3_hit
   val s3_tag_error = RegEnable(s2_tag_error, s2_fire)
   val s3_flag_error = RegEnable(s2_flag_error, s2_fire)
diff --git a/src/main/scala/xiangshan/cache/mmu/MMUBundle.scala b/src/main/scala/xiangshan/cache/mmu/MMUBundle.scala
index 914163d54ba..50228c92917 100644
--- a/src/main/scala/xiangshan/cache/mmu/MMUBundle.scala
+++ b/src/main/scala/xiangshan/cache/mmu/MMUBundle.scala
@@ -634,6 +634,7 @@ class TlbReq(implicit p: Parameters) extends TlbBundle {
   val memidx = Output(new MemBlockidxBundle)
   // do not translate, but still do pmp/pma check
   val no_translate = Output(Bool())
+  val pmp_addr = Output(UInt(PAddrBits.W)) // load s1 send prefetch paddr
   val debug = new Bundle {
     val pc = Output(UInt(XLEN.W))
     val robIdx = Output(new RobPtr)
diff --git a/src/main/scala/xiangshan/cache/mmu/TLB.scala b/src/main/scala/xiangshan/cache/mmu/TLB.scala
index 2ca42edcd55..c2979370015 100644
--- a/src/main/scala/xiangshan/cache/mmu/TLB.scala
+++ b/src/main/scala/xiangshan/cache/mmu/TLB.scala
@@ -131,7 +131,11 @@ class TLB(Width: Int, nRespDups: Int = 1, Block: Seq[Boolean], q: TLBParameters)
   // check pmp use paddr (for timing optization, use pmp_addr here)
   // check permisson
   (0 until Width).foreach{i =>
-    pmp_check(pmp_addr(i), req_out(i).size, req_out(i).cmd, i)
+    when (RegNext(req(i).bits.no_translate)) {
+      pmp_check(req(i).bits.pmp_addr, req_out(i).size, req_out(i).cmd, i)
+    } .otherwise {
+      pmp_check(pmp_addr(i), req_out(i).size, req_out(i).cmd, i)
+    }
     for (d <- 0 until nRespDups) {
       perm_check(perm(i)(d), req_out(i).cmd, i, d, g_perm(i)(d), req_out(i).hlvx, req_out_s2xlate(i))
     }
diff --git a/src/main/scala/xiangshan/cache/mmu/TLBStorage.scala b/src/main/scala/xiangshan/cache/mmu/TLBStorage.scala
index 2305efc1bb8..c31cefaf136 100644
--- a/src/main/scala/xiangshan/cache/mmu/TLBStorage.scala
+++ b/src/main/scala/xiangshan/cache/mmu/TLBStorage.scala
@@ -128,19 +128,23 @@ class TLBFA(
 
     resp.valid := GatedValidRegNext(req.valid)
     resp.bits.hit := Cat(hitVecReg).orR
+    val ppnReg   = RegEnable(VecInit(entries.map(_.genPPN(saveLevel, req.valid)(vpn))), req.fire)
+    val permReg  = RegEnable(VecInit(entries.map(_.perm)), req.fire)
+    val gPermReg = RegEnable(VecInit(entries.map(_.g_perm)), req.fire)
+    val s2xLate  = RegEnable(VecInit(entries.map(_.s2xlate)), req.fire)
     if (nWays == 1) {
       for (d <- 0 until nDups) {
-        resp.bits.ppn(d) := RegEnable(entries(0).genPPN(saveLevel, req.valid)(vpn), req.fire)
-        resp.bits.perm(d) := RegEnable(entries(0).perm, req.fire)
-        resp.bits.g_perm(d) := RegEnable(entries(0).g_perm, req.fire)
-        resp.bits.s2xlate(d) := RegEnable(entries(0).s2xlate, req.fire)
+        resp.bits.ppn(d) := ppnReg(0)
+        resp.bits.perm(d) := permReg(0)
+        resp.bits.g_perm(d) := gPermReg(0)
+        resp.bits.s2xlate(d) := s2xLate(0)
       }
     } else {
       for (d <- 0 until nDups) {
-        resp.bits.ppn(d) := RegEnable(ParallelMux(hitVec zip entries.map(_.genPPN(saveLevel, req.valid)(vpn))), req.fire)
-        resp.bits.perm(d) := RegEnable(ParallelMux(hitVec zip entries.map(_.perm)), req.fire)
-        resp.bits.g_perm(d) := RegEnable(ParallelMux(hitVec zip entries.map(_.g_perm)), req.fire)
-        resp.bits.s2xlate(d) := RegEnable(ParallelMux(hitVec zip entries.map(_.s2xlate)), req.fire)
+        resp.bits.ppn(d) := Mux1H(hitVecReg zip ppnReg)
+        resp.bits.perm(d) := Mux1H(hitVecReg zip permReg)
+        resp.bits.g_perm(d) := Mux1H(hitVecReg zip gPermReg)
+        resp.bits.s2xlate(d) := Mux1H(hitVecReg zip s2xLate)
       }
     }
 
diff --git a/src/main/scala/xiangshan/frontend/IFU.scala b/src/main/scala/xiangshan/frontend/IFU.scala
index 0654255b639..52af5874e76 100644
--- a/src/main/scala/xiangshan/frontend/IFU.scala
+++ b/src/main/scala/xiangshan/frontend/IFU.scala
@@ -739,6 +739,7 @@ class NewIFU(implicit p: Parameters) extends XSModule
   io.iTLBInter.req.bits.debug.robIdx        := DontCare
   io.iTLBInter.req.bits.no_translate        := false.B
   io.iTLBInter.req.bits.debug.isFirstIssue  := DontCare
+  io.iTLBInter.req.bits.pmp_addr            := DontCare
 
   io.pmp.req.valid := (mmio_state === m_sendPMP) && f3_req_is_mmio
   io.pmp.req.bits.addr  := mmio_resend_addr
diff --git a/src/main/scala/xiangshan/mem/lsqueue/LoadQueue.scala b/src/main/scala/xiangshan/mem/lsqueue/LoadQueue.scala
index abfc111c00e..34b87ef941a 100644
--- a/src/main/scala/xiangshan/mem/lsqueue/LoadQueue.scala
+++ b/src/main/scala/xiangshan/mem/lsqueue/LoadQueue.scala
@@ -77,6 +77,44 @@ trait HasLoadHelper { this: XSModule =>
     ))
   }
 
+  def genRdataOH(uop: DynInst): UInt = {
+    val fuOpType = uop.fuOpType
+    val fpWen    = uop.fpWen
+    val result = Cat(
+      (fuOpType === LSUOpType.lw && fpWen),
+      (fuOpType === LSUOpType.lw && !fpWen) || (fuOpType === LSUOpType.hlvw),
+      (fuOpType === LSUOpType.lh)           || (fuOpType === LSUOpType.hlvh),
+      (fuOpType === LSUOpType.lb)           || (fuOpType === LSUOpType.hlvb),
+      (fuOpType === LSUOpType.ld)           || (fuOpType === LSUOpType.hlvd),
+      (fuOpType === LSUOpType.lwu)          || (fuOpType === LSUOpType.hlvwu) || (fuOpType === LSUOpType.hlvxwu),
+      (fuOpType === LSUOpType.lhu)          || (fuOpType === LSUOpType.hlvhu) || (fuOpType === LSUOpType.hlvxhu),
+      (fuOpType === LSUOpType.lbu)          || (fuOpType === LSUOpType.hlvbu),
+    )
+    result
+  }
+
+  def newRdataHelper(select: UInt, rdata: UInt): UInt = {
+    XSError(PopCount(select) > 1.U, "data selector must be One-Hot!\n")
+    val selData = Seq(
+      ZeroExt(rdata(7, 0), XLEN),
+      ZeroExt(rdata(15, 0), XLEN),
+      ZeroExt(rdata(31, 0), XLEN),
+      rdata(63, 0),
+      SignExt(rdata(7, 0) , XLEN),
+      SignExt(rdata(15, 0) , XLEN),
+      SignExt(rdata(31, 0) , XLEN),
+      FPU.box(rdata, FPU.S)
+    )
+    Mux1H(select, selData)
+  }
+
+  def genDataSelectByOffset(addrOffset: UInt): Vec[Bool] = {
+    require(addrOffset.getWidth == 4)
+    VecInit((0 until 16).map{ case i =>
+      addrOffset === i.U
+    })
+  }
+
   def rdataVecHelper(alignedType: UInt, rdata: UInt): UInt = {
     LookupTree(alignedType, List(
       "b00".U -> ZeroExt(rdata(7, 0), VLEN),
diff --git a/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala b/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala
index 73191c8b455..f1e9b83a99d 100644
--- a/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala
+++ b/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala
@@ -840,7 +840,7 @@ class StoreQueue(implicit p: Parameters) extends XSModule
   // (4) or vector store:
   // TODO: implement it!
   io.vecmmioStout := DontCare
-  io.vecmmioStout.valid := uncacheState === s_wb && isVec(deqPtr)
+  io.vecmmioStout.valid := false.B //uncacheState === s_wb && isVec(deqPtr)
   io.vecmmioStout.bits.uop := uop(deqPtr)
   io.vecmmioStout.bits.uop.sqIdx := deqPtrExt(0)
   io.vecmmioStout.bits.data := shiftDataToLow(paddrModule.io.rdata(0), dataModule.io.rdata(0).data) // dataModule.io.rdata.read(deqPtr)
diff --git a/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala b/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala
index 5695967c754..1a176e19ae1 100644
--- a/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala
+++ b/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala
@@ -202,6 +202,8 @@ class LoadUnit(implicit p: Parameters) extends XSModule
   val s0_fire          = s0_valid && s0_can_go
   val s0_mmio_fire     = s0_mmio_select && s0_can_go
   val s0_out           = Wire(new LqWriteBundle)
+  val s0_tlb_vaddr     = Wire(UInt(VAddrBits.W))
+  val s0_dcache_vaddr  = Wire(UInt(VAddrBits.W))
 
   // flow source bundle
   class FlowSource extends Bundle {
@@ -369,12 +371,12 @@ class LoadUnit(implicit p: Parameters) extends XSModule
   io.canAcceptHighConfPrefetch := s0_high_conf_prf_ready && io.dcache.req.ready
 
   // query DTLB
-  io.tlb.req.valid                   := s0_valid
+  io.tlb.req.valid                   := s0_valid && !s0_hw_prf_select // if is hardware prefetch, don't send valid to tlb, but need no_translate
   io.tlb.req.bits.cmd                := Mux(s0_sel_src.prf,
                                          Mux(s0_sel_src.prf_wr, TlbCmd.write, TlbCmd.read),
                                          TlbCmd.read
                                        )
-  io.tlb.req.bits.vaddr              := Mux(s0_hw_prf_select, io.prefetch_req.bits.paddr, s0_sel_src.vaddr)
+  io.tlb.req.bits.vaddr              := s0_tlb_vaddr
   io.tlb.req.bits.hyperinst          := s0_sel_src.hlv
   io.tlb.req.bits.hlvx               := s0_sel_src.hlvx
   io.tlb.req.bits.size               := Mux(s0_sel_src.isvec, s0_sel_src.alignedType(2,0), LSUOpType.size(s0_sel_src.uop.fuOpType))
@@ -383,7 +385,7 @@ class LoadUnit(implicit p: Parameters) extends XSModule
   io.tlb.req.bits.memidx.is_st       := false.B
   io.tlb.req.bits.memidx.idx         := s0_sel_src.uop.lqIdx.value
   io.tlb.req.bits.debug.robIdx       := s0_sel_src.uop.robIdx
-  io.tlb.req.bits.no_translate       := s0_hw_prf_select  // hw b.reqetch addr does not need to be translated
+  io.tlb.req.bits.no_translate       := s0_hw_prf_select  // hw b.reqetch addr does not need to be translated, need this signal for pmp check
   io.tlb.req.bits.debug.pc           := s0_sel_src.uop.pc
   io.tlb.req.bits.debug.isFirstIssue := s0_sel_src.isFirstIssue
 
@@ -393,7 +395,7 @@ class LoadUnit(implicit p: Parameters) extends XSModule
                                       MemoryOpConstants.M_PFR,
                                       Mux(s0_sel_src.prf_wr, MemoryOpConstants.M_PFW, MemoryOpConstants.M_XRD)
                                     )
-  io.dcache.req.bits.vaddr        := s0_sel_src.vaddr
+  io.dcache.req.bits.vaddr        := s0_dcache_vaddr
   io.dcache.req.bits.mask         := s0_sel_src.mask
   io.dcache.req.bits.data         := DontCare
   io.dcache.req.bits.isFirstIssue := s0_sel_src.isFirstIssue
@@ -413,7 +415,6 @@ class LoadUnit(implicit p: Parameters) extends XSModule
 
   def fromFastReplaySource(src: LqWriteBundle): FlowSource = {
     val out = WireInit(0.U.asTypeOf(new FlowSource))
-    out.vaddr         := src.vaddr
     out.mask          := src.mask
     out.uop           := src.uop
     out.try_l2l       := false.B
@@ -447,7 +448,6 @@ class LoadUnit(implicit p: Parameters) extends XSModule
   // TODO: implement vector mmio
   def fromMmioSource(src: MemExuOutput) = {
     val out = WireInit(0.U.asTypeOf(new FlowSource))
-    out.vaddr        := 0.U
     out.mask          := 0.U
     out.uop           := src.uop
     out.try_l2l       := false.B
@@ -470,7 +470,6 @@ class LoadUnit(implicit p: Parameters) extends XSModule
 
   def fromNormalReplaySource(src: LsPipelineBundle): FlowSource = {
     val out = WireInit(0.U.asTypeOf(new FlowSource))
-    out.vaddr         := src.vaddr
     out.mask          := Mux(src.isvec, src.mask, genVWmask(src.vaddr, src.uop.fuOpType(1, 0)))
     out.uop           := src.uop
     out.try_l2l       := false.B
@@ -504,7 +503,6 @@ class LoadUnit(implicit p: Parameters) extends XSModule
   // TODO: implement vector prefetch
   def fromPrefetchSource(src: L1PrefetchReq): FlowSource = {
     val out = WireInit(0.U.asTypeOf(new FlowSource))
-    out.vaddr         := src.getVaddr()
     out.mask          := 0.U
     out.uop           := DontCare
     out.try_l2l       := false.B
@@ -524,7 +522,6 @@ class LoadUnit(implicit p: Parameters) extends XSModule
 
   def fromVecIssueSource(src: VecPipeBundle): FlowSource = {
     val out = WireInit(0.U.asTypeOf(new FlowSource))
-    out.vaddr         := src.vaddr
     out.mask          := src.mask
     out.uop           := src.uop
     out.try_l2l       := false.B
@@ -566,8 +563,8 @@ class LoadUnit(implicit p: Parameters) extends XSModule
 
   def fromIntIssueSource(src: MemExuInput): FlowSource = {
     val out = WireInit(0.U.asTypeOf(new FlowSource))
-    out.vaddr         := src.src(0) + SignExt(src.uop.imm(11, 0), VAddrBits)
-    out.mask          := genVWmask(out.vaddr, src.uop.fuOpType(1,0))
+    val addr           = io.ldin.bits.src(0) + SignExt(io.ldin.bits.uop.imm(11, 0), VAddrBits)
+    out.mask          := genVWmask(addr, src.uop.fuOpType(1,0))
     out.uop           := src.uop
     out.try_l2l       := false.B
     out.has_rob_entry := true.B
@@ -590,7 +587,6 @@ class LoadUnit(implicit p: Parameters) extends XSModule
   // TODO: implement vector l2l
   def fromLoadToLoadSource(src: LoadToLoadIO): FlowSource = {
     val out = WireInit(0.U.asTypeOf(new FlowSource))
-    out.vaddr              := Cat(src.data(XLEN-1, 6), s0_ptr_chasing_vaddr(5,0))
     out.mask               := genVWmask(0.U, LSUOpType.ld)
     // When there's no valid instruction from RS and LSQ, we try the load-to-load forwarding.
     // Assume the pointer chasing is always ld.
@@ -616,14 +612,15 @@ class LoadUnit(implicit p: Parameters) extends XSModule
 
   // set default
   val s0_src_selector = Seq(
-    s0_super_ld_rep_select,
-    s0_ld_fast_rep_select,
-    s0_ld_mmio_select,
-    s0_ld_rep_select,
-    s0_hw_prf_select,
-    s0_vec_iss_select,
-    s0_int_iss_select,
-    (if (EnableLoadToLoadForward) s0_l2l_fwd_select else true.B)
+    s0_super_ld_rep_valid,
+    s0_ld_fast_rep_valid,
+    s0_ld_mmio_valid,
+    s0_ld_rep_valid,
+    s0_high_conf_prf_valid,
+    s0_vec_iss_valid,
+    s0_int_iss_valid,
+    (if (EnableLoadToLoadForward) s0_l2l_fwd_valid else false.B),
+    s0_low_conf_prf_valid
   )
   val s0_src_format = Seq(
     fromNormalReplaySource(io.replay.bits),
@@ -633,23 +630,43 @@ class LoadUnit(implicit p: Parameters) extends XSModule
     fromPrefetchSource(io.prefetch_req.bits),
     fromVecIssueSource(io.vecldin.bits),
     fromIntIssueSource(io.ldin.bits),
-    (if (EnableLoadToLoadForward) fromLoadToLoadSource(io.l2l_fwd_in) else fromNullSource())
+    (if (EnableLoadToLoadForward) fromLoadToLoadSource(io.l2l_fwd_in) else fromNullSource()),
+    fromPrefetchSource(io.prefetch_req.bits)
   )
   s0_sel_src := ParallelPriorityMux(s0_src_selector, s0_src_format)
 
+  val s0_addr_selector = Seq(
+    s0_super_ld_rep_valid,
+    s0_ld_fast_rep_valid,
+    s0_ld_rep_valid,
+    s0_vec_iss_valid,
+    s0_int_iss_valid,
+    (if (EnableLoadToLoadForward) s0_l2l_fwd_valid else false.B),
+  )
+  val s0_addr_format = Seq(
+    io.replay.bits.vaddr,
+    io.fast_rep_in.bits.vaddr,
+    io.replay.bits.vaddr,
+    io.vecldin.bits.vaddr,
+    io.ldin.bits.src(0) + SignExt(io.ldin.bits.uop.imm(11, 0), VAddrBits),
+    (if (EnableLoadToLoadForward) Cat(io.l2l_fwd_in.data(XLEN-1, 6), s0_ptr_chasing_vaddr(5,0)) else 0.U(VAddrBits.W)),
+  )
+  s0_tlb_vaddr := ParallelPriorityMux(s0_addr_selector, s0_addr_format)
+  s0_dcache_vaddr := Mux(s0_hw_prf_select, io.prefetch_req.bits.getVaddr(), s0_tlb_vaddr)
+
   // address align check
   val s0_addr_aligned = LookupTree(Mux(s0_sel_src.isvec, s0_sel_src.alignedType(1,0), s0_sel_src.uop.fuOpType(1, 0)), List(
     "b00".U   -> true.B,                   //b
-    "b01".U   -> (s0_sel_src.vaddr(0)    === 0.U), //h
-    "b10".U   -> (s0_sel_src.vaddr(1, 0) === 0.U), //w
-    "b11".U   -> (s0_sel_src.vaddr(2, 0) === 0.U)  //d
+    "b01".U   -> (s0_dcache_vaddr(0)    === 0.U), //h
+    "b10".U   -> (s0_dcache_vaddr(1, 0) === 0.U), //w
+    "b11".U   -> (s0_dcache_vaddr(2, 0) === 0.U)  //d
   ))
-  XSError(s0_sel_src.isvec && s0_sel_src.vaddr(3, 0) =/= 0.U && s0_sel_src.alignedType(2), "unit-stride 128 bit element is not aligned!")
+  XSError(s0_sel_src.isvec && s0_dcache_vaddr(3, 0) =/= 0.U && s0_sel_src.alignedType(2), "unit-stride 128 bit element is not aligned!")
 
   // accept load flow if dcache ready (tlb is always ready)
   // TODO: prefetch need writeback to loadQueueFlag
   s0_out               := DontCare
-  s0_out.vaddr         := s0_sel_src.vaddr
+  s0_out.vaddr         := s0_dcache_vaddr
   s0_out.mask          := s0_sel_src.mask
   s0_out.uop           := s0_sel_src.uop
   s0_out.isFirstIssue  := s0_sel_src.isFirstIssue
@@ -663,6 +680,7 @@ class LoadUnit(implicit p: Parameters) extends XSModule
   s0_out.isvec           := s0_sel_src.isvec
   s0_out.is128bit        := s0_sel_src.is128bit
   s0_out.uop_unit_stride_fof := s0_sel_src.uop_unit_stride_fof
+  s0_out.paddr         := io.prefetch_req.bits.paddr // only for prefetch
   // s0_out.rob_idx_valid   := s0_rob_idx_valid
   // s0_out.inner_idx       := s0_inner_idx
   // s0_out.rob_idx         := s0_rob_idx
@@ -712,11 +730,26 @@ class LoadUnit(implicit p: Parameters) extends XSModule
 
   // load wakeup
   // TODO: vector load wakeup?
-  io.wakeup.valid := !s0_sel_src.isvec && s0_fire && (s0_super_ld_rep_select || s0_ld_fast_rep_select || s0_ld_rep_select || s0_int_iss_select) || s0_mmio_fire
-  io.wakeup.bits := s0_out.uop
+  val s0_wakeup_selector = Seq(
+    s0_super_ld_rep_valid,
+    s0_ld_fast_rep_valid,
+    s0_mmio_fire,
+    s0_ld_rep_valid,
+    s0_int_iss_valid
+  )
+  val s0_wakeup_format = Seq(
+    io.replay.bits.uop,
+    io.fast_rep_in.bits.uop,
+    io.lsq.uncache.bits.uop,
+    io.replay.bits.uop,
+    io.ldin.bits.uop,
+  )
+  val s0_wakeup_uop = ParallelPriorityMux(s0_wakeup_selector, s0_wakeup_format)
+  io.wakeup.valid := !s0_sel_src.isvec && s0_fire && (s0_super_ld_rep_valid || s0_ld_fast_rep_valid || s0_ld_rep_valid || s0_int_iss_valid && !s0_vec_iss_valid && !s0_high_conf_prf_valid) || s0_mmio_fire
+  io.wakeup.bits := s0_wakeup_uop
 
   XSDebug(io.dcache.req.fire,
-    p"[DCACHE LOAD REQ] pc ${Hexadecimal(s0_sel_src.uop.pc)}, vaddr ${Hexadecimal(s0_sel_src.vaddr)}\n"
+    p"[DCACHE LOAD REQ] pc ${Hexadecimal(s0_sel_src.uop.pc)}, vaddr ${Hexadecimal(s0_dcache_vaddr)}\n"
   )
   XSDebug(s0_valid,
     p"S0: pc ${Hexadecimal(s0_out.uop.pc)}, lId ${Hexadecimal(s0_out.uop.lqIdx.asUInt)}, " +
@@ -761,9 +794,9 @@ class LoadUnit(implicit p: Parameters) extends XSModule
   s1_vaddr_hi         := s1_in.vaddr(VAddrBits - 1, 6)
   s1_vaddr_lo         := s1_in.vaddr(5, 0)
   s1_vaddr            := Cat(s1_vaddr_hi, s1_vaddr_lo)
-  s1_paddr_dup_lsu    := io.tlb.resp.bits.paddr(0)
-  s1_paddr_dup_dcache := io.tlb.resp.bits.paddr(1)
-  s1_gpaddr_dup_lsu   := io.tlb.resp.bits.gpaddr(0)
+  s1_paddr_dup_lsu    := Mux(s1_hw_prf, s1_in.paddr, io.tlb.resp.bits.paddr(0))
+  s1_paddr_dup_dcache := Mux(s1_hw_prf, s1_in.paddr, io.tlb.resp.bits.paddr(1))
+  s1_gpaddr_dup_lsu   := Mux(s1_hw_prf, s1_in.paddr, io.tlb.resp.bits.gpaddr(0))
 
   when (s1_tlb_memidx.is_ld && io.tlb.resp.valid && !s1_tlb_miss && s1_tlb_memidx.idx === s1_in.uop.lqIdx.value) {
     // printf("load idx = %d\n", s1_tlb_memidx.idx)
@@ -771,6 +804,7 @@ class LoadUnit(implicit p: Parameters) extends XSModule
   }
 
   io.tlb.req_kill   := s1_kill || s1_dly_err
+  io.tlb.req.bits.pmp_addr := s1_in.paddr
   io.tlb.resp.ready := true.B
 
   io.dcache.s1_paddr_dup_lsu    <> s1_paddr_dup_lsu
@@ -924,6 +958,8 @@ class LoadUnit(implicit p: Parameters) extends XSModule
   val s2_fire   = s2_valid && !s2_kill && s2_can_go
   val s2_vecActive = RegEnable(s1_out.vecActive, true.B, s1_fire)
   val s2_isvec  = RegEnable(s1_out.isvec, false.B, s1_fire)
+  val s2_data_select  = genRdataOH(s2_out.uop)
+  val s2_data_select_by_offset = genDataSelectByOffset(s2_out.paddr(3, 0))
 
   s2_kill := s2_in.uop.robIdx.needFlush(io.redirect)
   s2_ready := !s2_valid || s2_kill || s3_ready
@@ -1154,7 +1190,7 @@ class LoadUnit(implicit p: Parameters) extends XSModule
   // RegNext prefetch train for better timing
   // ** Now, prefetch train is valid at load s3 **
   val s2_prefetch_train_valid = WireInit(false.B)
-  s2_prefetch_train_valid              := s2_valid && !s2_actually_mmio && !s2_in.tlbMiss
+  s2_prefetch_train_valid              := s2_valid && !s2_actually_mmio && (!s2_in.tlbMiss || s2_hw_prf)
   io.prefetch_train.valid              := GatedValidRegNext(s2_prefetch_train_valid)
   io.prefetch_train.bits.fromLsPipelineBundle(s2_in, latch = true, enable = s2_prefetch_train_valid)
   io.prefetch_train.bits.miss          := RegEnable(io.dcache.resp.bits.miss, s2_prefetch_train_valid) // TODO: use trace with bank conflict?
@@ -1207,6 +1243,8 @@ class LoadUnit(implicit p: Parameters) extends XSModule
   val s3_vec_alignedType = RegEnable(s2_out.alignedType, s2_fire)
   val s3_vec_mBIndex     = RegEnable(s2_out.mbIndex, s2_fire)
   val s3_mmio         = Wire(Valid(new MemExuOutput))
+  val s3_data_select  = RegEnable(s2_data_select, 0.U(s2_data_select.getWidth.W), s2_fire)
+  val s3_data_select_by_offset = RegEnable(s2_data_select_by_offset, 0.U.asTypeOf(s2_data_select_by_offset), s2_fire)
   // TODO: Fix vector load merge buffer nack
   val s3_vec_mb_nack  = Wire(Bool())
   s3_vec_mb_nack     := false.B
@@ -1367,31 +1405,32 @@ class LoadUnit(implicit p: Parameters) extends XSModule
   s3_ld_raw_data_frm_cache.forward_result_valid := RegEnable(s2_fwd_data_valid, false.B, s2_valid)
 
   val s3_merged_data_frm_cache = s3_ld_raw_data_frm_cache.mergedData()
-  val s3_picked_data_frm_cache = LookupTree(s3_ld_raw_data_frm_cache.addrOffset, List(
-    "b0000".U -> s3_merged_data_frm_cache(63,    0),
-    "b0001".U -> s3_merged_data_frm_cache(63,    8),
-    "b0010".U -> s3_merged_data_frm_cache(63,   16),
-    "b0011".U -> s3_merged_data_frm_cache(63,   24),
-    "b0100".U -> s3_merged_data_frm_cache(63,   32),
-    "b0101".U -> s3_merged_data_frm_cache(63,   40),
-    "b0110".U -> s3_merged_data_frm_cache(63,   48),
-    "b0111".U -> s3_merged_data_frm_cache(63,   56),
-    "b1000".U -> s3_merged_data_frm_cache(127,  64),
-    "b1001".U -> s3_merged_data_frm_cache(127,  72),
-    "b1010".U -> s3_merged_data_frm_cache(127,  80),
-    "b1011".U -> s3_merged_data_frm_cache(127,  88),
-    "b1100".U -> s3_merged_data_frm_cache(127,  96),
-    "b1101".U -> s3_merged_data_frm_cache(127, 104),
-    "b1110".U -> s3_merged_data_frm_cache(127, 112),
-    "b1111".U -> s3_merged_data_frm_cache(127, 120)
-  ))
-  val s3_ld_data_frm_cache = rdataHelper(s3_ld_raw_data_frm_cache.uop, s3_picked_data_frm_cache)
+  val s3_data_frm_cache = Seq(
+    s3_merged_data_frm_cache(63,    0),
+    s3_merged_data_frm_cache(63,    8),
+    s3_merged_data_frm_cache(63,   16),
+    s3_merged_data_frm_cache(63,   24),
+    s3_merged_data_frm_cache(63,   32),
+    s3_merged_data_frm_cache(63,   40),
+    s3_merged_data_frm_cache(63,   48),
+    s3_merged_data_frm_cache(63,   56),
+    s3_merged_data_frm_cache(127,  64),
+    s3_merged_data_frm_cache(127,  72),
+    s3_merged_data_frm_cache(127,  80),
+    s3_merged_data_frm_cache(127,  88),
+    s3_merged_data_frm_cache(127,  96),
+    s3_merged_data_frm_cache(127, 104),
+    s3_merged_data_frm_cache(127, 112),
+    s3_merged_data_frm_cache(127, 120)
+  )
+  val s3_picked_data_frm_cache = Mux1H(s3_data_select_by_offset, s3_data_frm_cache)
+  val s3_ld_data_frm_cache = newRdataHelper(s3_data_select, s3_picked_data_frm_cache)
 
   // FIXME: add 1 cycle delay ?
   // io.lsq.uncache.ready := !s3_valid
   val s3_outexception = ExceptionNO.selectByFu(s3_out.bits.uop.exceptionVec, LduCfg).asUInt.orR && s3_vecActive
   io.ldout.bits        := s3_ld_wb_meta
-  io.ldout.bits.data   := Mux(s3_valid, Mux(!s3_outexception, s3_ld_data_frm_cache, 0.U), s3_ld_data_frm_uncache)
+  io.ldout.bits.data   := Mux(s3_valid, s3_ld_data_frm_cache, s3_ld_data_frm_uncache)
   io.ldout.valid       := (s3_out.valid && !s3_vecout.isvec || (s3_mmio.valid && !s3_valid))
   io.ldout.bits.uop.exceptionVec := ExceptionNO.selectByFu(s3_ld_wb_meta.uop.exceptionVec, LduCfg)
 
@@ -1503,12 +1542,12 @@ class LoadUnit(implicit p: Parameters) extends XSModule
   XSPerfAccumulate("s0_fast_replay_vecissue",      io.fast_rep_in.fire && io.fast_rep_in.bits.isvec)
   XSPerfAccumulate("s0_stall_out",                 s0_valid && !s0_can_go)
   XSPerfAccumulate("s0_stall_dcache",              s0_valid && !io.dcache.req.ready)
-  XSPerfAccumulate("s0_addr_spec_success",         s0_fire && s0_sel_src.vaddr(VAddrBits-1, 12) === io.ldin.bits.src(0)(VAddrBits-1, 12))
-  XSPerfAccumulate("s0_addr_spec_failed",          s0_fire && s0_sel_src.vaddr(VAddrBits-1, 12) =/= io.ldin.bits.src(0)(VAddrBits-1, 12))
-  XSPerfAccumulate("s0_addr_spec_success_once",    s0_fire && s0_sel_src.vaddr(VAddrBits-1, 12) === io.ldin.bits.src(0)(VAddrBits-1, 12) && s0_sel_src.isFirstIssue)
-  XSPerfAccumulate("s0_addr_spec_failed_once",     s0_fire && s0_sel_src.vaddr(VAddrBits-1, 12) =/= io.ldin.bits.src(0)(VAddrBits-1, 12) && s0_sel_src.isFirstIssue)
-  XSPerfAccumulate("s0_vec_addr_vlen_aligned",     s0_fire && s0_sel_src.isvec && s0_sel_src.vaddr(3, 0) === 0.U)
-  XSPerfAccumulate("s0_vec_addr_vlen_unaligned",   s0_fire && s0_sel_src.isvec && s0_sel_src.vaddr(3, 0) =/= 0.U)
+  XSPerfAccumulate("s0_addr_spec_success",         s0_fire && s0_dcache_vaddr(VAddrBits-1, 12) === io.ldin.bits.src(0)(VAddrBits-1, 12))
+  XSPerfAccumulate("s0_addr_spec_failed",          s0_fire && s0_dcache_vaddr(VAddrBits-1, 12) =/= io.ldin.bits.src(0)(VAddrBits-1, 12))
+  XSPerfAccumulate("s0_addr_spec_success_once",    s0_fire && s0_dcache_vaddr(VAddrBits-1, 12) === io.ldin.bits.src(0)(VAddrBits-1, 12) && s0_sel_src.isFirstIssue)
+  XSPerfAccumulate("s0_addr_spec_failed_once",     s0_fire && s0_dcache_vaddr(VAddrBits-1, 12) =/= io.ldin.bits.src(0)(VAddrBits-1, 12) && s0_sel_src.isFirstIssue)
+  XSPerfAccumulate("s0_vec_addr_vlen_aligned",     s0_fire && s0_sel_src.isvec && s0_dcache_vaddr(3, 0) === 0.U)
+  XSPerfAccumulate("s0_vec_addr_vlen_unaligned",   s0_fire && s0_sel_src.isvec && s0_dcache_vaddr(3, 0) =/= 0.U)
   XSPerfAccumulate("s0_forward_tl_d_channel",      s0_out.forward_tlDchannel)
   XSPerfAccumulate("s0_hardware_prefetch_fire",    s0_fire && s0_hw_prf_select)
   XSPerfAccumulate("s0_software_prefetch_fire",    s0_fire && s0_sel_src.prf && s0_int_iss_select)
diff --git a/src/main/scala/xiangshan/mem/pipeline/StoreUnit.scala b/src/main/scala/xiangshan/mem/pipeline/StoreUnit.scala
index f255c7eed97..81e76162775 100644
--- a/src/main/scala/xiangshan/mem/pipeline/StoreUnit.scala
+++ b/src/main/scala/xiangshan/mem/pipeline/StoreUnit.scala
@@ -104,14 +104,7 @@ class StoreUnit(implicit p: Parameters) extends XSModule
   val s0_mBIndex      = s0_vecstin.mBIndex
 
   // generate addr
-  // val saddr = s0_in.bits.src(0) + SignExt(s0_in.bits.uop.imm(11,0), VAddrBits)
-  val imm12 = WireInit(s0_uop.imm(11,0))
-  val saddr_lo = s0_stin.src(0)(11,0) + Cat(0.U(1.W), imm12)
-  val saddr_hi = Mux(saddr_lo(12),
-    Mux(imm12(11), s0_stin.src(0)(VAddrBits-1, 12), s0_stin.src(0)(VAddrBits-1, 12)+1.U),
-    Mux(imm12(11), s0_stin.src(0)(VAddrBits-1, 12)+SignExt(1.U, VAddrBits-12), s0_stin.src(0)(VAddrBits-1, 12)),
-  )
-  val s0_saddr = Cat(saddr_hi, saddr_lo(11,0))
+  val s0_saddr = s0_stin.src(0) + SignExt(s0_uop.imm(11,0), VAddrBits)
   val s0_vaddr = Mux(
     s0_use_flow_rs,
     s0_saddr,
@@ -147,6 +140,7 @@ class StoreUnit(implicit p: Parameters) extends XSModule
   io.tlb.req_kill                    := false.B
   io.tlb.req.bits.hyperinst          := LSUOpType.isHsv(s0_uop.fuOpType)
   io.tlb.req.bits.hlvx               := false.B
+  io.tlb.req.bits.pmp_addr           := DontCare
 
   // Dcache access here: not **real** dcache write
   // just read meta and tag in dcache, to find out the store will hit or miss
diff --git a/src/main/scala/xiangshan/mem/prefetch/L1PrefetchComponent.scala b/src/main/scala/xiangshan/mem/prefetch/L1PrefetchComponent.scala
index c8774bc5813..230c56543a1 100644
--- a/src/main/scala/xiangshan/mem/prefetch/L1PrefetchComponent.scala
+++ b/src/main/scala/xiangshan/mem/prefetch/L1PrefetchComponent.scala
@@ -608,6 +608,7 @@ class MutiLevelPrefetchFilter(implicit p: Parameters) extends XSModule with HasL
     tlb_req_arb.io.in(i).bits.debug := DontCare
     tlb_req_arb.io.in(i).bits.hlvx := DontCare
     tlb_req_arb.io.in(i).bits.hyperinst := DontCare
+    tlb_req_arb.io.in(i).bits.pmp_addr  := DontCare
   }
 
   assert(PopCount(s0_tlb_fire_vec) <= 1.U, "s0_tlb_fire_vec should be one-hot or empty")
diff --git a/src/main/scala/xiangshan/mem/prefetch/SMSPrefetcher.scala b/src/main/scala/xiangshan/mem/prefetch/SMSPrefetcher.scala
index 2e85d6eb92a..e644c523a8e 100644
--- a/src/main/scala/xiangshan/mem/prefetch/SMSPrefetcher.scala
+++ b/src/main/scala/xiangshan/mem/prefetch/SMSPrefetcher.scala
@@ -892,6 +892,7 @@ class PrefetchFilter()(implicit p: Parameters) extends XSModule with HasSMSModul
     tlb_req_arb.io.in(i).bits.debug := DontCare
     tlb_req_arb.io.in(i).bits.hlvx := DontCare
     tlb_req_arb.io.in(i).bits.hyperinst := DontCare
+    tlb_req_arb.io.in(i).bits.pmp_addr := DontCare
 
     val pending_req_vec = ent.region_bits & (~ent.filter_bits).asUInt
     val first_one_offset = PriorityMux(
diff --git a/src/main/scala/xiangshan/mem/vector/VSegmentUnit.scala b/src/main/scala/xiangshan/mem/vector/VSegmentUnit.scala
index 34f9a7a0e72..150dade70ea 100644
--- a/src/main/scala/xiangshan/mem/vector/VSegmentUnit.scala
+++ b/src/main/scala/xiangshan/mem/vector/VSegmentUnit.scala
@@ -600,7 +600,7 @@ class VSegmentUnit (implicit p: Parameters) extends VLSUModule
   when(stateNext === s_idle){
     instMicroOpValid := false.B
   }
-  io.uopwriteback.valid               := (state === s_finish) && distanceBetween(enqPtr, deqPtr) =/= 0.U
+  io.uopwriteback.valid               := (state === s_finish) && !isEmpty(enqPtr, deqPtr)
   io.uopwriteback.bits.uop            := uopq(deqPtr.value).uop
   io.uopwriteback.bits.uop.vpu        := instMicroOp.uop.vpu
   io.uopwriteback.bits.uop.exceptionVec := instMicroOp.uop.exceptionVec
@@ -617,7 +617,7 @@ class VSegmentUnit (implicit p: Parameters) extends VLSUModule
   io.uopwriteback.bits.uop.fuOpType   := instMicroOp.uop.fuOpType
 
   //to RS
-  io.feedback.valid                   := state === s_finish && distanceBetween(enqPtr, deqPtr) =/= 0.U
+  io.feedback.valid                   := state === s_finish && !isEmpty(enqPtr, deqPtr)
   io.feedback.bits.hit                := true.B
   io.feedback.bits.robIdx             := instMicroOp.uop.robIdx
   io.feedback.bits.sourceType         := DontCare
@@ -633,6 +633,6 @@ class VSegmentUnit (implicit p: Parameters) extends VLSUModule
   io.exceptionInfo.bits.vstart        := instMicroOp.exceptionVstart
   io.exceptionInfo.bits.vaddr         := instMicroOp.exceptionVaddr
   io.exceptionInfo.bits.vl            := instMicroOp.exceptionVl
-  io.exceptionInfo.valid              := (state === s_finish) && instMicroOp.uop.exceptionVec.asUInt.orR && distanceBetween(enqPtr, deqPtr) =/= 0.U
+  io.exceptionInfo.valid              := (state === s_finish) && instMicroOp.uop.exceptionVec.asUInt.orR && !isEmpty(enqPtr, deqPtr)
 }
 
diff --git a/src/main/scala/xiangshan/mem/vector/VSplit.scala b/src/main/scala/xiangshan/mem/vector/VSplit.scala
index 28f7c8d1b81..2c628afdada 100644
--- a/src/main/scala/xiangshan/mem/vector/VSplit.scala
+++ b/src/main/scala/xiangshan/mem/vector/VSplit.scala
@@ -220,6 +220,7 @@ class VSplitPipeline(isVStore: Boolean = false)(implicit p: Parameters) extends
   // for Unit-Stride, if uop's addr is aligned with 128-bits, split it to one flow, otherwise split two
   val usLowBitsAddr    = getCheckAddrLowBits(s1_in.baseAddr, maxMemByteNum) + getCheckAddrLowBits(uopOffset, maxMemByteNum)
   val usAligned128     = (getCheckAddrLowBits(usLowBitsAddr, maxMemByteNum) === 0.U)// addr 128-bit aligned
+  val usMask           = Cat(0.U(VLENB.W), s1_in.byteMask) << getCheckAddrLowBits(usLowBitsAddr, maxMemByteNum)
 
   s1_kill               := s1_in.uop.robIdx.needFlush(io.redirect)
 
@@ -250,6 +251,7 @@ class VSplitPipeline(isVStore: Boolean = false)(implicit p: Parameters) extends
   io.out.bits.mBIndex   := io.toMergeBuffer.resp.bits.mBIndex
   io.out.bits.usLowBitsAddr := usLowBitsAddr
   io.out.bits.usAligned128  := usAligned128
+  io.out.bits.usMask        := usMask
 
   XSPerfAccumulate("split_out",     io.out.fire)
   XSPerfAccumulate("pipe_block",    io.out.valid && !io.out.ready)
@@ -305,6 +307,7 @@ abstract class VSplitBuffer(isVStore: Boolean = false)(implicit p: Parameters) e
   val issueAlignedType = issueEntry.alignedType
   val issuePreIsSplit  = issueEntry.preIsSplit
   val issueByteMask    = issueEntry.byteMask
+  val issueUsMask      = issueEntry.usMask
   val issueVLMAXMask   = issueEntry.vlmax - 1.U
   val issueIsWholeReg  = issueEntry.usWholeReg
   val issueVLMAXLog2   = GenVLMAXLog2(issueEntry.lmul, issueSew)
@@ -341,8 +344,9 @@ abstract class VSplitBuffer(isVStore: Boolean = false)(implicit p: Parameters) e
    * Unit-Stride split to one flow or two flow.
    * for Unit-Stride, if uop's addr is aligned with 128-bits, split it to one flow, otherwise split two
    */
-  val usSplitMask      = genUSSplitMask(issueByteMask, splitIdx, getCheckAddrLowBits(issueUsLowBitsAddr, maxMemByteNum))
-  val usNoSplit        = (issueUsAligned128 || !getOverflowBit(getCheckAddrLowBits(issueUsLowBitsAddr, maxMemByteNum) +& PopCount(usSplitMask), maxMemByteNum)) &&
+  val usSplitMask      = genUSSplitMask(issueUsMask, splitIdx)
+  val usMaskInSingleUop = (genUSSplitMask(issueUsMask, 1.U) === 0.U) // if second splited Mask is zero, means this uop is unnecessary to split
+  val usNoSplit        = (issueUsAligned128 || usMaskInSingleUop) &&
                           !issuePreIsSplit &&
                           (splitIdx === 0.U)// unit-stride uop don't need to split into two flow
   val usSplitVaddr     = genUSSplitAddr(vaddr, splitIdx)
diff --git a/src/main/scala/xiangshan/mem/vector/VecBundle.scala b/src/main/scala/xiangshan/mem/vector/VecBundle.scala
index 091c6a8573b..7cea18d75bd 100644
--- a/src/main/scala/xiangshan/mem/vector/VecBundle.scala
+++ b/src/main/scala/xiangshan/mem/vector/VecBundle.scala
@@ -72,6 +72,7 @@ class VLSBundle(isVStore: Boolean=false)(implicit p: Parameters) extends VLSUBun
 
   val usLowBitsAddr       = UInt((log2Up(maxMemByteNum)).W)
   val usAligned128        = Bool()
+  val usMask              = UInt((VLENB*2).W) // for unit-stride split
 }
 
 object VSFQFeedbackType {
diff --git a/src/main/scala/xiangshan/mem/vector/VecCommon.scala b/src/main/scala/xiangshan/mem/vector/VecCommon.scala
index a4b18854b0e..0db6d43c2d1 100644
--- a/src/main/scala/xiangshan/mem/vector/VecCommon.scala
+++ b/src/main/scala/xiangshan/mem/vector/VecCommon.scala
@@ -741,11 +741,11 @@ object genUSSplitAddr{
 }
 
 object genUSSplitMask{
-  def apply(mask: UInt, index: UInt, addrOffset: UInt): UInt = {
-    val tmpMask = Cat(0.U(16.W),mask) << addrOffset // 32-bits
+  def apply(mask: UInt, index: UInt): UInt = {
+    require(mask.getWidth == 32) // need to be 32-bits
     LookupTree(index, List(
-      0.U -> tmpMask(15, 0),
-      1.U -> tmpMask(31, 16),
+      0.U -> mask(15, 0),
+      1.U -> mask(31, 16),
     ))
   }
 }
```
