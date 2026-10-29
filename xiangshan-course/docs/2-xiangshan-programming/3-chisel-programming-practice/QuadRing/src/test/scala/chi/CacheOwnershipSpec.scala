package chi

import chisel3._
import chisel3.reflect.DataMirror
import chisel3.simulator.Exceptions.AssertionFailed
import circt.stage.ChiselStage
import noc.RingParams
import org.chipsalliance.cde.config.Parameters
import org.chipsalliance.diplomacy.lazymodule.LazyModule
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

class CacheOwnershipSpec extends AnyFreeSpec with Matchers with ChiNodeTestSupport {
  private implicit val p: Parameters = Parameters.empty
  private val memory = Seq.tabulate(8)(i => (BigInt(1) << 511) | BigInt(i))
  private val configs = Seq(
    ChiSystemParams(initialLines = memory, coherent = true, cacheLines = 2, directoryLines = 8),
    ChiSystemParams(ChiNoCParams(ChiParams(3, 7, 32), RingParams(4, 5)),
      6, 1, 4, 0, memory, coherent = true, cacheLines = 4, directoryLines = 8),
    ChiSystemParams(ChiNoCParams(ChiParams(2, 1, 8), RingParams(4, 2)),
      initialLines = memory.take(3), coherent = true, cacheLines = 2, directoryLines = 4)
  )

  private def clear(dut: CacheOwnershipMonitor): Unit = {
    for (entries <- dut.io; entry <- entries) {
      entry.valid.poke(false)
      entry.addr.poke(0.U)
      entry.state.poke(RnCacheState.I)
    }
  }

  private def set(entry: OwnershipEntry, address: BigInt, state: UInt): Unit = {
    entry.valid.poke(true)
    entry.addr.poke(address)
    entry.state.poke(state)
  }

  "monitor allows sharing, distinct tags, invalid entries and ownership transfer" in {
    simulate(new CacheOwnershipMonitor(32, 2)) { dut =>
      clear(dut)
      set(dut.io(0)(0), 0, RnCacheState.SC)
      set(dut.io(1)(0), 0, RnCacheState.SC)
      dut.clock.step()
      set(dut.io(0)(0), 0, RnCacheState.UC)
      set(dut.io(1)(0), 128, RnCacheState.UD)
      dut.clock.step() // Same index, different tags.
      dut.io(0)(0).valid.poke(false)
      set(dut.io(1)(0), 0, RnCacheState.UD)
      dut.clock.step()
      dut.io(1)(0).valid.poke(false)
      set(dut.io(0)(2), 0, RnCacheState.UD)
      dut.clock.step() // Dirty ownership can reside only in a writeback buffer.
      dut.io(0)(2).valid.poke(false)
      set(dut.io(1)(0), 0, RnCacheState.UC)
      dut.clock.step()
    }
  }

  for ((firstSlot, firstState, secondSlot, secondState) <- Seq(
    (0, RnCacheState.UC, 0, RnCacheState.UD),
    (0, RnCacheState.UD, 0, RnCacheState.SC),
    (2, RnCacheState.UD, 0, RnCacheState.UC),
    (0, RnCacheState.SC, 2, RnCacheState.UD),
    (2, RnCacheState.UD, 2, RnCacheState.UD))) {
    s"monitor rejects cross-RN conflict $firstSlot/$firstState vs $secondSlot/$secondState" in {
      val error = intercept[AssertionFailed] {
        simulate(new CacheOwnershipMonitor(32, 2)) { dut =>
          clear(dut)
          set(dut.io(0)(firstSlot), 0, firstState)
          set(dut.io(1)(secondSlot), 0, secondState)
          dut.clock.step()
        }
      }
      error.getMessage should include("conflicting ownership across RNs")
    }
  }

  "monitor rejects a cache line and dirty victim duplicated in one RN" in {
    val error = intercept[AssertionFailed] {
      simulate(new CacheOwnershipMonitor(32, 2)) { dut =>
        clear(dut)
        set(dut.io(0)(0), 0, RnCacheState.SC)
        set(dut.io(0)(2), 0, RnCacheState.UD)
        dut.clock.step()
      }
    }
    error.getMessage should include("RN holds duplicate ownership")
  }

  for ((address, state, message) <- Seq(
    (BigInt(0), RnCacheState.I, "invalid state"),
    (BigInt(1), RnCacheState.UC, "not line aligned"))) {
    s"monitor rejects malformed valid entry at $address with state $state" in {
      val error = intercept[AssertionFailed] {
        simulate(new CacheOwnershipMonitor(32, 2)) { dut =>
          clear(dut)
          set(dut.io(0)(0), address, state)
          dut.clock.step()
        }
      }
      error.getMessage should include(message)
    }
  }

