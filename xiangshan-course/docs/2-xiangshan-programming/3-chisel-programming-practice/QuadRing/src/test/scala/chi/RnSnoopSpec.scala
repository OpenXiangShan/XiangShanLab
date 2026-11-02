package chi

import chisel3._
import chisel3.simulator.Exceptions.AssertionFailed
import noc.RingParams
import org.chipsalliance.cde.config.Parameters
import org.chipsalliance.diplomacy.lazymodule.LazyModule
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

class RnSnoopSpec extends AnyFreeSpec with Matchers with ChiNodeTestSupport {
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
  private val lineData = (BigInt(1) << 511) | (BigInt(0xfedcba98L) << 256) | BigInt(0xa5a50000L)
  private val written = lineData ^ (BigInt(1) << 510)

  private def reset(dut: ChiNodeFixtures.RnModule): Unit = {
    initialize(dut.io.local)
    initialize(dut.io.network)
    dut.reset.poke(true)
    dut.clock.step(2)
    dut.reset.poke(false)
  }

  private def start(dut: ChiNodeFixtures.RnModule, address: BigInt, txn: Int = 0,
    write: Boolean = false): Unit = {
    if (write) localWriteRequest(dut.io.local, txn, address, written)
    else localRequest(dut.io.local, txn, address)
    dut.io.local.request.ready.expect(true.B)
    dut.clock.step()
    dut.io.local.request.valid.poke(false)
  }

  private def finishResult(dut: ChiNodeFixtures.RnModule, txn: Int = 0,
    result: BigInt = lineData): Unit = {
    dut.io.local.response.valid.expect(true.B)
    dut.io.local.response.bits.txnID.expect(txn.U)
    dut.io.local.response.bits.data.expect(result.U)
    dut.io.local.response.ready.poke(true)
    dut.clock.step()
    dut.io.local.response.ready.poke(false)
  }

  private def complete(dut: ChiNodeFixtures.RnModule, params: RnParams, resp: UInt,
    txn: Int = 0, ack: Boolean = true, write: Boolean = false): Unit = {
    val port = dut.io.network
    data(port.datRx.bits, params.nodeId, params.hnId, txn, lineData)
    port.datRx.bits.resp.poke(resp)
    port.datRx.bits.dbID.poke(1.U)
    port.datRx.valid.poke(true)
    port.datRx.ready.expect(true.B)
    dut.clock.step()
    port.datRx.valid.poke(false)
    if (ack) {
      port.rspTx.valid.expect(true.B)
      expectResponse(port.rspTx.bits, params.hnId, params.nodeId, 1, ChiOpcode.Rsp.CompAck)
      dut.clock.step()
      finishResult(dut, txn, if (write) BigInt(0) else lineData)
    }
  }

  private def fill(dut: ChiNodeFixtures.RnModule, params: RnParams, address: BigInt,
    resp: UInt = ChiResp.UC): Unit = {
    start(dut, address)
    dut.clock.step()
    complete(dut, params, resp)
  }

  private def prepareSnoop(dut: ChiNodeFixtures.RnModule, params: RnParams,
    address: BigInt, unique: Boolean, txn: Int): Unit = {
    val port = dut.io.network
    port.snpRx.bits.srcID.poke(params.hnId.U)
    port.snpRx.bits.txnID.poke(txn.U)
    port.snpRx.bits.addr.poke(address.U)
    port.snpRx.bits.opcode.poke(if (unique) ChiOpcode.Snp.SnpUnique else ChiOpcode.Snp.SnpShared)
    port.snpRx.valid.poke(true)
  }

  private def snoop(dut: ChiNodeFixtures.RnModule, params: RnParams,
    address: BigInt, unique: Boolean, txn: Int = 1): Unit = {
    prepareSnoop(dut, params, address, unique, txn)
    dut.io.network.snpRx.ready.expect(true.B)
    dut.clock.step()
    dut.io.network.snpRx.valid.poke(false)
    dut.io.network.snpRx.bits.addr.poke(0.U)
    dut.io.network.snpRx.bits.txnID.poke(0.U)
  }

