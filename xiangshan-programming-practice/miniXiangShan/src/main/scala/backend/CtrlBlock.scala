package minixiangshan.backend
 
import chisel3._
import chisel3.util._
import minixiangshan.config._
import minixiangshan.frontend.CtrlFlowIO
import minixiangshan.backend.decode._
import minixiangshan.backend.rename._
import minixiangshan.backend.dispatch._
import minixiangshan.backend.rob._
import minixiangshan.backend.issue._
import minixiangshan.difftest._
import minixiangshan.backend.execute._
import minixiangshan.backend.redirect._
import minixiangshan.csr._
import minixiangshan.mmu._

class CtrlBlockIO(implicit p: Parameters) extends NSBundle {
  // ── 来自前端 ──
  val in       = Vec(CtrlBlockWidth, Flipped(Decoupled(new CtrlFlowIO)))
 
  // ── 到各 Issue Queue ──
  val q1IQEnq  = Vec(IQEnqPorts.Q1, ValidIO(new DispatchedInst))
  val q2IQEnq  = Vec(IQEnqPorts.Q2, ValidIO(new DispatchedInst))
  val q3IQEnq  = Vec(IQEnqPorts.Q3, ValidIO(new DispatchedInst))
  val q4IQEnq  = Vec(IQEnqPorts.Q4, ValidIO(new DispatchedInst))
  val q5IQEnq  = Vec(IQEnqPorts.Q5, ValidIO(new DispatchedInst))
 
  // ── IQ 反馈 ──
  val iqFeedback = Input(new IssueQueueFeedback)
 
  // ── LSQ ──
  val lsEnq    = new LsEnqIO


  val writeback = Input(Vec(WbBusWidth, Valid(new RobWriteback)))  // 执行单元写回
 
  // ── ROB 提交 ──
  //val commit   = Output(Vec(CommitWidth, new RobCommitInfo))
  val commitToSq  = new RobCommitToSq
  val commitToCsr = new RobCommitToCsr
  val currentPriv  = Input(UInt(privLen.W))
  val storeQueueEmpty = Input(Bool())
  val fenceIReq = Output(Bool())
  val fenceIReady = Input(Bool())
  val cacheOpICacheReq = Output(Bool())

 
  // ── 重定向 ──
  val excpEedirect = Output(new RedirectInfo)
  // brMsRedirect   = Flipped (ValidIO( new brMispredictRedirect) )    // 误预测重定向
  // 错误预测信息
  val bruInfo    = Flipped( ValidIO( new redirectInfoFromBru ))    // 误预测重定向

  // 输出重定向
  val redirectInfo    = (ValidIO( new redirectInfoToModule )) 

  // 陷入请求（送 CSR 计算入口地址并更新特权状态）
  val trapReq             = Output(new TrapReq)
  /** CSR 的陷入环境（mtvec/stvec/委托/返回地址） */
  val trapEnv             = Input(new TrapEnv)
  /** CSR 给出的最高优先级中断编号 */
  val intrCode            = Input(UInt(IntrCode.width.W))
  /** 本周期是否有指令提交（用于 minstret） */
  val commitValid         = Output(Bool())
  


 
  // ── 冲刷与外部中断 ──
  //val flush    = Input(Bool())
  val extInt   = Input(Bool())

  val wakeupPorts   = Input(Vec(IQNumWakeupPorts, Valid(new IssueWakeup)))
  val robHead         = Output(Valid(new RobPtr(RobSize)))
}
 
class CtrlBlock(implicit p: Parameters) extends NSModule {
  val io = IO(new CtrlBlockIO)
  val difftest = if (EnableDifftest) Some(IO(Output(new CtrlBlockDifftestBundle))) else None



  val doFlush = io.redirectInfo.valid && io.redirectInfo.bits.doRedirect
  // ================================================================
  //  译码级
// ================================================================
  val decodeStage = Module(new DecodeStage)
  val renameStage = Module(new RenameStage)
  val redirectController = Module(new RedirectController)
  val dispatchStage = Module(new DispatchStage)
  val disp2Rob = Module(new DispatchRobBuffer)
  val rob = Module(new ROB)
  val disp2Lsq = Module(new DispatchLsqBuffer)


  redirectController.io.trapEnv <> io.trapEnv

  io.redirectInfo := redirectController.io.redirectInfo

  redirectController.io.bruRedirect := io.bruInfo
  redirectController.io.currentPriv := io.currentPriv
  redirectController.io.intrCode    := io.intrCode

  // 陷入请求送往 CSR；CSR 组合返回入口地址
  io.trapReq := redirectController.io.trapReq

  decodeStage.io.in    <> io.in
  decodeStage.io.extInt := io.extInt
  decodeStage.io.flush  := doFlush                  
 
  // ================================================================
  //  重命名级
  // ================================================================

  renameStage.io.in      <> decodeStage.io.out
  renameStage.io.ratRead <> decodeStage.io.ratRead
  renameStage.io.flush    := doFlush
  renameStage.io.robCount := rob.io.robCount
  renameStage.io.inFlightToRename := disp2Rob.io.inFlightToRename
  renameStage.io.redirectInfo := io.redirectInfo
  //因为Rob的归还物理寄存器的逻辑是在收到redirct之后打一拍后再归还，所以这里必须配合在Redirect拉起的那个周期就必须暂停rename和dispatch让他不要再变了……变了就还不了了~
  renameStage.io.stall := redirectController.io.robRedirectPause // || rob.io.robRedirect.valid
  

  if (EnableDifftest) {
    difftest.get.archState := renameStage.difftest.get
  }
 
  // ================================================================
  //  分发级
  // ================================================================
  
