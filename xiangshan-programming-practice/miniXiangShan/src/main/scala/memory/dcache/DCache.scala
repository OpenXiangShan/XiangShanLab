package minixiangshan.mem.dcache
 
import chisel3._
import chisel3.util._
import minixiangshan.config._
import minixiangshan.backend.decode._
import minixiangshan.backend.rename._
import minixiangshan.mem.L2cache._
import minixiangshan.frontend.icache.CacheReplacerD
import minixiangshan.backend.execute._

class DCache(implicit p: Parameters) extends NSModule {
 
  val io = IO(new Bundle {
    val loadReq  = Flipped(Decoupled(new Bundle {
      val lqIdx     = UInt(log2Ceil(LqSize).W)
      val robIdx    = new RobPtr(RobSize)
      val paddr     = UInt(XLEN.W)
      val vaddr     = UInt(XLEN.W)
      val cacheable = Bool()
      val lsuOp     = UInt(LsuOp.width.W)
    }))
    val loadResp = Decoupled(new Bundle {
      val lqIdx = UInt(log2Ceil(LqSize).W)
      val data  = UInt(XLEN.W)
    })
    val storeReq = Flipped(Decoupled(new Bundle {
      val paddr     = UInt(XLEN.W)
      val vaddr     = UInt(XLEN.W)
      val data      = UInt(XLEN.W)
      val lsuOp     = UInt(LsuOp.width.W)
      val cacheable = Bool()
      val sqIdx     = UInt(log2Ceil(SqSize).W)
    }))
    val storeAck = Decoupled(new Bundle {
      val sqIdx = UInt(log2Ceil(SqSize).W)
    })
    val fenceReq  = Input(Bool())
    val fenceDone = Output(Bool())
    val l2        = new L2NativeMasterIO(1)
    val redirectInfo = Flipped(ValidIO(new redirectInfoToModule))
  })
 
  val array    = Module(new DCacheArray)
  val replacer = Module(new CacheReplacerD)
  val mshr     = Module(new DCacheMSHRFile)
 
  val f_idle :: f_wait_idle :: f_find_dirty :: f_read_req :: f_read_resp :: f_wb_req :: f_wb_done :: f_clear :: f_done :: Nil = Enum(9)
  val fenceState = RegInit(f_idle)
  val fenceSet = RegInit(0.U(idxBitsD.W))
  val fenceWay = RegInit(0.U(wayBitsD.W))
  val fenceWbTag = RegInit(0.U(tagBitsD.W))
  val fenceWbData = RegInit(0.U((blockBytes * 8).W))
  io.fenceDone := fenceState === f_done
 
  def shouldFlush(robIdx: RobPtr): Bool =
     robIdx.isAfter(io.redirectInfo.bits.robIdx) && io.redirectInfo.valid && io.redirectInfo.bits.doRedirect

  def doTagCompare(data: DCacheArrayReadData, paddr: UInt, cacheable: Bool) = {
    val ptag = paddr(31, blockOffBits + idxBitsD)
    val hits = Wire(Vec(nWaysD, Bool()))
    for (i <- 0 until nWaysD) hits(i) := data.ways(i).valid && data.ways(i).tag === ptag
    val hit    = hits.asUInt.orR && cacheable
    val hitWay = OHToUInt(hits)
    (hit, hitWay)
  }

  def extractLoadData(data: DCacheArrayReadData, hitWay: UInt, paddr: UInt, lsuOp: UInt) = {
    val lineData = data.ways(hitWay).data
    val wordOff  = paddr(blockOffBits - 1, 2)
    val byteOff  = paddr(1, 0)
    val rawWord = Wire(UInt(XLEN.W)); rawWord := 0.U
    for (w <- 0 until blockBytes / 4) when(wordOff === w.U) { rawWord := lineData(w * XLEN + XLEN - 1, w * XLEN) }
    val byteData = Wire(UInt(8.W)); byteData := 0.U
    switch(byteOff) {
      is(0.U) { byteData := rawWord(7, 0) }
      is(1.U) { byteData := rawWord(15, 8) }
      is(2.U) { byteData := rawWord(23, 16) }
      is(3.U) { byteData := rawWord(31, 24) }
    }
    val halfData = Wire(UInt(16.W)); halfData := 0.U
    switch( byteOff(1).asUInt ) {
      is(0.U) { halfData := rawWord(15, 0) }
      is(1.U) { halfData := rawWord(31, 16) }
    }
    MuxLookup(lsuOp, rawWord, Seq(
      LsuOp.lw  -> rawWord, LsuOp.lh  -> Cat(Fill(16, halfData(15)), halfData), LsuOp.lhu -> Cat(0.U(16.W), halfData),
      LsuOp.lb  -> Cat(Fill(24, byteData(7)), byteData), LsuOp.lbu -> Cat(0.U(24.W), byteData)
    ))
  }

