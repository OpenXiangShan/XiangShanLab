package minixiangshan.backend.issue
 
import chisel3._
import chisel3.util._
import minixiangshan.config._
import minixiangshan.backend.dispatch._
import minixiangshan.backend.rename._
 
// ════════════════════════════════════════════════════════════════
//  DataSource 编码：bypass 数据来源（2 值，Option A）
// ════════════════════════════════════════════════════════════════
object DataSource {
  val exeUnit = 0.U  // 从 ExeUnit Phase2 结果旁路
  val regFile = 1.U  // 从 PRF 读数据（含写前推）
  val width   = 1
}
 
// ════════════════════════════════════════════════════════════════
//  写回唤醒广播信号（原有，不变）
// ════════════════════════════════════════════════════════════════
class IssueWakeup(implicit p: Parameters) extends NSBundle {
  val pdst = UInt(PhyRegIdxWidth.W)
}
 
// ════════════════════════════════════════════════════════════════
//  快速唤醒信号：IQ fire 时发出（仅 ALU/BRU/CSR）
// ════════════════════════════════════════════════════════════════
class WakeupSignal(implicit p: Parameters) extends NSBundle {
  val valid     = Bool()
  val exeSource = UInt(log2Ceil(IQNum).W)  // 产生结果的 ExeUnit 端口编号
  val pdst      = UInt(PhyRegIdxWidth.W)   // 写入的物理目的寄存器
}
 
// ════════════════════════════════════════════════════════════════
//  IQ → RegisterRead 接口（携带 dataSource / exeSource）
// ════════════════════════════════════════════════════════════════
class RegReadIssue(implicit p: Parameters) extends NSBundle {
  val uop            = new DispatchedInst
  val src1DataSource = UInt(DataSource.width.W)
  val src2DataSource = UInt(DataSource.width.W)
  val src1ExeSource  = UInt(log2Ceil(IQNum).W)
  val src2ExeSource  = UInt(log2Ceil(IQNum).W)
}
 
// ════════════════════════════════════════════════════════════════
//  ExeUnit 旁路结果：供 BypassNetwork 使用
// ════════════════════════════════════════════════════════════════
class BypassResult(implicit p: Parameters) extends NSBundle {
  val valid = Bool()
  val data  = UInt(XLEN.W)
  val pdst  = UInt(PhyRegIdxWidth.W)
}
 
// ════════════════════════════════════════════════════════════════
//  IQ 类型标识（不变）
// ════════════════════════════════════════════════════════════════
object IQType {
  val ALU_CSR     = 0
  val ALU_DIV     = 1
  val ALU_MUL_JMP = 2
  val LOAD_STA    = 3
  val STD         = 4
}
 
