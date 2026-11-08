package chi

import chisel3._
import chisel3.simulator.Exceptions.AssertionFailed
import noc.RingParams
import org.chipsalliance.cde.config.Parameters
import org.chipsalliance.diplomacy.lazymodule.LazyModule
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

class HnSharedSnoopSpec extends AnyFreeSpec with Matchers with ChiNodeTestSupport {
  private implicit val p: Parameters = Parameters.empty
  private val config = ChiNoCParams()
  private val hn = HnParams(coherent = true, directoryLines = 4)
  private val configs = Seq(
    (config, hn, 0, 2),
    (ChiNoCParams(ChiParams(3, 7, 32), RingParams(4, 5)),
      HnParams(5, 1, coherent = true, directoryLines = 4), 6, 0),
    (ChiNoCParams(ChiParams(2, 1, 8), RingParams(4, 2)),
      HnParams(coherent = true, directoryLines = 4), 2, 0)
  )

  private def issue(dut: ChiNodeFixtures.HnModule, params: HnParams, src: Int, txn: Int,
    address: BigInt = 0, credited: Boolean = false): Unit = {
    request(dut.io.reqRx.bits, params.nodeId, src, txn, address)
    dut.io.reqRx.bits.opcode.poke(ChiOpcode.Req.ReadShared)
    dut.io.reqRx.bits.allowRetry.poke((!credited).B)
    dut.io.reqRx.valid.poke(true)
    dut.io.reqRx.ready.expect(true.B)
    dut.clock.step()
    dut.io.reqRx.valid.poke(false)
  }

  private def snData(dut: ChiNodeFixtures.HnModule, params: HnParams, payload: BigInt): Unit = {
    data(dut.io.datRx.bits, params.nodeId, params.snId, 0, payload)
    dut.io.datRx.valid.poke(true)
    dut.io.datRx.ready.expect(true.B)
    dut.clock.step()
    dut.io.datRx.valid.poke(false)
  }

  private def rnData(dut: ChiNodeFixtures.HnModule, params: HnParams, src: Int, txn: Int,
    payload: BigInt, resp: UInt, ack: Boolean = true): Unit = {
    val port = dut.io
    port.datTx.ready.poke(false)
    for (_ <- 0 until 3) {
      port.datTx.valid.expect(true.B)
      port.datTx.bits.tgtID.expect(src.U)
      port.datTx.bits.srcID.expect(params.nodeId.U)
      port.datTx.bits.txnID.expect(txn.U)
      port.datTx.bits.opcode.expect(ChiOpcode.Dat.CompData)
      port.datTx.bits.resp.expect(resp)
      port.datTx.bits.dbID.expect(0.U)
      port.datTx.bits.data.expect(payload.U)
      port.snpTx.valid.expect(false.B)
      port.reqTx.valid.expect(false.B)
      dut.clock.step()
    }
    port.datTx.ready.poke(true)
    dut.clock.step()
    port.datTx.valid.expect(false.B)
    port.rspRx.ready.expect(true.B)
    if (ack) ackRead(dut, params, src)
  }

  private def ackRead(dut: ChiNodeFixtures.HnModule, params: HnParams, src: Int): Unit = {
    response(dut.io.rspRx.bits, params.nodeId, src, 0, ChiOpcode.Rsp.CompAck)
    dut.io.rspRx.valid.poke(true)
    dut.io.rspRx.ready.expect(true.B)
    dut.clock.step()
    dut.io.rspRx.valid.poke(false)
  }

  private def seed(dut: ChiNodeFixtures.HnModule, params: HnParams, owner: Int,
    address: BigInt = 0): Unit = {
    issue(dut, params, owner, 0, address)
    dut.io.reqTx.valid.expect(true.B)
    expectRequest(dut.io.reqTx.bits, params.snId, params.nodeId, 0, address)
    dut.clock.step()
    snData(dut, params, lines.head)
    rnData(dut, params, owner, 0, lines.head, ChiResp.UC)
  }

