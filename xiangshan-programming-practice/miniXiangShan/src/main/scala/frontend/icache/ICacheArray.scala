package minixiangshan.frontend.icache

import chisel3._
import chisel3.util._
import minixiangshan.config._
import minixiangshan.util._
import minixiangshan.config.Parameters
import minixiangshan.config.NSModule
import minixiangshan.config.NSBundle
// 内部存储单元定义
class MetaEntry(implicit p: Parameters) extends NSBundle {
  val valid = Bool()
  val tag   = UInt(tagBitsI.W)
  
  def toUInt: UInt = Cat(valid, tag)
  def fromUInt(value: UInt): MetaEntry = {
    val result = Wire(new MetaEntry)
    result.valid := value(tagBitsI)
    result.tag   := value(tagBitsI-1, 0)
    result
  }
}


// 集成阵列模块 - 直接使用 SimpleBlockRAM
class ICacheArray(implicit p: Parameters) extends NSModule {

  val dataBits: Int = blockBytes * 8
  val metaWidth: Int = tagBitsI // + 1  // 1位valid + tagBits位标签
  
  val io = IO(new Bundle {
    // 读取端口
    val read = Flipped(new ICacheArrayRead)
    val write = Flipped(new ICacheArrayWrite)


    // Flush端口
    val flush = new Bundle {
      val valid = Input(Bool())
      val idx   = Input(UInt(idxBitsI.W))
    }
    val invalidate = Input(Bool())
  })
  
  // === 创建 BlockRAM 阵列 ===
  // 每个 way 有自己的 meta 和 data BlockRAM
  // === valid 位用寄存器存储，复位时自动清零 ===
  val validArray = RegInit(VecInit(Seq.fill(nWaysI)(0.U(nSetsI.W))))

  val metaBRAMs = VecInit(Seq.fill(nWaysI)(
    Module(new SimpleBlockRAM(
      depth = nSetsI,
      width = metaWidth,
      readLatency = 1
    )).io
  ))

  
  val dataBRAMs = VecInit(Seq.fill(nWaysI)(
    Module(new SimpleBlockRAM(
      depth = nSetsI,
      width = dataBits,
      readLatency = 1
    )).io
  ))
  
  // === 读取逻辑 ===
  // 连接所有 BlockRAM 的读取地址
  for (way <- 0 until nWaysI) {
    metaBRAMs(way).rd_en   := io.read.req.valid
    metaBRAMs(way).rd_addr := io.read.req.idx
    dataBRAMs(way).rd_en   := io.read.req.valid
    dataBRAMs(way).rd_addr := io.read.req.idx
  }

  // 读取响应 - 使用 BlockRAM 的输出
  // 注意：BlockRAM 的 rd_valid 信号在读取使能后的第2个周期变高
  val readRespValid = WireDefault(false.B)
  val readRespData = Wire(new arrayReadData)

  //val readIdxReg = RegEnable(io.read.req.idx, io.read.req.valid)
  val readIdxReg = RegEnable(io.read.req.idx, 0.U(idxBitsI.W), io.read.req.valid)
  // 组合 BlockRAM 的输出
  for (way <- 0 until nWaysI) {
    // 从 BlockRAM 输出转换为数据格式
    val metaUInt = metaBRAMs(way).rd_data
    val dataUInt = dataBRAMs(way).rd_data
    
    //readRespData.cacheLine(way).has  := metaUInt(tagBits)
    readRespData.cacheLine(way).has  := validArray(way)(readIdxReg)  // 从寄存器读 valid

    readRespData.cacheLine(way).tag  := metaUInt(tagBitsI-1, 0)
    readRespData.cacheLine(way).data := dataUInt
  }
  
  // 使用任意一个 way 的 rd_valid 作为整体有效信号（所有 way 同时读取）
  val anyRdValid = //metaBRAMs(0).rd_valid && dataBRAMs(0).rd_valid
                    RegNext(io.read.req.valid)
  
  io.read.resp.valid := anyRdValid
  io.read.resp.data  := readRespData
  
  // === 写入逻辑 ===
  // 写使能解码
  val writeWayOneHot = UIntToOH(io.write.way)
  diffDontTouch(writeWayOneHot)
  
  for (way <- 0 until nWaysI) {
    val waySel = writeWayOneHot(way)
    diffDontTouch(waySel)
    
    // 标签写入：构造 meta 数据 (valid + tag)
    // val metaWriteData = Cat(true.B, io.write.tag)  // 写入时总是设置 valid = true
    
    metaBRAMs(way).wr_en   := io.write.valid && waySel
    metaBRAMs(way).wr_addr := io.write.idx
    metaBRAMs(way).wr_data := io.write.tag //metaWriteData

    when(io.write.valid && waySel) {
      validArray(way) := validArray(way).bitSet(io.write.idx, true.B)
    }
    
    // 数据写入
    dataBRAMs(way).wr_en   := io.write.valid && waySel
    dataBRAMs(way).wr_addr := io.write.idx
    dataBRAMs(way).wr_data := io.write.data
    
    // 调试输出
  //  when(io.write.valid && waySel) {
  //    printf(p"[ICache Write] idx=${io.write.idx}, way=$way, " +
  //           p"tag=0x${Hexadecimal(io.write.tag)}, " +
  //           p"data=0x${Hexadecimal(io.write.data)}\n")
  //  }
  }
  
  // === Flush逻辑 ===
  // flush 时清除所有 way 的指定地址
  for (way <- 0 until nWaysI) {
    // flush 时写入 meta 为 0 (valid = false, tag = 0)
    when(io.flush.valid){
      validArray(way) := validArray(way).bitSet(io.flush.idx, false.B)

      metaBRAMs(way).wr_en   := io.flush.valid
      metaBRAMs(way).wr_addr := io.flush.idx
      metaBRAMs(way).wr_data := 0.U

      // flush 时写入 data 为 0
      dataBRAMs(way).wr_en   := io.flush.valid
      dataBRAMs(way).wr_addr := io.flush.idx
      dataBRAMs(way).wr_data := 0.U
    }

  }

  // IBAR Action
  when(io.invalidate) {
    for (way <- 0 until nWaysI) {
      validArray(way) := 0.U
    }
  }
  
//  when(io.flush.valid) {
//    printf(p"[ICache Flush] idx=${io.flush.idx}\n")
//  }
//  
  // === 冲突处理（可选）===
  // 如果读写同时访问同一地址，需要处理冲突
  // 这里使用简单的优先级：写优先
  val readWriteConflict = Wire(Vec(nWaysI, Bool()))
  for (way <- 0 until nWaysI) {
    val waySel = if (way < nWaysI) writeWayOneHot(way) else false.B
    readWriteConflict(way) := io.read.req.valid && io.write.valid && 
                              (io.read.req.idx === io.write.idx) && waySel
  }
  
  // 如果有冲突，可以暂停读取（这里只打印警告）
//  when(readWriteConflict.reduce(_ || _)) {
//    printf(p"[ICache Conflict] Read-Write conflict at idx=${io.read.req.idx}\n")
//  }
//  
//  println("ICacheIntegratedArray instantiated with SimpleBlockRAM:")
//  println(s"  Sets: $nSets, Ways: $nWaysI")
//  println(s"  Tag Bits: $tagBits, Data Bits: $dataBits")
//  println(s"  Meta BRAM Width: $metaWidth bits, Data BRAM Width: $dataBits bits")
//  println(s"  Read Latency: 2 cycles (BlockRAM default)")
//  println(s"  Write Latency: 1 cycle")
}
