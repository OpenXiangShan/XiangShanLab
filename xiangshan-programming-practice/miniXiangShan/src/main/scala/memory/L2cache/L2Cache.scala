package minixiangshan.mem.L2cache

import chisel3._
import chisel3.util._
import minixiangshan.axi._
import minixiangshan.config._

object L2PrefetchProtocol {
  final val EpochBits = 8
}

object L2PortSource {
  def icache: Bool = false.B
  def dcache: Bool = true.B
}

object L2BridgeReadOwner {
  def mshr: UInt = 0.U(2.W)
  def uncache: UInt = 1.U(2.W)
}

object L2BridgeWriteOwner {
  def eviction: UInt = 0.U(2.W)
  def cleanLine: UInt = 1.U(2.W)
  def uncache: UInt = 2.U(2.W)
}

class L2LookupToken(implicit p: Parameters) extends NSBundle {
  val source = Bool()
  val id = UInt(1.W)
  val addr = UInt(XLEN.W)
  val isStb = Bool()
  val isUncacheProbe = Bool()
  val isPrefetch = Bool()
  val prefetchEpoch = UInt(L2PrefetchProtocol.EpochBits.W)
  val cancelled = Bool()
  val stbSlot = UInt(log2Ceil(l2IStbEntries + l2DStbEntries).W)
  val busySlot = UInt(L2LookupBusyScoreboard.SlotBits.W)
}

class L2LookupResult(implicit p: Parameters) extends NSBundle {
  val token = new L2LookupToken
  val hit = Bool()
  val way = UInt(l2WayBits.W)
  val oldValid = Bool()
  val oldDirty = Bool()
  val oldTag = UInt(l2TagBits.W)
  val oldData = UInt(l2LineBits.W)
  val stbMatch = Bool()
  val stbForwarded = Bool()
  val mshrSetConflict = Bool()
  val ebBlockConflict = Bool()
}

class L2Cache(implicit p: Parameters) extends NSModule {
  val io = IO(new Bundle {
    val icache = Flipped(new L2NativeReadIO(1))
    val dcache = Flipped(new L2NativeMasterIO(1))
    val maintenance = Flipped(new L2MaintenanceMasterIO)
    val axi = new AXI3MasterIO
  })

  val array = Module(new L2CacheArray)
  val replacer = Module(new L2Replacer)
  val bridge = Module(new L2Bridge)
  io.axi <> bridge.io.axi

  // V1 不接管 Cache 维护路径。
  io.maintenance.req.ready := false.B
  io.maintenance.done.valid := false.B
  io.maintenance.done.bits.done := false.B

  // ICache request boundary: no combinational bypass into L2.
  val iReqEntryValid = RegInit(false.B)
  val iReqEntryBits = RegInit(0.U.asTypeOf(new L2ReadReq(1)))
  val iActive = RegInit(false.B)
  val iActiveMshr = RegInit(0.U(l2MshrIdBits.W))
  val iActiveHasMshr = RegInit(false.B)
  val iKilled = RegInit(false.B)
  // Strict non-flowing D-side request boundaries. L2 internals never inspect
  // raw DCache payloads or feed CAM/Array backpressure directly to DCache.
  val dReqEntryValid = RegInit(false.B)
  val dReqEntryBits = RegInit(0.U.asTypeOf(new L2ReadReq(1)))
  val dWriteEntryValid = RegInit(false.B)
  val dWriteEntryBits = RegInit(0.U.asTypeOf(new L2WriteReq(1)))
  val iCancelPending = RegInit(false.B)
  val iCancelClearEntry = RegInit(false.B)
  val iCancelKillActive = RegInit(false.B)
  val iPrefetchEpoch = RegInit(0.U(L2PrefetchProtocol.EpochBits.W))
  val iCancelWasHigh = RegNext(io.icache.cancel, false.B)
  val iCancelPulse = io.icache.cancel && !iCancelWasHigh


  // Completion events cross another register before entering either predictor.
  val iPrefetchTrainValid = RegInit(false.B)
  val iPrefetchTrainAddr = RegInit(0.U(XLEN.W))
  val dPrefetchTrainValid = RegInit(false.B)
  val dPrefetchTrainAddr = RegInit(0.U(XLEN.W))
  // A live grant owns one of the four lookup/result credits until it fires or
  // is discarded, so ordinary demand lookup can never overbook resultQueue.
  val prefetchGrantValid = RegInit(false.B)

  val iLrbValid = RegInit(false.B)
  val iLrbBits = Reg(new L2ReadResp(1))
  val iLrbFromMshr = RegInit(false.B)
  val iLrbFromUncache = RegInit(false.B)
  val iLrbMshr = RegInit(0.U(l2MshrIdBits.W))
  val iLrbAddr = RegInit(0.U(XLEN.W))

  val dLrbValid = RegInit(false.B)
  val dLrbBits = Reg(new L2ReadResp(1))
  val dLrbFromMshr = RegInit(false.B)
  val dLrbFromUncache = RegInit(false.B)
  val dLrbMshr = RegInit(0.U(l2MshrIdBits.W))
  val dLrbAddr = RegInit(0.U(XLEN.W))

  io.icache.resp.valid := iLrbValid && !iKilled && !iCancelPending
  io.icache.resp.bits := iLrbBits
  io.dcache.read.resp.valid := dLrbValid
  io.dcache.read.resp.bits := dLrbBits

  val iLrbReady = !iLrbValid
  val dLrbReady = !dLrbValid

  // 每个MSHR各自拥有一个16拍LFB。
  val mshrValid = RegInit(VecInit(Seq.fill(l2MshrEntries)(false.B)))
  val mshrSource = RegInit(VecInit(Seq.fill(l2MshrEntries)(false.B)))
  val mshrId = RegInit(VecInit(Seq.fill(l2MshrEntries)(0.U(1.W))))
  val mshrPrefetch = RegInit(VecInit(Seq.fill(l2MshrEntries)(false.B)))
  val mshrAddr = RegInit(VecInit(Seq.fill(l2MshrEntries)(0.U(XLEN.W))))
  val mshrWay = RegInit(VecInit(Seq.fill(l2MshrEntries)(0.U(l2WayBits.W))))
  val mshrWaitEb = RegInit(VecInit(Seq.fill(l2MshrEntries)(false.B)))
  val mshrReadIssued = RegInit(VecInit(Seq.fill(l2MshrEntries)(false.B)))
  val mshrRecvBeat = RegInit(VecInit(Seq.fill(l2MshrEntries)(0.U(l2BeatIdxBits.W))))
  val mshrSendBeat = RegInit(VecInit(Seq.fill(l2MshrEntries)(0.U(l2BeatIdxBits.W))))
  val mshrFillComplete = RegInit(VecInit(Seq.fill(l2MshrEntries)(false.B)))
  val mshrInstalled = RegInit(VecInit(Seq.fill(l2MshrEntries)(false.B)))
  val mshrResponseQueued = RegInit(VecInit(Seq.fill(l2MshrEntries)(false.B)))
  val mshrResponseAck = RegInit(VecInit(Seq.fill(l2MshrEntries)(false.B)))
  val lfbWords = RegInit(VecInit(Seq.fill(l2MshrEntries)(
    VecInit(Seq.fill(l2BurstBeats)(0.U(XLEN.W)))
  )))
  val lfbBeatValid = RegInit(VecInit(Seq.fill(l2MshrEntries)(
    VecInit(Seq.fill(l2BurstBeats)(false.B))
  )))

  // 1项EB在DDR写响应到达前持续拥有脏victim。
  val ebValid = RegInit(false.B)
  val ebIssued = RegInit(false.B)
  val ebAddr = RegInit(0.U(XLEN.W))
  val ebData = RegInit(0.U(l2LineBits.W))
  val ebHasMshr = RegInit(false.B)
  val ebMshr = RegInit(0.U(l2MshrIdBits.W))
  val ebForUncache = RegInit(false.B)

  // 总计3项STB：第0项为未来I侧exclusive预留，后2项供DCache。
  val stbCount = l2IStbEntries + l2DStbEntries
  val stbSlotBits = log2Ceil(stbCount)
  val stbValid = RegInit(VecInit(Seq.fill(stbCount)(false.B)))
  val stbAddr = RegInit(VecInit(Seq.fill(stbCount)(0.U(XLEN.W))))
  val stbData = RegInit(VecInit(Seq.fill(stbCount)(0.U(l2LineBits.W))))
  val stbKind = RegInit(VecInit(Seq.fill(stbCount)(L2WriteKind.putLine)))
  val stbId = RegInit(VecInit(Seq.fill(stbCount)(0.U(1.W))))
  val stbLookupIssued = RegInit(VecInit(Seq.fill(stbCount)(false.B)))
  val stbInstalled = RegInit(VecInit(Seq.fill(stbCount)(false.B)))
  val stbWriteIssued = RegInit(VecInit(Seq.fill(stbCount)(false.B)))
  val stbDdrDone = RegInit(VecInit(Seq.fill(stbCount)(false.B)))
  val stbDoneQueued = RegInit(VecInit(Seq.fill(stbCount)(false.B)))
  val stbDoneAck = RegInit(VecInit(Seq.fill(stbCount)(false.B)))

