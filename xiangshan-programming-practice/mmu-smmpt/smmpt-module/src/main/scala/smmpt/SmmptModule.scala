package smmpt

import chisel3._
import chisel3.util._

case class SmmptParams(
  hasMptCheck: Boolean = true,
  ppnWidth: Int = 44,
  xlen: Int = 64,
  sourceWidth: Int = 4,
  ptwWidth: Int = 4
) {
  require(ppnWidth > 0)
  require(xlen > 0)
  require(sourceWidth > 0)
  require(ptwWidth > 0)
  require(xlen >= ppnWidth + 12)
}

class SmmptMmptBundle(p: SmmptParams) extends Bundle {
  val mode = UInt(4.W)
  val optOutInNode = Bool()
  val sdid = UInt(6.W)
  val ppn = UInt(p.ppnWidth.W)
  val changed = Bool()
}

class SmmptSatpLikeBundle extends Bundle {
  val changed = Bool()
}

class SmmptPrivBundle extends Bundle {
  val virtChanged = Bool()
}

class SmmptCsrBundle(p: SmmptParams) extends Bundle {
  val satp = new SmmptSatpLikeBundle
  val vsatp = new SmmptSatpLikeBundle
  val hgatp = new SmmptSatpLikeBundle
  val priv = new SmmptPrivBundle
  val mmpt = new SmmptMmptBundle(p)
}

class SmmptSfenceBits(p: SmmptParams) extends Bundle {
  val rs1 = Bool()
  val rs2 = Bool()
  val id = UInt(6.W)
  val addr = UInt(p.xlen.W)
  val mfence = if (p.hasMptCheck) Some(Bool()) else None
}

class SmmptSfenceBundle(p: SmmptParams) extends Bundle {
  val valid = Bool()
  val bits = new SmmptSfenceBits(p)
}

class SmmptReqBundle(p: SmmptParams) extends Bundle {
  val reqPA = UInt(p.ppnWidth.W)
  val id = UInt(p.sourceWidth.W)
  val mptOnly = Bool()
}

class SmmptRespBundle(p: SmmptParams) extends Bundle {
  val id = UInt(p.sourceWidth.W)
  val reqPA = UInt(p.ppnWidth.W)
  val mptOnly = Bool()
  val accessFault = Bool()
  val mptPerm = UInt(3.W)
  val mptLevel = UInt(2.W)
  val contigousPerm = Bool()
  val permIsNAPOT = Bool()
}

class SmmptMemReqBundle(p: SmmptParams) extends Bundle {
  val addr = UInt(p.xlen.W)
  val id = UInt(p.sourceWidth.W)
}

class SmmptStatusBundle(p: SmmptParams) extends Bundle {
  val mptEn = Bool()
  val mptAf = Bool()
  val accessFaultMpt = Bool()
  val memReqBlockedByMptAf = Bool()
  val checkIntermediateNode = Bool()
  val flush = Bool()
  val mfenceActive = Bool()
}

class SmmptCheckerShellIO(p: SmmptParams) extends Bundle {
  val csr = Input(new SmmptCsrBundle(p))
  val sfence = Input(new SmmptSfenceBundle(p))
  val upstreamAccessFault = Input(Bool())
  val req = Flipped(Decoupled(new SmmptReqBundle(p)))
  val resp = Valid(new SmmptRespBundle(p))
  val mem = new Bundle {
    val req = Decoupled(new SmmptMemReqBundle(p))
    val resp = Flipped(Valid(UInt(p.xlen.W)))
    val mask = Input(Bool())
  }
  val status = Output(new SmmptStatusBundle(p))
}

class SmmptHasMptCheckShell(p: SmmptParams = SmmptParams()) extends Module {
  val io = IO(new SmmptCheckerShellIO(p))

  val mptEn = if (p.hasMptCheck) io.csr.mmpt.mode =/= 0.U else false.B
  val checkIntermediateNode = if (p.hasMptCheck) !io.csr.mmpt.optOutInNode else false.B
  val mfenceActive = if (p.hasMptCheck) io.sfence.valid && io.sfence.bits.mfence.getOrElse(false.B) else false.B
  val flush = io.sfence.valid || io.csr.satp.changed || io.csr.vsatp.changed ||
    io.csr.hgatp.changed || io.csr.priv.virtChanged || (if (p.hasMptCheck) io.csr.mmpt.changed else false.B)

  val waitingMemResp = RegInit(false.B)
  val flushLatched = RegInit(false.B)
  val respValid = RegInit(false.B)
  val respReg = Reg(new SmmptRespBundle(p))
  val mptAf = RegInit(false.B)

  io.req.ready := !waitingMemResp && !io.mem.mask && !mptAf
  io.mem.req.valid := io.req.valid && io.req.ready && mptEn && checkIntermediateNode && !flush
  io.mem.req.bits.addr := Cat(0.U((p.xlen - p.ppnWidth - 12).W), io.req.bits.reqPA, 0.U(12.W))
  io.mem.req.bits.id := io.req.bits.id

  when(io.req.fire) {
    respReg.id := io.req.bits.id
    respReg.reqPA := io.req.bits.reqPA
    respReg.mptOnly := io.req.bits.mptOnly
    respReg.mptLevel := 0.U
    respReg.contigousPerm := true.B
    respReg.permIsNAPOT := true.B
    when(!mptEn) {
      respReg.accessFault := false.B
      respReg.mptPerm := "b111".U
      respValid := true.B
    }.elsewhen(!checkIntermediateNode) {
      respReg.accessFault := false.B
      respReg.mptPerm := "b111".U
      respValid := true.B
    }.otherwise {
      waitingMemResp := true.B
      respValid := false.B
    }
  }.otherwise {
    respValid := false.B
  }

  when(io.mem.resp.valid && waitingMemResp) {
    waitingMemResp := false.B
    flushLatched := false.B
    respReg.accessFault := false.B
    respReg.mptPerm := io.mem.resp.bits(2, 0)
    respReg.mptLevel := 0.U
    respReg.contigousPerm := true.B
    respReg.permIsNAPOT := true.B
    respValid := !flushLatched
  }

  when(flush && waitingMemResp) {
    flushLatched := true.B
  }

  val respMptAf = respValid && (!respReg.mptPerm(0) || respReg.accessFault)
  when(respValid) {
    mptAf := !respReg.mptPerm(0) || respReg.accessFault
  }
  when(flush) {
    mptAf := false.B
  }

  io.resp.valid := respValid
  io.resp.bits := respReg
  io.status.mptEn := mptEn
  io.status.mptAf := mptAf
  io.status.accessFaultMpt := io.upstreamAccessFault || mptAf || respMptAf
  io.status.memReqBlockedByMptAf := mptAf
  io.status.checkIntermediateNode := checkIntermediateNode
  io.status.flush := flush
  io.status.mfenceActive := mfenceActive
}

object SmmptModuleMain extends App {
  _root_.circt.stage.ChiselStage.emitSystemVerilogFile(
    new SmmptHasMptCheckShell(),
    Array("--target-dir", "build/smmpt-module"),
    Array("--disable-all-randomization", "--strip-debug-info")
  )
}
