package minixiangshan.backend.dispatch
 
import chisel3._
import chisel3.util._
import minixiangshan.config._
import minixiangshan.backend.decode._
import minixiangshan.backend.rename._
import minixiangshan.backend.regfile._
import minixiangshan.backend.issue._
import minixiangshan.backend.execute._
import minixiangshan.util.CircularQueuePtr
 
/**
 * ═══════════════════════════════════════════════════════════════
 * 分发流水级（DispatchStage）—— 5 IQ 版本 (LSQ 单端口发射优化版)
 * ═══════════════════════════════════════════════════════════════
 *
 * 优化说明：
 * 1. 彻底解决 LSQ 批量分配造成的指针脏读时序问题。
 * 2. LSQ 的请求接口改为单个（伴随 IQ4 的发射同步触发）。
 * 3. 只有当 LSQ 且 IQ 均有空位时，访存指令才被允许参与发射竞争。
 * 4. Ptr 状态在成功发射当拍自增 1，杜绝批量加法逻辑。
 * ═══════════════════════════════════════════════════════════════
 */
class DispatchStage(implicit p: Parameters) extends NSModule {
  val io = IO(new Bundle {
    val in       = Vec(CtrlBlockWidth, Flipped(Decoupled(new RenamedInst)))
    val q1IQEnq  = Vec(IQEnqPorts.Q1, ValidIO(new DispatchedInst))
    val q2IQEnq  = Vec(IQEnqPorts.Q2, ValidIO(new DispatchedInst))
    val q3IQEnq  = Vec(IQEnqPorts.Q3, ValidIO(new DispatchedInst))
    val q4IQEnq  = Vec(IQEnqPorts.Q4, ValidIO(new DispatchedInst))
    val q5IQEnq  = Vec(IQEnqPorts.Q5, ValidIO(new DispatchedInst))
    val iqFeedback = Input(new IssueQueueFeedback)
    
    // 注意：这里的 LsEnqIO 已经被视作单请求端口 (Valid(new LsEnqReq))
    val lsEnq   = new LsEnqIO 

    val dispatchLqFull = Input(Bool())
    val dispatchSqFull = Input(Bool())
    // ── 状态 ──
    val bufHasPendingLoadNeedFlush  = Input(Bool())
    val bufHasPendingStoreNeedFlush = Input(Bool())

    //val bufHassqIdx   = Flipped( new SqPtr(SqSize) )
    //val bufHaslqIdx   = Flipped( new LqPtr(LqSize) )
    
    val robEnq  = new RobEnqIO
    val robEmpty = Input(Bool())
    val dis2robHas = Input(Bool())

    val flush   = Input(Bool())
    val redirectInfo    = Flipped(ValidIO( new redirectInfoToModule )) 
    val stall = Input(Bool())

    //val redirect = Input(new RedirectInfo)

    val wakeupPorts   = Input(Vec(IQNumWakeupPorts, Valid(new IssueWakeup)))
  })
 
  val busyTable = Module(new BusyTable)
 
  // ================================================================
  //  引入具体的队列指针类型
  // ================================================================
  //class LqPtr extends CircularQueuePtr[LqPtr](LqSize)
  //class SqPtr extends CircularQueuePtr[SqPtr](SqSize)

  // ================================================================
  //  流水级寄存器 (已精简冗余的 LSQ 状态)
  // ================================================================
  val laneValid   = RegInit(VecInit(Seq.fill(CtrlBlockWidth)(false.B)))
  val robWritten  = RegInit(VecInit(Seq.fill(CtrlBlockWidth)(false.B)))
  val iqSent      = RegInit(VecInit(Seq.fill(CtrlBlockWidth)(false.B)))
  val stgData = RegInit(VecInit(Seq.fill(CtrlBlockWidth)(0.U.asTypeOf(new RenamedInst))))
 
  // ── 需求掩码 ──
  val needRob = VecInit((0 until CtrlBlockWidth).map(i => laneValid(i) && !robWritten(i)))
  val needIq  = VecInit((0 until CtrlBlockWidth).map(i => laneValid(i) && !iqSent(i)))

  val stgValid = needIq.asUInt.orR

