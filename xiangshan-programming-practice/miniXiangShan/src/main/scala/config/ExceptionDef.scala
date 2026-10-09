package minixiangshan.config
import chisel3._
import chisel3.util._
import minixiangshan.config.Parameters
import minixiangshan.config.NSBundle

/* ============================================================================
 *  RISC-V 异常 / 中断编码（特权规范 v1.12）
 *
 *  枚举值 = 位向量位号 = 判定优先级（0 最高），与 mcause/scause 的编码解耦，
 *  再由 codeInt 映射到规范定义的 cause 编号。
 * ==========================================================================*/
object ExcType extends Enumeration {
  type ExcType = Value

  val INT      = Value(0)   // 中断（cause 高位，具体编号由 CSR 给出）
  val IALIGN   = Value(1)   // 0  取指地址非对齐
  val IACCESS  = Value(2)   // 1  取指访问错误
  val ILLEGAL  = Value(3)   // 2  非法指令
  val BREAK    = Value(4)   // 3  断点
  val LALIGN   = Value(5)   // 4  Load 地址非对齐
  val LACCESS  = Value(6)   // 5  Load 访问错误
  val SALIGN   = Value(7)   // 6  Store/AMO 地址非对齐
  val SACCESS  = Value(8)   // 7  Store/AMO 访问错误
  val ECALL    = Value(9)   // 8/9/11 环境调用（具体编号在陷入时按当前特权级确定）
  val IPAGE    = Value(10)  // 12 取指页错误
  val LPAGE    = Value(11)  // 13 Load 页错误
  val SPAGE    = Value(12)  // 15 Store/AMO 页错误
  val XRET     = Value(13)  // 内部：mret/sret 的串行化标记（不产生 cause）

  val excpnum: Int = values.size

  /** 位号 -> mcause/scause 的异常编号 */
  def codeInt(exc: ExcType): Int = exc match {
    case INT     => 0   // 占位：中断编号由 CSR 提供
    case IALIGN  => 0
    case IACCESS => 1
    case ILLEGAL => 2
    case BREAK   => 3
    case LALIGN  => 4
    case LACCESS => 5
    case SALIGN  => 6
    case SACCESS => 7
    case ECALL   => 11  // 占位：由 CSR 文件按当前特权级改写为 8/9/11
    case IPAGE   => 12
    case LPAGE   => 13
    case SPAGE   => 15
    case XRET    => 0
  }
}

/* ============================================================================
 *  RISC-V 中断编号（用于 mcause/scause 高位为 1 时的低位）
 * ==========================================================================*/
object IntrCode {
  val width = 5
  val SSI = 1    // Supervisor software
  val MSI = 3    // Machine software
  val STI = 5    // Supervisor timer
  val MTI = 7    // Machine timer
  val SEI = 9    // Supervisor external
  val MEI = 11   // Machine external

  val all: Seq[Int] = Seq(SSI, MSI, STI, MTI, SEI, MEI)
}

import ExcType._

class ExceptionBundle extends Bundle {
  val excpnum = ExcType.excpnum
  val excpVec = UInt(excpnum.W)
  /** 中断编号（仅 excpVec(INT) 有效时使用） */
  val intrCode = UInt(IntrCode.width.W)

  def hasException: Bool = excpVec =/= 0.U
  def isXret: Bool = excpVec(XRET.id).asBool
  def isInterrupt: Bool = excpVec(INT.id).asBool

  def has(exc: ExcType): Bool = excpVec(exc.id)

  def mergeMany(base: UInt, pairs: (Bool, ExcType)*): UInt = {
    pairs.foldLeft(base) { case (vec, (cond, exc)) =>
      vec | Mux(cond, 1.U << exc.id.U, 0.U)
    }
  }

  /** 优先级编码：位号越小优先级越高 */
  def highestPriority: UInt = {
    MuxCase(0.U, (0 until excpnum).map { i =>
      excpVec(i) -> i.U
    })
  }

  /** 最高优先级异常的 cause 编号（不含中断标志位） */
  def cause: UInt = {
    val pri = highestPriority
    MuxLookup(pri, 0.U)(ExcType.values.toSeq.map { exc =>
      exc.id.U -> ExcType.codeInt(exc).U(6.W)
    })
  }

  /** 最高优先级异常是否要把 tval 写入 mtval/stval */
  def needsTvalWrite: Bool = {
    val exc = highestPriority
    exc === IALIGN.id.U  || exc === IACCESS.id.U || exc === ILLEGAL.id.U ||
    exc === BREAK.id.U   || exc === LALIGN.id.U  || exc === LACCESS.id.U ||
    exc === SALIGN.id.U  || exc === SACCESS.id.U || exc === IPAGE.id.U   ||
    exc === LPAGE.id.U   || exc === SPAGE.id.U
  }

  def isPageFault: Bool = {
    val exc = highestPriority
    exc === IPAGE.id.U || exc === LPAGE.id.U || exc === SPAGE.id.U
  }

  /** 兼容别名：cause == ecode */
  def ecode: UInt = cause

  /** 最高优先级异常的 tval 来源 */
  def tvalSelect(pc: UInt, inst: UInt, memVaddr: UInt): UInt = {
    val exc = highestPriority
    val useInst = exc === ILLEGAL.id.U
    val useMem  = exc === LALIGN.id.U || exc === LACCESS.id.U ||
                  exc === SALIGN.id.U || exc === SACCESS.id.U ||
                  exc === LPAGE.id.U  || exc === SPAGE.id.U
    Mux(useInst, inst, Mux(useMem, memVaddr, pc))
  }
}

object ExceptionBundle {
  def default: ExceptionBundle = {
    val e = Wire(new ExceptionBundle)
    e.excpVec  := 0.U
    e.intrCode := 0.U
    e
  }
}
