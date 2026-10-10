package minixiangshan.mmu

import chisel3._
import chisel3.util._

import minixiangshan.config._
import minixiangshan.mem.L2cache.L2NativeReadIO

/* ============================================================================
 *  Sv32 硬件页表遍历器
 *
 *  Sv32：两级页表，4KB 页
 *    一级页表基址 = {satp.PPN[19:0], 12'b0}
 *    一级索引     = va[31:22]
 *    二级索引     = va[21:12]
 *    页内偏移     = va[11:0]
 *
 *  PTE 读取通过 L2 的物理读通路（uncache 读）完成，读回数据低 32 位即 PTE。
 *
 *  非叶子 PTE：V=1 且 R=0 且 W=0 且 X=0
 *  叶子   PTE：V=1 且 (R=1 或 X=1)
 *  非法     ：V=0，或 (R=0 且 W=1)
 * ==========================================================================*/
class Ptw(implicit p: Parameters) extends NSModule with HasCsrParameters {
  val io = IO(new Bundle {
    val req  = Flipped(Decoupled(new PtwReq))
    val resp = Valid(new PtwResp)
    val l2   = new L2NativeReadIO(1)
  })

  val sIdle :: sReq :: sWait :: sDone :: Nil = Enum(4)
  val state = RegInit(sIdle)

  val level    = RegInit(1.U(1.W))  // 1 = 一级页表，0 = 二级页表
  val reqVaddr = RegInit(0.U(XLEN.W))
  val rsvSatp  = RegInit(0.U(XLEN.W))
  val ptAddr   = RegInit(0.U(XLEN.W))

  val vpn1 = reqVaddr(vpnLen + pageOffLen - 1, vpnLevelLen + pageOffLen)
  val vpn0 = reqVaddr(vpnLevelLen + pageOffLen - 1, pageOffLen)
  val off  = reqVaddr(pageOffLen - 1, 0)

  // ---- 结果寄存器 ----
  val faultReg = RegInit(false.B)
  val ppnReg   = RegInit(0.U(ppnLen.W))
  val rReg     = RegInit(false.B)
  val wReg     = RegInit(false.B)
  val xReg     = RegInit(false.B)
  val uReg     = RegInit(false.B)
  val gReg     = RegInit(false.B)
  val paddrReg = RegInit(0.U(XLEN.W))
  val vpnReg   = RegInit(0.U(vpnLen.W))

  io.req.ready := state === sIdle

  // ---- L2 物理读 ----
  io.l2.req.valid        := state === sReq
  io.l2.req.bits.id      := 0.U
  io.l2.req.bits.addr    := ptAddr
  io.l2.req.bits.size    := 2.U
  io.l2.req.bits.uncache := true.B
  io.l2.cancel           := false.B
  io.l2.resp.ready       := state === sWait

  // ---- 响应 ----
  io.resp.valid      := state === sDone
  io.resp.bits.fault := faultReg
  io.resp.bits.vpn   := vpnReg
  io.resp.bits.ppn   := ppnReg
  io.resp.bits.r     := rReg
  io.resp.bits.w     := wReg
  io.resp.bits.x     := xReg
  io.resp.bits.u     := uReg
  io.resp.bits.g     := gReg
  io.resp.bits.paddr := paddrReg

  val pte      = io.l2.resp.bits.data(XLEN - 1, 0)
  val pteV     = pte(PTE_V_BIT)
  val pteR     = pte(PTE_R_BIT)
  val pteW     = pte(PTE_W_BIT)
  val pteX     = pte(PTE_X_BIT)
  val pteU     = pte(PTE_U_BIT)
  val pteG     = pte(PTE_G_BIT)
  val ptePpn   = pte(PTE_PPN_LO + ppnLen - 1, PTE_PPN_LO)

  val pteIllegal  = !pteV || (!pteR && pteW)
  val pteLeaf     = pteV && !pteIllegal && (pteR || pteX)
  // 一级叶子为大页（4MB）：要求 PPN 低 10 位为 0
  val superMisalign = (level === 1.U) && (ptePpn(vpnLevelLen - 1, 0) =/= 0.U)

  val ppnForPage = Mux(level === 1.U,
    Cat(ptePpn(ppnLen - 1, vpnLevelLen), vpn0),
    ptePpn)

  switch(state) {
    is(sIdle) {
      when(io.req.fire) {
        reqVaddr := io.req.bits.vaddr
        rsvSatp  := io.req.bits.satp
        level    := 1.U
        // 一级页表地址 = {satp.PPN[19:0], 12'b0} + {vpn1, 2'b00}
        ptAddr   := Cat(io.req.bits.satp(SATP_PPN_LO + ppnLen - 1, SATP_PPN_LO),
                        io.req.bits.vaddr(vpnLen + pageOffLen - 1, vpnLevelLen + pageOffLen),
                        0.U(2.W))
        state    := sReq
      }
    }

    is(sReq) {
      when(io.l2.req.fire) { state := sWait }
    }

    is(sWait) {
      when(io.l2.resp.fire) {
        when(pteIllegal || (superMisalign && pteLeaf)) {
          faultReg := true.B
          state    := sDone
        }.elsewhen(pteLeaf) {
          faultReg := false.B
          ppnReg   := ppnForPage
          rReg     := pteR
          wReg     := pteW
          xReg     := pteX
          uReg     := pteU
          gReg     := pteG
          paddrReg := Cat(ppnForPage, off)
          vpnReg   := reqVaddr(vpnLen + pageOffLen - 1, pageOffLen)
          state    := sDone
        }.elsewhen(level === 1.U) {
          // 非叶子：进入二级页表
          ptAddr := Cat(ptePpn, vpn0, 0.U(2.W))
          level  := 0.U
          state  := sReq
        }.otherwise {
          // 二级仍非叶子 -> 页错误
          faultReg := true.B
          state    := sDone
        }
      }
    }

    is(sDone) {
      state := sIdle
    }
  }
}
