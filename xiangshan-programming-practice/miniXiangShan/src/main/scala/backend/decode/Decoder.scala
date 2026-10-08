package minixiangshan.backend.decode

import chisel3._
import chisel3.util._
import minixiangshan.config.{NSModule, Parameters, ExceptionBundle, ExcType}
import minixiangshan.config.ExcType._
import minixiangshan.frontend.CtrlFlowIO
import minixiangshan.csr.CsrAddrMap

import Instructions._

/* ============================================================================
 *  RV32IM + Zicsr 译码表
 *
 *  列表元素顺序（共 25 项）：
 *    0  valid(表内匹配标记)   1  fuType
 *    2  aluOp                 3  bruOp
 *    4  lsuOp                 5  barOp
 *    6  csrOp                 7  tlbOp
 *    8  mulOp                 9  divOp
 *   10  src1Type             11  src2Type
 *   12  immType              13  rfWen
 *   14  memRead              15  memWrite
 *   16  csrWen(表内初值)     17  isBranch
 *   18  isJump               19  privLevel
 *   20  isIdle               21  waitForward
 *   22  blockBackward        23  flushOnCommit
 *   24  illegalBase
 * ==========================================================================*/
object DecodeTable {
  private val y = 1.U(1.W)
  private val n = 0.U(1.W)

  val default: List[UInt] = List(
    n, FuType.none,
    AluOp.add, BruOp.none, LsuOp.none, BarOp.none,
    CsrOp.none, TlbOp.none, MulOp.none, DivOp.none,
    SrcType.none, SrcType.none, ImmType.none,
    n, n, n, n, n, n, PrivLevel.any, n, n, n, n, y
  )

  private def ctrl(
    fuType: UInt = FuType.none,
    aluOp: UInt = AluOp.add,
    bruOp: UInt = BruOp.none,
    lsuOp: UInt = LsuOp.none,
    barOp: UInt = BarOp.none,
    csrOp: UInt = CsrOp.none,
    tlbOp: UInt = TlbOp.none,
    mulOp: UInt = MulOp.none,
    divOp: UInt = DivOp.none,
    src1Type: UInt = SrcType.reg,
    src2Type: UInt = SrcType.reg,
    immType: UInt = ImmType.none,
    rfWen: UInt = y,
    memRead: UInt = n,
    memWrite: UInt = n,
    csrWen: UInt = n,
    isBranch: UInt = n,
    isJump: UInt = n,
    privLevel: UInt = PrivLevel.any,
    isIdle: UInt = n,
    waitForward: UInt = n,
    blockBackward: UInt = n,
    flushOnCommit: UInt = n
  ): List[UInt] = List(
    y, fuType,
    aluOp, bruOp, lsuOp, barOp, csrOp, tlbOp, mulOp, divOp,
    src1Type, src2Type, immType,
    rfWen, memRead, memWrite, csrWen, isBranch, isJump, privLevel, isIdle,
    waitForward, blockBackward, flushOnCommit, n
  )

  /** 需要串行化执行的系统指令模板 */
  private def sysCtrl(
    fuType: UInt,
    csrOp: UInt = CsrOp.none,
    tlbOp: UInt = TlbOp.none,
    barOp: UInt = BarOp.none,
    src1Type: UInt = SrcType.none,
    immType: UInt = ImmType.none,
    privLevel: UInt = PrivLevel.any,
    isIdle: UInt = n,
    flushOnCommit: UInt = y
  ): List[UInt] = ctrl(
    fuType = fuType,
    csrOp = csrOp,
    tlbOp = tlbOp,
    barOp = barOp,
    src1Type = src1Type,
    immType = immType,
    rfWen = n,
    privLevel = privLevel,
    isIdle = isIdle,
    waitForward = y,
    blockBackward = y,
    flushOnCommit = flushOnCommit
  )

