package minixiangshan.frontend.icache

import chisel3._
import chiseltest._
import minixiangshan.config._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class ICacheCancelSpec
    extends AnyFlatSpec
    with ChiselScalatestTester
    with Matchers {
  behavior of "ICache redirect cancellation"

  private implicit val p: Parameters =
    new Parameters(Map(DebugConfigKeys.EnableDifftest -> true))

  private def idleInputs(dut: ICacheMainPipe): Unit = {
    dut.io.redirect.poke(false.B)
    dut.io.cpu_req.valid.poke(false.B)
    dut.io.cpu_req.bits.addr.poke(0.U)
    dut.io.icache_resp.ready.poke(true.B)
    dut.io.arrays_read.resp.valid.poke(false.B)
    for (way <- 0 until 4) {
      dut.io.arrays_read.resp.data.cacheLine(way).has.poke(false.B)
      dut.io.arrays_read.resp.data.cacheLine(way).tag.poke(0.U)
      dut.io.arrays_read.resp.data.cacheLine(way).data.poke(0.U)
    }
    dut.io.victim_read.resp.poke(0.U)
    dut.io.mmu.toMmu.ready.poke(true.B)
    dut.io.mmu.fromMmu.valid.poke(false.B)
    dut.io.mmu.fromMmu.bits.paddr.poke(0.U)
    dut.io.mmu.fromMmu.bits.cacheable.poke(false.B)
    dut.io.mmu.fromMmu.bits.hasError.poke(false.B)
    dut.io.mmu.fromMmu.bits.error.excpTlbRefill.poke(false.B)
    dut.io.mmu.fromMmu.bits.error.excpTlbPif.poke(false.B)
    dut.io.mmu.fromMmu.bits.error.excpTlbPpi.poke(false.B)
    dut.io.mmu.fromMmu.bits.error.excpAdef.poke(false.B)
    dut.io.mmu.fromMmu.bits.error.excpAle.poke(false.B)
    dut.io.l2_read.req.ready.poke(false.B)
    dut.io.l2_read.resp.valid.poke(false.B)
    dut.io.l2_read.resp.bits.id.poke(0.U)
    dut.io.l2_read.resp.bits.data.poke(0.U)
    dut.io.l2_read.resp.bits.fullLine.poke(false.B)
    dut.io.l2_read.resp.bits.last.poke(false.B)
  }

  private def resetDut(dut: ICacheMainPipe): Unit = {
    idleInputs(dut)
    dut.reset.poke(true.B)
    dut.clock.step(2)
    dut.reset.poke(false.B)
  }

  private def launchCachedMiss(dut: ICacheMainPipe): Unit = {
    dut.io.cpu_req.valid.poke(true.B)
    dut.io.cpu_req.bits.addr.poke(0x80000400L.U)
    dut.io.cpu_req.ready.expect(true.B)
    dut.clock.step()
    dut.io.cpu_req.valid.poke(false.B)
    dut.clock.step()
    dut.io.mmu.fromMmu.valid.poke(true.B)
    dut.io.mmu.fromMmu.bits.paddr.poke(0x90000400L.U)
    dut.io.mmu.fromMmu.bits.cacheable.poke(true.B)
    dut.io.arrays_read.resp.valid.poke(true.B)
    dut.clock.step()
    dut.io.mmu.fromMmu.valid.poke(false.B)
    dut.io.arrays_read.resp.valid.poke(false.B)
    dut.clock.step(2)
    dut.io.l2_read.req.valid.expect(true.B)
  }

  it should "leave an accepted wrong-path read immediately without draining it" in {
    test(new ICacheMainPipe) { dut =>
      resetDut(dut)
      launchCachedMiss(dut)
      dut.io.l2_read.req.ready.poke(true.B)
      dut.clock.step()
      dut.io.l2_read.req.ready.poke(false.B)

      dut.io.redirect.poke(true.B)
      dut.clock.step()
      dut.io.redirect.poke(false.B)

      // The old implementation remains in s_drain_miss and drives ready high.
      // The new ownership contract returns idle without consuming the stale beat.
      dut.io.l2_read.resp.valid.poke(true.B)
      dut.io.l2_read.resp.bits.last.poke(true.B)
      dut.io.l2_read.resp.ready.expect(false.B)
      dut.io.array_write.valid.expect(false.B)
      dut.io.icache_resp.valid.expect(false.B)
    }
  }
  it should "cancel an accepted full-line beat on the redirect cycle" in {
    test(new ICacheMainPipe) { dut =>
      resetDut(dut)
      launchCachedMiss(dut)
      dut.io.l2_read.req.ready.poke(true.B)
      dut.clock.step()
      dut.io.l2_read.req.ready.poke(false.B)

      dut.io.redirect.poke(true.B)
      dut.io.l2_read.resp.valid.poke(true.B)
      dut.io.l2_read.resp.bits.id.poke(0.U)
      dut.io.l2_read.resp.bits.data.poke(BigInt("5a" * 64, 16).U)
      dut.io.l2_read.resp.bits.fullLine.poke(true.B)
      dut.io.l2_read.resp.bits.last.poke(true.B)
      dut.io.l2_read.cancel.expect(true.B)
      dut.io.l2_read.resp.ready.expect(false.B)
      dut.io.array_write.valid.expect(false.B)
      dut.io.icache_resp.valid.expect(false.B)
      dut.clock.step()

      dut.io.redirect.poke(false.B)
      dut.io.l2_read.resp.valid.poke(false.B)
      dut.io.array_write.valid.expect(false.B)
      dut.io.icache_resp.valid.expect(false.B)
    }
  }

  it should "withdraw a not-yet-accepted request without sending cancel" in {
    test(new ICacheMainPipe) { dut =>
      resetDut(dut)
      launchCachedMiss(dut)
      dut.io.redirect.poke(true.B)
      dut.io.l2_read.req.valid.expect(false.B)
      dut.io.l2_read.cancel.expect(false.B)
      dut.clock.step()
      dut.io.redirect.poke(false.B)
      dut.io.l2_read.req.valid.expect(false.B)
      dut.io.l2_read.resp.ready.expect(false.B)
    }
  }
  it should "serve a redirected L1 hit without waiting for the stale L2 response" in {
    test(new ICacheMainPipe) { dut =>
      resetDut(dut)
      launchCachedMiss(dut)
      dut.io.l2_read.req.ready.poke(true.B)
      dut.clock.step()
      dut.io.l2_read.req.ready.poke(false.B)
      dut.io.redirect.poke(true.B)
      dut.clock.step()
      dut.io.redirect.poke(false.B)

      val targetVaddr = BigInt("80000820", 16)
      val targetPaddr = BigInt("90000820", 16)
      val targetLine = (0 until 16).foldLeft(BigInt(0)) { (line, word) =>
        line | (BigInt(0x3000 + word) << (word * 32))
      }

      dut.io.cpu_req.valid.poke(true.B)
      dut.io.cpu_req.bits.addr.poke(targetVaddr.U)
      dut.io.cpu_req.ready.expect(true.B)
      dut.clock.step()
      dut.io.cpu_req.valid.poke(false.B)
      dut.io.mmu.toMmu.valid.expect(true.B)
      dut.io.arrays_read.req.valid.expect(true.B)
      dut.clock.step()

      dut.io.mmu.fromMmu.valid.poke(true.B)
      dut.io.mmu.fromMmu.bits.paddr.poke(targetPaddr.U)
      dut.io.mmu.fromMmu.bits.cacheable.poke(true.B)
      dut.io.arrays_read.resp.valid.poke(true.B)
      dut.io.arrays_read.resp.data.cacheLine(0).has.poke(true.B)
      dut.io.arrays_read.resp.data.cacheLine(0).tag.poke((targetPaddr >> 12).U)
      dut.io.arrays_read.resp.data.cacheLine(0).data.poke(targetLine.U)
      dut.clock.step()
      dut.io.mmu.fromMmu.valid.poke(false.B)
      dut.io.arrays_read.resp.valid.poke(false.B)

      // No response is ever provided for the cancelled line.
      dut.clock.step()
      dut.io.icache_resp.valid.expect(true.B)
      dut.io.icache_resp.bits.addr.expect(targetVaddr.U)
      dut.io.icache_resp.bits.instrs(0).expect(0x3008.U)
      dut.io.array_write.valid.expect(false.B)
    }
  }
}
