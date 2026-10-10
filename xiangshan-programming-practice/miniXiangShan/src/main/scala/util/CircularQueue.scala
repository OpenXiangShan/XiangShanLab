package minixiangshan.util
 
import chisel3._
import chisel3.util._
 
/**
 * 通用环形队列模块
 *
 * 【功能】
 *   - 支持多端口同时入队（enqWidth路）
 *   - 支持多端口同时出队（deqWidth路）
 *   - 自动管理头尾指针和旗子翻转
 *   - 提供空/满/项数查询
 *   - 支持按索引随机读取（ROB等组件需要）
 *
 * 【参数】
 *   gen:      队列中存储的数据类型（如新的Bundle）
 *   entries:  队列容量，必须是2的幂
 *   enqWidth: 每周期最多入队几个元素（默认1）
 *   deqWidth: 每周期最多出队几个元素（默认1）
 *
 * 【端口说明】
 *   io.enq(i).valid/fire  —— 第i路入队请求
 *   io.enq(i).bits        —— 第i路入队数据
 *   io.deq(i).valid/fire  —— 第i路出队结果
 *   io.deq(i).bits        —— 第i路出队数据
 *   io.empty              —— 队列是否为空
 *   io.full               —— 队列是否为满
 *   io.count              —— 队列中当前有多少项
 *   io.read(idx)          —— 按索引随机读取（用于ROB等场景）
 *
 * 【使用示例】
 *   val ib = Module(new CircularQueue(new DecoupledIO(UInt(32.W)), entries=16))
 *   ib.io.enq(0) <> myInput
 *   myOutput <> ib.io.deq(0)
 */
class CircularQueueIO[T <: Data](gen: T, entries: Int, enqWidth: Int, deqWidth: Int) extends Bundle {
 
  /** 入队端口：enqWidth路，每路都是Decoupled（带握手的） */
  val enq  = Vec(enqWidth, Flipped(DecoupledIO(gen)))
  /** 出队端口：deqWidth路，每路都是Decoupled */
  val deq  = Vec(deqWidth, DecoupledIO(gen))
 
  /** 队列是否为空 */
  val empty = Output(Bool())
 
  /** 队列是否为满 */
  val full  = Output(Bool())
 
  /** 队列中当前有多少项 */
  val count = Output(UInt(log2Ceil(entries + 1).W))
 
  /** 随机读端口：给一个索引，返回对应位置的数据
   *  用途：ROB中需要按robIdx读某条指令的信息
   *  注意：读操作不移动指针，不影响队列状态
   */
  val read  = Vec(enqWidth + deqWidth, (new ReadPortIO(gen, entries)))

   /** 刷新端口：高电平时立即清空队列 */
  val flush = Input(Bool())  // 新增：刷新信号

}
 
/** 随机读端口 */
class ReadPortIO[T <: Data](gen: T, entries: Int) extends Bundle {
  val addr = Input(UInt(log2Ceil(entries).W))   // 要读的索引
  val data = Output(gen)                         // 读出的数据
}
 
