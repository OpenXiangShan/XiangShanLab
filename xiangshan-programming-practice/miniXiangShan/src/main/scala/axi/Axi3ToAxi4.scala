package minixiangshan.axi

import chisel3._
import chisel3.util._
import minixiangshan.config._

/**
 * AXI3 (32-bit data, 4-bit ID) to AXI4 (64-bit data, 4-bit ID) adapter.
 *
 * miniXiangShan's L2 bridge is single-outstanding and emits either a 64-byte
 * line burst (16 x 32-bit beats) or a single uncache beat.  The adapter keeps
 * that transaction model intact and only widens/narrows the external bus.  In
 * particular, a line read is returned to the core as 16 consecutive 32-bit
 * beats, so the core-visible L2 timing is not changed by the wider DRAM.
 */
class Axi3ToAxi4(implicit p: Parameters) extends NSModule {
  val io = IO(new Bundle {
    val in  = Flipped(new AXI3MasterIO)
    val out = new AXI4MasterIO
  })

  // -------------------------------------------------------------------------
  // Read path
  // -------------------------------------------------------------------------
  val readActive   = RegInit(false.B)
  val readIsLine   = RegInit(false.B)
  val readAddr     = RegInit(0.U(32.W))
  val readId       = RegInit(0.U(4.W))
  val rHighPending = RegInit(false.B)
  val rHighData    = RegInit(0.U(32.W))
  val rHighId      = RegInit(0.U(4.W))
  val rHighLast    = RegInit(false.B)

  val lineRead = io.in.ar.data.arlen === 15.U && io.in.ar.data.arsize === 2.U
  io.in.ar.arready := io.out.ar.arready && !readActive
  io.out.ar.data.arid    := io.in.ar.data.arid
  io.out.ar.data.araddr  := Mux(lineRead,
    Cat(io.in.ar.data.araddr(31, 6), 0.U(6.W)),
    Cat(io.in.ar.data.araddr(31, 3), 0.U(3.W)))
  io.out.ar.data.arlen   := Mux(lineRead, 7.U, 0.U)
  io.out.ar.data.arsize  := 3.U
  io.out.ar.data.arburst := 1.U
  io.out.ar.data.arlock  := 0.U
  io.out.ar.data.arcache := 0.U
  io.out.ar.data.arprot  := 0.U
  io.out.ar.data.arqos   := 0.U
  io.out.ar.data.aruser  := 0.U
  io.out.ar.data.arvalid := io.in.ar.data.arvalid && !readActive

  val arFire = io.in.ar.data.arvalid && io.in.ar.arready
  when(arFire) {
    readActive := true.B
    readIsLine := lineRead
    readAddr   := io.in.ar.data.araddr
    readId     := io.in.ar.data.arid
    rHighPending := false.B
  }

  // In line mode one AXI4 64-bit beat becomes two core-visible 32-bit beats.
  // The low half is forwarded combinationally; the high half is held for the
  // next cycle.  This keeps a 64-byte line at 16 core beats, not 24.
  io.out.r.rready := Mux(readIsLine, !rHighPending && io.in.r.rready,
    io.in.r.rready)
  io.in.r.data.rvalid := Mux(readIsLine && rHighPending, true.B,
    io.out.r.data.rvalid && readActive)
  io.in.r.data.rid := Mux(readIsLine && rHighPending, rHighId, io.out.r.data.rid)
  io.in.r.data.rdata := Mux(readIsLine,
    Mux(rHighPending, rHighData, io.out.r.data.rdata(31, 0)),
    Mux(readAddr(2), io.out.r.data.rdata(63, 32), io.out.r.data.rdata(31, 0)))
  io.in.r.data.rresp := 0.U
  io.in.r.data.rlast := Mux(readIsLine,
    rHighPending && rHighLast,
    io.out.r.data.rlast)

  val lowHalfFire = readIsLine && !rHighPending &&
    io.out.r.data.rvalid && io.out.r.rready
  when(lowHalfFire) {
    rHighPending := true.B
    rHighData    := io.out.r.data.rdata(63, 32)
    rHighId      := io.out.r.data.rid
    rHighLast    := io.out.r.data.rlast
  }

  val highHalfFire = readIsLine && rHighPending &&
    io.in.r.data.rvalid && io.in.r.rready
  when(highHalfFire) {
    rHighPending := false.B
    when(rHighLast) { readActive := false.B }
  }
  when(!readIsLine && io.out.r.data.rvalid && io.out.r.rready) {
    readActive := false.B
  }

