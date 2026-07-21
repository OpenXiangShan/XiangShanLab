package iopmp

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config.Parameters
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.amba.axi4._
import freechips.rocketchip.amba.apb._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.devices.tilelink._
import freechips.rocketchip.util._
import utility.AXI4Error
import _root_.circt.stage.ChiselStage
import device._

class AXIMemory
(
  address: Seq[AddressSet]
)(implicit p: Parameters)
  extends AXI4SlaveModule(address, executable = false, beatBytes = 8)

class Demo(implicit p: Parameters) extends LazyModule {

  val masterNode = AXI4MasterNode(Seq(AXI4MasterPortParameters(
    Seq(AXI4MasterParameters(
      name      = "iopmp-bridge",
      id        = IdRange(0, 14),
      aligned   = true,
      maxFlight = Some(8)
    ))
  )))


  val dmac = LazyModule(new AXI4DMAC(Seq(AddressSet(0x40003000L, 0xfff))))
  val iopmp = LazyModule(new IopmpLazy(1))
  val memory = LazyModule(new AXIMemory(Seq(AddressSet(0x80000000L, 0x7fffffffL)))) // fake memory, no cache
  val error = LazyModule(new AXI4Error(Seq(AddressSet(0x50000000L, 0xfff))))
  val error_tl = LazyModule(new TLError(DevNullParams(Seq(AddressSet(0x1000000000000L, 0xffffffffffffL)), maxAtomic = 1, maxTransfer = 8),beatBytes = 8))
  val axiBus = AXI4Xbar()
  val tlBus = TLXbar()

  axiBus := masterNode
  dmac.node := axiBus
  error.node := axiBus

  error_tl.node := tlBus
  tlBus :=
    TLFIFOFixer() :=
    AXI4ToTL(wcorrupt = false) :=
    AXI4UserYanker() :=
    AXI4IdIndexer(1) :=
    axiBus

  val apb_node = APBSlaveNode(Seq(APBSlavePortParameters(
    Seq(APBSlaveParameters(
      address       = Seq(AddressSet(0x40008000L, 0xfff)),
      regionType    = RegionType.UNCACHED)),
    beatBytes     = 4)))

  apb_node :=
    TLToAPB() :=
    TLFragmenter(4,8) :=
    TLWidthWidget(8) :=
    tlBus

  iopmp.slaveNodes(0) := dmac.masterNode
  memory.node := iopmp.masterNodes(0)

  val io_slv = InModuleBody {
    masterNode.makeIOs()
  }

  lazy val module = new Imp
  class Imp extends LazyModuleImp(this) {

    val iopmp_apb = apb_node.in.head._1

    iopmp.module.apb_s.paddr    <> iopmp_apb.paddr
    iopmp.module.apb_s.psel     <> iopmp_apb.psel
    iopmp.module.apb_s.penable  <> iopmp_apb.penable
    iopmp.module.apb_s.pwrite   <> iopmp_apb.pwrite
    iopmp.module.apb_s.pwdata   <> iopmp_apb.pwdata
    iopmp.module.apb_s.pready   <> iopmp_apb.pready
    iopmp.module.apb_s.prdata   <> iopmp_apb.prdata

  }
}
