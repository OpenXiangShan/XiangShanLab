package minixiangshan.frontend
 
import chisel3._
import chisel3.util._
import minixiangshan.config.Parameters
import minixiangshan.config._
import minixiangshan.mmu._
import minixiangshan.config.NSModule
import minixiangshan.config.NSBundle
import minixiangshan.frontend.icache._

class Predecoder(implicit p: Parameters) extends NSModule {
  val io = IO(new Bundle {
    val flush         = Input(Bool())
    val icacheResp    = Flipped(Decoupled(new IcacheResp))
    val bpuInfo       = Input(new bpuInfoBundle)
    val bpuInfoValid  = Input(Bool())
    val out           = Decoupled(new PredecodeResp)
  })
  diffDontTouch(io.bpuInfo)
  diffDontTouch(io.bpuInfoValid)
  diffDontTouch(io.out)
 
  // ================================================================
  // 第一部分：输入流水线寄存器 —— 只存原始数据，不计算
  // ================================================================
 
  val s_valid    = RegInit(false.B)
  val s_instrs   = RegInit(VecInit(Seq.fill(fetchWidth)(0.U(32.W))))
  val s_valids   = RegInit(VecInit(Seq.fill(fetchWidth)(false.B)))
  val s_addr     = RegInit(0.U(32.W))
  val s_uncached = RegInit(false.B)
  val s_mmuError = RegInit(0.U.asTypeOf(new FetchMmuError))
  val s_bpu      = RegInit(0.U.asTypeOf(new bpuInfoBundle))
 
  val inFire  = io.icacheResp.valid && io.icacheResp.ready && io.bpuInfoValid
  val outFire = io.out.valid && io.out.ready
 
  io.icacheResp.ready := !s_valid || outFire
 
  when(io.flush) {
    s_valid := false.B
  }.elsewhen(inFire) {
    s_valid    := true.B
    s_instrs   := io.icacheResp.bits.instrs
    s_valids   := io.icacheResp.bits.instvalids
    s_addr     := io.icacheResp.bits.addr
    s_uncached := io.icacheResp.bits.uncached
    s_mmuError := io.icacheResp.bits.mmu_error
    s_bpu      := io.bpuInfo
  }.elsewhen(outFire) {
    s_valid := false.B
  }
 
  // ================================================================
  // 第二部分：组合逻辑 —— 预译码与校验
  // ================================================================
 
  // ---- Step 1: 逐条指令基础预译码（纯组合，每信号赋值一次） ----
  val pc         = Wire(Vec(fetchWidth, UInt(32.W)))
  val isJalInst  = Wire(Vec(fetchWidth, Bool()))   
  val isJirlInst = Wire(Vec(fetchWidth, Bool()))   
  val isBrInst   = Wire(Vec(fetchWidth, Bool()))   
  val isCfiInst  = Wire(Vec(fetchWidth, Bool()))   
  val jalTgt     = Wire(Vec(fetchWidth, UInt(32.W)))  
 
  for (i <- 0 until fetchWidth) {
    val instr  = s_instrs(i)
    val opcode = instr(6, 0)
    val v      = s_valids(i)
 
    pc(i)         := s_addr + (i * 4).U
    isJalInst(i)  := v && (opcode === RiscVOpcodes.OPC_JAL)
    isJirlInst(i) := v && (opcode === RiscVOpcodes.OPC_JALR)
    isBrInst(i)   := v && RiscVOpcodes.isBranchOpcode(opcode)
    isCfiInst(i)  := isJalInst(i) || isJirlInst(i) || isBrInst(i)
 
    // J 型立即数：imm[20|10:1|11|19:12]
    val imm20         = Cat(instr(31), instr(19, 12), instr(20), instr(30, 21))
    val jalOffsetSext = Cat(Fill(11, instr(31)), imm20, 0.U(1.W))
    jalTgt(i)        := Mux(v, pc(i) + jalOffsetSext, 0.U)
  }
 
  // ---- Step 2: 前缀扫描找第一条强跳转 (B/BL) ----
  val priorJal = Wire(Vec(fetchWidth + 1, Bool()))
  priorJal(0)  := false.B
  for (i <- 0 until fetchWidth) {
    priorJal(i + 1) := priorJal(i) || isJalInst(i)
  }
 
  val isFirstJal = Wire(Vec(fetchWidth, Bool()))
  for (i <- 0 until fetchWidth) {
    isFirstJal(i) := isJalInst(i) && !priorJal(i)
  }
 
