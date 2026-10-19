package noc

import chisel3._
import circt.stage.ChiselStage

/** Generate four independent rings with example payload widths. */
object QuadRingMain {
  def main(args: Array[String]): Unit = {
    ChiselStage.emitSystemVerilogFile(
      new QuadRing(RingParams(), UInt(8.W), UInt(16.W), UInt(32.W), UInt(64.W)),
      args
    )
  }
}
