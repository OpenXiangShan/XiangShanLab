# Commit Log
- Issue: #3899
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/3899
- Issue state: closed
- Tested RTL commit: -
- Related PR: #3899
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/3899
- Changed files: 3
- Additions: 121
- Deletions: 65

## Files
- `src/main/scala/xiangshan/frontend/icache/ICache.scala`
- `src/main/scala/xiangshan/frontend/icache/ICacheBundle.scala`
- `src/main/scala/xiangshan/frontend/icache/ICacheMainPipe.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/frontend/icache/ICache.scala b/src/main/scala/xiangshan/frontend/icache/ICache.scala
index 7c0bebdcaa3..b1f4fc0b57b 100644
--- a/src/main/scala/xiangshan/frontend/icache/ICache.scala
+++ b/src/main/scala/xiangshan/frontend/icache/ICache.scala
@@ -221,7 +221,8 @@ class ICacheMetaArray()(implicit p: Parameters) extends ICacheArray {
     val write    = Flipped(DecoupledIO(new ICacheMetaWriteBundle))
     val read     = Flipped(DecoupledIO(new ICacheReadBundle))
     val readResp = Output(new ICacheMetaRespBundle)
-    val fencei   = Input(Bool())
+    val flush    = Vec(PortNumber, Flipped(ValidIO(new ICacheMetaFlushBundle)))
+    val flushAll = Input(Bool())
   })
 
   val port_0_read_0 = io.read.valid && !io.read.bits.vSetIdx(0)(0)
@@ -292,7 +293,8 @@ class ICacheMetaArray()(implicit p: Parameters) extends ICacheArray {
   )
   io.readResp.entryValid := valid_metas
 
-  io.read.ready := !io.write.valid && !io.fencei && tagArrays.map(_.io.r.req.ready).reduce(_ && _)
+  io.read.ready := !io.write.valid && !io.flush.map(_.valid).reduce(_ || _) && !io.flushAll &&
+    tagArrays.map(_.io.r.req.ready).reduce(_ && _)
 
   // valid write
   val way_num = OHToUInt(io.write.bits.waymask)
@@ -331,12 +333,31 @@ class ICacheMetaArray()(implicit p: Parameters) extends ICacheArray {
 
   io.write.ready := true.B // TODO : has bug ? should be !io.cacheOp.req.valid
 
-  // fencei logic : reset valid_array
-  when(io.fencei) {
-    (0 until nWays).foreach(way =>
-      valid_array(way) := 0.U
-    )
+  /*
+   * flush logic
+   */
+  // flush standalone set (e.g. flushed by mainPipe before doing re-fetch)
+  when(io.flush.map(_.valid).reduce(_ || _)) {
+    (0 until nWays).foreach { w =>
+      valid_array(w) := (0 until PortNumber).map { i =>
+        Mux(
+          // check if set `virIdx` in way `w` is requested to be flushed by port `i`
+          io.flush(i).valid && io.flush(i).bits.waymask(w),
+          valid_array(w).bitSet(io.flush(i).bits.virIdx, false.B),
+          valid_array(w)
+        )
+      }.reduce(_ & _)
+    }
   }
+
+  // flush all (e.g. fence.i)
+  when(io.flushAll) {
+    (0 until nWays).foreach(w => valid_array(w) := 0.U)
+  }
+
+  // PERF: flush counter
+  XSPerfAccumulate("flush", io.flush.map(_.valid).reduce(_ || _))
+  XSPerfAccumulate("flush_all", io.flushAll)
 }
 
 class ICacheDataArray(implicit p: Parameters) extends ICacheArray {
@@ -548,7 +569,8 @@ class ICacheImp(outer: ICache) extends LazyModuleImp(outer) with HasICacheParame
   dataArray.io.read <> mainPipe.io.dataArray.toIData
   dataArray.io.readResp <> mainPipe.io.dataArray.fromIData
 
-  metaArray.io.fencei := io.fencei
+  metaArray.io.flushAll := io.fencei
+  metaArray.io.flush <> mainPipe.io.metaArrayFlush
   metaArray.io.write <> missUnit.io.meta_write
   metaArray.io.read <> prefetcher.io.metaRead.toIMeta
   metaArray.io.readResp <> prefetcher.io.metaRead.fromIMeta
diff --git a/src/main/scala/xiangshan/frontend/icache/ICacheBundle.scala b/src/main/scala/xiangshan/frontend/icache/ICacheBundle.scala
index 7f4969e3ece..f3872e5ae10 100644
--- a/src/main/scala/xiangshan/frontend/icache/ICacheBundle.scala
+++ b/src/main/scala/xiangshan/frontend/icache/ICacheBundle.scala
@@ -45,7 +45,11 @@ class ICacheMetaWriteBundle(implicit p: Parameters) extends ICacheBundle {
     this.waymask := waymask
     this.bankIdx := bankIdx
   }
+}
 
+class ICacheMetaFlushBundle(implicit p: Parameters) extends ICacheBundle {
+  val virIdx  = UInt(idxBits.W)
+  val waymask = UInt(nWays.W)
 }
 
 class ICacheDataWriteBundle(implicit p: Parameters) extends ICacheBundle {
@@ -60,7 +64,6 @@ class ICacheDataWriteBundle(implicit p: Parameters) extends ICacheBundle {
     this.waymask := waymask
     this.bankIdx := bankIdx
   }
-
 }
 
 class ICacheMetaRespBundle(implicit p: Parameters) extends ICacheBundle {
@@ -77,11 +80,6 @@ class ICacheDataRespBundle(implicit p: Parameters) extends ICacheBundle {
   val codes = Vec(ICacheDataBanks, UInt(ICacheDataCodeBits.W))
 }
 
-class ICacheMetaReadBundle(implicit p: Parameters) extends ICacheBundle {
-  val req  = Flipped(DecoupledIO(new ICacheReadBundle))
-  val resp = Output(new ICacheMetaRespBundle)
-}
-
 class ReplacerTouch(implicit p: Parameters) extends ICacheBundle {
   val vSetIdx = UInt(log2Ceil(nSets).W)
   val way     = UInt(log2Ceil(nWays).W)
diff --git a/src/main/scala/xiangshan/frontend/icache/ICacheMainPipe.scala b/src/main/scala/xiangshan/frontend/icache/ICacheMainPipe.scala
index 55c83c81bf3..846abace530 100644
--- a/src/main/scala/xiangshan/frontend/icache/ICacheMainPipe.scala
+++ b/src/main/scala/xiangshan/frontend/icache/ICacheMainPipe.scala
@@ -93,7 +93,8 @@ class ICacheMainPipeInterface(implicit p: Parameters) extends ICacheBundle {
   val hartId = Input(UInt(hartIdLen.W))
 
   /*** internal interface ***/
-  val dataArray = new ICacheDataReqBundle
+  val dataArray      = new ICacheDataReqBundle
+  val metaArrayFlush = Vec(PortNumber, ValidIO(new ICacheMetaFlushBundle))
 
   /** prefetch io */
   val touch         = Vec(PortNumber, ValidIO(new ReplacerTouch))
@@ -129,9 +130,11 @@ class ICacheMainPipe(implicit p: Parameters) extends ICacheModule {
   /** Input/Output port */
   val (fromFtq, toIFU)   = (io.fetch.req, io.fetch.resp)
   val (toData, fromData) = (io.dataArray.toIData, io.dataArray.fromIData)
+  val toMetaFlush        = io.metaArrayFlush
   val (toMSHR, fromMSHR) = (io.mshr.req, io.mshr.resp)
   val (toPMP, fromPMP)   = (io.pmp.map(_.req), io.pmp.map(_.resp))
   val fromWayLookup      = io.wayLookupRead
+  val csr_parity_enable  = if (ICacheForceMetaECCError || ICacheForceDataECCError) true.B else io.csr_parity_enable
 
   // Statistics on the frequency distribution of FTQ fire interval
   val cntFtqFireInterval = RegInit(0.U(32.W))
@@ -248,6 +251,10 @@ class ICacheMainPipe(implicit p: Parameters) extends ICacheModule {
     (encodeMetaECC(meta) =/= code && hit_num === 1.U) || // hit one way, but parity code does not match, ECC failure
     hit_num > 1.U                                        // hit multi way, must be a ECC failure
   })
+  // force clear meta_corrupt when parity check is disabled
+  when(!csr_parity_enable) {
+    s1_meta_corrupt := VecInit(Seq.fill(PortNumber)(false.B))
+  }
 
   /**
     ******************************************************************************
@@ -276,18 +283,12 @@ class ICacheMainPipe(implicit p: Parameters) extends ICacheModule {
   val s1_pmp_exception = VecInit(fromPMP.map(ExceptionType.fromPMPResp))
   val s1_pmp_mmio      = VecInit(fromPMP.map(_.mmio))
 
-  // also raise af when meta array corrupt is detected, to cancel fetch
-  val s1_meta_exception = VecInit(s1_meta_corrupt.map(ExceptionType.fromECC(io.csr_parity_enable, _)))
-
-  // merge s1 itlb/pmp/meta exceptions, itlb has the highest priority, pmp next, meta lowest
+  // merge s1 itlb/pmp exceptions, itlb has the highest priority, pmp next
   val s1_exception_out = ExceptionType.merge(
     s1_itlb_exception,
-    s1_pmp_exception,
-    s1_meta_exception
+    s1_pmp_exception
   )
 
-  // DO NOT merge pmp mmio and itlb pbmt here, we need them to be passed to IFU separately
-
   /**
     ******************************************************************************
     * select data from MSHR, SRAM
@@ -313,6 +314,9 @@ class ICacheMainPipe(implicit p: Parameters) extends ICacheModule {
   val s1_datas = VecInit((0 until ICacheDataBanks).map(i =>
     DataHoldBypass(Mux(s1_bankMSHRHit(i), s1_MSHR_datas(i), fromData.datas(i)), s1_bankMSHRHit(i) || RegNext(s0_fire))
   ))
+  val s1_data_is_from_MSHR = VecInit((0 until ICacheDataBanks).map(i =>
+    DataHoldBypass(s1_bankMSHRHit(i), s1_bankMSHRHit(i) || RegNext(s0_fire))
+  ))
   val s1_codes = DataHoldBypass(fromData.codes, RegNext(s0_fire))
 
   s1_flush := io.flush
@@ -335,50 +339,73 @@ class ICacheMainPipe(implicit p: Parameters) extends ICacheModule {
   val s2_req_gpaddr            = RegEnable(s1_req_gpaddr, 0.U.asTypeOf(s1_req_gpaddr), s1_fire)
   val s2_req_isForVSnonLeafPTE = RegEnable(s1_req_isForVSnonLeafPTE, 0.U.asTypeOf(s1_req_isForVSnonLeafPTE), s1_fire)
   val s2_doubleline            = RegEnable(s1_doubleline, 0.U.asTypeOf(s1_doubleline), s1_fire)
-  val s2_exception =
-    RegEnable(s1_exception_out, 0.U.asTypeOf(s1_exception_out), s1_fire) // includes itlb/pmp/meta exception
-  val s2_backendException = RegEnable(s1_backendException, false.B, s1_fire)
-  val s2_pmp_mmio         = RegEnable(s1_pmp_mmio, 0.U.asTypeOf(s1_pmp_mmio), s1_fire)
-  val s2_itlb_pbmt        = RegEnable(s1_itlb_pbmt, 0.U.asTypeOf(s1_itlb_pbmt), s1_fire)
+  val s2_exception             = RegEnable(s1_exception_out, 0.U.asTypeOf(s1_exception_out), s1_fire)
+  val s2_backendException      = RegEnable(s1_backendException, false.B, s1_fire)
+  val s2_pmp_mmio              = RegEnable(s1_pmp_mmio, 0.U.asTypeOf(s1_pmp_mmio), s1_fire)
+  val s2_itlb_pbmt             = RegEnable(s1_itlb_pbmt, 0.U.asTypeOf(s1_itlb_pbmt), s1_fire)
+  val s2_waymasks              = RegEnable(s1_waymasks, 0.U.asTypeOf(s1_waymasks), s1_fire)
 
   val s2_req_vSetIdx = s2_req_vaddr.map(get_idx)
   val s2_req_offset  = s2_req_vaddr(0)(log2Ceil(blockBytes) - 1, 0)
   val s2_req_paddr   = s2_req_vaddr.zip(s2_req_ptags).map { case (vaddr, ptag) => get_paddr_from_ptag(vaddr, ptag) }
 
-  val s2_SRAMhits = RegEnable(s1_SRAMhits, 0.U.asTypeOf(s1_SRAMhits), s1_fire)
-  val s2_codes    = RegEnable(s1_codes, 0.U.asTypeOf(s1_codes), s1_fire)
-  val s2_hits     = RegInit(VecInit(Seq.fill(PortNumber)(false.B)))
-  val s2_datas    = RegInit(VecInit(Seq.fill(ICacheDataBanks)(0.U((blockBits / ICacheDataBanks).W))))
+  val s2_SRAMhits          = RegEnable(s1_SRAMhits, 0.U.asTypeOf(s1_SRAMhits), s1_fire)
+  val s2_codes             = RegEnable(s1_codes, 0.U.asTypeOf(s1_codes), s1_fire)
+  val s2_hits              = RegInit(VecInit(Seq.fill(PortNumber)(false.B)))
+  val s2_datas             = RegInit(VecInit(Seq.fill(ICacheDataBanks)(0.U((blockBits / ICacheDataBanks).W))))
+  val s2_data_is_from_MSHR = RegInit(VecInit(Seq.fill(ICacheDataBanks)(false.B)))
 
   /**
     ******************************************************************************
-    * report data parity error
+    * ECC check
     ******************************************************************************
     */
   // check data error
   val s2_bankSel      = getBankSel(s2_req_offset, s2_valid)
   val s2_bank_corrupt = (0 until ICacheDataBanks).map(i => encodeDataECC(s2_datas(i)) =/= s2_codes(i))
-  val s2_data_corrupt = (0 until PortNumber).map(port =>
+  // if data is from MSHR, we don't need to check ECC
+  val s2_data_corrupt = VecInit((0 until PortNumber).map(port =>
     (0 until ICacheDataBanks).map(bank =>
-      s2_bank_corrupt(bank) && s2_bankSel(port)(bank).asBool
+      s2_bank_corrupt(bank) && s2_bankSel(port)(bank).asBool && !s2_data_is_from_MSHR(bank)
     ).reduce(_ || _) && s2_SRAMhits(port)
-  )
-  // meta error is checked in prefetch pipeline
+  ))
+  // force clear data_corrupt when parity check is disabled
+  when(!csr_parity_enable) {
+    s2_data_corrupt := VecInit(Seq.fill(PortNumber)(false.B))
+  }
+  // meta error is checked in s1 stage
   val s2_meta_corrupt = RegEnable(s1_meta_corrupt, 0.U.asTypeOf(s1_meta_corrupt), s1_fire)
   // send errors to top
+  // TODO: support RERI spec standard interface
   (0 until PortNumber).map { i =>
-    io.errors(i).valid := io.csr_parity_enable && RegNext(s1_fire) && (s2_meta_corrupt(i) || s2_data_corrupt(i))
-    io.errors(i).bits.report_to_beu := io.csr_parity_enable && RegNext(s1_fire) && (s2_meta_corrupt(
-      i
-    ) || s2_data_corrupt(i))
-    io.errors(i).bits.paddr        := s2_req_paddr(i)
-    io.errors(i).bits.source       := DontCare
-    io.errors(i).bits.source.tag   := s2_meta_corrupt(i)
-    io.errors(i).bits.source.data  := s2_data_corrupt(i)
-    io.errors(i).bits.source.l2    := false.B
-    io.errors(i).bits.opType       := DontCare
-    io.errors(i).bits.opType.fetch := true.B
+    io.errors(i).valid              := (s2_meta_corrupt(i) || s2_data_corrupt(i)) && RegNext(s1_fire)
+    io.errors(i).bits.report_to_beu := (s2_meta_corrupt(i) || s2_data_corrupt(i)) && RegNext(s1_fire)
+    io.errors(i).bits.paddr         := s2_req_paddr(i)
+    io.errors(i).bits.source        := DontCare
+    io.errors(i).bits.source.tag    := s2_meta_corrupt(i)
+    io.errors(i).bits.source.data   := s2_data_corrupt(i)
+    io.errors(i).bits.source.l2     := false.B
+    io.errors(i).bits.opType        := DontCare
+    io.errors(i).bits.opType.fetch  := true.B
+  }
+  // flush metaArray to prepare for re-fetch
+  (0 until PortNumber).foreach { i =>
+    toMetaFlush(i).valid       := (s2_meta_corrupt(i) || s2_data_corrupt(i)) && RegNext(s1_fire)
+    toMetaFlush(i).bits.virIdx := s2_req_vSetIdx(i)
+    // if is meta corrupt, clear all way (since waymask may be unreliable)
+    // if is data corrupt, only clear the way that has error
+    toMetaFlush(i).bits.waymask := Mux(s2_meta_corrupt(i), Fill(nWays, true.B), s2_waymasks(i).asUInt)
   }
+  // PERF: count the number of data parity errors
+  XSPerfAccumulate("data_corrupt_0", s2_data_corrupt(0) && RegNext(s1_fire))
+  XSPerfAccumulate("data_corrupt_1", s2_data_corrupt(1) && RegNext(s1_fire))
+  XSPerfAccumulate("meta_corrupt_0", s2_meta_corrupt(0) && RegNext(s1_fire))
+  XSPerfAccumulate("meta_corrupt_1", s2_meta_corrupt(1) && RegNext(s1_fire))
+  // TEST: stop simulation if parity error is detected, and dump wave
+//  val (assert_valid, assert_val) = DelayNWithValid(s2_meta_corrupt.reduce(_ || _), s2_valid, 1000)
+//  assert(!(assert_valid && assert_val))
+//  val (assert_valid, assert_val) = DelayNWithValid(s2_data_corrupt.reduce(_ || _), s2_valid, 1000)
+//  assert(!(assert_valid && assert_val))
 
   /**
     ******************************************************************************
@@ -400,10 +427,12 @@ class ICacheMainPipe(implicit p: Parameters) extends ICacheModule {
 
   (0 until ICacheDataBanks).foreach { i =>
     when(s1_fire) {
-      s2_datas := s1_datas
-    }.elsewhen(s2_bankMSHRHit(i) && !fromMSHR.bits.corrupt) {
-      // if corrupt, no need to update s2_datas (it's wrong anyway), to save power
+      s2_datas             := s1_datas
+      s2_data_is_from_MSHR := s1_data_is_from_MSHR
+    }.elsewhen(s2_bankMSHRHit(i)) {
       s2_datas(i) := s2_MSHR_datas(i)
+      // also update s2_data_is_from_MSHR when re-fetched, to clear s2_data_corrupt flag and let s2_fire
+      s2_data_is_from_MSHR(i) := true.B
     }
   }
 
@@ -413,6 +442,8 @@ class ICacheMainPipe(implicit p: Parameters) extends ICacheModule {
     }.elsewhen(s2_MSHR_hits(i)) {
       // update s2_hits even if it's corrupt, to let s2_fire
       s2_hits(i) := true.B
+      // also clear s2_meta_corrupt flag when re-fetched, to let s2_fire
+      s2_meta_corrupt(i) := false.B
     }
   }
 
@@ -427,7 +458,7 @@ class ICacheMainPipe(implicit p: Parameters) extends ICacheModule {
 
   /**
     ******************************************************************************
-    * send request to MSHR if ICache miss
+    * send request to MSHR if ICache miss / ECC corrupt
     ******************************************************************************
     */
 
@@ -436,12 +467,18 @@ class ICacheMainPipe(implicit p: Parameters) extends ICacheModule {
     mmio || Pbmt.isUncache(pbmt)
   })
 
+  // try re-fetch data from L2 cache if ECC error is detected, unless it's from MSHR
+  val s2_corrupt_refetch = (s2_meta_corrupt zip s2_data_corrupt).map {
+    case (meta, data) => meta || data
+  }
+
   /* s2_exception includes itlb pf/gpf/af, pmp af and meta corruption (af), neither of which should be fetched
    * mmio should not be fetched, it will be fetched by IFU mmio fsm
    * also, if previous has exception, latter port should also not be fetched
    */
-  val s2_miss = VecInit((0 until PortNumber).map { i =>
-    !s2_hits(i) && (if (i == 0) true.B else s2_doubleline) &&
+  val s2_should_fetch = VecInit((0 until PortNumber).map { i =>
+    (!s2_hits(i) || s2_corrupt_refetch(i)) &&
+    (if (i == 0) true.B else s2_doubleline) &&
     !ExceptionType.hasException(s2_exception.take(i + 1)) &&
     s2_mmio.take(i + 1).map(!_).reduce(_ && _)
   })
@@ -449,17 +486,17 @@ class ICacheMainPipe(implicit p: Parameters) extends ICacheModule {
   val toMSHRArbiter = Module(new Arbiter(new ICacheMissReq, PortNumber))
 
   // To avoid sending duplicate requests.
-  val has_send = RegInit(VecInit(Seq.fill(PortNumber)(false.B)))
+  val s2_has_send = RegInit(VecInit(Seq.fill(PortNumber)(false.B)))
   (0 until PortNumber).foreach { i =>
     when(s1_fire) {
-      has_send(i) := false.B
+      s2_has_send(i) := false.B
     }.elsewhen(toMSHRArbiter.io.in(i).fire) {
-      has_send(i) := true.B
+      s2_has_send(i) := true.B
     }
   }
 
   (0 until PortNumber).map { i =>
-    toMSHRArbiter.io.in(i).valid         := s2_valid && s2_miss(i) && !has_send(i) && !s2_flush
+    toMSHRArbiter.io.in(i).valid         := s2_valid && s2_should_fetch(i) && !s2_has_send(i) && !s2_flush
     toMSHRArbiter.io.in(i).bits.blkPaddr := getBlkAddr(s2_req_paddr(i))
     toMSHRArbiter.io.in(i).bits.vSetIdx  := s2_req_vSetIdx(i)
   }
@@ -467,16 +504,15 @@ class ICacheMainPipe(implicit p: Parameters) extends ICacheModule {
 
   XSPerfAccumulate("to_missUnit_stall", toMSHR.valid && !toMSHR.ready)
 
-  val s2_fetch_finish = !s2_miss.reduce(_ || _)
+  val s2_fetch_finish = !s2_should_fetch.reduce(_ || _)
 
-  // also raise af if data/l2 corrupt is detected
-  val s2_data_exception = VecInit(s2_data_corrupt.map(ExceptionType.fromECC(io.csr_parity_enable, _)))
-  val s2_l2_exception   = VecInit(s2_l2_corrupt.map(ExceptionType.fromECC(true.B, _)))
+  // also raise af if l2 corrupt is detected
+  val s2_l2_exception = VecInit(s2_l2_corrupt.map(ExceptionType.fromECC(true.B, _)))
+  // NOTE: do NOT raise af if meta/data corrupt is detected, they are automatically recovered by re-fetching from L2
 
-  // merge s2 exceptions, itlb has the highest priority, meta next, meta/data/l2 lowest (and we dont care about prioritizing between this three)
+  // merge s2 exceptions, itlb has the highest priority, then l2
   val s2_exception_out = ExceptionType.merge(
-    s2_exception, // includes itlb/pmp/meta exception
-    s2_data_exception,
+    s2_exception, // includes itlb/pmp exception
     s2_l2_exception
   )
```
