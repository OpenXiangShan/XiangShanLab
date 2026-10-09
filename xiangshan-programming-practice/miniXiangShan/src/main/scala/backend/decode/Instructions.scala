package minixiangshan.backend.decode

import chisel3.util.BitPat

/* ============================================================================
 *  RV32IM + Zicsr 指令编码（The RISC-V Instruction Set Manual, Volume I: Unprivileged ISA）
 *
 *  字段布局（低地址在右）：
 *    funct7[31:25] | rs2[24:20] | rs1[19:15] | funct3[14:12] | rd[11:7] | opcode[6:0]
 * ==========================================================================*/
object Instructions {
  // ---- opcode ----
  private val OPC_LOAD     = "0000011"
  private val OPC_MISC_MEM = "0001111"
  private val OPC_OP_IMM   = "0010011"
  private val OPC_AUIPC    = "0010111"
  private val OPC_STORE    = "0100011"
  private val OPC_AMO      = "0101111"
  private val OPC_OP       = "0110011"
  private val OPC_LUI      = "0110111"
  private val OPC_BRANCH   = "1100011"
  private val OPC_JALR     = "1100111"
  private val OPC_JAL      = "1101111"
  private val OPC_SYSTEM   = "1110011"

  private def pat(s: String): BitPat = BitPat("b" + s)

  /** R 型：funct7 | rs2 | rs1 | funct3 | rd | opcode */
  private def rType(funct7: String, funct3: String): BitPat =
    pat(funct7 + "?????" + "?????" + funct3 + "?????" + OPC_OP)

  /** I 型：imm[11:0] | rs1 | funct3 | rd | opcode */
  private def iType(funct3: String, opcode: String): BitPat =
    pat("????????????" + "?????" + funct3 + "?????" + opcode)

  /** S/B 型：imm[11:5] | rs2 | rs1 | funct3 | rd | opcode */
  private def sType(funct3: String, opcode: String): BitPat =
    pat("???????" + "?????" + "?????" + funct3 + "?????" + opcode)

  // ----------------------------------------------------------------
  // RV32I：寄存器-寄存器运算（OP）
  // ----------------------------------------------------------------
  val ADD    = rType("0000000", "000")
  val SUB    = rType("0100000", "000")
  val SLL    = rType("0000000", "001")
  val SLT    = rType("0000000", "010")
  val SLTU   = rType("0000000", "011")
  val XOR    = rType("0000000", "100")
  val SRL    = rType("0000000", "101")
  val SRA    = rType("0100000", "101")
  val OR     = rType("0000000", "110")
  val AND    = rType("0000000", "111")

  // ----------------------------------------------------------------
  // RV32I：立即数运算（OP-IMM）
  // ----------------------------------------------------------------
  val ADDI   = iType("000", OPC_OP_IMM)
  val SLTI   = iType("010", OPC_OP_IMM)
  val SLTIU  = iType("011", OPC_OP_IMM)
  val XORI   = iType("100", OPC_OP_IMM)
  val ORI    = iType("110", OPC_OP_IMM)
  val ANDI   = iType("111", OPC_OP_IMM)
  // 移位立即数：funct7 区分逻辑/算术右移
  val SLLI   = pat("0000000" + "?????" + "?????" + "001" + "?????" + OPC_OP_IMM)
  val SRLI   = pat("0000000" + "?????" + "?????" + "101" + "?????" + OPC_OP_IMM)
  val SRAI   = pat("0100000" + "?????" + "?????" + "101" + "?????" + OPC_OP_IMM)

  // ----------------------------------------------------------------
  // RV32I：高位立即数 / 跳转
  // ----------------------------------------------------------------
  val LUI    = pat("????????????????????" + "?????" + OPC_LUI)   // U 型
  val AUIPC  = pat("????????????????????" + "?????" + OPC_AUIPC) // U 型
  val JAL    = pat("????????????????????" + "?????" + OPC_JAL)   // J 型
  val JALR   = iType("000", OPC_JALR)

