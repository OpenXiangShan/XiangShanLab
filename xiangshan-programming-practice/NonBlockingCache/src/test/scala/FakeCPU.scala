import chisel3._
import chiseltest._
import scala.collection.mutable.ArrayBuffer
import scala.util.Random
import utils.DataUtils._  // 导入所有函数
import utils.MemoryData._
import scala.util.Using
import java.io.{PrintWriter, File}
import GlobalVars._
//------------------------------------------------------------
// Fake CPU model (Testbench side only)
//------------------------------------------------------------
class FakeCPU(dut: NonBlockingCache, reqNum: Int) {

  case class CPUReq(
    addr: BigInt,
    rdIdx: Int
  )
  val seed = randomSeed
  private val rng = new Random(seed)

  // 生成热点地址基址
  val hotBases: Vector[BigInt] = Vector.tabulate(8) { i =>
    val tag = BigInt(36, rng) | (BigInt(1) << 35)
    val index = rng.nextInt(1 << 6)
    val base = (tag << 12) | (BigInt(index) << 6)
    base
  }

  // 生成冲突index（只包含index部分，offset为0）
  val conflictIndices: Vector[BigInt] = Vector.tabulate(4) { i =>
    val index = rng.nextInt(1 << 6)
    val base = BigInt(index) << 6  // index<<6，offset部分为0
    base
  }

  // 请求地址地址生成函数
  def randomAddrWithLocality(hotBases: Vector[BigInt], localityRatio: Double = 0.8): BigInt = {
    val offsetBits = 6
    val indexBits = 6
    val tagBits = 36
    val rand = rng.nextDouble()
    if (rand < localityRatio * 0.7 && hotBases.nonEmpty) {
      // 80% * 70% 的局部性：相同tag+index，不同offset
      val base = hotBases(rng.nextInt(hotBases.size))
      val offset = BigInt(rng.nextInt(1 << offsetBits))
      base | offset
    } else if (rand < localityRatio && conflictIndices.nonEmpty) {
      // 80% * 30%的局部性：使用相同index，但生成新的tag
      val baseIndexOnly = conflictIndices(rng.nextInt(conflictIndices.size))
      // 提取index部分
      val index = (baseIndexOnly >> 6) & ((1 << indexBits) - 1)
      val tag = BigInt(tagBits, rng) | (BigInt(1) << (tagBits - 1))
      val offset = BigInt(rng.nextInt(1 << offsetBits))
      (tag << (indexBits + offsetBits)) | (index << offsetBits) | offset
    } else {
      // 20% 完全随机生成地址
      val tag = BigInt(tagBits, rng) | (BigInt(1) << (tagBits - 1))
      val index = rng.nextInt(1 << indexBits)
      val offset = BigInt(rng.nextInt(1 << offsetBits))
      (tag << (indexBits + offsetBits)) | (BigInt(index) << offsetBits) | offset
    }
  }

  private val requests = ArrayBuffer[CPUReq]()
  var hotAccessCount = 0
  var randomAccessCount = 0

  for (_ <- 0 until reqNum) {
    val addr = randomAddrWithLocality(hotBases, localityRatio = 0.8)
    val rdIdx = rng.nextInt(32)
    requests += CPUReq(addr, rdIdx)
  }
  //countdown(3, 1)
  println("Generating RAM data in ./TestData/Ram.txt ......")
  printMemory()
  //countdown(3, 2)
  val pw = new PrintWriter(new File("./TestData/Req.txt"))
  println("Generating request data in ./TestData/Req.txt ......")
  pw.println("========== CPU INIT REQUESTS ==========")
  pw.println("This data is generated with a fixed random seed during simulation testing.")
  pw.println("The request data will contain a certain proportion of test hits/replacement cache samples, rather than being completely randomly generated.")
  pw.println("For detailed code, see: src/test/scala/FakeCPU.scala")
  requests.zipWithIndex.foreach { case (r, i) =>
    pw.println(f"REQ$i%02d : addr=0x${r.addr.toString(16)} rd=${r.rdIdx}")
  }
  pw.println("=======================================")
  //countdown(3, 3)
  pw.close()
  println("Start testing ......")


