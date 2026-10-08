package minixiangshan.csr

import chisel3._
import chisel3.util._
import minixiangshan.config._

class CsrFileBundleSkel(implicit p: Parameters) extends BundleSkel

class CsrFileReadReq(implicit p: Parameters) extends CsrFileBundleSkel {
  val addr = UInt(csrAddrLen.W)
}

class CsrFileReadResp(implicit p: Parameters) extends CsrFileBundleSkel {
  val data = UInt(XLEN.W)
}

class CsrFileWriteReq(implicit p: Parameters) extends CsrFileBundleSkel {
  val wen = Bool()
  val addr = UInt(csrAddrLen.W)
  val data = UInt(XLEN.W)
}

/**
 * 陷入请求（RedirectController -> CsrFile）
 * 既包含组合路径（用于计算 trap 入口地址），也由 CSR 内部打拍后用于状态更新。
 */
class TrapReq(implicit p: Parameters) extends CsrFileBundleSkel {
  val valid       = Bool()
  val isInterrupt = Bool()
  val cause       = UInt(6.W)
  val epc         = UInt(XLEN.W)
  val tval        = UInt(XLEN.W)
  val priv        = UInt(privLen.W)  // 陷入发生时的特权级
  val xret        = Bool()   // mret / sret
  val isMret      = Bool()
}

/** CsrFile -> RedirectController 的陷入环境（寄存器输出，回滚期间保持稳定） */
class TrapEnv(implicit p: Parameters) extends CsrFileBundleSkel {
  val mtvec    = UInt(XLEN.W)
  val stvec    = UInt(XLEN.W)
  val medeleg  = UInt(XLEN.W)
  val mideleg  = UInt(XLEN.W)
  val mepc     = UInt(XLEN.W)
  val sepc     = UInt(XLEN.W)
}

class TimerBundle(implicit p: Parameters) extends CsrFileBundleSkel {
  val tid   = UInt(XLEN.W)
  val timer = UInt(TimerLen.W)
}

class PrivCtrl(implicit p: Parameters) extends CsrFileBundleSkel {
  val curPriv  = UInt(privLen.W)  // 当前特权级
  val dataPriv = UInt(privLen.W)  // 数据访问特权级（考虑 mstatus.MPRV）
}

class CsrFileIo(implicit p: Parameters) extends CsrFileBundleSkel {
  val irqBus = Input(UInt(8.W))
  val hasIrq = Output(Bool())
  /** 当前最高优先级的 pending+enabled 中断编号 */
  val intrCode = Output(UInt(IntrCode.width.W))

  val rReq = Input(new CsrFileReadReq)
  val rResp = Output(new CsrFileReadResp)
  val wReq = Input(new CsrFileWriteReq)

  val trapReq = Input(new TrapReq)
  val trapEnv = Output(new TrapEnv)

  val timerInfo = Output(new TimerBundle)
  val priv = Output(new PrivCtrl)
  val mmuCtrl = Output(new minixiangshan.mmu.CsrToMmu)
  val flushTlb = Output(Bool())

  val lrValidSet = Input(Bool())
  val lrValidClear = Input(Bool())
  val lrValid = Output(Bool())

  /** ROB 本周期是否有指令提交（用于 minstret） */
  val commitValid = Input(Bool())
}

/* ============================================================================
 *  已实现 CSR 地址表
 * ==========================================================================*/
object CsrAddrMap {
  private def eq(a: UInt, b: Int): Bool = a === b.U

  def isImplemented(a: UInt): Bool = {
    val list = Seq(
      0x000, 0x001, 0x002, 0x003,                                  // ustatus/fcsr
      0x100, 0x104, 0x105, 0x106, 0x140, 0x141, 0x142, 0x143, 0x144, 0x180,
      0x300, 0x301, 0x302, 0x303, 0x304, 0x305, 0x306, 0x310, 0x312, 0x313,
      0x340, 0x341, 0x342, 0x343, 0x344, 0x34a, 0x34b,
      0x3a0, 0x3a1, 0x3a2, 0x3a3,
      0xb00, 0xb02, 0xb80, 0xb82,
      0xc00, 0xc01, 0xc02, 0xc80, 0xc81, 0xc82,
      0xf11, 0xf12, 0xf13, 0xf14, 0xf15
    )
    val base = list.map(x => eq(a, x)).reduce(_ || _)
    // pmpaddr0 .. pmpaddr15 -> 0x3b0 .. 0x3bf
    val pmpAddr = (a >= 0x3b0.U) && (a <= 0x3bf.U)
    base || pmpAddr
  }
}
