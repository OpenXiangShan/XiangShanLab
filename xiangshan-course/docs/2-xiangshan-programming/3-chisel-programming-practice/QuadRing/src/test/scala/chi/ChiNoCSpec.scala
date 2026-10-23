package chi

import chisel3._
import chisel3.reflect.DataMirror
import chisel3.simulator.scalatest.ChiselSim
import chisel3.util.DecoupledIO
import circt.stage.ChiselStage
import noc.RingParams
import org.chipsalliance.cde.config.Parameters
import org.chipsalliance.diplomacy.lazymodule.LazyModule
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

class ChiNoCSpec extends AnyFreeSpec with Matchers with ChiselSim {
  private implicit val p: Parameters = Parameters.empty
  private val defaultEndpoints = Seq(ChiEndpointParams(0, ChiRole.RN), ChiEndpointParams(1, ChiRole.HN),
    ChiEndpointParams(2, ChiRole.RN), ChiEndpointParams(3, ChiRole.SN))
  private val configs = Seq(
    ChiNoCParams() -> defaultEndpoints,
    ChiNoCParams(ChiParams(3, 7, 32), RingParams(3, 5)) ->
      Seq(ChiEndpointParams(6, ChiRole.SN), ChiEndpointParams(1, ChiRole.RN), ChiEndpointParams(4, ChiRole.HN))
  )

  "negotiated edges retain endpoint identities, roles, widths, and handshake directions" in {
    for ((params, endpoints) <- configs) {
      ChiselStage.emitCHIRRTL {
        val top = LazyModule(new ChiNoCTop(params, endpoints))
        val dut = top.module
        top.network.node.edges.in.map(_.endpoint) shouldBe endpoints
        top.network.node.edges.in.map(_.network) shouldBe Seq.fill(endpoints.size)(params)
        top.endpointNodes.map(_.edges.out.head.network) shouldBe Seq.fill(endpoints.size)(params)
        for (port <- dut.io.ports) {
          def checkTx[T <: Data](tx: DecoupledIO[T]): Unit = {
            DataMirror.directionOf(tx.valid) shouldBe ActualDirection.Input
            DataMirror.directionOf(tx.ready) shouldBe ActualDirection.Output
          }
          def checkRx[T <: Data](rx: DecoupledIO[T]): Unit = {
            DataMirror.directionOf(rx.valid) shouldBe ActualDirection.Output
            DataMirror.directionOf(rx.ready) shouldBe ActualDirection.Input
          }
          checkTx(port.reqTx)
          checkTx(port.rspTx)
          checkTx(port.snpTx)
          checkTx(port.datTx)
          checkRx(port.reqRx)
          checkRx(port.rspRx)
          checkRx(port.snpRx)
          checkRx(port.datRx)
          DataMirror.directionOf(port.reqTx.bits.tgtID) shouldBe ActualDirection.Input
          DataMirror.directionOf(port.datRx.bits.data) shouldBe ActualDirection.Output
          port.reqTx.bits.tgtID.getWidth shouldBe params.chi.nodeIdWidth
          port.reqRx.bits.txnID.getWidth shouldBe params.chi.txnIdWidth
          port.snpTx.bits.dst.getWidth shouldBe params.chi.nodeIdWidth
          port.snpRx.bits.addr.getWidth shouldBe params.chi.addrWidth
          port.datRx.bits.data.getWidth shouldBe 512
        }
        dut
      }
    }
  }

  "negative endpoint IDs fail before graph construction" in {
    an[IllegalArgumentException] should be thrownBy ChiEndpointParams(-1, ChiRole.RN)
  }

  "duplicate endpoint IDs are rejected" in {
    val endpoints = defaultEndpoints.updated(3, ChiEndpointParams(0, ChiRole.SN))
    val error = intercept[IllegalArgumentException] {
      ChiselStage.emitCHIRRTL(LazyModule(new ChiNoCTop(ChiNoCParams(), endpoints)).module)
    }
    error.getMessage should include("unique")
  }

  "endpoint IDs that exceed the negotiated width are rejected" in {
    val endpoints = defaultEndpoints.updated(3, ChiEndpointParams(4, ChiRole.SN))
    val error = intercept[IllegalArgumentException] {
      ChiselStage.emitCHIRRTL(LazyModule(new ChiNoCTop(ChiNoCParams(), endpoints)).module)
    }
    error.getMessage should include("negotiated width")
  }

  "the graph requires one endpoint connection per ring stop" in {
    an[IllegalArgumentException] should be thrownBy {
      ChiselStage.emitCHIRRTL(LazyModule(new ChiNoCTop(ChiNoCParams(), defaultEndpoints.take(3))).module)
    }
  }

  private def pokeFields(payload: Bundle, values: Map[String, BigInt]): Unit = {
    payload.elements.keySet shouldBe values.keySet
    for ((name, value) <- values) {
      payload.elements(name) match {
        case field: Bool => field.poke((value != 0).B)
        case field: UInt => field.poke(value.U)
      }
    }
  }

