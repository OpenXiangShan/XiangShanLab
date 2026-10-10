package minixiangshan.frontend.icache

import chisel3._
import chisel3.util._
import minixiangshan.config.Parameters
import minixiangshan.config._
import minixiangshan.mmu._
import minixiangshan.config.NSModule
import minixiangshan.config.NSBundle

class arrayReadData(implicit p: Parameters) extends  NSBundle{

  val cacheLine =
    Vec(nWaysI, new Bundle {
      val has  = Bool()
      val tag  = UInt(tagBitsI.W)
      val data = UInt((blockBytes * 8).W)
    })
}

class ICacheArrayRead(implicit p: Parameters) extends NSBundle {

  val req   = new Bundle{
    val valid = Output(Bool())
    val idx = Output(UInt(idxBitsI.W))
  }

  val resp    = new Bundle{
    val valid = Input(Bool())
    val data  = Input(new arrayReadData)
  }

}

class ICacheArrayWrite(implicit p: Parameters) extends NSBundle {

    val valid = Output( Bool() )
    val idx   = Output( UInt(idxBitsI.W) )
    val way = Output(UInt(wayBitsI.W))
    val tag   = Output( UInt(tagBitsI.W) )
    val data  = Output( UInt((blockBytes * 8).W) )

}

class victimRead(implicit p: Parameters) extends NSBundle {

    val req  = Output(Bool())
    val idx  = Output(UInt(idxBitsI.W))
    val resp = Input(UInt(wayBitsI.W))
}

class victimChange(implicit p: Parameters) extends NSBundle {

    val valid = Output(Bool())
    val idx   = Output(UInt(idxBitsI.W))
    val way   = Output(UInt(wayBitsI.W))
}

//class mmuReadData(implicit p: Parameters) extends  NSBundle{
//    val valid    = Bool()      // 转换结果有效
//    val data =  new Bundle {
//        val paddr    = UInt(32.W)  // 物理地址
//        val uncached = Bool()      // 是否为uncached访问
//        val error    = new FetchMmuError      // 转换错误(如TLB缺失)，我不清楚是否只有这一个异常，如果有很多那这个信号的位数不止一位
//    }
//
//}

class MMURead(implicit p: Parameters) extends NSBundle {
  // 请求
  //val req = Output(new Bundle {
  //  val vaddr = UInt(32.W)     // 虚拟地址
  //  val valid = Bool()         // 转换请求有效
  //})
  //// 响应
  //val resp = Input(new mmuReadData)

  val toMmu = (Decoupled(new IcacheToMmu))
  val fromMmu   = Flipped( Decoupled(new MmuToIcache) )
}

class IcacheResp(implicit p: Parameters) extends NSBundle {

  //val valid  = Output(Bool())
  val instrs = Output(Vec(fetchWidth, UInt(32.W)))
  val instvalids = Output(Vec(fetchWidth, Bool()))

  val addr   = Output(UInt(32.W))  // 返回虚拟地址
  //val miss   = Output(Bool())
  val uncached   = Output(Bool())
  val mmu_error   = new FetchMmuError //异常
}
