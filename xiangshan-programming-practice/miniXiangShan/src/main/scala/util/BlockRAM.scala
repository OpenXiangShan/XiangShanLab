package minixiangshan.util
 
import chisel3._
import chisel3.util._
import minixiangshan.config._
import minixiangshan.config.Parameters
 
// ===================== BlackBox：FPGA 模式专用 =====================
class SimpleBlockRAMBlackBox(
  val depth: Int,
  val width: Int,
  val readLatency: Int = 1
)(implicit p: Parameters) extends BlackBox with HasBlackBoxResource {
 
  // ★ 关键1：重写 desiredName，把参数编码进模块名
  //    生成 Verilog 时模块名变成 SimpleBlockRAM_256_9 这种，而非 SimpleBlockRAMBlackBox
  override def desiredName: String = s"BlockRAM_${(depth)}x${(width)}"
 
  // ★ 关键2：BlackBox 构造器里不要传 Map("DEPTH" -> depth, ...)
  //    这样 Verilog 里就不会出现 #(.DEPTH(64), .WIDTH(2)) 参数化实例
  val addrWidth = log2Ceil(depth)
  

  //  val clka      = IO(Input(Clock()))
  //  val wea      = IO(Input(Bool()))
  //  val addra      = IO(Input(UInt(addrWidth.W)))
  //  val dina      = IO(Input(UInt(width.W)))
  //  val clkb      = IO(Input(Clock()))
  //  val rstb      = IO(Input(Bool()))
  //  val enb       = IO(Input(Bool()))
  //  val addrb     = IO(Input(UInt(addrWidth.W)))
  //  val doutb      =  IO(Output(UInt(width.W)))

      val io = IO(new Bundle {
    val clka   = Input(Clock())
    val wea    = Input(Bool())
    val ena    = Input(Bool())
    val addra  = Input(UInt(addrWidth.W))
    val dina   = Input(UInt(width.W))

    val clkb   = Input(Clock())
    val rstb   = Input(Bool())
    val enb    = Input(Bool())
    val addrb  = Input(UInt(addrWidth.W))
    val doutb  = Output(UInt(width.W))
  })



    //val rd_valid = Output(Bool())

}
 
// ===================== SimpleBlockRAM：仿真 / FPGA 双模式 =====================
class SimpleBlockRAM(
  val depth: Int = 1024,
  val width: Int = 32,
  val readLatency: Int = 2,
  val initVals: Option[Seq[BigInt]] = None
)(implicit p: Parameters) extends NSModule {   // ← 新增 implicit p
  val addrWidth = log2Ceil(depth)
  //val enableDifftest = p(DebugConfigKeys.EnableDifftest)
 
  val io = IO(new Bundle {
    val wr_en    = Input(Bool())
    val wr_addr  = Input(UInt(addrWidth.W))
    val wr_data  = Input(UInt(width.W))
    val rd_en    = Input(Bool())
    val rd_addr  = Input(UInt(addrWidth.W))
    val rd_data  = Output(UInt(width.W))
    //val rd_valid = Output(Bool())
  })
 
  if (EnableDifftest) {
    // ============ 仿真模式：行为级 RAM ============
    val mem = initVals match {
      case Some(vals) =>
        require(vals.length == depth, "传入的初始化数组长度必须等于RAM深度")
        RegInit(VecInit(vals.map(v => v.U(width.W))))
      case None =>
        RegInit(VecInit(Seq.fill(depth)(0.U(width.W))))
    }
 
    val rdPipeline   = RegInit(VecInit(Seq.fill(readLatency)(false.B)))
    val dataPipeline = RegInit(VecInit(Seq.fill(readLatency)(0.U(width.W))))
 
    when(io.rd_en) {
      dataPipeline(0) := mem(io.rd_addr)
    }
    rdPipeline(0) := io.rd_en
 
    for (i <- 1 until readLatency) {
      dataPipeline(i) := dataPipeline(i-1)
      rdPipeline(i) := dataPipeline(i-1)
      rdPipeline(i) := rdPipeline(i-1)
    }
 
    io.rd_data := dataPipeline(readLatency-1)
    //io.rd_valid := rdPipeline(readLatency-1)
 
    when(io.wr_en) {
      mem(io.wr_addr) := io.wr_data
    }
 
  } else {
    // ============ FPGA 模式：实例化 BlackBox ============
    val blackbox = Module(new SimpleBlockRAMBlackBox(depth, width, 1))

    blackbox.io.clka    := clock
    blackbox.io.wea     := io.wr_en
    blackbox.io.ena     := io.wr_en
    blackbox.io.addra   := io.wr_addr
    blackbox.io.dina    := io.wr_data
    blackbox.io.clkb    := clock
    blackbox.io.rstb    := reset.asBool
    blackbox.io.enb     := io.rd_en
    blackbox.io.addrb   := io.rd_addr
    io.rd_data      := blackbox.io.doutb

    // BRAM IP 读延迟为 1 周期，如果调用方需要更多延迟则额外打拍
    //if (readLatency <= 1) {
    //  io.rd_data  := blackbox.io.rd_data
    //  io.rd_valid := blackbox.io.rd_valid
    //} else {
    //  val extraStages = readLatency - 1
    //  val dataPipe  = RegInit(VecInit(Seq.fill(extraStages)(0.U(width.W))))
    //  val validPipe = RegInit(VecInit(Seq.fill(extraStages)(false.B)))
 //
    //  dataPipe(0)  := blackbox.io.rd_data
    //  validPipe(0) := blackbox.io.rd_valid
    //  for (i <- 1 until extraStages) {
    //    dataPipe(i)  := dataPipe(i-1)
    //    validPipe(i) := validPipe(i-1)
    //  }
    //  io.rd_data  := dataPipe(extraStages - 1)
    //  io.rd_valid := validPipe(extraStages - 1)
    //}
  }
}