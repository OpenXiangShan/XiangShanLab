package chi

import chisel3._
import chisel3.reflect.DataMirror
import noc.RingParams
import org.chipsalliance.cde.config.Parameters
import org.chipsalliance.diplomacy.lazymodule.LazyModule
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

class RnSnoopNoCSpec extends AnyFreeSpec with Matchers with ChiNodeTestSupport {
  private implicit val p: Parameters = Parameters.empty
  private val configs = Seq(
    ChiNoCParams() -> Seq(0, 1, 2, 3),
    ChiNoCParams(ChiParams(3, 7, 32), RingParams(4, 5)) -> Seq(6, 1, 4, 0)
  )
  private val lineData = (BigInt(1) << 511) | (BigInt(0xfedcba98L) << 256) | BigInt(0xa5a50000L)

  private def waitFor(dut: ChiNodeFixtures.RnOnNoCModule)(condition: => Boolean): Unit = {
    var cycles = 0
    while (!condition && cycles < 128) {
      dut.clock.step()
      cycles += 1
    }
    withClue("RN transport did not progress within 128 cycles: ") { condition shouldBe true }
  }

  private def start(dut: ChiNodeFixtures.RnOnNoCModule, txn: Int, address: Int): Unit = {
    localRequest(dut.io.local, txn, BigInt(address))
    dut.io.local.request.ready.expect(true.B)
    dut.clock.step()
    dut.io.local.request.valid.poke(false)
    dut.io.local.request.bits.addr.poke(0.U)
  }

  private def acceptRequest(dut: ChiNodeFixtures.RnOnNoCModule, ids: Seq[Int], txn: Int,
    address: Int, opcode: UInt, credited: Boolean = false): Unit = {
    val port = dut.io.hn
    waitFor(dut)(port.reqRx.valid.peekBoolean())
    expectRequest(port.reqRx.bits, ids(1), ids.head, txn, BigInt(address), opcode, allowRetry = !credited)
    port.reqRx.ready.poke(true)
    dut.clock.step()
    port.reqRx.ready.poke(false)
  }

  private def respond(dut: ChiNodeFixtures.RnOnNoCModule, ids: Seq[Int], txn: Int,
    opcode: UInt, dbId: Int = 0): Unit = {
    val port = dut.io.hn
    response(port.rspTx.bits, ids.head, ids(1), txn, opcode, dbId)
    port.rspTx.valid.poke(true)
    waitFor(dut)(port.rspTx.ready.peekBoolean())
    dut.clock.step()
    port.rspTx.valid.poke(false)
  }

  private def complete(dut: ChiNodeFixtures.RnOnNoCModule, ids: Seq[Int], txn: Int,
    resp: UInt): Unit = {
    val port = dut.io.hn
    data(port.datTx.bits, ids.head, ids(1), txn, lineData)
    port.datTx.bits.resp.poke(resp)
    port.datTx.bits.dbID.poke(9.U)
    port.datTx.valid.poke(true)
    waitFor(dut)(port.datTx.ready.peekBoolean())
    dut.clock.step()
    port.datTx.valid.poke(false)
    waitFor(dut)(port.rspRx.valid.peekBoolean())
    expectResponse(port.rspRx.bits, ids(1), ids.head, 9, ChiOpcode.Rsp.CompAck)
    port.rspRx.ready.poke(true)
    dut.clock.step()
    port.rspRx.ready.poke(false)
    waitFor(dut)(dut.io.local.response.valid.peekBoolean())
    dut.io.local.response.bits.txnID.expect(txn.U)
    dut.io.local.response.bits.data.expect(lineData.U)
    dut.io.local.response.ready.poke(true)
    dut.clock.step()
    dut.io.local.response.ready.poke(false)
  }

  private def sendSnoop(dut: ChiNodeFixtures.RnOnNoCModule, ids: Seq[Int], txn: Int,
    unique: Boolean): Unit = {
    val port = dut.io.hn
    port.snpTx.bits.dst.poke(ids.head.U)
    port.snpTx.bits.payload.srcID.poke(ids(1).U)
    port.snpTx.bits.payload.txnID.poke(txn.U)
    port.snpTx.bits.payload.opcode.poke(if (unique) ChiOpcode.Snp.SnpUnique else ChiOpcode.Snp.SnpShared)
    port.snpTx.bits.payload.addr.poke(64.U)
    port.snpTx.valid.poke(true)
    waitFor(dut)(port.snpTx.ready.peekBoolean())
    dut.clock.step()
    port.snpTx.valid.poke(false)
    port.snpTx.bits.payload.addr.poke(0.U)
  }

