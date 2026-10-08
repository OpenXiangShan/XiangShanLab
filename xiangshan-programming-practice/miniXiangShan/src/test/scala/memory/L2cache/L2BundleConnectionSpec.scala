package minixiangshan.mem.L2cache

import chisel3._
import chiseltest._
import minixiangshan.config._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class L2BundlePassthrough(idWidth: Int)(implicit p: Parameters) extends NSModule {
  val io = IO(new Bundle {
    val nativeIn = Flipped(new L2NativeMasterIO(idWidth))
    val nativeOut = new L2NativeMasterIO(idWidth)
    val bridgeIn = Flipped(new L2BridgeClientIO)
    val bridgeOut = new L2BridgeClientIO
    val maintenanceIn = Flipped(new L2MaintenanceMasterIO)
    val maintenanceOut = new L2MaintenanceMasterIO
  })

  io.nativeOut <> io.nativeIn
  io.bridgeOut <> io.bridgeIn
  io.maintenanceOut <> io.maintenanceIn
}

class L2BundleConnectionSpec
    extends AnyFlatSpec
    with ChiselScalatestTester
    with Matchers {
  behavior of "L2 cache interface bundles"

  private implicit val p: Parameters =
    new Parameters(Map(DebugConfigKeys.EnableDifftest -> true))

  it should "connect grouped master and flipped slave interfaces without truncation" in {
    test(new L2BundlePassthrough(3)) { dut =>
      dut.io.nativeIn.read.req.valid.poke(true.B)
      dut.io.nativeIn.read.req.bits.id.poke(7.U)
      dut.io.nativeIn.read.req.bits.addr.poke("h12345678".U)
      dut.io.nativeIn.read.req.bits.size.poke(2.U)
      dut.io.nativeIn.read.req.bits.uncache.poke(false.B)
      dut.io.nativeOut.read.req.ready.poke(true.B)

      dut.io.nativeOut.read.req.valid.expect(true.B)
      dut.io.nativeOut.read.req.bits.id.expect(7.U)
      dut.io.nativeOut.read.req.bits.addr.expect("h12345678".U)
      dut.io.nativeIn.read.req.ready.expect(true.B)

      dut.io.bridgeIn.read.req.valid.poke(true.B)
      dut.io.bridgeIn.read.req.bits.owner.source.poke(3.U)
      dut.io.bridgeIn.read.req.bits.owner.slot.poke(3.U)
      dut.io.bridgeIn.read.req.bits.addr.poke("h80000040".U)
      dut.io.bridgeIn.read.req.bits.isLine.poke(true.B)
      dut.io.bridgeIn.read.req.bits.size.poke(2.U)
      dut.io.bridgeOut.read.req.ready.poke(true.B)

      dut.io.bridgeOut.read.req.valid.expect(true.B)
      dut.io.bridgeOut.read.req.bits.owner.source.expect(3.U)
      dut.io.bridgeOut.read.req.bits.owner.slot.expect(3.U)
      dut.io.bridgeIn.read.req.ready.expect(true.B)

      dut.io.maintenanceIn.req.valid.poke(true.B)
      dut.io.maintenanceIn.req.bits.op.poke(0x1f.U)
      dut.io.maintenanceIn.req.bits.addr.poke("h80000100".U)
      dut.io.maintenanceOut.req.ready.poke(true.B)
      dut.io.maintenanceOut.req.valid.expect(true.B)
      dut.io.maintenanceOut.req.bits.op.expect(0x1f.U)
      dut.io.maintenanceIn.req.ready.expect(true.B)
    }
  }
}
