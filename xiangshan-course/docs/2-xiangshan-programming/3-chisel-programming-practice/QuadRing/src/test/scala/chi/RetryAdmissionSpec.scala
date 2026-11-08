package chi

import chisel3._
import noc.RingParams
import org.chipsalliance.cde.config.Parameters
import org.chipsalliance.diplomacy.lazymodule.LazyModule
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

/** T2 checks that a busy completion side consumes retryable requests and later grants them fairly. */
class RetryAdmissionSpec extends AnyFreeSpec with Matchers with ChiNodeTestSupport {
  private implicit val p: Parameters = Parameters.empty

  for (coherent <- Seq(false, true); retrySlots <- Seq(1, 2)) {
    s"HN preserves its reservation when a new request arrives before the retry, coherent=$coherent, retrySlots=$retrySlots" in {
      val config = ChiNoCParams(ChiParams(3, 4, 32))
      val hn = HnParams(coherent = coherent, directoryLines = 4, retrySlots = retrySlots)
      simulate(LazyModule(new ChiNodeFixtures.Hn(config, hn)).module) { dut =>
        val port = dut.io
        initialize(port)
        val read = if (coherent) ChiOpcode.Req.ReadShared else ChiOpcode.Req.ReadNoSnp
        val granted = if (coherent) ChiOpcode.Req.ReadUnique else ChiOpcode.Req.WriteNoSnpFull
        val newcomer = if (coherent) ChiOpcode.Req.Evict else ChiOpcode.Req.ReadNoSnp
        val grantedAddress = if (coherent) BigInt(0) else BigInt(64)
        val newAddress = if (coherent) BigInt(0) else BigInt(128)

        def prepare(src: Int, txn: Int, opcode: UInt, address: BigInt, credited: Boolean = false): Unit = {
          request(port.reqRx.bits, 1, src, txn, address)
          port.reqRx.bits.opcode.poke(opcode)
          port.reqRx.bits.allowRetry.poke((!credited).B)
          port.reqRx.valid.poke(true)
        }
        def accept(): Unit = {
          port.reqRx.ready.expect(true.B)
          dut.clock.step()
          port.reqRx.valid.poke(false)
        }
        def receiveResponse(src: Int, txn: Int, opcode: UInt, dbId: Int = 0): Unit = {
          response(port.rspRx.bits, 1, src, txn, opcode, dbId)
          port.rspRx.valid.poke(true)
          port.rspRx.ready.expect(true.B)
          dut.clock.step()
          port.rspRx.valid.poke(false)
        }
        def receiveData(value: BigInt): Unit = {
          data(port.datRx.bits, 1, 3, 0, value)
          port.datRx.valid.poke(true)
          port.datRx.ready.expect(true.B)
          dut.clock.step()
          port.datRx.valid.poke(false)
        }

        prepare(0, 1, read, 0)
        accept()
        dut.clock.step() // Active SN read.
        prepare(2, 2, granted, grantedAddress)
        accept()
        expectResponse(port.rspTx.bits, 2, 1, 2, ChiOpcode.Rsp.RetryAck)
        dut.clock.step()
        receiveData(lines.head)
        dut.clock.step() // RN0 completion.
        if (coherent) receiveResponse(0, 0, ChiOpcode.Rsp.CompAck)
        dut.clock.step() // Reserve the slot and present Grant.
        port.rspTx.valid.expect(true.B)
        expectResponse(port.rspTx.bits, 2, 1, 0, ChiOpcode.Rsp.PCrdGrant)
        dut.clock.step()

        // RN0's new REQ wins the race to HN, but cannot consume RN2's reservation.
        port.rspTx.ready.poke(false)
        prepare(0, 3, newcomer, newAddress)
        accept()
        prepare(2, 2, granted, grantedAddress, credited = true)
        for (_ <- 0 until 4) {
          port.rspTx.valid.expect(true.B)
          expectResponse(port.rspTx.bits, 0, 1, 3, ChiOpcode.Rsp.RetryAck)
          port.reqRx.ready.expect(false.B)
          port.reqTx.valid.expect(false.B)
          port.snpTx.valid.expect(false.B)
          dut.clock.step()
        }
        port.rspTx.ready.poke(true)
        dut.clock.step() // Deliver RetryAck before allowing the credited request to proceed.
        accept()
        if (coherent) {
          // The rejected Evict must not remove RN0 from the directory.
          expectSnoopRequest(port, 0, 1, 0)
          dut.clock.step()
          receiveResponse(0, 0, ChiOpcode.Rsp.SnpResp)
          port.reqTx.valid.expect(true.B)
          expectRequest(port.reqTx.bits, 3, 1, 0, 0)
          dut.clock.step()
          receiveData(lines.head)
          port.datTx.bits.resp.expect(ChiResp.UC)
          port.datTx.bits.tgtID.expect(2.U)
          port.datTx.bits.txnID.expect(2.U)
          dut.clock.step()
          receiveResponse(2, 0, ChiOpcode.Rsp.CompAck)
        } else {
          port.rspTx.valid.expect(true.B)
          expectResponse(port.rspTx.bits, 2, 1, 2, ChiOpcode.Rsp.CompDBIDResp)
          dut.clock.step()
          writeData(port.datRx.bits, 1, 2, 0, lines(2))
          port.datRx.valid.poke(true)
          dut.clock.step()
          port.datRx.valid.poke(false)
          expectRequest(port.reqTx.bits, 3, 1, 0, 64, ChiOpcode.Req.WriteNoSnpFull)
          dut.clock.step()
          receiveResponse(3, 0, ChiOpcode.Rsp.DBIDResp, dbId = 9)
          expectData(port.datTx.bits, 3, 1, 9, lines(2), ChiOpcode.Dat.NonCopyBackWrData)
          dut.clock.step()
          receiveResponse(3, 0, ChiOpcode.Rsp.Comp, dbId = 9)
        }
        dut.clock.step()
        port.rspTx.valid.expect(true.B)
        expectResponse(port.rspTx.bits, 0, 1, 0, ChiOpcode.Rsp.PCrdGrant)
        dut.clock.step()
        prepare(0, 3, newcomer, newAddress, credited = true)
        accept()
        if (coherent) {
          port.rspTx.valid.expect(true.B)
          expectResponse(port.rspTx.bits, 0, 1, 3, ChiOpcode.Rsp.Comp)
          dut.clock.step()
        } else {
          expectRequest(port.reqTx.bits, 3, 1, 0, 128)
          dut.clock.step()
          receiveData(lines(1))
          expectData(port.datTx.bits, 0, 1, 3, lines(1))
          dut.clock.step()
        }
        for (_ <- 0 until 16) {
          port.rspTx.valid.expect(false.B)
          port.reqTx.valid.expect(false.B)
          port.datTx.valid.expect(false.B)
          dut.clock.step()
        }
      }
    }
  }

