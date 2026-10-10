package minixiangshan.mem
 
import chisel3._
import chisel3.util._
import minixiangshan.config._
import minixiangshan.backend.dispatch._
import minixiangshan.backend.decode._
import minixiangshan.backend.rename._
import minixiangshan.backend.execute._
import minixiangshan.mmu._
import minixiangshan.util.CircularQueuePtr
 
// ★ 新增：SQ → LQ 的转发信息 Bundle
class SqForwardInfoBundle(implicit p: Parameters) extends NSBundle {
  val paddr        = UInt(XLEN.W)
  val addrValid    = Bool()
  val dataValid    = Bool()
  val data         = UInt(XLEN.W)
  val robIdxFull   = new RobPtr(RobSize)
  val valid        = Bool()
  val alreadyFlush = Bool()
  val hasException = Bool()
  val lsuOp        = UInt(LsuOp.width.W)
  val cacheable    = Bool() 
  val committed    = Bool()

}
 
class StoreQueue(implicit p: Parameters) extends NSModule {
 
  class SqPtrInner extends CircularQueuePtr[SqPtrInner](SqSize)
 
  class SqEntry(implicit p: Parameters) extends NSBundle {
    val robIdxFull   = new RobPtr(RobSize)
    val lqIdx        = UInt(log2Ceil(LqSize).W)
    val valid        = Bool()
    val addrValid    = Bool()
    val dataValid    = Bool()
    val committed    = Bool()
    val writtenBack  = Bool()
    val Memwritten   = Bool()
    val alreadyFlush = Bool()
    val dcacheIssued = Bool()
    val vaddr        = UInt(XLEN.W)
    val paddr        = UInt(XLEN.W)
    val data         = UInt(XLEN.W)
    val excp         = new ExceptionBundle
    val cacheable    = Bool()
    val scSuccess    = Bool()
    val lsuOp        = UInt(LsuOp.width.W)
    val pc           = UInt(XLEN.W)
    val pdst         = UInt(PhyRegIdxWidth.W)
    val rfWen        = Bool()
    val fuType       = UInt(FuType.width.W)
  }
 
  val io = IO(new Bundle {
    val redirectInfo    = Flipped(ValidIO(new redirectInfoToModule))
 
    val enq = new Bundle {
      val valid  = Input(Bool())
      val robIdx = Input(new RobPtr(RobSize))
      val lqIdx  = Input(UInt(log2Ceil(LqSize).W))
      val pc     = Input(UInt(XLEN.W))
      val pdst   = Input(UInt(PhyRegIdxWidth.W))
      val rfWen  = Input(Bool())
      val lsuOp  = Input(UInt(LsuOp.width.W))
      val fuType = Input(UInt(FuType.width.W))
    }
 
    val addrWrite = new Bundle {
      val valid     = Input(Bool())
      val idx       = Input(UInt(log2Ceil(SqSize).W))
      val vaddr     = Input(UInt(XLEN.W))
      val paddr     = Input(UInt(XLEN.W))
      val excp      = Input(new ExceptionBundle)
      val cacheable = Input(Bool())
      val scSuccess = Input(Bool())
    }
 
    val dataWrite = new Bundle {
      val valid = Input(Bool())
      val idx   = Input(UInt(log2Ceil(SqSize).W))
      val data  = Input(UInt(XLEN.W))
    }
 
    val robCommit = Vec(CommitWidth, new Bundle {
      val valid = Input(Bool())
      val sqIdx = Input(UInt(log2Ceil(SqSize).W))
    })
 
    val dcacheReq = Decoupled(new Bundle {
      val sqIdx    = UInt(log2Ceil(SqSize).W)
      val paddr    = UInt(XLEN.W)
      val vaddr    = UInt(XLEN.W)
      val cacheable = Bool()
      val data     = UInt(XLEN.W)
      val lsuOp    = UInt(LsuOp.width.W)
    })
 
    val storeAck = Flipped(Decoupled(new Bundle {
      val sqIdx = UInt(log2Ceil(SqSize).W)
    }))
 
    val outResult = Decoupled(new ExeResult)
 
    // ★ 新增：SQ → LQ 转发信息向量
    val sqForwardInfo = Output(Vec(SqSize, new SqForwardInfoBundle))
 
    val oldestRobIdx = Output(new RobPtr(RobSize))
    val sqEmpty      = Output(Bool())
    val committedStoreEmpty = Output(Bool())
    val full                = Output(Bool())
    val empty               = Output(Bool())
    val enqPtr              = Output(UInt(log2Ceil(SqSize).W))
    val sqHasEntries = Output(UInt(log2Ceil(SqSize + 1).W))
  })
 
