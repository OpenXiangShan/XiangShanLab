package minixiangshan.backend.rob

import chisel3._
import chisel3.util._
import minixiangshan.config._
import minixiangshan.backend.dispatch._
import minixiangshan.backend.rename._
import minixiangshan.backend.decode._
import minixiangshan.backend.execute._
import minixiangshan.util.CircularQueuePtr
import minixiangshan.backend.redirect._
import minixiangshan.csr._
import minixiangshan.mmu._
 
// ═══════════════════════════════════════════════════════════════
//  ROB 内部表项
// ═══════════════════════════════════════════════════════════════
class RobEntryInner(implicit p: Parameters) extends NSBundle {
  val pc          = UInt(XLEN.W)
  val inst        = UInt(XLEN.W)
  val fuType      = UInt(FuType.width.W)
  val pdst        = UInt(PhyRegIdxWidth.W)
  val oldPdst     = UInt(PhyRegIdxWidth.W)
  val ldst        = UInt(5.W)
  val rfWen       = Bool()
  val rfdata      = UInt(XLEN.W)
  val memRead     = Bool()
  val memWrite    = Bool()
  val memVaddr    = UInt(XLEN.W)
  val memPaddr    = UInt(XLEN.W)
  val storeData   = UInt(XLEN.W)
  val storeValid  = Bool()
  val sqIdx       = new SqPtr(SqSize)
  val csrWen      = Bool()
  val csrOp       = UInt(CsrOp.width.W)
  val csrWaddr    = UInt(csrAddrLen.W)
  val csrWdata    = UInt(XLEN.W)
  val csrTimer    = UInt(64.W)
  val tlbOp       = UInt(TlbOp.width.W)
  val tlbFillIdx  = UInt(tlbIdxLen.W)
  val flushOnCommit = Bool()
  val privLevel   = UInt(PrivLevel.width.W)   // 指令要求的最小特权级
  val isIdle      = Bool()
  val waitStore   = Bool()
  val lrValidSet    = Bool()
  val lrValidClear  = Bool()
  val fenceI        = Bool()
  val isCacheOp     = Bool()
  val cacheOpType = UInt(CacheOpCode.cacheTypeWidth.W)
  val cacheOpOperation = UInt(CacheOpCode.operationWidth.W)
  val excp        = new ExceptionBundle
  val robIdx      = new RobPtr(RobSize)
  val writtenBack = Bool()
  val valid       = Bool()
  val needsRollback = Bool()   // 保留以维持接口兼容性，内部已通过状态机指针化简
}
 
class RobCommitIO(implicit p: Parameters) extends NSBundle {
  val valid        = Vec(CommitWidth, Output(Bool()))
  val bits         = Vec(CommitWidth, Output(new RobEntryInner))
  val isWalk       = Output(Bool())
  val isExcpCommit = Vec(CommitWidth, Output(Bool()))
}
 
class RobCommitToSq(implicit p: Parameters) extends NSBundle {
  val valid = Vec(CommitWidth, Output(Bool()))
  val bits  = Vec(CommitWidth, Output(new RobEntryInner))
}
 
class RobCommitToCsr(implicit p: Parameters) extends NSBundle {
  val csrWen   = Bool()
  val csrWaddr = UInt(csrAddrLen.W)
  val csrWdata = UInt(XLEN.W)
  val lrValidSet = Bool()
  val lrValidClear = Bool()
//icahe的invalid由redirect做
// val fenceI = Bool()
  val idle = Bool()
}

class ArchCommitInfo(implicit p: Parameters) extends NSBundle {
  val valid   = Bool()
  val isWalk  = Bool()
  val ldst    = UInt(5.W)
  val pdst    = UInt(PhyRegIdxWidth.W)
  val oldPdst = UInt(PhyRegIdxWidth.W)
  val rfWen   = Bool()
}

