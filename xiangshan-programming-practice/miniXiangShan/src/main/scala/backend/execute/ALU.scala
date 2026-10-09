package minixiangshan.backend.execute
 
import chisel3._
import chisel3.util._
import minixiangshan.config._
import minixiangshan.backend.decode._
import minixiangshan.backend.dispatch.DispatchedInst
 
// ═══════════════════════════════════════════════════════════════
//  ALU 执行单元
//
//  支持操作：add, sub, sll, slt, sltu, xor, srl, sra, or, and, pass2
//  单拍组合逻辑完成
// ═══════════════════════════════════════════════════════════════
class ALU(implicit p: Parameters) extends NSModule {
  val io = IO(new Bundle {
    val valid  = Input(Bool())
    val uop    = Input(new DispatchedInst)
    val rs1    = Input(UInt(XLEN.W))
    val rs2    = Input(UInt(XLEN.W))
    val result = Output(UInt(XLEN.W))
  })
 
  val op    = io.uop.ctrl.aluOp

  val src1 = WireDefault(0.U(XLEN.W))
  switch(io.uop.ctrl.src1Type) {
    is(SrcType.reg)  { src1 := io.rs1 }
    is(SrcType.pc)   { src1 := io.uop.pc }
    is(SrcType.imm)  { src1 := io.uop.imm }
    is(SrcType.zero) { src1 := 0.U }
  }
  
  val src2 = WireDefault(0.U(XLEN.W))
  switch(io.uop.ctrl.src2Type) {
    is(SrcType.reg)  { src2 := io.rs2 }
    is(SrcType.pc)   { src2 := io.uop.pc }
    is(SrcType.imm)  { src2 := io.uop.imm }
    is(SrcType.zero) { src2 := 0.U }
  }

  // ── 加减法 ──
  val addResult  = (src1 + src2)(XLEN - 1, 0)
  val subResult  = (src1 - src2)(XLEN - 1, 0)
 
  // ── 比较类 ──
  val sltResult  = Mux(src1.asSInt < src2.asSInt, 1.U(XLEN.W), 0.U(XLEN.W))
  val sltuResult = Mux(src1 < src2, 1.U(XLEN.W), 0.U(XLEN.W))
 
  // ── 逻辑类 ──
  val andResult  = src1 & src2
  val orResult   = src1 | src2
  val xorResult  = src1 ^ src2
 
  // ── 移位类 ──
  // RISC-V（RV32）移位量只取低 5 位
  val shamt = src2(4, 0)
  val sllResult  = src1 << shamt
  val srlResult  = src1 >> shamt
  val sraResult  = (src1.asSInt >> shamt).asUInt
 
  // ── 直通 ──
  val pass2Result = src2

  // ── 结果选择 ──
  val customUnit = if (customInstrEnable) {
    val unit = Module(new CustomAluUnit)
    unit.io.valid := io.valid && op === AluOp.custom
    unit.io.op := op
    unit.io.inst := io.uop.inst
    unit.io.rs1 := src1
    unit.io.rs2 := src2
    Some(unit)
  } else {
    None
  }

  io.result := MuxCase(0.U(XLEN.W), Seq(
    (op === AluOp.add  ) -> addResult,
    (op === AluOp.sub  ) -> subResult,
    (op === AluOp.slt  ) -> sltResult,
    (op === AluOp.sltu ) -> sltuResult,
    (op === AluOp.and  ) -> andResult,
    (op === AluOp.or   ) -> orResult,
    (op === AluOp.xor  ) -> xorResult,
    (op === AluOp.sll  ) -> sllResult(XLEN - 1, 0),
    (op === AluOp.srl  ) -> srlResult(XLEN - 1, 0),
    (op === AluOp.sra  ) -> sraResult(XLEN - 1, 0),
    (op === AluOp.pass2) -> pass2Result
  ) ++ customUnit.toSeq.map(unit => (op === AluOp.custom) -> unit.io.result))
}