  def extractUncacheLoadData(rawWord: UInt, paddr: UInt, lsuOp: UInt) = {
    val byteOff  = paddr(1, 0)
    val byteData = MuxLookup(byteOff, rawWord(7, 0), Seq(0.U -> rawWord(7, 0), 1.U -> rawWord(15, 8), 2.U -> rawWord(23, 16), 3.U -> rawWord(31, 24)))
    val halfData = Mux(byteOff(1), rawWord(31, 16), rawWord(15, 0))
    MuxLookup(lsuOp, rawWord, Seq(LsuOp.lw -> rawWord, LsuOp.lh -> Cat(Fill(16, halfData(15)), halfData), LsuOp.lhu -> Cat(0.U(16.W), halfData), LsuOp.lb -> Cat(Fill(24, byteData(7)), byteData), LsuOp.lbu -> Cat(0.U(24.W), byteData)))
  }

  def mergeStoreLine(data: DCacheArrayReadData, hitWay: UInt, paddr: UInt, storeData: UInt, lsuOp: UInt) = {
    val lineData = data.ways(hitWay).data
    val wordOff  = paddr(blockOffBits - 1, 2)
    val byteOff  = paddr(1, 0)
    val merged = Wire(Vec(blockBytes / 4, UInt(XLEN.W)))
    for (w <- 0 until blockBytes / 4) merged(w) := lineData(w * XLEN + XLEN - 1, w * XLEN)
    val targetWord = Wire(UInt(XLEN.W)); targetWord := 0.U
    for (w <- 0 until blockBytes / 4) when(wordOff === w.U) { targetWord := lineData(w * XLEN + XLEN - 1, w * XLEN) }
    val sbEnable = UIntToOH(byteOff, 4)
    val shEnable = Mux(byteOff(1), Cat(true.B, true.B, false.B, false.B), Cat(false.B, false.B, true.B, true.B))
    val swEnable = Cat(true.B, true.B, true.B, true.B)
    val finalEnable = MuxLookup(lsuOp, swEnable, Seq(LsuOp.sb -> sbEnable, LsuOp.sh -> shEnable, LsuOp.sw -> swEnable))
    val shiftedStoreData = MuxLookup(lsuOp, storeData, Seq(LsuOp.sb -> (storeData(7, 0) << (byteOff * 8.U)), LsuOp.sh -> (storeData(15, 0) << (Cat(byteOff(1), 0.U(1.W)) * 8.U)), LsuOp.sw -> storeData))
    val newWord = Cat(Mux(finalEnable(3), shiftedStoreData(31, 24), targetWord(31, 24)), Mux(finalEnable(2), shiftedStoreData(23, 16), targetWord(23, 16)), Mux(finalEnable(1), shiftedStoreData(15, 8),  targetWord(15, 8)), Mux(finalEnable(0), shiftedStoreData(7, 0), targetWord(7, 0)))
    for (w <- 0 until blockBytes / 4) when(wordOff === w.U) { merged(w) := newWord }
    Cat(merged.reverse)
  }

  // ================================================================
  //  Pending Buffer (用于拯救由于 MSHR 满载而无路可走的请求)
  // ================================================================
  val pend_valid     = RegInit(false.B)
  val pend_paddr     = RegInit(0.U(XLEN.W))
  val pend_lqIdx     = RegInit(0.U(log2Ceil(LqSize).W))
  val pend_sqIdx     = RegInit(0.U(log2Ceil(SqSize).W))
  val pend_robIdx    = RegInit(0.U.asTypeOf(new RobPtr(RobSize)))
  val pend_lsuOp     = RegInit(0.U(LsuOp.width.W))
  val pend_storeData = RegInit(0.U(XLEN.W))
  val pend_isLoad    = RegInit(false.B)
  val pend_isStore   = RegInit(false.B)
  val pend_cacheable = RegInit(false.B)

