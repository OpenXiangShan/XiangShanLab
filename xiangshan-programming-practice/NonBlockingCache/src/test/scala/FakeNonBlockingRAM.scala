import scala.collection.mutable
import scala.util.Random
import chiseltest._
import chisel3._
import utils.DataUtils._  // 导入tool中所有函数
import utils.MemoryData._
import scala.math.BigInt
import GlobalVars._
class FakeNonBlockingRAM(dut: NonBlockingCache) {

  val RAM_LINES  = 64
  val BURST_SIZE = 8  // 突发传输大小，8个64位数据
  val seed = randomSeed
  val rng = new Random(seed)
  
  case class PendingReq(
    addr: BigInt,
    rdaddr: BigInt,
    sendReq: BigInt,
    readyCycle: Long
  )

  val pending = mutable.ArrayBuffer[PendingReq]()

  // 突发传输状态
  case class BurstState(
    addr: BigInt,
    rdaddr: BigInt,
    sendReq: BigInt,
    data: BigInt,        // 512位数据
    burstCount: Int,     // 已传输的burst数
    startCycle: Long
  )

  var burstState: Option[BurstState] = None

  def randDelay(min: Int = 1, max: Int = 10): Int = {
    rng.nextInt(max - min + 1) + min
  }

  //--------------------------------------------------
  // main RAM behavior (call every cycle)
  //--------------------------------------------------
  def step(cycle: Long): Unit = {

    dut.io.mem_addr_ok.poke(false.B)
    dut.io.mem_data_ok.poke(false.B)
    dut.io.mem_data_valid.poke(false.B)
    dut.io.mem_rdata.poke(0.U)

    //------------------------------------------------
    // 处理突发传输
    //------------------------------------------------
    burstState match {
      case Some(state) =>
        // 突发传输进行中
        val bankIdx = state.burstCount
        if (bankIdx < BURST_SIZE) {

          val bankData = extractBankFrom512(state.data, bankIdx)
          // 设置输出信号
          dut.io.mem_data_valid.poke(true.B)
          dut.io.mem_rdata.poke(bankData.U)
          //dut.io.mem_rdata_512.poke(state.data.U)
          //dut.io.mem_addr_back.poke(state.addr.U)
          //dut.io.mem_rdaddr_back.poke(state.rdaddr.U)
          dut.io.mem_sendReq_back.poke(state.sendReq.U)
    
          // 如果是最后一个数据，设置data_ok
          if (bankIdx == BURST_SIZE - 1) {
            dut.io.mem_data_ok.poke(true.B)
          }
          // 更新burst状态
          burstState = Some(state.copy(burstCount = bankIdx + 1))
        } else {
          // 突发传输完成，清除状态
          burstState = None
        }
        
      case None =>

    }

    /*===========================================
      1. FakeRAM接收来自Cache的请求
    ===========================================*/

    if (dut.io.mem_req.peekBoolean()) {
      // random addr accept
      if (rng.nextBoolean()) {
        val addr = dut.io.mem_addr.peekInt()
        val rdaddr = 0//dut.io.mem_rdaddr.peekInt()
        val sendReq = dut.io.mem_sendReq_out.peekInt()

        dut.io.mem_addr_ok.poke(true.B)
        val ready = cycle + randDelay(5, 20)

        pending += PendingReq(addr, rdaddr, sendReq, ready)
        println(f"[RAM ] RECEIVE  [Time ${dut.io.counter.peekInt()}%4d] REQ [${sendReq}%3d] addr=0x${format64Hex(addr)}")
      }
    }

    /*===========================================
      2. FakeRAM向Cache突发传输出对应的数据
    ===========================================*/
    if (burstState.isEmpty) {
      val readyList = pending.filter(_.readyCycle <= cycle)
      if (readyList.nonEmpty) {
        val idx = rng.nextInt(readyList.length)
        val req = readyList(idx)

        val shifted = req.addr >> 6
        val lineAddr = {
          val mod = shifted % RAM_LINES
          if (mod >= 0) mod.toInt
          else (mod + RAM_LINES).toInt
        }
        val data = memory(lineAddr.toInt)
        //println(s"  [RAM]     Addr        ->  0x${format64Hex(req.addr)}")
        //println(s"  [RAM] lineAddr        ->  0x${format64Hex(lineAddr)}")
        //println(s"  [RAM] CacheLineData   ->  0x${format512Hex(data)}")
        // 开始新的突发传输
        burstState = Some(BurstState(
          addr = req.addr,
          rdaddr = req.rdaddr,
          sendReq = req.sendReq,
          data = data,
          burstCount = 0,
          startCycle = cycle
        ))

        println(
          f"[RAM ] BACKDATA [Time ${dut.io.counter.peekInt()}%4d] REQ [${req.sendReq}%3d] addr=0x${format64Hex(req.addr)}"
        )
        println(
          f"                                      data=0x${format512Hex(data)}"
        )
        
        // 从pending列表中移除
        pending -= req
      }
    }
  }
}