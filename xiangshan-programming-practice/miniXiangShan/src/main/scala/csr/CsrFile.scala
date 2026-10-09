package minixiangshan.csr

import chisel3._
import chisel3.util._
import minixiangshan.config._
import minixiangshan.config.IntrCode
import minixiangshan.csr.CsrBundles._
import minixiangshan.difftest.DifftestCSRState

/* ============================================================================
 *  RISC-V 特权架构 CSR 文件（RV32 + M/S/U + Sv32）
 *
 *  实现的寄存器：
 *    M: mstatus misa medeleg mideleg mie mtvec mcounteren mscratch mepc
 *       mcause mtval mip pmpcfg0-3 pmpaddr0-15 mcycle minstret
 *       mvendorid marchid mimpid mhartid mconfigptr
 *    S: sstatus sie stvec scounteren sscratch sepc scause stval sip satp
 *    U: cycle time instret（只读）
 *
 *  陷入/返回：mret / sret / ecall / ebreak / 各类异常与中断
 *  陷入向量支持 direct / vectored 两种模式，支持 medeleg/mideleg 委托
 * ==========================================================================*/
class CsrFile(implicit p: Parameters) extends NSModule with HasCsrParameters {
  val io = IO(new CsrFileIo)
  val difftest = if (EnableDifftest) Some(IO(Output(new DifftestCSRState))) else None

  // ---------------- 特权级 ----------------
  val priv = RegInit(PRIV_M_VAL.U(privLen.W))

  // ---------------- mstatus ----------------
  val mstatus = RegInit(0.U.asTypeOf(new MstatusBundle))

  // ---------------- 机器级寄存器 ----------------
  val misa       = RegInit("h40141100".U(XLEN.W))  // RV32IM + S + U
  val medeleg    = RegInit(0.U(XLEN.W))
  val mideleg    = RegInit(0.U(XLEN.W))
  val mie        = RegInit(0.U(XLEN.W))
  val mtvec      = RegInit(0.U(XLEN.W))
  val mcounteren = RegInit(0.U(XLEN.W))
  val mscratch   = RegInit(0.U(XLEN.W))
  val mepc       = RegInit(0.U(XLEN.W))
  val mcause     = RegInit(0.U(XLEN.W))
  val mtval      = RegInit(0.U(XLEN.W))
  val mipSw      = RegInit(0.U(XLEN.W))   // 仅 SSIP/MSIP 可由软件写

  // ---------------- 监督级寄存器 ----------------
  val stvec      = RegInit(0.U(XLEN.W))
  val scounteren = RegInit(0.U(XLEN.W))
  val sscratch   = RegInit(0.U(XLEN.W))
  val sepc       = RegInit(0.U(XLEN.W))
  val scause     = RegInit(0.U(XLEN.W))
  val stval      = RegInit(0.U(XLEN.W))
  val satp       = RegInit(0.U(XLEN.W))

  // ---------------- PMP（占位，不做检查） ----------------
  val pmpcfg  = RegInit(VecInit(Seq.fill(4)(0.U(XLEN.W))))
  val pmpaddr = RegInit(VecInit(Seq.fill(16)(0.U(XLEN.W))))

  // ---------------- 计数器 ----------------
  val mcycle    = RegInit(0.U(TimerLen.W))   // 自由运行周期计数
  val minstret  = RegInit(0.U(TimerLen.W))
  val timer64   = RegInit(0.U(TimerLen.W))
  mcycle  := mcycle + 1.U
  timer64 := timer64 + 1.U
  when(io.commitValid) { minstret := minstret + 1.U }

  // LL/SC reservation bit
  val lrValid = RegInit(false.B)
  when(io.lrValidSet) { lrValid := true.B }
  when(io.lrValidClear) { lrValid := false.B }

  // ---------------- 软件写 ----------------
  val wen   = io.wReq.wen
  val waddr = io.wReq.addr
  val wdata = io.wReq.data

  def swWen(addr: Int): Bool = wen && (waddr === addr.U)

  val flushTlbReg = RegNext(swWen(csrAddr.satp), false.B)
  io.flushTlb := flushTlbReg

  // ---------------- mstatus / sstatus 写 ----------------
  // sstatus 的字段映射到 mstatus 的对应位
  val sstatusWData = wdata
  val isSstatusW = swWen(csrAddr.sstatus)

