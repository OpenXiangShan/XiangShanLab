package noc

import chisel3._
import chisel3.simulator.scalatest.ChiselSim
import chisel3.util.DecoupledIO
import circt.stage.ChiselStage
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

class RingStopSpec extends AnyFreeSpec with Matchers with ChiselSim {
  private def initialize(dut: RingStop[UInt]): Unit = {
    dut.io.ringIn.valid.poke(false.B)
    dut.io.ringIn.bits.dst.poke(0.U)
    dut.io.ringIn.bits.payload.poke(0.U)
    dut.io.local.inject.valid.poke(false.B)
    dut.io.local.inject.bits.dst.poke(0.U)
    dut.io.local.inject.bits.payload.poke(0.U)
    dut.io.ringOut.ready.poke(false.B)
    dut.io.creditReturned.poke(false.B)
    dut.io.local.eject.ready.poke(false.B)
  }

  private def offer(port: DecoupledIO[RingFlit[UInt]], dst: Int, payload: Int): Unit = {
    port.bits.dst.poke(dst.U)
    port.bits.payload.poke(payload.U)
    port.valid.poke(true.B)
  }

  private def expectFlit(port: DecoupledIO[RingFlit[UInt]], dst: Int, payload: Int): Unit = {
    port.valid.expect(true.B)
    port.bits.dst.expect(dst.U)
    port.bits.payload.expect(payload.U)
  }

  private def expectEmpty(dut: RingStop[UInt]): Unit = {
    dut.io.ringOut.valid.expect(false.B)
    dut.io.local.eject.valid.expect(false.B)
  }

  "nodeId must refer to an existing ring position" in {
    for (nodeId <- Seq(-1, 4)) {
      an[IllegalArgumentException] should be thrownBy {
        ChiselStage.emitCHIRRTL(new RingStop(RingParams(), nodeId, UInt(8.W)))
      }
    }
  }

  "ring input for this node ejects even when the downstream link is blocked" in {
    simulate(new RingStop(RingParams(), nodeId = 1, UInt(8.W))) { dut =>
      initialize(dut)
      expectEmpty(dut)
      offer(dut.io.ringIn, dst = 1, payload = 41)
      dut.io.ringIn.ready.expect(true.B)
      dut.clock.step()
      dut.io.ringIn.valid.poke(false.B)

      expectFlit(dut.io.local.eject, dst = 1, payload = 41)
      dut.io.ringOut.valid.expect(false.B)
      dut.io.local.eject.ready.poke(true.B)
      dut.clock.step()
      expectEmpty(dut)
    }
  }

  "ring input for another node forwards even when local ejection is blocked" in {
    simulate(new RingStop(RingParams(), nodeId = 1, UInt(8.W))) { dut =>
      initialize(dut)
      offer(dut.io.ringIn, dst = 3, payload = 42)
      dut.io.ringIn.ready.expect(true.B)
      dut.clock.step()
      dut.io.ringIn.valid.poke(false.B)

      expectFlit(dut.io.ringOut, dst = 3, payload = 42)
      dut.io.local.eject.valid.expect(false.B)
      dut.io.ringOut.ready.poke(true.B)
      dut.clock.step()
      expectEmpty(dut)
    }
  }

  "local injection enters ring output directly, including a self-addressed flit" in {
    simulate(new RingStop(RingParams(), nodeId = 1, UInt(8.W))) { dut =>
      initialize(dut)
      dut.io.ringOut.ready.poke(true.B)
      dut.io.local.eject.ready.poke(true.B)

      for ((dst, payload) <- Seq(2 -> 51, 1 -> 52)) {
        offer(dut.io.local.inject, dst, payload)
        dut.io.local.inject.ready.expect(true.B)
        expectFlit(dut.io.ringOut, dst, payload)
        dut.io.local.eject.valid.expect(false.B)
        dut.clock.step()
        dut.io.local.inject.valid.poke(false.B)
        expectEmpty(dut)

        if (dst == 1) {
          // Emulate the flit returning after traversing the ring.
          offer(dut.io.ringIn, dst, payload)
          dut.io.ringIn.ready.expect(true.B)
          dut.clock.step()
          dut.io.ringIn.valid.poke(false.B)
          expectFlit(dut.io.local.eject, dst, payload)
          dut.io.ringOut.valid.expect(false.B)
          dut.clock.step()
          expectEmpty(dut)
        }
      }
    }
  }

