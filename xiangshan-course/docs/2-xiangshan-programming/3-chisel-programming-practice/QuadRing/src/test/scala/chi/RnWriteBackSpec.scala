package chi

import chisel3._
import chisel3.simulator.Exceptions.AssertionFailed
import noc.RingParams
import org.chipsalliance.cde.config.Parameters
import org.chipsalliance.diplomacy.lazymodule.LazyModule
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

class RnWriteBackSpec extends AnyFreeSpec with Matchers with ChiNodeTestSupport {
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
  private val oldData = (BigInt(1) << 511) | (BigInt(0xfedcba98L) << 256) | BigInt(0xa5a50000L)
  private val newData = (BigInt(1) << 510) | (BigInt(0x89abcdefL) << 256) | BigInt(0xdeadbeefL)

  private def start(dut: ChiNodeFixtures.RnModule, txn: Int, address: BigInt,
    write: Boolean = false, value: BigInt = BigInt(0)): Unit = {
    if (write) localWriteRequest(dut.io.local, txn, address, value)
    else localRequest(dut.io.local, txn, address)
    dut.io.local.request.ready.expect(true.B)
    dut.clock.step()
    dut.io.local.request.valid.poke(false)
  }

  private def result(dut: ChiNodeFixtures.RnModule, txn: Int, value: BigInt): Unit = {
    for (_ <- 0 until 3) {
      dut.io.local.response.valid.expect(true.B)
      dut.io.local.response.bits.txnID.expect(txn.U)
      dut.io.local.response.bits.data.expect(value.U)
      dut.io.local.request.ready.expect(false.B)
      dut.io.network.reqTx.valid.expect(false.B)
      dut.io.network.datTx.valid.expect(false.B)
      dut.io.network.rspTx.valid.expect(false.B)
      dut.clock.step()
    }
    dut.io.local.response.ready.poke(true)
    dut.clock.step()
    dut.io.local.response.ready.poke(false)
  }

  private def completeRead(dut: ChiNodeFixtures.RnModule, params: RnParams, txn: Int,
    value: BigInt, write: Boolean, resp: UInt = ChiResp.UC): Unit = {
    val port = dut.io.network
    data(port.datRx.bits, params.nodeId, params.hnId, txn, value)
    port.datRx.bits.resp.poke(resp)
    port.datRx.bits.dbID.poke(1.U)
    port.datRx.valid.poke(true)
    port.datRx.ready.expect(true.B)
    dut.clock.step()
    port.datRx.valid.poke(false)
    port.rspTx.valid.expect(true.B)
    expectResponse(port.rspTx.bits, params.hnId, params.nodeId, 1, ChiOpcode.Rsp.CompAck)
    dut.io.local.response.valid.expect(false.B)
    dut.clock.step()
    result(dut, txn, if (write) BigInt(0) else value)
  }

  private def dirtyLine(dut: ChiNodeFixtures.RnModule, params: RnParams,
    address: BigInt = 0): Unit = {
    start(dut, 0, address, write = true, value = oldData)
    dut.io.network.reqTx.valid.expect(true.B)
    expectRequest(dut.io.network.reqTx.bits, params.hnId, params.nodeId, 0,
      address, ChiOpcode.Req.ReadUnique)
    dut.clock.step()
    completeRead(dut, params, 0, lines.head, write = true)
  }

  private def reply(dut: ChiNodeFixtures.RnModule, params: RnParams, txn: Int,
    opcode: UInt, dbId: Int = 0): Unit = {
    val port = dut.io.network
    response(port.rspRx.bits, params.nodeId, params.hnId, txn, opcode, dbId)
    port.rspRx.valid.poke(true)
    port.rspRx.ready.expect(true.B)
    dut.clock.step()
    port.rspRx.valid.poke(false)
  }

  private def expectWriteData(dut: ChiNodeFixtures.RnModule, params: RnParams,
    dbId: Int, value: BigInt): Unit = {
    val port = dut.io.network
    port.datTx.valid.expect(true.B)
    port.datTx.bits.tgtID.expect(params.hnId.U)
    port.datTx.bits.srcID.expect(params.nodeId.U)
    port.datTx.bits.txnID.expect(dbId.U)
    port.datTx.bits.opcode.expect(ChiOpcode.Dat.CopyBackWrData)
    port.datTx.bits.resp.expect(ChiResp.UD_PD)
    port.datTx.bits.dbID.expect(0.U)
    port.datTx.bits.data.expect(value.U)
    port.reqTx.valid.expect(false.B)
    port.rspTx.valid.expect(false.B)
    dut.io.local.request.ready.expect(false.B)
    dut.io.local.response.valid.expect(false.B)
  }

