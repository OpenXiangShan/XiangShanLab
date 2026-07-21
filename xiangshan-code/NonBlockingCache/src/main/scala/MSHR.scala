import chisel3._
import chisel3.util._

/**
  * MSHR模块 - 非阻塞Cache的核心控制器
  * 支持最多4个非重合的未完成的内存请求 8个部分重合的内存请求 （mshrtable：4项 loadtable：8项）
  * 只支持读操作，不支持写操作
  */
class MSHRModule extends Module {
  val io = IO(new Bundle {
    /*----- 来自CPU的请求接口 -----*/
    val cpu_req_valid  = Input(Bool())           // CPU请求有效
    val cpu_req_addr   = Input(UInt(48.W))       // CPU请求地址
    val cpu_req_id     = Input(UInt(16.W))       // CPU请求ID（唯一标识）
    val cpu_req_idx    = Input(UInt(5.W))        //写回寄存器
    val cpu_can_accept = Output(Bool())          // 能否接受新的miss请求
    
    /*----- 内存请求接口 -----*/
    val mem_req_valid = Output(Bool())          // 内存请求有效
    val mem_req_addr  = Output(UInt(48.W))      // 内存请求地址
    val mem_req_id    = Output(UInt(16.W))      // 内存请求ID
    val mem_req_ready = Input(Bool())           // 内存请求是否被接受
    
    /*----- 内存响应接口 -----*/
    val mem_rsp_valid = Input(Bool())           // 内存响应有效
    val mem_rsp_data  = Input(UInt(512.W))      // 内存响应数据（512位）
    val mem_rsp_id    = Input(UInt(16.W))       // 内存响应ID
    
    /*----- Cache写接口 -----*/
    val cache_wr_valid = Output(Bool())         // Cache写使能
    val cache_wr_addr  = Output(UInt(48.W))     // Cache写地址
    val cache_wr_data  = Output(UInt(512.W))    // Cache写数据
    
    /*----- 返回CPU的数据接口 -----*/
    val cpu_rsp_valid = Output(Bool())          // CPU响应有效
    val cpu_rsp_data  = Output(UInt(64.W))      // CPU响应数据（64位）
    val cpu_rsp_id    = Output(UInt(16.W))      // CPU响应ID
    val cpu_rsp_idx    = Output(UInt(5.W))      // CPU响应ID
    val cpu_rsp_ready = Input(Bool())           // CPU响应是否被接受
    
    /*----- 调试接口 -----*/
    val debug_mshr_full  = Output(Bool())       // MSHR是否已满
    val debug_mshr_empty = Output(Bool())       // MSHR是否为空

    val debug_load_full  = Output(Bool())       // MSHR是否已满
    val debug_load_empty = Output(Bool())       // MSHR是否为空
  })
  
  /*===========================================
    1. MSHR表项定义
  ===========================================*/
  val MSHR_ENTRIES = 4
  val MSHR_ID_WIDTH = 2  // 4个entry需要2位
  
  // MSHR表项数据结构
  class MSHREntry extends Bundle {
    val valid         = Bool()               // 【该表项是否有请求（是否空余）】
    val issued        = Bool()               // 【该请求是否已经被发出】
    val data_valid    = Bool()               // 【该请求是否已接收到数据】
    val written_cache = Bool()               // 【该表项的数据是否已被写入Cache】
    val addr          = UInt(48.W)           // 【该表项请求的地址】
    val data          = UInt(512.W)          // 【该请求的具体数据】
    val req_id        = UInt(16.W)           // 【该请求的编号】
  }
  
  // MSHR寄存器数组
  val mshr_entries = RegInit(VecInit(Seq.fill(MSHR_ENTRIES){
    val entry = Wire(new MSHREntry)
    entry.valid := false.B
    entry.issued := false.B
    entry.data_valid := false.B
    entry.written_cache := false.B
    entry.addr := 0.U
    entry.data := 0.U
    entry.req_id := 0.U
    entry
  }))
  
  /*===========================================
    2. Load表项定义
  ===========================================*/
  val LOAD_ENTRIES = 8
  val LOAD_ID_WIDTH = 3
  
  // Load表项数据结构
  class LoadEntry extends Bundle {
    val valid         = Bool()                // 【该表项是否还有load请求（是否空余）】
    val mshr_id       = UInt(MSHR_ID_WIDTH.W) // 【指示该load请求被包括在哪个MSHR表项中】
    val req_id        = UInt(16.W)            // 【该load请求的唯一标识】：便于从Mem回来的数据找他
    val req_idx        = UInt(5.W)            // 【回写寄存器】
    val data          = UInt(64.W)            // 【该load请求的数据】
    val data_valid    = Bool()                // 【该请求是否已接收到数据】
    val offset        = UInt(3.W)             // 【块内偏移】
    val returned_cpu  = Bool()                // 【该表项的数据是否已成功返还给CPU】
  }
  
