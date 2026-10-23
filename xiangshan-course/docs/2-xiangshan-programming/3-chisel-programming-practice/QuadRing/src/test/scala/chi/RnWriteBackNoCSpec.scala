package chi

import chisel3._
import noc.RingParams
import org.chipsalliance.cde.config.Parameters
import org.chipsalliance.diplomacy.lazymodule.LazyModule
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

class RnWriteBackNoCSpec extends AnyFreeSpec with Matchers with ChiNodeTestSupport {
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
    if (write) localWriteRequest(local, txn, BigInt(line * 64), value)
    else localRequest(local, txn, BigInt(line * 64))
    local.request.ready.expect(true.B)
    dut.clock.step()
    local.request.valid.poke(false)
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
    for (_ <- 0 until 4) {
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
    s"RN dirty replacement writes the real SN before subsequent reads with IDs $ids" in {
      val top = LazyModule(new ChiNodeFixtures.CoherentSystem(config, ids, memory, cacheLines))
      simulate(top.module) { dut =>
        top.network.node.edges.in.map(_.endpoint.nodeId) shouldBe ids
        Seq(top.rn0.node, top.hn.node, top.rn1.node, top.sn.node).foreach { node =>
          node.edges.out.head.network shouldBe config
        }
        dut.io.foreach(initialize)
        val a = 1
        val b = a + cacheLines
        val c = b + cacheLines
        val first = (BigInt(1) << 511) | (BigInt(0xfedcba98L) << 256) | BigInt(0xa5a50000L)
        val second = first ^ ((BigInt(1) << 510) | BigInt(0x55aa))
        val third = (BigInt(1) << 509) | BigInt(0xdeadbeefL)

        transact(dut, 0, 1, 0, memory.head) // Other cache indices must survive writeback.
        transact(dut, 0, 2, a, memory(a))
        transact(dut, 0, 3, a, first, write = true) // UC -> UD locally.
        transact(dut, 0, 4, b, memory(b)) // Dirty A -> clean B.
        transact(dut, 0, 5, a, first) // Clean B -> A, fetched from updated SN.
        transact(dut, 0, 6, a, second, write = true)
        transact(dut, 0, 7, b, third, write = true) // Dirty A -> dirty B.
        transact(dut, 0, 8, b, third) // New local write survived the old line's writeback.
        transact(dut, 0, 9, c, memory(c)) // Write B back too.
        transact(dut, 0, 10, 0, memory.head)

        // Both victims are now uncached; RN1 must see each latest write through HN/SN.
        transact(dut, 1, 11, a, second)
        transact(dut, 1, 12, b, third)
        for (_ <- 0 until 24) {
          dut.io.foreach { port =>
            port.request.ready.expect(true.B)
            port.response.valid.expect(false.B)
          }
          dut.clock.step()
        }
      }
    }
  }
}
