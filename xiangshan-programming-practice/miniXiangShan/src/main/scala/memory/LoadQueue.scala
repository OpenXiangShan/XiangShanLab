package minixiangshan.mem
 
import chisel3._
import chisel3.util._
import minixiangshan.config._
import minixiangshan.backend.dispatch._
import minixiangshan.backend.decode._
import minixiangshan.backend.rename._
import minixiangshan.backend.execute._
import minixiangshan.util.CircularQueuePtr
import os.truncate
 
class LoadQueue(implicit p: Parameters) extends NSModule {
 
  class LqPtrInner extends CircularQueuePtr[LqPtrInner](LqSize)
 
  class LqEntry(implicit p: Parameters) extends NSBundle {
    val robIdxFull   = new RobPtr(RobSize)
    val sqIdx        = UInt(log2Ceil(SqSize).W)
    val valid        = Bool()
    val addrValid    = Bool()
    val alreadyFlush = Bool()
    val readyToIssue = Bool() 
    val issued       = Bool()
    val dataValid    = Bool()
    val writtenBack  = Bool()
    val vaddr        = UInt(XLEN.W)
    val paddr        = UInt(XLEN.W)
    val cacheable    = Bool()
    val data         = UInt(XLEN.W)
    val excp         = new ExceptionBundle
    val lsuOp        = UInt(LsuOp.width.W)
    val pc           = UInt(XLEN.W)
    val pdst         = UInt(PhyRegIdxWidth.W)
    val rfWen        = Bool()
    val fuType       = UInt(FuType.width.W)
    val cacheOp        = new CacheOpDecode
  }
 