  when(swWen(csrAddr.mstatus)) {
    mstatus.sie  := wdata(MSTATUS_SIE_BIT).asUInt
    mstatus.mie  := wdata(MSTATUS_MIE_BIT).asUInt
    mstatus.spie := wdata(MSTATUS_SPIE_BIT).asUInt
    mstatus.mpie := wdata(MSTATUS_MPIE_BIT).asUInt
    mstatus.spp  := wdata(MSTATUS_SPP_BIT).asUInt
    mstatus.mpp  := wdata(MSTATUS_MPP_HI, MSTATUS_MPP_LO)
    mstatus.mprv := wdata(MSTATUS_MPRV_BIT).asUInt
    mstatus.sum  := wdata(MSTATUS_SUM_BIT).asUInt
    mstatus.mxr  := wdata(MSTATUS_MXR_BIT).asUInt
    mstatus.tvm  := wdata(MSTATUS_TVM_BIT).asUInt
    mstatus.tw   := wdata(MSTATUS_TW_BIT).asUInt
    mstatus.tsr  := wdata(MSTATUS_TSR_BIT).asUInt
  }.elsewhen(isSstatusW) {
    mstatus.sie  := sstatusWData(MSTATUS_SIE_BIT).asUInt
    mstatus.spie := sstatusWData(MSTATUS_SPIE_BIT).asUInt
    mstatus.spp  := sstatusWData(MSTATUS_SPP_BIT).asUInt
    mstatus.sum  := sstatusWData(MSTATUS_SUM_BIT).asUInt
    mstatus.mxr  := sstatusWData(MSTATUS_MXR_BIT).asUInt
  }

  // ---------------- 陷阱委托 / 中断使能 ----------------
  when(swWen(csrAddr.medeleg)) { medeleg := wdata }
  when(swWen(csrAddr.mideleg)) { mideleg := wdata }
  when(swWen(csrAddr.mie))     { mie := wdata }
  when(swWen(csrAddr.sie))     { mie := mie | (wdata & mideleg) }
  when(swWen(csrAddr.mtvec))   { mtvec := Cat(wdata(XLEN - 1, 2), wdata(1, 0)) }
  when(swWen(csrAddr.stvec))   { stvec := Cat(wdata(XLEN - 1, 2), wdata(1, 0)) }
  when(swWen(csrAddr.mscratch)){ mscratch := wdata }
  when(swWen(csrAddr.sscratch)){ sscratch := wdata }
  when(swWen(csrAddr.mepc))    { mepc := wdata & "hFFFFFFFC".U(XLEN.W) }
  when(swWen(csrAddr.sepc))    { sepc := wdata & "hFFFFFFFC".U(XLEN.W) }
  when(swWen(csrAddr.mcause))  { mcause := wdata }
  when(swWen(csrAddr.scause))  { scause := wdata }
  when(swWen(csrAddr.mtval))   { mtval := wdata }
  when(swWen(csrAddr.stval))   { stval := wdata }
  when(swWen(csrAddr.mip))     { mipSw := wdata & "h0000000A".U(XLEN.W) }
  when(swWen(csrAddr.sip))     { mipSw := wdata & "h0000000A".U(XLEN.W) }
  when(swWen(csrAddr.satp))    { satp := wdata }
  when(swWen(csrAddr.mcounteren)) { mcounteren := wdata }
  when(swWen(csrAddr.scounteren)) { scounteren := wdata }

  // mcycle / minstret 读写
  when(swWen(csrAddr.mcycle))   { mcycle := Cat(mcycle(TimerLen - 1, XLEN), wdata) }
  when(swWen(csrAddr.mcycleh))  { mcycle := Cat(wdata, mcycle(XLEN - 1, 0)) }
  when(swWen(csrAddr.minstret)) { minstret := Cat(minstret(TimerLen - 1, XLEN), wdata) }
  when(swWen(csrAddr.minstreth)){ minstret := Cat(wdata, minstret(XLEN - 1, 0)) }

  // PMP
  for (i <- 0 until 4) {
    when(swWen(csrAddr.pmpcfg0 + i)) { pmpcfg(i) := wdata }
  }
  for (i <- 0 until 16) {
    when(swWen(csrAddr.pmpaddr0 + i)) { pmpaddr(i) := wdata }
  }

  // ---------------- 硬件中断输入 ----------------
  // irqBus 映射：0->MSIP 1->MTIP 2->MEIP 3->SSIP 4->STIP 5->SEIP
  val mipHw = Seq(
    IRQ_MSI_BIT -> io.irqBus(0),
    IRQ_MTI_BIT -> io.irqBus(1),
    IRQ_MEI_BIT -> io.irqBus(2),
    IRQ_SSI_BIT -> io.irqBus(3),
    IRQ_STI_BIT -> io.irqBus(4),
    IRQ_SEI_BIT -> io.irqBus(5)
  ).foldLeft(0.U(XLEN.W)) { case (acc, (bit, v)) => acc | (v.asUInt << bit) }