  // ================================================================
  //  流水线 Stage 2 (S2) 寄存器组
  // ================================================================
  val s2_valid     = RegInit(false.B)
  val s2_paddr     = RegInit(0.U(XLEN.W))
  val s2_lqIdx     = RegInit(0.U(log2Ceil(LqSize).W))
  val s2_sqIdx     = RegInit(0.U(log2Ceil(SqSize).W))
  val s2_robIdx    = RegInit(0.U.asTypeOf(new RobPtr(RobSize)))
  val s2_lsuOp     = RegInit(0.U(LsuOp.width.W))
  val s2_storeData = RegInit(0.U(XLEN.W))
  val s2_isLoad    = RegInit(false.B)
  val s2_isStore   = RegInit(false.B)
  val s2_cacheable = RegInit(false.B)
  val s2_isReplay  = RegInit(false.B)
  val s2_lsIdx     = RegInit(0.U(log2Ceil(4).W))
  val s2_ucData    = RegInit(0.U(XLEN.W))
  val s2_already_flushed = RegInit(false.B)
  val s2_isPend    = RegInit(false.B) // 标记 S2 的指令是否来自 Pending Buffer，防死循环

  val s2_ready = Wire(Bool())
  val do_refill = mshr.io.refillWriteReq.valid
  val storeBlocked = mshr.io.hasStore

  // ================================================================
  //  流水线 Stage 2 (S2) : 执行与反馈 (包含 Abort 逻辑)
  // ================================================================
  val s2_is_flushed = (s2_isLoad && !s2_isStore && shouldFlush(s2_robIdx)) || s2_already_flushed
  val (s2_hit, s2_hitWay) = doTagCompare(array.io.read.resp, s2_paddr, s2_cacheable)
  val s2_is_miss   = s2_valid && s2_cacheable && !s2_hit && !s2_isReplay && !s2_is_flushed
  val s2_is_uc_req = s2_valid && !s2_cacheable && !s2_isReplay && !s2_is_flushed

  val s2_fire_load  = s2_valid && s2_isLoad && (s2_hit || (!s2_cacheable && s2_isReplay)) && !s2_is_flushed
  val s2_fire_store = s2_valid && s2_isStore && (s2_hit || (!s2_cacheable && s2_isReplay)) && !s2_is_flushed && !do_refill
  val s2_req_mshr   = s2_is_miss || s2_is_uc_req

  val s2_mshr_accept = s2_req_mshr && mshr.io.missReq.ready
  // 核心改动：如果 MSHR 拒绝，触发 Reject 信号，允许 S2 ready（即丢弃当前流水级并进入 Pending）
  val s2_mshr_reject = s2_req_mshr && !mshr.io.missReq.ready

  s2_ready := !s2_valid || s2_is_flushed ||
              (s2_fire_load && io.loadResp.ready) ||
              (s2_fire_store && io.storeAck.ready) ||
              s2_mshr_accept || s2_mshr_reject

  // ================================================================
  //  流水线 Stage 1 (S1) : 仲裁与 SRAM 预读
  // ================================================================
  val storeWaitCnt  = RegInit(0.U(8.W))
  val storeStarving = storeWaitCnt >= 8.U
  val loadSelected  = io.loadReq.valid && (!io.storeReq.valid || !storeStarving)
  val storeSelected = io.storeReq.valid && (!io.loadReq.valid || storeStarving)
  val lsuHasReq     = io.loadReq.valid || io.storeReq.valid

  // 优先级 1: MSHR Replay (最高优先级，用于清空 MSHR 打破死锁)
  val mshr_replay_in_s2 = s2_valid && s2_isReplay && s2_lsIdx === mshr.io.lsIdx
  val do_mshr_replay = mshr.io.lsReady && !mshr_replay_in_s2

  // 优先级 2: Pending Retry
  val do_pend_retry = pend_valid && !do_mshr_replay && !s2_isPend

  // 优先级 3: LSU Request (仅在无前两级干扰、且不引发 Pending 重叠时允许发射)
  // 如果 S2 当前正要产生 Reject 写入 Pend，绝对禁止 S1 发射新指令，防止覆盖数据
  val s2_will_reject = s2_valid && !s2_is_flushed && s2_mshr_reject
  val lsu_allowed = lsuHasReq && !storeBlocked && fenceState === f_idle && !io.fenceReq && !pend_valid && !s2_will_reject
  val do_lsu_req = lsu_allowed && !do_mshr_replay && !do_pend_retry

  val s1_valid = do_mshr_replay || do_pend_retry || do_lsu_req
  val s1_isReplay = do_mshr_replay
  val s1_isPend   = do_pend_retry

