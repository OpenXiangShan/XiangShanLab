package chi

import chisel3._
import chisel3.simulator.Exceptions.AssertionFailed
import circt.stage.ChiselStage
import noc.RingParams
import org.chipsalliance.cde.config.Parameters
import org.chipsalliance.diplomacy.lazymodule.LazyModule
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

class SnNodeSpec extends AnyFreeSpec with Matchers with ChiNodeTestSupport {
  private implicit val p: Parameters = Parameters.empty

  "invalid memory initialization is rejected before graph construction" in {
    an[IllegalArgumentException] should be thrownBy SnParams(-1)
    an[IllegalArgumentException] should be thrownBy SnParams(initialLines = Seq.empty)
    an[IllegalArgumentException] should be thrownBy SnParams(initialLines = Seq(BigInt(-1)))
    an[IllegalArgumentException] should be thrownBy SnParams(initialLines = Seq(BigInt(1) << 512))
  }

  "the memory must fit the negotiated address space" in {
    val error = intercept[IllegalArgumentException] {
      ChiselStage.emitCHIRRTL(LazyModule(new ChiNodeFixtures.Sn(
        ChiNoCParams(ChiParams(1, 1, 6), RingParams(2)), SnParams(1, lines))).module)
    }
    error.getMessage should include("address width")
  }

  private val configs = Seq(
    (ChiNoCParams(), SnParams(3, lines), 1),
    (ChiNoCParams(ChiParams(3, 7, 32), RingParams(4, 5)), SnParams(6, lines), 4),
    (ChiNoCParams(ChiParams(1, 1, 6), RingParams(2)), SnParams(1, Seq(lines.head)), 0)
  )
  for ((config, sn, hn) <- configs) {
    s"SN ${sn.nodeId} reads every line and holds blocked data with parameters ${config.chi}" in {
      simulate(LazyModule(new ChiNodeFixtures.Sn(config, sn)).module) { dut =>
        initialize(dut.io)
        for ((value, index) <- sn.initialLines.zipWithIndex) {
          val txn = (index + 1) & ((1 << config.chi.txnIdWidth) - 1)
          dut.io.datTx.ready.poke(false)
          request(dut.io.reqRx.bits, sn.nodeId, hn, txn, BigInt(index) << 6)
          dut.io.reqRx.valid.poke(true)
          dut.io.reqRx.ready.expect(true.B)
          dut.clock.step()
          dut.io.reqRx.valid.poke(false)
          request(dut.io.reqRx.bits, sn.nodeId, 0, 0, 0)
          dut.io.datTx.valid.expect(false.B)
          dut.clock.step()
          for (_ <- 0 until 6) {
            dut.io.reqRx.ready.expect(true.B)
            dut.io.datTx.valid.expect(true.B)
            expectData(dut.io.datTx.bits, hn, sn.nodeId, txn, value)
            dut.io.reqTx.valid.expect(false.B)
            dut.io.rspTx.valid.expect(false.B)
            dut.io.snpTx.valid.expect(false.B)
            dut.clock.step()
          }
          dut.io.datTx.ready.poke(true)
          dut.clock.step()
          for (_ <- 0 until 3) {
            dut.io.reqRx.ready.expect(true.B)
            dut.io.datTx.valid.expect(false.B)
            dut.clock.step()
          }
        }
      }
    }
  }

  for ((address, message) <- Seq(BigInt(1) -> "line-aligned", BigInt(192) -> "outside memory",
    (BigInt(1) << 47) -> "outside memory"); write <- Seq(false, true)) {
    s"SN rejects invalid address $address for write=$write without aliasing another line" in {
      val error = intercept[AssertionFailed] {
        simulate(LazyModule(new ChiNodeFixtures.Sn(ChiNoCParams(), SnParams(3, lines))).module) { dut =>
          initialize(dut.io)
          request(dut.io.reqRx.bits, 3, 1, 13, address)
          if (write) dut.io.reqRx.bits.opcode.poke(ChiOpcode.Req.WriteNoSnpFull)
          dut.io.reqRx.valid.poke(true)
          dut.clock.step()
        }
      }
      error.getMessage should include(message)
    }
  }

