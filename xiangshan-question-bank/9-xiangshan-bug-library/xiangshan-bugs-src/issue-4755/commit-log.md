# Commit Log
- Issue: #4755
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/4755
- Issue state: closed
- Tested RTL commit: -
- Related PR: #4755
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/4755
- Changed files: 3
- Additions: 56
- Deletions: 37

## Files
- `src/main/scala/xiangshan/cache/dcache/DCacheWrapper.scala`
- `src/main/scala/xiangshan/cache/dcache/data/BankedDataArray.scala`
- `src/main/scala/xiangshan/cache/dcache/mainpipe/MainPipe.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/cache/dcache/DCacheWrapper.scala b/src/main/scala/xiangshan/cache/dcache/DCacheWrapper.scala
index 4d556437a07..527fb2aa2a3 100644
--- a/src/main/scala/xiangshan/cache/dcache/DCacheWrapper.scala
+++ b/src/main/scala/xiangshan/cache/dcache/DCacheWrapper.scala
@@ -1294,6 +1294,7 @@ class DCacheImp(outer: DCache) extends LazyModuleImp(outer) with HasDCacheParame
   bankedDataArray.io.readline_stall := mainPipe.io.data_readline_stall
   bankedDataArray.io.readline_can_resp := mainPipe.io.data_readline_can_resp
   bankedDataArray.io.readline_intend := mainPipe.io.data_read_intend
+  mainPipe.io.readline_error := bankedDataArray.io.readline_error
   mainPipe.io.readline_error_delayed := bankedDataArray.io.readline_error_delayed
   mainPipe.io.data_resp := bankedDataArray.io.readline_resp
 
diff --git a/src/main/scala/xiangshan/cache/dcache/data/BankedDataArray.scala b/src/main/scala/xiangshan/cache/dcache/data/BankedDataArray.scala
index 5d8e529765f..6acee163b43 100644
--- a/src/main/scala/xiangshan/cache/dcache/data/BankedDataArray.scala
+++ b/src/main/scala/xiangshan/cache/dcache/data/BankedDataArray.scala
@@ -260,6 +260,7 @@ abstract class AbstractBankedDataArray(implicit p: Parameters) extends DCacheMod
     val write_dup = Vec(DCacheBanks, Flipped(Decoupled(new L1BankedDataWriteReqCtrl)))
     // data for readline and loadpipe
     val readline_resp = Output(Vec(DCacheBanks, new L1BankedDataReadResult()))
+    val readline_error = Output(Bool())
     val readline_error_delayed = Output(Bool())
     val read_resp          = Output(Vec(LoadPipelineWidth, Vec(VLEN/DCacheSRAMRowBits, new L1BankedDataReadResult())))
     val read_error_delayed = Output(Vec(LoadPipelineWidth,Vec(VLEN/DCacheSRAMRowBits, Bool())))
@@ -590,6 +591,7 @@ class SramedDataArray(implicit p: Parameters) extends AbstractBankedDataArray {
     io.readline_resp(i) := read_result(readline_r_div_addr)(i)(readline_r_way_addr)
     readline_error_delayed(i) := read_result(readline_rr_div_addr)(i)(readline_rr_way_addr).error_delayed
   })
+  io.readline_error := RegNext(RegNext(io.readline.fire)) && readline_error_delayed.asUInt.orR
   io.readline_error_delayed := RegNext(RegNext(io.readline.fire)) && readline_error_delayed.asUInt.orR
 
   // write data_banks & ecc_banks
@@ -667,6 +669,12 @@ class BankedDataArray(implicit p: Parameters) extends AbstractBankedDataArray {
   val mbistSramPorts = mbistPl.map(pl => Seq.tabulate(DCacheSetDiv, DCacheBanks, DCacheWays) ({ (i, j, k) =>
     pl.toSRAM(i * DCacheBanks * DCacheWays + j * DCacheWays + k)
   }))
+
+  // read result: expose banked read result
+  private val mbist_r_way = OHToUInt(mbistSramPorts.map(_.flatMap(_.map(w => Cat(w.map(_.re).reverse))).reduce(_ | _)).getOrElse(0.U(DCacheWays.W)))
+  private val mbist_r_div = OHToUInt(mbistSramPorts.map(_.map(d => Cat(d.flatMap(w => w.map(_.re))).orR)).getOrElse(Seq.fill(DCacheSetDiv)(false.B)))
+  private val mbist_ack = mbistPl.map(_.mbist.ack).getOrElse(false.B)
+
   data_banks.map(_.map(_.dump()))
 
   val way_en = Wire(Vec(LoadPipelineWidth, io.read(0).bits.way_en.cloneType))
@@ -896,16 +904,12 @@ class BankedDataArray(implicit p: Parameters) extends AbstractBankedDataArray {
     })
   })
 
-  // read result: expose banked read result
-  private val mbist_r_way = OHToUInt(mbistSramPorts.map(_.flatMap(_.map(w => Cat(w.map(_.re).reverse))).reduce(_ | _)).getOrElse(0.U(DCacheWays.W)))
-  private val mbist_r_div = OHToUInt(mbistSramPorts.map(_.map(d => Cat(d.flatMap(w => w.map(_.re))).orR)).getOrElse(Seq.fill(DCacheSetDiv)(false.B)))
-  private val mbist_ack = mbistPl.map(_.mbist.ack).getOrElse(false.B)
-
+  val readline_error = Wire(Vec(DCacheBanks, Bool()))
   val readline_error_delayed = Wire(Vec(DCacheBanks, Bool()))
-  val readline_r_way_addr = RegEnable(Mux(mbist_ack, mbist_r_way, OHToUInt(io.readline.bits.way_en)), io.readline.valid | mbist_ack)
-  val readline_rr_way_addr = RegEnable(readline_r_way_addr, RegNext(io.readline.valid))
-  val readline_r_div_addr = RegEnable(Mux(mbist_ack, mbist_r_div, line_div_addr), io.readline.valid | mbist_ack)
-  val readline_rr_div_addr = RegEnable(readline_r_div_addr, RegNext(io.readline.valid))
+  val readline_r_way_addr = RegEnable(Mux(mbist_ack, mbist_r_way, OHToUInt(io.readline.bits.way_en)), io.readline.fire | mbist_ack)
+  val readline_rr_way_addr = RegEnable(readline_r_way_addr, RegNext(io.readline.fire))
+  val readline_r_div_addr = RegEnable(Mux(mbist_ack, mbist_r_div, line_div_addr), io.readline.fire | mbist_ack)
+  val readline_rr_div_addr = RegEnable(readline_r_div_addr, RegNext(io.readline.fire))
   val readline_resp = Wire(io.readline_resp.cloneType)
   (0 until DCacheBanks).foreach(i => {
     mbistSramPorts.foreach(_.foreach(_(i).foreach(_.rdata := Cat(io.readline_resp(i).ecc, io.readline_resp(i).raw_data))))
@@ -914,10 +918,20 @@ class BankedDataArray(implicit p: Parameters) extends AbstractBankedDataArray {
       bank_result(readline_r_div_addr)(i)(readline_r_way_addr),
       RegEnable(readline_resp(i), io.readline_stall | mbist_ack)
     )
-    readline_error_delayed(i) := bank_result(readline_rr_div_addr)(i)(readline_rr_way_addr).error_delayed
+
+    if (EnableDataEcc) {
+      readline_error(i) := bank_result(readline_rr_div_addr)(i)(readline_rr_way_addr).error_delayed
+     //
+      val ecc_data_delayed = io.readline_resp(i).asECCData()
+      readline_error_delayed(i) := dcacheParameters.dataCode.decode(ecc_data_delayed).error
+    } else {
+      readline_error(i) := false.B
+      readline_error_delayed(i) := false.B
+    }
   })
   io.readline_resp := RegEnable(readline_resp, io.readline_can_resp | mbist_ack)
-  io.readline_error_delayed := RegNext(RegNext(io.readline.fire)) && readline_error_delayed.asUInt.orR
+  io.readline_error := readline_error.asUInt.orR
+  io.readline_error_delayed := readline_error_delayed.asUInt.orR
 
   // write data_banks & ecc_banks
   for (div_index <- 0 until DCacheSetDiv) {
diff --git a/src/main/scala/xiangshan/cache/dcache/mainpipe/MainPipe.scala b/src/main/scala/xiangshan/cache/dcache/mainpipe/MainPipe.scala
index 9c11eb80a5d..fe687575ef1 100644
--- a/src/main/scala/xiangshan/cache/dcache/mainpipe/MainPipe.scala
+++ b/src/main/scala/xiangshan/cache/dcache/mainpipe/MainPipe.scala
@@ -153,6 +153,7 @@ class MainPipe(implicit p: Parameters) extends DCacheModule with HasPerfEvents w
     val data_readline_stall = Output(Bool())
     val data_readline_can_resp = Output(Bool())
     val data_resp = Input(Vec(DCacheBanks, new L1BankedDataReadResult()))
+    val readline_error = Input(Bool())
     val readline_error_delayed = Input(Bool())
     val data_write = DecoupledIO(new L1BankedDataWriteReq)
     val data_write_dup = Vec(DCacheBanks, Valid(new L1BankedDataWriteReqCtrl))
@@ -572,19 +573,22 @@ class MainPipe(implicit p: Parameters) extends DCacheModule with HasPerfEvents w
   val miss_new_coh = ClientMetadata(missCohGen(s3_req.cmd, s3_miss_param, s3_miss_dirty))
 
   // report ecc error
-  val s3_tag_error = RegEnable(s2_tag_error, false.B, s2_fire_to_s3)
+  val s3_tag_error_beu = RegEnable(s2_tag_error, s2_fire)
+  val s3_tag_error_wb = RegEnable(s2_tag_error, s2_fire_to_s3)
+
   // data_error will be reported by data array 1 cycle after data read resp
-  val s3_data_error = Wire(Bool())
-  s3_data_error := Mux(GatedValidRegNextN(s1_fire, 2), // ecc check result is generated 2 cycle after read req
-    io.readline_error_delayed && RegNext(s2_may_report_data_error),
-    RegNext(s3_data_error) // do not update s3_data_error if !s1_fire
-  )
-  val s3_l2_error = RegEnable(s2_l2_error, false.B, s2_fire_to_s3)
-  val s3_flag_error = RegEnable(s2_flag_error, false.B, s2_fire_to_s3)
+  val s3_data_error_beu = io.readline_error && RegEnable(s2_may_report_data_error, s2_fire)
+  val s3_data_error_wb = io.readline_error_delayed && RegEnable(s2_may_report_data_error, s2_fire_to_s3)
+
+  val s3_l2_error_beu = RegEnable(s2_l2_error, s2_fire)
+  val s3_l2_error_wb = RegEnable(s2_l2_error, s2_fire_to_s3)
+  val s3_flag_error_beu = RegEnable(s2_flag_error, s2_fire)
+
   // error signal for amo inst
-  // s3_error = s3_flag_error || s3_tag_error || s3_l2_error || s3_data_error
-  val s3_error = RegEnable(s2_error, 0.U.asTypeOf(s2_error), s2_fire_to_s3) || s3_data_error
-  val s3_error_paddr = get_block_addr(RegEnable(Cat(s2_tag, get_untag(s2_req.vaddr)), s2_fire_to_s3))
+  // s3_error_beu = s3_flag_error_beu || s3_tag_error_beu || s3_l2_error_beu || s3_data_error_beu
+  val s3_error_beu = RegEnable(s2_error, 0.U.asTypeOf(s2_error), s2_fire) || s3_data_error_beu
+  val s3_error_wb = RegEnable(s2_error, 0.U.asTypeOf(s2_error), s2_fire_to_s3) || s3_data_error_wb
+  val s3_error_paddr_beu = get_block_addr(RegEnable(Cat(s2_tag, get_untag(s2_req.vaddr)), s2_fire))
 
   // LR, SC and AMO
   val debug_sc_fail_addr = RegInit(0.U)
@@ -863,7 +867,7 @@ class MainPipe(implicit p: Parameters) extends DCacheModule with HasPerfEvents w
   atomic_hit_resp.data := Mux(s3_sc, s3_sc_fail.asUInt, s3_data_quad_word)
   atomic_hit_resp.miss := false.B
   atomic_hit_resp.miss_id := s3_req.miss_id
-  atomic_hit_resp.error := s3_error
+  atomic_hit_resp.error := s3_error_wb
   atomic_hit_resp.replay := false.B
   atomic_hit_resp.ack_miss_queue := s3_req.miss
   atomic_hit_resp.id := lrsc_valid
@@ -891,10 +895,10 @@ class MainPipe(implicit p: Parameters) extends DCacheModule with HasPerfEvents w
   io.meta_write.bits.way_en := s3_way_en
   io.meta_write.bits.meta.coh := new_coh
 
-  io.error_flag_write.valid := s3_fire && update_meta && (s3_l2_error || s3_req.miss)
+  io.error_flag_write.valid := s3_fire && update_meta && (s3_l2_error_wb || s3_req.miss)
   io.error_flag_write.bits.idx := s3_idx
   io.error_flag_write.bits.way_en := s3_way_en
-  io.error_flag_write.bits.flag := s3_l2_error
+  io.error_flag_write.bits.flag := s3_l2_error_wb
 
   // if we use (prefetch_flag && meta =/= ClientStates.Nothing) for prefetch check
   // prefetch_flag_write can be omited
@@ -979,10 +983,10 @@ class MainPipe(implicit p: Parameters) extends DCacheModule with HasPerfEvents w
   io.wb.bits.addr := get_block_addr(Cat(s3_tag, get_untag(s3_req.vaddr)))
   io.wb.bits.param := writeback_param
   io.wb.bits.voluntary := s3_req.miss || s3_req.replace
-  io.wb.bits.hasData := writeback_data && !s3_tag_error
+  io.wb.bits.hasData := writeback_data && !s3_tag_error_wb
   io.wb.bits.dirty := s3_coh === ClientStates.Dirty
   io.wb.bits.data := s3_data.asUInt
-  io.wb.bits.corrupt := s3_tag_error || s3_data_error
+  io.wb.bits.corrupt := s3_tag_error_wb || s3_data_error_wb
   io.wb.bits.delay_release := s3_req.replace
   io.wb.bits.miss_id := s3_req.miss_id
 
@@ -1039,18 +1043,18 @@ class MainPipe(implicit p: Parameters) extends DCacheModule with HasPerfEvents w
   // report error to beu and csr, 1 cycle after read data resp
   io.error := 0.U.asTypeOf(ValidIO(new L1CacheErrorInfo))
   // report error, update error csr
-  io.error.valid := s3_error && GatedValidRegNext(s2_fire && !s2_should_not_report_ecc_error)
+  io.error.valid := s3_error_beu && GatedValidRegNext(s2_fire && !s2_should_not_report_ecc_error)
   // only tag_error and data_error will be reported to beu
   // l2_error should not be reported (l2 will report that)
-  io.error.bits.report_to_beu := (s3_tag_error || s3_data_error) && RegNext(s2_fire)
-  io.error.bits.paddr := s3_error_paddr
-  io.error.bits.source.tag := s3_tag_error
-  io.error.bits.source.data := s3_data_error
-  io.error.bits.source.l2 := s3_flag_error || s3_l2_error
-  io.error.bits.opType.store := s3_req.isStore && !s3_req.probe
-  io.error.bits.opType.probe := s3_req.probe
-  io.error.bits.opType.release := s3_req.replace
-  io.error.bits.opType.atom := s3_req.isAMO && !s3_req.probe
+  io.error.bits.report_to_beu := (s3_tag_error_beu || s3_data_error_beu) && RegNext(s2_fire)
+  io.error.bits.paddr := s3_error_paddr_beu
+  io.error.bits.source.tag := s3_tag_error_beu
+  io.error.bits.source.data := s3_data_error_beu
+  io.error.bits.source.l2 := s3_flag_error_beu || s3_l2_error_beu
+  io.error.bits.opType.store := RegEnable(s2_req.isStore && !s2_req.probe, s2_fire)
+  io.error.bits.opType.probe := RegEnable(s2_req.probe, s2_fire)
+  io.error.bits.opType.release := RegEnable(s2_req.replace, s2_fire)
+  io.error.bits.opType.atom := RegEnable(s2_req.isAMO && !s2_req.probe, s2_fire)
 
   val perfEvents = Seq(
     ("dcache_mp_req          ", s0_fire                                                      ),
```
