package chi

import chisel3._
import chisel3.simulator.Exceptions.AssertionFailed
import circt.stage.ChiselStage
import noc.RingParams
import org.chipsalliance.cde.config.Parameters
import org.chipsalliance.diplomacy.lazymodule.LazyModule
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

class HnNodeSpec extends AnyFreeSpec with Matchers with ChiNodeTestSupport {
  private implicit val p: Parameters = Parameters.empty
  private val configs = Seq(
    (ChiNoCParams(), HnParams(), 0, 2),
    (ChiNoCParams(ChiParams(3, 7, 32), RingParams(4, 5)), HnParams(4, 1), 6, 0)
  )

  "HN parameters reject negative IDs and an SN target wider than the negotiated ID" in {
    an[IllegalArgumentException] should be thrownBy HnParams(-1, 3)
    an[IllegalArgumentException] should be thrownBy HnParams(1, -1)
    val error = intercept[IllegalArgumentException] {
      ChiselStage.emitCHIRRTL(LazyModule(new ChiNodeFixtures.Hn(ChiNoCParams(), HnParams(1, 4))).module)
    }
    error.getMessage should include("SN target")
  }

  for ((config, hn, rn0, rn1) <- configs) {
    s"HN ${hn.nodeId} retains the upstream identity across REQ and DAT backpressure" in {
      simulate(LazyModule(new ChiNodeFixtures.Hn(config, hn)).module) { dut =>
        val port = dut.io
        initialize(port)
        for ((src, txn, address, value) <- Seq(
          (rn0, 13, BigInt(128), lines(2)), (rn1, 13, BigInt(0), lines.head))) {
          port.reqTx.ready.poke(false)
          port.datTx.ready.poke(false)
          request(port.reqRx.bits, hn.nodeId, src, txn, address)
          port.reqRx.valid.poke(true)
          port.reqRx.ready.expect(true.B)
          dut.clock.step()
          port.reqRx.valid.poke(false)
          request(port.reqRx.bits, hn.nodeId, rn1, 3, 64)
          for (_ <- 0 until 6) {
            port.reqRx.ready.expect(true.B)
            port.reqTx.valid.expect(true.B)
            expectRequest(port.reqTx.bits, hn.snId, hn.nodeId, 0, address)
            port.datRx.ready.expect(false.B)
            dut.clock.step()
          }
          port.reqTx.ready.poke(true)
          dut.clock.step()
          port.reqTx.valid.expect(false.B)
          port.datRx.ready.expect(true.B)
          dut.clock.step(3)
          data(port.datRx.bits, hn.nodeId, hn.snId, 0, value)
          port.datRx.valid.poke(true)
          dut.clock.step()
          port.datRx.valid.poke(false)
          port.datRx.bits.data.poke(BigInt(0))
          for (_ <- 0 until 6) {
            port.reqRx.ready.expect(true.B)
            port.datTx.valid.expect(true.B)
            expectData(port.datTx.bits, src, hn.nodeId, txn, value)
            port.rspTx.valid.expect(false.B)
            port.snpTx.valid.expect(false.B)
            dut.clock.step()
          }
          port.datTx.ready.poke(true)
          dut.clock.step()
          // Non-coherent read releases at DAT acceptance, without waiting for CompAck.
          port.reqRx.ready.expect(true.B)
          port.datTx.valid.expect(false.B)
          dut.clock.step(3)
          port.datTx.valid.expect(false.B)
        }
      }
    }
  }

  for (wrongField <- Seq("srcID", "txnID", "opcode")) {
    s"SN data with mismatched $wrongField cannot advance the HN transaction" in {
      val error = intercept[AssertionFailed] {
        simulate(LazyModule(new ChiNodeFixtures.Hn(ChiNoCParams(), HnParams())).module) { dut =>
          initialize(dut.io)
          request(dut.io.reqRx.bits, 1, 0, 13, 64)
          dut.io.reqRx.valid.poke(true)
          dut.clock.step()
          dut.io.reqRx.valid.poke(false)
          dut.clock.step()
          data(dut.io.datRx.bits, 1, 3, 0, lines(1))
          dut.io.datRx.bits.elements(wrongField).asInstanceOf[UInt].poke(BigInt(2))
          dut.io.datRx.valid.poke(true)
          dut.clock.step()
        }
      }
      error.getMessage should include(if (wrongField == "opcode") "CompData" else "another transaction")
    }
  }