  val hasJal         = priorJal(fetchWidth)
  val firstJalIdx    = MuxCase(0.U, (0 until fetchWidth).map(i => isFirstJal(i) -> i.U))
  val firstJalTarget = MuxCase(0.U, (0 until fetchWidth).map(i => isFirstJal(i) -> jalTgt(i)))
  val firstJalPC     = MuxCase(0.U, (0 until fetchWidth).map(i => isFirstJal(i) -> pc(i)))
 
  // ---- Step 3: BPU 预测分解 ----
  val predTaken  = s_bpu.taken
  val predIdx    = s_bpu.takenOffset
  val predTarget = s_bpu.target
 
  val predIsJal     = MuxCase(false.B, (0 until fetchWidth).map(i => (i.U === predIdx) -> isJalInst(i)))
  val predIsCfi     = MuxCase(false.B, (0 until fetchWidth).map(i => (i.U === predIdx) -> isCfiInst(i)))
  val predJalTarget = MuxCase(0.U,    (0 until fetchWidth).map(i => (i.U === predIdx) -> jalTgt(i)))
  val predPC        = MuxCase(0.U,    (0 until fetchWidth).map(i => (i.U === predIdx) -> pc(i)))
 
  val hasJalBeforePred = (0 until fetchWidth)
    .map(i => isJalInst(i) && i.U < predIdx)
    .reduce(_ || _)
 
  val jalTargetCorrect = predIsJal && (predTarget === predJalTarget)
 
  // ---- Step 4: 场景判定 ----
  val scenario = MuxCase(0.U(3.W), Seq(
    (!predTaken && !hasJal)                                            -> 0.U,
    (!predTaken && hasJal)                                             -> 1.U,
    (predTaken && hasJalBeforePred)                                    -> 2.U,
    (predTaken && !hasJalBeforePred && predIsJal && jalTargetCorrect)  -> 3.U,
    (predTaken && !hasJalBeforePred && predIsJal && !jalTargetCorrect) -> 4.U,
    (predTaken && !hasJalBeforePred && !predIsJal && predIsCfi)        -> 5.U,
    (predTaken && !hasJalBeforePred && !predIsCfi)                     -> 6.U
  ))
 
  // ---- Step 5: 前端重定向（修改后加入打拍逻辑） ----
  val needRedirect = scenario === 1.U || scenario === 2.U ||
                     scenario === 4.U || scenario === 6.U || ( s_uncached && s_valid )
 
  val redirectTarget = MuxCase(0.U, Seq(
    ( s_uncached && s_valid && !isFirstJal(0)) -> (s_addr + 4.U),
    ( s_uncached && s_valid && isFirstJal(0)) -> firstJalTarget,
    (scenario === 1.U || scenario === 2.U) -> firstJalTarget,
    (scenario === 4.U)                     -> predJalTarget,
    (scenario === 6.U)                     -> (predPC + 4.U)
  ))
 
  // 【修改逻辑】：注册一拍的重定向信号
  val pendingRedirect = RegInit(false.B)
  val pendingRedirectTarget = RegInit(0.U(32.W))

  // 当指令成功发射(outFire)且需要重定向时，锁存信号并延迟一拍发出
  when(outFire && needRedirect) {
    pendingRedirect := true.B
    pendingRedirectTarget := redirectTarget
  } .elsewhen(pendingRedirect) {
    // 脉冲一拍后自动清除
    pendingRedirect := false.B
  }
 
  val feRedirect = Wire(new FrontendRedirect)
  feRedirect.valid  := pendingRedirect
  feRedirect.target := pendingRedirectTarget
 
  // ---- Step 6: BPU 更新 ----
  val needBpuUpdate = scenario === 1.U || scenario === 2.U ||
                      scenario === 3.U || scenario === 4.U || scenario === 6.U
 
  val bpuUpdatePC     = Mux(scenario === 1.U || scenario === 2.U, firstJalPC, predPC)
  val bpuUpdateTarget = Mux(scenario === 1.U || scenario === 2.U, firstJalTarget,
                       Mux(scenario === 6.U, predPC + 4.U, predJalTarget))
  val bpuUpdateTaken  = Mux(scenario === 6.U, false.B, true.B)
  val bpuUpdateIsJal  = scenario === 1.U || scenario === 2.U ||
                        scenario === 3.U || scenario === 4.U
  val bpuUpdateOffset = Mux(scenario === 1.U || scenario === 2.U,
                       firstJalPC(fetchOffsetBits + 1, 2),
                       predPC(fetchOffsetBits + 1, 2))
 
