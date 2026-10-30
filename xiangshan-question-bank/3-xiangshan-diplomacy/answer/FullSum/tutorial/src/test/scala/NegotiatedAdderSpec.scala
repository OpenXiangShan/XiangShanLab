package chapter_13

import chisel3._
import chisel3.simulator.scalatest.ChiselSim
import org.chipsalliance.cde.config.Parameters
import org.chipsalliance.diplomacy.lazymodule.LazyModule
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

class NegotiatedAdderSpec extends AnyFreeSpec with Matchers with ChiselSim {
  "negotiates widths on every edge and broadcasts the sum" in {
    implicit val p: Parameters = Parameters.empty
    simulateRaw(LazyModule(new NegotiatedAdderTop).module) { dut =>
      dut.inputs.map(_.getWidth) shouldBe Seq(8, 16, 32, 32, 32)
      dut.outputs.map(_.getWidth) shouldBe Seq(16, 32, 32)

      val values = Seq[BigInt](1, 2, 3, 4, 5)
      dut.inputs.zip(values).foreach { case (port, value) => port.poke(value.U) }
      dut.outputs.foreach(_.expect(values.sum.U))
    }
  }
}
