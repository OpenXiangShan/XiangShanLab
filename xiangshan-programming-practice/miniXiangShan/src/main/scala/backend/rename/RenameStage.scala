package minixiangshan.backend.rename
 
import chisel3._
import chisel3.util._
import minixiangshan.config._
import minixiangshan.backend.decode._
import minixiangshan.backend.execute._
import minixiangshan.backend.rob._
import minixiangshan.util.CircularQueuePtr

class RenameStage(implicit p: Parameters) extends NSModule {
 
  val io = IO(new Bundle {
    val in      = Vec(CtrlBlockWidth, Flipped(Decoupled(new DecodedInst)))
    val ratRead = Vec(CtrlBlockWidth, Flipped(new RATReadIO))
    val out     = Vec(CtrlBlockWidth, Decoupled(new RenamedInst))
    val archCommit       = Vec(CommitWidth, Input(new ArchCommitInfo))
    val robCount     = Input(UInt(log2Ceil(RobSize + 1).W))   
    val inFlightToRename    = Input(UInt(log2Ceil(CtrlBlockWidth * 2 + 1).W))  

    val redirectInfo    = Flipped ( ValidIO( new redirectInfoToModule ))
    val stall = Input(Bool())
    val flush   = Input(Bool())
  })
  val difftest = if (EnableDifftest) Some(IO(Output(Vec(IntLogicRegs, UInt(PhyRegIdxWidth.W))))) else None
 
  val resolve = Wire(Valid(new SnapshotResolveInfo))

  resolve.valid := io.redirectInfo.valid && io.redirectInfo.bits.fromBru 
  resolve.bits.snptId := io.redirectInfo.bits.snptId
  resolve.bits.isMispredict := io.redirectInfo.bits.doRedirect

  val rat             = Module(new RenameTable)
  val freeList        = Module(new FreeList)
  val snapshotManager = Module(new SnapshotManager)   
  snapshotManager.io.resolveAllSs := io.redirectInfo.valid && io.redirectInfo.bits.doRedirect && io.redirectInfo.bits.fromRob

  if (EnableDifftest) {
    difftest.get := rat.difftest.get
  }

  val stgValid  = RegInit(false.B)
  val laneValid = RegInit(VecInit(Seq.fill(CtrlBlockWidth)(false.B)))
  val stgData = RegInit(VecInit(Seq.fill(CtrlBlockWidth)(0.U.asTypeOf(new DecodedInst))))

  val laneWaitForward = VecInit((0 until CtrlBlockWidth).map(i =>
    laneValid(i) && stgData(i).ctrl.waitForward && stgValid
  ))
  val laneBlockBackward = VecInit((0 until CtrlBlockWidth).map(i =>
    laneValid(i) && stgData(i).ctrl.blockBackward && stgValid
  ))

  val hasSpecial = laneWaitForward.asUInt.orR || laneBlockBackward.asUInt.orR

  val outReadyAll = (0 until CtrlBlockWidth).map(i =>
    !laneValid(i) || io.out(i).ready
  ).reduce(_ && _)
 
  val needAllocVec = VecInit((0 until CtrlBlockWidth).map(i =>
    stgValid && laneValid(i) && stgData(i).rdValid && stgData(i).rd =/= 0.U
  ))

  val needRobAllocCount = PopCount(VecInit((0 until CtrlBlockWidth).map(i =>
    stgValid && laneValid(i)  
  )))
 
  val canRobAccept = ( RobSize.U >= needRobAllocCount +& io.inFlightToRename +& io.robCount ) && ( !hasSpecial || (hasSpecial && io.inFlightToRename === 0.U && io.robCount === 0.U))
 
  val canFireThisCycle = freeList.io.canAlloc && snapshotManager.io.allocOk && canRobAccept && !io.stall
 
  val outFire = stgValid && outReadyAll && canFireThisCycle 

  val stgReady = !stgValid || outFire
  val inValid = io.in.map(_.valid).reduce(_ || _)
  val inFire  = inValid && stgReady
 
  for (i <- 0 until CtrlBlockWidth) {
    io.in(i).ready := stgReady
  }
 