  // 序列化指令先等待所有更老指令离开 Dispatch/ROB；进入后阻止年轻
  // 指令继续分发，直到该指令离开 ROB，或被流水线冲刷。
  val blockBackwardActive = RegInit(false.B)
  val lanePending = VecInit((0 until CtrlBlockWidth).map(i => needRob(i) || needIq(i)))
  val laneWaitForward = VecInit((0 until CtrlBlockWidth).map(i =>
    laneValid(i) && stgData(i).ctrl.waitForward
  ))
  val laneBlockBackward = VecInit((0 until CtrlBlockWidth).map(i =>
    laneValid(i) && stgData(i).ctrl.blockBackward
  ))
  val laneCanDispatch = Wire(Vec(CtrlBlockWidth, Bool()))
  for (i <- 0 until CtrlBlockWidth) {
    val olderPending = if (i == 0) false.B else {
      VecInit((0 until i).map(j => lanePending(j))).asUInt.orR
    }
    val olderBlockBackward = if (i == 0) false.B else {
      VecInit((0 until i).map(j =>
        lanePending(j) && laneBlockBackward(j))).asUInt.orR
    }
    //这里很乱，但是应该有效
    //Empty直连的话，Rob->dispatch->rename 超长组合路径
    //首先，有waitForward性质的指令进入dispatch的时候，rob包空
    //olderPending保证，前面有指令在dispatch的时候waitForward被挡住
    //io.dis2robHas保证，前面指令发到dip2rob时被挡住
    //由于robEmpty是综合了dis2robHas的数据的，所以再下一拍，这个条件又能保证waitForward被挡住
    // 结论：没有逻辑、但行得通
    val waitForward = laneWaitForward(i) && (olderPending || io.dis2robHas || RegNext(!io.robEmpty ))

    laneCanDispatch(i) := !blockBackwardActive &&
      !olderBlockBackward && !waitForward
  }
    
  // ================================================================
  //  一、指令分类（基于 needIq）
  // ================================================================
  val isAluLane   = VecInit((0 until CtrlBlockWidth).map(i => needIq(i) && laneCanDispatch(i) && stgData(i).ctrl.fuType === FuType.alu))
  val privLane    = VecInit((0 until CtrlBlockWidth).map(i =>
    needIq(i) && laneCanDispatch(i) && (
      stgData(i).ctrl.fuType === FuType.csr ||
      stgData(i).ctrl.tlbOp =/= TlbOp.none ||
      ( stgData(i).ctrl.fuType === FuType.priv ) // 特权指令单独走带 MMU 的通道
    )
  ))
  val isDivLane   = VecInit((0 until CtrlBlockWidth).map(i => needIq(i) && laneCanDispatch(i) && stgData(i).ctrl.fuType === FuType.div))
  val isMulLane   = VecInit((0 until CtrlBlockWidth).map(i => needIq(i) && laneCanDispatch(i) && stgData(i).ctrl.fuType === FuType.mul))
  val isJmpLane   = VecInit((0 until CtrlBlockWidth).map(i => needIq(i) && laneCanDispatch(i) && stgData(i).ctrl.fuType === FuType.bru))
  val isLoadLane  = VecInit((0 until CtrlBlockWidth).map(i => needIq(i) && laneCanDispatch(i) && stgData(i).ctrl.fuType === FuType.lsu && stgData(i).ctrl.memRead))
  val isStoreLane = VecInit((0 until CtrlBlockWidth).map(i => needIq(i) && laneCanDispatch(i) && stgData(i).ctrl.fuType === FuType.lsu && stgData(i).ctrl.memWrite))
 
  // ================================================================
  //  二、计算IQ 可用性
  // ================================================================
  val q1Avail = io.iqFeedback.q1FreeEntries.asUInt.orR
  val q2Avail = io.iqFeedback.q2FreeEntries.asUInt.orR
  val q3Avail = io.iqFeedback.q3FreeEntries.asUInt.orR
  val q4Avail = io.iqFeedback.q4FreeEntries.asUInt.orR
  val q5Avail = io.iqFeedback.q5FreeEntries.asUInt.orR
 
  def truncateMask(isMatch: Vec[Bool], maxPorts: Int): Vec[Bool] = {
    val result = Wire(Vec(CtrlBlockWidth, Bool()))
    var count = 0.U(log2Ceil(maxPorts + 1).W)
    for (i <- 0 until CtrlBlockWidth) {
      result(i) := isMatch(i) && (count < maxPorts.U)
      count = count + (isMatch(i) && result(i)).asUInt
    }
    result
  }
 
  // ================================================================
  //  Phase 1: 专属指令路由
  // ================================================================
  val privToQ1 = truncateMask(VecInit((0 until CtrlBlockWidth).map(i => privLane(i) && q1Avail)), 1)
  val divToQ2 = truncateMask(VecInit( (0 until CtrlBlockWidth).map(i => isDivLane(i) && q2Avail)), 1)
  val isMulOrJmpLane = VecInit((0 until CtrlBlockWidth).map(i => (isMulLane(i) || isJmpLane(i)) && q3Avail))
  val mulJmpToQ3 = truncateMask(isMulOrJmpLane, 1)
  
