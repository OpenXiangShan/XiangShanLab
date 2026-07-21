import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import GlobalVars._
import scala.util.control.Breaks._
class CacheSpec extends AnyFlatSpec with ChiselScalatestTester {

  behavior of "Non-BlockingCache"

  it should "using random delays through randomly generated test cases" in {

    test(new NonBlockingCache(6,36,6))
      .withAnnotations(Seq(WriteVcdAnnotation)) { dut =>

      dut.clock.setTimeout(0)

      val ram = new FakeNonBlockingRAM(dut)
      val cpu = new FakeCPU(dut, TEST_NUM)

      dut.reset.poke(true.B)
      dut.clock.step(10) 
      dut.reset.poke(false.B)
      var stopEarly = false

      for (cycle <- 0 until TEST_CYCEL if !stopEarly) {
        //println(s"---- cycle $cycle ----")
        ram.step(cycle)
        cpu.step(cycle)
        dut.clock.step(1)
        if(tested_num == TEST_NUM){
          stopEarly = true //全部测试结束后立即停止
        }
      }
      
      println(f"Tested Num: $tested_num%-4d  All Test Num: $TEST_NUM%-4d ")

    }
  }

}