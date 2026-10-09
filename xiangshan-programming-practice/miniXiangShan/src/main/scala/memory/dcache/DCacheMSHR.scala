package minixiangshan.mem.dcache
 
import chisel3._
import chisel3.util._
import minixiangshan.config._
import minixiangshan.backend.decode._
import minixiangshan.backend.rename._
import minixiangshan.backend.execute._
import minixiangshan.mem.L2cache._
 
// ================================================================
//  MSHR 顶层：2 Primary + 4 LoadStore
// ================================================================
class DCacheMSHRFile(implicit p: Parameters) extends NSModule {
  val nPrim = 2
  val nSec  = 4
 
  val io = IO(new Bundle {
    val missReq = Flipped(Decoupled(new Bundle {
      val paddr       = UInt(XLEN.W)
      val lqIdx       = UInt(log2Ceil(LqSize).W)
      val sqIdx       = UInt(log2Ceil(SqSize).W)
      val robIdx      = new RobPtr(RobSize)
      val lsuOp       = UInt(LsuOp.width.W)
      val storeData   = UInt(XLEN.W)
      val isLoad      = Bool()
      val isStore     = Bool()
      val cacheable   = Bool()
      val victimWay   = UInt(wayBitsD.W)
      val victimDirty = Bool()
      val victimTag   = UInt(tagBitsD.W)
      val victimData  = UInt((blockBytes * 8).W)
    }))
 
    val probeBlockAddr = Input(UInt((tagBitsD + idxBitsD).W))
    val probeMatch     = Output(Bool())
    val isFirstMiss    = Output(Bool())
    val matchPrimId    = Output(UInt(1.W))
 
    val hasStore = Output(Bool())
    val idle     = Output(Bool())
 
    val canAlloc        = Output(Bool())
    val refillWriteReq  = Output(Valid(new Bundle {
      val idx  = UInt(idxBitsD.W)
      val way  = UInt(wayBitsD.W)
      val tag  = UInt(tagBitsD.W)
      val data = UInt((blockBytes * 8).W)
    }))
    val refillWriteAck    = Input(Valid(UInt(1.W)))
    val refillWritePrimId = Output(UInt(1.W))
 
    val lsReady       = Output(Bool())
    val lsIdx         = Output(UInt(log2Ceil(nSec).W))
    val lsIsUncache   = Output(Bool())
    val lsUncacheData = Output(UInt(XLEN.W))
    val lsPaddr       = Output(UInt(XLEN.W))
    val lsLqIdx       = Output(UInt(log2Ceil(LqSize).W))
    val lsSqIdx       = Output(UInt(log2Ceil(SqSize).W))
    val lsRobIdx      = Output(new RobPtr(RobSize))
    val lsLsuOp       = Output(UInt(LsuOp.width.W))
    val lsStoreData   = Output(UInt(XLEN.W))
    val lsIsLoad      = Output(Bool())
    val lsIsStore     = Output(Bool())
    val lsAck         = Input(Valid(UInt(log2Ceil(nSec).W)))
 
    val l2 = new L2NativeMasterIO(1)
    val redirectInfo    = Flipped(ValidIO(new redirectInfoToModule))
  })
 
  // ===== Primary 实例化 =====
  val primaries = Seq.tabulate(nPrim)(i => Module(new MSHREntry))
 
  // ===== LoadStore 表项 =====
  val lsValid     = RegInit(VecInit(Seq.fill(nSec)(false.B)))
  val lsReadyReg  = RegInit(VecInit(Seq.fill(nSec)(false.B)))
  val lsPaddr     = RegInit(VecInit(Seq.fill(nSec)(0.U(XLEN.W))))
  val lsLqIdx     = RegInit(VecInit(Seq.fill(nSec)(0.U(log2Ceil(LqSize).W))))
  val lsSqIdx     = RegInit(VecInit(Seq.fill(nSec)(0.U(log2Ceil(SqSize).W))))
  val lsRobIdx    = RegInit(VecInit(Seq.fill(nSec)(0.U.asTypeOf(new RobPtr(RobSize)))))
  val lsLsuOp     = RegInit(VecInit(Seq.fill(nSec)(0.U(LsuOp.width.W))))
  val lsStoreData = RegInit(VecInit(Seq.fill(nSec)(0.U(XLEN.W))))
  val lsIsLoad    = RegInit(VecInit(Seq.fill(nSec)(false.B)))
  val lsIsStore   = RegInit(VecInit(Seq.fill(nSec)(false.B)))
  val lsPrimaryId = RegInit(VecInit(Seq.fill(nSec)(0.U(1.W))))
  val lsIsUncache = RegInit(VecInit(Seq.fill(nSec)(false.B)))
  val lsFlushed   = RegInit(VecInit(Seq.fill(nSec)(false.B)))
 
