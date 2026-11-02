package chi

import chisel3._
import chisel3.simulator.Exceptions.AssertionFailed
import noc.RingParams
import org.chipsalliance.cde.config.Parameters
import org.chipsalliance.diplomacy.lazymodule.LazyModule
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

class HnUniqueSnoopSpec extends AnyFreeSpec with Matchers with ChiNodeTestSupport {
  private implicit val p: Parameters = Parameters.empty
  private val config = ChiNoCParams()
  private val hn = HnParams(coherent = true, directoryLines = 4)
  private val configs = Seq(
    (config, hn, 0, 2),
    (ChiNoCParams(ChiParams(3, 7, 32), RingParams(4, 5)),
      HnParams(5, 1, coherent = true, directoryLines = 4), 6, 0),
    (ChiNoCParams(ChiParams(2, 1, 8), RingParams(4, 2)), hn, 2, 0)
  )

  private def issue(dut: ChiNodeFixtures.HnModule, params: HnParams, src: Int,
    txn: Int = 1, address: BigInt = 192, opcode: UInt = ChiOpcode.Req.ReadUnique,
    credited: Boolean = false): Unit = {
    request(dut.io.reqRx.bits, params.nodeId, src, txn, address)
    dut.io.reqRx.bits.opcode.poke(opcode)
    dut.io.reqRx.bits.allowRetry.poke((!credited).B)
    dut.io.reqRx.valid.poke(true)
    dut.io.reqRx.ready.expect(true.B)
    dut.clock.step()
    dut.io.reqRx.valid.poke(false)
  }

  private def ack(dut: ChiNodeFixtures.HnModule, params: HnParams, src: Int): Unit = {
    response(dut.io.rspRx.bits, params.nodeId, src, 0, ChiOpcode.Rsp.CompAck)
    dut.io.rspRx.valid.poke(true)
    dut.io.rspRx.ready.expect(true.B)
    dut.clock.step()
    dut.io.rspRx.valid.poke(false)
  }

  private def complete(dut: ChiNodeFixtures.HnModule, params: HnParams, src: Int,
    value: BigInt, resp: UInt, txn: Int = 1, acknowledge: Boolean = true): Unit = {
    val port = dut.io
    port.datTx.ready.poke(false)
    for (_ <- 0 until 4) {
      port.datTx.valid.expect(true.B)
      port.datTx.bits.tgtID.expect(src.U)
      port.datTx.bits.srcID.expect(params.nodeId.U)
      port.datTx.bits.txnID.expect(txn.U)
      port.datTx.bits.opcode.expect(ChiOpcode.Dat.CompData)
      port.datTx.bits.dbID.expect(0.U)
      port.datTx.bits.resp.expect(resp)
      port.datTx.bits.data.expect(value.U)
      port.reqTx.valid.expect(false.B)
      port.snpTx.valid.expect(false.B)
      dut.clock.step()
    }
    port.datTx.ready.poke(true)
    dut.clock.step()
    for (_ <- 0 until 3) {
      port.datTx.valid.expect(false.B)
      port.rspRx.ready.expect(true.B)
      dut.clock.step()
    }
    if (acknowledge) ack(dut, params, src)
  }

  private def memoryData(dut: ChiNodeFixtures.HnModule, params: HnParams,
    credited: Boolean = false): Unit = {
    dut.io.reqTx.valid.expect(true.B)
    expectRequest(dut.io.reqTx.bits, params.snId, params.nodeId, 0, 192,
      allowRetry = !credited)
    dut.clock.step()
    data(dut.io.datRx.bits, params.nodeId, params.snId, 0, lines.head)
    dut.io.datRx.valid.poke(true)
    dut.io.datRx.ready.expect(true.B)
    dut.clock.step()
    dut.io.datRx.valid.poke(false)
  }

  private def seed(dut: ChiNodeFixtures.HnModule, params: HnParams, owner: Int): Unit = {
    issue(dut, params, owner, opcode = ChiOpcode.Req.ReadShared)
    memoryData(dut, params)
    complete(dut, params, owner, lines.head, ChiResp.UC)
  }

