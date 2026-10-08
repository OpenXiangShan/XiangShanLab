package minixiangshan.backend.dispatch
 
import chisel3._
import chisel3.util._
import minixiangshan.config._
import minixiangshan.backend.decode._
import minixiangshan.backend.rob._
import minixiangshan.backend.rename._
import minixiangshan.frontend.PredecodeInfo
import minixiangshan.util.CircularQueuePtr
import minixiangshan.frontend.bpuInfoBundle

object IssueQueueId {
  val Q1 = 0   // ALU + CSR
  val Q2 = 1   // ALU + DIV
  val Q3 = 2   // ALU + MUL + JMP
  val Q4 = 3   // LOAD + STA
  val Q5 = 4   // STD
  val NUM = 5
  val width = log2Ceil(NUM)
}
 
object IQEnqPorts {
  val Q1 = 1; val Q2 = 1; val Q3 = 1; val Q4 = 1; val Q5 = 1
  val TOTAL = 5
}
 
// ================================================================
//  Dispatch 输出的已分发指令（发给各 Issue Queue）
// ================================================================
class LqPtr(LqSize: Int) extends CircularQueuePtr[LqPtr](LqSize)
class SqPtr(SqSize: Int) extends CircularQueuePtr[SqPtr](SqSize)
class DispatchedInst(implicit p: Parameters) extends NSBundle {
  // ── 来自 RenamedInst 的全部字段 ──
  val pc         = UInt(XLEN.W)
  val inst       = UInt(XLEN.W)
  val ctrl       = new DecodeCtrl
  val excp       = new ExceptionBundle
  val imm        = UInt(XLEN.W)
  val csrAddress = UInt(csrAddrLen.W)
  val cacheOp      = new CacheOpDecode
  val pdInfo     = new PredecodeInfo
  val bpuInfo     = new bpuInfoBundle
 
  val ldst = UInt(5.W)
  val lrs1 = UInt(5.W)
  val lrs2 = UInt(5.W)
 
  val pdst     = UInt(PhyRegIdxWidth.W)
  val prs1     = UInt(PhyRegIdxWidth.W)
  val prs2     = UInt(PhyRegIdxWidth.W)
  val oldPdst  = UInt(PhyRegIdxWidth.W)
 
  val rs1Valid = Bool()
  val rs2Valid = Bool()
  val rdValid  = Bool()

  val snptId = Valid(UInt(log2Ceil(SnapshotNum).W))

 
  val robIdx   = new RobPtr(RobSize)
 
  // ── Dispatch 新增字段 ──
  val robIdxFull = new RobPtr(RobSize)  // 含 flag 的完整 ROB 指针
 
  val lqIdx    = new SqPtr(SqSize)//UInt(log2Ceil(LqSize).W)   // Load Queue 指针
  val sqIdx    =  new LqPtr(LqSize)//UInt(log2Ceil(SqSize).W)   // Store Queue 指针
 
  val issueQueue = UInt(IssueQueueId.width.W)  // 分发目标队列编号
 
  val prs1Busy = Bool()  // 源 1 是否就绪
  val prs2Busy = Bool()  // 源 2 是否就绪
 
  // ── Store 分裂标记 ──
  val isSta    = Bool()   // Store-Addr 微操作
  val isStd    = Bool()   // Store-Data 微操作
}
 
class IssueQueueFeedback(implicit p: Parameters) extends NSBundle {
  val q1FreeEntries = UInt(IQ1Width.W)  // Q1 当前空闲条目数
  val q2FreeEntries = UInt(IQ2Width.W)
  val q3FreeEntries = UInt(IQ3Width.W)
  val q4FreeEntries = UInt(IQ4Width.W)
  val q5FreeEntries = UInt(IQ5Width.W)
}
 
 
// ================================================================
//  LSQ 入队请求（仅分配条目，地址/数据后续由执行单元填入）
// ================================================================
class LsEnqEntry(implicit p: Parameters) extends NSBundle {
  val robIdx  = new RobPtr(RobSize)
  val isLoad  = Bool()
  val isStore = Bool()
  val sqIdx   = new SqPtr(SqSize)
  val lqIdx   = new LqPtr(LqSize)
}
 
class LsEnqIO(implicit p: Parameters) extends NSBundle {
  val req    = Valid(new LsEnqEntry)
  val toLsqData = new RenamedInst

  val lqHasEntries = Input(UInt(log2Ceil(LqSize + 1).W))
  val sqHasEntries = Input(UInt(log2Ceil(SqSize + 1).W))
    
}
 
// ================================================================
//  BusyTable IO
// ================================================================
class BusyTableIO(implicit p: Parameters) extends NSBundle {
  // ── 读：查询物理寄存器是否忙 ──
  val readReq  = Input(Vec(CtrlBlockWidth * 2, UInt(PhyRegIdxWidth.W)))  // 各路 rs1+rs2
  val readResp = Output(Vec(CtrlBlockWidth * 2, Bool()))                 // true=忙
 
  // ── 写：分配时置忙 ──
  val allocReq = Input(Vec(CtrlBlockWidth, Valid(UInt(PhyRegIdxWidth.W))))
 
  // ── 写：写回时清忙 ──
  val wbReq    = Input(Vec(WbBusWidth, Valid(UInt(PhyRegIdxWidth.W))))
}
 
