package minixiangshan.frontend.icache

import chisel3._
import chiseltest._
import minixiangshan.config._
import org.scalatest.flatspec.AnyFlatSpec

class ICacheUncacheCancelSpec extends AnyFlatSpec with ChiselScalatestTester {
  private implicit val p: Parameters =
    new Parameters(Map(DebugConfigKeys.EnableDifftest -> true))

  it should "cancel an accepted uncache read and return idle immediately" in {
    test(new ICacheMainPipe) { dut =>
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
      dut.reset.poke(true.B)
      dut.clock.step(2)
      dut.reset.poke(false.B)

      dut.io.cpu_req.valid.poke(true.B)
      dut.io.cpu_req.bits.addr.poke(0x1c00102cL.U)
      dut.clock.step()
      dut.io.cpu_req.valid.poke(false.B)
      dut.clock.step()
      dut.io.mmu.fromMmu.valid.poke(true.B)
      dut.io.mmu.fromMmu.bits.paddr.poke(0x1d00102cL.U)
      dut.io.mmu.fromMmu.bits.cacheable.poke(false.B)
      dut.io.arrays_read.resp.valid.poke(true.B)
      dut.clock.step()
      dut.io.mmu.fromMmu.valid.poke(false.B)
      dut.io.arrays_read.resp.valid.poke(false.B)
      dut.clock.step(2)
      dut.io.l2_read.req.valid.expect(true.B)
      dut.io.l2_read.req.bits.uncache.expect(true.B)
      dut.io.l2_read.req.ready.poke(true.B)
      dut.clock.step()
      dut.io.l2_read.req.ready.poke(false.B)

      dut.io.redirect.poke(true.B)
      dut.io.l2_read.cancel.expect(true.B)
      dut.clock.step()
      dut.io.redirect.poke(false.B)
      dut.io.l2_read.cancel.expect(false.B)
      dut.io.l2_read.resp.ready.expect(false.B)
      dut.io.array_write.valid.expect(false.B)
      dut.io.icache_resp.valid.expect(false.B)
    }
  }
}