  "SN remembers a busy read, grants it after the active read, and accepts only the credited retry" in {
    val config = ChiNoCParams(ChiParams(3, 4, 32), RingParams(4, 3))
    val sn = SnParams(3, lines)
    simulate(LazyModule(new ChiNodeFixtures.Sn(config, sn)).module) { dut =>
      val port = dut.io
      initialize(port)
      port.datTx.ready.poke(false)
      request(port.reqRx.bits, 3, 1, 5, 0)
      port.reqRx.valid.poke(true)
      port.reqRx.ready.expect(true.B)
      dut.clock.step()
      port.reqRx.valid.poke(false)
      dut.clock.step() // readLine -> sendData

      port.rspTx.ready.poke(false)
      request(port.reqRx.bits, 3, 2, 6, 64)
      port.reqRx.valid.poke(true)
      port.reqRx.ready.expect(true.B)
      dut.clock.step()
      port.reqRx.valid.poke(false)
      port.rspTx.valid.expect(true.B)
      expectResponse(port.rspTx.bits, 2, 3, 6, ChiOpcode.Rsp.RetryAck)
      dut.clock.step(3)
      port.rspTx.ready.poke(true)
      dut.clock.step()

      port.datTx.ready.poke(true)
      dut.clock.step()
      port.rspTx.ready.poke(false)
      port.rspTx.valid.expect(true.B)
      expectResponse(port.rspTx.bits, 2, 3, 0, ChiOpcode.Rsp.PCrdGrant)
      dut.clock.step(2)
      port.rspTx.ready.poke(true)
      dut.clock.step()

      request(port.reqRx.bits, 3, 2, 6, 64)
      port.reqRx.bits.allowRetry.poke(false)
      port.reqRx.valid.poke(true)
      port.reqRx.ready.expect(true.B)
      dut.clock.step()
      port.reqRx.valid.poke(false)
      dut.clock.step()
      port.datTx.valid.expect(true.B)
      expectData(port.datTx.bits, 2, 3, 6, lines(1))
      dut.clock.step()
    }
  }

