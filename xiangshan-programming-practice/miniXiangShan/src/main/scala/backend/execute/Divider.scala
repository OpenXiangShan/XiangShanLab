package minixiangshan.backend.execute
 
import chisel3._
import chisel3.util._
import minixiangshan.config._
import minixiangshan.backend.decode._
import minixiangshan.backend.dispatch.DispatchedInst
import minixiangshan.backend.regread.ExeReq
import minixiangshan.backend.rename.RedirectInfo
 
// ═══════════════════════════════════════════════════════════════
//  多周期除法器
//
//  支持操作：DIV  (有符号除法，商)
//            REM  (有符号取余)
//            DIVU (无符号除法，商)
//            REMU (无符号取余)
//
//  算法：恢复余数法（参考 open-la500）
//        每周期处理1位，32周期完成32位除法
//
//  状态机：IDLE → COMPUTE → DONE
//
//  握手：输入仅在 IDLE 状态可接收，输出在 DONE 状态有效
// ═══════════════════════════════════════════════════════════════
class Divider(implicit p: Parameters) extends NSModule {
  val io = IO(new Bundle {
    val in    = Flipped(Decoupled(new ExeReq))
    val out   = Decoupled(new ExeResult)
   // val flush = Input(Bool())
        val redirectInfo    = Flipped(ValidIO( new redirectInfoToModule ))    // 误预测重定向

  })
  
  io.out.bits.memValid := false.B
  io.out.bits.memRead := false.B
  io.out.bits.memWrite := false.B
  io.out.bits.memVaddr := 0.U
  io.out.bits.memPaddr := 0.U
  io.out.bits.memStoreData := 0.U
  io.out.bits.storeValid := false.B
  io.out.bits.csrWen := false.B
  io.out.bits.csrWaddr := 0.U
  io.out.bits.csrWdata := 0.U
  io.out.bits.csrTimer := 0.U
  io.out.bits.tlbFillIdx := 0.U
 
  // ================================================================
  //  状态机
  // ================================================================
  val s_idle :: s_compute :: s_done :: Nil = Enum(3)
  val state = RegInit(s_idle)
 
  // ================================================================
  //  除法寄存器
  // ================================================================
  val count     = RegInit(0.U(6.W))         // 迭代计数器 0..32
  val remainder = RegInit(0.U(33.W))
  val quotient  = RegInit(0.U(XLEN.W))
  val absDiv    = RegInit(0.U(XLEN.W))
  val uop       = RegInit(0.U.asTypeOf(new DispatchedInst))
  val signA     = RegInit(false.B)
  val signB     = RegInit(false.B)
  val isMod     = RegInit(false.B)
  val isSigned  = RegInit(false.B)
  val divByZero = RegInit(false.B)
 
  // ================================================================
  //  输入握手：仅在空闲时可以接收
  // ================================================================
  io.in.ready := (state === s_idle)
 
  // ================================================================
  //  除法步进计算（组合逻辑）
  //
  //  恢复余数法（参考 open-la500 div.v）：
  //    shifted = {remainder[31:0], quotient[31]}  ← 余数左移1位，补入被除数最高位
  //    sub    = shifted - divisor                  ← 试减
  //    若减法结果非负：商位=1，余数=减法结果
  //    若减法结果为负：商位=0，余数恢复为shifted
  // ================================================================
  val absDivExt = Cat(0.U(1.W), absDiv)                       // 33位除数
  val shifted   = Cat(remainder(31, 0), quotient(XLEN - 1))   // 33位：左移并补位
  val sub       = shifted - absDivExt                          // 33位减法
 
  // ================================================================
  //  状态转移
  // ================================================================

  val doRedirect = io.redirectInfo.valid && io.redirectInfo.bits.doRedirect
  val redirectRobIdx = io.redirectInfo.bits.robIdx

 //重定向后，防止该被刷的指令污染Rob
 //只有除法&mem要做这个
 //因为只有他们延迟高，可能会发生到有新入Rob队的指令后进行污染
  val divDoFlush = (!io.in.ready && doRedirect &&  uop.robIdxFull.isAfter(redirectRobIdx)) || 
   (io.in.fire && doRedirect && io.in.bits.uop.robIdxFull.isAfter(redirectRobIdx))
 
  when(divDoFlush) {
    state := s_idle
  }.otherwise {
    switch(state) {
      // ── IDLE：等待输入 ──
      is(s_idle) {
        when(io.in.fire) {
          val a  = io.in.bits.rs1Data
          val b  = io.in.bits.rs2Data
          val op = io.in.bits.uop.ctrl.divOp
          val signed = (op === DivOp.div) || (op === DivOp.rem)
          val sA = signed && a(XLEN - 1)
          val sB = signed && b(XLEN - 1)
          val divisorZero = (b === 0.U)
 
          state     := Mux(divisorZero, s_done, s_compute)
          uop       := io.in.bits.uop
          signA     := sA
          signB     := sB
          isSigned  := signed
          isMod     := (op === DivOp.rem) || (op === DivOp.remu)
          divByZero := divisorZero
          remainder := 0.U
          quotient  := Mux(sA, (-a).asUInt, a)   // |被除数| → 商寄存器
          absDiv    := Mux(sB, (-b).asUInt, b)   // |除数|
          count     := 0.U
        }
      }
 
      // ── COMPUTE：32次迭代 ──
      is(s_compute) {
        when(count < XLEN.U) {
          when(sub(32)) {
            // 减法溢出（结果为负）：商位=0，恢复余数
            remainder := shifted
            quotient  := Cat(quotient(XLEN - 2, 0), 0.U(1.W))
          }.otherwise {
            // 减法成功（结果非负）：商位=1，更新余数
            remainder := sub
            quotient  := Cat(quotient(XLEN - 2, 0), 1.U(1.W))
          }
          count := count + 1.U
        }.otherwise {
          state := s_done
        }
      }
 
      // ── DONE：等待输出握手 ──
      is(s_done) {
        when(io.out.fire) {
          state := s_idle
        }
      }
    }
  }
 
  // ================================================================
  //  结果形成
  // ================================================================
 
  // 商的符号：被除数和除数符号不同时为负
  val quotNeg = signA =/= signB
  // 余数的符号：与被除数相同
  val remNeg  = signA
 
  // 有符号时需要修正符号
  val finalQ = Mux(isSigned && quotNeg, (-quotient).asUInt, quotient)
  val finalR = Mux(isSigned && remNeg,  (-remainder(XLEN - 1, 0)).asUInt, remainder(XLEN - 1, 0))
 
  // 除零处理（RISC-V：商为全 1，余数为被除数）
  val origDividend = Mux(signA, (-quotient).asUInt, quotient)  // quotient此时仍存|被除数|
  val resultQ = Mux(divByZero, Fill(XLEN, true.B), finalQ)
  val resultR = Mux(divByZero, origDividend, finalR)
 
  val result = Mux(isMod, resultR, resultQ)
 
  // ================================================================
  //  输出
  // ================================================================
  io.out.valid              := (state === s_done) && !divDoFlush
  io.out.bits.uop           := uop
  io.out.bits.data          := result
  io.out.bits.redirect.valid := false.B
  io.out.bits.redirect.bits  := DontCare
}