  // ================================================================
  //  存储体 + 指针
  // ================================================================
  val entries = RegInit(VecInit(Seq.fill(SqSize)(0.U.asTypeOf(new SqEntry))))
  diffDontTouch(entries)
 
  val enqPtr = RegInit({
    val p = Wire(new SqPtrInner); p.value := 0.U; p.flag := false.B; p
  })
  val deqPtr = RegInit({
    val p = Wire(new SqPtrInner); p.value := 0.U; p.flag := false.B; p
  })
 
  val empty = deqPtr === enqPtr
  val full  = (deqPtr.value === enqPtr.value) && (deqPtr.flag =/= enqPtr.flag)
 
  io.full   := full
  io.empty  := empty
  io.enqPtr := enqPtr.value
 
  //val count = enqPtr.distanceTo(deqPtr)
  //io.sqHasEntries := count
  // 独立的表项计数寄存器
  val sqHasEntriesReg = RegInit(0.U(log2Ceil(SqSize + 1).W))
  io.sqHasEntries := sqHasEntriesReg
 
  // SQ → LQ 转发信息连线
  for (i <- 0 until SqSize) {
    val e = entries(i)
    io.sqForwardInfo(i).paddr        := e.paddr
    io.sqForwardInfo(i).addrValid    := e.addrValid
    io.sqForwardInfo(i).dataValid    := e.dataValid
    io.sqForwardInfo(i).data         := e.data
    io.sqForwardInfo(i).robIdxFull   := e.robIdxFull
    io.sqForwardInfo(i).valid        := e.valid
    io.sqForwardInfo(i).alreadyFlush := e.alreadyFlush
    io.sqForwardInfo(i).hasException := e.excp.hasException
    io.sqForwardInfo(i).lsuOp        := e.lsuOp
    io.sqForwardInfo(i).cacheable := e.cacheable
    io.sqForwardInfo(i).committed := e.committed
  }
 
  // ── oldestRobIdx ──
  val activeCandidates = Wire(Vec(SqSize, Bool()))
  for (i <- 0 until SqSize) {
    val idx = (deqPtr.value + i.U)(log2Ceil(SqSize) - 1, 0)
    val e = entries(idx)
    activeCandidates(i) := e.valid && !e.alreadyFlush
  }
 
  val hasActiveStore = activeCandidates.reduce(_ || _)
  val activeOffset   = PriorityEncoder(activeCandidates)
  val activeIdx      = (deqPtr.value + activeOffset)(log2Ceil(SqSize) - 1, 0)
 
  val defaultRobIdx = Wire(new RobPtr(RobSize))
  defaultRobIdx.value := 0.U
  defaultRobIdx.flag  := false.B
 
  io.oldestRobIdx := Mux(hasActiveStore, entries(activeIdx).robIdxFull, defaultRobIdx)
  io.sqEmpty      := !hasActiveStore

  io.committedStoreEmpty := !VecInit(entries.map(e =>
    e.valid && !e.alreadyFlush && e.committed
  )).asUInt.orR

  // ================================================================
  //  1. 入队
  // ================================================================
  val enqFire = io.enq.valid && !full
 
