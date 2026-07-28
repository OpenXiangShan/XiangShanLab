// mytop.scala
package mytop

import chisel3._
import device._
import chisel3.util._
import org.chipsalliance.cde.config.Parameters
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.amba.axi4._
import freechips.rocketchip.util._
import _root_.circt.stage.ChiselStage
import chisel3.experimental.BundleLiterals.AddBundleLiteralConstructor
import chisel3.Data.DataEquality

class TwoToOneXbarSystem(implicit p: Parameters) extends LazyModule {
  val dmac = LazyModule(new AXI4DMAC(Seq(AddressSet(0x40000000L, 0xfff))))
  val dummyMaster = AXI4MasterNode(Seq(AXI4MasterPortParameters(
    Seq(AXI4MasterParameters(
      name = "dummy",
      id = IdRange(0, 1)
    ))
  )))
  dmac.node := dummyMaster
  
  val dcache = LazyModule(new AXI4DCache(AXI4MasterParameters(
    name = "dcache_master",
    id = IdRange(0, 256),
    aligned = true
  )))
  
  val memory = LazyModule(new AXI4Memory(
    address = Seq(AddressSet(0x80000000L, 0x0fffffffL)),
    size = 0x10000000L,
    executable = true,
    beatBytes = 8
  ))
  
  val xbar = AXI4Xbar()
  
  xbar := dmac.masterNode
  xbar := dcache.node    
  memory.node := xbar     
  
  lazy val module = new TwoToOneXbarSystemModule(this)
}

class TwoToOneXbarSystemModule(outer: TwoToOneXbarSystem) extends LazyModuleImp(outer) {
  val io = IO(new Bundle {
    val dma_cfg_wen = Input(Bool())
    val dma_cfg_addr = Input(UInt(12.W))
    val dma_cfg_wdata = Input(UInt(64.W))
    val dma_cfg_ren = Input(Bool())
    val dma_cfg_rdata = Output(UInt(64.W))
    val dma_start = Input(Bool())
    val dma_src_addr = Input(UInt(64.W))
    val dma_dst_addr = Input(UInt(64.W))
    
    val system_reset = Input(Bool())
    
    // 调试信号
    val dma_status_busy = Output(Bool())
    val dma_status_done = Output(Bool())
    val cycle_counter = Output(UInt(32.W))
  })
  
  // 内部寄存器
  val cycle_counter = RegInit(0.U(64.W))
  cycle_counter := cycle_counter + 1.U
  io.cycle_counter := cycle_counter(31, 0)
  
  val (dummy_bundle, _) = outer.dummyMaster.out.head
  
  // 不发起任何请求
  dummy_bundle.aw.valid := false.B
  dummy_bundle.w.valid := false.B
  dummy_bundle.ar.valid := false.B
  dummy_bundle.r.ready := true.B
  dummy_bundle.b.ready := true.B
  
  // DMA配置接口占位符
  io.dma_cfg_rdata := 0.U
  
  // DMA状态（简化版本）
  io.dma_status_busy := false.B
  io.dma_status_done := false.B
  
  // 当DMA启动时，设置繁忙标志
  when(io.dma_start) {
    io.dma_status_busy := true.B
  }
  
  // 调试输出
  when(cycle_counter % 1000.U === 0.U) {
    printf(p"[TwoToOneXbarSystem-Cycle ${cycle_counter}] System running\n")
  }
}

import java.io._
import java.nio.file._

object TOP extends App {
  println("Generating 2-to-1 Xbar system...")
  println("=" * 60)
  println("System Architecture:")
  println("  DMAC   -> AXI4Xbar -> Memory")
  println("  DCache -> AXI4Xbar -> Memory")
  println("=" * 60)
  
  implicit val p: Parameters = Parameters.empty
  val system = LazyModule(new TwoToOneXbarSystem)
  
  // 生成Verilog代码
  val verilog = ChiselStage.emitSystemVerilog(
    system.module,
    firtoolOpts = Array("-disable-all-randomization", "-strip-debug-info","--split-verilog", "-o=./build/rtl")
  )
  
//  // 保存到文件
//  val outputDir = "./build/rtl"
//  val outputFile = s"$outputDir/TwoToOneXbarSystem.sv"
//  
//  // 确保目录存在
//  Files.createDirectories(Paths.get(outputDir))
//  
//  // 写入文件
//  val pw = new PrintWriter(new File(outputFile))
//  pw.write(verilog)
//  pw.close()
//  
//  println(s"\nVerilog code has been saved to: $outputFile")
//  println(s"File size: ${verilog.length} characters")
//  println("=" * 60)


}