  val doFlush = io.redirectInfo.valid && io.redirectInfo.bits.doRedirect
  when(doFlush) {
    stgValid := false.B
    for (i <- 0 until CtrlBlockWidth) { laneValid(i) := false.B }
  }.elsewhen(inFire) {
    stgValid := true.B
    for (i <- 0 until CtrlBlockWidth) {
      laneValid(i) := io.in(i).valid
      stgData(i)   := io.in(i).bits
    }
  }.elsewhen(outFire) {
    stgValid := false.B
    for (i <- 0 until CtrlBlockWidth) { laneValid(i) := false.B }
  }

  val needSs = VecInit((0 until CtrlBlockWidth).map(i =>
    stgValid && laneValid(i) && ( (stgData(i).ctrl.isBranch && !stgData(i).pdInfo.isJal) || stgData(i).pdInfo.isJalr)
  ))

  val doNeedSs = VecInit((0 until CtrlBlockWidth).map(i =>
    needSs(i) && outFire  && !doFlush
  ))

  snapshotManager.io.doAllocReqs := doNeedSs
  snapshotManager.io.allocReqs := needSs
  snapshotManager.io.resolve  := resolve
 
  for (i <- 0 until CtrlBlockWidth) {
    rat.io.readPorts(i).addr               := io.ratRead(i).rs1
    rat.io.readPorts(i).hold               := io.ratRead(i).hold1
    rat.io.readPorts(CtrlBlockWidth + i).addr   := io.ratRead(i).rs2
    rat.io.readPorts(CtrlBlockWidth + i).hold   := io.ratRead(i).hold2

    rat.io.readPorts(2 * CtrlBlockWidth + i).addr := 0.U
    //  Mux(stgValid, stgData(i).rd, io.in(i).bits.rd)
    rat.io.readPorts(2 * CtrlBlockWidth + i).hold := true.B
  }
 
  for (i <- 0 until CtrlBlockWidth) {
    freeList.io.allocReqs(i) := needAllocVec(i)
  }
  freeList.io.doAlloc := outFire && !doFlush
 
  val specWritePorts = Wire(Vec(CtrlBlockWidth, new RatWritePort))
  for (i <- 0 until CtrlBlockWidth) {
    specWritePorts(i).wen  := needAllocVec(i) 
    specWritePorts(i).addr := stgData(i).rd
    specWritePorts(i).data := freeList.io.allocPdest(i).bits
  }
  rat.io.specWritePorts := specWritePorts
 
  for (i <- 0 until CommitWidth) {
    rat.io.archWritePorts(i).wen  := io.archCommit(i).valid && io.archCommit(i).rfWen && !io.archCommit(i).isWalk
    rat.io.archWritePorts(i).addr := io.archCommit(i).ldst
    rat.io.archWritePorts(i).data := io.archCommit(i).pdst
  }
 
  rat.io.redirect      := doFlush
  rat.io.doRecover     := snapshotManager.io.doRecover        
  rat.io.recoverId     := snapshotManager.io.recoverId        
  rat.io.snptSave      := snapshotManager.io.allocIds         
  rat.io.snptInvalidate := snapshotManager.io.invalidateSlots 
  rat.io.writeFire     := outFire 
 
  freeList.io.snptSave       := snapshotManager.io.allocIds          
  freeList.io.doRecover      := snapshotManager.io.doRecover         
  freeList.io.recoverId      := snapshotManager.io.recoverId         
  freeList.io.snptInvalidate := snapshotManager.io.invalidateSlots   
 
  for (i <- 0 until CommitWidth) {
    rat.io.archReadPorts(i).laddr    := io.archCommit(i).ldst
    freeList.io.deallocReqs(i).valid := io.archCommit(i).valid && io.archCommit(i).rfWen
    freeList.io.deallocReqs(i).bits  := Mux( io.archCommit(i).isWalk , io.archCommit(i).pdst, rat.io.archReadPorts(i).pdata)
  }
 
  val prs1Raw    = VecInit((0 until CtrlBlockWidth).map(i => rat.io.readPorts(i).data))
  val prs2Raw    = VecInit((0 until CtrlBlockWidth).map(i => rat.io.readPorts(CtrlBlockWidth + i).data))
  //val oldPdstRaw = VecInit((0 until CtrlBlockWidth).map(i => rat.io.readPorts(2 * CtrlBlockWidth + i).data))
  val oldPdstRaw = VecInit((0 until CtrlBlockWidth).map(i => 0.U))
 
