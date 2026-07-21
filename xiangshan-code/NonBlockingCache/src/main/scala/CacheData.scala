import chisel3._
import chisel3.util._

class CacheData(
  INDEX_WD: Int,
  TAG_WD: Int,
  OFFSET_WD: Int
) extends Module {

  val io = IO(new Bundle {

    /*----待定参数----*/
    val req = Input(Bool())

    /*----来自于外层Ctrl控制的回填数据----*/
    val replace_fromCtrl      = Input(Bool())
    val refillAddr_fromCtrl   = Input(UInt(64.W))
    val newCacheline_fromCtrl = Input(UInt(512.W))

    /*----hit时机的读取数据----*/
    val hit_fromTag      = Input(UInt(4.W))
    val reqAddr_fromCtrl = Input(UInt(64.W)) //阻塞的话那么这个东西和上面那个是一样的
    val pointer_fromTag  = Input(UInt(4.W))
    val rdata_toCtrl     = Output(UInt(64.W))

  })

  val OFFSET_LSB = 0
  val OFFSET_MSB = OFFSET_WD - 1
  val INDEX_LSB  = OFFSET_WD
  val INDEX_MSB  = OFFSET_WD + INDEX_WD - 1
  val TAG_LSB    = INDEX_MSB + 1
  val TAG_MSB    = TAG_LSB + TAG_WD - 1

  /*-----Cache块大小-----*/
    // 一个Cache块 64B -> 8 * 64bits
  val CACHE_LINE =  1 << ( OFFSET_WD - 3 /*64位*/ );

  /*-----计算回填信息-----*/
  val refill_offset = io.refillAddr_fromCtrl(OFFSET_MSB , OFFSET_LSB)
  val refill_index  = io.refillAddr_fromCtrl(INDEX_MSB  , INDEX_LSB)
  val refill_tag    = io.refillAddr_fromCtrl(TAG_MSB    , TAG_LSB)

  /*-----计算请求信息-----*/
  val req_offset    = io.reqAddr_fromCtrl(OFFSET_MSB    , OFFSET_LSB)
  val req_index     = io.reqAddr_fromCtrl(INDEX_MSB     , INDEX_LSB)
  val req_tag       = io.reqAddr_fromCtrl(TAG_MSB       , TAG_LSB)
  //从cache块中选择数据 【 512bits 选 64bits 】
  val bank_sel      = 1.U << req_offset(5, 3)

  /*--------开始制作存储器----------*/
  
  /*第一路-cacheWay0*/
  val Way0_brams = Seq.fill(CACHE_LINE)(Module(new CacheDualPortBRAM))
  val rdata_way0 = Wire(Vec(CACHE_LINE, UInt(64.W)))

  for (n <- 0 until CACHE_LINE) {
      /*--写信号--*/
      val wr_en    = io.replace_fromCtrl && io.pointer_fromTag(0)
      val wr_index = refill_index
      val startBit = n * 64
      val endBit = (n + 1) * 64 - 1
      val wr_data  = io.newCacheline_fromCtrl(endBit, startBit)
      Way0_brams(n).io.wr_en    := wr_en
      Way0_brams(n).io.wr_index := wr_index
      Way0_brams(n).io.wr_data  := wr_data
      
      /*--读信号--*/ /*单周期读出*/
      val rd_en    =  io.req && bank_sel(n) && io.hit_fromTag(0)
      val rd_index =  req_index
      val rd_data  =  Way0_brams(n).io.rd_data 
      Way0_brams(n).io.rd_en     := rd_en
      Way0_brams(n).io.rd_index  := rd_index
      rdata_way0(n)         := rd_data
    }
  
  /*第二路-cacheWay1*/
  val Way1_brams = Seq.fill(CACHE_LINE)(Module(new CacheDualPortBRAM))
  val rdata_way1 = Wire(Vec(CACHE_LINE, UInt(64.W)))

  for (n <- 0 until CACHE_LINE) {
      /*--写信号--*/
      val wr_en    = io.replace_fromCtrl && io.pointer_fromTag(1)
      val wr_index = refill_index
      val startBit = n * 64
      val endBit = (n + 1) * 64 - 1
      val wr_data  = io.newCacheline_fromCtrl(endBit, startBit)
      Way1_brams(n).io.wr_en    := wr_en
      Way1_brams(n).io.wr_index := wr_index
      Way1_brams(n).io.wr_data  := wr_data
      
      /*--读信号--*/ /*单周期读出*/
      val rd_en    =  io.req && bank_sel(n) && io.hit_fromTag(1)
      val rd_index =  req_index
      val rd_data  =  Way1_brams(n).io.rd_data 
      Way1_brams(n).io.rd_en     := rd_en
      Way1_brams(n).io.rd_index  := rd_index
      rdata_way1(n)         := rd_data
    }

  /*第三路-cacheWay2*/
  val Way2_brams = Seq.fill(CACHE_LINE)(Module(new CacheDualPortBRAM))
  val rdata_way2 = Wire(Vec(CACHE_LINE, UInt(64.W)))

  for (n <- 0 until CACHE_LINE) {
      /*--写信号--*/
      val wr_en    = io.replace_fromCtrl && io.pointer_fromTag(2)
      val wr_index = refill_index
      val startBit = n * 64
      val endBit = (n + 1) * 64 - 1
      val wr_data  = io.newCacheline_fromCtrl(endBit, startBit)
      Way2_brams(n).io.wr_en    := wr_en
      Way2_brams(n).io.wr_index := wr_index
      Way2_brams(n).io.wr_data  := wr_data
      
      /*--读信号--*/ /*单周期读出*/
      val rd_en    =  io.req && bank_sel(n) && io.hit_fromTag(2)
      val rd_index =  req_index
      val rd_data  =  Way2_brams(n).io.rd_data 
      Way2_brams(n).io.rd_en     := rd_en
      Way2_brams(n).io.rd_index  := rd_index
      rdata_way2(n)         := rd_data
    }

  /*第四路-cacheWay2*/
  val Way3_brams = Seq.fill(CACHE_LINE)(Module(new CacheDualPortBRAM))
  val rdata_way3 = Wire(Vec(CACHE_LINE, UInt(64.W)))

  for (n <- 0 until CACHE_LINE) {
      /*--写信号--*/
      val wr_en    = io.replace_fromCtrl && io.pointer_fromTag(3)
      val wr_index = refill_index
      val startBit = n * 64
      val endBit = (n + 1) * 64 - 1
      val wr_data  = io.newCacheline_fromCtrl(endBit, startBit)
      Way3_brams(n).io.wr_en    := wr_en
      Way3_brams(n).io.wr_index := wr_index
      Way3_brams(n).io.wr_data  := wr_data
      
      /*--读信号--*/ /*单周期读出*/
      val rd_en    =  io.req && bank_sel(n) && io.hit_fromTag(3)
      val rd_index =  req_index
      val rd_data  =  Way3_brams(n).io.rd_data 
      Way3_brams(n).io.rd_en     := rd_en
      Way3_brams(n).io.rd_index  := rd_index
      rdata_way3(n)         := rd_data
    }
    //对以下数据进行选择提取出想要的那64bits
    //val rdata_way0 
    //val rdata_way1 
    //val rdata_way2 
    //val rdata_way3 
    val selected_way0_data = Wire(UInt(64.W))
    val selected_way1_data = Wire(UInt(64.W))
    val selected_way2_data = Wire(UInt(64.W))
    val selected_way3_data = Wire(UInt(64.W))

    selected_way0_data := Mux1H(bank_sel.asBools, rdata_way0)
    selected_way1_data := Mux1H(bank_sel.asBools, rdata_way1)
    selected_way2_data := Mux1H(bank_sel.asBools, rdata_way2)
    selected_way3_data := Mux1H(bank_sel.asBools, rdata_way3)

    val hit_selected_data = Wire(UInt(64.W))

    when(io.hit_fromTag(0)) {
      hit_selected_data := selected_way0_data
    }.elsewhen(io.hit_fromTag(1)) {
      hit_selected_data := selected_way1_data
    }.elsewhen(io.hit_fromTag(2)) {
      hit_selected_data := selected_way2_data
    }.elsewhen(io.hit_fromTag(3)) {
      hit_selected_data := selected_way3_data
    }.otherwise {
      hit_selected_data := 0.U  // 没有命中时输出0
    }

    io.rdata_toCtrl := hit_selected_data


}