  val bpuUpdate = Wire(new BpuUpdateReq)
  bpuUpdate.valid         := s_valid && needBpuUpdate
  bpuUpdate.validEntry    := scenario =/= 6.U
  bpuUpdate.pc            := bpuUpdatePC
  bpuUpdate.target        := bpuUpdateTarget
  bpuUpdate.taken         := bpuUpdateTaken
  bpuUpdate.isJalr        := false.B
  bpuUpdate.isJal         := bpuUpdateIsJal
  bpuUpdate.isCall        := false.B
  bpuUpdate.isRet         := false.B
  bpuUpdate.offset        := bpuUpdateOffset
  bpuUpdate.rasTop        := s_bpu.meta.rasTop
  bpuUpdate.oldPhtCounter := Mux(scenario === 1.U || scenario === 2.U, 0.U,
                               ( s_bpu.meta.phtCounter)
                             )  
 
  // ---- Step 7: 入队掩码（截断逻辑） ----
  val truncateIdx = MuxCase((fetchWidth - 1).U, Seq(
    (scenario === 1.U || scenario === 2.U) -> firstJalIdx,
    (scenario === 3.U || scenario === 4.U || scenario === 5.U || scenario === 6.U) -> predIdx
  ))
 
  val enqMask = Wire(Vec(fetchWidth, Bool()))
  for (i <- 0 until fetchWidth) {
    enqMask(i) := s_valids(i) && (i.U <= truncateIdx)
  }
 
  // ---- Step 8: 逐条指令输出 ----
  val outPdInfo  = Wire(Vec(fetchWidth, new PredecodeInfo))
  val outBpuInfo = Wire(Vec(fetchWidth, new bpuInfoBundle))
 
  for (i <- 0 until fetchWidth) {
    val instr  = s_instrs(i)
    val opcode = instr(6, 0)
    val v      = s_valids(i) && enqMask(i)   
 
    outPdInfo(i).valid      := v
    outPdInfo(i).isBr       := v && RiscVOpcodes.isBranchOpcode(opcode)
    outPdInfo(i).isJal      := v && (opcode === RiscVOpcodes.OPC_JAL)
    outPdInfo(i).isJalr     := v && (opcode === RiscVOpcodes.OPC_JALR)
    outPdInfo(i).isCall     := false.B   
    outPdInfo(i).isRet      := false.B   
    outPdInfo(i).jumpTarget := Mux(
      v && (opcode === RiscVOpcodes.OPC_JAL),
      jalTgt(i), 0.U
    )
 
    val isSpecialJal  = (scenario === 1.U || scenario === 2.U) && (i.U === firstJalIdx)
    val isSpecialPred = (scenario === 3.U || scenario === 4.U || scenario === 5.U) && (i.U === predIdx)
    val isSpecial     = isSpecialJal || isSpecialPred
 
    outBpuInfo(i).pc          := pc(i)
    outBpuInfo(i).fallThrough := pc(i) + 4.U
    outBpuInfo(i).taken       := isSpecial && !s_uncached
    outBpuInfo(i).takenOffset := i.U
    outBpuInfo(i).target      := Mux(isSpecialJal, firstJalTarget,
                             Mux(isSpecialPred,
                               Mux(predIsJal, predJalTarget, predTarget),
                               pc(i) + 4.U))
    outBpuInfo(i).meta        := s_bpu.meta
  }
 
  // ================================================================
  // 第三部分：输出
  // ================================================================
 
  //：如果当前周期正在发起打拍后的重定向(pendingRedirect)，
  // 或者接收到了外部的 flush（包括后端的重定向），直接强制掩掉输出有效信号！
  // 此机制完美阻断了错取数据进入 IBuffer，实现了预译码级内的“自我清洗”。
  val isFlushed = io.flush || pendingRedirect
  io.out.valid               := s_valid && !isFlushed

  io.out.bits.instrs         := s_instrs
  io.out.bits.instvalids     := s_valids
  io.out.bits.pcs            := pc
  io.out.bits.addr           := s_addr
  io.out.bits.uncached       := s_uncached
  io.out.bits.mmu_error      := s_mmuError
  io.out.bits.pdInfo         := outPdInfo
  io.out.bits.bpuInfo        := outBpuInfo
  io.out.bits.enqMask        := enqMask
  io.out.bits.frontendRedirect := feRedirect
  io.out.bits.bpuUpdate      := bpuUpdate
}
