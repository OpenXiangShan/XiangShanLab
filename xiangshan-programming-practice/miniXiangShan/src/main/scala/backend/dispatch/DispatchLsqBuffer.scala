package minixiangshan.backend.dispatch
 
import chisel3._
import chisel3.util._
import minixiangshan.config._
import minixiangshan.backend.rob._
import minixiangshan.backend.rename._
import minixiangshan.backend.decode._
import minixiangshan.backend.execute._
 
/**
 * Dispatch→LSQ 流水线寄存器
 * 
 * 功能：
 * 1. 将分发级的 LSQ 入队请求打一拍送入 LSQ，切断关键路径。
 * 2. 根据自身挂起的请求量调整 freeEntries 回传分发级。
 * 3. 误预测重定向时全刷。
 * 4. 异常回滚时暂停。
 */
class DispatchLsqBuffer(implicit p: Parameters) extends NSModule {
  val io = IO(new Bundle {
    // ── 从 DispatchStage 接收 ──

    val enqReq   =Flipped(new LsEnqIO) 

    //val enqReq    = Flipped(Valid(new LsEnqEntry))
    //val enqData   = Flipped(new RenamedInst)
    // ── 向 LSQ 发送 ──
    val deqReq    = new LsEnqIO


    val redirectInfo    = Flipped(ValidIO( new redirectInfoToModule )) 

    // ── ★ 调整后的容量（回传给DispatchStage）──
    val dispatchLqFull = Output(Bool())
    val dispatchSqFull = Output(Bool())
    // ── 状态 ──
    val bufHasPendingLoadNeedFlush  = Output(Bool())
    val bufHasPendingStoreNeedFlush = Output(Bool())

    //val bufHassqIdx   = new SqPtr(SqSize)
    //val bufHaslqIdx   = new LqPtr(LqSize)

    //val pause = Input(Bool())   // 异常回滚：暂停
  })
  io.enqReq.lqHasEntries := DontCare
  io.enqReq.sqHasEntries := DontCare
 
  // ================================================================
  //  流水级寄存器（1-entry buffer）
  // ================================================================
  val bufValid     = RegInit(false.B)

 // val req    = Valid(new LsEnqEntry)
 // val toLsqData = new RenamedInst

  val bufReq      = RegInit(0.U.asTypeOf(new Valid(new LsEnqEntry)))
  val bufData      = RegInit(0.U.asTypeOf(new RenamedInst))

  val doRedirect = io.redirectInfo.valid && io.redirectInfo.bits.doRedirect
  val redirectRobIdx = io.redirectInfo.bits.robIdx
  val doFlush = doRedirect && bufValid && bufReq.bits.robIdx.isAfter(redirectRobIdx)

  // ── 出队：向 LSQ 发送 ──
  val deqFire = bufValid && !doFlush
  
  io.deqReq.req := bufReq
  io.deqReq.req.valid := bufValid && bufReq.valid && !doFlush//关键
  io.deqReq.toLsqData := bufData

  

  // ── 入队：从 DispatchStage 接收 ──
  // 当buffer空或者正在出队（腾出位置），可以接受新请求
  val canAccept = !bufValid || deqFire
  val enqFire = io.enqReq.req.valid && canAccept && !doFlush
 
  when(doFlush) {
    // 误预测：全刷buffer
    bufValid := false.B
  }.elsewhen(enqFire) {
    // 入队覆盖（优先级高于出队后清空）
    bufValid := true.B
    bufReq   := io.enqReq.req
    bufData   := io.enqReq.toLsqData
  }.elsewhen(deqFire) {
    // 出队后清空
    bufValid := false.B
  }
 
  // ================================================================
  //  ★ 核心：调整 freeEntries 回传 DispatchStage
  // ================================================================
  // 如果buffer中挂起的是load请求，LQ的有效空位要减1
  // 如果buffer中挂起的是store请求，SQ的有效空位要减1
  // 用 +& 做加法、用翻转比较避免减法下溢
  val bufPendingLoadCount  = Mux(bufValid && bufReq.bits.isLoad,  1.U, 0.U)
  val bufPendingStoreCount = Mux(bufValid && bufReq.bits.isStore, 1.U, 0.U)
 
  // effectiveFree = lsqFree - bufPending
  // 用翻转比较：lsqFree >= bufPending 时，差值有效；否则为0（视作满）
  io.dispatchLqFull := io.deqReq.lqHasEntries +& bufPendingLoadCount === LqSize.U

  io.dispatchSqFull := io.deqReq.sqHasEntries +& bufPendingStoreCount === SqSize.U
 
  io.bufHasPendingLoadNeedFlush  := bufValid && bufReq.bits.isLoad && doFlush
  io.bufHasPendingStoreNeedFlush := bufValid && bufReq.bits.isStore && doFlush

  //io.bufHaslqIdx        := bufReq.req.bits.lqIdx
  //io.bufHassqIdx        := bufReq.req.bits.sqIdx


}