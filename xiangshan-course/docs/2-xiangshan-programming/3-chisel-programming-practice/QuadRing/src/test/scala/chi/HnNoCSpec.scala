package chi

import chisel3._
import chisel3.reflect.DataMirror
import circt.stage.ChiselStage
import noc.RingParams
import org.chipsalliance.cde.config.Parameters
import org.chipsalliance.diplomacy.lazymodule.LazyModule
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

class HnNoCSpec extends AnyFreeSpec with Matchers with ChiNodeTestSupport {
  private implicit val p: Parameters = Parameters.empty
  private val configs = Seq(
    ChiNoCParams() -> Seq(0, 1, 2, 3),
    ChiNoCParams(ChiParams(3, 7, 32), RingParams(4, 5)) -> Seq(6, 1, 4, 0)
  )

  private def waitFor(dut: ChiNodeFixtures.HnSnOnNoCModule, message: String)(condition: => Boolean): Unit = {
    var cycles = 0
    while (!condition && cycles < 128) {
      dut.clock.step()
      cycles += 1
    }
    withClue(message) { condition shouldBe true }
  }

  private def rnId(ids: Seq[Int], rn: Int): Int = ids(rn * 2)

  private def sendRequest(dut: ChiNodeFixtures.HnSnOnNoCModule, ids: Seq[Int],
    rn: Int, txn: Int, index: Int, write: Boolean): Unit = {
    val port = dut.io(rn)
    request(port.reqTx.bits, ids(1), rnId(ids, rn), txn, BigInt(index) << 6)
    if (write) port.reqTx.bits.opcode.poke(ChiOpcode.Req.WriteNoSnpFull)
    port.reqTx.valid.poke(true)
    waitFor(dut, "RN REQ injection stalled: ")(port.reqTx.ready.peekBoolean())
    dut.clock.step()
    port.reqTx.valid.poke(false)
  }

  private def acceptDbId(dut: ChiNodeFixtures.HnSnOnNoCModule, ids: Seq[Int], rn: Int, txn: Int): Unit = {
    val port = dut.io(rn)
    waitFor(dut, "HN did not authorize RN write data: ")(port.rspRx.valid.peekBoolean())
    for (_ <- 0 until 8) {
      port.rspRx.valid.expect(true.B)
      expectResponse(port.rspRx.bits, rnId(ids, rn), ids(1), txn, ChiOpcode.Rsp.CompDBIDResp)
      dut.io(1 - rn).rspRx.valid.expect(false.B)
      dut.io.foreach(_.datRx.valid.expect(false.B))
      dut.clock.step()
    }
    port.rspRx.ready.poke(true)
    dut.clock.step()
    port.rspRx.ready.poke(false)
    for (_ <- 0 until 8) {
      dut.io.foreach(_.rspRx.valid.expect(false.B))
      dut.clock.step()
    }
  }

  private def sendData(dut: ChiNodeFixtures.HnSnOnNoCModule, ids: Seq[Int], rn: Int, value: BigInt): Unit = {
    val port = dut.io(rn)
    writeData(port.datTx.bits, ids(1), rnId(ids, rn), 0, value)
    port.datTx.valid.poke(true)
    waitFor(dut, "RN DAT injection stalled: ")(port.datTx.ready.peekBoolean())
    dut.clock.step()
    port.datTx.valid.poke(false)
    port.datTx.bits.data.poke(BigInt(0))
  }

  private def receiveRead(dut: ChiNodeFixtures.HnSnOnNoCModule, ids: Seq[Int],
    rn: Int, txn: Int, index: Int, value: BigInt): Unit = {
    val port = dut.io(rn)
    var retryAck = false
    var credit = false
    var resent = false
    var cycles = 0
    while (!port.datRx.valid.peekBoolean() && cycles < 128) {
      if (port.rspRx.valid.peekBoolean()) {
        val opcode = port.rspRx.bits.opcode.peek().litValue.toInt
        opcode match {
          case value if value == ChiOpcode.Rsp.RetryAck.litValue.toInt =>
            expectResponse(port.rspRx.bits, rnId(ids, rn), ids(1), txn, ChiOpcode.Rsp.RetryAck)
            retryAck = true
          case value if value == ChiOpcode.Rsp.PCrdGrant.litValue.toInt =>
            expectResponse(port.rspRx.bits, rnId(ids, rn), ids(1), 0, ChiOpcode.Rsp.PCrdGrant)
            credit = true
          case _ => fail(s"unexpected HN response opcode $opcode while waiting for read data")
        }
        port.rspRx.ready.poke(true)
        dut.clock.step()
        port.rspRx.ready.poke(false)
        if (retryAck && credit && !resent) {
          request(port.reqTx.bits, ids(1), rnId(ids, rn), txn, BigInt(index) << 6)
          port.reqTx.bits.allowRetry.poke(false)
          port.reqTx.valid.poke(true)
          waitFor(dut, "credited RN REQ injection stalled: ")(port.reqTx.ready.peekBoolean())
          dut.clock.step()
          port.reqTx.valid.poke(false)
          resent = true
        }
      } else {
        dut.clock.step()
      }
      cycles += 1
    }
    withClue("read after HN write did not finish: ") { port.datRx.valid.peekBoolean() shouldBe true }
    for (_ <- 0 until 8) {
      port.datRx.valid.expect(true.B)
      expectData(port.datRx.bits, rnId(ids, rn), ids(1), txn, value)
      dut.io(1 - rn).datRx.valid.expect(false.B)
      dut.io.foreach(_.rspRx.valid.expect(false.B))
      dut.clock.step()
    }
    port.datRx.ready.poke(true)
    dut.clock.step()
    port.datRx.ready.poke(false)
  }

