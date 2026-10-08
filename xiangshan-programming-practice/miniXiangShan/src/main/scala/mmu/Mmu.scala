package minixiangshan.mmu

import chisel3._
import chisel3.util._

import minixiangshan.config._
import minixiangshan.backend.decode.LsuOp

/* ============================================================================
 *  RISC-V Sv32 MMU
 *
 *  · satp.MODE = 0 (Bare)：恒等映射
 *  · satp.MODE = 1 (Sv32)：TLB 查找 + 硬件页表遍历（TLB 缺失时）
 *  · 两个翻译端口（取指 / 访存）共享一个页表遍历器，按轮转仲裁
 *  · 权限检查：R/W/X 位、U 位、SUM、MXR
 *  · 未实现 PMP/PMA，因此不产生 access fault；MMIO 区域标记为不可缓存
 * ==========================================================================*/
class Mmu(implicit p: Parameters) extends NSModule with HasCsrParameters {
  val io = IO(new MmuIoBundle)

  val tlb = Module(new Tlb)
  val ptw = Module(new Ptw)

  // ---------------- PTW 物理读端口透传 ----------------
  io.ptwRead.req.valid  := ptw.io.l2.req.valid
  ptw.io.l2.req.ready   := io.ptwRead.req.ready
  io.ptwRead.req.bits   := ptw.io.l2.req.bits
  io.ptwRead.cancel     := ptw.io.l2.cancel
  ptw.io.l2.resp.valid  := io.ptwRead.resp.valid
  io.ptwRead.resp.ready := ptw.io.l2.resp.ready
  ptw.io.l2.resp.bits   := io.ptwRead.resp.bits

  // ---------------- satp ----------------
  val sv32 = io.fromCsr.satp(SATP_MODE_BIT)
  val asid = io.fromCsr.satp(SATP_ASID_HI, SATP_ASID_LO)

  // ---------------- 状态机 ----------------
  val sIdle :: sWalk :: sDrain :: sResp :: Nil = Enum(4)
  val state = RegInit(sIdle)

  val curPort      = RegInit(0.U(1.W))      // 0 = 取指，1 = 访存
  val lastServ     = RegInit(0.U(1.W))      // 轮转仲裁：上次服务的端口
  val reqVaddr     = RegInit(0.U(XLEN.W))
  val reqLsuOp     = RegInit(0.U(LsuOp.width.W))
  val reqAcc       = RegInit(0.U(MmuAccType.width.W))
  val reqPriv      = RegInit(0.U(privLen.W))
  val respPaddr    = RegInit(0.U(XLEN.W))
  val respMisalign = RegInit(false.B)
  val respPageFlt  = RegInit(false.B)
  val respAccessFlt = RegInit(false.B)

  // ---------------- 输入仲裁（轮转） ----------------
  val prioIcache = !lastServ
  val acceptI = io.fromIcache.valid && io.fromIcache.ready
  val acceptM = io.fromMem.valid    && io.fromMem.ready

  io.fromIcache.ready := (state === sIdle) && io.fromIcache.valid &&
    (!io.fromMem.valid || prioIcache)
  io.fromMem.ready := (state === sIdle) && io.fromMem.valid &&
    (!io.fromIcache.valid || !prioIcache)

  val inVaddr = Mux(acceptI, io.fromIcache.bits.vaddr, io.fromMem.bits.vaddr)
  val inPort  = Mux(acceptI, 0.U(1.W), 1.U(1.W))
  val inAcc   = Mux(acceptI, MmuAccType.fetch,
                Mux(LsuOp.isStore(io.fromMem.bits.lsuOp), MmuAccType.store, MmuAccType.load))
  val inPriv  = Mux(acceptI, io.fromCsr.priv, io.fromCsr.dataPriv)

  // ---------------- 组合 TLB 查找 ----------------
  tlb.io.lookup.req.vaddr := inVaddr
  tlb.io.lookup.req.asid  := asid
  tlb.io.lookup.req.acc   := inAcc

  val tlbHit      = tlb.io.lookup.resp.hit
  val tlbHitPaddr = Cat(tlb.io.lookup.resp.ppn, inVaddr(pageOffLen - 1, 0))
  val needWalk    = sv32 && !tlbHit

  // ---------------- 权限检查 ----------------
  def permFault(r: Bool, w: Bool, x: Bool, u: Bool, acc: UInt, priv: UInt): Bool = {
    val sum = io.fromCsr.sum
    val mxr = io.fromCsr.mxr
    val accessOk = Mux(acc === MmuAccType.store, w,
                    Mux(acc === MmuAccType.fetch, x,
                      r || (mxr && x)))
    val privOk = Mux(priv === PRIV_U_VAL.U, u,
                  Mux(priv === PRIV_S_VAL.U,
                    !u || (sum && (acc =/= MmuAccType.fetch)),
                    true.B))
    !(accessOk && privOk)
  }

  // 访存地址非对齐（按访问宽度）
  def memMisalign(vaddr: UInt, lsuOp: UInt): Bool =
    Mux(LsuOp.isByte(lsuOp), false.B,
      Mux(LsuOp.isHalf(lsuOp), vaddr(0),
        vaddr(1, 0) =/= 0.U))

