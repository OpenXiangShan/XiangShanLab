package minixiangshan.mem
 
import chisel3._
import chisel3.util._
import minixiangshan.config._
import minixiangshan.config.ExcType._
import minixiangshan.backend.dispatch._
import minixiangshan.backend.decode._
import minixiangshan.backend.rename._
import minixiangshan.backend.execute._
import minixiangshan.mmu._
import minixiangshan.mem.dcache.DCache
import minixiangshan.mem.L2cache._
 
class ExeMmuResult(implicit p: Parameters) extends NSBundle {
  val exeRes    = new ExeResult
  val mmuRes    = Flipped(new MmuToSqResp)
  val scSuccess = Bool()
}
 
class MemoryBlock(implicit p: Parameters) extends NSModule {
 
  val io = IO(new Bundle {
    val lsEnq = new Bundle {
      val req       = Flipped(Valid(new LsEnqEntry))
      val toLsqData = Flipped(new RenamedInst)
      val lqHasEntries = Output(UInt(log2Ceil(LqSize + 1).W))
      val sqHasEntries = Output(UInt(log2Ceil(SqSize + 1).W))
    }
 
    val fromExeMmuResult = Flipped(Decoupled(new ExeMmuResult))
    val fromExeResult = Flipped(Decoupled(new ExeResult))
 
    val toWbResult = Vec(2, Decoupled(new ExeResult))
 
    val lqEnqPtr = Output(UInt(log2Ceil(LqSize).W))
    val sqEnqPtr = Output(UInt(log2Ceil(SqSize).W))
 
    val robCommit = Vec(CommitWidth, new Bundle {
      val valid = Input(Bool())
      val sqIdx = Input(UInt(log2Ceil(SqSize).W))
    })
    // True when every older committed store has completed.  Speculative
    // younger stores are intentionally excluded to avoid a ROB/SQ deadlock.
    val storeQueueEmpty = Output(Bool())
    val fenceIReq = Input(Bool())
    val fenceIReady = Output(Bool())

    val l2 = new L2NativeMasterIO(1)
 
    val redirect = Flipped(Valid(new Bundle {
      val robIdx = new RobPtr(RobSize)
    }))
 
    val redirectInfo = Flipped(ValidIO(new redirectInfoToModule))
    val robHead         = Input(Valid(new RobPtr(RobSize)))
  })
 
  val loadQueue  = Module(new LoadQueue)
  val storeQueue = Module(new StoreQueue)
  storeQueue.io.redirectInfo <> io.redirectInfo
  loadQueue.io.redirectInfo  <> io.redirectInfo
 
  // ── Dispatch 入队路由 ──
  loadQueue.io.enq.valid  := io.lsEnq.req.valid && io.lsEnq.req.bits.isLoad
  loadQueue.io.enq.robIdx := io.lsEnq.req.bits.robIdx
  loadQueue.io.enq.sqIdx  := io.lsEnq.req.bits.sqIdx.value
  loadQueue.io.enq.pc     := io.lsEnq.toLsqData.pc
  loadQueue.io.enq.pdst   := io.lsEnq.toLsqData.pdst
  loadQueue.io.enq.rfWen  := io.lsEnq.toLsqData.ctrl.rfWen
  loadQueue.io.enq.lsuOp  := io.lsEnq.toLsqData.ctrl.lsuOp
  loadQueue.io.enq.fuType := io.lsEnq.toLsqData.ctrl.fuType
  loadQueue.io.enq.cacheOp  := io.lsEnq.toLsqData.cacheOp
 
  storeQueue.io.enq.valid  := io.lsEnq.req.valid && io.lsEnq.req.bits.isStore
  storeQueue.io.enq.robIdx := io.lsEnq.req.bits.robIdx
  storeQueue.io.enq.lqIdx  := io.lsEnq.req.bits.lqIdx.value
  storeQueue.io.enq.pc     := io.lsEnq.toLsqData.pc
  storeQueue.io.enq.pdst   := io.lsEnq.toLsqData.pdst
  storeQueue.io.enq.rfWen  := io.lsEnq.toLsqData.ctrl.rfWen
  storeQueue.io.enq.lsuOp  := io.lsEnq.toLsqData.ctrl.lsuOp
  storeQueue.io.enq.fuType := io.lsEnq.toLsqData.ctrl.fuType
 
  io.lsEnq.lqHasEntries := loadQueue.io.lqHasEntries
  io.lsEnq.sqHasEntries := storeQueue.io.sqHasEntries
 