  // Load寄存器数组
  val load_entries = RegInit(VecInit(Seq.fill(LOAD_ENTRIES){
    val entry = Wire(new LoadEntry)
    entry.valid := false.B
    entry.mshr_id := 0.U
    entry.req_id := 0.U
    entry.req_idx := 0.U
    entry.data := 0.U
    entry.data_valid := false.B
    entry.offset := 0.U
    entry.returned_cpu := false.B
    entry
  }))
  
  /*===========================================
    3. 状态信号 做出状态信息向外传或者后面使用
  ===========================================*/
  // MSHR状态
  val mshr_valid_vec = mshr_entries.map(_.valid) //将mshr table的valid字段提取出来 该表项空不空
  val mshr_issued_vec = mshr_entries.map(_.issued) //将mshr table的issued字段提取出来 该表项请求被发出去没有
  val mshr_data_valid_vec = mshr_entries.map(_.data_valid) //将mshr table的data valid字段提取出来 该表项的数据有没有取到
  val mshr_written_vec = mshr_entries.map(_.written_cache) //将mshr table的written cache字段提取出来 该表项的数据有没有被写入到cache中
  
  val mshr_full = mshr_valid_vec.reduce(_ && _)  // 对valid字段进行and操作 指示mshr table是否已经满了
  val mshr_empty = !mshr_valid_vec.reduce(_ || _) // 对valid字段进行or操作并取反 指示mshr table是否空的【调试用】
  
  // Load状态 - 与上面一样的操作
  val load_valid_vec = load_entries.map(_.valid)
  val load_data_valid_vec = load_entries.map(_.data_valid)
  val load_returned_vec = load_entries.map(_.returned_cpu)
  
  val load_full = load_valid_vec.reduce(_ && _)
  val load_empty = !load_valid_vec.reduce(_ || _)
  
  // 调试信号
  io.debug_mshr_full := mshr_full
  io.debug_mshr_empty := mshr_empty

  io.debug_load_full := load_full
  io.debug_load_empty := load_empty
  
  /*===========================================
    4. 任务A：接收新的miss请求
  ===========================================*/
  //[1].对于从外面传进来的地址cpu_req_addr
  //    需要检测这个地址 能否写入到MSHR 以及 如何写

  //    a.检查地址是否与现有MSHR表项重合的重合情况 并且找出来
  val addr_match = mshr_entries.zipWithIndex.map { case (entry, i) =>
    entry.valid && (entry.addr(47,6) === io.cpu_req_addr(47,6))
  }
  val addr_match_vec = VecInit(addr_match)
  val addr_match_any = addr_match_vec.reduce(_ || _)   //是否匹配某一个
  val match_mshr_id = PriorityEncoder(addr_match_vec)  //编码找出是哪一个表项与现有的请求重合

  //    b.新miss请求必然会写入load table中 只需检测该表项是否非满
  val load_free_vec = load_entries.zipWithIndex.map { case (entry, i) =>
    !entry.valid //|| (entry.data_valid && entry.returned_cpu)  // 找出空闲的表项或已完成的表项******************先不加*应该会被后面优先级高的清零顶开了
  }
  val load_free_any = load_free_vec.reduce(_ || _)     // 指示load table是否有空闲
  
  //    c.综合以上两table的条件，指示能否接受新miss请求的条件
  val can_accept_new = ( !mshr_full && !load_full ) || (addr_match_any && load_free_any) // 非空 或者 地址匹配且load table非空 即可接收新miss
  io.cpu_can_accept := can_accept_new
  
  //[2].开始查找空闲表项的具体位置【找出写表项地址】

  //    a.查找空闲的MSHR表项
  val mshr_free_vec = mshr_entries.zipWithIndex.map { case (entry, i) =>
    !entry.valid || (entry.data_valid && entry.written_cache && entry.valid)  // 空闲或已完成
  }
  val mshr_free_oh = PriorityEncoderOH(mshr_free_vec)
  val mshr_free_id = OHToUInt(mshr_free_oh) //找到那个空闲的mshr table位置
  val mshr_free_any = mshr_free_vec.reduce(_ || _)
  
