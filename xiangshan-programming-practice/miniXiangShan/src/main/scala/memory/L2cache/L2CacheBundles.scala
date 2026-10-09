package minixiangshan.mem.L2cache

import chisel3._
import chisel3.util._
import minixiangshan.config._

// L1 写请求的三种语义。普通牺牲行只等待 L2 接收，另外两种等待 DDR B。
object L2WriteKind {
  val width = 2
  def putLine: UInt = 0.U(width.W)
  def uncache: UInt = 1.U(width.W)
  def cleanLine: UInt = 2.U(width.W)
}

class L2ReadReq(val idWidth: Int)(implicit p: Parameters) extends NSBundle {
  val id = UInt(idWidth.W)
  val addr = UInt(XLEN.W)
  val size = UInt(3.W)
  val uncache = Bool()
}

class L2ReadResp(val idWidth: Int)(implicit p: Parameters) extends NSBundle {
  val id = UInt(idWidth.W)
  val data = UInt(l2LineBits.W)
  val fullLine = Bool()
  val last = Bool()
}

class L2WriteReq(val idWidth: Int)(implicit p: Parameters) extends NSBundle {
  val id = UInt(idWidth.W)
  val addr = UInt(XLEN.W)
  val kind = UInt(L2WriteKind.width.W)
  val size = UInt(3.W)
  val data = UInt(l2LineBits.W)
  val strb = UInt(l2BeatBytes.W)
}

class L2WriteDone(val idWidth: Int)(implicit p: Parameters) extends NSBundle {
  val id = UInt(idWidth.W)
}

// 从 L1 master 视角定义方向；L2 顶层使用 Flipped 接入。
class L2NativeReadIO(val idWidth: Int)(implicit p: Parameters) extends NSBundle {
  val req = Decoupled(new L2ReadReq(idWidth))
  // Master撤销已经被L2接收的读请求；L2继续排空下游事务但丢弃响应。
  val cancel = Output(Bool())
  val resp = Flipped(Decoupled(new L2ReadResp(idWidth)))
}

class L2NativeWriteIO(val idWidth: Int)(implicit p: Parameters) extends NSBundle {
  val req = Decoupled(new L2WriteReq(idWidth))
  val done = Flipped(Decoupled(new L2WriteDone(idWidth)))
}

class L2NativeMasterIO(val idWidth: Int)(implicit p: Parameters) extends NSBundle {
  val read = new L2NativeReadIO(idWidth)
  val write = new L2NativeWriteIO(idWidth)
}

// Bridge只保存并回传内部槽号；L1原始ID由L2的MSHR保存，不能在此处截断。
class L2BridgeOwner(implicit p: Parameters) extends NSBundle {
  val source = UInt(l2BridgeSourceBits.W)
  val slot = UInt(l2MshrIdBits.W)
}

class L2BridgeReadCmd(implicit p: Parameters) extends NSBundle {
  val owner = new L2BridgeOwner
  val addr = UInt(XLEN.W)
  val isLine = Bool()
  val size = UInt(3.W)
}

class L2BridgeReadBeat(implicit p: Parameters) extends NSBundle {
  val owner = new L2BridgeOwner
  val data = UInt(XLEN.W)
  val last = Bool()
}

class L2BridgeWriteCmd(implicit p: Parameters) extends NSBundle {
  val owner = new L2BridgeOwner
  val addr = UInt(XLEN.W)
  val isLine = Bool()
  val size = UInt(3.W)
  val data = UInt(l2LineBits.W)
  val strb = UInt(l2BeatBytes.W)
}

class L2BridgeWriteDone(implicit p: Parameters) extends NSBundle {
  val owner = new L2BridgeOwner
}

class L2BridgeReadClientIO(implicit p: Parameters) extends NSBundle {
  val req = Decoupled(new L2BridgeReadCmd)
  val beat = Flipped(Decoupled(new L2BridgeReadBeat))
}

class L2BridgeWriteClientIO(implicit p: Parameters) extends NSBundle {
  val req = Decoupled(new L2BridgeWriteCmd)
  val done = Flipped(Decoupled(new L2BridgeWriteDone))
}

// 从 L2 controller 视角定义 Bridge 事务方向。
class L2BridgeClientIO(implicit p: Parameters) extends NSBundle {
  val read = new L2BridgeReadClientIO
  val write = new L2BridgeWriteClientIO
}

class L2MaintenanceReq(implicit p: Parameters) extends NSBundle {
  val op = UInt(5.W)
  val addr = UInt(XLEN.W)
}

class L2MaintenanceDone(implicit p: Parameters) extends NSBundle {
  val done = Bool()
}

// V1 只预留该组接口。
class L2MaintenanceMasterIO(implicit p: Parameters) extends NSBundle {
  val req = Decoupled(new L2MaintenanceReq)
  val done = Flipped(Decoupled(new L2MaintenanceDone))
}

class L2ReplacerLookup(implicit p: Parameters) extends NSBundle {
  val set = UInt(l2IdxBits.W)
  val validMask = UInt(l2Ways.W)
}

class L2ReplacerTouch(implicit p: Parameters) extends NSBundle {
  val set = UInt(l2IdxBits.W)
  val way = UInt(l2WayBits.W)
}

class L2ArrayWayData(implicit p: Parameters) extends NSBundle {
  val valid = Bool()
  val dirty = Bool()
  val tag = UInt(l2TagBits.W)
  val data = UInt(l2LineBits.W)
}

class L2ArrayReadReq(implicit p: Parameters) extends NSBundle {
  val set = UInt(l2IdxBits.W)
}

class L2ArrayReadResp(implicit p: Parameters) extends NSBundle {
  val set = UInt(l2IdxBits.W)
  val ways = Vec(l2Ways, new L2ArrayWayData)
}

class L2ArrayWriteReq(implicit p: Parameters) extends NSBundle {
  val set = UInt(l2IdxBits.W)
  val way = UInt(l2WayBits.W)
  val valid = Bool()
  val dirty = Bool()
  val tag = UInt(l2TagBits.W)
  val data = UInt(l2LineBits.W)
  val dataWen = Bool()
}

class L2ArrayReadIO(implicit p: Parameters) extends NSBundle {
  val req = Flipped(Decoupled(new L2ArrayReadReq))
  val resp = Valid(new L2ArrayReadResp)
}

class L2ArrayIO(implicit p: Parameters) extends NSBundle {
  val initDone = Output(Bool())
  val read = new L2ArrayReadIO
  val write = Flipped(Valid(new L2ArrayWriteReq))
}