  // ===== 探针 =====
  val reqIsUncache  = !io.missReq.bits.cacheable
 
  val blockMatchVec = primaries.map(p => p.io.busy && !p.io.isUncache && p.io.blockAddr === io.probeBlockAddr && !reqIsUncache)
  io.probeMatch  := VecInit(blockMatchVec).asUInt.orR
  io.isFirstMiss := !VecInit(blockMatchVec).asUInt.orR
  io.matchPrimId := PriorityMux(blockMatchVec.zipWithIndex.map { case (m, i) => m -> i.U })
 
  io.hasStore := VecInit((0 until nSec).map(i => lsValid(i) && lsIsStore(i) && !lsFlushed(i))).asUInt.orR
  io.idle := !VecInit(primaries.map(_.io.busy)).asUInt.orR && !lsValid.asUInt.orR
 
  // ===== 请求分配逻辑 =====
  val reqBlockAddr  = io.missReq.bits.paddr(31, blockOffBits)
  val reqSetIdx     = io.missReq.bits.paddr(blockOffBits + idxBitsD - 1, blockOffBits)
 
  val isFirstMissReq = !VecInit(blockMatchVec).asUInt.orR
  val matchPrimIdReq = PriorityMux(blockMatchVec.zipWithIndex.map { case (m, i) => m -> i.U })
 
  val freePrimMask = VecInit(primaries.map(_.io.canAccept)).asUInt
  val hasFreePrim  = freePrimMask.orR
  val allocPrimId  = PriorityEncoder(freePrimMask)
 
  val freeSecMask = VecInit((0 until nSec).map(i => !lsValid(i))).asUInt
  val hasFreeSec  = freeSecMask.orR
  val allocSecIdx = PriorityEncoder(freeSecMask)
 
  val setConflictVec = primaries.map(p =>
    p.io.busy && p.io.setIdx === reqSetIdx && p.io.blockAddr =/= reqBlockAddr
  )
  val setConflict = VecInit(setConflictVec).asUInt.orR
 
  val canAllocFirst  = hasFreePrim && hasFreeSec && !setConflict
  val canAllocMerge  = hasFreeSec
  // Uncache requests still need a secondary LS slot to carry completion
  // metadata back into the DCache replay path.
  val canAllocUncache = hasFreePrim && hasFreeSec
 
  val canAllocReq = Mux(reqIsUncache, canAllocUncache,
                    Mux(isFirstMissReq, canAllocFirst, canAllocMerge))
  io.canAlloc      := canAllocReq
  io.missReq.ready := canAllocReq
 
  // ===== Primary 连接 =====
  for ((prim, i) <- primaries.zipWithIndex) {
    prim.io.id := i.U
 
    val lsAllDone = !VecInit((0 until nSec).map(j =>
      lsValid(j) && lsPrimaryId(j) === i.U && !lsFlushed(j)
    )).asUInt.orR
    prim.io.release := prim.io.done && lsAllDone
 
    prim.io.refillWriteAck := io.refillWriteAck.valid && io.refillWriteAck.bits === i.U
 
    val allocThisPrim = isFirstMissReq && allocPrimId === i.U
    val allocUncacheThis = reqIsUncache && allocPrimId === i.U
    prim.io.req.valid := io.missReq.fire && (allocThisPrim || allocUncacheThis)
    prim.io.req.bits.paddr       := io.missReq.bits.paddr
    prim.io.req.bits.reqType     := Mux(reqIsUncache,
      Mux(io.missReq.bits.isLoad, MshrReqType.uncacheRead, MshrReqType.uncacheWrite),
      MshrReqType.cacheable)
    prim.io.req.bits.victimWay   := io.missReq.bits.victimWay
    prim.io.req.bits.victimDirty := io.missReq.bits.victimDirty
    prim.io.req.bits.victimTag   := io.missReq.bits.victimTag
    prim.io.req.bits.victimData  := io.missReq.bits.victimData
    prim.io.req.bits.storeData   := io.missReq.bits.storeData
    prim.io.req.bits.lsuOp       := io.missReq.bits.lsuOp
  }
 
