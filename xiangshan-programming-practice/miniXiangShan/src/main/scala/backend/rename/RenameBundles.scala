package minixiangshan.backend.rename
 
import chisel3._
import chisel3.util._
import minixiangshan.config._
import minixiangshan.backend.decode._
import minixiangshan.frontend.{PredecodeInfo, bpuInfoBundle}
import minixiangshan.util.CircularQueuePtr


// ================================================================
//  ROB 指针：复用 CircularQueuePtr，entries 由外部传入
// ================================================================
class RobPtr(robEntries: Int) extends CircularQueuePtr[RobPtr](robEntries)


class SnapshotResolveInfo(implicit p: Parameters) extends NSBundle {
  val snptId       = UInt(log2Ceil(SnapshotNum).W)   // 快照槽位编号
  val isMispredict = Bool()                           // 是否误预测
}


// ================================================================
//  重命名后的指令（RenameStage 输出，流向 Dispatch/ROB）
// ================================================================
class RenamedInst(implicit p: Parameters) extends NSBundle {
  // ── 直传自译码级 ──
  val pc         = UInt(XLEN.W)
  val inst       = UInt(XLEN.W)
  val ctrl       = new DecodeCtrl
  val excp       = new ExceptionBundle
  val imm        = UInt(XLEN.W)
  val csrAddress = UInt(csrAddrLen.W)
  val cacheOp      = new CacheOpDecode
  val pdInfo     = new PredecodeInfo
  val bpuInfo    = new bpuInfoBundle
 
  // ── 逻辑寄存器号（保留，供 ROB 提交时写架构表） ──
  val ldst = UInt(5.W)
  val lrs1 = UInt(5.W)
  val lrs2 = UInt(5.W)
 
  // ── 物理寄存器号 ──
  val pdst      = UInt(PhyRegIdxWidth.W)   // 新分配的物理目的寄存器
  val prs1      = UInt(PhyRegIdxWidth.W)   // 物理源寄存器 1
  val prs2      = UInt(PhyRegIdxWidth.W)   // 物理源寄存器 2
  val oldPdst = UInt(PhyRegIdxWidth.W)   // 被覆盖的旧物理目的寄存器（提交时释放）
  
  val snptId = Valid(UInt(log2Ceil(SnapshotNum).W))
  
  // ── 有效性 ──
  val rs1Valid = Bool()
  val rs2Valid = Bool()
  val rdValid  = Bool()
 
  // ── ROB 索引（环形队列 value 部分，flag 由 ROB 内部维护） ──
  val robIdx = new RobPtr(RobSize)
}
 
// ================================================================
//  ROB 提交信息（ROB → RenameStage，用于释放旧物理寄存器 + 更新架构表）
// ================================================================
class RobCommitInfo(implicit p: Parameters) extends NSBundle {
  val valid     = Bool()
  val ldst      = UInt(5.W)
  val pdst      = UInt(PhyRegIdxWidth.W)   // 新物理寄存器（写入架构表）
  val oldPdst = UInt(PhyRegIdxWidth.W)   // 旧物理寄存器（释放回 FreeList）
  val rfWen     = Bool()                   // 是否写整数寄存器堆
  val isWalk    = Bool()                   // 是否处于误预测回退(walk)模式
}
 
// ================================================================
//  重定向信息
// ================================================================
class RedirectInfo(implicit p: Parameters) extends NSBundle {
  val valid     = Bool()
  val robIdx    = new RobPtr(RobSize)   // 误预测指令的 ROB 索引
  //val flushSelf = Bool()                      // 是否冲刷误预测指令本身
}
 
// ================================================================
//  FreeList IO
// ================================================================
//class FreeListIO(implicit p: Parameters) extends NSBundle {
//  // ── 分配侧 ──
//  val allocReqs   = Input(Vec(CtrlBlockWidth, Bool()))
//  val allocPdest  = Vec(CtrlBlockWidth, Valid(UInt(PhyRegIdxWidth.W)))
//  val canAlloc    = Output(Bool())
//  val doAlloc     = Input(Bool())
// 
//  // ── 释放侧（ROB 提交） ──
//  val deallocReqs = Input(Vec(CommitWidth, Valid(UInt(PhyRegIdxWidth.W))))
// 
//  // ── 分支快照相关 ──
//  val renBrTags   = Input(Vec(CtrlBlockWidth, Valid(UInt(log2Ceil(SnapshotNum).W))))
//  val brMispredict = Input(Bool())
//  val brMispredTag = Input(UInt(log2Ceil(SnapshotNum).W))
// 
//  // ── 全局冲刷 ──
//  val flush       = Input(Bool())
//}
 
// ================================================================
//  RAT 读写端口
// ================================================================
class RatReadPort(implicit p: Parameters) extends NSBundle {
  val addr = Input(UInt(5.W))
  val hold = Input(Bool())
  val data = Output(UInt(PhyRegIdxWidth.W))
}

class RatWritePort(implicit p: Parameters) extends NSBundle {
  val wen  = Bool()
  val addr = UInt(5.W)
  val data = UInt(PhyRegIdxWidth.W)
}
 
// ================================================================
//  RenameStage IO
// ================================================================
//class RenameStageIO(implicit p: Parameters) extends NSBundle {
//  // ── 来自译码级 ──
//  val in      = Vec(CtrlBlockWidth, Flipped(Decoupled(new DecodedInst)))
//  // ── 译码级给出的 RAT 读请求（T0 组合信号，直接接入 RAT） ──
//  val ratRead = Vec(CtrlBlockWidth, Flipped(new RATReadIO))
// 
//  // ── 向 Dispatch 输出 ──
//  val out     = Vec(CtrlBlockWidth, Decoupled(new RenamedInst))
// 
//  // ── ROB 提交回传 ──
//  val commit  = Input(Vec(CommitWidth, new RobCommitInfo))
// 
//  // ── 重定向 ──
//  val redirect = Input(new RedirectInfo)
// 
//  // ── 全局冲刷 ──
//  val flush   = Input(Bool())
//}