  private def expectSnoop(dut: ChiNodeFixtures.HnModule, params: HnParams,
    target: Int, address: BigInt = 0): Unit = {
    val snp = dut.io.snpTx
    snp.valid.expect(true.B)
    snp.bits.dst.expect(target.U)
    snp.bits.payload.srcID.expect(params.nodeId.U)
    snp.bits.payload.txnID.expect(0.U)
    snp.bits.payload.opcode.expect(ChiOpcode.Snp.SnpShared)
    snp.bits.payload.addr.expect(address.U)
    dut.io.reqTx.valid.expect(false.B)
    dut.io.datTx.valid.expect(false.B)
  }

  private def snoopReply(dut: ChiNodeFixtures.HnModule, params: HnParams,
    owner: Int, resp: UInt, dirty: Boolean): Unit = {
    val port = dut.io
    if (dirty) {
      data(port.datRx.bits, params.nodeId, owner, 0, lines(2))
      port.datRx.bits.opcode.poke(ChiOpcode.Dat.SnpRespData)
      port.datRx.bits.resp.poke(resp)
      port.datRx.valid.poke(true)
      port.datRx.ready.expect(true.B)
      dut.clock.step()
      port.datRx.valid.poke(false)
      port.datRx.bits.data.poke(0.U)
    } else {
      response(port.rspRx.bits, params.nodeId, owner, 0, ChiOpcode.Rsp.SnpResp)
      port.rspRx.bits.resp.poke(resp)
      port.rspRx.valid.poke(true)
      port.rspRx.ready.expect(true.B)
      port.datRx.ready.expect(false.B)
      dut.clock.step()
      port.rspRx.valid.poke(false)
    }
  }

  private def snResponse(dut: ChiNodeFixtures.HnModule, params: HnParams,
    opcode: UInt, dbId: Int = 0): Unit = {
    response(dut.io.rspRx.bits, params.nodeId, params.snId, 0, opcode, dbId)
    dut.io.rspRx.valid.poke(true)
    dut.io.rspRx.ready.expect(true.B)
    dut.clock.step()
    dut.io.rspRx.valid.poke(false)
  }

  private def finishSnWrite(dut: ChiNodeFixtures.HnModule, params: HnParams, dbId: Int): Unit = {
    val port = dut.io
    snResponse(dut, params, ChiOpcode.Rsp.DBIDResp, dbId)
    port.datTx.ready.poke(false)
    for (_ <- 0 until 3) {
      port.datTx.valid.expect(true.B)
      expectData(port.datTx.bits, params.snId, params.nodeId, dbId, lines(2), ChiOpcode.Dat.NonCopyBackWrData)
      dut.clock.step()
    }
    port.datTx.ready.poke(true)
    dut.clock.step()
    for (_ <- 0 until 4) {
      port.datTx.valid.expect(false.B) // No read completion until SN's actual write completion.
      port.reqTx.valid.expect(false.B)
      port.rspRx.ready.expect(true.B)
      dut.clock.step()
    }
    snResponse(dut, params, ChiOpcode.Rsp.Comp, dbId)
  }

