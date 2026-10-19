package chi

import chisel3._

/** Internal payloads, not the complete CHI wire layout. Routing uses RingFlit.dst. */
class ReqFlit(params: ChiParams) extends Bundle {
  val tgtID = UInt(params.nodeIdWidth.W)
  val srcID = UInt(params.nodeIdWidth.W)
  val txnID = UInt(params.txnIdWidth.W)
  val opcode = UInt(6.W)
  val addr = UInt(params.addrWidth.W)
  val allowRetry = Bool()
  val pCrdType = UInt(4.W)
}

class RspFlit(params: ChiParams) extends Bundle {
  val tgtID = UInt(params.nodeIdWidth.W)
  val srcID = UInt(params.nodeIdWidth.W)
  val txnID = UInt(params.txnIdWidth.W)
  val opcode = UInt(4.W)
  val resp = UInt(3.W)
  val dbID = UInt(params.txnIdWidth.W)
  val pCrdType = UInt(4.W)
}

class SnpFlit(params: ChiParams) extends Bundle {
  val srcID = UInt(params.nodeIdWidth.W)
  val txnID = UInt(params.txnIdWidth.W)
  val opcode = UInt(5.W)
  // This subset carries a full byte address, including the zero line offset.
  val addr = UInt(params.addrWidth.W)
}

class DatFlit(params: ChiParams) extends Bundle {
  val tgtID = UInt(params.nodeIdWidth.W)
  val srcID = UInt(params.nodeIdWidth.W)
  val txnID = UInt(params.txnIdWidth.W)
  val opcode = UInt(3.W)
  val resp = UInt(3.W)
  val dbID = UInt(params.txnIdWidth.W)
  val data = UInt(512.W)
}