  // ===== LS 表项分配 =====
  when(io.missReq.fire) {
    val idx = allocSecIdx
    lsValid(idx)     := true.B
    lsReadyReg(idx)  := false.B
    lsPaddr(idx)     := io.missReq.bits.paddr
    lsLqIdx(idx)     := io.missReq.bits.lqIdx
    lsSqIdx(idx)     := io.missReq.bits.sqIdx
    lsRobIdx(idx)    := io.missReq.bits.robIdx
    lsLsuOp(idx)     := io.missReq.bits.lsuOp
    lsStoreData(idx) := io.missReq.bits.storeData
    lsIsLoad(idx)    := io.missReq.bits.isLoad
    lsIsStore(idx)   := io.missReq.bits.isStore
    lsIsUncache(idx) := reqIsUncache
    lsFlushed(idx)   := false.B
 
    val primId = Mux(isFirstMissReq, allocPrimId, matchPrimIdReq)
    lsPrimaryId(idx) := primId
 
    val fastDone = VecInit(primaries.zipWithIndex.map { case (p, pi) =>
      p.io.done && pi.U === primId
    }).asUInt.orR
    when(fastDone) { lsReadyReg(idx) := true.B }
  }
 
  // ===== Wakeup =====
  for (i <- 0 until nPrim) {
    val prevDone = RegNext(primaries(i).io.done, false.B)
    when(primaries(i).io.done && !prevDone) {
      for (j <- 0 until nSec) {
        when(lsValid(j) && lsPrimaryId(j) === i.U && !lsFlushed(j)) {
          lsReadyReg(j) := true.B
        }
      }
    }
  }
 
  // ===== Redirect =====
  when(io.redirectInfo.valid && io.redirectInfo.bits.doRedirect) {
    for (j <- 0 until nSec) {
      when(lsValid(j) && lsIsLoad(j) && !lsIsStore(j) && !lsFlushed(j)) {
        when(lsRobIdx(j).isAfter(io.redirectInfo.bits.robIdx)) {
          lsFlushed(j) := true.B
        }
      }
    }
  }
 
  // 释放 flushed 表项
  for (j <- 0 until nSec) {
    when(lsValid(j) && lsFlushed(j)) {
      lsValid(j)    := false.B
      lsReadyReg(j) := false.B
      lsFlushed(j)  := false.B
    }
  }
 
  // ===== LS Ack =====
  when(io.lsAck.valid) {
    val idx = io.lsAck.bits
    lsValid(idx)    := false.B
    lsReadyReg(idx) := false.B
  }
 
  // ===== LS Ready 选择（Store 优先） =====
  val readyStores = Wire(Vec(nSec, Bool()))
  val readyLoads  = Wire(Vec(nSec, Bool()))
  for (j <- 0 until nSec) {
    readyStores(j) := lsValid(j) && lsReadyReg(j) && lsIsStore(j) && !lsFlushed(j)
    readyLoads(j)  := lsValid(j) && lsReadyReg(j) && !lsIsStore(j) && !lsFlushed(j)
  }
  val hasReadyStore = readyStores.asUInt.orR
  val hasReadyLs    = VecInit((0 until nSec).map(j =>

//      val isFlushedByRedirect = io.redirectInfo.valid && io.redirectInfo.bits.doRedirect &&
//  lsIsLoad(j) && !lsIsStore(j) && lsRobIdx(j).isAfter(io.redirectInfo.bits.robIdx)

    lsValid(j) && lsReadyReg(j) && !lsFlushed(j) && !(io.redirectInfo.valid && io.redirectInfo.bits.doRedirect
    /* && lsIsLoad(j) && !lsIsStore(j) && lsRobIdx(j).isAfter(io.redirectInfo.bits.robIdx) */)


  )).asUInt.orR
  val selectedLsIdx = Mux(hasReadyStore, PriorityEncoder(readyStores), PriorityEncoder(readyLoads))
 
  io.lsReady       := hasReadyLs
  io.lsIdx         := selectedLsIdx
  io.lsIsUncache   := lsIsUncache(selectedLsIdx)
  io.lsUncacheData := VecInit(primaries.map(_.io.uncacheData))(lsPrimaryId(selectedLsIdx))
  io.lsPaddr       := lsPaddr(selectedLsIdx)
  io.lsLqIdx       := lsLqIdx(selectedLsIdx)
  io.lsSqIdx       := lsSqIdx(selectedLsIdx)
  io.lsRobIdx      := lsRobIdx(selectedLsIdx)
  io.lsLsuOp       := lsLsuOp(selectedLsIdx)
  io.lsStoreData   := lsStoreData(selectedLsIdx)
  io.lsIsLoad      := lsIsLoad(selectedLsIdx)
  io.lsIsStore     := lsIsStore(selectedLsIdx)
 
