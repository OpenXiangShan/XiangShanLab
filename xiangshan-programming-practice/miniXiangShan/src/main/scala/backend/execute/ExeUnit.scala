

package minixiangshan.backend.execute
 
import chisel3._
import chisel3.util._
import minixiangshan.config._
import minixiangshan.backend.decode._
import minixiangshan.csr._
import minixiangshan.backend.dispatch.DispatchedInst
import minixiangshan.backend.regread.ExeReq
import minixiangshan.backend.rename.RedirectInfo
import minixiangshan.frontend.BpuUpdateReq
import minixiangshan.mmu._

class ExeResult(implicit p: Parameters) extends NSBundle {
  val uop           = new DispatchedInst
  val data          = UInt(XLEN.W)
  val redirect      = Valid(new RedirectInfo)
 
  val memValid      = Bool()
  val memRead       = Bool()
  val memWrite      = Bool()
  val memVaddr      = UInt(XLEN.W)
  val memPaddr      = UInt(XLEN.W)
  val memStoreData  = UInt(XLEN.W)
  val storeValid    = Bool()
 
  // CSR 写回信息（提交时使用）
  val csrWen        = Bool()
  val csrWaddr      = UInt(csrAddrLen.W)
  val csrWdata      = UInt(XLEN.W)

  val csrTimer    = UInt(64.W)

  val tlbFillIdx  = UInt(tlbIdxLen.W)
}
 
case class ExeUnitParams(
  hasAlu: Boolean = false,
  hasBru: Boolean = false,
  hasCsr: Boolean = false,
  hasTlb: Boolean = false,
  hasMul: Boolean = false,
  hasDiv: Boolean = false,
  hasMemAddr: Boolean = false,
  hasStd: Boolean = false
)
 
class ExeUnit(val params: ExeUnitParams)(implicit p: Parameters) extends NSModule with HasCsrParameters {
  val io = IO(new Bundle {
    val inReq        = Flipped(Decoupled(new ExeReq))
    val outResult    = Decoupled(new ExeResult)
    //val flush        = Input(Bool())
    val bruInfo    = ValidIO( new redirectInfoFromBru )    // 误预测重定向
    val bpuUpdate = Output(new BpuUpdateReq)                    // BPU 更新数据（始终发出）

    val redirectInfo    = Flipped(ValidIO( new redirectInfoToModule ))    // 误预测重定向
 
    // CSR 寄存器堆读端口（组合逻辑读）
    val csrRaddr     = Output(UInt(csrAddrLen.W)) 
    val csrRdata     = Input(UInt(XLEN.W))
    val timerInfo =        Input(new TimerBundle)

    val tlbInstr   = Valid(new TlbInstr)
    val tlbFillIdx = Input(UInt(tlbIdxLen.W))
    val currentPriv = Input(UInt(privLen.W))
  })



 
  // ================================================================
  //  输入路由
  // ================================================================
  val incomingFuType = io.inReq.bits.uop.ctrl.fuType
  val isFastPath = incomingFuType === FuType.alu || incomingFuType === FuType.bru ||
                   incomingFuType === FuType.lsu || incomingFuType === FuType.csr ||
                   incomingFuType === FuType.priv
  val isMulInst = if (params.hasMul) incomingFuType === FuType.mul else false.B
  val isDivInst = if (params.hasDiv) incomingFuType === FuType.div else false.B
 
  // ================================================================
  //  快速通道 Phase 1：流水级寄存器
  // ================================================================
  val stgValid = RegInit(false.B)
  val stgData = RegInit(0.U.asTypeOf(new ExeReq))

  val stgIsTlb = if (params.hasTlb)
    stgValid && stgData.uop.ctrl.tlbOp =/= TlbOp.none
  else false.B
  val tlbKilled = io.redirectInfo.valid && io.redirectInfo.bits.doRedirect &&
    stgData.uop.robIdxFull.isAfter(io.redirectInfo.bits.robIdx)
  val tlbNeedsExec = stgIsTlb && !tlbKilled &&
    !stgData.uop.excp.hasException && io.currentPriv =/= 0.U   // U 模式下不允许 sfence.vma
  val fastOutValid = stgValid

  val outFire  = fastOutValid && io.outResult.ready
  val stgReady = !stgValid || outFire
 
  val fastInFire = io.inReq.valid && isFastPath && stgReady

