package chi

import chisel3._
import chisel3.simulator.Exceptions.AssertionFailed
import noc.RingParams
import org.chipsalliance.cde.config.Parameters
import org.chipsalliance.diplomacy.lazymodule.LazyModule
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

class RnRetrySpec extends AnyFreeSpec with Matchers with ChiNodeTestSupport {
  private implicit val p: Parameters = Parameters.empty
  private val configs = Seq(
    ChiNoCParams() -> RnParams(),
    ChiNoCParams(ChiParams(3, 7, 32), RingParams(4, 5)) -> RnParams(6, 4),
    ChiNoCParams(ChiParams(2, 1, 6), RingParams(4, 2)) -> RnParams()
  )

  private def start(dut: ChiNodeFixtures.RnModule, write: Boolean, txn: Int,
    address: BigInt = 0): Unit = {
    if (write) localWriteRequest(dut.io.local, txn, address, lines.head)
    else localRequest(dut.io.local, txn, address)
    dut.io.local.request.ready.expect(true.B)
    dut.clock.step()
    dut.io.local.request.valid.poke(false)
    dut.clock.step() // Original REQ injection.
  }

  private def sendRetry(dut: ChiNodeFixtures.RnModule, rn: RnParams, txn: Int, grant: Boolean): Unit = {
    val port = dut.io.network
    response(port.rspRx.bits, rn.nodeId, rn.hnId, if (grant) 0 else txn,
      if (grant) ChiOpcode.Rsp.PCrdGrant else ChiOpcode.Rsp.RetryAck)
    port.rspRx.valid.poke(true)
    port.rspRx.ready.expect(true.B)
    dut.clock.step()
    port.rspRx.valid.poke(false)
  }

  private def finish(dut: ChiNodeFixtures.RnModule, rn: RnParams, write: Boolean, txn: Int,
    dbId: Int = 0): Unit = {
    val port = dut.io.network
    if (write) {
      response(port.rspRx.bits, rn.nodeId, rn.hnId, txn, ChiOpcode.Rsp.CompDBIDResp, dbId)
      port.rspRx.valid.poke(true)
      port.rspRx.ready.expect(true.B)
      dut.clock.step()
      port.rspRx.valid.poke(false)
      port.datTx.ready.poke(false)
      for (_ <- 0 until 4) {
        port.datTx.valid.expect(true.B)
        expectData(port.datTx.bits, rn.hnId, rn.nodeId, dbId, lines.head,
          ChiOpcode.Dat.NonCopyBackWrData)
        dut.io.local.response.valid.expect(false.B)
        dut.clock.step()
      }
      port.datTx.ready.poke(true)
      dut.clock.step()
    } else {
      data(port.datRx.bits, rn.nodeId, rn.hnId, txn, lines(2))
      port.datRx.valid.poke(true)
      port.datRx.ready.expect(true.B)
      dut.clock.step()
      port.datRx.valid.poke(false)
    }
    for (_ <- 0 until 4) {
      dut.io.local.response.valid.expect(true.B)
      dut.io.local.response.bits.txnID.expect(txn.U)
      dut.io.local.response.bits.data.expect((if (write) BigInt(0) else lines(2)).U)
      dut.io.local.request.ready.expect(false.B)
      port.reqTx.valid.expect(false.B)
      port.datTx.valid.expect(false.B)
      dut.clock.step()
    }
    dut.io.local.response.ready.poke(true)
    dut.clock.step()
    dut.io.local.response.ready.poke(false)
    dut.io.local.request.ready.expect(true.B)
  }

  for ((config, rn) <- configs; write <- Seq(false, true); grantFirst <- Seq(false, true)) {
    s"RN retry preserves write=$write with grantFirst=$grantFirst and ${config.chi}" in {
      simulate(LazyModule(new ChiNodeFixtures.Rn(config, rn)).module) { dut =>
        val port = dut.io.network
        initialize(dut.io.local)
        initialize(port)
        val txn = (1 << config.chi.txnIdWidth) - 1
        val address = if (config.chi.addrWidth == 6) BigInt(0) else BigInt(128)
        start(dut, write, txn, address)
        // Changing local inputs cannot alter the saved transaction or write data.
        dut.io.local.request.bits.write.poke(!write)
        dut.io.local.request.bits.addr.poke(BigInt(0))
        dut.io.local.request.bits.txnID.poke(BigInt(0))
        dut.io.local.request.bits.data.poke(BigInt(0))
        dut.io.local.request.valid.poke(true)
        sendRetry(dut, rn, txn, grantFirst)
        for (_ <- 0 until 6) {
          port.reqTx.valid.expect(false.B)
          port.datTx.valid.expect(false.B)
          port.datRx.ready.expect(false.B)
          dut.io.local.response.valid.expect(false.B)
          dut.io.local.request.ready.expect(false.B)
          dut.clock.step()
        }
        port.reqTx.ready.poke(false)
        sendRetry(dut, rn, txn, !grantFirst)
        dut.clock.step() // Schedule the credited request.
        val opcode = if (write) ChiOpcode.Req.WriteNoSnpFull else ChiOpcode.Req.ReadNoSnp
        for (_ <- 0 until 6) {
          port.reqTx.valid.expect(true.B)
          expectRequest(port.reqTx.bits, rn.hnId, rn.nodeId, txn, address, opcode, allowRetry = false)
          port.datTx.valid.expect(false.B)
          port.datRx.ready.expect(false.B)
          port.rspRx.ready.expect(false.B)
          dut.io.local.response.valid.expect(false.B)
          dut.clock.step()
        }
        dut.io.local.request.valid.poke(false)
        port.reqTx.ready.poke(true)
        dut.clock.step()
        for (_ <- 0 until 5) {
          port.reqTx.valid.expect(false.B) // One grant authorizes exactly one retry.
          dut.io.local.response.valid.expect(false.B)
          dut.clock.step()
        }
        finish(dut, rn, write, txn, 1 << (config.chi.txnIdWidth - 1))
        // A new transaction starts without any credit left from the previous one.
        val nextTxn = txn - 1
        if (write) localWriteRequest(dut.io.local, nextTxn, 0, lines.head)
        else localRequest(dut.io.local, nextTxn, 0)
        dut.clock.step()
        dut.io.local.request.valid.poke(false)
        expectRequest(port.reqTx.bits, rn.hnId, rn.nodeId, nextTxn, 0, opcode)
        dut.clock.step()
        finish(dut, rn, write, nextTxn)
      }
    }
  }

