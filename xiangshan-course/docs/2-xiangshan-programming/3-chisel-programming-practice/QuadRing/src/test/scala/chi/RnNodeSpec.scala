package chi

import chisel3._
import chisel3.simulator.Exceptions.AssertionFailed
import circt.stage.ChiselStage
import noc.RingParams
import org.chipsalliance.cde.config.Parameters
import org.chipsalliance.diplomacy.lazymodule.LazyModule
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

class RnNodeSpec extends AnyFreeSpec with Matchers with ChiNodeTestSupport {
  private implicit val p: Parameters = Parameters.empty
  private val configs = Seq(
    ChiNoCParams() -> RnParams(),
    ChiNoCParams(ChiParams(3, 7, 32), RingParams(4, 5)) -> RnParams(6, 4)
  )

  "RN parameters reject negative IDs and an HN target wider than the negotiated ID" in {
    an[IllegalArgumentException] should be thrownBy RnParams(-1, 1)
    an[IllegalArgumentException] should be thrownBy RnParams(0, -1)
    val error = intercept[IllegalArgumentException] {
      ChiselStage.emitCHIRRTL(LazyModule(new ChiNodeFixtures.Rn(ChiNoCParams(), RnParams(0, 4))).module)
    }
    error.getMessage should include("HN target")
  }

  for ((config, rn) <- configs) {
    s"RN ${rn.nodeId} holds REQ and local results stable and accepts the next command only after completion" in {
      simulate(LazyModule(new ChiNodeFixtures.Rn(config, rn)).module) { dut =>
        val local = dut.io.local
        val port = dut.io.network
        initialize(local)
        initialize(port)
        for ((txn, address, value) <- Seq((13, BigInt(128), lines(2)), (2, BigInt(0), lines.head))) {
          port.reqTx.ready.poke(false)
          localRequest(local, txn, address)
          local.request.ready.expect(true.B)
          dut.clock.step()
          local.request.valid.poke(false)
          local.request.bits.txnID.poke(BigInt(1))
          local.request.bits.addr.poke(BigInt(64))
          for (_ <- 0 until 6) {
            local.request.ready.expect(false.B)
            port.reqTx.valid.expect(true.B)
            expectRequest(port.reqTx.bits, rn.hnId, rn.nodeId, txn, address)
            port.datRx.ready.expect(false.B)
            dut.clock.step()
          }
          port.reqTx.ready.poke(true)
          dut.clock.step()
          port.reqTx.valid.expect(false.B)
          port.datRx.ready.expect(true.B)
          local.response.valid.expect(false.B)
          dut.clock.step(3)
          data(port.datRx.bits, rn.nodeId, rn.hnId, txn, value)
          port.datRx.valid.poke(true)
          dut.clock.step()
          port.datRx.valid.poke(false)
          port.datRx.bits.data.poke(BigInt(0))
          for (_ <- 0 until 6) {
            local.request.ready.expect(false.B)
            local.response.valid.expect(true.B)
            local.response.bits.txnID.expect(txn.U)
            local.response.bits.data.expect(value.U)
            port.datRx.ready.expect(false.B)
            port.rspTx.valid.expect(false.B) // ReadNoSnp has no CompAck.
            port.snpTx.valid.expect(false.B)
            port.datTx.valid.expect(false.B)
            dut.clock.step()
          }
          local.response.ready.poke(true)
          dut.clock.step()
          local.response.ready.poke(false)
          local.request.ready.expect(true.B)
          local.response.valid.expect(false.B)
          dut.clock.step(3)
          local.response.valid.expect(false.B)
        }
      }
    }
  }

