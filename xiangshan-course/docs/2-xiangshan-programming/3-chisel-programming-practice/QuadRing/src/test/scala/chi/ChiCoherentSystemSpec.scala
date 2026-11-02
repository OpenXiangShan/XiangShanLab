package chi

import chisel3._
import chisel3.reflect.DataMirror
import circt.stage.ChiselStage
import noc.RingParams
import org.chipsalliance.cde.config.Parameters
import org.chipsalliance.diplomacy.lazymodule.LazyModule
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

class ChiCoherentSystemSpec extends AnyFreeSpec with Matchers with ChiNodeTestSupport {
  private implicit val p: Parameters = Parameters.empty
  private val memory = Seq.tabulate(16)(i => (BigInt(1) << 511) | (BigInt(i) << 256) | BigInt(i))
  private val first = (BigInt(1) << 510) | (BigInt(0xfedcba98L) << 256) | BigInt(0xa5a50000L)
  private val second = (BigInt(1) << 509) | (BigInt(0x89abcdefL) << 128) | BigInt(0x5a5affffL)
  private val configs = Seq(
    ChiSystemParams(initialLines = memory, coherent = true, cacheLines = 2, directoryLines = 16),
    ChiSystemParams(ChiNoCParams(ChiParams(3, 7, 32), RingParams(4, 5)), 6, 1, 4, 0,
      memory.take(13), coherent = true, cacheLines = 4, directoryLines = 16),
    ChiSystemParams(ChiNoCParams(ChiParams(2, 1, 8), RingParams(4, 2)),
      initialLines = memory.take(3), coherent = true, cacheLines = 2, directoryLines = 4)
  )

  private def ports(dut: ChiSystemModule): Seq[RnPort] = Seq(dut.io.rn0, dut.io.rn1)

  private def send(dut: ChiSystemModule, rn: Int, line: Int, value: Option[BigInt] = None): Unit = {
    val port = ports(dut)(rn)
    value match {
      case Some(v) => localWriteRequest(port, 1, BigInt(line * 64), v)
      case None => localRequest(port, 1, BigInt(line * 64))
    }
    port.request.ready.expect(true.B)
    dut.clock.step()
    port.request.valid.poke(false)
    port.request.bits.addr.poke(0.U)
    port.request.bits.data.poke(0.U)
    port.request.bits.txnID.poke(0.U)
  }

  private def result(dut: ChiSystemModule, rn: Int, value: BigInt, consume: Boolean = true): Unit = {
    val port = ports(dut)(rn)
    var cycles = 0
    while (!port.response.valid.peekBoolean() && cycles < 256) {
      port.request.ready.expect(false.B)
      dut.clock.step()
      cycles += 1
    }
    for (_ <- 0 until 4) {
      port.response.valid.expect(true.B)
      port.response.bits.txnID.expect(1.U)
      port.response.bits.data.expect(value.U)
      port.request.ready.expect(false.B)
      dut.clock.step()
    }
    if (consume) {
      port.response.ready.poke(true)
      dut.clock.step()
      port.response.ready.poke(false)
    }
  }

  private def transact(dut: ChiSystemModule, rn: Int, line: Int, value: BigInt,
    write: Boolean = false): Unit = {
    send(dut, rn, line, if (write) Some(value) else None)
    result(dut, rn, if (write) BigInt(0) else value)
  }

  "coherent system configuration rejects invalid capacities before graph construction" in {
    for (count <- Seq(0, 1, 3)) {
      an[IllegalArgumentException] should be thrownBy ChiSystemParams(coherent = true, cacheLines = count)
      an[IllegalArgumentException] should be thrownBy ChiSystemParams(coherent = true, directoryLines = count)
    }
    val error = intercept[IllegalArgumentException] {
      ChiSystemParams(initialLines = memory.take(5), coherent = true, directoryLines = 4)
    }
    error.getMessage should include("cover all SN memory lines")
    // A failed parameter value must not leave an open Diplomacy scope behind.
    ChiselStage.emitCHIRRTL(LazyModule(new ChiSystem(configs.head)).module)
    ChiselStage.emitCHIRRTL(LazyModule(new ChiSystem(ChiSystemParams(
      initialLines = memory.take(3), directoryLines = 2))).module)
  }

  "coherent cache tags and directory indexes must fit the negotiated address width" in {
    val narrow = ChiNoCParams(ChiParams(2, 1, 8))
    val directoryError = intercept[IllegalArgumentException] {
      ChiSystemParams(narrow, initialLines = memory.take(3), coherent = true,
        cacheLines = 2, directoryLines = 8)
    }
    directoryError.getMessage should include("directory must fit")
    val cacheError = intercept[IllegalArgumentException] {
      ChiSystemParams(narrow, initialLines = memory.take(3), coherent = true,
        cacheLines = 4, directoryLines = 4)
    }
    cacheError.getMessage should include("at least one tag bit")
    // The non-coherent mode does not index either structure.
    ChiselStage.emitCHIRRTL(LazyModule(new ChiSystem(ChiSystemParams(
      ChiNoCParams(ChiParams(2, 1, 6)), initialLines = memory.take(1)))).module)
  }

