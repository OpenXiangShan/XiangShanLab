package chi

import circt.stage.ChiselStage
import noc.{QuadRing, RingParams}

/** Elaborate the existing four-ring transport with CHI subset payloads. */
object ChiFlitMain {
  def main(args: Array[String]): Unit = {
    val params = ChiParams()
    ChiselStage.emitSystemVerilogFile(
      new QuadRing(RingParams(), new ReqFlit(params), new RspFlit(params),
        new SnpFlit(params), new DatFlit(params)),
      args
    )
  }
}