  for ((grant, field, wrongValue, message) <- Seq(
    (false, "tgtID", 2, "another transaction"), (false, "srcID", 2, "another transaction"),
    (false, "txnID", 12, "another transaction"), (false, "pCrdType", 1, "credit type zero"),
    (false, "resp", 2, "credit type zero"), (false, "dbID", 1, "zero RetryAck DBID"),
    (true, "tgtID", 2, "another transaction"), (true, "srcID", 2, "another transaction"),
    (true, "pCrdType", 1, "credit type zero"), (true, "txnID", 13, "zero PCrdGrant IDs"),
    (true, "dbID", 1, "zero PCrdGrant IDs"), (true, "resp", 2, "credit type zero"))) {
    s"RN rejects grant=$grant with invalid $field" in {
      val error = intercept[AssertionFailed] {
        simulate(LazyModule(new ChiNodeFixtures.Rn(ChiNoCParams(), RnParams())).module) { dut =>
          initialize(dut.io.local)
          initialize(dut.io.network)
          start(dut, write = false, txn = 13)
          response(dut.io.network.rspRx.bits, 0, 1, if (grant) 0 else 13,
            if (grant) ChiOpcode.Rsp.PCrdGrant else ChiOpcode.Rsp.RetryAck)
          dut.io.network.rspRx.bits.elements(field).asInstanceOf[UInt].poke(BigInt(wrongValue))
          dut.io.network.rspRx.valid.poke(true)
          dut.clock.step()
        }
      }
      error.getMessage should include(message)
    }
  }

  for (grant <- Seq(false, true)) {
    s"RN rejects a duplicate grant=$grant response" in {
      val error = intercept[AssertionFailed] {
        simulate(LazyModule(new ChiNodeFixtures.Rn(ChiNoCParams(), RnParams())).module) { dut =>
          initialize(dut.io.local)
          initialize(dut.io.network)
          start(dut, write = false, txn = 13)
          sendRetry(dut, RnParams(), 13, grant)
          sendRetry(dut, RnParams(), 13, grant)
        }
      }
      error.getMessage should include("duplicate")
    }
  }

  "RN rejects RetryAck after its credited REQ was injected" in {
    val error = intercept[AssertionFailed] {
      simulate(LazyModule(new ChiNodeFixtures.Rn(ChiNoCParams(), RnParams())).module) { dut =>
        initialize(dut.io.local)
        initialize(dut.io.network)
        start(dut, write = false, txn = 13)
        sendRetry(dut, RnParams(), 13, grant = false)
        sendRetry(dut, RnParams(), 13, grant = true)
        dut.clock.step(2)
        sendRetry(dut, RnParams(), 13, grant = false)
      }
    }
    error.getMessage should include("already credited")
  }

  for (grant <- Seq(false, true)) {
    s"RN cannot accept write authorization after only grant=$grant" in {
      val error = intercept[AssertionFailed] {
        simulate(LazyModule(new ChiNodeFixtures.Rn(ChiNoCParams(), RnParams())).module) { dut =>
          initialize(dut.io.local)
          initialize(dut.io.network)
          start(dut, write = true, txn = 13)
          sendRetry(dut, RnParams(), 13, grant)
          response(dut.io.network.rspRx.bits, 0, 1, 13, ChiOpcode.Rsp.CompDBIDResp)
          dut.io.network.rspRx.valid.poke(true)
          dut.clock.step()
        }
      }
      error.getMessage should include("awaiting retry")
    }
  }

  for (write <- Seq(false, true); phase <- Seq("ack", "grant", "blocked retry", "injected retry")) {
    s"reset clears RN retry during $phase with write=$write" in {
      simulate(LazyModule(new ChiNodeFixtures.Rn(ChiNoCParams(), RnParams())).module) { dut =>
        initialize(dut.io.local)
        initialize(dut.io.network)
        start(dut, write, 13)
        if (phase == "ack" || phase.contains("retry")) sendRetry(dut, RnParams(), 13, grant = false)
        if (phase != "ack") sendRetry(dut, RnParams(), 13, grant = true)
        if (phase == "blocked retry") dut.io.network.reqTx.ready.poke(false)
        if (phase.contains("retry")) dut.clock.step(2)
        dut.reset.poke(true.B)
        dut.clock.step(2)
        dut.reset.poke(false.B)
        initialize(dut.io.local)
        initialize(dut.io.network)
        dut.io.local.request.ready.expect(true.B)
        dut.io.local.response.valid.expect(false.B)
        dut.io.network.reqTx.valid.expect(false.B)
        localRequest(dut.io.local, 2, 0)
        dut.clock.step()
        dut.io.local.request.valid.poke(false)
        expectRequest(dut.io.network.reqTx.bits, 1, 0, 2, 0)
        dut.clock.step()
        finish(dut, RnParams(), write = false, txn = 2)
      }
    }
  }
}
