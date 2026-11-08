package chi

import chisel3._
import chisel3.util.Decoupled

/** Local whole-line command. CHI opcodes are selected by the RN. */
class RnRequest(params: ChiParams) extends Bundle {
  val write = Bool()
  val addr = UInt(params.addrWidth.W)
  val data = UInt(512.W)
  val txnID = UInt(params.txnIdWidth.W)
}

class RnResponse(params: ChiParams) extends Bundle {
  val txnID = UInt(params.txnIdWidth.W)
  val data = UInt(512.W)
}

class RnPort(params: ChiParams) extends Bundle {
  val request = Flipped(Decoupled(new RnRequest(params)))
  val response = Decoupled(new RnResponse(params))
}