  "an unsupported REQ is rejected rather than forwarded as a read" in {
    val error = intercept[AssertionFailed] {
      simulate(LazyModule(new ChiNodeFixtures.Hn(ChiNoCParams(), HnParams())).module) { dut =>
        initialize(dut.io)
        request(dut.io.reqRx.bits, 1, 0, 3, 0)
        dut.io.reqRx.bits.opcode.poke(ChiOpcode.Req.ReadShared)
        dut.io.reqRx.valid.poke(true)
        dut.clock.step()
      }
    }
    error.getMessage should include("only ReadNoSnp")
  }

  private val writeConfigs = configs :+
    ((ChiNoCParams(ChiParams(2, 1, 6), RingParams(4, 2)), HnParams(), 0, 2))
  for ((config, hn, rn0, rn1) <- writeConfigs) {
    s"HN ${hn.nodeId} retains a write until SN Comp with parameters ${config.chi}" in {
      simulate(LazyModule(new ChiNodeFixtures.Hn(config, hn)).module) { dut =>
        val port = dut.io
        initialize(port)
        val txn = (1 << config.chi.txnIdWidth) - 1
        val snSlot = 1 << (config.chi.txnIdWidth - 1)
        val address = if (config.chi.addrWidth == 6) BigInt(0) else BigInt(128)
        for ((src, addr, value) <- Seq((rn0, address, lines.head), (rn1, BigInt(0), lines(2)))) {
          port.rspTx.ready.poke(false)
          port.reqTx.ready.poke(false)
          port.datTx.ready.poke(false)
          writeRequest(port.reqRx.bits, hn.nodeId, src, txn, addr)
          port.reqRx.valid.poke(true)
          port.reqRx.ready.expect(true.B)
          dut.clock.step()
          // A second REQ and early DAT cannot replace the active request.
          request(port.reqRx.bits, hn.nodeId, rn1, 0, 0)
          writeData(port.datRx.bits, hn.nodeId, src, 0, value)
          port.datRx.valid.poke(true)
          port.reqRx.valid.poke(false)
          for (_ <- 0 until 6) {
            port.reqRx.ready.expect(true.B)
            port.datRx.ready.expect(false.B)
            port.reqTx.valid.expect(false.B)
            port.rspTx.valid.expect(true.B)
            expectResponse(port.rspTx.bits, src, hn.nodeId, txn, ChiOpcode.Rsp.CompDBIDResp)
            dut.clock.step()
          }
          port.rspTx.ready.poke(true)
          dut.clock.step()
          port.datRx.valid.poke(false)
          for (_ <- 0 until 3) {
            port.datRx.ready.expect(true.B)
            port.reqRx.ready.expect(true.B)
            port.reqTx.valid.expect(false.B)
            port.rspTx.valid.expect(false.B)
            dut.clock.step()
          }
          port.datRx.valid.poke(true)
          dut.clock.step()
          port.datRx.valid.poke(false)
          port.datRx.bits.data.poke(BigInt(0))
          for (_ <- 0 until 6) {
            port.reqRx.ready.expect(true.B)
            port.reqTx.valid.expect(true.B)
            expectRequest(port.reqTx.bits, hn.snId, hn.nodeId, 0, addr, ChiOpcode.Req.WriteNoSnpFull)
            port.rspRx.ready.expect(false.B)
            port.datTx.valid.expect(false.B)
            dut.clock.step()
          }
          port.reqTx.ready.poke(true)
          dut.clock.step()
          for (_ <- 0 until 3) {
            port.reqRx.ready.expect(true.B)
            port.rspRx.ready.expect(true.B)
            port.datTx.valid.expect(false.B)
            dut.clock.step()
          }
          response(port.rspRx.bits, hn.nodeId, hn.snId, 0, ChiOpcode.Rsp.DBIDResp, snSlot)
          port.rspRx.valid.poke(true)
          dut.clock.step()
          port.rspRx.valid.poke(false)
          port.rspRx.bits.dbID.poke(BigInt(0))
          for (_ <- 0 until 6) {
            port.reqRx.ready.expect(true.B)
            port.rspRx.ready.expect(false.B)
            port.datTx.valid.expect(true.B)
            expectData(port.datTx.bits, hn.snId, hn.nodeId, snSlot, value, ChiOpcode.Dat.NonCopyBackWrData)
            dut.clock.step()
          }
          port.datTx.ready.poke(true)
          dut.clock.step()
          // Sending data cannot release the HN before SN confirms the memory write.
          for (_ <- 0 until 6) {
            port.reqRx.ready.expect(true.B)
            port.rspRx.ready.expect(true.B)
            port.reqTx.valid.expect(false.B)
            port.rspTx.valid.expect(false.B)
            port.datTx.valid.expect(false.B)
            dut.clock.step()
          }
          port.reqRx.valid.poke(false)
          response(port.rspRx.bits, hn.nodeId, hn.snId, 0, ChiOpcode.Rsp.Comp, snSlot)
          port.rspRx.valid.poke(true)
          dut.clock.step()
          port.rspRx.valid.poke(false)
          for (_ <- 0 until 3) {
            port.reqRx.ready.expect(true.B)
            port.reqTx.valid.expect(false.B)
            port.rspTx.valid.expect(false.B)
            port.datTx.valid.expect(false.B)
            dut.clock.step()
          }
        }
      }
    }
  }

