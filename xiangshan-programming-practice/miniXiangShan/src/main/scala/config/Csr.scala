package minixiangshan.config

import chisel3._

/* ============================================================================
 *  RISC-V 特权架构相关常量与字段布局
 *
 *  参考：The RISC-V Instruction Set Manual, Volume II: Privileged Architecture
 *        v1.12（RV32 + M/S/U + Sv32）
 * ==========================================================================*/
object CsrConfigKeys {
  // ---- 特权级编码（2 bit）----
  val PRIV_U = new Field[Int](0)
  val PRIV_S = new Field[Int](1)
  val PRIV_M = new Field[Int](3)

  // ---- mstatus / sstatus 字段位号 ----
  val MSTATUS_SIE  = new Field[Int](1)
  val MSTATUS_MIE  = new Field[Int](3)
  val MSTATUS_SPIE = new Field[Int](5)
  val MSTATUS_MPIE = new Field[Int](7)
  val MSTATUS_SPP  = new Field[Int](8)
  val MSTATUS_MPP  = new Field[(Int, Int)]((12, 11))
  val MSTATUS_MPRV = new Field[Int](17)
  val MSTATUS_SUM  = new Field[Int](18)
  val MSTATUS_MXR  = new Field[Int](19)
  val MSTATUS_TVM  = new Field[Int](20)
  val MSTATUS_TW   = new Field[Int](21)
  val MSTATUS_TSR  = new Field[Int](22)

  // ---- mip / mie 中断位 ----
  val IRQ_SSI = new Field[Int](1)
  val IRQ_MSI = new Field[Int](3)
  val IRQ_STI = new Field[Int](5)
  val IRQ_MTI = new Field[Int](7)
  val IRQ_SEI = new Field[Int](9)
  val IRQ_MEI = new Field[Int](11)

  // ---- mtvec / stvec ----
  val TVEC_BASE = new Field[(Int, Int)]((31, 2))
  val TVEC_MODE = new Field[(Int, Int)]((1, 0))

  // ---- satp ----
  val SATP_MODE = new Field[Int](31)
  val SATP_ASID = new Field[(Int, Int)]((30, 22))
  val SATP_PPN  = new Field[(Int, Int)]((21, 0))

  // ---- Sv32 页表项 PTE ----
  val PTE_V = new Field[Int](0)
  val PTE_R = new Field[Int](1)
  val PTE_W = new Field[Int](2)
  val PTE_X = new Field[Int](3)
  val PTE_U = new Field[Int](4)
  val PTE_G = new Field[Int](5)
  val PTE_A = new Field[Int](6)
  val PTE_D = new Field[Int](7)
  val PTE_PPN = new Field[(Int, Int)]((31, 10))

  // ---- 几何参数 ----
  val VPN_LEN       = new Field[Int](20) // Sv32 虚拟页号总位宽
  val VPN_LEVEL_LEN = new Field[Int](10) // 每一级页表的 VPN 位宽
  val TLB_ASID_LEN  = new Field[Int](9)  // satp.ASID
  // 本实现的物理地址为 32 bit，因此有效 PPN 为 20 bit
  // （PTE 中的 PPN 域仍是 22 bit，高位在 32 bit 物理地址下被忽略）
  val PPN_LEN       = new Field[Int](20) // Sv32 有效物理页号
  val PAGE_OFF_LEN  = new Field[Int](12)
  val CSR_ADDR_LEN  = new Field[Int](12)
  val PRIV_LEN      = new Field[Int](2)
  val IRQ_WIDTH     = new Field[Int](12)
  val TIMER_LEN     = new Field[Int](64)
  val ISA           = new Field[String]("RV32IM")
}

trait HasCsrParameters {
  implicit val p: Parameters

  val PRIV_U_VAL: Int = p(CsrConfigKeys.PRIV_U)
  val PRIV_S_VAL: Int = p(CsrConfigKeys.PRIV_S)
  val PRIV_M_VAL: Int = p(CsrConfigKeys.PRIV_M)

  val MSTATUS_SIE_BIT:  Int = p(CsrConfigKeys.MSTATUS_SIE)
  val MSTATUS_MIE_BIT:  Int = p(CsrConfigKeys.MSTATUS_MIE)
  val MSTATUS_SPIE_BIT: Int = p(CsrConfigKeys.MSTATUS_SPIE)
  val MSTATUS_MPIE_BIT: Int = p(CsrConfigKeys.MSTATUS_MPIE)
  val MSTATUS_SPP_BIT:  Int = p(CsrConfigKeys.MSTATUS_SPP)
  // 注意：CsrConfigKeys 中所有位段都按 (高位, 低位) 存放，所以 _1 = HI，_2 = LO
  val MSTATUS_MPP_LO:   Int = p(CsrConfigKeys.MSTATUS_MPP)._2
  val MSTATUS_MPP_HI:   Int = p(CsrConfigKeys.MSTATUS_MPP)._1
  val MSTATUS_MPRV_BIT: Int = p(CsrConfigKeys.MSTATUS_MPRV)
  val MSTATUS_SUM_BIT:  Int = p(CsrConfigKeys.MSTATUS_SUM)
  val MSTATUS_MXR_BIT:  Int = p(CsrConfigKeys.MSTATUS_MXR)
  val MSTATUS_TVM_BIT:  Int = p(CsrConfigKeys.MSTATUS_TVM)
  val MSTATUS_TW_BIT:   Int = p(CsrConfigKeys.MSTATUS_TW)
  val MSTATUS_TSR_BIT:  Int = p(CsrConfigKeys.MSTATUS_TSR)