  when(enqFire) {
    val idx = enqPtr.value
    entries(idx).robIdxFull   := io.enq.robIdx
    entries(idx).lqIdx        := io.enq.lqIdx
    entries(idx).valid        := true.B
    entries(idx).addrValid    := false.B
    entries(idx).dataValid    := false.B
    entries(idx).committed    := false.B
    entries(idx).alreadyFlush := false.B
    entries(idx).writtenBack  := false.B
    entries(idx).Memwritten   := false.B
    entries(idx).dcacheIssued := false.B
    entries(idx).vaddr        := 0.U
    entries(idx).paddr        := 0.U
    entries(idx).data         := 0.U
    entries(idx).excp         := 0.U.asTypeOf(new ExceptionBundle)
    entries(idx).cacheable    := false.B
    entries(idx).scSuccess    := false.B
    entries(idx).lsuOp        := io.enq.lsuOp
    entries(idx).pc           := io.enq.pc
    entries(idx).pdst         := io.enq.pdst
    entries(idx).rfWen        := io.enq.rfWen
    entries(idx).fuType       := io.enq.fuType
    enqPtr := enqPtr + 1.U
  }
 
  // ================================================================
  //  重定向
  // ================================================================
  val doRedirect = io.redirectInfo.valid && io.redirectInfo.bits.doRedirect
  val redirectRobIdx = io.redirectInfo.bits.robIdx
  when(doRedirect) {
    for (i <- 0 until SqSize) {
      val e = entries(i)
      when(e.valid && !e.committed) {
        val isNewer = e.robIdxFull.isAfter(redirectRobIdx)
        when(isNewer) {
          e.alreadyFlush := true.B
        }
      }
    }
  }
 
  // ================================================================
  //  2. STA 地址写入
  // ================================================================
  when(io.addrWrite.valid) {
    val idx = io.addrWrite.idx
    entries(idx).addrValid := true.B
    entries(idx).vaddr     := io.addrWrite.vaddr
    entries(idx).paddr     := io.addrWrite.paddr
    entries(idx).excp      := io.addrWrite.excp
    entries(idx).cacheable := io.addrWrite.cacheable
    entries(idx).scSuccess := io.addrWrite.scSuccess
  }
 
  // ================================================================
  //  3. STD 数据写入
  // ================================================================
  when(io.dataWrite.valid) {
    val idx = io.dataWrite.idx
    entries(idx).dataValid := true.B
    entries(idx).data := MuxLookup(entries(idx).lsuOp, io.dataWrite.data)(Seq(
      LsuOp.sb -> Cat(0.U(24.W), io.dataWrite.data(7, 0)),
      LsuOp.sh -> Cat(0.U(16.W), io.dataWrite.data(15, 0)),
      LsuOp.sw -> io.dataWrite.data,
      LsuOp.scw -> io.dataWrite.data,
    ))
  }
 
  // ================================================================
  //  6. 向后端写回
  // ================================================================
  val wbCandidates = Wire(Vec(SqSize, Bool()))
  for (i <- 0 until SqSize) {
    val idx = (deqPtr.value + i.U)(log2Ceil(SqSize) - 1, 0)
    val e = entries(idx)
    wbCandidates(i) := e.valid && !e.alreadyFlush && e.addrValid && e.dataValid && !e.writtenBack
  }
 
  val hasWbCandidate = wbCandidates.reduce(_ || _)
  val wbOffset       = PriorityEncoder(wbCandidates)
  val wbIdx          = (deqPtr.value + wbOffset)(log2Ceil(SqSize) - 1, 0)
  val wbEntry        = entries(wbIdx)
 
  val wbIsSc = wbEntry.lsuOp === LsuOp.scw

  io.outResult.valid                := hasWbCandidate
  //io.outResult.bits.data            := Mux(wbIsSc, wbEntry.scSuccess.asUInt, 0.U)
    // RISC-V：SC.W 成功写 rd=0，失败写 rd=1（与龙架构相反）
  io.outResult.bits.data            := Mux(wbIsSc, (!wbEntry.scSuccess).asUInt, 0.U)
  
