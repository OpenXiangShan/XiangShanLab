package chi

import chisel3._
import chisel3.simulator.Exceptions.AssertionFailed
import noc.RingParams
import org.chipsalliance.cde.config.Parameters
import org.chipsalliance.diplomacy.lazymodule.LazyModule
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

class HnEvictSpec extends AnyFreeSpec with Matchers with ChiNodeTestSupport {
  private implicit val p: Parameters = Parameters.empty
  private val config = ChiNoCParams()
  private val hn = HnParams(coherent = true, directoryLines = 4)
  private val configs = Seq(
    (config, hn, 0, 2),
    (ChiNoCParams(ChiParams(3, 7, 32), RingParams(4, 5)),
      HnParams(nodeId = 5, snId = 1, coherent = true, directoryLines = 4), 6, 0),
    (ChiNoCParams(ChiParams(2, 1, 7), RingParams(4, 2)),
      HnParams(coherent = true, directoryLines = 2), 0, 2)
  )

  private def issue(dut: ChiNodeFixtures.HnModule, params: HnParams, src: Int,
    txn: Int, address: BigInt, opcode: UInt, credited: Boolean = false): Unit = {
    request(dut.io.reqRx.bits, params.nodeId, src, txn, address)
    dut.io.reqRx.bits.opcode.poke(opcode)
    dut.io.reqRx.bits.allowRetry.poke((!credited).B)
    dut.io.reqRx.valid.poke(true)
    dut.io.reqRx.ready.expect(true.B)
    dut.clock.step()
    dut.io.reqRx.valid.poke(false)
  }

  private def ackRead(dut: ChiNodeFixtures.HnModule, params: HnParams, src: Int): Unit = {
    response(dut.io.rspRx.bits, params.nodeId, src, 0, ChiOpcode.Rsp.CompAck)
    dut.io.rspRx.valid.poke(true)
    dut.io.rspRx.ready.expect(true.B)
    dut.clock.step()
    dut.io.rspRx.valid.poke(false)
  }

  private def readLine(dut: ChiNodeFixtures.HnModule, params: HnParams, src: Int,
    txn: Int, address: BigInt, acknowledge: Boolean = true, credited: Boolean = false): Unit = {
    issue(dut, params, src, txn, address, ChiOpcode.Req.ReadShared, credited)
    dut.io.reqTx.valid.expect(true.B)
    expectRequest(dut.io.reqTx.bits, params.snId, params.nodeId, 0, address)
    dut.clock.step()
    data(dut.io.datRx.bits, params.nodeId, params.snId, 0, lines.head)
    dut.io.datRx.valid.poke(true)
    dut.io.datRx.ready.expect(true.B)
    dut.clock.step()
    dut.io.datRx.valid.poke(false)
    dut.io.datTx.valid.expect(true.B)
    dut.io.datTx.bits.tgtID.expect(src.U)
    dut.io.datTx.bits.srcID.expect(params.nodeId.U)
    dut.io.datTx.bits.txnID.expect(txn.U)
    dut.io.datTx.bits.opcode.expect(ChiOpcode.Dat.CompData)
    dut.io.datTx.bits.dbID.expect(0.U)
    dut.io.datTx.bits.resp.expect(ChiResp.UC)
    dut.io.datTx.bits.data.expect(lines.head.U)
    dut.clock.step()
    if (acknowledge) ackRead(dut, params, src)
  }

  private def expectComp(dut: ChiNodeFixtures.HnModule, params: HnParams, src: Int, txn: Int): Unit = {
    dut.io.rspTx.valid.expect(true.B)
    expectResponse(dut.io.rspTx.bits, src, params.nodeId, txn, ChiOpcode.Rsp.Comp)
    dut.io.reqTx.valid.expect(false.B)
    dut.io.datTx.valid.expect(false.B)
    dut.io.snpTx.valid.expect(false.B)
    dut.io.datRx.ready.expect(false.B)
    dut.io.rspRx.ready.expect(false.B)
  }

