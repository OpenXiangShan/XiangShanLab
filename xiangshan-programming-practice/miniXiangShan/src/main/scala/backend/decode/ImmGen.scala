package minixiangshan.backend.decode

import chisel3._
import chisel3.util._

/* ============================================================================
 *  RISC-V 立即数生成
 *    I 型：imm[11:0] 符号扩展
 *    S 型：imm[11:5] | imm[4:0]，符号扩展
 *    B 型：imm[12|10:5|4:1|11]，最低位补 0
 *    U 型：imm[31:12] << 12
 *    J 型：imm[20|10:1|11|19:12]，最低位补 0
 * ==========================================================================*/
object ImmGen {
  def apply(inst: UInt, immType: UInt): UInt = {
    // ---- I 型 ----
    val immI = Cat(Fill(20, inst(31)), inst(31, 20))

    // ---- S 型 ----
    val immS = Cat(Fill(20, inst(31)), inst(31, 25), inst(11, 7))

    // ---- B 型 ----
    val immB = Cat(Fill(19, inst(31)), inst(31), inst(7),
                   inst(30, 25), inst(11, 8), 0.U(1.W))

    // ---- U 型 ----
    val immU = Cat(inst(31, 12), 0.U(12.W))

    // ---- J 型 ----
    val immJ = Cat(Fill(11, inst(31)), inst(31), inst(19, 12),
                   inst(20), inst(30, 21), 0.U(1.W))

    // ---- 移位量（inst[24:20]，零扩展） ----
    val immShamt = Cat(0.U(27.W), inst(24, 20))

    // ---- CSR uimm（inst[19:15]，零扩展） ----
    val immZimm = Cat(0.U(27.W), inst(19, 15))

    MuxLookup(immType, 0.U(32.W))(Seq(
      ImmType.i     -> immI,
      ImmType.s     -> immS,
      ImmType.b     -> immB,
      ImmType.u     -> immU,
      ImmType.j     -> immJ,
      ImmType.shamt -> immShamt,
      ImmType.zimm  -> immZimm
    ))
  }
}
