package chapter_13

import chisel3._
import chisel3.experimental.SourceInfo
import circt.stage.ChiselStage
import java.nio.file.{Files, Paths}
import org.chipsalliance.cde.config.Parameters
import org.chipsalliance.diplomacy.lazymodule._
import org.chipsalliance.diplomacy.nodes._

object MultiAdderNodeImp extends SimpleNodeImp[Unit, Unit, Unit, UInt] {
  def edge(pd: Unit, pu: Unit, p: Parameters, sourceInfo: SourceInfo): Unit = ()
  def bundle(edge: Unit): UInt = UInt(32.W)
  def render(edge: Unit): RenderedEdge = RenderedEdge(colour = "#000000")
}

class MultiAdder(implicit p: Parameters) extends LazyModule {
  val node = new NexusNode(MultiAdderNodeImp)(_ => (), _ => ())
  lazy val module = new MultiAdderImp(this)
}

class MultiAdderImp(outer: MultiAdder) extends LazyModuleImp(outer) {
  val sum = Wire(UInt(32.W))
  sum := outer.node.in.map(_._1).reduce(_ + _)
  outer.node.out.foreach { case (wire, _) => wire := sum }
}

class MultiAdderTop(implicit p: Parameters) extends LazyModule {
  val inputNodes = new SourceNode(MultiAdderNodeImp)(Seq.fill(5)(()))
  val outputNodes = new SinkNode(MultiAdderNodeImp)(Seq.fill(3)(()))
  val adder = LazyModule(new MultiAdder)

  outputNodes :*= adder.node
  adder.node :=* inputNodes

  lazy val module = new MultiAdderTopImp(this)
}

class MultiAdderTopImp(outer: MultiAdderTop) extends LazyModuleImp(outer) {
  val inputs = outer.inputNodes.out.zipWithIndex.map { case ((wire, _), i) =>
    val in = IO(Input(UInt(32.W))).suggestName(s"in_$i")
    wire := in
    in
  }
  val outputs = outer.outputNodes.in.zipWithIndex.map { case ((wire, _), i) =>
    val out = IO(Output(UInt(32.W))).suggestName(s"out_$i")
    out := wire
    out
  }
}

object MultiAdderMain extends App {
  implicit val p: Parameters = Parameters.empty
  val top = LazyModule(new MultiAdderTop)
  println(ChiselStage.emitSystemVerilog(
    top.module,
    firtoolOpts = Array("--disable-layers=Verification")
  ))
  val graphPath = Paths.get("out", "tutorial", "multi-adder.graphml")
  Files.createDirectories(graphPath.getParent)
  Files.writeString(graphPath, top.graphML)
  println(s"Diplomacy graph: $graphPath")
}
