package chi

import chisel3._
import chisel3.util._
import noc.{QuadRing, RingPort}
import org.chipsalliance.cde.config.Parameters
import org.chipsalliance.diplomacy.lazymodule._
import org.chipsalliance.diplomacy.nodes._

/** Diplomatic CHI transport, reusing the four independent generic rings. */
class ChiNoC(val params: ChiNoCParams)(implicit p: Parameters) extends LazyModule {
  val node = new SinkNode(ChiNodeImp)(Seq.fill(params.ring.nodeCount)(params))
  lazy val module = new ChiNoCModule(this)
}

class ChiNoCModule(outer: ChiNoC) extends LazyModuleImp(outer) {
  val endpoints = outer.node.in.map(_._2.endpoint)
  private val ids = endpoints.map(_.nodeId)
  require(ids.distinct.size == ids.size, "endpoint node IDs must be unique")

  private val params = outer.params
  private val ring = Module(new QuadRing(params.ring,
    new ReqFlit(params.chi), new RspFlit(params.chi),
    new SnpFlit(params.chi), new DatFlit(params.chi)))

  // Connection order determines physical ring positions; CHI node IDs may be sparse/permuted.
  private def route(target: UInt, valid: Bool): UInt = {
    val matches = ids.map(id => target === id.U(params.chi.nodeIdWidth.W))
    when(valid) { assert(matches.reduce(_ || _), "unknown CHI target node ID") }
    MuxCase(0.U(params.ring.dstWidth.W), matches.zipWithIndex.map {
      case (hit, index) => hit -> index.U(params.ring.dstWidth.W)
    })
  }

  private def connect[T <: Data](tx: DecoupledIO[T], rx: DecoupledIO[T],
    port: RingPort[T], target: UInt): Unit = {
    port.inject.valid := tx.valid
    port.inject.bits.dst := route(target, tx.valid)
    port.inject.bits.payload := tx.bits
    tx.ready := port.inject.ready
    rx.valid := port.eject.valid
    rx.bits := port.eject.bits.payload
    port.eject.ready := rx.ready
  }

  for (((port, _), index) <- outer.node.in.zipWithIndex) {
    connect(port.reqTx, port.reqRx, ring.io.channel0(index), port.reqTx.bits.tgtID)
    connect(port.rspTx, port.rspRx, ring.io.channel1(index), port.rspTx.bits.tgtID)
    connect(port.datTx, port.datRx, ring.io.channel3(index), port.datTx.bits.tgtID)

    val snp = ring.io.channel2(index)
    snp.inject.valid := port.snpTx.valid
    snp.inject.bits.dst := route(port.snpTx.bits.dst, port.snpTx.valid)
    snp.inject.bits.payload := port.snpTx.bits.payload
    port.snpTx.ready := snp.inject.ready
    port.snpRx.valid := snp.eject.valid
    port.snpRx.bits := snp.eject.bits.payload
    snp.eject.ready := port.snpRx.ready
  }
}
