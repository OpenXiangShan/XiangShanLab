package chi

import chisel3._
import chisel3.simulator.Exceptions.AssertionFailed
import circt.stage.ChiselStage
import org.chipsalliance.cde.config.Parameters
import org.chipsalliance.diplomacy.lazymodule.LazyModule
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

class HnDirectorySpec extends AnyFreeSpec with Matchers with ChiNodeTestSupport {
  private implicit val p: Parameters = Parameters.empty

  private val config = ChiNoCParams()
  private val hn = HnParams(coherent = true, directoryLines = 4)

  private def issueRead(dut: ChiNodeFixtures.HnModule, src: Int, txn: Int,
    address: BigInt, opcode: UInt): Unit = {
    val port = dut.io
    request(port.reqRx.bits, hn.nodeId, src, txn, address)
    port.reqRx.bits.opcode.poke(opcode)
    port.reqRx.valid.poke(true)
    port.reqRx.ready.expect(true.B)
    dut.clock.step()
    port.reqRx.valid.poke(false)
  }

  private def finishRead(dut: ChiNodeFixtures.HnModule, src: Int, txn: Int,
    address: BigInt, value: BigInt, resp: UInt, acknowledge: Boolean = true): Unit = {
    val port = dut.io
    port.reqTx.valid.expect(true.B)
    expectRequest(port.reqTx.bits, hn.snId, hn.nodeId, 0, address, ChiOpcode.Req.ReadNoSnp)
    dut.clock.step()

    port.datRx.ready.expect(true.B)
    data(port.datRx.bits, hn.nodeId, hn.snId, 0, value)
    port.datRx.valid.poke(true)
    dut.clock.step()
    port.datRx.valid.poke(false)

    port.datTx.valid.expect(true.B)
    port.datTx.bits.tgtID.expect(src.U)
    port.datTx.bits.srcID.expect(hn.nodeId.U)
    port.datTx.bits.txnID.expect(txn.U)
    port.datTx.bits.opcode.expect(ChiOpcode.Dat.CompData)
    port.datTx.bits.dbID.expect(0.U)
    port.datTx.bits.data.expect(value.U)
    port.datTx.bits.resp.expect(resp)
    dut.clock.step()
    port.datTx.valid.expect(false.B)

    if (acknowledge) ackRead(dut, src)
  }

  private def ackRead(dut: ChiNodeFixtures.HnModule, src: Int): Unit = {
    response(dut.io.rspRx.bits, hn.nodeId, src, 0, ChiOpcode.Rsp.CompAck)
    dut.io.rspRx.valid.poke(true)
    dut.io.rspRx.ready.expect(true.B)
    dut.clock.step()
    dut.io.rspRx.valid.poke(false)
  }

  for (addrWidth <- Seq(48, 8)) {
    s"a complete directory supports first and last lines with $addrWidth-bit addresses" in {
      val network = config.copy(chi = config.chi.copy(addrWidth = addrWidth))
      simulate(LazyModule(new ChiNodeFixtures.Hn(network, hn)).module) { dut =>
        initialize(dut.io)
        issueRead(dut, 0, 3, 0, ChiOpcode.Req.ReadShared)
        finishRead(dut, 0, 3, 0, lines.head, ChiResp.UC)
        issueRead(dut, 2, 4, BigInt(3 * 64), ChiOpcode.Req.ReadShared)
        finishRead(dut, 2, 4, BigInt(3 * 64), lines(1), ChiResp.UC)
      }
    }
  }

  "ReadUnique allocates a UC directory entry when no RN owns the line" in {
    simulate(LazyModule(new ChiNodeFixtures.Hn(config, hn)).module) { dut =>
      initialize(dut.io)
      issueRead(dut, 2, 9, BigInt(64), ChiOpcode.Req.ReadUnique)
      finishRead(dut, 2, 9, BigInt(64), lines(1), ChiResp.UC)
    }
  }

  "non-coherent reads beyond the directory do not alias a cached lower address" in {
    simulate(LazyModule(new ChiNodeFixtures.Hn(config, hn)).module) { dut =>
      initialize(dut.io)
      issueRead(dut, 0, 3, 0, ChiOpcode.Req.ReadShared)
      finishRead(dut, 0, 3, 0, lines.head, ChiResp.UC)
      for (address <- Seq(BigInt(hn.directoryLines * 64), BigInt(1) << 30)) {
        issueRead(dut, 2, 4, address, ChiOpcode.Req.ReadNoSnp)
        finishRead(dut, 2, 4, address, lines(1), ChiResp.I, acknowledge = false)
        dut.io.reqRx.ready.expect(true.B)
        dut.io.rspRx.ready.expect(false.B)
      }
    }
  }