  var consumedMask = privToQ1.asUInt | divToQ2.asUInt | mulJmpToQ3.asUInt
  
  val q1FreeAfterExclusive = !privToQ1.asUInt.orR && q1Avail
  val q2FreeAfterExclusive = !divToQ2.asUInt.orR && q2Avail
  val q3FreeAfterExclusive = !mulJmpToQ3.asUInt.orR && q3Avail
 
  // ================================================================
  //  Phase 2: ALU 动态负载均衡分配 (基于历史状态的轮询 RR 优化版)
  // ================================================================
  val q1CanAcceptAlu = q1FreeAfterExclusive
  val q2CanAcceptAlu = q2FreeAfterExclusive
  val q3CanAcceptAlu = q3FreeAfterExclusive
 
  // 1. 维护轮询状态机 (0:优先Q1, 1:优先Q2, 2:优先Q3)
  val aluRrState = RegInit(0.U(2.W))
 
  // 2. 根据状态机生成三档优先级排序 (One-Hot表示: bit0=Q1, bit1=Q2, bit2=Q3)
  val Q1_OH = "b001".U(3.W)
  val Q2_OH = "b010".U(3.W)
  val Q3_OH = "b100".U(3.W)
 
  val p0_OH = Mux(aluRrState === 0.U, Q1_OH, Mux(aluRrState === 1.U, Q2_OH, Q3_OH))
  val p1_OH = Mux(aluRrState === 0.U, Q2_OH, Mux(aluRrState === 1.U, Q3_OH, Q1_OH))
  val p2_OH = Mux(aluRrState === 0.U, Q3_OH, Mux(aluRrState === 1.U, Q1_OH, Q2_OH))
 
  // 3. 结合当前实际是否有空位，过滤出真实可用的队列
  val availMask = Cat(q3CanAcceptAlu, q2CanAcceptAlu, q1CanAcceptAlu)
  
  val p0_avail = (p0_OH & availMask).orR
  val p1_avail = (p1_OH & availMask).orR
  val p2_avail = (p2_OH & availMask).orR
 
  // 4. 依次提取第1可用、第2可用、第3可用的队列 (仅使用与或逻辑)
  val rank0OH_uint = Mux(p0_avail, p0_OH, Mux(p1_avail, p1_OH, Mux(p2_avail, p2_OH, 0.U)))
  val rank1OH_uint = Mux(p0_avail && p1_avail, p1_OH, Mux((p0_avail || p1_avail) && p2_avail, p2_OH, 0.U))
  val rank2OH_uint = Mux(p0_avail && p1_avail && p2_avail, p2_OH, 0.U)
 
  val rank0OH = Wire(Vec(CtrlBlockWidth, Bool()))
  val rank1OH = Wire(Vec(CtrlBlockWidth, Bool()))
  val rank2OH = Wire(Vec(CtrlBlockWidth, Bool()))
  for (i <- 0 until CtrlBlockWidth) {
    rank0OH(i) := rank0OH_uint(i)
    rank1OH(i) := rank1OH_uint(i)
    rank2OH(i) := rank2OH_uint(i)
  }
 
  // 5. 容量判断极大简化：因为 rankXOH 已经使用 availMask 过滤，只要非零即代表有容量
  val rank0HasCap = rank0OH_uint.orR
  val rank1HasCap = rank1OH_uint.orR
  val rank2HasCap = rank2OH_uint.orR

  val aluCandR1 = VecInit((0 until CtrlBlockWidth).map(i => isAluLane(i) && !consumedMask(i)))
  val aluRound1 = truncateMask(aluCandR1, 1)
  val aluRound1Valid = aluRound1.asUInt.orR && rank0HasCap
  val aluRound1ToQ1 = aluRound1Valid && rank0OH(0)
  val aluRound1ToQ2 = aluRound1Valid && rank0OH(1)
  val aluRound1ToQ3 = aluRound1Valid && rank0OH(2)
  consumedMask = consumedMask | Mux(aluRound1Valid, aluRound1.asUInt, 0.U)
 