  private def expectSnoop(dut: ChiNodeFixtures.RnModule, params: RnParams, resp: UInt,
    dirty: Boolean, txn: Int = 1, payload: BigInt = lineData): Unit = {
    val port = dut.io.network
    if (dirty) {
      port.datTx.valid.expect(true.B)
      port.datTx.bits.tgtID.expect(params.hnId.U)
      port.datTx.bits.srcID.expect(params.nodeId.U)
      port.datTx.bits.txnID.expect(txn.U)
      port.datTx.bits.opcode.expect(ChiOpcode.Dat.SnpRespData)
      port.datTx.bits.resp.expect(resp)
      port.datTx.bits.dbID.expect(0.U)
      port.datTx.bits.data.expect(payload.U)
    } else {
      port.rspTx.valid.expect(true.B)
      port.rspTx.bits.tgtID.expect(params.hnId.U)
      port.rspTx.bits.srcID.expect(params.nodeId.U)
      port.rspTx.bits.txnID.expect(txn.U)
      port.rspTx.bits.opcode.expect(ChiOpcode.Rsp.SnpResp)
      port.rspTx.bits.resp.expect(resp)
      port.rspTx.bits.dbID.expect(0.U)
      port.rspTx.bits.pCrdType.expect(0.U)
    }
  }

  private def takeSnoop(dut: ChiNodeFixtures.RnModule, params: RnParams,
    resp: UInt, dirty: Boolean, txn: Int = 1): Unit = {
    val port = dut.io.network
    for (_ <- 0 until 3) {
      expectSnoop(dut, params, resp, dirty, txn)
      port.snpRx.ready.expect(false.B)
      dut.clock.step()
    }
    if (dirty) port.datTx.ready.poke(true) else port.rspTx.ready.poke(true)
    dut.clock.step()
    port.datTx.ready.poke(false)
    port.rspTx.ready.poke(false)
    port.snpRx.ready.expect(true.B)
  }

  private def reply(dut: ChiNodeFixtures.RnModule, opcode: UInt, txn: Int = 0,
    dbId: Int = 0): Unit = {
    response(dut.io.network.rspRx.bits, rn.nodeId, rn.hnId, txn, opcode, dbId)
    dut.io.network.rspRx.valid.poke(true)
    dut.io.network.rspRx.ready.expect(true.B)
    dut.clock.step()
    dut.io.network.rspRx.valid.poke(false)
  }

  for ((network, params) <- configs) {
    s"SnpShared and SnpUnique cover I SC UC UD with ${network.chi}" in {
      simulate(LazyModule(new ChiNodeFixtures.Rn(network, params)).module) { dut =>
        val port = dut.io.network
        val address = (BigInt(1) << (network.chi.addrWidth - 1)) | BigInt((params.cacheLines - 1) * 64)
        val txn = (1 << network.chi.txnIdWidth) - 1
        for (initial <- Seq("I", "SC", "UC", "UD"); unique <- Seq(false, true)) {
          withClue(s"initial=$initial unique=$unique: ") {
            reset(dut)
            if (initial != "I") fill(dut, params, address,
              if (initial == "SC") ChiResp.SC else if (initial == "UD") ChiResp.UD_PD else ChiResp.UC)
            port.rspTx.ready.poke(false)
            port.datTx.ready.poke(false)
            // Same index with a different tag must not snoop the resident line.
            snoop(dut, params, address ^ BigInt(params.cacheLines * 64), unique, txn)
            takeSnoop(dut, params, ChiResp.I, dirty = false, txn)
            snoop(dut, params, address, unique, txn)
            val resp = if (initial == "UD") {
              if (unique) ChiResp.I_PD else ChiResp.SC_PD
            } else if (unique || initial == "I") ChiResp.I else ChiResp.SC
            takeSnoop(dut, params, resp, initial == "UD", txn)
            port.datTx.valid.expect(false.B)
            port.rspTx.valid.expect(false.B)
            // After the first snoop, dirty responsibility must not be transferred twice.
            snoop(dut, params, address, unique = false, txn)
            takeSnoop(dut, params, if (unique || initial == "I") ChiResp.I else ChiResp.SC,
              dirty = false, txn)
            port.rspTx.ready.poke(true)
            port.datTx.ready.poke(true)
            start(dut, address)
            if (unique || initial == "I") {
              port.reqTx.valid.expect(true.B)
              expectRequest(port.reqTx.bits, params.hnId, params.nodeId, 0, address, ChiOpcode.Req.ReadShared)
              dut.clock.step()
              complete(dut, params, ChiResp.UC)
            } else {
              port.reqTx.valid.expect(false.B)
              finishResult(dut)
            }
          }
        }
      }
    }
  }

