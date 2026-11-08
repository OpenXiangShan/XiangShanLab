package chi

import circt.stage.ChiselStage
import org.chipsalliance.cde.config.Parameters
import org.chipsalliance.diplomacy.lazymodule.LazyModule

object ChiSystemMain {
  def main(args: Array[String]): Unit = {
    implicit val p: Parameters = Parameters.empty
    val params = ChiSystemParams(initialLines = Seq.tabulate(4)(index => BigInt(0x100 + index)),
      coherent = args.contains("--coherent"))
    val stageArgs = args.filterNot(_ == "--coherent")
    ChiselStage.emitSystemVerilogFile(LazyModule(new ChiSystem(params)).module, stageArgs)
  }
}