  val aluCandR2 = VecInit((0 until CtrlBlockWidth).map(i => isAluLane(i) && !consumedMask(i)))
  val aluRound2 = truncateMask(aluCandR2, 1)
  val aluRound2Valid = aluRound2.asUInt.orR && rank1HasCap
  val aluRound2ToQ1 = aluRound2Valid && rank1OH(0)
  val aluRound2ToQ2 = aluRound2Valid && rank1OH(1)
  val aluRound2ToQ3 = aluRound2Valid && rank1OH(2)
  consumedMask = consumedMask | Mux(aluRound2Valid, aluRound2.asUInt, 0.U)
 
  val aluCandR3 = VecInit((0 until CtrlBlockWidth).map(i => isAluLane(i) && !consumedMask(i)))
  val aluRound3 = truncateMask(aluCandR3, 1)
  val aluRound3Valid = aluRound3.asUInt.orR && rank2HasCap
  val aluRound3ToQ1 = aluRound3Valid && rank2OH(0)
  val aluRound3ToQ2 = aluRound3Valid && rank2OH(1)
  val aluRound3ToQ3 = aluRound3Valid && rank2OH(2)
  consumedMask = consumedMask | Mux(aluRound3Valid, aluRound3.asUInt, 0.U)
 
  val aluToQ1 = VecInit((0 until CtrlBlockWidth).map(i => (aluRound1(i) && aluRound1ToQ1) || (aluRound2(i) && aluRound2ToQ1) || (aluRound3(i) && aluRound3ToQ1)))
  val aluToQ2 = VecInit((0 until CtrlBlockWidth).map(i => (aluRound1(i) && aluRound1ToQ2) || (aluRound2(i) && aluRound2ToQ2) || (aluRound3(i) && aluRound3ToQ2)))
  val aluToQ3 = VecInit((0 until CtrlBlockWidth).map(i => (aluRound1(i) && aluRound1ToQ3) || (aluRound2(i) && aluRound2ToQ3) || (aluRound3(i) && aluRound3ToQ3)))
 
  val q1Final = VecInit((0 until CtrlBlockWidth).map(i => privToQ1(i) || aluToQ1(i)))
  val q2Final = VecInit((0 until CtrlBlockWidth).map(i => divToQ2(i) || aluToQ2(i)))
  val q3Final = VecInit((0 until CtrlBlockWidth).map(i => mulJmpToQ3(i) || aluToQ3(i)))
 
  // ================================================================
  //  Q4/Q5 路由 (结合了 LSQ 容量判断，只有 LSQ 能接住才允许发往 IQ)
  // ================================================================
  val q4Cand = VecInit((0 until CtrlBlockWidth).map(i => {
    val canLoad  = isLoadLane(i)  && !io.dispatchLqFull
    val canStore = isStoreLane(i) && !io.dispatchSqFull && q5Avail

    // 更老的store被阻塞时,新的load不能进队
    val hasOlderMemBlocked = if (i == 0) false.B else {
      VecInit((0 until i).map(j =>
        // 更老的 load 因 LQ 满而阻塞
        (isLoadLane(j)  && io.dispatchLqFull) ||
        // 更老的 store 因 SQ 满或 IQ5 满而阻塞
        (isStoreLane(j) && (io.dispatchSqFull || !q5Avail))
      )).asUInt.orR
    }

    (canLoad || canStore) && !hasOlderMemBlocked && q4Avail
  }))
  val q4Selected = truncateMask(q4Cand, 1)
  diffDontTouch(q4Selected)
 
  val q5Selected = VecInit((0 until CtrlBlockWidth).map(i => q4Selected(i) && isStoreLane(i)))
  diffDontTouch(q5Selected)
 
  val iqDispatchMask = VecInit((0 until CtrlBlockWidth).map(i =>
    q1Final(i) || q2Final(i) || q3Final(i) || q4Selected(i) || q5Selected(i)
  ))

  val laneTargetQ = Wire(Vec(CtrlBlockWidth, UInt(IssueQueueId.width.W)))
  for (i <- 0 until CtrlBlockWidth) {
    laneTargetQ(i) := MuxCase(IssueQueueId.Q1.U, Seq(
      q1Final(i)    -> IssueQueueId.Q1.U,
      q2Final(i)    -> IssueQueueId.Q2.U,
      q3Final(i)    -> IssueQueueId.Q3.U,
      q4Selected(i) -> IssueQueueId.Q4.U,
      q5Selected(i) -> IssueQueueId.Q5.U,
    ))
  }
 
  // ================================================================
  //  资源就绪与发射条件
  // ================================================================
  //val anyNeedRob = needRob.asUInt.orR
  //val robBatchReady = !anyNeedRob // || io.robEnq.canEnq

