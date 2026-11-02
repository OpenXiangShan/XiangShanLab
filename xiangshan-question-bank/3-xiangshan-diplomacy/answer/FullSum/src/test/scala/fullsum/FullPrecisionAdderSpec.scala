package fullsum

import chisel3._
import chisel3.simulator.scalatest.ChiselSim
import circt.stage.ChiselStage
import org.chipsalliance.cde.config.Parameters
import org.chipsalliance.diplomacy.lazymodule.LazyModule
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import scala.util.Random

class FullPrecisionAdderSpec extends AnyFreeSpec with Matchers with ChiselSim {
  import AdderTestGraph._
  implicit private val p: Parameters = Parameters.empty

  private def exhaustive(widths: Seq[Int]): Seq[Seq[BigInt]] =
    widths.foldLeft(Seq(Seq.empty[BigInt])) { (prefixes, width) =>
      for (prefix <- prefixes; value <- 0 until (1 << width)) yield prefix :+ BigInt(value)
    }

  private def verify(widths: Seq[Int], tree: Tree, vectors: Seq[Seq[BigInt]],
    capacities: Seq[Option[Int]] = Seq(None)): Unit = {
    simulateRaw(LazyModule(new AdderTestGraph(widths, tree, capacities)).module) { dut =>
      dut.inputs.map(_.getWidth) shouldBe widths
      val max = widths.map(w => (BigInt(1) << w) - 1).sum
      dut.outputs.foreach(_.getWidth shouldBe max.bitLength.max(1))
      dut.resultEdges.foreach(_.maxValue shouldBe max)
      vectors.foreach { values =>
        dut.inputs.zip(values).foreach { case (port, value) => port.poke(value.U) }
        withClue(s"widths=$widths inputs=$values: ") {
          dut.outputs.foreach(_.expect(values.sum.U))
        }
      }
    }
  }

  for (widths <- Seq(Seq(1, 1), Seq(2, 3), Seq(1, 1, 3), Seq(3))) {
    s"compute every sum for $widths" in {
      verify(widths, flat(widths.size), exhaustive(widths))
    }
  }

  "cascade and broadcast preserve the exact reachable range" in {
    val widths = Seq(1, 1, 1)
    verify(widths, Add(Seq(Add(Seq(Input(0), Input(1))), Input(2))),
      exhaustive(widths), Seq(None, Some(2), Some(12)))
  }

  "wide heterogeneous inputs preserve carries" in {
    val widths = Seq(1, 65, 129, 17)
    val random = new Random(0x46554c4c53554dL)
    val maxima = widths.map(w => (BigInt(1) << w) - 1)
    val vectors = Seq(widths.map(_ => BigInt(0)), maxima) ++
      (1 until 129).map(bit => Seq(BigInt(1), BigInt(0), (BigInt(1) << bit) - 1, BigInt(0))) ++
      Seq.fill(256)(widths.map(w => BigInt(w, random)))
    verify(widths, flat(widths.size), vectors)
  }

  "invalid parameters and insufficient sink capacities are rejected" in {
    intercept[IllegalArgumentException](SumSourceParameters.fromWidth(0))
    intercept[IllegalArgumentException](SumSourceParameters(-1))
    intercept[IllegalArgumentException](SumSinkParameters(Some(0)))
    val error = intercept[Exception] {
      ChiselStage.emitCHIRRTL(LazyModule(new AdderTestGraph(Seq(8, 8), flat(2), Seq(Some(8)))).module)
    }
    val causes = Iterator.iterate[Throwable](error)(_.getCause).takeWhile(_ != null).toSeq
    causes.exists(e => e.isInstanceOf[IllegalArgumentException] &&
      Option(e.getMessage).exists(m => m.contains("9") && m.contains("8"))) shouldBe true
  }

  for ((widths, capacities) <- Seq(
    (Seq.empty[Int], Seq(None)),
    (Seq(8, 8), Seq.empty[Option[Int]]),
    (Seq.empty[Int], Seq.empty[Option[Int]])
  )) {
    s"reject disconnected graph inputs=$widths outputs=$capacities" in {
      intercept[IllegalArgumentException] {
        ChiselStage.emitCHIRRTL(LazyModule(new AdderTestGraph(widths, flat(widths.size), capacities)).module)
      }
    }
  }
}