  val doRedirect = io.redirectInfo.valid && io.redirectInfo.bits.doRedirect
  val redirectRobIdx = io.redirectInfo.bits.robIdx
  //其实这个除了除法要刷，其他的都没有什么必要
  val inDoFlush = doRedirect && (
                                (fastInFire &&  io.inReq.bits.uop.robIdxFull.isAfter(redirectRobIdx))
                                // || (!outFire && stgValid && stgData.uop.robIdxFull.isAfter(redirectRobIdx))

                                //理论上讲这里outfire是不可能无效的
                                )
  //不需要去关心访存要不要刷，因为是直接在LSQ刷的，这里访存在不在无所谓，因为LSQ的表项已经被刷了

  when( false.B ){//inDoFlush ) {
    stgValid := false.B
  }.elsewhen(fastInFire) {
    stgValid := true.B
    stgData  := io.inReq.bits
  }.elsewhen(outFire) {
    stgValid := false.B
  }

  // ================================================================
  //  快速通道 Phase 2：功能单元计算
  // ================================================================
  val fuType = stgData.uop.ctrl.fuType
 
  // ── ALU ──
  val aluValid = if (params.hasAlu) stgValid && fuType === FuType.alu else false.B
  val alu = if (params.hasAlu) Module(new ALU) else null
  if (params.hasAlu) {
    alu.io.valid := aluValid
    alu.io.uop   := stgData.uop
    alu.io.rs1   := stgData.rs1Data
    alu.io.rs2   := stgData.rs2Data
  }
  val aluData = if (params.hasAlu) alu.io.result else null
 
  // ── BRU ──
  val bruValid = if (params.hasBru) stgValid && fuType === FuType.bru else false.B
  val bru = if (params.hasBru) Module(new BRU) else null
  if (params.hasBru) {
    bru.io.valid := bruValid
    bru.io.uop   := stgData.uop
    bru.io.rs1   := stgData.rs1Data
    bru.io.rs2   := stgData.rs2Data
  }
  val bruData = if (params.hasBru) bru.io.result else null
 
  // ── CSR ──
  val csrValid = if (params.hasCsr) stgValid && fuType === FuType.csr else false.B
  val csrUnit  = if (params.hasCsr) Module(new CSRUnit) else null
  if (params.hasCsr) {
    csrUnit.io.valid    := csrValid
    csrUnit.io.uop      := stgData.uop
    csrUnit.io.rs1      := stgData.rs1Data    // CSRWR/CSRXCHG: rd 旧值
    csrUnit.io.rs2      := stgData.rs2Data
    csrUnit.io.imm      := stgData.uop.imm    // CSR 立即数形式（uimm）
    csrUnit.io.csrRdata := io.csrRdata        // CSR 寄存器堆读回数据
    csrUnit.io.timerInfo := io.timerInfo
  }
  val csrData   = if (params.hasCsr) csrUnit.io.result   else null
  val csrWen    = if (params.hasCsr) csrUnit.io.csrWen   else false.B
  val csrWdata  = if (params.hasCsr) csrUnit.io.csrWdata else 0.U
  val csrTimer  = if (params.hasCsr) csrUnit.io.timerInfo.timer else 0.U
 
  // CSR 读地址：快速通道有数据时用 stgData 中的地址，否则用 0（无害）
  io.csrRaddr := Mux(stgValid && fuType === FuType.csr, stgData.uop.csrAddress, 0.U)
 
  // ── load/store 地址运算 ──
  val memAddrValid = if (params.hasMemAddr) stgValid && fuType === FuType.lsu && !stgData.uop.isStd else false.B
  val rs1Data      = if (params.hasMemAddr) stgData.rs1Data else null
  val memImm       = if (params.hasMemAddr) stgData.uop.imm else null
  val memAddr      = if (params.hasMemAddr) (rs1Data + memImm)(XLEN - 1, 0) else 0.U
 
  // ── std ──
  val stdValid = if (params.hasStd) (stgValid && fuType === FuType.lsu && stgData.uop.isStd) else false.B
  val stdData  = if (params.hasStd) stgData.rs2Data else null
 
  // ── 快速通道数据收集 ──
  val subValids = Seq(
    if (params.hasAlu) aluValid else false.B,
    if (params.hasBru) bruValid else false.B,
    if (params.hasCsr) csrValid else false.B,
    if (params.hasMemAddr) memAddrValid else false.B,
    if (params.hasStd) stdValid else false.B
  )
  val subData = Seq(
    if (params.hasAlu) aluData else 0.U,
    if (params.hasBru) bruData else 0.U,
    if (params.hasCsr) csrData else 0.U,
    if (params.hasMemAddr) memAddr else 0.U,
    if (params.hasStd) stdData else 0.U
  )
 