  val hasIqDispatch = iqDispatchMask.zip(needIq).map { case (d, n) => d && n }.reduce(_ || _)

  // 由于 LSQ 检查已融入 q4Cand，此处不再需要 lsqBatchReady
  val dispatchFire = stgValid && hasIqDispatch && /*(robBatchReady || !anyNeedRob) && */ !io.flush && !io.stall //flush是指flush的时候不做任何fire，因为下面那个寄存器不能阻断flush
                                                                                                     // stall就是回滚的stall

  val blockBackwardFire = dispatchFire && VecInit((0 until CtrlBlockWidth).map(i =>
    laneBlockBackward(i) && laneCanDispatch(i) && needRob(i) && iqDispatchMask(i)
  )).asUInt.orR

  val AllWillFire = VecInit((0 until CtrlBlockWidth).map(i => (needIq(i) && iqDispatchMask(i)) || !needIq(i)  )).reduce(_ && _)
  val canAcceptNew = !blockBackwardActive && !blockBackwardFire &&
    (!stgValid || (dispatchFire && AllWillFire))

  val inValid = io.in.map(_.valid).reduce(_ || _)
  val inFire  = inValid && canAcceptNew && !io.flush
  for (i <- 0 until CtrlBlockWidth) {
    io.in(i).ready := canAcceptNew
  }

  // ================================================================
  //  新增：轮询状态机更新逻辑
  // ================================================================
  // 计算当拍到底分配出去了几条 ALU 指令
  val aluDispatchedCnt = aluRound1Valid.asUInt + aluRound2Valid.asUInt + aluRound3Valid.asUInt
  
  when(dispatchFire) {
    // 每次成功发射，将优先权轮转。采用加法后处理避免模运算开销
    val nextStateSum = aluRrState + aluDispatchedCnt
    aluRrState := Mux(nextStateSum >= 3.U, nextStateSum - 3.U, nextStateSum)
  }
 
  // ================================================================
  //  状态转移
  // ================================================================
  val doRedirect = io.redirectInfo.valid && io.redirectInfo.bits.doRedirect
  val redirectRobIdx = io.redirectInfo.bits.robIdx

  when(io.flush) {
    blockBackwardActive := false.B
  }.elsewhen(blockBackwardFire) {
    blockBackwardActive := true.B
  }.elsewhen(blockBackwardActive  && io.robEmpty ) { //解开需要动态依赖rob的信息
    blockBackwardActive := false.B
  }

  //val doFlush.asUInt.orR = doRedirect && ( (  io.inReq.bits.uop.robIdxFull.isAfter(redirectRobIdx)))
  val doFlush = Wire(Vec(CtrlBlockWidth, Bool()))
  for (i <- 0 until CtrlBlockWidth) {
    doFlush(i) := doRedirect && laneValid(i) && stgData(i).robIdx.isAfter(redirectRobIdx)
  }
  
  when(doFlush.asUInt.orR) {
    for (i <- 0 until CtrlBlockWidth) {
      when(doFlush(i)){
        laneValid(i)  := false.B
        robWritten(i) := false.B
        iqSent(i)     := false.B
      }
    }
  }.elsewhen(inFire) {
    for (i <- 0 until CtrlBlockWidth) {
      laneValid(i)  := io.in(i).valid
      robWritten(i) := false.B
      iqSent(i)     := false.B
      stgData(i)    := io.in(i).bits
    }
  }.elsewhen(dispatchFire) {
    for (i <- 0 until CtrlBlockWidth) {
      when(needRob(i) && laneCanDispatch(i)) { robWritten(i) := true.B }
      when(dispatchFire && iqDispatchMask(i) && needIq(i)) { iqSent(i) := true.B }
    }
  }
 
  // ================================================================
  //  LQ / SQ 单点指针维护与 LSQ 请求生成 (核心变动)
  // ================================================================
  val lqHeadPtr = RegInit(0.U.asTypeOf(new LqPtr(LqSize)))
  val sqHeadPtr = RegInit(0.U.asTypeOf(new SqPtr(SqSize)))
 
  // 解析当前被选中发往 IQ4 的访存指令信息
  val memDispatchedThisCycle = dispatchFire && q4Selected.asUInt.orR && !io.flush
  val selectedIsLoad  = Mux1H(q4Selected, (0 until CtrlBlockWidth).map(i => stgData(i).ctrl.memRead))
  val selectedIsStore = Mux1H(q4Selected, (0 until CtrlBlockWidth).map(i => stgData(i).ctrl.memWrite))
  val selectedMemInst = Mux1H(q4Selected, stgData)