  private def reply(dut: ChiNodeFixtures.HnModule, params: HnParams, owner: Int,
    dirty: Boolean, shared: Boolean = false): Unit = {
    if (dirty) {
      data(dut.io.datRx.bits, params.nodeId, owner, 0, lines(2))
      dut.io.datRx.bits.opcode.poke(ChiOpcode.Dat.SnpRespData)
      dut.io.datRx.bits.resp.poke(ChiResp.I_PD)
      dut.io.datRx.valid.poke(true)
      dut.io.datRx.ready.expect(true.B)
      dut.clock.step()
      dut.io.datRx.valid.poke(false)
      dut.io.datRx.bits.data.poke(0.U)
    } else {
      response(dut.io.rspRx.bits, params.nodeId, owner, 0, ChiOpcode.Rsp.SnpResp)
      dut.io.rspRx.bits.resp.poke(if (shared) ChiResp.SC else ChiResp.I)
      dut.io.rspRx.valid.poke(true)
      dut.io.rspRx.ready.expect(true.B)
      dut.io.datRx.ready.expect(false.B)
      dut.clock.step()
      dut.io.rspRx.valid.poke(false)
    }
  }

  for ((network, params, owner, reader) <- configs) {
    s"unique snoop transfers ownership and upgrades shared holders with ${network.chi}" in {
      simulate(LazyModule(new ChiNodeFixtures.Hn(network, params)).module) { dut =>
        val port = dut.io
        for ((dirty, upgrade, requesterFirst) <- Seq(
          (false, false, false), (true, false, false), (false, true, false), (false, true, true))) {
          initialize(port)
          dut.reset.poke(true)
          dut.clock.step(2)
          dut.reset.poke(false)
          seed(dut, params, owner)
          if (upgrade) {
            issue(dut, params, reader, opcode = ChiOpcode.Req.ReadShared)
            expectSnoopRequest(port, owner, params.nodeId, 192, ChiOpcode.Snp.SnpShared)
            dut.clock.step()
            reply(dut, params, owner, dirty = false, shared = true)
            memoryData(dut, params)
            complete(dut, params, reader, lines.head, ChiResp.SC)
          }
          val requester = if (requesterFirst) owner else reader
          val target = if (requesterFirst) reader else owner
          port.snpTx.ready.poke(false)
          issue(dut, params, requester)
          request(port.reqRx.bits, params.nodeId, owner, 0, 0)
          for (_ <- 0 until 4) {
            expectSnoopRequest(port, target, params.nodeId, 192)
            port.datRx.ready.expect(false.B)
            port.rspRx.ready.expect(false.B)
            dut.clock.step()
          }
          port.snpTx.ready.poke(true)
          dut.clock.step()
          dut.clock.step(3)
          port.reqTx.valid.expect(false.B)
          port.reqTx.ready.poke(false)
          reply(dut, params, target, dirty)
          if (!dirty) {
            for (_ <- 0 until 4) {
              expectRequest(port.reqTx.bits, params.snId, params.nodeId, 0, 192)
              port.reqTx.valid.expect(true.B)
              dut.clock.step()
            }
            port.reqTx.ready.poke(true)
            memoryData(dut, params)
          }
          complete(dut, params, requester, if (dirty) lines(2) else lines.head,
            if (dirty) ChiResp.UD_PD else ChiResp.UC)
          // Revisit from the invalidated peer: only the new owner must be snooped.
          issue(dut, params, target)
          expectSnoopRequest(port, requester, params.nodeId, 192)
          dut.clock.step()
          reply(dut, params, requester, dirty = true)
          complete(dut, params, target, lines(2), ChiResp.UD_PD)
        }
      }
    }
  }

