package minixiangshan.mem.dcache

import chisel3._
import chisel3.util._
import minixiangshan.backend.decode._
import minixiangshan.backend.rename._
import minixiangshan.config._
import minixiangshan.mem.L2cache._

// Primary MSHR 表项：直接使用 L2 native 接口。
class MSHREntry(implicit p: Parameters) extends NSModule {
  private val refillBeats = blockBytes / (XLEN / 8)

  val io = IO(new Bundle {
    val id = Input(UInt(1.W))

    val req = Flipped(Decoupled(new Bundle {
      val paddr       = UInt(XLEN.W)
      val reqType     = UInt(MshrReqType.width.W)
      val victimWay   = UInt(wayBitsD.W)
      val victimDirty = Bool()
      val victimTag   = UInt(tagBitsD.W)
      val victimData  = UInt((blockBytes * 8).W)
      val storeData   = UInt(XLEN.W)
      val lsuOp       = UInt(LsuOp.width.W)
    }))

    val l2 = new L2NativeMasterIO(1)

    val refillWriteReq = Output(Bool())
    val refillWriteAck = Input(Bool())
    val fetchDone          = Output(Bool())
    val fetchDoneBlockAddr = Output(UInt((tagBitsD + idxBitsD).W))
    val uncacheData = Output(UInt(XLEN.W))
    val refillData  = Output(UInt((blockBytes * 8).W))
    val refillTag   = Output(UInt(tagBitsD.W))
    val release = Input(Bool())
    val busy          = Output(Bool())
    val done          = Output(Bool())
    val blockAddr     = Output(UInt((tagBitsD + idxBitsD).W))
    val setIdx        = Output(UInt(idxBitsD.W))
    val mshrVictimWay = Output(UInt(wayBitsD.W))
    val canAccept     = Output(Bool())
    val isWriteback   = Output(Bool())
    val isUncache     = Output(Bool())
  })

  val states = Enum(11)
  val sIdle = states(0)
  val sPutLine = states(1)
  val sReadReq = states(2)
  val sReadResp = states(3)
  val sRefillWrite = states(4)
  val sDone = states(5)
  val sUcReadReq = states(6)
  val sUcReadResp = states(7)
  val sUcWriteReq = states(8)
  val sUcWriteDone = states(9)
  val sUcDone = states(10)
  val state = RegInit(sIdle)

  val reqPaddr       = RegInit(0.U(XLEN.W))
  val reqType        = RegInit(0.U(MshrReqType.width.W))
  val reqVictimWay   = RegInit(0.U(wayBitsD.W))
  val reqVictimTag   = RegInit(0.U(tagBitsD.W))
  val reqVictimData  = RegInit(0.U((blockBytes * 8).W))
  val reqStoreData   = RegInit(0.U(XLEN.W))
  val reqLsuOp       = RegInit(0.U(LsuOp.width.W))
  val beatCnt        = RegInit(0.U(log2Ceil(refillBeats).W))
  val refillBuf      = RegInit(VecInit(Seq.fill(refillBeats)(0.U(XLEN.W))))
  val ucDataReg      = RegInit(0.U(XLEN.W))

  val setIdx = reqPaddr(blockOffBits + idxBitsD - 1, blockOffBits)
  val refillTag = reqPaddr(31, blockOffBits + idxBitsD)
  val refillAddr = Cat(reqPaddr(31, blockOffBits), 0.U(blockOffBits.W))
  val victimAddr = Cat(reqVictimTag, setIdx, 0.U(blockOffBits.W))

  io.busy          := state =/= sIdle
  io.done          := state === sDone || state === sUcDone
  io.blockAddr     := reqPaddr(31, blockOffBits)
  io.setIdx        := setIdx
  io.mshrVictimWay := reqVictimWay
  io.canAccept     := state === sIdle
  io.isWriteback   := state === sPutLine
  io.isUncache     := reqType =/= MshrReqType.cacheable
  io.fetchDone          := io.done
  io.fetchDoneBlockAddr := reqPaddr(31, blockOffBits)
  io.uncacheData   := ucDataReg
  io.refillWriteReq := state === sRefillWrite
  io.refillData    := Cat(refillBuf.reverse)
  io.refillTag     := refillTag

  io.req.ready := state === sIdle
  when(io.req.fire) {
    reqPaddr       := io.req.bits.paddr
    reqType        := io.req.bits.reqType
    reqVictimWay   := io.req.bits.victimWay
    reqVictimTag   := io.req.bits.victimTag
    reqVictimData  := io.req.bits.victimData
    reqStoreData   := io.req.bits.storeData
    reqLsuOp       := io.req.bits.lsuOp
    beatCnt        := 0.U
    state := Mux(io.req.bits.reqType === MshrReqType.uncacheRead, sUcReadReq,
      Mux(io.req.bits.reqType === MshrReqType.uncacheWrite, sUcWriteReq,
        Mux(io.req.bits.victimDirty, sPutLine, sReadReq)))
  }

