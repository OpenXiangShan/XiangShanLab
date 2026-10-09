package minixiangshan.mem.L2cache

import chisel3._
import chisel3.util._

import minixiangshan.config._

/* ============================================================================
 *  L2 读端口仲裁器
 *
 *  两个只读 master（0：I-Cache，1：页表遍历器 PTW）共享 L2 的一个只读端口。
 *  两个 master 各自同一时刻最多只有一个未完成请求（I-Cache 的 miss/uncache
 *  状态机与 PTW 的状态机都满足该约束），因此只需记录当前响应对应的 owner
 *  即可完成响应回送。
 *
 *  仲裁策略：PTW 优先（它的请求量很小，且位于 load/store 的关键路径上）。
 *  被放弃的请求（cancel）会立即释放 owner，后续响应被丢弃。
 * ==========================================================================*/
class L2ReadArbiter(implicit p: Parameters) extends NSModule {
  val io = IO(new Bundle {
    /** 0: I-Cache，1: PTW */
    val m = Vec(2, Flipped(new L2NativeReadIO(1)))
    val s = new L2NativeReadIO(1)
  })

  val busy  = RegInit(false.B)
  val owner = RegInit(0.U(1.W))

  val req0 = io.m(0).req.valid
  val req1 = io.m(1).req.valid
  val sel1 = req1
  val sel0 = !req1 && req0
  val canSend = !busy

  io.s.req.valid := canSend && (req0 || req1)
  io.s.req.bits  := Mux(sel1, io.m(1).req.bits, io.m(0).req.bits)

  io.m(0).req.ready := canSend && sel0 && io.s.req.ready
  io.m(1).req.ready := canSend && sel1 && io.s.req.ready

  when(canSend && io.s.req.fire) {
    busy  := true.B
    owner := Mux(sel1, 1.U(1.W), 0.U(1.W))
  }

  // 响应回送
  io.m(0).resp.valid := io.s.resp.valid && busy && (owner === 0.U)
  io.m(1).resp.valid := io.s.resp.valid && busy && (owner === 1.U)
  io.m(0).resp.bits  := io.s.resp.bits
  io.m(1).resp.bits  := io.s.resp.bits

  io.s.resp.ready := Mux(!busy, true.B,
    Mux(owner === 1.U, io.m(1).resp.ready, io.m(0).resp.ready))

  when(io.s.resp.fire) { busy := false.B }

  // 取消：只允许当前 owner 是 I-Cache 时透传，并释放占用
  io.s.cancel := busy && (owner === 0.U) && io.m(0).cancel
  when(busy && (owner === 0.U) && io.m(0).cancel) { busy := false.B }
}
