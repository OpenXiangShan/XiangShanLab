package minixiangshan.mem.L2cache

import chisel3._
import chisel3.util._
import minixiangshan.config._

// Keeps the wide lookup result behind a local register. Only the registered
// head fans out into L2 control, so the storage read pointer cannot become the
// start of a long path through victim, MSHR, Array, and busy-set logic.
class L2ResultQueue(val entries: Int)(implicit p: Parameters) extends NSModule {
  require(entries >= 2, "L2ResultQueue needs storage plus one registered head")

  val io = IO(new Bundle {
    val enq = Flipped(Decoupled(new L2LookupResult))
    val deq = Decoupled(new L2LookupResult)
    val count = Output(UInt(log2Ceil(entries + 1).W))
  })

  // The output register is part of the advertised capacity. A three-entry
  // backing queue plus this head therefore preserves the original depth four.
  val storage = Module(new Queue(
    new L2LookupResult,
    entries - 1,
    pipe = true,
    flow = false))
  val headValid = RegInit(false.B)
  val headBits = RegInit(0.U.asTypeOf(new L2LookupResult))
  val advanceHead = !headValid || io.deq.ready

  storage.io.enq <> io.enq
  storage.io.deq.ready := advanceHead
  when(advanceHead) {
    headValid := storage.io.deq.valid
    when(storage.io.deq.valid) {
      headBits := storage.io.deq.bits
    }
  }

  io.deq.valid := headValid
  io.deq.bits := headBits
  io.count := storage.io.count +& headValid.asUInt
}
