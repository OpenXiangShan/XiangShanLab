package noc

import chisel3._

/** One channel, connected in the direction 0 -> 1 -> ... -> 0. */
class Ring[T <: Data](params: RingParams, payloadType: T) extends Module {
  val io = IO(new Bundle {
    val nodes = Vec(params.nodeCount, new RingPort(params, payloadType))
  })

  val stops = Seq.tabulate(params.nodeCount) { nodeId =>
    Module(new RingStop(params, nodeId, payloadType))
  }

  for (nodeId <- 0 until params.nodeCount) {
    io.nodes(nodeId) <> stops(nodeId).io.local

    val nextNodeId = (nodeId + 1) % params.nodeCount
    stops(nextNodeId).io.ringIn <> stops(nodeId).io.ringOut
    stops(nodeId).io.creditReturned := stops(nextNodeId).io.creditFreed
  }
}