  val s1_paddr    = Mux(do_mshr_replay, mshr.io.lsPaddr, Mux(do_pend_retry, pend_paddr, Mux(loadSelected, io.loadReq.bits.paddr, io.storeReq.bits.paddr)))
  val s1_lqIdx    = Mux(do_mshr_replay, mshr.io.lsLqIdx, Mux(do_pend_retry, pend_lqIdx, Mux(loadSelected, io.loadReq.bits.lqIdx, 0.U)))
  val s1_sqIdx    = Mux(do_mshr_replay, mshr.io.lsSqIdx, Mux(do_pend_retry, pend_sqIdx, Mux(storeSelected, io.storeReq.bits.sqIdx, 0.U)))
  val s1_robIdx   = Mux(do_mshr_replay, mshr.io.lsRobIdx, Mux(do_pend_retry, pend_robIdx, Mux(loadSelected, io.loadReq.bits.robIdx, 0.U.asTypeOf(new RobPtr(RobSize)))))
  val s1_lsuOp    = Mux(do_mshr_replay, mshr.io.lsLsuOp, Mux(do_pend_retry, pend_lsuOp, Mux(loadSelected, io.loadReq.bits.lsuOp, io.storeReq.bits.lsuOp)))
  val s1_storeData= Mux(do_mshr_replay, mshr.io.lsStoreData, Mux(do_pend_retry, pend_storeData, Mux(storeSelected, io.storeReq.bits.data, 0.U)))
  val s1_isLoad   = Mux(do_mshr_replay, mshr.io.lsIsLoad, Mux(do_pend_retry, pend_isLoad, loadSelected))
  val s1_isStore  = Mux(do_mshr_replay, mshr.io.lsIsStore, Mux(do_pend_retry, pend_isStore, storeSelected))
  val s1_cacheable= Mux(do_mshr_replay, !mshr.io.lsIsUncache, Mux(do_pend_retry, pend_cacheable, Mux(loadSelected, io.loadReq.bits.cacheable, io.storeReq.bits.cacheable)))
  val s1_lsIdx    = mshr.io.lsIdx
  val s1_ucData   = mshr.io.lsUncacheData

  val s1_raw_hazard = do_refill || (s2_valid && s2_isStore)
  val s1_fire = s1_valid && s2_ready && !s1_raw_hazard

  when(io.storeReq.valid && !io.loadReq.valid) {
    storeWaitCnt := 0.U
  }.elsewhen(io.storeReq.valid && io.loadReq.valid && !storeStarving) {
    when(s1_fire && do_lsu_req && loadSelected) { storeWaitCnt := storeWaitCnt + 1.U }
  }.otherwise {
    storeWaitCnt := 0.U
  }

  io.loadReq.ready  := s1_fire && do_lsu_req && loadSelected
  io.storeReq.ready := s1_fire && do_lsu_req && storeSelected

  // ================================================================
  //  Pending & S2 寄存器状态更新逻辑
  // ================================================================
  val pend_flushed = pend_valid && pend_isLoad && !pend_isStore && shouldFlush(pend_robIdx)

  when (pend_flushed) {
    pend_valid := false.B
  } .elsewhen (s2_valid && !s2_is_flushed && s2_mshr_reject) {
    // S2 遭遇 MSHR 满载，放弃当前流水级，打包存入 Pending 避让死锁
    pend_valid     := true.B
    pend_paddr     := s2_paddr
    pend_lqIdx     := s2_lqIdx
    pend_sqIdx     := s2_sqIdx
    pend_robIdx    := s2_robIdx
    pend_lsuOp     := s2_lsuOp
    pend_storeData := s2_storeData
    pend_isLoad    := s2_isLoad
    pend_isStore   := s2_isStore
    pend_cacheable := s2_cacheable
  } .elsewhen (s1_fire && do_pend_retry) {
    // Pending 成功出列进入 S1
    pend_valid := false.B
  }

  when (s2_ready) {
    s2_valid := s1_fire
    s2_already_flushed := s1_isLoad && !s1_isStore && shouldFlush(s1_robIdx)
    s2_isPend := s1_fire && s1_isPend
    when (s1_fire) {
      s2_paddr     := s1_paddr
      s2_lqIdx     := s1_lqIdx
      s2_sqIdx     := s1_sqIdx
      s2_robIdx    := s1_robIdx
      s2_lsuOp     := s1_lsuOp
      s2_storeData := s1_storeData
      s2_isLoad    := s1_isLoad
      s2_isStore   := s1_isStore
      s2_cacheable := s1_cacheable
      s2_isReplay  := s1_isReplay
      s2_lsIdx     := s1_lsIdx
      s2_ucData    := s1_ucData
    }
  } .otherwise {
    when (s2_isLoad && !s2_isStore && shouldFlush(s2_robIdx)) { s2_already_flushed := true.B }
  }

