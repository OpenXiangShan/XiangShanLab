package minixiangshan.mmu

import chisel3._
import chisel3.util._

import minixiangshan.config._

/* ============================================================================
 *  Sv32 全相联 TLB
 *
 *  · 4KB 页粒度，表项保存 VPN / ASID / PPN 与 RISC-V PTE 权限位
 *  · 组合查找（供取指与访存两个端口使用）
 *  · 页表遍历命中后由 MMU 写入（fill）
 *  · sfence.vma 冲刷（全表 / 按 VPN / 按 ASID）
 * ==========================================================================*/
class Tlb(implicit p: Parameters) extends NSModule with HasCsrParameters {
  val io = IO(new Bundle {
    val lookup = new Bundle {
      val req  = Input(new TlbLookupReq)
      val resp = Output(new TlbLookupResp)
    }
    val fill    = Input(new TlbFill)
    val flush   = Input(new TlbFlush)
    val fillIdx = Output(UInt(tlbIdxLen.W))
  })

  val entries = RegInit(VecInit(Seq.fill(nrTlb)(0.U.asTypeOf(new TlbEntry))))

  // ---------------- 组合查找 ----------------
  val reqVpn = io.lookup.req.vaddr(vpnLen + pageOffLen - 1, pageOffLen)
  val matchVec = VecInit(entries.map { e =>
    e.valid && (e.g || e.asid === io.lookup.req.asid) && (e.vpn === reqVpn)
  })
  val hitE = entries(PriorityEncoder(matchVec))

  io.lookup.resp.hit := matchVec.asUInt.orR
  io.lookup.resp.ppn := hitE.ppn
  io.lookup.resp.r   := hitE.r
  io.lookup.resp.w   := hitE.w
  io.lookup.resp.x   := hitE.x
  io.lookup.resp.u   := hitE.u
  io.lookup.resp.g   := hitE.g

  // ---------------- 填充（优先使用无效表项，否则轮转替换） ----------------
  val invalidVec = VecInit(entries.map(e => !e.valid))
  val rrPtr      = RegInit(0.U(tlbIdxLen.W))
  val freeIdx    = PriorityEncoder(invalidVec)
  val fillIdx    = Mux(invalidVec.asUInt.orR, freeIdx, rrPtr)
  io.fillIdx := fillIdx

  when(io.fill.valid) {
    entries(fillIdx).valid := true.B
    entries(fillIdx).vpn   := io.fill.vpn
    entries(fillIdx).asid  := io.fill.asid
    entries(fillIdx).ppn   := io.fill.ppn
    entries(fillIdx).r     := io.fill.r
    entries(fillIdx).w     := io.fill.w
    entries(fillIdx).x     := io.fill.x
    entries(fillIdx).u     := io.fill.u
    entries(fillIdx).g     := io.fill.g
    rrPtr := Mux(fillIdx === (nrTlb - 1).U, 0.U, fillIdx + 1.U)
  }

  // ---------------- sfence.vma 冲刷 ----------------
  when(io.flush.valid) {
    val flushVpn = io.flush.vaddr(vpnLen + pageOffLen - 1, pageOffLen)
    for (i <- 0 until nrTlb) {
      val vpnHit  = entries(i).vpn === flushVpn
      val asidHit = entries(i).g || (entries(i).asid === io.flush.asid)
      val clr = MuxCase(true.B, Seq(
        (io.flush.all && !io.flush.noAsid)  -> asidHit,
        (io.flush.all && io.flush.noAsid)   -> true.B,
        (!io.flush.all && io.flush.noAsid)  -> vpnHit,
        (!io.flush.all && !io.flush.noAsid) -> (vpnHit && asidHit)
      ))
      when(clr) { entries(i).valid := false.B }
    }
  }
}
