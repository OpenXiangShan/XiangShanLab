package minixiangshan.mem.L2cache

import chisel3._
import chisel3.util._
import minixiangshan.config._

object L2LookupBusyScoreboard {
  val Entries = 4
  val SlotBits = log2Ceil(Entries)
}

class L2LookupBusyScoreboard(val queryPorts: Int)(implicit p: Parameters)
    extends NSModule {
  import L2LookupBusyScoreboard._

  require(queryPorts > 0, "lookup busy scoreboard needs at least one query port")

  val io = IO(new Bundle {
    val querySet = Input(Vec(queryPorts, UInt(l2IdxBits.W)))
    val queryBusy = Output(Vec(queryPorts, Bool()))
    val allocate = Flipped(Valid(UInt(l2IdxBits.W)))
    val allocateSlot = Output(UInt(SlotBits.W))
    val release = Flipped(Valid(UInt(SlotBits.W)))
    val hasFree = Output(Bool())
    val anyBusy = Output(Bool())
  })

  private val slotValid = RegInit(VecInit(Seq.fill(Entries)(false.B)))
  private val slotSet = Reg(Vec(Entries, UInt(l2IdxBits.W)))
  private val freeMask = VecInit(slotValid.map(valid => !valid)).asUInt

  io.hasFree := freeMask.orR
  io.anyBusy := slotValid.asUInt.orR
  io.allocateSlot := Mux(io.hasFree, PriorityEncoder(freeMask), 0.U)

  for (query <- 0 until queryPorts) {
    io.queryBusy(query) := VecInit((0 until Entries).map { slot =>
      slotValid(slot) && slotSet(slot) === io.querySet(query)
    }).asUInt.orR
  }

  when(io.release.valid) {
    assert(slotValid(io.release.bits), "released lookup busy slot must be valid")
    slotValid(io.release.bits) := false.B
  }

  when(io.allocate.valid) {
    assert(io.hasFree, "lookup busy scoreboard overflow")
    assert(!(io.release.valid && io.release.bits === io.allocateSlot),
      "allocation must not reuse a slot until the cycle after release")
    slotValid(io.allocateSlot) := true.B
    slotSet(io.allocateSlot) := io.allocate.bits
  }
}
