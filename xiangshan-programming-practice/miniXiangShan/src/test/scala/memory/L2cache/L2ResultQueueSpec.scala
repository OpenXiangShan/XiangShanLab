package minixiangshan.mem.L2cache

import chisel3._
import chiseltest._
import minixiangshan.config._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class L2ResultQueueSpec extends AnyFlatSpec with ChiselScalatestTester with Matchers {
  behavior of "L2ResultQueue"

  private implicit val p: Parameters = new Parameters(Map(
    DebugConfigKeys.EnableDifftest -> true,
    CoreConfigKeys.L2Ways -> 4,
    CoreConfigKeys.L2Prefetch -> L2PrefetchMode.Disabled))

  private def resetDut(dut: L2ResultQueue): Unit = {
    dut.io.enq.valid.poke(false.B)
    dut.io.deq.ready.poke(false.B)
    dut.reset.poke(true.B)
    dut.clock.step(2)
    dut.reset.poke(false.B)
  }

  private def driveResult(dut: L2ResultQueue, addr: BigInt): Unit = {
    dut.io.enq.bits.token.source.poke(false.B)
    dut.io.enq.bits.token.id.poke(0.U)
    dut.io.enq.bits.token.addr.poke(addr.U)
    dut.io.enq.bits.token.isStb.poke(false.B)
    dut.io.enq.bits.token.isUncacheProbe.poke(false.B)
    dut.io.enq.bits.token.isPrefetch.poke(false.B)
    dut.io.enq.bits.token.prefetchEpoch.poke(0.U)
    dut.io.enq.bits.token.cancelled.poke(false.B)
    dut.io.enq.bits.token.stbSlot.poke(0.U)
    dut.io.enq.bits.token.busySlot.poke(0.U)
    dut.io.enq.bits.hit.poke(true.B)
    dut.io.enq.bits.way.poke(0.U)
    dut.io.enq.bits.oldValid.poke(true.B)
    dut.io.enq.bits.oldDirty.poke(false.B)
    dut.io.enq.bits.oldTag.poke(1.U)
    dut.io.enq.bits.oldData.poke(addr.U)
    dut.io.enq.bits.stbMatch.poke(false.B)
    dut.io.enq.bits.stbForwarded.poke(false.B)
    dut.io.enq.bits.mshrSetConflict.poke(false.B)
    dut.io.enq.bits.ebBlockConflict.poke(false.B)
  }

  it should "register a queued result before exposing it to L2 control" in {
    test(new L2ResultQueue(4)) { dut =>
      resetDut(dut)
      driveResult(dut, 0x80001000L)
      dut.io.enq.valid.poke(true.B)
      dut.io.enq.ready.expect(true.B)
      dut.io.deq.valid.expect(false.B)
      dut.clock.step()

      dut.io.enq.valid.poke(false.B)
      dut.io.deq.valid.expect(false.B)
      dut.io.count.expect(1.U)
      dut.clock.step()

      dut.io.deq.valid.expect(true.B)
      dut.io.deq.bits.token.addr.expect(0x80001000L.U)
      dut.io.count.expect(1.U)
    }
  }

  it should "hold a blocked head and then drain one registered result per cycle" in {
    test(new L2ResultQueue(4)) { dut =>
      resetDut(dut)
      dut.io.deq.ready.poke(false.B)

      val addresses = Seq(0x80002000L, 0x80002040L, 0x80002080L)
      addresses.foreach { addr =>
        driveResult(dut, addr)
        dut.io.enq.valid.poke(true.B)
        dut.io.enq.ready.expect(true.B)
        dut.clock.step()
      }
      dut.io.enq.valid.poke(false.B)

      dut.io.deq.valid.expect(true.B)
      dut.io.deq.bits.token.addr.expect(addresses.head.U)
      dut.io.count.expect(3.U)
      dut.clock.step(2)
      dut.io.deq.bits.token.addr.expect(addresses.head.U)
      dut.io.count.expect(3.U)

      dut.io.deq.ready.poke(true.B)
      dut.clock.step()
      dut.io.deq.valid.expect(true.B)
      dut.io.deq.bits.token.addr.expect(addresses(1).U)
      dut.io.count.expect(2.U)
      dut.clock.step()
      dut.io.deq.valid.expect(true.B)
      dut.io.deq.bits.token.addr.expect(addresses(2).U)
      dut.io.count.expect(1.U)
      dut.clock.step()
      dut.io.deq.valid.expect(false.B)
      dut.io.count.expect(0.U)
    }
  }
}