  val ucReadValid = RegInit(false.B)
  val ucReadIssued = RegInit(false.B)
  val ucReadSource = RegInit(false.B)
  val ucReadId = RegInit(0.U(1.W))
  val ucReadAddr = RegInit(0.U(XLEN.W))
  val ucReadSize = RegInit(0.U(3.W))
  val ucProbePending = RegInit(false.B)
  val ucProbeInFlight = RegInit(false.B)
  val ucCoherenceReady = RegInit(false.B)

  val ucWriteValid = RegInit(false.B)
  val ucWriteIssued = RegInit(false.B)
  val ucWriteBridgeDone = RegInit(false.B)
  val ucWriteDoneQueued = RegInit(false.B)
  val ucWriteId = RegInit(0.U(1.W))
  val ucWriteAddr = RegInit(0.U(XLEN.W))
  val ucWriteSize = RegInit(0.U(3.W))
  val ucWriteData = RegInit(0.U(l2LineBits.W))
  val ucWriteStrb = RegInit(0.U(l2BeatBytes.W))

  val dDoneValid = RegInit(false.B)
  val dDoneBits = Reg(new L2WriteDone(1))
  val dDoneIsStb = RegInit(false.B)
  val dDoneStbSlot = RegInit(0.U(stbSlotBits.W))
  val dDoneIsUncache = RegInit(false.B)
  io.dcache.write.done.valid := dDoneValid
  io.dcache.write.done.bits := dDoneBits

  val lookupBusy = Module(new L2LookupBusyScoreboard(queryPorts = 5 + stbCount))
  val iBusyQueryPort = 0
  val dBusyQueryPort = 1
  val ucBusyQueryPort = 2
  val prefetchBusyQueryPort = 3
  val dWriteBusyQueryPort = 4
  val stbBusyQueryBase = 5
  val lookupToken = Wire(new L2LookupToken)
  lookupToken := 0.U.asTypeOf(new L2LookupToken)
  val lookupTokenD1 = RegEnable(lookupToken, 0.U.asTypeOf(new L2LookupToken), array.io.read.req.fire)
  val lookupTokenD1Valid = RegNext(array.io.read.req.fire, false.B)
  val lookupTokenD2 = RegEnable(lookupTokenD1, 0.U.asTypeOf(new L2LookupToken), lookupTokenD1Valid)
  val lookupTokenD2Valid = RegNext(lookupTokenD1Valid, false.B)

  val cacheLineWrite = dWriteEntryBits.kind === L2WriteKind.putLine ||
    dWriteEntryBits.kind === L2WriteKind.cleanLine
  val uncacheWrite = dWriteEntryBits.kind === L2WriteKind.uncache

  // Observe STB/MSHR/EB state beside the Array read. The wide CAM and data mux
  // terminate at resultQueue instead of extending from its registered head.
  val lookupHazard = Module(new L2LookupHazard)
  lookupHazard.io.addr := lookupTokenD2.addr
  lookupHazard.io.isStb := lookupTokenD2.isStb
  lookupHazard.io.isUncacheProbe := lookupTokenD2.isUncacheProbe
  lookupHazard.io.isPrefetch := lookupTokenD2.isPrefetch
  for (i <- 0 until stbCount) {
    lookupHazard.io.stbValid(i) := stbValid(i) && !stbInstalled(i)
    lookupHazard.io.stbAddr(i) := stbAddr(i)
    lookupHazard.io.stbData(i) := stbData(i)
  }
  lookupHazard.io.pendingWriteValid := dWriteEntryValid && cacheLineWrite
  lookupHazard.io.pendingWriteAddr := dWriteEntryBits.addr
  lookupHazard.io.pendingWriteData := dWriteEntryBits.data
  for (i <- 0 until l2MshrEntries) {
    lookupHazard.io.mshrValid(i) := mshrValid(i)
    lookupHazard.io.mshrAddr(i) := mshrAddr(i)
  }
  lookupHazard.io.ebValid := ebValid
  lookupHazard.io.ebAddr := ebAddr

  val resultQueue = Module(new L2ResultQueue(4))
  val arrayValidMask = VecInit(array.io.read.resp.bits.ways.map(_.valid)).asUInt
  val arrayHitMask = VecInit(array.io.read.resp.bits.ways.map(way =>
    way.valid && way.tag === lookupTokenD2.addr(XLEN - 1, l2IdxBits + l2BlockOffBits)
  )).asUInt
  val arrayHit = arrayHitMask.orR
  val arrayHitWay = OHToUInt(arrayHitMask)
  replacer.io.lookup.set := array.io.read.resp.bits.set
  replacer.io.lookup.validMask := arrayValidMask
  val arrayChosenWay = Mux(arrayHit, arrayHitWay, replacer.io.victim)
  val arrayChosenOH = UIntToOH(arrayChosenWay, l2Ways)
  val arrayChosenData = Mux1H(arrayChosenOH, array.io.read.resp.bits.ways.map(_.data))

  resultQueue.io.enq.valid := array.io.read.resp.valid && lookupTokenD2Valid
  resultQueue.io.enq.bits.token := lookupTokenD2
  resultQueue.io.enq.bits.hit := arrayHit
  resultQueue.io.enq.bits.way := arrayChosenWay
  resultQueue.io.enq.bits.oldValid := Mux1H(arrayChosenOH, array.io.read.resp.bits.ways.map(_.valid))
  resultQueue.io.enq.bits.oldDirty := Mux1H(arrayChosenOH, array.io.read.resp.bits.ways.map(_.dirty))
  resultQueue.io.enq.bits.oldTag := Mux1H(arrayChosenOH, array.io.read.resp.bits.ways.map(_.tag))
  resultQueue.io.enq.bits.oldData := Mux(lookupHazard.io.stbForwarded,
    lookupHazard.io.stbForwardData, arrayChosenData)
  resultQueue.io.enq.bits.stbMatch := lookupHazard.io.stbMatch
  resultQueue.io.enq.bits.stbForwarded := lookupHazard.io.stbForwarded
  resultQueue.io.enq.bits.mshrSetConflict := lookupHazard.io.mshrSetConflict
  resultQueue.io.enq.bits.ebBlockConflict := lookupHazard.io.ebBlockConflict

  val lookupOccupancy = resultQueue.io.count +
    lookupTokenD1Valid.asUInt + lookupTokenD2Valid.asUInt
  val effectiveLookupOccupancy =
    lookupOccupancy + prefetchGrantValid.asUInt
  val lookupHasCredit = effectiveLookupOccupancy < 4.U && lookupBusy.io.hasFree

  val mshrFreeMask = VecInit(mshrValid.map(v => !v)).asUInt
  val hasFreeMshr = mshrFreeMask.orR
  val freeMshr = PriorityEncoder(mshrFreeMask)
  val freeMshrCount = PopCount(mshrFreeMask)
  val hasPurePrefetchMshr = mshrPrefetch.asUInt.orR

  val demandFillVec = VecInit((0 until l2MshrEntries).map(i =>
    mshrValid(i) && !mshrPrefetch(i) && mshrFillComplete(i) && !mshrInstalled(i)
  ))
  val prefetchFillVec = VecInit((0 until l2MshrEntries).map(i =>
    mshrValid(i) && mshrPrefetch(i) && mshrFillComplete(i) && !mshrInstalled(i)
  ))
  val hasDemandFillInstall = demandFillVec.asUInt.orR
  val hasPrefetchFillInstall = prefetchFillVec.asUInt.orR
  val hasFillInstall = hasDemandFillInstall || hasPrefetchFillInstall
  val fillInstallMshr = Mux(hasDemandFillInstall,
    PriorityEncoder(demandFillVec), PriorityEncoder(prefetchFillVec))

  val head = resultQueue.io.deq.bits
  val headBlock = head.token.addr(XLEN - 1, l2BlockOffBits)
  val latePendingWriteMatch = dWriteEntryValid && cacheLineWrite &&
    dWriteEntryBits.addr(XLEN - 1, l2BlockOffBits) === headBlock
  val headHasStbForward = head.stbForwarded || latePendingWriteMatch
  val headNeedsEb = !head.token.isUncacheProbe && !head.token.isPrefetch &&
    !head.hit && head.oldValid && head.oldDirty

