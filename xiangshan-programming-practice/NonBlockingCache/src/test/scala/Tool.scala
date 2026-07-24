package utils
import scala.math.BigInt
import scala.util.Random
import scala.util.Using
import java.io.{PrintWriter, File}
//import GlobalVars._

object DataUtils {
  
  //def generateCacheLine(index: Int): BigInt = {
  //  var data = BigInt(0)
  //  for (i <- 0 until 8) {
  //    val bankData = (BigInt(index) << 32) | (BigInt(i) << 16) | 0xCAFEL
  //    data = (data << 64) | bankData
  //  }
  //  data & ((BigInt(1) << 512) - 1)
  //}
  def extractBankFrom512(data512: BigInt, bankIdx: Int): BigInt = {
    require(bankIdx >= 0 && bankIdx < 8, "bankIdx must be 0-7")
    // 512位数据：bank7[511:448] bank6[447:384] ... bank0[63:0]
    val shift = bankIdx * 64  
    (data512 >> shift) & ((BigInt(1) << 64) - 1)
  }

  // 格式化64位数据为16进制字符串，补0对齐
  def format64Hex(data: BigInt): String = {
    val hex = data.toString(16)
    if (hex.length <= 16) "0" * (16 - hex.length) + hex
    else hex.takeRight(16) // 只取后16个字符，防止超过64位
  }
  // 格式化512位数据为16进制字符串，补0对齐
  def format512Hex(data: BigInt): String = {
    val hex = data.toString(16)
    if (hex.length <= 128) "0" * (128 - hex.length) + hex
    else hex.takeRight(128) // 只取后128个字符，防止超过512位
  }

  def format8Hex(data: BigInt): String = {
    val hex = data.toString(16)
    if (hex.length <= 2) "0" * (2 - hex.length) + hex
    else hex.takeRight(2)
  }

  def countdown(seconds: Int, thetype: Int): Unit = {
      if     (thetype == 1) println(s"Print RAM data after $seconds seconds...")
      else if(thetype == 2) println(s"Print request data after $seconds seconds...")
      else if(thetype == 3) println(s"Test will start in $seconds seconds...")
    
  
    for (i <- seconds to 1 by -1) {
      println(s"⏰ $i...")
      Thread.sleep(1000)  // 暂停1秒
    }
  }
}

object MemoryData {

  // 使用固定种子
  val seed = 5454
  private val rng = new Random(seed)
  
  val RAM_LINES = 64 //index不只有6位嘛
  
  // 共享的memory数组
  lazy val memory: Array[BigInt] = Array.tabulate(RAM_LINES) { i =>
    BigInt(512, rng)
  }
  
  def printMemory(): Unit = {
    val pw = new PrintWriter(new File("./TestData/Ram.txt"))
    pw.println("Generated with a random seed during test program execution")
    pw.println("For detailed code, see: src/test/scala/Tool.scala")
    pw.println("╔══════════════════════════════════════════╗")
    pw.println("║      SYSTEM RAM CONTENTS (512-bit)       ║")
    pw.println("╠════════╤═════════════════════════════════╣")
    pw.println("║ Addr   │ Data                            ║")
    pw.println("╟────────┼─────────────────────────────────╢")
    
    memory.zipWithIndex.foreach { case (data, addr) =>
      val hex = data.toString(16)
      val paddedHex = if (hex.length < 128) "0" * (128 - hex.length) + hex else hex
      
      // 每行显示16个字符
      val lines = paddedHex.grouped(16).toArray
      
      pw.println(f"║ 0x$addr%02x   │ ${lines.head}                ║")
      lines.tail.foreach { line =>
        pw.println(f"║        │ $line                ║")
      }
      
      if (addr < RAM_LINES - 1) {
        pw.println( "║────────┼─────────────────────────────────║")
      }
    }
      pw.println( "╚════════╧═════════════════════════════════╝")
      pw.close()
  }
  
  def getMemoryData(index: Int): BigInt = {
    require(index >= 0 && index < RAM_LINES, s"Index $index out of bounds")
    memory(index)
  }
}