  private def startWrite(dut: ChiNodeFixtures.HnModule): Unit = {
    initialize(dut.io)
    writeRequest(dut.io.reqRx.bits, 1, 0, 13, 64)
    dut.io.reqRx.valid.poke(true)
    dut.clock.step()
    dut.io.reqRx.valid.poke(false)
    dut.clock.step() // Accept CompDBIDResp.
  }

  private def issueSnWrite(dut: ChiNodeFixtures.HnModule): Unit = {
    startWrite(dut)
    writeData(dut.io.datRx.bits, 1, 0, 0, lines.head)
    dut.io.datRx.valid.poke(true)
    dut.clock.step()
    dut.io.datRx.valid.poke(false)
    dut.clock.step() // Accept the downstream REQ.
  }

  for ((field, wrongValue, message) <- Seq(
    ("tgtID", 2, "another transaction"), ("srcID", 2, "another transaction"),
    ("txnID", 13, "another transaction"), ("opcode", 4, "NonCopyBackWrData"),
    ("resp", 2, "NonCopyBackWrData"), ("dbID", 1, "NonCopyBackWrData"))) {
    s"HN rejects RN write data with invalid $field" in {
      val error = intercept[AssertionFailed] {
        simulate(LazyModule(new ChiNodeFixtures.Hn(ChiNoCParams(), HnParams())).module) { dut =>
          startWrite(dut)
          writeData(dut.io.datRx.bits, 1, 0, 0, lines.head)
          dut.io.datRx.bits.elements(field).asInstanceOf[UInt].poke(BigInt(wrongValue))
          dut.io.datRx.valid.poke(true)
          dut.clock.step()
        }
      }
      error.getMessage should include(message)
    }
  }

  for (completing <- Seq(false, true)) {
    val phase = if (completing) "Comp" else "DBIDResp"
    val wrongFields = Seq(
      ("tgtID", 2, "another transaction"), ("srcID", 0, "another transaction"),
      ("txnID", 13, "another transaction"),
      ("opcode", if (completing) 6 else 4, if (completing) "write completion" else "DBIDResp"),
      ("resp", 2, "non-coherent SN responses"), ("pCrdType", 1, "non-coherent SN responses")) ++
      (if (completing) Seq(("dbID", 3, "another data slot")) else Seq.empty)
    for ((field, wrongValue, message) <- wrongFields) {
      s"HN rejects SN $phase with invalid $field" in {
        val error = intercept[AssertionFailed] {
          simulate(LazyModule(new ChiNodeFixtures.Hn(ChiNoCParams(), HnParams())).module) { dut =>
            issueSnWrite(dut)
            if (completing) {
              response(dut.io.rspRx.bits, 1, 3, 0, ChiOpcode.Rsp.DBIDResp, 8)
              dut.io.rspRx.valid.poke(true)
              dut.clock.step()
              dut.io.rspRx.valid.poke(false)
              dut.clock.step() // Accept downstream write DAT.
            }
            response(dut.io.rspRx.bits, 1, 3, 0,
              if (completing) ChiOpcode.Rsp.Comp else ChiOpcode.Rsp.DBIDResp, 8)
            dut.io.rspRx.bits.elements(field).asInstanceOf[UInt].poke(BigInt(wrongValue))
            dut.io.rspRx.valid.poke(true)
            dut.clock.step()
          }
        }
        error.getMessage should include(message)
      }
    }
  }