  for (grantFirst <- Seq(false, true); dirty <- Seq(false, true)) {
    s"credited unique read preserves completion with dirty=$dirty grantFirst=$grantFirst" in {
      simulate(LazyModule(new ChiNodeFixtures.Hn(config, hn)).module) { dut =>
        val port = dut.io
        initialize(port)
        issue(dut, hn, 0, opcode = ChiOpcode.Req.ReadShared)
        memoryData(dut, hn)
        complete(dut, hn, 0, lines.head, ChiResp.UC, acknowledge = false)
        issue(dut, hn, 2)
        expectResponse(port.rspTx.bits, 2, hn.nodeId, 1, ChiOpcode.Rsp.RetryAck)
        dut.clock.step()
        ack(dut, hn, 0)
        dut.clock.step()
        expectResponse(port.rspTx.bits, 2, hn.nodeId, 0, ChiOpcode.Rsp.PCrdGrant)
        dut.clock.step()
        issue(dut, hn, 2, credited = true)
        expectSnoopRequest(port, 0, hn.nodeId, 192)
        dut.clock.step()
        reply(dut, hn, 0, dirty)
        if (!dirty) {
          dut.clock.step() // Initial SN read is rejected.
          for (opcode <- (if (grantFirst) Seq(ChiOpcode.Rsp.PCrdGrant, ChiOpcode.Rsp.RetryAck)
            else Seq(ChiOpcode.Rsp.RetryAck, ChiOpcode.Rsp.PCrdGrant))) {
            response(port.rspRx.bits, hn.nodeId, hn.snId, 0, opcode)
            port.rspRx.valid.poke(true)
            port.rspRx.ready.expect(true.B)
            dut.clock.step()
          }
          port.rspRx.valid.poke(false)
          port.reqTx.ready.poke(false)
          dut.clock.step()
          for (_ <- 0 until 3) {
            port.reqTx.valid.expect(true.B)
            expectRequest(port.reqTx.bits, hn.snId, hn.nodeId, 0, 192, allowRetry = false)
            dut.clock.step()
          }
          port.reqTx.ready.poke(true)
          memoryData(dut, hn, credited = true)
        }
        complete(dut, hn, 2, if (dirty) lines(2) else lines.head,
          if (dirty) ChiResp.UD_PD else ChiResp.UC)
      }
    }
  }

  for (dirty <- Seq(false, true);
    field <- Seq("tgtID", "srcID", "txnID", "opcode", "resp", "dbID")) {
    s"unique snoop rejects dirty=$dirty with invalid $field" in {
      val error = intercept[AssertionFailed] {
        simulate(LazyModule(new ChiNodeFixtures.Hn(config, hn)).module) { dut =>
          val port = dut.io
          initialize(port)
          seed(dut, hn, 0)
          issue(dut, hn, 2)
          dut.clock.step()
          val wrong = BigInt(field match {
            case "tgtID" | "srcID" => 2
            case "opcode" => 4
            case "resp" => if (dirty) ChiResp.SC_PD.litValue.toInt else ChiResp.SC.litValue.toInt
            case _ => 1
          })
          if (dirty) {
            data(port.datRx.bits, hn.nodeId, 0, 0, lines(2))
            port.datRx.bits.opcode.poke(ChiOpcode.Dat.SnpRespData)
            port.datRx.bits.resp.poke(ChiResp.I_PD)
            port.datRx.bits.elements(field).asInstanceOf[UInt].poke(wrong)
            port.datRx.valid.poke(true)
          } else {
            response(port.rspRx.bits, hn.nodeId, 0, 0, ChiOpcode.Rsp.SnpResp)
            port.rspRx.bits.elements(field).asInstanceOf[UInt].poke(wrong)
            port.rspRx.valid.poke(true)
          }
          dut.clock.step()
        }
      }
      error.getMessage should include(if (Set("tgtID", "srcID", "txnID")(field))
        "another transaction" else if (dirty) "snoop data" else "SnpResp")
    }
  }

  "reset discards unique snoop context and dirty completion" in {
    simulate(LazyModule(new ChiNodeFixtures.Hn(config, hn)).module) { dut =>
      val port = dut.io
      for (phase <- Seq("SNP", "response", "data", "CompAck")) {
        initialize(port)
        seed(dut, hn, 0)
        port.snpTx.ready.poke((phase != "SNP").B)
        issue(dut, hn, 2)
        if (phase != "SNP") dut.clock.step()
        if (phase == "data" || phase == "CompAck") {
          reply(dut, hn, 0, dirty = true)
          port.datTx.ready.poke(false)
          if (phase == "CompAck") {
            port.datTx.ready.poke(true)
            dut.clock.step()
          }
        }
        dut.reset.poke(true)
        dut.clock.step(2)
        dut.reset.poke(false)
        initialize(port)
        port.datTx.valid.expect(false.B)
        port.snpTx.valid.expect(false.B)
        seed(dut, hn, 2)
        dut.reset.poke(true)
        dut.clock.step(2)
        dut.reset.poke(false)
      }
    }
  }
}
