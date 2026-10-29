package fullsum

import chisel3._
import org.chipsalliance.cde.config.Parameters
import org.chipsalliance.diplomacy.ValName
import org.chipsalliance.diplomacy.lazymodule._

private[fullsum] object AdderTestGraph {
  sealed trait Tree
  final case class Input(index: Int) extends Tree
  final case class Add(children: Seq[Tree]) extends Tree
  def flat(size: Int): Tree = Add((0 until size).map(Input))
}

private[fullsum] class AdderTestGraph(
  widths: Seq[Int],
  tree: AdderTestGraph.Tree,
  capacities: Seq[Option[Int]] = Seq(None)
)(implicit p: Parameters) extends LazyModule {
  import AdderTestGraph._

  val sources = widths.map(w => new SumSourceNode(Seq(SumSourceParameters.fromWidth(w))))
  val sinks = capacities.map(c => new SumSinkNode(Seq(SumSinkParameters(c))))

  private def connect(tree: Tree): FullPrecisionAdder = tree match {
    case Add(children) =>
      val adder = LazyModule(new FullPrecisionAdder)
      children.foreach {
        case Input(index) => adder.node := sources(index)
        case subtree: Add => adder.node := connect(subtree).node
      }
      adder
    case Input(_) => throw new IllegalArgumentException("test graph root must be an adder")
  }

  val root = connect(tree)
  sinks.foreach(_ := root.node)

  lazy val module = new AdderTestGraphImp(this)
}

private[fullsum] class AdderTestGraphImp(outer: AdderTestGraph) extends LazyModuleImp(outer) {
  val inputs = outer.sources.zipWithIndex.map { case (node, i) => node.makeIOs()(ValName(s"input_$i")).head }
  val outputs = outer.sinks.zipWithIndex.map { case (node, i) => node.makeIOs()(ValName(s"output_$i")).head }
  val resultEdges = outer.sinks.flatMap(_.in.map(_._2))
}
