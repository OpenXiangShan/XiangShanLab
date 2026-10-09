package minixiangshan.backend.execute

import chisel3._
import minixiangshan.backend.decode.BruOp
import minixiangshan.config.{NSModule, Parameters}

class CustomBruUnit(implicit p: Parameters) extends NSModule {
  val io = IO(new Bundle {
    val valid = Input(Bool())
    val op = Input(UInt(BruOp.width.W))
    val inst = Input(UInt(XLEN.W))
    val rs1 = Input(UInt(XLEN.W))
    val rs2 = Input(UInt(XLEN.W))
    val taken = Output(Bool())
  })

  io.taken := false.B
}