  "a simultaneous CPU command waits for snoop and observes its updated permission" in {
    simulate(LazyModule(new ChiNodeFixtures.Rn(config, rn)).module) { dut =>
      val port = dut.io.network
      for (write <- Seq(false, true); unique <- Seq(false, true)) {
        reset(dut)
        fill(dut, rn, 0)
        port.reqTx.ready.poke(false)
        port.rspTx.ready.poke(false)
        if (write) localWriteRequest(dut.io.local, 2, 0, written)
        else localRequest(dut.io.local, 2, 0)
        prepareSnoop(dut, rn, 0, unique, 3)
        dut.io.local.request.ready.expect(false.B)
        dut.clock.step()
        port.snpRx.valid.poke(false)
        dut.io.local.request.ready.expect(true.B)
        dut.clock.step()
        dut.io.local.request.valid.poke(false)
        takeSnoop(dut, rn, if (unique) ChiResp.I else ChiResp.SC, dirty = false, txn = 3)
        port.rspTx.ready.poke(true)
        if (write || unique) {
          expectRequest(port.reqTx.bits, rn.hnId, rn.nodeId, 2, 0,
            if (write) ChiOpcode.Req.ReadUnique else ChiOpcode.Req.ReadShared)
          port.reqTx.ready.poke(true)
          dut.clock.step()
          complete(dut, rn, ChiResp.UC, txn = 2, write = write)
        } else finishResult(dut, 2)
      }
    }
  }

  "a full snoop reply slot backpressures the next snoop without changing its snapshot" in {
    simulate(LazyModule(new ChiNodeFixtures.Rn(config, rn)).module) { dut =>
      val port = dut.io.network
      reset(dut)
      fill(dut, rn, 0, ChiResp.UD_PD)
      port.datTx.ready.poke(false)
      port.rspTx.ready.poke(false)
      snoop(dut, rn, 0, unique = false, txn = 3)
      prepareSnoop(dut, rn, 0, unique = true, txn = 4)
      takeSnoop(dut, rn, ChiResp.SC_PD, dirty = true, txn = 3)
      dut.clock.step() // The second snoop enters after the first reply has left.
      port.snpRx.valid.poke(false)
      takeSnoop(dut, rn, ChiResp.I, dirty = false, txn = 4)
      port.datTx.valid.expect(false.B)
      port.rspTx.valid.expect(false.B)
    }
  }

  "snoop wins over incoming data and pending CPU writes become visible before CompAck" in {
    simulate(LazyModule(new ChiNodeFixtures.Rn(config, rn)).module) { dut =>
      val port = dut.io.network
      reset(dut)
      fill(dut, rn, 0, ChiResp.SC)
      start(dut, 0, txn = 2, write = true)
      expectRequest(port.reqTx.bits, rn.hnId, rn.nodeId, 2, 0, ChiOpcode.Req.ReadUnique)
      dut.clock.step()
      // Invalidate the old SC copy while its upgrade completion is presented.
      data(port.datRx.bits, rn.nodeId, rn.hnId, 2, lineData)
      port.datRx.bits.resp.poke(ChiResp.UD_PD)
      port.datRx.bits.dbID.poke(9.U)
      port.datRx.valid.poke(true)
      port.rspTx.ready.poke(false)
      prepareSnoop(dut, rn, 0, unique = true, txn = 3)
      port.datRx.ready.expect(false.B)
      dut.clock.step()
      port.snpRx.valid.poke(false)
      port.datRx.ready.expect(true.B)
      dut.clock.step()
      port.datRx.valid.poke(false)
      for (_ <- 0 until 3) {
        expectSnoop(dut, rn, ChiResp.I, dirty = false, txn = 3)
        dut.io.local.response.valid.expect(false.B)
        dut.clock.step()
      }
      port.rspTx.ready.poke(true)
      dut.clock.step() // SnpResp.
      expectResponse(port.rspTx.bits, rn.hnId, rn.nodeId, 9, ChiOpcode.Rsp.CompAck)
      dut.clock.step()
      // Keep the CPU result blocked. A later snoop sees the newly written data.
      port.datTx.ready.poke(false)
      snoop(dut, rn, 0, unique = true, txn = 4)
      for (_ <- 0 until 4) {
        expectSnoop(dut, rn, ChiResp.I_PD, dirty = true, txn = 4, payload = written)
        dut.io.local.response.valid.expect(true.B)
        dut.io.local.response.bits.txnID.expect(2.U)
        dut.io.local.response.bits.data.expect(0.U)
        dut.clock.step()
      }
      port.datTx.ready.poke(true)
      dut.clock.step()
      finishResult(dut, 2, BigInt(0))
    }
  }

