package minixiangshan.config

import chisel3._
import chisel3.util._
import chisel3.experimental

object DebugPrint {
  // 最精简的打印函数
  def debugPrint(cond: Bool, fmt: String, data: Bits*): Unit = {
    when(cond) {
      // 使用 Chisel 的内置 printf，这会在 Verilog 中生成 $display
      printf(fmt, data: _*)
    }
  }
  
  // 简化的版本，总是打印
  //def debugPrint(fmt: String, data: Bits*): Unit = {
  //  debugPrint(true.B, fmt, data: _*)
  //}
}