  val table: Array[(BitPat, List[UInt])] = Array(
    // ---------------- RV32I：整数运算 ----------------
    ADD    -> ctrl(FuType.alu, aluOp = AluOp.add),
    SUB    -> ctrl(FuType.alu, aluOp = AluOp.sub),
    SLL    -> ctrl(FuType.alu, aluOp = AluOp.sll),
    SLT    -> ctrl(FuType.alu, aluOp = AluOp.slt),
    SLTU   -> ctrl(FuType.alu, aluOp = AluOp.sltu),
    XOR    -> ctrl(FuType.alu, aluOp = AluOp.xor),
    SRL    -> ctrl(FuType.alu, aluOp = AluOp.srl),
    SRA    -> ctrl(FuType.alu, aluOp = AluOp.sra),
    OR     -> ctrl(FuType.alu, aluOp = AluOp.or),
    AND    -> ctrl(FuType.alu, aluOp = AluOp.and),

    ADDI   -> ctrl(FuType.alu, aluOp = AluOp.add,  src2Type = SrcType.imm, immType = ImmType.i),
    SLTI   -> ctrl(FuType.alu, aluOp = AluOp.slt,  src2Type = SrcType.imm, immType = ImmType.i),
    SLTIU  -> ctrl(FuType.alu, aluOp = AluOp.sltu, src2Type = SrcType.imm, immType = ImmType.i),
    XORI   -> ctrl(FuType.alu, aluOp = AluOp.xor,  src2Type = SrcType.imm, immType = ImmType.i),
    ORI    -> ctrl(FuType.alu, aluOp = AluOp.or,   src2Type = SrcType.imm, immType = ImmType.i),
    ANDI   -> ctrl(FuType.alu, aluOp = AluOp.and,  src2Type = SrcType.imm, immType = ImmType.i),
    SLLI   -> ctrl(FuType.alu, aluOp = AluOp.sll,  src2Type = SrcType.imm, immType = ImmType.shamt),
    SRLI   -> ctrl(FuType.alu, aluOp = AluOp.srl,  src2Type = SrcType.imm, immType = ImmType.shamt),
    SRAI   -> ctrl(FuType.alu, aluOp = AluOp.sra,  src2Type = SrcType.imm, immType = ImmType.shamt),

    LUI    -> ctrl(FuType.alu, aluOp = AluOp.pass2,
                   src1Type = SrcType.zero, src2Type = SrcType.imm, immType = ImmType.u),
    AUIPC  -> ctrl(FuType.alu, aluOp = AluOp.add,
                   src1Type = SrcType.pc, src2Type = SrcType.imm, immType = ImmType.u),

    // ---------------- RV32M：乘除法 ----------------
    MUL    -> ctrl(FuType.mul, mulOp = MulOp.mul),
    MULH   -> ctrl(FuType.mul, mulOp = MulOp.mulh),
    MULHSU -> ctrl(FuType.mul, mulOp = MulOp.mulhsu),
    MULHU  -> ctrl(FuType.mul, mulOp = MulOp.mulhu),
    DIV    -> ctrl(FuType.div, divOp = DivOp.div),
    DIVU   -> ctrl(FuType.div, divOp = DivOp.divu),
    REM    -> ctrl(FuType.div, divOp = DivOp.rem),
    REMU   -> ctrl(FuType.div, divOp = DivOp.remu),

    // ---------------- RV32I：访存 ----------------
    LB     -> ctrl(FuType.lsu, aluOp = AluOp.add, lsuOp = LsuOp.lb,
                   src2Type = SrcType.imm, immType = ImmType.i, memRead = y),
    LH     -> ctrl(FuType.lsu, aluOp = AluOp.add, lsuOp = LsuOp.lh,
                   src2Type = SrcType.imm, immType = ImmType.i, memRead = y),
    LW     -> ctrl(FuType.lsu, aluOp = AluOp.add, lsuOp = LsuOp.lw,
                   src2Type = SrcType.imm, immType = ImmType.i, memRead = y),
    LBU    -> ctrl(FuType.lsu, aluOp = AluOp.add, lsuOp = LsuOp.lbu,
                   src2Type = SrcType.imm, immType = ImmType.i, memRead = y),
    LHU    -> ctrl(FuType.lsu, aluOp = AluOp.add, lsuOp = LsuOp.lhu,
                   src2Type = SrcType.imm, immType = ImmType.i, memRead = y),

    SB     -> ctrl(FuType.lsu, aluOp = AluOp.add, lsuOp = LsuOp.sb,
                   src2Type = SrcType.imm, immType = ImmType.s, rfWen = n, memWrite = y),
    SH     -> ctrl(FuType.lsu, aluOp = AluOp.add, lsuOp = LsuOp.sh,
                   src2Type = SrcType.imm, immType = ImmType.s, rfWen = n, memWrite = y),
    SW     -> ctrl(FuType.lsu, aluOp = AluOp.add, lsuOp = LsuOp.sw,
                   src2Type = SrcType.imm, immType = ImmType.s, rfWen = n, memWrite = y),

    // LR.W / SC.W：需要与前后指令串行化，保证 reservation 精确
    // LR.W / SC.W 的地址就是 rs1（没有立即数偏移）
    LR_W   -> ctrl(FuType.lsu, aluOp = AluOp.add, lsuOp = LsuOp.lrw,
                   src2Type = SrcType.zero, immType = ImmType.none, memRead = y,
                   waitForward = y, blockBackward = y, flushOnCommit = y),
    SC_W   -> ctrl(FuType.lsu, aluOp = AluOp.add, lsuOp = LsuOp.scw,
                   src2Type = SrcType.zero, immType = ImmType.none, memWrite = y,
                   waitForward = y, blockBackward = y, flushOnCommit = y),

    // ---------------- RV32I：屏障 ----------------
    FENCE   -> ctrl(FuType.alu, barOp = BarOp.fence,
                    src1Type = SrcType.none, src2Type = SrcType.none,
                    rfWen = n, flushOnCommit = y),
    FENCE_I -> ctrl(FuType.alu, barOp = BarOp.fenceI,
                    src1Type = SrcType.none, src2Type = SrcType.none,
                    rfWen = n, waitForward = y, blockBackward = y,
                    flushOnCommit = y),

    // ---------------- RV32I：跳转与分支 ----------------
    JAL    -> ctrl(FuType.bru, bruOp = BruOp.jal,
                   src1Type = SrcType.none, src2Type = SrcType.imm, immType = ImmType.j,
                   isJump = y),
    JALR   -> ctrl(FuType.bru, bruOp = BruOp.jalr,
                   src1Type = SrcType.reg, src2Type = SrcType.imm, immType = ImmType.i,
                   isJump = y),
    BEQ    -> ctrl(FuType.bru, bruOp = BruOp.beq, src1Type = SrcType.reg, src2Type = SrcType.reg,
                   immType = ImmType.b, rfWen = n, isBranch = y),
    BNE    -> ctrl(FuType.bru, bruOp = BruOp.bne, src1Type = SrcType.reg, src2Type = SrcType.reg,
                   immType = ImmType.b, rfWen = n, isBranch = y),
    BLT    -> ctrl(FuType.bru, bruOp = BruOp.blt, src1Type = SrcType.reg, src2Type = SrcType.reg,
                   immType = ImmType.b, rfWen = n, isBranch = y),
    BGE    -> ctrl(FuType.bru, bruOp = BruOp.bge, src1Type = SrcType.reg, src2Type = SrcType.reg,
                   immType = ImmType.b, rfWen = n, isBranch = y),
    BLTU   -> ctrl(FuType.bru, bruOp = BruOp.bltu, src1Type = SrcType.reg, src2Type = SrcType.reg,
                   immType = ImmType.b, rfWen = n, isBranch = y),
    BGEU   -> ctrl(FuType.bru, bruOp = BruOp.bgeu, src1Type = SrcType.reg, src2Type = SrcType.reg,
                   immType = ImmType.b, rfWen = n, isBranch = y),

    // ---------------- Zicsr ----------------
    CSRRW  -> ctrl(FuType.csr, csrOp = CsrOp.rw,
                   src1Type = SrcType.reg, src2Type = SrcType.none, rfWen = y),
    CSRRS  -> ctrl(FuType.csr, csrOp = CsrOp.rs,
                   src1Type = SrcType.reg, src2Type = SrcType.none, rfWen = y),
    CSRRC  -> ctrl(FuType.csr, csrOp = CsrOp.rc,
                   src1Type = SrcType.reg, src2Type = SrcType.none, rfWen = y),
    CSRRWI -> ctrl(FuType.csr, csrOp = CsrOp.rwi, src1Type = SrcType.none, src2Type = SrcType.none,
                   immType = ImmType.zimm, rfWen = y),
    CSRRSI -> ctrl(FuType.csr, csrOp = CsrOp.rsi, src1Type = SrcType.none, src2Type = SrcType.none,
                   immType = ImmType.zimm, rfWen = y),
    CSRRCI -> ctrl(FuType.csr, csrOp = CsrOp.rci, src1Type = SrcType.none, src2Type = SrcType.none,
                   immType = ImmType.zimm, rfWen = y),

    // ---------------- 特权指令 ----------------
    Instructions.ECALL -> sysCtrl(FuType.priv),
    EBREAK     -> sysCtrl(FuType.priv),
    MRET       -> sysCtrl(FuType.priv, privLevel = PrivLevel.m, flushOnCommit = n),
    SRET       -> sysCtrl(FuType.priv, privLevel = PrivLevel.s, flushOnCommit = n),
    WFI        -> sysCtrl(FuType.priv, privLevel = PrivLevel.any, isIdle = y),
    SFENCE_VMA -> sysCtrl(FuType.priv, tlbOp = TlbOp.sfence,
                          src1Type = SrcType.reg, privLevel = PrivLevel.s)
  )

