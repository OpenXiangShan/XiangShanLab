package minixiangshan.mem.L2cache

import chisel3._
import chiseltest._
import minixiangshan.config._
import org.scalatest.flatspec.AnyFlatSpec

class L2LookupHazardSpec extends AnyFlatSpec with ChiselScalatestTester {
  behavior of "L2 lookup hazard observation"

  private implicit val p: Parameters = new Parameters(Map(
    DebugConfigKeys.EnableDifftest -> true,
    CoreConfigKeys.L2Ways -> 4,
    CoreConfigKeys.L2Prefetch -> L2PrefetchMode.Disabled))

  it should "observe STB, MSHR, and EB beside the Array lookup" in {
    test(new L2LookupHazard) { dut =>
      val addr = BigInt("80001180", 16)
      val sameSetOtherTag = addr + (BigInt(1) << (6 + 9))
      val bufferedData = BigInt("11" * 64, 16)
      val pendingData = BigInt("22" * 64, 16)

      dut.io.addr.poke(addr.U)
      dut.io.isStb.poke(false.B)
      dut.io.isUncacheProbe.poke(false.B)
      dut.io.isPrefetch.poke(false.B)
      dut.io.stbValid.foreach(_.poke(false.B))
      dut.io.stbAddr.foreach(_.poke(0.U))
      dut.io.stbData.foreach(_.poke(0.U))
      dut.io.pendingWriteValid.poke(false.B)
      dut.io.pendingWriteAddr.poke(0.U)
      dut.io.pendingWriteData.poke(0.U)
      dut.io.mshrValid.foreach(_.poke(false.B))
      dut.io.mshrAddr.foreach(_.poke(0.U))
      dut.io.ebValid.poke(false.B)
      dut.io.ebAddr.poke(0.U)

      dut.io.stbValid(1).poke(true.B)
      dut.io.stbAddr(1).poke(addr.U)
      dut.io.stbData(1).poke(bufferedData.U)
      dut.io.pendingWriteValid.poke(true.B)
      dut.io.pendingWriteAddr.poke(addr.U)
      dut.io.pendingWriteData.poke(pendingData.U)
      dut.io.stbMatch.expect(true.B)
      dut.io.stbForwarded.expect(true.B)
      dut.io.stbForwardData.expect(pendingData.U)

      dut.io.mshrValid(0).poke(true.B)
      dut.io.mshrAddr(0).poke(sameSetOtherTag.U)
      dut.io.mshrSetConflict.expect(true.B)

      dut.io.ebValid.poke(true.B)
      dut.io.ebAddr.poke(addr.U)
      dut.io.ebBlockConflict.expect(true.B)

      dut.io.isStb.poke(true.B)
      dut.io.stbMatch.expect(true.B)
      dut.io.stbForwarded.expect(false.B)
    }
  }
}
