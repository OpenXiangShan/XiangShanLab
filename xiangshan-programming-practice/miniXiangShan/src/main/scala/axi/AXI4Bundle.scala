package minixiangshan.axi

import chisel3._
import minixiangshan.config.{NSBundle, Parameters}

/** AXI4 full master-side bundle, 32-bit address and 64-bit data. */
class AXI4ARData(implicit p: Parameters) extends NSBundle {
  val arid    = UInt(4.W)
  val araddr  = UInt(32.W)
  val arlen   = UInt(8.W)
  val arsize  = UInt(3.W)
  val arburst = UInt(2.W)
  val arlock  = UInt(1.W)
  val arcache = UInt(4.W)
  val arprot  = UInt(3.W)
  val arqos   = UInt(4.W)
  val aruser  = UInt(1.W)
  val arvalid = Bool()
}

class AXI4RData(implicit p: Parameters) extends NSBundle {
  val rid    = UInt(4.W)
  val rdata  = UInt(64.W)
  val rresp  = UInt(2.W)
  val rlast  = Bool()
  val ruser  = UInt(1.W)
  val rvalid = Bool()
}

class AXI4AWData(implicit p: Parameters) extends NSBundle {
  val awid    = UInt(4.W)
  val awaddr  = UInt(32.W)
  val awlen   = UInt(8.W)
  val awsize  = UInt(3.W)
  val awburst = UInt(2.W)
  val awlock  = UInt(1.W)
  val awcache = UInt(4.W)
  val awprot  = UInt(3.W)
  val awqos   = UInt(4.W)
  val awuser  = UInt(1.W)
  val awvalid = Bool()
}

class AXI4WData(implicit p: Parameters) extends NSBundle {
  val wdata  = UInt(64.W)
  val wstrb  = UInt(8.W)
  val wlast  = Bool()
  val wuser  = UInt(1.W)
  val wvalid = Bool()
}

class AXI4BData(implicit p: Parameters) extends NSBundle {
  val bid    = UInt(4.W)
  val bresp  = UInt(2.W)
  val buser  = UInt(1.W)
  val bvalid = Bool()
}

class AXI4ARChannel(implicit p: Parameters) extends NSBundle {
  val data = Output(new AXI4ARData)
  val arready = Input(Bool())
}

class AXI4RChannel(implicit p: Parameters) extends NSBundle {
  val data = Input(new AXI4RData)
  val rready = Output(Bool())
}

class AXI4AWChannel(implicit p: Parameters) extends NSBundle {
  val data = Output(new AXI4AWData)
  val awready = Input(Bool())
}

class AXI4WChannel(implicit p: Parameters) extends NSBundle {
  val data = Output(new AXI4WData)
  val wready = Input(Bool())
}

class AXI4BChannel(implicit p: Parameters) extends NSBundle {
  val data = Input(new AXI4BData)
  val bready = Output(Bool())
}

class AXI4MasterIO(implicit p: Parameters) extends NSBundle {
  val ar = new AXI4ARChannel
  val aw = new AXI4AWChannel
  val w  = new AXI4WChannel
  val r  = new AXI4RChannel
  val b  = new AXI4BChannel
}
