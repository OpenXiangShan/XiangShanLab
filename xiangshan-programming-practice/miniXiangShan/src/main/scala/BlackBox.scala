package minixiangshan

import chisel3._
import chisel3.util._
import minixiangshan.axi._
import minixiangshan.config.NSModule
import minixiangshan.config.NSBundle
import minixiangshan.config.Parameters  // 导入Parameters类型
class cache_BlackBox(implicit p: Parameters) extends BlackBox with HasBlackBoxResource {
    val io = IO(new Bundle {
    // 对外AXI3 Master接口（对接转接桥）
      val axi_master = new AXI3MasterIO
      // 对内CPU接口（预留扩展）
      val cpu_if = new Bundle {
        val req_addr  = Input(UInt(32.W)) 
        val req_valid = Input(Bool())     
        val resp_data = Output(UInt(32.W))
        val resp_valid = Output(Bool())   
      }
  })


}

class uncache1_BlackBox(implicit p: Parameters) extends BlackBox with HasBlackBoxResource {
    val io = IO(new Bundle {
    // 对外AXI3 Master接口（对接转接桥）
    val axi_master = new AXI3MasterIO
    // 对内CPU接口（预留扩展）
    val cpu_if = new Bundle {
      val req_addr  = Input(UInt(32.W))  // CPU请求地址
      val req_valid = Input(Bool())     // CPU请求有效
      val resp_data = Output(UInt(32.W))// Cache返回数据
      val resp_valid = Output(Bool())   // Cache返回有效
    }
  })


}
class uncache2_BlackBox(implicit p: Parameters) extends BlackBox with HasBlackBoxResource {
    val io = IO(new Bundle {
    // 对外AXI3 Master接口（对接转接桥）
    val axi_master = new AXI3MasterIO
    // 对内CPU接口（预留扩展）
    val cpu_if = new Bundle {
      val req_addr  = Input(UInt(32.W))  // CPU请求地址
      val req_valid = Input(Bool())     // CPU请求有效
      val resp_data = Output(UInt(32.W))// Cache返回数据
      val resp_valid = Output(Bool())   // Cache返回有效
    }
  })


}