  "both cached RNs negotiate roles widths and local port directions through Diplomacy" in {
    for (params <- configs :+ ChiSystemParams(coherent = true)) {
      ChiselStage.emitCHIRRTL {
        val top = LazyModule(new ChiSystem(params))
        val dut = top.module
        val expected = Seq(ChiEndpointParams(params.rn0Id, ChiRole.RN),
          ChiEndpointParams(params.hnId, ChiRole.HN), ChiEndpointParams(params.rn1Id, ChiRole.RN),
          ChiEndpointParams(params.snId, ChiRole.SN))
        top.network.node.edges.in.map(_.endpoint) shouldBe expected
        Seq(top.rn0.node, top.hn.node, top.rn1.node, top.sn.node).foreach { node =>
          node.edges.out.head.network shouldBe params.network
          node.out.head._1.reqTx.bits.addr.getWidth shouldBe params.network.chi.addrWidth
          node.out.head._1.datRx.bits.txnID.getWidth shouldBe params.network.chi.txnIdWidth
        }
        for (port <- ports(dut)) {
          DataMirror.directionOf(port.request.valid) shouldBe ActualDirection.Input
          DataMirror.directionOf(port.request.ready) shouldBe ActualDirection.Output
          DataMirror.directionOf(port.response.valid) shouldBe ActualDirection.Output
          DataMirror.directionOf(port.response.ready) shouldBe ActualDirection.Input
          port.request.bits.txnID.getWidth shouldBe params.network.chi.txnIdWidth
          port.response.bits.data.getWidth shouldBe 512
        }
        dut
      }
    }
  }

  for (params <- configs) {
    s"one RN completes cache hits and clean dirty replacements with ${params.network.chi}" in {
      simulate(LazyModule(new ChiSystem(params)).module) { dut =>
        ports(dut).foreach(initialize)
        transact(dut, 0, 0, params.initialLines.head)
        transact(dut, 0, params.cacheLines, params.initialLines(params.cacheLines))
        transact(dut, 0, 0, params.initialLines.head)
        transact(dut, 0, 0, first, write = true)
        transact(dut, 0, 0, first)
        transact(dut, 0, params.cacheLines, params.initialLines(params.cacheLines))
        transact(dut, 0, 0, first)
        val last = params.initialLines.size - 1
        transact(dut, 0, last, params.initialLines(last))
        transact(dut, 0, 0, first)
        dut.clock.step(16)
        ports(dut).foreach(_.response.valid.expect(false.B))
      }
    }

    s"both product RNs share invalidate and transfer dirty ownership with ${params.network.chi}" in {
      simulate(LazyModule(new ChiSystem(params)).module) { dut =>
        ports(dut).foreach(initialize)
        send(dut, 0, 0)
        result(dut, 0, params.initialLines.head, consume = false)
        // SnpUnique must complete even while the old CPU result remains blocked.
        transact(dut, 1, 0, first, write = true)
        result(dut, 0, params.initialLines.head)
        transact(dut, 0, 0, first) // Dirty sharing; both caches become SC.
        transact(dut, 0, 0, second, write = true) // SC -> U upgrade.
        transact(dut, 1, 0, first, write = true) // Dirty ownership transfer.
        transact(dut, 0, 0, first)
        transact(dut, 1, 0, first)
        transact(dut, 0, params.cacheLines, params.initialLines(params.cacheLines))
        transact(dut, 1, params.cacheLines, params.initialLines(params.cacheLines))
        transact(dut, 0, 0, first) // Both old copies were evicted; read the cleaned SN line.
        dut.clock.step(16)
        ports(dut).foreach(_.response.valid.expect(false.B))
      }
    }

    s"equal IDs and concurrent upgrades complete through the product system with ${params.network.chi}" in {
      simulate(LazyModule(new ChiSystem(params)).module) { dut =>
        ports(dut).foreach(initialize)
        transact(dut, 0, 0, params.initialLines.head)
        transact(dut, 1, 0, params.initialLines.head)
        localWriteRequest(dut.io.rn0, 1, 0, first)
        localWriteRequest(dut.io.rn1, 1, 0, second)
        ports(dut).foreach(_.request.ready.expect(true.B))
        dut.clock.step()
        ports(dut).foreach(_.request.valid.poke(false))
        result(dut, 0, 0)
        result(dut, 1, 0)
        send(dut, 0, 0)
        var cycles = 0
        while (!dut.io.rn0.response.valid.peekBoolean() && cycles < 256) {
          dut.clock.step()
          cycles += 1
        }
        dut.io.rn0.response.valid.expect(true.B)
        val latest = dut.io.rn0.response.bits.data.peek().litValue
        Set(first, second) should contain(latest)
        result(dut, 0, latest)
        transact(dut, 1, 0, latest)
        dut.clock.step(16)
        ports(dut).foreach(_.response.valid.expect(false.B))
      }
    }

    s"global reset clears coherent caches directory memory and in-flight traffic with ${params.network.chi}" in {
      simulate(LazyModule(new ChiSystem(params)).module) { dut =>
        for (elapsed <- Seq(0, 8, 32)) {
          ports(dut).foreach(initialize)
          transact(dut, 0, 0, first, write = true)
          transact(dut, 1, 0, first) // SN now contains first, and both caches are SC.
          transact(dut, 1, 0, second, write = true)
          send(dut, 0, params.initialLines.size - 1)
          if (elapsed > 0) dut.clock.step(elapsed)
          dut.reset.poke(true)
          dut.clock.step(2)
          dut.reset.poke(false)
          for (_ <- 0 until 16) {
            ports(dut).foreach { port =>
              port.request.ready.expect(true.B)
              port.response.valid.expect(false.B)
            }
            dut.clock.step()
          }
          transact(dut, 0, 0, params.initialLines.head)
          transact(dut, 1, 0, params.initialLines.head)
          dut.reset.poke(true)
          dut.clock.step(2)
          dut.reset.poke(false)
        }
      }
    }
  }
}
