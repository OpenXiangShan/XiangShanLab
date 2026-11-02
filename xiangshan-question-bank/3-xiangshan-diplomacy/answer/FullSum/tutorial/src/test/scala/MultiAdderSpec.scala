package chapter_13

import chisel3._
import chisel3.simulator.scalatest.ChiselSim
import org.chipsalliance.cde.config.Parameters
import org.chipsalliance.diplomacy.lazymodule.LazyModule
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

class MultiAdderSpec extends AnyFreeSpec with Matchers with ChiselSim {
  "the nexus sums five inputs and broadcasts the truncated sum to three outputs" in {
    implicit val p: Parameters = Parameters.empty
    simulateRaw(LazyModule(new MultiAdderTop).module) { dut =>
      dut.inputs.size shouldBe 5
      dut.outputs.size shouldBe 3
      dut.inputs.foreach(_.getWidth shouldBe 32)
      dut.outputs.foreach(_.getWidth shouldBe 32)
      val max = (BigInt(1) << 32) - 1
      val vectors = Seq(
        Seq.fill(5)(BigInt(0)),
        Seq[BigInt](1, 2, 3, 4, 5),
        Seq(max, BigInt(1), BigInt(0), BigInt(0), BigInt(0)),
        Seq.fill(5)(max)
      )
      vectors.foreach { values =>
        dut.inputs.zip(values).foreach { case (port, value) => port.poke(value.U) }
        dut.outputs.foreach(_.expect((values.sum & max).U))
      }
    }
  }
}
