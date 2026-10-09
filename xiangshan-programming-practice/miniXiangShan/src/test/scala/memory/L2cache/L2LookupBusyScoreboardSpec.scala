package minixiangshan.mem.L2cache

import chisel3._
import chiseltest._
import minixiangshan.config._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class L2LookupBusyScoreboardSpec
    extends AnyFlatSpec
    with ChiselScalatestTester
    with Matchers {
  behavior of "L2 lookup busy scoreboard"

  private implicit val p: Parameters = new Parameters(Map(
    DebugConfigKeys.EnableDifftest -> true,
    CoreConfigKeys.L2Ways -> 4,
    CoreConfigKeys.L2Prefetch -> L2PrefetchMode.Disabled))

  private def initialize(dut: L2LookupBusyScoreboard): Unit = {
    dut.io.allocate.valid.poke(false.B)
    dut.io.allocate.bits.poke(0.U)
    dut.io.release.valid.poke(false.B)
    dut.io.release.bits.poke(0.U)
    dut.io.querySet.foreach(_.poke(0.U))
    dut.reset.poke(true.B)
    dut.clock.step()
    dut.reset.poke(false.B)
  }

  it should "lock four set indices and release the exact completed lookup slot" in {
    test(new L2LookupBusyScoreboard(queryPorts = 2)) { dut =>
      initialize(dut)

      val sets = Seq(12, 23, 34, 45)
      val slots = sets.map { set =>
        val slot = dut.io.allocateSlot.peek().litValue.toInt
        dut.io.allocate.bits.poke(set.U)
        dut.io.allocate.valid.poke(true.B)
        dut.clock.step()
        dut.io.allocate.valid.poke(false.B)
        slot
      }

      slots.distinct.size shouldBe 4
      dut.io.anyBusy.expect(true.B)
      dut.io.hasFree.expect(false.B)
      dut.io.querySet(0).poke(23.U)
      dut.io.querySet(1).poke(24.U)
      dut.io.queryBusy(0).expect(true.B)
      dut.io.queryBusy(1).expect(false.B)

      dut.io.release.bits.poke(slots(1).U)
      dut.io.release.valid.poke(true.B)
      dut.clock.step()
      dut.io.release.valid.poke(false.B)
      dut.io.queryBusy(0).expect(false.B)
      dut.io.hasFree.expect(true.B)

      dut.io.allocateSlot.expect(slots(1).U)
      dut.io.allocate.bits.poke(99.U)
      dut.io.allocate.valid.poke(true.B)
      dut.clock.step()
      dut.io.allocate.valid.poke(false.B)
      dut.io.querySet(0).poke(99.U)
      dut.io.queryBusy(0).expect(true.B)

      dut.reset.poke(true.B)
      dut.clock.step()
      dut.reset.poke(false.B)
      dut.io.anyBusy.expect(false.B)
      dut.io.hasFree.expect(true.B)
    }
  }
}