  val fastOutData = Mux1H(subValids, subData)
  val fastOutUop  = stgData.uop
 
  // ── 快速通道 mem 信号 ──
  val fastIsLsu = if (params.hasMemAddr || params.hasStd)
                    stgValid && fuType === FuType.lsu
                  else false.B
 
  val fastMemValid     = fastIsLsu
  val fastMemRead      = fastIsLsu && stgData.uop.ctrl.memRead
  val fastMemWrite     = fastIsLsu && stgData.uop.ctrl.memWrite
  val fastMemVaddr     = memAddr    
  val fastMemPaddr     = 0.U  
  val fastMemStoreData = stgData.rs2Data
 
  // ── 快速通道 CSR 写信号 ──
  val fastIsCsr    = if (params.hasCsr) stgValid && fuType === FuType.csr else false.B
  val fastCsrWen   = if (params.hasCsr) fastIsCsr && csrWen else false.B
  val fastCsrWaddr = Mux(fastIsCsr, stgData.uop.csrAddress, 0.U)
  val fastCsrWdata = Mux(fastIsCsr, csrWdata, 0.U)
  val fastCsrTimer = Mux(fastIsCsr, csrTimer, 0.U)

  val fastIsTlb = stgIsTlb
  io.tlbInstr.valid    := tlbNeedsExec && outFire
  io.tlbInstr.bits.cmd := stgData.uop.ctrl.tlbOp
  io.tlbInstr.bits.all    := stgData.uop.lrs1 === 0.U
  io.tlbInstr.bits.noAsid := stgData.uop.lrs2 === 0.U
  io.tlbInstr.bits.vaddr  := stgData.rs1Data
  io.tlbInstr.bits.asid   := stgData.rs2Data(asidLen - 1, 0)
 
  // ================================================================
  //  乘法器（流水线）
  // ================================================================
  val mul = if (params.hasMul) Module(new Multiplier) else null
  if (params.hasMul) {
    mul.io.in.valid := io.inReq.valid && isMulInst
    mul.io.in.bits  := io.inReq.bits
    mul.io.redirectInfo    := io.redirectInfo //io.flush
  }
  val mulOutValid = if (params.hasMul) mul.io.out.valid else false.B
 
  // ================================================================
  //  除法器（多周期）
  // ================================================================
  val div = if (params.hasDiv) Module(new Divider) else null
  if (params.hasDiv) {
    div.io.in.valid := io.inReq.valid && isDivInst
    div.io.in.bits  := io.inReq.bits
    div.io.redirectInfo    := io.redirectInfo //io.flush
  }
  val divOutValid = if (params.hasDiv) div.io.out.valid else false.B
 
  // ================================================================
  //  输入 ready 信号
  // ================================================================
  val fastReady = stgReady
  val mulReady  = if (params.hasMul) mul.io.in.ready else false.B
  val divReady  = if (params.hasDiv) div.io.in.ready else false.B
 
  io.inReq.ready := Mux(isFastPath, fastReady,
                    Mux(isMulInst,  mulReady,
                    Mux(isDivInst,  divReady, false.B)))
 
  // ================================================================
  //  输出仲裁：快速通道 > 除法通道 > 乘法通道
  // ================================================================
  val fastWins = stgValid
  val divWins  = !stgValid && divOutValid
  val mulWins  = !stgValid && !divOutValid && mulOutValid
 
  io.outResult.valid := fastOutValid || mulOutValid || divOutValid
 
  if (params.hasDiv) {
    div.io.out.ready := divWins && io.outResult.ready
  }
  if (params.hasMul) {
    mul.io.out.ready := mulWins && io.outResult.ready
  }
 
  // ── uop / data 选择 ──
  val outUopSelects = Seq(fastWins -> fastOutUop) ++
    (if (params.hasMul) Seq(mulWins -> mul.io.out.bits.uop) else Seq()) ++
    (if (params.hasDiv) Seq(divWins -> div.io.out.bits.uop) else Seq())
 
  val outDataSelects = Seq(fastWins -> fastOutData) ++
    (if (params.hasMul) Seq(mulWins -> mul.io.out.bits.data) else Seq()) ++
    (if (params.hasDiv) Seq(divWins -> div.io.out.bits.data) else Seq())
 
  io.outResult.bits.uop  := Mux1H(outUopSelects)
  io.outResult.bits.data := Mux1H(outDataSelects)
 
  io.outResult.bits.redirect.valid := false.B
  io.outResult.bits.redirect.bits  := DontCare
 
