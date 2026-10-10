package minixiangshan.backend.dispatch
 
import chisel3._
import chisel3.util._
import minixiangshan.config._
import minixiangshan.backend.rob._
import minixiangshan.backend.rename._
//import minixiangshan.backend.execute._
 
/**
 * ═══════════════════════════════════════════════════════════════
 * Dispatch→ROB 流水线寄存器（DispatchRobBuffer）
 * ═══════════════════════════════════════════════════════════════
 *
 * 功能：
 * 1. 将分发阶段的 ROB 入队请求打一拍送入 ROB，切断关键路径。
 * 2. 误预测重定向时全刷（所有指令一定比误预测分支更年轻），
 *    pdest 直接归还 FreeList。
 * 3. 异常回滚时暂停，pdest 由 ROB 回滚 FSM 通过 archCommit 归还。
 * 4. 输出 validCount 供重命名级计算 inFlightToRob。
 * ═══════════════════════════════════════════════════════════════
 */
class DispatchRobBuffer(implicit p: Parameters) extends NSModule {
  val io = IO(new Bundle {
    // ── 从 DispatchStage 接收 ──
    val enq = Flipped(new RobEnqIO)
    // ── 向 ROB 发送 ──
    val deq = new RobEnqIO
    val deqDisp2Rob = new RobEnqIO
    // ── Buffer 刷新归还端口（误预测时直接归还FreeList）──
    //val bufferFlushDealloc = Vec(CtrlBlockWidth, Valid(UInt(PhyRegIdxWidth.W)))
    // ── 状态 ──
    val inFlightToRename = Output(UInt(log2Ceil(2 * CtrlBlockWidth + 1).W))
    val empty       = Output(Bool())
    // ── 控制 ──
    val flush       = Input(Bool())   // 误预测重定向全刷
    val pause       = Input(Bool())   // 异常回滚暂停（不向ROB发送）
    //val redirectInfo = Flipped(ValidIO(new redirectInfoToModule))
  })

  io.deqDisp2Rob <> io.enq
 
  // ================================================================
  //  流水级寄存器
  // ================================================================
  val bufValid  = RegInit(VecInit(Seq.fill(CtrlBlockWidth)(false.B)))
  val bufValidForPreg  = RegInit(VecInit(Seq.fill(CtrlBlockWidth)(false.B)))
  val bufData   = RegInit(VecInit(Seq.fill(CtrlBlockWidth)(0.U.asTypeOf(new RobEntryInner))))
 
  
 
  // ── 入队：从 DispatchStage 接收 ──
  // 分发级已经由重命名级保证了ROB容量，所以只要分发级fire了就一定可以写入buffer
  val enqFire = io.enq.valid.asUInt.orR  // 有任何有效指令需要写入
 
  when(io.flush) {
    // 误预测重定向：全刷
    for (i <- 0 until CtrlBlockWidth) {
      bufValid(i) := false.B
      bufValidForPreg(i) := false.B
    }
  }.elsewhen(enqFire) {
    // 正常写入：从分发级接收
    for (i <- 0 until CtrlBlockWidth) {
      bufValid(i) := io.enq.valid(i)
      bufValidForPreg(i) := io.enq.validforPreg(i)
      bufData(i)  := io.enq.bits(i)
    }
  }
 
  // ================================================================
  //  出队：向 ROB 发送
  // ================================================================
  // Buffer中有数据且不在暂停状态时，向ROB发送
  val deqFire = bufValid.asUInt.orR && !io.pause && !io.flush
 
  for (i <- 0 until CtrlBlockWidth) {
    io.deq.valid(i)  := bufValid(i) && deqFire
    //io.deq.valids(i) := bufValid(i)
    io.deq.bits(i)   := bufData(i)
    io.deq.validforPreg(i) := bufValidForPreg(i)
  }
 
  // 出队成功后清空buffer（如果同时有入队，入队优先级更高因为已经在elsewhen中处理）
  when(deqFire && !enqFire) {
    for (i <- 0 until CtrlBlockWidth) {
      bufValid(i) := false.B
      bufValidForPreg(i) := false.B
    }
  }
 
  // ================================================================
  //  validCount 输出（供重命名级计算 inFlightToRob）
  // ================================================================
  val enqValidCount = PopCount(io.enq.valid)
  val bufValidCount = PopCount(bufValid)
  diffDontTouch(enqValidCount)
  diffDontTouch(bufValidCount)
  io.inFlightToRename := enqValidCount +& bufValidCount
  io.empty := !bufValid.asUInt.orR
 
  // ================================================================
  //  误预测刷新时的 pdest 归还端口
  // ================================================================
  // 当flush生效时，buffer中所有有效指令的pdest需要直接归还FreeList
  //for (i <- 0 until CtrlBlockWidth) {
  //  io.bufferFlushDealloc(i).valid := io.flush && bufValid(i) && bufData(i).rfWen && bufData(i).ldst =/= 0.U
  //  io.bufferFlushDealloc(i).bits  := bufData(i).pdst
  //}
 
  // ROB侧的 canEnq/full 信号透传回分发级（实际分发级不再使用canEnq，
  // 但这些信号需要回传给重命名级用于容量计算）
  //io.enq.canEnq := io.deq.canEnq  // 透传ROB的canEnq（重命名级会使用）
  //io.enq.full   := io.deq.full    // 透传ROB的full

}
