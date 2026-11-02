package noc

import chisel3._

/** Four independent channels sharing only node numbering, clock, and reset. */
class QuadRing[A <: Data, B <: Data, C <: Data, D <: Data](
  params: RingParams,
  payload0: A,
  payload1: B,
  payload2: C,
  payload3: D
) extends Module {
  val io = IO(new Bundle {
    val channel0 = Vec(params.nodeCount, new RingPort(params, payload0))
    val channel1 = Vec(params.nodeCount, new RingPort(params, payload1))
    val channel2 = Vec(params.nodeCount, new RingPort(params, payload2))
    val channel3 = Vec(params.nodeCount, new RingPort(params, payload3))
  })

  val ring0 = Module(new Ring(params, payload0))
  val ring1 = Module(new Ring(params, payload1))
  val ring2 = Module(new Ring(params, payload2))
  val ring3 = Module(new Ring(params, payload3))

  io.channel0 <> ring0.io.nodes
  io.channel1 <> ring1.io.nodes
  io.channel2 <> ring2.io.nodes
  io.channel3 <> ring3.io.nodes
}