  io.outResult.bits.memValid        := true.B
  io.outResult.bits.memRead         := false.B
  io.outResult.bits.memWrite        := true.B
  io.outResult.bits.memVaddr        := wbEntry.vaddr
  io.outResult.bits.memPaddr        := wbEntry.paddr
 
  val storeByteOff = wbEntry.paddr(1, 0)
  io.outResult.bits.memStoreData    := Mux(wbEntry.lsuOp === LsuOp.sb,
                                           wbEntry.data << (storeByteOff * 8.U),
                                           wbEntry.data << (wbEntry.paddr(1) * 16.U))
  io.outResult.bits.storeValid := !wbEntry.excp.hasException &&
    (!wbIsSc || wbEntry.scSuccess)
 
  io.outResult.bits.redirect.valid  := DontCare
  io.outResult.bits.redirect.bits.valid  := DontCare
  io.outResult.bits.redirect.bits.robIdx := wbEntry.robIdxFull
  io.outResult.bits.csrWen     := DontCare
  io.outResult.bits.csrWaddr   := DontCare
  io.outResult.bits.csrWdata   := DontCare
  io.outResult.bits.csrTimer   := DontCare
  io.outResult.bits.tlbFillIdx := 0.U
 
  val wbUop = io.outResult.bits.uop
  wbUop.pc         := wbEntry.pc
  wbUop.inst       := 0.U
  wbUop.excp       := wbEntry.excp
  wbUop.imm        := 0.U
  wbUop.csrAddress := 0.U
  wbUop.ldst       := 0.U
  wbUop.lrs1       := 0.U
  wbUop.lrs2       := 0.U
  wbUop.pdst       := wbEntry.pdst
  wbUop.prs1       := 0.U
  wbUop.prs2       := 0.U
  wbUop.oldPdst    := 0.U
  wbUop.rs1Valid   := false.B
  wbUop.rs2Valid   := false.B
  wbUop.rdValid    := wbEntry.rfWen
  wbUop.robIdx     := wbEntry.robIdxFull
  wbUop.robIdxFull := wbEntry.robIdxFull
  wbUop.issueQueue := 0.U
  wbUop.prs1Busy   := false.B
  wbUop.prs2Busy   := false.B
  wbUop.isSta      := false.B
  wbUop.isStd      := false.B
 
  val wbLqIdx = Wire(new SqPtr(SqSize))
  wbLqIdx.value := wbEntry.lqIdx
  wbLqIdx.flag  := false.B
  wbUop.lqIdx   := wbLqIdx
 
  val wbSqIdx = Wire(new LqPtr(LqSize))
  wbSqIdx.value := wbIdx
  wbSqIdx.flag  := false.B
  wbUop.sqIdx   := wbSqIdx
 
  wbUop.ctrl.fuType   := wbEntry.fuType
  wbUop.ctrl.lsuOp    := wbEntry.lsuOp
  wbUop.ctrl.barOp    := BarOp.none
  wbUop.ctrl.rfWen    := wbEntry.rfWen
  wbUop.ctrl.memRead  := false.B
  wbUop.ctrl.memWrite := true.B
  wbUop.ctrl.aluOp    := 0.U
  wbUop.ctrl.bruOp    := 0.U
  wbUop.ctrl.csrOp    := 0.U
  wbUop.ctrl.tlbOp    := TlbOp.none
  wbUop.ctrl.mulOp    := 0.U
  wbUop.ctrl.divOp    := 0.U
  wbUop.ctrl.src1Type := 0.U
  wbUop.ctrl.src2Type := 0.U
  wbUop.ctrl.immType  := 0.U
  wbUop.ctrl.csrWen   := false.B
  wbUop.ctrl.isBranch := false.B
  wbUop.ctrl.isJump   := false.B
  wbUop.ctrl.privLevel   := 0.U
  wbUop.ctrl.isIdle   := false.B
  wbUop.ctrl.waitForward := false.B
  wbUop.ctrl.blockBackward := false.B
  wbUop.ctrl.flushOnCommit := false.B
  wbUop.cacheOp.valid := false.B
  wbUop.cacheOp.code := 0.U
  wbUop.cacheOp.cacheType := 0.U
  wbUop.cacheOp.operation := 0.U
 