  // 当拍同步触发 LSQ 写入
  io.lsEnq.req.valid        :=  memDispatchedThisCycle
  io.lsEnq.req.bits.robIdx  :=  selectedMemInst.robIdx
  io.lsEnq.req.bits.isLoad  :=  selectedIsLoad
  io.lsEnq.req.bits.isStore :=  selectedIsStore
  io.lsEnq.req.bits.lqIdx   :=  lqHeadPtr
  io.lsEnq.req.bits.sqIdx   :=  sqHeadPtr
  io.lsEnq.toLsqData        :=  selectedMemInst

  // 更新当前指针 (仅+1)
  when(io.flush){
    lqHeadPtr := Mux(io.bufHasPendingLoadNeedFlush, lqHeadPtr - 1.U, lqHeadPtr)
    sqHeadPtr := Mux(io.bufHasPendingStoreNeedFlush, sqHeadPtr - 1.U, sqHeadPtr)

  }.elsewhen(memDispatchedThisCycle){

    when(selectedIsLoad) {
      lqHeadPtr := lqHeadPtr + 1.U
    }
    when(selectedIsStore) {
      sqHeadPtr := sqHeadPtr + 1.U
    }
  }

 // when(io.flush) {
 //   lqHeadPtr := 0.U.asTypeOf(new LqPtr(LqSize))
 //   sqHeadPtr := 0.U.asTypeOf(new SqPtr(SqSize))
 // }
 
  // ================================================================
  //  BusyTable 读写与更新逻辑
  // ================================================================
  for (i <- 0 until CtrlBlockWidth) {
    busyTable.io.readReq(i * 2)     := stgData(i).prs1
    busyTable.io.readReq(i * 2 + 1) := stgData(i).prs2
  }

  val allocValids = Wire(Vec(CtrlBlockWidth, Bool()))
  for (i <- 0 until CtrlBlockWidth) {
    allocValids(i) := dispatchFire && needRob(i) && laneCanDispatch(i) &&
      stgData(i).rdValid && stgData(i).ldst =/= 0.U
    busyTable.io.allocReq(i).valid := allocValids(i)
    busyTable.io.allocReq(i).bits  := stgData(i).pdst
  }
  
  val wakeupPorts = io.wakeupPorts
  for (i <- 0 until WbBusWidth) {
    busyTable.io.wbReq(i).valid := wakeupPorts(i).valid
    busyTable.io.wbReq(i).bits  := wakeupPorts(i).bits.pdst
  }

  val prs1BusyRaw = VecInit((0 until CtrlBlockWidth).map(i => busyTable.io.readResp(i * 2)))
  val prs2BusyRaw = VecInit((0 until CtrlBlockWidth).map(i => busyTable.io.readResp(i * 2 + 1)))

  val prs1Busy = Wire(Vec(CtrlBlockWidth, Bool()))
  val prs2Busy = Wire(Vec(CtrlBlockWidth, Bool()))
  
  for (i <- 0 until CtrlBlockWidth) {
    val prs1WakeupHits = wakeupPorts.map(w => w.valid && (w.bits.pdst === stgData(i).prs1))
    val prs1WokenUp    = VecInit(prs1WakeupHits).asUInt.orR

    val prs2WakeupHits = wakeupPorts.map(w => w.valid && (w.bits.pdst === stgData(i).prs2))
    val prs2WokenUp    = VecInit(prs2WakeupHits).asUInt.orR

    val prs1AllocByOlder = if (i == 0) false.B else {
      VecInit((0 until i).map(j => allocValids(j) && (stgData(j).pdst === stgData(i).prs1))).asUInt.orR
    }
    val prs2AllocByOlder = if (i == 0) false.B else {
      VecInit((0 until i).map(j => allocValids(j) && (stgData(j).pdst === stgData(i).prs2))).asUInt.orR
    }

    prs1Busy(i) := prs1AllocByOlder || (prs1BusyRaw(i) && !prs1WokenUp)
    prs2Busy(i) := prs2AllocByOlder || (prs2BusyRaw(i) && !prs2WokenUp)
  }

