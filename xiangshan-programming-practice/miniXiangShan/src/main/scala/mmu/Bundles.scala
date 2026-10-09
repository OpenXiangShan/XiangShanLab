package minixiangshan.mmu

import chisel3._
import chisel3.util._

import minixiangshan.config._
import minixiangshan.backend.decode.LsuOp

/* ============================================================================
 *  Sv32 地址翻译相关数据结构
 * ==========================================================================*/

/** 访问类型：取指 / Load / Store */
object MmuAccType {
  val width = 2
  val fetch = 0.U(width.W)
  val load  = 1.U(width.W)
  val store = 2.U(width.W)
}

/** 全相联 TLB 表项（4KB 页粒度，Sv32） */
class TlbEntry(implicit p: Parameters) extends NSBundle {
  val valid = Bool()
  val vpn   = UInt(vpnLen.W)
  val asid  = UInt(asidLen.W)
  val ppn   = UInt(ppnLen.W)
  val r     = Bool()
  val w     = Bool()
  val x     = Bool()
  val u     = Bool()
  val g     = Bool()
}

class TlbLookupReq(implicit p: Parameters) extends NSBundle {
  val vaddr = UInt(XLEN.W)
  val asid  = UInt(asidLen.W)
  val acc   = UInt(MmuAccType.width.W)
}

class TlbLookupResp(implicit p: Parameters) extends NSBundle {
  val hit  = Bool()
  val ppn  = UInt(ppnLen.W)
  val r    = Bool()
  val w    = Bool()
  val x    = Bool()
  val u    = Bool()
  val g    = Bool()
}

class TlbFill(implicit p: Parameters) extends NSBundle {
  val valid = Bool()
  val vpn   = UInt(vpnLen.W)
  val asid  = UInt(asidLen.W)
  val ppn   = UInt(ppnLen.W)
  val r     = Bool()
  val w     = Bool()
  val x     = Bool()
  val u     = Bool()
  val g     = Bool()
}

/** sfence.vma 冲刷请求 */
class TlbFlush(implicit p: Parameters) extends NSBundle {
  val valid = Bool()
  val all   = Bool()          // rs1 = x0：整表冲刷
  val noAsid = Bool()         // rs2 = x0：忽略 ASID
  val vaddr = UInt(XLEN.W)
  val asid  = UInt(asidLen.W)
}

/* ---------------------------------------------------------------------------
 *  MMU 错误
 * -------------------------------------------------------------------------*/
class FetchMmuError(implicit p: Parameters) extends NSBundle {
  val misalign    = Bool()
  val pageFault   = Bool()
  val accessFault = Bool()
  def any: Bool = misalign || pageFault || accessFault
  def getAnyError: Bool = any
}

class MemMmuError(implicit p: Parameters) extends NSBundle {
  val misalign    = Bool()
  val pageFault   = Bool()
  val accessFault = Bool()
  def any: Bool = misalign || pageFault || accessFault
  def getAnyError: Bool = any
}

class IcacheToMmu(implicit p: Parameters) extends NSBundle {
  val vaddr = UInt(XLEN.W)
}

class MmuToIcache(implicit p: Parameters) extends NSBundle {
  val paddr     = UInt(XLEN.W)
  val cacheable = Bool()
  val hasError  = Bool()
  val error     = new FetchMmuError
}

class SqToMmuReq(implicit p: Parameters) extends NSBundle {
  val vaddr = UInt(XLEN.W)
  val lsuOp = UInt(LsuOp.width.W)
}

class MmuToSqResp(implicit p: Parameters) extends NSBundle {
  val paddr     = UInt(XLEN.W)
  val cacheable = Bool()
  val hasError  = Bool()
  val error     = new MemMmuError
}

/** CSR -> MMU 的翻译控制信息 */
class CsrToMmu(implicit p: Parameters) extends NSBundle {
  val satp     = UInt(XLEN.W)  // MODE[31] | ASID[30:22] | PPN[21:0]
  val priv     = UInt(privLen.W) // 取指特权级
  val dataPriv = UInt(privLen.W) // 数据访问特权级（考虑 mstatus.MPRV）
  val sum      = Bool()
  val mxr      = Bool()
}

/** sfence.vma 指令信息 */
class TlbInstr(implicit p: Parameters) extends NSBundle {
  val cmd   = UInt(minixiangshan.backend.decode.TlbOp.width.W)
  val all   = Bool()   // rs1 = x0：忽略 vaddr
  val noAsid = Bool()  // rs2 = x0：忽略 asid
  val vaddr = UInt(XLEN.W)
  val asid  = UInt(asidLen.W)
}

/* ---------------------------------------------------------------------------
 *  页表遍历器接口
 * -------------------------------------------------------------------------*/
class PtwReq(implicit p: Parameters) extends NSBundle {
  val vaddr = UInt(XLEN.W)
  val acc   = UInt(MmuAccType.width.W)
  val satp  = UInt(XLEN.W)
}

class PtwResp(implicit p: Parameters) extends NSBundle {
  val fault = Bool()          // 页错误
  val vpn   = UInt(vpnLen.W)
  val ppn   = UInt(ppnLen.W)
  val r     = Bool()
  val w     = Bool()
  val x     = Bool()
  val u     = Bool()
  val g     = Bool()
  val paddr = UInt(XLEN.W)
}

class MmuIoBundle(implicit p: Parameters) extends NSBundle {
  val fromCsr = Input(new CsrToMmu)

  val fromIcache = Flipped(Decoupled(new IcacheToMmu))
  val toIcache   = Decoupled(new MmuToIcache)

  val fromIcacheFlush = Input(Bool())

  val fromMem = Flipped(Decoupled(new SqToMmuReq))
  val toMem   = Decoupled(new MmuToSqResp)
  val fromMemFlush = Input(Bool())

  /** sfence.vma / satp 写导致的 TLB 冲刷 */
  val tlbFlush = Input(new TlbFlush)

  /** TLB 填充索引（供调试/difftest 观测） */
  val tlbFillIdx = Output(UInt(tlbIdxLen.W))

  /** 页表遍历的物理读端口，连到 L2 读仲裁器 */
  val ptwRead = new minixiangshan.mem.L2cache.L2NativeReadIO(1)
}