  val customTable: Array[(BitPat, List[UInt])] = Array(
    CUSTOM -> ctrl(aluOp = AluOp.custom)
  )
}

/** 纯组合逻辑译码器 */
class Decoder(implicit p: Parameters) extends NSModule {
  val io = IO(new Bundle {
    val inData = Input(new CtrlFlowIO)
    val extInt = Input(Bool())
    val out    = Output(new DecodedInst)
  })

  val inst = io.inData.instr
  val pc   = io.inData.pc

  // ===========================================================
  // 1. 字段提取（RISC-V 固定 32 bit 指令格式）
  // ===========================================================
  val rd      = inst(11, 7)
  val rs1Idx  = inst(19, 15)
  val rs2Idx  = inst(24, 20)
  // 注意：不能命名为 csrAddr —— NSModule 继承了 HasCsrParameters.csrAddr（CSR 地址表对象）
  val csrAddrField = inst(31, 20)

  // ===========================================================
  // 2. 查表译码
  // ===========================================================
  val decoded = ListLookup(inst, DecodeTable.default,
    if (customInstrEnable) DecodeTable.table ++ DecodeTable.customTable
    else DecodeTable.table)

  val isInstValid   = decoded(0).asBool
  val fuType        = decoded(1)
  val aluOp         = decoded(2)
  val bruOp         = decoded(3)
  val lsuOp         = decoded(4)
  val barOp         = decoded(5)
  val csrOp         = decoded(6)
  val tlbOp         = decoded(7)
  val mulOp         = decoded(8)
  val divOp         = decoded(9)
  val src1Type      = decoded(10)
  val src2Type      = decoded(11)
  val immType       = decoded(12)
  val rfWen         = decoded(13).asBool
  val memRead       = decoded(14).asBool
  val memWrite      = decoded(15).asBool
  val csrWenTable   = decoded(16).asBool
  val isBranch      = decoded(17).asBool
  val isJump        = decoded(18).asBool
  val privLevelTbl  = decoded(19)
  val isIdle        = decoded(20).asBool
  val waitForward   = decoded(21).asBool
  val blockBackward = decoded(22).asBool
  val flushOnCommit = decoded(23).asBool
  val isIllegalBase = decoded(24).asBool