  private var sendPtr = 0
  private var sending = false

  def step(cycle: Int): Unit = {

    //println(s"addr_ok = ${dut.io.addr_ok.peekBoolean()}")
    //println(s"data_ok = ${dut.io.data_ok.peekBoolean()}")
    //println(s"counter = ${dut.io.counter.peekInt()}")

    dut.io.req.poke(false.B)

    if (sending && sendPtr < requests.length) {
      val req = requests(sendPtr)
      dut.io.req.poke(true.B)
      dut.io.addr.poke(req.addr.U)
      dut.io.rd_idx_in.poke(req.rdIdx.U)
      dut.io.sendReq_in.poke(sendPtr.U)

      if (dut.io.addr_ok.peekBoolean()) {
        //println(s"HI3")

        println(f" ")
        println(
          f"[CPU ] SEND     [Time ${dut.io.counter.peekInt()}%4d] REQ [${sendPtr}%3d] addr=0x${req.addr.toString(16)} rd=${req.rdIdx}%2d"
        )
        sendPtr += 1
        if (sendPtr < requests.length){
            sending = rng.nextBoolean() //决定下一个周期还发不发
        }
        else{
            sending = false
        }
      }

    } else {
      if (sendPtr < requests.length) {
        sending = rng.nextBoolean()
      }
    }

    if (dut.io.data_ok.peekBoolean()) {
      val data  = dut.io.rdata.peekInt()
      val rdIdx = dut.io.rd_idx_out.peekInt()
      val sendReq = dut.io.sendReq_out.peekInt()
      println( f" ")
      println(
        f"[CPU ] RECIEVE  [Time ${dut.io.counter.peekInt()}%4d] REQ [${sendReq}%3d] rd=${rdIdx}%2d data=0x${data.toString(16)}"
      )
      val req_verify    = requests(sendReq.toInt )
      val addr_veritfy  = req_verify.addr
      //println(s"  Addr_veritfy   ->  0x${format64Hex(addr_veritfy)}")
      val rdIdx_veritfy = req_verify.rdIdx
      val shifted = addr_veritfy >> 6
      val lineAddr = {
        val mod = shifted % RAM_LINES
        if (mod >= 0) mod.toInt
        else (mod + RAM_LINES).toInt
      }
      val data_veritfy = memory(lineAddr.toInt)
      val bankIndex = ((addr_veritfy >> 3) & 0x7).toInt  // 提取第3-5位（bank选择位）
      // 从512位数据中提取对应的64位bank
      val selectedDataVerify = extractBankFrom512(data_veritfy, bankIndex)
      println("┌──────────────────────────────────────────────────────────────────┐")
      println(f"│ [VERIFY] SendReq: $sendReq%-4d                                           │")
      println("├──────────────────────────────────────────────────────────────────┤")
      println(f"│ Addr        : 0x${format64Hex(addr_veritfy)}                                 │")
      println(f"│ LineIndex   : 0x${format8Hex(lineAddr)}                                               │")
      println(f"│ BankIndex   : 0x${format8Hex(bankIndex)}                                               │")
      println("├──────────────────────────────────────────────────────────────────┤")
      println(f"│ Expected    : Data: 0x${format64Hex(selectedDataVerify)} RdIdx: $rdIdx_veritfy%-2d                 │")
      println(f"│ Actual      : Data: 0x${format64Hex(data)} RdIdx: $rdIdx%-2d                 │")
      println("└──────────────────────────────────────────────────────────────────┘")

      //println(s"  Expected CacheLineData   ->  0x${format512Hex(data_veritfy)}")
      //println(s"  [CPU] lineAddr        ->  0x${format64Hex(lineAddr)}")
      //println(s"  [CPU] CacheLineData   ->  0x${format512Hex(data_veritfy)}")

      if(data == selectedDataVerify && rdIdx == rdIdx_veritfy){
        println(s"    --------> PASS")
      }else{
        println(s"    --------> ERROR!!!")
        sys.error(s"VERIFICATION FAILED!")
      }

      tested_num = tested_num + 1
    }
  }
}
