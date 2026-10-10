package minixiangshan.frontend
 
import chisel3._
import chisel3.util._
import minixiangshan.config.Parameters
import minixiangshan.config._
import minixiangshan.config.NSModule
import minixiangshan.config.NSBundle
import minixiangshan.frontend.icache._
import minixiangshan.backend.execute._
 
class IFU(implicit p: Parameters) extends NSModule {
  val io = IO(new Bundle {
    // 前后端后端重定向输入
    //val redirect = Flipped(new RedirectIO)
    val frontendRedirect = Input(new FrontendRedirect)
    val redirectInfo    = Flipped ( ValidIO( new redirectInfoToModule )   ) // 误预测重定向
    val idle            = Input(Bool())


    
    // BPU接口
    val predictReq   = Output(new BpuPredictReq)
    val predictFire  = Output(Bool())   // 预测结果被实际使用(请求成功发射到ICache)
    val predictResp  = Input(new BpuPredictResp)
    // ICache接口
    val icache_req = new Bundle {
      val addr  = Output(UInt(32.W))
      val valid = Output(Bool())
      val ready = Input(Bool())
      val flush  = Output(Bool())
    }

    val bpuInfoQueuEnq   = DecoupledIO( new bpuInfoBundle )

  })
  // ==================== PC生成单元 ====================
  val pcReg    = RegInit(0x1BFFFFFC.U(32.W))
  val pcValid  = RegInit(false.B)
  pcValid := RegNext(true.B)
  diffDontTouch(pcValid)
  
 
  // ==================== 计算下一个PC ====================
  val blockOffset   = pcReg(blockOffBits - 1, 0)
  val bytesInLine   = blockBytes.U - blockOffset
  val instsInLine   = bytesInLine >> 2
  val crossLine     = instsInLine < fetchWidth.U
  val seqPC         = Mux(crossLine,
                          Cat(pcReg(31, blockOffBits) + 1.U, 0.U(blockOffBits.W)),
                          pcReg + (fetchWidth * 4).U)
  val fallThroughPC = seqPC
 

  // ==================== 接收预测的结果 ====================
  val bpuTaken  = io.predictResp.taken
  val bpuTarget = io.predictResp.target
  val bpuMeta   = io.predictResp.meta
 
  // 前端重定向
  val frontendRedirect =  io.frontendRedirect
  // 后端重定向
  val backendRedirectValid  = io.redirectInfo.valid && io.redirectInfo.bits.doRedirect
  val backendRedirectTarget = io.redirectInfo.bits.target
 
  // 下一拍PC选择 (优先级: 后端redirect > 前端redirect > BPU预测 > 顺序)
  val nextPC = Mux(  backendRedirectValid   ,  backendRedirectTarget   ,
               Mux(  frontendRedirect.valid ,  frontendRedirect.target ,
               Mux(  bpuTaken               ,  bpuTarget               ,
                                               seqPC                   )))

    // pcReg将要进入Icache条件
  val pc_fire = pcValid && !io.idle &&
    (io.icache_req.ready && io.bpuInfoQueuEnq.ready ) && !backendRedirectValid && !frontendRedirect.valid
 
  // ==================== 发起BPU的预测请求 ====================
  io.predictReq.nextPC := nextPC
  io.predictReq.crossLine := crossLine //如果当前的PC是横跨了Cache行的，那就不能使用bup的第二个mem的结果了
  io.predictReq.pc := pcReg
  io.predictReq.rdBpu := pc_fire || frontendRedirect.valid || backendRedirectValid
  //                   阻塞时保持原值                 重定向时必读

  // 通知BPU预测结果被使用
  // RAS相关
  io.predictFire := pc_fire
  
  val pcRegRedirect = backendRedirectValid || frontendRedirect.valid
  // 更新PC
  // 什么时候pc可以变了？
  // 1.当当前pc被icahe成功接收之后
  // 2.当重定向来了之后，流水线最尖端的位置是没有flush的
  //   所以重定向来了之后强制变pc
  when(pc_fire || pcRegRedirect) {
    pcReg := nextPC
  }
 
  // ==================== ICache请求 ====================
  io.icache_req.addr  := pcReg
  io.icache_req.valid := !io.idle && pcReg =/= 0x1BFFFFFC.U &&
                         !backendRedirectValid &&
                         !frontendRedirect.valid //当重定向来了之后，不给Cache发当前请求
  io.icache_req.flush  := backendRedirectValid || frontendRedirect.valid
 
  
  val currentPredInfo = Wire(new bpuInfoBundle)
  currentPredInfo.pc          := pcReg
  currentPredInfo.fallThrough := fallThroughPC
  currentPredInfo.taken       := bpuTaken
  currentPredInfo.target      := bpuTarget
  currentPredInfo.takenOffset := io.predictResp.takenOffset
  currentPredInfo.meta        := bpuMeta
  diffDontTouch(currentPredInfo)
  // ==================== bpuInfoQueue请求 ====================
  io.bpuInfoQueuEnq.valid := pc_fire && pcReg =/= 0x1BFFFFFC.U
  io.bpuInfoQueuEnq.bits  := currentPredInfo

}