  val s2_is_first_cycle = s2_valid && RegNext(s1_fire)
  val s2_victimWay_reg = RegInit(0.U(wayBitsD.W))
  when (s2_is_first_cycle) { s2_victimWay_reg := replacer.io.victim.resp }
  val s2_victimWay = Mux(s2_is_first_cycle, replacer.io.victim.resp, s2_victimWay_reg)

  // ================================================================
  //  输出到外部 / 下游的连线
  // ================================================================
  io.loadResp.valid      := s2_fire_load
  io.loadResp.bits.lqIdx := s2_lqIdx
  io.loadResp.bits.data  := Mux(!s2_cacheable, extractUncacheLoadData(s2_ucData, s2_paddr, s2_lsuOp), extractLoadData(array.io.read.resp, s2_hitWay, s2_paddr, s2_lsuOp))

  io.storeAck.valid      := s2_fire_store
  io.storeAck.bits.sqIdx := s2_sqIdx

  mshr.io.missReq.valid            := s2_mshr_accept
  mshr.io.missReq.bits.paddr       := s2_paddr
  mshr.io.missReq.bits.lqIdx       := s2_lqIdx
  mshr.io.missReq.bits.sqIdx       := s2_sqIdx
  mshr.io.missReq.bits.robIdx      := s2_robIdx
  mshr.io.missReq.bits.lsuOp       := s2_lsuOp
  mshr.io.missReq.bits.storeData   := s2_storeData
  mshr.io.missReq.bits.isLoad      := s2_isLoad
  mshr.io.missReq.bits.isStore     := s2_isStore
  mshr.io.missReq.bits.cacheable   := s2_cacheable
  mshr.io.missReq.bits.victimWay   := s2_victimWay
  mshr.io.missReq.bits.victimDirty := s2_cacheable && array.io.read.resp.ways(s2_victimWay).dirty && array.io.read.resp.ways(s2_victimWay).valid
  mshr.io.missReq.bits.victimTag   := Mux(s2_cacheable, array.io.read.resp.ways(s2_victimWay).tag, 0.U)
  mshr.io.missReq.bits.victimData  := Mux(s2_cacheable, array.io.read.resp.ways(s2_victimWay).data, 0.U)

  mshr.io.redirectInfo := io.redirectInfo
  mshr.io.probeBlockAddr := s2_paddr(31, blockOffBits)
  mshr.io.refillWriteAck.valid := do_refill
  mshr.io.refillWriteAck.bits  := mshr.io.refillWritePrimId
  mshr.io.lsAck.valid := s2_valid && s2_isReplay && ((s2_fire_load && io.loadResp.ready) || (s2_fire_store && io.storeAck.ready))
  mshr.io.lsAck.bits  := s2_lsIdx

  val fenceArrayReadValid = fenceState === f_read_req
  array.io.read.valid := fenceArrayReadValid || s1_fire
  array.io.read.idx   := Mux(fenceArrayReadValid, fenceSet, s1_paddr(blockOffBits + idxBitsD - 1, blockOffBits))

  val storeWriteActive  = s2_fire_store && io.storeAck.ready && s2_cacheable
  val refillWriteActive = do_refill
  array.io.write.valid := storeWriteActive || refillWriteActive
  array.io.write.idx   := Mux(refillWriteActive, mshr.io.refillWriteReq.bits.idx, s2_paddr(blockOffBits + idxBitsD - 1, blockOffBits))
  array.io.write.way   := Mux(refillWriteActive, mshr.io.refillWriteReq.bits.way, s2_hitWay)
  array.io.write.tag   := Mux(refillWriteActive, mshr.io.refillWriteReq.bits.tag, s2_paddr(31, blockOffBits + idxBitsD))
  array.io.write.dirty := Mux(refillWriteActive, false.B, true.B)
  array.io.write.data  := Mux(refillWriteActive, mshr.io.refillWriteReq.bits.data, mergeStoreLine(array.io.read.resp, s2_hitWay, s2_paddr, s2_storeData, s2_lsuOp))
  array.io.write.wen   := true.B