  val mipEff = mipHw | (mipSw & "h0000000A".U(XLEN.W))

  def mipBit(code: Int): Bool = mipEff(code)
  def mieBit(code: Int): Bool = mie(code)
  def midelegBit(code: Int): Bool = mideleg(code)

  // 中断是否可以被当前特权级取走
  def intrPending(code: Int): Bool = mipBit(code) && mieBit(code)
  // 中断优先级：MEI > MSI > MTI > SEI > SSI > STI
  val intrPriority = Seq(IntrCode.MEI, IntrCode.MSI, IntrCode.MTI,
                         IntrCode.SEI, IntrCode.SSI, IntrCode.STI)
  val intrTakenVec = intrPriority.map { c =>
    intrPending(c) && (Mux(midelegBit(c), priv =/= PRIV_M_VAL.U &&
      (priv === PRIV_U_VAL.U || mstatus.sie.asBool),
      (priv =/= PRIV_M_VAL.U || mstatus.mie.asBool)))
  }
  io.hasIrq   := intrTakenVec.reduce(_ || _)
  io.intrCode := PriorityMux(
    intrTakenVec.zip(intrPriority.map(_.U(IntrCode.width.W))) :+
      (true.B -> 0.U(IntrCode.width.W)))

  // ---------------- 陷入 / 返回 ----------------
  val trap     = RegNext(io.trapReq)
  val trapPriv = trap.priv
  // ecall 的 cause 依当前特权级确定：U=8, S=9, M=11
  val causeAdj = Mux(trap.cause === 11.U,
    Mux(trapPriv === PRIV_U_VAL.U, 8.U,
      Mux(trapPriv === PRIV_S_VAL.U, 9.U, 11.U)),
    trap.cause)
  val trapDelegated = Mux(trap.isInterrupt,
    mideleg(causeAdj), medeleg(causeAdj)) && (trapPriv =/= PRIV_M_VAL.U)

  // mcause/scause：bit31 = 中断标志，[30:0] = 编号
  val causeFull = Cat(trap.isInterrupt, 0.U(25.W), causeAdj)

  when(trap.valid && trap.xret) {
    when(trap.isMret) {
      priv            := mstatus.mpp.bits
      mstatus.mie     := mstatus.mpie.bits
      mstatus.mpie    := 1.U
      mstatus.mpp     := 0.U(2.W)
      mstatus.mprv    := 0.U
    }.otherwise {
      priv            := Cat(0.U(1.W), mstatus.spp.bits)
      mstatus.sie     := mstatus.spie.bits
      mstatus.spie    := 1.U
      mstatus.spp     := 0.U
      mstatus.mprv    := 0.U
    }
  }.elsewhen(trap.valid) {
    when(trapDelegated) {
      sepc            := trap.epc
      scause          := causeFull
      stval           := trap.tval
      mstatus.spie    := mstatus.sie.bits
      mstatus.sie     := 0.U
      mstatus.spp     := trapPriv(0).asUInt
      priv            := PRIV_S_VAL.U
    }.otherwise {
      mepc            := trap.epc
      mcause          := causeFull
      mtval           := trap.tval
      mstatus.mpie    := mstatus.mie.bits
      mstatus.mie     := 0.U
      mstatus.mpp     := trapPriv
      priv            := PRIV_M_VAL.U
    }
  }

  // ---------------- 陷入环境（供重定向控制器计算入口地址） ----------------
  io.trapEnv.mtvec   := mtvec
  io.trapEnv.stvec   := stvec
  io.trapEnv.medeleg := medeleg
  io.trapEnv.mideleg := mideleg
  io.trapEnv.mepc    := mepc
  io.trapEnv.sepc    := sepc

  // ---------------- 读数据 MUX ----------------
  val sstatusView = Cat(
    0.U(12.W),
    mstatus.mxr.bits, mstatus.sum.bits,
    0.U(9.W),
    mstatus.spp.bits, 0.U(2.W),
    mstatus.spie.bits, 0.U(3.W),
    mstatus.sie.bits, 0.U(1.W)
  )
  val sieView = mie & mideleg
  val sipView = mipEff & mideleg

  val rdAddr = io.rReq.addr

