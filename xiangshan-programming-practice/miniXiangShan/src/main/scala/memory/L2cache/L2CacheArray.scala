package minixiangshan.mem.L2cache

import chisel3._
import chisel3.util._
import minixiangshan.config._

class L2DataRAMBlackBox(val depth: Int, val width: Int)(implicit p: Parameters)
    extends BlackBox {
  private val addrWidth = log2Ceil(depth)

  override def desiredName: String = s"L2_bram_${depth}x${width}"

  val io = IO(new Bundle {
    val clka = Input(Clock())
    val ena = Input(Bool())
    val wea = Input(Bool())
    val addra = Input(UInt(addrWidth.W))
    val dina = Input(UInt(width.W))

    val clkb = Input(Clock())
    val rstb = Input(Bool())
    val enb = Input(Bool())
    val addrb = Input(UInt(addrWidth.W))
    val doutb = Output(UInt(width.W))
  })
}

class L2MetadataRAMBlackBox(val depth: Int, val width: Int)(implicit p: Parameters)
    extends BlackBox {
  private val addrWidth = log2Ceil(depth)

  override def desiredName: String = s"L2_meta_${depth}x${width}"

  val io = IO(new Bundle {
    val clka = Input(Clock())
    val ena = Input(Bool())
    val wea = Input(Bool())
    val addra = Input(UInt(addrWidth.W))
    val dina = Input(UInt(width.W))

    val clkb = Input(Clock())
    val rstb = Input(Bool())
    val enb = Input(Bool())
    val addrb = Input(UInt(addrWidth.W))
    val doutb = Output(UInt(width.W))
  })
}

// 数据RAM保持原有模块名和Vivado IP接口，总读延迟仍为两拍。
class L2DataRAM(val depth: Int, val width: Int)(implicit p: Parameters)
    extends NSModule {
  private val addrWidth = log2Ceil(depth)

  val io = IO(new Bundle {
    val write = Flipped(Valid(new Bundle {
      val addr = UInt(addrWidth.W)
      val data = UInt(width.W)
    }))
    val read = new Bundle {
      val enable = Input(Bool())
      val addr = Input(UInt(addrWidth.W))
      val data = Output(UInt(width.W))
    }
  })

  if (EnableDifftest) {
    val memory = SyncReadMem(depth, UInt(width.W))
    when(io.write.valid) {
      memory.write(io.write.bits.addr, io.write.bits.data)
    }
    val memoryData = memory.read(io.read.addr, io.read.enable)
    val readEnableD1 = RegNext(io.read.enable, false.B)
    io.read.data := RegEnable(memoryData, 0.U(width.W), readEnableD1)
  } else {
    val memory = Module(new L2DataRAMBlackBox(depth, width))
    memory.io.clka := clock
    memory.io.ena := io.write.valid
    memory.io.wea := io.write.valid
    memory.io.addra := io.write.bits.addr
    memory.io.dina := io.write.bits.data
    memory.io.clkb := clock
    memory.io.rstb := reset.asBool
    memory.io.enb := true.B
    memory.io.addrb := io.read.addr
    io.read.data := memory.io.doutb
  }
}

// metadata仿真使用SyncReadMem+输出寄存器；FPGA使用独立的19-bit SDP IP。
class L2MetadataRAM(val depth: Int, val width: Int)(implicit p: Parameters)
    extends NSModule {
  private val addrWidth = log2Ceil(depth)

  val io = IO(new Bundle {
    val write = Flipped(Valid(new Bundle {
      val addr = UInt(addrWidth.W)
      val data = UInt(width.W)
    }))
    val read = new Bundle {
      val enable = Input(Bool())
      val addr = Input(UInt(addrWidth.W))
      val data = Output(UInt(width.W))
    }
  })

  if (EnableDifftest) {
    val memory = SyncReadMem(depth, UInt(width.W))
    when(io.write.valid) {
      memory.write(io.write.bits.addr, io.write.bits.data)
    }
    val memoryData = memory.read(io.read.addr, io.read.enable)
    val readEnableD1 = RegNext(io.read.enable, false.B)
    io.read.data := RegEnable(memoryData, 0.U(width.W), readEnableD1)
  } else {
    val memory = Module(new L2MetadataRAMBlackBox(depth, width))
    memory.io.clka := clock
    memory.io.ena := io.write.valid
    memory.io.wea := io.write.valid
    memory.io.addra := io.write.bits.addr
    memory.io.dina := io.write.bits.data
    memory.io.clkb := clock
    memory.io.rstb := reset.asBool
    memory.io.enb := true.B
    memory.io.addrb := io.read.addr
    io.read.data := memory.io.doutb
  }
}