  // ===========================================================
  // 3. 系统指令识别
  // ===========================================================
  val isEcall  = Instructions.ECALL === inst
  val isEbreak = EBREAK === inst
  val isMret   = MRET === inst
  val isSret   = SRET === inst
  val isWfi    = WFI === inst
  val isSfence = SFENCE_VMA === inst
  val isCsrInst = (csrOp =/= CsrOp.none)

  // CSR 写使能：CSRRW/CSRRWI 必写；CSRRS/CSRRC(+I) 仅在源非 x0/uimm≠0 时写
  val csrWriteReq = CsrOp.isSwap(csrOp) ||
    ((CsrOp.isSet(csrOp) || CsrOp.isClear(csrOp)) && (rs1Idx =/= 0.U))

  // ===========================================================
  // 4. 合法性检查
  // ===========================================================
  val csrReadOnly   = csrAddrField(11, 10) === "b11".U(2.W)
  val csrAddrExists = CsrAddrMap.isImplemented(csrAddrField)
  val csrIllegal    = isCsrInst && (!csrAddrExists || (csrWriteReq && csrReadOnly))
  val isIllegal     = isIllegalBase || csrIllegal

  // 指令所需最小特权级：CSR 指令取自 CSR 地址 [9:8]
  val csrPriv      = csrAddrField(9, 8)
  val privLevel    = Mux(isCsrInst, csrPriv, privLevelTbl)