  "HN grants two queued requesters in round-robin order and keeps their identities" in {
    val config = ChiNoCParams(ChiParams(3, 4, 32), RingParams(4, 3))
    val hn = HnParams(nodeId = 1, snId = 3, retrySlots = 2)
    simulate(LazyModule(new ChiNodeFixtures.Hn(config, hn)).module) { dut =>
      val port = dut.io
      initialize(port)
      port.reqTx.ready.poke(false)
      request(port.reqRx.bits, 1, 0, 1, 0)
      port.reqRx.valid.poke(true)
      dut.clock.step()
      port.reqRx.valid.poke(false)

      port.rspTx.ready.poke(false)
      request(port.reqRx.bits, 1, 2, 2, 64)
      port.reqRx.valid.poke(true)
      port.reqRx.ready.expect(true.B)
      dut.clock.step()
      port.reqRx.valid.poke(false)
      port.rspTx.valid.expect(true.B)
      expectResponse(port.rspTx.bits, 2, 1, 2, ChiOpcode.Rsp.RetryAck)
      port.rspTx.ready.poke(true)
      dut.clock.step()

      request(port.reqRx.bits, 1, 4, 3, 128)
      port.reqRx.valid.poke(true)
      port.reqRx.ready.expect(true.B)
      port.rspTx.ready.poke(false)
      dut.clock.step()
      port.reqRx.valid.poke(false)
      port.rspTx.valid.expect(true.B)
      expectResponse(port.rspTx.bits, 4, 1, 3, ChiOpcode.Rsp.RetryAck)
      port.rspTx.ready.poke(true)
      dut.clock.step()

      port.reqTx.ready.poke(true)
      dut.clock.step() // active SN REQ is accepted
      data(port.datRx.bits, 1, 3, 0, lines.head)
      port.datRx.valid.poke(true)
      dut.clock.step()
      port.datRx.valid.poke(false)
      port.datTx.ready.poke(true)
      dut.clock.step() // active response is delivered; slot 0 is granted first
      dut.clock.step()
      port.rspTx.ready.poke(false)
      port.rspTx.valid.expect(true.B)
      expectResponse(port.rspTx.bits, 2, 1, 0, ChiOpcode.Rsp.PCrdGrant)
      port.rspTx.ready.poke(true)
      dut.clock.step()

      request(port.reqRx.bits, 1, 2, 2, 64)
      port.reqRx.bits.allowRetry.poke(false)
      port.reqRx.valid.poke(true)
      dut.clock.step()
      port.reqRx.valid.poke(false)
      port.reqTx.ready.poke(false)
      dut.clock.step()
      expectRequest(port.reqTx.bits, 3, 1, 0, 64)
      port.reqTx.valid.expect(true.B)

      port.reqTx.ready.poke(true)
      dut.clock.step()
      data(port.datRx.bits, 1, 3, 0, lines(1))
      port.datRx.valid.poke(true)
      dut.clock.step()
      port.datRx.valid.poke(false)
      port.datTx.ready.poke(true)
      dut.clock.step()
      dut.clock.step()
      port.rspTx.ready.poke(false)
      port.rspTx.valid.expect(true.B)
      expectResponse(port.rspTx.bits, 4, 1, 0, ChiOpcode.Rsp.PCrdGrant)
    }
  }
}
