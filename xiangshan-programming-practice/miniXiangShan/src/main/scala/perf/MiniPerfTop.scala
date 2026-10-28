package minixiangshan.perf

import chisel3._
import minixiangshan.core_top
import minixiangshan.config._
import minixiangshan.axi._

/** Flat-port wrapper around the shared AXI4 performance platform. */
class Axi4PerfPlatform extends BlackBox {
  override def desiredName: String = "Axi4PerfPlatform"

  val io = IO(new Bundle {
    val aclk    = Input(Clock())
    val aresetn = Input(Bool())

    val s_axi_arid    = Input(UInt(4.W))
    val s_axi_araddr  = Input(UInt(32.W))
    val s_axi_arlen   = Input(UInt(8.W))
    val s_axi_arsize  = Input(UInt(3.W))
    val s_axi_arburst = Input(UInt(2.W))
    val s_axi_arlock  = Input(UInt(1.W))
    val s_axi_arcache = Input(UInt(4.W))
    val s_axi_arprot  = Input(UInt(3.W))
    val s_axi_arqos   = Input(UInt(4.W))
    val s_axi_aruser  = Input(UInt(1.W))
    val s_axi_arvalid = Input(Bool())
    val s_axi_arready = Output(Bool())

    val s_axi_rid    = Output(UInt(4.W))
    val s_axi_rdata  = Output(UInt(64.W))
    val s_axi_rresp  = Output(UInt(2.W))
    val s_axi_rlast  = Output(Bool())
    val s_axi_ruser  = Output(UInt(1.W))
    val s_axi_rvalid = Output(Bool())
    val s_axi_rready = Input(Bool())

    val s_axi_awid    = Input(UInt(4.W))
    val s_axi_awaddr  = Input(UInt(32.W))
    val s_axi_awlen   = Input(UInt(8.W))
    val s_axi_awsize  = Input(UInt(3.W))
    val s_axi_awburst = Input(UInt(2.W))
    val s_axi_awlock  = Input(UInt(1.W))
    val s_axi_awcache = Input(UInt(4.W))
    val s_axi_awprot  = Input(UInt(3.W))
    val s_axi_awqos   = Input(UInt(4.W))
    val s_axi_awuser  = Input(UInt(1.W))
    val s_axi_awvalid = Input(Bool())
    val s_axi_awready = Output(Bool())

    val s_axi_wdata  = Input(UInt(64.W))
    val s_axi_wstrb  = Input(UInt(8.W))
    val s_axi_wlast  = Input(Bool())
    val s_axi_wuser  = Input(UInt(1.W))
    val s_axi_wvalid = Input(Bool())
    val s_axi_wready = Output(Bool())

    val s_axi_bid    = Output(UInt(4.W))
    val s_axi_bresp  = Output(UInt(2.W))
    val s_axi_buser  = Output(UInt(1.W))
    val s_axi_bvalid = Output(Bool())
    val s_axi_bready = Input(Bool())

    val perf_done         = Output(Bool())
    val perf_signature    = Output(UInt(32.W))
    val perf_cycle_lo     = Output(UInt(32.W))
    val perf_cycle_hi     = Output(UInt(32.W))
    val perf_instret_lo   = Output(UInt(32.W))
    val perf_instret_hi   = Output(UInt(32.W))
    val perf_checksum     = Output(UInt(32.W))
  })
}

/**
 * Performance-comparison top for miniXiangShan.
 *
 * Only the external bus is changed.  The core, L1s, L2, and the existing
 * single-outstanding L2Bridge remain the same RTL as the normal build.
 */
class MiniPerfTop(implicit p: Parameters) extends NSRawModule {
  val aclk    = IO(Input(Clock()))
  val aresetn = IO(Input(Bool()))

  val perf_done       = IO(Output(Bool()))
  val perf_signature  = IO(Output(UInt(32.W)))
  val perf_cycle_lo   = IO(Output(UInt(32.W)))
  val perf_cycle_hi   = IO(Output(UInt(32.W)))
  val perf_instret_lo = IO(Output(UInt(32.W)))
  val perf_instret_hi = IO(Output(UInt(32.W)))
  val perf_checksum   = IO(Output(UInt(32.W)))

  val core = Module(new core_top)
  core.aclk    := aclk
  core.aresetn := aresetn
  core.intrpt  := 0.U
  core.break_point := false.B
  core.infor_flag  := false.B
  core.reg_num     := 0.U

  val (adapter, platform) = withClockAndReset(aclk, ~aresetn) {
    (Module(new Axi3ToAxi4), Module(new Axi4PerfPlatform))
  }

  platform.io.aclk    := aclk
  platform.io.aresetn := aresetn

  adapter.io.in.ar.data.arid    := core.arid
  adapter.io.in.ar.data.araddr  := core.araddr
  adapter.io.in.ar.data.arlen   := core.arlen
  adapter.io.in.ar.data.arsize  := core.arsize
  adapter.io.in.ar.data.arburst := core.arburst
  adapter.io.in.ar.data.arlock  := core.arlock
  adapter.io.in.ar.data.arcache := core.arcache
  adapter.io.in.ar.data.arprot  := core.arprot
  adapter.io.in.ar.data.arvalid := core.arvalid
  core.arready := adapter.io.in.ar.arready

