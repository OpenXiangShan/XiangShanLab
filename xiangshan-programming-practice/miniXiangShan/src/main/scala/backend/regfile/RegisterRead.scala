package minixiangshan.backend.regread
 
import chisel3._
import chisel3.util._
import minixiangshan.config._
import minixiangshan.backend.dispatch.DispatchedInst
import minixiangshan.backend.rename.RedirectInfo
import minixiangshan.backend.execute._
import minixiangshan.backend.issue._
 
// ═══════════════════════════════════════════════════════════════
//  执行单元请求：datapath → ExeUnit
// ═══════════════════════════════════════════════════════════════
class ExeReq(implicit p: Parameters) extends NSBundle {
  val uop            = new DispatchedInst
  val rs1Data        = UInt(XLEN.W)
  val rs2Data        = UInt(XLEN.W)
  val src1DataSource = UInt(DataSource.width.W)   // ← 新增
  val src2DataSource = UInt(DataSource.width.W)   // ← 新增
  val src1ExeSource  = UInt(log2Ceil(IQNum).W)    // ← 新增
  val src2ExeSource  = UInt(log2Ceil(IQNum).W)    // ← 新增
}
 
// ═══════════════════════════════════════════════════════════════
//  读寄存器级（RegisterRead）—— 单级 datapath 流水线
//
//  5 个通道对应 5 个 IQ：
//    Q1 (ALU+CSR)      : 2 读端口 (prs1, prs2)
//    Q2 (ALU+DIV)      : 2 读端口 (prs1, prs2)
//    Q3 (ALU+MUL+JMP)  : 2 读端口 (prs1, prs2)
//    Q4 (LOAD+STA)     : 1 读端口 (prs1)
//    Q5 (STD)          : 1 读端口 (prs2)
//
//  流水线结构（每通道独立）：
//    datapath 级：锁存 IQ 发来的 uop，PRF 数据就绪后直接输出到执行单元
//
//  总延迟：IQ 发射 → 执行单元可见 = 1 拍
//    T+0: IQ fire → 锁存 uop，发 PRF 地址
//    T+1: PRF 数据返回（同步读）→ datapath 级直接输出到执行单元
//
//  重定向处理：
//    仅使用 redirectInfo 信号，不再设 flushPipeline
//    当 redirectInfo 指示需要冲刷时，杀掉 datapath 级中的指令
//    并阻断其进入后续流水级（exeReq.valid = false）
// ═══════════════════════════════════════════════════════════════
class RegisterRead(implicit p: Parameters) extends NSModule with HasCoreParameters {
 
  // ── 通道配置 ──
  val numChannels       = IQNum
  val readPortsPerChan  = Seq(2, 2, 2, 1, 1)     // 每通道读端口数
  val totalReadPorts    = readPortsPerChan.sum     // 8
  // Q5 的单端口读的是 prs2 而非 prs1
  val singlePortReadsPrs2 = Seq(false, false, false, false, true)
 
  val io = IO(new Bundle {
    // ── 从 IQ 接收 ──
    val iqIssues     = Vec(numChannels, Flipped(Decoupled(new RegReadIssue)))
 
    // ── 连接 PRF ──
    val rfReadAddrs  = Output(Vec(totalReadPorts, UInt(PhyRegIdxWidth.W)))
    val rfReadData   = Input(Vec(totalReadPorts, UInt(XLEN.W)))
 
    // ── 发往执行单元 ──
    val exeReqs      = Vec(numChannels, Decoupled(new ExeReq))
 
    // ── 重定向（唯一刷新信号，不再设 flushPipeline） ──
    val redirectInfo  = Flipped(ValidIO(new redirectInfoToModule))
  })
 
 
  // ══════════════════════════════════════════════════════════════
  //  逐通道构建流水线
  // ══════════════════════════════════════════════════════════════
  var portOffset = 0
 
