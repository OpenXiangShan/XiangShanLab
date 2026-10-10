package minixiangshan.backend.redirect

import chisel3._
import chisel3.util._
import minixiangshan.config._
import minixiangshan.backend.decode._
import minixiangshan.backend.execute._
import minixiangshan.backend.rename._
import minixiangshan.csr._
import minixiangshan.config.ExcType._

/** ROB 发出的重定向请求（异常 / CSR 写 / 串行化指令） */
class RobRedirectReq(implicit p: Parameters) extends NSBundle {
  val valid         = Bool()
  val robIdx        = new RobPtr(RobSize)
  val isException   = Bool()
  val excp          = new ExceptionBundle
  val pc            = UInt(XLEN.W)
  val inst          = UInt(XLEN.W)
  val excpVaddr     = UInt(XLEN.W)
  val invalidIcache = Bool()
}

/* ============================================================================
 *  重定向控制器
 *
 *  · BRU 误预测：直接发出重定向
 *  · ROB 异常 / 串行化：等待 ROB 回滚结束后发出重定向
 *  · 陷入入口：由 mtvec / stvec（含 vectored 偏移）与 medeleg / mideleg 决定
 *  · mret / sret：返回 mepc / sepc
 *
 *  陷入相关的信息在进入回滚时锁存，从而在若干周期的回滚过程中保持稳定。
 * ==========================================================================*/
class RedirectController(implicit p: Parameters) extends NSModule with HasCsrParameters {
  val io = IO(new Bundle {
    // ── 输入 ──
    val bruRedirect     = Input(Valid(new redirectInfoFromBru))
    val robRedirect     = Input(new RobRedirectReq)
    val robRollbackDone = Input(Bool())

    /** 当前特权级（陷入时锁存，用于 ecall 编号与委托判断） */
    val currentPriv     = Input(UInt(privLen.W))
    /** CSR 给出的最高优先级中断编号 */
    val intrCode        = Input(UInt(IntrCode.width.W))
    /** CSR 的陷入环境（寄存器输出） */
    val trapEnv         = Input(new TrapEnv)

    // ── 统一重定向输出 ──
    val redirectInfo    = ValidIO(new redirectInfoToModule)

    // ── 暂停信号 ──
    val robRedirectPause     = Output(Bool())

    // ── ROB 回滚控制 ──
    val robNeedRollback   = Output(Bool())
    val robRollbackTarget = Output(new RobPtr(RobSize))

    // ── 陷入请求（送 CSR 做状态更新） ──
    val trapReq = Output(new TrapReq)
  })

  // ================================================================
  //  陷入请求（组合，来自 ROB 的重定向请求）
  // ================================================================
  val commitException = io.robRedirect.valid && io.robRedirect.isException
  val isXret          = commitException && io.robRedirect.excp.has(XRET)
  val isTrap          = commitException && !isXret
  val hasInt          = commitException && io.robRedirect.excp.has(INT)
  val isMret          = isXret && (Instructions.MRET === io.robRedirect.inst)

  val trapCause = Mux(hasInt, io.intrCode, io.robRedirect.excp.cause)
  val trapTval  = io.robRedirect.excp.tvalSelect(io.robRedirect.pc,
                   io.robRedirect.inst, io.robRedirect.excpVaddr)

  io.trapReq.valid       := isTrap || isXret
  io.trapReq.isInterrupt := hasInt
  io.trapReq.cause       := trapCause
  io.trapReq.epc         := io.robRedirect.pc
  io.trapReq.tval        := trapTval
  io.trapReq.priv        := io.currentPriv
  io.trapReq.xret        := isXret
  io.trapReq.isMret      := isMret

  // ================================================================
  //  输入寄存
  // ================================================================
  val bruReg = RegNext(io.bruRedirect)

  // ================================================================
  //  状态机
  // ================================================================
  val s_idle :: s_bru_redirect :: s_rob_rollback :: s_rob_flush :: Nil = Enum(4)
  val state = RegInit(s_idle)

  val robInfoIsException    = RegInit(false.B)
  val robInfoInvalidIcache  = RegInit(false.B)
  val robInfoExcpVec        = RegInit(0.U.asTypeOf(new ExceptionBundle))
  val robInfoPc             = RegInit(0.U(XLEN.W))
  val robInfoRobIdx         = RegInit(0.U.asTypeOf(new RobPtr(RobSize)))

