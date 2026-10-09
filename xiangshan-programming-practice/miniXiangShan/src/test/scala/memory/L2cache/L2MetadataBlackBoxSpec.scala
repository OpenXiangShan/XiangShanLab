package minixiangshan.mem.L2cache

import chisel3.stage.ChiselStage
import minixiangshan.config._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class L2MetadataBlackBoxSpec extends AnyFlatSpec with Matchers {
  behavior of "L2CacheArray FPGA structure"

  it should "instantiate eight L2_meta_512x19 memories" in {
    implicit val p: Parameters =
      new Parameters(Map(
        DebugConfigKeys.EnableDifftest -> false,
        CoreConfigKeys.L2Ways -> 8))
    val verilog = ChiselStage.emitSystemVerilog(new L2CacheArray)

    verilog should include("L2_meta_512x19")
    val wrapperPattern = "(?m)^  L2MetadataRAM metadataRams_[0-7] \\(".r
    wrapperPattern.findAllMatchIn(verilog).length shouldBe 8
  }

  it should "keep both FPGA BRAM read enables asserted" in {
    implicit val p: Parameters =
      new Parameters(Map(
        DebugConfigKeys.EnableDifftest -> false,
        CoreConfigKeys.L2Ways -> 8))
    val verilog = ChiselStage.emitSystemVerilog(new L2CacheArray)
    val dataEnable =
      "(?s)module L2DataRAM\\(.*?assign memory_enb = 1'h1;.*?endmodule".r
    val metadataEnable =
      "(?s)module L2MetadataRAM\\(.*?assign memory_enb = 1'h1;.*?endmodule".r

    dataEnable.findFirstIn(verilog).isDefined shouldBe true
    metadataEnable.findFirstIn(verilog).isDefined shouldBe true
  }
}