  val headPortReady = Mux(head.token.source === L2PortSource.dcache, dLrbReady, iLrbReady || iKilled)
  val headReadCanProcess = Mux(
    headHasStbForward || head.hit,
    headPortReady,
    hasFreeMshr && (!headNeedsEb || !ebValid)
  )
  val headStbCanProcess = (!headNeedsEb || !ebValid)
  val cancelledLookupResult = resultQueue.io.deq.valid && head.token.cancelled &&
    !hasFillInstall
  val ucProbeResultCanProcess = resultQueue.io.deq.valid && head.token.isUncacheProbe &&
    !hasFillInstall && (!head.hit || !head.oldDirty || !ebValid)
  val demandResultCanProcess = resultQueue.io.deq.valid &&
    !head.token.isUncacheProbe && !head.token.isPrefetch &&
    !head.token.cancelled && !hasFillInstall &&
    Mux(head.token.isStb, headStbCanProcess, headReadCanProcess)
  val prefetchDrainPresent = ucReadValid || ucWriteValid || ucProbePending ||
    (iReqEntryValid && iReqEntryBits.uncache) ||
    (dReqEntryValid && dReqEntryBits.uncache) ||
    (dWriteEntryValid && dWriteEntryBits.kind === L2WriteKind.uncache)
  val staleIPrefetchResult = head.token.isPrefetch &&
    head.token.source === L2PortSource.icache &&
    head.token.prefetchEpoch =/= iPrefetchEpoch
  val prefetchResultCanAllocate = resultQueue.io.deq.valid &&
    head.token.isPrefetch && !head.hit && !head.oldDirty &&
    !head.stbMatch && !latePendingWriteMatch && !hasFillInstall &&
    freeMshrCount >= 3.U && !hasPurePrefetchMshr &&
    !head.mshrSetConflict && !head.ebBlockConflict && !prefetchDrainPresent &&
    !staleIPrefetchResult
  // A speculative result is lossy and never blocks a later demand result.
  val prefetchResultCanProcess = resultQueue.io.deq.valid &&
    head.token.isPrefetch
  val resultCanProcess = demandResultCanProcess || prefetchResultCanProcess ||
    ucProbeResultCanProcess || cancelledLookupResult
  lookupBusy.io.release.valid := resultCanProcess
  lookupBusy.io.release.bits := head.token.busySlot
  resultQueue.io.deq.ready := resultCanProcess

  val resultReadResponse = demandResultCanProcess && !head.token.isStb &&
    (headHasStbForward || head.hit)
  val resultToI = resultReadResponse && head.token.source === L2PortSource.icache && !iKilled && !iCancelPending
  val resultToD = resultReadResponse && head.token.source === L2PortSource.dcache
  val killedIResult = demandResultCanProcess && !head.token.isStb &&
    head.token.source === L2PortSource.icache && (iKilled || iCancelPending)
  val resultResponseData = Mux(latePendingWriteMatch, dWriteEntryBits.data, head.oldData)

  // Array只有一个整行写口：fill安装优先，其次处理lookup结果。
  array.io.write.valid := false.B
  array.io.write.bits := 0.U.asTypeOf(new L2ArrayWriteReq)
  replacer.io.touch.valid := false.B
  replacer.io.touch.bits := 0.U.asTypeOf(new L2ReplacerTouch)

  when(hasFillInstall) {
    array.io.write.valid := true.B
    array.io.write.bits.set := mshrAddr(fillInstallMshr)(l2BlockOffBits + l2IdxBits - 1, l2BlockOffBits)
    array.io.write.bits.way := mshrWay(fillInstallMshr)
    array.io.write.bits.valid := true.B
    array.io.write.bits.dirty := false.B
    array.io.write.bits.tag := mshrAddr(fillInstallMshr)(XLEN - 1, l2BlockOffBits + l2IdxBits)
    array.io.write.bits.data := lfbWords(fillInstallMshr).asUInt
    array.io.write.bits.dataWen := true.B
    when(!mshrPrefetch(fillInstallMshr)) {
      replacer.io.touch.valid := true.B
      replacer.io.touch.bits.set := array.io.write.bits.set
      replacer.io.touch.bits.way := array.io.write.bits.way
    }
  }.elsewhen(demandResultCanProcess && head.token.isStb) {
    val slot = head.token.stbSlot
    array.io.write.valid := true.B
    array.io.write.bits.set := stbAddr(slot)(l2BlockOffBits + l2IdxBits - 1, l2BlockOffBits)
    array.io.write.bits.way := head.way
    array.io.write.bits.valid := true.B
    array.io.write.bits.dirty := stbKind(slot) === L2WriteKind.putLine
    array.io.write.bits.tag := stbAddr(slot)(XLEN - 1, l2BlockOffBits + l2IdxBits)
    array.io.write.bits.data := stbData(slot)
    array.io.write.bits.dataWen := true.B
    replacer.io.touch.valid := true.B
    replacer.io.touch.bits.set := array.io.write.bits.set
    replacer.io.touch.bits.way := head.way
  }.elsewhen(ucProbeResultCanProcess && head.hit) {
    array.io.write.valid := true.B
    array.io.write.bits.set := head.token.addr(
      l2BlockOffBits + l2IdxBits - 1, l2BlockOffBits)
    array.io.write.bits.way := head.way
    array.io.write.bits.valid := false.B
    array.io.write.bits.dirty := false.B
    array.io.write.bits.tag := head.oldTag
    array.io.write.bits.data := 0.U
    array.io.write.bits.dataWen := false.B
  }.elsewhen(demandResultCanProcess && !head.token.isStb &&
      !head.hit && !headHasStbForward) {
    // Demand miss allocation invalidates its victim immediately.  A prefetch
    // keeps a clean victim intact until the completed fill installs atomically.
    array.io.write.valid := true.B
    array.io.write.bits.set := head.token.addr(l2BlockOffBits + l2IdxBits - 1, l2BlockOffBits)
    array.io.write.bits.way := head.way
    array.io.write.bits.valid := false.B
    array.io.write.bits.dirty := false.B
    array.io.write.bits.tag := head.oldTag
    array.io.write.bits.data := 0.U
    array.io.write.bits.dataWen := false.B
  }.elsewhen(resultReadResponse && !headHasStbForward) {
    replacer.io.touch.valid := true.B
    replacer.io.touch.bits.set := head.token.addr(l2BlockOffBits + l2IdxBits - 1, l2BlockOffBits)
    replacer.io.touch.bits.way := head.way
  }

  when(hasFillInstall) {
    mshrInstalled(fillInstallMshr) := true.B
  }

  when(resultCanProcess) {
    when(head.token.isPrefetch) {
      when(prefetchResultCanAllocate) {
        val slot = freeMshr
        mshrValid(slot) := true.B
        mshrSource(slot) := head.token.source
        mshrId(slot) := 0.U
        mshrPrefetch(slot) := true.B
        mshrAddr(slot) := Cat(head.token.addr(XLEN - 1, l2BlockOffBits),
          0.U(l2BlockOffBits.W))
        mshrWay(slot) := head.way
        mshrWaitEb(slot) := false.B
        mshrReadIssued(slot) := false.B
        mshrRecvBeat(slot) := 0.U
        mshrSendBeat(slot) := 0.U
        mshrFillComplete(slot) := false.B
        mshrInstalled(slot) := false.B
        mshrResponseQueued(slot) := false.B
        mshrResponseAck(slot) := false.B
        for (beat <- 0 until l2BurstBeats) {
          lfbBeatValid(slot)(beat) := false.B
        }
      }
    }.elsewhen(head.token.cancelled) {
      // The request reached only the registered lookup boundary. Drop it
      // before allocating an MSHR or issuing DDR traffic.
      iActive := false.B
      iKilled := false.B
      iActiveHasMshr := false.B
    }.elsewhen(head.token.isUncacheProbe) {
      ucProbeInFlight := false.B
      when(!head.hit) {
        ucCoherenceReady := true.B
      }.elsewhen(!head.oldDirty) {
        ucCoherenceReady := true.B
      }.otherwise {
        ebValid := true.B
        ebIssued := false.B
        ebAddr := Cat(head.oldTag,
          head.token.addr(l2BlockOffBits + l2IdxBits - 1, l2BlockOffBits),
          0.U(l2BlockOffBits.W))
        ebData := head.oldData
        ebHasMshr := false.B
        ebForUncache := true.B
        ucCoherenceReady := false.B
      }
    }.elsewhen(head.token.isStb) {
      val slot = head.token.stbSlot
      stbInstalled(slot) := true.B
      when(headNeedsEb) {
        ebValid := true.B
        ebIssued := false.B
        ebAddr := Cat(head.oldTag,
          head.token.addr(l2BlockOffBits + l2IdxBits - 1, l2BlockOffBits),
          0.U(l2BlockOffBits.W))
        ebData := head.oldData
        ebHasMshr := false.B
        ebForUncache := false.B
      }
      when(stbKind(slot) === L2WriteKind.putLine) {
        stbValid(slot) := false.B
      }
    }.elsewhen(!head.hit && !headHasStbForward) {
      val slot = freeMshr
      mshrValid(slot) := true.B
      mshrPrefetch(slot) := false.B
      mshrSource(slot) := head.token.source
      mshrId(slot) := head.token.id
      mshrAddr(slot) := Cat(head.token.addr(XLEN - 1, l2BlockOffBits), 0.U(l2BlockOffBits.W))
      mshrWay(slot) := head.way
      mshrWaitEb(slot) := headNeedsEb
      mshrReadIssued(slot) := false.B
      mshrRecvBeat(slot) := 0.U
      mshrSendBeat(slot) := 0.U
      mshrFillComplete(slot) := false.B
      mshrInstalled(slot) := false.B
      mshrResponseQueued(slot) := false.B
      mshrResponseAck(slot) := false.B
      when(head.token.source === L2PortSource.icache) {
        iActiveMshr := slot
        iActiveHasMshr := true.B
      }
      for (beat <- 0 until l2BurstBeats) {
        lfbBeatValid(slot)(beat) := false.B
      }
      when(headNeedsEb) {
        ebValid := true.B
        ebIssued := false.B
        ebAddr := Cat(head.oldTag,
          head.token.addr(l2BlockOffBits + l2IdxBits - 1, l2BlockOffBits),
          0.U(l2BlockOffBits.W))
        ebData := head.oldData
        ebHasMshr := true.B
        ebMshr := slot
        ebForUncache := false.B
      }
    }
  }