  private def receiveSnoop(dut: ChiNodeFixtures.RnOnNoCModule, ids: Seq[Int], txn: Int,
    resp: UInt, dirty: Boolean): Unit = {
    val port = dut.io.hn
    waitFor(dut)(if (dirty) port.datRx.valid.peekBoolean() else port.rspRx.valid.peekBoolean())
    for (_ <- 0 until 4) {
      if (dirty) {
        port.datRx.valid.expect(true.B)
        port.datRx.bits.tgtID.expect(ids(1).U)
        port.datRx.bits.srcID.expect(ids.head.U)
        port.datRx.bits.txnID.expect(txn.U)
        port.datRx.bits.opcode.expect(ChiOpcode.Dat.SnpRespData)
        port.datRx.bits.resp.expect(resp)
        port.datRx.bits.dbID.expect(0.U)
        port.datRx.bits.data.expect(lineData.U)
      } else {
        port.rspRx.valid.expect(true.B)
        port.rspRx.bits.opcode.expect(ChiOpcode.Rsp.SnpResp)
        port.rspRx.bits.tgtID.expect(ids(1).U)
        port.rspRx.bits.srcID.expect(ids.head.U)
        port.rspRx.bits.txnID.expect(txn.U)
        port.rspRx.bits.resp.expect(resp)
        port.rspRx.bits.dbID.expect(0.U)
        port.rspRx.bits.pCrdType.expect(0.U)
      }
      dut.clock.step()
    }
    if (dirty) port.datRx.ready.poke(true) else port.rspRx.ready.poke(true)
    dut.clock.step()
    port.datRx.ready.poke(false)
    port.rspRx.ready.poke(false)
  }

  for ((config, ids) <- configs) {
    s"RN snoop data and cancelled writeback traverse the four rings with IDs $ids" in {
      val top = LazyModule(new ChiNodeFixtures.RnOnNoC(config, ids))
      simulate(top.module) { dut =>
        top.network.node.edges.in.map(_.endpoint.nodeId) shouldBe ids
        top.rn.node.edges.out.head.network shouldBe config
        top.drivers.head.edges.out.head.endpoint.role shouldBe ChiRole.HN
        DataMirror.directionOf(dut.io.hn.snpTx.valid) shouldBe ActualDirection.Input
        DataMirror.directionOf(dut.io.hn.datRx.valid) shouldBe ActualDirection.Output
        dut.io.hn.snpTx.bits.payload.addr.getWidth shouldBe config.chi.addrWidth
        initialize(dut.io.local)
        initializeDriver(dut.io.hn)
        start(dut, 1, 64)
        acceptRequest(dut, ids, 1, 64, ChiOpcode.Req.ReadShared)
        complete(dut, ids, 1, ChiResp.UD_PD)
        sendSnoop(dut, ids, 3, unique = false)
        receiveSnoop(dut, ids, 3, ChiResp.SC_PD, dirty = true)
        sendSnoop(dut, ids, 4, unique = true)
        receiveSnoop(dut, ids, 4, ChiResp.I, dirty = false)

        start(dut, 5, 64)
        acceptRequest(dut, ids, 5, 64, ChiOpcode.Req.ReadShared)
        complete(dut, ids, 5, ChiResp.UD_PD)
        start(dut, 6, 192) // Conflicts with the dirty line at 64.
        acceptRequest(dut, ids, 6, 64, ChiOpcode.Req.WriteBackFull)
        respond(dut, ids, 6, ChiOpcode.Rsp.RetryAck)
        sendSnoop(dut, ids, 7, unique = true)
        receiveSnoop(dut, ids, 7, ChiResp.I_PD, dirty = true)
        sendSnoop(dut, ids, 8, unique = false)
        receiveSnoop(dut, ids, 8, ChiResp.I, dirty = false)
        respond(dut, ids, 0, ChiOpcode.Rsp.PCrdGrant)
        acceptRequest(dut, ids, 6, 64, ChiOpcode.Req.WriteBackFull, credited = true)
        respond(dut, ids, 6, ChiOpcode.Rsp.CompDBIDResp, dbId = 9)
        waitFor(dut)(dut.io.hn.datRx.valid.peekBoolean())
        expectData(dut.io.hn.datRx.bits, ids(1), ids.head, 9, BigInt(0), ChiOpcode.Dat.CopyBackWrData)
        dut.io.hn.datRx.ready.poke(true)
        dut.clock.step()
        dut.io.hn.datRx.ready.poke(false)
        acceptRequest(dut, ids, 6, 192, ChiOpcode.Req.ReadShared)
        complete(dut, ids, 6, ChiResp.UC)
        for (_ <- 0 until 24) {
          Seq(dut.io.hn.reqRx, dut.io.hn.rspRx, dut.io.hn.snpRx, dut.io.hn.datRx)
            .foreach(_.valid.expect(false.B))
          dut.clock.step()
        }
      }
    }
  }
}
