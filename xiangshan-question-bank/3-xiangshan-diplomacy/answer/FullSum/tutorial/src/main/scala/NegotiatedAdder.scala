package chapter_13

import chisel3._
import chisel3.experimental.SourceInfo
import circt.stage.ChiselStage
import java.nio.file.{Files, Paths}
import org.chipsalliance.cde.config.Parameters
import org.chipsalliance.diplomacy.lazymodule._
import org.chipsalliance.diplomacy.nodes._

object WidthNegotiatedNodeImp extends SimpleNodeImp[Int, Int, Int, UInt] {
  def edge(pd: Int, pu: Int, p: Parameters, sourceInfo: SourceInfo): Int = {
    val negotiated = pd.min(pu)
    println(s"Negotiation: downstream=$pd bits, upstream=$pu bits -> $negotiated bits")
    negotiated
  }

  def bundle(edge: Int): UInt = {
    println(s"Generating hardware bundle: UInt(${edge}.W)")
    UInt(edge.W)
  }

  def render(edge: Int): RenderedEdge =
    RenderedEdge(colour = "#000000", label = s"${edge}-bit")
}

class NegotiatedAdder(implicit p: Parameters) extends LazyModule {
  val node = new NexusNode(WidthNegotiatedNodeImp)(
    _ => 32,
    _ => 32
  )

  lazy val module = new NegotiatedAdderImp(this)
}

class NegotiatedAdderImp(outer: NegotiatedAdder) extends LazyModuleImp(outer) {
  val inputs = outer.node.in.map(_._1)
  val outputs = outer.node.out.map(_._1)
  val sum = inputs.reduce(_ + _)
  outputs.foreach(_ := sum)
}

class NegotiatedAdderTop(implicit p: Parameters) extends LazyModule {
  val adder = LazyModule(new NegotiatedAdder)
  val inputNodes = new SourceNode(WidthNegotiatedNodeImp)(Seq(8, 16, 32, 64, 128))
  val outputNodes = new SinkNode(WidthNegotiatedNodeImp)(Seq(16, 32, 64))

  adder.node :=* inputNodes
  outputNodes :*= adder.node

  lazy val module = new NegotiatedAdderTopImp(this)
}

class NegotiatedAdderTopImp(outer: NegotiatedAdderTop) extends LazyModuleImp(outer) {
  val inputs = outer.inputNodes.out.zipWithIndex.map { case ((wire, width), i) =>
    val in = IO(Input(UInt(width.W))).suggestName(s"in_${i}_${width}bit")
    wire := in
    in
  }
  val outputs = outer.outputNodes.in.zipWithIndex.map { case ((wire, width), i) =>
    val out = IO(Output(UInt(width.W))).suggestName(s"out_${i}_${width}bit")
    out := wire
    out
  }
}

object NegotiatedAdderMain extends App {
  implicit val p: Parameters = Parameters.empty
  val top = LazyModule(new NegotiatedAdderTop)
  println(ChiselStage.emitSystemVerilog(
    top.module,
    firtoolOpts = Array("--disable-layers=Verification")
  ))
  val graphPath = Paths.get("out", "tutorial", "negotiated-adder.graphml")
  Files.createDirectories(graphPath.getParent)
  Files.writeString(graphPath, top.graphML)
  println(s"Diplomacy graph: $graphPath")
}
