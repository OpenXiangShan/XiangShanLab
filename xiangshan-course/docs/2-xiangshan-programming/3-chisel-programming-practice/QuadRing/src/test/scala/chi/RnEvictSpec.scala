package chi

import chisel3._
import chisel3.simulator.Exceptions.AssertionFailed
import noc.RingParams
import org.chipsalliance.cde.config.Parameters
import org.chipsalliance.diplomacy.lazymodule.LazyModule
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

class RnEvictSpec extends AnyFreeSpec with Matchers with ChiNodeTestSupport {
  private implicit val p: Parameters = Parameters.empty
  private val config = ChiNoCParams()
  private val rn = RnParams(coherent = true, cacheLines = 4)
  private val configs = Seq(
    config -> rn,
    ChiNoCParams(ChiParams(3, 7, 32), RingParams(4, 5)) ->
      RnParams(6, 4, coherent = true, cacheLines = 8),
    ChiNoCParams(ChiParams(2, 1, 8), RingParams(4, 2)) ->
      RnParams(coherent = true, cacheLines = 2)
  )

  private def start(dut: ChiNodeFixtures.RnModule, txn: Int, address: BigInt,
    write: Boolean = false): Unit = {
    if (write) localWriteRequest(dut.io.local, txn, address, lines(2))
    else localRequest(dut.io.local, txn, address)
    dut.io.local.request.ready.expect(true.B)
    dut.clock.step()
    dut.io.local.request.valid.poke(false)
  }

  private def complete(dut: ChiNodeFixtures.RnModule, params: RnParams, txn: Int,
    value: BigInt, resp: UInt = ChiResp.UC, write: Boolean = false): Unit = {
    val port = dut.io.network
    data(port.datRx.bits, params.nodeId, params.hnId, txn, value)
    port.datRx.bits.resp.poke(resp)
    port.datRx.bits.dbID.poke(1.U)
    port.datRx.valid.poke(true)
    port.datRx.ready.expect(true.B)
    dut.clock.step()
    port.datRx.valid.poke(false)
    expectResponse(port.rspTx.bits, params.hnId, params.nodeId, 1, ChiOpcode.Rsp.CompAck)
    port.rspTx.valid.expect(true.B)
    dut.io.local.response.valid.expect(false.B)
    dut.clock.step()
    for (_ <- 0 until 3) {
      dut.io.local.response.valid.expect(true.B)
      dut.io.local.response.bits.txnID.expect(txn.U)
      dut.io.local.response.bits.data.expect((if (write) BigInt(0) else value).U)
      dut.io.local.request.ready.expect(false.B)
      port.reqTx.valid.expect(false.B)
      port.datTx.valid.expect(false.B)
      port.rspTx.valid.expect(false.B)
      dut.clock.step()
    }
    dut.io.local.response.ready.poke(true)
    dut.clock.step()
    dut.io.local.response.ready.poke(false)
  }

  private def fill(dut: ChiNodeFixtures.RnModule, params: RnParams, txn: Int,
    address: BigInt, resp: UInt = ChiResp.UC): Unit = {
    start(dut, txn, address)
    dut.io.network.reqTx.valid.expect(true.B)
    expectRequest(dut.io.network.reqTx.bits, params.hnId, params.nodeId, txn,
      address, ChiOpcode.Req.ReadShared)
    dut.clock.step()
    complete(dut, params, txn, lines.head, resp)
  }

  private def reply(dut: ChiNodeFixtures.RnModule, params: RnParams,
    txn: Int, opcode: UInt): Unit = {
    val port = dut.io.network
    response(port.rspRx.bits, params.nodeId, params.hnId, txn, opcode)
    port.rspRx.valid.poke(true)
    port.rspRx.ready.expect(true.B)
    dut.clock.step()
    port.rspRx.valid.poke(false)
  }