// ═══════════════════════════════════════════════════════════════
//  重排序缓冲区 (ROB) 
// ═══════════════════════════════════════════════════════════════
class ROB(implicit p: Parameters) extends NSModule {
 
  val io = IO(new Bundle {
    val flush            = Input(Bool())
    val enq              = Flipped(new RobEnqIO)
    val commit           = new RobCommitIO
    val commitToSq       = new RobCommitToSq
    val commitToCsr      = new RobCommitToCsr
    val currentPriv       = Input(UInt(privLen.W))
    val storeQueueEmpty  = Input(Bool())
    val fenceIReq     = Output(Bool())
    val fenceIReady    = Input(Bool())
    val cacheOpICacheReq   = Output(Bool())
    val writeback        = Input(Vec(WbBusWidth, Valid(new RobWriteback)))
 
    val archCommit       = Vec(CommitWidth, Output(new ArchCommitInfo))
    val robRedirect      = Output(new RobRedirectReq)
    val redirectInfo     = Flipped(ValidIO(new redirectInfoToModule))
    val robPause         = Input(Bool())
    val robNeedRollback  = Input(Bool())
    val robRollbackTarget= Input(new RobPtr(RobSize))
    val robRollbackDone  = Output(Bool())

    val robCount     = Output(UInt(log2Ceil(RobSize + 1).W))
    val head         = Output(Valid(new RobPtr(RobSize)))
    val enqFromDispatch  = Flipped(new RobEnqIO)
    
  })
 
  // ================================================================
  //  0. 指针类型与辅助函数
  // ================================================================
  class RobPtrInner extends CircularQueuePtr[RobPtrInner](RobSize)
 
  //def decPtr(ptr: RobPtrInner): RobPtrInner = {
  //  val next = Wire(new RobPtrInner)
  //  when(ptr.value === 0.U) {
  //    next.value := (RobSize - 1).U
  //    next.flag  := !ptr.flag
  //  }.otherwise {
  //    next.value := ptr.value - 1.U
  //    next.flag  := ptr.flag
  //  }
  //  next
  //}
 
  //def ptrEq(a: RobPtrInner, b: RobPtrInner): Bool =
  //  a.value === b.value && a.flag === b.flag

  // ================================================================
  //  1. 存储体 + 头尾指针
  // ================================================================
  val entries = RegInit(VecInit(Seq.fill(RobSize)(0.U.asTypeOf(new RobEntryInner))))
  diffDontTouch(entries)
 
  val deqPtr = RegInit({ val p = Wire(new RobPtrInner); p.value := 0.U; p.flag := false.B; p })
  val enqPtr = RegInit({ val p = Wire(new RobPtrInner); p.value := 0.U; p.flag := false.B; p })
 
  val full  = (deqPtr.value === enqPtr.value) && (deqPtr.flag =/= enqPtr.flag)
  val count = enqPtr.distanceTo(deqPtr)
  io.robCount := count
  io.head.valid := entries(deqPtr.value).valid
  io.head.bits  := deqPtr //entries(deqPtr.value).robIdx
 
  // ================================================================
  //  2. 入队逻辑 (Enqueue)
  // ================================================================
  val enqValidCount = PopCount(io.enq.valid)
  //io.enq.canEnq := !full 
  //&& (count +& enqValidCount <= RobSize.U)
  //io.enq.full := count  > RobSize.U - 6.U //真没招了
 
  val enqPrefixSum = Wire(Vec(CtrlBlockWidth + 1, UInt(log2Ceil(RobSize).W)))
  enqPrefixSum(0) := 0.U
  for (j <- 0 until CtrlBlockWidth) {
    enqPrefixSum(j + 1) := enqPrefixSum(j) + io.enq.valid(j).asUInt
  }
 
  val bruArrived = io.redirectInfo.valid && io.redirectInfo.bits.doRedirect && io.redirectInfo.bits.fromBru
  
