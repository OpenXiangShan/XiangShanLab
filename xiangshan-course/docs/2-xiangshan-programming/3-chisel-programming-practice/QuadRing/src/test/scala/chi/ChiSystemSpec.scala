package chi

import chisel3._
import chisel3.reflect.DataMirror
import circt.stage.ChiselStage
import noc.RingParams
import org.chipsalliance.cde.config.Parameters
import org.chipsalliance.diplomacy.lazymodule.LazyModule
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

class ChiSystemSpec extends AnyFreeSpec with Matchers with ChiNodeTestSupport {
  private implicit val p: Parameters = Parameters.empty
  private val configs = Seq(
    ChiSystemParams(initialLines = lines),
    ChiSystemParams(ChiNoCParams(ChiParams(3, 7, 32), RingParams(4, 5)), 6, 1, 4, 0, lines)
  )

  private def ports(dut: ChiSystemModule): Seq[RnPort] = Seq(dut.io.rn0, dut.io.rn1)

  private def expectResult(port: RnPort, txn: Int, value: BigInt): Unit = {
    port.response.valid.expect(true.B)
    port.response.bits.txnID.expect(txn.U)
    port.response.bits.data.expect(value.U)
    port.request.ready.expect(false.B)
  }

  private def waitForResult(dut: ChiSystemModule, port: RnPort, txn: Int, value: BigInt): Unit = {
    var cycles = 0
    while (!port.response.valid.peekBoolean() && cycles < 128) {
      port.request.ready.expect(false.B)
      dut.clock.step()
      cycles += 1
    }
    withClue("end-to-end read did not finish: ") { expectResult(port, txn, value) }
  }

  "the four nodes negotiate roles and widths behind local RN request/result interfaces" in {
    for (params <- configs) {
      ChiselStage.emitCHIRRTL {
        val top = LazyModule(new ChiSystem(params))
        val dut = top.module
        val expected = Seq(ChiEndpointParams(params.rn0Id, ChiRole.RN),
          ChiEndpointParams(params.hnId, ChiRole.HN), ChiEndpointParams(params.rn1Id, ChiRole.RN),
          ChiEndpointParams(params.snId, ChiRole.SN))
        top.network.node.edges.in.map(_.endpoint) shouldBe expected
        val endpoints = Seq(top.rn0.node, top.hn.node, top.rn1.node, top.sn.node)
        endpoints.map(_.edges.out.head.endpoint) shouldBe expected
        endpoints.map(_.edges.out.head.network) shouldBe Seq.fill(4)(params.network)
        for (node <- endpoints) {
          node.out.head._1.reqTx.bits.addr.getWidth shouldBe params.network.chi.addrWidth
          node.out.head._1.datRx.bits.txnID.getWidth shouldBe params.network.chi.txnIdWidth
          node.out.head._1.datRx.bits.data.getWidth shouldBe 512
        }
        for (port <- ports(dut) ++ Seq(top.rn0.module.io, top.rn1.module.io)) {
          DataMirror.directionOf(port.request.valid) shouldBe ActualDirection.Input
          DataMirror.directionOf(port.request.ready) shouldBe ActualDirection.Output
          DataMirror.directionOf(port.response.valid) shouldBe ActualDirection.Output
          DataMirror.directionOf(port.response.ready) shouldBe ActualDirection.Input
          port.request.bits.addr.getWidth shouldBe params.network.chi.addrWidth
          port.request.bits.txnID.getWidth shouldBe params.network.chi.txnIdWidth
          port.response.bits.data.getWidth shouldBe 512
        }
        dut
      }
    }
  }

  "the assembled system requires four ring stops" in {
    an[IllegalArgumentException] should be thrownBy ChiSystemParams(ChiNoCParams(ring = RingParams(3)))
  }