  for ((config, rn) <- configs) {
    s"RN ${rn.nodeId} waits for write authorization, forwards data, and returns a completion" in {
      simulate(LazyModule(new ChiNodeFixtures.Rn(config, rn)).module) { dut =>
        val local = dut.io.local
        val port = dut.io.network
        initialize(local)
        initialize(port)
        val txn = (1 << config.chi.txnIdWidth) - 1
        val dbId = 1 << (config.chi.txnIdWidth - 1)
        val address = if (config.chi.addrWidth == 6) BigInt(0) else BigInt(128)
        val value = (BigInt(1) << 511) | (BigInt(0x5a) << 256) | BigInt(0x1234)

        localWriteRequest(local, txn, address, value)
        local.request.ready.expect(true.B)
        dut.clock.step()
        local.request.valid.poke(false)
        local.request.bits.data.poke(BigInt(0))
        port.reqTx.ready.poke(false)
        for (_ <- 0 until 6) {
          local.request.ready.expect(false.B)
          port.reqTx.valid.expect(true.B)
          expectRequest(port.reqTx.bits, rn.hnId, rn.nodeId, txn, address,
            ChiOpcode.Req.WriteNoSnpFull)
          port.rspRx.ready.expect(false.B)
          port.datTx.valid.expect(false.B)
          dut.clock.step()
        }
        port.reqTx.ready.poke(true)
        dut.clock.step()
        port.reqTx.valid.expect(false.B)
        port.rspRx.ready.expect(true.B)

        response(port.rspRx.bits, rn.nodeId, rn.hnId, txn, ChiOpcode.Rsp.CompDBIDResp, dbId)
        port.rspRx.valid.poke(true)
        port.rspRx.valid.expect(true.B)
        port.rspRx.ready.expect(true.B)
        port.datTx.valid.expect(false.B)
        local.response.valid.expect(false.B)
        dut.clock.step()
        port.rspRx.valid.poke(false)
        port.rspRx.bits.dbID.poke(BigInt(0))
        port.datTx.ready.poke(false)
        for (_ <- 0 until 6) {
          port.datTx.valid.expect(true.B)
          expectData(port.datTx.bits, rn.hnId, rn.nodeId, dbId, value,
            ChiOpcode.Dat.NonCopyBackWrData)
          local.response.valid.expect(false.B)
          dut.clock.step()
        }
        port.datTx.ready.poke(true)
        dut.clock.step()
        port.datTx.valid.expect(false.B)
        for (_ <- 0 until 6) {
          local.request.ready.expect(false.B)
          local.response.valid.expect(true.B)
          local.response.bits.txnID.expect(txn.U)
          local.response.bits.data.expect(0.U)
          dut.clock.step()
        }
        local.response.ready.poke(true)
        dut.clock.step()
        local.response.ready.poke(false)
        local.request.ready.expect(true.B)
        local.response.valid.expect(false.B)
      }
    }
  }

  "misaligned local writes are rejected" in {
    val error = intercept[AssertionFailed] {
      simulate(LazyModule(new ChiNodeFixtures.Rn(ChiNoCParams(), RnParams())).module) { dut =>
        initialize(dut.io.local)
        initialize(dut.io.network)
        localWriteRequest(dut.io.local, 3, 1, lines.head)
        dut.clock.step()
      }
    }
    error.getMessage should include("line-aligned")
  }

  for ((field, wrongValue, message) <- Seq(
    ("tgtID", 2, "another transaction"), ("srcID", 2, "another transaction"),
    ("txnID", 12, "another transaction"), ("opcode", 4, "CompDBIDResp"),
    ("resp", 2, "CompDBIDResp"), ("pCrdType", 1, "CompDBIDResp"))) {
    s"write response with invalid $field cannot authorize RN data" in {
      val error = intercept[AssertionFailed] {
        simulate(LazyModule(new ChiNodeFixtures.Rn(ChiNoCParams(), RnParams())).module) { dut =>
          initialize(dut.io.local)
          initialize(dut.io.network)
          localWriteRequest(dut.io.local, 13, 64, lines.head)
          dut.clock.step()
          dut.io.local.request.valid.poke(false)
          dut.clock.step()
          response(dut.io.network.rspRx.bits, 0, 1, 13, ChiOpcode.Rsp.CompDBIDResp, 8)
          dut.io.network.rspRx.bits.elements(field).asInstanceOf[UInt].poke(BigInt(wrongValue))
          dut.io.network.rspRx.valid.poke(true)
          dut.clock.step()
        }
      }
      error.getMessage should include(message)
    }
  }

  for (wrongField <- Seq("srcID", "txnID", "opcode")) {
    s"data with mismatched $wrongField cannot complete the local read" in {
      val error = intercept[AssertionFailed] {
        simulate(LazyModule(new ChiNodeFixtures.Rn(ChiNoCParams(), RnParams())).module) { dut =>
          initialize(dut.io.local)
          initialize(dut.io.network)
          localRequest(dut.io.local, 13, 64)
          dut.clock.step()
          dut.io.local.request.valid.poke(false)
          dut.clock.step()
          data(dut.io.network.datRx.bits, 0, 1, 13, lines(1))
          dut.io.network.datRx.bits.elements(wrongField).asInstanceOf[UInt].poke(BigInt(0))
          dut.io.network.datRx.valid.poke(true)
          dut.clock.step()
        }
      }
      error.getMessage should include(if (wrongField == "opcode") "CompData" else "another transaction")
    }
  }
}
