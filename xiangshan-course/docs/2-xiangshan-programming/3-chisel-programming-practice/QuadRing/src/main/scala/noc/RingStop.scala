package noc

import chisel3._
import chisel3.util._

/** Buffer ring input, eject local destinations, and prioritize forwards at ring output. */
class RingStop[T <: Data](params: RingParams, nodeId: Int, payloadType: T) extends Module {
  require(nodeId >= 0 && nodeId < params.nodeCount, "nodeId must be a valid ring position")

  val io = IO(new Bundle {
    val ringIn = Flipped(Decoupled(new RingFlit(params, payloadType)))
    val ringOut = Decoupled(new RingFlit(params, payloadType))
    val creditReturned = Input(Bool()) // Downstream freed one buffer entry.
    val creditFreed = Output(Bool()) // This buffer freed one entry for upstream.
    val local = new RingPort(params, payloadType)
  })

  // Only ring input enters the FIFO. Local injection waits outside this buffer.
  val buffer = Module(new Queue(new RingFlit(params, payloadType), entries = params.bufferDepth))
  buffer.io.enq <> io.ringIn

  // Each outgoing transfer reserves one entry in the downstream buffer.
  val creditWidth = log2Ceil(params.bufferDepth + 1)
  val downstreamCredits = RegInit(params.bufferDepth.U(creditWidth.W))
  val canForward = downstreamCredits >= 1.U
  val canInject = downstreamCredits >= 2.U

  // Only the FIFO head can leave; backpressure preserves its contents and order.
  val isLocal = buffer.io.deq.bits.dst === nodeId.U
  io.local.eject.valid := buffer.io.deq.valid && isLocal
  io.local.eject.bits := buffer.io.deq.bits
  buffer.io.deq.ready := Mux(isLocal, io.local.eject.ready, io.ringOut.ready && canForward)
  io.creditFreed := buffer.io.deq.fire

  // Arbitrate at ring output: a buffered forward wins over pending local injection.
  // Decoupled permits changing the selected source while stalled; only valid && ready transfers.
  val forward = buffer.io.deq.valid && !isLocal
  io.ringOut.valid := Mux(forward, canForward, io.local.inject.valid && canInject)
  io.ringOut.bits := Mux(forward, buffer.io.deq.bits, io.local.inject.bits)
  io.local.inject.ready := io.ringOut.ready && !forward && canInject

  // A returned credit becomes available next cycle. Send + return cancel out.
  val sent = io.ringOut.fire
  when(sent && !io.creditReturned) {
    downstreamCredits := downstreamCredits - 1.U
  }.elsewhen(io.creditReturned && !sent) {
    assert(downstreamCredits < params.bufferDepth.U, "returned credit exceeds buffer depth")
    downstreamCredits := downstreamCredits + 1.U
  }
  assert(downstreamCredits <= params.bufferDepth.U, "downstream credit count overflow")
}
