package minixiangshan.backend.execute

import chisel3._
import chisel3.util._
import minixiangshan.config._
import minixiangshan.csr._
import minixiangshan.backend.decode._
import minixiangshan.backend.dispatch.DispatchedInst

// ================================================================
//  Zicsr CSR 执行单元
//
//  CSRRW / CSRRWI : 写 src（不读旧值参与写，旧值回写 rd）
//  CSRRS / CSRRSI : csrNew = csrOld | src
//  CSRRC / CSRRCI : csrNew = csrOld & ~src
//
//  三种形式的返回值都是 CSR 旧值（写入 rd）。
//  CSR 的真正写在提交阶段完成（csrWen/csrWdata 送到提交路径）。
// ================================================================
class CSRUnit(implicit p: Parameters) extends NSModule with HasCsrParameters {
  val io = IO(new Bundle {
    val valid    = Input(Bool())
    val uop      = Input(new DispatchedInst)
    val rs1      = Input(UInt(XLEN.W))   // rs1 寄存器值
    val rs2      = Input(UInt(XLEN.W))   // 备用（未使用）
    val imm      = Input(UInt(XLEN.W))   // CSR 立即数形式下的 uimm
    val csrRdata = Input(UInt(XLEN.W))   // CSR 读回数据

    val result   = Output(UInt(XLEN.W))  // CSR 旧值 -> rd
    val csrWen   = Output(Bool())
    val csrWdata = Output(UInt(XLEN.W))
    val timerInfo = Input(new TimerBundle)
  })

  val op     = io.uop.ctrl.csrOp
  val csrOld = io.csrRdata
  val srcVal = Mux(CsrOp.isImmForm(op), io.imm, io.rs1)

  val csrNew = MuxCase(csrOld, Seq(
    CsrOp.isSwap(op)  -> srcVal,
    CsrOp.isSet(op)   -> (csrOld | srcVal),
    CsrOp.isClear(op) -> (csrOld & ~srcVal)
  ))

  io.result   := csrOld
  io.csrWen   := io.uop.ctrl.csrWen
  io.csrWdata := csrNew
}
