package minixiangshan.mem.L2cache

import chisel3._
import chiseltest._
import minixiangshan.config._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class L2MshrCountProbe(implicit p: Parameters) extends NSModule {
  val io = IO(new Bundle {
    val count = Output(UInt(3.W))
  })
  io.count := l2MshrEntries.U
}

class L2MshrParameterSpec
    extends AnyFlatSpec
    with ChiselScalatestTester
    with Matchers {
  behavior of "L2 MSHR capacity"

  private implicit val p: Parameters = new Parameters(Map(
    DebugConfigKeys.EnableDifftest -> true,
    CoreConfigKeys.L2Ways -> 4,
    CoreConfigKeys.L2Prefetch -> L2PrefetchMode.Disabled))

  it should "expose exactly three miss status slots" in {
    test(new L2MshrCountProbe) { dut =>
      dut.io.count.expect(3.U)
    }
  }
}
