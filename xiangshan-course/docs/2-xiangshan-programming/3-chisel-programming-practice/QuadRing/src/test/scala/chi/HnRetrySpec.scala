package chi

import chisel3._
import chisel3.simulator.Exceptions.AssertionFailed
import noc.RingParams
import org.chipsalliance.cde.config.Parameters
import org.chipsalliance.diplomacy.lazymodule.LazyModule
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

class HnRetrySpec extends AnyFreeSpec with Matchers with ChiNodeTestSupport {
  private implicit val p: Parameters = Parameters.empty
  private val configs = Seq(
    (ChiNoCParams(), HnParams(), 2),
    (ChiNoCParams(ChiParams(3, 7, 32), RingParams(4, 5)), HnParams(4, 1), 6),
    (ChiNoCParams(ChiParams(2, 1, 6), RingParams(4, 2)), HnParams(), 0)
  )

  private def start(dut: ChiNodeFixtures.HnModule, hn: HnParams, src: Int,
    write: Boolean, txn: Int, address: BigInt = 0): Unit = {
    val port = dut.io
    if (write) writeRequest(port.reqRx.bits, hn.nodeId, src, txn, address)
    else request(port.reqRx.bits, hn.nodeId, src, txn, address)
    port.reqRx.valid.poke(true)
    port.reqRx.ready.expect(true.B)
    dut.clock.step()
    port.reqRx.valid.poke(false)
    if (write) {
      port.rspTx.valid.expect(true.B)
      expectResponse(port.rspTx.bits, src, hn.nodeId, txn, ChiOpcode.Rsp.CompDBIDResp)
      dut.clock.step()
      writeData(port.datRx.bits, hn.nodeId, src, 0, lines.head)
      port.datRx.valid.poke(true)
      port.datRx.ready.expect(true.B)
      dut.clock.step()
      port.datRx.valid.poke(false)
    }
    expectRequest(port.reqTx.bits, hn.snId, hn.nodeId, 0, address,
      if (write) ChiOpcode.Req.WriteNoSnpFull else ChiOpcode.Req.ReadNoSnp)
    dut.clock.step()
  }

  private def sendRetry(dut: ChiNodeFixtures.HnModule, hn: HnParams, grant: Boolean): Unit = {
    response(dut.io.rspRx.bits, hn.nodeId, hn.snId, 0,
      if (grant) ChiOpcode.Rsp.PCrdGrant else ChiOpcode.Rsp.RetryAck)
    dut.io.rspRx.valid.poke(true)
    dut.io.rspRx.ready.expect(true.B)
    dut.clock.step()
    dut.io.rspRx.valid.poke(false)
  }

  private def finish(dut: ChiNodeFixtures.HnModule, hn: HnParams, src: Int, write: Boolean,
    txn: Int, dbId: Int = 0): Unit = {
    val port = dut.io
    port.datTx.ready.poke(false)
    if (write) {
      response(port.rspRx.bits, hn.nodeId, hn.snId, 0, ChiOpcode.Rsp.DBIDResp, dbId)
      port.rspRx.valid.poke(true)
      port.rspRx.ready.expect(true.B)
      dut.clock.step()
      port.rspRx.valid.poke(false)
    } else {
      data(port.datRx.bits, hn.nodeId, hn.snId, 0, lines(2))
      port.datRx.valid.poke(true)
      port.datRx.ready.expect(true.B)
      dut.clock.step()
      port.datRx.valid.poke(false)
    }
    for (_ <- 0 until 4) {
      port.datTx.valid.expect(true.B)
      if (write) expectData(port.datTx.bits, hn.snId, hn.nodeId, dbId, lines.head,
        ChiOpcode.Dat.NonCopyBackWrData)
      else expectData(port.datTx.bits, src, hn.nodeId, txn, lines(2))
      port.reqRx.ready.expect(true.B)
      port.rspTx.valid.expect(false.B)
      port.reqTx.valid.expect(false.B)
      dut.clock.step()
    }
    port.datTx.ready.poke(true)
    dut.clock.step()
    if (write) {
      for (_ <- 0 until 5) {
        port.reqRx.ready.expect(true.B) // SN DAT injection still is not write completion.
        port.datTx.valid.expect(false.B)
        dut.clock.step()
      }
      response(port.rspRx.bits, hn.nodeId, hn.snId, 0, ChiOpcode.Rsp.Comp, dbId)
      port.rspRx.valid.poke(true)
      dut.clock.step()
      port.rspRx.valid.poke(false)
    }
    port.reqRx.ready.expect(true.B)
    port.datTx.valid.expect(false.B)
  }

