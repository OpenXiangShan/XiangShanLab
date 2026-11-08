package chi

import chisel3._
import noc.RingParams
import org.chipsalliance.cde.config.Parameters
import org.chipsalliance.diplomacy.lazymodule.LazyModule
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

class RetryObservationSpec extends AnyFreeSpec with Matchers with ChiNodeTestSupport {
  private implicit val p: Parameters = Parameters.empty

  "real system reset clears an in-flight grant, endpoint reservations and all ring credits" in {
    val config = ChiSystemParams(initialLines = lines, coherent = true, cacheLines = 2, directoryLines = 4)
    val top = LazyModule(new ProtocolCheckedSystem(config))
    simulate(top.module) { dut =>
      val protocol = top.protocol.getWrappedValue
      val ports = Seq(dut.io.rn0, dut.io.rn1)
      protocol.checkDrained.poke(false)
      ports.foreach(initialize)
      for ((port, rn) <- ports.zipWithIndex) {
        localRequest(port, 1, BigInt(rn * 64))
        port.request.ready.expect(true.B)
      }
      dut.clock.step()
      ports.foreach(_.request.valid.poke(false))
      var cycles = 0
      while (protocol.hn.grants.peek().litValue == 0 && cycles < 128) {
        dut.clock.step()
        cycles += 1
      }
      protocol.hn.grants.expect(1.U)
      protocol.hn.retries.expect(0.U) // Grant is in flight; HN is still reserving the slot.
      dut.reset.poke(true)
      dut.clock.step(2)
      dut.reset.poke(false)
      protocol.checkDrained.poke(true)
      dut.clock.step(2)
      protocol.hn.first.expect(0.U)
      protocol.sn.first.expect(0.U)
      ports.foreach(_.response.valid.expect(false.B))

      protocol.checkDrained.poke(false)
      localRequest(ports.head, 1, 0)
      ports.head.request.ready.expect(true.B)
      dut.clock.step()
      ports.head.request.valid.poke(false)
      cycles = 0
      while (!ports.head.response.valid.peekBoolean() && cycles < 128) {
        dut.clock.step()
        cycles += 1
      }
      ports.head.response.valid.expect(true.B)
      ports.head.response.bits.data.expect(lines.head.U)
      ports.head.response.ready.poke(true)
      dut.clock.step(32)
      protocol.hn.first.expect(1.U)
      protocol.hn.finished.expect(1.U)
      protocol.checkDrained.poke(true)
      dut.clock.step()
    }
  }

  for (config <- Seq(ChiNoCParams(), ChiNoCParams(ChiParams(3, 7, 32), RingParams(4, 5)))) {
    s"real SN executes a retried write once and clears its credit/reservation with ${config.chi}" in {
      val top = LazyModule(new RetryCheckedSn(config, SnParams(3, lines)))
      simulate(top.module) { dut =>
        val port = dut.io
        val protocol = top.protocol.getWrappedValue
        initialize(port)
        protocol.checkDrained.poke(false)

        def send(txn: Int, write: Boolean, credited: Boolean = false): Unit = {
          request(port.reqRx.bits, 3, 1, txn, if (write) BigInt(64) else BigInt(0))
          if (write) port.reqRx.bits.opcode.poke(ChiOpcode.Req.WriteNoSnpFull)
          port.reqRx.bits.allowRetry.poke(!credited)
          port.reqRx.valid.poke(true)
          port.reqRx.ready.expect(true.B)
          dut.clock.step()
          port.reqRx.valid.poke(false)
        }
        def waitFor(message: String)(condition: => Boolean): Unit = {
          var cycles = 0
          while (!condition && cycles < 64) { dut.clock.step(); cycles += 1 }
          withClue(message) { condition shouldBe true }
        }
        def receiveRsp(txn: Int, opcode: UInt): Unit = {
          waitFor("SN response missing: ")(port.rspTx.valid.peekBoolean())
          expectResponse(port.rspTx.bits, 1, 3, txn, opcode)
          dut.clock.step()
        }

        port.datTx.ready.poke(false)
        send(1, write = false)
        send(2, write = true) // Reject the write in the first read's execution cycle.
        receiveRsp(2, ChiOpcode.Rsp.RetryAck)
        port.datTx.valid.expect(true.B)
        expectData(port.datTx.bits, 1, 3, 1, lines.head)
        port.datTx.ready.poke(true)
        dut.clock.step()
        port.rspTx.ready.poke(false)
        for (_ <- 0 until 4) {
          port.rspTx.valid.expect(true.B)
          expectResponse(port.rspTx.bits, 1, 3, 0, ChiOpcode.Rsp.PCrdGrant)
          dut.clock.step()
        }
        port.rspTx.ready.poke(true)
        receiveRsp(0, ChiOpcode.Rsp.PCrdGrant)
        dut.clock.step(4) // Keep the granted reservation before reissuing.
        send(2, write = true, credited = true)
        receiveRsp(2, ChiOpcode.Rsp.DBIDResp)
        writeData(port.datRx.bits, 3, 1, 0, lines(2))
        port.datRx.valid.poke(true)
        port.datRx.ready.expect(true.B)
        dut.clock.step()
        port.datRx.valid.poke(false)
        receiveRsp(2, ChiOpcode.Rsp.Comp)
        dut.clock.step(2)
        protocol.counts.first.expect(2.U)
        protocol.counts.acks.expect(1.U)
        protocol.counts.grants.expect(1.U)
        protocol.counts.retries.expect(1.U)
        protocol.counts.started.expect(2.U)
        protocol.counts.finished.expect(2.U)

        // Read back the written line through the real memory path, reusing txnID 2.
        request(port.reqRx.bits, 3, 1, 2, 64)
        port.reqRx.valid.poke(true)
        dut.clock.step()
        port.reqRx.valid.poke(false)
        waitFor("SN readback missing: ")(port.datTx.valid.peekBoolean())
        expectData(port.datTx.bits, 1, 3, 2, lines(2))
        dut.clock.step(3)
        protocol.checkDrained.poke(true)
        dut.clock.step(2)

        // Reset while a second request holds a retry reservation; both ledgers must clear.
        protocol.checkDrained.poke(false)
        port.datTx.ready.poke(false)
        send(1, write = false)
        dut.clock.step()
        send(2, write = true)
        receiveRsp(2, ChiOpcode.Rsp.RetryAck)
        port.datTx.ready.poke(true)
        dut.clock.step()
        receiveRsp(0, ChiOpcode.Rsp.PCrdGrant)
        dut.reset.poke(true)
        dut.clock.step(2)
        dut.reset.poke(false)
        protocol.checkDrained.poke(true)
        dut.clock.step(2)
        protocol.counts.first.expect(0.U)
        protocol.counts.grants.expect(0.U)
      }
    }
  }
}
