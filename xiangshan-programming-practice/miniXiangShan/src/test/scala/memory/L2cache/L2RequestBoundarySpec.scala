package minixiangshan.mem.L2cache

import chisel3._
import chiseltest._
import chiseltest.simulator.{VerilatorBackendAnnotation, VerilatorCFlags, VerilatorFlags}
import minixiangshan.config._
import org.scalatest.flatspec.AnyFlatSpec

class L2RequestBoundarySpec extends AnyFlatSpec with ChiselScalatestTester {
  private implicit val p: Parameters =
    new Parameters(Map(DebugConfigKeys.EnableDifftest -> true))

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

  it should "capture an I request without issuing a DDR read in the same cycle" in {
    test(new L2CacheTestHarness).withAnnotations(verilator) { dut =>
      resetDut(dut)
      dut.io.iReq.valid.poke(true.B)
      dut.io.iReq.bits.addr.poke(BigInt("81000000", 16).U)
      dut.io.iReq.ready.expect(true.B)
      dut.io.axi.ar.data.arvalid.expect(false.B)
      dut.clock.step()
      dut.io.iReq.valid.poke(false.B)
      // The request may only enter lookup from this cycle onward.
      dut.io.axi.ar.data.arvalid.expect(false.B)
      var wait = 0
      while (!dut.io.axi.ar.data.arvalid.peek().litToBoolean && wait < 20) {
        dut.clock.step()
        wait += 1
      }
      dut.io.axi.ar.data.arvalid.expect(true.B)
      dut.io.axi.ar.data.araddr.expect(BigInt("81000000", 16).U)
    }
  }

  it should "drop a pending entry when cancel arrives before lookup" in {
    test(new L2CacheTestHarness).withAnnotations(verilator) { dut =>
      resetDut(dut)
      // Keep lookup busy with D so the I request remains in its entry.
      dut.io.dReadReq.valid.poke(true.B)
      dut.io.dReadReq.bits.addr.poke(BigInt("82000000", 16).U)
      dut.io.iReq.valid.poke(true.B)
      dut.io.iReq.bits.addr.poke(BigInt("83000000", 16).U)
      dut.io.iReq.ready.expect(true.B)
      dut.clock.step()
      dut.io.iReq.valid.poke(false.B)
      dut.io.iCancel.poke(true.B)
      dut.clock.step()
      dut.io.iCancel.poke(false.B)
      dut.io.dReadReq.valid.poke(false.B)
      for (_ <- 0 until 12) {
        if (dut.io.axi.ar.data.arvalid.peek().litToBoolean) {
          dut.io.axi.ar.data.araddr.expect(BigInt("82000000", 16).U)
        }
        dut.clock.step()
      }
    }
  }
}
