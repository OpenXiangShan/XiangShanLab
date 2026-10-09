package minixiangshan.backend.decode

import chisel3._
import chisel3.util._
import minixiangshan.config.{NSBundle, Parameters, ExceptionBundle}
import minixiangshan.frontend.{PredecodeInfo, CtrlFlowIO, bpuInfoBundle}
import minixiangshan.mmu.{FetchMmuError}

// 读 RAT 接口
class RATReadIO extends Bundle {
  val rs1   = UInt(5.W)
  val rs2   = UInt(5.W)
  val hold1 = Bool()
  val hold2 = Bool()
}

/** 缓存维护微操作（RISC-V 中 FENCE.I 走 BarOp.fenceI，本结构保留备用） */
class CacheOpDecode extends Bundle {
  val valid     = Bool()
  val code      = UInt(CacheOpCode.width.W)
  val cacheType = UInt(CacheOpCode.cacheTypeWidth.W)
  val operation = UInt(CacheOpCode.operationWidth.W)
}

/** 控制信号打平 */
class DecodeCtrl(implicit p: Parameters) extends NSBundle {
  val fuType   = UInt(FuType.width.W)
  val aluOp    = UInt(AluOp.width.W)
  val bruOp    = UInt(BruOp.width.W)
  val lsuOp    = UInt(LsuOp.width.W)
  val barOp    = UInt(BarOp.width.W)
  val csrOp    = UInt(CsrOp.width.W)
  val tlbOp    = UInt(TlbOp.width.W)
  val mulOp    = UInt(MulOp.width.W)
  val divOp    = UInt(DivOp.width.W)
  val src1Type = UInt(SrcType.width.W)
  val src2Type = UInt(SrcType.width.W)
  val immType  = UInt(ImmType.width.W)

  val rfWen    = Bool()
  val memRead  = Bool()
  val memWrite = Bool()
  val csrWen   = Bool()
  val isBranch = Bool()
  val isJump   = Bool()
  /** 执行该指令所需的最小特权级（PrivLevel.*） */
  val privLevel = UInt(PrivLevel.width.W)
  val isIdle   = Bool()
  val waitForward = Bool()
  val blockBackward = Bool()
  val flushOnCommit = Bool()
}

/** 译码后发往后端的指令宏包 */
class DecodedInst(implicit p: Parameters) extends NSBundle {
  val pc         = UInt(XLEN.W)
  val inst       = UInt(XLEN.W)

  val rd         = UInt(5.W)
  val rs1        = UInt(5.W) // 实际参与读 RAT 的源寄存器 1（CSR 立即数形式下为 uimm）
  val rs2        = UInt(5.W) // 实际参与读 RAT 的源寄存器 2
  val rs1Valid   = Bool()
  val rs2Valid   = Bool()
  val rdValid    = Bool()

  val csrAddress = UInt(csrAddrLen.W)
  val imm        = UInt(XLEN.W)
  val cacheOp    = new CacheOpDecode

  val ctrl       = new DecodeCtrl
  val excp       = new ExceptionBundle
  val pdInfo     = new PredecodeInfo
  val bpuInfo    = new bpuInfoBundle
}