  "an unsupported REQ is rejected rather than executed as a memory operation" in {
    val error = intercept[AssertionFailed] {
      simulate(LazyModule(new ChiNodeFixtures.Sn(ChiNoCParams(), SnParams(3, lines))).module) { dut =>
        initialize(dut.io)
        request(dut.io.reqRx.bits, 3, 1, 13, 0)
        dut.io.reqRx.bits.opcode.poke(ChiOpcode.Req.ReadShared)
        dut.io.reqRx.valid.poke(true)
        dut.clock.step()
      }
    }
    error.getMessage should include("only ReadNoSnp")
  }

  private def readBack(dut: ChiNodeFixtures.SnModule, sn: SnParams, hn: Int,
    txn: Int, index: Int, value: BigInt): Unit = {
    request(dut.io.reqRx.bits, sn.nodeId, hn, txn, BigInt(index) << 6)
    dut.io.reqRx.valid.poke(true)
    dut.io.reqRx.ready.expect(true.B)
    dut.clock.step()
    dut.io.reqRx.valid.poke(false)
    dut.io.datTx.valid.expect(false.B)
    dut.clock.step()
    dut.io.datTx.valid.expect(true.B)
    expectData(dut.io.datTx.bits, hn, sn.nodeId, txn, value)
    dut.io.rspTx.valid.expect(false.B)
    dut.clock.step()
    dut.io.reqRx.ready.expect(true.B)
    dut.io.datTx.valid.expect(false.B)
  }

  for ((config, sn, hn) <- configs) {
    s"SN ${sn.nodeId} authorizes, writes, and completes once under backpressure with ${config.chi}" in {
      simulate(LazyModule(new ChiNodeFixtures.Sn(config, sn)).module) { dut =>
        val port = dut.io
        initialize(port)
        val expected = sn.initialLines.toArray
        val txn = (1 << config.chi.txnIdWidth) - 1 // Deliberately different from DBID=0.
        for (round <- 0 until 2; index <- expected.indices) {
          val value = if (round == 0) {
            (BigInt(1) << 511) | (BigInt(index + 7) << 256) | BigInt(0xfedcba98L)
          } else if (index == 0) BigInt(0) else (BigInt(1) << 512) - 1
          port.rspTx.ready.poke(false)
          writeRequest(port.reqRx.bits, sn.nodeId, hn, txn, BigInt(index) << 6)
          port.reqRx.valid.poke(true)
          port.reqRx.ready.expect(true.B)
          dut.clock.step()
          // Hold another request and early write data while authorization is blocked.
          request(port.reqRx.bits, sn.nodeId, 0, 0, 0)
          writeData(port.datRx.bits, sn.nodeId, hn, 0, value)
          port.datRx.valid.poke(true)
          port.reqRx.valid.poke(false)
          for (_ <- 0 until 6) {
            port.reqRx.ready.expect(true.B)
            port.datRx.ready.expect(false.B)
            port.datTx.valid.expect(false.B)
            port.rspTx.valid.expect(true.B)
            expectResponse(port.rspTx.bits, hn, sn.nodeId, txn, ChiOpcode.Rsp.DBIDResp)
            dut.clock.step()
          }
          port.rspTx.ready.poke(true)
          dut.clock.step()
          port.rspTx.ready.poke(false)
          port.datRx.valid.poke(false)
          // Authorization alone cannot produce a completion.
          for (_ <- 0 until 3) {
            port.reqRx.ready.expect(true.B)
            port.datRx.ready.expect(true.B)
            port.rspTx.valid.expect(false.B)
            dut.clock.step()
          }
          port.datRx.valid.poke(true)
          dut.clock.step()
          port.datRx.valid.poke(false)
          port.datRx.bits.data.poke(BigInt(0))
          port.datRx.ready.expect(false.B)
          port.rspTx.valid.expect(false.B) // Memory has not been updated yet.
          dut.clock.step()
          for (_ <- 0 until 6) {
            port.reqRx.ready.expect(true.B)
            port.datRx.ready.expect(false.B)
            port.rspTx.valid.expect(true.B)
            expectResponse(port.rspTx.bits, hn, sn.nodeId, txn, ChiOpcode.Rsp.Comp)
            port.datTx.valid.expect(false.B)
            dut.clock.step()
          }
          port.reqRx.valid.poke(false)
          port.rspTx.ready.poke(true)
          dut.clock.step()
          port.reqRx.ready.expect(true.B)
          for (_ <- 0 until 3) {
            port.rspTx.valid.expect(false.B)
            port.datTx.valid.expect(false.B)
            dut.clock.step()
          }
          expected(index) = value
          // Check the written line and every untouched neighbor, including non-power-of-two memory.
          for (row <- expected.indices) readBack(dut, sn, hn, txn, row, expected(row))
        }
      }
    }
  }