  for (i <- 0 until CtrlBlockWidth) {
    val writeIdx = (enqPtr.value + enqPrefixSum(i))(log2Ceil(RobSize) - 1, 0)
    when(io.enq.valid(i) /* && io.enq.canEnq */ && !io.robPause) {
      entries(writeIdx).pc           := io.enq.bits(i).pc
      entries(writeIdx).inst         := io.enq.bits(i).inst
      entries(writeIdx).pdst         := io.enq.bits(i).pdst
      entries(writeIdx).oldPdst      := io.enq.bits(i).oldPdst
      entries(writeIdx).ldst         := io.enq.bits(i).ldst
      entries(writeIdx).rfWen        := io.enq.bits(i).rfWen
      entries(writeIdx).memRead      := io.enq.bits(i).memRead
      entries(writeIdx).memWrite     := io.enq.bits(i).memWrite
      entries(writeIdx).memVaddr     := 0.U
      entries(writeIdx).memPaddr     := 0.U
      entries(writeIdx).storeData    := 0.U
      entries(writeIdx).storeValid   := false.B
      entries(writeIdx).csrWen       := io.enq.bits(i).csrWen
      entries(writeIdx).csrWaddr     := io.enq.bits(i).csrWaddr
      entries(writeIdx).csrOp        := io.enq.bits(i).csrOp
      entries(writeIdx).tlbOp        := io.enq.bits(i).tlbOp
      entries(writeIdx).tlbFillIdx := 0.U
      entries(writeIdx).flushOnCommit:= io.enq.bits(i).flushOnCommit
      entries(writeIdx).privLevel    := io.enq.bits(i).privLevel
      entries(writeIdx).isIdle       := io.enq.bits(i).isIdle
      entries(writeIdx).waitStore    := io.enq.bits(i).waitStore
      entries(writeIdx).lrValidSet     := io.enq.bits(i).lrValidSet
      entries(writeIdx).lrValidClear   := io.enq.bits(i).lrValidClear
      entries(writeIdx).fenceI         := io.enq.bits(i).fenceI
      entries(writeIdx).isCacheOp      := io.enq.bits(i).isCacheOp
      entries(writeIdx).cacheOpType := io.enq.bits(i).cacheOpType
      entries(writeIdx).cacheOpOperation := io.enq.bits(i).cacheOpOperation
      entries(writeIdx).fuType       := io.enq.bits(i).fuType
      entries(writeIdx).excp         := io.enq.bits(i).excp
      entries(writeIdx).writtenBack  := false.B
      entries(writeIdx).needsRollback:= false.B
      entries(writeIdx).robIdx.value := writeIdx
      entries(writeIdx).robIdx.flag  := enqPtr.flag ^ (enqPtr.value +& enqPrefixSum(i) >= RobSize.U)
    }
  }
 
  when(/* io.enq.canEnq && */ enqValidCount.orR && io.enq.valid.asUInt.orR && !io.robPause ) {
    enqPtr := enqPtr + enqValidCount
  }
 
  // ================================================================
  //  3. 写回逻辑 (Writeback)
  // ================================================================
  for (wb <- io.writeback) {
    when(wb.valid) {
      val idx = wb.bits.robIdx.value
      entries(idx).writtenBack := true.B
      entries(idx).rfdata      := wb.bits.rfdata
      entries(idx).sqIdx       := wb.bits.sqIdx
      when(entries(idx).isCacheOp) {
        entries(idx).memRead   := false.B
        entries(idx).memWrite  := false.B
        entries(idx).memVaddr  := wb.bits.memVaddr
        entries(idx).memPaddr  := wb.bits.memPaddr
        entries(idx).storeData := 0.U
      }
      when(wb.bits.memValid) {
        entries(idx).memRead    := wb.bits.isMemRead
        entries(idx).memWrite   := wb.bits.isMemWrite
        entries(idx).memVaddr   := wb.bits.memVaddr
        entries(idx).memPaddr   := wb.bits.memPaddr
        entries(idx).storeData  := wb.bits.memStoreData
        entries(idx).storeValid := wb.bits.storeValid
      }
      entries(idx).csrWdata := wb.bits.csrWdata
      entries(idx).csrTimer := wb.bits.csrTimer
      entries(idx).tlbFillIdx := wb.bits.tlbFillIdx
      when(wb.bits.excp.hasException) {
        entries(idx).excp := wb.bits.excp
      }
    }
  }

