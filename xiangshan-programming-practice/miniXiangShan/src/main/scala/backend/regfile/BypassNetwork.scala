package minixiangshan.backend.bypass
 
import chisel3._
import chisel3.util._
import minixiangshan.config._
import minixiangshan.backend.regread.ExeReq
import minixiangshan.backend.issue.BypassResult
import minixiangshan.backend.issue.DataSource
 
// ════════════════════════════════════════════════════════════════
//  BypassNetwork：纯组合逻辑数据选择器
//
//  位置：RegisterRead → BypassNetwork → ExeUnit（无流水寄存器）
//
//  功能：对每个通道的 rs1Data / rs2Data，
//        根据 dataSource 选择来自 ExeUnit 旁路或 PRF 读数据。
//        当 dataSource = exeUnit 但指定 ExeUnit 端口无有效结果时
//        （冲刷、阻塞等异常），安全回落到 regFile 数据。
//
//  时序：同一周期内完成选择，结果直接送入 ExeUnit Phase1。
//        ExeUnit Phase2 → BypassNetwork → ExeUnit Phase1 寄存器
//        切断组合环路，无环回 Phase2。
// ════════════════════════════════════════════════════════════════
class BypassNetwork(implicit p: Parameters) extends NSModule with HasCoreParameters {
  val io = IO(new Bundle {
    // ── 从 RegisterRead 接收（每通道一个 ExeReq） ──
    val inReqs   = Vec(IQNum, Flipped(Decoupled(new ExeReq)))
 
    // ── 向 ExeUnit 输出（每通道一个 ExeReq，数据已旁路选择） ──
    val outReqs  = Vec(IQNum, Decoupled(new ExeReq))
 
    // ── ExeUnit 旁路结果（每端口一个，组合逻辑输出） ──
    val bypassResults = Input(Vec(IQNum, new BypassResult))
  })
 
  for (ch <- 0 until IQNum) {
    // ── valid / ready 直通，不修改 ──
    io.outReqs(ch).valid := io.inReqs(ch).valid
    io.inReqs(ch).ready  := io.outReqs(ch).ready
 
    // ── uop 直通 ──
    io.outReqs(ch).bits.uop := io.inReqs(ch).bits.uop
 
    // ── dataSource / exeSource 直通（供 ExeUnit 参考或调试） ──
    io.outReqs(ch).bits.src1DataSource := io.inReqs(ch).bits.src1DataSource
    io.outReqs(ch).bits.src2DataSource := io.inReqs(ch).bits.src2DataSource
    io.outReqs(ch).bits.src1ExeSource  := io.inReqs(ch).bits.src1ExeSource
    io.outReqs(ch).bits.src2ExeSource  := io.inReqs(ch).bits.src2ExeSource
 
    // ── PRF 原始数据 ──
    val prfRs1 = io.inReqs(ch).bits.rs1Data
    val prfRs2 = io.inReqs(ch).bits.rs2Data
 
    // ── ExeUnit 旁路数据 ──
    val src1ExeIdx  = io.inReqs(ch).bits.src1ExeSource
    val src2ExeIdx  = io.inReqs(ch).bits.src2ExeSource
    val src1BypassV = io.bypassResults(src1ExeIdx).valid
    val src2BypassV = io.bypassResults(src2ExeIdx).valid
    val src1BypassD = io.bypassResults(src1ExeIdx).data
    val src2BypassD = io.bypassResults(src2ExeIdx).data
 
    // ── 安全校验：pdst 匹配检查 ──
    // 当 dataSource=exeUnit 但旁路端口的 pdst 与指令的 prs 不匹配时
    // （冲刷/重定向导致 ExeUnit 内容变更），回落到 regFile
    val src1PdstMatch = io.bypassResults(src1ExeIdx).pdst === io.inReqs(ch).bits.uop.prs1
    val src2PdstMatch = io.bypassResults(src2ExeIdx).pdst === io.inReqs(ch).bits.uop.prs2
    
    val lastCycleHasBlock = RegInit(false.B)

    val src1UseBypass = io.inReqs(ch).bits.src1DataSource === DataSource.exeUnit &&
                        src1BypassV  && !lastCycleHasBlock
    val src2UseBypass = io.inReqs(ch).bits.src2DataSource === DataSource.exeUnit &&
                        src2BypassV  && !lastCycleHasBlock
    
    //不必考虑被刷时的处理，因为valid的在被刷的那个周期会拉低
    when (io.outReqs(ch).valid && !io.outReqs(ch).ready) {
      lastCycleHasBlock := true.B
    } .otherwise {
      lastCycleHasBlock := false.B
    }

    val src1err = src1UseBypass && !src1PdstMatch
    val src2err = src2UseBypass && !src2PdstMatch
    diffDontTouch(src1err)
    diffDontTouch(src2err)
 
    // ── x0 处理：prs=0 时恒返回 0，无论 dataSource ──
    // （RegisterRead 已经在 PRF 读数据中处理了 x0，但旁路数据也需要处理）
    val src1Final = Mux(io.inReqs(ch).bits.uop.prs1 === 0.U, 0.U,
                    Mux(!io.inReqs(ch).bits.uop.rs1Valid, 0.U,
                    Mux(src1UseBypass, src1BypassD, prfRs1)))
    val src2Final = Mux(io.inReqs(ch).bits.uop.prs2 === 0.U, 0.U,
                    Mux(!io.inReqs(ch).bits.uop.rs2Valid, 0.U,
                    Mux(src2UseBypass, src2BypassD, prfRs2)))
 
    // ── 通道读端口数配置 ──
    val readPortsPerChan = Seq(2, 2, 2, 1, 1)
    val numPorts = readPortsPerChan(ch).asUInt
    val singlePortReadsPrs2 = Seq(false, false, false, false, true)
    val readsPrs2 = singlePortReadsPrs2(ch)
 
    // Q4(LOAD+STA) 只用 rs1，Q5(STD) 只用 rs2
    io.outReqs(ch).bits.rs1Data := Mux(numPorts === 1.U && readsPrs2.B, 0.U, src1Final)
    io.outReqs(ch).bits.rs2Data := Mux(numPorts === 1.U && !readsPrs2.B, 0.U, src2Final)
  }
}