  core.rid    := adapter.io.in.r.data.rid
  core.rdata  := adapter.io.in.r.data.rdata
  core.rresp  := adapter.io.in.r.data.rresp
  core.rlast  := adapter.io.in.r.data.rlast
  core.rvalid := adapter.io.in.r.data.rvalid
  adapter.io.in.r.rready := core.rready

  adapter.io.in.aw.data.awid    := core.awid
  adapter.io.in.aw.data.awaddr  := core.awaddr
  adapter.io.in.aw.data.awlen   := core.awlen
  adapter.io.in.aw.data.awsize  := core.awsize
  adapter.io.in.aw.data.awburst := core.awburst
  adapter.io.in.aw.data.awlock  := core.awlock
  adapter.io.in.aw.data.awcache := core.awcache
  adapter.io.in.aw.data.awprot  := core.awprot
  adapter.io.in.aw.data.awvalid := core.awvalid
  core.awready := adapter.io.in.aw.awready

  adapter.io.in.w.data.wid    := core.wid
  adapter.io.in.w.data.wdata  := core.wdata
  adapter.io.in.w.data.wstrb  := core.wstrb
  adapter.io.in.w.data.wlast  := core.wlast
  adapter.io.in.w.data.wvalid := core.wvalid
  core.wready := adapter.io.in.w.wready

  core.bid    := adapter.io.in.b.data.bid
  core.bresp  := adapter.io.in.b.data.bresp
  core.bvalid := adapter.io.in.b.data.bvalid
  adapter.io.in.b.bready := core.bready

  platform.io.s_axi_arid    := adapter.io.out.ar.data.arid
  platform.io.s_axi_araddr  := adapter.io.out.ar.data.araddr
  platform.io.s_axi_arlen   := adapter.io.out.ar.data.arlen
  platform.io.s_axi_arsize  := adapter.io.out.ar.data.arsize
  platform.io.s_axi_arburst := adapter.io.out.ar.data.arburst
  platform.io.s_axi_arlock  := adapter.io.out.ar.data.arlock
  platform.io.s_axi_arcache := adapter.io.out.ar.data.arcache
  platform.io.s_axi_arprot  := adapter.io.out.ar.data.arprot
  platform.io.s_axi_arqos   := adapter.io.out.ar.data.arqos
  platform.io.s_axi_aruser  := adapter.io.out.ar.data.aruser
  platform.io.s_axi_arvalid := adapter.io.out.ar.data.arvalid
  adapter.io.out.ar.arready := platform.io.s_axi_arready

  adapter.io.out.r.data.rid    := platform.io.s_axi_rid
  adapter.io.out.r.data.rdata  := platform.io.s_axi_rdata
  adapter.io.out.r.data.rresp  := platform.io.s_axi_rresp
  adapter.io.out.r.data.rlast  := platform.io.s_axi_rlast
  adapter.io.out.r.data.ruser  := platform.io.s_axi_ruser
  adapter.io.out.r.data.rvalid := platform.io.s_axi_rvalid
  platform.io.s_axi_rready := adapter.io.out.r.rready

  platform.io.s_axi_awid    := adapter.io.out.aw.data.awid
  platform.io.s_axi_awaddr  := adapter.io.out.aw.data.awaddr
  platform.io.s_axi_awlen   := adapter.io.out.aw.data.awlen
  platform.io.s_axi_awsize  := adapter.io.out.aw.data.awsize
  platform.io.s_axi_awburst := adapter.io.out.aw.data.awburst
  platform.io.s_axi_awlock  := adapter.io.out.aw.data.awlock
  platform.io.s_axi_awcache := adapter.io.out.aw.data.awcache
  platform.io.s_axi_awprot  := adapter.io.out.aw.data.awprot
  platform.io.s_axi_awqos   := adapter.io.out.aw.data.awqos
  platform.io.s_axi_awuser  := adapter.io.out.aw.data.awuser
  platform.io.s_axi_awvalid := adapter.io.out.aw.data.awvalid
  adapter.io.out.aw.awready := platform.io.s_axi_awready

  platform.io.s_axi_wdata  := adapter.io.out.w.data.wdata
  platform.io.s_axi_wstrb  := adapter.io.out.w.data.wstrb
  platform.io.s_axi_wlast  := adapter.io.out.w.data.wlast
  platform.io.s_axi_wuser  := adapter.io.out.w.data.wuser
  platform.io.s_axi_wvalid := adapter.io.out.w.data.wvalid
  adapter.io.out.w.wready  := platform.io.s_axi_wready

  adapter.io.out.b.data.bid    := platform.io.s_axi_bid
  adapter.io.out.b.data.bresp  := platform.io.s_axi_bresp
  adapter.io.out.b.data.buser  := platform.io.s_axi_buser
  adapter.io.out.b.data.bvalid := platform.io.s_axi_bvalid
  platform.io.s_axi_bready := adapter.io.out.b.bready

  perf_done       := platform.io.perf_done
  perf_signature  := platform.io.perf_signature
  perf_cycle_lo   := platform.io.perf_cycle_lo
  perf_cycle_hi   := platform.io.perf_cycle_hi
  perf_instret_lo := platform.io.perf_instret_lo
  perf_instret_hi := platform.io.perf_instret_hi
  perf_checksum   := platform.io.perf_checksum
}
