package minixiangshan.mem.L2cache

import chisel3._
import chisel3.util._
import chiseltest._
import chiseltest.simulator.{VerilatorBackendAnnotation, VerilatorCFlags, VerilatorFlags}
import minixiangshan.axi._
import minixiangshan.config._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

// Verilator 5将宽端口表示为VlWide，老版chiseltest的harness无法直接访问。
// 该test-only包装器将两个512位端口拆成16个32位word，不改动L2 RTL。
class L2CacheTestHarness(implicit p: Parameters) extends NSModule {
  val io = IO(new Bundle {
    val iReq = Flipped(Decoupled(new L2ReadReq(1)))
    val iCancel = Input(Bool())
    val iRespValid = Output(Bool())
    val iRespReady = Input(Bool())
    val iRespId = Output(UInt(1.W))
    val iRespWords = Output(Vec(l2BurstBeats, UInt(XLEN.W)))
    val iRespFullLine = Output(Bool())
    val iRespLast = Output(Bool())

    val dReadReq = Flipped(Decoupled(new L2ReadReq(1)))
    val dReadRespReady = Input(Bool())
    val dReadRespValid = Output(Bool())
    val dReadRespId = Output(UInt(1.W))
    val dReadRespWords = Output(Vec(l2BurstBeats, UInt(XLEN.W)))
    val dReadRespFullLine = Output(Bool())
    val dReadRespLast = Output(Bool())
    val dWriteReqValid = Input(Bool())
    val dWriteReqReady = Output(Bool())
    val dWriteReqId = Input(UInt(1.W))
    val dWriteReqAddr = Input(UInt(XLEN.W))
    val dWriteReqKind = Input(UInt(L2WriteKind.width.W))
    val dWriteReqSize = Input(UInt(3.W))
    val dWriteReqWords = Input(Vec(l2BurstBeats, UInt(XLEN.W)))
    val dWriteReqStrb = Input(UInt(l2BeatBytes.W))
    val dWriteDoneReady = Input(Bool())
    val dWriteDoneValid = Output(Bool())
    val dWriteDoneId = Output(UInt(1.W))

    val axi = new AXI3MasterIO
  })

  val cache = Module(new L2Cache)
  cache.io.icache.req <> io.iReq
  cache.io.icache.cancel := io.iCancel
  io.iRespValid := cache.io.icache.resp.valid
  cache.io.icache.resp.ready := io.iRespReady
  io.iRespId := cache.io.icache.resp.bits.id
  io.iRespWords := cache.io.icache.resp.bits.data.asTypeOf(
    Vec(l2BurstBeats, UInt(XLEN.W)))
  io.iRespFullLine := cache.io.icache.resp.bits.fullLine
  io.iRespLast := cache.io.icache.resp.bits.last

  cache.io.dcache.read.req <> io.dReadReq
  cache.io.dcache.read.cancel := false.B
  cache.io.dcache.read.resp.ready := io.dReadRespReady
  io.dReadRespValid := cache.io.dcache.read.resp.valid
  io.dReadRespId := cache.io.dcache.read.resp.bits.id
  io.dReadRespWords := cache.io.dcache.read.resp.bits.data.asTypeOf(
    Vec(l2BurstBeats, UInt(XLEN.W)))
  io.dReadRespFullLine := cache.io.dcache.read.resp.bits.fullLine
  io.dReadRespLast := cache.io.dcache.read.resp.bits.last
  cache.io.dcache.write.req.valid := io.dWriteReqValid
  io.dWriteReqReady := cache.io.dcache.write.req.ready
  cache.io.dcache.write.req.bits.id := io.dWriteReqId
  cache.io.dcache.write.req.bits.addr := io.dWriteReqAddr
  cache.io.dcache.write.req.bits.kind := io.dWriteReqKind
  cache.io.dcache.write.req.bits.size := io.dWriteReqSize
  cache.io.dcache.write.req.bits.data := io.dWriteReqWords.asUInt
  cache.io.dcache.write.req.bits.strb := io.dWriteReqStrb
  cache.io.dcache.write.done.ready := io.dWriteDoneReady
  io.dWriteDoneValid := cache.io.dcache.write.done.valid
  io.dWriteDoneId := cache.io.dcache.write.done.bits.id