  // ===== Refill Write =====
  val refillWritePrimVec = VecInit(primaries.map(_.io.refillWriteReq))
  val hasRefillWrite     = refillWritePrimVec.asUInt.orR
  val refillWritePrimSel = PriorityEncoder(refillWritePrimVec)
 
  val primSetIdxVec    = VecInit(primaries.map(_.io.setIdx))
  val primVictimWayVec = VecInit(primaries.map(_.io.mshrVictimWay))
  val primRefillTagVec = VecInit(primaries.map(_.io.refillTag))
  val primRefillDataVec = VecInit(primaries.map(_.io.refillData))
 
  io.refillWriteReq.valid := hasRefillWrite
  io.refillWriteReq.bits.idx  := primSetIdxVec(refillWritePrimSel)
  io.refillWriteReq.bits.way  := primVictimWayVec(refillWritePrimSel)
  io.refillWriteReq.bits.tag  := primRefillTagVec(refillWritePrimSel)
  io.refillWriteReq.bits.data := primRefillDataVec(refillWritePrimSel)
  io.refillWritePrimId := refillWritePrimSel
 
  // 两个 Primary 的 native 请求均锁定到握手，响应按 id 返回。
  val readLocked = RegInit(false.B)
  val readWinner = RegInit(0.U(1.W))
  val readValids = VecInit(primaries.map(_.io.l2.read.req.valid))
  val readSel = Mux(readLocked, readWinner, Mux(readValids(0), 0.U, 1.U))
  val readSelOH = UIntToOH(readSel, nPrim)
  val readOutValid = readValids(readSel)
  val readFire = readOutValid && io.l2.read.req.ready

  when(!readLocked && readValids.asUInt.orR && !readFire) {
    readLocked := true.B
    readWinner := readSel
  }.elsewhen(readLocked && readFire) {
    readLocked := false.B
  }

  io.l2.read.cancel := false.B
  io.l2.read.req.valid := readOutValid
  io.l2.read.req.bits := Mux1H(readSelOH, primaries.map(_.io.l2.read.req.bits))
  for ((prim, i) <- primaries.zipWithIndex) {
    prim.io.l2.read.req.ready := io.l2.read.req.ready && readSelOH(i)
    prim.io.l2.read.resp.valid := io.l2.read.resp.valid &&
      io.l2.read.resp.bits.id === i.U
    prim.io.l2.read.resp.bits := io.l2.read.resp.bits
  }
  io.l2.read.resp.ready := Mux(io.l2.read.resp.bits.id === 0.U,
    primaries(0).io.l2.read.resp.ready, primaries(1).io.l2.read.resp.ready)

  val writeLocked = RegInit(false.B)
  val writeWinner = RegInit(0.U(1.W))
  val writeValids = VecInit(primaries.map(_.io.l2.write.req.valid))
  val writeSel = Mux(writeLocked, writeWinner, Mux(writeValids(0), 0.U, 1.U))
  val writeSelOH = UIntToOH(writeSel, nPrim)
  val writeOutValid = writeValids(writeSel)
  val writeFire = writeOutValid && io.l2.write.req.ready

  when(!writeLocked && writeValids.asUInt.orR && !writeFire) {
    writeLocked := true.B
    writeWinner := writeSel
  }.elsewhen(writeLocked && writeFire) {
    writeLocked := false.B
  }

  io.l2.write.req.valid := writeOutValid
  io.l2.write.req.bits := Mux1H(writeSelOH, primaries.map(_.io.l2.write.req.bits))
  for ((prim, i) <- primaries.zipWithIndex) {
    prim.io.l2.write.req.ready := io.l2.write.req.ready && writeSelOH(i)
    prim.io.l2.write.done.valid := io.l2.write.done.valid &&
      io.l2.write.done.bits.id === i.U
    prim.io.l2.write.done.bits := io.l2.write.done.bits
  }
  io.l2.write.done.ready := Mux(io.l2.write.done.bits.id === 0.U,
    primaries(0).io.l2.write.done.ready, primaries(1).io.l2.write.done.ready)
}
