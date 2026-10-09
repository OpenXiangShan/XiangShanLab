package minixiangshan.mem.L2cache

import chisel3._
import chisel3.util._
import minixiangshan.config._

/**
  * Observes registered L2 ownership state in parallel with the two-cycle Array
  * read. Wide comparisons terminate at the lookup-result register boundary.
  */
class L2LookupHazard(implicit p: Parameters) extends NSModule {
  private val stbCount = l2IStbEntries + l2DStbEntries

  val io = IO(new Bundle {
    val addr = Input(UInt(XLEN.W))
    val isStb = Input(Bool())
    val isUncacheProbe = Input(Bool())
    val isPrefetch = Input(Bool())

    val stbValid = Input(Vec(stbCount, Bool()))
    val stbAddr = Input(Vec(stbCount, UInt(XLEN.W)))
    val stbData = Input(Vec(stbCount, UInt(l2LineBits.W)))
    val pendingWriteValid = Input(Bool())
    val pendingWriteAddr = Input(UInt(XLEN.W))
    val pendingWriteData = Input(UInt(l2LineBits.W))

    val mshrValid = Input(Vec(l2MshrEntries, Bool()))
    val mshrAddr = Input(Vec(l2MshrEntries, UInt(XLEN.W)))
    val ebValid = Input(Bool())
    val ebAddr = Input(UInt(XLEN.W))

    val stbMatch = Output(Bool())
    val stbForwarded = Output(Bool())
    val stbForwardData = Output(UInt(l2LineBits.W))
    val mshrSetConflict = Output(Bool())
    val ebBlockConflict = Output(Bool())
  })

  private def block(addr: UInt): UInt = addr(XLEN - 1, l2BlockOffBits)
  private def set(addr: UInt): UInt =
    addr(l2BlockOffBits + l2IdxBits - 1, l2BlockOffBits)

  val stbMatchVec = VecInit((0 until stbCount).map(i =>
    io.stbValid(i) && block(io.stbAddr(i)) === block(io.addr)))
  val bufferedStbMatch = stbMatchVec.asUInt.orR
  val pendingWriteMatch =
    io.pendingWriteValid && block(io.pendingWriteAddr) === block(io.addr)

  io.stbMatch := pendingWriteMatch || bufferedStbMatch
  io.stbForwarded := io.stbMatch && !io.isStb &&
    !io.isUncacheProbe && !io.isPrefetch
  io.stbForwardData := Mux(pendingWriteMatch, io.pendingWriteData,
    Mux(bufferedStbMatch, io.stbData(PriorityEncoder(stbMatchVec)), 0.U))

  io.mshrSetConflict := VecInit((0 until l2MshrEntries).map(i =>
    io.mshrValid(i) && set(io.mshrAddr(i)) === set(io.addr))).asUInt.orR
  io.ebBlockConflict := io.ebValid && block(io.ebAddr) === block(io.addr)
}