  // -------------------------------------------------------------------------
  // Write path
  // -------------------------------------------------------------------------
  val awLatched  = RegInit(false.B)
  val awIsLine   = RegInit(false.B)
  val awAddr     = RegInit(0.U(32.W))
  val awId       = RegInit(0.U(4.W))
  val wPhase     = RegInit(0.U(1.W))
  val wLoData    = RegInit(0.U(32.W))
  val wLoStrb    = RegInit(0.U(4.W))

  val lineWrite = io.in.aw.data.awlen === 15.U && io.in.aw.data.awsize === 2.U
  io.in.aw.awready := io.out.aw.awready && !awLatched
  io.out.aw.data.awid    := io.in.aw.data.awid
  io.out.aw.data.awaddr  := Mux(lineWrite,
    Cat(io.in.aw.data.awaddr(31, 6), 0.U(6.W)),
    Cat(io.in.aw.data.awaddr(31, 3), 0.U(3.W)))
  io.out.aw.data.awlen   := Mux(lineWrite, 7.U, 0.U)
  io.out.aw.data.awsize  := 3.U
  io.out.aw.data.awburst := 1.U
  io.out.aw.data.awlock  := 0.U
  io.out.aw.data.awcache := 0.U
  io.out.aw.data.awprot  := 0.U
  io.out.aw.data.awqos   := 0.U
  io.out.aw.data.awuser  := 0.U
  io.out.aw.data.awvalid := io.in.aw.data.awvalid && !awLatched

  val awFire = io.in.aw.data.awvalid && io.in.aw.awready
  // W may legally accompany AW in the same cycle; use the unlatched metadata
  // in that case instead of inserting a bubble.
  val writeMetaValid = awLatched || awFire
  val writeLineMeta  = Mux(awLatched, awIsLine, lineWrite)
  val writeAddrMeta  = Mux(awLatched, awAddr, io.in.aw.data.awaddr)
  val writeIdMeta    = Mux(awLatched, awId, io.in.aw.data.awid)

  when(awFire) {
    awLatched := true.B
    awIsLine  := lineWrite
    awAddr    := io.in.aw.data.awaddr
    awId      := io.in.aw.data.awid
    wPhase    := 0.U
  }

  val linePairReady = writeLineMeta && wPhase === 1.U
  io.in.w.wready := writeMetaValid && Mux(writeLineMeta,
    Mux(wPhase === 0.U, true.B, io.out.w.wready),
    io.out.w.wready)

  io.out.w.data.wdata := Mux(writeLineMeta,
    Cat(io.in.w.data.wdata, wLoData),
    Mux(writeAddrMeta(2),
      Cat(io.in.w.data.wdata, 0.U(32.W)),
      Cat(0.U(32.W), io.in.w.data.wdata)))
  io.out.w.data.wstrb := Mux(writeLineMeta,
    Cat(io.in.w.data.wstrb, wLoStrb),
    Mux(writeAddrMeta(2),
      Cat(io.in.w.data.wstrb, 0.U(4.W)),
      Cat(0.U(4.W), io.in.w.data.wstrb)))
  io.out.w.data.wlast := Mux(writeLineMeta, io.in.w.data.wlast, true.B)
  io.out.w.data.wuser := 0.U
  io.out.w.data.wvalid := writeMetaValid && Mux(writeLineMeta,
    wPhase === 1.U,
    true.B)

  val wLowFire = writeMetaValid && writeLineMeta && wPhase === 0.U &&
    io.in.w.data.wvalid && io.in.w.wready
  when(wLowFire) {
    wLoData := io.in.w.data.wdata
    wLoStrb := io.in.w.data.wstrb
    wPhase  := 1.U
  }

  val wHighFire = linePairReady && io.in.w.data.wvalid && io.in.w.wready
  when(wHighFire) {
    wPhase := 0.U
  }

  io.in.b.data.bid    := io.out.b.data.bid
  io.in.b.data.bresp  := io.out.b.data.bresp
  io.in.b.data.bvalid := io.out.b.data.bvalid
  io.out.b.bready     := io.in.b.bready
  when(io.out.b.data.bvalid && io.out.b.bready) {
    awLatched := false.B
    wPhase    := 0.U
  }
}