  // Killed lookup hits/forwards have no lower transaction to drain.
  when(killedIResult && (head.hit || headHasStbForward)) {
    iActive := false.B
    iKilled := false.B
    iActiveHasMshr := false.B
  }

  // STB/lookup/active miss CAM在接收请求前完成。
  val iReqSet = iReqEntryBits.addr(l2BlockOffBits + l2IdxBits - 1, l2BlockOffBits)
  val dReqSet = dReqEntryBits.addr(l2BlockOffBits + l2IdxBits - 1, l2BlockOffBits)
  lookupBusy.io.querySet(iBusyQueryPort) := iReqSet
  lookupBusy.io.querySet(dBusyQueryPort) := dReqSet
  val iReqBlock = iReqEntryBits.addr(XLEN - 1, l2BlockOffBits)
  val dReqBlock = dReqEntryBits.addr(XLEN - 1, l2BlockOffBits)
  val iMshrConflict = VecInit((0 until l2MshrEntries).map(i => mshrValid(i) &&
    mshrAddr(i)(XLEN - 1, l2BlockOffBits) === iReqBlock)).asUInt.orR
  val dMshrConflict = VecInit((0 until l2MshrEntries).map(i => mshrValid(i) &&
    mshrAddr(i)(XLEN - 1, l2BlockOffBits) === dReqBlock)).asUInt.orR
  val iPrefetchMatchVec = VecInit((0 until l2MshrEntries).map(i =>
    mshrValid(i) && mshrPrefetch(i) &&
      mshrAddr(i)(XLEN - 1, l2BlockOffBits) === iReqBlock))
  val dPrefetchMatchVec = VecInit((0 until l2MshrEntries).map(i =>
    mshrValid(i) && mshrPrefetch(i) &&
      mshrAddr(i)(XLEN - 1, l2BlockOffBits) === dReqBlock))
  val iPrefetchMatch = iPrefetchMatchVec.asUInt.orR
  val dPrefetchMatch = dPrefetchMatchVec.asUInt.orR
  val iMshrSetConflict = VecInit((0 until l2MshrEntries).map(i => mshrValid(i) &&
    mshrAddr(i)(l2BlockOffBits + l2IdxBits - 1, l2BlockOffBits) === iReqSet)).asUInt.orR
  val dMshrSetConflict = VecInit((0 until l2MshrEntries).map(i => mshrValid(i) &&
    mshrAddr(i)(l2BlockOffBits + l2IdxBits - 1, l2BlockOffBits) === dReqSet)).asUInt.orR
  val iEbConflict = ebValid && ebAddr(XLEN - 1, l2BlockOffBits) === iReqBlock
  val dEbConflict = ebValid && ebAddr(XLEN - 1, l2BlockOffBits) === dReqBlock

  val pendingCacheableBoundary =
    (iReqEntryValid && !iReqEntryBits.uncache) ||
    (dReqEntryValid && !dReqEntryBits.uncache) ||
    (dWriteEntryValid && cacheLineWrite)
  val allCacheWorkDrained = !mshrValid.asUInt.orR && !stbValid.asUInt.orR &&
    !ebValid && !lookupBusy.io.anyBusy && resultQueue.io.count === 0.U &&
    !lookupTokenD1Valid && !lookupTokenD2Valid && !iLrbValid && !dLrbValid &&
    !ucReadValid && !ucWriteValid && !dDoneValid && !pendingCacheableBoundary
  // uncache一旦等待排空，就停止接收新的cacheable请求，避免被持续流量饿死。
  val uncacheBusy = ucReadValid || ucWriteValid
  val iUncacheReadWantsDrain = iReqEntryValid && iReqEntryBits.uncache
  val dUncacheReadWantsDrain = dReqEntryValid && dReqEntryBits.uncache
  val dUncacheWriteWantsDrain = dWriteEntryValid && uncacheWrite
  val uncacheWantsDrain = iUncacheReadWantsDrain || dUncacheReadWantsDrain ||
    dUncacheWriteWantsDrain
  val uncacheAdmissionLocked = uncacheBusy || uncacheWantsDrain

  val prefetchCandidateValid = WireDefault(false.B)
  val prefetchCandidateBits = WireDefault(
    0.U.asTypeOf(new L2PrefetchCandidate))
  val prefetchCandidateReady = WireDefault(false.B)
  if (l2PrefetchMode != L2PrefetchMode.Disabled) {
    val prefetch = Module(new L2PrefetchHub)
    prefetch.io.iTrain.valid := iPrefetchTrainValid
    prefetch.io.iTrain.bits := iPrefetchTrainAddr
    prefetch.io.dTrain.valid := dPrefetchTrainValid
    prefetch.io.dTrain.bits := dPrefetchTrainAddr
    prefetch.io.iClear := uncacheAdmissionLocked || iCancelPending
    prefetch.io.dClear := uncacheAdmissionLocked
    prefetch.io.queueClear := uncacheAdmissionLocked || iCancelPending
    prefetchCandidateValid := prefetch.io.candidate.valid
    prefetchCandidateBits := prefetch.io.candidate.bits
    prefetch.io.candidate.ready := prefetchCandidateReady
  }
  // Two registered boundaries keep predictor and conflict checks away from
  // the Array request mux.  All expensive CAM/reduction work ends at grant D.
  val prefetchIssueValid = RegInit(false.B)
  val prefetchIssueBits = RegInit(0.U.asTypeOf(new L2PrefetchCandidate))
  val prefetchIssueEpoch = RegInit(0.U(L2PrefetchProtocol.EpochBits.W))
  val prefetchGrantBits = RegInit(0.U.asTypeOf(new L2PrefetchCandidate))
  val prefetchGrantEpoch = RegInit(0.U(L2PrefetchProtocol.EpochBits.W))
  prefetchCandidateReady := !prefetchIssueValid
  when(prefetchCandidateValid && prefetchCandidateReady) {
    prefetchIssueValid := true.B
    prefetchIssueBits := prefetchCandidateBits
    prefetchIssueEpoch := iPrefetchEpoch
  }

  // 同拍多个uncache意向采用固定优先级：D写 > I读 > D读。
  val iUncacheCanHandle = iUncacheReadWantsDrain && allCacheWorkDrained &&
    !dUncacheWriteWantsDrain
  val dUncacheCanHandle = dUncacheReadWantsDrain && allCacheWorkDrained &&
    !dUncacheWriteWantsDrain && !iUncacheReadWantsDrain

  val iCacheEligible = !iReqEntryBits.uncache && lookupHasCredit &&
    !uncacheBusy && !lookupBusy.io.queryBusy(iBusyQueryPort) && !iMshrConflict && !iMshrSetConflict && !iEbConflict &&
    (!array.io.write.valid || array.io.write.bits.set =/= iReqSet)
  val dCacheEligible = !dReqEntryBits.uncache && lookupHasCredit &&
    !uncacheBusy && !lookupBusy.io.queryBusy(dBusyQueryPort) && !dMshrConflict && !dMshrSetConflict && !dEbConflict &&
    (!array.io.write.valid || array.io.write.bits.set =/= dReqSet)
  val iReadCanHandle = iReqEntryValid && !iActive && Mux(iReqEntryBits.uncache,
    iUncacheCanHandle, iCacheEligible) && !iCancelPending
  val dReadCanHandle = dReqEntryValid && Mux(dReqEntryBits.uncache,
    dUncacheCanHandle, dCacheEligible)

