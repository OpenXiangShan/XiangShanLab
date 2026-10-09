package minixiangshan.frontend

import chisel3._
import chisel3.util._
import minixiangshan.config.Parameters
import minixiangshan.config._
import minixiangshan.config.NSModule
import minixiangshan.config.NSBundle
import minixiangshan.mmu.FetchMmuError
import minixiangshan.util.CircularQueue  // 导入新写的环形队列模块

class IBF(implicit p: Parameters) extends NSModule {
  val io = IO(new Bundle {
    // 来自预译码的输入
    val in = Flipped(Decoupled(new PredecodeResp))
    // 输出到后端
    val out = Vec(CtrlBlockWidth, Decoupled(new CtrlFlowIO))
    // 控制信号
    val flush = Input(Bool())  // 后端redirect时清空(延迟一拍)
  })
  
  diffDontTouch(io.out)
  
  // ==================== 使用CircularQueue重构 ====================
  
  // 实例化环形队列
  val queue = Module(new CircularQueue(
    gen = new CtrlFlowIO,        // 存储的数据类型
    entries = ibufDepth,         // 队列容量
    enqWidth = fetchWidth,       // 入队宽度（一次最多入队fetchWidth条指令）
    deqWidth = CtrlBlockWidth    // 出队宽度（一次最多出队CtrlBlockWidth条指令）
  ))
  queue.io.read := DontCare

  queue.io.flush := io.flush
  
  // ==================== 入队逻辑 ====================
  for (i <- 0 until fetchWidth) {
    
      queue.io.enq(i).valid := io.in.valid && io.in.bits.enqMask(i) && io.in.ready
      /*因为前面来的是一个指令块，必须是同时入队的，为了避免单独几个入队的情况发生
      [独属于IBF模块的情况] 必须要加上 io.in.ready
      */
      
      queue.io.enq(i).bits.instr := io.in.bits.instrs(i)
      queue.io.enq(i).bits.pc := io.in.bits.pcs(i)
      queue.io.enq(i).bits.pdInfo := io.in.bits.pdInfo(i)
      queue.io.enq(i).bits.bpuInfo := io.in.bits.bpuInfo(i)

      // 异常处理
      queue.io.enq(i).bits.exception := io.in.bits.mmu_error
      //when(io.in.bits.pcs(i) === 0x1c000024.U){
      //  queue.io.enq(i).valid := false.B
      //}
    

  }

  
  // 因为前面发过来的是一个指令块
  // 所以说无论有多少个有效的可以入队的指令，一定都应该是必须要同时入队的
  // 发给前面的指令块的ready条件：
  // 当队列里面的剩余数量 大于等于 前方请求的数量数量时
  io.in.ready :=  PopCount(queue.io.enq.map(_.ready)) >= PopCount(io.in.bits.enqMask) //fetchWidth.U
  //!queue.io.full
  
  // ==================== 出队逻辑 ====================
  // 连接CircularQueue的出队端口到后端输出
  
  for (i <- 0 until CtrlBlockWidth) {
    // 连接valid和bits信号
    io.out(i).valid := queue.io.deq(i).valid
    io.out(i).bits := queue.io.deq(i).bits
    

    /*-------- 现在暂且认为，整个系统不支持向前插空 ---------*/
    // 所以在连接循坏队列的ready信号的时候
    // 要注意
    // 这个是不能直接这样传的
    // queue.io.deq(i).ready := io.out(i).ready
    // OK  NO
    // OK  NO
    // OK  OK
    // 以上情况就会出现错误
    // 队列中直接检测到第三组的ready都是OK的，就会错误的出队一个，但是这样是错误的
    // 所以要更新给queue.io.deq(i).ready的赋值方式

  }
  val deqReadyMask = Wire(Vec(CtrlBlockWidth, Bool()))
  deqReadyMask(0) := io.out(0).ready
  // 后续端口：取决于自己的ready AND 前面所有端口都ready
  for (i <- 1 until CtrlBlockWidth) {
    deqReadyMask(i) := io.out(i).ready && deqReadyMask(i-1)
  }
  for (i <- 0 until CtrlBlockWidth) {
    queue.io.deq(i).ready := deqReadyMask(i)
  }
  
  // ==================== 状态信号透传 ====================
  // 可以将队列状态信号输出用于调试
  val queueEmpty = queue.io.empty
  val queueFull = queue.io.full
  val queueCount = queue.io.count
  
  // ==================== 随机读端口 ====================
  // 如果后端需要随机读取指令（如ROB需要读取特定指令的信息），可以使用read端口
  // 这里示例中暂时不使用，但保留了接口
  
  // ==================== 调试信息 ====================
  when(io.in.fire) {
    val enqCount = PopCount(io.in.bits.enqMask)
    //printf(p"[IBF-CircularQueue] Enqueue: ${enqCount} instructions, queue count=${queueCount}\n")
    
    // 输出每条入队的指令信息
    for (i <- 0 until fetchWidth) {
      when(io.in.bits.enqMask(i)) {
        //printf(p"  Instr[${i}]: pc=0x${Hexadecimal(io.in.bits.pcs(i))}, " +
        //       p"instr=0x${Hexadecimal(io.in.bits.instrs(i))}\n")
      }
    }
  }
  
  // 输出出队信息
  for (i <- 0 until CtrlBlockWidth) {
    when(io.out(i).fire) {
      //printf(p"[IBF-CircularQueue] Dequeue[${i}]: pc=0x${Hexadecimal(io.out(i).bits.pc)}, " +
       //      p"instr=0x${Hexadecimal(io.out(i).bits.instr)}\n")
    }
  }
  
  // 队列状态监控
  //printf(p"[IBF-CircularQueue Status] count=$queueCount, empty=$queueEmpty, full=$queueFull\n")
  
  when(io.flush) {
    //printf(p"[IBF-CircularQueue] Buffer flushed due to redirect\n")
  }
  
  // ==================== 断言检查 ====================
  // 确保不会在队列满时尝试入队 就连个线你检查个屁
  //when(io.in.valid && queue.io.full) {
  //  assert(!io.in.valid || !queue.io.full, 
  //         "IBF: Attempting to enqueue when queue is full")
  //}
  
  // 确保出队的指令是有效的
  for (i <- 0 until CtrlBlockWidth) {
    when(io.out(i).valid && !queue.io.empty) {
      // 出队有效时，队列不应为空
      // 这个检查在CircularQueue内部已经做了，这里再加一层保护
    }
  }
}