  // ================================================================
  //  4. 提交逻辑 (Commit) - 完全修复组合逻辑链
  // ================================================================
  val commitValids     = Wire(Vec(CommitWidth, Bool()))
  val commitCandidates = Wire(Vec(CommitWidth, new RobEntryInner))

  val headEntry = entries(deqPtr.value)
  val headIsDcacheCacheOp = headEntry.isCacheOp &&
    headEntry.cacheOpType === CacheOpCode.dCache &&
    headEntry.cacheOpOperation =/= CacheOpCode.implementationDefined
  val headIsIcacheCacheOp = headEntry.isCacheOp &&
    headEntry.cacheOpType === CacheOpCode.iCache &&
    headEntry.cacheOpOperation =/= CacheOpCode.implementationDefined
  // toDcache
  io.fenceIReq := 
    RegNext(
      headEntry.valid && headEntry.writtenBack &&
      (headEntry.fenceI || headIsDcacheCacheOp) && io.storeQueueEmpty &&
      !headEntry.excp.hasException
    )
  // Icache的cacheOp
  //已弃用
  io.cacheOpICacheReq := headEntry.valid && headEntry.writtenBack &&
    headIsIcacheCacheOp && io.storeQueueEmpty && !headEntry.excp.hasException
  
  val canConsider = Wire(Vec(CommitWidth, Bool()))
  val isExcpSlot  = Wire(Vec(CommitWidth, Bool()))
  val isCsrSlot   = Wire(Vec(CommitWidth, Bool()))
  val isFlushSlot = Wire(Vec(CommitWidth, Bool()))
  val blockNext   = Wire(Vec(CommitWidth, Bool())) // 标记当前槽位是否会阻断后续槽位
  val inFlushRange = Wire(Vec(RobSize, Bool()))
  for (i <- 0 until CommitWidth) {
    val idx       = (deqPtr.value + i.U)(log2Ceil(RobSize) - 1, 0)
    val entry     = entries(idx)
    val isCacheOp   = entry.isCacheOp
    val userHitCacheOpAllowed = isCacheOp &&
      io.currentPriv === 3.U &&
      CacheOpCode.isHitOp(entry.cacheOpOperation)
    // 特权级检查：PrivLevel.s(1) 需要 S 及以上，PrivLevel.m(3) 需要 M
    val privViolation =
      ((entry.privLevel === PrivLevel.s) && (io.currentPriv === 0.U)) ||
      ((entry.privLevel === PrivLevel.m) && (io.currentPriv =/= 3.U))
    val isIllegalPriv = privViolation && !userHitCacheOpAllowed
    val commitExcp = Wire(new ExceptionBundle)
    commitExcp.excpVec := entry.excp.mergeMany(
      base = entry.excp.excpVec,
      isIllegalPriv -> ExcType.ILLEGAL
    )
    commitExcp.intrCode := entry.excp.intrCode
    val hasExcp = commitExcp.hasException
    val olderStoreCommitting = if (i == 0) false.B else {
      VecInit((0 until i).map(j =>
        canConsider(j) && commitCandidates(j).memWrite)).asUInt.orR
    }
    val fenceIReadyLocal = !entry.fenceI || io.fenceIReady
    val cacheOpReady = !entry.isCacheOp ||
      entry.cacheOpOperation === CacheOpCode.implementationDefined ||
      entry.cacheOpType =/= CacheOpCode.dCache || io.fenceIReady
    val storeReady = !entry.waitStore ||
      (io.storeQueueEmpty && !olderStoreCommitting && fenceIReadyLocal && cacheOpReady) || hasExcp
    val thisReady = entry.valid && entry.writtenBack &&
      storeReady && !inFlushRange(idx)
    val isCsrW    = entry.csrWen && !hasExcp

    // 构建严格的依赖链，代替存在 BUG 的 Scala var 循环赋值
    if (i == 0) {
      canConsider(0) := thisReady && !io.robPause 
    } else {
      canConsider(i) := canConsider(i-1) && !blockNext(i-1) && thisReady
    }

    isExcpSlot(i)       := canConsider(i) && hasExcp
    isCsrSlot(i)        := canConsider(i) && isCsrW  && !hasExcp
    isFlushSlot(i)      := canConsider(i) && entry.flushOnCommit && !hasExcp
    commitValids(i)     := canConsider(i) //&& !hasExcp // 正常提交（异常指令也走提交的方式来消失） 
    commitCandidates(i) := entry
    commitCandidates(i).excp := commitExcp

    // Exceptions, CSR writes and all flush-on-commit instructions serialize retirement.
    blockNext(i) := !canConsider(i) || isExcpSlot(i) ||
      isCsrSlot(i) || isFlushSlot(i)
  }