  for ((field, message) <- Seq("addr" -> "line-aligned", "allowRetry" -> "first attempts",
    "pCrdType" -> "first attempts")) {
    s"HN rejects a write REQ with invalid $field" in {
      val error = intercept[AssertionFailed] {
        simulate(LazyModule(new ChiNodeFixtures.Hn(ChiNoCParams(), HnParams())).module) { dut =>
          initialize(dut.io)
          writeRequest(dut.io.reqRx.bits, 1, 0, 13, 64)
          if (field == "allowRetry") dut.io.reqRx.bits.allowRetry.poke(false)
          else dut.io.reqRx.bits.elements(field).asInstanceOf[UInt].poke(BigInt(1))
          dut.io.reqRx.valid.poke(true)
          dut.clock.step()
        }
      }
      error.getMessage should include(message)
    }
  }

  for ((phase, progress) <- Seq("RN authorization", "RN write data", "SN request", "SN authorization",
    "SN write data", "SN completion").zipWithIndex) {
    s"reset cancels an HN write during $phase and permits a new read" in {
      simulate(LazyModule(new ChiNodeFixtures.Hn(ChiNoCParams(), HnParams())).module) { dut =>
        val port = dut.io
        initialize(port)
        port.rspTx.ready.poke(false)
        port.reqTx.ready.poke(false)
        port.datTx.ready.poke(false)
        writeRequest(port.reqRx.bits, 1, 0, 13, 64)
        port.reqRx.valid.poke(true)
        dut.clock.step()
        port.reqRx.valid.poke(false)
        if (progress >= 1) {
          port.rspTx.ready.poke(true)
          dut.clock.step()
        }
        if (progress >= 2) {
          writeData(port.datRx.bits, 1, 0, 0, lines.head)
          port.datRx.valid.poke(true)
          dut.clock.step()
          port.datRx.valid.poke(false)
        }
        if (progress >= 3) {
          port.reqTx.ready.poke(true)
          dut.clock.step()
        }
        if (progress >= 4) {
          response(port.rspRx.bits, 1, 3, 0, ChiOpcode.Rsp.DBIDResp, 8)
          port.rspRx.valid.poke(true)
          dut.clock.step()
          port.rspRx.valid.poke(false)
        }
        if (progress >= 5) {
          port.datTx.ready.poke(true)
          dut.clock.step()
        }
        dut.reset.poke(true.B)
        dut.clock.step(2)
        dut.reset.poke(false.B)
        initialize(port)
        for (_ <- 0 until 3) {
          port.reqRx.ready.expect(true.B)
          port.reqTx.valid.expect(false.B)
          port.rspTx.valid.expect(false.B)
          port.datTx.valid.expect(false.B)
          dut.clock.step()
        }
        request(port.reqRx.bits, 1, 0, 2, 0)
        port.reqRx.valid.poke(true)
        dut.clock.step()
        port.reqRx.valid.poke(false)
        expectRequest(port.reqTx.bits, 3, 1, 0, 0)
        dut.clock.step()
        data(port.datRx.bits, 1, 3, 0, lines(2))
        port.datRx.valid.poke(true)
        dut.clock.step()
        port.datRx.valid.poke(false)
        port.datTx.valid.expect(true.B)
        expectData(port.datTx.bits, 0, 1, 2, lines(2))
        dut.clock.step()
        port.reqRx.ready.expect(true.B)
      }
    }
  }
}