  "invalid node IDs and memory initialization fail before graph construction" in {
    for ((rn0, hn, rn1, sn) <- Seq((-1, 1, 2, 3), (0, -1, 2, 3),
      (0, 1, -1, 3), (0, 1, 2, -1))) {
      an[IllegalArgumentException] should be thrownBy {
        ChiSystemParams(rn0Id = rn0, hnId = hn, rn1Id = rn1, snId = sn)
      }
    }
    an[IllegalArgumentException] should be thrownBy ChiSystemParams(initialLines = Seq.empty)
    an[IllegalArgumentException] should be thrownBy ChiSystemParams(initialLines = Seq(BigInt(-1)))
  }

  "duplicate endpoint IDs are rejected by the assembled network" in {
    val error = intercept[IllegalArgumentException] {
      ChiselStage.emitCHIRRTL(LazyModule(new ChiSystem(ChiSystemParams(snId = 0))).module)
    }
    error.getMessage should include("unique")
  }

  "endpoint IDs must fit the negotiated width" in {
    val error = intercept[IllegalArgumentException] {
      ChiselStage.emitCHIRRTL(LazyModule(new ChiSystem(ChiSystemParams(hnId = 4))).module)
    }
    error.getMessage should include("negotiated width")
  }

  for (params <- configs) {
    s"both RNs write complete lines through HN and SN (${params.network.chi})" in {
      simulate(LazyModule(new ChiSystem(params)).module) { dut =>
        ports(dut).foreach(initialize)
        val mask = (1 << params.network.chi.txnIdWidth) - 1
        for ((port, rn) <- ports(dut).zipWithIndex; index <- params.initialLines.indices) {
          val txn = (mask - index - rn) & mask
          val value = if (rn == 0) {
            (BigInt(1) << 511) | (BigInt(index + 9) << 256) | BigInt(0xa5a50000L + index)
          } else if (index == 0) BigInt(0) else (BigInt(1) << 512) - 1
          localWriteRequest(port, txn, BigInt(index) << 6, value)
          port.request.ready.expect(true.B)
          dut.clock.step()
          port.request.valid.poke(false)
          port.request.bits.data.poke(BigInt(0))
          waitForResult(dut, port, txn, 0)
          for (_ <- 0 until 4) {
            expectResult(port, txn, 0)
            ports(dut)(1 - rn).response.valid.expect(false.B)
            dut.clock.step()
          }
          port.response.ready.poke(true)
          dut.clock.step()
          port.response.ready.poke(false)
          port.request.ready.expect(true.B)
          // Read back through the other RN as well; the value must have reached SN.
          val reader = ports(dut)(1 - rn)
          val readTxn = (txn - 1) & mask
          localRequest(reader, readTxn, BigInt(index) << 6)
          reader.request.ready.expect(true.B)
          dut.clock.step()
          reader.request.valid.poke(false)
          waitForResult(dut, reader, readTxn, value)
          reader.response.ready.poke(true)
          dut.clock.step()
          reader.response.ready.poke(false)
        }
      }
    }

    s"RN write data and response remain stable under network and local backpressure (${params.network.chi})" in {
      simulate(LazyModule(new ChiSystem(params)).module) { dut =>
        ports(dut).foreach(initialize)
        val port = dut.io.rn0
        val txn = (1 << params.network.chi.txnIdWidth) - 1
        val value = (BigInt(1) << 512) - 1
        localWriteRequest(port, txn, 0, value)
        dut.clock.step()
        port.request.valid.poke(false)
        port.request.bits.data.poke(BigInt(0))
        // Keep the response blocked while the write traverses HN/SN.
        for (_ <- 0 until 8) {
          port.response.ready.poke(false)
          dut.clock.step()
        }
        waitForResult(dut, port, txn, 0)
        for (_ <- 0 until 8) {
          expectResult(port, txn, 0)
          dut.clock.step()
        }
        port.response.ready.poke(true)
        dut.clock.step()
        port.response.ready.poke(false)
      }
    }

    s"both RNs read every SN line through the real NoC with parameters ${params.network}" in {
      simulate(LazyModule(new ChiSystem(params)).module) { dut =>
        ports(dut).foreach(initialize)
        val mask = (1 << params.network.chi.txnIdWidth) - 1
        for (round <- 0 until 2; (port, rn) <- ports(dut).zipWithIndex;
          (value, index) <- params.initialLines.zipWithIndex) {
          val txn = (mask - index - round) & mask
          localRequest(port, txn, BigInt(index) << 6)
          port.request.ready.expect(true.B)
          dut.clock.step()
          port.request.valid.poke(false)
          port.request.bits.addr.poke(BigInt(0))
          port.request.bits.txnID.poke(BigInt(0))
          waitForResult(dut, port, txn, value)
          for (_ <- 0 until 8) {
            expectResult(port, txn, value)
            ports(dut)(1 - rn).response.valid.expect(false.B)
            dut.clock.step()
          }
          port.response.ready.poke(true)
          dut.clock.step()
          port.response.ready.poke(false)
          port.request.ready.expect(true.B)
          for (_ <- 0 until 8) {
            ports(dut).foreach(_.response.valid.expect(false.B))
            dut.clock.step()
          }
        }
      }
    }

    s"equal transaction IDs at both RNs stay separate while one local result is blocked (${params.network.chi})" in {
      simulate(LazyModule(new ChiSystem(params)).module) { dut =>
        ports(dut).foreach(initialize)
        val txn = (1 << params.network.chi.txnIdWidth) - 1
        localRequest(dut.io.rn0, txn, 0)
        localRequest(dut.io.rn1, txn, 128)
        ports(dut).foreach(_.request.ready.expect(true.B))
        dut.clock.step()
        ports(dut).foreach(_.request.valid.poke(false))
        var cycles = 0
        while (!ports(dut).forall(_.response.valid.peekBoolean()) && cycles < 128) {
          if (dut.io.rn0.response.valid.peekBoolean()) expectResult(dut.io.rn0, txn, lines.head)
          if (dut.io.rn1.response.valid.peekBoolean()) expectResult(dut.io.rn1, txn, lines(2))
          dut.clock.step()
          cycles += 1
        }
        expectResult(dut.io.rn0, txn, lines.head)
        expectResult(dut.io.rn1, txn, lines(2))
        dut.io.rn0.response.ready.poke(true)
        dut.clock.step()
        dut.io.rn0.response.ready.poke(false)
        // RN1's local backpressure must not prevent a later RN0 transaction.
        localRequest(dut.io.rn0, txn - 1, 64)
        dut.io.rn0.request.ready.expect(true.B)
        dut.clock.step()
        dut.io.rn0.request.valid.poke(false)
        waitForResult(dut, dut.io.rn0, txn - 1, lines(1))
        for (_ <- 0 until 8) {
          expectResult(dut.io.rn0, txn - 1, lines(1))
          expectResult(dut.io.rn1, txn, lines(2))
          dut.clock.step()
        }
        ports(dut).foreach(_.response.ready.poke(true))
        dut.clock.step()
        for (_ <- 0 until 24) {
          ports(dut).foreach(_.response.valid.expect(false.B))
          dut.clock.step()
        }
      }
    }
  }

  for (elapsed <- Seq(0, 8, 32)) {
    s"global reset clears the active transaction or blocked result after $elapsed cycles" in {
      val params = ChiSystemParams(initialLines = lines)
      simulate(LazyModule(new ChiSystem(params)).module) { dut =>
        ports(dut).foreach(initialize)
        localRequest(dut.io.rn0, 13, 64)
        dut.clock.step()
        dut.io.rn0.request.valid.poke(false)
        if (elapsed > 0) dut.clock.step(elapsed)
        dut.reset.poke(true.B)
        dut.clock.step(2)
        dut.reset.poke(false.B)
        for (_ <- 0 until 24) {
          ports(dut).foreach { port =>
            port.request.ready.expect(true.B)
            port.response.valid.expect(false.B)
          }
          dut.clock.step()
        }
        localRequest(dut.io.rn1, 2, 128)
        dut.clock.step()
        dut.io.rn1.request.valid.poke(false)
        waitForResult(dut, dut.io.rn1, 2, lines(2))
        dut.io.rn1.response.ready.poke(true)
        dut.clock.step()
        for (_ <- 0 until 24) {
          ports(dut).foreach(_.response.valid.expect(false.B))
          dut.clock.step()
        }
      }
    }
  }
}
