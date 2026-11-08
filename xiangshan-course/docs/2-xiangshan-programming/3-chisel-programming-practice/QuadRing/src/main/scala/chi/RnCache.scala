package chi

import chisel3._

/** Minimal cache-state encoding used by the RN direct-mapped line store. */
object RnCacheState {
  val I = 0.U(2.W)
  val SC = 1.U(2.W)
  val UC = 2.U(2.W)
  val UD = 3.U(2.W)
}
