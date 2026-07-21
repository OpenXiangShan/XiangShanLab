package device

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config.Parameters
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.amba.axi4._

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config.Parameters
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.amba.axi4._
import freechips.rocketchip.tilelink._

import freechips.rocketchip.util._
import _root_.circt.stage.ChiselStage
import chisel3.experimental.BundleLiterals.AddBundleLiteralConstructor
import chisel3.Data.DataEquality

/**
  * 可综合的AXI4只读Memory
  * 极简实现，立即响应读请求
  */
class AXI4Memory(
  address: Seq[AddressSet],
  size: BigInt = 0x10000000L,
  executable: Boolean = true,
  beatBytes: Int = 8
)(implicit p: Parameters) extends AXI4SlaveModule(address, executable, beatBytes) {
  
  override lazy val module = new AXI4SlaveModuleImp(this) {
    // 打印内存信息
    val baseHex = address.head.base.toString(16)
    val endAddr = address.head.base + address.head.mask
    val endHex = endAddr.toString(16)
    //println(s"[AXI4Memory] Address range: 0x$baseHex to 0x$endHex")
    //println(s"[AXI4Memory] Size: $size bytes, BeatBytes: $beatBytes")
    
    // 内存参数
    val memDepth = 1024  // 固定大小，1024个条目
    val dataWidth = beatBytes * 8
    
    // 同步存储器
    val memory = SyncReadMem(memDepth, UInt(dataWidth.W))
    
    // 状态定义
    val sIdle :: sRead :: Nil = Enum(2)
    val mystate = RegInit(sIdle)
    
    // 寄存器
    val rId = Reg(UInt(in.params.idBits.W))
    val rAddrBase = Reg(UInt(64.W))
    val rLen = Reg(UInt(8.W))
    val rCount = RegInit(0.U(8.W))
    val rResp = RegInit(0.U(2.W))
    
    // AR通道
    in.ar.ready := (mystate === sIdle)
    
    // 处理AR握手
    when(in.ar.fire) {
      val addr = in.ar.bits.addr
      val id = in.ar.bits.id
      val len = in.ar.bits.len
      
      // 地址有效性检查
      val addrValid = addr >= address.head.base.U && 
                     addr < (address.head.base + size).U
      
      when(addrValid) {
        rId := id
        rAddrBase := addr
        rLen := len
        rCount := 0.U
        rResp := 0.U  // OKAY响应
        mystate := sRead
        
        ////println(s"[AXI4Memory] AR accepted: id=$id, addr=0x${addr.litValue.toString}, len=$len")
      }.otherwise {
        // 地址错误，立即返回错误响应
        rId := id
        rResp := 2.U  // DECERR错误
        rCount := 0.U
        rLen := 0.U
        mystate := sRead
        
        //println(s"[AXI4Memory] ERROR: Invalid address 0x${addr.litValue.toString}")
      }
    }
    
    // R通道
    in.r.valid := (mystate === sRead)
    in.r.bits.id := rId
    in.r.bits.resp := rResp
    in.r.bits.last := (rCount === rLen)
    
    // 计算内存地址
    val memIdx = Mux(rResp === 0.U,  // 只有地址有效时才读取内存
      (rAddrBase - address.head.base.U) >> log2Ceil(beatBytes).U + rCount,
      0.U
    )
    
    val memData = memory.read(memIdx, in.r.ready && in.r.valid && rResp === 0.U)
    in.r.bits.data := Mux(rResp === 0.U, memData, 0.U)
    
    // 更新计数器
    when(mystate === sRead && in.r.fire) {
      when(rCount === rLen) {
        mystate := sIdle
        //println(s"[AXI4Memory] Read burst completed")
      }.otherwise {
        rCount := rCount + 1.U
      }
    }
    
    // 写通道 - 永远不接收
    in.aw.ready := false.B
    in.w.ready := false.B
    in.b.valid := false.B
    in.b.bits.resp := 0.U
    in.b.bits.id := 0.U
    
    // 调试信息
    when(in.r.fire) {
      //println(s"[AXI4Memory] R: id=${rId.litValue}, data=0x${in.r.bits.data.litValue.toString}, " +
      //  s"resp=${rResp.litValue}, last=${in.r.bits.last.litValue}")
    }
    
    // 初始化内存内容
    when(reset.asBool) {
      // 可以用测试数据初始化内存
      for (i <- 0 until math.min(memDepth, 16)) {
        val data = (0xDEADBEEFL + i * 0x1000L).U(64.W)
        memory.write(i.U, data)
      }
    }
  }
}