  dispatchStage.io.in       <> renameStage.io.out
  dispatchStage.io.flush    := doFlush
  dispatchStage.io.redirectInfo := io.redirectInfo
  dispatchStage.io.stall := redirectController.io.robRedirectPause //|| rob.io.robRedirect.valid
  dispatchStage.io.robEmpty := rob.io.robCount === 0.U && disp2Rob.io.empty
  // ── IQ 入队端口 ──
  dispatchStage.io.q1IQEnq     <> io.q1IQEnq
  dispatchStage.io.q2IQEnq     <> io.q2IQEnq
  dispatchStage.io.q3IQEnq     <> io.q3IQEnq
  dispatchStage.io.q4IQEnq     <> io.q4IQEnq
  dispatchStage.io.q5IQEnq     <> io.q5IQEnq
 
  // ── IQ 反馈 ──
  dispatchStage.io.iqFeedback <> io.iqFeedback
 
  // ── LSQ ──
  //dispatchStage.io.lsEnq <> io.lsEnq
  dispatchStage.io.lsEnq <> disp2Lsq.io.enqReq
  io.lsEnq <> disp2Lsq.io.deqReq

  disp2Lsq.io.redirectInfo := io.redirectInfo
  dispatchStage.io.dispatchLqFull := disp2Lsq.io.dispatchLqFull
  dispatchStage.io.dispatchSqFull := disp2Lsq.io.dispatchSqFull

  dispatchStage.io.bufHasPendingLoadNeedFlush := disp2Lsq.io.bufHasPendingLoadNeedFlush
  dispatchStage.io.bufHasPendingStoreNeedFlush := disp2Lsq.io.bufHasPendingStoreNeedFlush

  // ================================================================
  //  Rob请求打一拍
  // ================================================================

  disp2Rob.io.enq <> dispatchStage.io.robEnq
  disp2Rob.io.flush := doFlush
  disp2Rob.io.pause := redirectController.io.robRedirectPause // || rob.io.robRedirect.valid

  dispatchStage.io.dis2robHas := !disp2Rob.io.empty
  
  // ================================================================
  //  ROB
  // ================================================================
  //rob的重定向
  rob.io.redirectInfo :=  io.redirectInfo

  //rob接受暂停
  rob.io.robPause := redirectController.io.robRedirectPause

  //异常 、CSr相关重定向信号
  redirectController.io.robRedirect := rob.io.robRedirect

  //回滚控制
  rob.io.robNeedRollback := redirectController.io.robNeedRollback
  //回滚目标
  rob.io.robRollbackTarget := redirectController.io.robRollbackTarget
  //回滚响应
  redirectController.io.robRollbackDone := rob.io.robRollbackDone

  io.robHead := rob.io.head

  io.commitToSq             := rob.io.commitToSq
  
  //缓解时序~
  renameStage.io.archCommit <>  RegNext( rob.io.archCommit               )
  io.commitToCsr            :=  RegNext( rob.io.commitToCsr              )
  io.commitValid            :=  rob.io.commit.valid.asUInt.orR

  rob.io.currentPriv := io.currentPriv

  rob.io.storeQueueEmpty := io.storeQueueEmpty
  io.fenceIReq        := rob.io.fenceIReq
  rob.io.fenceIReady   := io.fenceIReady
  io.cacheOpICacheReq      := rob.io.cacheOpICacheReq
  if (EnableDifftest) {
    for (i <- 0 until CommitWidth) {
      val robCommit      = RegNext(    rob.io.commit.bits(i) )
      val robCommitvalid = RegNext(    rob.io.commit.valid(i) )

      val diffCommit = difftest.get.commit(i)

      //ROB提交窗口中时包含着异常的，也就是在rob视角异常也会提交（用这种方式清除他），但肯定不会改架构
      diffCommit.valid      := robCommitvalid//&& !rob.io.commit.isExcpCommit(i) 
      diffCommit.pc         := robCommit.pc
      diffCommit.instr      := robCommit.inst(31, 0)
      diffCommit.rfWen      := robCommit.rfWen
      diffCommit.wdest      := robCommit.ldst
      diffCommit.wdata      := robCommit.rfdata
      val isPureXret = robCommit.excp.excpVec === (1.U << ExcType.XRET.id).asUInt
      diffCommit.excpFlush  := robCommit.excp.hasException && !isPureXret
      diffCommit.xretFlush  := isPureXret
      diffCommit.cause      := DifftestUtils.excpCause(robCommit.excp)
      diffCommit.trap       := DifftestUtils.isTrap(robCommit.inst)
      diffCommit.trapCode   := 0.U
      diffCommit.tlbFillIdx := robCommit.tlbFillIdx
      diffCommit.load.valid := robCommit.memRead
      diffCommit.load.paddr := robCommit.memPaddr
      diffCommit.load.vaddr := robCommit.memVaddr
      diffCommit.store.valid := robCommit.storeValid
      diffCommit.store.paddr := robCommit.memPaddr
      diffCommit.store.vaddr := robCommit.memVaddr
      diffCommit.store.data  := robCommit.storeData
    }
  }
 
  // ROB 重定向
  rob.io.flush := false.B//brMsFlush || io.excpEedirect.valid
  rob.io.redirectInfo := io.redirectInfo

  rob.io.writeback <> io.writeback
  dispatchStage.io.wakeupPorts <> io.wakeupPorts

 
  // ROB 入队连接（从 Dispatch 级发起）
  rob.io.enq <> disp2Rob.io.deq
  rob.io.enqFromDispatch <> disp2Rob.io.deqDisp2Rob
 
  // ================================================================
  //  重定向信号
  // ================================================================
  io.excpEedirect := 0.U.asTypeOf(new RedirectInfo)
}
