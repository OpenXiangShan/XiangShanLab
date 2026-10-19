package chi

import chisel3.experimental.SourceInfo
import noc.RingParams
import org.chipsalliance.cde.config.Parameters
import org.chipsalliance.diplomacy.nodes._

sealed trait ChiRole
object ChiRole {
  case object RN extends ChiRole
  case object HN extends ChiRole
  case object SN extends ChiRole
}

case class ChiEndpointParams(nodeId: Int, role: ChiRole) {
  require(nodeId >= 0, "node IDs must be nonnegative")
}

case class ChiNoCParams(chi: ChiParams = ChiParams(), ring: RingParams = RingParams())

case class ChiEdgeParams(endpoint: ChiEndpointParams, network: ChiNoCParams) {
  require(BigInt(endpoint.nodeId) < (BigInt(1) << network.chi.nodeIdWidth),
    "endpoint node ID does not fit the negotiated width")
}

/** Source/sink describe parameter flow; each hardware edge carries both TX and RX. */
object ChiNodeImp extends SimpleNodeImp[ChiEndpointParams, ChiNoCParams, ChiEdgeParams, ChiPort] {
  def edge(endpoint: ChiEndpointParams, network: ChiNoCParams,
    p: Parameters, sourceInfo: SourceInfo): ChiEdgeParams = ChiEdgeParams(endpoint, network)

  def bundle(edge: ChiEdgeParams): ChiPort = new ChiPort(edge.network.chi)

  def render(edge: ChiEdgeParams): RenderedEdge =
    RenderedEdge(colour = "#336699", label = s"${edge.endpoint.role}:${edge.endpoint.nodeId}")
}