  val preferD = RegInit(false.B)
  val iPromoteEligible = iReqEntryValid && !iReqEntryBits.uncache &&
    !iActive && !iCancelPending && iPrefetchMatch
  val dPromoteEligible = dReqEntryValid && !dReqEntryBits.uncache &&
    dPrefetchMatch
  val chooseIPromotion = iPromoteEligible &&
    (!dPromoteEligible || !preferD)
  val chooseDPromotion = dPromoteEligible &&
    (!iPromoteEligible || preferD)
  val choosePromotion = chooseIPromotion || chooseDPromotion
  val promoteMshr = Mux(chooseDPromotion,
    PriorityEncoder(dPrefetchMatchVec), PriorityEncoder(iPrefetchMatchVec))
  val promoteMask = UIntToOH(promoteMshr, l2MshrEntries) &
    Fill(l2MshrEntries, choosePromotion)

  for (slot <- 0 until stbCount) {
    lookupBusy.io.querySet(stbBusyQueryBase + slot) :=
      stbAddr(slot)(l2BlockOffBits + l2IdxBits - 1, l2BlockOffBits)
  }
  val stbLookupVec = VecInit((0 until stbCount).map(i => stbValid(i) &&
    !stbLookupIssued(i) && !stbInstalled(i) &&
    !lookupBusy.io.queryBusy(stbBusyQueryBase + i) &&
    !VecInit((0 until l2MshrEntries).map(m => mshrValid(m) &&
      mshrAddr(m)(l2BlockOffBits + l2IdxBits - 1, l2BlockOffBits) ===
        stbAddr(i)(l2BlockOffBits + l2IdxBits - 1, l2BlockOffBits))).asUInt.orR &&
    (!array.io.write.valid || array.io.write.bits.set =/=
      stbAddr(i)(l2BlockOffBits + l2IdxBits - 1, l2BlockOffBits))
  ))
  val hasStbLookup = stbLookupVec.asUInt.orR && lookupHasCredit
  val stbLookupSlot = PriorityEncoder(stbLookupVec)
  val preferStb = RegInit(false.B)
  val readWantsLookup = iReadCanHandle || dReadCanHandle
  val chooseStbLookup = !choosePromotion && hasStbLookup &&
    (!readWantsLookup || preferStb)
  val chooseI = !choosePromotion && !chooseStbLookup && iReadCanHandle &&
    (!dReadCanHandle || !preferD)
  val chooseD = !choosePromotion && !chooseStbLookup && dReadCanHandle &&
    (!iReadCanHandle || preferD)
  val chosenUncache = (chooseI && iReqEntryBits.uncache) ||
    (chooseD && dReqEntryBits.uncache)
  val ucProbeOwnerAddr = Mux(ucReadValid, ucReadAddr, ucWriteAddr)
  val ucProbeAddr = Cat(ucProbeOwnerAddr(XLEN - 1, l2BlockOffBits),
    0.U(l2BlockOffBits.W))
  val ucProbeSet = ucProbeAddr(l2BlockOffBits + l2IdxBits - 1, l2BlockOffBits)
  lookupBusy.io.querySet(ucBusyQueryPort) := ucProbeSet
  val chooseUcProbe = ucProbePending && !ucProbeInFlight && lookupHasCredit &&
    !lookupBusy.io.queryBusy(ucBusyQueryPort) && !array.io.write.valid

  val otherLookupSelected =
    (((chooseI || chooseD) && !chosenUncache) ||
      chooseStbLookup || chooseUcProbe)
  val otherLookupSet = Mux(chooseUcProbe, ucProbeSet, Mux(chooseStbLookup,
    stbAddr(stbLookupSlot)(
      l2BlockOffBits + l2IdxBits - 1, l2BlockOffBits),
    Mux(chooseD, dReqSet, iReqSet)))

  val prefetchIssueBlock = prefetchIssueBits.addr(XLEN - 1, l2BlockOffBits)
  val prefetchIssueSet = prefetchIssueBits.addr(
    l2BlockOffBits + l2IdxBits - 1, l2BlockOffBits)
  lookupBusy.io.querySet(prefetchBusyQueryPort) := prefetchIssueSet
  val prefetchIssueMshrSetConflict = VecInit((0 until l2MshrEntries).map(i =>
    mshrValid(i) && mshrAddr(i)(
      l2BlockOffBits + l2IdxBits - 1, l2BlockOffBits) === prefetchIssueSet
  )).asUInt.orR
  val prefetchIssueStbSetConflict = VecInit((0 until stbCount).map(i =>
    stbValid(i) && stbAddr(i)(
      l2BlockOffBits + l2IdxBits - 1, l2BlockOffBits) === prefetchIssueSet
  )).asUInt.orR
  val prefetchIssueEbConflict = ebValid &&
    ebAddr(XLEN - 1, l2BlockOffBits) === prefetchIssueBlock
  val prefetchCheckBlocked = uncacheAdmissionLocked ||
    (iCancelPending && prefetchIssueBits.isInstruction) ||
    hasPurePrefetchMshr || freeMshrCount < 3.U ||
    lookupOccupancy >= 3.U || lookupBusy.io.queryBusy(prefetchBusyQueryPort) ||
    prefetchIssueMshrSetConflict || prefetchIssueStbSetConflict ||
    prefetchIssueEbConflict ||
    (otherLookupSelected && otherLookupSet === prefetchIssueSet) ||
    (array.io.write.valid && array.io.write.bits.set === prefetchIssueSet)

  when(prefetchIssueValid && !prefetchGrantValid) {
    prefetchIssueValid := false.B
    when(!prefetchCheckBlocked) {
      prefetchGrantValid := true.B
      prefetchGrantBits := prefetchIssueBits
      prefetchGrantEpoch := prefetchIssueEpoch
    }
  }
  when(uncacheAdmissionLocked ||
      (iCancelPending && prefetchIssueBits.isInstruction)) {
    prefetchIssueValid := false.B
  }
  when(uncacheAdmissionLocked ||
      (iCancelPending && prefetchGrantBits.isInstruction)) {
    prefetchGrantValid := false.B
  }

  val prefetchSet = prefetchGrantBits.addr(
    l2BlockOffBits + l2IdxBits - 1, l2BlockOffBits)
  // The final Array-select stage contains only registered candidate state,
  // fixed priority, and the mandatory single-set Array collision guard.
  val prefetchGrantSuperseded =
    (otherLookupSelected && otherLookupSet === prefetchSet) ||
    (array.io.write.valid && array.io.write.bits.set === prefetchSet)
  val prefetchGrantCanIssue =
    !mshrValid.asUInt.orR && lookupOccupancy === 0.U
  val choosePrefetch = prefetchGrantValid && prefetchGrantCanIssue && !prefetchGrantSuperseded &&
    !chooseUcProbe && !chooseStbLookup && !chooseI && !chooseD
  when(prefetchGrantValid &&
      (prefetchGrantSuperseded ||
        (choosePrefetch && array.io.read.req.ready))) {
    prefetchGrantValid := false.B
  }

  array.io.read.req.valid := ((chooseI || chooseD) && !chosenUncache) || chooseStbLookup ||
    chooseUcProbe || choosePrefetch
  array.io.read.req.bits.set := Mux(chooseUcProbe, ucProbeSet, Mux(chooseStbLookup,
    stbAddr(stbLookupSlot)(l2BlockOffBits + l2IdxBits - 1, l2BlockOffBits),
    Mux(choosePrefetch, prefetchSet, Mux(chooseD, dReqSet, iReqSet))))
  lookupBusy.io.allocate.valid := array.io.read.req.fire
  lookupBusy.io.allocate.bits := array.io.read.req.bits.set
  lookupToken.source := Mux(chooseUcProbe,
    Mux(ucReadValid, ucReadSource, L2PortSource.dcache),
    Mux(choosePrefetch, !prefetchGrantBits.isInstruction,
      Mux(chooseD, L2PortSource.dcache, L2PortSource.icache)))
  lookupToken.id := Mux(chooseUcProbe, Mux(ucReadValid, ucReadId, ucWriteId),
    Mux(choosePrefetch, 0.U, Mux(chooseD, dReqEntryBits.id, iReqEntryBits.id)))
  lookupToken.addr := Mux(chooseUcProbe, ucProbeAddr, Mux(chooseStbLookup,
    stbAddr(stbLookupSlot), Mux(choosePrefetch, prefetchGrantBits.addr,
      Mux(chooseD, dReqEntryBits.addr, iReqEntryBits.addr))))
  lookupToken.isStb := chooseStbLookup
  lookupToken.isUncacheProbe := chooseUcProbe
  lookupToken.isPrefetch := choosePrefetch
  lookupToken.cancelled := chooseI && io.icache.cancel
  lookupToken.prefetchEpoch := Mux(choosePrefetch &&
    prefetchGrantBits.isInstruction, prefetchGrantEpoch, 0.U)
  lookupToken.stbSlot := stbLookupSlot
  lookupToken.busySlot := lookupBusy.io.allocateSlot