  for ((network, params) <- configs; write <- Seq(false, true)) {
    s"dirty replacement preserves both lines under backpressure with write=$write and ${network.chi}" in {
      simulate(LazyModule(new ChiNodeFixtures.Rn(network, params)).module) { dut =>
        val local = dut.io.local
        val port = dut.io.network
        initialize(local)
        initialize(port)
        val txn = (1 << network.chi.txnIdWidth) - 1
        val dbId = 1 << (network.chi.txnIdWidth - 1)
        val next = BigInt((params.cacheLines - 1) * 64)
        val victim = (BigInt(1) << (network.chi.addrWidth - 1)) | next
        dirtyLine(dut, params, victim)
        port.reqTx.ready.poke(false)
        start(dut, txn, next, write, newData)
        local.request.bits.addr.poke(0.U)
        local.request.bits.txnID.poke(0.U)
        local.request.bits.write.poke((!write).B)
        local.request.bits.data.poke(0.U)
        local.request.valid.poke(true)
        for (_ <- 0 until 4) {
          port.reqTx.valid.expect(true.B)
          expectRequest(port.reqTx.bits, params.hnId, params.nodeId, txn, victim,
            ChiOpcode.Req.WriteBackFull)
          port.datTx.valid.expect(false.B)
          local.request.ready.expect(false.B)
          local.response.valid.expect(false.B)
          dut.clock.step()
        }
        port.reqTx.ready.poke(true)
        dut.clock.step()
        for (_ <- 0 until 4) {
          port.reqTx.valid.expect(false.B)
          port.datTx.valid.expect(false.B)
          port.datRx.ready.expect(false.B)
          local.response.valid.expect(false.B)
          dut.clock.step()
        }
        port.datTx.ready.poke(false)
        reply(dut, params, txn, ChiOpcode.Rsp.CompDBIDResp, dbId)
        port.rspRx.bits.dbID.poke(0.U) // DAT must use the captured DBID, not live RSP bits.
        for (_ <- 0 until 5) {
          expectWriteData(dut, params, dbId, oldData)
          dut.clock.step()
        }
        port.reqTx.ready.poke(false)
        port.datTx.ready.poke(true)
        dut.clock.step()
        for (_ <- 0 until 4) {
          port.reqTx.valid.expect(true.B)
          expectRequest(port.reqTx.bits, params.hnId, params.nodeId, txn, next,
            if (write) ChiOpcode.Req.ReadUnique else ChiOpcode.Req.ReadShared)
          port.datTx.valid.expect(false.B)
          local.response.valid.expect(false.B)
          dut.clock.step()
        }
        local.request.valid.poke(false)
        port.reqTx.ready.poke(true)
        dut.clock.step()
        completeRead(dut, params, txn, lines(1), write)
        start(dut, 0, next)
        result(dut, 0, if (write) newData else lines(1))
        // A second replacement must use the new tag and its current clean/dirty state.
        start(dut, txn, victim)
        expectRequest(port.reqTx.bits, params.hnId, params.nodeId, txn, next,
          if (write) ChiOpcode.Req.WriteBackFull else ChiOpcode.Req.Evict)
        dut.clock.step()
        if (write) {
          reply(dut, params, txn, ChiOpcode.Rsp.CompDBIDResp, dbId)
          expectWriteData(dut, params, dbId, newData)
          dut.clock.step()
        } else reply(dut, params, txn, ChiOpcode.Rsp.Comp)
        expectRequest(port.reqTx.bits, params.hnId, params.nodeId, txn, victim, ChiOpcode.Req.ReadShared)
        dut.clock.step()
        completeRead(dut, params, txn, oldData, write = false)
      }
    }
  }

  for (initialResp <- Seq(ChiResp.UC, ChiResp.SC)) {
    s"local writes to ${initialResp.litValue} preserve the latest dirty data for replacement" in {
      simulate(LazyModule(new ChiNodeFixtures.Rn(config, rn)).module) { dut =>
        initialize(dut.io.local)
        initialize(dut.io.network)
        start(dut, 1, 64)
        dut.clock.step()
        completeRead(dut, rn, 1, lines.head, write = false, resp = initialResp)
        start(dut, 2, 64, write = true, value = oldData)
        if (initialResp.litValue == ChiResp.SC.litValue) {
          expectRequest(dut.io.network.reqTx.bits, rn.hnId, rn.nodeId, 2, 64, ChiOpcode.Req.ReadUnique)
          dut.clock.step()
          completeRead(dut, rn, 2, lines.head, write = true)
        } else result(dut, 2, BigInt(0))
        start(dut, 3, 64, write = true, value = newData)
        result(dut, 3, BigInt(0))
        start(dut, 4, 64 + rn.cacheLines * 64)
        expectRequest(dut.io.network.reqTx.bits, rn.hnId, rn.nodeId, 4, 64, ChiOpcode.Req.WriteBackFull)
        dut.clock.step()
        reply(dut, rn, 4, ChiOpcode.Rsp.CompDBIDResp, dbId = 9)
        expectWriteData(dut, rn, 9, newData)
        dut.clock.step()
        dut.clock.step()
        completeRead(dut, rn, 4, lines(1), write = false)
      }
    }
  }