  val csrMap = Seq(
    csrAddr.ustatus    -> 0.U,
    csrAddr.fcsr       -> 0.U,
    csrAddr.sstatus    -> sstatusView,
    csrAddr.sie        -> sieView,
    csrAddr.stvec      -> stvec,
    csrAddr.scounteren -> scounteren,
    csrAddr.sscratch   -> sscratch,
    csrAddr.sepc       -> sepc,
    csrAddr.scause     -> scause,
    csrAddr.stval      -> stval,
    csrAddr.sip        -> sipView,
    csrAddr.satp       -> satp,
    csrAddr.mstatus    -> mstatus.toUInt,
    csrAddr.misa       -> misa,
    csrAddr.medeleg    -> medeleg,
    csrAddr.mideleg    -> mideleg,
    csrAddr.mie        -> mie,
    csrAddr.mtvec      -> mtvec,
    csrAddr.mcounteren -> mcounteren,
    csrAddr.mstatush   -> 0.U,
    csrAddr.medelegh   -> 0.U,
    csrAddr.midelegh   -> 0.U,
    csrAddr.mscratch   -> mscratch,
    csrAddr.mepc       -> mepc,
    csrAddr.mcause     -> mcause,
    csrAddr.mtval      -> mtval,
    csrAddr.mip        -> mipEff,
    csrAddr.mtinst     -> 0.U,
    csrAddr.mtval2     -> 0.U,
    csrAddr.mcycle     -> mcycle(XLEN - 1, 0),
    csrAddr.minstret   -> minstret(XLEN - 1, 0),
    csrAddr.mcycleh    -> mcycle(TimerLen - 1, XLEN),
    csrAddr.minstreth  -> minstret(TimerLen - 1, XLEN),
    csrAddr.cycle      -> mcycle(XLEN - 1, 0),
    csrAddr.time       -> mcycle(XLEN - 1, 0),
    csrAddr.instret    -> minstret(XLEN - 1, 0),
    csrAddr.cycleh     -> mcycle(TimerLen - 1, XLEN),
    csrAddr.timeh      -> mcycle(TimerLen - 1, XLEN),
    csrAddr.instreth   -> minstret(TimerLen - 1, XLEN),
    csrAddr.mvendorid  -> 0.U,
    csrAddr.marchid    -> 0.U,
    csrAddr.mimpid     -> 0.U,
    csrAddr.mhartid    -> 0.U,
    csrAddr.mconfigptr -> 0.U
  )

  val csrRead = csrMap.map { case (addr, data) =>
    Mux(rdAddr === addr.U, data, 0.U(XLEN.W))
  }.reduce(_ | _)

  // PMP 寄存器（地址连续，单独做动态索引）
  val pmpCfgRead = (0 until 4).map { i =>
    Mux(rdAddr === (csrAddr.pmpcfg0 + i).U, pmpcfg(i), 0.U(XLEN.W))
  }.reduce(_ | _)
  val pmpAddrRead = (0 until 16).map { i =>
    Mux(rdAddr === (csrAddr.pmpaddr0 + i).U, pmpaddr(i), 0.U(XLEN.W))
  }.reduce(_ | _)

  io.rResp.data := csrRead | pmpCfgRead | pmpAddrRead

  // ---------------- 输出 ----------------
  val dataPriv = Mux(mstatus.mprv.asBool && priv === PRIV_M_VAL.U,
                     mstatus.mpp.bits, priv)
  io.priv.curPriv  := priv
  io.priv.dataPriv := dataPriv
  io.mmuCtrl.satp     := satp
  io.mmuCtrl.priv     := priv
  io.mmuCtrl.dataPriv := dataPriv
  io.mmuCtrl.sum      := mstatus.sum.bits
  io.mmuCtrl.mxr      := mstatus.mxr.bits
  io.lrValid := lrValid
  io.timerInfo.tid   := 0.U
  io.timerInfo.timer := timer64

  if (EnableDifftest) {
    val dt = difftest.get
    dt.mstatus    := mstatus.toUInt
    dt.misa       := misa
    dt.medeleg    := medeleg
    dt.mideleg    := mideleg
    dt.mie        := mie
    dt.mtvec      := mtvec
    dt.mscratch   := mscratch
    dt.mepc       := mepc
    dt.mcause     := mcause
    dt.mtval      := mtval
    dt.mip        := mipEff
    dt.sstatus    := sstatusView
    dt.stvec      := stvec
    dt.sscratch   := sscratch
    dt.sepc       := sepc
    dt.scause     := scause
    dt.stval      := stval
    dt.satp       := satp
    dt.priv       := priv
    dt.mcycle     := mcycle
    dt.minstret   := minstret
    dt.timer64    := timer64
  }
}