  private def loadSize(op: UInt): UInt = MuxLookup(op, 2.U)(Seq(
    LsuOp.lb -> 0.U, LsuOp.lbu -> 0.U,
    LsuOp.lh -> 1.U, LsuOp.lhu -> 1.U,
    LsuOp.lw -> 2.U
  ))

  private def storeSize(op: UInt): UInt = MuxLookup(op, 2.U)(Seq(
    LsuOp.sb -> 0.U, LsuOp.sh -> 1.U, LsuOp.sw -> 2.U
  ))

  val byteOff = reqPaddr(1, 0)
  val ucWstrb = MuxLookup(reqLsuOp, "b1111".U(4.W))(Seq(
    LsuOp.sb -> UIntToOH(byteOff, 4),
    LsuOp.sh -> Mux(byteOff(1), "b1100".U, "b0011".U),
    LsuOp.sw -> "b1111".U
  ))
  val ucWdata = MuxLookup(reqLsuOp, reqStoreData)(Seq(
    LsuOp.sb -> (reqStoreData(7, 0) << (byteOff * 8.U)),
    LsuOp.sh -> (reqStoreData(15, 0) << (Cat(byteOff(1), 0.U(1.W)) * 8.U)),
    LsuOp.sw -> reqStoreData
  ))

  // Dirty victim 的 putLine 在请求握手时即由 L2 接管。
  io.l2.write.req.valid := state === sPutLine || state === sUcWriteReq
  io.l2.write.req.bits.id := io.id
  io.l2.write.req.bits.addr := Mux(state === sUcWriteReq, reqPaddr, victimAddr)
  io.l2.write.req.bits.kind := Mux(state === sUcWriteReq,
    L2WriteKind.uncache, L2WriteKind.putLine)
  io.l2.write.req.bits.size := Mux(state === sUcWriteReq, storeSize(reqLsuOp), 2.U)
  io.l2.write.req.bits.data := Mux(state === sUcWriteReq,
    ucWdata.pad(l2LineBits), reqVictimData)
  io.l2.write.req.bits.strb := Mux(state === sUcWriteReq, ucWstrb,
    Fill(l2BeatBytes, 1.U(1.W)))

  when(state === sPutLine && io.l2.write.req.fire) {
    state := sReadReq
  }
  when(state === sUcWriteReq && io.l2.write.req.fire) {
    state := sUcWriteDone
  }

  io.l2.write.done.ready := state === sUcWriteDone
  when(state === sUcWriteDone && io.l2.write.done.fire) {
    state := sUcDone
  }

  io.l2.read.cancel := false.B
  io.l2.read.req.valid := state === sReadReq || state === sUcReadReq
  io.l2.read.req.bits.id := io.id
  io.l2.read.req.bits.addr := Mux(state === sUcReadReq, reqPaddr, refillAddr)
  io.l2.read.req.bits.size := Mux(state === sUcReadReq, loadSize(reqLsuOp), 2.U)
  io.l2.read.req.bits.uncache := state === sUcReadReq

  when(state === sReadReq && io.l2.read.req.fire) {
    state := sReadResp
  }
  when(state === sUcReadReq && io.l2.read.req.fire) {
    state := sUcReadResp
  }

  io.l2.read.resp.ready := state === sReadResp || state === sUcReadResp
  when(state === sReadResp && io.l2.read.resp.fire) {
    when(io.l2.read.resp.bits.fullLine) {
      for (i <- 0 until refillBeats) {
        refillBuf(i) := io.l2.read.resp.bits.data((i + 1) * XLEN - 1, i * XLEN)
      }
    }.otherwise {
      refillBuf(beatCnt) := io.l2.read.resp.bits.data(XLEN - 1, 0)
      beatCnt := beatCnt + 1.U
    }
    when(io.l2.read.resp.bits.last) {
      beatCnt := 0.U
      state := sRefillWrite
    }
  }
  when(state === sUcReadResp && io.l2.read.resp.fire) {
    ucDataReg := io.l2.read.resp.bits.data(XLEN - 1, 0)
    state := sUcDone
  }

  when(state === sRefillWrite && io.refillWriteAck) {
    state := sDone
  }
  when(state === sDone && io.release) {
    state := sIdle
  }
  when(state === sUcDone && io.release) {
    state := sIdle
  }
}
