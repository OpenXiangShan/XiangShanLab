package chi

import chisel3._
import chisel3.simulator.Exceptions.AssertionFailed
import noc.RingParams
import org.chipsalliance.cde.config.Parameters
import org.chipsalliance.diplomacy.lazymodule.LazyModule
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

class HnWriteBackSpec extends AnyFreeSpec with Matchers with ChiNodeTestSupport {
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
    dut.io.datTx.bits.txnID.expect(txn.U)
    dut.io.datTx.bits.opcode.expect(ChiOpcode.Dat.CompData)
    dut.io.datTx.bits.resp.expect(ChiResp.UC)
    dut.clock.step()
    if (acknowledge) ackRead(dut, params, src)
  }

  private def authorize(dut: ChiNodeFixtures.HnModule, params: HnParams, src: Int, txn: Int): Unit = {
    dut.io.rspTx.valid.expect(true.B)
    expectResponse(dut.io.rspTx.bits, src, params.nodeId, txn, ChiOpcode.Rsp.CompDBIDResp)
    dut.clock.step()
    dut.io.datRx.ready.expect(true.B)
  }

  private def copyBack(dut: ChiNodeFixtures.HnModule, params: HnParams, src: Int,
    value: BigInt, resp: UInt = ChiResp.UD_PD): Unit = {
    data(dut.io.datRx.bits, params.nodeId, src, 0, value)
    dut.io.datRx.bits.opcode.poke(ChiOpcode.Dat.CopyBackWrData)
    dut.io.datRx.bits.resp.poke(resp)
    dut.io.datRx.valid.poke(true)
    dut.io.datRx.ready.expect(true.B)
    dut.clock.step()
    dut.io.datRx.valid.poke(false)
  }

  private def snResponse(dut: ChiNodeFixtures.HnModule, params: HnParams,
    opcode: UInt, dbId: Int = 0): Unit = {
    response(dut.io.rspRx.bits, params.nodeId, params.snId, 0, opcode, dbId)
    dut.io.rspRx.valid.poke(true)
    dut.io.rspRx.ready.expect(true.B)
    dut.clock.step()
    dut.io.rspRx.valid.poke(false)
  }

  for ((network, params, rn0, rn1) <- configs) {
    s"WriteBackFull preserves data under backpressure and waits for SN Comp with ${network.chi}" in {
      simulate(LazyModule(new ChiNodeFixtures.Hn(network, params)).module) { dut =>
        val port = dut.io
        initialize(port)
        val txn = (1 << network.chi.txnIdWidth) - 1
        val snSlot = 1 << (network.chi.txnIdWidth - 1)
        val address = BigInt(params.directoryLines - 1) << 6
        val value = (BigInt(1) << 511) | (BigInt(0xfedcba98L) << 256) | BigInt(0xdeadbeefL)
        readLine(dut, params, rn0, txn, address)
        // A different directory row must not be selected by changing the REQ input.
        readLine(dut, params, rn1, txn, 0)
        port.rspTx.ready.poke(false)
        port.reqTx.ready.poke(false)
        port.datTx.ready.poke(false)
        issue(dut, params, rn0, txn, address, ChiOpcode.Req.WriteBackFull)
        data(port.datRx.bits, params.nodeId, rn0, 0, value)
        port.datRx.bits.opcode.poke(ChiOpcode.Dat.CopyBackWrData)
        port.datRx.bits.resp.poke(ChiResp.UD_PD)
        port.datRx.valid.poke(true)
        request(port.reqRx.bits, params.nodeId, rn1, 0, 0)
        for (_ <- 0 until 4) {
          port.rspTx.valid.expect(true.B)
          expectResponse(port.rspTx.bits, rn0, params.nodeId, txn, ChiOpcode.Rsp.CompDBIDResp)
          port.datRx.ready.expect(false.B)
          port.reqTx.valid.expect(false.B)
          dut.clock.step()
        }
        port.datRx.valid.poke(false)
        port.rspTx.ready.poke(true)
        authorize(dut, params, rn0, txn)
        dut.clock.step(3)
        port.reqTx.valid.expect(false.B)
        copyBack(dut, params, rn0, value)
        port.datRx.bits.data.poke(0.U)
        for (_ <- 0 until 4) {
          port.reqTx.valid.expect(true.B)
          expectRequest(port.reqTx.bits, params.snId, params.nodeId, 0, address,
            ChiOpcode.Req.WriteNoSnpFull)
          port.datTx.valid.expect(false.B)
          dut.clock.step()
        }
        port.reqTx.ready.poke(true)
        dut.clock.step()
        dut.clock.step(3)
        port.datTx.valid.expect(false.B)
        snResponse(dut, params, ChiOpcode.Rsp.DBIDResp, snSlot)
        for (_ <- 0 until 4) {
          port.datTx.valid.expect(true.B)
          expectData(port.datTx.bits, params.snId, params.nodeId, snSlot, value,
            ChiOpcode.Dat.NonCopyBackWrData)
          dut.clock.step()
        }
        port.datTx.ready.poke(true)
        dut.clock.step()
        for (_ <- 0 until 4) {
          port.rspRx.ready.expect(true.B)
          port.rspTx.valid.expect(false.B)
          port.reqTx.valid.expect(false.B)
          port.datTx.valid.expect(false.B)
          dut.clock.step()
        }
        snResponse(dut, params, ChiOpcode.Rsp.Comp, snSlot)
        port.rspRx.ready.expect(false.B)
        readLine(dut, params, rn1, txn, address)
      }
    }
  }

  for (grantFirst <- Seq(false, true)) {
    s"upstream and downstream retries are independent with grantFirst=$grantFirst" in {
      simulate(LazyModule(new ChiNodeFixtures.Hn(config, hn)).module) { dut =>
        val port = dut.io
        initialize(port)
        readLine(dut, hn, 0, 3, 0, acknowledge = false)
        issue(dut, hn, 0, 4, 0, ChiOpcode.Req.WriteBackFull)
        port.rspTx.valid.expect(true.B)
        expectResponse(port.rspTx.bits, 0, hn.nodeId, 4, ChiOpcode.Rsp.RetryAck)
        dut.clock.step()
        ackRead(dut, hn, 0)
        dut.clock.step()
        expectResponse(port.rspTx.bits, 0, hn.nodeId, 0, ChiOpcode.Rsp.PCrdGrant)
        dut.clock.step()
        issue(dut, hn, 0, 4, 0, ChiOpcode.Req.WriteBackFull, credited = true)
        authorize(dut, hn, 0, 4)
        copyBack(dut, hn, 0, lines(2))
        port.reqTx.valid.expect(true.B)
        expectRequest(port.reqTx.bits, hn.snId, hn.nodeId, 0, 0, ChiOpcode.Req.WriteNoSnpFull)
        dut.clock.step()
        snResponse(dut, hn, if (grantFirst) ChiOpcode.Rsp.PCrdGrant else ChiOpcode.Rsp.RetryAck)
        dut.clock.step(3)
        port.reqTx.valid.expect(false.B)
        port.datTx.valid.expect(false.B)
        issue(dut, hn, 2, 5, 0, ChiOpcode.Req.ReadShared)
        expectResponse(port.rspTx.bits, 2, hn.nodeId, 5, ChiOpcode.Rsp.RetryAck)
        dut.clock.step()
        port.reqTx.ready.poke(false)
        snResponse(dut, hn, if (grantFirst) ChiOpcode.Rsp.RetryAck else ChiOpcode.Rsp.PCrdGrant)
        dut.clock.step()
        for (_ <- 0 until 4) {
          port.reqTx.valid.expect(true.B)
          expectRequest(port.reqTx.bits, hn.snId, hn.nodeId, 0, 0,
            ChiOpcode.Req.WriteNoSnpFull, allowRetry = false)
          dut.clock.step()
        }
        port.reqTx.ready.poke(true)
        dut.clock.step()
        snResponse(dut, hn, ChiOpcode.Rsp.DBIDResp, dbId = 9)
        port.datTx.valid.expect(true.B)
        expectData(port.datTx.bits, hn.snId, hn.nodeId, 9, lines(2), ChiOpcode.Dat.NonCopyBackWrData)
        dut.clock.step()
        for (_ <- 0 until 4) {
          port.rspTx.valid.expect(false.B)
          port.rspRx.ready.expect(true.B)
          dut.clock.step()
        }
        snResponse(dut, hn, ChiOpcode.Rsp.Comp, dbId = 9)
        dut.clock.step()
        port.rspTx.valid.expect(true.B)
        expectResponse(port.rspTx.bits, 2, hn.nodeId, 0, ChiOpcode.Rsp.PCrdGrant)
        dut.clock.step()
        readLine(dut, hn, 2, 5, 0, credited = true)
      }
    }
  }

  "a cancelled writeback has no SN traffic and preserves another owner" in {
    simulate(LazyModule(new ChiNodeFixtures.Hn(config, hn)).module) { dut =>
      initialize(dut.io)
      readLine(dut, hn, 2, 3, 0)
      issue(dut, hn, 0, 4, 0, ChiOpcode.Req.WriteBackFull)
      authorize(dut, hn, 0, 4)
      copyBack(dut, hn, 0, BigInt(0), ChiResp.I)
      for (_ <- 0 until 6) {
        dut.io.reqTx.valid.expect(false.B)
        dut.io.datTx.valid.expect(false.B)
        dut.io.rspTx.valid.expect(false.B)
        dut.clock.step()
      }
      issue(dut, hn, 0, 5, 0, ChiOpcode.Req.ReadUnique)
      expectSnoopRequest(dut.io, 2, hn.nodeId, 0)
    }
  }

  for ((field, wrongValue, message) <- Seq(
    ("tgtID", 2, "another transaction"), ("srcID", 2, "another transaction"),
    ("txnID", 7, "another transaction"), ("opcode", 3, "expects CopyBackWrData"),
    ("resp", 1, "expects CopyBackWrData"), ("dbID", 1, "expects CopyBackWrData"))) {
    s"WriteBackFull rejects CopyBackWrData with invalid $field" in {
      val error = intercept[AssertionFailed] {
        simulate(LazyModule(new ChiNodeFixtures.Hn(config, hn)).module) { dut =>
          initialize(dut.io)
          readLine(dut, hn, 0, 3, 0)
          issue(dut, hn, 0, 7, 0, ChiOpcode.Req.WriteBackFull)
          authorize(dut, hn, 0, 7)
          data(dut.io.datRx.bits, hn.nodeId, 0, 0, lines(2))
          dut.io.datRx.bits.opcode.poke(ChiOpcode.Dat.CopyBackWrData)
          dut.io.datRx.bits.resp.poke(ChiResp.UD_PD)
          dut.io.datRx.bits.elements(field).asInstanceOf[UInt].poke(BigInt(wrongValue))
          dut.io.datRx.valid.poke(true)
          dut.clock.step()
        }
      }
      error.getMessage should include(message)
    }
  }

  for (holder <- Seq("absent", "another RN", "another address")) {
    s"dirty writeback requires ownership when the holder is $holder" in {
      val error = intercept[AssertionFailed] {
        simulate(LazyModule(new ChiNodeFixtures.Hn(config, hn)).module) { dut =>
          initialize(dut.io)
          if (holder != "absent") readLine(dut, hn, if (holder == "another RN") 2 else 0, 3,
            if (holder == "another address") BigInt(64) else BigInt(0))
          issue(dut, hn, 0, 4, 0, ChiOpcode.Req.WriteBackFull)
          authorize(dut, hn, 0, 4)
          copyBack(dut, hn, 0, lines(2))
        }
      }
      error.getMessage should include("dirty writeback requires the unique holder")
    }
  }

  for (afterData <- Seq(false, true)) {
    s"reset cancels WriteBackFull afterData=$afterData and clears its directory context" in {
      simulate(LazyModule(new ChiNodeFixtures.Hn(config, hn)).module) { dut =>
        initialize(dut.io)
        readLine(dut, hn, 0, 3, 0)
        issue(dut, hn, 0, 4, 0, ChiOpcode.Req.WriteBackFull)
        authorize(dut, hn, 0, 4)
        if (afterData) {
          dut.io.reqTx.ready.poke(false)
          copyBack(dut, hn, 0, lines(2))
        }
        dut.reset.poke(true.B)
        dut.clock.step(2)
        dut.reset.poke(false.B)
        initialize(dut.io)
        dut.io.reqTx.valid.expect(false.B)
        dut.io.datTx.valid.expect(false.B)
        dut.io.rspTx.valid.expect(false.B)
        readLine(dut, hn, 2, 5, 0)
      }
    }
  }

  "WriteBackFull cannot address a row beyond the coherent directory" in {
    val error = intercept[AssertionFailed] {
      simulate(LazyModule(new ChiNodeFixtures.Hn(config, hn)).module) { dut =>
        initialize(dut.io)
        issue(dut, hn, 0, 3, BigInt(hn.directoryLines * 64), ChiOpcode.Req.WriteBackFull)
      }
    }
    error.getMessage should include("address exceeds directory range")
  }

  "non-coherent mode rejects WriteBackFull" in {
    val error = intercept[AssertionFailed] {
      simulate(LazyModule(new ChiNodeFixtures.Hn(config, HnParams())).module) { dut =>
        initialize(dut.io)
        issue(dut, HnParams(), 0, 3, 0, ChiOpcode.Req.WriteBackFull)
      }
    }
    error.getMessage should include("only ReadNoSnp and WriteNoSnpFull")
  }
}