  cache.io.maintenance.req.valid := false.B
  cache.io.maintenance.req.bits.op := 0.U
  cache.io.maintenance.req.bits.addr := 0.U
  cache.io.maintenance.done.ready := true.B
  io.axi <> cache.io.axi
}

class L2CacheSpec
    extends AnyFlatSpec
    with ChiselScalatestTester
    with Matchers {
  behavior of "L2Cache"

  private implicit val p: Parameters =
    new Parameters(Map(DebugConfigKeys.EnableDifftest -> true))

  // Verilator 5移除了旧WData别名；包装器已确保顶层无宽端口。
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
    for (word <- 0 until 16) dut.io.dWriteReqWords(word).poke(0.U)
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

  private def sendIRead(
      dut: L2CacheTestHarness,
      addr: BigInt,
      id: Int = 0,
      uncache: Boolean = false
  ): Unit = {
    dut.io.iReq.valid.poke(true.B)
    dut.io.iReq.bits.id.poke(id.U)
    dut.io.iReq.bits.addr.poke(addr.U)
    dut.io.iReq.bits.size.poke(2.U)
    dut.io.iReq.bits.uncache.poke(uncache.B)
    var wait = 0
    while (!dut.io.iReq.ready.peek().litToBoolean && wait < 80) {
      dut.clock.step()
      wait += 1
    }
    withClue(s"read request 0x${addr.toString(16)} was not accepted") {
      dut.io.iReq.ready.peek().litToBoolean shouldBe true
    }
    dut.clock.step()
    dut.io.iReq.valid.poke(false.B)
  }

  it should "register an I request before any L2 or AXI activity" in {
    test(new L2CacheTestHarness).withAnnotations(verilator) { dut =>
      resetDut(dut)
      val addr = BigInt("86000040", 16)
      dut.io.iReq.valid.poke(true.B)
      dut.io.iReq.bits.addr.poke(addr.U)
      dut.io.iReq.ready.expect(true.B)
      dut.io.axi.ar.data.arvalid.expect(false.B)
      dut.clock.step()
      dut.io.iReq.valid.poke(false.B)
      dut.io.iReq.ready.expect(false.B)
      dut.io.axi.ar.data.arvalid.expect(false.B)
    }
  }

  it should "cancel a pending registered I request without issuing it" in {
    test(new L2CacheTestHarness).withAnnotations(verilator) { dut =>
      resetDut(dut)
      val addr = BigInt("86000140", 16)
      dut.io.iReq.valid.poke(true.B)
      dut.io.iReq.bits.addr.poke(addr.U)
      dut.io.iReq.ready.expect(true.B)
      dut.clock.step()
      dut.io.iReq.valid.poke(false.B)
      dut.io.iCancel.poke(true.B)
      dut.clock.step()
      dut.io.iCancel.poke(false.B)
      for (_ <- 0 until 8) {
        dut.io.axi.ar.data.arvalid.expect(false.B)
        dut.io.iRespValid.expect(false.B)
        dut.clock.step()
      }
      dut.io.iReq.ready.expect(true.B)
    }
  }

  it should "drain a cancelled I miss into L2 without responding to ICache" in {
    test(new L2CacheTestHarness).withAnnotations(verilator) { dut =>
      resetDut(dut)
      val addr = BigInt("86000240", 16)
      val words = (0 until 16).map(i => BigInt(0x72000000L + i))
      sendIRead(dut, addr)
      var wait = 0
      while (!dut.io.axi.ar.data.arvalid.peek().litToBoolean && wait < 40) {
        dut.clock.step(); wait += 1
      }
      dut.io.axi.ar.data.arvalid.expect(true.B)
      dut.io.axi.ar.arready.poke(true.B)
      dut.clock.step()
      dut.io.axi.ar.arready.poke(false.B)
      dut.io.iCancel.poke(true.B)
      dut.clock.step()
      dut.io.iCancel.poke(false.B)
      for (beat <- 0 until 16) {
        dut.io.axi.r.data.rvalid.poke(true.B)
        dut.io.axi.r.data.rdata.poke(words(beat).U)
        dut.io.axi.r.data.rlast.poke((beat == 15).B)
        dut.io.axi.r.rready.expect(true.B)
        dut.io.iRespValid.expect(false.B)
        dut.clock.step()
      }
      dut.io.axi.r.data.rvalid.poke(false.B)
      for (_ <- 0 until 20) { dut.io.iRespValid.expect(false.B); dut.clock.step() }
      sendIRead(dut, addr)
      wait = 0
      while (!dut.io.iRespValid.peek().litToBoolean && wait < 40) {
        dut.io.axi.ar.data.arvalid.expect(false.B)
        dut.clock.step(); wait += 1
      }
      dut.io.iRespValid.expect(true.B)
      dut.io.iRespFullLine.expect(true.B)
      for (word <- 0 until 16) dut.io.iRespWords(word).expect(words(word).U)
    }
  }
  it should "cancel an already buffered L2 hit and accept the next I request" in {
    test(new L2CacheTestHarness).withAnnotations(verilator) { dut =>
      resetDut(dut)
      val hitAddr = BigInt("86000380", 16)
      val nextAddr = BigInt("86000480", 16)
      val words = (0 until 16).map(i => BigInt(0x73000000L + i))
      dut.io.dWriteReqValid.poke(true.B)
      dut.io.dWriteReqAddr.poke(hitAddr.U)
      dut.io.dWriteReqKind.poke(L2WriteKind.putLine)
      for (word <- 0 until 16) dut.io.dWriteReqWords(word).poke(words(word).U)
      dut.io.dWriteReqReady.expect(true.B)
      dut.clock.step()
      dut.io.dWriteReqValid.poke(false.B)
      dut.clock.step(8)

      dut.io.iRespReady.poke(false.B)
      sendIRead(dut, hitAddr)
      var wait = 0
      while (!dut.io.iRespValid.peek().litToBoolean && wait < 40) {
        dut.clock.step(); wait += 1
      }
      dut.io.iRespValid.expect(true.B)
      dut.io.iCancel.poke(true.B)
      dut.clock.step()
      dut.io.iCancel.poke(false.B)
      dut.io.iRespValid.expect(false.B)
      dut.io.iRespReady.poke(true.B)
      sendIRead(dut, nextAddr)
      wait = 0
      while (!dut.io.axi.ar.data.arvalid.peek().litToBoolean && wait < 40) {
        dut.clock.step(); wait += 1
      }
      dut.io.axi.ar.data.arvalid.expect(true.B)
      dut.io.axi.ar.data.araddr.expect(nextAddr.U)
    }
  }
  it should "keep a new owner active when the prior normal MSHR cleans up" in {
    test(new L2CacheTestHarness).withAnnotations(verilator) { dut =>
      resetDut(dut)
      val oldAddr = BigInt("82100000", 16)
      val targetAddr = BigInt("82100140", 16)

      dut.io.iRespReady.poke(false.B)
      sendIRead(dut, oldAddr)
      var wait = 0
      while (!dut.io.axi.ar.data.arvalid.peek().litToBoolean && wait < 40) {
        dut.clock.step()
        wait += 1
      }
      dut.io.axi.ar.data.arvalid.expect(true.B)
      dut.io.axi.ar.data.araddr.expect(oldAddr.U)
      dut.io.axi.ar.arready.poke(true.B)
      dut.clock.step()
      dut.io.axi.ar.arready.poke(false.B)

      for (beat <- 0 until 16) {
        dut.io.axi.r.data.rvalid.poke(true.B)
        dut.io.axi.r.data.rdata.poke((0x56000000L + beat).U)
        dut.io.axi.r.data.rlast.poke((beat == 15).B)
        dut.io.axi.r.rready.expect(true.B)
        dut.clock.step()
      }
      dut.io.axi.r.data.rvalid.poke(false.B)
      dut.io.axi.r.data.rlast.poke(false.B)

      dut.io.iRespReady.poke(true.B)
      var consumed = 0
      while (consumed < 15) {
        if (dut.io.iRespValid.peek().litToBoolean) {
          dut.io.iRespLast.expect(false.B)
          consumed += 1
        }
        dut.clock.step()
      }
      wait = 0
      while (!dut.io.iRespValid.peek().litToBoolean && wait < 40) {
        dut.clock.step()
        wait += 1
      }
      dut.io.iRespValid.expect(true.B)
      dut.io.iRespLast.expect(true.B)

      // Capture the next miss on the old request's accepted last response.
      dut.io.iReq.valid.poke(true.B)
      dut.io.iReq.bits.addr.poke(targetAddr.U)
      dut.io.iReq.bits.uncache.poke(false.B)
      dut.io.iReq.ready.expect(true.B)
      dut.clock.step()
      dut.io.iReq.valid.poke(false.B)

      // On this edge the new lookup is accepted while the prior MSHR cleans up.
      // Cancelling immediately afterwards must still target the new owner.
      dut.clock.step()
      dut.io.iCancel.poke(true.B)
      dut.clock.step()
      dut.io.iCancel.poke(false.B)

      wait = 0
      while (!dut.io.axi.ar.data.arvalid.peek().litToBoolean && wait < 40) {
        dut.io.iRespValid.expect(false.B)
        dut.clock.step()
        wait += 1
      }
      dut.io.axi.ar.data.arvalid.expect(true.B)
      dut.io.axi.ar.data.araddr.expect(targetAddr.U)
      dut.io.axi.ar.arready.poke(true.B)
      dut.clock.step()
      dut.io.axi.ar.arready.poke(false.B)
      for (beat <- 0 until 16) {
        dut.io.axi.r.data.rvalid.poke(true.B)
        dut.io.axi.r.data.rdata.poke((0x57000000L + beat).U)
        dut.io.axi.r.data.rlast.poke((beat == 15).B)
        dut.io.axi.r.rready.expect(true.B)
        dut.io.iRespValid.expect(false.B)
        dut.clock.step()
      }
      dut.io.axi.r.data.rvalid.poke(false.B)
      dut.io.axi.r.data.rlast.poke(false.B)
      for (_ <- 0 until 20) {
        dut.io.iRespValid.expect(false.B)
        dut.clock.step()
      }
    }
  }

  it should "accept a putLine into the D STB and return it as one full line" in {
    test(new L2CacheTestHarness).withAnnotations(verilator) { dut =>
      resetDut(dut)
      val addr = BigInt("80000180", 16)
      val words = (0 until 16).map(i => BigInt(0x31000000L + i))

      dut.io.dWriteReqValid.poke(true.B)
      dut.io.dWriteReqId.poke(1.U)
      dut.io.dWriteReqAddr.poke(addr.U)
      dut.io.dWriteReqKind.poke(L2WriteKind.putLine)
      for (word <- 0 until 16) dut.io.dWriteReqWords(word).poke(words(word).U)
      dut.io.dWriteReqReady.expect(true.B)
      dut.clock.step()
      dut.io.dWriteReqValid.poke(false.B)

      sendIRead(dut, addr, id = 1)
      var wait = 0
      while (!dut.io.iRespValid.peek().litToBoolean && wait < 40) {
        dut.io.axi.ar.data.arvalid.expect(false.B)
        dut.clock.step()
        wait += 1
      }
      dut.io.iRespValid.expect(true.B)
      dut.io.iRespId.expect(1.U)
      dut.io.iRespFullLine.expect(true.B)
      dut.io.iRespLast.expect(true.B)
      for (word <- 0 until 16) dut.io.iRespWords(word).expect(words(word).U)
    }
  }

  it should "forward a putLine accepted after Array response but before result commit" in {
    test(new L2CacheTestHarness).withAnnotations(verilator) { dut =>
      resetDut(dut)
      val addr = BigInt("80000280", 16)
      val words = (0 until 16).map(i => BigInt(0x33000000L + i))

      // Capture the read, then let its lookup reach the D2/Array response cycle.
      sendIRead(dut, addr, id = 1)
      dut.clock.step(2)

      // This write is accepted on the same edge that the lookup result enters
      // resultQueue. It was not visible to the earlier parallel STB snapshot.
      dut.io.dWriteReqValid.poke(true.B)
      dut.io.dWriteReqId.poke(1.U)
      dut.io.dWriteReqAddr.poke(addr.U)
      dut.io.dWriteReqKind.poke(L2WriteKind.putLine)
      for (word <- 0 until 16) {
        dut.io.dWriteReqWords(word).poke(words(word).U)
      }
      dut.io.dWriteReqReady.expect(true.B)
      dut.clock.step()
      dut.io.dWriteReqValid.poke(false.B)

      var wait = 0
      while (!dut.io.iRespValid.peek().litToBoolean && wait < 24) {
        dut.io.axi.ar.data.arvalid.expect(false.B)
        dut.clock.step()
        wait += 1
      }
      dut.io.iRespValid.expect(true.B)
      dut.io.iRespFullLine.expect(true.B)
      dut.io.iRespLast.expect(true.B)
      dut.io.axi.ar.data.arvalid.expect(false.B)
      for (word <- 0 until 16) {
        dut.io.iRespWords(word).expect(words(word).U)
      }
    }
  }

  it should "stream a DDR miss to L1, install it, then hit as a full line" in {
    test(new L2CacheTestHarness).withAnnotations(verilator) { dut =>
      resetDut(dut)
      val addr = BigInt("81000440", 16)
      val words = (0 until 16).map(i => BigInt(0x42000000L + i))

      dut.io.iRespReady.poke(false.B)
      sendIRead(dut, addr, id = 1)

      var wait = 0
      while (!dut.io.axi.ar.data.arvalid.peek().litToBoolean && wait < 40) {
        dut.clock.step()
        wait += 1
      }
      dut.io.axi.ar.data.arvalid.expect(true.B)
      dut.io.axi.ar.data.araddr.expect(addr.U)
      dut.io.axi.ar.data.arlen.expect(15.U)
      dut.io.axi.ar.data.arsize.expect(2.U)
      dut.io.axi.ar.arready.poke(true.B)
      dut.clock.step()
      dut.io.axi.ar.arready.poke(false.B)

      for (beat <- 0 until 16) {
        dut.io.axi.r.data.rvalid.poke(true.B)
        dut.io.axi.r.data.rdata.poke(words(beat).U)
        dut.io.axi.r.data.rlast.poke((beat == 15).B)
        dut.io.axi.r.rready.expect(true.B)
        dut.clock.step()
      }
      dut.io.axi.r.data.rvalid.poke(false.B)

      dut.io.iRespReady.poke(true.B)
      for (beat <- 0 until 16) {
        var responseWait = 0
        while (!dut.io.iRespValid.peek().litToBoolean && responseWait < 40) {
          dut.clock.step()
          responseWait += 1
        }
        dut.io.iRespValid.expect(true.B)
        dut.io.iRespId.expect(1.U)
        dut.io.iRespFullLine.expect(false.B)
        dut.io.iRespWords(0).expect(words(beat).U)
        dut.io.iRespLast.expect((beat == 15).B)
        dut.clock.step()
      }

      dut.clock.step(3)
      sendIRead(dut, addr, id = 0)
      wait = 0
      while (!dut.io.iRespValid.peek().litToBoolean && wait < 40) {
        dut.io.axi.ar.data.arvalid.expect(false.B)
        dut.clock.step()
        wait += 1
      }
      dut.io.iRespValid.expect(true.B)
      dut.io.iRespId.expect(0.U)
      dut.io.iRespFullLine.expect(true.B)
      dut.io.iRespLast.expect(true.B)
      for (word <- 0 until 16) dut.io.iRespWords(word).expect(words(word).U)
    }
  }

  it should "serialize later cacheable reads behind an uncached read" in {
    test(new L2CacheTestHarness).withAnnotations(verilator) { dut =>
      resetDut(dut)
      sendIRead(dut, BigInt("1c00102c", 16), uncache = true)

      dut.io.iReq.valid.poke(true.B)
      dut.io.iReq.bits.id.poke(0.U)
      dut.io.iReq.bits.addr.poke(BigInt("80002000", 16).U)
      dut.io.iReq.bits.size.poke(2.U)
      dut.io.iReq.bits.uncache.poke(false.B)
      // Once an uncache request owns the drain, the non-flowing boundary
      // rejects younger cacheable requests until that transaction completes.
      for (_ <- 0 until 4) {
        dut.io.iReq.ready.expect(false.B)
        // The outstanding uncache may hold ARVALID; the younger cacheable line
        // must never replace or bypass it.
        if (dut.io.axi.ar.data.arvalid.peek().litToBoolean) {
          dut.io.axi.ar.data.araddr.expect(BigInt("1c00102c", 16).U)
        }
        dut.clock.step()
      }
      dut.io.iReq.valid.poke(false.B)
    }
  }

  it should "give a same-cycle uncached write exclusive admission" in {
    test(new L2CacheTestHarness).withAnnotations(verilator) { dut =>
      resetDut(dut)

      dut.io.dWriteReqValid.poke(true.B)
      dut.io.dWriteReqId.poke(1.U)
      dut.io.dWriteReqAddr.poke(BigInt("1c002003", 16).U)
      dut.io.dWriteReqKind.poke(L2WriteKind.uncache)
      dut.io.dWriteReqSize.poke(0.U)
      dut.io.dWriteReqWords(0).poke("h7f000000".U)
      dut.io.dWriteReqStrb.poke(8.U)

      dut.io.iReq.valid.poke(true.B)
      dut.io.iReq.bits.id.poke(0.U)
      dut.io.iReq.bits.addr.poke(BigInt("80003000", 16).U)
      dut.io.iReq.bits.size.poke(2.U)
      dut.io.iReq.bits.uncache.poke(false.B)

      dut.io.dWriteReqReady.expect(true.B)
      // The write keeps exclusive internal admission; I may only enter its
      // boundary register and cannot issue a cacheable lookup yet.
      dut.io.iReq.ready.expect(true.B)
      dut.clock.step()
      dut.io.iReq.valid.poke(false.B)
      dut.io.dWriteReqValid.poke(false.B)
      for (_ <- 0 until 3) {
        dut.io.iReq.ready.expect(false.B)
        dut.io.axi.ar.data.arvalid.expect(false.B)
        dut.clock.step()
      }
    }
  }
  it should "hold a new I miss in the entry while a cancelled miss drains" in {
    test(new L2CacheTestHarness).withAnnotations(verilator) { dut =>
      resetDut(dut)
      val staleAddr = BigInt("82000000", 16)
      val targetAddr = BigInt("82000140", 16)

      sendIRead(dut, staleAddr)
      var wait = 0
      while (!dut.io.axi.ar.data.arvalid.peek().litToBoolean && wait < 40) {
        dut.clock.step()
        wait += 1
      }
      dut.io.axi.ar.data.arvalid.expect(true.B)
      dut.io.axi.ar.data.araddr.expect(staleAddr.U)
      dut.io.axi.ar.arready.poke(true.B)
      dut.clock.step()
      dut.io.axi.ar.arready.poke(false.B)

      dut.io.iCancel.poke(true.B)
      dut.clock.step()
      dut.io.iCancel.poke(false.B)

      // The redirected target may enter the one-entry boundary immediately.
      dut.io.iReq.valid.poke(true.B)
      dut.io.iReq.bits.addr.poke(targetAddr.U)
      dut.io.iReq.bits.uncache.poke(false.B)
      dut.io.iReq.ready.expect(true.B)
      dut.clock.step()
      dut.io.iReq.valid.poke(false.B)

      // The stale DDR transaction is still outstanding. The target request
      // must remain queued: no lookup consumption, second AR, or response.
      for (_ <- 0 until 8) {
        dut.io.iReq.ready.expect(false.B)
        dut.io.axi.ar.data.arvalid.expect(false.B)
        dut.io.iRespValid.expect(false.B)
        dut.clock.step()
      }
    }
  }

  it should "give an STB a bounded lookup turn under continuous L1 reads" in {
    test(new L2CacheTestHarness).withAnnotations(verilator) { dut =>
      resetDut(dut)
      val readLines = (0 until 6).map(i => BigInt("84001000", 16) + i * 0x40)
      val stbLines = Seq(BigInt("83002000", 16), BigInt("83002040", 16))
      val thirdLine = BigInt("83002080", 16)

      // 先用putLine安装多条命中行，再形成每拍可接收的普通lookup流。
      for (addr <- readLines) {
        dut.io.dWriteReqValid.poke(true.B)
        dut.io.dWriteReqAddr.poke(addr.U)
        dut.io.dWriteReqKind.poke(L2WriteKind.putLine)
        dut.io.dWriteReqReady.expect(true.B)
        dut.clock.step()
        dut.io.dWriteReqValid.poke(false.B)
        dut.clock.step(8)
      }

      dut.io.iReq.valid.poke(true.B)
      dut.io.dReadReq.valid.poke(true.B)
      var readIndex = 0
      for (addr <- stbLines) {
        dut.io.iReq.bits.addr.poke(readLines(readIndex).U)
        dut.io.dReadReq.bits.addr.poke(readLines(readIndex + 1).U)
        dut.io.dWriteReqValid.poke(true.B)
        dut.io.dWriteReqAddr.poke(addr.U)
        dut.io.dWriteReqKind.poke(L2WriteKind.putLine)
        var boundaryWait = 0
        while (!dut.io.dWriteReqReady.peek().litToBoolean && boundaryWait < 8) {
          dut.clock.step()
          boundaryWait += 1
        }
        dut.io.dWriteReqReady.expect(true.B)
        dut.clock.step()
        readIndex += 2
      }

      dut.io.dWriteReqAddr.poke(thirdLine.U)
      var thirdAccepted = false
      var cycles = 0
      while (!thirdAccepted && cycles < 24) {
        dut.io.iReq.bits.addr.poke(readLines(readIndex % readLines.length).U)
        dut.io.dReadReq.bits.addr.poke(readLines((readIndex + 1) % readLines.length).U)
        thirdAccepted = dut.io.dWriteReqReady.peek().litToBoolean
        dut.clock.step()
        readIndex += 2
        cycles += 1
      }
      withClue("continuous L1 lookups must not starve STB installation") {
        thirdAccepted shouldBe true
      }
    }
  }

  it should "block new cacheable admissions while an uncache request waits for drain" in {
    test(new L2CacheTestHarness).withAnnotations(verilator) { dut =>
      resetDut(dut)
      val activeLine = BigInt("85000000", 16)
      val uncacheAddr = BigInt("1f000004", 16)
      val blockedLine = BigInt("85000140", 16)

      // 先保留一个未完成miss，再让uncache请求持续等待排空。
      sendIRead(dut, activeLine)
      dut.clock.step(4)
      dut.io.iReq.valid.poke(true.B)
      dut.io.iReq.bits.addr.poke(uncacheAddr.U)
      dut.io.iReq.bits.uncache.poke(true.B)

      // Capture the uncache into the registered I boundary first. Requiring
      // its raw bits to block D in this same cycle would recreate the path we
      // are deliberately cutting.
      dut.io.iReq.ready.expect(true.B)
      dut.clock.step()
      dut.io.iReq.valid.poke(false.B)

      dut.io.dReadReq.valid.poke(true.B)
      dut.io.dReadReq.bits.addr.poke(blockedLine.U)
      dut.io.dReadReq.bits.uncache.poke(false.B)
      dut.io.iReq.ready.expect(false.B)
      dut.io.dReadReq.ready.expect(false.B)
      dut.io.dReadReq.valid.poke(false.B)

      var wait = 0
      while (!dut.io.axi.ar.data.arvalid.peek().litToBoolean && wait < 40) {
        dut.clock.step()
        wait += 1
      }
      dut.io.axi.ar.data.arvalid.expect(true.B)
      dut.io.axi.ar.data.araddr.expect(activeLine.U)
      dut.io.axi.ar.arready.poke(true.B)
      dut.clock.step()
      dut.io.axi.ar.arready.poke(false.B)

      for (beat <- 0 until 16) {
        dut.io.axi.r.data.rvalid.poke(true.B)
        dut.io.axi.r.data.rdata.poke((0x61000000L + beat).U)
        dut.io.axi.r.data.rlast.poke((beat == 15).B)
        dut.io.axi.r.rready.expect(true.B)
        dut.clock.step()
      }
      dut.io.axi.r.data.rvalid.poke(false.B)
      dut.io.axi.r.data.rlast.poke(false.B)

      wait = 0
      while (!dut.io.axi.ar.data.arvalid.peek().litToBoolean && wait < 100) {
        dut.clock.step()
        wait += 1
      }
      withClue("queued uncache must issue after old cache work drains") {
        dut.io.axi.ar.data.arvalid.peek().litToBoolean shouldBe true
      }
      dut.io.axi.ar.data.araddr.expect(uncacheAddr.U)
    }
  }

}