  for (grantFirst <- Seq(false, true); write <- Seq(false, true)) {
    s"writeback and new request retry independently with grantFirst=$grantFirst write=$write" in {
      simulate(LazyModule(new ChiNodeFixtures.Rn(config, rn)).module) { dut =>
        val port = dut.io.network
        initialize(dut.io.local)
        initialize(port)
        dirtyLine(dut, rn, 64)
        start(dut, 2, 64 + rn.cacheLines * 64, write, newData)
        for (writeback <- Seq(true, false)) {
          val opcode = if (writeback) ChiOpcode.Req.WriteBackFull
            else if (write) ChiOpcode.Req.ReadUnique else ChiOpcode.Req.ReadShared
          val address = BigInt(if (writeback) 64 else 64 + rn.cacheLines * 64)
          port.reqTx.valid.expect(true.B)
          expectRequest(port.reqTx.bits, rn.hnId, rn.nodeId, 2, address, opcode)
          dut.clock.step()
          for (grant <- Seq(grantFirst, !grantFirst)) {
            reply(dut, rn, if (grant) 0 else 2,
              if (grant) ChiOpcode.Rsp.PCrdGrant else ChiOpcode.Rsp.RetryAck)
            port.datTx.valid.expect(false.B)
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
          if (writeback) {
            reply(dut, rn, 2, ChiOpcode.Rsp.CompDBIDResp, dbId = 9)
            expectWriteData(dut, rn, 9, oldData)
            dut.clock.step()
          } else completeRead(dut, rn, 2, lines(1), write)
        }
        start(dut, 3, 64 + rn.cacheLines * 64)
        result(dut, 3, if (write) newData else lines(1))
      }
    }
  }

  for ((field, wrong) <- Seq("tgtID" -> 2, "srcID" -> 2, "txnID" -> 3,
    "opcode" -> 4, "resp" -> 1, "pCrdType" -> 1)) {
    s"writeback rejects CompDBIDResp with invalid $field" in {
      val error = intercept[AssertionFailed] {
        simulate(LazyModule(new ChiNodeFixtures.Rn(config, rn)).module) { dut =>
          initialize(dut.io.local)
          initialize(dut.io.network)
          dirtyLine(dut, rn)
          start(dut, 2, rn.cacheLines * 64)
          dut.clock.step()
          response(dut.io.network.rspRx.bits, rn.nodeId, rn.hnId, 2, ChiOpcode.Rsp.CompDBIDResp)
          dut.io.network.rspRx.bits.elements(field).asInstanceOf[UInt].poke(BigInt(wrong))
          dut.io.network.rspRx.valid.poke(true)
          dut.clock.step()
        }
      }
      error.getMessage should include(if (Set("tgtID", "srcID", "txnID")(field))
        "another transaction" else "expects WriteBackFull CompDBIDResp")
    }
  }

  for (grant <- Seq(false, true)) {
    s"writeback rejects authorization while waiting for grant=$grant" in {
      val error = intercept[AssertionFailed] {
        simulate(LazyModule(new ChiNodeFixtures.Rn(config, rn)).module) { dut =>
          initialize(dut.io.local)
          initialize(dut.io.network)
          dirtyLine(dut, rn)
          start(dut, 2, rn.cacheLines * 64)
          dut.clock.step()
          reply(dut, rn, if (grant) 0 else 2,
            if (grant) ChiOpcode.Rsp.PCrdGrant else ChiOpcode.Rsp.RetryAck)
          reply(dut, rn, 2, ChiOpcode.Rsp.CompDBIDResp)
        }
      }
      error.getMessage should include("awaiting retry")
    }
  }

  for (phase <- Seq("blocked REQ", "wait DBID", "retry", "blocked DAT", "new request")) {
    s"reset clears the dirty replacement during $phase" in {
      simulate(LazyModule(new ChiNodeFixtures.Rn(config, rn)).module) { dut =>
        val port = dut.io.network
        initialize(dut.io.local)
        initialize(port)
        dirtyLine(dut, rn)
        port.reqTx.ready.poke((phase != "blocked REQ").B)
        start(dut, 2, rn.cacheLines * 64)
        if (phase != "blocked REQ") dut.clock.step()
        if (phase == "retry") reply(dut, rn, 2, ChiOpcode.Rsp.RetryAck)
        if (phase == "blocked DAT" || phase == "new request") {
          port.datTx.ready.poke(false)
          reply(dut, rn, 2, ChiOpcode.Rsp.CompDBIDResp, dbId = 9)
          expectWriteData(dut, rn, 9, oldData)
          if (phase == "new request") {
            port.reqTx.ready.poke(false)
            port.datTx.ready.poke(true)
            dut.clock.step()
          }
        }
        dut.reset.poke(true)
        dut.clock.step(2)
        dut.reset.poke(false)
        initialize(dut.io.local)
        initialize(port)
        port.reqTx.valid.expect(false.B)
        port.datTx.valid.expect(false.B)
        dut.io.local.response.valid.expect(false.B)
        dirtyLine(dut, rn) // Reset made the previous dirty tag invalid.
      }
    }
  }
}
