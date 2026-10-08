package minixiangshan.mem.L2cache

import chisel3.stage.ChiselStage
import minixiangshan.config._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class L2WaysParameterSpec extends AnyFlatSpec with Matchers {
  behavior of "L2 ways parameter"

  private def params(ways: Int, mode: Int): Parameters = new Parameters(Map(
    DebugConfigKeys.EnableDifftest -> true,
    CoreConfigKeys.L2Ways -> ways,
    CoreConfigKeys.L2Prefetch -> mode))

  private val modes = Seq(
    L2PrefetchMode.Disabled -> "disabled",
    L2PrefetchMode.IOnly -> "I-only",
    L2PrefetchMode.DOnly -> "D-only",
    L2PrefetchMode.Hybrid -> "hybrid"
  )

  for {
    ways <- Seq(2, 4, 8, 16)
    (mode, modeName) <- modes
  } {
    it should s"elaborate the complete $ways-way L2 cache in $modeName mode" in {
      implicit val p: Parameters = params(ways, mode)
      val cacheVerilog = ChiselStage.emitSystemVerilog(new L2Cache)
      cacheVerilog should include ("module L2Cache")
      if (mode == L2PrefetchMode.Disabled) {
        cacheVerilog should not include "module L2PrefetchHub"
      } else {
        cacheVerilog should include ("module L2PrefetchHub")
      }
    }
  }
}