  // 查找空闲的Load表项
  val load_free_oh = PriorityEncoderOH(load_free_vec)
  val load_free_id = OHToUInt(load_free_oh) //找到那个空闲的load table位置
  
  //[3].分别判断两表的写使能

  // 写MSHR信号：地址不重合且有空间，或者地址重合但Load有空间
  val write_mshr = io.cpu_req_valid && can_accept_new && !addr_match_any && mshr_free_any
  
  // 写Load信号：总是需要写Load Table
  val write_load = io.cpu_req_valid && can_accept_new && load_free_any
  
  // 确定MSHR ID：如果地址重合，使用已存在的请求的地址；否则使用新的
  val allocated_mshr_id = Mux(addr_match_any, match_mshr_id, mshr_free_id)
  
  // 更新MSHR表项
  when(write_mshr) {
    mshr_entries(mshr_free_id).valid := true.B
    mshr_entries(mshr_free_id).issued := false.B
    mshr_entries(mshr_free_id).data_valid := false.B
    mshr_entries(mshr_free_id).written_cache := false.B
    mshr_entries(mshr_free_id).addr := io.cpu_req_addr
    mshr_entries(mshr_free_id).req_id := io.cpu_req_id
  }
  
  // 更新Load表项
  //这里还有一点需要注意的就是，在只写load table的情况下（mshr table已经有了重合的请求）
  //这个时候就可能出现，这个新请求写入load table的时机 和 重合请求返回来的时机 是同一个周期
  //所以在这里需要进行一个对比判断是否有这种情况，如果有直接把返回的数据写入load table
  //不然新请求会被一直卡在那里，或者后面得到其他请求的错误的数据

  val rsp_match_any = Wire(Bool())
  val rsp_match_id  = Wire(UInt(MSHR_ID_WIDTH.W))

  //判断是否会出现时机重合的情况
  val match_rsp = io.mem_rsp_valid && rsp_match_any && (mshr_entries(rsp_match_id).addr(47,6) === io.cpu_req_addr(47,6))

  val special_data_valid = Mux(match_rsp, true.B, false.B)

  val rsp_selected_data = Wire(UInt(64.W))
  rsp_selected_data := Mux1H(
    (0 until 8).map(j => (io.cpu_req_addr(5,3) === j.U) -> io.mem_rsp_data(64*j+63, 64*j))
  )
  val special_data       = Mux(match_rsp, rsp_selected_data, 0.U)

  when(write_load) {
    load_entries(load_free_id).valid    := true.B
    load_entries(load_free_id).mshr_id  := allocated_mshr_id
    load_entries(load_free_id).req_id   := io.cpu_req_id
    load_entries(load_free_id).req_idx   := io.cpu_req_idx

    load_entries(load_free_id).data         := special_data
    load_entries(load_free_id).data_valid   := special_data_valid

    load_entries(load_free_id).offset       := io.cpu_req_addr(5,3)
    load_entries(load_free_id).returned_cpu := false.B
  }
  
  /*===========================================
    5. 任务B：MSHR向外发送内存请求
  ===========================================*/
  // 查找可以发出的请求：有效、未发出、未完成
  val can_issue_vec = mshr_entries.zipWithIndex.map { case (entry, i) =>
    entry.valid && !entry.issued && !entry.data_valid
  }
  val can_issue_oh = PriorityEncoderOH(can_issue_vec)
  val can_issue_id = OHToUInt(can_issue_oh)
  val can_issue_any = can_issue_vec.reduce(_ || _)
  
  // 发出内存请求
  io.mem_req_valid := can_issue_any
  io.mem_req_addr := Mux1H(can_issue_oh, mshr_entries.map(_.addr))
  io.mem_req_id := Mux1H(can_issue_oh, mshr_entries.map(_.req_id))
  
  // 更新issued状态
  when(can_issue_any && io.mem_req_ready) {
    mshr_entries(can_issue_id).issued := true.B
  }
  
  /*===========================================
    6. 任务C：接收内存返回的数据
  ===========================================*/
  // 根据ID查找对应的MSHR表项
  val rsp_match_vec = mshr_entries.zipWithIndex.map { case (entry, i) =>
    entry.valid && entry.issued && (entry.req_id === io.mem_rsp_id)
  }
  val rsp_match_oh = PriorityEncoderOH(rsp_match_vec)
  rsp_match_id  := OHToUInt(rsp_match_oh)
  rsp_match_any := rsp_match_vec.reduce(_ || _)
  
  // 更新MSHR表项的数据
  when(io.mem_rsp_valid && rsp_match_any) {
    mshr_entries(rsp_match_id).data_valid := true.B
    mshr_entries(rsp_match_id).data := io.mem_rsp_data
  }
  