  // 提取首个导致 Redirect 的槽位信息
  val redirectMask  = isExcpSlot.asUInt | isCsrSlot.asUInt | isFlushSlot.asUInt
  val redirectValid = redirectMask.orR
  val redirectIdx   = PriorityEncoder(redirectMask)
  val redirectEntry = commitCandidates(redirectIdx)

  io.robRedirect.valid       := redirectValid
  io.robRedirect.isException := isExcpSlot(redirectIdx)
  io.robRedirect.robIdx      := redirectEntry.robIdx
  io.robRedirect.excp        := redirectEntry.excp
  io.robRedirect.pc          := redirectEntry.pc
  io.robRedirect.inst        := redirectEntry.inst
  io.robRedirect.excpVaddr   := redirectEntry.memVaddr
  io.robRedirect.invalidIcache       :=   redirectValid && (redirectEntry.fenceI ||

  ( redirectEntry.isCacheOp &&
    redirectEntry.cacheOpType === CacheOpCode.iCache &&
    redirectEntry.cacheOpOperation =/= CacheOpCode.implementationDefined)
   )&&  !redirectEntry.excp.hasException

  io.commitToCsr.csrWen      := isCsrSlot.asUInt.orR
  io.commitToCsr.csrWaddr    := redirectEntry.csrWaddr
  io.commitToCsr.csrWdata    := redirectEntry.csrWdata

  io.commitToCsr.lrValidSet    :=   redirectValid && redirectEntry.lrValidSet &&  !redirectEntry.excp.hasException
  io.commitToCsr.lrValidClear  :=   redirectValid && redirectEntry.lrValidClear &&  !redirectEntry.excp.hasException
   
  io.commitToCsr.idle        :=   redirectValid && redirectEntry.isIdle &&  !redirectEntry.excp.hasException
  
  // 输出正常 Commit 信号
  for (i <- 0 until CommitWidth) {
    io.commit.valid(i)        := commitValids(i)
    io.commit.bits(i)         := commitCandidates(i)
    io.commit.isExcpCommit(i) := commitCandidates(i).excp.hasException
    
    io.commitToSq.valid(i)    := commitValids(i) &&
      commitCandidates(i).memWrite
    io.commitToSq.bits(i)     := commitCandidates(i)
    
    //异常也是必须要提交架构，但他并不是“提交架构”，而是复用这个端口来归还“物理寄存器”
    io.archCommit(i).valid    := commitValids(i) && commitCandidates(i).rfWen && commitCandidates(i).ldst =/= 0.U  //&& !commitCandidates(i).excp.hasException
    io.archCommit(i).isWalk   := Mux(commitCandidates(i).excp.hasException, true.B, false.B)
    io.archCommit(i).ldst     := commitCandidates(i).ldst
    io.archCommit(i).pdst     := commitCandidates(i).pdst
    io.archCommit(i).oldPdst  := commitCandidates(i).oldPdst
    io.archCommit(i).rfWen    := commitValids(i) && commitCandidates(i).rfWen
  }
  io.commit.isWalk := false.B