  "snoops progress while the main request waits for injection, data, Comp, credit, or local result" in {
    simulate(LazyModule(new ChiNodeFixtures.Rn(config, rn)).module) { dut =>
      val port = dut.io.network
      for (phase <- Seq("REQ", "data", "Comp", "credit", "credited REQ", "result")) {
        withClue(s"phase=$phase: ") {
          reset(dut)
          fill(dut, rn, 0)
          port.reqTx.ready.poke((phase != "REQ").B)
          start(dut, if (phase == "Comp") rn.cacheLines * 64 else 64)
          if (phase != "REQ") dut.clock.step()
          if (phase == "credit" || phase == "credited REQ") reply(dut, ChiOpcode.Rsp.RetryAck)
          if (phase == "credited REQ") {
            port.reqTx.ready.poke(false)
            reply(dut, ChiOpcode.Rsp.PCrdGrant)
            dut.clock.step()
          }
          if (phase == "result") {
            complete(dut, rn, ChiResp.UC, ack = false)
            dut.clock.step() // CompAck, but do not consume the local result.
          }
          port.rspTx.ready.poke(false)
          snoop(dut, rn, 0, unique = true, txn = 3)
          takeSnoop(dut, rn, ChiResp.I, dirty = false, txn = 3)
          port.rspTx.ready.poke(true)
          if (phase == "REQ" || phase == "credited REQ") {
            port.reqTx.ready.poke(true)
            dut.clock.step()
          }
          if (phase == "credit") {
            reply(dut, ChiOpcode.Rsp.PCrdGrant)
            dut.clock.step(2)
          }
          if (phase == "Comp") {
            reply(dut, ChiOpcode.Rsp.Comp)
            dut.clock.step()
          }
          if (phase == "result") finishResult(dut)
          else complete(dut, rn, ChiResp.UC)
        }
      }
    }
  }

  "RSP arbitration preserves a stalled message in either order" in {
    simulate(LazyModule(new ChiNodeFixtures.Rn(config, rn)).module) { dut =>
      val port = dut.io.network
      for (snoopFirst <- Seq(false, true)) {
        reset(dut)
        fill(dut, rn, 0)
        start(dut, 64)
        dut.clock.step()
        port.rspTx.ready.poke(false)
        if (snoopFirst) snoop(dut, rn, 0, unique = true, txn = 3)
        complete(dut, rn, ChiResp.UC, ack = false)
        if (!snoopFirst) snoop(dut, rn, 0, unique = true, txn = 3)
        for (_ <- 0 until 4) {
          if (snoopFirst) expectSnoop(dut, rn, ChiResp.I, dirty = false, txn = 3)
          else expectResponse(port.rspTx.bits, rn.hnId, rn.nodeId, 1, ChiOpcode.Rsp.CompAck)
          dut.io.local.response.valid.expect(false.B)
          dut.clock.step()
        }
        port.rspTx.ready.poke(true)
        dut.clock.step()
        if (snoopFirst) {
          expectResponse(port.rspTx.bits, rn.hnId, rn.nodeId, 1, ChiOpcode.Rsp.CompAck)
          dut.io.local.response.valid.expect(false.B)
        } else expectSnoop(dut, rn, ChiResp.I, dirty = false, txn = 3)
        dut.clock.step()
        finishResult(dut)
      }
    }
  }

