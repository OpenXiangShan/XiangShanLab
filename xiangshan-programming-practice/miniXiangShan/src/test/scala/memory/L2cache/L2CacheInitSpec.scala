package minixiangshan.mem.L2cache

import chisel3._
import chiseltest._
import chiseltest.simulator.{VerilatorBackendAnnotation, VerilatorCFlags, VerilatorFlags}
import minixiangshan.config._
import org.scalatest.flatspec.AnyFlatSpec

class L2CacheInitSpec extends AnyFlatSpec with ChiselScalatestTester {
  behavior of "L2Cache metadata initialization"

  private implicit val p: Parameters =
    new Parameters(Map(DebugConfigKeys.EnableDifftest -> true))

  private val verilator = Seq(
    VerilatorBackendAnnotation,
    VerilatorFlags(Seq("--output-split", "0")),
    VerilatorCFlags(Seq("-DWData=IData"))
  )

  it should "reject all native requests during the 512 scrub cycles" in {
    test(new L2CacheTestHarness).withAnnotations(verilator) { dut =>
      dut.io.iReq.valid.poke(true.B)
      dut.io.iReq.bits.id.poke(0.U)
      dut.io.iReq.bits.addr.poke("h80000000".U)
      dut.io.iReq.bits.size.poke(2.U)
      dut.io.iReq.bits.uncache.poke(false.B)
      dut.io.iRespReady.poke(true.B)

      dut.io.dReadReq.valid.poke(true.B)
      dut.io.dReadReq.bits.id.poke(0.U)
      dut.io.dReadReq.bits.addr.poke("h80000040".U)
      dut.io.dReadReq.bits.size.poke(2.U)
      dut.io.dReadReq.bits.uncache.poke(false.B)
      dut.io.dReadRespReady.poke(true.B)

      dut.io.dWriteReqValid.poke(true.B)
      dut.io.dWriteReqId.poke(0.U)
      dut.io.dWriteReqAddr.poke("h80000080".U)
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

      dut.reset.poke(true.B)
      dut.clock.step(2)
      dut.reset.poke(false.B)

      for (_ <- 0 until 512) {
        dut.io.iReq.ready.expect(false.B)
        dut.io.dReadReq.ready.expect(false.B)
        dut.io.dWriteReqReady.expect(false.B)
        dut.clock.step()
      }

      dut.io.dReadReq.valid.poke(false.B)
      dut.io.dWriteReqValid.poke(false.B)
      dut.io.iReq.ready.expect(true.B)

      dut.io.iReq.valid.poke(false.B)
      dut.io.dReadReq.valid.poke(true.B)
      dut.io.dReadReq.ready.expect(true.B)

      dut.io.dReadReq.valid.poke(false.B)
      dut.io.dWriteReqValid.poke(true.B)
      dut.io.dWriteReqReady.expect(true.B)
    }
  }
}
