package chi

import chisel3._
import chisel3.util.Decoupled

/** SNP has no TgtID, so its sender supplies a separate routing destination. */
class RoutedSnp(params: ChiParams) extends Bundle {
  val dst = UInt(params.nodeIdWidth.W)
  val payload = new SnpFlit(params)
}

/** Endpoint-side directions. All channels are transport ports, independent of role. */
class ChiPort(params: ChiParams) extends Bundle {
  val reqTx = Decoupled(new ReqFlit(params))
  val reqRx = Flipped(Decoupled(new ReqFlit(params)))
  val rspTx = Decoupled(new RspFlit(params))
  val rspRx = Flipped(Decoupled(new RspFlit(params)))
  val snpTx = Decoupled(new RoutedSnp(params))
  val snpRx = Flipped(Decoupled(new SnpFlit(params)))
  val datTx = Decoupled(new DatFlit(params))
  val datRx = Flipped(Decoupled(new DatFlit(params)))
}