  // ===========================================================
  // 5. 源寄存器有效性
  // ===========================================================
  val rs1Valid = src1Type === SrcType.reg
  // store 的写数据来自 rs2，需要读寄存器堆
  val rs2Valid = (src2Type === SrcType.reg) || memWrite
  val rdValid  = rfWen

  // ===========================================================
  // 6. 异常向量拼接
  // ===========================================================
  val excpIn = io.inData.exception
  val hasInt = io.extInt && !csrWriteReq

  val excp = Wire(new ExceptionBundle)
  val excpBase = WireDefault(0.U.asTypeOf(new ExceptionBundle))
  val excpVecMerged = excpBase.mergeMany(
    base = 0.U(ExcType.excpnum.W),
    isIllegal            -> ILLEGAL,
    isEbreak             -> BREAK,
    isEcall              -> ExcType.ECALL,
    excpIn.misalign      -> IALIGN,
    excpIn.pageFault     -> IPAGE,
    excpIn.accessFault   -> IACCESS,
    hasInt               -> INT
  )
  val xretBit = (BigInt(1) << XRET.id).U(ExcType.excpnum.W)
  excp.excpVec := excpVecMerged |
    Mux(isMret || isSret, xretBit, 0.U(ExcType.excpnum.W))
  excp.intrCode := 0.U

  // ===========================================================
  // 7. 输出
  // ===========================================================
  io.out.pc         := pc
  io.out.inst       := inst
  io.out.rd         := rd
  io.out.rs1        := rs1Idx
  io.out.rs2        := rs2Idx
  io.out.rs1Valid   := rs1Valid
  io.out.rs2Valid   := rs2Valid
  io.out.rdValid    := rdValid
  io.out.csrAddress := csrAddrField
  io.out.imm        := ImmGen(inst, immType)

  // CacheOp 通路保留但恒无效（FENCE.I 由 BarOp.fenceI 承担）
  io.out.cacheOp.valid     := false.B
  io.out.cacheOp.code      := 0.U
  io.out.cacheOp.cacheType := 0.U
  io.out.cacheOp.operation := 0.U

  io.out.ctrl.fuType        := Mux(isIllegal, FuType.csr, fuType)
  io.out.ctrl.aluOp         := aluOp
  io.out.ctrl.bruOp         := bruOp
  io.out.ctrl.lsuOp         := lsuOp
  io.out.ctrl.barOp         := barOp
  io.out.ctrl.csrOp         := csrOp
  io.out.ctrl.tlbOp         := Mux(isIllegal, TlbOp.none, tlbOp)
  io.out.ctrl.mulOp         := mulOp
  io.out.ctrl.divOp         := divOp
  io.out.ctrl.src1Type      := src1Type
  io.out.ctrl.src2Type      := src2Type
  io.out.ctrl.immType       := immType
  io.out.ctrl.rfWen         := rfWen
  io.out.ctrl.memRead       := memRead
  io.out.ctrl.memWrite      := memWrite
  io.out.ctrl.csrWen        := csrWriteReq && !isIllegal
  io.out.ctrl.isBranch      := isBranch
  io.out.ctrl.isJump        := isJump
  io.out.ctrl.privLevel     := privLevel
  io.out.ctrl.isIdle        := isIdle && !isIllegal
  io.out.ctrl.waitForward   := waitForward && !isIllegal
  io.out.ctrl.blockBackward := blockBackward && !isIllegal
  io.out.ctrl.flushOnCommit := flushOnCommit && !isIllegal

  io.out.excp    := excp
  io.out.pdInfo  := io.inData.pdInfo
  io.out.bpuInfo := io.inData.bpuInfo
}