  for ((config, hn, rn) <- configs; write <- Seq(false, true); grantFirst <- Seq(false, true)) {
    s"HN SN retry preserves write=$write with grantFirst=$grantFirst and ${config.chi}" in {
      simulate(LazyModule(new ChiNodeFixtures.Hn(config, hn)).module) { dut =>
        val port = dut.io
        initialize(port)
        val txn = (1 << config.chi.txnIdWidth) - 1
        val address = if (config.chi.addrWidth == 6) BigInt(0) else BigInt(128)
        start(dut, hn, rn, write, txn, address)
        request(port.reqRx.bits, hn.nodeId, 0, 0, 0)
        port.reqRx.valid.poke(false)
        port.datRx.bits.data.poke(BigInt(0))
        sendRetry(dut, hn, grantFirst)
        for (_ <- 0 until 6) {
          port.reqTx.valid.expect(false.B)
          port.datTx.valid.expect(false.B)
          port.datRx.ready.expect(false.B)
          port.reqRx.ready.expect(true.B)
          port.rspTx.valid.expect(false.B)
          dut.clock.step()
        }
        port.reqTx.ready.poke(false)
        sendRetry(dut, hn, !grantFirst)
        dut.clock.step()
        for (_ <- 0 until 6) {
          port.reqTx.valid.expect(true.B)
          expectRequest(port.reqTx.bits, hn.snId, hn.nodeId, 0, address,
            if (write) ChiOpcode.Req.WriteNoSnpFull else ChiOpcode.Req.ReadNoSnp, allowRetry = false)
          port.datRx.ready.expect(false.B)
          port.rspRx.ready.expect(false.B)
          port.reqRx.ready.expect(true.B)
          port.rspTx.valid.expect(false.B)
          dut.clock.step()
        }
        port.reqRx.valid.poke(false)
        port.reqTx.ready.poke(true)
        dut.clock.step()
        for (_ <- 0 until 5) {
          port.reqTx.valid.expect(false.B)
          port.datTx.valid.expect(false.B)
          dut.clock.step()
        }
        finish(dut, hn, rn, write, txn, 1 << (config.chi.txnIdWidth - 1))
        start(dut, hn, rn, write, txn - 1)
        finish(dut, hn, rn, write, txn - 1)
      }
    }
  }

  for ((grant, field, wrongValue, message) <- Seq(
    (false, "tgtID", 2, "another transaction"), (false, "srcID", 0, "another transaction"),
    (false, "txnID", 1, "another transaction"), (false, "pCrdType", 1, "non-coherent SN responses"),
    (false, "resp", 2, "non-coherent SN responses"), (false, "dbID", 1, "zero RetryAck DBID"),
    (true, "tgtID", 2, "another transaction"), (true, "srcID", 0, "another transaction"),
    (true, "pCrdType", 1, "non-coherent SN responses"), (true, "txnID", 1, "zero PCrdGrant IDs"),
    (true, "dbID", 1, "zero PCrdGrant IDs"), (true, "resp", 2, "non-coherent SN responses"))) {
    s"HN rejects SN grant=$grant with invalid $field" in {
      val error = intercept[AssertionFailed] {
        simulate(LazyModule(new ChiNodeFixtures.Hn(ChiNoCParams(), HnParams())).module) { dut =>
          initialize(dut.io)
          start(dut, HnParams(), 0, write = false, txn = 13)
          response(dut.io.rspRx.bits, 1, 3, 0,
            if (grant) ChiOpcode.Rsp.PCrdGrant else ChiOpcode.Rsp.RetryAck)
          dut.io.rspRx.bits.elements(field).asInstanceOf[UInt].poke(BigInt(wrongValue))
          dut.io.rspRx.valid.poke(true)
          dut.clock.step()
        }
      }
      error.getMessage should include(message)
    }
  }

  for (grant <- Seq(false, true)) {
    s"HN rejects duplicate SN grant=$grant responses" in {
      val error = intercept[AssertionFailed] {
        simulate(LazyModule(new ChiNodeFixtures.Hn(ChiNoCParams(), HnParams())).module) { dut =>
          initialize(dut.io)
          start(dut, HnParams(), 0, write = true, txn = 13)
          sendRetry(dut, HnParams(), grant)
          sendRetry(dut, HnParams(), grant)
        }
      }
      error.getMessage should include("duplicate")
    }
  }

  "HN rejects RetryAck after its credited SN REQ was injected" in {
    val error = intercept[AssertionFailed] {
      simulate(LazyModule(new ChiNodeFixtures.Hn(ChiNoCParams(), HnParams())).module) { dut =>
        initialize(dut.io)
        start(dut, HnParams(), 0, write = true, txn = 13)
        sendRetry(dut, HnParams(), grant = false)
        sendRetry(dut, HnParams(), grant = true)
        dut.clock.step(2)
        sendRetry(dut, HnParams(), grant = false)
      }
    }
    error.getMessage should include("already accepted")
  }

  for (grant <- Seq(false, true)) {
    s"HN cannot accept SN write authorization after only grant=$grant" in {
      val error = intercept[AssertionFailed] {
        simulate(LazyModule(new ChiNodeFixtures.Hn(ChiNoCParams(), HnParams())).module) { dut =>
          initialize(dut.io)
          start(dut, HnParams(), 0, write = true, txn = 13)
          sendRetry(dut, HnParams(), grant)
          response(dut.io.rspRx.bits, 1, 3, 0, ChiOpcode.Rsp.DBIDResp)
          dut.io.rspRx.valid.poke(true)
          dut.clock.step()
        }
      }
      error.getMessage should include("awaiting retry")
    }
  }

  for (write <- Seq(false, true); phase <- Seq("ack", "grant", "blocked retry", "injected retry")) {
    s"reset clears HN retry during $phase with write=$write" in {
      simulate(LazyModule(new ChiNodeFixtures.Hn(ChiNoCParams(), HnParams())).module) { dut =>
        initialize(dut.io)
        start(dut, HnParams(), 0, write, 13)
        if (phase == "ack" || phase.contains("retry")) sendRetry(dut, HnParams(), grant = false)
        if (phase != "ack") sendRetry(dut, HnParams(), grant = true)
        if (phase == "blocked retry") dut.io.reqTx.ready.poke(false)
        if (phase.contains("retry")) dut.clock.step(2)
        dut.reset.poke(true.B)
        dut.clock.step(2)
        dut.reset.poke(false.B)
        initialize(dut.io)
        dut.io.reqRx.ready.expect(true.B)
        dut.io.reqTx.valid.expect(false.B)
        dut.io.datTx.valid.expect(false.B)
        start(dut, HnParams(), 2, write = false, txn = 2)
        finish(dut, HnParams(), 2, write = false, txn = 2)
      }
    }
  }
}
