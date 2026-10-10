package minixiangshan.difftest

import chisel3._
import chisel3.util._
import minixiangshan.config._
import minixiangshan.backend.decode._

class DifftestLoadInfo(implicit p: Parameters) extends NSBundle {
  val valid = Bool()
  val paddr = UInt(XLEN.W)
  val vaddr = UInt(XLEN.W)
}

class DifftestStoreInfo(implicit p: Parameters) extends NSBundle {
  val valid = Bool()
  val paddr = UInt(XLEN.W)
  val vaddr = UInt(XLEN.W)
  val data  = UInt(XLEN.W)
}

/** 提交信息（RISC-V） */
class DifftestCommitInfo(implicit p: Parameters) extends NSBundle {
  val valid      = Bool()
  val pc         = UInt(XLEN.W)
  val instr      = UInt(32.W)
  val rfWen      = Bool()
  val wdest      = UInt(5.W)
  val wdata      = UInt(XLEN.W)
  val excpFlush  = Bool()          // 触发异常
  val xretFlush  = Bool()          // mret / sret
  val cause      = UInt(6.W)       // mcause/scause 低位
  val trap       = Bool()          // 测试结束（ebreak 约定）
  val trapCode   = UInt(8.W)
  val tlbFillIdx = UInt(5.W)
  val load       = new DifftestLoadInfo
  val store      = new DifftestStoreInfo
}

/** 架构 CSR 状态（RISC-V） */
class DifftestCSRState(implicit p: Parameters) extends NSBundle {
  val mstatus   = UInt(32.W)
  val misa      = UInt(32.W)
  val medeleg   = UInt(32.W)
  val mideleg   = UInt(32.W)
  val mie       = UInt(32.W)
  val mtvec     = UInt(32.W)
  val mscratch  = UInt(32.W)
  val mepc      = UInt(32.W)
  val mcause    = UInt(32.W)
  val mtval     = UInt(32.W)
  val mip       = UInt(32.W)
  val sstatus   = UInt(32.W)
  val stvec     = UInt(32.W)
  val sscratch  = UInt(32.W)
  val sepc      = UInt(32.W)
  val scause    = UInt(32.W)
  val stval     = UInt(32.W)
  val satp      = UInt(32.W)
  val priv      = UInt(2.W)
  val mcycle    = UInt(64.W)
  val minstret  = UInt(64.W)
  val timer64   = UInt(64.W)
}

class CtrlBlockDifftestBundle(implicit p: Parameters) extends NSBundle {
  val commit    = Vec(CommitWidth, new DifftestCommitInfo)
  val archState = Vec(IntLogicRegs, UInt(PhyRegIdxWidth.W))
}

class BackendDifftestBundle(implicit p: Parameters) extends NSBundle {
  val commit = Vec(CommitWidth, new DifftestCommitInfo)
  val regs   = Vec(IntLogicRegs, UInt(XLEN.W))
}

class CoreDifftestBundle(implicit p: Parameters) extends NSBundle {
  val commit = Vec(CommitWidth, new DifftestCommitInfo)
  val regs   = Vec(IntLogicRegs, UInt(XLEN.W))
  val csr    = new DifftestCSRState
}

object DifftestUtils {
  /** 约定：ebreak 作为程序结束标志 */
  def isTrap(inst: UInt): Bool = Instructions.EBREAK === inst

  def excpCause(excp: ExceptionBundle): UInt = excp.cause
}
