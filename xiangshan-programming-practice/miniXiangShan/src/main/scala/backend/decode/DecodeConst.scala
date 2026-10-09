package minixiangshan.backend.decode

import chisel3._

/* ============================================================================
 *  RV32IM + Zicsr + 特权指令的微操作编码
 * ==========================================================================*/

/** 功能单元类型 */
object FuType {
  val width = 4
  val none   = 0.U(width.W)
  val alu    = 1.U(width.W)
  val bru    = 2.U(width.W)
  val lsu    = 3.U(width.W)
  val csr    = 4.U(width.W)
  val mul    = 5.U(width.W)
  val div    = 6.U(width.W)
  val priv   = 7.U(width.W)
}

/** 源操作数类型 */
object SrcType {
  val width = 3
  val none = 0.U(width.W)
  val reg  = 1.U(width.W)
  val pc   = 2.U(width.W)
  val imm  = 3.U(width.W)
  val zero = 4.U(width.W)
}

/** 立即数类型（RISC-V 六种基本格式 + 移位量） */
object ImmType {
  val width = 4
  val none  = 0.U(width.W)
  val i     = 1.U(width.W)  // I 型：符号扩展 imm[11:0]
  val s     = 2.U(width.W)  // S 型：store 立即数
  val b     = 3.U(width.W)  // B 型：条件分支偏移
  val u     = 4.U(width.W)  // U 型：imm[31:12] << 12
  val j     = 5.U(width.W)  // J 型：无条件跳转偏移
  val shamt = 6.U(width.W)  // 移位量（inst[24:20]，零扩展）
  val zimm  = 7.U(width.W)  // CSR 立即数：零扩展的 inst[19:15]（uimm）
}

/** ALU 操作 */
object AluOp {
  val width = 5
  val add    = 0.U(width.W)
  val sub    = 1.U(width.W)
  val sll    = 2.U(width.W)
  val slt    = 3.U(width.W)
  val sltu   = 4.U(width.W)
  val xor    = 5.U(width.W)
  val srl    = 6.U(width.W)
  val sra    = 7.U(width.W)
  val or     = 8.U(width.W)
  val and    = 9.U(width.W)
  val pass2  = 10.U(width.W)
  val custom = 11.U(width.W)
}

/** 分支/跳转操作 */
object BruOp {
  val width = 4
  val none   = 0.U(width.W)
  val jal    = 1.U(width.W)
  val jalr   = 2.U(width.W)
  val beq    = 3.U(width.W)
  val bne    = 4.U(width.W)
  val blt    = 5.U(width.W)
  val bge    = 6.U(width.W)
  val bltu   = 7.U(width.W)
  val bgeu   = 8.U(width.W)
  val custom = 9.U(width.W)
}

/** 访存操作 */
object LsuOp {
  val width = 4
  val none   = 0.U(width.W)
  val lb     = 1.U(width.W)
  val lh     = 2.U(width.W)
  val lw     = 3.U(width.W)
  val lbu    = 4.U(width.W)
  val lhu    = 5.U(width.W)
  val sb     = 6.U(width.W)
  val sh     = 7.U(width.W)
  val sw     = 8.U(width.W)
  val lrw    = 9.U(width.W)   // LR.W
  val scw    = 10.U(width.W)  // SC.W

  def isLoad(op: UInt): Bool =
    op === lb || op === lh || op === lw || op === lbu || op === lhu || op === lrw
  def isStore(op: UInt): Bool = op === sb || op === sh || op === sw || op === scw
  def isByte(op: UInt): Bool  = op === lb || op === lbu || op === sb
  def isHalf(op: UInt): Bool  = op === lh || op === lhu || op === sh
  def isWord(op: UInt): Bool  = op === lw || op === sw || op === lrw || op === scw
}

/** 内存屏障操作 */
object BarOp {
  val width = 2
  val none   = 0.U(width.W)
  val fence  = 1.U(width.W)  // FENCE
  val fenceI = 2.U(width.W)  // FENCE.I（等待 store 排空并刷新 I-Cache）
}

/**
 * 访存/缓存维护描述
 * 当前 RISC-V 版本中 FENCE.I 走 BarOp.fenceI，CacheOp 通路保留但恒无效。
 */
object CacheOpCode {
  val width          = 5
  val cacheTypeWidth = 3
  val operationWidth = 2

  val iCache      = 0.U(cacheTypeWidth.W)
  val dCache      = 1.U(cacheTypeWidth.W)
  val sharedCache = 2.U(cacheTypeWidth.W)

  val storeTag                   = 0.U(operationWidth.W)
  val indexInvalidateOrWriteback = 1.U(operationWidth.W)
  val hitInvalidateOrWriteback   = 2.U(operationWidth.W)
  val implementationDefined      = 3.U(operationWidth.W)

  def cacheType(code: UInt): UInt = code(cacheTypeWidth - 1, 0)
  def operation(code: UInt): UInt = code(width - 1, cacheTypeWidth)
  def isHitOp(op: UInt): Bool = op === hitInvalidateOrWriteback
}

/** CSR 指令类型（Zicsr） */
object CsrOp {
  val width = 3
  val none = 0.U(width.W)
  val rw   = 1.U(width.W)  // CSRRW
  val rs   = 2.U(width.W)  // CSRRS
  val rc   = 3.U(width.W)  // CSRRC
  val rwi  = 4.U(width.W)  // CSRRWI
  val rsi  = 5.U(width.W)  // CSRRSI
  val rci  = 6.U(width.W)  // CSRRCI

  /** 立即数形式：uimm 取自 rs1 字段 */
  def isImmForm(op: UInt): Bool = op === rwi || op === rsi || op === rci
  /** 无条件写：CSRRW / CSRRWI */
  def isSwap(op: UInt): Bool = op === rw || op === rwi
  def isSet(op: UInt): Bool  = op === rs || op === rsi
  def isClear(op: UInt): Bool = op === rc || op === rci
}

/** TLB 维护（sfence.vma） */
object TlbOp {
  val width = 2
  val none   = 0.U(width.W)
  val sfence = 1.U(width.W)
}

/** 乘法操作 */
object MulOp {
  val width = 3
  val none   = 0.U(width.W)
  val mul    = 1.U(width.W)  // MUL    低 32 位
  val mulh   = 2.U(width.W)  // MULH   有符号 × 有符号 高 32 位
  val mulhsu = 3.U(width.W)  // MULHSU 有符号 × 无符号 高 32 位
  val mulhu  = 4.U(width.W)  // MULHU  无符号 × 无符号 高 32 位
}

/** 除法操作 */
object DivOp {
  val width = 3
  val none = 0.U(width.W)
  val div  = 1.U(width.W)  // DIV
  val divu = 2.U(width.W)  // DIVU
  val rem  = 3.U(width.W)  // REM
  val remu = 4.U(width.W)  // REMU
}

/** 指令执行所需的最小特权级（0 = 任意，1 = S 及以上，3 = 仅 M） */
object PrivLevel {
  val width = 2
  val any = 0.U(width.W)
  val s   = 1.U(width.W)
  val m   = 3.U(width.W)
}
