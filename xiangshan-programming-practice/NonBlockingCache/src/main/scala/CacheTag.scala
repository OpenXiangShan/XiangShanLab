import chisel3._
import chisel3.util._


class CacheTag(
  INDEX_WD: Int,
  TAG_WD: Int,
  OFFSET_WD: Int
) extends Module {

  val io = IO(new Bundle {

    /*
     TagModule接收新的当前的请求与具体请求的地址
    */
    val req   = Input(Bool())
    val req_addr  = Input(UInt(48.W))
    
    /*
     TagModule传出命中/缺失信号
    */
    val hit   = Output(UInt(4.W)) //命中which way
    val miss  = Output(Bool())

    /*
     接收到来自于Ram的数据之后更改现有Tag的相关输入信息
    */
    val replace_cache = Input(Bool()) //Cache的写信号
    val replace_addr  = Input(UInt(48.W)) //要添加的新的信息
    /*替换which way在本模块中运算*/
    val replace_pointer   = Output(UInt(4.W)) //传出去的替换指针
   
  })
  val INDEX_NUM      = 1 << INDEX_WD
  val replacePointer =  Wire(UInt(4.W))
  io.replace_pointer := replacePointer

  val replace_cache = io.replace_cache

  

  val OFFSET_LSB = 0
  val OFFSET_MSB = OFFSET_WD - 1

  val INDEX_LSB  = OFFSET_WD
  val INDEX_MSB  = OFFSET_WD + INDEX_WD - 1

  val TAG_LSB    = INDEX_MSB + 1
  val TAG_MSB    = TAG_LSB + TAG_WD - 1
/*--------------------当req来的那个周期，首先要做的事情-begin-------------------*/
//1.将地址分离出想要的成分
  val req        = io.req
  val req_offset = io.req_addr(OFFSET_MSB, OFFSET_LSB)
  val req_index  = io.req_addr(INDEX_MSB, INDEX_LSB)
  val req_tag    = io.req_addr(TAG_MSB, TAG_LSB)
//2.利用index找的tag进行对比验证是否命中
val validTag = Cat(1.U(1.W), req_tag)
  //1.Way0
  val tag_way0 = RegInit(
    VecInit(Seq.fill(INDEX_NUM)(
      0.U((TAG_WD + 1).W) /*1位的Valid + TAG_WD位的Tag*/
    ))
  )
  val hit_way0 =
    req &&
    (validTag === tag_way0(req_index))


  //2.Way1
  val tag_way1 = RegInit(
    VecInit(Seq.fill(INDEX_NUM)(
      0.U((TAG_WD + 1).W) /*1位Valid + TAG_WD位的Tag*/
    ))
  )
  val hit_way1 =
    req &&
    (validTag === tag_way1(req_index))

  //3.Way2
  val tag_way2 = RegInit(
    VecInit(Seq.fill(INDEX_NUM)(
      0.U((TAG_WD + 1).W) /*1位Valid + TAG_WD位的Tag*/
    ))
  )
  val hit_way2 =
    req &&
    (validTag === tag_way2(req_index))


  //4.Way3
  val tag_way3 = RegInit(
    VecInit(Seq.fill(INDEX_NUM)(
      0.U((TAG_WD + 1).W) /*1位Valid + TAG_WD位的Tag*/
    ))
  )
  val hit_way3 =
    req &&
    (validTag === tag_way3(req_index))

  val hit  = Cat(hit_way3, hit_way2, hit_way1, hit_way0)            /*命中信号*/
  val miss = req && ~(hit_way0 || hit_way1 || hit_way2 || hit_way3);/*缺失信号*/
  io.hit  := hit
  io.miss := miss


/*--------------------tag存储-begin-------------------*/

  val replace_offset = io.replace_addr(OFFSET_MSB, OFFSET_LSB)
  val replace_index  = io.replace_addr(INDEX_MSB, INDEX_LSB)
  val replace_tag    = io.replace_addr(TAG_MSB, TAG_LSB) 
  //当【新替换数据到来】且【替换指针指向this Way】
when (replace_cache && replacePointer(0)) {
  tag_way0(replace_index) := Cat(1.U(1.W), replace_tag)
}
  //当【新替换数据到来】且【替换指针指向this Way】
when (replace_cache && replacePointer(1)) {
  tag_way1(replace_index) := Cat(1.U(1.W), replace_tag)
}
  //当【新替换数据到来】且【替换指针指向this Way】
when (replace_cache && replacePointer(2)) {
  tag_way2(replace_index) := Cat(1.U(1.W), replace_tag)
}
  //当【新替换数据到来】且【替换指针指向this Way】
when (replace_cache && replacePointer(3)) {
  tag_way3(replace_index) := Cat(1.U(1.W), replace_tag)
}


/*--------------------tag存储-end-------------------*/

/*--------------------替换算法-begin-------------------*/

/*
Tree-LRU:
          b2
        /    \
      b1      b0
     /  \    /  \
  way0 way1 way2 way3

*/
val lru = RegInit(
  VecInit(Seq.fill(INDEX_NUM)(
    0.U(3.W) /*3位 分别存储 (b2, b1, b0)*/
  ))
)

val use_index = Mux(hit.orR, req_index, replace_index)
//更改lru逻辑
when (reset.asBool) {
  for (i <- 0 until INDEX_NUM) {
    lru(i) := 0.U
  }
}
.otherwise {

  /*******************
   * 访问cache有两种可能
   * 凡是访问cache，必然伴随着访问 【某一Way】的【某一个Line】
   * 1.可能是Hit命中了某一个Cache块
   * 2.可能是未命中，数据回来之后开始Replace填充Cache块
   *
   * 以上两种方式都需要变化树指针，让这个Cache块的刚刚访问的这一Way的指针远离自己
   ******************/
  when (hit_way0 ||  (replace_cache && replacePointer(0)) ) {
    lru(use_index) := Cat( //这里的地址待完善 
      1.U(1.W),     // b2
      1.U(1.W),     // b1
      lru(use_index)(0) // b0 不变
    )
  }
  .elsewhen (hit_way1 || (replace_cache && replacePointer(1))) {
    lru(use_index) := Cat(
      1.U(1.W),
      0.U(1.W),
      lru(use_index)(0)
    )
  }
  .elsewhen (hit_way2 || (replace_cache && replacePointer(2))) {
    lru(use_index) := Cat(
      0.U(1.W),
      lru(use_index)(1),
      1.U(1.W)
    )
  }
  .elsewhen (hit_way3 || (replace_cache && replacePointer(3))) {
    lru(use_index) := Cat(
      0.U(1.W),
      lru(use_index)(1),
      0.U(1.W)
    )
  }
}
val b2 = lru(replace_index)(2)
val b1 = lru(replace_index)(1)
val b0 = lru(replace_index)(0)
replacePointer :=
  Mux(!b2,
    Mux(!b1, 1.U, 2.U), /*0001:way0  0010:way1*/
    Mux(!b0, 4.U, 8.U)  /*0100:way2  1000:way3*/
  )


/*--------------------替换算法-end-------------------*/

}
