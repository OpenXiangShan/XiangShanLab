package minixiangshan.backend.writeback
 
import chisel3._
import chisel3.util._
import minixiangshan.config._
import minixiangshan.backend.dispatch._
import minixiangshan.backend.execute._
import minixiangshan.backend.issue.IssueWakeup
import minixiangshan.backend.regfile.PRFWritePortIO
import minixiangshan.backend.rename.RedirectInfo
 
// ═══════════════════════════════════════════════════════════════
//  写回级（Writeback Stage）
//
//  功能：
//    1. 接收各执行单元的结果（打一拍存入本级寄存器）
//    2. 广播 wakeup 信号给所有 IQ
//    3. 将结果写入 PRF
//    4. 传递重定向信号
//    5. 传递结果到 ROB 用于提交
//
//  【流水线时序特征】
//    · 独立通道：各个执行单元的结果互相独立，谁到了就存谁，不需等待其他通道。
//    · 无下游反压：写回级的下游是 PRF、ROB、IQ。由于资源在 Rename 阶段
//      已经预留好，写回级本质上是“永远 Ready”的。
// ═══════════════════════════════════════════════════════════════
class Writeback(numExeUnits: Int)(implicit p: Parameters) extends NSModule with HasCoreParameters {
  val io = IO(new Bundle {
    // ── 从各执行单元接收结果 ──
    // 【修改】为了和 ExeUnit 握手，改为 Decoupled 接口
    val InExeResults = Vec(numExeUnits, Flipped(Decoupled(new ExeResult)))
 
    // ── 写 PRF 端口 ──
    val rfWritePorts = Vec(intRegFileWritePorts, Flipped(new PRFWritePortIO))
 
    // ── 唤醒广播给 IQ ──
    val wakeupPorts  = Vec(IQNumWakeupPorts, Valid(new IssueWakeup))
 
    // ── 重定向 ──
    val redirect     = Output(Valid(new RedirectInfo))
 
    // ── 送到 ROB 用于提交 ──
    val toRObResults = Vec(numExeUnits, Valid(new RobWriteback))

    // ── 增加：全局冲刷信号 ──
    val flush        = Input(Bool())
    //    val redirectInfo    = Flipped(ValidIO( new redirectInfoToModule ))    // 误预测重定向


  })
 
  // ================================================================
  //  Phase 1: 流水级寄存器（每个通道独立握手与打拍）
  // ================================================================
  
  // 各通道独立的有效位和数据寄存器
  val stgValid = RegInit(VecInit(Seq.fill(numExeUnits)(false.B)))
  val stgData = RegInit(VecInit(Seq.fill(numExeUnits)(0.U.asTypeOf(new ExeResult))))
  diffDontTouch(stgData)

  //val doRedirect = io.redirectInfo.valid && io.redirectInfo.bits.doRedirect
  //val redirectRobIdx = io.redirectInfo.bits.robIdx


  for (i <- 0 until numExeUnits) {
    // 因为下游是 PRF 和 ROB（不反压），本级数据只要有效，下个周期必定能发走
    val outFire = stgValid(i) 
    
    // 本级永远能接收新数据（要么本级为空，要么本级数据本周期发走）
    val stgReady = !stgValid(i) || outFire // 永远为 true.B
    
    val inFire = io.InExeResults(i).valid && stgReady

    //val inDoFlush = doRedirect &&  inFire &&  io.InExeResults(i).bits.uop.robIdxFull.isAfter(redirectRobIdx)

    // 将 ready 信号反馈给对应的执行单元
    io.InExeResults(i).ready := stgReady

    // 严格状态转移
    when(false.B ){ //inDoFlush) {
      stgValid(i) := false.B
    }.elsewhen(inFire) {
      stgValid(i) := true.B
      stgData(i)  := io.InExeResults(i).bits
    }.elsewhen(outFire) {
      stgValid(i) := false.B
    }
  }

  // ================================================================
  //  Phase 2: 组合逻辑 —— 驱动各模块写端口
  //  注意：全部使用打过一拍的稳定数据 stgData
  // ================================================================
  
  // ── 1. 驱动 PRF 写端口与 IQ 唤醒、ROB 提交 ──
  for (w <- 0 until numExeUnits) {
    val valid = stgValid(w)
    val res   = stgData(w)
    
    // 是否需要写回目的寄存器
    val needWrite = valid && res.uop.ctrl.rfWen && res.uop.rdValid
 
    // PRF 写回
    io.rfWritePorts(w).valid := needWrite
    io.rfWritePorts(w).addr  := res.uop.pdst
    io.rfWritePorts(w).data  := res.data
 
    // 唤醒广播
    io.wakeupPorts(w).valid     := needWrite
    io.wakeupPorts(w).bits.pdst := res.uop.pdst

    // 送到 ROB 提交通知 (带有执行单元的数据和标志)
    io.toRObResults(w).valid := valid
    io.toRObResults(w).bits.excp  := res.uop.excp
    io.toRObResults(w).bits.isBypass  := false.B
    io.toRObResults(w).bits.robIdx   := res.uop.robIdx
    io.toRObResults(w).bits.rfdata   := res.data
    io.toRObResults(w).bits.sqIdx   := res.uop.sqIdx
    io.toRObResults(w).bits.isMemWrite   := res.uop.ctrl.memWrite
    io.toRObResults(w).bits.isMemRead    := res.uop.ctrl.memRead
    io.toRObResults(w).bits.memValid     := res.memValid
    io.toRObResults(w).bits.memVaddr     := res.memVaddr
    io.toRObResults(w).bits.memPaddr     := res.memPaddr
    io.toRObResults(w).bits.memStoreData := res.memStoreData
    io.toRObResults(w).bits.storeValid   := res.storeValid

      io.toRObResults(w).bits.csrWen   :=  res.csrWen 
      io.toRObResults(w).bits.csrWaddr :=res.csrWaddr
      io.toRObResults(w).bits.csrWdata :=res.csrWdata
      io.toRObResults(w).bits.csrTimer :=res.csrTimer
    io.toRObResults(w).bits.tlbFillIdx := res.tlbFillIdx

  }
 
  // 多余的写端口暂置无效
  for (w <- numExeUnits until intRegFileWritePorts) {
    io.rfWritePorts(w).valid := false.B
    io.rfWritePorts(w).addr  := 0.U
    io.rfWritePorts(w).data  := 0.U
  }
  
  // 多余的唤醒端口暂置无效
  for (w <- numExeUnits until IQNumWakeupPorts) {
    io.wakeupPorts(w).valid     := false.B
    io.wakeupPorts(w).bits.pdst := 0.U
  }
 
  // ================================================================
  //  Phase 3: 重定向信号处理
  // ================================================================
  // 提取所有有效的重定向请求：只有该通道数据有效，并且带有的 redirect.valid 为真时才生效
  val redirectCandidates = (0 until numExeUnits).map { i =>
    val r = stgData(i).redirect
    (stgValid(i) && r.valid, r.bits)
  }
  
  val anyRedirect = redirectCandidates.map(_._1).reduce(_ || _)
 
  io.redirect.valid := anyRedirect
  // 优先选择 index 靠前的重定向（也可以根据你的设计，把 robIdx 加入判断，选择最老的指令）
  io.redirect.bits  := PriorityMux(redirectCandidates)

}
