package minixiangshan.backend.execute
 
import chisel3._
import chisel3.util._
import minixiangshan.config._
import minixiangshan.backend.decode._
import minixiangshan.backend.dispatch.DispatchedInst
import minixiangshan.backend.regread.ExeReq
import minixiangshan.backend.rename.RedirectInfo
 
// ═══════════════════════════════════════════════════════════════
//  流水线乘法器
//
//  支持操作：MUL    (低 32 位)
//            MULH   (有符号 × 有符号，高 32 位)
//            MULHSU (有符号 × 无符号，高 32 位)
//            MULHU  (无符号 × 无符号，高 32 位)
//
//  2级流水线：
//    S1: 锁存原始输入（切断旁路网络与乘法器的组合耦合）
//    S2: 乘法运算 + 结果选择 + 输出握手
//
//  设计参考：
//    - open-la500 的 Booth 编码 + Wallace 树思想
//    - 香山的符号修正策略
//    - iFuCore 的流水线打拍结构
// ═══════════════════════════════════════════════════════════════
class Multiplier(implicit p: Parameters) extends NSModule {
  val io = IO(new Bundle {
    val in    = Flipped(Decoupled(new ExeReq))
    val out   = Decoupled(new ExeResult)
    val redirectInfo = Flipped(ValidIO(new redirectInfoToModule))
  })

  io.out.bits.memValid      := false.B
  io.out.bits.memRead       := false.B
  io.out.bits.memWrite      := false.B
  io.out.bits.memVaddr      := 0.U
  io.out.bits.memPaddr      := 0.U
  io.out.bits.memStoreData  := 0.U
  io.out.bits.storeValid    := false.B
  io.out.bits.csrWen        := false.B
  io.out.bits.csrWaddr      := 0.U
  io.out.bits.csrWdata      := 0.U
  io.out.bits.csrTimer      := 0.U
  io.out.bits.tlbFillIdx    := 0.U
 
  // ================================================================
  //  S1 寄存器：锁存原始输入（不计算）
  // ================================================================
  val s1_valid    = RegInit(false.B)
  val s1_a        = RegInit(0.U(XLEN.W))
  val s1_b        = RegInit(0.U(XLEN.W))
  val s1_uop      = RegInit(0.U.asTypeOf(new DispatchedInst))
  val s1_mulOp    = RegInit(MulOp.none)
 
  // ================================================================
  //  S2 寄存器：锁存乘法结果 + 输出
  // ================================================================
  val s2_valid = RegInit(false.B)
  val s2_uop   = RegInit(0.U.asTypeOf(new DispatchedInst))
  val s2_data  = RegInit(0.U(XLEN.W))
 
  // ================================================================
  //  流水线反压控制
  // ================================================================
  val s2_fire = s2_valid && io.out.ready
  val s1_fire = s1_valid && (!s2_valid || s2_fire)
  val in_fire = io.in.valid && (!s1_valid || s1_fire)
 
  io.in.ready := !s1_valid || s1_fire
 
  // ================================================================
  //  重定向
  // ================================================================
  val doRedirect     = io.redirectInfo.valid && io.redirectInfo.bits.doRedirect
  val redirectRobIdx = io.redirectInfo.bits.robIdx
 
  val inDoFlush = doRedirect && io.in.bits.uop.robIdxFull.isAfter(redirectRobIdx)
 
  // ================================================================
  //  S1 更新：只锁存输入
  // ================================================================
  val s1DoFlush = doRedirect && s1_valid && s1_uop.robIdxFull.isAfter(redirectRobIdx)
 
  when(inDoFlush) {
    s1_valid := false.B
  }.elsewhen(in_fire) {
    s1_valid    := !inDoFlush
    s1_a        := io.in.bits.rs1Data
    s1_b        := io.in.bits.rs2Data
    s1_uop      := io.in.bits.uop
    s1_mulOp    := io.in.bits.uop.ctrl.mulOp
  }.elsewhen(s1_fire) {
    s1_valid := false.B
  }
 
  // ================================================================
  //  S1→S2：乘法运算（在两个寄存器级之间）
  //
  //  策略：
  //  1. 先计算无符号乘积 s1_a * s1_b
  //  2. 有符号乘积通过符号修正推导：
  //     signed = unsigned - a[31]*b*2^32 - b[31]*a*2^32
  //     MULHSU 只对 a 做修正
  //  3. MUL 低 32 位在有无符号下结果一致，无需区分
  // ================================================================
  val prodUnsigned = s1_a * s1_b
 
  val corrA = Mux(s1_a(XLEN - 1), Cat(s1_b, 0.U(XLEN.W)), 0.U((2 * XLEN).W))
  val corrB = Mux(s1_b(XLEN - 1), Cat(s1_a, 0.U(XLEN.W)), 0.U((2 * XLEN).W))
  val prodSigned   = prodUnsigned - corrA - corrB   // MULH
  val prodSignedUn = prodUnsigned - corrA           // MULHSU
 
  val prod = MuxCase(prodUnsigned, Seq(
    (s1_mulOp === MulOp.mulh)   -> prodSigned,
    (s1_mulOp === MulOp.mulhsu) -> prodSignedUn
  ))
 
  // 结果选择：MUL→低32位，MULH/MULHU→高32位
  val s1_result = Mux(s1_mulOp === MulOp.mul, prod(XLEN - 1, 0), prod(2 * XLEN - 1, XLEN))
 
  // ================================================================
  //  S2 更新：锁存乘法结果
  // ================================================================
  val s2DoFlush = doRedirect && s1_valid && s1_uop.robIdxFull.isAfter(redirectRobIdx)
 
  when(s1DoFlush) {
    s2_valid := false.B
  }.elsewhen(s1_fire) {
    s2_valid := true.B
    s2_uop   := s1_uop
    s2_data  := s1_result
  }.elsewhen(s2_fire) {
    s2_valid := false.B
  }
 
  // ================================================================
  //  输出
  // ================================================================
  io.out.valid               := s2_valid
  io.out.bits.uop            := s2_uop
  io.out.bits.data           := s2_data
  io.out.bits.redirect.valid := false.B
  io.out.bits.redirect.bits  := DontCare
}