class CircularQueue[T <: Data](
  val gen:     T,        // 存储的数据类型
  val entries: Int,      // 队列容量
  val enqWidth: Int = 1, // 入队宽度
  val deqWidth: Int = 1  // 出队宽度
) extends Module {
 
  // 编译期检查
  require(isPow2(entries), s"CircularQueue大小必须是2的幂，当前: $entries")
  require(enqWidth >= 1 && deqWidth >= 1, "入队/出队宽度至少为1")
  require(enqWidth <= entries && deqWidth <= entries, "入队/出队宽度不能超过队列大小")
 
  val io = IO(new CircularQueueIO(gen, entries, enqWidth, deqWidth))
 
  // =====================================================================
  // 1. 定义指针类型
  // =====================================================================
  // 创建一个专属于本队列的指针类
  class QueuePtr extends CircularQueuePtr[QueuePtr](entries)
 
  // =====================================================================
  // 2. 头尾指针寄存器
  // =====================================================================
 
  /**
   * 头指针（deqPtr）：指向该被取走的位置
   * 尾指针（enqPtr）：指向下一个该放入的位置
   *
   * 初始都是 value=0, flag=0（空队列）
   */
  val deqPtr = RegInit(0.U.asTypeOf(new QueuePtr))  // 头（出队端）
  val enqPtr = RegInit(0.U.asTypeOf(new QueuePtr))  // 尾（入队端）
  dontTouch(deqPtr)
  dontTouch(enqPtr)
 
  // =====================================================================
  // 3. 存储体：用 Vec 寄存器数组
  // =====================================================================
 
  /**
   * data: 队列的存储体，entries个寄存器，每个存储gen类型的数据
   *
   * 为什么用 Reg 而不是 Mem？
   * - Reg: 所有项都可以同时读写，适合小队列（<64项）
   * - Mem: 只能单/双端口读写，适合大队列（省面积）
   * 这里用Reg是为了简单和灵活，您可以根据需求改为Mem
   */
  val data = RegInit(VecInit(Seq.fill(entries)(0.U.asTypeOf(gen))))
 
  // =====================================================================
  // 4. 空满判断
  // =====================================================================
 
  /**
   * 空：value相等 且 flag相等 两个指针完全相同
   * 满：value相等 且 flag不同 差了一整圈
   */
  val empty = deqPtr === enqPtr   // value相同 且 flag相同
  val full  = (deqPtr.value === enqPtr.value) && (deqPtr.flag =/= enqPtr.flag)
  //diffDontTouch(full)
  //diffDontTouch(empty)
  io.empty := empty
  io.full  := full
 
  // =====================================================================
  // 5. 项数计数
  // =====================================================================
 
  /**
   * count = enqPtr 到 deqPtr 的距离
   * distanceTo 方法实现
   */
  val count = WireInit(0.U(log2Ceil(entries + 1).W))
    
    //该方法的俩指针的方向不能变
  count :=  enqPtr.distanceTo(deqPtr)
  io.count := count
 
  // =====================================================================
  // 6. 入队逻辑（支持多路同时入队）
  // =====================================================================
 
  /**
   * 入队特性：
   * 一对一的特性
   * 不会块与块之间的问腿
   * 只要某一个路上是valid
   * 那就入队
   * 
   * 但是需要强制满足一个条件
   * 就是必须得保证，valid从前到后不能断开
   * 也就是不能是下面这种情况：
   * port0： valid true
   * port1： valid true
   * port2： valid true
   * port3： valid false
   * port4： valid false
   * port5： valid false
   * port6： valid true [错误！!]
   * port7： valid false
   */
  for (i <- 0 until enqWidth - 1) {
    // 如果当前端口valid为false，但下一个端口valid为true，就是错误
    when(!io.enq(i).valid && io.enq(i+1).valid) {
      assert(false.B, "IBF input invalid")
    }
  }
  val enqFireCnt = WireInit(0.U(log2Ceil(enqWidth + 1).W))  // 本周期实际入队数dfdsf
  

  for (i <- 0 until enqWidth) {
    // 第i路能入队的条件：队列剩余空间 > i
    val canEnq = ((count +& i.U) < entries.U) // && io.enq(i).valid
    
    io.enq(i).ready := canEnq && !full
    
    when (io.enq(i).fire) {
      // 计算第i路应该写入的位置
      // 自动取余
      val writeIdx = (enqPtr.value + i.U)(log2Ceil(entries) - 1, 0)
      //dontouch(writeIdx)
      data(writeIdx) := io.enq(i).bits
    }
  }
 
  // 统计本周期有多少路入队成功
  // 这个是对的吗
  enqFireCnt := PopCount(io.enq.map(_.fire))
 
  // 入队成功后，尾指针前进 enqFireCnt 步
  enqPtr := enqPtr + enqFireCnt
 
  // =====================================================================
  // 7. 出队逻辑
  // =====================================================================

  val deqFireCnt = WireInit(0.U(log2Ceil(deqWidth + 1).W))  // 本周期实际出队数
 
  for (i <- 0 until deqWidth) {
    // 第i路能出队的条件：队列中的项数 > i
    val canDeq = count > i.U
 
    io.deq(i).valid := canDeq && !empty
 
    // 读出数据：从 deqPtr + i 的位置读
    val readIdx = (deqPtr.value + i.U)(log2Ceil(entries) - 1, 0)
    io.deq(i).bits := data(readIdx)
  }
 
  // 统计本周期有多少路出队成功
  deqFireCnt := PopCount(io.deq.map(_.fire))
 
  // 出队成功后，头指针前进 deqFireCnt 步
  deqPtr := deqPtr + deqFireCnt
 
  // =====================================================================
  // 8. 随机读端口（用于ROB等需要按索引访问的场景）
  // =====================================================================
 
  /**
   * 随机读：给一个索引，返回 data(idx)
   * 不移动指针，不影响队列状态
   * 就像您翻到柜子的某个编号看看里面有什么，但不取走
   */
  for (i <- io.read.indices) {
    io.read(i).data := data(io.read(i).addr)
  }

   // =====================================================================
  // 9. flush处理逻辑
  // =====================================================================
  when(io.flush) {
    // 重置指针到初始状态
    deqPtr.value := 0.U
    deqPtr.flag := false.B
    enqPtr.value := 0.U
    enqPtr.flag := false.B
    
    // 注意：这里不清除数据存储，因为flush后新数据会覆盖旧数据
    // 如果需要清除数据，可以添加以下代码：
    // for (i <- 0 until entries) {
    //   data(i) := 0.U.asTypeOf(gen)
    // }
    
    // 调试输出
    //printf(p"[CircularQueue] Flush activated. Queue cleared.\n")
  }


}
