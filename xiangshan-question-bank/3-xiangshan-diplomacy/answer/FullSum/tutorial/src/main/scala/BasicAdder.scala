package chapter_13

import chisel3._
import org.chipsalliance.cde.config.Parameters
import org.chipsalliance.diplomacy.lazymodule._
import circt.stage.ChiselStage

class BasicAdder(implicit p: Parameters) extends LazyModule {
  lazy val module = new BasicAdderImp(this)
}

class BasicAdderImp(outer: BasicAdder) extends LazyModuleImp(outer) {
  val in1 = IO(Input(UInt(32.W)))
  val in2 = IO(Input(UInt(32.W)))
  val out = IO(Output(UInt(32.W)))
  out := in1 + in2
}

object BasicAdderMain extends App {
  implicit val p: Parameters = Parameters.empty
  println(ChiselStage.emitSystemVerilog(
    LazyModule(new BasicAdder).module,
    firtoolOpts = Array("--disable-layers=Verification")
  ))
}