  // 广播数据到Load Table并更新
  for (i <- 0 until LOAD_ENTRIES) {
    when(io.mem_rsp_valid && rsp_match_any && 
         load_entries(i).valid && 
         !load_entries(i).data_valid && 
         (load_entries(i).mshr_id === rsp_match_id)) { //判断该返回的数据是否是该项需要的数据
      
      // 根据地址的[5:3]位选择64位数据
      val bank_sel = load_entries(i).offset
      
      // 从512位数据中选择对应的64位
      val selected_data = Wire(UInt(64.W))
      selected_data := Mux1H(
        (0 until 8).map(j => (bank_sel === j.U) -> io.mem_rsp_data(64*j+63, 64*j))
      )
      
      load_entries(i).data := selected_data
      load_entries(i).data_valid := true.B
    }
  }
  
  /*===========================================
    7. 任务D：写Cache操作 为缩减传播延迟，不依据内存的dataOK写cache，依据表项的written_cache信号写
  ===========================================*/
  // 查找可以写Cache的MSHR表项：数据有效但未写Cache
  val can_write_cache_vec = mshr_entries.zipWithIndex.map { case (entry, i) =>
    entry.valid && entry.data_valid && !entry.written_cache
  }
  val can_write_cache_oh = PriorityEncoderOH(can_write_cache_vec)
  val can_write_cache_id = OHToUInt(can_write_cache_oh)
  val can_write_cache_any = can_write_cache_vec.reduce(_ || _)
  
  // 写Cache Data Tag接口
  io.cache_wr_valid := can_write_cache_any
  io.cache_wr_addr := Mux1H(can_write_cache_oh, mshr_entries.map(_.addr))
  io.cache_wr_data := Mux1H(can_write_cache_oh, mshr_entries.map(_.data))
  
  // 更新written_cache状态
  when(can_write_cache_any) {
    mshr_entries(can_write_cache_id).written_cache := true.B
  }
  
  /*===========================================
    8. 任务E：返回数据给CPU
  ===========================================*/
  // 查找可以返回给CPU的Load表项：数据有效但未返回
  val can_return_vec = load_entries.zipWithIndex.map { case (entry, i) =>
    entry.valid && entry.data_valid && !entry.returned_cpu
  }
  val can_return_oh = PriorityEncoderOH(can_return_vec)
  val can_return_id = OHToUInt(can_return_oh)
  val can_return_any = can_return_vec.reduce(_ || _)
  
  // CPU返回接口
  io.cpu_rsp_valid := can_return_any
  io.cpu_rsp_data := Mux1H(can_return_oh, load_entries.map(_.data))
  io.cpu_rsp_id := Mux1H(can_return_oh, load_entries.map(_.req_id))
  io.cpu_rsp_idx := Mux1H(can_return_oh, load_entries.map(_.req_idx))
  
  // 更新returned_cpu状态
  when(can_return_any && io.cpu_rsp_ready) { //要外界能够返才返，避免与外界HIT返数据冲突的情况发生
    load_entries(can_return_id).returned_cpu := true.B
  }
  
  /*===========================================
    9. 清理已完成表项
  ===========================================*/
  // 清理MSHR表项：数据已写Cache，且关联的所有Load请求都已完成
  for (i <- 0 until MSHR_ENTRIES) {
    val mshr_entry = mshr_entries(i)
    
    when(mshr_entry.valid && mshr_entry.written_cache && !write_mshr ) { //&& !write_mshr：后面发现的BUG，清零操作和初始化写操作可能同时进行
      mshr_entries(i).valid := false.B
      mshr_entries(i).issued := false.B
      mshr_entries(i).data_valid := false.B
      mshr_entries(i).written_cache := false.B

      mshr_entries(i).addr   := 0.U
      mshr_entries(i).data   := 0.U
      mshr_entries(i).req_id := 0.U
    }
  }

  //(entry.data_valid && entry.returned_cpu)
  // 清理Load表项：已返回CPU
  for (i <- 0 until LOAD_ENTRIES) {
    when(load_entries(i).valid && load_entries(i).returned_cpu && !write_load) {
      load_entries(i).valid        := false.B
      load_entries(i).mshr_id      := 0.U
      load_entries(i).req_id       := 0.U
      load_entries(i).data         := 0.U
      load_entries(i).data_valid   := false.B
      load_entries(i).offset       := 0.U
      load_entries(i).returned_cpu := false.B
    }
  }


}