package noc

import chisel3._
import chisel3.util.Decoupled

/** Local endpoint interface, viewed from the network. */
class RingPort[T <: Data](params: RingParams, payloadType: T) extends Bundle {
  val inject = Flipped(Decoupled(new RingFlit(params, payloadType)))
  val eject = Decoupled(new RingFlit(params, payloadType))
}
