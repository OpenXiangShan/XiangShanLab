package device

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config.Parameters
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.amba.axi4._
import freechips.rocketchip.tilelink._

import freechips.rocketchip.util._
import _root_.circt.stage.ChiselStage
import chisel3.experimental.BundleLiterals.AddBundleLiteralConstructor
import chisel3.Data.DataEquality

// 简化的AXI4DCache，不暴露IO
class AXI4DCache(params: AXI4MasterParameters)(implicit p: Parameters) extends LazyModule {

  val node = AXI4MasterNode(Seq(AXI4MasterPortParameters(Seq(params))))
  
  override lazy val module = new LazyModuleImp(this) {
    // val (axi_bundle, _) = node.out.head
    val axi_bundle = node.out.head._1
    dontTouch(axi_bundle)
    // TODO: cp my cache here
    axi_bundle.ar.valid := false.B
    axi_bundle.r.ready := true.B

    axi_bundle.aw.valid := false.B
    axi_bundle.w.valid := false.B
    axi_bundle.b.ready := true.B


  }
}