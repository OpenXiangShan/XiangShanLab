package chi

import chisel3._
import circt.stage.ChiselStage
import org.chipsalliance.cde.config.Parameters
import org.chipsalliance.diplomacy.lazymodule._
import org.chipsalliance.diplomacy.nodes._

/** Expose diplomatic endpoint nodes as IO for external drivers; no endpoint state machines. */
class ChiNoCTop(val params: ChiNoCParams, val endpoints: Seq[ChiEndpointParams])(
  implicit p: Parameters) extends LazyModule {
  val network = LazyModule(new ChiNoC(params))
  val endpointNodes = endpoints.map(endpoint => new SourceNode(ChiNodeImp)(Seq(endpoint)))
  endpointNodes.foreach(node => network.node := node)
  lazy val module = new ChiNoCTopModule(this)
}

class ChiNoCTopModule(outer: ChiNoCTop) extends LazyModuleImp(outer) {
  val io = IO(new Bundle {
    val ports = Vec(outer.endpoints.size, Flipped(new ChiPort(outer.params.chi)))
  })
  for ((node, port) <- outer.endpointNodes.zip(io.ports)) {
    node.out.head._1 <> port
  }
}

object ChiNoCMain {
  def main(args: Array[String]): Unit = {
    implicit val p: Parameters = Parameters.empty
    val endpoints = Seq(ChiEndpointParams(0, ChiRole.RN), ChiEndpointParams(1, ChiRole.HN),
      ChiEndpointParams(2, ChiRole.RN), ChiEndpointParams(3, ChiRole.SN))
    ChiselStage.emitSystemVerilogFile(LazyModule(new ChiNoCTop(ChiNoCParams(), endpoints)).module, args)
  }
}
