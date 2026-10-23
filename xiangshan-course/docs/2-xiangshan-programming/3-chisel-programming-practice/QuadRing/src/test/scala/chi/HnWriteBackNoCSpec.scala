package chi

import chisel3._
import noc.RingParams
import org.chipsalliance.cde.config.Parameters
import org.chipsalliance.diplomacy.lazymodule.LazyModule
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

class HnWriteBackNoCSpec extends AnyFreeSpec with Matchers with ChiNodeTestSupport {
  private implicit val p: Parameters = Parameters.empty
  private val configs = Seq(
    ChiNoCParams() -> Seq(0, 1, 2, 3),
    ChiNoCParams(ChiParams(3, 7, 32), RingParams(4, 5)) -> Seq(6, 1, 4, 0)
  )

  private def waitFor(dut: ChiNodeFixtures.HnSnOnNoCModule)(condition: => Boolean): Unit = {
    var cycles = 0
    while (!condition && cycles < 128) {
      dut.clock.step()
      cycles += 1
    }
    withClue("CHI message did not arrive within 128 cycles: ") { condition shouldBe true }
  }

  private def sendRequest(dut: ChiNodeFixtures.HnSnOnNoCModule, ids: Seq[Int], rn: Int,
    txn: Int, opcode: UInt, credited: Boolean = false): Unit = {
    val port = dut.io(rn)
    request(port.reqTx.bits, ids(1), ids(rn * 2), txn, 64)
    port.reqTx.bits.opcode.poke(opcode)
    port.reqTx.bits.allowRetry.poke((!credited).B)
    port.reqTx.valid.poke(true)
    waitFor(dut)(port.reqTx.ready.peekBoolean())
    dut.clock.step()
    port.reqTx.valid.poke(false)
  }

  private def awaitReply(dut: ChiNodeFixtures.HnSnOnNoCModule, ids: Seq[Int], rn: Int,
    txn: Int, opcode: UInt, dataReply: Boolean): Unit = {
    val port = dut.io(rn)
    var ackSeen = false
    var creditHeld = false
    var resent = false
    var cycles = 0
    def arrived: Boolean = if (dataReply) port.datRx.valid.peekBoolean() else {
      port.rspRx.valid.peekBoolean() && port.rspRx.bits.opcode.peek().litValue == ChiOpcode.Rsp.CompDBIDResp.litValue
    }
    while (!arrived && cycles < 128) {
      if (port.rspRx.valid.peekBoolean()) {
        val retry = port.rspRx.bits.opcode.peek().litValue == ChiOpcode.Rsp.RetryAck.litValue
        val grant = port.rspRx.bits.opcode.peek().litValue == ChiOpcode.Rsp.PCrdGrant.litValue
        (retry || grant) shouldBe true
        expectResponse(port.rspRx.bits, ids(rn * 2), ids(1), if (retry) txn else 0,
          if (retry) ChiOpcode.Rsp.RetryAck else ChiOpcode.Rsp.PCrdGrant)
        if (retry) {
          ackSeen shouldBe false
          ackSeen = true
        } else {
          creditHeld shouldBe false
          creditHeld = true
        }
        port.rspRx.ready.poke(true)
        dut.clock.step()
        port.rspRx.ready.poke(false)
        if (ackSeen && creditHeld && !resent) {
          sendRequest(dut, ids, rn, txn, opcode, credited = true)
          resent = true
        }
      } else dut.clock.step()
      cycles += 1
    }
    withClue("HN did not finish the request: ") { arrived shouldBe true }
  }

  private def receiveLine(dut: ChiNodeFixtures.HnSnOnNoCModule, ids: Seq[Int], rn: Int,
    txn: Int, value: BigInt): Unit = {
    val port = dut.io(rn)
    awaitReply(dut, ids, rn, txn, ChiOpcode.Req.ReadShared, dataReply = true)
    for (_ <- 0 until 4) {
      port.datRx.valid.expect(true.B)
      port.datRx.bits.tgtID.expect(ids(rn * 2).U)
      port.datRx.bits.srcID.expect(ids(1).U)
      port.datRx.bits.txnID.expect(txn.U)
      port.datRx.bits.opcode.expect(ChiOpcode.Dat.CompData)
      port.datRx.bits.resp.expect(ChiResp.UC)
      port.datRx.bits.dbID.expect(0.U)
      port.datRx.bits.data.expect(value.U)
      dut.io(1 - rn).datRx.valid.expect(false.B)
      dut.clock.step()
    }
    port.datRx.ready.poke(true)
    dut.clock.step()
    port.datRx.ready.poke(false)
    response(port.rspTx.bits, ids(1), ids(rn * 2), 0, ChiOpcode.Rsp.CompAck)
    port.rspTx.valid.poke(true)
    waitFor(dut)(port.rspTx.ready.peekBoolean())
    dut.clock.step()
    port.rspTx.valid.poke(false)
  }

  for ((config, ids) <- configs) {
    s"a dirty line is written into the real SN and read by the other RN with IDs $ids" in {
      val top = LazyModule(new ChiNodeFixtures.HnSnOnNoC(config, ids, lines, coherent = true))
      simulate(top.module) { dut =>
        top.hn.params.coherent shouldBe true
        top.network.node.edges.in.map(_.endpoint.nodeId) shouldBe ids
        top.hn.node.edges.out.head.network shouldBe config
        top.sn.node.edges.out.head.network shouldBe config
        dut.io.foreach(initializeDriver)
        sendRequest(dut, ids, 0, 3, ChiOpcode.Req.ReadShared)
        receiveLine(dut, ids, 0, 3, lines(1))
        sendRequest(dut, ids, 0, 4, ChiOpcode.Req.WriteBackFull)
        awaitReply(dut, ids, 0, 4, ChiOpcode.Req.WriteBackFull, dataReply = false)
        val port = dut.io.head
        for (_ <- 0 until 4) {
          port.rspRx.valid.expect(true.B)
          expectResponse(port.rspRx.bits, ids.head, ids(1), 4, ChiOpcode.Rsp.CompDBIDResp)
          dut.clock.step()
        }
        port.rspRx.ready.poke(true)
        dut.clock.step()
        port.rspRx.ready.poke(false)
        val value = (BigInt(1) << 511) | (BigInt(0x89abcdefL) << 256) | BigInt(0xfedcba98L)
        data(port.datTx.bits, ids(1), ids.head, 0, value)
        port.datTx.bits.opcode.poke(ChiOpcode.Dat.CopyBackWrData)
        port.datTx.bits.resp.poke(ChiResp.UD_PD)
        port.datTx.valid.poke(true)
        waitFor(dut)(port.datTx.ready.peekBoolean())
        dut.clock.step()
        port.datTx.valid.poke(false)
        port.datTx.bits.data.poke(0.U)
        sendRequest(dut, ids, 1, 5, ChiOpcode.Req.ReadShared)
        receiveLine(dut, ids, 1, 5, value)
        for (_ <- 0 until 24) {
          for (port <- dut.io) {
            Seq(port.reqRx, port.rspRx, port.snpRx, port.datRx).foreach(_.valid.expect(false.B))
          }
          dut.clock.step()
        }
      }
    }
  }
}
