package minixiangshan.frontend.icache

import chisel3._
import chisel3.util._

import minixiangshan.mem.L2cache.L2NativeReadIO

import minixiangshan.config.Parameters
import minixiangshan.config.NSModule
// OK
class ICache(implicit p: Parameters) extends NSModule {
  
  
  val io = IO(new Bundle {
    val redirect = Input(Bool())
    val invalidate = Input(Bool())
    // CPU接口
    val cpu_req = Flipped( Decoupled(new Bundle {
      val addr  = (UInt(32.W))   // 虚拟地址
    }))
    
    val icache_resp = Decoupled(new IcacheResp)

    val l2_read = new L2NativeReadIO(1)

    val mmu = new MMURead
    
  })
  
  // === 子模块实例化 ===
  
  val mainPipe = Module(new ICacheMainPipe)
  val array = Module(new ICacheArray)
  val replacer = Module(new CacheReplacerI)
  
  
  // === 连接CPU接口 ===
  
  mainPipe.io.cpu_req  <> io.cpu_req
  mainPipe.io.icache_resp  <> io.icache_resp
  mainPipe.io.l2_read <> io.l2_read

  mainPipe.io.arrays_read  <> array.io.read
  mainPipe.io.array_write  <> array.io.write
  mainPipe.io.redirect := io.redirect


  array.io.flush.valid := false.B
  array.io.flush.idx := 0.U
  array.io.invalidate := io.invalidate
  //array OK

  mainPipe.io.victim_read  <> replacer.io.victim
  mainPipe.io.replacer_touch  <> replacer.io.touch
  mainPipe.io.mmu <> io.mmu

  //mainPipe OK

  replacer.io.flush.valid := false.B
  replacer.io.flush.idx := 0.U

  //replacer OK
}