  for ((network, params, owner, reader) <- configs) {
    s"shared snoop resolves I SC SC_PD I_PD with ${network.chi}" in {
      simulate(LazyModule(new ChiNodeFixtures.Hn(network, params)).module) { dut =>
        val port = dut.io
        val txn = (1 << network.chi.txnIdWidth) - 1
        val address = BigInt((params.directoryLines - 1) * 64)
        val dbId = 1 << (network.chi.txnIdWidth - 1)
        for ((resp, dirty, remains) <- Seq(
          (ChiResp.I, false, false), (ChiResp.SC, false, true),
          (ChiResp.SC_PD, true, true), (ChiResp.I_PD, true, false))) {
          withClue(s"snoop resp=${resp.litValue}: ") {
            initialize(port)
            dut.reset.poke(true)
            dut.clock.step(2)
            dut.reset.poke(false)
            seed(dut, params, owner, address)
            port.snpTx.ready.poke(false)
            port.reqTx.ready.poke(false)
            issue(dut, params, reader, txn, address)
            request(port.reqRx.bits, params.nodeId, reader, 0, 0)
            for (_ <- 0 until 4) {
              expectSnoop(dut, params, owner, address)
              port.rspRx.ready.expect(false.B)
              port.datRx.ready.expect(false.B)
              dut.clock.step()
            }
            port.snpTx.ready.poke(true)
            dut.clock.step()
            dut.clock.step(3)
            port.reqTx.valid.expect(false.B)
            snoopReply(dut, params, owner, resp, dirty)
            for (_ <- 0 until 4) {
              port.reqTx.valid.expect(true.B)
              expectRequest(port.reqTx.bits, params.snId, params.nodeId, 0, address,
                if (dirty) ChiOpcode.Req.WriteNoSnpFull else ChiOpcode.Req.ReadNoSnp)
              port.datTx.valid.expect(false.B)
              port.snpTx.valid.expect(false.B)
              dut.clock.step()
            }
            port.reqTx.ready.poke(true)
            dut.clock.step()
            if (dirty) finishSnWrite(dut, params, dbId)
            else snData(dut, params, lines(1))
            rnData(dut, params, reader, txn, if (dirty) lines(2) else lines(1),
              if (remains) ChiResp.SC else ChiResp.UC)
            // Revisit the line from the old owner. Its peer is now the other directory slot.
            issue(dut, params, owner, txn, address)
            expectSnoop(dut, params, reader, address)
            dut.clock.step()
            snoopReply(dut, params, reader, if (remains) ChiResp.SC else ChiResp.I, dirty = false)
            dut.clock.step()
            snData(dut, params, lines(2))
            rnData(dut, params, owner, txn, lines(2), if (remains) ChiResp.SC else ChiResp.UC)
          }
        }
      }
    }
  }

  for (grantFirst <- Seq(false, true)) {
    s"credited upstream read can snoop and retry its SN write with grantFirst=$grantFirst" in {
      simulate(LazyModule(new ChiNodeFixtures.Hn(config, hn)).module) { dut =>
        val port = dut.io
        initialize(port)
        seed(dut, hn, 0)
        issue(dut, hn, 0, 3, 64)
        dut.clock.step()
        snData(dut, hn, lines.head)
        rnData(dut, hn, 0, 3, lines.head, ChiResp.UC, ack = false)
        issue(dut, hn, 2, 4)
        expectResponse(port.rspTx.bits, 2, hn.nodeId, 4, ChiOpcode.Rsp.RetryAck)
        dut.clock.step()
        ackRead(dut, hn, 0)
        dut.clock.step()
        expectResponse(port.rspTx.bits, 2, hn.nodeId, 0, ChiOpcode.Rsp.PCrdGrant)
        dut.clock.step()
        issue(dut, hn, 2, 4, credited = true)
        expectSnoop(dut, hn, 0)
        dut.clock.step()
        snoopReply(dut, hn, 0, ChiResp.SC_PD, dirty = true)
        expectRequest(port.reqTx.bits, hn.snId, hn.nodeId, 0, 0, ChiOpcode.Req.WriteNoSnpFull)
        dut.clock.step()
        snResponse(dut, hn, if (grantFirst) ChiOpcode.Rsp.PCrdGrant else ChiOpcode.Rsp.RetryAck)
        port.reqTx.ready.poke(false)
        snResponse(dut, hn, if (grantFirst) ChiOpcode.Rsp.RetryAck else ChiOpcode.Rsp.PCrdGrant)
        dut.clock.step()
        for (_ <- 0 until 3) {
          expectRequest(port.reqTx.bits, hn.snId, hn.nodeId, 0, 0,
            ChiOpcode.Req.WriteNoSnpFull, allowRetry = false)
          dut.clock.step()
        }
        port.reqTx.ready.poke(true)
        dut.clock.step()
        finishSnWrite(dut, hn, dbId = 9)
        rnData(dut, hn, 2, 4, lines(2), ChiResp.SC)
      }
    }
  }

