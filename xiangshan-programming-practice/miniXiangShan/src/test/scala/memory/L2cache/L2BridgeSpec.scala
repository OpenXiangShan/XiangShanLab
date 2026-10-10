package minixiangshan.mem.L2cache

import chisel3._
import chiseltest._
import minixiangshan.config._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class L2BridgeSpec
    extends AnyFlatSpec
    with ChiselScalatestTester
    with Matchers {
  behavior of "L2Bridge"

  private implicit val p: Parameters =
    new Parameters(Map(DebugConfigKeys.EnableDifftest -> true))

  private def init(dut: L2Bridge): Unit = {
    dut.io.client.read.req.valid.poke(false.B)
    dut.io.client.read.req.bits.owner.source.poke(0.U)
    dut.io.client.read.req.bits.owner.slot.poke(0.U)
    dut.io.client.read.req.bits.addr.poke(0.U)
    dut.io.client.read.req.bits.isLine.poke(false.B)
    dut.io.client.read.req.bits.size.poke(0.U)
    dut.io.client.read.beat.ready.poke(false.B)

    dut.io.client.write.req.valid.poke(false.B)
    dut.io.client.write.req.bits.owner.source.poke(0.U)
    dut.io.client.write.req.bits.owner.slot.poke(0.U)
    dut.io.client.write.req.bits.addr.poke(0.U)
    dut.io.client.write.req.bits.isLine.poke(false.B)
    dut.io.client.write.req.bits.size.poke(0.U)
    dut.io.client.write.req.bits.data.poke(0.U)
    dut.io.client.write.req.bits.strb.poke(0.U)
    dut.io.client.write.done.ready.poke(false.B)

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

  it should "prioritize a simultaneous write and preserve a stalled line write" in {
    test(new L2Bridge) { dut =>
      init(dut)
      val words = (0 until 16).map(i => BigInt(0x10000000L + i))
      val line = words.zipWithIndex.foldLeft(BigInt(0)) { case (value, (word, i)) =>
        value | (word << (32 * i))
      }

      dut.io.client.read.req.valid.poke(true.B)
      dut.io.client.read.req.bits.owner.source.poke(1.U)
      dut.io.client.read.req.bits.owner.slot.poke(1.U)
      dut.io.client.read.req.bits.addr.poke("h9000007c".U)
      dut.io.client.read.req.bits.isLine.poke(true.B)
      dut.io.client.read.req.bits.size.poke(0.U)

      dut.io.client.write.req.valid.poke(true.B)
      dut.io.client.write.req.bits.owner.source.poke(2.U)
      dut.io.client.write.req.bits.owner.slot.poke(3.U)
      dut.io.client.write.req.bits.addr.poke("h80000077".U)
      dut.io.client.write.req.bits.isLine.poke(true.B)
      dut.io.client.write.req.bits.size.poke(0.U)
      dut.io.client.write.req.bits.data.poke(line.U)
      dut.io.client.write.req.bits.strb.poke(3.U)

      dut.io.client.write.req.ready.expect(true.B)
      dut.io.client.read.req.ready.expect(false.B)
      dut.clock.step()
      dut.io.client.read.req.valid.poke(false.B)
      dut.io.client.write.req.valid.poke(false.B)

      dut.io.axi.aw.data.awvalid.expect(true.B)
      dut.io.axi.aw.data.awid.expect(0.U)
      dut.io.axi.aw.data.awaddr.expect("h80000040".U)
      dut.io.axi.aw.data.awlen.expect(15.U)
      dut.io.axi.aw.data.awsize.expect(2.U)
      dut.io.axi.aw.data.awburst.expect(1.U)
      dut.io.axi.w.data.wvalid.expect(true.B)
      dut.clock.step(2)
      dut.io.axi.aw.data.awvalid.expect(true.B)
      dut.io.axi.aw.data.awaddr.expect("h80000040".U)
      dut.io.axi.aw.data.awlen.expect(15.U)

      // W可以先于AW完成若干beat，AW仍保持稳定有效。
      dut.io.axi.w.wready.poke(true.B)
      for (i <- 0 until 2) {
        dut.io.axi.w.data.wvalid.expect(true.B)
        dut.io.axi.w.data.wdata.expect(words(i).U)
        dut.io.axi.aw.data.awvalid.expect(true.B)
        dut.io.axi.aw.data.awaddr.expect("h80000040".U)
        dut.clock.step()
      }
      dut.io.axi.w.wready.poke(false.B)

      dut.io.axi.aw.awready.poke(true.B)
      dut.clock.step()
      dut.io.axi.aw.awready.poke(false.B)
      dut.io.axi.aw.data.awvalid.expect(false.B)

      for (i <- 2 until 16) {
        if (i == 3) {
          dut.io.axi.w.wready.poke(false.B)
          dut.io.axi.w.data.wvalid.expect(true.B)
          dut.io.axi.w.data.wdata.expect(words(i).U)
          dut.io.axi.w.data.wlast.expect(false.B)
          dut.clock.step(2)
          dut.io.axi.w.data.wvalid.expect(true.B)
          dut.io.axi.w.data.wdata.expect(words(i).U)
        }
        dut.io.axi.w.wready.poke(true.B)
        dut.io.axi.w.data.wvalid.expect(true.B)
        dut.io.axi.w.data.wid.expect(0.U)
        dut.io.axi.w.data.wdata.expect(words(i).U)
        dut.io.axi.w.data.wstrb.expect(15.U)
        dut.io.axi.w.data.wlast.expect((i == 15).B)
        dut.clock.step()
      }
      dut.io.axi.w.wready.poke(false.B)
      dut.io.axi.w.data.wvalid.expect(false.B)

      dut.io.axi.b.data.bvalid.poke(true.B)
      dut.io.client.write.done.ready.poke(false.B)
      dut.io.axi.b.bready.expect(false.B)
      dut.io.client.write.done.valid.expect(true.B)
      dut.io.client.write.done.bits.owner.source.expect(2.U)
      dut.io.client.write.done.bits.owner.slot.expect(3.U)
      dut.clock.step(2)
      dut.io.client.write.done.valid.expect(true.B)
      dut.io.client.write.done.bits.owner.slot.expect(3.U)

      dut.io.client.write.done.ready.poke(true.B)
      dut.io.axi.b.bready.expect(true.B)
      dut.clock.step()
      dut.io.client.write.done.valid.expect(false.B)
      dut.io.client.read.req.ready.expect(true.B)
    }
  }

  it should "hold AR while stalled and forward all line read beats with backpressure" in {
    test(new L2Bridge) { dut =>
      init(dut)
      dut.io.client.read.req.valid.poke(true.B)
      dut.io.client.read.req.bits.owner.source.poke(3.U)
      dut.io.client.read.req.bits.owner.slot.poke(2.U)
      dut.io.client.read.req.bits.addr.poke("h8100007d".U)
      dut.io.client.read.req.bits.isLine.poke(true.B)
      dut.io.client.read.req.bits.size.poke(1.U)
      dut.io.client.read.req.ready.expect(true.B)
      dut.clock.step()
      dut.io.client.read.req.valid.poke(false.B)

      dut.io.axi.ar.data.arvalid.expect(true.B)
      dut.io.axi.ar.data.arid.expect(0.U)
      dut.io.axi.ar.data.araddr.expect("h81000040".U)
      dut.io.axi.ar.data.arlen.expect(15.U)
      dut.io.axi.ar.data.arsize.expect(2.U)
      dut.io.axi.ar.data.arburst.expect(1.U)
      dut.clock.step(2)
      dut.io.axi.ar.data.arvalid.expect(true.B)
      dut.io.axi.ar.data.araddr.expect("h81000040".U)

      dut.io.axi.ar.arready.poke(true.B)
      dut.clock.step()
      dut.io.axi.ar.arready.poke(false.B)

      for (i <- 0 until 16) {
        dut.io.axi.r.data.rvalid.poke(true.B)
        dut.io.axi.r.data.rdata.poke((0x20000000L + i).U)
        dut.io.axi.r.data.rlast.poke((i == 15).B)
        if (i == 5) {
          dut.io.client.read.beat.ready.poke(false.B)
          dut.io.axi.r.rready.expect(false.B)
          dut.io.client.read.beat.valid.expect(true.B)
          dut.io.client.read.beat.bits.owner.source.expect(3.U)
          dut.io.client.read.beat.bits.owner.slot.expect(2.U)
          dut.io.client.read.beat.bits.data.expect((0x20000000L + i).U)
          dut.clock.step(2)
          dut.io.client.read.beat.bits.data.expect((0x20000000L + i).U)
        }
        dut.io.client.read.beat.ready.poke(true.B)
        dut.io.axi.r.rready.expect(true.B)
        dut.io.client.read.beat.valid.expect(true.B)
        dut.io.client.read.beat.bits.owner.source.expect(3.U)
        dut.io.client.read.beat.bits.owner.slot.expect(2.U)
        dut.io.client.read.beat.bits.data.expect((0x20000000L + i).U)
        dut.io.client.read.beat.bits.last.expect((i == 15).B)
        dut.clock.step()
      }
      dut.io.axi.r.data.rvalid.poke(false.B)
      dut.io.client.read.beat.valid.expect(false.B)
      dut.io.client.read.req.ready.expect(true.B)
    }
  }

  it should "use raw address size and one beat for uncached reads and writes" in {
    test(new L2Bridge) { dut =>
      init(dut)
      dut.io.axi.ar.arready.poke(true.B)
      dut.io.client.read.req.valid.poke(true.B)
      dut.io.client.read.req.bits.owner.source.poke(1.U)
      dut.io.client.read.req.bits.owner.slot.poke(3.U)
      dut.io.client.read.req.bits.addr.poke("h80401203".U)
      dut.io.client.read.req.bits.isLine.poke(false.B)
      dut.io.client.read.req.bits.size.poke(0.U)
      dut.clock.step()
      dut.io.client.read.req.valid.poke(false.B)
      dut.io.axi.ar.data.arvalid.expect(true.B)
      dut.io.axi.ar.data.araddr.expect("h80401203".U)
      dut.io.axi.ar.data.arlen.expect(0.U)
      dut.io.axi.ar.data.arsize.expect(0.U)
      dut.io.axi.ar.data.arburst.expect(0.U)
      dut.clock.step()
      dut.io.axi.ar.arready.poke(false.B)

      dut.io.axi.r.data.rvalid.poke(true.B)
      dut.io.axi.r.data.rdata.poke("hdeadbeef".U)
      dut.io.axi.r.data.rlast.poke(true.B)
      dut.io.client.read.beat.ready.poke(true.B)
      dut.io.client.read.beat.valid.expect(true.B)
      dut.io.client.read.beat.bits.data.expect("hdeadbeef".U)
      dut.io.client.read.beat.bits.last.expect(true.B)
      dut.io.client.read.beat.bits.owner.source.expect(1.U)
      dut.io.client.read.beat.bits.owner.slot.expect(3.U)
      dut.clock.step()
      dut.io.axi.r.data.rvalid.poke(false.B)

      dut.io.client.write.req.valid.poke(true.B)
      dut.io.client.write.req.bits.owner.source.poke(2.U)
      dut.io.client.write.req.bits.owner.slot.poke(1.U)
      dut.io.client.write.req.bits.addr.poke("h80405679".U)
      dut.io.client.write.req.bits.isLine.poke(false.B)
      dut.io.client.write.req.bits.size.poke(1.U)
      dut.io.client.write.req.bits.data.poke(BigInt("1122334455667789abcdef", 16).U)
      dut.io.client.write.req.bits.strb.poke(5.U)
      dut.clock.step()
      dut.io.client.write.req.valid.poke(false.B)

      dut.io.axi.aw.data.awvalid.expect(true.B)
      dut.io.axi.aw.data.awaddr.expect("h80405679".U)
      dut.io.axi.aw.data.awlen.expect(0.U)
      dut.io.axi.aw.data.awsize.expect(1.U)
      dut.io.axi.aw.data.awburst.expect(0.U)
      dut.io.axi.aw.awready.poke(true.B)
      dut.clock.step()
      dut.io.axi.aw.awready.poke(false.B)

      dut.io.axi.w.data.wvalid.expect(true.B)
      dut.io.axi.w.data.wdata.expect("h89abcdef".U)
      dut.io.axi.w.data.wstrb.expect(5.U)
      dut.io.axi.w.data.wlast.expect(true.B)
      dut.io.axi.w.wready.poke(true.B)
      dut.clock.step()
      dut.io.axi.w.wready.poke(false.B)

      dut.io.axi.b.data.bvalid.poke(true.B)
      dut.io.client.write.done.ready.poke(true.B)
      dut.io.client.write.done.valid.expect(true.B)
      dut.io.client.write.done.bits.owner.source.expect(2.U)
      dut.io.client.write.done.bits.owner.slot.expect(1.U)
      dut.io.axi.b.bready.expect(true.B)
      dut.clock.step()
    }
  }
}