  "non-coherent writes beyond the directory forward data and wait for SN completion" in {
    simulate(LazyModule(new ChiNodeFixtures.Hn(config, hn)).module) { dut =>
      val port = dut.io
      initialize(port)
      issueRead(dut, 0, 3, 0, ChiOpcode.Req.ReadShared)
      finishRead(dut, 0, 3, 0, lines.head, ChiResp.UC)
      for (address <- Seq(BigInt(hn.directoryLines * 64), BigInt(1) << 30)) {
        issueRead(dut, 2, 5, address, ChiOpcode.Req.WriteNoSnpFull)
        port.rspTx.valid.expect(true.B)
        expectResponse(port.rspTx.bits, 2, hn.nodeId, 5, ChiOpcode.Rsp.CompDBIDResp)
        dut.clock.step()
        writeData(port.datRx.bits, hn.nodeId, 2, 0, lines(2))
        port.datRx.valid.poke(true)
        port.datRx.ready.expect(true.B)
        dut.clock.step()
        port.datRx.valid.poke(false)
        port.reqTx.valid.expect(true.B)
        expectRequest(port.reqTx.bits, hn.snId, hn.nodeId, 0, address, ChiOpcode.Req.WriteNoSnpFull)
        dut.clock.step()
        response(port.rspRx.bits, hn.nodeId, hn.snId, 0, ChiOpcode.Rsp.DBIDResp, dbId = 9)
        port.rspRx.valid.poke(true)
        port.rspRx.ready.expect(true.B)
        dut.clock.step()
        port.rspRx.valid.poke(false)
        port.datTx.valid.expect(true.B)
        expectData(port.datTx.bits, hn.snId, hn.nodeId, 9, lines(2), ChiOpcode.Dat.NonCopyBackWrData)
        dut.clock.step()
        for (_ <- 0 until 3) {
          port.rspRx.ready.expect(true.B)
          port.reqTx.valid.expect(false.B)
          port.datTx.valid.expect(false.B)
          dut.clock.step()
        }
        response(port.rspRx.bits, hn.nodeId, hn.snId, 0, ChiOpcode.Rsp.Comp, dbId = 9)
        port.rspRx.valid.poke(true)
        dut.clock.step()
        port.rspRx.valid.poke(false)
        port.rspRx.ready.expect(false.B)
        port.reqRx.ready.expect(true.B)
      }
    }
  }

  "HN waits for CompAck before granting a waiting request and allocates only on acceptance" in {
    simulate(LazyModule(new ChiNodeFixtures.Hn(config, hn)).module) { dut =>
      val port = dut.io
      initialize(port)
      issueRead(dut, 0, 7, 0, ChiOpcode.Req.ReadShared)
      finishRead(dut, 0, 7, 0, lines.head, ChiResp.UC, acknowledge = false)

      issueRead(dut, 2, 8, 64, ChiOpcode.Req.ReadUnique)
      port.rspTx.valid.expect(true.B)
      expectResponse(port.rspTx.bits, 2, hn.nodeId, 8, ChiOpcode.Rsp.RetryAck)
      dut.clock.step()
      for (_ <- 0 until 5) {
        port.reqTx.valid.expect(false.B)
        port.datTx.valid.expect(false.B)
        port.rspTx.valid.expect(false.B)
        port.rspRx.ready.expect(true.B)
        dut.clock.step()
      }
      port.rspTx.ready.poke(false)
      ackRead(dut, 0)
      dut.clock.step()
      for (_ <- 0 until 3) {
        port.rspTx.valid.expect(true.B)
        expectResponse(port.rspTx.bits, 2, hn.nodeId, 0, ChiOpcode.Rsp.PCrdGrant)
        port.reqRx.ready.expect(false.B)
        dut.clock.step()
      }
      port.rspTx.ready.poke(true)
      dut.clock.step()
      request(port.reqRx.bits, hn.nodeId, 2, 8, 64)
      port.reqRx.bits.opcode.poke(ChiOpcode.Req.ReadUnique)
      port.reqRx.bits.allowRetry.poke(false)
      port.reqRx.valid.poke(true)
      port.reqRx.ready.expect(true.B)
      dut.clock.step()
      port.reqRx.valid.poke(false)
      finishRead(dut, 2, 8, 64, lines(1), ChiResp.UC)
      port.reqRx.ready.expect(true.B)
      port.rspTx.valid.expect(false.B)
    }
  }