  val IRQ_SSI_BIT: Int = p(CsrConfigKeys.IRQ_SSI)
  val IRQ_MSI_BIT: Int = p(CsrConfigKeys.IRQ_MSI)
  val IRQ_STI_BIT: Int = p(CsrConfigKeys.IRQ_STI)
  val IRQ_MTI_BIT: Int = p(CsrConfigKeys.IRQ_MTI)
  val IRQ_SEI_BIT: Int = p(CsrConfigKeys.IRQ_SEI)
  val IRQ_MEI_BIT: Int = p(CsrConfigKeys.IRQ_MEI)

  val TVEC_BASE_LO: Int = p(CsrConfigKeys.TVEC_BASE)._2
  val TVEC_BASE_HI: Int = p(CsrConfigKeys.TVEC_BASE)._1
  val TVEC_MODE_LO: Int = p(CsrConfigKeys.TVEC_MODE)._2
  val TVEC_MODE_HI: Int = p(CsrConfigKeys.TVEC_MODE)._1

  val SATP_MODE_BIT:  Int = p(CsrConfigKeys.SATP_MODE)
  val SATP_ASID_LO:   Int = p(CsrConfigKeys.SATP_ASID)._2
  val SATP_ASID_HI:   Int = p(CsrConfigKeys.SATP_ASID)._1
  val SATP_PPN_LO:    Int = p(CsrConfigKeys.SATP_PPN)._2
  val SATP_PPN_HI:    Int = p(CsrConfigKeys.SATP_PPN)._1

  val PTE_V_BIT: Int = p(CsrConfigKeys.PTE_V)
  val PTE_R_BIT: Int = p(CsrConfigKeys.PTE_R)
  val PTE_W_BIT: Int = p(CsrConfigKeys.PTE_W)
  val PTE_X_BIT: Int = p(CsrConfigKeys.PTE_X)
  val PTE_U_BIT: Int = p(CsrConfigKeys.PTE_U)
  val PTE_G_BIT: Int = p(CsrConfigKeys.PTE_G)
  val PTE_A_BIT: Int = p(CsrConfigKeys.PTE_A)
  val PTE_D_BIT: Int = p(CsrConfigKeys.PTE_D)
  val PTE_PPN_LO: Int = p(CsrConfigKeys.PTE_PPN)._2
  val PTE_PPN_HI: Int = p(CsrConfigKeys.PTE_PPN)._1

  val vpnLen: Int      = p(CsrConfigKeys.VPN_LEN)
  val vpnLevelLen: Int = p(CsrConfigKeys.VPN_LEVEL_LEN)
  val asidLen: Int     = p(CsrConfigKeys.TLB_ASID_LEN)
  val ppnLen: Int      = p(CsrConfigKeys.PPN_LEN)
  val pageOffLen: Int  = p(CsrConfigKeys.PAGE_OFF_LEN)
  val csrAddrLen: Int  = p(CsrConfigKeys.CSR_ADDR_LEN)
  val privLen: Int      = p(CsrConfigKeys.PRIV_LEN)
  val irqWidth: Int    = p(CsrConfigKeys.IRQ_WIDTH)
  val TimerLen: Int    = p(CsrConfigKeys.TIMER_LEN)
  val ISA: String      = p(CsrConfigKeys.ISA)

  /* ------------------------------------------------------------------
   *  CSR 地址表（12 bit）
   * ------------------------------------------------------------------*/
  object csrAddr {
    val ustatus    = 0x000
    val fflags     = 0x001
    val frm        = 0x002
    val fcsr       = 0x003

    val sstatus    = 0x100
    val sie        = 0x104
    val stvec      = 0x105
    val scounteren = 0x106
    val sscratch   = 0x140
    val sepc       = 0x141
    val scause     = 0x142
    val stval      = 0x143
    val sip        = 0x144
    val satp       = 0x180

    val mstatus    = 0x300
    val misa       = 0x301
    val medeleg    = 0x302
    val mideleg    = 0x303
    val mie        = 0x304
    val mtvec      = 0x305
    val mcounteren = 0x306
    val mstatush   = 0x310
    val medelegh   = 0x312
    val midelegh   = 0x313
    val mscratch   = 0x340
    val mepc       = 0x341
    val mcause     = 0x342
    val mtval      = 0x343
    val mip        = 0x344
    val mtinst     = 0x34a
    val mtval2     = 0x34b

    val pmpcfg0    = 0x3a0
    val pmpcfg1    = 0x3a1
    val pmpcfg2    = 0x3a2
    val pmpcfg3    = 0x3a3
    val pmpaddr0   = 0x3b0 // pmpaddr0 .. pmpaddr15 -> 0x3b0 .. 0x3bf

    val mcycle     = 0xb00
    val minstret   = 0xb02
    val mcycleh    = 0xb80
    val minstreth  = 0xb82

    val cycle      = 0xc00
    val time       = 0xc01
    val instret    = 0xc02
    val cycleh     = 0xc80
    val timeh      = 0xc81
    val instreth   = 0xc82

    val mvendorid  = 0xf11
    val marchid    = 0xf12
    val mimpid     = 0xf13
    val mhartid    = 0xf14
    val mconfigptr = 0xf15
  }

  def extractField(data: UInt, hi: Int, lo: Int): UInt = data(hi, lo)

  def setField(data: UInt, value: UInt, hi: Int, lo: Int): UInt = {
    val mask = ((1L << (hi - lo + 1)) - 1L).U << lo
    (data & ~mask) | (value << lo)
  }
}
