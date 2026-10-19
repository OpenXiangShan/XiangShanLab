package chi

import chisel3._
import chisel3.reflect.DataMirror
import circt.stage.ChiselStage
import noc.RingParams
import org.chipsalliance.cde.config.Parameters
import org.chipsalliance.diplomacy.lazymodule.LazyModule
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

class SnNoCSpec extends AnyFreeSpec with Matchers with ChiNodeTestSupport {
  private implicit val p: Parameters = Parameters.empty
  private val configs = Seq(
    ChiNoCParams() -> Seq(0, 1, 2, 3),
    ChiNoCParams(ChiParams(3, 7, 32), RingParams(4, 5)) -> Seq(6, 1, 4, 0)
  )

  private def waitFor(dut: ChiNodeFixtures.SnOnNoCModule, message: String)(condition: => Boolean): Unit = {
    var cycles = 0
    while (!condition && cycles < 128) {
      dut.clock.step()
      cycles += 1
    }
    withClue(message) { condition shouldBe true }
  }

  private def sendRequest(dut: ChiNodeFixtures.SnOnNoCModule, ids: Seq[Int],
    txn: Int, index: Int, write: Boolean): Unit = {
    request(dut.io.reqTx.bits, ids(3), ids(1), txn, BigInt(index) << 6)
    if (write) dut.io.reqTx.bits.opcode.poke(ChiOpcode.Req.WriteNoSnpFull)
    dut.io.reqTx.valid.poke(true)
    waitFor(dut, "REQ injection stalled: ")(dut.io.reqTx.ready.peekBoolean())
    dut.clock.step()
    dut.io.reqTx.valid.poke(false)
  }

  private def acceptDbId(dut: ChiNodeFixtures.SnOnNoCModule, ids: Seq[Int], txn: Int): Unit = {
    waitFor(dut, "SN did not authorize write data: ")(dut.io.rspRx.valid.peekBoolean())
    for (_ <- 0 until 8) {
      dut.io.rspRx.valid.expect(true.B)
      expectResponse(dut.io.rspRx.bits, ids(1), ids(3), txn, ChiOpcode.Rsp.DBIDResp)
      dut.io.datRx.valid.expect(false.B)
      dut.clock.step()
    }
    dut.io.rspRx.ready.poke(true)
    dut.clock.step()
    dut.io.rspRx.ready.poke(false)
    for (_ <- 0 until 8) {
      dut.io.rspRx.valid.expect(false.B)
      dut.clock.step()
    }
  }

  private def sendData(dut: ChiNodeFixtures.SnOnNoCModule, ids: Seq[Int], value: BigInt): Unit = {
    writeData(dut.io.datTx.bits, ids(3), ids(1), 0, value)
    dut.io.datTx.valid.poke(true)
    waitFor(dut, "DAT injection stalled: ")(dut.io.datTx.ready.peekBoolean())
    dut.clock.step()
    dut.io.datTx.valid.poke(false)
    dut.io.datTx.bits.data.poke(BigInt(0))
  }

  private def readBack(dut: ChiNodeFixtures.SnOnNoCModule, ids: Seq[Int],
    txn: Int, index: Int, value: BigInt): Unit = {
    sendRequest(dut, ids, txn, index, write = false)
    waitFor(dut, "SN read did not finish: ")(dut.io.datRx.valid.peekBoolean())
    for (_ <- 0 until 8) {
      dut.io.datRx.valid.expect(true.B)
      expectData(dut.io.datRx.bits, ids(1), ids(3), txn, value)
      dut.io.rspRx.valid.expect(false.B)
      dut.clock.step()
    }
    dut.io.datRx.ready.poke(true)
    dut.clock.step()
    dut.io.datRx.ready.poke(false)
  }

