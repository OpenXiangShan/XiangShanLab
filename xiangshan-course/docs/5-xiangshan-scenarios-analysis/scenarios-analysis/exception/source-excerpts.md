# Trap 分析的源码摘录

主仓库 HEAD：`abd0f867a86b66a92d4fc5d3c6d62944725c747f`。以下为本地文件摘录；工作树状态限制见正文。

阅读方式：先读正文解释，再看对应 S 编号。代码片段保持原文，因此原注释中可能存在与实际波形不完全一致的时间标签；正文已指出 T3/T4 的差异。

<a id="s1"></a>
## S1 译码：ECALL 分类与非法指令标记

[DecodeUnit.scala:219](/nfs/home/wanghao/emuByYuan/stable-kmh-v2/src/main/scala/xiangshan/backend/decode/DecodeUnit.scala#L219)，摘录 219～226 行。

```scala

    EBREAK  -> XSDecode(SrcType.reg, SrcType.imm, SrcType.X, FuType.csr, CSROpType.jmp, SelImm.IMM_I, xWen = T, noSpec = T, blockBack = T),
    ECALL   -> XSDecode(SrcType.reg, SrcType.imm, SrcType.X, FuType.csr, CSROpType.jmp, SelImm.IMM_I, xWen = T, noSpec = T, blockBack = T),
    SRET    -> XSDecode(SrcType.reg, SrcType.imm, SrcType.X, FuType.csr, CSROpType.jmp, SelImm.IMM_I, xWen = T, noSpec = T, blockBack = T),
    MRET    -> XSDecode(SrcType.reg, SrcType.imm, SrcType.X, FuType.csr, CSROpType.jmp, SelImm.IMM_I, xWen = T, noSpec = T, blockBack = T),
    MNRET   -> XSDecode(SrcType.reg, SrcType.imm, SrcType.X, FuType.csr, CSROpType.jmp, SelImm.IMM_I, xWen = T, noSpec = T, blockBack = T),
    DRET    -> XSDecode(SrcType.reg, SrcType.imm, SrcType.X, FuType.csr, CSROpType.jmp, SelImm.IMM_I, xWen = T, noSpec = T, blockBack = T),
    WFI     -> XSDecode(SrcType.pc , SrcType.imm, SrcType.X, FuType.csr, CSROpType.wfi, SelImm.X    , xWen = T, noSpec = T, blockBack = T),
```

[DecodeUnit.scala:928](/nfs/home/wanghao/emuByYuan/stable-kmh-v2/src/main/scala/xiangshan/backend/decode/DecodeUnit.scala#L928)，摘录 928～936 行。

```scala
    io.fromCSR.virtualInst.cboCF      && (isCboClean || isCboFlush) ||
    io.fromCSR.virtualInst.cboI       && isCboInval


  decodedInst.exceptionVec(illegalInstr) := exceptionII || io.enq.ctrlFlow.exceptionVec(EX_II)
  decodedInst.exceptionVec(virtualInstr) := exceptionVI

  //update exceptionVec: from frontend trigger's breakpoint exception. To reduce 1 bit of overhead in ibuffer entry.
  decodedInst.exceptionVec(breakPoint) := TriggerAction.isExp(ctrl_flow.trigger)
```

<a id="s2"></a>
## S2 Rename：ROB 身份分配与回收

[Rename.scala:176](/nfs/home/wanghao/emuByYuan/stable-kmh-v2/src/main/scala/xiangshan/backend/rename/Rename.scala#L176)，摘录 176～184 行。

```scala
  // speculatively assign the instruction with an robIdx
  val validCount = PopCount(io.in.zip(needRobFlags).map{ case(in, needRobFlag) => in.valid && in.bits.lastUop && needRobFlag}) // number of instructions waiting to enter rob (from decode)
  val robIdxHead = RegInit(0.U.asTypeOf(new RobPtr))
  val lastCycleMisprediction = GatedValidRegNext(io.redirect.valid && !io.redirect.bits.flushItself())
  val robIdxHeadNext = Mux(io.redirect.valid, io.redirect.bits.robIdx, // redirect: move ptr to given rob index
         Mux(lastCycleMisprediction, robIdxHead + 1.U, // mis-predict: not flush robIdx itself
           Mux(canOut, robIdxHead + validCount, // instructions successfully entered next stage: increase robIdx
                      /* default */  robIdxHead))) // no instructions passed by this cycle: stick to old value
  robIdxHead := robIdxHeadNext
```

[Rename.scala:342](/nfs/home/wanghao/emuByYuan/stable-kmh-v2/src/main/scala/xiangshan/backend/rename/Rename.scala#L342)，摘录 342～348 行。

```scala

    // no valid instruction from decode stage || all resources (dispatch1 + both free lists) ready
    io.in(i).ready := !io.in(0).valid || canOut

    uops(i).robIdx := robIdxHead + PopCount(io.in.zip(needRobFlags).take(i).map{ case(in, needRobFlag) => in.valid && in.bits.lastUop && needRobFlag})
    uops(i).instrSize := instrSizesVec(i)
    val hasExceptionExceptFlushPipe = Cat(selectFrontend(uops(i).exceptionVec) :+ uops(i).exceptionVec(illegalInstr) :+ uops(i).exceptionVec(virtualInstr)).orR || TriggerAction.isDmode(uops(i).trigger)
```

[Rename.scala:410](/nfs/home/wanghao/emuByYuan/stable-kmh-v2/src/main/scala/xiangshan/backend/rename/Rename.scala#L410)，摘录 410～416 行。

```scala

    // Assign performance counters
    uops(i).debugInfo.renameTime := GTimer()

    io.out(i).valid := io.in(i).valid && intFreeList.io.canAllocate && fpFreeList.io.canAllocate && vecFreeList.io.canAllocate && v0FreeList.io.canAllocate && vlFreeList.io.canAllocate && !io.rabCommits.isWalk
    io.out(i).bits := uops(i)
    // dirty code
```

<a id="s3"></a>
## S3 Dispatch：等待前方排空，不等于没有容量

[NewDispatch.scala:794](/nfs/home/wanghao/emuByYuan/stable-kmh-v2/src/main/scala/xiangshan/backend/dispatch/NewDispatch.scala#L794)，摘录 794～801 行。

```scala

  private val blockedByWaitForward = Wire(Vec(RenameWidth, Bool()))
  blockedByWaitForward(0) := !io.enqRob.isEmpty && isWaitForward(0)
  for (i <- 1 until RenameWidth) {
    blockedByWaitForward(i) := blockedByWaitForward(i - 1) || (!io.enqRob.isEmpty || Cat(fromRename.take(i).map(_.valid)).orR) && isWaitForward(i)
  }
  if(backendParams.debugEn){
    dontTouch(blockedByWaitForward)
```

[NewDispatch.scala:805](/nfs/home/wanghao/emuByYuan/stable-kmh-v2/src/main/scala/xiangshan/backend/dispatch/NewDispatch.scala#L805)，摘录 805～830 行。

```scala
  // Only the uop with block backward flag will block the next uop
  val nextCanOut = VecInit((0 until RenameWidth).map(i =>
    !isBlockBackward(i)
  ))
  val notBlockedByPrevious = VecInit((0 until RenameWidth).map(i =>
    if (i == 0) true.B
    else Cat((0 until i).map(j => nextCanOut(j))).andR
  ))

  // for noSpecExec: (robEmpty || !this.noSpecExec) && !previous.noSpecExec
  // For blockBackward:
  // this instruction can actually dequeue: 3 conditions
  // (1) resources are ready
  // (2) previous instructions are ready
  thisCanActualOut := VecInit((0 until RenameWidth).map(i => !blockedByWaitForward(i) && notBlockedByPrevious(i) && io.enqRob.canAccept))
  val thisActualOut = (0 until RenameWidth).map(i => io.enqRob.req(i).valid && io.enqRob.canAccept)

  // input for ROB, LSQ
  for (i <- 0 until RenameWidth) {
    // needAlloc no use, need deleted
    io.enqRob.needAlloc(i) := fromRename(i).valid
    io.enqRob.req(i).valid := fromRename(i).fire
    io.enqRob.req(i).bits := updatedUop(i)
    io.enqRob.req(i).bits.hasException := updatedUop(i).hasException || updatedUop(i).singleStep
    io.enqRob.req(i).bits.numWB := Mux(updatedUop(i).singleStep, 0.U, updatedUop(i).numWB)
  }
```

<a id="s4"></a>
## S4 IssueQueue：注册后的发射接口

[IssueQueue.scala:808](/nfs/home/wanghao/emuByYuan/stable-kmh-v2/src/main/scala/xiangshan/backend/issue/IssueQueue.scala#L808)，摘录 808～820 行。

```scala
  val deqDelay = Reg(params.genIssueValidBundle)
  deqDelay.zip(deqBeforeDly).foreach { case (deqDly, deq) =>
    deqDly.valid := deq.valid
    when(validVec.asUInt.orR) {
      deqDly.bits := deq.bits
    }
    // deqBeforeDly.ready is always true
    deq.ready := true.B
  }
  io.deqDelay.zip(deqDelay).foreach { case (sink, source) =>
    sink.valid := source.valid
    sink.bits := source.bits
  }
```

<a id="s5"></a>
## S5 CSR wrapper：ECALL 异常、trap 输入与 MRET 重定向

[CSR.scala:128](/nfs/home/wanghao/emuByYuan/stable-kmh-v2/src/main/scala/xiangshan/backend/fu/wrapper/CSR.scala#L128)，摘录 128～138 行。

```scala
  csrMod.io.fromRob.trap.valid := csrIn.exception.valid
  csrMod.io.fromRob.trap.bits.pc := csrIn.exception.bits.pc
  csrMod.io.fromRob.trap.bits.instr := csrIn.exception.bits.instr
  csrMod.io.fromRob.trap.bits.pcGPA := csrIn.exception.bits.gpaddr
  // Todo: shrink the width of trap vector.
  // We use 64bits trap vector in CSR, and 24 bits exceptionVec in exception bundle.
  csrMod.io.fromRob.trap.bits.trapVec := csrIn.exception.bits.exceptionVec.asUInt
  csrMod.io.fromRob.trap.bits.isFetchBkpt := csrIn.exception.bits.isPcBkpt
  csrMod.io.fromRob.trap.bits.singleStep := csrIn.exception.bits.singleStep
  csrMod.io.fromRob.trap.bits.crossPageIPFFix := csrIn.exception.bits.crossPageIPFFix
  csrMod.io.fromRob.trap.bits.isInterrupt := csrIn.exception.bits.isInterrupt
```

[CSR.scala:255](/nfs/home/wanghao/emuByYuan/stable-kmh-v2/src/main/scala/xiangshan/backend/fu/wrapper/CSR.scala#L255)，摘录 255～265 行。

```scala
  private val exceptionVec = WireInit(0.U.asTypeOf(ExceptionVec())) // Todo:

  exceptionVec(EX_BP    ) := DataHoldBypass(isEbreak, false.B, io.in.fire)
  exceptionVec(EX_MCALL ) := DataHoldBypass(isEcall && privState.isModeM, false.B, io.in.fire)
  exceptionVec(EX_HSCALL) := DataHoldBypass(isEcall && privState.isModeHS, false.B, io.in.fire)
  exceptionVec(EX_VSCALL) := DataHoldBypass(isEcall && privState.isModeVS, false.B, io.in.fire)
  exceptionVec(EX_UCALL ) := DataHoldBypass(isEcall && privState.isModeHUorVU, false.B, io.in.fire)
  exceptionVec(EX_II    ) := csrMod.io.out.bits.EX_II
  exceptionVec(EX_VI    ) := csrMod.io.out.bits.EX_VI

  val isXRet = valid && func === CSROpType.jmp && !isEcall && !isEbreak
```

[CSR.scala:307](/nfs/home/wanghao/emuByYuan/stable-kmh-v2/src/main/scala/xiangshan/backend/fu/wrapper/CSR.scala#L307)，摘录 307～334 行。

```scala
  /** Since some CSR read instructions are allowed to be pipelined, ready/valid signals should be modified */
  io.in.ready := csrMod.io.in.ready // Todo: Async read imsic may block CSR
  io.out.valid := csrModOutValid
  io.out.bits.ctrl.exceptionVec.get := exceptionVec
  io.out.bits.ctrl.flushPipe.get := flushPipe
  io.out.bits.ctrl.satpFlushPipe.get := csrMod.io.status.satp.wen || csrMod.io.status.vsatp.wen
  io.out.bits.res.data := csrMod.io.out.bits.rData

  /** initialize NewCSR's io_out_ready from wrapper's io */
  csrMod.io.out.ready := io.out.ready

  io.out.bits.res.redirect.get.valid := io.out.valid && RegEnable(isXRet, false.B, io.in.fire)
  val redirect = io.out.bits.res.redirect.get.bits
  redirect := 0.U.asTypeOf(redirect)
  redirect.level := RedirectLevel.flushAfter
  redirect.robIdx := robIdxReg
  redirect.ftqIdx := RegEnable(io.in.bits.ctrl.ftqIdx.get, io.in.fire)
  redirect.ftqOffset := RegEnable(io.in.bits.ctrl.ftqOffset.get, io.in.fire)
  redirect.cfiUpdate.predTaken := true.B
  redirect.cfiUpdate.taken := true.B
  redirect.cfiUpdate.target := csrMod.io.out.bits.targetPc.pc
  redirect.cfiUpdate.backendIPF := csrMod.io.out.bits.targetPc.raiseIPF
  redirect.cfiUpdate.backendIAF := csrMod.io.out.bits.targetPc.raiseIAF
  redirect.cfiUpdate.backendIGPF := csrMod.io.out.bits.targetPc.raiseIGPF
  // Only mispred will send redirect to frontend
  redirect.cfiUpdate.isMisPred := false.B

  connectNonPipedCtrlSingal
```

<a id="s6"></a>
## S6 ROB 和 ExceptionGen：发现异常与处理异常分离

[ExceptionGen.scala:152](/nfs/home/wanghao/emuByYuan/stable-kmh-v2/src/main/scala/xiangshan/backend/rob/ExceptionGen.scala#L152)，摘录 152～167 行。

```scala
      }
    }
  }.elsewhen (s1_out_valid && !s1_flush) {
    currentValid := true.B
    current := s1_out_bits
    current.isEnqExcp := false.B
  }.elsewhen (enq_s1_valid && !(io.redirect.valid || io.flush)) {
    currentValid := true.B
    current := enq_s1_bits
    current.isEnqExcp := true.B
  }

  io.out.valid   := s1_out_valid || enq_s1_valid && enq_s1_bits.can_writeback
  io.out.bits    := Mux(s1_out_valid, s1_out_bits, enq_s1_bits)
  io.state.valid := currentValid
  io.state.bits  := current
```

[Rob.scala:299](/nfs/home/wanghao/emuByYuan/stable-kmh-v2/src/main/scala/xiangshan/backend/rob/Rob.scala#L299)，摘录 299～300 行。

```scala
  val s_idle :: s_walk :: Nil = Enum(2)
  val state = RegInit(s_idle)
```

[Rob.scala:579](/nfs/home/wanghao/emuByYuan/stable-kmh-v2/src/main/scala/xiangshan/backend/rob/Rob.scala#L579)，摘录 579～585 行。

```scala
  val deqHitExceptionGenState = exceptionDataRead.valid && exceptionDataRead.bits.robIdx === deqPtr
  val deqNeedFlushAndHitExceptionGenState = deqNeedFlush && deqHitExceptionGenState
  val exceptionGenStateIsException = exceptionDataRead.bits.exceptionVec.asUInt.orR || exceptionDataRead.bits.singleStep || TriggerAction.isDmode(exceptionDataRead.bits.trigger)
  val deqHasException = deqNeedFlushAndHitExceptionGenState && exceptionGenStateIsException && RegNext(RegNext(deqPtrEntry.commit_w))
  val deqHasFlushPipe = deqNeedFlushAndHitExceptionGenState && exceptionDataRead.bits.flushPipe && !deqHasException && RegNext(RegNext(deqPtrEntry.commit_w))
  val deqHasReplayInst = deqNeedFlushAndHitExceptionGenState && exceptionDataRead.bits.replayInst
  val deqIsVlsException = deqHasException && deqPtrEntry.isVls && !exceptionDataRead.bits.isEnqExcp
```

[Rob.scala:619](/nfs/home/wanghao/emuByYuan/stable-kmh-v2/src/main/scala/xiangshan/backend/rob/Rob.scala#L619)，摘录 619～645 行。

```scala
  // io.flushOut will trigger redirect at the next cycle.
  // Block any redirect or commit at the next cycle.
  val lastCycleFlush = RegNext(io.flushOut.valid)

  io.flushOut.valid := (state === s_idle) && deqPtrEntryValid && (intrEnable || deqHasException && (!deqIsVlsException || deqVlsCanCommit) || isFlushPipe) && !lastCycleFlush
  io.flushOut.bits := DontCare
  io.flushOut.bits.isRVC := deqPtrEntry.isRVC
  io.flushOut.bits.robIdx := Mux(needModifyFtqIdxOffset, firstVInstrRobIdx, deqPtr)
  io.flushOut.bits.ftqIdx := Mux(needModifyFtqIdxOffset, firstVInstrFtqPtr, deqPtrEntry.ftqIdx)
  io.flushOut.bits.ftqOffset := Mux(needModifyFtqIdxOffset, firstVInstrFtqOffset, deqPtrEntry.ftqOffset)
  io.flushOut.bits.level := Mux(deqHasReplayInst || intrEnable || deqHasException || needModifyFtqIdxOffset, RedirectLevel.flush, RedirectLevel.flushAfter) // TODO use this to implement "exception next"
  io.flushOut.bits.interrupt := true.B
  io.flushOut.bits.satpFlush := isFlushPipe && exceptionDataRead.bits.satpFlushPipe
  XSPerfAccumulate("flush_num", io.flushOut.valid)
  XSPerfAccumulate("interrupt_num", io.flushOut.valid && intrEnable)
  XSPerfAccumulate("exception_num", io.flushOut.valid && deqHasException)
  XSPerfAccumulate("flush_pipe_num", io.flushOut.valid && isFlushPipe)
  XSPerfAccumulate("replay_inst_num", io.flushOut.valid && isFlushPipe && deqHasReplayInst)

  val exceptionHappen = (state === s_idle) && deqPtrEntryValid && (intrEnable || deqHasException && (!deqIsVlsException || deqVlsCanCommit)) && !lastCycleFlush
  io.exception.valid := RegNext(exceptionHappen)
  io.exception.bits.pc := RegEnable(debug_deqUop.pc, exceptionHappen)
  io.exception.bits.gpaddr := io.readGPAMemData.gpaddr
  io.exception.bits.isForVSnonLeafPTE := io.readGPAMemData.isForVSnonLeafPTE
  io.exception.bits.instr := RegEnable(debug_deqUop.instr, exceptionHappen)
  io.exception.bits.commitType := RegEnable(deqPtrEntry.commitType, exceptionHappen)
  io.exception.bits.exceptionVec := RegEnable(exceptionDataRead.bits.exceptionVec, exceptionHappen)
```

[Rob.scala:844](/nfs/home/wanghao/emuByYuan/stable-kmh-v2/src/main/scala/xiangshan/backend/rob/Rob.scala#L844)，摘录 844～853 行。

```scala
   * (1) redirect: switch to s_walk
   * (2) walk: when walking comes to the end, switch to s_idle
   */
  state_next := Mux(
    io.redirect.valid || RegNext(io.redirect.valid), s_walk,
    Mux(
      state === s_walk && walkFinished && rab.io.status.walkEnd && vtypeBuffer.io.status.walkEnd, s_idle,
      state
    )
  )
```

[Rob.scala:1171](/nfs/home/wanghao/emuByYuan/stable-kmh-v2/src/main/scala/xiangshan/backend/rob/Rob.scala#L1171)，摘录 1171～1179 行。

```scala
    exceptionGen.io.enq(i).valid := canEnqueueEG(i)
    exceptionGen.io.enq(i).bits.robIdx := io.enq.req(i).bits.robIdx
    exceptionGen.io.enq(i).bits.ftqPtr := io.enq.req(i).bits.ftqPtr
    exceptionGen.io.enq(i).bits.ftqOffset := io.enq.req(i).bits.ftqOffset
    exceptionGen.io.enq(i).bits.exceptionVec := ExceptionNO.selectFrontend(io.enq.req(i).bits.exceptionVec)
    exceptionGen.io.enq(i).bits.hasException := io.enq.req(i).bits.hasException
    exceptionGen.io.enq(i).bits.isEnqExcp := io.enq.req(i).bits.hasException
    exceptionGen.io.enq(i).bits.isFetchMalAddr := io.enq.req(i).bits.isFetchMalAddr
    exceptionGen.io.enq(i).bits.satpFlushFirstFetchFault := io.enq.req(i).bits.satpFlushFirstFetchFault
```

<a id="s7"></a>
## S7 CtrlBlock：清空控制和前端目标对齐

[CtrlBlock.scala:104](/nfs/home/wanghao/emuByYuan/stable-kmh-v2/src/main/scala/xiangshan/backend/CtrlBlock.scala#L104)，摘录 104～123 行。

```scala
  private val s0_robFlushRedirect = rob.io.flushOut
  private val s1_robFlushRedirect = Wire(Valid(new Redirect))
  s1_robFlushRedirect.valid := GatedValidRegNext(s0_robFlushRedirect.valid, false.B)
  s1_robFlushRedirect.bits := RegEnable(s0_robFlushRedirect.bits, s0_robFlushRedirect.valid)

  pcMem.io.ren.get(pcMemRdIndexes("robFlush").head) := s0_robFlushRedirect.valid
  pcMem.io.raddr(pcMemRdIndexes("robFlush").head) := s0_robFlushRedirect.bits.ftqIdx.value
  private val s1_robFlushPc = pcMem.io.rdata(pcMemRdIndexes("robFlush").head).startAddr + (RegEnable(s0_robFlushRedirect.bits.ftqOffset, s0_robFlushRedirect.valid) << instOffsetBits)
  private val s3_redirectGen = redirectGen.io.stage2Redirect
  private val s1_s3_redirect = Mux(s1_robFlushRedirect.valid, s1_robFlushRedirect, s3_redirectGen)
  private val s2_s4_pendingRedirectValid = RegInit(false.B)
  when (s1_s3_redirect.valid) {
    s2_s4_pendingRedirectValid := true.B
  }.elsewhen (GatedValidRegNext(io.frontend.toFtq.redirect.valid)) {
    s2_s4_pendingRedirectValid := false.B
  }

  // Redirect will be RegNext at ExuBlocks and IssueBlocks
  val s2_s4_redirect = RegNextWithEnable(s1_s3_redirect)
  val s3_s5_redirect = RegNextWithEnable(s2_s4_redirect)
```

[CtrlBlock.scala:333](/nfs/home/wanghao/emuByYuan/stable-kmh-v2/src/main/scala/xiangshan/backend/CtrlBlock.scala#L333)，摘录 333～349 行。

```scala
  val s5_flushFromRobValidAhead = DelayN(s1_robFlushRedirect.valid, 4)
  val s6_flushFromRobValid = GatedValidRegNext(s5_flushFromRobValidAhead)
  val frontendFlushBits = RegEnable(s1_robFlushRedirect.bits, s1_robFlushRedirect.valid) // ??
  // When ROB commits an instruction with a flush, we notify the frontend of the flush without the commit.
  // Flushes to frontend may be delayed by some cycles and commit before flush causes errors.
  // Thus, we make all flush reasons to behave the same as exceptions for frontend.
  for (i <- 0 until CommitWidth) {
    // why flushOut: instructions with flushPipe are not commited to frontend
    // If we commit them to frontend, it will cause flush after commit, which is not acceptable by frontend.
    val s1_isCommit = rob.io.commits.commitValid(i) && rob.io.commits.isCommit && !s0_robFlushRedirect.valid
    io.frontend.toFtq.rob_commits(i).valid := GatedValidRegNext(s1_isCommit)
    io.frontend.toFtq.rob_commits(i).bits := RegEnable(rob.io.commits.info(i), s1_isCommit)
  }
  io.frontend.toFtq.redirect.valid := s6_flushFromRobValid || s3_redirectGen.valid
  io.frontend.toFtq.redirect.bits := Mux(s6_flushFromRobValid, frontendFlushBits, s3_redirectGen.bits)
  io.frontend.toFtq.ftqIdxSelOH.valid := s6_flushFromRobValid || redirectGen.io.stage2Redirect.valid
  io.frontend.toFtq.ftqIdxSelOH.bits := Cat(s6_flushFromRobValid, redirectGen.io.stage2oldestOH & Fill(NumRedirect + 1, !s6_flushFromRobValid))
```

[CtrlBlock.scala:361](/nfs/home/wanghao/emuByYuan/stable-kmh-v2/src/main/scala/xiangshan/backend/CtrlBlock.scala#L361)，摘录 361～387 行。

```scala
  // Be careful here:
  // T0: rob.io.flushOut, s0_robFlushRedirect
  // T1: s1_robFlushRedirect, rob.io.exception.valid
  // T2: csr.redirect.valid
  // T3: csr.exception.valid
  // T4: csr.trapTarget
  // T5: ctrlBlock.trapTarget
  // T6: io.frontend.toFtq.stage2Redirect.valid
  val s2_robFlushPc = RegEnable(Mux(s1_robFlushRedirect.bits.flushItself(),
    s1_robFlushPc, // replay inst
    s1_robFlushPc + Mux(s1_robFlushRedirect.bits.isRVC, 2.U, 4.U) // flush pipe
  ), s1_robFlushRedirect.valid)
  private val s5_csrIsTrap = DelayN(rob.io.exception.valid, 4)
  private val s5_trapTargetFromCsr = io.robio.csr.trapTarget

  val flushTarget = Mux(s5_csrIsTrap, s5_trapTargetFromCsr.pc, s2_robFlushPc)
  val s5_trapTargetIAF = Mux(s5_csrIsTrap, s5_trapTargetFromCsr.raiseIAF, false.B)
  val s5_trapTargetIPF = Mux(s5_csrIsTrap, s5_trapTargetFromCsr.raiseIPF, false.B)
  val s5_trapTargetIGPF = Mux(s5_csrIsTrap, s5_trapTargetFromCsr.raiseIGPF, false.B)
  when (s6_flushFromRobValid) {
    io.frontend.toFtq.redirect.bits.level := RedirectLevel.flush
    io.frontend.toFtq.redirect.bits.cfiUpdate.target := RegEnable(flushTarget, s5_flushFromRobValidAhead)
    io.frontend.toFtq.redirect.bits.cfiUpdate.backendIAF := RegEnable(s5_trapTargetIAF, s5_flushFromRobValidAhead)
    io.frontend.toFtq.redirect.bits.cfiUpdate.backendIPF := RegEnable(s5_trapTargetIPF, s5_flushFromRobValidAhead)
    io.frontend.toFtq.redirect.bits.cfiUpdate.backendIGPF := RegEnable(s5_trapTargetIGPF, s5_flushFromRobValidAhead)
  }

```

[CtrlBlock.scala:738](/nfs/home/wanghao/emuByYuan/stable-kmh-v2/src/main/scala/xiangshan/backend/CtrlBlock.scala#L738)，摘录 738～745 行。

```scala
  io.toIssueBlock.flush   <> s2_s4_redirect

  pcMem.io.wen.head   := GatedValidRegNext(io.frontend.fromFtq.pc_mem_wen)
  pcMem.io.waddr.head := RegEnable(io.frontend.fromFtq.pc_mem_waddr, io.frontend.fromFtq.pc_mem_wen)
  pcMem.io.wdata.head := RegEnable(io.frontend.fromFtq.pc_mem_wdata, io.frontend.fromFtq.pc_mem_wen)

  io.toDataPath.flush := s2_s4_redirect
  io.toExuBlock.flush := s2_s4_redirect
```

<a id="s8"></a>
## S8 NewCSR：trap 选择、状态机和目标旁路

[NewCSR.scala:805](/nfs/home/wanghao/emuByYuan/stable-kmh-v2/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala#L805)，摘录 805～813 行。

```scala

  trapEntryMNEvent.valid  := ((hasTrap && nmi) || dbltrpToMN) && !entryDebugMode && !debugMode && mnstatus.regOut.NMIE
  trapEntryMEvent .valid  := hasTrap && entryPrivState.isModeM && !dbltrpToMN && !entryDebugMode && !debugMode && !nmi && mnstatus.regOut.NMIE
  trapEntryHSEvent.valid  := hasTrap && entryPrivState.isModeHS && !entryDebugMode && !debugMode && mnstatus.regOut.NMIE
  trapEntryVSEvent.valid  := hasTrap && entryPrivState.isModeVS && !entryDebugMode && !debugMode && mnstatus.regOut.NMIE

  Seq(trapEntryMEvent, trapEntryMNEvent, trapEntryHSEvent, trapEntryVSEvent, trapEntryDEvent).foreach { eMod =>
    eMod.in match {
      case in: TrapEntryEventInput =>
```

[NewCSR.scala:1044](/nfs/home/wanghao/emuByYuan/stable-kmh-v2/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala#L1044)，摘录 1044～1072 行。

```scala
  private val s_idle :: s_waitIMSIC :: s_finish :: Nil = Enum(3)

  /** the state machine of newCSR module */
  private val state = RegInit(s_idle)
  /** the next state of newCSR */
  private val stateNext = WireInit(state)
  state := stateNext

  /**
   * Asynchronous access operation of CSR. Check whether an access is asynchronous when read/write-enable is high.
   * AIA registers are designed to be access asynchronously, so newCSR will wait for response.
   **/
  private val asyncAccess = (wen || ren) && !(permitMod.io.out.EX_II || permitMod.io.out.EX_VI) && (
    mireg.addr.U === addr && miselect.inIMSICRange ||
    sireg.addr.U === addr && ((!V.asUInt.asBool && siselect.inIMSICRange) || (V.asUInt.asBool && vsiselect.inIMSICRange)) ||
    vsireg.addr.U === addr && vsiselect.inIMSICRange
  )

  /** State machine of newCSR */
  switch(state) {
    is(s_idle) {
      when(valid && redirectFlush) {
        stateNext := s_idle
      }.elsewhen(valid && asyncAccess) {
        stateNext := s_waitIMSIC
      }.elsewhen(valid) {
        stateNext := s_finish
      }
    }
```

[NewCSR.scala:1097](/nfs/home/wanghao/emuByYuan/stable-kmh-v2/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala#L1097)，摘录 1097～1112 行。

```scala
  /** Set io.in.ready when state machine is ready to receive a new request synchronously */
  io.in.ready := (state === s_idle)

  /**
   * Valid signal of newCSR output.
   * When in IDLE state, when input_valid is high, we set it.
   * When in waitIMSIC state, and the next state is IDLE, we set it.
   **/

  /** Data that have been read before,and should be stored because output not fired */
  val normalCSRValid = state === s_idle && valid && !asyncAccess
  val waitIMSICValid = state === s_waitIMSIC && fromAIA.rdata.valid
  val claimAIA = mtopei.w.wen | stopei.w.wen | vstopei.w.wen

  io.out.valid := (waitIMSICValid || state === s_finish) && !redirectFlush
  io.out.bits.EX_II := DataHoldBypass(Mux1H(Seq(
```

[NewCSR.scala:1129](/nfs/home/wanghao/emuByYuan/stable-kmh-v2/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala#L1129)，摘录 1129～1144 行。

```scala
  io.out.bits.targetPc := DataHoldBypass(
    Mux(trapEntryDEvent.out.targetPc.valid,
      trapEntryDEvent.out.targetPc.bits,
      Mux1H(Seq(
        mnretEvent.out.targetPc.valid -> mnretEvent.out.targetPc.bits,
        mretEvent.out.targetPc.valid  -> mretEvent.out.targetPc.bits,
        sretEvent.out.targetPc.valid  -> sretEvent.out.targetPc.bits,
        dretEvent.out.targetPc.valid  -> dretEvent.out.targetPc.bits,
        trapEntryMEvent.out.targetPc.valid -> trapEntryMEvent.out.targetPc.bits,
        trapEntryMNEvent.out.targetPc.valid -> trapEntryMNEvent.out.targetPc.bits,
        trapEntryHSEvent.out.targetPc.valid -> trapEntryHSEvent.out.targetPc.bits,
        trapEntryVSEvent.out.targetPc.valid -> trapEntryVSEvent.out.targetPc.bits)
      )
    ),
  needTargetUpdate)
  io.out.bits.targetPcUpdate := needTargetUpdate
```

<a id="s9"></a>
## S9 Trap entry 保存现场，MRET 恢复现场

[TrapHandleModule.scala:96](/nfs/home/wanghao/emuByYuan/stable-kmh-v2/src/main/scala/xiangshan/backend/fu/NewCSR/TrapHandleModule.scala#L96)，摘录 96～110 行。

```scala
  private val adjustinterruptNO = Mux(
    InterruptNO.getVS.map(_.U === interruptNO).reduce(_ || _) && vsHasIR,
    interruptNO - 1.U, // map VSSIP, VSTIP, VSEIP to SSIP, STIP, SEIP
    interruptNO,
  )
  private val pcFromXtvec = Cat(xtvec.addr.asUInt + Mux(xtvec.mode === XtvecMode.Vectored && hasIR, adjustinterruptNO(5, 0), 0.U), 0.U(2.W))

  io.out.entryPrivState := MuxCase(default = PrivState.ModeM, mapping = Seq(
    traptoVS -> PrivState.ModeVS,
    trapToHS -> PrivState.ModeHS,
  ))

  io.out.causeNO.Interrupt := hasIR
  io.out.causeNO.ExceptionCode := causeNO
  io.out.pcFromXtvec := pcFromXtvec
```

[TrapEntryMEvent.scala:93](/nfs/home/wanghao/emuByYuan/stable-kmh-v2/src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/TrapEntryMEvent.scala#L93)，摘录 93～100 行。

```scala
  private val tvalFillInst     = isIllegalInst

  private val tval = Mux1H(Seq(
    (tvalFillPc                        ) -> trapPC,
    (tvalFillPcPlus2                   ) -> (trapPC + 2.U),
    (tvalFillMemVaddr || isLSGuestExcp ) -> trapMemVA,
    (tvalFillInst                      ) -> trapInst,
  ))
```

[TrapEntryMEvent.scala:120](/nfs/home/wanghao/emuByYuan/stable-kmh-v2/src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/TrapEntryMEvent.scala#L120)，摘录 120～138 行。

```scala
  out.targetPc .valid := valid

  out.privState.bits            := PrivState.ModeM
  out.mstatus.bits.MPV          := current.privState.V
  out.mstatus.bits.MPP          := current.privState.PRVM
  out.mstatus.bits.GVA          := tvalFillGVA
  out.mstatus.bits.MPIE         := current.mstatus.MIE
  out.mstatus.bits.MIE          := 0.U
  out.mstatus.bits.MDT          := 1.U
  out.mepc.bits.epc             := Mux(satpFlushFirstFetchFault, trapPC(63, 1), Mux(isFetchMalAddr, in.fetchMalTval(63, 1), trapPC(63, 1)))
  out.mcause.bits.Interrupt     := isInterrupt && !isDTExcp
  out.mcause.bits.ExceptionCode := Mux(isDTExcp, ExceptionNO.EX_DT.U, highPrioTrapNO)
  out.mtval.bits.ALL            := Mux(satpFlushFirstFetchFaultExcp, tval, Mux(isFetchMalAddrExcp, in.fetchMalTval, tval))
  out.mtval2.bits.ALL           := Mux(isDTExcp, precause, tval2 >> 2)
  out.mtinst.bits.ALL           := Mux(isFetchGuestExcp && in.trapIsForVSnonLeafPTE || isLSGuestExcp && in.memExceptionIsForVSnonLeafPTE, 0x3000.U, 0.U)
  out.targetPc.bits.pc          := in.pcFromXtvec
  out.targetPc.bits.raiseIPF    := false.B
  out.targetPc.bits.raiseIAF    := AddrTransType(bare = true).checkAccessFault(in.pcFromXtvec)
  out.targetPc.bits.raiseIGPF   := false.B
```

[MretEvent.scala:51](/nfs/home/wanghao/emuByYuan/stable-kmh-v2/src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/MretEvent.scala#L51)，摘录 51～81 行。

```scala
  val outPrivState   = Wire(new PrivState)
  outPrivState.PRVM := in.mstatus.MPP
  outPrivState.V    := Mux(in.mstatus.MPP === PrivMode.M, VirtMode.Off.asUInt, in.mstatus.MPV.asUInt)

  val mretToM  = outPrivState.isModeM
  val mretToS  = outPrivState.isModeHS
  val mretToVu = outPrivState.isModeVU

  out := DontCare

  out.privState.valid := valid
  out.mstatus  .valid := valid
  out.targetPc .valid := valid

  out.privState.bits          := outPrivState
  out.mstatus.bits.MPP        := PrivMode.U
  out.mstatus.bits.MPV        := VirtMode.Off.asUInt
  out.mstatus.bits.MIE        := in.mstatus.MPIE
  out.mstatus.bits.MPIE       := 1.U
  out.mstatus.bits.MPRV       := Mux(in.mstatus.MPP =/= PrivMode.M, 0.U, in.mstatus.MPRV.asUInt)
  // clear MDT when return mret always execute in M mode
  out.mstatus.bits.MDT    := 0.U
  // clear sstatus.SDT when return mode below M and HS
  out.mstatus.bits.SDT    := Mux(mretToM || mretToS, in.mstatus.SDT.asBool, 0.U)
  // clear vsstatus.SDT when return to VU
  out.vsstatus.bits.SDT   := Mux(mretToVu, 0.U, in.vsstatus.SDT.asBool)

  out.targetPc.bits.pc        := in.mepc.asUInt
  out.targetPc.bits.raiseIPF  := instrAddrTransType.checkPageFault(in.mepc.asUInt)
  out.targetPc.bits.raiseIAF  := instrAddrTransType.checkAccessFault(in.mepc.asUInt)
  out.targetPc.bits.raiseIGPF := instrAddrTransType.checkGuestPageFault(in.mepc.asUInt)
```

<a id="s10"></a>
## S10 FTQ、IBuffer 和 Decode 的边界

[NewFtq.scala:901](/nfs/home/wanghao/emuByYuan/stable-kmh-v2/src/main/scala/xiangshan/frontend/NewFtq.scala#L901)，摘录 901～905 行。

```scala
  io.toIfu.req.valid              := entry_is_to_send && ifuPtr =/= bpuPtr
  io.toIfu.req.bits.nextStartAddr := entry_next_addr
  io.toIfu.req.bits.ftqOffset     := entry_ftq_offset
  io.toIfu.req.bits.fromFtqPcBundle(toIfuPcBundle)

```

[NewFtq.scala:951](/nfs/home/wanghao/emuByYuan/stable-kmh-v2/src/main/scala/xiangshan/frontend/NewFtq.scala#L951)，摘录 951～962 行。

```scala
    toIfuPcBundle.fallThruError && entry_hit_status(ifuPtr.value) === h_hit &&
      io.toIfu.req.fire && !(bpu_s2_redirect && bpu_s2_resp.ftq_idx === ifuPtr) && !(bpu_s3_redirect && bpu_s3_resp.ftq_idx === ifuPtr)
  )

  val ifu_req_should_be_flushed =
    io.toIfu.flushFromBpu.shouldFlushByStage2(io.toIfu.req.bits.ftqIdx) ||
      io.toIfu.flushFromBpu.shouldFlushByStage3(io.toIfu.req.bits.ftqIdx)

  when(io.toIfu.req.fire && !ifu_req_should_be_flushed) {
    entry_fetch_status(ifuPtr.value) := f_sent
  }

```

[IBuffer.scala:279](/nfs/home/wanghao/emuByYuan/stable-kmh-v2/src/main/scala/xiangshan/frontend/IBuffer.scala#L279)，摘录 279～294 行。

```scala
  io.out zip outputEntries foreach {
    case (io, reg) =>
      io.valid := reg.valid
      io.bits  := reg.bits.toCtrlFlow
  }
  (outputEntries zip bypassEntries).zipWithIndex.foreach {
    case ((out, bypass), i) =>
      when(decodeCanAccept) {
        when(useBypass && io.in.valid) {
          out := bypass
        }.otherwise {
          out := deqEntries(i)
        }
      }.elsewhen(outputEntriesIsNotFull) {
        out.valid := deqEntries(i).valid
        out.bits := Mux(
```

[CtrlBlock.scala:487](/nfs/home/wanghao/emuByYuan/stable-kmh-v2/src/main/scala/xiangshan/backend/CtrlBlock.scala#L487)，摘录 487～505 行。

```scala
   *   decode.io.in(i).valid:
   *     decodeBufValid(0) is true : decodeBufValid(i)            | from decode buffer
   *                         false : decodeFromFrontend(i).valid  | from frontend
   *
   *   decodeFromFrontend(i).ready:
   *     decodeFromFrontend(0).valid && !decodeBufValid(0) && decodeFromFrontend(i).valid && !decode.io.redirect
   *     valid instr in input, no instr in decode buffer, decodeFromFrontend(i) is valid, no redirection
   *
   *   decode.io.in(i).bits:
   *     decodeBufValid(i) is true : decodeBufBits(i)             | from decode buffer
   *                         false : decodeConnectFromFrontend(i) | from frontend
   */
  decode.io.in.zipWithIndex.foreach { case (decodeIn, i) =>
    decodeIn.valid := Mux(decodeBufValid(0), decodeBufValid(i), decodeFromFrontend(i).valid)
    decodeFromFrontend(i).ready := decodeFromFrontend(0).valid && !decodeBufValid(0) && decodeFromFrontend(i).valid && !decode.io.redirect
    decodeIn.bits := Mux(decodeBufValid(i), decodeBufBits(i), decodeConnectFromFrontend(i))
  }
  /** no valid instr in decode buffer && no valid instr from frontend --> can accept new instr from frontend */
  io.frontend.canAccept := !decodeBufValid(0) || !decodeFromFrontend(0).valid
```

<a id="s11"></a>
## S11 分支预测纠正与异常年龄优先级

[BranchUnit.scala:30](/nfs/home/wanghao/emuByYuan/stable-kmh-v2/src/main/scala/xiangshan/backend/fu/wrapper/BranchUnit.scala#L30)，摘录 30～66 行。

```scala
  val dataModule = Module(new BranchModule)
  val addModule = Module(new AddrAddModule)
  dataModule.io.src(0) := io.in.bits.data.src(0) // rs1
  dataModule.io.src(1) := io.in.bits.data.src(1) // rs2
  dataModule.io.func := io.in.bits.ctrl.fuOpType
  dataModule.io.pred_taken := io.in.bits.ctrl.predictInfo.get.taken

  val pcExtend = Mux(io.instrAddrTransType.get.shouldBeSext,
    SignExt(io.in.bits.data.pc.get, VAddrBits + 1),
    ZeroExt(io.in.bits.data.pc.get, VAddrBits + 1)
  )
  addModule.io.pcExtend := pcExtend
  addModule.io.imm := io.in.bits.data.imm // imm
  addModule.io.taken := dataModule.io.taken
  addModule.io.isRVC := io.in.bits.ctrl.preDecode.get.isRVC
  addModule.io.nextPcOffset := io.in.bits.data.nextPcOffset.get

  io.out.valid := io.in.valid
  io.in.ready := io.out.ready

  io.out.bits.res.data := 0.U
  io.out.bits.res.redirect.get match {
    case redirect =>
      redirect.valid := io.out.valid && (dataModule.io.mispredict || redirect.bits.cfiUpdate.hasBackendFault)
      redirect.bits := 0.U.asTypeOf(io.out.bits.res.redirect.get.bits)
      redirect.bits.level := RedirectLevel.flushAfter
      redirect.bits.robIdx := io.in.bits.ctrl.robIdx
      redirect.bits.ftqIdx := io.in.bits.ctrl.ftqIdx.get
      redirect.bits.ftqOffset := io.in.bits.ctrl.ftqOffset.get
      redirect.bits.fullTarget := addModule.io.target
      redirect.bits.cfiUpdate.isMisPred := dataModule.io.mispredict
      redirect.bits.cfiUpdate.taken := dataModule.io.taken
      redirect.bits.cfiUpdate.predTaken := dataModule.io.pred_taken
      redirect.bits.cfiUpdate.target := addModule.io.target
      redirect.bits.cfiUpdate.pc := io.in.bits.data.pc.get
      redirect.bits.cfiUpdate.backendIAF := io.instrAddrTransType.get.checkAccessFault(addModule.io.target)
      redirect.bits.cfiUpdate.backendIPF := io.instrAddrTransType.get.checkPageFault(addModule.io.target)
```

[RedirectGenerator.scala:39](/nfs/home/wanghao/emuByYuan/stable-kmh-v2/src/main/scala/xiangshan/backend/ctrlblock/RedirectGenerator.scala#L39)，摘录 39～62 行。

```scala
    oldestExuRedirect.bits.cfiUpdate.backendIPF := io.instrAddrTransType.checkPageFault(oldestExuRedirect.bits.fullTarget)
    oldestExuRedirect.bits.cfiUpdate.backendIGPF := io.instrAddrTransType.checkGuestPageFault(oldestExuRedirect.bits.fullTarget)
  }
  val allRedirect: Vec[ValidIO[Redirect]] = VecInit(oldestExuRedirect, loadRedirect)
  val oldestOneHot = Redirect.selectOldestRedirect(allRedirect)
  val flushAfter = RegInit(0.U.asTypeOf(ValidIO(new Redirect)))
  val needFlushVec = VecInit(allRedirect.map(_.bits.robIdx.needFlush(flushAfter) || robFlush.valid))
  val oldestValid = VecInit(oldestOneHot.zip(needFlushVec).map { case (v, f) => v && !f }).asUInt.orR
  val oldestExuPredecode = io.oldestExuOutPredecode
  val oldestRedirect = Mux1H(oldestOneHot, allRedirect)
  val s1_redirect_bits_reg = RegEnable(oldestRedirect.bits, oldestValid)
  val s1_redirect_valid_reg = GatedValidRegNext(oldestValid)
  val s1_redirect_onehot = VecInit(oldestOneHot.map(x => GatedValidRegNext(x)))

  if (backendParams.debugEn){
    dontTouch(oldestValid)
    dontTouch(needFlushVec)
  }
  val flushAfterCounter = Reg(UInt(3.W))
  val robFlushOrExuFlushValid = oldestValid || robFlush.valid
  when(robFlushOrExuFlushValid) {
    flushAfter.valid := true.B
    flushAfter.bits := Mux(robFlush.valid, robFlush.bits, oldestRedirect.bits)
  }.elsewhen(!flushAfterCounter(0)) {
```

[CtrlBlock.scala:190](/nfs/home/wanghao/emuByYuan/stable-kmh-v2/src/main/scala/xiangshan/backend/CtrlBlock.scala#L190)，摘录 190～199 行。

```scala
  )

  private val exuRedirects: Seq[ValidIO[Redirect]] = io.fromWB.wbData.filter(_.bits.redirect.nonEmpty).map(x => {
    val hasCSR = x.bits.params.hasCSR
    val out = Wire(Valid(new Redirect()))
    out.valid := x.valid && x.bits.redirect.get.valid && !x.bits.robIdx.needFlush(Seq(s1_s3_redirect, s2_s4_redirect))
    out.bits := x.bits.redirect.get.bits
    out.bits.debugIsCtrl := true.B
    out.bits.debugIsMemVio := false.B
    // for fix timing, next cycle assgin
```

<a id="s12"></a>
## S12 波形采样方法

仓库 wavekit 的 `src/wavekit/readers/base.py` 提供 `load_waveform(..., sample_on_posedge=True)` 与 `load_unknown_mask`；`src/wavekit/readers/value_change.pyx` 采用 `value_time <= clock_time` 的末值规则。故本文以同时间戳最终记录值为准，不宣称恢复了 delta-cycle 内部变化。
