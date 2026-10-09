package minixiangshan.mem.dcache

import chisel3.stage.ChiselStage
import minixiangshan.config._
import org.scalatest.flatspec.AnyFlatSpec

class DCacheNativeElaborationSpec extends AnyFlatSpec {
  behavior of "DCache native L2 integration"

  private implicit val p: Parameters =
    new Parameters(Map(DebugConfigKeys.EnableDifftest -> true))

  it should "elaborate the DCache and native MSHR hierarchy" in {
    ChiselStage.emitSystemVerilog(new DCache)
  }
}