  val prs1Final    = Wire(Vec(CtrlBlockWidth, UInt(PhyRegIdxWidth.W)))
  val prs2Final    = Wire(Vec(CtrlBlockWidth, UInt(PhyRegIdxWidth.W)))
  val oldPdstFinal = Wire(Vec(CtrlBlockWidth, UInt(PhyRegIdxWidth.W)))
 
  for (i <- 0 until CtrlBlockWidth) {
    prs1Final(i)    := prs1Raw(i)
    prs2Final(i)    := prs2Raw(i)
    oldPdstFinal(i) := oldPdstRaw(i)
 
    for (j <- 0 until i) {
      val jHasAlloc = laneValid(j) && needAllocVec(j) && stgData(j).rd =/= 0.U
      val jPdst     = freeList.io.allocPdest(j).bits
      when(jHasAlloc && stgData(j).rd === stgData(i).rs1 && stgData(i).rs1Valid) {
        prs1Final(i) := jPdst
      }
      when(jHasAlloc && stgData(j).rd === stgData(i).rs2 && stgData(i).rs2Valid) {
        prs2Final(i) := jPdst
      }
      when(jHasAlloc && stgData(j).rd === stgData(i).rd) {
        oldPdstFinal(i) := jPdst
      }
    }
  }
 
  val robIdxHead = RegInit({
    val ptr = Wire(new RobPtr(RobSize)); ptr.value := 0.U; ptr.flag := false.B; ptr
  })
  val validCount = PopCount(VecInit((0 until CtrlBlockWidth).map(i => stgValid && laneValid(i))))
  val robIdxHeadNext = Wire(new RobPtr(RobSize))
  robIdxHeadNext := robIdxHead
  when(doFlush) {
        //异常在ROb中的行为实际上是和普通的出队一样的操作的，所以这里的指针也应该是这样的变化
    robIdxHeadNext := io.redirectInfo.bits.robIdx + 1.U
  }.elsewhen(outFire) {
    robIdxHeadNext := robIdxHead + validCount
  }
  robIdxHead := robIdxHeadNext
 
  val robIndices = Wire(Vec(CtrlBlockWidth, new RobPtr(RobSize)))
  var robOffset  = 0.U(log2Ceil(RobSize).W)
  for (i <- 0 until CtrlBlockWidth) {
    robIndices(i) := robIdxHead + robOffset
    robOffset = robOffset + laneValid(i).asUInt
  }
 
  for (i <- 0 until CtrlBlockWidth) {
    val u = io.out(i).bits
 
    u.pc         := stgData(i).pc
    u.inst       := stgData(i).inst
    u.ctrl       := stgData(i).ctrl
    u.excp       := stgData(i).excp
    u.imm        := stgData(i).imm
    u.csrAddress := stgData(i).csrAddress
    u.cacheOp      := stgData(i).cacheOp
    u.pdInfo     := stgData(i).pdInfo
    u.bpuInfo    := stgData(i).bpuInfo
 
    u.ldst := stgData(i).rd
    u.lrs1 := stgData(i).rs1
    u.lrs2 := stgData(i).rs2
 
    u.prs1 := Mux(stgData(i).rs1 === 0.U || !stgData(i).rs1Valid, 0.U, prs1Final(i))
    u.prs2 := Mux(stgData(i).rs2 === 0.U || !stgData(i).rs2Valid, 0.U, prs2Final(i))
    u.pdst := Mux(needAllocVec(i), freeList.io.allocPdest(i).bits, 0.U)
    u.oldPdst := 0.U //Mux(needAllocVec(i) && stgData(i).rd =/= 0.U, oldPdstFinal(i), 0.U)
 
    u.rs1Valid := stgData(i).rs1Valid
    u.rs2Valid := stgData(i).rs2Valid
    u.rdValid  := stgData(i).rdValid
 
    u.robIdx := robIndices(i)
 
    u.snptId := snapshotManager.io.allocIds(i)
 
    io.out(i).valid := stgValid && laneValid(i) && canFireThisCycle
  }
}