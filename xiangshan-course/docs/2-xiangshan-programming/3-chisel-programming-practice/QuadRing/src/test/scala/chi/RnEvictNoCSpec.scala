package chi

import chisel3._
import chisel3.reflect.DataMirror
import noc.RingParams
import org.chipsalliance.cde.config.Parameters
import org.chipsalliance.diplomacy.lazymodule.LazyModule
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

class RnEvictNoCSpec extends AnyFreeSpec with Matchers with ChiNodeTestSupport {
  private implicit val p: Parameters = Parameters.empty
  private val configs = Seq(
    (ChiNoCParams(), Seq(0, 1, 2, 3), 2),
    (ChiNoCParams(ChiParams(3, 7, 32), RingParams(4, 5)), Seq(6, 1, 4, 0), 4)
  )
  private val memory = Seq.tabulate(16)(index =>
    (BigInt(1) << 511) | (BigInt(index) << 256) | BigInt(0x89abcdefL + index))

  private def transact(dut: ChiNodeFixtures.CoherentSystemModule, rn: Int,
    txn: Int, line: Int, value: BigInt, write: Boolean = false): Unit = {
    val local = dut.io(rn)
    val address = BigInt(line * 64)
    if (write) localWriteRequest(local, txn, address, value)
    else localRequest(local, txn, address)
    local.request.ready.expect(true.B)
    dut.clock.step()
    local.request.valid.poke(false)
    // Deliberately change all inputs while RN retains the original command.
    local.request.bits.addr.poke(0.U)
    local.request.bits.txnID.poke(0.U)
    local.request.bits.data.poke(0.U)
    var cycles = 0
    while (!local.response.valid.peekBoolean() && cycles < 256) {
      local.request.ready.expect(false.B)
      dut.io(1 - rn).response.valid.expect(false.B)
      dut.clock.step()
      cycles += 1
    }
    for (_ <- 0 until 5) {
      local.response.valid.expect(true.B)
      local.response.bits.txnID.expect(txn.U)
      local.response.bits.data.expect((if (write) BigInt(0) else value).U)
      local.request.ready.expect(false.B)
      dut.io(1 - rn).response.valid.expect(false.B)
      dut.clock.step()
    }
    local.response.ready.poke(true)
    dut.clock.step()
    local.response.ready.poke(false)
  }

  for ((config, ids, cacheLines) <- configs) {
    s"real RN clean replacement releases HN ownership through the NoC with IDs $ids" in {
      val top = LazyModule(new ChiNodeFixtures.CoherentSystem(config, ids, memory, cacheLines))
      simulate(top.module) { dut =>
        val endpoints = Seq(top.rn0.node, top.hn.node, top.rn1.node, top.sn.node)
        top.network.node.edges.in.map(_.endpoint.nodeId) shouldBe ids
        endpoints.map(_.edges.out.head.network) shouldBe Seq.fill(4)(config)
        endpoints.map(_.edges.out.head.endpoint.role) shouldBe
          Seq(ChiRole.RN, ChiRole.HN, ChiRole.RN, ChiRole.SN)
        for (port <- dut.io) {
          DataMirror.directionOf(port.request.valid) shouldBe ActualDirection.Input
          DataMirror.directionOf(port.request.ready) shouldBe ActualDirection.Output
          DataMirror.directionOf(port.response.valid) shouldBe ActualDirection.Output
          DataMirror.directionOf(port.response.ready) shouldBe ActualDirection.Input
          port.request.bits.addr.getWidth shouldBe config.chi.addrWidth
          port.response.bits.txnID.getWidth shouldBe config.chi.txnIdWidth
          initialize(port)
        }

        transact(dut, 0, 1, 1, memory(1))
        transact(dut, 0, 2, 0, memory.head) // A different cache index must survive replacement.
        transact(dut, 0, 3, 1 + cacheLines, memory(1 + cacheLines))
        // RN1 can now take the old victim without a snoop or stale unique-holder assertion.
        transact(dut, 1, 4, 1, memory(1))
        transact(dut, 0, 5, 1 + cacheLines, memory(1 + cacheLines))
        transact(dut, 0, 6, 0, memory.head)

        val written = (BigInt(1) << 510) | (BigInt(0xfedcba98L) << 256) | BigInt(0xa5a50000L)
        transact(dut, 0, 7, 1 + 2 * cacheLines, written, write = true)
        transact(dut, 1, 8, 1 + cacheLines, memory(1 + cacheLines))
        transact(dut, 0, 9, 1 + 2 * cacheLines, written)
        for (_ <- 0 until 24) {
          dut.io.foreach { port =>
            port.response.valid.expect(false.B)
            port.request.ready.expect(true.B)
          }
          dut.clock.step()
        }
      }
    }
  }
}