  for ((network, params, rn0, rn1) <- configs) {
    s"Evict completes without SN access or CompAck and permits a new owner with ${network.chi}" in {
      simulate(LazyModule(new ChiNodeFixtures.Hn(network, params)).module) { dut =>
        initialize(dut.io)
        val txn = (1 << network.chi.txnIdWidth) - 1
        val address = BigInt(params.directoryLines - 1) << 6
        readLine(dut, params, rn0, txn, address)
        dut.io.rspTx.ready.poke(false)
        issue(dut, params, rn0, txn, address, ChiOpcode.Req.Evict)
        // Inactive input bits must not alter the saved completion identity.
        request(dut.io.reqRx.bits, params.nodeId, rn1, 0, 0)
        for (_ <- 0 until 6) {
          expectComp(dut, params, rn0, txn)
          dut.clock.step()
        }
        dut.io.rspTx.ready.poke(true)
        dut.clock.step()
        for (_ <- 0 until 3) {
          dut.io.rspTx.valid.expect(false.B)
          dut.io.reqTx.valid.expect(false.B)
          dut.io.datTx.valid.expect(false.B)
          dut.clock.step()
        }
        readLine(dut, params, rn1, txn, address)
      }
    }
  }

  "Evict for an absent holder is harmless and can be repeated" in {
    simulate(LazyModule(new ChiNodeFixtures.Hn(config, hn)).module) { dut =>
      initialize(dut.io)
      for (_ <- 0 until 2) {
        issue(dut, hn, 0, 3, 0, ChiOpcode.Req.Evict)
        expectComp(dut, hn, 0, 3)
        dut.clock.step()
      }
      readLine(dut, hn, 2, 4, 0)
    }
  }

  for (otherAddress <- Seq(false, true)) {
    s"Evict preserves a holder belonging to another ${if (otherAddress) "address" else "RN"}" in {
      simulate(LazyModule(new ChiNodeFixtures.Hn(config, hn)).module) { dut =>
        initialize(dut.io)
        readLine(dut, hn, 0, 3, 0)
        issue(dut, hn, if (otherAddress) 0 else 2, 4,
          if (otherAddress) BigInt(64) else BigInt(0), ChiOpcode.Req.Evict)
        expectComp(dut, hn, if (otherAddress) 0 else 2, 4)
        dut.clock.step()
        issue(dut, hn, 2, 5, 0, ChiOpcode.Req.ReadUnique)
        expectSnoopRequest(dut.io, 0, hn.nodeId, 0)
      }
    }
  }

  "a busy Evict retries and its blocked Comp stays stable while another request is rejected" in {
    simulate(LazyModule(new ChiNodeFixtures.Hn(config, hn)).module) { dut =>
      val port = dut.io
      initialize(port)
      readLine(dut, hn, 0, 3, 0, acknowledge = false)
      issue(dut, hn, 0, 4, 0, ChiOpcode.Req.Evict)
      port.rspTx.ready.poke(false)
      for (_ <- 0 until 3) {
        port.rspTx.valid.expect(true.B)
        expectResponse(port.rspTx.bits, 0, hn.nodeId, 4, ChiOpcode.Rsp.RetryAck)
        port.reqTx.valid.expect(false.B)
        port.datTx.valid.expect(false.B)
        dut.clock.step()
      }
      port.rspTx.ready.poke(true)
      dut.clock.step()
      port.rspTx.valid.expect(false.B)
      ackRead(dut, hn, 0)
      dut.clock.step()
      port.rspTx.valid.expect(true.B)
      expectResponse(port.rspTx.bits, 0, hn.nodeId, 0, ChiOpcode.Rsp.PCrdGrant)
      dut.clock.step()
      port.rspTx.ready.poke(false)
      issue(dut, hn, 0, 4, 0, ChiOpcode.Req.Evict, credited = true)
      expectComp(dut, hn, 0, 4)

      issue(dut, hn, 2, 5, 0, ChiOpcode.Req.ReadShared)
      for (_ <- 0 until 4) {
        expectComp(dut, hn, 0, 4)
        dut.clock.step()
      }
      port.rspTx.ready.poke(true)
      dut.clock.step()
      expectResponse(port.rspTx.bits, 2, hn.nodeId, 5, ChiOpcode.Rsp.RetryAck)
      dut.clock.step()
      port.rspTx.valid.expect(true.B)
      expectResponse(port.rspTx.bits, 2, hn.nodeId, 0, ChiOpcode.Rsp.PCrdGrant)
      dut.clock.step()
      readLine(dut, hn, 2, 5, 0, credited = true)
      for (_ <- 0 until 4) {
        port.rspTx.valid.expect(false.B)
        port.reqTx.valid.expect(false.B)
        port.datTx.valid.expect(false.B)
        dut.clock.step()
      }
    }
  }