  // ── mem 字段 ──
  io.outResult.bits.memValid      := Mux1H(Seq(fastWins -> fastMemValid) ++
    (if (params.hasMul) Seq(mulWins -> false.B) else Seq()) ++
    (if (params.hasDiv) Seq(divWins -> false.B) else Seq()))
  io.outResult.bits.memRead       := Mux1H(Seq(fastWins -> fastMemRead) ++
    (if (params.hasMul) Seq(mulWins -> false.B) else Seq()) ++
    (if (params.hasDiv) Seq(divWins -> false.B) else Seq()))
  io.outResult.bits.memWrite      := Mux1H(Seq(fastWins -> fastMemWrite) ++
    (if (params.hasMul) Seq(mulWins -> false.B) else Seq()) ++
    (if (params.hasDiv) Seq(divWins -> false.B) else Seq()))
  io.outResult.bits.memVaddr      := Mux1H(Seq(fastWins -> fastMemVaddr) ++
    (if (params.hasMul) Seq(mulWins -> 0.U) else Seq()) ++
    (if (params.hasDiv) Seq(divWins -> 0.U) else Seq()))
  io.outResult.bits.memPaddr      := Mux1H(Seq(fastWins -> fastMemPaddr) ++
    (if (params.hasMul) Seq(mulWins -> 0.U) else Seq()) ++
    (if (params.hasDiv) Seq(divWins -> 0.U) else Seq()))
  io.outResult.bits.memStoreData  := Mux1H(Seq(fastWins -> fastMemStoreData) ++
    (if (params.hasMul) Seq(mulWins -> 0.U) else Seq()) ++
    (if (params.hasDiv) Seq(divWins -> 0.U) else Seq()))
  io.outResult.bits.storeValid := false.B
 
  // ── CSR 字段 ──
  io.outResult.bits.csrWen   := Mux1H(Seq(fastWins -> fastCsrWen) ++
    (if (params.hasMul) Seq(mulWins -> false.B) else Seq()) ++
    (if (params.hasDiv) Seq(divWins -> false.B) else Seq()))
  io.outResult.bits.csrWaddr := Mux1H(Seq(fastWins -> fastCsrWaddr) ++
    (if (params.hasMul) Seq(mulWins -> 0.U) else Seq()) ++
    (if (params.hasDiv) Seq(divWins -> 0.U) else Seq()))
  io.outResult.bits.csrWdata := Mux1H(Seq(fastWins -> fastCsrWdata) ++
    (if (params.hasMul) Seq(mulWins -> 0.U) else Seq()) ++
    (if (params.hasDiv) Seq(divWins -> 0.U) else Seq()))
  io.outResult.bits.csrTimer := Mux1H(Seq(fastWins -> fastCsrTimer) ++
    (if (params.hasMul) Seq(mulWins -> 0.U) else Seq()) ++
    (if (params.hasDiv) Seq(divWins -> 0.U) else Seq()))

  // RISC-V 版本不再有软件 TLB 填充指令，填充索引仅用于观测
  io.outResult.bits.tlbFillIdx := 0.U
 
  // ================================================================
  //  重定向：BRU
  // ================================================================
  //val doRedirect = io.redirectInfo.valid && io.redirectInfo.bits.doRedirect
  //val redirectRobIdx = io.redirectInfo.bits.robIdx
  val stopNewRedirect = stgValid && doRedirect &&
                     stgData.uop.robIdxFull.isAfter(redirectRobIdx)
  if (params.hasBru) {
    io.bruInfo.valid := bruValid && bru.io.bruInfo.valid && !stopNewRedirect
    io.bruInfo.bits  := bru.io.bruInfo.bits
    io.bpuUpdate := bru.io.bpuUpdate
  } else {
    io.bruInfo.valid := false.B
    io.bruInfo.bits  := DontCare
    io.bpuUpdate := DontCare
  }
 
  // ================================================================
  //  断言
  // ================================================================
  val supportedList = Seq(
    (params.hasAlu,   FuType.alu),
    (params.hasBru,   FuType.bru),
    (params.hasCsr,   FuType.csr),
    (params.hasTlb,   FuType.priv),
    (params.hasMul,   FuType.mul),
    (params.hasDiv,   FuType.div),
    (params.hasMemAddr || params.hasStd, FuType.lsu)
  ).filter(_._1).map(_._2).distinct
 
  val fuTypeSupported = supportedList.map(t => io.inReq.bits.uop.ctrl.fuType === t).reduce(_ || _)
 
  //when(io.inReq.valid && io.inReq.ready) {
  //  assert(fuTypeSupported, "ExeUnit received instruction with unsupported FuType!")
  //}
}
