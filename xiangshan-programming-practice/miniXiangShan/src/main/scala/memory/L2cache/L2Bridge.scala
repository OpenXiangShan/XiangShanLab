package minixiangshan.mem.L2cache

import chisel3._
import chisel3.util._
import minixiangshan.axi._
import minixiangshan.config._

class L2Bridge(implicit p: Parameters) extends NSModule {
  val io = IO(new Bundle {
    val client = Flipped(new L2BridgeClientIO)
    val axi = new AXI3MasterIO
  })

  val idle :: readAddr :: readData :: writeChannels :: writeResp :: Nil = Enum(5)
  val state = RegInit(idle)

  val readCmd = Reg(new L2BridgeReadCmd)
  val writeCmd = Reg(new L2BridgeWriteCmd)
  val writeBeat = RegInit(0.U(l2BeatIdxBits.W))
  val awDone = RegInit(false.B)
  val wDone = RegInit(false.B)
  val writeWords = writeCmd.data.asTypeOf(Vec(l2BurstBeats, UInt(XLEN.W)))

  io.client.read.req.ready := state === idle && !io.client.write.req.valid
  io.client.write.req.ready := state === idle

  io.client.read.beat.valid := false.B
  io.client.read.beat.bits.owner := readCmd.owner
  io.client.read.beat.bits.data := io.axi.r.data.rdata
  io.client.read.beat.bits.last := io.axi.r.data.rlast

  io.client.write.done.valid := false.B
  io.client.write.done.bits.owner := writeCmd.owner

  io.axi.ar.data.arid := 0.U
  io.axi.ar.data.araddr := Mux(
    readCmd.isLine,
    Cat(readCmd.addr(XLEN - 1, l2BlockOffBits), 0.U(l2BlockOffBits.W)),
    readCmd.addr
  )
  io.axi.ar.data.arlen := Mux(readCmd.isLine, (l2BurstBeats - 1).U, 0.U)
  io.axi.ar.data.arsize := Mux(readCmd.isLine, 2.U, readCmd.size)
  io.axi.ar.data.arburst := Mux(readCmd.isLine, 1.U, 0.U)
  io.axi.ar.data.arlock := 0.U
  io.axi.ar.data.arcache := 0.U
  io.axi.ar.data.arprot := 0.U
  io.axi.ar.data.arvalid := state === readAddr
  io.axi.r.rready := state === readData && io.client.read.beat.ready

  io.axi.aw.data.awid := 0.U
  io.axi.aw.data.awaddr := Mux(
    writeCmd.isLine,
    Cat(writeCmd.addr(XLEN - 1, l2BlockOffBits), 0.U(l2BlockOffBits.W)),
    writeCmd.addr
  )
  io.axi.aw.data.awlen := Mux(writeCmd.isLine, (l2BurstBeats - 1).U, 0.U)
  io.axi.aw.data.awsize := Mux(writeCmd.isLine, 2.U, writeCmd.size)
  io.axi.aw.data.awburst := Mux(writeCmd.isLine, 1.U, 0.U)
  io.axi.aw.data.awlock := 0.U
  io.axi.aw.data.awcache := 0.U
  io.axi.aw.data.awprot := 0.U
  io.axi.aw.data.awvalid := state === writeChannels && !awDone

  io.axi.w.data.wid := 0.U
  io.axi.w.data.wdata := writeWords(writeBeat)
  io.axi.w.data.wstrb := Mux(writeCmd.isLine, Fill(l2BeatBytes, 1.U(1.W)), writeCmd.strb)
  io.axi.w.data.wlast := !writeCmd.isLine || writeBeat === (l2BurstBeats - 1).U
  io.axi.w.data.wvalid := state === writeChannels && !wDone

  io.axi.b.bready := false.B

  // A write wins when read and write requests arrive together.
  when(state === idle) {
    when(io.client.write.req.fire) {
      writeCmd := io.client.write.req.bits
      writeBeat := 0.U
      awDone := false.B
      wDone := false.B
      state := writeChannels
    }.elsewhen(io.client.read.req.fire) {
      readCmd := io.client.read.req.bits
      state := readAddr
    }
  }

  when(state === readAddr && io.axi.ar.data.arvalid && io.axi.ar.arready) {
    state := readData
  }

  // Forward R beats with the client's backpressure.
  when(state === readData) {
    io.client.read.beat.valid := io.axi.r.data.rvalid
    when(io.axi.r.data.rvalid && io.axi.r.rready && io.axi.r.data.rlast) {
      state := idle
    }
  }

  val awFire = io.axi.aw.data.awvalid && io.axi.aw.awready
  val wFire = io.axi.w.data.wvalid && io.axi.w.wready
  val lastWFire = wFire && io.axi.w.data.wlast

  // AW和W独立握手，任何一个通道都不能等待另一个通道的READY。
  when(state === writeChannels && awFire) {
    awDone := true.B
  }
  when(state === writeChannels && wFire) {
    when(io.axi.w.data.wlast) {
      wDone := true.B
    }.otherwise {
      writeBeat := writeBeat + 1.U
    }
  }
  when(state === writeChannels && (awDone || awFire) && (wDone || lastWFire)) {
    state := writeResp
  }

  // Complete the write only when B and done handshake together.
  when(state === writeResp) {
    io.client.write.done.valid := io.axi.b.data.bvalid
    io.axi.b.bready := io.client.write.done.ready
    when(io.axi.b.data.bvalid && io.axi.b.bready) {
      state := idle
    }
  }
}