  "SN write ports negotiate identities, widths, and directions through the real NoC" in {
    for ((config, ids) <- configs) {
      ChiselStage.emitCHIRRTL {
        val top = LazyModule(new ChiNodeFixtures.SnOnNoC(config, ids, lines))
        val dut = top.module
        top.network.node.edges.in.map(_.endpoint.nodeId) shouldBe ids
        top.sn.node.edges.out.head.endpoint shouldBe ChiEndpointParams(ids(3), ChiRole.SN)
        top.sn.node.edges.out.head.network shouldBe config
        dut.io.reqTx.bits.addr.getWidth shouldBe config.chi.addrWidth
        dut.io.rspRx.bits.dbID.getWidth shouldBe config.chi.txnIdWidth
        dut.io.datTx.bits.data.getWidth shouldBe 512
        DataMirror.directionOf(dut.io.reqTx.valid) shouldBe ActualDirection.Input
        DataMirror.directionOf(dut.io.reqTx.ready) shouldBe ActualDirection.Output
        DataMirror.directionOf(dut.io.rspRx.valid) shouldBe ActualDirection.Output
        DataMirror.directionOf(dut.io.rspRx.ready) shouldBe ActualDirection.Input
        DataMirror.directionOf(dut.io.datTx.valid) shouldBe ActualDirection.Input
        DataMirror.directionOf(dut.io.datTx.ready) shouldBe ActualDirection.Output
        dut
      }
    }
  }

  for ((config, ids) <- configs) {
    s"SN writes and reads every line through REQ RSP and DAT rings with IDs $ids" in {
      simulate(LazyModule(new ChiNodeFixtures.SnOnNoC(config, ids, lines)).module) { dut =>
        initializeDriver(dut.io)
        val expected = lines.toArray
        val mask = (1 << config.chi.txnIdWidth) - 1
        for (round <- 0 until 2; index <- expected.indices) {
          val txn = mask - index
          val value = if (round == 0) {
            (BigInt(1) << 511) | (BigInt(index + 7) << 256) | BigInt(0xfedcba98L)
          } else if (index == 0) BigInt(0) else (BigInt(1) << 512) - 1
          sendRequest(dut, ids, txn, index, write = true)
          acceptDbId(dut, ids, txn)
          sendData(dut, ids, value)
          waitFor(dut, "SN write did not complete: ")(dut.io.rspRx.valid.peekBoolean())
          for (_ <- 0 until 8) {
            dut.io.rspRx.valid.expect(true.B)
            expectResponse(dut.io.rspRx.bits, ids(1), ids(3), txn, ChiOpcode.Rsp.Comp)
            dut.io.datRx.valid.expect(false.B)
            dut.clock.step()
          }
          dut.io.rspRx.ready.poke(true)
          dut.clock.step()
          dut.io.rspRx.ready.poke(false)
          expected(index) = value
          for (row <- expected.indices) readBack(dut, ids, txn - 3, row, expected(row))
        }
        for (_ <- 0 until 24) {
          Seq(dut.io.reqRx, dut.io.rspRx, dut.io.snpRx, dut.io.datRx).foreach(_.valid.expect(false.B))
          dut.clock.step()
        }
      }
    }
  }

  for (phase <- Seq("authorization", "completion")) {
    s"global reset clears SN write $phase in the NoC and reinitializes memory" in {
      val (config, ids) = configs.head
      simulate(LazyModule(new ChiNodeFixtures.SnOnNoC(config, ids, lines)).module) { dut =>
        initializeDriver(dut.io)
        sendRequest(dut, ids, 13, 1, write = true)
        if (phase == "completion") {
          acceptDbId(dut, ids, 13)
          sendData(dut, ids, BigInt(0))
        }
        waitFor(dut, "SN response missing before reset: ")(dut.io.rspRx.valid.peekBoolean())
        expectResponse(dut.io.rspRx.bits, ids(1), ids(3), 13,
          if (phase == "authorization") ChiOpcode.Rsp.DBIDResp else ChiOpcode.Rsp.Comp)
        dut.reset.poke(true.B)
        dut.clock.step(2)
        dut.reset.poke(false.B)
        for (_ <- 0 until 24) {
          dut.io.rspRx.valid.expect(false.B)
          dut.io.datRx.valid.expect(false.B)
          dut.clock.step()
        }
        readBack(dut, ids, 2, 1, lines(1))
      }
    }
  }
}