  val commitCount = PopCount(commitValids)
  when(commitCount.orR) {
    deqPtr := deqPtr + commitCount
  }
 
  // ================================================================
  //  5. BRU 重定向冲刷 (Redirect Flush)
  // ================================================================
  val redirectValidReg  = RegInit(false.B)
  val redirectBegin = RegInit(0.U(log2Ceil(RobSize).W))
  val redirectEnd   = RegInit(0.U(log2Ceil(RobSize).W))
  val redirectFlushSelf = RegInit(false.B)
  //val redirectAll      = RegInit(false.B)
 

  when(redirectValidReg) { 
    redirectValidReg := false.B
  }
  //顺序不能换//正确处理两条bru连续到来的情况
  when(bruArrived) {
    redirectValidReg  := true.B
    redirectBegin     := io.redirectInfo.bits.robIdx.value
    redirectEnd       := enqPtr.value
    redirectFlushSelf := io.redirectInfo.bits.flushSelf
    //redirectAll :=   (io.redirectInfo.bits.robIdx.value === enqPtr.value) && (io.redirectInfo.bits.robIdx.flag ^ enqPtr.flag)
    enqPtr            := io.redirectInfo.bits.robIdx + 1.U
    
  }
// when(redirectValidReg){
//   
// }

 
  // ================================================================
  //  6. ROB 回滚逻辑 (Rollback FSM)
  // ================================================================
  val rb_idle :: wait_reg ::rb_buffer :: rb_disp ::rb_rob :: Nil = Enum(5)
  val rollbackState = RegInit(rb_idle)
  val isRollingBack = rollbackState =/= rb_idle
 
  // ── 锁存未能入队的 Dispatch 寄存器信息，防止物理寄存器泄漏 ──
  //val latchCanEnq   = RegInit(true.B)
  val latchEnqValid = RegInit(VecInit(Seq.fill(CtrlBlockWidth)(false.B)))
  val latchEnqPdst  = RegInit(VecInit(Seq.fill(CtrlBlockWidth)(0.U(PhyRegIdxWidth.W))))
  val latchEnqRfWen = RegInit(VecInit(Seq.fill(CtrlBlockWidth)(false.B)))

  //val dispatchCanEnq   = RegInit(true.B)
  val dispatchValid = RegInit(VecInit(Seq.fill(CtrlBlockWidth)(false.B)))
  val dispatchPdst  = RegInit(VecInit(Seq.fill(CtrlBlockWidth)(0.U(PhyRegIdxWidth.W))))
  val dispatchRfWen = RegInit(VecInit(Seq.fill(CtrlBlockWidth)(false.B)))
 
  when(io.robNeedRollback && rollbackState === rb_idle) {
    //latchCanEnq := io.enq.canEnq
    for (i <- 0 until CtrlBlockWidth) {
      latchEnqValid(i) := io.enq.validforPreg(i) && /*io.enq.canEnq && */ io.enq.bits(i).rfWen && io.enq.bits(i).ldst =/= 0.U
      latchEnqPdst(i)  := io.enq.bits(i).pdst
      latchEnqRfWen(i) := io.enq.bits(i).rfWen
    }

    //dispatchCanEnq := io.enqFromDispatch.canEnq
    for (i <- 0 until CtrlBlockWidth) {
      dispatchValid(i) := io.enqFromDispatch.validforPreg(i) && /*!io.enqFromDispatch.canEnq && */ io.enqFromDispatch.bits(i).rfWen && io.enqFromDispatch.bits(i).ldst =/= 0.U
      dispatchPdst(i)  := io.enqFromDispatch.bits(i).pdst
      dispatchRfWen(i) := io.enqFromDispatch.bits(i).rfWen
    }


  }
 