  // ================================================================
  //  微操作构造
  // ================================================================
  def makeBaseUop(i: Int): DispatchedInst = {
    val u = Wire(new DispatchedInst)
    u.pc         := stgData(i).pc
    u.inst       := stgData(i).inst
    u.ctrl       := stgData(i).ctrl
    u.excp       := stgData(i).excp
    u.imm        := stgData(i).imm
    u.csrAddress := stgData(i).csrAddress
    u.cacheOp      := stgData(i).cacheOp
    u.pdInfo     := stgData(i).pdInfo
    u.bpuInfo     := stgData(i).bpuInfo
    u.ldst       := stgData(i).ldst
    u.lrs1       := stgData(i).lrs1
    u.lrs2       := stgData(i).lrs2
    u.pdst       := stgData(i).pdst
    u.prs1       := stgData(i).prs1
    u.prs2       := stgData(i).prs2
    u.oldPdst    := stgData(i).oldPdst
    u.rs1Valid   := stgData(i).rs1Valid
    u.rs2Valid   := stgData(i).rs2Valid
    u.rdValid    := stgData(i).rdValid
    u.robIdx     := stgData(i).robIdx
    u.robIdxFull := stgData(i).robIdx

    u.snptId := stgData(i).snptId
    
    // 初始化清零
    u.sqIdx      :=  0.U.asTypeOf(new LqPtr(SqSize))
    u.lqIdx      :=  0.U.asTypeOf(new LqPtr(LqSize))
    
    u.issueQueue := laneTargetQ(i)
    u.prs1Busy   := Mux(stgData(i).rs1Valid && stgData(i).lrs1 =/= 0.U, prs1Busy(i), false.B)
    u.prs2Busy   := Mux(stgData(i).rs2Valid && stgData(i).lrs2 =/= 0.U, prs2Busy(i), false.B)
    u.isSta      := false.B
    u.isStd      := false.B
    u
  }
 
  def makeLoadUop(i: Int): DispatchedInst = {
    val u = makeBaseUop(i)
    u.lqIdx      := lqHeadPtr // 动态赋予当拍实时有效的 HeadPtr
    u.issueQueue := IssueQueueId.Q4.U
    u
  }
 
  def makeStaUop(i: Int): DispatchedInst = {
    val u = makeBaseUop(i)
    u.sqIdx      := sqHeadPtr // 动态赋予当拍实时有效的 HeadPtr
    u.issueQueue := IssueQueueId.Q4.U
    u.isSta      := true.B
    u.rs2Valid   := false.B
    u.prs2Busy   := false.B
    u.rdValid    := false.B
    u.pdst       := 0.U
    u
  }
 
  def makeStdUop(i: Int): DispatchedInst = {
    val u = makeBaseUop(i)
    u.sqIdx      := sqHeadPtr // 与 STA 指令共用同一个周期的 HeadPtr
    u.issueQueue := IssueQueueId.Q5.U
    u.isStd      := true.B
    u.rs1Valid   := false.B
    u.prs1Busy   := false.B
    u.imm        := 0.U
    u.rdValid    := false.B
    u.pdst       := 0.U
    u
  }
 
  // ================================================================
  //  IQ 端口分配
  // ================================================================
  io.q1IQEnq(0).valid := false.B;  io.q1IQEnq(0).bits := DontCare
  io.q2IQEnq(0).valid := false.B;  io.q2IQEnq(0).bits := DontCare
  io.q3IQEnq(0).valid := false.B;  io.q3IQEnq(0).bits := DontCare
  io.q4IQEnq(0).valid := false.B;  io.q4IQEnq(0).bits := DontCare
  io.q5IQEnq(0).valid := false.B;  io.q5IQEnq(0).bits := DontCare
 
  val q1Uops = (0 until CtrlBlockWidth).map(i => {
    val u = Wire(new DispatchedInst)
    u := makeBaseUop(i)
    u.issueQueue := IssueQueueId.Q1.U
    u
  })
  when(q1Final.asUInt.orR && dispatchFire) {
    io.q1IQEnq(0).valid := true.B && !io.flush
    io.q1IQEnq(0).bits  := Mux1H(q1Final, q1Uops)
  }
 
  val q2Uops = (0 until CtrlBlockWidth).map(i => {
    val u = Wire(new DispatchedInst)
    u := makeBaseUop(i)
    u.issueQueue := IssueQueueId.Q2.U
    u
  })
  when(q2Final.asUInt.orR && dispatchFire) {
    io.q2IQEnq(0).valid := true.B && !io.flush
    io.q2IQEnq(0).bits  := Mux1H(q2Final, q2Uops)
  }
 