  "HN and SN negotiate their identities and both RN port directions through the NoC" in {
    for ((config, ids) <- configs) {
      ChiselStage.emitCHIRRTL {
        val top = LazyModule(new ChiNodeFixtures.HnSnOnNoC(config, ids, lines))
        val dut = top.module
        top.network.node.edges.in.map(_.endpoint.nodeId) shouldBe ids
        top.network.node.edges.in.map(_.endpoint.role) shouldBe Seq(ChiRole.RN, ChiRole.HN, ChiRole.RN, ChiRole.SN)
        Seq(top.hn.node.edges.out.head.network, top.sn.node.edges.out.head.network) shouldBe Seq.fill(2)(config)
        for (port <- dut.io) {
          port.reqTx.bits.addr.getWidth shouldBe config.chi.addrWidth
          port.rspRx.bits.dbID.getWidth shouldBe config.chi.txnIdWidth
          port.datTx.bits.data.getWidth shouldBe 512
          DataMirror.directionOf(port.reqTx.valid) shouldBe ActualDirection.Input
          DataMirror.directionOf(port.reqTx.ready) shouldBe ActualDirection.Output
          DataMirror.directionOf(port.rspRx.valid) shouldBe ActualDirection.Output
          DataMirror.directionOf(port.rspRx.ready) shouldBe ActualDirection.Input
          DataMirror.directionOf(port.datTx.valid) shouldBe ActualDirection.Input
          DataMirror.directionOf(port.datTx.ready) shouldBe ActualDirection.Output
          DataMirror.directionOf(port.datRx.valid) shouldBe ActualDirection.Output
          DataMirror.directionOf(port.datRx.ready) shouldBe ActualDirection.Input
        }
        dut
      }
    }
  }

  for ((config, ids) <- configs) {
    s"both RN drivers write through the real HN and SN with IDs $ids" in {
      simulate(LazyModule(new ChiNodeFixtures.HnSnOnNoC(config, ids, lines)).module) { dut =>
        dut.io.foreach(initializeDriver)
        val expected = lines.toArray
        val txn = (1 << config.chi.txnIdWidth) - 1
        for (round <- 0 until 2; index <- expected.indices) {
          val rn = (round + index) % 2
          val value = if (round == 0) {
            (BigInt(1) << 511) | (BigInt(index + 7) << 256) | BigInt(0xfedcba98L)
          } else if (index == 0) BigInt(0) else (BigInt(1) << 512) - 1
          sendRequest(dut, ids, rn, txn, index, write = true)
          acceptDbId(dut, ids, rn, txn)
          sendData(dut, ids, rn, value)
          expected(index) = value
          // RN may issue its next request as soon as its write DAT is accepted.
          sendRequest(dut, ids, rn, txn, index, write = false)
          receiveRead(dut, ids, rn, txn, index, value)
          sendRequest(dut, ids, 1 - rn, txn, index, write = false)
          receiveRead(dut, ids, 1 - rn, txn, index, value)
        }
        for (rn <- 0 until 2; index <- expected.indices) {
          sendRequest(dut, ids, rn, txn - 1, index, write = false)
          receiveRead(dut, ids, rn, txn - 1, index, expected(index))
        }
        for (_ <- 0 until 24) {
          for (port <- dut.io) {
            Seq(port.reqRx, port.rspRx, port.snpRx, port.datRx).foreach(_.valid.expect(false.B))
          }
          dut.clock.step()
        }
      }
    }
  }

