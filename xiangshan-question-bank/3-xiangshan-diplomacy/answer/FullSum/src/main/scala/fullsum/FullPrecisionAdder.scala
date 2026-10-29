package fullsum

import chisel3._
import chisel3.experimental.SourceInfo
import circt.stage.ChiselStage
import org.chipsalliance.cde.config.Parameters
import org.chipsalliance.diplomacy.ValName
import org.chipsalliance.diplomacy.lazymodule._
import org.chipsalliance.diplomacy.nodes._

final case class SumSourceParameters(maxValue: BigInt) {
  require(maxValue >= 0, s"maximum value must be nonnegative: $maxValue")
}

object SumSourceParameters {
  def fromWidth(width: Int): SumSourceParameters = {
    require(width > 0, s"source width must be positive: $width")
    SumSourceParameters((BigInt(1) << width) - 1)
  }
}

final case class SumSinkParameters(capacity: Option[Int] = None, label: String = "sink") {
  require(capacity.forall(_ > 0), s"sink capacity must be positive: $capacity")
}

final case class SumEdgeParameters(maxValue: BigInt) {
  val width: Int = maxValue.bitLength.max(1)
}

object SumNodeImp extends SimpleNodeImp[SumSourceParameters, SumSinkParameters, SumEdgeParameters, UInt] {
  def edge(pd: SumSourceParameters, pu: SumSinkParameters, p: Parameters, sourceInfo: SourceInfo): SumEdgeParameters = {
    val result = SumEdgeParameters(pd.maxValue)
    pu.capacity.foreach { capacity =>
      require(result.width <= capacity,
        s"${pu.label}: sum requires ${result.width} bits but sink capacity is $capacity bits")
    }
    result
  }

  def bundle(edge: SumEdgeParameters): UInt = UInt(edge.width.W)
  def render(edge: SumEdgeParameters): RenderedEdge =
    RenderedEdge(colour = "#000000", label = s"${edge.width}-bit, max=${edge.maxValue}")
}

class SumSourceNode(parameters: Seq[SumSourceParameters])(implicit valName: ValName)
    extends SourceNode(SumNodeImp)(parameters)

class SumSinkNode(parameters: Seq[SumSinkParameters])(implicit valName: ValName)
    extends SinkNode(SumNodeImp)(parameters)

object FullPrecisionAdderTop {
  private def validatedParameters(sourceWidths: Seq[Int], sinkMaxWidths: Seq[Int], p: Parameters): Parameters = {
    require(sourceWidths.nonEmpty, "sourceWidths must be non-empty")
    require(sourceWidths.forall(_ > 0), s"sourceWidths must be positive: $sourceWidths")
    require(sinkMaxWidths.nonEmpty, "sinkMaxWidths must be non-empty")
    require(sinkMaxWidths.forall(_ > 0), s"sinkMaxWidths must be positive: $sinkMaxWidths")
    p
  }
}

class FullPrecisionAdderTop(
  val sourceWidths: Seq[Int] = Seq(8, 8, 8),
  val sinkMaxWidths: Seq[Int] = Seq(10, 16)
)(implicit p: Parameters) extends LazyModule()(FullPrecisionAdderTop.validatedParameters(sourceWidths, sinkMaxWidths, p)) {
  Console.err.println(s"FullPrecisionAdder configuration: sourceWidths=${sourceWidths.mkString("[", ",", "]")}, " +
    s"sinkMaxWidths=${sinkMaxWidths.mkString("[", ",", "]")}")

  val sources = sourceWidths.map(width =>
    new SumSourceNode(Seq(SumSourceParameters.fromWidth(width))))
  val sinks = sinkMaxWidths.zipWithIndex.map { case (width, index) =>
    new SumSinkNode(Seq(SumSinkParameters(Some(width), s"sink_$index")))
  }
  val adder = LazyModule(new FullPrecisionAdder)

  sources.foreach(adder.node := _)
  sinks.foreach(_ := adder.node)

  lazy val module = new FullPrecisionAdderTopImp(this)
}

class FullPrecisionAdderTopImp(outer: FullPrecisionAdderTop) extends LazyModuleImp(outer) {
  val inputs = outer.sources.zipWithIndex.map { case (node, index) =>
    val (wire, edge) = node.out.head
    val in = IO(Input(UInt(edge.width.W))).suggestName(s"in_$index")
    wire := in
    in
  }
  val outputs = outer.sinks.zipWithIndex.map { case (node, index) =>
    val (wire, edge) = node.in.head
    val out = IO(Output(UInt(edge.width.W))).suggestName(s"out_$index")
    out := wire
    out
  }
}

class FullPrecisionAdder(implicit p: Parameters) extends LazyModule {
  val node = new NexusNode(SumNodeImp)(
    inputs => SumSourceParameters(inputs.map(_.maxValue).sum),
    _ => SumSinkParameters()
  )

  lazy val module = new LazyModuleImp(this) {
    val inputs = node.in.map(_._1)
    val outputs = node.out.map(_._1)
    require(inputs.nonEmpty, "full-precision adder requires at least one input")
    require(outputs.nonEmpty, "full-precision adder requires at least one output")
    Console.err.println(s"FullPrecisionAdder negotiation: inputWidths=${node.in.map(_._2.width).mkString("[", ",", "]")}, " +
      s"maxSum=${node.out.head._2.maxValue}, sumWidth=${node.out.head._2.width}")
    val sum = inputs.reduce(_ +& _)
    outputs.foreach(_ := sum)
  }
}

object FullPrecisionAdderMain extends App {
  require(args.length == 0 || args.length == 2,
    "usage: FullPrecisionAdderMain [sourceWidths sinkMaxWidths] (comma-separated positive integers)")
  implicit val p: Parameters = Parameters.empty
  val top = if (args.isEmpty) LazyModule(new FullPrecisionAdderTop())
    else LazyModule(new FullPrecisionAdderTop(
      args(0).split(",", -1).map(_.toInt).toSeq,
      args(1).split(",", -1).map(_.toInt).toSeq
    ))
  println(ChiselStage.emitSystemVerilog(top.module, firtoolOpts = Array("--disable-layers=Verification")))
}
