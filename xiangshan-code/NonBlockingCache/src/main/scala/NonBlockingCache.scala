import chisel3._
import chisel3.util._
//import chisel3.experimental.dontTouch
import chisel3.dontTouch

class CacheIO extends Bundle {

  // ---------------- CPU side (SRAM like)
  val req          = Input(Bool())
  val addr         = Input(UInt(48.W))
  val rd_idx_in    = Input(UInt(5.W)) //回写寄存器地址
  val sendReq_in   = Input(UInt(16.W))//请求编号（由CPU生成的唯一标识）

  val addr_ok      = Output(Bool()) //地址握手信号
  val data_ok      = Output(Bool()) //数据握手信号
  val rdata        = Output(UInt(64.W))//读取的数据
  val rd_idx_out   = Output(UInt(5.W)) //回写寄存器的地址
  val sendReq_out  = Output(UInt(16.W))//返回的唯一编号

  // ---------------- Memory side
  val mem_req           = Output(Bool())
  val mem_addr          = Output(UInt(48.W))
  //val mem_rdaddr        = Output(UInt(5.W))
  val mem_sendReq_out   = Output(UInt(16.W)) //唯一编号
  
  val mem_addr_ok      = Input(Bool())
  val mem_data_valid   = Input(Bool()) //突发传输时数据有效的指示信号
  val mem_data_ok      = Input(Bool()) //突发结束后（最后一个周期）时的数据握手信号
  //val mem_addr_back    = Input(UInt(48.W))
  //val mem_rdaddr_back  = Input(UInt(5.W))
  val mem_sendReq_back = Input(UInt(16.W)) //乱序返回的请求的唯一编号
  val mem_rdata        = Input(UInt(64.W)) //64bits数据接口
  //val mem_rdata_512        = Input(UInt( (64*8).W))

    //debug port
  val counter   = Output(UInt(16.W))
  //val dataOK_fromCache_Debug    = Output(Bool())
  //val dataOK_fromMem_Debug    = Output(Bool())
}

class NonBlockingCache(
  INDEX_WD: Int, // 6 bits
  TAG_WD: Int,   //36 bits
  OFFSET_WD: Int // 6 bits
) extends Module {

  val io = IO(new CacheIO)
  val clkSignal = !clock.asBool
  withClock( clkSignal.asClock ){ //为什么要把clk取反？因为我不知道为什么外层仿真对模块的激励信号都是在下降沿变化
    //调试计数器                   //找了一天都没找到为什么会这样，又怎么改。为了让仿真正常运行只能出此下策
    val counter = RegInit(0.U(16.W))
    counter := counter + 1.U
    io.counter := counter

    val req_fromCPU         = (io.req)
    val addr_fromCPU        = (io.addr)
    val rdIdx_fromCPU       = (io.rd_idx_in)
    val reqNum_fromCPU      = (io.sendReq_in)

    val addr_ok_fromMem       = (io.mem_addr_ok     )
    val data_ok_fromMem       = (io.mem_data_ok     )
    val data_valid_fromMem       = (io.mem_data_valid     )
    // val addr_back_fromMem     = (io.mem_addr_back   )
    // val rdaddr_back_fromMem   = (io.mem_rdaddr_back )
    val sendReq_back_fromMem  = (io.mem_sendReq_back)
    val rdata_fromMem         = (io.mem_rdata       )

    //TAG模块
    val cache_tag =Module(new CacheTag(INDEX_WD, TAG_WD, OFFSET_WD))
    dontTouch(cache_tag.io)
    //控制模块
    val cache_ctrl =Module(new CacheCtrl(INDEX_WD, TAG_WD, OFFSET_WD))
    dontTouch(cache_ctrl.io)
    //数据存储模块
    val cache_data =Module(new CacheData(INDEX_WD, TAG_WD, OFFSET_WD))
    dontTouch(cache_data.io)

    //io.dataOK_fromCache_Debug   := cache_ctrl.io.dataOK_fromCache_Debug
    //io.dataOK_fromMem_Debug     := cache_ctrl.io.dataOK_fromMem_Debug
    
    //下面所有内容全部都是连线了
    cache_ctrl.io.req_fromCPU       := req_fromCPU
    cache_ctrl.io.hit_fromTag       := cache_tag.io.hit
    cache_ctrl.io.miss_fromTag      := cache_tag.io.miss
    cache_ctrl.io.req_addr_fromCPU  := addr_fromCPU
    cache_ctrl.io.rdIdx_fromCPU     := rdIdx_fromCPU
    cache_ctrl.io.reqNum_fromCPU    := reqNum_fromCPU


    io.mem_req         := cache_ctrl.io.req_toMem      
    io.mem_addr        := cache_ctrl.io.addr_toMem     
    // io.mem_rdaddr      := cache_ctrl.io.rdIdx_toMem    
    io.mem_sendReq_out := cache_ctrl.io.reqNum_toMem   

    cache_ctrl.io.addr_ok_fromMem       := addr_ok_fromMem     
    cache_ctrl.io.data_ok_fromMem       := data_ok_fromMem     
    cache_ctrl.io.data_valid_fromMem    := data_valid_fromMem     
    //cache_ctrl.io.addr_back_fromMem     := addr_back_fromMem   
    //cache_ctrl.io.rdIdx_back_fromMem    := rdaddr_back_fromMem 
    cache_ctrl.io.reqNum_back_fromMem   := sendReq_back_fromMem
    cache_ctrl.io.rdata_fromMem         := rdata_fromMem       

    cache_tag.io.replace_cache := cache_ctrl.io.replace_cache
    cache_tag.io.replace_addr  := cache_ctrl.io.refill_addr  

    io.addr_ok := cache_ctrl.io.addr_ok_toCPU
    io.data_ok := cache_ctrl.io.data_ok_toCPU

    cache_ctrl.io.rdata_fromData := cache_data.io.rdata_toCtrl

    io.rdata       := cache_ctrl.io.rdata_toCPU 
    io.rd_idx_out  := cache_ctrl.io.rdIdx_toCPU 
    io.sendReq_out := cache_ctrl.io.reqNum_toCPU

    cache_tag.io.req      := req_fromCPU
    cache_tag.io.req_addr := addr_fromCPU

    //Data's Input
    cache_data.io.req                   := req_fromCPU
    cache_data.io.replace_fromCtrl      := cache_ctrl.io.replace_cache
    cache_data.io.refillAddr_fromCtrl   := cache_ctrl.io.refill_addr
    cache_data.io.newCacheline_fromCtrl := cache_ctrl.io.newCacheline 
    cache_data.io.hit_fromTag           := cache_tag.io.hit
    cache_data.io.reqAddr_fromCtrl      := cache_ctrl.io.reqAddr_toData
    cache_data.io.pointer_fromTag       := cache_tag.io.replace_pointer
  
  }
}

object NonBlockingCache extends App {
  emitVerilog(
    new NonBlockingCache(6,36,6),
    Array("--target-dir","generated")
  )
}