  val q3Uops = (0 until CtrlBlockWidth).map(i => {
    val u = Wire(new DispatchedInst)
    u := makeBaseUop(i)
    u.issueQueue := IssueQueueId.Q3.U
    u
  })
  when(q3Final.asUInt.orR && dispatchFire) {
    io.q3IQEnq(0).valid := true.B && !io.flush
    io.q3IQEnq(0).bits  := Mux1H(q3Final, q3Uops)
  }
 
  val q4Uops = (0 until CtrlBlockWidth).map(i => Mux(isStoreLane(i), makeStaUop(i), makeLoadUop(i)))
  when(q4Selected.asUInt.orR && dispatchFire) {
    io.q4IQEnq(0).valid := true.B  && !io.flush
    io.q4IQEnq(0).bits  := Mux1H(q4Selected, q4Uops)
  }
 
  val q5Uops = (0 until CtrlBlockWidth).map(i => makeStdUop(i))
  when(q5Selected.asUInt.orR && dispatchFire) {
    io.q5IQEnq(0).valid := true.B && !io.flush
    io.q5IQEnq(0).bits  := Mux1H(q5Selected, q5Uops)
  }
  diffDontTouch(io.q5IQEnq)
  diffDontTouch(io.q4IQEnq)
 
  // ================================================================
  //  ROB 批量写入 (ROB仍然维持进入流水级当拍进行一次性批量分发)
  // ================================================================
  for (i <- 0 until CtrlBlockWidth) {
    io.robEnq.valid(i)              := dispatchFire && needRob(i) && laneCanDispatch(i)
    io.robEnq.validforPreg(i)       := needRob(i)
    io.robEnq.bits(i).pc            := stgData(i).pc
    io.robEnq.bits(i).inst          := stgData(i).inst
    io.robEnq.bits(i).fuType        := stgData(i).ctrl.fuType
    io.robEnq.bits(i).pdst          := stgData(i).pdst
    io.robEnq.bits(i).oldPdst       := stgData(i).oldPdst
    io.robEnq.bits(i).ldst          := stgData(i).ldst
    io.robEnq.bits(i).rfWen         := stgData(i).ctrl.rfWen
    io.robEnq.bits(i).memRead       := stgData(i).ctrl.memRead
    io.robEnq.bits(i).memWrite      := stgData(i).ctrl.memWrite
    io.robEnq.bits(i).csrWen        := stgData(i).ctrl.csrWen
    io.robEnq.bits(i).csrOp         := stgData(i).ctrl.csrOp
    io.robEnq.bits(i).tlbOp         := stgData(i).ctrl.tlbOp
    io.robEnq.bits(i).csrWaddr      := stgData(i).csrAddress
    io.robEnq.bits(i).flushOnCommit := stgData(i).ctrl.flushOnCommit
    io.robEnq.bits(i).privLevel    := stgData(i).ctrl.privLevel
    io.robEnq.bits(i).isIdle        := stgData(i).ctrl.isIdle
    io.robEnq.bits(i).waitStore     := stgData(i).ctrl.barOp =/= BarOp.none ||
      stgData(i).ctrl.lsuOp === LsuOp.scw || stgData(i).cacheOp.valid
    io.robEnq.bits(i).lrValidSet      := stgData(i).ctrl.lsuOp === LsuOp.lrw
    io.robEnq.bits(i).lrValidClear    := stgData(i).ctrl.lsuOp === LsuOp.scw
    io.robEnq.bits(i).fenceI          := stgData(i).ctrl.barOp === BarOp.fenceI
    io.robEnq.bits(i).isCacheOp       := stgData(i).cacheOp.valid
    io.robEnq.bits(i).cacheOpType := stgData(i).cacheOp.cacheType
    io.robEnq.bits(i).cacheOpOperation := stgData(i).cacheOp.operation
    io.robEnq.bits(i).excp          := stgData(i).excp
    io.robEnq.bits(i).robIdx        := stgData(i).robIdx

    io.robEnq.bits(i).writtenBack   := DontCare
    io.robEnq.bits(i).valid         := DontCare
    io.robEnq.bits(i).needsRollback := DontCare
    io.robEnq.bits(i).rfdata        := DontCare
    io.robEnq.bits(i).memVaddr      := DontCare
    io.robEnq.bits(i).memPaddr      := DontCare
    io.robEnq.bits(i).storeData     := DontCare
    io.robEnq.bits(i).storeValid    := DontCare
    io.robEnq.bits(i).sqIdx         := DontCare
    io.robEnq.bits(i).csrWdata      := DontCare
    io.robEnq.bits(i).csrTimer      := DontCare
    io.robEnq.bits(i).tlbFillIdx    := DontCare

  }
}