  for ((field, wrongValue, message) <- Seq(
    ("tgtID", 2, "another transaction"), ("srcID", 0, "another transaction"),
    ("txnID", 13, "another transaction"), ("opcode", 4, "NonCopyBackWrData"),
    ("resp", 2, "NonCopyBackWrData"), ("dbID", 1, "NonCopyBackWrData"))) {
    s"SN rejects write data with invalid $field" in {
      val error = intercept[AssertionFailed] {
        simulate(LazyModule(new ChiNodeFixtures.Sn(ChiNoCParams(), SnParams(3, lines))).module) { dut =>
          initialize(dut.io)
          writeRequest(dut.io.reqRx.bits, 3, 1, 13, 64)
          dut.io.reqRx.valid.poke(true)
          dut.clock.step()
          dut.io.reqRx.valid.poke(false)
          dut.clock.step() // Accept DBIDResp.
          writeData(dut.io.datRx.bits, 3, 1, 0, lines.head)
          dut.io.datRx.bits.elements(field).asInstanceOf[UInt].poke(BigInt(wrongValue))
          dut.io.datRx.valid.poke(true)
          dut.clock.step()
        }
      }
      error.getMessage should include(message)
    }
  }

  for (phase <- Seq("blocked DBIDResp", "waiting for DAT", "before memory write", "blocked Comp")) {
    s"reset clears an SN write during $phase and restores initialized memory" in {
      val sn = SnParams(3, lines)
      simulate(LazyModule(new ChiNodeFixtures.Sn(ChiNoCParams(), sn)).module) { dut =>
        initialize(dut.io)
        dut.io.rspTx.ready.poke(false)
        writeRequest(dut.io.reqRx.bits, 3, 1, 13, 64)
        dut.io.reqRx.valid.poke(true)
        dut.clock.step()
        dut.io.reqRx.valid.poke(false)
        if (phase != "blocked DBIDResp") {
          dut.io.rspTx.ready.poke(true)
          dut.clock.step()
          dut.io.rspTx.ready.poke(false)
        }
        if (phase == "before memory write" || phase == "blocked Comp") {
          writeData(dut.io.datRx.bits, 3, 1, 0, BigInt(0))
          dut.io.datRx.valid.poke(true)
          dut.clock.step()
          dut.io.datRx.valid.poke(false)
          if (phase == "blocked Comp") dut.clock.step()
        }
        dut.reset.poke(true.B)
        dut.clock.step(2)
        dut.reset.poke(false.B)
        dut.io.rspTx.ready.poke(true)
        for (_ <- 0 until 3) {
          dut.io.reqRx.ready.expect(true.B)
          dut.io.datRx.ready.expect(false.B)
          dut.io.rspTx.valid.expect(false.B)
          dut.io.datTx.valid.expect(false.B)
          dut.clock.step()
        }
        readBack(dut, sn, 1, 2, 1, lines(1))
      }
    }
  }

  for (readCycles <- Seq(0, 1)) {
    s"reset cancels the SN read after $readCycles local read cycles" in {
      simulate(LazyModule(new ChiNodeFixtures.Sn(ChiNoCParams(), SnParams(3, lines))).module) { dut =>
        initialize(dut.io)
        dut.io.datTx.ready.poke(false)
        request(dut.io.reqRx.bits, 3, 1, 13, 64)
        dut.io.reqRx.valid.poke(true)
        dut.clock.step()
        dut.io.reqRx.valid.poke(false)
        if (readCycles > 0) dut.clock.step(readCycles)
        dut.reset.poke(true.B)
        dut.clock.step(2)
        dut.reset.poke(false.B)
        dut.io.reqRx.ready.expect(true.B)
        dut.io.datTx.valid.expect(false.B)
        dut.clock.step(3)
        dut.io.datTx.valid.expect(false.B)
        request(dut.io.reqRx.bits, 3, 1, 2, 128)
        dut.io.reqRx.valid.poke(true)
        dut.clock.step()
        dut.io.reqRx.valid.poke(false)
        dut.clock.step()
        dut.io.datTx.valid.expect(true.B)
        expectData(dut.io.datTx.bits, 1, 3, 2, lines(2))
        dut.io.datTx.ready.poke(true)
        dut.clock.step()
        dut.io.reqRx.ready.expect(true.B)
      }
    }
  }
}