  for ((network, params) <- configs; write <- Seq(false, true)) {
    s"clean eviction waits for Comp and preserves write=$write with ${network.chi}" in {
      simulate(LazyModule(new ChiNodeFixtures.Rn(network, params)).module) { dut =>
        val port = dut.io.network
        val local = dut.io.local
        initialize(local)
        initialize(port)
        val txn = (1 << network.chi.txnIdWidth) - 1
        val next = BigInt((params.cacheLines - 1) * 64)
        val victim = (BigInt(1) << (network.chi.addrWidth - 1)) | next
        fill(dut, params, 0, victim)
        port.reqTx.ready.poke(false)
        start(dut, txn, next, write)
        // Live local inputs must not change either address or the pending write data.
        local.request.bits.addr.poke(0.U)
        local.request.bits.txnID.poke(0.U)
        local.request.bits.write.poke((!write).B)
        local.request.bits.data.poke(0.U)
        local.request.valid.poke(true)
        for (_ <- 0 until 4) {
          port.reqTx.valid.expect(true.B)
          expectRequest(port.reqTx.bits, params.hnId, params.nodeId, txn, victim, ChiOpcode.Req.Evict)
          local.request.ready.expect(false.B)
          local.response.valid.expect(false.B)
          dut.clock.step()
        }
        port.reqTx.ready.poke(true)
        dut.clock.step()
        for (_ <- 0 until 4) {
          port.reqTx.valid.expect(false.B)
          port.datRx.ready.expect(false.B)
          port.datTx.valid.expect(false.B)
          port.rspTx.valid.expect(false.B)
          local.response.valid.expect(false.B)
          dut.clock.step()
        }
        port.reqTx.ready.poke(false)
        reply(dut, params, txn, ChiOpcode.Rsp.Comp)
        for (_ <- 0 until 4) {
          port.reqTx.valid.expect(true.B)
          expectRequest(port.reqTx.bits, params.hnId, params.nodeId, txn, next,
            if (write) ChiOpcode.Req.ReadUnique else ChiOpcode.Req.ReadShared)
          port.rspTx.valid.expect(false.B) // Evict does not require CompAck.
          local.response.valid.expect(false.B)
          dut.clock.step()
        }
        local.request.valid.poke(false)
        port.reqTx.ready.poke(true)
        dut.clock.step()
        complete(dut, params, txn, lines(1), write = write)
        start(dut, 0, next)
        port.reqTx.valid.expect(false.B)
        local.response.valid.expect(true.B)
        local.response.bits.data.expect((if (write) lines(2) else lines(1)).U)
        local.response.ready.poke(true)
        dut.clock.step()
        local.response.ready.poke(false)
        if (!write) {
          // The old tag was removed, so returning to the victim needs another eviction.
          start(dut, txn, victim)
          expectRequest(port.reqTx.bits, params.hnId, params.nodeId, txn, next, ChiOpcode.Req.Evict)
          dut.clock.step()
          reply(dut, params, txn, ChiOpcode.Rsp.Comp)
          expectRequest(port.reqTx.bits, params.hnId, params.nodeId, txn, victim, ChiOpcode.Req.ReadShared)
          dut.clock.step()
          complete(dut, params, txn, lines.head)
        }
      }
    }
  }

  for (write <- Seq(false, true)) {
    s"a shared clean victim is evicted before write=$write" in {
      simulate(LazyModule(new ChiNodeFixtures.Rn(config, rn)).module) { dut =>
        initialize(dut.io.local)
        initialize(dut.io.network)
        fill(dut, rn, 1, 64, ChiResp.SC)
        start(dut, 2, 64 + rn.cacheLines * 64, write)
        expectRequest(dut.io.network.reqTx.bits, rn.hnId, rn.nodeId, 2, 64, ChiOpcode.Req.Evict)
        dut.clock.step()
        reply(dut, rn, 2, ChiOpcode.Rsp.Comp)
        expectRequest(dut.io.network.reqTx.bits, rn.hnId, rn.nodeId, 2, 64 + rn.cacheLines * 64,
          if (write) ChiOpcode.Req.ReadUnique else ChiOpcode.Req.ReadShared)
        dut.clock.step()
        complete(dut, rn, 2, lines(1), write = write)
      }
    }
  }