  wbUop.pdInfo  := DontCare
  wbUop.bpuInfo := DontCare
  wbUop.snptId  := DontCare
 
  when(io.outResult.fire) {
    entries(wbIdx).writtenBack := true.B
  }
 
  // ================================================================
  //  7. ROB 提交
  // ================================================================
  for (i <- 0 until CommitWidth) {
    when(io.robCommit(i).valid) {
      val idx = io.robCommit(i).sqIdx
      entries(idx).committed := true.B
      when(entries(idx).lsuOp === LsuOp.scw && !entries(idx).scSuccess) {
        entries(idx).Memwritten := true.B
      }
    }
  }
 
  // ================================================================
  //  8. 向 DCache 发出 Store 写请求
  // ================================================================
  val dcacheCandidates = Wire(Vec(SqSize, Bool()))
  for (i <- 0 until SqSize) {
    val idx = (deqPtr.value + i.U)(log2Ceil(SqSize) - 1, 0)
    val e = entries(idx)
    val scFailed = e.lsuOp === LsuOp.scw && !e.scSuccess
    dcacheCandidates(i) := e.valid && e.committed && !scFailed &&
      !e.excp.hasException && !e.dcacheIssued && !e.alreadyFlush
  }
 
  val hasDcacheCandidate = dcacheCandidates.reduce(_ || _)
  val dcacheOffset       = PriorityEncoder(dcacheCandidates)
  val dcacheIdx          = (deqPtr.value + dcacheOffset)(log2Ceil(SqSize) - 1, 0)
  val dcacheEntry        = entries(dcacheIdx)
 
  io.dcacheReq.valid      := hasDcacheCandidate
  io.dcacheReq.bits.paddr := dcacheEntry.paddr
  io.dcacheReq.bits.vaddr := dcacheEntry.vaddr
  io.dcacheReq.bits.data  := dcacheEntry.data
  io.dcacheReq.bits.cacheable := dcacheEntry.cacheable
  io.dcacheReq.bits.sqIdx := dcacheIdx
  io.dcacheReq.bits.lsuOp := Mux(dcacheEntry.lsuOp === LsuOp.scw,
    LsuOp.sw, dcacheEntry.lsuOp)
 
  when(io.dcacheReq.fire) {
    entries(dcacheIdx).dcacheIssued := true.B
  }
 
  io.storeAck.ready := true.B
  when(io.storeAck.valid) {
    val idx = io.storeAck.bits.sqIdx
    entries(idx).Memwritten := true.B
  }
 
  // ================================================================
  //  9. 出队
  // ================================================================
  val canDeqNormal = entries(deqPtr.value).valid && (entries(deqPtr.value).Memwritten || entries(deqPtr.value).alreadyFlush)
  val canDeqExcp   = entries(deqPtr.value).valid && entries(deqPtr.value).writtenBack &&
                     entries(deqPtr.value).excp.hasException
  val canDeq = canDeqNormal || canDeqExcp
 
  when(canDeq) {
    entries(deqPtr.value).valid := false.B
    deqPtr := deqPtr + 1.U
  }
  // 维护表项计数寄存器
  when(enqFire && !canDeq) {
    sqHasEntriesReg := sqHasEntriesReg + 1.U
  }.elsewhen(!enqFire && canDeq) {
    sqHasEntriesReg := sqHasEntriesReg - 1.U
  }

}