  // External ready depends only on registered boundary-level state.
  io.icache.req.ready := array.io.initDone && !iReqEntryValid && !uncacheAdmissionLocked
  io.dcache.read.req.ready := array.io.initDone && !dReqEntryValid && !uncacheAdmissionLocked
  val iInternalAccept = chooseIPromotion ||
    (chooseI && Mux(chosenUncache, true.B, array.io.read.req.fire))
  val dInternalAccept = chooseDPromotion ||
    (chooseD && Mux(chosenUncache, true.B, array.io.read.req.fire))
  when(io.icache.req.fire) {
    iReqEntryBits := io.icache.req.bits
    iReqEntryValid := true.B
  }
  when(io.dcache.read.req.fire) {
    dReqEntryBits := io.dcache.read.req.bits
    dReqEntryValid := true.B
  }
  when(iInternalAccept) {
    iReqEntryValid := false.B
    iActive := true.B
    iKilled := false.B
    iActiveHasMshr := false.B
  }
  when(dInternalAccept) {
    dReqEntryValid := false.B
  }
  when(choosePromotion) {
    mshrPrefetch(promoteMshr) := false.B
    mshrSource(promoteMshr) := Mux(chooseDPromotion,
      L2PortSource.dcache, L2PortSource.icache)
    mshrId(promoteMshr) := Mux(chooseDPromotion,
      dReqEntryBits.id, iReqEntryBits.id)
    mshrSendBeat(promoteMshr) := 0.U
    mshrResponseQueued(promoteMshr) := false.B
    mshrResponseAck(promoteMshr) := false.B
    when(chooseIPromotion) {
      iActive := true.B
      iKilled := false.B
      iActiveMshr := promoteMshr
      iActiveHasMshr := true.B
    }
  }

  // Raw cancel is sampled into local state. It never drives Array/CAM control
  // or native ready/valid combinationally.
  when(iCancelPending) {
    iCancelPending := false.B
    when(iCancelClearEntry) { iReqEntryValid := false.B }
    when(iCancelKillActive) { iKilled := true.B }
    when(killedIResult && (head.hit || headHasStbForward)) {
      iActive := false.B
      iKilled := false.B
      iActiveHasMshr := false.B
    }
    when(iLrbValid) {
      when(iLrbFromUncache) {
        ucReadValid := false.B
        iActive := false.B
        iKilled := false.B
        iActiveHasMshr := false.B
      }.elsewhen(!iLrbFromMshr) {
        iActive := false.B
        iKilled := false.B
        iActiveHasMshr := false.B
      }.elsewhen(iLrbBits.last) {
        mshrResponseAck(iLrbMshr) := true.B
      }
    }
    iLrbValid := false.B
  }
  when(io.icache.cancel) {
    iCancelPending := true.B
    iCancelClearEntry := iReqEntryValid && !iInternalAccept
    iCancelKillActive := iActive || iInternalAccept
  }
  when(iCancelPulse) {
    iPrefetchEpoch := iPrefetchEpoch + 1.U
  }

  when(array.io.read.req.fire) {
    preferStb := !chooseStbLookup
    when(chooseStbLookup) {
      stbLookupIssued(stbLookupSlot) := true.B
    }
    when(chooseUcProbe) {
      ucProbePending := false.B
      ucProbeInFlight := true.B
    }
  }
  when(iInternalAccept || dInternalAccept) {
    preferD := iInternalAccept
  }
  when(iInternalAccept && iReqEntryBits.uncache) {
    ucReadValid := true.B
    ucReadIssued := false.B
    ucReadSource := L2PortSource.icache
    ucReadId := iReqEntryBits.id
    ucReadAddr := iReqEntryBits.addr
    ucReadSize := iReqEntryBits.size
    ucProbePending := true.B
    ucProbeInFlight := false.B
    ucCoherenceReady := false.B
  }.elsewhen(dInternalAccept && dReqEntryBits.uncache) {
    ucReadValid := true.B
    ucReadIssued := false.B
    ucReadSource := L2PortSource.dcache
    ucReadId := dReqEntryBits.id
    ucReadAddr := dReqEntryBits.addr
    ucReadSize := dReqEntryBits.size
    ucProbePending := true.B
    ucProbeInFlight := false.B
    ucCoherenceReady := false.B
  }

  // DCache整行写进入2项D-STB；uncache写只在所有cacheable工作排空后接收。
  val dStbFreeVec = VecInit((0 until stbCount).map(i =>
    if (i < l2IStbEntries) false.B else !stbValid(i)
  ))
  val hasDStbFree = dStbFreeVec.asUInt.orR
  val dStbFreeSlot = PriorityEncoder(dStbFreeVec)
  val writeReqBlock = dWriteEntryBits.addr(XLEN - 1, l2BlockOffBits)
  val writeReqSet = dWriteEntryBits.addr(l2BlockOffBits + l2IdxBits - 1, l2BlockOffBits)
  lookupBusy.io.querySet(dWriteBusyQueryPort) := writeReqSet
  val dWriteSetBusy = lookupBusy.io.queryBusy(dWriteBusyQueryPort)
  val sameStbWrite = VecInit((0 until stbCount).map(i => stbValid(i) &&
    stbAddr(i)(XLEN - 1, l2BlockOffBits) === writeReqBlock)).asUInt.orR
  val dWriteInternalAccept = dWriteEntryValid && Mux(cacheLineWrite,
    hasDStbFree && !sameStbWrite && !uncacheBusy && !dWriteSetBusy,
    dUncacheWriteWantsDrain && allCacheWorkDrained)
  io.dcache.write.req.ready := array.io.initDone && !dWriteEntryValid &&
    !uncacheAdmissionLocked
  when(io.dcache.write.req.fire) {
    dWriteEntryBits := io.dcache.write.req.bits
    dWriteEntryValid := true.B
  }
  when(dWriteInternalAccept) {
    dWriteEntryValid := false.B
  }

  when(dWriteInternalAccept && cacheLineWrite) {
    val slot = dStbFreeSlot
    stbValid(slot) := true.B
    stbAddr(slot) := Cat(dWriteEntryBits.addr(XLEN - 1, l2BlockOffBits),
      0.U(l2BlockOffBits.W))
    stbData(slot) := dWriteEntryBits.data
    stbKind(slot) := dWriteEntryBits.kind
    stbId(slot) := dWriteEntryBits.id
    stbLookupIssued(slot) := false.B
    stbInstalled(slot) := false.B
    stbWriteIssued(slot) := false.B
    stbDdrDone(slot) := false.B
    stbDoneQueued(slot) := false.B
    stbDoneAck(slot) := false.B
  }.elsewhen(dWriteInternalAccept && uncacheWrite) {
    ucWriteValid := true.B
    ucWriteIssued := false.B
    ucWriteBridgeDone := false.B
    ucWriteDoneQueued := false.B
    ucWriteId := dWriteEntryBits.id
    ucWriteAddr := dWriteEntryBits.addr
    ucWriteSize := dWriteEntryBits.size
    ucWriteData := dWriteEntryBits.data
    ucWriteStrb := dWriteEntryBits.strb
    ucProbePending := true.B
    ucProbeInFlight := false.B
    ucCoherenceReady := false.B
  }

  // Bridge写事务：EB优先，随后cleanLine，最后uncache写。
  val cleanWriteVec = VecInit((0 until stbCount).map(i => stbValid(i) &&
    stbKind(i) === L2WriteKind.cleanLine && !stbWriteIssued(i)))
  val hasCleanWrite = cleanWriteVec.asUInt.orR
  val cleanWriteSlot = PriorityEncoder(cleanWriteVec)
  val bridgeWriteQueue = Module(new Queue(new L2BridgeWriteCmd, 1,
    pipe = false, flow = false))
  bridge.io.client.write.req <> bridgeWriteQueue.io.deq
  bridgeWriteQueue.io.enq.valid := (ebValid && !ebIssued) || hasCleanWrite ||
    (ucWriteValid && !ucWriteIssued && ucCoherenceReady)
  bridgeWriteQueue.io.enq.bits := 0.U.asTypeOf(new L2BridgeWriteCmd)
  when(ebValid && !ebIssued) {
    bridgeWriteQueue.io.enq.bits.owner.source := L2BridgeWriteOwner.eviction
    bridgeWriteQueue.io.enq.bits.owner.slot := ebMshr
    bridgeWriteQueue.io.enq.bits.addr := ebAddr
    bridgeWriteQueue.io.enq.bits.isLine := true.B
    bridgeWriteQueue.io.enq.bits.size := 2.U
    bridgeWriteQueue.io.enq.bits.data := ebData
    bridgeWriteQueue.io.enq.bits.strb := Fill(l2BeatBytes, 1.U(1.W))
  }.elsewhen(hasCleanWrite) {
    bridgeWriteQueue.io.enq.bits.owner.source := L2BridgeWriteOwner.cleanLine
    bridgeWriteQueue.io.enq.bits.owner.slot := cleanWriteSlot
    bridgeWriteQueue.io.enq.bits.addr := stbAddr(cleanWriteSlot)
    bridgeWriteQueue.io.enq.bits.isLine := true.B
    bridgeWriteQueue.io.enq.bits.size := 2.U
    bridgeWriteQueue.io.enq.bits.data := stbData(cleanWriteSlot)
    bridgeWriteQueue.io.enq.bits.strb := Fill(l2BeatBytes, 1.U(1.W))
  }.otherwise {
    bridgeWriteQueue.io.enq.bits.owner.source := L2BridgeWriteOwner.uncache
    bridgeWriteQueue.io.enq.bits.owner.slot := 0.U
    bridgeWriteQueue.io.enq.bits.addr := ucWriteAddr
    bridgeWriteQueue.io.enq.bits.isLine := false.B
    bridgeWriteQueue.io.enq.bits.size := ucWriteSize
    bridgeWriteQueue.io.enq.bits.data := ucWriteData
    bridgeWriteQueue.io.enq.bits.strb := ucWriteStrb
  }
  when(bridgeWriteQueue.io.enq.fire) {
    when(bridgeWriteQueue.io.enq.bits.owner.source === L2BridgeWriteOwner.eviction) {
      ebIssued := true.B
    }.elsewhen(bridgeWriteQueue.io.enq.bits.owner.source === L2BridgeWriteOwner.cleanLine) {
      stbWriteIssued(bridgeWriteQueue.io.enq.bits.owner.slot) := true.B
    }.otherwise {
      ucWriteIssued := true.B
    }
  }