  "observation is optional and keeps the product system IO unchanged" in {
    val error = intercept[IllegalArgumentException] {
      LazyModule(new OwnershipCheckedSystem(ChiSystemParams()))
    }
    error.getMessage should include("ownership checks require coherent mode")
    for (config <- configs) {
      val top = LazyModule(new ChiSystem(config))
      ChiselStage.emitCHIRRTL(top.module)
      for (module <- Seq(top.module, top.rn0.module, top.rn1.module)) {
        DataMirror.modulePorts(module).map(_._1).toSet shouldBe Set("clock", "reset", "auto", "io")
      }
    }
  }

  for (config <- configs) {
    s"real RN registers expose resident and victim ownership through reset with ${config.network.chi}" in {
      val top = LazyModule(new OwnershipCheckedSystem(config))
      simulate(top.module) { dut =>
        val ports = Seq(dut.io.rn0, dut.io.rn1)
        val ownership = top.ownership.getWrappedValue
        ports.foreach(initialize)
        top.network.node.edges.in.map(_.endpoint.nodeId) shouldBe
          Seq(config.rn0Id, config.hnId, config.rn1Id, config.snId)
        ownership.size shouldBe 2
        ownership.head.size shouldBe config.cacheLines + 1
        DataMirror.directionOf(ownership.head.head.valid) shouldBe ActualDirection.Output
        ownership.head.head.addr.getWidth shouldBe config.network.chi.addrWidth

        def transact(rn: Int, line: Int, expected: BigInt, write: Boolean = false): Unit = {
          val port = ports(rn)
          if (write) localWriteRequest(port, 1, BigInt(line * 64), expected)
          else localRequest(port, 1, BigInt(line * 64))
          port.request.ready.expect(true.B)
          dut.clock.step()
          port.request.valid.poke(false)
          var cycles = 0
          while (!port.response.valid.peekBoolean() && cycles < 256) {
            dut.clock.step()
            cycles += 1
          }
          port.response.valid.expect(true.B)
          port.response.bits.txnID.expect(1.U)
          port.response.bits.data.expect((if (write) BigInt(0) else expected).U)
          port.response.ready.poke(true)
          dut.clock.step()
          port.response.ready.poke(false)
        }

        transact(0, 0, memory.head)
        ownership(0)(0).valid.expect(true.B)
        ownership(0)(0).addr.expect(0.U)
        ownership(0)(0).state.expect(RnCacheState.UC)
        transact(1, 0, memory.head)
        ownership.foreach(_(0).state.expect(RnCacheState.SC))
        val dirty = (BigInt(1) << 510) | BigInt(0xfedcba98L)
        transact(1, 0, dirty, write = true)
        ownership(0)(0).valid.expect(false.B)
        ownership(1)(0).state.expect(RnCacheState.UD)
        transact(0, 0, dirty, write = true)
        ownership(0)(0).state.expect(RnCacheState.UD)
        ownership(1)(0).valid.expect(false.B)

        // Capture the real dirty victim immediately after moving it out of the cache.
        localRequest(ports(0), 1, BigInt(config.cacheLines * 64))
        ports(0).request.ready.expect(true.B)
        dut.clock.step()
        ports(0).request.valid.poke(false)
        ownership(0)(0).valid.expect(false.B)
        ownership(0)(config.cacheLines).valid.expect(true.B)
        ownership(0)(config.cacheLines).addr.expect(0.U)
        dut.clock.step(128)
        ports(0).response.valid.expect(true.B)
        ports(0).response.bits.data.expect(memory(config.cacheLines).U)
        ownership(0)(config.cacheLines).valid.expect(false.B)
        ownership(0)(0).addr.expect((config.cacheLines * 64).U)
        ownership(0)(0).state.expect(RnCacheState.UC)
        ports(0).response.ready.poke(true)
        dut.clock.step()
        ports(0).response.ready.poke(false)
        transact(1, 0, dirty)

        dut.reset.poke(true.B)
        dut.clock.step(2)
        dut.reset.poke(false.B)
        ownership.foreach(_.foreach(_.valid.expect(false.B)))
        transact(0, 0, memory.head)
        dut.clock.step(16)
        ports.foreach(_.response.valid.expect(false.B))
      }
    }
  }
}
