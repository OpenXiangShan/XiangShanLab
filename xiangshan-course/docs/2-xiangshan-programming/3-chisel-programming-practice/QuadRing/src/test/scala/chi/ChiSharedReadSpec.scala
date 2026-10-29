package chi

import chisel3._
import noc.RingParams
import org.chipsalliance.cde.config.Parameters
import org.chipsalliance.diplomacy.lazymodule.LazyModule
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

class ChiSharedReadSpec extends AnyFreeSpec with Matchers with ChiNodeTestSupport {
  private implicit val p: Parameters = Parameters.empty
  private val configs = Seq(
    (ChiNoCParams(), Seq(0, 1, 2, 3), 2),
    (ChiNoCParams(ChiParams(3, 7, 32), RingParams(4, 5)), Seq(6, 1, 4, 0), 4)
  )
  private val memory = Seq.tabulate(16)(index =>
    (BigInt(1) << 511) | (BigInt(index) << 256) | BigInt(0x89abcdefL + index))
  private val dirtyData = (BigInt(1) << 510) | (BigInt(0xfedcba98L) << 256) | BigInt(0xa5a50000L)

  private class Monitor(dut: ChiNodeFixtures.CoherentSystemModule) {
    var cancellations = 0
    var writes = 0
    def step(cycles: Int = 1): Unit = for (_ <- 0 until cycles) {
      if (dut.cancelledWriteback.peekBoolean()) cancellations += 1
      if (dut.memoryWrite.peekBoolean()) writes += 1
      dut.clock.step()
    }
  }

  private def send(dut: ChiNodeFixtures.CoherentSystemModule, monitor: Monitor, rn: Int,
    txn: Int, line: Int, write: Boolean = false): Unit = {
    val port = dut.io(rn)
    if (write) localWriteRequest(port, txn, BigInt(line * 64), dirtyData)
    else localRequest(port, txn, BigInt(line * 64))
    port.request.ready.expect(true.B)
    monitor.step()
    port.request.valid.poke(false)
    port.request.bits.addr.poke(0.U)
    port.request.bits.data.poke(0.U)
    port.request.bits.txnID.poke(0.U)
  }

  private def awaitResult(dut: ChiNodeFixtures.CoherentSystemModule, monitor: Monitor,
    rn: Int, txn: Int, expected: BigInt, consume: Boolean = true): Unit = {
    val port = dut.io(rn)
    var cycles = 0
    while (!port.response.valid.peekBoolean() && cycles < 256) {
      port.request.ready.expect(false.B)
      monitor.step()
      cycles += 1
    }
    for (_ <- 0 until 4) {
      port.response.valid.expect(true.B)
      port.response.bits.txnID.expect(txn.U)
      port.response.bits.data.expect(expected.U)
      port.request.ready.expect(false.B)
      monitor.step()
    }
    if (consume) {
      port.response.ready.poke(true)
      monitor.step()
      port.response.ready.poke(false)
    }
  }

  private def transact(dut: ChiNodeFixtures.CoherentSystemModule, monitor: Monitor,
    rn: Int, txn: Int, line: Int, expected: BigInt, write: Boolean = false): Unit = {
    send(dut, monitor, rn, txn, line, write)
    awaitResult(dut, monitor, rn, txn, if (write) BigInt(0) else expected)
  }

  for ((config, ids, cacheLines) <- configs) {
    s"clean and dirty sharing complete through real RN HN SN nodes with IDs $ids" in {
      val top = LazyModule(new ChiNodeFixtures.CoherentSystem(config, ids, memory, cacheLines))
      simulate(top.module) { dut =>
        dut.io.foreach(initialize)
        val monitor = new Monitor(dut)
        top.network.node.edges.in.map(_.endpoint.nodeId) shouldBe ids
        Seq(top.rn0.node, top.hn.node, top.rn1.node, top.sn.node).foreach { node =>
          node.edges.out.head.network shouldBe config
        }
        // Keep the first CPU result blocked: snooping must still downgrade that RN.
        send(dut, monitor, 0, 1, 0)
        awaitResult(dut, monitor, 0, 1, memory.head, consume = false)
        transact(dut, monitor, 1, 1, 0, memory.head)
        awaitResult(dut, monitor, 0, 1, memory.head)
        transact(dut, monitor, 0, 2, 0, memory.head)
        // A cache miss while the peer is SC must snoop it again and retain sharing.
        transact(dut, monitor, 1, 3, cacheLines, memory(cacheLines))
        transact(dut, monitor, 1, 4, 0, memory.head)
        monitor.writes shouldBe 0

        // Use another index so neither RN needs SnpUnique yet.
        transact(dut, monitor, 0, 5, 1, dirtyData, write = true)
        transact(dut, monitor, 1, 6, 1, dirtyData)
        monitor.writes shouldBe 1
        transact(dut, monitor, 0, 7, 1, dirtyData)
        // Evict both SC copies; a fresh memory read must observe the cleaned data.
        transact(dut, monitor, 0, 8, 1 + cacheLines, memory(1 + cacheLines))
        transact(dut, monitor, 1, 9, 1 + cacheLines, memory(1 + cacheLines))
        transact(dut, monitor, 0, 10, 1, dirtyData)
        transact(dut, monitor, 1, 11, 1, dirtyData)
        monitor.writes shouldBe 1 // Sharing must not repeatedly flush the same data.
        monitor.step(24)
        dut.io.foreach(_.response.valid.expect(false.B))
      }
    }

    s"shared read and dirty replacement preserve data across retry with IDs $ids" in {
      simulate(LazyModule(new ChiNodeFixtures.CoherentSystem(config, ids, memory, cacheLines)).module) { dut =>
        var totalCancellations = 0
        // Stagger the two local requests to exercise either winner at HN admission.
        for (delay <- 0 until 5) {
          dut.io.foreach(initialize)
          dut.reset.poke(true)
          dut.clock.step(2)
          dut.reset.poke(false)
          val monitor = new Monitor(dut)
          transact(dut, monitor, 0, 1, 1, dirtyData, write = true)
          send(dut, monitor, 1, 2, 1)
          monitor.step(delay)
          send(dut, monitor, 0, 2, 1 + cacheLines)
          // Equal transaction IDs across RNs, and either local result may finish first.
          awaitResult(dut, monitor, 1, 2, dirtyData)
          awaitResult(dut, monitor, 0, 2, memory(1 + cacheLines))
          monitor.step(16)
          monitor.writes shouldBe 1
          monitor.cancellations should be <= 1
          totalCancellations += monitor.cancellations
          // Force a subsequent read from SN after RN1 drops its clean/shared copy.
          transact(dut, monitor, 1, 3, 1 + cacheLines, memory(1 + cacheLines))
          transact(dut, monitor, 0, 4, 1, dirtyData)
          monitor.writes shouldBe 1
        }
        withClue("the shared-read race did not exercise writeback cancellation: ") {
          totalCancellations should be > 0
        }
      }
    }
  }
}