  for (grantFirst <- Seq(false, true)) {
    s"Evict and the following read retry independently with grantFirst=$grantFirst" in {
      simulate(LazyModule(new ChiNodeFixtures.Rn(config, rn)).module) { dut =>
        val port = dut.io.network
        initialize(dut.io.local)
        initialize(port)
        fill(dut, rn, 1, 64)
        start(dut, 2, 64 + rn.cacheLines * 64)
        for (evict <- Seq(true, false)) {
          val opcode = if (evict) ChiOpcode.Req.Evict else ChiOpcode.Req.ReadShared
          val address = BigInt(if (evict) 64 else 64 + rn.cacheLines * 64)
          port.reqTx.valid.expect(true.B)
          expectRequest(port.reqTx.bits, rn.hnId, rn.nodeId, 2, address, opcode)
          dut.clock.step()
          for (grant <- Seq(grantFirst, !grantFirst)) {
            reply(dut, rn, if (grant) 0 else 2,
              if (grant) ChiOpcode.Rsp.PCrdGrant else ChiOpcode.Rsp.RetryAck)
            port.reqTx.valid.expect(false.B)
            port.datRx.ready.expect(false.B)
            dut.io.local.response.valid.expect(false.B)
            port.reqTx.ready.poke(false)
            dut.clock.step(3)
          }
          for (_ <- 0 until 4) {
            port.reqTx.valid.expect(true.B)
            expectRequest(port.reqTx.bits, rn.hnId, rn.nodeId, 2, address, opcode, allowRetry = false)
            dut.clock.step()
          }
          port.reqTx.ready.poke(true)
          dut.clock.step()
          if (evict) reply(dut, rn, 2, ChiOpcode.Rsp.Comp)
          else complete(dut, rn, 2, lines(1))
        }
      }
    }
  }

  for ((field, wrong) <- Seq("tgtID" -> 2, "srcID" -> 2, "txnID" -> 3,
    "opcode" -> 5, "resp" -> 1, "dbID" -> 1, "pCrdType" -> 1)) {
    s"Evict rejects Comp with invalid $field" in {
      val error = intercept[AssertionFailed] {
        simulate(LazyModule(new ChiNodeFixtures.Rn(config, rn)).module) { dut =>
          initialize(dut.io.local)
          initialize(dut.io.network)
          fill(dut, rn, 1, 0)
          start(dut, 2, rn.cacheLines * 64)
          dut.clock.step()
          response(dut.io.network.rspRx.bits, rn.nodeId, rn.hnId, 2, ChiOpcode.Rsp.Comp)
          dut.io.network.rspRx.bits.elements(field).asInstanceOf[UInt].poke(BigInt(wrong))
          dut.io.network.rspRx.valid.poke(true)
          dut.clock.step()
        }
      }
      error.getMessage should include(if (Set("tgtID", "srcID", "txnID")(field))
        "another transaction" else "expects Evict Comp")
    }
  }

  for (grant <- Seq(false, true)) {
    s"Evict cannot complete after only grant=$grant" in {
      val error = intercept[AssertionFailed] {
        simulate(LazyModule(new ChiNodeFixtures.Rn(config, rn)).module) { dut =>
          initialize(dut.io.local)
          initialize(dut.io.network)
          fill(dut, rn, 1, 0)
          start(dut, 2, rn.cacheLines * 64)
          dut.clock.step()
          reply(dut, rn, if (grant) 0 else 2,
            if (grant) ChiOpcode.Rsp.PCrdGrant else ChiOpcode.Rsp.RetryAck)
          reply(dut, rn, 2, ChiOpcode.Rsp.Comp)
        }
      }
      error.getMessage should include("without a pending retry")
    }
  }

  for (phase <- Seq("blocked Evict", "wait Comp", "retry", "blocked read")) {
    s"reset discards the replacement context during $phase" in {
      simulate(LazyModule(new ChiNodeFixtures.Rn(config, rn)).module) { dut =>
        val port = dut.io.network
        initialize(dut.io.local)
        initialize(port)
        fill(dut, rn, 1, 0)
        port.reqTx.ready.poke((phase != "blocked Evict").B)
        start(dut, 2, rn.cacheLines * 64)
        if (phase != "blocked Evict") dut.clock.step()
        if (phase == "retry") reply(dut, rn, 2, ChiOpcode.Rsp.RetryAck)
        if (phase == "blocked read") {
          port.reqTx.ready.poke(false)
          reply(dut, rn, 2, ChiOpcode.Rsp.Comp)
        }
        dut.reset.poke(true)
        dut.clock.step(2)
        dut.reset.poke(false)
        initialize(dut.io.local)
        initialize(port)
        port.reqTx.valid.expect(false.B)
        dut.io.local.response.valid.expect(false.B)
        fill(dut, rn, 3, 0)
      }
    }
  }
}