  val dispIdx     = RegInit(0.U(log2Ceil(CtrlBlockWidth + 1).W))
  val rollbackPtr = RegInit({ val p = Wire(new RobPtrInner); p.value := 0.U; p.flag := false.B; p })
  //val rollbackAtDeq = ptrEq(rollbackPtr, deqPtr)
  val rollbackAtDeq = rollbackPtr === deqPtr
 
  // ── 启动回滚与状态转移 ──
  when(io.robNeedRollback && rollbackState === rb_idle) {
    //latchEnqValid和dispatchValid是需要先存一级寄存器的
    //所以要先等他俩存好延迟一个周期
    rollbackState := wait_reg


  }
 
  switch(rollbackState) {
    is(wait_reg){
      when((/* !latchCanEnq || */ !latchEnqValid.asUInt.orR) && ( /* !dispatchCanEnq || */ !dispatchValid.asUInt.orR)) {
        // Dispatch 无遗漏，直接进入 ROB 扫描阶段
        rollbackState := rb_rob
        rollbackPtr   := Mux(enqPtr === deqPtr , deqPtr, enqPtr-1.U )
      }.otherwise {
        rollbackState := rb_buffer
        dispIdx       := 0.U
      }
    }
    is(rb_buffer) {
      when(dispIdx >= CtrlBlockWidth.U - 1.U) {
        //rollbackState := rb_rob
        //rollbackPtr   := Mux(ptrEq(enqPtr, deqPtr), deqPtr, decPtr(enqPtr)) // 安全起见检查 ROB 是否已空
        rollbackState := rb_disp
        dispIdx       := 0.U
      }.otherwise {
        dispIdx := dispIdx + 1.U
      }
    }

    is(rb_disp) {
      when(dispIdx >=  CtrlBlockWidth.U - 1.U) {
        rollbackState := rb_rob
        rollbackPtr   := Mux(enqPtr === deqPtr, deqPtr, enqPtr - 1.U) // 安全起见检查 ROB 是否已空
      }.otherwise {
        dispIdx := dispIdx + 1.U
      }
    }

    is(rb_rob) {
      when(rollbackAtDeq) {
        rollbackState := rb_idle
        enqPtr        := deqPtr // 回滚彻底完成，清空游标
      }.otherwise {
       // rollbackPtr   := decPtr(rollbackPtr)
        rollbackPtr   := rollbackPtr - 1.U
      }
    }
  }

  // 完美对接 Controller 的时序：在回滚扫到 deqPtr 的当拍发出 done
  io.robRollbackDone := (rollbackState === rb_rob) && rollbackAtDeq
 
  // ── 覆盖输出：回滚时强制通过 ArchCommit 接口进行 pdst 释放 ──
  val rollbackEntry    = entries(rollbackPtr.value)
  val rollbackNeedFree = rollbackEntry.valid && rollbackEntry.rfWen && rollbackEntry.ldst =/= 0.U
  val bufferNeedFree     = latchEnqValid(dispIdx)

  val dispNeedFree     = dispatchValid(dispIdx)
 
