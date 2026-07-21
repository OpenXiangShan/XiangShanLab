import chisel3._
import chisel3.util._

class CacheDualPortBRAM extends Module {
  val io = IO(new Bundle {
    // 写端口
    val wr_en    = Input(Bool())      // 写使能
    val wr_index = Input(UInt(6.W))   // 写索引
    val wr_data  = Input(UInt(64.W))  // 写数据
    
    // 读端口
    val rd_en    = Input(Bool())      // 读使能
    val rd_index = Input(UInt(6.W))   // 读索引
    val rd_data  = Output(UInt(64.W)) // 读数据
  })
  

    // 数据存储器
    val data_mem = SyncReadMem(64, UInt(64.W))
    
    // 写操作
    when(io.wr_en) {
      data_mem.write(io.wr_index, io.wr_data)
    }
    
    // 读操作
    io.rd_data := data_mem.read(io.rd_index, io.rd_en)



  
}