class L2CacheArray(implicit p: Parameters) extends NSModule {
  require(l2ReadLatency == 2, "L2CacheArray V1 requires a two-cycle read")
  require(l2Sets == 512 && l2LineBits == 512)
  require(Seq(2, 4, 8, 16).contains(l2Ways))
  private val metadataBits = l2TagBits + 2
  require(metadataBits == 19)

  val io = IO(new L2ArrayIO)

  val dataRams = Seq.fill(l2Ways)(Module(new L2DataRAM(l2Sets, l2LineBits)))
  val metadataRams = Seq.fill(l2Ways)(
    Module(new L2MetadataRAM(l2Sets, metadataBits)))

  // reset后每拍同时清全部way的一个set；512拍内拒绝所有外部请求。
  val initDone = RegInit(false.B)
  val scrubSet = RegInit(0.U(l2IdxBits.W))
  when(!initDone) {
    when(scrubSet === (l2Sets - 1).U) {
      initDone := true.B
    }.otherwise {
      scrubSet := scrubSet + 1.U
    }
  }
  io.initDone := initDone

  val sameSetWrite =
    io.write.valid && io.write.bits.set === io.read.req.bits.set
  io.read.req.ready := initDone && !sameSetWrite
  val readFire = io.read.req.fire

  val readValidD1 = RegNext(readFire, false.B)
  val readValidD2 = RegNext(readValidD1, false.B)
  val readSetD1 = RegEnable(io.read.req.bits.set, 0.U, readFire)
  val readSetD2 = RegEnable(readSetD1, 0.U, readValidD1)

  io.read.resp.valid := readValidD2
  io.read.resp.bits.set := readSetD2

  val writeWayOH = UIntToOH(io.write.bits.way, l2Ways)
  // metadata始终整项写入，避免tag、valid、dirty发生撕裂。
  val normalMetadata = Cat(
    io.write.bits.valid,
    io.write.bits.valid && io.write.bits.dirty,
    io.write.bits.tag
  )

  for (way <- 0 until l2Ways) {
    val wayWrite = initDone && io.write.valid && writeWayOH(way)

    dataRams(way).io.read.enable := readFire
    dataRams(way).io.read.addr := io.read.req.bits.set
    dataRams(way).io.write.valid := wayWrite && io.write.bits.dataWen
    dataRams(way).io.write.bits.addr := io.write.bits.set
    dataRams(way).io.write.bits.data := io.write.bits.data

    metadataRams(way).io.read.enable := readFire
    metadataRams(way).io.read.addr := io.read.req.bits.set
    metadataRams(way).io.write.valid := !initDone || wayWrite
    metadataRams(way).io.write.bits.addr := Mux(initDone, io.write.bits.set, scrubSet)
    metadataRams(way).io.write.bits.data := Mux(initDone, normalMetadata, 0.U)

    val metadataData = metadataRams(way).io.read.data
    val valid = metadataData(metadataBits - 1)
    io.read.resp.bits.ways(way).valid := valid
    io.read.resp.bits.ways(way).dirty := valid && metadataData(metadataBits - 2)
    io.read.resp.bits.ways(way).tag := metadataData(l2TagBits - 1, 0)
    io.read.resp.bits.ways(way).data := dataRams(way).io.read.data
  }
}