  bridge.io.client.write.done.ready := true.B
  when(bridge.io.client.write.done.fire) {
    when(bridge.io.client.write.done.bits.owner.source === L2BridgeWriteOwner.eviction) {
      when(ebForUncache) {
        ucCoherenceReady := true.B
      }
      ebValid := false.B
      ebIssued := false.B
      when(ebHasMshr) {
        mshrWaitEb(ebMshr) := false.B
      }
      ebHasMshr := false.B
      ebForUncache := false.B
    }.elsewhen(bridge.io.client.write.done.bits.owner.source === L2BridgeWriteOwner.cleanLine) {
      stbDdrDone(bridge.io.client.write.done.bits.owner.slot) := true.B
    }.otherwise {
      ucWriteBridgeDone := true.B
    }
  }

  // Bridge读事务：等待EB的MSHR不能发AR；uncache只会在排空后出现。
  val cancelPurePrefetch = VecInit((0 until l2MshrEntries).map { i =>
    val slotSet = mshrAddr(i)(
      l2BlockOffBits + l2IdxBits - 1, l2BlockOffBits)
    val boundarySetConflict =
      (iReqEntryValid && !iReqEntryBits.uncache && iReqSet === slotSet) ||
      (dReqEntryValid && !dReqEntryBits.uncache && dReqSet === slotSet) ||
      (dWriteEntryValid && cacheLineWrite &&
        dWriteEntryBits.addr(
          l2BlockOffBits + l2IdxBits - 1, l2BlockOffBits) === slotSet)
    mshrValid(i) && mshrPrefetch(i) && !mshrReadIssued(i) &&
      !promoteMask(i) &&
      (uncacheAdmissionLocked || boundarySetConflict ||
        (iCancelPending && mshrSource(i) === L2PortSource.icache))
  })
  for (i <- 0 until l2MshrEntries) {
    when(cancelPurePrefetch(i)) {
      mshrValid(i) := false.B
      mshrPrefetch(i) := false.B
    }
  }
  val demandMshrReadVec = VecInit((0 until l2MshrEntries).map(i =>
    mshrValid(i) && !mshrPrefetch(i) &&
      !mshrReadIssued(i) && !mshrWaitEb(i)))
  val prefetchMshrReadVec = VecInit((0 until l2MshrEntries).map(i =>
    mshrValid(i) && mshrPrefetch(i) && !cancelPurePrefetch(i) &&
      !mshrReadIssued(i) && !mshrWaitEb(i)))
  val hasDemandMshrRead = demandMshrReadVec.asUInt.orR
  val hasPrefetchMshrRead = prefetchMshrReadVec.asUInt.orR
  val demandReadMshr = PriorityEncoder(demandMshrReadVec)
  val prefetchReadMshr = PriorityEncoder(prefetchMshrReadVec)
  val registeredDemandWork = pendingCacheableBoundary || iActive ||
    stbValid.asUInt.orR || ebValid || lookupTokenD1Valid ||
    lookupTokenD2Valid || resultQueue.io.count =/= 0.U
  val prefetchBridgeMayQueue = hasPrefetchMshrRead &&
    !hasDemandMshrRead && !registeredDemandWork && !uncacheAdmissionLocked
  val bridgeReadMshr = Mux(hasDemandMshrRead,
    demandReadMshr, prefetchReadMshr)
  val bridgeReadQueue = Module(new Queue(new L2BridgeReadCmd, 1,
    pipe = false, flow = false))
  bridge.io.client.read.req <> bridgeReadQueue.io.deq
  bridgeReadQueue.io.enq.valid := hasDemandMshrRead || prefetchBridgeMayQueue ||
    (ucReadValid && !ucReadIssued && ucCoherenceReady)
  bridgeReadQueue.io.enq.bits := 0.U.asTypeOf(new L2BridgeReadCmd)
  when(hasDemandMshrRead || prefetchBridgeMayQueue) {
    bridgeReadQueue.io.enq.bits.owner.source := L2BridgeReadOwner.mshr
    bridgeReadQueue.io.enq.bits.owner.slot := bridgeReadMshr
    bridgeReadQueue.io.enq.bits.addr := mshrAddr(bridgeReadMshr)
    bridgeReadQueue.io.enq.bits.isLine := true.B
    bridgeReadQueue.io.enq.bits.size := 2.U
  }.otherwise {
    bridgeReadQueue.io.enq.bits.owner.source := L2BridgeReadOwner.uncache
    bridgeReadQueue.io.enq.bits.owner.slot := 0.U
    bridgeReadQueue.io.enq.bits.addr := ucReadAddr
    bridgeReadQueue.io.enq.bits.isLine := false.B
    bridgeReadQueue.io.enq.bits.size := ucReadSize
  }
  when(bridgeReadQueue.io.enq.fire) {
    when(bridgeReadQueue.io.enq.bits.owner.source === L2BridgeReadOwner.mshr) {
      mshrReadIssued(bridgeReadQueue.io.enq.bits.owner.slot) := true.B
    }.otherwise {
      ucReadIssued := true.B
    }
  }

  val bridgeBeatIsMshr = bridge.io.client.read.beat.bits.owner.source === L2BridgeReadOwner.mshr
  val bridgeBeatIsUc = bridge.io.client.read.beat.bits.owner.source === L2BridgeReadOwner.uncache
  val ucBeatPortReady = Mux(ucReadSource === L2PortSource.dcache, dLrbReady, iLrbReady || iKilled || iCancelPending)
  val ucBeatBlockedByResult = Mux(ucReadSource === L2PortSource.dcache,
    resultToD, resultToI)
  bridge.io.client.read.beat.ready := Mux(bridgeBeatIsMshr, true.B,
    bridgeBeatIsUc && ucBeatPortReady && !ucBeatBlockedByResult)

  when(bridge.io.client.read.beat.fire && bridgeBeatIsMshr) {
    val slot = bridge.io.client.read.beat.bits.owner.slot
    val beat = mshrRecvBeat(slot)
    lfbWords(slot)(beat) := bridge.io.client.read.beat.bits.data
    lfbBeatValid(slot)(beat) := true.B
    when(bridge.io.client.read.beat.bits.last) {
      mshrRecvBeat(slot) := 0.U
      mshrFillComplete(slot) := true.B
    }.otherwise {
      mshrRecvBeat(slot) := beat + 1.U
    }
  }

