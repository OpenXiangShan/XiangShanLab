package minixiangshan.backend.decode

import chisel3._
import chisel3.util._
import minixiangshan.config.{NSModule, Parameters}
import minixiangshan.frontend.CtrlFlowIO

class DecodeStage(implicit p: Parameters) extends NSModule {
  val io = IO(new Bundle {
    val in      = Vec(CtrlBlockWidth, Flipped(Decoupled(new CtrlFlowIO)))
    val out     = Vec(CtrlBlockWidth, Decoupled(new DecodedInst))
    val ratRead = Vec(CtrlBlockWidth, Output(new RATReadIO))
    val extInt  = Input(Bool())
    val flush   = Input(Bool())
  })
  diffDontTouch(io.out)

  // ===========================================================
  // Phase 1: 严格的流水级数据保持 (Input -> Register)
  // 按序同进同出：统一的级有效信号 + 独立的通道有效信号
  // ===========================================================
  val stgValid  = RegInit(false.B) // 标识整个流水级当前是否有指令块
  val laneValid = RegInit(VecInit(Seq.fill(CtrlBlockWidth)(false.B))) // 标识每一路是否实际装载了有效指令
  val stgData = RegInit(VecInit(Seq.fill(CtrlBlockWidth)(0.U.asTypeOf(new CtrlFlowIO))))

  // 1. 判定后端是否对"所有有效指令"都准备就绪
  // 核心逻辑：对于每一路，如果它是空的(!laneValid)，或者后端已经ready，就算这一路没卡顿。
  // 只有当所有路都不卡顿时，整个数据块才能统一发往下级。
  val outReadyAll = (0 until CtrlBlockWidth).map(i => !laneValid(i) || io.out(i).ready).reduce(_ && _)
  val outFire = stgValid && outReadyAll

  // 2. 判定当前级是否可以接收新数据
  // 只要当前级为空，或者当前级的数据能够全部成功发射，就可以接收新数据
  val stgReady = !stgValid || outFire

  // 3. 判定前端是否输入了有效数据
  // 前端只要有任意一路发出 valid，就认为有一组新数据需要打入
  val inValid = io.in.map(_.valid).reduce(_ || _)
  val inFire  = inValid && stgReady

  // 统一给出反压信号：所有路的 ready 严格保持一致，确保前端也是同进同出
  for (i <- 0 until CtrlBlockWidth) {
    io.in(i).ready := stgReady
  }

  // 4. 严格的状态转移
  when(io.flush) {
    stgValid := false.B
    for (i <- 0 until CtrlBlockWidth) {
      laneValid(i) := false.B
    }
  } .elsewhen(inFire) {
    stgValid := true.B
    for (i <- 0 until CtrlBlockWidth) {
      laneValid(i) := io.in(i).valid
      stgData(i)   := io.in(i).bits
    }
  } .elsewhen(outFire) {
    // 只有在没有新数据进入，且旧数据全部成功发射时，流水级才变为空泡
    stgValid := false.B
    for (i <- 0 until CtrlBlockWidth) {
      laneValid(i) := false.B
    }
  }


  // ===========================================================
  // Phase 2: 实例化译码器进行解码，并挂载级间输出与 RAT 请求
  // ===========================================================
  for (i <- 0 until CtrlBlockWidth) {
    val decoder = Module(new Decoder)
    
    // 1. 输入连接：将打入流水级寄存器的数据传给纯组合逻辑的 Decoder
    decoder.io.inData := stgData(i)
    decoder.io.extInt := io.extInt

    // 2. 下一级负载挂载
    io.out(i).valid := stgValid && laneValid(i)
    io.out(i).bits  := decoder.io.out

    // 3. 读 RAT 请求挂载：只在流水级有效且确实需要读时触发
    io.ratRead(i).rs1      := decoder.io.out.rs1
    io.ratRead(i).rs2      := decoder.io.out.rs2
    io.ratRead(i).hold1    := !outFire
    io.ratRead(i).hold2    := !outFire
  }
}