  private def expectFields(payload: Bundle, values: Map[String, BigInt]): Unit = {
    for ((name, value) <- values) {
      withClue(s"field $name: ") {
        payload.elements(name) match {
          case field: Bool => field.expect((value != 0).B)
          case field: UInt => field.expect(value.U)
        }
      }
    }
  }

  for ((params, endpoints) <- configs) {
    s"all four CHI channels route every pair with node IDs ${endpoints.map(_.nodeId)}" in {
      simulate(LazyModule(new ChiNoCTop(params, endpoints)).module) { dut =>
        val count = endpoints.size
        for (port <- dut.io.ports) {
          port.reqTx.valid.poke(false.B)
          port.rspTx.valid.poke(false.B)
          port.snpTx.valid.poke(false.B)
          port.datTx.valid.poke(false.B)
          port.reqRx.ready.poke(false.B)
          port.rspRx.ready.poke(false.B)
          port.snpRx.ready.poke(false.B)
          port.datRx.ready.poke(false.B)
          for (bits <- Seq(port.reqTx.bits, port.rspTx.bits, port.snpTx.bits.payload, port.datTx.bits)) {
            pokeFields(bits, bits.elements.keys.map(_ -> BigInt(0)).toMap)
          }
          port.snpTx.bits.dst.poke(0.U)
        }

        for (src <- 0 until count; dst <- 0 until count) {
          val tag = src * count + dst + 1
          val txn = BigInt(tag) & ((BigInt(1) << params.chi.txnIdWidth) - 1)
          val addr = (BigInt(1) << (params.chi.addrWidth - 1)) | (BigInt(tag) << 6)
          val data = (BigInt(1) << 511) | (BigInt(1) << 257) | (BigInt(tag) << 128) | 0x12345678
          val req: Map[String, BigInt] = Map("tgtID" -> endpoints(dst).nodeId, "srcID" -> endpoints(src).nodeId,
            "txnID" -> txn, "opcode" -> 1, "addr" -> addr, "allowRetry" -> (tag % 2), "pCrdType" -> 0)
          val rsp: Map[String, BigInt] = Map("tgtID" -> endpoints(dst).nodeId, "srcID" -> endpoints(src).nodeId,
            "txnID" -> txn, "opcode" -> 6, "resp" -> 0, "dbID" -> txn, "pCrdType" -> 0)
          val snp: Map[String, BigInt] = Map("srcID" -> endpoints(src).nodeId,
            "txnID" -> txn, "opcode" -> 7, "addr" -> addr)
          val dat: Map[String, BigInt] = Map("tgtID" -> endpoints(dst).nodeId, "srcID" -> endpoints(src).nodeId,
            "txnID" -> txn, "opcode" -> 4, "resp" -> 6, "dbID" -> txn, "data" -> data)
          val source = dut.io.ports(src)
          val target = dut.io.ports(dst)
          val receivers = Seq(target.reqRx, target.rspRx, target.snpRx, target.datRx)
          receivers.foreach(_.ready.poke(false.B))
          pokeFields(source.reqTx.bits, req)
          pokeFields(source.rspTx.bits, rsp)
          pokeFields(source.snpTx.bits.payload, snp)
          source.snpTx.bits.dst.poke(endpoints(dst).nodeId.U)
          pokeFields(source.datTx.bits, dat)
          val senders = Seq(source.reqTx, source.rspTx, source.snpTx, source.datTx)
          senders.foreach { tx =>
            tx.valid.poke(true.B)
            tx.ready.expect(true.B)
          }
          dut.clock.step()
          senders.foreach(_.valid.poke(false.B))
          dut.clock.step(count + 2)
          receivers.foreach(_.valid.expect(true.B))

          val delivered = Array.fill(4)(0)
          def checkRx[T <: Bundle](rx: DecoupledIO[T], values: Map[String, BigInt], node: Int, channel: Int): Unit = {
            if (rx.valid.peek().litToBoolean) {
              node shouldBe dst
              expectFields(rx.bits, values)
              if (rx.ready.peek().litToBoolean) delivered(channel) += 1
            }
          }
          def tick(): Unit = {
            for ((port, node) <- dut.io.ports.zipWithIndex) {
              checkRx(port.reqRx, req, node, 0)
              checkRx(port.rspRx, rsp, node, 1)
              checkRx(port.snpRx, snp, node, 2)
              checkRx(port.datRx, dat, node, 3)
            }
            dut.clock.step()
          }

          for (_ <- 0 until 4) tick()
          delivered.toSeq shouldBe Seq(0, 0, 0, 0)
          // Keep one channel blocked while the other three deliver their messages.
          val blocked = (src + dst) % 4
          for ((rx, channel) <- receivers.zipWithIndex) rx.ready.poke((channel != blocked).B)
          for (_ <- 0 until 6) {
            receivers(blocked).valid.expect(true.B)
            tick()
          }
          delivered.toSeq shouldBe (0 until 4).map(channel => if (channel == blocked) 0 else 1)
          receivers.foreach(_.ready.poke(true.B))
          for (_ <- 0 until 2 * count + 6) tick()
          delivered.toSeq shouldBe Seq(1, 1, 1, 1)
          receivers.foreach(_.valid.expect(false.B))
        }
      }
    }
  }
}
