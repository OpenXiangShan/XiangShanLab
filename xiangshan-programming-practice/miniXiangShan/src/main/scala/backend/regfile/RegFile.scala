package minixiangshan.backend.regfile
 
import chisel3._
import chisel3.util._
import minixiangshan.config._
 
// ═══════════════════════════════════════════════════════════════
//  物理寄存器堆读端口
// ═══════════════════════════════════════════════════════════════
class PRFReadPortIO(implicit p: Parameters) extends NSBundle {
  val addr = Input(UInt(PhyRegIdxWidth.W))
  val data = Output(UInt(XLEN.W))
}
 
// ═══════════════════════════════════════════════════════════════
//  物理寄存器堆写端口
// ═══════════════════════════════════════════════════════════════
class PRFWritePortIO(implicit p: Parameters) extends NSBundle {
  val valid = Input(Bool())
  val addr  = Input(UInt(PhyRegIdxWidth.W))
  val data  = Input(UInt(XLEN.W))
}
 
// ═══════════════════════════════════════════════════════════════
//  物理寄存器堆（PRF）
//
//  同步读：地址在时钟沿注册，数据下一拍组合逻辑输出
//  写优先：同拍写后读，读出的是新写入的值
//  x0 处理：地址 0 恒返回 0
// ═══════════════════════════════════════════════════════════════
class RegFile(implicit p: Parameters) extends NSModule with HasCoreParameters {
  val io = IO(new Bundle {
    val readPorts  = Vec(intRegFileReadPorts, new PRFReadPortIO)
    val writePorts = Vec(intRegFileWritePorts, new PRFWritePortIO)
  })
  val difftest = if (EnableDifftest) Some(IO(Output(Vec(IntPhyRegs, UInt(XLEN.W))))) else None
 
//  val regfile = Mem(IntPhyRegs, UInt(XLEN.W))
//  for (i <- 0 until IntPhyRegs) {
//    dontTouch(regfile(i))  // 保持 regfile 可见性，便于后续添加调试功能
//  }

  //val tableInit = VecInit.tabulate(IntPhyRegs)(_.U(XLEN.W))
  val tableInit = VecInit(Seq.fill(IntPhyRegs)(0.U(XLEN.W)))
 
  val regfile     = RegInit(tableInit)
  if (EnableDifftest) {
    difftest.get := regfile
  }
  // ── 同步读：注册读地址 ──
  val readAddrs = io.readPorts.map(p => RegNext(p.addr, 0.U))
 
  // ── 组合逻辑读出 ──
  val readDataRaw = Wire(Vec(intRegFileReadPorts, UInt(XLEN.W)))
  for (i <- 0 until intRegFileReadPorts) {
    readDataRaw(i) := regfile(readAddrs(i))
  }
 
  // ── 写端口：同拍写后读旁路 ──
  // 如果某个写端口在本拍写入了 readAddrs(i) 对应的地址，
  // 则读出的数据应为新写入的值
  for (i <- 0 until intRegFileReadPorts) {
    val bypassMatches = io.writePorts.map(w =>
      w.valid && w.addr === readAddrs(i) && w.addr =/= 0.U
    )
    val bypassData = Mux1H(VecInit(bypassMatches), VecInit(io.writePorts.map(_.data)))
    val bypassHit = bypassMatches.reduce(_ || _)
 
    // x0 恒返回 0，旁路优先级高于 SRAM 读出
    io.readPorts(i).data := Mux(readAddrs(i) === 0.U, 0.U,
                             Mux(bypassHit, bypassData, readDataRaw(i)))
  }
 
  // ── 写端口写入 ──
  for (wport <- io.writePorts) {
    when(wport.valid && wport.addr =/= 0.U) {
      regfile(wport.addr) := wport.data
    }
  }
}