  // 每个L1端口独立从LFB取已到达beat，DDR接收不依赖L1 ready。
  val iSendVec = VecInit((0 until l2MshrEntries).map(i => mshrValid(i) &&
    !mshrPrefetch(i) && mshrSource(i) === L2PortSource.icache &&
    !mshrResponseQueued(i) &&
    lfbBeatValid(i)(mshrSendBeat(i))))
  val dSendVec = VecInit((0 until l2MshrEntries).map(i => mshrValid(i) &&
    !mshrPrefetch(i) && mshrSource(i) === L2PortSource.dcache &&
    !mshrResponseQueued(i) &&
    lfbBeatValid(i)(mshrSendBeat(i))))
  val hasISend = iSendVec.asUInt.orR
  val hasDSend = dSendVec.asUInt.orR
  val iSendMshr = PriorityEncoder(iSendVec)
  val dSendMshr = PriorityEncoder(dSendVec)
  val iMshrLoad = !iKilled && !iCancelPending && !resultToI && hasISend && iLrbReady &&
    !(bridge.io.client.read.beat.valid && bridgeBeatIsUc && ucReadSource === L2PortSource.icache)
  val dMshrLoad = !resultToD && hasDSend && dLrbReady &&
    !(bridge.io.client.read.beat.valid && bridgeBeatIsUc && ucReadSource === L2PortSource.dcache)
  val ucBeatLoad = bridge.io.client.read.beat.fire && bridgeBeatIsUc
  val killedUcBeat = ucBeatLoad && ucReadSource === L2PortSource.icache && (iKilled || iCancelPending)
  val killedMshrCanAdvance = iKilled && iActiveHasMshr && mshrValid(iActiveMshr) &&
    mshrSource(iActiveMshr) === L2PortSource.icache && !mshrResponseQueued(iActiveMshr) &&
    lfbBeatValid(iActiveMshr)(mshrSendBeat(iActiveMshr))
  val killedMshrSlot = iActiveMshr
  val killedMshrBeat = mshrSendBeat(killedMshrSlot)

  // A killed I miss is consumed internally; DDR refill/install is retained in L2.
  when(killedMshrCanAdvance) {
    lfbBeatValid(killedMshrSlot)(killedMshrBeat) := false.B
    when(killedMshrBeat === (l2BurstBeats - 1).U) {
      mshrResponseQueued(killedMshrSlot) := true.B
      mshrResponseAck(killedMshrSlot) := true.B
    }.otherwise {
      mshrSendBeat(killedMshrSlot) := killedMshrBeat + 1.U
    }
  }
  when(killedUcBeat && bridge.io.client.read.beat.bits.last) {
    ucReadValid := false.B
    iActive := false.B
    iKilled := false.B
    iActiveHasMshr := false.B
  }

  iPrefetchTrainValid := false.B
  dPrefetchTrainValid := false.B
  when(io.icache.resp.fire) {
    iLrbValid := false.B
    when(iLrbFromMshr && iLrbBits.last) {
      mshrResponseAck(iLrbMshr) := true.B
    }
    when(iLrbBits.last && !iLrbFromUncache && !io.icache.cancel) {
      iPrefetchTrainValid := true.B
      iPrefetchTrainAddr := iLrbAddr
    }
    when(iLrbFromUncache) {
      ucReadValid := false.B
    }
  }
  when(io.icache.resp.fire && iLrbBits.last) {
    iActive := false.B
    iKilled := false.B
    iActiveHasMshr := false.B
  }

  when(io.dcache.read.resp.fire) {
    dLrbValid := false.B
    when(dLrbFromMshr && dLrbBits.last) {
      mshrResponseAck(dLrbMshr) := true.B
    }
    when(dLrbBits.last && !dLrbFromUncache) {
      dPrefetchTrainValid := true.B
      dPrefetchTrainAddr := dLrbAddr
    }
    when(dLrbFromUncache) {
      ucReadValid := false.B
    }
  }

  when(ucBeatLoad && ucReadSource === L2PortSource.icache && !iKilled && !iCancelPending) {
    iLrbValid := true.B
    iLrbBits.id := ucReadId
    iLrbBits.data := Cat(0.U((l2LineBits - XLEN).W), bridge.io.client.read.beat.bits.data)
    iLrbBits.fullLine := false.B
    iLrbBits.last := bridge.io.client.read.beat.bits.last
    iLrbFromMshr := false.B
    iLrbFromUncache := true.B
    iLrbAddr := ucReadAddr
  }.elsewhen(resultReadResponse && head.token.source === L2PortSource.icache && !iKilled && !iCancelPending) {
    iLrbValid := true.B
    iLrbBits.id := head.token.id
    iLrbBits.data := resultResponseData
    iLrbBits.fullLine := true.B
    iLrbBits.last := true.B
    iLrbFromMshr := false.B
    iLrbFromUncache := false.B
    iLrbAddr := head.token.addr
  }.elsewhen(iMshrLoad) {
    val slot = iSendMshr
    val beat = mshrSendBeat(slot)
    iLrbValid := true.B
    iLrbBits.id := mshrId(slot)
    iLrbBits.data := Cat(0.U((l2LineBits - XLEN).W), lfbWords(slot)(beat))
    iLrbBits.fullLine := false.B
    iLrbBits.last := beat === (l2BurstBeats - 1).U
    iLrbFromMshr := true.B
    iLrbFromUncache := false.B
    iLrbMshr := slot
    iLrbAddr := mshrAddr(slot)
    when(beat === (l2BurstBeats - 1).U) {
      mshrResponseQueued(slot) := true.B
    }.otherwise {
      mshrSendBeat(slot) := beat + 1.U
    }
  }

  when(ucBeatLoad && ucReadSource === L2PortSource.dcache) {
    dLrbValid := true.B
    dLrbBits.id := ucReadId
    dLrbBits.data := Cat(0.U((l2LineBits - XLEN).W), bridge.io.client.read.beat.bits.data)
    dLrbBits.fullLine := false.B
    dLrbBits.last := bridge.io.client.read.beat.bits.last
    dLrbFromMshr := false.B
    dLrbFromUncache := true.B
    dLrbAddr := ucReadAddr
  }.elsewhen(resultReadResponse && head.token.source === L2PortSource.dcache) {
    dLrbValid := true.B
    dLrbBits.id := head.token.id
    dLrbBits.data := resultResponseData
    dLrbBits.fullLine := true.B
    dLrbBits.last := true.B
    dLrbFromMshr := false.B
    dLrbFromUncache := false.B
    dLrbAddr := head.token.addr
  }.elsewhen(dMshrLoad) {
    val slot = dSendMshr
    val beat = mshrSendBeat(slot)
    dLrbValid := true.B
    dLrbBits.id := mshrId(slot)
    dLrbBits.data := Cat(0.U((l2LineBits - XLEN).W), lfbWords(slot)(beat))
    dLrbBits.fullLine := false.B
    dLrbBits.last := beat === (l2BurstBeats - 1).U
    dLrbFromMshr := true.B
    dLrbFromUncache := false.B
    dLrbMshr := slot
    dLrbAddr := mshrAddr(slot)
    when(beat === (l2BurstBeats - 1).U) {
      mshrResponseQueued(slot) := true.B
    }.otherwise {
      mshrSendBeat(slot) := beat + 1.U
    }
  }

  // cleanLine/uncache的完成必须进入独立done缓冲，不能复用请求ready。
  val cleanDoneVec = VecInit((0 until stbCount).map(i => stbValid(i) &&
    stbKind(i) === L2WriteKind.cleanLine && stbDdrDone(i) && !stbDoneQueued(i)))
  val hasCleanDone = cleanDoneVec.asUInt.orR
  val cleanDoneSlot = PriorityEncoder(cleanDoneVec)
  val doneBufferReady = !dDoneValid
  when(io.dcache.write.done.fire) {
    dDoneValid := false.B
    when(dDoneIsStb) {
      stbDoneAck(dDoneStbSlot) := true.B
    }
    when(dDoneIsUncache) {
      ucWriteValid := false.B
      ucWriteBridgeDone := false.B
      ucWriteDoneQueued := false.B
    }
  }
  when(doneBufferReady && hasCleanDone) {
    dDoneValid := true.B
    dDoneBits.id := stbId(cleanDoneSlot)
    dDoneIsStb := true.B
    dDoneStbSlot := cleanDoneSlot
    dDoneIsUncache := false.B
    stbDoneQueued(cleanDoneSlot) := true.B
  }.elsewhen(doneBufferReady && ucWriteValid && ucWriteBridgeDone && !ucWriteDoneQueued) {
    dDoneValid := true.B
    dDoneBits.id := ucWriteId
    dDoneIsStb := false.B
    dDoneIsUncache := true.B
    ucWriteDoneQueued := true.B
  }

  for (i <- 0 until stbCount) {
    when(stbValid(i) && stbKind(i) === L2WriteKind.cleanLine &&
      stbInstalled(i) && stbDoneAck(i)) {
      stbValid(i) := false.B
    }
  }
  for (i <- 0 until l2MshrEntries) {
    val normalDone = !mshrPrefetch(i) && mshrInstalled(i) && mshrResponseAck(i)
    val prefetchDone = mshrPrefetch(i) && mshrInstalled(i) && !promoteMask(i)
    when(mshrValid(i) && (normalDone || prefetchDone)) {
      mshrValid(i) := false.B
      mshrPrefetch(i) := false.B
      // Normal I misses release ownership on the accepted last response beat.
      // Only the exact cancelled owner may release it from MSHR cleanup.
      when(mshrSource(i) === L2PortSource.icache && iActive && iKilled &&
        iActiveHasMshr && iActiveMshr === i.U) {
        iActive := false.B
        iKilled := false.B
        iActiveHasMshr := false.B
      }
    }
  }
}
