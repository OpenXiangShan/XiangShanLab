package minixiangshan.mem.L2cache

import chisel3._
import chiseltest._
import minixiangshan.config._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class L2ReplacerSpec extends AnyFlatSpec with ChiselScalatestTester with Matchers {
  behavior of "L2Replacer"

  private implicit val p: Parameters =
    new Parameters(Map(
      DebugConfigKeys.EnableDifftest -> true,
      CoreConfigKeys.L2Ways -> 8))

  private def resetDut(dut: L2Replacer): Unit = {
    dut.reset.poke(true.B)
    dut.io.lookup.set.poke(0.U)
    dut.io.lookup.validMask.poke("hff".U)
    dut.io.touch.valid.poke(false.B)
    dut.io.touch.bits.set.poke(0.U)
    dut.io.touch.bits.way.poke(0.U)
    dut.clock.step(2)
    dut.reset.poke(false.B)
  }

  private def touch(dut: L2Replacer, way: Int): Unit = {
    dut.io.touch.valid.poke(true.B)
    dut.io.touch.bits.set.poke(0.U)
    dut.io.touch.bits.way.poke(way.U)
    dut.clock.step()
    dut.io.touch.valid.poke(false.B)
  }

  it should "select the lowest invalid way before PLRU" in {
    test(new L2Replacer) { dut =>
      resetDut(dut)
      for (invalidWay <- 0 until 8) {
        dut.io.lookup.validMask.poke((0xff & ~(1 << invalidWay)).U)
        dut.io.victim.expect(invalidWay.U)
      }
      dut.io.lookup.validMask.poke("hf8".U)
      dut.io.victim.expect(0.U)
    }
  }

  it should "produce every victim way from hand-derived touch sequences" in {
    test(new L2Replacer) { dut =>
      val touchesForVictim = Seq(
        Seq(1, 2, 4),
        Seq(0, 2, 4),
        Seq(3, 0, 4),
        Seq(2, 0, 4),
        Seq(5, 6, 0),
        Seq(4, 6, 0),
        Seq(7, 4, 0),
        Seq(6, 4, 0)
      )

      for (victim <- 0 until 8) {
        resetDut(dut)
        dut.io.lookup.validMask.poke("hff".U)
        touchesForVictim(victim).foreach(touch(dut, _))
        dut.io.victim.expect(victim.U)
      }
    }
  }

  it should "update only the addressed set" in {
    test(new L2Replacer) { dut =>
      resetDut(dut)
      dut.io.lookup.validMask.poke("hff".U)
      dut.io.touch.valid.poke(true.B)
      dut.io.touch.bits.set.poke(7.U)
      dut.io.touch.bits.way.poke(0.U)
      dut.clock.step()
      dut.io.touch.valid.poke(false.B)
      dut.io.lookup.set.poke(0.U)
      dut.io.victim.expect(0.U)
      dut.io.lookup.set.poke(7.U)
      dut.io.victim.expect(4.U)
    }
  }
}