  val io = IO(new Bundle {
 
    val redirectInfo = Flipped(ValidIO(new redirectInfoToModule))
 
    val enq = new Bundle {
      val valid  = Input(Bool())
      val robIdx = Input(new RobPtr(RobSize))
      val sqIdx  = Input(UInt(log2Ceil(SqSize).W))
      val pc     = Input(UInt(XLEN.W))
      val pdst   = Input(UInt(PhyRegIdxWidth.W))
      val rfWen  = Input(Bool())
      val lsuOp  = Input(UInt(LsuOp.width.W))
      val fuType = Input(UInt(FuType.width.W))
      val cacheOp  = Input(new CacheOpDecode)
    }
 
    val addrWrite = new Bundle {
      val valid     = Input(Bool())
      val idx       = Input(UInt(log2Ceil(LqSize).W))
      val vaddr     = Input(UInt(XLEN.W))
      val paddr     = Input(UInt(XLEN.W))
      val cacheable = Input(Bool())
      val excp      = Input(new ExceptionBundle)
    }
 
    val sqForwardInfo  = Input(Vec(SqSize, new SqForwardInfoBundle))
    val sqOldestRobIdx = Input(new RobPtr(RobSize))
    val sqEmpty        = Input(Bool())
 
    val dcacheReq = Decoupled(new Bundle {
      val lqIdx     = UInt(log2Ceil(LqSize).W)
      val robIdx    = new RobPtr(RobSize)
      val paddr     = UInt(XLEN.W)
      val vaddr     = UInt(XLEN.W)
      val cacheable = Bool()
      val lsuOp     = UInt(LsuOp.width.W)
    })
 
    val dcacheResp = Flipped(Decoupled(new Bundle {
      val lqIdx = UInt(log2Ceil(LqSize).W)
      val data  = UInt(XLEN.W)
    }))
 
    val outResult = Decoupled(new ExeResult)
 
    val full          = Output(Bool())
    val empty         = Output(Bool())
    val enqPtr        = Output(UInt(log2Ceil(LqSize).W))
    val lqHasEntries  = Output(UInt(log2Ceil(LqSize + 1).W))
    val committedStoreEmpty = Input(Bool())
    val robHead         = Input(Valid(new RobPtr(RobSize)))

  })
 if(LoadQVersion == 0){
    // ================================================================
    //  存储体 + 指针
    // ================================================================
    val entries = RegInit(VecInit(Seq.fill(LqSize)(0.U.asTypeOf(new LqEntry))))
    diffDontTouch(entries)
   
    val enqPtr = RegInit({
      val p = Wire(new LqPtrInner); p.value := 0.U; p.flag := false.B; p
    })
    val deqPtr = RegInit({
      val p = Wire(new LqPtrInner); p.value := 0.U; p.flag := false.B; p
    })
   
    val empty = deqPtr === enqPtr
    val full  = (deqPtr.value === enqPtr.value) && (deqPtr.flag =/= enqPtr.flag)
   
    io.full   := full
    io.empty  := empty
    io.enqPtr := enqPtr.value
    //val count = enqPtr.distanceTo(deqPtr)
    //io.lqHasEntries := count
    // 新增：独立的表项计数寄存器
    val lqHasEntriesReg = RegInit(0.U(log2Ceil(LqSize + 1).W))
    io.lqHasEntries := lqHasEntriesReg
   
    // ================================================================
    //  1. 入队
    // ================================================================
    val enqFire = io.enq.valid && !full
   
    when(enqFire) {
      val idx = enqPtr.value
      entries(idx).robIdxFull   := io.enq.robIdx
      entries(idx).sqIdx        := io.enq.sqIdx
      entries(idx).valid        := true.B
      entries(idx).addrValid    := false.B
      entries(idx).issued       := false.B
      entries(idx).dataValid    := false.B
      entries(idx).alreadyFlush := false.B
      entries(idx).writtenBack  := false.B
      entries(idx).vaddr        := 0.U
      entries(idx).paddr        := 0.U
      entries(idx).cacheable    := false.B
      entries(idx).data         := 0.U
      entries(idx).excp         := 0.U.asTypeOf(new ExceptionBundle)
      entries(idx).lsuOp        := io.enq.lsuOp
      entries(idx).pc           := io.enq.pc
      entries(idx).pdst         := io.enq.pdst
      entries(idx).rfWen        := io.enq.rfWen
      entries(idx).fuType       := io.enq.fuType
      entries(idx).cacheOp        := io.enq.cacheOp
      enqPtr := enqPtr + 1.U
    }
   
    // ================================================================
    //  重定向
    // ================================================================
    val doRedirect      = io.redirectInfo.valid && io.redirectInfo.bits.doRedirect
    val redirectRobIdx = io.redirectInfo.bits.robIdx
    val isNewer = Wire(Vec(LqSize, Bool()))
   
    for (i <- 0 until LqSize) {
      val e = entries(i)
      isNewer(i) := e.robIdxFull.isAfter(redirectRobIdx) && doRedirect && e.valid
      when(isNewer(i)) {
        e.alreadyFlush := true.B
      }
    }
   
    // ================================================================
    //  2. 地址写入
    // ================================================================
    when(io.addrWrite.valid) {
      val idx = io.addrWrite.idx
      entries(idx).addrValid := true.B
      entries(idx).vaddr     := io.addrWrite.vaddr
      entries(idx).paddr     := io.addrWrite.paddr
      entries(idx).excp      := io.addrWrite.excp
      entries(idx).cacheable := io.addrWrite.cacheable
      when(entries(idx).cacheOp.valid) {
        entries(idx).issued := true.B
        entries(idx).dataValid := true.B
        entries(idx).data := 0.U
      }
    }
   
    // ================================================================
    //  3. SQ 分析 + 转发仲裁（★ 简化版 ★）
    //
    //  简化要点：
    //    - 多个地址冲突 → 直接等，不找最年轻的
    //    - 非 stw 冲突 → 直接等，不做囊括分析
    //    - 仅 "单冲突 + stw + dataValid" 做转发
    //    - 转发数据仅对 stw 做按 load lsuOp 提取
    //
    //  判定逻辑树：
    //    1) uncache → 仅判断有无更老 store
    //    2) cacheable → 逐层判定：
    //       2.1  无更老 → 可发射
    //       2.2  有更老 → 地址就位? → uncache? → 地址冲突?
    //            → 多冲突等 / 单冲突非stw等 / 单冲突stw看数据
    // ================================================================
   
    val lqCanIssue    = Wire(Vec(LqSize, Bool()))
    val lqDoForward   = Wire(Vec(LqSize, Bool()))
    val lqForwardData = Wire(Vec(LqSize, UInt(XLEN.W)))
   
    for (lqI <- 0 until LqSize) {
      val e = entries(lqI)
      val isUncache     = !e.cacheable
      val loadPaddrWord = e.paddr(31, 2)
   
      lqCanIssue(lqI)    := false.B
      lqDoForward(lqI)   := false.B
      lqForwardData(lqI) := 0.U
   
      val basicReady = e.valid && e.addrValid && !e.issued &&
                       !e.excp.hasException && !e.alreadyFlush
   
      when(basicReady) {
   
        // ── 对每个 SQ 表项计算关系 ──
        val sqOlderActive       = Wire(Vec(SqSize, Bool()))
        val sqOlderAddrUnknown  = Wire(Vec(SqSize, Bool()))
        val sqOlderUncache      = Wire(Vec(SqSize, Bool()))
        val sqOlderAddrConflict = Wire(Vec(SqSize, Bool()))
   
        for (sqI <- 0 until SqSize) {
          val sq = io.sqForwardInfo(sqI)
          val isOlder  = e.robIdxFull.isAfter(sq.robIdxFull) || sq.committed
          val isActive = sq.valid && !sq.alreadyFlush && !sq.hasException
   
          sqOlderActive(sqI)       := isOlder && isActive
          sqOlderAddrUnknown(sqI)  := sqOlderActive(sqI) && !sq.addrValid
          sqOlderUncache(sqI)      := sqOlderActive(sqI) && sq.addrValid && !sq.cacheable
          sqOlderAddrConflict(sqI) := sqOlderActive(sqI) && sq.addrValid && sq.cacheable &&
                                      (sq.paddr(31, 2) === loadPaddrWord)
        }
   
        val hasOlderActive      = sqOlderActive.reduce(_ || _)
        val hasOlderAddrUnknown = sqOlderAddrUnknown.reduce(_ || _)
        val hasOlderUncache     = sqOlderUncache.reduce(_ || _)
   
        // ★ 冲突计数：PopCount 替代 O(n²) isYoungest
        val conflictCount       = PopCount(sqOlderAddrConflict)
        val hasNoConflict       = conflictCount === 0.U
        val hasSingleConflict   = conflictCount === 1.U
        val hasMultipleConflict = conflictCount > 1.U
   
        // ═══════════════════════════════════════
        //  情况 1：Uncache load
        // ═══════════════════════════════════════
        when(isUncache) {
          lqCanIssue(lqI)  := !hasOlderActive && io.robHead.bits === e.robIdxFull //&& (lqI.U === deqPtr.value) && io.committedStoreEmpty && io.robHead.valid 
          lqDoForward(lqI) := false.B
        }
   
        // ═══════════════════════════════════════
        //  情况 2：Cacheable load
        // ═══════════════════════════════════════
        .otherwise {
   
          // 2.1 无更老 store → 可发射
          when(!hasOlderActive) {
            lqCanIssue(lqI)  := true.B
            lqDoForward(lqI) := false.B
          }
          .otherwise {
            // 2.2.1 有地址未就位 → 等
            when(hasOlderAddrUnknown) {
              lqCanIssue(lqI)  := false.B
              lqDoForward(lqI) := false.B
            }
            .otherwise {
   
              // 2.2.2.1 存在 uncache 更老 store → 等，不转发
              when(hasOlderUncache) {
                lqCanIssue(lqI)  := false.B
                lqDoForward(lqI) := false.B
              }
              .otherwise {
   
                // 2.2.2.2.1 无地址冲突 → 可发射
                when(hasNoConflict) {
                  lqCanIssue(lqI)  := true.B
                  lqDoForward(lqI) := false.B
                }
                .otherwise {
   
                  // 2.2.2.2.2 有地址冲突
                  // 多冲突 → 等
                  // 单冲突 + 非stw → 等
                  // 单冲突 + stw + dataValid → 转发
                  // 单冲突 + stw + !dataValid → 等
   
                  lqCanIssue(lqI) := false.B  // 有冲突一律不作为候选
   
                  when(hasSingleConflict && !e.cacheOp.valid) {
                    val singleConflictIdx = PriorityEncoder(sqOlderAddrConflict)
                    val singleSq = io.sqForwardInfo(singleConflictIdx)
   
                    when(singleSq.lsuOp === LsuOp.sw) {
                      // 2.2.2.2.2.2 是 stw
                      when(singleSq.dataValid) {
                        // 数据就位 → 转发
                        lqDoForward(lqI) := true.B
   
                        // ★ 按 load 的 paddr + lsuOp 提取（与 DCache extractUncacheLoadData 一致）
                        val rawWord  = singleSq.data
                        val byteOff  = e.paddr(1, 0)
                        val byteData = MuxLookup(byteOff, rawWord(7, 0), Seq(
                          0.U -> rawWord(7, 0),
                          1.U -> rawWord(15, 8),
                          2.U -> rawWord(23, 16),
                          3.U -> rawWord(31, 24)
                        ))
                        val halfData = Mux(byteOff(1), rawWord(31, 16), rawWord(15, 0))
   
                        lqForwardData(lqI) := MuxLookup(e.lsuOp, rawWord, Seq(
                          LsuOp.lw  -> rawWord,
                          LsuOp.lh  -> Cat(Fill(16, halfData(15)), halfData),
                          LsuOp.lhu -> Cat(0.U(16.W), halfData),
                          LsuOp.lb  -> Cat(Fill(24, byteData(7)), byteData),
                          LsuOp.lbu -> Cat(0.U(24.W), byteData)
                        ))
                      }
                      // dataValid = false → 等，lqDoForward 保持 false
                    }
                    // 非 stw → 等，lqDoForward 保持 false
                  }
                  // 多冲突 → 等，lqDoForward 保持 false
                }
              }
            }
            
  
  
          }
        }
      }
    }
   
    // ── 3b. 基于分析结果，计算发射候选 ──
    val issueCandidates = Wire(Vec(LqSize, Bool()))
    for (i <- 0 until LqSize) {
      val idx = (deqPtr.value + i.U)(log2Ceil(LqSize) - 1, 0)
      val e = entries(idx)
      issueCandidates(i) := e.valid && e.addrValid && !e.issued &&
                            !e.excp.hasException && !e.alreadyFlush &&
                            !e.cacheOp.valid &&
                            lqCanIssue(idx)
    }
   
    val hasIssueCandidate = issueCandidates.reduce(_ || _)
    val issueOffset       = PriorityEncoder(issueCandidates)
    val issueIdx          = (deqPtr.value + issueOffset)(log2Ceil(LqSize) - 1, 0)
    val issueEntry        = entries(issueIdx)
   
    // ── 3c. DCache 请求 ──
    io.dcacheReq.valid           := hasIssueCandidate && !isNewer(issueIdx) 
    io.dcacheReq.bits.lqIdx      := issueIdx
    io.dcacheReq.bits.paddr      := issueEntry.paddr
    io.dcacheReq.bits.vaddr      := issueEntry.vaddr
    io.dcacheReq.bits.cacheable  := issueEntry.cacheable
    io.dcacheReq.bits.robIdx     := issueEntry.robIdxFull
    io.dcacheReq.bits.lsuOp := Mux(issueEntry.lsuOp === LsuOp.lrw,
      LsuOp.lw, issueEntry.lsuOp)
   
    when(io.dcacheReq.fire) {
      entries(issueIdx).issued := true.B
    }
   
    // ── 3d. 转发写入 ──
    for (lqI <- 0 until LqSize) {
      val e = entries(lqI)
      val canForward = e.valid && e.addrValid && !e.issued &&
                       !e.excp.hasException && !e.alreadyFlush &&
                       lqDoForward(lqI) && !isNewer(lqI)
      when(canForward) {
        e.issued    := true.B
        e.dataValid := true.B
        e.data      := lqForwardData(lqI)
      }
    }
   
    // ================================================================
    //  4. 接收 DCache 响应
    // ================================================================
    io.dcacheResp.ready := true.B
    when(io.dcacheResp.fire) {
      val idx = io.dcacheResp.bits.lqIdx
      entries(idx).dataValid := true.B
      entries(idx).data      := io.dcacheResp.bits.data
    }
   
    // ================================================================
    //  5. 向后端写回
    // ================================================================
    val wbCandidates = Wire(Vec(LqSize, Bool()))
    for (i <- 0 until LqSize) {
      val idx = (deqPtr.value + i.U)(log2Ceil(LqSize) - 1, 0)
      val e = entries(idx)
      wbCandidates(i) := e.valid && !e.alreadyFlush &&  (e.dataValid || e.excp.hasException) && !e.writtenBack
    }
   
    val hasWbCandidate = wbCandidates.reduce(_ || _)
    val wbOffset       = PriorityEncoder(wbCandidates)
    val wbIdx          = (deqPtr.value + wbOffset)(log2Ceil(LqSize) - 1, 0)
    val wbEntry        = entries(wbIdx)
    val wbIsCacheOp      = wbEntry.cacheOp.valid
   
    io.outResult.valid                := hasWbCandidate
    io.outResult.bits.data            := wbEntry.data
    io.outResult.bits.memValid        := !wbIsCacheOp
    io.outResult.bits.memRead         := !wbIsCacheOp
    io.outResult.bits.memWrite        := false.B
    io.outResult.bits.memVaddr        := wbEntry.vaddr
    io.outResult.bits.memPaddr        := wbEntry.paddr
    io.outResult.bits.memStoreData    := 0.U
    io.outResult.bits.storeValid := false.B
    io.outResult.bits.redirect.valid  := DontCare
    io.outResult.bits.redirect.bits.valid  := DontCare
    io.outResult.bits.redirect.bits.robIdx := DontCare
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
   
    val wbLqIdx = Wire(new LqPtr(LqSize))
    wbLqIdx.value := wbIdx
    wbLqIdx.flag  := false.B
    wbUop.lqIdx   := wbLqIdx
   
    val wbSqIdx = Wire(new SqPtr(SqSize))
    wbSqIdx.value := wbEntry.sqIdx
    wbSqIdx.flag  := false.B
    wbUop.sqIdx   := wbSqIdx
   
    wbUop.ctrl.fuType   := wbEntry.fuType
    wbUop.ctrl.lsuOp    := wbEntry.lsuOp
    wbUop.ctrl.barOp    := BarOp.none
    wbUop.ctrl.rfWen    := wbEntry.rfWen
    wbUop.ctrl.memRead  := !wbIsCacheOp
    wbUop.ctrl.memWrite := false.B
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
    wbUop.cacheOp := wbEntry.cacheOp
   
    wbUop.pdInfo  := DontCare
    wbUop.bpuInfo := DontCare
    wbUop.snptId  := DontCare
   
    when(io.outResult.fire) {
      entries(wbIdx).writtenBack := true.B
    }
   
    // ================================================================
    //  6. 出队
    // ================================================================
    val canDeq = entries(deqPtr.value).valid &&
                 (entries(deqPtr.value).writtenBack || entries(deqPtr.value).alreadyFlush)
    when(canDeq) {
      entries(deqPtr.value).valid := false.B
      deqPtr := deqPtr + 1.U
    }

    when(enqFire && !canDeq) {
      lqHasEntriesReg := lqHasEntriesReg + 1.U
    }.elsewhen(!enqFire && canDeq) {
      lqHasEntriesReg := lqHasEntriesReg - 1.U
    }

  }else if(LoadQVersion == 1){
    // ================================================================
    //  存储体 + 指针
    // ================================================================
    val entries = RegInit(VecInit(Seq.fill(LqSize)(0.U.asTypeOf(new LqEntry))))
    diffDontTouch(entries)
   
    val enqPtr = RegInit({
      val p = Wire(new LqPtrInner); p.value := 0.U; p.flag := false.B; p
    })
    val deqPtr = RegInit({
      val p = Wire(new LqPtrInner); p.value := 0.U; p.flag := false.B; p
    })
   
    val empty = deqPtr === enqPtr
    val full  = (deqPtr.value === enqPtr.value) && (deqPtr.flag =/= enqPtr.flag)
   
    io.full   := full
    io.empty  := empty
    io.enqPtr := enqPtr.value
    //val count = enqPtr.distanceTo(deqPtr)
    //io.lqHasEntries := count
    val lqHasEntriesReg = RegInit(0.U(log2Ceil(LqSize + 1).W))
    io.lqHasEntries := lqHasEntriesReg
   
    // ================================================================
    //  1. 入队
    // ================================================================
    val enqFire = io.enq.valid && !full
   
    when(enqFire) {
      val idx = enqPtr.value
      entries(idx).robIdxFull   := io.enq.robIdx
      entries(idx).sqIdx        := io.enq.sqIdx
      entries(idx).valid        := true.B
      entries(idx).addrValid    := false.B
      entries(idx).issued       := false.B
      entries(idx).dataValid    := false.B
      entries(idx).alreadyFlush := false.B
      entries(idx).writtenBack  := false.B
      entries(idx).vaddr        := 0.U
      entries(idx).paddr        := 0.U
      entries(idx).cacheable    := false.B
      entries(idx).data         := 0.U
      entries(idx).excp         := 0.U.asTypeOf(new ExceptionBundle)
      entries(idx).lsuOp        := io.enq.lsuOp
      entries(idx).pc           := io.enq.pc
      entries(idx).pdst         := io.enq.pdst
      entries(idx).rfWen        := io.enq.rfWen
      entries(idx).fuType       := io.enq.fuType
      entries(idx).cacheOp        := io.enq.cacheOp
      enqPtr := enqPtr + 1.U
    }
   
    // ================================================================
    //  重定向
    // ================================================================
    val doRedirect      = io.redirectInfo.valid && io.redirectInfo.bits.doRedirect
    val redirectRobIdx = io.redirectInfo.bits.robIdx
    val isNewer = Wire(Vec(LqSize, Bool()))
   
    for (i <- 0 until LqSize) {
      val e = entries(i)
      isNewer(i) := e.robIdxFull.isAfter(redirectRobIdx) && doRedirect && e.valid
      when(isNewer(i)) {
        e.alreadyFlush := true.B
      }
    }
   
    // ================================================================
    //  2. 地址写入
    // ================================================================
    when(io.addrWrite.valid) {
      val idx = io.addrWrite.idx
      entries(idx).addrValid := true.B
      entries(idx).vaddr     := io.addrWrite.vaddr
      entries(idx).paddr     := io.addrWrite.paddr
      entries(idx).excp      := io.addrWrite.excp
      entries(idx).cacheable := io.addrWrite.cacheable
      when(entries(idx).cacheOp.valid) {
        entries(idx).issued := true.B
        entries(idx).dataValid := true.B
        entries(idx).data := 0.U
      }
    }
   
    // ================================================================
    //  3. SQ 分析 + 转发仲裁（★ 简化版 ★）
    // ================================================================
   
    val lqCanIssue    = Wire(Vec(LqSize, Bool()))
    val lqDoForward   = Wire(Vec(LqSize, Bool()))
    val lqForwardData = Wire(Vec(LqSize, UInt(XLEN.W)))
   
    for (lqI <- 0 until LqSize) {
      val e = entries(lqI)
      val isUncache     = !e.cacheable
      val loadPaddrWord = e.paddr(31, 2)
   
      lqCanIssue(lqI)    := false.B
      lqDoForward(lqI)   := false.B
      lqForwardData(lqI) := 0.U
   
      val basicReady = e.valid && e.addrValid && !e.issued &&
                       !e.excp.hasException && !e.alreadyFlush
   
      when(basicReady) {
        val sqOlderActive       = Wire(Vec(SqSize, Bool()))
        val sqOlderAddrUnknown  = Wire(Vec(SqSize, Bool()))
        val sqOlderUncache      = Wire(Vec(SqSize, Bool()))
        val sqOlderAddrConflict = Wire(Vec(SqSize, Bool()))
   
        for (sqI <- 0 until SqSize) {
          val sq = io.sqForwardInfo(sqI)
          val isOlder  = e.robIdxFull.isAfter(sq.robIdxFull) || sq.committed
          val isActive = sq.valid && !sq.alreadyFlush && !sq.hasException
   
          sqOlderActive(sqI)       := isOlder && isActive
          sqOlderAddrUnknown(sqI)  := sqOlderActive(sqI) && !sq.addrValid
          sqOlderUncache(sqI)      := sqOlderActive(sqI) && sq.addrValid && !sq.cacheable
          sqOlderAddrConflict(sqI) := sqOlderActive(sqI) && sq.addrValid && sq.cacheable &&
                                      (sq.paddr(31, 2) === loadPaddrWord)
        }
   
        val hasOlderActive      = sqOlderActive.reduce(_ || _)
        val hasOlderAddrUnknown = sqOlderAddrUnknown.reduce(_ || _)
        val hasOlderUncache     = sqOlderUncache.reduce(_ || _)
   
        val conflictCount       = PopCount(sqOlderAddrConflict)
        val hasNoConflict       = conflictCount === 0.U
        val hasSingleConflict   = conflictCount === 1.U
        val hasMultipleConflict = conflictCount > 1.U
   
        // 情况 1：Uncache load
        when(isUncache) {
          lqCanIssue(lqI)  := !hasOlderActive && io.robHead.bits === e.robIdxFull
          lqDoForward(lqI) := false.B
        }
        // 情况 2：Cacheable load
        .otherwise {
          when(!hasOlderActive) {
            lqCanIssue(lqI)  := true.B
            lqDoForward(lqI) := false.B
          }
          .otherwise {
            when(hasOlderAddrUnknown) {
              lqCanIssue(lqI)  := false.B
              lqDoForward(lqI) := false.B
            }
            .otherwise {
              when(hasOlderUncache) {
                lqCanIssue(lqI)  := false.B
                lqDoForward(lqI) := false.B
              }
              .otherwise {
                when(hasNoConflict) {
                  lqCanIssue(lqI)  := true.B
                  lqDoForward(lqI) := false.B
                }
                .otherwise {
                  lqCanIssue(lqI) := false.B 
   
                  when(hasSingleConflict && !e.cacheOp.valid) {
                    val singleConflictIdx = PriorityEncoder(sqOlderAddrConflict)
                    val singleSq = io.sqForwardInfo(singleConflictIdx)
   
                    when(singleSq.lsuOp === LsuOp.sw) {
                      when(singleSq.dataValid) {
                        lqDoForward(lqI) := true.B
   
                        val rawWord  = singleSq.data
                        val byteOff  = e.paddr(1, 0)
                        val byteData = MuxLookup(byteOff, rawWord(7, 0), Seq(
                          0.U -> rawWord(7, 0),
                          1.U -> rawWord(15, 8),
                          2.U -> rawWord(23, 16),
                          3.U -> rawWord(31, 24)
                        ))
                        val halfData = Mux(byteOff(1), rawWord(31, 16), rawWord(15, 0))
   
                        lqForwardData(lqI) := MuxLookup(e.lsuOp, rawWord, Seq(
                          LsuOp.lw  -> rawWord,
                          LsuOp.lh  -> Cat(Fill(16, halfData(15)), halfData),
                          LsuOp.lhu -> Cat(0.U(16.W), halfData),
                          LsuOp.lb  -> Cat(Fill(24, byteData(7)), byteData),
                          LsuOp.lbu -> Cat(0.U(24.W), byteData)
                        ))
                      }
                    }
                  }
                }
              }
            }
          }
        }
      }
    }
   
    val issueCandidates = Wire(Vec(LqSize, Bool()))
    for (i <- 0 until LqSize) {
      val idx = (deqPtr.value + i.U)(log2Ceil(LqSize) - 1, 0)
      val e = entries(idx)
      issueCandidates(i) := e.valid && e.addrValid && !e.issued &&
                            !e.excp.hasException && !e.alreadyFlush &&
                            !e.cacheOp.valid &&
                            lqCanIssue(idx)
    }
   
    val hasIssueCandidate = issueCandidates.reduce(_ || _)
    val issueOffset       = PriorityEncoder(issueCandidates)
    val issueIdx          = (deqPtr.value + issueOffset)(log2Ceil(LqSize) - 1, 0)
    val issueEntry        = entries(issueIdx)
   
    io.dcacheReq.valid           := hasIssueCandidate && !isNewer(issueIdx) 
    io.dcacheReq.bits.lqIdx      := issueIdx
    io.dcacheReq.bits.paddr      := issueEntry.paddr
    io.dcacheReq.bits.vaddr      := issueEntry.vaddr
    io.dcacheReq.bits.cacheable  := issueEntry.cacheable
    io.dcacheReq.bits.robIdx     := issueEntry.robIdxFull
    io.dcacheReq.bits.lsuOp := Mux(issueEntry.lsuOp === LsuOp.lrw,
      LsuOp.lw, issueEntry.lsuOp)
   
    when(io.dcacheReq.fire) {
      entries(issueIdx).issued := true.B
    }
   
    for (lqI <- 0 until LqSize) {
      val e = entries(lqI)
      val canForward = e.valid && e.addrValid && !e.issued &&
                       !e.excp.hasException && !e.alreadyFlush &&
                       lqDoForward(lqI) && !isNewer(lqI)
      when(canForward) {
        e.issued    := true.B
        e.dataValid := true.B
        e.data      := lqForwardData(lqI)
      }
    }
   
    // ================================================================
    //  4. 接收 DCache 响应 & 5. 向后端写回 (优化旁路逻辑)
    // ================================================================
    io.dcacheResp.ready := true.B
    
    // Dcache 响应的基础状态记录
    when(io.dcacheResp.fire) {
      val idx = io.dcacheResp.bits.lqIdx
      entries(idx).dataValid := true.B
      entries(idx).data      := io.dcacheResp.bits.data
    }
   
    // 本地 LQ 产生的回写候选者 (如异常表项、已从 SQ 转发拿到数据的表项等)
    val wbCandidates = Wire(Vec(LqSize, Bool()))
    for (i <- 0 until LqSize) {
      val idx = (deqPtr.value + i.U)(log2Ceil(LqSize) - 1, 0)
      val e = entries(idx)
      // 竞争候选者：有效，未被冲刷，数据有效或有异常，且尚未写回
      wbCandidates(i) := e.valid && !e.alreadyFlush && (e.dataValid || e.excp.hasException) && !e.writtenBack
    }
   
    val hasLocalWbCandidate = wbCandidates.reduce(_ || _)
    val wbOffset            = PriorityEncoder(wbCandidates)
    val localWbIdx          = (deqPtr.value + wbOffset)(log2Ceil(LqSize) - 1, 0)
    
    // -- 旁路仲裁核心机制 (Bypass Arbitration) --
    // 如果 dcache 传回数据，且该对应项没有被 flush，则赋予最高优先级旁路直接回写
    val dcacheRespValid = io.dcacheResp.valid
    val dcacheRespIdx   = io.dcacheResp.bits.lqIdx
    val dcacheRespData  = io.dcacheResp.bits.data
    val dcacheRespEntry = entries(dcacheRespIdx)
    
    val doBypass = dcacheRespValid && !dcacheRespEntry.alreadyFlush
    
    // 多路选择：优先使用旁路通道的数据和索引，从而阻塞普通的本地写回候选
    val finalWbValid = doBypass || hasLocalWbCandidate
    val finalWbIdx   = Mux(doBypass, dcacheRespIdx, localWbIdx)
    val finalWbData  = Mux(doBypass, dcacheRespData, entries(localWbIdx).data)
    
    val finalWbEntry = entries(finalWbIdx)
    val wbIsCacheOp    = finalWbEntry.cacheOp.valid
   
    // 构建回写端口信号
    io.outResult.valid                := finalWbValid
    io.outResult.bits.data            := finalWbData
    io.outResult.bits.memValid        := !wbIsCacheOp
    io.outResult.bits.memRead         := !wbIsCacheOp
    io.outResult.bits.memWrite        := false.B
    io.outResult.bits.memVaddr        := finalWbEntry.vaddr
    io.outResult.bits.memPaddr        := finalWbEntry.paddr
    io.outResult.bits.memStoreData    := 0.U
    io.outResult.bits.storeValid      := false.B
    io.outResult.bits.redirect.valid  := DontCare
    io.outResult.bits.redirect.bits.valid  := DontCare
    io.outResult.bits.redirect.bits.robIdx := DontCare
    io.outResult.bits.csrWen     := DontCare
    io.outResult.bits.csrWaddr   := DontCare
    io.outResult.bits.csrWdata   := DontCare
    io.outResult.bits.csrTimer   := DontCare
    io.outResult.bits.tlbFillIdx := 0.U
   
    val wbUop = io.outResult.bits.uop
    wbUop.pc         := finalWbEntry.pc
    wbUop.inst       := 0.U
    wbUop.excp       := finalWbEntry.excp
    wbUop.imm        := 0.U
    wbUop.csrAddress := 0.U
    wbUop.ldst       := 0.U
    wbUop.lrs1       := 0.U
    wbUop.lrs2       := 0.U
    wbUop.pdst       := finalWbEntry.pdst
    wbUop.prs1       := 0.U
    wbUop.prs2       := 0.U
    wbUop.oldPdst    := 0.U
    wbUop.rs1Valid   := false.B
    wbUop.rs2Valid   := false.B
    wbUop.rdValid    := finalWbEntry.rfWen
    wbUop.robIdx     := finalWbEntry.robIdxFull
    wbUop.robIdxFull := finalWbEntry.robIdxFull
    wbUop.issueQueue := 0.U
    wbUop.prs1Busy   := false.B
    wbUop.prs2Busy   := false.B
    wbUop.isSta      := false.B
    wbUop.isStd      := false.B
   
    val wbLqIdx = Wire(new LqPtr(LqSize))
    wbLqIdx.value := finalWbIdx
    wbLqIdx.flag  := false.B
    wbUop.lqIdx   := wbLqIdx
   
    val wbSqIdx = Wire(new SqPtr(SqSize))
    wbSqIdx.value := finalWbEntry.sqIdx
    wbSqIdx.flag  := false.B
    wbUop.sqIdx   := wbSqIdx
   
    wbUop.ctrl.fuType   := finalWbEntry.fuType
    wbUop.ctrl.lsuOp    := finalWbEntry.lsuOp
    wbUop.ctrl.barOp    := BarOp.none
    wbUop.ctrl.rfWen    := finalWbEntry.rfWen
    wbUop.ctrl.memRead  := !wbIsCacheOp
    wbUop.ctrl.memWrite := false.B
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
    wbUop.cacheOp := finalWbEntry.cacheOp
   
    wbUop.pdInfo  := DontCare
    wbUop.bpuInfo := DontCare
    wbUop.snptId  := DontCare
   
    // 无论是由 dcacheResp 旁路驱动还是本地驱动，只要握手成功即标记被写回
    when(io.outResult.fire) {
      entries(finalWbIdx).writtenBack := true.B
    }
   
    // ================================================================
    //  6. 出队
    // ================================================================
    val canDeq = entries(deqPtr.value).valid &&
                 (entries(deqPtr.value).writtenBack || entries(deqPtr.value).alreadyFlush)
    when(canDeq) {
      entries(deqPtr.value).valid := false.B
      deqPtr := deqPtr + 1.U
    }

    when(enqFire && !canDeq) {
      lqHasEntriesReg := lqHasEntriesReg + 1.U
    }.elsewhen(!enqFire && canDeq) {
      lqHasEntriesReg := lqHasEntriesReg - 1.U
    }

  }else if(LoadQVersion == 2){
    // ================================================================
    //  切断了forward和req的联系
    // ================================================================
    val entries = RegInit(VecInit(Seq.fill(LqSize)(0.U.asTypeOf(new LqEntry))))
    diffDontTouch(entries)
   
    val enqPtr = RegInit({
      val p = Wire(new LqPtrInner); p.value := 0.U; p.flag := false.B; p
    })
    val deqPtr = RegInit({
      val p = Wire(new LqPtrInner); p.value := 0.U; p.flag := false.B; p
    })
   
    val empty = deqPtr === enqPtr
    val full  = (deqPtr.value === enqPtr.value) && (deqPtr.flag =/= enqPtr.flag)
   
    io.full   := full
    io.empty  := empty
    io.enqPtr := enqPtr.value
    //val count = enqPtr.distanceTo(deqPtr)
    //io.lqHasEntries := count
    val lqHasEntriesReg = RegInit(0.U(log2Ceil(LqSize + 1).W))
    io.lqHasEntries := lqHasEntriesReg
   
    // ================================================================
    //  1. 入队
    // ================================================================
    val enqFire = io.enq.valid && !full
   
    when(enqFire) {
      val idx = enqPtr.value
      entries(idx).robIdxFull   := io.enq.robIdx
      entries(idx).sqIdx        := io.enq.sqIdx
      entries(idx).valid        := true.B
      entries(idx).addrValid    := false.B
      entries(idx).alreadyFlush := false.B
      
      entries(idx).readyToIssue := false.B // ★ 入队时初始化为 false
      
      entries(idx).issued       := false.B
      entries(idx).dataValid    := false.B
      entries(idx).writtenBack  := false.B
      entries(idx).vaddr        := 0.U
      entries(idx).paddr        := 0.U
      entries(idx).cacheable    := false.B
      entries(idx).data         := 0.U
      entries(idx).excp         := 0.U.asTypeOf(new ExceptionBundle)
      entries(idx).lsuOp        := io.enq.lsuOp
      entries(idx).pc           := io.enq.pc
      entries(idx).pdst         := io.enq.pdst
      entries(idx).rfWen        := io.enq.rfWen
      entries(idx).fuType       := io.enq.fuType
      entries(idx).cacheOp        := io.enq.cacheOp
      enqPtr := enqPtr + 1.U
    }
   
    // ================================================================
    //  重定向
    // ================================================================
    val doRedirect      = io.redirectInfo.valid && io.redirectInfo.bits.doRedirect
    val redirectRobIdx = io.redirectInfo.bits.robIdx
    val isNewer = Wire(Vec(LqSize, Bool()))
   
    for (i <- 0 until LqSize) {
      val e = entries(i)
      isNewer(i) := e.robIdxFull.isAfter(redirectRobIdx) && doRedirect && e.valid
      when(isNewer(i)) {
        e.alreadyFlush := true.B
      }
    }
   
    // ================================================================
    //  2. 地址写入
    // ================================================================
    when(io.addrWrite.valid) {
      val idx = io.addrWrite.idx
      entries(idx).addrValid := true.B
      entries(idx).vaddr     := io.addrWrite.vaddr
      entries(idx).paddr     := io.addrWrite.paddr
      entries(idx).excp      := io.addrWrite.excp
      entries(idx).cacheable := io.addrWrite.cacheable
      when(entries(idx).cacheOp.valid) {
        entries(idx).issued := true.B
        entries(idx).dataValid := true.B
        entries(idx).data := 0.U
      }
    }
   
    // ================================================================
    //  3. 第一级：SQ 分析与前递仲裁 (状态打拍)
    //
    //  ★ 优化说明：这里的极其复杂的组合逻辑结果，不再直接连到 io.dcacheReq，
    //  而是存入各个 entry 的寄存器中，从而切断时序关键路径。
    // ================================================================
    for (lqI <- 0 until LqSize) {
      val e = entries(lqI)
      val isUncache     = !e.cacheable
      val loadPaddrWord = e.paddr(31, 2)
   
      // 基础条件：有效，地址算出，还没发射，没有异常，没有被冲刷，且尚未被判定为 readyToIssue
      val basicReady = e.valid && e.addrValid && !e.issued &&
                       !e.excp.hasException && !e.alreadyFlush && !e.readyToIssue
   
      when(basicReady) {
        val sqOlderActive       = Wire(Vec(SqSize, Bool()))
        val sqOlderAddrUnknown  = Wire(Vec(SqSize, Bool()))
        val sqOlderUncache      = Wire(Vec(SqSize, Bool()))
        val sqOlderAddrConflict = Wire(Vec(SqSize, Bool()))
   
        for (sqI <- 0 until SqSize) {
          val sq = io.sqForwardInfo(sqI)
          val isOlder  = e.robIdxFull.isAfter(sq.robIdxFull) || sq.committed
          val isActive = sq.valid && !sq.alreadyFlush && !sq.hasException
   
          sqOlderActive(sqI)       := isOlder && isActive
          sqOlderAddrUnknown(sqI)  := sqOlderActive(sqI) && !sq.addrValid
          sqOlderUncache(sqI)      := sqOlderActive(sqI) && sq.addrValid && !sq.cacheable
          sqOlderAddrConflict(sqI) := sqOlderActive(sqI) && sq.addrValid && sq.cacheable &&
                                      (sq.paddr(31, 2) === loadPaddrWord)
        }
   
        val hasOlderActive      = sqOlderActive.reduce(_ || _)
        val hasOlderAddrUnknown = sqOlderAddrUnknown.reduce(_ || _)
        val hasOlderUncache     = sqOlderUncache.reduce(_ || _)
   
        val conflictCount       = PopCount(sqOlderAddrConflict)
        val hasNoConflict       = conflictCount === 0.U
        val hasSingleConflict   = conflictCount === 1.U
   
        // 情况 1：Uncache load (必须在 ROB Head 才能发射)
        when(isUncache) {
          when(!hasOlderActive && io.robHead.bits === e.robIdxFull) {
             e.readyToIssue := true.B  // 满足条件，标记为可发射
          }
        }
        // 情况 2：Cacheable load
        .otherwise {
          when(!hasOlderActive) {
            e.readyToIssue := true.B
          }
          .otherwise {
            // 有未决地址或 uncache 更老 store 时，继续等待，readyToIssue 维持 false
            when(!hasOlderAddrUnknown && !hasOlderUncache) {
              when(hasNoConflict) {
                e.readyToIssue := true.B
              }
              .elsewhen(hasSingleConflict && !e.cacheOp.valid) {
                // 处理唯一的前递冲突
                val singleConflictIdx = PriorityEncoder(sqOlderAddrConflict)
                val singleSq = io.sqForwardInfo(singleConflictIdx)
   
                when(singleSq.lsuOp === LsuOp.sw && singleSq.dataValid && !isNewer(lqI)) {
                  // ★ 前递逻辑：数据已经就绪，直接截断！不需要 dcache，直接视为已经发射和数据有效
                  e.issued    := true.B
                  e.dataValid := true.B
                  
                  val rawWord  = singleSq.data
                  val byteOff  = e.paddr(1, 0)
                  val byteData = MuxLookup(byteOff, rawWord(7, 0), Seq(
                    0.U -> rawWord(7, 0),
                    1.U -> rawWord(15, 8),
                    2.U -> rawWord(23, 16),
                    3.U -> rawWord(31, 24)
                  ))
                  val halfData = Mux(byteOff(1), rawWord(31, 16), rawWord(15, 0))
   
                  e.data := MuxLookup(e.lsuOp, rawWord, Seq(
                    LsuOp.lw  -> rawWord,
                    LsuOp.lh  -> Cat(Fill(16, halfData(15)), halfData),
                    LsuOp.lhu -> Cat(0.U(16.W), halfData),
                    LsuOp.lb  -> Cat(Fill(24, byteData(7)), byteData),
                    LsuOp.lbu -> Cat(0.U(24.W), byteData)
                  ))
                }
              }
            }
          }
        }
      }
    }
   
    // ================================================================
    //  3b. 第二级：基于状态寄存器，计算 DCache 发射候选 
    //
    //  ★ 优化说明：仲裁器的输入现在只依赖于 e.readyToIssue (以及基础的 valid 信号)，
    //  大幅减少了优先级编码器前的逻辑级数。
    // ================================================================
    val issueCandidates = Wire(Vec(LqSize, Bool()))
    for (i <- 0 until LqSize) {
      val idx = (deqPtr.value + i.U)(log2Ceil(LqSize) - 1, 0)
      val e = entries(idx)
      issueCandidates(i) := e.valid && !e.issued && !e.alreadyFlush && 
                            !e.excp.hasException && !e.cacheOp.valid &&
                            e.readyToIssue  // 仅看状态寄存器
    }
   
    val hasIssueCandidate = issueCandidates.reduce(_ || _)
    val issueOffset       = PriorityEncoder(issueCandidates)
    val issueIdx          = (deqPtr.value + issueOffset)(log2Ceil(LqSize) - 1, 0)
    val issueEntry        = entries(issueIdx)
   
    // ── 3c. 真正的 DCache 请求 ──
    io.dcacheReq.valid           := hasIssueCandidate && !isNewer(issueIdx) 
    io.dcacheReq.bits.lqIdx      := issueIdx
    io.dcacheReq.bits.paddr      := issueEntry.paddr
    io.dcacheReq.bits.vaddr      := issueEntry.vaddr
    io.dcacheReq.bits.cacheable  := issueEntry.cacheable
    io.dcacheReq.bits.robIdx     := issueEntry.robIdxFull
    io.dcacheReq.bits.lsuOp := Mux(issueEntry.lsuOp === LsuOp.lrw,
      LsuOp.lw, issueEntry.lsuOp)
   
    when(io.dcacheReq.fire) {
      entries(issueIdx).issued := true.B
      // 发射后，不需要刻意清空 readyToIssue，因为 issued = true 会在此后一直屏蔽它
    }
   
    // ================================================================
    //  4. 接收 DCache 响应 & 5. 向后端写回 (包含旁路机制)
    // ================================================================
    io.dcacheResp.ready := true.B
    
    when(io.dcacheResp.fire) {
      val idx = io.dcacheResp.bits.lqIdx
      entries(idx).dataValid := true.B
      entries(idx).data      := io.dcacheResp.bits.data
    }
   
    val wbCandidates = Wire(Vec(LqSize, Bool()))
    for (i <- 0 until LqSize) {
      val idx = (deqPtr.value + i.U)(log2Ceil(LqSize) - 1, 0)
      val e = entries(idx)
      wbCandidates(i) := e.valid && !e.alreadyFlush && (e.dataValid || e.excp.hasException) && !e.writtenBack
    }
   
    val hasLocalWbCandidate = wbCandidates.reduce(_ || _)
    val wbOffset            = PriorityEncoder(wbCandidates)
    val localWbIdx          = (deqPtr.value + wbOffset)(log2Ceil(LqSize) - 1, 0)
    
    val dcacheRespValid = io.dcacheResp.valid
    val dcacheRespIdx   = io.dcacheResp.bits.lqIdx
    val dcacheRespData  = io.dcacheResp.bits.data
    val dcacheRespEntry = entries(dcacheRespIdx)
    
    val doBypass = dcacheRespValid && !dcacheRespEntry.alreadyFlush
    
    val finalWbValid = doBypass || hasLocalWbCandidate
    val finalWbIdx   = Mux(doBypass, dcacheRespIdx, localWbIdx)
    val finalWbData  = Mux(doBypass, dcacheRespData, entries(localWbIdx).data)
    
    val finalWbEntry = entries(finalWbIdx)
    val wbIsCacheOp    = finalWbEntry.cacheOp.valid
   
    io.outResult.valid                := finalWbValid
    io.outResult.bits.data            := finalWbData
    io.outResult.bits.memValid        := !wbIsCacheOp
    io.outResult.bits.memRead         := !wbIsCacheOp
    io.outResult.bits.memWrite        := false.B
    io.outResult.bits.memVaddr        := finalWbEntry.vaddr
    io.outResult.bits.memPaddr        := finalWbEntry.paddr
    io.outResult.bits.memStoreData    := 0.U
    io.outResult.bits.storeValid      := false.B
    io.outResult.bits.redirect.valid  := DontCare
    io.outResult.bits.redirect.bits.valid  := DontCare
    io.outResult.bits.redirect.bits.robIdx := DontCare
    io.outResult.bits.csrWen     := DontCare
    io.outResult.bits.csrWaddr   := DontCare
    io.outResult.bits.csrWdata   := DontCare
    io.outResult.bits.csrTimer   := DontCare
    io.outResult.bits.tlbFillIdx := 0.U
   
    val wbUop = io.outResult.bits.uop
    wbUop.pc         := finalWbEntry.pc
    wbUop.inst       := 0.U
    wbUop.excp       := finalWbEntry.excp
    wbUop.imm        := 0.U
    wbUop.csrAddress := 0.U
    wbUop.ldst       := 0.U
    wbUop.lrs1       := 0.U
    wbUop.lrs2       := 0.U
    wbUop.pdst       := finalWbEntry.pdst
    wbUop.prs1       := 0.U
    wbUop.prs2       := 0.U
    wbUop.oldPdst    := 0.U
    wbUop.rs1Valid   := false.B
    wbUop.rs2Valid   := false.B
    wbUop.rdValid    := finalWbEntry.rfWen
    wbUop.robIdx     := finalWbEntry.robIdxFull
    wbUop.robIdxFull := finalWbEntry.robIdxFull
    wbUop.issueQueue := 0.U
    wbUop.prs1Busy   := false.B
    wbUop.prs2Busy   := false.B
    wbUop.isSta      := false.B
    wbUop.isStd      := false.B
   
    val wbLqIdx = Wire(new LqPtr(LqSize))
    wbLqIdx.value := finalWbIdx
    wbLqIdx.flag  := false.B
    wbUop.lqIdx   := wbLqIdx
   
    val wbSqIdx = Wire(new SqPtr(SqSize))
    wbSqIdx.value := finalWbEntry.sqIdx
    wbSqIdx.flag  := false.B
    wbUop.sqIdx   := wbSqIdx
   
    wbUop.ctrl.fuType   := finalWbEntry.fuType
    wbUop.ctrl.lsuOp    := finalWbEntry.lsuOp
    wbUop.ctrl.barOp    := BarOp.none
    wbUop.ctrl.rfWen    := finalWbEntry.rfWen
    wbUop.ctrl.memRead  := !wbIsCacheOp
    wbUop.ctrl.memWrite := false.B
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
    wbUop.cacheOp := finalWbEntry.cacheOp
   
    wbUop.pdInfo  := DontCare
    wbUop.bpuInfo := DontCare
    wbUop.snptId  := DontCare
   
    when(io.outResult.fire) {
      entries(finalWbIdx).writtenBack := true.B
    }
   
    // ================================================================
    //  6. 出队
    // ================================================================
    val canDeq = entries(deqPtr.value).valid &&
                 (entries(deqPtr.value).writtenBack || entries(deqPtr.value).alreadyFlush)
    when(canDeq) {
      entries(deqPtr.value).valid := false.B
      deqPtr := deqPtr + 1.U
    }

    when(enqFire && !canDeq) {
      lqHasEntriesReg := lqHasEntriesReg + 1.U
    }.elsewhen(!enqFire && canDeq) {
      lqHasEntriesReg := lqHasEntriesReg - 1.U
    }
  
  
  }
  
}
  