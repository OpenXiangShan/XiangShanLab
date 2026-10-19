package noc

import chisel3._

/** A destination in [0, nodeCount) and an opaque, caller-defined payload. */
class RingFlit[T <: Data](params: RingParams, payloadType: T) extends Bundle {
  val dst = UInt(params.dstWidth.W)
  val payload = payloadType.cloneType
}
