package minixiangshan.mem.L2cache

import chisel3._
import chiseltest._
import minixiangshan.config._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class L2PrefetchModeProbe(implicit val p: Parameters) extends Module with HasCoreParameters {
  val io = IO(new Bundle {
    val mode = Output(UInt(2.W))
    val iEnabled = Output(Bool())
    val dEnabled = Output(Bool())
  })

  io.mode := l2PrefetchMode.U
  io.iEnabled := l2IPrefetchEnabled.B
  io.dEnabled := l2DPrefetchEnabled.B
}

class L2PrefetchSpec extends AnyFlatSpec with ChiselScalatestTester with Matchers {
  behavior of "L2 prefetch configuration"

  Seq(
    L2PrefetchMode.Disabled -> (false, false),
    L2PrefetchMode.IOnly -> (true, false),
    L2PrefetchMode.DOnly -> (false, true),
    L2PrefetchMode.Hybrid -> (true, true)
  ).foreach { case (mode, (iEnabled, dEnabled)) =>
    it should s"decode mode $mode into the intended engines" in {
      implicit val p: Parameters = new Parameters(Map(CoreConfigKeys.L2Prefetch -> mode))
      test(new L2PrefetchModeProbe) { dut =>
        dut.io.mode.expect(mode.U)
        dut.io.iEnabled.expect(iEnabled.B)
        dut.io.dEnabled.expect(dEnabled.B)
      }
    }
  }

  behavior of "L2 I prefetcher"

  private def resetI(dut: L2IPrefetch): Unit = {
    dut.reset.poke(true.B)
    dut.io.clear.poke(false.B)
    dut.io.train.valid.poke(false.B)
    dut.io.train.bits.poke(0.U)
    dut.io.candidate.ready.poke(false.B)
    dut.clock.step(2)
    dut.reset.poke(false.B)
  }

  it should "register one aligned next-line candidate" in {
    implicit val p: Parameters = new Parameters(Map.empty)
    test(new L2IPrefetch) { dut =>
      resetI(dut)
      dut.io.candidate.ready.poke(true.B)
      dut.io.train.valid.poke(true.B)
      dut.io.train.bits.poke("h80001024".U)
      dut.io.candidate.valid.expect(false.B)
      dut.clock.step()
      dut.io.train.valid.poke(false.B)
      dut.io.candidate.valid.expect(true.B)
      dut.io.candidate.bits.expect("h80001040".U)
      dut.clock.step()
      dut.io.candidate.valid.expect(false.B)
    }
  }

  it should "drop a next-line candidate that would cross a 4 KiB page" in {
    implicit val p: Parameters = new Parameters(Map.empty)
    test(new L2IPrefetch) { dut =>
      resetI(dut)
      dut.io.candidate.ready.poke(true.B)
      dut.io.train.valid.poke(true.B)
      dut.io.train.bits.poke("h80001fc0".U)
      dut.clock.step()
      dut.io.train.valid.poke(false.B)
      dut.io.candidate.valid.expect(false.B)
    }
  }

  it should "hold a stalled candidate and drop newer training" in {
    implicit val p: Parameters = new Parameters(Map.empty)
    test(new L2IPrefetch) { dut =>
      resetI(dut)
      dut.io.train.valid.poke(true.B)
      dut.io.train.bits.poke("h80002000".U)
      dut.clock.step()
      dut.io.train.bits.poke("h80003000".U)
      dut.io.candidate.valid.expect(true.B)
      dut.io.candidate.bits.expect("h80002040".U)
      dut.clock.step(2)
      dut.io.candidate.valid.expect(true.B)
      dut.io.candidate.bits.expect("h80002040".U)
    }
  }

  it should "clear a buffered candidate before it can issue" in {
    implicit val p: Parameters = new Parameters(Map.empty)
    test(new L2IPrefetch) { dut =>
      resetI(dut)
      dut.io.train.valid.poke(true.B)
      dut.io.train.bits.poke("h80004000".U)
      dut.clock.step()
      dut.io.train.valid.poke(false.B)
      dut.io.candidate.valid.expect(true.B)
      dut.io.clear.poke(true.B)
      dut.clock.step()
      dut.io.clear.poke(false.B)
      dut.io.candidate.valid.expect(false.B)
    }
  }

  behavior of "L2 D micro-BOP prefetcher"

  private def resetD(dut: L2DPrefetch): Unit = {
    dut.reset.poke(true.B)
    dut.io.clear.poke(false.B)
    dut.io.train.valid.poke(false.B)
    dut.io.train.bits.poke(0.U)
    dut.io.candidate.ready.poke(false.B)
    dut.clock.step(2)
    dut.reset.poke(false.B)
  }