  "a rejected Evict does not clear its holder before the credited retry" in {
    simulate(LazyModule(new ChiNodeFixtures.Hn(config, hn)).module) { dut =>
      val port = dut.io
      initialize(port)
      readLine(dut, hn, 0, 3, 0, acknowledge = false)
      // Queue the competing read first so it gets credit before the Evict.
      for ((src, txn, opcode) <- Seq(
        (2, 4, ChiOpcode.Req.ReadUnique), (0, 5, ChiOpcode.Req.Evict))) {
        issue(dut, hn, src, txn, 0, opcode)
        port.rspTx.valid.expect(true.B)
        expectResponse(port.rspTx.bits, src, hn.nodeId, txn, ChiOpcode.Rsp.RetryAck)
        dut.clock.step()
      }
      ackRead(dut, hn, 0)
      dut.clock.step()
      port.rspTx.valid.expect(true.B)
      expectResponse(port.rspTx.bits, 2, hn.nodeId, 0, ChiOpcode.Rsp.PCrdGrant)
      dut.clock.step()
      issue(dut, hn, 2, 4, 0, ChiOpcode.Req.ReadUnique, credited = true)
      expectSnoopRequest(dut.io, 0, hn.nodeId, 0)
    }
  }

  "reset cancels a blocked Evict completion and its retry waiter" in {
    simulate(LazyModule(new ChiNodeFixtures.Hn(config, hn)).module) { dut =>
      initialize(dut.io)
      readLine(dut, hn, 0, 3, 0)
      dut.io.rspTx.ready.poke(false)
      issue(dut, hn, 0, 4, 0, ChiOpcode.Req.Evict)
      issue(dut, hn, 2, 5, 0, ChiOpcode.Req.ReadShared)
      expectComp(dut, hn, 0, 4)
      dut.reset.poke(true.B)
      dut.clock.step(2)
      dut.reset.poke(false.B)
      dut.io.rspTx.ready.poke(true)
      for (_ <- 0 until 4) {
        dut.io.rspTx.valid.expect(false.B)
        dut.clock.step()
      }
      readLine(dut, hn, 2, 6, 0)
    }
  }

  for ((field, value, message) <- Seq(
    ("tgtID", BigInt(2), "REQ target"), ("addr", BigInt(1), "line-aligned"),
    ("addr", BigInt(hn.directoryLines * 64), "address exceeds directory range"),
    ("allowRetry", BigInt(0), "only first attempts"), ("pCrdType", BigInt(1), "only first attempts"))) {
    s"Evict rejects invalid $field=$value" in {
      val error = intercept[AssertionFailed] {
        simulate(LazyModule(new ChiNodeFixtures.Hn(config, hn)).module) { dut =>
          initialize(dut.io)
          request(dut.io.reqRx.bits, hn.nodeId, 0, 3, 0)
          dut.io.reqRx.bits.opcode.poke(ChiOpcode.Req.Evict)
          dut.io.reqRx.bits.elements(field).asInstanceOf[UInt].poke(value)
          dut.io.reqRx.valid.poke(true)
          dut.clock.step()
        }
      }
      error.getMessage should include(message)
    }
  }

  "non-coherent mode continues to reject Evict" in {
    val error = intercept[AssertionFailed] {
      simulate(LazyModule(new ChiNodeFixtures.Hn(config, HnParams())).module) { dut =>
        initialize(dut.io)
        issue(dut, HnParams(), 0, 3, 0, ChiOpcode.Req.Evict)
      }
    }
    error.getMessage should include("only ReadNoSnp and WriteNoSnpFull")
  }
}
