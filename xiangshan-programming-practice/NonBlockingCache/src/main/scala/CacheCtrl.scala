import chisel3._
import chisel3.util._


class CacheCtrl(
  INDEX_WD: Int,
  TAG_WD: Int,
  OFFSET_WD: Int
) extends Module {

  val io = IO(new Bundle {

    //1.接收来自于CPU端口的请求数据
        
    val req_fromCPU       = Input(Bool())
    val req_addr_fromCPU  = Input(UInt(48.W))
    val rdIdx_fromCPU     = Input(UInt( 5.W))
    val reqNum_fromCPU    = Input(UInt(16.W))
    
    //2.向外层RAM的接口
        //(1).传出
    val req_toMem      = Output(Bool())//OK
    val addr_toMem     = Output(UInt(48.W))//OK
    //val rdIdx_toMem    = Output(UInt(5.W))//OK
    val reqNum_toMem   = Output(UInt(16.W))//OK
        //(2).输入
    val addr_ok_fromMem       = Input(Bool())
    val data_ok_fromMem       = Input(Bool())
    val data_valid_fromMem    = Input(Bool())
    //val addr_back_fromMem     = Input(UInt(48.W))
    //val rdIdx_back_fromMem    = Input(UInt(5.W))
    val reqNum_back_fromMem   = Input(UInt(16.W))
    val rdata_fromMem         = Input(UInt( 64.W))
    
    //3.与其他模块的交互数据
    val hit_fromTag      = Input(UInt( 4.W))
    val miss_fromTag      = Input(Bool())
      //(1)写
      //向data块与tag块出传入写使能的信号
    val replace_cache  = Output(UInt(1.W))
      //写地址
    val refill_addr    = Output(UInt(48.W))
      //向data块出传入新的数据行
    val newCacheline   = Output(UInt(512.W))

      //(2)读 /*------（使能在tag中自己生成其实就是hit信号）------*/
    val reqAddr_toData   = Output(UInt(48.W)) //OK
    val rdata_fromData   = Input(UInt(64.W))  //OK

    //4.Ctrl总控与CPU端的握手信号及具体数据
    val addr_ok_toCPU    = Output(Bool())
    val data_ok_toCPU    = Output(Bool()) //OK
    
    val rdata_toCPU      = Output(UInt(64.W)) //OK
    val rdIdx_toCPU      = Output(UInt(5.W)) //OK
    val reqNum_toCPU     = Output(UInt(16.W)) //OK

    //debug port
    //val dataOK_fromCache_Debug    = Output(Bool())
    //val dataOK_fromMem_Debug    = Output(Bool())
  })

  val mshr =Module(new MSHRModule())
  dontTouch(mshr.io)

  object CacheState extends ChiselEnum {
    val IDLE, CPU_BACK_ADDROK, CPU_BACK_DATA, MSHR_OK, WRITE_MSHR = Value
  }
  import CacheState._

  /*===========================================
    1. 处理Hit的状态机
       处理：对于来自CPU的Hit请求 以及 来自MSHR的已准备好的表项请求
       这俩处理需要在同一状态机，因为他们可能同时发生
  ===========================================*/

  val hitState = RegInit(IDLE)
  switch(hitState) {

    is(IDLE) {
      when( mshr.io.cpu_rsp_valid ) { //来自于MSHR的“Hit”请求
        hitState := MSHR_OK

      }.elsewhen(io.req_fromCPU && io.hit_fromTag.orR) { //来自于CPU的Hit请求
        hitState := CPU_BACK_ADDROK

      }.otherwise {
        hitState := IDLE
      }
    }
    is(MSHR_OK) {
      hitState := IDLE
    }
    is(CPU_BACK_ADDROK) {
      hitState := CPU_BACK_DATA
    }
    is(CPU_BACK_DATA) {
      hitState := IDLE
    }
  }
  io.reqAddr_toData := io.req_addr_fromCPU
  val rdata_fromData_reg     =  RegNext( io.rdata_fromData  )
  val rdIdx_fromCPU_reg     =  RegNext( io.rdIdx_fromCPU  )
  val reqNum_fromCPU_reg    =  RegNext( io.reqNum_fromCPU )

  val dataOK_fromCache = ( hitState === CPU_BACK_DATA )
  val dataOK_fromMSHR  = ( hitState === MSHR_OK )
  mshr.io.cpu_rsp_ready  := ( hitState === MSHR_OK )

  io.rdIdx_toCPU      := Mux(dataOK_fromCache,
                                  rdIdx_fromCPU_reg,  //Hit时数据来源于Cache中的Data
                                  mshr.io.cpu_rsp_idx //mshr表项有准备好的数据时，来源于表项
                            )

  io.reqNum_toCPU     := Mux(dataOK_fromCache,
                                  reqNum_fromCPU_reg,
                                  mshr.io.cpu_rsp_id
                            )

  io.rdata_toCPU      :=  Mux(dataOK_fromCache,
                                  rdata_fromData_reg,
                                  mshr.io.cpu_rsp_data
                            )


  io.data_ok_toCPU    :=  dataOK_fromCache || dataOK_fromMSHR


    /*===========================================
    2. 处理Miss的状态机
       包括：对新进来的Miss请求进行写MSHR操作
  ===========================================*/

  val missState = RegInit(IDLE)
  switch(missState) {
    is(IDLE) {
      //请求发生 请求缺失 mshr可以接收
      when( io.req_fromCPU && io.miss_fromTag && mshr.io.cpu_can_accept ) {
        missState := WRITE_MSHR
      }

    }
    is(WRITE_MSHR) {
      missState := IDLE
    }

  }

  mshr.io.cpu_req_valid := (missState === WRITE_MSHR)
  mshr.io.cpu_req_addr  := io.req_addr_fromCPU
  mshr.io.cpu_req_id    := io.reqNum_fromCPU
  mshr.io.cpu_req_idx   := io.rdIdx_fromCPU

  io.addr_ok_toCPU    :=  ( hitState === CPU_BACK_ADDROK   )  || //HIt时
                          ( missState === WRITE_MSHR   )       //MISS时

  /*===========================================
    3. MSHR向内存的请求接口
  ===========================================*/

  io.req_toMem          := mshr.io.mem_req_valid   //= Output(Bool())          // 内存请求有效
  io.addr_toMem         := mshr.io.mem_req_addr    //= Output(UInt(48.W))      // 内存请求地址
  io.reqNum_toMem       := mshr.io.mem_req_id      //= Output(UInt(16.W))      // 内存请求ID
  mshr.io.mem_req_ready    := io.addr_ok_fromMem   //= Input(Bool())           // 内存请求是否被接受

  /*===========================================
    4. 内存响应到MSHR的接口 主要处理突发传输
  ===========================================*/
  mshr.io.mem_rsp_id     := RegNext(io.reqNum_back_fromMem) //唯一标识：配合突发传输需要延迟一个周期
  //Men输入：
    //  io.data_ok_fromMem            // = Input(Bool())
    //  io.data_valid_fromMem         // = Input(Bool())
    //  io.rdata_fromMem              // = Input(UInt( 64.W))
  //MSHR输入：
    //  mshr.io.mem_rsp_valid  := //= Input(Bool())           // 内存响应有效
    //  mshr.io.mem_rsp_data   := //= Input(UInt(512.W))      // 内存响应数据（512位）

  // 突发传输
  val burst_counter = RegInit(0.U(3.W))
  val data_buffer = Reg(Vec(8, UInt(64.W)))
  val rsp_valid_reg = RegInit(false.B)
    // 当data_valid有效时，接收数据
  when(io.data_valid_fromMem) {
    data_buffer(burst_counter) := io.rdata_fromMem
    // 更新计数器
    when(io.data_ok_fromMem) {
      // 最后一个数据，计数器复位
      burst_counter := 0.U
      rsp_valid_reg := true.B
    }.otherwise {
      // 非最后一个数据，计数器加1
      burst_counter := burst_counter + 1.U
      rsp_valid_reg := false.B
    }
  }.otherwise {
    rsp_valid_reg := false.B
    for (i <- 0 until 8) {
      data_buffer(i) := 0.U
    }
  }
  mshr.io.mem_rsp_valid  := rsp_valid_reg
  mshr.io.mem_rsp_data   := Cat(
                            data_buffer(7), data_buffer(6), data_buffer(5), data_buffer(4),
                            data_buffer(3), data_buffer(2), data_buffer(1), data_buffer(0)
                          )

  /*===========================================
    5. MSHR写CacheData和CacheTag的接口
  ===========================================*/

  io.replace_cache  := mshr.io.cache_wr_valid 
  io.refill_addr    := mshr.io.cache_wr_addr  
  io.newCacheline   := mshr.io.cache_wr_data  

}