// ================================================================
//  ROB 入队 IO
// ================================================================
class RobEnqIO(implicit p: Parameters) extends NSBundle {
  val valid = Vec(CtrlBlockWidth, Bool())
  val validforPreg = Vec(CtrlBlockWidth, Bool())
  val bits  = Vec(CtrlBlockWidth, new RobEntryInner)
  //val canEnq = Output(Bool())  // ROB 是否能容纳本批指令
  //val full = Output(Bool())
}

// ================================================================
//  ROB 表项
// ================================================================
//class enqRobEntry(implicit p: Parameters) extends NSBundle {
//  val pc       = UInt(XLEN.W)
//  val inst     = UInt(XLEN.W)
//  val pdst     = UInt(PhyRegIdxWidth.W)
//  val oldPdst  = UInt(PhyRegIdxWidth.W)
//  val ldst     = UInt(5.W)
//  val rfWen    = Bool()
//  val memRead  = Bool()
//  val memWrite = Bool()
//  val csrWen   = Bool()
//  val csrOp    = UInt(CsrOp.width.W)
//  val csrWaddr = UInt(csrAddrLen.W)
//  val isPriv   = Bool()
//  val excpVec  = UInt(ExceptionCode.width.W)
//  val fuType   = UInt(FuType.width.W)
//  val robIdx   = new RobPtr(RobSize)
//}
 
// ================================================================
//  ROB 提交 IO
// ================================================================

//class RobCommitEntry(implicit p: Parameters) extends NSBundle {
//  val pdst     = UInt(PhyRegIdxWidth.W)
//  val pc     = UInt(XLEN.W)
//  val inst        = UInt(XLEN.W)
//  val rfdata     = UInt(XLEN.W)
//  val oldPdst  = UInt(PhyRegIdxWidth.W)
//  val ldst     = UInt(5.W)
//  val rfWen    = Bool()
//
//  val sqIdx    = new SqPtr(SqSize)
//  val memWrite    = Bool()
//  val memRead     = Bool()
//  val memVaddr    = UInt(XLEN.W)
//  val memPaddr    = UInt(XLEN.W)
//  val storeData   = UInt(XLEN.W)
//
//  val csrWen      = Bool()
//  val csrOp    = UInt(CsrOp.width.W)
//  val csrWaddr    = UInt(csrAddrLen.W)
//  val csrWdata    = UInt(XLEN.W)
//
//  val isPriv      = Bool()
//  val fuType      = UInt(FuType.width.W)
//  val excpVec     = UInt(ExceptionCode.width.W)
//}



 

 
// ================================================================
//  ROB 重定向 IO
// ================================================================
class RobRedirectIO(implicit p: Parameters) extends NSBundle {
  val valid    = Output(Bool())
  val robIdx   = new RobPtr(RobSize)
  val flushSelf = Output(Bool())
  val pc       = Output(UInt(XLEN.W))
  val excp       = new ExceptionBundle
  val isEbreak = Output(Bool())
}
 
// ================================================================
//  ROB 完整 IO
// ================================================================
//class RobIO(implicit p: Parameters) extends NSBundle {
//  val enq     = new RobEnqIO
//  val commit  = new RobCommitIO
//  val redirect = new RobRedirectIO
//  val flush   = Input(Bool())
//  val writeback = Input(Vec(WbBusWidth, Valid(new RobWriteback)))  // 执行单元写回
//}

class RobWriteback(implicit p: Parameters) extends NSBundle {
  val robIdx  = new RobPtr(RobSize)
  val sqIdx  = new SqPtr(SqSize)
  val isMemWrite  = Bool()
  val isMemRead  = Bool()
  val memValid  = Bool()
  val memVaddr  = UInt(XLEN.W)
  val memPaddr  = UInt(XLEN.W)
  val memStoreData  = UInt(XLEN.W)
  val storeValid  = Bool()
  val rfdata  = UInt(XLEN.W)

  val csrWen      = Bool()
  val csrWaddr    = UInt(csrAddrLen.W)
  val csrWdata    = UInt(XLEN.W)
  val csrTimer    = UInt(64.W)

  val tlbFillIdx  = UInt(tlbIdxLen.W)

  val excp       = new ExceptionBundle
  val isBypass = Bool()  // 异常/误预测标记
}
 
// ================================================================
//  Dispatch 级 IO
// ================================================================
// class DispatchStageIO(implicit p: Parameters) extends NSBundle {
//   val in       = Vec(CtrlBlockWidth, Flipped(Decoupled(new RenamedInst)))
//   val q1IQEnq  = Vec(IQEnqPorts.Q1, ValidIO(new DispatchedInst))
//   val q2IQEnq  = Vec(IQEnqPorts.Q2, ValidIO(new DispatchedInst))
//   val q3IQEnq  = Vec(IQEnqPorts.Q3, ValidIO(new DispatchedInst))
//   val q4IQEnq  = Vec(IQEnqPorts.Q4, ValidIO(new DispatchedInst))
//   val q5IQEnq  = Vec(IQEnqPorts.Q5, ValidIO(new DispatchedInst))
//   val iqFeedback = Input(new IssueQueueFeedback)
//   val lsEnq   = new LsEnqIO
//   val robEnq  = Flipped(new RobEnqIO)
//   val flush   = Input(Bool())
//   val redirect = Input(new RedirectInfo)
// }
