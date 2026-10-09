package minixiangshan.mem.L2cache

import chisel3._
import chiseltest._
import chiseltest.simulator.{VerilatorBackendAnnotation, VerilatorCFlags, VerilatorFlags}
import minixiangshan.config._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class L2PrefetchIntegrationSpec
    extends AnyFlatSpec
    with ChiselScalatestTester
    with Matchers {
  behavior of "L2Cache lightweight prefetch integration"

  private val burstBeats = 16
  private val verilator = Seq(
    VerilatorBackendAnnotation,
    VerilatorFlags(Seq("--output-split", "0")),
    VerilatorCFlags(Seq("-DWData=IData"))
  )

  private def init(dut: L2CacheTestHarness): Unit = {
    dut.io.iReq.valid.poke(false.B)
    dut.io.iReq.bits.id.poke(0.U)
    dut.io.iReq.bits.addr.poke(0.U)
    dut.io.iReq.bits.size.poke(2.U)
    dut.io.iReq.bits.uncache.poke(false.B)
    dut.io.iCancel.poke(false.B)
    dut.io.iRespReady.poke(true.B)

    dut.io.dReadReq.valid.poke(false.B)
    dut.io.dReadReq.bits.id.poke(0.U)
    dut.io.dReadReq.bits.addr.poke(0.U)
    dut.io.dReadReq.bits.size.poke(2.U)
    dut.io.dReadReq.bits.uncache.poke(false.B)
    dut.io.dReadRespReady.poke(true.B)

    dut.io.dWriteReqValid.poke(false.B)
    dut.io.dWriteReqId.poke(0.U)
    dut.io.dWriteReqAddr.poke(0.U)
    dut.io.dWriteReqKind.poke(L2WriteKind.putLine)
    dut.io.dWriteReqSize.poke(2.U)
    for (word <- 0 until burstBeats) dut.io.dWriteReqWords(word).poke(0.U)
    dut.io.dWriteReqStrb.poke(0.U)
    dut.io.dWriteDoneReady.poke(true.B)

    dut.io.axi.ar.arready.poke(false.B)
    dut.io.axi.aw.awready.poke(false.B)
    dut.io.axi.w.wready.poke(false.B)
    dut.io.axi.r.data.rid.poke(0.U)
    dut.io.axi.r.data.rdata.poke(0.U)
    dut.io.axi.r.data.rresp.poke(0.U)
    dut.io.axi.r.data.rlast.poke(false.B)
    dut.io.axi.r.data.rvalid.poke(false.B)
    dut.io.axi.b.data.bid.poke(0.U)
    dut.io.axi.b.data.bresp.poke(0.U)
    dut.io.axi.b.data.bvalid.poke(false.B)
  }

  private def resetDut(dut: L2CacheTestHarness): Unit = {
    init(dut)
    dut.reset.poke(true.B)
    dut.clock.step(2)
    dut.reset.poke(false.B)
    dut.clock.step(512)
  }

  private def sendI(dut: L2CacheTestHarness, addr: BigInt): Unit = {
    dut.io.iReq.valid.poke(true.B)
    dut.io.iReq.bits.addr.poke(addr.U)
    var wait = 0
    while (!dut.io.iReq.ready.peek().litToBoolean && wait < 200) {
      dut.clock.step()
      wait += 1
    }
    withClue(f"I request 0x$addr%x was not accepted") {
      dut.io.iReq.ready.peek().litToBoolean shouldBe true
    }
    dut.clock.step()
    dut.io.iReq.valid.poke(false.B)
  }

  private def acceptAr(dut: L2CacheTestHarness, limit: Int = 300): BigInt = {
    var wait = 0
    while (!dut.io.axi.ar.data.arvalid.peek().litToBoolean && wait < limit) {
      dut.clock.step()
      wait += 1
    }
    withClue("timed out waiting for an AXI read command") {
      dut.io.axi.ar.data.arvalid.peek().litToBoolean shouldBe true
    }
    val addr = dut.io.axi.ar.data.araddr.peek().litValue
    dut.io.axi.ar.arready.poke(true.B)
    dut.clock.step()
    dut.io.axi.ar.arready.poke(false.B)
    addr
  }

  private def returnLine(
      dut: L2CacheTestHarness,
      words: Seq[BigInt],
      requireSilence: Boolean
  ): Unit = {
    require(words.length == burstBeats)
    for (beat <- words.indices) {
      dut.io.axi.r.data.rvalid.poke(true.B)
      dut.io.axi.r.data.rdata.poke(words(beat).U)
      dut.io.axi.r.data.rlast.poke((beat == words.length - 1).B)
      var wait = 0
      while (!dut.io.axi.r.rready.peek().litToBoolean && wait < 80) {
        if (requireSilence) {
          dut.io.iRespValid.expect(false.B)
          dut.io.dReadRespValid.expect(false.B)
        }
        dut.clock.step()
        wait += 1
      }
      dut.io.axi.r.rready.expect(true.B)
      if (requireSilence) {
        dut.io.iRespValid.expect(false.B)
        dut.io.dReadRespValid.expect(false.B)
      }
      dut.clock.step()
    }
    dut.io.axi.r.data.rvalid.poke(false.B)
    dut.io.axi.r.data.rlast.poke(false.B)
  }

  private def consumeILast(dut: L2CacheTestHarness): Unit = {
    dut.io.iRespReady.poke(true.B)
    var wait = 0
    while (!(dut.io.iRespValid.peek().litToBoolean &&
        dut.io.iRespLast.peek().litToBoolean) && wait < 160) {
      dut.clock.step()
      wait += 1
    }
    dut.io.iRespValid.expect(true.B)
    dut.io.iRespLast.expect(true.B)
    dut.clock.step()
  }

  private def sendD(
      dut: L2CacheTestHarness,
      addr: BigInt,
      uncache: Boolean = false
  ): Unit = {
    dut.io.dReadReq.valid.poke(true.B)
    dut.io.dReadReq.bits.addr.poke(addr.U)
    dut.io.dReadReq.bits.uncache.poke(uncache.B)
    var wait = 0
    while (!dut.io.dReadReq.ready.peek().litToBoolean && wait < 240) {
      dut.clock.step()
      wait += 1
    }
    withClue(f"D request 0x$addr%x was not accepted") {
      dut.io.dReadReq.ready.peek().litToBoolean shouldBe true
    }
    dut.clock.step()
    dut.io.dReadReq.valid.poke(false.B)
  }

  private def consumeDLast(dut: L2CacheTestHarness): Unit = {
    dut.io.dReadRespReady.poke(true.B)
    var wait = 0
    while (!(dut.io.dReadRespValid.peek().litToBoolean &&
        dut.io.dReadRespLast.peek().litToBoolean) && wait < 200) {
      dut.clock.step()
      wait += 1
    }
    dut.io.dReadRespValid.expect(true.B)
    dut.io.dReadRespLast.expect(true.B)
    dut.clock.step()
  }

  private def completeIMiss(
      dut: L2CacheTestHarness,
      addr: BigInt,
      seed: Int
  ): Unit = {
    val words = (0 until burstBeats).map(i => BigInt(seed.toLong + i))
    dut.io.iRespReady.poke(false.B)
    sendI(dut, addr)
    acceptAr(dut) shouldBe addr
    returnLine(dut, words, requireSilence = false)
    consumeILast(dut)
  }

  private def completeDMiss(
      dut: L2CacheTestHarness,
      addr: BigInt,
      seed: Int
  ): Unit = {
    val words = (0 until burstBeats).map(i => BigInt(seed.toLong + i))
    dut.io.dReadRespReady.poke(false.B)
    sendD(dut, addr)
    acceptAr(dut) shouldBe addr
    returnLine(dut, words, requireSilence = false)
    consumeDLast(dut)
  }

  it should "silently fill the next I line and serve its later demand from L2" in {
    implicit val p: Parameters = new Parameters(Map(
      DebugConfigKeys.EnableDifftest -> true,
      CoreConfigKeys.L2Prefetch -> L2PrefetchMode.IOnly
    ))
    test(new L2CacheTestHarness).withAnnotations(verilator) { dut =>
      resetDut(dut)
      val demand = BigInt("86001040", 16)
      val target = demand + 64
      val demandWords = (0 until burstBeats).map(i => BigInt(0x41000000L + i))
      val prefetchWords = (0 until burstBeats).map(i => BigInt(0x42000000L + i))

      dut.io.iRespReady.poke(false.B)
      sendI(dut, demand)
      acceptAr(dut) shouldBe demand
      returnLine(dut, demandWords, requireSilence = false)
      consumeILast(dut)

      acceptAr(dut) shouldBe target
      returnLine(dut, prefetchWords, requireSilence = true)
      dut.clock.step(8)

      sendI(dut, target)
      var wait = 0
      while (!dut.io.iRespValid.peek().litToBoolean && wait < 100) {
        dut.io.axi.ar.data.arvalid.expect(false.B)
        dut.clock.step()
        wait += 1
      }
      dut.io.iRespValid.expect(true.B)
      dut.io.iRespFullLine.expect(true.B)
      for (word <- 0 until burstBeats) {
        dut.io.iRespWords(word).expect(prefetchWords(word).U)
      }
    }
  }

  it should "continue the I prefetch chain after a prefetched-line hit" in {
    implicit val p: Parameters = new Parameters(Map(
      DebugConfigKeys.EnableDifftest -> true,
      CoreConfigKeys.L2Prefetch -> L2PrefetchMode.IOnly
    ))
    test(new L2CacheTestHarness).withAnnotations(verilator) { dut =>
      resetDut(dut)
      val first = BigInt("86011040", 16)
      val second = first + 64
      val third = second + 64
      completeIMiss(dut, first, 0x42100000)

      acceptAr(dut) shouldBe second
      val secondWords = (0 until burstBeats).map(i => BigInt(0x42200000L + i))
      returnLine(dut, secondWords, requireSilence = true)
      dut.clock.step(8)

      sendI(dut, second)
      var wait = 0
      while (!dut.io.iRespValid.peek().litToBoolean && wait < 120) {
        dut.io.axi.ar.data.arvalid.expect(false.B)
        dut.clock.step()
        wait += 1
      }
      dut.io.iRespValid.expect(true.B)
      dut.io.iRespFullLine.expect(true.B)
      dut.clock.step()

      acceptAr(dut, limit = 400) shouldBe third
    }
  }

  it should "promote an in-flight I prefetch when its demand arrives" in {
    implicit val p: Parameters = new Parameters(Map(
      DebugConfigKeys.EnableDifftest -> true,
      CoreConfigKeys.L2Prefetch -> L2PrefetchMode.IOnly
    ))
    test(new L2CacheTestHarness).withAnnotations(verilator) { dut =>
      resetDut(dut)
      val first = BigInt("86021040", 16)
      val target = first + 64
      completeIMiss(dut, first, 0x42300000)
      acceptAr(dut) shouldBe target

      sendI(dut, target)
      dut.clock.step(6)
      val firstBeat = BigInt("42400000", 16)
      dut.io.axi.r.data.rvalid.poke(true.B)
      dut.io.axi.r.data.rdata.poke(firstBeat.U)
      dut.io.axi.r.data.rlast.poke(false.B)
      dut.io.axi.r.rready.expect(true.B)
      dut.clock.step()
      dut.io.axi.r.data.rvalid.poke(false.B)

      var wait = 0
      while (!dut.io.iRespValid.peek().litToBoolean && wait < 40) {
        dut.clock.step()
        wait += 1
      }
      dut.io.iRespValid.expect(true.B)
      dut.io.iRespFullLine.expect(false.B)
      dut.io.iRespWords(0).expect(firstBeat.U)
    }
  }

  it should "issue no speculative request when mode is disabled" in {
    implicit val p: Parameters = new Parameters(Map(
      DebugConfigKeys.EnableDifftest -> true,
      CoreConfigKeys.L2Prefetch -> L2PrefetchMode.Disabled
    ))
    test(new L2CacheTestHarness).withAnnotations(verilator) { dut =>
      resetDut(dut)
      completeIMiss(dut, BigInt("86100040", 16), 0x43000000)
      for (_ <- 0 until 80) {
        dut.io.axi.ar.data.arvalid.expect(false.B)
        dut.clock.step()
      }
    }
  }

  it should "give a registered D demand priority over a pending I candidate" in {
    implicit val p: Parameters = new Parameters(Map(
      DebugConfigKeys.EnableDifftest -> true,
      CoreConfigKeys.L2Prefetch -> L2PrefetchMode.IOnly
    ))
    test(new L2CacheTestHarness).withAnnotations(verilator) { dut =>
      resetDut(dut)
      completeIMiss(dut, BigInt("86200040", 16), 0x44000000)
      val dDemand = BigInt("87300200", 16)
      sendD(dut, dDemand)
      acceptAr(dut) shouldBe dDemand
      val dWords = (0 until burstBeats).map(i => BigInt(0x44010000L + i))
      returnLine(dut, dWords, requireSilence = false)
      consumeDLast(dut)
      acceptAr(dut, limit = 300) shouldBe BigInt("86200080", 16)
    }
  }

  it should "discard an I candidate cancelled after it entered lookup" in {
    implicit val p: Parameters = new Parameters(Map(
      DebugConfigKeys.EnableDifftest -> true,
      CoreConfigKeys.L2Prefetch -> L2PrefetchMode.IOnly
    ))
    test(new L2CacheTestHarness).withAnnotations(verilator) { dut =>
      resetDut(dut)
      completeIMiss(dut, BigInt("86300040", 16), 0x45000000)
      dut.clock.step(3)
      dut.io.iCancel.poke(true.B)
      dut.clock.step()
      dut.io.iCancel.poke(false.B)
      for (_ <- 0 until 100) {
        dut.io.axi.ar.data.arvalid.expect(false.B)
        dut.clock.step()
      }
    }
  }


  it should "cancel an I candidate parked at the registered issue boundary" in {
    implicit val p: Parameters = new Parameters(Map(
      DebugConfigKeys.EnableDifftest -> true,
      CoreConfigKeys.L2Prefetch -> L2PrefetchMode.IOnly
    ))
    test(new L2CacheTestHarness).withAnnotations(verilator) { dut =>
      resetDut(dut)
      completeIMiss(dut, BigInt("86310040", 16), 0x45100000)
      dut.clock.step(2)
      dut.io.iCancel.poke(true.B)
      dut.clock.step()
      dut.io.iCancel.poke(false.B)
      for (_ <- 0 until 100) {
        dut.io.axi.ar.data.arvalid.expect(false.B)
        dut.clock.step()
      }
    }
  }
  it should "drop an unissued candidate and let an uncached read drain" in {
    implicit val p: Parameters = new Parameters(Map(
      DebugConfigKeys.EnableDifftest -> true,
      CoreConfigKeys.L2Prefetch -> L2PrefetchMode.IOnly
    ))
    test(new L2CacheTestHarness).withAnnotations(verilator) { dut =>
      resetDut(dut)
      completeIMiss(dut, BigInt("86400040", 16), 0x46000000)
      val uncached = BigInt("9f001024", 16)
      dut.io.dReadRespReady.poke(false.B)
      sendD(dut, uncached, uncache = true)
      acceptAr(dut, limit = 400) shouldBe uncached
      dut.io.axi.ar.data.arlen.expect(0.U)
    }
  }

  it should "learn a D offset, silently prefetch, and serve a later D hit" in {
    implicit val p: Parameters = new Parameters(Map(
      DebugConfigKeys.EnableDifftest -> true,
      CoreConfigKeys.L2Prefetch -> L2PrefetchMode.DOnly
    ))
    test(new L2CacheTestHarness).withAnnotations(verilator) { dut =>
      resetDut(dut)
      val base = BigInt("88000000", 16)
      for (line <- 0 until 16) {
        completeDMiss(dut, base + line * 64, 0x50000000 + line * 0x100)
      }
      dut.clock.step(6)
      val trigger = base + 16 * 64
      val target = base + 17 * 64
      completeDMiss(dut, trigger, 0x51000000)

      acceptAr(dut, limit = 400) shouldBe target
      val prefetched = (0 until burstBeats).map(i => BigInt(0x52000000L + i))
      returnLine(dut, prefetched, requireSilence = true)
      dut.clock.step(8)

      sendD(dut, target)
      var wait = 0
      while (!dut.io.dReadRespValid.peek().litToBoolean && wait < 120) {
        dut.io.axi.ar.data.arvalid.expect(false.B)
        dut.clock.step()
        wait += 1
      }
      dut.io.dReadRespValid.expect(true.B)
      dut.io.dReadRespFullLine.expect(true.B)
      for (word <- 0 until burstBeats) {
        dut.io.dReadRespWords(word).expect(prefetched(word).U)
      }
    }
  }
}
