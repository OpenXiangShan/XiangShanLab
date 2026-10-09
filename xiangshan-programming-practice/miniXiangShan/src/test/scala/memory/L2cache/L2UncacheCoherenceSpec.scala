package minixiangshan.mem.L2cache

import chisel3._
import chiseltest._
import chiseltest.simulator.{VerilatorBackendAnnotation, VerilatorCFlags, VerilatorFlags}
import minixiangshan.axi._
import minixiangshan.config._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class L2UncacheCoherenceSpec
    extends AnyFlatSpec
    with ChiselScalatestTester
    with Matchers {
  behavior of "L2Cache uncached coherence"

  private implicit val p: Parameters =
    new Parameters(Map(DebugConfigKeys.EnableDifftest -> true))
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

  private def sendDRead(
      dut: L2CacheTestHarness,
      addr: BigInt,
      uncache: Boolean,
      id: Int = 0
  ): Unit = {
    dut.io.dReadReq.valid.poke(true.B)
    dut.io.dReadReq.bits.id.poke(id.U)
    dut.io.dReadReq.bits.addr.poke(addr.U)
    dut.io.dReadReq.bits.size.poke(2.U)
    dut.io.dReadReq.bits.uncache.poke(uncache.B)
    var wait = 0
    while (!dut.io.dReadReq.ready.peek().litToBoolean && wait < 120) {
      dut.clock.step()
      wait += 1
    }
    withClue(s"D read 0x${addr.toString(16)} was not accepted") {
      dut.io.dReadReq.ready.peek().litToBoolean shouldBe true
    }
    dut.clock.step()
    dut.io.dReadReq.valid.poke(false.B)
  }

  private def waitForAr(dut: L2CacheTestHarness, limit: Int = 80): Unit = {
    var wait = 0
    while (!dut.io.axi.ar.data.arvalid.peek().litToBoolean && wait < limit) {
      dut.clock.step()
      wait += 1
    }
    dut.io.axi.ar.data.arvalid.expect(true.B)
  }

  private def completeRead(dut: L2CacheTestHarness, words: Seq[BigInt]): Unit = {
    dut.io.axi.ar.arready.poke(true.B)
    dut.clock.step()
    dut.io.axi.ar.arready.poke(false.B)
    for ((word, beat) <- words.zipWithIndex) {
      dut.io.axi.r.data.rvalid.poke(true.B)
      dut.io.axi.r.data.rdata.poke(word.U)
      dut.io.axi.r.data.rlast.poke((beat == words.size - 1).B)
      dut.io.axi.r.rready.expect(true.B)
      dut.clock.step()
    }
    dut.io.axi.r.data.rvalid.poke(false.B)
    dut.io.axi.r.data.rlast.poke(false.B)
  }

  private def fillCleanLine(dut: L2CacheTestHarness, addr: BigInt): Unit = {
    val words = (0 until burstBeats).map(i => BigInt(0x71000000L + i))
    dut.io.dReadRespReady.poke(false.B)
    sendDRead(dut, addr, uncache = false)
    waitForAr(dut)
    dut.io.axi.ar.data.araddr.expect(addr.U)
    dut.io.axi.ar.data.arlen.expect((burstBeats - 1).U)
    completeRead(dut, words)
    dut.io.dReadRespReady.poke(true.B)
    var responses = 0
    var wait = 0
    while (responses < burstBeats && wait < 120) {
      if (dut.io.dReadRespValid.peek().litToBoolean) responses += 1
      dut.clock.step()
      wait += 1
    }
    withClue("cacheable refill did not return all L1 beats") {
      responses shouldBe burstBeats
    }
    dut.clock.step(4)
  }

  private def completeUncachedRead(dut: L2CacheTestHarness, addr: BigInt): Unit = {
    dut.io.dReadRespReady.poke(false.B)
    sendDRead(dut, addr, uncache = true)
    waitForAr(dut)
    dut.io.axi.aw.data.awvalid.expect(false.B)
    dut.io.axi.w.data.wvalid.expect(false.B)
    dut.io.axi.ar.data.araddr.expect(addr.U)
    dut.io.axi.ar.data.arlen.expect(0.U)
    completeRead(dut, Seq(BigInt("12345678", 16)))
    dut.io.dReadRespReady.poke(true.B)
    var wait = 0
    while (!dut.io.dReadRespValid.peek().litToBoolean && wait < 40) {
      dut.clock.step()
      wait += 1
    }
    dut.io.dReadRespValid.expect(true.B)
    dut.io.dReadRespFullLine.expect(false.B)
    dut.io.dReadRespLast.expect(true.B)
    dut.clock.step(3)
  }

  private def requireDdrMiss(dut: L2CacheTestHarness, addr: BigInt): Unit = {
    sendDRead(dut, addr, uncache = false)
    var sawAr = false
    var wait = 0
    while (!sawAr && wait < 80) {
      sawAr = dut.io.axi.ar.data.arvalid.peek().litToBoolean
      if (!sawAr) dut.clock.step()
      wait += 1
    }
    withClue("cacheable read must miss after uncached access invalidates its clean L2 copy") {
      sawAr shouldBe true
    }
    dut.io.axi.ar.data.araddr.expect(addr.U)
    dut.io.axi.ar.data.arlen.expect((burstBeats - 1).U)
  }

  private def completeUncachedWrite(dut: L2CacheTestHarness, addr: BigInt): Unit = {
    dut.io.dWriteReqValid.poke(true.B)
    dut.io.dWriteReqId.poke(1.U)
    dut.io.dWriteReqAddr.poke(addr.U)
    dut.io.dWriteReqKind.poke(L2WriteKind.uncache)
    dut.io.dWriteReqSize.poke(0.U)
    dut.io.dWriteReqWords(0).poke("h0000007f".U)
    dut.io.dWriteReqStrb.poke(8.U)
    var wait = 0
    while (!dut.io.dWriteReqReady.peek().litToBoolean && wait < 120) {
      dut.clock.step()
      wait += 1
    }
    dut.io.dWriteReqReady.expect(true.B)
    dut.clock.step()
    dut.io.dWriteReqValid.poke(false.B)

    wait = 0
    while (!dut.io.axi.aw.data.awvalid.peek().litToBoolean && wait < 80) {
      dut.clock.step()
      wait += 1
    }
    dut.io.axi.aw.data.awvalid.expect(true.B)
    dut.io.axi.aw.data.awaddr.expect(addr.U)
    dut.io.axi.aw.data.awlen.expect(0.U)
    dut.io.axi.aw.data.awsize.expect(0.U)
    dut.io.axi.aw.awready.poke(true.B)
    dut.io.axi.w.wready.poke(true.B)
    dut.clock.step()
    dut.io.axi.aw.awready.poke(false.B)
    dut.io.axi.w.wready.poke(false.B)

    wait = 0
    while (!dut.io.axi.b.bready.peek().litToBoolean && wait < 20) {
      dut.clock.step()
      wait += 1
    }
    dut.io.axi.b.bready.expect(true.B)
    dut.io.axi.b.data.bvalid.poke(true.B)
    dut.clock.step()
    dut.io.axi.b.data.bvalid.poke(false.B)
    dut.clock.step(3)
  }

  private def installDirtyLine(dut: L2CacheTestHarness, addr: BigInt, words: Seq[BigInt]): Unit = {
    dut.io.dWriteReqValid.poke(true.B)
    dut.io.dWriteReqId.poke(1.U)
    dut.io.dWriteReqAddr.poke(addr.U)
    dut.io.dWriteReqKind.poke(L2WriteKind.putLine)
    dut.io.dWriteReqSize.poke(2.U)
    for ((word, beat) <- words.zipWithIndex) dut.io.dWriteReqWords(beat).poke(word.U)
    dut.io.dWriteReqStrb.poke("hf".U)
    dut.io.dWriteReqReady.expect(true.B)
    dut.clock.step()
    dut.io.dWriteReqValid.poke(false.B)
    dut.clock.step(8)
  }

  private def sendUncachedPartialWrite(dut: L2CacheTestHarness, addr: BigInt): Unit = {
    dut.io.dWriteReqValid.poke(true.B)
    dut.io.dWriteReqId.poke(1.U)
    dut.io.dWriteReqAddr.poke(addr.U)
    dut.io.dWriteReqKind.poke(L2WriteKind.uncache)
    dut.io.dWriteReqSize.poke(0.U)
    dut.io.dWriteReqWords(0).poke("h0000007f".U)
    dut.io.dWriteReqStrb.poke(8.U)
    var wait = 0
    while (!dut.io.dWriteReqReady.peek().litToBoolean && wait < 120) {
      dut.clock.step()
      wait += 1
    }
    dut.io.dWriteReqReady.expect(true.B)
    dut.clock.step()
    dut.io.dWriteReqValid.poke(false.B)
  }

  private def completeDirtyWriteback(
      dut: L2CacheTestHarness, addr: BigInt, words: Seq[BigInt]): Unit = {
    var wait = 0
    while (!dut.io.axi.aw.data.awvalid.peek().litToBoolean && wait < 120) {
      dut.clock.step()
      wait += 1
    }
    dut.io.axi.aw.data.awvalid.expect(true.B)
    dut.io.axi.aw.data.awaddr.expect(addr.U)
    dut.io.axi.aw.data.awlen.expect((burstBeats - 1).U)
    dut.io.axi.aw.data.awsize.expect(2.U)
    dut.io.axi.aw.awready.poke(true.B)
    dut.io.axi.w.wready.poke(true.B)
    for ((word, beat) <- words.zipWithIndex) {
      dut.io.axi.w.data.wvalid.expect(true.B)
      dut.io.axi.w.data.wdata.expect(word.U)
      dut.io.axi.w.data.wstrb.expect("hf".U)
      dut.io.axi.w.data.wlast.expect((beat == burstBeats - 1).B)
      dut.clock.step()
    }
    dut.io.axi.aw.awready.poke(false.B)
    dut.io.axi.w.wready.poke(false.B)
    wait = 0
    while (!dut.io.axi.b.bready.peek().litToBoolean && wait < 20) {
      dut.clock.step()
      wait += 1
    }
    dut.io.axi.b.bready.expect(true.B)
    for (_ <- 0 until 8) {
      dut.io.axi.ar.data.arvalid.expect(false.B)
      dut.io.axi.aw.data.awvalid.expect(false.B)
      dut.clock.step()
    }
    dut.io.axi.b.data.bvalid.poke(true.B)
    dut.clock.step()
    dut.io.axi.b.data.bvalid.poke(false.B)
  }

  private def completeRawReadAfterWriteback(dut: L2CacheTestHarness, addr: BigInt): Unit = {
    waitForAr(dut)
    dut.io.axi.ar.data.araddr.expect(addr.U)
    dut.io.axi.ar.data.arlen.expect(0.U)
    dut.io.axi.ar.data.arsize.expect(2.U)
    completeRead(dut, Seq(BigInt("76543210", 16)))
    dut.io.dReadRespReady.poke(true.B)
    var wait = 0
    while (!dut.io.dReadRespValid.peek().litToBoolean && wait < 40) {
      dut.clock.step()
      wait += 1
    }
    dut.io.dReadRespValid.expect(true.B)
    dut.io.dReadRespFullLine.expect(false.B)
    dut.clock.step(3)
  }

  private def completeRawWriteAfterWriteback(dut: L2CacheTestHarness, addr: BigInt): Unit = {
    var wait = 0
    while (!dut.io.axi.aw.data.awvalid.peek().litToBoolean && wait < 80) {
      dut.clock.step()
      wait += 1
    }
    dut.io.axi.aw.data.awvalid.expect(true.B)
    dut.io.axi.aw.data.awaddr.expect(addr.U)
    dut.io.axi.aw.data.awlen.expect(0.U)
    dut.io.axi.aw.data.awsize.expect(0.U)
    dut.io.axi.w.data.wvalid.expect(true.B)
    dut.io.axi.w.data.wdata.expect("h0000007f".U)
    dut.io.axi.w.data.wstrb.expect(8.U)
    dut.io.axi.w.data.wlast.expect(true.B)
    dut.io.axi.aw.awready.poke(true.B)
    dut.io.axi.w.wready.poke(true.B)
    dut.clock.step()
    dut.io.axi.aw.awready.poke(false.B)
    dut.io.axi.w.wready.poke(false.B)
    wait = 0
    while (!dut.io.axi.b.bready.peek().litToBoolean && wait < 20) {
      dut.clock.step()
      wait += 1
    }
    dut.io.axi.b.bready.expect(true.B)
    dut.io.axi.b.data.bvalid.poke(true.B)
    dut.clock.step()
    dut.io.axi.b.data.bvalid.poke(false.B)
    wait = 0
    while (!dut.io.dWriteDoneValid.peek().litToBoolean && wait < 20) {
      dut.clock.step()
      wait += 1
    }
    dut.io.dWriteDoneValid.expect(true.B)
    dut.io.dWriteDoneId.expect(1.U)
    dut.clock.step(3)
  }


  private def sendIUncachedRead(dut: L2CacheTestHarness, addr: BigInt): Unit = {
    dut.io.iReq.valid.poke(true.B)
    dut.io.iReq.bits.id.poke(0.U)
    dut.io.iReq.bits.addr.poke(addr.U)
    dut.io.iReq.bits.size.poke(2.U)
    dut.io.iReq.bits.uncache.poke(true.B)
    var wait = 0
    while (!dut.io.iReq.ready.peek().litToBoolean && wait < 80) {
      dut.clock.step()
      wait += 1
    }
    dut.io.iReq.ready.expect(true.B)
    dut.clock.step()
    dut.io.iReq.valid.poke(false.B)
  }

  private def completeCleanDUncachedReadNoWrite(
      dut: L2CacheTestHarness, addr: BigInt): Unit = {
    sendDRead(dut, addr, uncache = true)
    var wait = 0
    while (!dut.io.axi.ar.data.arvalid.peek().litToBoolean && wait < 80) {
      dut.io.axi.aw.data.awvalid.expect(false.B)
      dut.clock.step()
      wait += 1
    }
    dut.io.axi.aw.data.awvalid.expect(false.B)
    dut.io.axi.ar.data.araddr.expect(addr.U)
    dut.io.axi.ar.data.arlen.expect(0.U)
    completeRead(dut, Seq(BigInt("2468ace0", 16)))
    while (!dut.io.dReadRespValid.peek().litToBoolean && wait < 120) {
      dut.clock.step()
      wait += 1
    }
    dut.io.dReadRespValid.expect(true.B)
    dut.clock.step(3)
  }


  it should "probe and invalidate a clean hit before an uncached read" in {
    test(new L2CacheTestHarness).withAnnotations(verilator) { dut =>
      resetDut(dut)
      val target = BigInt("80012000", 16)
      fillCleanLine(dut, target)
      completeUncachedRead(dut, target + 4)
      requireDdrMiss(dut, target)
    }
  }

  it should "leave an unrelated victim untouched when an uncached probe misses" in {
    test(new L2CacheTestHarness).withAnnotations(verilator) { dut =>
      resetDut(dut)
      val resident = BigInt("80014040", 16)
      val missingSameSet = BigInt("80024040", 16)
      fillCleanLine(dut, resident)
      completeUncachedRead(dut, missingSameSet + 4)
      sendDRead(dut, resident, uncache = false)
      var sawHit = false
      var wait = 0
      while (!sawHit && wait < 40) {
        dut.io.axi.ar.data.arvalid.expect(false.B)
        sawHit = dut.io.dReadRespValid.peek().litToBoolean
        dut.clock.step()
        wait += 1
      }
      sawHit shouldBe true
      dut.io.dReadRespFullLine.expect(true.B)
    }
  }

  it should "probe and invalidate a clean hit before an uncached write" in {
    test(new L2CacheTestHarness).withAnnotations(verilator) { dut =>
      resetDut(dut)
      val target = BigInt("80018000", 16)
      fillCleanLine(dut, target)
      completeUncachedWrite(dut, target + 3)
      requireDdrMiss(dut, target)
    }
  }

  it should "invalidate an I-origin clean line before an uncached D word write" in {
    test(new L2CacheTestHarness).withAnnotations(verilator) { dut =>
      resetDut(dut)
      val line = BigInt("1c07c6c0", 16)
      val modifiedWord = BigInt("5000bc00", 16)
      val words = (0 until burstBeats).map(i => BigInt(0x61000000L + i))

      // Fill the L2 line through the instruction-side port, matching n73.
      dut.io.iRespReady.poke(false.B)
      dut.io.iReq.valid.poke(true.B)
      dut.io.iReq.bits.id.poke(0.U)
      dut.io.iReq.bits.addr.poke(line.U)
      dut.io.iReq.bits.size.poke(2.U)
      dut.io.iReq.bits.uncache.poke(false.B)
      var wait = 0
      while (!dut.io.iReq.ready.peek().litToBoolean && wait < 120) {
        dut.clock.step()
        wait += 1
      }
      dut.io.iReq.ready.expect(true.B)
      dut.clock.step()
      dut.io.iReq.valid.poke(false.B)
      waitForAr(dut)
      dut.io.axi.ar.data.araddr.expect(line.U)
      dut.io.axi.ar.data.arlen.expect((burstBeats - 1).U)
      completeRead(dut, words)
      dut.io.iRespReady.poke(true.B)
      var responses = 0
      wait = 0
      while (responses < burstBeats && wait < 120) {
        if (dut.io.iRespValid.peek().litToBoolean) responses += 1
        dut.clock.step()
        wait += 1
      }
      withClue("I-origin cacheable refill did not return all beats") {
        responses shouldBe burstBeats
      }
      dut.clock.step(4)

      // Perform the same aligned uncached word write used to patch code.
      dut.io.dWriteReqValid.poke(true.B)
      dut.io.dWriteReqId.poke(1.U)
      dut.io.dWriteReqAddr.poke((line + 0x20).U)
      dut.io.dWriteReqKind.poke(L2WriteKind.uncache)
      dut.io.dWriteReqSize.poke(2.U)
      dut.io.dWriteReqWords(0).poke(modifiedWord.U)
      dut.io.dWriteReqStrb.poke("hf".U)
      wait = 0
      while (!dut.io.dWriteReqReady.peek().litToBoolean && wait < 120) {
        dut.clock.step()
        wait += 1
      }
      dut.io.dWriteReqReady.expect(true.B)
      dut.clock.step()
      dut.io.dWriteReqValid.poke(false.B)
      wait = 0
      while (!dut.io.axi.aw.data.awvalid.peek().litToBoolean && wait < 120) {
        dut.clock.step()
        wait += 1
      }
      dut.io.axi.aw.data.awvalid.expect(true.B)
      dut.io.axi.aw.data.awaddr.expect((line + 0x20).U)
      dut.io.axi.aw.data.awlen.expect(0.U)
      dut.io.axi.aw.data.awsize.expect(2.U)
      dut.io.axi.w.data.wvalid.expect(true.B)
      dut.io.axi.w.data.wdata.expect(modifiedWord.U)
      dut.io.axi.w.data.wstrb.expect("hf".U)
      dut.io.axi.aw.awready.poke(true.B)
      dut.io.axi.w.wready.poke(true.B)
      dut.clock.step()
      dut.io.axi.aw.awready.poke(false.B)
      dut.io.axi.w.wready.poke(false.B)
      dut.io.axi.b.data.bvalid.poke(true.B)
      dut.clock.step()
      dut.io.axi.b.data.bvalid.poke(false.B)
      dut.clock.step(4)

      // A following instruction-side fetch must miss; a hit would expose stale code.
      dut.io.iReq.valid.poke(true.B)
      dut.io.iReq.bits.id.poke(0.U)
      dut.io.iReq.bits.addr.poke(line.U)
      dut.io.iReq.bits.size.poke(2.U)
      dut.io.iReq.bits.uncache.poke(false.B)
      wait = 0
      while (!dut.io.iReq.ready.peek().litToBoolean && wait < 120) {
        dut.clock.step()
        wait += 1
      }
      dut.io.iReq.ready.expect(true.B)
      dut.clock.step()
      dut.io.iReq.valid.poke(false.B)
      waitForAr(dut)
      dut.io.axi.ar.data.araddr.expect(line.U)
      dut.io.axi.ar.data.arlen.expect((burstBeats - 1).U)
    }
  }
  it should "wait for dirty writeback B before issuing an uncached read" in {
    test(new L2CacheTestHarness).withAnnotations(verilator) { dut =>
      resetDut(dut)
      val target = BigInt("8001c000", 16)
      val words = (0 until burstBeats).map(i => BigInt(0x72000000L + i))
      installDirtyLine(dut, target, words)
      dut.io.dReadRespReady.poke(false.B)
      sendDRead(dut, target + 4, uncache = true)
      completeDirtyWriteback(dut, target, words)
      completeRawReadAfterWriteback(dut, target + 4)
      requireDdrMiss(dut, target)
    }
  }

  it should "wait for dirty writeback B before issuing an uncached write" in {
    test(new L2CacheTestHarness).withAnnotations(verilator) { dut =>
      resetDut(dut)
      val target = BigInt("80020000", 16)
      val words = (0 until burstBeats).map(i => BigInt(0x73000000L + i))
      installDirtyLine(dut, target, words)
      sendUncachedPartialWrite(dut, target + 3)
      completeDirtyWriteback(dut, target, words)
      completeRawWriteAfterWriteback(dut, target + 3)
      requireDdrMiss(dut, target)
    }
  }


  it should "rearm dirty and clean uncached D reads without a second line writeback" in {
    test(new L2CacheTestHarness).withAnnotations(verilator) { dut =>
      resetDut(dut)
      val dirty = BigInt("80024000", 16)
      val clean = BigInt("80028000", 16)
      val words = (0 until burstBeats).map(i => BigInt(0x74000000L + i))
      installDirtyLine(dut, dirty, words)
      fillCleanLine(dut, clean)
      dut.io.dReadRespReady.poke(false.B)
      sendDRead(dut, dirty + 4, uncache = true)
      completeDirtyWriteback(dut, dirty, words)
      completeRawReadAfterWriteback(dut, dirty + 4)
      completeCleanDUncachedReadNoWrite(dut, clean + 4)
      requireDdrMiss(dut, clean)
    }
  }

  it should "drain a cancelled I uncached owner before the next D uncached probe" in {
    test(new L2CacheTestHarness).withAnnotations(verilator) { dut =>
      resetDut(dut)
      val first = BigInt("8002c000", 16)
      val next = BigInt("80030000", 16)
      fillCleanLine(dut, first)
      fillCleanLine(dut, next)
      sendIUncachedRead(dut, first + 4)
      waitForAr(dut)
      dut.io.axi.ar.data.araddr.expect((first + 4).U)
      dut.io.iCancel.poke(true.B)
      dut.clock.step()
      dut.io.iCancel.poke(false.B)
      dut.io.iRespValid.expect(false.B)
      dut.io.axi.ar.arready.poke(true.B)
      dut.clock.step()
      dut.io.axi.ar.arready.poke(false.B)
      dut.io.axi.r.data.rvalid.poke(true.B)
      dut.io.axi.r.data.rdata.poke("h13579bdf".U)
      dut.io.axi.r.data.rlast.poke(true.B)
      dut.io.axi.r.rready.expect(true.B)
      dut.io.iRespValid.expect(false.B)
      dut.clock.step()
      dut.io.axi.r.data.rvalid.poke(false.B)
      dut.io.axi.r.data.rlast.poke(false.B)
      for (_ <- 0 until 6) {
        dut.io.iRespValid.expect(false.B)
        dut.clock.step()
      }
      completeCleanDUncachedReadNoWrite(dut, next + 4)
      requireDdrMiss(dut, next)
    }
  }


}