  def fetchMisalign(vaddr: UInt): Bool = vaddr(1, 0) =/= 0.U

  // MMIO 区域不可缓存（与 chiplab SoC 地址映射一致的简化实现）
  def isCacheable(paddr: UInt): Bool = paddr(31, 16) =/= 0xbfaf.U

  // ---------------- PTW 请求默认值 ----------------
  ptw.io.req.valid       := false.B
  ptw.io.req.bits.vaddr  := inVaddr
  ptw.io.req.bits.acc    := inAcc
  ptw.io.req.bits.satp   := io.fromCsr.satp

  tlb.io.fill.valid := false.B
  tlb.io.fill.vpn   := 0.U
  tlb.io.fill.asid  := asid
  tlb.io.fill.ppn   := 0.U
  tlb.io.fill.r     := false.B
  tlb.io.fill.w     := false.B
  tlb.io.fill.x     := false.B
  tlb.io.fill.u     := false.B
  tlb.io.fill.g     := false.B

  // PTW 返回结果的权限检查
  val ptwPermFault = permFault(ptw.io.resp.bits.r, ptw.io.resp.bits.w,
                               ptw.io.resp.bits.x, ptw.io.resp.bits.u,
                               reqAcc, reqPriv)

  switch(state) {
    is(sIdle) {
      when(acceptI || acceptM) {
        curPort  := inPort
        lastServ := inPort
        reqVaddr := inVaddr
        reqLsuOp := io.fromMem.bits.lsuOp
        reqAcc   := inAcc
        reqPriv  := inPriv
        when(!needWalk) {
          // Bare 模式或 TLB 命中
          respPaddr     := Mux(sv32, tlbHitPaddr, inVaddr)
          respMisalign  := Mux(acceptI, fetchMisalign(inVaddr),
                               memMisalign(inVaddr, io.fromMem.bits.lsuOp))
          respPageFlt   := sv32 && permFault(tlb.io.lookup.resp.r, tlb.io.lookup.resp.w,
                                             tlb.io.lookup.resp.x, tlb.io.lookup.resp.u,
                                             inAcc, inPriv)
          respAccessFlt := false.B
          state         := sResp
        }.otherwise {
          ptw.io.req.valid := true.B
          when(ptw.io.req.fire) { state := sWalk }
        }
      }
    }

    is(sWalk) {
      when(ptw.io.resp.valid) {
        val pr = ptw.io.resp.bits
        when(!pr.fault) {
          tlb.io.fill.valid := true.B
          tlb.io.fill.vpn   := pr.vpn
          tlb.io.fill.asid  := asid
          tlb.io.fill.ppn   := pr.ppn
          tlb.io.fill.r     := pr.r
          tlb.io.fill.w     := pr.w
          tlb.io.fill.x     := pr.x
          tlb.io.fill.u     := pr.u
          tlb.io.fill.g     := pr.g
        }
        respPaddr     := pr.paddr
        respMisalign  := Mux(curPort === 0.U, fetchMisalign(reqVaddr),
                             memMisalign(reqVaddr, reqLsuOp))
        respPageFlt   := pr.fault || ptwPermFault
        respAccessFlt := false.B
        state         := sResp
      }
    }

    is(sDrain) {
      // 等待被放弃的页表遍历结束，保证 PTW 回到空闲
      when(ptw.io.resp.valid) { state := sIdle }
    }

    is(sResp) {
      when(Mux(curPort === 0.U, io.toIcache.fire, io.toMem.fire)) {
        state := sIdle
      }
    }
  }

  // ---------------- 重定向冲刷 ----------------
  val curFlushed = Mux(curPort === 0.U, io.fromIcacheFlush, io.fromMemFlush)
  when(state =/= sIdle && curFlushed) {
    // 若页表遍历恰在本周期返回，则直接回空闲；否则进入排空态等待
    state := Mux(state === sWalk && !ptw.io.resp.valid, sDrain, sIdle)
  }

  // ---------------- 输出 ----------------
  io.toIcache.valid           := (state === sResp) && (curPort === 0.U)
  io.toIcache.bits.paddr      := respPaddr
  io.toIcache.bits.cacheable  := isCacheable(respPaddr)
  io.toIcache.bits.error.misalign    := respMisalign
  io.toIcache.bits.error.pageFault   := respPageFlt
  io.toIcache.bits.error.accessFault := respAccessFlt
  io.toIcache.bits.hasError   := respMisalign || respPageFlt || respAccessFlt

  io.toMem.valid              := (state === sResp) && (curPort === 1.U)
  io.toMem.bits.paddr         := respPaddr
  io.toMem.bits.cacheable     := isCacheable(respPaddr)
  io.toMem.bits.error.misalign    := respMisalign
  io.toMem.bits.error.pageFault   := respPageFlt
  io.toMem.bits.error.accessFault := respAccessFlt
  io.toMem.bits.hasError      := respMisalign || respPageFlt || respAccessFlt

  // ---------------- TLB 冲刷 ----------------
  tlb.io.flush := io.tlbFlush
  io.tlbFillIdx := tlb.io.fillIdx
}
