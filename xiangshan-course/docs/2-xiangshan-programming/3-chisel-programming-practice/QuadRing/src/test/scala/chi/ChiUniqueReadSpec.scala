package chi

import chisel3._
import noc.RingParams
import org.chipsalliance.cde.config.Parameters
import org.chipsalliance.diplomacy.lazymodule.LazyModule
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

class ChiUniqueReadSpec extends AnyFreeSpec with Matchers with ChiNodeTestSupport {
  private implicit val p: Parameters = Parameters.empty
  private val configs = Seq(
    (ChiNoCParams(), Seq(0, 1, 2, 3), 2),
    (ChiNoCParams(ChiParams(3, 7, 32), RingParams(4, 5)), Seq(6, 1, 4, 0), 4)
  )
  private val memory = Seq.tabulate(16)(i => (BigInt(1) << 511) | (BigInt(i) << 256) | BigInt(i))
  private val first = (BigInt(1) << 510) | (BigInt(0xfedcba98L) << 256) | BigInt(0xa5a50000L)
  private val second = (BigInt(1) << 509) | (BigInt(0x89abcdefL) << 128) | BigInt(0x5a5affffL)

  private class Monitor(dut: ChiNodeFixtures.CoherentSystemModule) {
    var reads = 0
    var writes = 0
    var cancellations = 0
    def step(cycles: Int = 1): Unit = for (_ <- 0 until cycles) {
      if (dut.memoryRead.peekBoolean()) reads += 1
      if (dut.memoryWrite.peekBoolean()) writes += 1
      if (dut.cancelledWriteback.peekBoolean()) cancellations += 1
      dut.clock.step()
    }
  }

  private def send(dut: ChiNodeFixtures.CoherentSystemModule, m: Monitor, rn: Int,
    txn: Int, line: Int, value: Option[BigInt] = None): Unit = {
    val port = dut.io(rn)
    value match {
      case Some(v) => localWriteRequest(port, txn, BigInt(line * 64), v)
      case None => localRequest(port, txn, BigInt(line * 64))
    }
    port.request.ready.expect(true.B)
    m.step()
    port.request.valid.poke(false)
    port.request.bits.addr.poke(0.U)
    port.request.bits.data.poke(0.U)
  }

  private def result(dut: ChiNodeFixtures.CoherentSystemModule, m: Monitor, rn: Int,
    txn: Int, expected: BigInt, consume: Boolean = true): Unit = {
    val port = dut.io(rn)
    var cycles = 0
    while (!port.response.valid.peekBoolean() && cycles < 256) {
      m.step()
      cycles += 1
    }
    for (_ <- 0 until 4) {
      port.response.valid.expect(true.B)
      port.response.bits.txnID.expect(txn.U)
      port.response.bits.data.expect(expected.U)
      port.request.ready.expect(false.B)
      m.step()
    }
    if (consume) {
      port.response.ready.poke(true)
      m.step()
      port.response.ready.poke(false)
    }
  }

  private def transact(dut: ChiNodeFixtures.CoherentSystemModule, m: Monitor, rn: Int,
    txn: Int, line: Int, expected: BigInt, write: Boolean = false): Unit = {
    send(dut, m, rn, txn, line, if (write) Some(expected) else None)
    result(dut, m, rn, txn, if (write) BigInt(0) else expected)
  }

