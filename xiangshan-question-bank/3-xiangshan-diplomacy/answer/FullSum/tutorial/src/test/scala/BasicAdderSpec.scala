package chapter_13

import chisel3._
import chisel3.simulator.scalatest.ChiselSim
import org.chipsalliance.cde.config.Parameters
import org.chipsalliance.diplomacy.lazymodule.LazyModule
import org.scalatest.freespec.AnyFreeSpec

class BasicAdderSpec extends AnyFreeSpec with ChiselSim {
  "the tutorial adder adds 32-bit inputs and truncates carry" in {
    implicit val p: Parameters = Parameters.empty
    simulateRaw(LazyModule(new BasicAdder).module) { dut =>
      val cases = Seq(
        (BigInt(0), BigInt(0)),
        (BigInt(12), BigInt(30)),
        (BigInt("ffffffff", 16), BigInt(0)),
        (BigInt("ffffffff", 16), BigInt(1)),
        (BigInt("ffffffff", 16), BigInt("ffffffff", 16))
      )
      cases.foreach { case (a, b) =>
        dut.in1.poke(a.U)
        dut.in2.poke(b.U)
        dut.out.expect(((a + b) & BigInt("ffffffff", 16)).U)
      }
    }
  }
}