  private def sendD(dut: L2DPrefetch, addr: BigInt): Unit = {
    dut.io.train.valid.poke(true.B)
    dut.io.train.bits.poke(addr.U)
    dut.clock.step()
    dut.io.train.valid.poke(false.B)
    dut.clock.step()
  }

  private def trainDStride(
    dut: L2DPrefetch,
    base: BigInt,
    strideBytes: BigInt
  ): Unit = {
    for (i <- 0 until 16) {
      sendD(dut, base + i * strideBytes)
      dut.io.candidate.valid.expect(false.B)
    }
    dut.clock.step(4)
  }

  it should "stay silent before an offset reaches confidence" in {
    implicit val p: Parameters = new Parameters(Map.empty)
    test(new L2DPrefetch) { dut =>
      resetD(dut)
      dut.io.candidate.ready.poke(true.B)
      for (i <- 0 until 8) {
        sendD(dut, BigInt("80010000", 16) + i * 64)
        dut.io.candidate.valid.expect(false.B)
      }
    }
  }

  it should "learn a positive one-line offset and register its candidate" in {
    implicit val p: Parameters = new Parameters(Map.empty)
    test(new L2DPrefetch) { dut =>
      resetD(dut)
      dut.io.candidate.ready.poke(true.B)
      val base = BigInt("80020000", 16)
      trainDStride(dut, base, 64)
      sendD(dut, base + 16 * 64)
      dut.io.candidate.valid.expect(true.B)
      dut.io.candidate.bits.expect((base + 17 * 64).U)
      dut.clock.step()
      dut.io.candidate.valid.expect(false.B)
    }
  }

  it should "learn a positive two-line offset without a parallel winner tree" in {
    implicit val p: Parameters = new Parameters(Map.empty)
    test(new L2DPrefetch) { dut =>
      resetD(dut)
      dut.io.candidate.ready.poke(true.B)
      val base = BigInt("80030000", 16)
      trainDStride(dut, base, 128)
      sendD(dut, base + 16 * 128)
      dut.io.candidate.valid.expect(true.B)
      dut.io.candidate.bits.expect((base + 17 * 128).U)
    }
  }

  it should "hold a stalled D candidate and clear all learned state" in {
    implicit val p: Parameters = new Parameters(Map.empty)
    test(new L2DPrefetch) { dut =>
      resetD(dut)
      val base = BigInt("80040000", 16)
      trainDStride(dut, base, 64)

      sendD(dut, base + 16 * 64)
      dut.io.candidate.valid.expect(true.B)
      val held = base + 17 * 64
      dut.io.candidate.bits.expect(held.U)
      sendD(dut, base + 17 * 64)
      dut.io.candidate.valid.expect(true.B)
      dut.io.candidate.bits.expect(held.U)

      dut.io.clear.poke(true.B)
      dut.clock.step()
      dut.io.clear.poke(false.B)
      dut.io.candidate.valid.expect(false.B)
      sendD(dut, base + 18 * 64)
      dut.io.candidate.valid.expect(false.B)
    }
  }

  behavior of "L2 prefetch hub"

  it should "clear all queued candidates in one registered clear cycle" in {
    implicit val p: Parameters = new Parameters(Map(
      CoreConfigKeys.L2Prefetch -> L2PrefetchMode.Hybrid
    ))
    test(new L2PrefetchHub) { dut =>
      dut.reset.poke(true.B)
      dut.io.iTrain.valid.poke(false.B)
      dut.io.iTrain.bits.poke(0.U)
      dut.io.dTrain.valid.poke(false.B)
      dut.io.dTrain.bits.poke(0.U)
      dut.io.iClear.poke(false.B)
      dut.io.dClear.poke(false.B)
      dut.io.queueClear.poke(false.B)
      dut.io.candidate.ready.poke(false.B)
      dut.clock.step(2)
      dut.reset.poke(false.B)

      dut.io.iTrain.valid.poke(true.B)
      dut.io.iTrain.bits.poke("h80050000".U)
      dut.clock.step()
      dut.io.iTrain.bits.poke("h80050100".U)
      dut.clock.step()
      dut.io.iTrain.valid.poke(false.B)
      dut.clock.step()
      dut.io.candidate.valid.expect(true.B)

      dut.io.queueClear.poke(true.B)
      dut.io.iClear.poke(true.B)
      dut.clock.step()
      dut.io.queueClear.poke(false.B)
      dut.io.iClear.poke(false.B)
      dut.io.candidate.valid.expect(false.B)
      dut.clock.step(2)
      dut.io.candidate.valid.expect(false.B)
    }
  }
}