  "DAT arbitration preserves a stalled writeback or snoop reply in either order" in {
    simulate(LazyModule(new ChiNodeFixtures.Rn(config, rn)).module) { dut =>
      val port = dut.io.network
      for (snoopFirst <- Seq(false, true)) {
        reset(dut)
        fill(dut, rn, 0, ChiResp.UD_PD)
        fill(dut, rn, 64, ChiResp.UD_PD)
        start(dut, rn.cacheLines * 64)
        dut.clock.step()
        port.datTx.ready.poke(false)
        if (snoopFirst) snoop(dut, rn, 64, unique = true, txn = 3)
        reply(dut, ChiOpcode.Rsp.CompDBIDResp, dbId = 9)
        if (!snoopFirst) snoop(dut, rn, 64, unique = true, txn = 3)
        for (_ <- 0 until 4) {
          if (snoopFirst) expectSnoop(dut, rn, ChiResp.I_PD, dirty = true, txn = 3)
          else {
            port.datTx.bits.opcode.expect(ChiOpcode.Dat.CopyBackWrData)
            port.datTx.bits.txnID.expect(9.U)
            port.datTx.bits.resp.expect(ChiResp.UD_PD)
            port.datTx.bits.data.expect(lineData.U)
          }
          dut.io.local.response.valid.expect(false.B)
          dut.clock.step()
        }
        port.reqTx.ready.poke(false)
        port.datTx.ready.poke(true)
        dut.clock.step()
        if (snoopFirst) {
          port.datTx.bits.opcode.expect(ChiOpcode.Dat.CopyBackWrData)
          port.datTx.bits.txnID.expect(9.U)
          port.datTx.bits.data.expect(lineData.U)
        } else expectSnoop(dut, rn, ChiResp.I_PD, dirty = true, txn = 3)
        dut.clock.step()
        port.datTx.valid.expect(false.B)
        expectRequest(port.reqTx.bits, rn.hnId, rn.nodeId, 0, rn.cacheLines * 64, ChiOpcode.Req.ReadShared)
        port.reqTx.ready.poke(true)
        dut.clock.step()
        complete(dut, rn, ChiResp.UC)
      }
    }
  }

  "snooping a writeback transfers data once and cancels it even during retry" in {
    simulate(LazyModule(new ChiNodeFixtures.Rn(config, rn)).module) { dut =>
      val port = dut.io.network
      for (unique <- Seq(false, true);
        phase <- Seq("REQ", "DBID", "ack", "grant", "credited REQ", "simultaneous DBID")) {
        withClue(s"unique=$unique phase=$phase: ") {
          reset(dut)
          fill(dut, rn, 0, ChiResp.UD_PD)
          port.reqTx.ready.poke((phase != "REQ").B)
          start(dut, rn.cacheLines * 64, txn = 2, write = true)
          if (phase != "REQ") dut.clock.step()
          if (phase == "ack" || phase == "credited REQ") reply(dut, ChiOpcode.Rsp.RetryAck, txn = 2)
          if (phase == "grant" || phase == "credited REQ") reply(dut, ChiOpcode.Rsp.PCrdGrant)
          if (phase == "credited REQ") {
            port.reqTx.ready.poke(false)
            dut.clock.step()
          }
          port.datTx.ready.poke(false)
          port.rspTx.ready.poke(false)
          if (phase == "simultaneous DBID") {
            response(port.rspRx.bits, rn.nodeId, rn.hnId, 2, ChiOpcode.Rsp.CompDBIDResp, dbId = 9)
            port.rspRx.valid.poke(true)
          }
          snoop(dut, rn, 0, unique, txn = 3)
          port.rspRx.valid.poke(false)
          takeSnoop(dut, rn, ChiResp.I_PD, dirty = true, txn = 3)
          snoop(dut, rn, 0, !unique, txn = 4)
          takeSnoop(dut, rn, ChiResp.I, dirty = false, txn = 4)
          port.rspTx.ready.poke(true)
          if (phase == "ack" || phase == "grant") {
            port.reqTx.ready.poke(false)
            reply(dut, if (phase == "ack") ChiOpcode.Rsp.PCrdGrant else ChiOpcode.Rsp.RetryAck,
              txn = if (phase == "ack") 0 else 2)
            dut.clock.step()
          }
          if (phase != "DBID" && phase != "simultaneous DBID") {
            expectRequest(port.reqTx.bits, rn.hnId, rn.nodeId, 2, 0, ChiOpcode.Req.WriteBackFull,
              allowRetry = phase == "REQ")
            port.reqTx.ready.poke(true)
            dut.clock.step()
          }
          if (phase != "simultaneous DBID") reply(dut, ChiOpcode.Rsp.CompDBIDResp, txn = 2, dbId = 9)
          for (_ <- 0 until 4) {
            port.datTx.valid.expect(true.B)
            expectData(port.datTx.bits, rn.hnId, rn.nodeId, 9, BigInt(0), ChiOpcode.Dat.CopyBackWrData)
            port.reqTx.valid.expect(false.B)
            dut.io.local.response.valid.expect(false.B)
            dut.clock.step()
          }
          port.datTx.ready.poke(true)
          dut.clock.step()
          expectRequest(port.reqTx.bits, rn.hnId, rn.nodeId, 2, rn.cacheLines * 64, ChiOpcode.Req.ReadUnique)
          dut.clock.step()
          complete(dut, rn, ChiResp.UC, txn = 2, write = true)
          start(dut, rn.cacheLines * 64)
          finishResult(dut, result = written)
        }
      }
    }
  }