  "a buffered forward overtakes local injection that arrived earlier under backpressure" in {
    simulate(new RingStop(RingParams(), nodeId = 1, UInt(8.W))) { dut =>
      initialize(dut)
      offer(dut.io.local.inject, dst = 3, payload = 62)
      expectFlit(dut.io.ringOut, dst = 3, payload = 62)
      dut.io.local.inject.ready.expect(false.B)
      dut.clock.step()

      offer(dut.io.ringIn, dst = 2, payload = 61)
      dut.io.ringIn.ready.expect(true.B)
      dut.io.local.inject.ready.expect(false.B)
      dut.clock.step()
      dut.io.ringIn.valid.poke(false.B)

      expectFlit(dut.io.ringOut, dst = 2, payload = 61)
      for (_ <- 0 until 3) {
        dut.io.local.inject.ready.expect(false.B)
        expectFlit(dut.io.ringOut, dst = 2, payload = 61)
        dut.clock.step()
      }

      dut.io.ringOut.ready.poke(true.B)
      dut.io.local.inject.ready.expect(false.B)
      dut.clock.step()
      expectFlit(dut.io.ringOut, dst = 3, payload = 62)
      dut.io.local.inject.ready.expect(true.B)
      dut.clock.step()
      dut.io.local.inject.valid.poke(false.B)
      expectEmpty(dut)
      dut.clock.step(3)
      expectEmpty(dut)
    }
  }

  "ring reception and local injection can complete on the same edge" in {
    simulate(new RingStop(RingParams(), nodeId = 1, UInt(8.W))) { dut =>
      initialize(dut)
      dut.io.ringOut.ready.poke(true.B)
      offer(dut.io.ringIn, dst = 2, payload = 63)
      offer(dut.io.local.inject, dst = 3, payload = 64)
      dut.io.ringIn.ready.expect(true.B)
      dut.io.local.inject.ready.expect(true.B)
      expectFlit(dut.io.ringOut, dst = 3, payload = 64)
      dut.clock.step()
      dut.io.ringIn.valid.poke(false.B)
      dut.io.local.inject.valid.poke(false.B)

      expectFlit(dut.io.ringOut, dst = 2, payload = 63)
      dut.clock.step()
      expectEmpty(dut)
    }
  }

  "local ejection and injection can complete on the same edge" in {
    simulate(new RingStop(RingParams(), nodeId = 1, UInt(8.W))) { dut =>
      initialize(dut)
      offer(dut.io.ringIn, dst = 1, payload = 65)
      dut.clock.step()
      dut.io.ringIn.valid.poke(false.B)

      dut.io.local.eject.ready.poke(true.B)
      dut.io.ringOut.ready.poke(true.B)
      offer(dut.io.local.inject, dst = 2, payload = 66)
      expectFlit(dut.io.local.eject, dst = 1, payload = 65)
      expectFlit(dut.io.ringOut, dst = 2, payload = 66)
      dut.io.local.inject.ready.expect(true.B)
      dut.clock.step()
      dut.io.local.inject.valid.poke(false.B)
      expectEmpty(dut)
    }
  }

  "a three-entry ring buffer blocks ring input while injection bypasses stalled ejection" in {
    simulate(new RingStop(RingParams(), nodeId = 1, UInt(8.W))) { dut =>
      initialize(dut)
      dut.io.ringOut.ready.poke(true.B)
      offer(dut.io.ringIn, dst = 1, payload = 71)
      dut.io.ringIn.ready.expect(true.B)
      dut.clock.step()
      offer(dut.io.ringIn, dst = 2, payload = 72)
      dut.io.ringIn.ready.expect(true.B)
      dut.clock.step()
      offer(dut.io.ringIn, dst = 3, payload = 73)
      dut.io.ringIn.ready.expect(true.B)
      dut.clock.step()
      offer(dut.io.ringIn, dst = 0, payload = 74)

      // The full ring buffer does not store or block local injection.
      dut.io.ringIn.ready.expect(false.B)
      offer(dut.io.local.inject, dst = 0, payload = 75)
      dut.io.local.inject.ready.expect(true.B)
      expectFlit(dut.io.ringOut, dst = 0, payload = 75)
      expectFlit(dut.io.local.eject, dst = 1, payload = 71)
      // This isolated downstream model frees every accepted flit on the same edge.
      dut.io.creditReturned.poke(true.B)
      dut.clock.step()
      dut.io.creditReturned.poke(false.B)
      dut.io.local.inject.valid.poke(false.B)

      for (_ <- 0 until 5) {
        dut.io.ringIn.ready.expect(false.B)
        dut.io.local.inject.ready.expect(true.B)
        expectFlit(dut.io.local.eject, dst = 1, payload = 71)
        dut.io.ringOut.valid.expect(false.B)
        dut.clock.step()
      }

      dut.io.local.eject.ready.poke(true.B)
      dut.clock.step()
      expectFlit(dut.io.ringOut, dst = 2, payload = 72)
      dut.io.ringIn.ready.expect(true.B)
      dut.io.creditReturned.poke(true.B)
      dut.clock.step()
      dut.io.ringIn.valid.poke(false.B)
      expectFlit(dut.io.ringOut, dst = 3, payload = 73)
      dut.clock.step()
      expectFlit(dut.io.ringOut, dst = 0, payload = 74)
      dut.clock.step()
      dut.io.creditReturned.poke(false.B)
      expectEmpty(dut)
    }
  }