  val normalMetaWriteActive = s2_mshr_accept && mshr.io.isFirstMiss && s2_cacheable
  val fenceMetaWriteActive  = fenceState === f_clear
  array.io.metaWrite.valid     := fenceMetaWriteActive || normalMetaWriteActive
  array.io.metaWrite.idx       := Mux(fenceMetaWriteActive, fenceSet, s2_paddr(blockOffBits + idxBitsD - 1, blockOffBits))
  array.io.metaWrite.way       := Mux(fenceMetaWriteActive, fenceWay, s2_victimWay)
  array.io.metaWrite.metaValid := false.B
  array.io.metaWrite.dirty     := false.B
  array.io.metaWrite.tag       := Mux(fenceMetaWriteActive, fenceWbTag, 0.U)

  replacer.io.victim.req := array.io.read.valid && !s1_isReplay
  replacer.io.victim.idx := array.io.read.idx
  replacer.io.touch.valid := (s2_fire_load && io.loadResp.ready) || storeWriteActive || refillWriteActive
  replacer.io.touch.idx   := Mux(refillWriteActive, mshr.io.refillWriteReq.bits.idx, s2_paddr(blockOffBits + idxBitsD - 1, blockOffBits))
  replacer.io.touch.way   := Mux(refillWriteActive, mshr.io.refillWriteReq.bits.way, s2_hitWay)
  replacer.io.flush.valid := false.B
  replacer.io.flush.idx   := 0.U

  // ================================================================
  //  Fence 状态机与 L2 native 仲裁
  // ================================================================
  switch(fenceState) {
    is(f_idle) { when(io.fenceReq) { fenceState := f_wait_idle } }
    is(f_wait_idle) {
      when(!io.fenceReq) { fenceState := f_idle }
      .elsewhen(!s2_valid && !pend_valid && mshr.io.idle) { fenceState := f_find_dirty }
    }
    is(f_find_dirty) {
      when(array.io.hasDirty) {
        fenceSet := array.io.dirtyIdx
        fenceWay := array.io.dirtyWay
        fenceState := f_read_req
      }.otherwise { fenceState := f_done }
    }
    is(f_read_req) { fenceState := f_read_resp }
    is(f_read_resp) {
      when(array.io.read.validOut) {
        fenceWbTag := array.io.read.resp.ways(fenceWay).tag
        fenceWbData := array.io.read.resp.ways(fenceWay).data
        fenceState := f_wb_req
      }
    }
    is(f_wb_req) {
      when(io.l2.write.req.fire) { fenceState := f_wb_done }
    }
    // cleanLine只有在L2返回done（即DDR写响应完成）后才能清除dirty行。
    is(f_wb_done) {
      when(io.l2.write.done.fire) { fenceState := f_clear }
    }
    is(f_clear) { fenceState := f_find_dirty }
    is(f_done) { when(!io.fenceReq) { fenceState := f_idle } }
  }
  array.io.dcacheInvalid := fenceState === f_done

  val fenceOwnsL2 = fenceState === f_wb_req || fenceState === f_wb_done
  val fenceWbAddr = Cat(fenceWbTag, fenceSet, 0.U(blockOffBits.W))

  // 读通道始终由MSHR使用；fence只在MSHR排空后接管写通道。
  io.l2.read <> mshr.io.l2.read

  io.l2.write.req.valid := Mux(fenceOwnsL2,
    fenceState === f_wb_req, mshr.io.l2.write.req.valid)
  io.l2.write.req.bits := mshr.io.l2.write.req.bits
  when(fenceOwnsL2) {
    io.l2.write.req.bits.id := 0.U
    io.l2.write.req.bits.addr := fenceWbAddr
    io.l2.write.req.bits.kind := L2WriteKind.cleanLine
    io.l2.write.req.bits.size := 2.U
    io.l2.write.req.bits.data := fenceWbData
    io.l2.write.req.bits.strb := Fill(l2BeatBytes, 1.U(1.W))
  }
  mshr.io.l2.write.req.ready := io.l2.write.req.ready && !fenceOwnsL2

  mshr.io.l2.write.done.valid := io.l2.write.done.valid && !fenceOwnsL2
  mshr.io.l2.write.done.bits := io.l2.write.done.bits
  io.l2.write.done.ready := Mux(fenceOwnsL2,
    fenceState === f_wb_done, mshr.io.l2.write.done.ready)
}