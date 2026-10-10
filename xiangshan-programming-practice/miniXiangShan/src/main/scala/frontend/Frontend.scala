package minixiangshan.frontend
 
import chisel3._
import chisel3.util._
import minixiangshan.config.Parameters
import minixiangshan.config._
import minixiangshan.config.NSModule
import minixiangshan.config.NSBundle
import minixiangshan.frontend.icache._
import minixiangshan.mem.L2cache.L2NativeReadIO
import minixiangshan.backend.execute._
 
class Frontend(implicit p: Parameters) extends NSModule {
  val io = IO(new Bundle {
    // ========== 到后端的指令输出 ==========
    val out          = Vec(CtrlBlockWidth, Decoupled(new CtrlFlowIO))
 
    // ========== 后端到前端的反馈 ==========
    val redirect       = Flipped(new RedirectIO)
    val bpuUpdateBr    = Input(new BpuUpdateReq)
    val idle           = Input(Bool())
    val redirectInfo    = Flipped ( ValidIO( new redirectInfoToModule )   ) // 误预测重定向
    //MMU
    val mmu = new MMURead
 
    // ========== ICache 到 L2 的原生读接口 ==========
    val l2_read = new L2NativeReadIO(1)
  })
 
  io.out.foreach(_.bits := DontCare)
 
  // ==================== 子模块实例化 ====================
  val bpu       = Module(new BPU)
  val ifu      = Module(new IFU)

  // 预测信息队列(跟踪ICache流水线中的BPU预测)
  val bpuInfoQueue = Module(new FlushableQueue(new bpuInfoBundle, entries = 2))
  val icache   = Module(new ICache) //it's OK

  icache.io.mmu <> io.mmu

  val predecoder = Module(new Predecoder)
  val ibuffer  = Module(new IBF)
  
  // 【修改】：解耦有效信号。因为重定向信号被打了一拍，发送重定向这拍时 io.out.valid 会被阻断。
  val frontendRedirectValid  = predecoder.io.out.bits.frontendRedirect.valid
  val frontendRedirectTarget = predecoder.io.out.bits.frontendRedirect.target
  val backendRedirectValid = io.redirectInfo.valid && io.redirectInfo.bits.doRedirect
  
  diffDontTouch(frontendRedirectValid)
  diffDontTouch(frontendRedirectTarget)
  diffDontTouch(backendRedirectValid)

  ifu.io.frontendRedirect.valid := frontendRedirectValid
  ifu.io.frontendRedirect.target := frontendRedirectTarget
  ifu.io.idle := io.idle
  // BPU接口
  bpu.io.predictReq := ifu.io.predictReq
  bpu.io.predictFire := ifu.io.predictFire

  ifu.io.predictResp := bpu.io.predictResp

  bpu.io.update_pd := predecoder.io.out.bits.bpuUpdate
  // 只有当预译码输出有效时才更新
  bpu.io.update_pd.valid := predecoder.io.out.bits.bpuUpdate.valid &&
                             predecoder.io.out.fire
 
  // 来自后端的精确反馈(暂不连接, 由core_top提供)
  bpu.io.update_br          := io.bpuUpdateBr
 
  // RAS恢复(后端redirect时)
  bpu.io.rasRestore     := backendRedirectValid
  bpu.io.rasRestoreTop  := Mux(backendRedirectValid,
                                io.redirect.rtype,  // 复用rtype字段传递rasTop
                                0.U)
 
  // ==================== IFU ↔ ICache ====================
  // 请求
  icache.io.cpu_req.valid := ifu.io.icache_req.valid
  icache.io.cpu_req.bits.addr := ifu.io.icache_req.addr
  ifu.io.icache_req.ready := icache.io.cpu_req.ready
  icache.io.redirect := ifu.io.icache_req.flush
  icache.io.invalidate := io.redirectInfo.bits.invalidIcache

  // ==================== IFU ↔ bpuQ ====================
  bpuInfoQueue.io.enq <> ifu.io.bpuInfoQueuEnq
  bpuInfoQueue.io.flush := backendRedirectValid || frontendRedirectValid

  // ==================== bpuQ ↔ pd ====================
  val hasBpuInfo = bpuInfoQueue.io.deq.valid
  val processResp = icache.io.icache_resp.valid && hasBpuInfo
  bpuInfoQueue.io.deq.ready := processResp && predecoder.io.icacheResp.ready

  predecoder.io.bpuInfo      := bpuInfoQueue.io.deq.bits
  predecoder.io.bpuInfoValid := hasBpuInfo

  // ==================== icache ↔ pd ====================
  icache.io.icache_resp.ready := predecoder.io.icacheResp.ready
  predecoder.io.icacheResp <> icache.io.icache_resp

  //使用一下BPU的信息
  for (i <- 0 until fetchWidth) {
      predecoder.io.icacheResp.bits.instvalids(i) := icache.io.icache_resp.bits.instvalids(i) && hasBpuInfo &&
                                                    ( 
                                                      !bpuInfoQueue.io.deq.bits.taken || 
                                                      (bpuInfoQueue.io.deq.bits.taken && i.U <= bpuInfoQueue.io.deq.bits.takenOffset)
                                                    )
  }

  predecoder.io.flush         := backendRedirectValid || frontendRedirectValid

  // ==================== pd → IBuffer ====================
  ibuffer.io.in <> predecoder.io.out
 
  // ==================== IBuffer → 后端 ====================
  io.out <> ibuffer.io.out
  
  // ==================== 后端反馈 → IFU ====================
  ifu.io.redirectInfo       <> io.redirectInfo
 
  // ==================== IBuffer flush ====================
  ibuffer.io.flush := backendRedirectValid
 
  // ==================== L2 原生读接口 ====================
  io.l2_read <> icache.io.l2_read
}