  io.lqEnqPtr := loadQueue.io.enqPtr
  io.sqEnqPtr := storeQueue.io.enqPtr
 
  // ================================================================
  //  SQ → LQ 排序信息（★ 新增 sqForwardInfo 连线 ★）
  // ================================================================
  loadQueue.io.sqOldestRobIdx := storeQueue.io.oldestRobIdx
  loadQueue.io.sqEmpty        := storeQueue.io.sqEmpty
  io.storeQueueEmpty          := storeQueue.io.committedStoreEmpty
  loadQueue.io.sqForwardInfo  <> storeQueue.io.sqForwardInfo  // ★ 新增

  loadQueue.io.robHead := io.robHead
 
  // ── 执行单元地址/数据通道路由 ──
  val addrChannel = io.fromExeMmuResult
  val addrFire    = addrChannel.fire
  val addrUop     = addrChannel.bits.exeRes.uop
 
  val dataChannel = io.fromExeResult
  val dataFire    = dataChannel.fire
  val dataUop     = dataChannel.bits.uop
 
  val mmuError = addrChannel.bits.mmuRes.error
  val excpIn   = addrChannel.bits.exeRes.uop.excp
  val excp     = Wire(new ExceptionBundle)

  val isStoreAccess = addrUop.ctrl.memWrite
  excp.excpVec := excp.mergeMany(
    base = excpIn.excpVec,
    (mmuError.misalign    && !isStoreAccess) -> LALIGN,
    (mmuError.misalign    &&  isStoreAccess) -> SALIGN,
    (mmuError.pageFault   && !isStoreAccess) -> LPAGE,
    (mmuError.pageFault   &&  isStoreAccess) -> SPAGE,
    (mmuError.accessFault && !isStoreAccess) -> LACCESS,
    (mmuError.accessFault &&  isStoreAccess) -> SACCESS
  )
  excp.intrCode := 0.U
 
  loadQueue.io.addrWrite.valid     := addrFire && addrUop.ctrl.memRead
  loadQueue.io.addrWrite.idx       := addrUop.lqIdx.value
  loadQueue.io.addrWrite.vaddr     := addrChannel.bits.exeRes.data
  loadQueue.io.addrWrite.paddr     := addrChannel.bits.mmuRes.paddr
  loadQueue.io.addrWrite.cacheable := addrChannel.bits.mmuRes.cacheable
  loadQueue.io.addrWrite.excp      := excp
  loadQueue.io.committedStoreEmpty := storeQueue.io.committedStoreEmpty
 
  storeQueue.io.addrWrite.valid     := addrFire && addrUop.isSta
  storeQueue.io.addrWrite.idx       := addrUop.sqIdx.value
  storeQueue.io.addrWrite.vaddr     := addrChannel.bits.exeRes.data
  storeQueue.io.addrWrite.paddr     := addrChannel.bits.mmuRes.paddr
  storeQueue.io.addrWrite.cacheable := addrChannel.bits.mmuRes.cacheable
  storeQueue.io.addrWrite.excp      := excp
  storeQueue.io.addrWrite.scSuccess := addrChannel.bits.scSuccess
 
  storeQueue.io.dataWrite.valid := dataFire && dataUop.isStd
  storeQueue.io.dataWrite.idx   := dataUop.sqIdx.value
  storeQueue.io.dataWrite.data  := dataChannel.bits.data
 
  io.fromExeResult.ready    := true.B
  io.fromExeMmuResult.ready := true.B
 
  // ── DCache 接口直连 ──
  val dcache = Module(new DCache)
 
  dcache.io.loadReq  <> loadQueue.io.dcacheReq
  dcache.io.loadResp <> loadQueue.io.dcacheResp
 
  dcache.io.storeReq <> storeQueue.io.dcacheReq
  dcache.io.storeAck <> storeQueue.io.storeAck
  dcache.io.fenceReq := io.fenceIReq
  io.fenceIReady := dcache.io.fenceDone
  dcache.io.l2 <> io.l2
  dcache.io.redirectInfo <> io.redirectInfo
 
  diffDontTouch(loadQueue.io.dcacheReq)
  diffDontTouch(storeQueue.io.dcacheReq)
 
  // ── ROB 提交直连 ──
  storeQueue.io.robCommit <> io.robCommit
 
  // ── 后端写回输出 ──
  io.toWbResult(0) <> loadQueue.io.outResult
  io.toWbResult(1) <> storeQueue.io.outResult
}
