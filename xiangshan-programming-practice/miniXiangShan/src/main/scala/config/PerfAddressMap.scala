package minixiangshan.config

import chisel3._

/**
 * Physical address map used by the performance-comparison platform.
 *
 * The MMIO ranges intentionally match NutShell's `AddressSpace`:
 *   0x3000_0000 - 0x3fff_ffff : internal devices (CLINT/PLIC)
 *   0x4000_0000 - 0x7fff_ffff : external devices (UART/perf registers)
 *   0x8000_0000 -             : DRAM
 */
object PerfAddressMap {
  val DramBase: BigInt = BigInt("80000000", 16)
  val PerfBase: BigInt = BigInt("4000f000", 16)

  def isMMIO(paddr: UInt): Bool = {
    val internal = (paddr ^ "h30000000".U(32.W))(31, 28) === 0.U
    val external = (paddr ^ "h40000000".U(32.W))(31, 30) === 0.U
    internal || external
  }

  def isCacheable(paddr: UInt): Bool = !isMMIO(paddr)
}
