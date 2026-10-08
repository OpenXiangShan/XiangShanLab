package minixiangshan.backend.issue
 
import chisel3._
import chisel3.util._
import minixiangshan.config._
import minixiangshan.backend.dispatch._
import minixiangshan.backend.rename._
import minixiangshan.backend.execute._
/**
 * ═══════════════════════════════════════════════════════════════
 *  调度器（Scheduler）
 *
 *  包含 5 个 IssueQueue，统一管理唤醒广播和重定向
 *    Q1: ALU + CSR      (16 项)
 *    Q2: ALU + DIV      (12 项)
 *    Q3: ALU + MUL + JMP(16 项)
 *    Q4: LOAD + STA     (16 项)
 *    Q5: STD            (8  项)
 * ═══════════════════════════════════════════════════════════════
 */
class Scheduler(implicit p: Parameters) extends NSModule {
  val io = IO(new Bundle {
    // ── 从分发阶段入队（每个 IQ 1 个端口） ──
    val q1IQEnq       = Flipped(ValidIO(new DispatchedInst))
    val q2IQEnq       = Flipped(ValidIO(new DispatchedInst))
    val q3IQEnq       = Flipped(ValidIO(new DispatchedInst))
    val q4IQEnq       = Flipped(ValidIO(new DispatchedInst))
    val q5IQEnq       = Flipped(ValidIO(new DispatchedInst))
 
    // ── 发射到读寄存器级（每个 IQ 1 个端口） ──
    val q1Issue       = Decoupled(new RegReadIssue)
    val q2Issue       = Decoupled(new RegReadIssue)
    val q3Issue       = Decoupled(new RegReadIssue)
    val q4Issue       = Decoupled(new RegReadIssue)
    val q5Issue       = Decoupled(new RegReadIssue)
 
    // ── 写回唤醒广播（全局广播到所有 IQ） ──
    val wakeupPorts   = Input(Vec(IQNumWakeupPorts, Valid(new IssueWakeup)))
    // ── 执行阶段唤醒（仅前三个整数执行单元） ──
    val execWakeup    = Input(Vec(3, new WakeupSignal))
 
    // ── 重定向 / 冲刷 ──
    val redirect      = Input(new RedirectInfo)
    val redirectInfo    = Flipped (ValidIO( new redirectInfoToModule ))    // 误预测重定向
    val flushPipeline = Input(Bool())
 
    // ── 反馈给分发阶段 ──
    val feedback      = Output(new IssueQueueFeedback)
  })
 
  // ================================================================
  //  实例化 5 个 IQ
  // ================================================================
  val q1 = Module(new IssueQueue(IQ1Params))
  val q2 = Module(new IssueQueue(IQ2Params))
  val q3 = Module(new IssueQueue(IQ3Params))
  val q4 = Module(new IssueQueue(IQ4Params))
  val q5 = Module(new IssueQueue(IQ5Params))
 
  val allIQs = Seq(q1, q2, q3, q4, q5)
 
  // ================================================================
  //  入队连接
  // ================================================================
  q1.io.enq <> io.q1IQEnq
  q2.io.enq <> io.q2IQEnq
  q3.io.enq <> io.q3IQEnq
  q4.io.enq <> io.q4IQEnq
  q5.io.enq <> io.q5IQEnq
 
  // ================================================================
  //  发射连接
  // ================================================================
  io.q1Issue <> q1.io.issue
  io.q2Issue <> q2.io.issue
  io.q3Issue <> q3.io.issue
  io.q4Issue <> q4.io.issue
  io.q5Issue <> q5.io.issue
 
  // ================================================================
  //  唤醒广播（所有 IQ 接收相同的写回端口）
  // ================================================================
  for (iq <- allIQs) {
    iq.io.wakeupPorts <> io.wakeupPorts
    iq.io.execWakeup := io.execWakeup
  }

  val fastIQs = Seq(q1, q2, q3)
  // ── 快速唤醒收集与广播 ──
  val fastWakeupVec = VecInit(fastIQs.map(_.io.wakeupOut))
  for (iq <- allIQs) {
    iq.io.fastWakeup := fastWakeupVec
  }
 
 
  // ================================================================
  //  重定向 / 冲刷（广播到所有 IQ）
  // ================================================================
  for (iq <- allIQs) {
    iq.io.redirectInfo      <> io.redirectInfo
    iq.io.flushPipeline <> io.flushPipeline
  }
 
  // ================================================================
  //  反馈信号
  // ================================================================
  io.feedback.q1FreeEntries := q1.io.freeEntries
  io.feedback.q2FreeEntries := q2.io.freeEntries
  io.feedback.q3FreeEntries := q3.io.freeEntries
  io.feedback.q4FreeEntries := q4.io.freeEntries
  io.feedback.q5FreeEntries := q5.io.freeEntries
}