  for ((config, ids) <- configs) {
    s"one-slot HN drains a new request ahead of a credited retry in the real ring, IDs $ids" in {
      val top = LazyModule(new RetryCheckedHnSn(config, ids, lines))
      simulate(top.module) { dut =>
        val protocol = top.protocol.getWrappedValue
        protocol.checkDrained.poke(false)
        dut.io.foreach(initializeDriver)

        def receiveRsp(rn: Int, txn: Int, opcode: UInt): Unit = {
          val port = dut.io(rn)
          waitFor(dut, "retry admission response stalled: ")(port.rspRx.valid.peekBoolean())
          expectResponse(port.rspRx.bits, rnId(ids, rn), ids(1), txn, opcode)
          port.rspRx.ready.poke(true)
          dut.clock.step()
          port.rspRx.ready.poke(false)
        }
        def reissue(rn: Int, txn: Int, index: Int): Unit = {
          val port = dut.io(rn)
          request(port.reqTx.bits, ids(1), rnId(ids, rn), txn, BigInt(index) << 6)
          port.reqTx.bits.allowRetry.poke(false)
          port.reqTx.valid.poke(true)
          waitFor(dut, "credited retry injection stalled: ")(port.reqTx.ready.peekBoolean())
          dut.clock.step()
          port.reqTx.valid.poke(false)
        }
        def receiveCompletion(rn: Int, txn: Int, value: BigInt): Unit = {
          val port = dut.io(rn)
          waitFor(dut, "credited read did not complete: ")(port.datRx.valid.peekBoolean())
          // The next RN's grant may arrive while this DAT waits at the RN stop.
          for (_ <- 0 until 8) {
            port.datRx.valid.expect(true.B)
            expectData(port.datRx.bits, rnId(ids, rn), ids(1), txn, value)
            dut.io(1 - rn).datRx.valid.expect(false.B)
            dut.clock.step()
          }
          port.datRx.ready.poke(true)
          dut.clock.step()
          port.datRx.ready.poke(false)
        }

        // Hold an active write at WaitRnData so RN0 must occupy the only retry slot.
        sendRequest(dut, ids, 1, 1, 0, write = true)
        acceptDbId(dut, ids, 1, 1)
        sendRequest(dut, ids, 0, 2, 1, write = false)
        receiveRsp(0, 2, ChiOpcode.Rsp.RetryAck)
        sendData(dut, ids, 1, lines.head)
        receiveRsp(0, 0, ChiOpcode.Rsp.PCrdGrant)

        // Delay RN0's reissue until RN1's new REQ is at the HN FIFO head.
        sendRequest(dut, ids, 1, 3, 2, write = false)
        dut.clock.step(16)
        reissue(0, 2, 1)
        receiveRsp(1, 3, ChiOpcode.Rsp.RetryAck)
        // The waiting slot is full again. RN0 must still pass and complete exactly once.
        receiveCompletion(0, 2, lines(1))
        receiveRsp(1, 0, ChiOpcode.Rsp.PCrdGrant)
        reissue(1, 3, 2)
        receiveCompletion(1, 3, lines(2))
        for (_ <- 0 until 24) {
          dut.io.foreach { port =>
            Seq(port.reqRx, port.rspRx, port.snpRx, port.datRx).foreach(_.valid.expect(false.B))
          }
          dut.clock.step()
        }
        protocol.hn.first.expect(3.U)
        protocol.hn.acks.expect(2.U)
        protocol.hn.grants.expect(2.U)
        protocol.hn.retries.expect(2.U)
        protocol.hn.started.expect(3.U)
        protocol.hn.finished.expect(3.U)
        protocol.checkDrained.poke(true)
        dut.clock.step()
      }
    }
  }

  for (phase <- Seq("blocked RN authorization", "after RN write data")) {
    s"global reset cancels a write through HN and SN during $phase" in {
      val (config, ids) = configs.head
      simulate(LazyModule(new ChiNodeFixtures.HnSnOnNoC(config, ids, lines)).module) { dut =>
        dut.io.foreach(initializeDriver)
        sendRequest(dut, ids, 0, 13, 1, write = true)
        if (phase == "blocked RN authorization") {
          waitFor(dut, "HN response missing before reset: ")(dut.io.head.rspRx.valid.peekBoolean())
          expectResponse(dut.io.head.rspRx.bits, ids.head, ids(1), 13, ChiOpcode.Rsp.CompDBIDResp)
        } else {
          acceptDbId(dut, ids, 0, 13)
          sendData(dut, ids, 0, BigInt(0))
          dut.clock.step(4)
        }
        dut.reset.poke(true.B)
        dut.clock.step(2)
        dut.reset.poke(false.B)
        for (_ <- 0 until 24) {
          dut.io.foreach { port =>
            port.rspRx.valid.expect(false.B)
            port.datRx.valid.expect(false.B)
          }
          dut.clock.step()
        }
        sendRequest(dut, ids, 1, 2, 1, write = false)
        receiveRead(dut, ids, 1, 2, 1, lines(1))
      }
    }
  }
}