  "local injection stays pending outside the ring buffer under downstream backpressure" in {
    simulate(new RingStop(RingParams(), nodeId = 1, UInt(8.W))) { dut =>
      initialize(dut)
      dut.io.local.eject.ready.poke(true.B)
      offer(dut.io.local.inject, dst = 0, payload = 81)

      for (_ <- 0 until 5) {
        dut.io.local.inject.ready.expect(false.B)
        dut.io.ringIn.ready.expect(true.B)
        expectFlit(dut.io.ringOut, dst = 0, payload = 81)
        dut.io.local.eject.valid.expect(false.B)
        dut.clock.step()
      }
      dut.io.ringOut.ready.poke(true.B)
      dut.io.local.inject.ready.expect(true.B)
      dut.clock.step()
      dut.io.local.inject.valid.poke(false.B)
      expectEmpty(dut)
      dut.clock.step(3)
      expectEmpty(dut)
    }
  }

  "reset clears buffered flits" in {
    simulate(new RingStop(RingParams(), nodeId = 1, UInt(8.W))) { dut =>
      initialize(dut)
      offer(dut.io.ringIn, dst = 2, payload = 91)
      dut.io.ringIn.ready.expect(true.B)
      dut.clock.step()
      dut.io.ringIn.valid.poke(false.B)
      expectFlit(dut.io.ringOut, dst = 2, payload = 91)

      dut.reset.poke(true.B)
      dut.clock.step()
      dut.reset.poke(false.B)
      expectEmpty(dut)
      dut.io.ringIn.ready.expect(true.B)
      dut.io.local.inject.ready.expect(false.B)
      dut.io.ringOut.ready.poke(true.B)
      dut.io.local.inject.ready.expect(true.B)
    }
  }

  "structured payloads pass unchanged through both output sources in a non-power-of-two ring" in {
    simulate(new RingStop(RingParams(3), nodeId = 2, new Bundle {
      val tag = UInt(3.W)
      val words = Vec(2, UInt(8.W))
    })) { dut =>
      dut.io.creditReturned.poke(false.B)
      dut.io.ringIn.bits.dst.poke(0.U)
      dut.io.ringIn.bits.payload.tag.poke(5.U)
      dut.io.ringIn.bits.payload.words(0).poke(165.U)
      dut.io.ringIn.bits.payload.words(1).poke(90.U)
      dut.io.local.inject.bits.dst.poke(0.U)
      dut.io.local.inject.bits.payload.tag.poke(5.U)
      dut.io.local.inject.bits.payload.words(0).poke(165.U)
      dut.io.local.inject.bits.payload.words(1).poke(90.U)
      dut.io.local.eject.ready.poke(false.B)

      for (fromRing <- Seq(true, false)) {
        dut.io.ringOut.ready.poke(false.B)
        dut.io.ringIn.valid.poke(fromRing.B)
        dut.io.local.inject.valid.poke((!fromRing).B)
        if (fromRing) {
          dut.io.ringIn.ready.expect(true.B)
          dut.clock.step()
          dut.io.ringIn.valid.poke(false.B)
        }

        for (_ <- 0 until 3) {
          dut.io.ringOut.valid.expect(true.B)
          dut.io.ringOut.bits.dst.expect(0.U)
          dut.io.ringOut.bits.payload.tag.expect(5.U)
          dut.io.ringOut.bits.payload.words(0).expect(165.U)
          dut.io.ringOut.bits.payload.words(1).expect(90.U)
          dut.io.local.eject.valid.expect(false.B)
          dut.clock.step()
        }
        dut.io.ringOut.ready.poke(true.B)
        dut.clock.step()
        dut.io.local.inject.valid.poke(false.B)
        dut.io.ringOut.valid.expect(false.B)
      }
    }
  }
}