  for (dirty <- Seq(false, true); field <- Seq("tgtID", "srcID", "txnID", "opcode", "resp", "dbID")) {
    s"shared snoop rejects dirty=$dirty with invalid $field" in {
      val error = intercept[AssertionFailed] {
        simulate(LazyModule(new ChiNodeFixtures.Hn(config, hn)).module) { dut =>
          val port = dut.io
          initialize(port)
          seed(dut, hn, 0)
          issue(dut, hn, 2, 3)
          dut.clock.step()
          if (dirty) {
            data(port.datRx.bits, hn.nodeId, 0, 0, lines(2))
            port.datRx.bits.opcode.poke(ChiOpcode.Dat.SnpRespData)
            port.datRx.bits.resp.poke(ChiResp.SC_PD)
            port.datRx.bits.elements(field).asInstanceOf[UInt].poke(BigInt(field match {
              case "srcID" => 2
              case "tgtID" => 2
              case "opcode" => 4
              case "resp" => 2
              case _ => 1
            }))
            port.datRx.valid.poke(true)
          } else {
            response(port.rspRx.bits, hn.nodeId, 0, 0, ChiOpcode.Rsp.SnpResp)
            port.rspRx.bits.resp.poke(ChiResp.SC)
            port.rspRx.bits.elements(field).asInstanceOf[UInt].poke(BigInt(field match {
              case "srcID" => 2
              case "opcode" => 4
              case "resp" => 2
              case "tgtID" => 2
              case _ => 1
            }))
            port.rspRx.valid.poke(true)
          }
          dut.clock.step()
        }
      }
      error.getMessage should include(if (Set("tgtID", "srcID", "txnID")(field))
        "another transaction" else if (dirty) "snoop data" else "SnpResp")
    }
  }

  "shared snoop rejects nonzero P-Credit type" in {
    val error = intercept[AssertionFailed] {
      simulate(LazyModule(new ChiNodeFixtures.Hn(config, hn)).module) { dut =>
        initialize(dut.io)
        seed(dut, hn, 0)
        issue(dut, hn, 2, 3)
        dut.clock.step()
        response(dut.io.rspRx.bits, hn.nodeId, 0, 0, ChiOpcode.Rsp.SnpResp)
        dut.io.rspRx.bits.pCrdType.poke(1.U)
        dut.io.rspRx.valid.poke(true)
        dut.clock.step()
      }
    }
    error.getMessage should include("SnpResp")
  }

  "reset clears the snoop target, dirty continuation, and directory" in {
    simulate(LazyModule(new ChiNodeFixtures.Hn(config, hn)).module) { dut =>
      val port = dut.io
      for (phase <- Seq("SNP", "response", "SN write", "SN Comp", "RN data", "CompAck")) {
        withClue(s"phase=$phase: ") {
          initialize(port)
          seed(dut, hn, 0)
          port.snpTx.ready.poke((phase != "SNP").B)
          issue(dut, hn, 2, 3)
          if (phase != "SNP") dut.clock.step()
          if (Seq("SN write", "SN Comp", "RN data", "CompAck").contains(phase)) {
            port.reqTx.ready.poke(false)
            snoopReply(dut, hn, 0, ChiResp.SC_PD, dirty = true)
            if (phase != "SN write") {
              port.reqTx.ready.poke(true)
              dut.clock.step()
              snResponse(dut, hn, ChiOpcode.Rsp.DBIDResp, dbId = 9)
              dut.clock.step() // SN DAT.
              if (phase != "SN Comp") {
                port.datTx.ready.poke(false)
                snResponse(dut, hn, ChiOpcode.Rsp.Comp, dbId = 9)
                if (phase == "CompAck") {
                  port.datTx.ready.poke(true)
                  dut.clock.step()
                }
              }
            }
          }
          dut.reset.poke(true)
          dut.clock.step(2)
          dut.reset.poke(false)
          initialize(port)
          port.snpTx.valid.expect(false.B)
          port.reqTx.valid.expect(false.B)
          port.datTx.valid.expect(false.B)
          seed(dut, hn, 2)
          dut.reset.poke(true)
          dut.clock.step(2)
          dut.reset.poke(false)
        }
      }
    }
  }
}