  "ReadUnique snoops the existing holder before accessing SN" in {
    simulate(LazyModule(new ChiNodeFixtures.Hn(config, hn)).module) { dut =>
      initialize(dut.io)
      issueRead(dut, 0, 3, 0, ChiOpcode.Req.ReadShared)
      finishRead(dut, 0, 3, 0, lines.head, ChiResp.UC)
      dut.io.snpTx.ready.poke(false)
      issueRead(dut, 2, 4, 0, ChiOpcode.Req.ReadUnique)
      for (_ <- 0 until 4) {
        expectSnoopRequest(dut.io, 0, hn.nodeId, 0)
        dut.clock.step()
      }
    }
  }

  "an address outside the complete directory cannot overwrite a holder" in {
    val error = intercept[AssertionFailed] {
      simulate(LazyModule(new ChiNodeFixtures.Hn(config, hn)).module) { dut =>
        initialize(dut.io)
        issueRead(dut, 0, 3, 0, ChiOpcode.Req.ReadShared)
        finishRead(dut, 0, 3, 0, lines.head, ChiResp.UC)
        issueRead(dut, 2, 4, BigInt(hn.directoryLines * 64), ChiOpcode.Req.ReadShared)
      }
    }
    error.getMessage should include("address exceeds directory range")
  }

  "HN rejects CompAck using the original request ID instead of its DBID" in {
    val error = intercept[AssertionFailed] {
      simulate(LazyModule(new ChiNodeFixtures.Hn(config, hn)).module) { dut =>
        initialize(dut.io)
        issueRead(dut, 0, 7, 0, ChiOpcode.Req.ReadShared)
        finishRead(dut, 0, 7, 0, lines.head, ChiResp.UC, acknowledge = false)
        response(dut.io.rspRx.bits, hn.nodeId, 0, 7, ChiOpcode.Rsp.CompAck)
        dut.io.rspRx.valid.poke(true)
        dut.clock.step()
      }
    }
    error.getMessage should include("expects matching RN CompAck")
  }

  "non-coherent access cannot bypass an exclusive cached copy" in {
    val error = intercept[AssertionFailed] {
      simulate(LazyModule(new ChiNodeFixtures.Hn(config, hn)).module) { dut =>
        initialize(dut.io)
        issueRead(dut, 0, 3, 0, ChiOpcode.Req.ReadShared)
        finishRead(dut, 0, 3, 0, lines.head, ChiResp.UC)
        issueRead(dut, 2, 4, 0, ChiOpcode.Req.ReadNoSnp)
      }
    }
    error.getMessage should include("non-coherent access requires an uncached line")
  }

  "directory parameters require a power-of-two entry count" in {
    an[IllegalArgumentException] should be thrownBy HnParams(coherent = true, directoryLines = 3)
  }

  "directory index must fit the negotiated address width" in {
    val network = config.copy(chi = config.chi.copy(addrWidth = 7))
    val error = intercept[IllegalArgumentException] {
      ChiselStage.emitCHIRRTL(LazyModule(new ChiNodeFixtures.Hn(network, hn)).module)
    }
    error.getMessage should include("directory must fit the negotiated address width")
  }

  "reset while waiting for CompAck cancels the transaction and clears holders" in {
    simulate(LazyModule(new ChiNodeFixtures.Hn(config, hn)).module) { dut =>
      initialize(dut.io)
      issueRead(dut, 0, 7, 0, ChiOpcode.Req.ReadShared)
      finishRead(dut, 0, 7, 0, lines.head, ChiResp.UC, acknowledge = false)
      dut.reset.poke(true.B)
      dut.clock.step(2)
      dut.reset.poke(false.B)
      dut.io.rspRx.ready.expect(false.B)
      dut.io.reqRx.ready.expect(true.B)
      issueRead(dut, 2, 8, 0, ChiOpcode.Req.ReadUnique)
      finishRead(dut, 2, 8, 0, lines(1), ChiResp.UC)
    }
  }
}
