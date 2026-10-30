package fullsum

import chisel3._
import chisel3.simulator.scalatest.ChiselSim
import circt.stage.ChiselStage
import java.io.{ByteArrayOutputStream, PrintStream}
import org.chipsalliance.cde.config.Parameters
import org.chipsalliance.diplomacy.lazymodule.LazyModule
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

class FullPrecisionAdderTopSpec extends AnyFreeSpec with Matchers with ChiselSim {
  implicit private val p: Parameters = Parameters.empty

  "default configuration produces two 10-bit outputs containing 765" in {
    simulateRaw(LazyModule(new FullPrecisionAdderTop()).module) { dut =>
      dut.inputs.map(_.getWidth) shouldBe Seq(8, 8, 8)
      dut.outputs.map(_.getWidth) shouldBe Seq(10, 10)
      dut.inputs.foreach(_.poke(255.U))
      dut.outputs.foreach(_.expect(765.U))
    }
  }

  "mixed input widths negotiate a 9-bit result" in {
    simulateRaw(LazyModule(new FullPrecisionAdderTop(Seq(1, 8), Seq(9, 16))).module) { dut =>
      dut.inputs.map(_.getWidth) shouldBe Seq(1, 8)
      dut.outputs.map(_.getWidth) shouldBe Seq(9, 9)
      dut.inputs(0).poke(1.U)
      dut.inputs(1).poke(255.U)
      dut.outputs.foreach(_.expect(256.U))
    }
  }

  "a single wide input passes through without losing precision" in {
    val value = (BigInt(1) << 129) - 1
    simulateRaw(LazyModule(new FullPrecisionAdderTop(Seq(129), Seq(129, 160))).module) { dut =>
      dut.inputs.map(_.getWidth) shouldBe Seq(129)
      dut.outputs.map(_.getWidth) shouldBe Seq(129, 129)
      dut.inputs.head.poke(value.U)
      dut.outputs.foreach(_.expect(value.U))
    }
  }

  "elaboration names ports and reports the negotiated configuration" in {
    val log = new ByteArrayOutputStream()
    val stream = new PrintStream(log)
    val chirrtl = try {
      Console.withErr(stream) {
        ChiselStage.emitCHIRRTL(LazyModule(new FullPrecisionAdderTop()).module)
      }
    } finally stream.close()
    for (index <- 0 until 3) chirrtl should include (s"input in_$index : UInt<8>")
    for (index <- 0 until 2) chirrtl should include (s"output out_$index : UInt<10>")
    val message = log.toString("UTF-8")
    message should include ("sourceWidths=[8,8,8]")
    message should include ("sinkMaxWidths=[10,16]")
    message should include ("maxSum=765, sumWidth=10")
  }

  "a narrow sink reports its index, required width and capacity" in {
    val error = intercept[Exception] {
      ChiselStage.emitCHIRRTL(LazyModule(new FullPrecisionAdderTop(Seq(8, 8, 8), Seq(10, 9))).module)
    }
    val messages = Iterator.iterate[Throwable](error)(_.getCause)
      .takeWhile(_ != null).flatMap(e => Option(e.getMessage)).mkString("\n")
    messages should include ("sink_1")
    messages should include ("10 bits")
    messages should include ("9 bits")
  }

  "invalid configurations are rejected without corrupting the next elaboration" in {
    val invalid = Seq(
      (Seq.empty[Int], Seq(10)),
      (Seq(8, 0), Seq(10)),
      (Seq(8, -1), Seq(10)),
      (Seq(8), Seq.empty[Int]),
      (Seq(8), Seq(0)),
      (Seq(8), Seq(-1))
    )
    invalid.foreach { case (sources, sinks) =>
      intercept[IllegalArgumentException](LazyModule(new FullPrecisionAdderTop(sources, sinks)))
      LazyModule.getScope shouldBe None
    }
    val chirrtl = ChiselStage.emitCHIRRTL(LazyModule(new FullPrecisionAdderTop()).module)
    chirrtl should include ("UInt<10>")
  }
}