  for ((config, ids, cacheLines) <- configs) {
    s"unique requests invalidate UC SC UD and keep dirty transfers off SN with IDs $ids" in {
      simulate(LazyModule(new ChiNodeFixtures.CoherentSystem(config, ids, memory, cacheLines)).module) { dut =>
        dut.io.foreach(initialize)
        val m = new Monitor(dut)
        send(dut, m, 0, 1, 0)
        result(dut, m, 0, 1, memory.head, consume = false)
        // Invalidate UC while its original CPU result is blocked; retain that result's snapshot.
        transact(dut, m, 1, 1, 0, first, write = true)
        result(dut, m, 0, 1, memory.head)
        m.reads shouldBe 2
        m.writes shouldBe 0
        transact(dut, m, 0, 2, 0, first) // Dirty sharing cleans SN and makes both RNs SC.
        m.writes shouldBe 1
        transact(dut, m, 0, 3, 0, second, write = true) // SC -> U upgrade invalidates the peer.
        m.reads shouldBe 3
        val readsBeforeTransfer = m.reads
        transact(dut, m, 1, 4, 0, first, write = true) // UD -> UD transfer, then local overwrite.
        m.reads shouldBe readsBeforeTransfer
        m.writes shouldBe 1
        transact(dut, m, 0, 5, 0, first) // The old owner must miss, rather than return second.
        transact(dut, m, 1, 6, 0, first)
        m.writes shouldBe 2
        // Remove both clean copies and verify SN retained the latest value.
        transact(dut, m, 0, 7, cacheLines, memory(cacheLines))
        transact(dut, m, 1, 8, cacheLines, memory(cacheLines))
        transact(dut, m, 0, 9, 0, first)
        m.writes shouldBe 2
        m.step(16)
        dut.io.foreach(_.response.valid.expect(false.B))
      }
    }

    s"unique request races dirty replacement without duplicate writes with IDs $ids" in {
      simulate(LazyModule(new ChiNodeFixtures.CoherentSystem(config, ids, memory, cacheLines)).module) { dut =>
        var totalCancellations = 0
        for (delay <- 0 until 5) {
          dut.io.foreach(initialize)
          dut.reset.poke(true)
          dut.clock.step(2)
          dut.reset.poke(false)
          val m = new Monitor(dut)
          transact(dut, m, 0, 1, 1, first, write = true)
          send(dut, m, 1, 2, 1, Some(second))
          m.step(delay)
          send(dut, m, 0, 2, 1 + cacheLines)
          result(dut, m, 1, 2, 0)
          result(dut, m, 0, 2, memory(1 + cacheLines))
          m.step(16)
          m.cancellations should be <= 1
          m.writes shouldBe (if (m.cancellations == 1) 0 else 1)
          totalCancellations += m.cancellations
          val writesBeforeReplacement = m.writes
          transact(dut, m, 1, 3, 1, second)
          transact(dut, m, 1, 4, 1 + cacheLines, memory(1 + cacheLines))
          transact(dut, m, 0, 5, 1, second)
          m.writes shouldBe writesBeforeReplacement + 1
        }
        totalCancellations should be > 0
      }
    }

    s"two shared holders can both request unique permission with IDs $ids" in {
      simulate(LazyModule(new ChiNodeFixtures.CoherentSystem(config, ids, memory, cacheLines)).module) { dut =>
        dut.io.foreach(initialize)
        val m = new Monitor(dut)
        transact(dut, m, 0, 1, 0, memory.head)
        transact(dut, m, 1, 1, 0, memory.head)
        localWriteRequest(dut.io(0), 2, 0, first)
        localWriteRequest(dut.io(1), 2, 0, second)
        dut.io.foreach(_.request.ready.expect(true.B))
        m.step()
        dut.io.foreach(_.request.valid.poke(false))
        result(dut, m, 0, 2, 0)
        result(dut, m, 1, 2, 0)
        // HN admission decides the order; both subsequent readers must agree on the last write.
        send(dut, m, 0, 3, 0)
        var cycles = 0
        while (!dut.io(0).response.valid.peekBoolean() && cycles < 256) {
          m.step()
          cycles += 1
        }
        dut.io(0).response.valid.expect(true.B)
        val latest = dut.io(0).response.bits.data.peek().litValue
        Set(first, second) should contain(latest)
        result(dut, m, 0, 3, latest)
        transact(dut, m, 1, 3, 0, latest)
        m.step(16)
        dut.io.foreach(_.response.valid.expect(false.B))
      }
    }
  }
}
