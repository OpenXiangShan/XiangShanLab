package minixiangshan.frontend.icache

import chisel3._
import chiseltest._
import minixiangshan.config._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class ICacheNativeReadSpec
    extends AnyFlatSpec
    with ChiselScalatestTester
    with Matchers {
  behavior of "ICache native L2 read path"

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

  private def launchRead(
      dut: ICacheMainPipe,
      vaddr: BigInt,
      paddr: BigInt,
      cacheable: Boolean
  ): Unit = {
    dut.io.cpu_req.valid.poke(true.B)
    dut.io.cpu_req.bits.addr.poke(vaddr.U)
    dut.io.cpu_req.ready.expect(true.B)
    dut.clock.step()
    dut.io.cpu_req.valid.poke(false.B)

    dut.io.mmu.toMmu.valid.expect(true.B)
    dut.io.mmu.toMmu.bits.vaddr.expect(vaddr.U)
    dut.io.arrays_read.req.valid.expect(true.B)
    dut.clock.step()

    dut.io.mmu.fromMmu.valid.poke(true.B)
    dut.io.mmu.fromMmu.bits.paddr.poke(paddr.U)
    dut.io.mmu.fromMmu.bits.cacheable.poke(cacheable.B)
    dut.io.arrays_read.resp.valid.poke(true.B)
    dut.clock.step()
    dut.io.mmu.fromMmu.valid.poke(false.B)
    dut.io.arrays_read.resp.valid.poke(false.B)

    dut.clock.step(2)
    dut.io.l2_read.req.valid.expect(true.B)
  }

  private def lineFromWords(words: Seq[BigInt]): BigInt =
    words.zipWithIndex.foldLeft(BigInt(0)) { case (line, (word, index)) =>
      line | ((word & BigInt("ffffffff", 16)) << (index * 32))
    }

  it should "hold an aligned cached request until ready and accept one full-line response" in {
    test(new ICacheMainPipe) { dut =>
      resetDut(dut)
      val words = (0 until 16).map(i => BigInt(0x1000 + i))
      val line = lineFromWords(words)

      launchRead(dut, vaddr = 0x80000124L, paddr = 0x90000124L, cacheable = true)
      dut.io.l2_read.req.bits.id.expect(0.U)
      dut.io.l2_read.req.bits.addr.expect(0x90000100L.U)
      dut.io.l2_read.req.bits.size.expect(2.U)
      dut.io.l2_read.req.bits.uncache.expect(false.B)

      dut.clock.step(3)
      dut.io.l2_read.req.valid.expect(true.B)
      dut.io.l2_read.req.bits.addr.expect(0x90000100L.U)
      dut.io.l2_read.req.bits.size.expect(2.U)

      dut.io.l2_read.req.ready.poke(true.B)
      dut.clock.step()
      dut.io.l2_read.req.valid.expect(false.B)

      dut.io.l2_read.resp.valid.poke(true.B)
      dut.io.l2_read.resp.bits.id.poke(0.U)
      dut.io.l2_read.resp.bits.data.poke(line.U)
      dut.io.l2_read.resp.bits.fullLine.poke(true.B)
      dut.io.l2_read.resp.bits.last.poke(true.B)
      dut.io.l2_read.resp.ready.expect(true.B)
      dut.clock.step()

      dut.io.l2_read.resp.valid.poke(false.B)
      dut.io.array_write.valid.expect(true.B)
      dut.io.array_write.data.expect(line.U)
      dut.clock.step()
      dut.io.array_write.valid.expect(false.B)
      dut.io.icache_resp.valid.expect(true.B)
      for (i <- 0 until 4) {
        dut.io.icache_resp.bits.instrs(i).expect(words(9 + i).U)
      }
    }
  }

  it should "assemble sixteen low words and stop only on the final beat" in {
    test(new ICacheMainPipe) { dut =>
      resetDut(dut)
      val words = (0 until 16).map(i => BigInt(0x2000 + i))
      val line = lineFromWords(words)

      launchRead(dut, vaddr = 0x80000200L, paddr = 0x90000200L, cacheable = true)
      dut.io.l2_read.req.ready.poke(true.B)
      dut.clock.step()

      for (beat <- 0 until 16) {
        dut.io.l2_read.resp.valid.poke(true.B)
        dut.io.l2_read.resp.bits.id.poke(0.U)
        dut.io.l2_read.resp.bits.data.poke(
          (words(beat) | (BigInt("deadbeef", 16) << 64)).U
        )
        dut.io.l2_read.resp.bits.fullLine.poke(false.B)
        dut.io.l2_read.resp.bits.last.poke((beat == 15).B)
        dut.io.l2_read.resp.ready.expect(true.B)
        dut.clock.step()
        if (beat < 15) {
          dut.io.array_write.valid.expect(false.B)
          dut.io.icache_resp.valid.expect(false.B)
        }
      }

      dut.io.l2_read.resp.valid.poke(false.B)
      dut.io.array_write.valid.expect(true.B)
      dut.io.array_write.data.expect(line.U)
    }
  }

  it should "preserve an uncache address and return only its low word" in {
    test(new ICacheMainPipe) { dut =>
      resetDut(dut)
      launchRead(dut, vaddr = 0x1c00102cL, paddr = 0x1d00102cL, cacheable = false)

      dut.io.l2_read.req.bits.addr.expect(0x1d00102cL.U)
      dut.io.l2_read.req.bits.size.expect(2.U)
      dut.io.l2_read.req.bits.uncache.expect(true.B)
      dut.io.l2_read.req.ready.poke(true.B)
      dut.clock.step()

      dut.io.l2_read.resp.valid.poke(true.B)
      dut.io.l2_read.resp.bits.id.poke(0.U)
      dut.io.l2_read.resp.bits.data.poke(
        (BigInt("12345678", 16) | (BigInt("abcdef01", 16) << 64)).U
      )
      dut.io.l2_read.resp.bits.fullLine.poke(false.B)
      dut.io.l2_read.resp.bits.last.poke(true.B)
      dut.clock.step()
      dut.io.l2_read.resp.valid.poke(false.B)

      dut.io.array_write.valid.expect(false.B)
      dut.io.icache_resp.valid.expect(true.B)
      dut.io.icache_resp.bits.uncached.expect(true.B)
      dut.io.icache_resp.bits.instrs(0).expect("h12345678".U)
    }
  }

  it should "withdraw a pending request and transfer an accepted request to L2 on redirect" in {
    test(new ICacheMainPipe) { dut =>
      resetDut(dut)
      launchRead(dut, vaddr = 0x80000300L, paddr = 0x90000300L, cacheable = true)

      dut.io.redirect.poke(true.B)
      dut.io.l2_read.req.valid.expect(false.B)
      dut.clock.step()
      dut.io.redirect.poke(false.B)
      dut.io.l2_read.req.valid.expect(false.B)
      dut.io.l2_read.resp.ready.expect(false.B)

      launchRead(dut, vaddr = 0x80000400L, paddr = 0x90000400L, cacheable = true)
      dut.io.l2_read.req.ready.poke(true.B)
      dut.clock.step()
      dut.io.l2_read.req.ready.poke(false.B)

      dut.io.l2_read.resp.valid.poke(true.B)
      dut.io.l2_read.resp.bits.id.poke(1.U)
      dut.io.l2_read.resp.bits.data.poke(BigInt("aa" * 64, 16).U)
      dut.io.l2_read.resp.bits.fullLine.poke(true.B)
      dut.io.l2_read.resp.bits.last.poke(true.B)
      dut.io.l2_read.resp.ready.expect(false.B)
      dut.clock.step()
      dut.io.array_write.valid.expect(false.B)

      dut.io.l2_read.resp.valid.poke(false.B)
      dut.io.redirect.poke(true.B)
      dut.io.l2_read.cancel.expect(true.B)
      dut.clock.step()
      dut.io.redirect.poke(false.B)
      dut.io.l2_read.cancel.expect(false.B)

      // L2 owns and drains the stale transaction after cancel; ICache is free.
      dut.io.l2_read.resp.valid.poke(true.B)
      dut.io.l2_read.resp.bits.id.poke(0.U)
      dut.io.l2_read.resp.ready.expect(false.B)
      for (_ <- 0 until 3) {
        dut.io.array_write.valid.expect(false.B)
        dut.io.icache_resp.valid.expect(false.B)
        dut.clock.step()
      }
    }
  }
}
