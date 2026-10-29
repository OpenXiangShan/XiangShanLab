package noc

import chisel3._
import circt.stage.ChiselStage

/** Generate the default four-node ring with a 32-bit payload. */
object RingMain {
  def main(args: Array[String]): Unit = {
    ChiselStage.emitSystemVerilogFile(new Ring(RingParams(), UInt(32.W)), args)
  }
}
