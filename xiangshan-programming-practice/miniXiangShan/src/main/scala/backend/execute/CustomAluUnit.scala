package minixiangshan.backend.execute

import chisel3._
import minixiangshan.backend.decode.AluOp
import minixiangshan.config.{NSModule, Parameters}

class CustomAluUnit(implicit p: Parameters) extends NSModule {
  val io = IO(new Bundle {
    val valid = Input(Bool())
    val op = Input(UInt(AluOp.width.W))
    val inst = Input(UInt(XLEN.W))
    val rs1 = Input(UInt(XLEN.W))
    val rs2 = Input(UInt(XLEN.W))
    val result = Output(UInt(XLEN.W))
  })

  io.result := 0.U
}