  // 陷入信息（回滚期间保持稳定）
  val heldCause  = RegInit(0.U(6.W))
  val heldIsIntr = RegInit(false.B)
  val heldIsXret = RegInit(false.B)
  val heldIsMret = RegInit(false.B)
  val heldPriv   = RegInit(PRIV_M_VAL.U(privLen.W))

  def latchRobInfo(): Unit = {
    robInfoIsException   := io.robRedirect.isException
    robInfoExcpVec       := io.robRedirect.excp
    robInfoPc            := io.robRedirect.pc
    robInfoRobIdx        := io.robRedirect.robIdx
    robInfoInvalidIcache := io.robRedirect.invalidIcache
    heldCause            := trapCause
    heldIsIntr           := hasInt
    heldIsXret           := isXret
    heldIsMret           := isMret
    heldPriv             := io.currentPriv
  }

  switch(state) {
    is(s_idle) {
      when(io.robRedirect.valid) {
        state := s_rob_rollback
        latchRobInfo()
      }.elsewhen(io.bruRedirect.valid) {
        state := s_bru_redirect
      }
    }
    is(s_bru_redirect) {
      when(io.robRedirect.valid) {
        state := s_rob_rollback
        latchRobInfo()
      }.elsewhen(io.bruRedirect.valid) {
        state := s_bru_redirect
      }.otherwise {
        state := s_idle
      }
    }
    is(s_rob_rollback) {
      when(io.robRollbackDone) { state := s_rob_flush }
    }
    is(s_rob_flush) {
      state := s_idle
    }
  }

  // ================================================================
  //  暂停与回滚
  // ================================================================
  val isRollingBack = (state === s_rob_rollback)
  io.robRedirectPause  := isRollingBack
  io.robNeedRollback   := (state === s_rob_rollback)
  io.robRollbackTarget := robInfoRobIdx

  // ================================================================
  //  重定向目标
  // ================================================================
  // ecall 的 cause 依陷入时的特权级确定：U=8, S=9, M=11
  val heldCauseAdj = Mux(heldCause === 11.U,
    Mux(heldPriv === PRIV_U_VAL.U, 8.U,
      Mux(heldPriv === PRIV_S_VAL.U, 9.U, 11.U)),
    heldCause)

  val heldDelegated = Mux(heldIsIntr,
    io.trapEnv.mideleg(heldCauseAdj), io.trapEnv.medeleg(heldCauseAdj)) &&
    (heldPriv =/= PRIV_M_VAL.U)

  val mtvecBase = Cat(io.trapEnv.mtvec(XLEN - 1, 2), 0.U(2.W))
  val stvecBase = Cat(io.trapEnv.stvec(XLEN - 1, 2), 0.U(2.W))
  val vectorOff = Cat(heldCauseAdj, 0.U(2.W))

  val mtvecEntry = Mux((io.trapEnv.mtvec(1, 0) === 1.U) && heldIsIntr,
                       mtvecBase + vectorOff, mtvecBase)
  val stvecEntry = Mux((io.trapEnv.stvec(1, 0) === 1.U) && heldIsIntr,
                       stvecBase + vectorOff, stvecBase)
  val xretTarget = Mux(heldIsMret, io.trapEnv.mepc, io.trapEnv.sepc)

  val robTarget = MuxCase(
    robInfoPc + 4.U,
    Seq(
      (robInfoIsException && heldIsXret)  -> xretTarget,
      (robInfoIsException && !heldIsXret) -> Mux(heldDelegated, stvecEntry, mtvecEntry)
    )
  )

  // ================================================================
  //  统一重定向输出
  // ================================================================
  val bruRedirecting = (state === s_bru_redirect)
  val robRedirecting = (state === s_rob_flush)

  io.redirectInfo.valid              := bruRedirecting || robRedirecting
  io.redirectInfo.bits.doRedirect    := Mux(bruRedirecting, bruReg.bits.doRedirect, robRedirecting)
  io.redirectInfo.bits.flushSelf     := Mux(bruRedirecting, false.B, robInfoIsException)
  io.redirectInfo.bits.fromBru       := bruRedirecting
  io.redirectInfo.bits.snptId        := bruReg.bits.snptId
  io.redirectInfo.bits.robIdx        := Mux(bruRedirecting, bruReg.bits.robIdx, robInfoRobIdx)
  io.redirectInfo.bits.invalidIcache := Mux(bruRedirecting, false.B, robInfoInvalidIcache)
  io.redirectInfo.bits.fromRob       := robRedirecting
  io.redirectInfo.bits.target        := Mux(bruRedirecting, bruReg.bits.target, robTarget)
}