  for (ch <- 0 until numChannels) {
    val numPorts     = readPortsPerChan(ch)
    val readsPrs2    = singlePortReadsPrs2(ch)
    val basePort     = portOffset
 
    // ──────────────────────────────────────────
    //  datapath 级寄存器：锁存 IQ 发来的 uop
    // ──────────────────────────────────────────
    val dp_valid = RegInit(false.B)
    val dp_uop   = RegInit(0.U.asTypeOf(new RegReadIssue))
 
    // ──────────────────────────────────────────
    //  Kill 检测
    //  当 redirectInfo 有效且指示需要重定向时，
    //  如果 datapath 级中指令的 robIdx 在重定向点之后，则杀掉该指令
    // ──────────────────────────────────────────
    val doRedirect      = io.redirectInfo.valid && io.redirectInfo.bits.doRedirect
    val redirectRobIdx  = io.redirectInfo.bits.robIdx
 
    val dp_killed = dp_valid && doRedirect &&
                    dp_uop.uop.robIdxFull.isAfter(redirectRobIdx)
 
    diffDontTouch(dp_killed)
 
    // ──────────────────────────────────────────
    //  握手控制信号
    // ──────────────────────────────────────────
    // datapath 级输出握手：指令未被杀掉且执行单元就绪时可以发出
    val dp_fire   = dp_valid && !dp_killed && io.exeReqs(ch).ready
    // datapath 级可以接收新指令：空 或 当前指令正在发出
    val dp_ready  = !dp_valid || dp_fire
    // IQ 发射
    val iq_fire   = io.iqIssues(ch).valid && dp_ready
 
    // IQ 握手
    io.iqIssues(ch).ready := dp_ready
 
    // ──────────────────────────────────────────
    //  PRF 读地址
    //  IQ fire 时发新地址；否则维持 dp_uop 的地址
    //  datapath 空时发 0（无害，PRF 地址 0 恒返回 0）
    // ──────────────────────────────────────────
    if (numPorts == 2) {
      io.rfReadAddrs(basePort)     := Mux(iq_fire, io.iqIssues(ch).bits.uop.prs1,
                                      Mux(dp_valid, dp_uop.uop.prs1, 0.U))
      io.rfReadAddrs(basePort + 1) := Mux(iq_fire, io.iqIssues(ch).bits.uop.prs2,
                                      Mux(dp_valid, dp_uop.uop.prs2, 0.U))
    } else {
      // 单端口：Q4 读 prs1，Q5 读 prs2
      val readSrc = Mux(readsPrs2.asBool,
        Mux(iq_fire, io.iqIssues(ch).bits.uop.prs2, Mux(dp_valid, dp_uop.uop.prs2, 0.U)),
        Mux(iq_fire, io.iqIssues(ch).bits.uop.prs1, Mux(dp_valid, dp_uop.uop.prs1, 0.U))
      )
      io.rfReadAddrs(basePort) := readSrc
    }
 
    // ──────────────────────────────────────────
    //  PRF 读数据 + x0 处理 + 未使用源置零
    //  数据直接来自 rfReadData，不再锁存到 out 级寄存器
    // ──────────────────────────────────────────
    val rfRs1 = io.rfReadData(basePort)
    val rfRs2 = if (numPorts == 2) io.rfReadData(basePort + 1) else 0.U
 
    val rs1Data = Mux(!dp_uop.uop.rs1Valid, 0.U,
                  Mux(dp_uop.uop.prs1 === 0.U, 0.U, rfRs1))
    val rs2Data = if (numPorts == 2) {
      Mux(!dp_uop.uop.rs2Valid, 0.U,
      Mux(dp_uop.uop.prs2 === 0.U, 0.U, rfRs2))
    } else {
      // 单端口通道：Q4 不需要 rs2，Q5 不需要 rs1
      Mux(!dp_uop.uop.rs2Valid, 0.U,
      Mux(dp_uop.uop.prs2 === 0.U, 0.U, rfRs1))  // Q5: 单端口数据给 rs2
    }
 
    // ──────────────────────────────────────────
    //  寄存器更新
    //  优先级：kill > 正常流水
    //  当 redirectInfo 杀掉指令时，清空 datapath 并阻断后续输出
    // ──────────────────────────────────────────
    when(dp_killed) {
      // 重定向冲刷：杀掉 datapath 级中的指令
      dp_valid := false.B
    }.elsewhen(iq_fire) {
      // IQ 发射：新指令进入 datapath
      dp_valid := true.B
      dp_uop   := io.iqIssues(ch).bits
    }.elsewhen(dp_fire) {
      // 指令发出到执行单元：datapath 级变空
      dp_valid := false.B
    }
 
    // ──────────────────────────────────────────
    //  输出到执行单元
    //  关键：dp_killed 时 exeReq.valid = false，阻断被杀指令进入后续流水级
    //  数据直接来自 PRF（不经过 out 级锁存），减少 1 拍延迟
    // ──────────────────────────────────────────
    io.exeReqs(ch).valid         := dp_valid && !dp_killed
    io.exeReqs(ch).bits.uop      := dp_uop.uop
    io.exeReqs(ch).bits.rs1Data  := rs1Data
    io.exeReqs(ch).bits.rs2Data  := rs2Data
    io.exeReqs(ch).bits.src1DataSource := dp_uop.src1DataSource
    io.exeReqs(ch).bits.src2DataSource := dp_uop.src2DataSource
    io.exeReqs(ch).bits.src1ExeSource  := dp_uop.src1ExeSource
    io.exeReqs(ch).bits.src2ExeSource  := dp_uop.src2ExeSource
 
    portOffset += numPorts
  }
}