  when(isRollingBack) {

    for (i <- 0 until CommitWidth) {
      io.archCommit(i).valid  := false.B
      io.archCommit(i).isWalk := false.B
    }

    
    // 第 0 槽位专门用于逐条归还物理寄存器
    when(rollbackState === rb_buffer && dispIdx < CtrlBlockWidth.U && bufferNeedFree) {
      io.archCommit(0).valid   := true.B
      io.archCommit(0).isWalk  := true.B
      io.archCommit(0).pdst    := latchEnqPdst(dispIdx)
      io.archCommit(0).ldst    := 0.U
      io.archCommit(0).oldPdst := 0.U
      io.archCommit(0).rfWen   := latchEnqRfWen(dispIdx)
    }.elsewhen(rollbackState === rb_disp && dispIdx < CtrlBlockWidth.U && dispNeedFree) {
      if(CommitWidth == 1) {
        io.archCommit(0).valid   := true.B
        io.archCommit(0).isWalk  := true.B
        io.archCommit(0).pdst    := dispatchPdst(dispIdx)
        io.archCommit(0).ldst    := 0.U
        io.archCommit(0).oldPdst := 0.U
        io.archCommit(0).rfWen   := dispatchRfWen(dispIdx)
      }else{
        io.archCommit(1).valid   := true.B
        io.archCommit(1).isWalk  := true.B
        io.archCommit(1).pdst    := dispatchPdst(dispIdx)
        io.archCommit(1).ldst    := 0.U
        io.archCommit(1).oldPdst := 0.U
        io.archCommit(1).rfWen   := dispatchRfWen(dispIdx)
      }
    }.elsewhen(rollbackState === rb_rob && rollbackNeedFree) {
      if(CommitWidth == 1) {
        io.archCommit(0).valid   := true.B
        io.archCommit(0).isWalk  := true.B
        io.archCommit(0).pdst    := rollbackEntry.pdst
        io.archCommit(0).ldst    := 0.U
        io.archCommit(0).oldPdst := 0.U
        io.archCommit(0).rfWen   := true.B
      }else{
        io.archCommit(2).valid   := true.B
        io.archCommit(2).isWalk  := true.B
        io.archCommit(2).pdst    := rollbackEntry.pdst
        io.archCommit(2).ldst    := 0.U
        io.archCommit(2).oldPdst := 0.U
        io.archCommit(2).rfWen   := true.B
      }
    }
    
  }
 
  // ================================================================
  //  7. 全局 Valid 位统一控制 (优先级仲裁)
  // ================================================================
  

  for (i <- 0 until RobSize) {
    // A. 判断入队命中
    val enqHit = VecInit((0 until CtrlBlockWidth).map(j => {
      val allocPtr = (enqPtr.value + enqPrefixSum(j))(log2Ceil(RobSize) - 1, 0)
      io.enq.valid(j) /* && io.enq.canEnq */ && allocPtr === i.U
    })).asUInt.orR && !bruArrived
 
    // B. 判断 Commit 命中
    val commitHit = VecInit(commitValids.zipWithIndex.map { case (v, j) =>
      v && ((deqPtr.value + j.U)(log2Ceil(RobSize) - 1, 0) === i.U)
    }).asUInt.orR
 
    // C. 判断 BRU 重定向冲刷命中
    inFlushRange(i) := redirectValidReg && Mux(redirectEnd > redirectBegin,
      i.U > redirectBegin && i.U < redirectEnd,
      i.U > redirectBegin || i.U < redirectEnd
    ) //|| redirectAll )

    val flushSelfHit = redirectValidReg && redirectFlushSelf && i.U === redirectBegin
    val redirectFlushHit = inFlushRange(i) || flushSelfHit
 
    // D. 判断 Rollback 回滚擦除命中
    val rollbackHit = (rollbackState === rb_rob) && (rollbackPtr.value === i.U)
 
    // 严格按照硬件优先级写入，防止互相覆盖
    when(io.flush) {
      entries(i).valid := false.B
    }.elsewhen(enqHit) {
      entries(i).valid := true.B
    }.elsewhen(commitHit) {
      entries(i).valid := false.B
    }.elsewhen(redirectFlushHit) {
      entries(i).valid := false.B
    }.elsewhen(rollbackHit) {
      entries(i).valid := false.B
    }
  }
 
  // ================================================================
  //  8. 全局复位 (Global Flush)
  // ================================================================
  when(io.flush) {
    deqPtr.value      := 0.U
    deqPtr.flag       := false.B
    enqPtr.value      := 0.U
    enqPtr.flag       := false.B
    redirectValidReg  := false.B
    rollbackState     := rb_idle
  }
}