  // ----------------------------------------------------------------
  // RV32I：条件分支（B 型）
  // ----------------------------------------------------------------
  val BEQ    = sType("000", OPC_BRANCH)
  val BNE    = sType("001", OPC_BRANCH)
  val BLT    = sType("100", OPC_BRANCH)
  val BGE    = sType("101", OPC_BRANCH)
  val BLTU   = sType("110", OPC_BRANCH)
  val BGEU   = sType("111", OPC_BRANCH)

  // ----------------------------------------------------------------
  // RV32I：访存
  // ----------------------------------------------------------------
  val LB     = iType("000", OPC_LOAD)
  val LH     = iType("001", OPC_LOAD)
  val LW     = iType("010", OPC_LOAD)
  val LBU    = iType("100", OPC_LOAD)
  val LHU    = iType("101", OPC_LOAD)

  val SB     = sType("000", OPC_STORE)
  val SH     = sType("001", OPC_STORE)
  val SW     = sType("010", OPC_STORE)

  // ----------------------------------------------------------------
  // RV32I：内存屏障
  // ----------------------------------------------------------------
  val FENCE   = pat("000000000000" + "?????" + "000" + "00000" + OPC_MISC_MEM)
  val FENCE_I = pat("000000000000" + "?????" + "001" + "00000" + OPC_MISC_MEM)

  // ----------------------------------------------------------------
  // RV32I：系统指令（SYSTEM）
  // ----------------------------------------------------------------
  val ECALL    = pat("000000000000" + "00000" + "000" + "00000" + OPC_SYSTEM)
  val EBREAK   = pat("000000000001" + "00000" + "000" + "00000" + OPC_SYSTEM)
  val URET     = pat("000000000010" + "00000" + "000" + "00000" + OPC_SYSTEM)
  val SRET     = pat("000100000010" + "00000" + "000" + "00000" + OPC_SYSTEM)
  val MRET     = pat("001100000010" + "00000" + "000" + "00000" + OPC_SYSTEM)
  val WFI      = pat("000100000101" + "00000" + "000" + "00000" + OPC_SYSTEM)
  // sfence.vma rs1, rs2  ->  funct7 = 0001001
  val SFENCE_VMA = pat("0001001" + "?????" + "?????" + "000" + "00000" + OPC_SYSTEM)

  // CSR 指令（Zicsr）：csr[31:20] | rs1 | funct3 | rd | opcode
  val CSRRW  = pat("????????????" + "?????" + "001" + "?????" + OPC_SYSTEM)
  val CSRRS  = pat("????????????" + "?????" + "010" + "?????" + OPC_SYSTEM)
  val CSRRC  = pat("????????????" + "?????" + "011" + "?????" + OPC_SYSTEM)
  val CSRRWI = pat("????????????" + "?????" + "101" + "?????" + OPC_SYSTEM)
  val CSRRSI = pat("????????????" + "?????" + "110" + "?????" + OPC_SYSTEM)
  val CSRRCI = pat("????????????" + "?????" + "111" + "?????" + OPC_SYSTEM)

  // ----------------------------------------------------------------
  // RV32M：乘除法（OP）
  // ----------------------------------------------------------------
  val MUL    = rType("0000001", "000")
  val MULH   = rType("0000001", "001")
  val MULHSU = rType("0000001", "010")
  val MULHU  = rType("0000001", "011")
  val DIV    = rType("0000001", "100")
  val DIVU   = rType("0000001", "101")
  val REM    = rType("0000001", "110")
  val REMU   = rType("0000001", "111")

  // ----------------------------------------------------------------
  // RV32A：LR.W / SC.W（沿用原有 reservation 通路）
  //   funct5[31:27] | aq[26] | rl[25] | rs2 | rs1 | funct3 | rd | opcode
  // ----------------------------------------------------------------
  val LR_W = pat("00010" + "??" + "00000" + "?????" + "010" + "?????" + OPC_AMO)
  val SC_W = pat("00011" + "??" + "?????" + "?????" + "010" + "?????" + OPC_AMO)

  // ----------------------------------------------------------------
  // 自定义指令（默认关闭）
  // ----------------------------------------------------------------
  val CUSTOM = pat("0000000" + "?????" + "?????" + "000" + "?????" + "0001011")
}