  for ((field, wrong, message) <- Seq(
    ("srcID", 2, "snoops from its HN"), ("addr", 1, "line-aligned snoops"),
    ("opcode", 0, "only SnpShared and SnpUnique"))) {
    s"invalid snoop $field is rejected" in {
      val error = intercept[AssertionFailed] {
        simulate(LazyModule(new ChiNodeFixtures.Rn(config, rn)).module) { dut =>
          reset(dut)
          prepareSnoop(dut, rn, 0, unique = false, txn = 3)
          dut.io.network.snpRx.bits.elements(field).asInstanceOf[UInt].poke(BigInt(wrong))
          dut.clock.step()
        }
      }
      error.getMessage should include(message)
    }
  }

  "HN must not snoop a writeback after granting its data slot" in {
    val error = intercept[AssertionFailed] {
      simulate(LazyModule(new ChiNodeFixtures.Rn(config, rn)).module) { dut =>
        reset(dut)
        fill(dut, rn, 0, ChiResp.UD_PD)
        start(dut, rn.cacheLines * 64)
        dut.clock.step()
        dut.io.network.datTx.ready.poke(false)
        reply(dut, ChiOpcode.Rsp.CompDBIDResp, dbId = 9)
        snoop(dut, rn, 0, unique = true)
      }
    }
    error.getMessage should include("cannot snoop an authorized writeback")
  }

  "non-coherent mode keeps snoops disabled and still completes its read" in {
    simulate(LazyModule(new ChiNodeFixtures.Rn(config, RnParams())).module) { dut =>
      val port = dut.io.network
      reset(dut)
      prepareSnoop(dut, rn, 0, unique = true, txn = 3)
      port.snpRx.ready.expect(false.B)
      start(dut, 64)
      expectRequest(port.reqTx.bits, rn.hnId, rn.nodeId, 0, 64, ChiOpcode.Req.ReadNoSnp)
      dut.clock.step()
      data(port.datRx.bits, rn.nodeId, rn.hnId, 0, lineData)
      port.datRx.valid.poke(true)
      port.datRx.ready.expect(true.B)
      dut.clock.step()
      port.datRx.valid.poke(false)
      port.rspTx.valid.expect(false.B)
      port.datTx.valid.expect(false.B)
      port.snpRx.ready.expect(false.B)
      finishResult(dut)
    }
  }

  "reset clears stalled snoop replies and writeback cancellation" in {
    simulate(LazyModule(new ChiNodeFixtures.Rn(config, rn)).module) { dut =>
      val port = dut.io.network
      for (dirty <- Seq(false, true)) {
        reset(dut)
        fill(dut, rn, 0, if (dirty) ChiResp.UD_PD else ChiResp.UC)
        if (dirty) {
          start(dut, rn.cacheLines * 64)
          dut.clock.step()
        }
        port.rspTx.ready.poke(false)
        port.datTx.ready.poke(false)
        snoop(dut, rn, 0, unique = true)
        expectSnoop(dut, rn, if (dirty) ChiResp.I_PD else ChiResp.I, dirty)
        dut.clock.step(3)
        reset(dut)
        port.rspTx.valid.expect(false.B)
        port.datTx.valid.expect(false.B)
        port.reqTx.valid.expect(false.B)
        port.snpRx.ready.expect(true.B)
        fill(dut, rn, 0, ChiResp.UD_PD)
        start(dut, rn.cacheLines * 64)
        dut.clock.step()
        port.datTx.ready.poke(false)
        reply(dut, ChiOpcode.Rsp.CompDBIDResp, dbId = 9)
        port.datTx.bits.opcode.expect(ChiOpcode.Dat.CopyBackWrData)
        port.datTx.bits.resp.expect(ChiResp.UD_PD)
        port.datTx.bits.data.expect(lineData.U)
      }
    }
  }
}
