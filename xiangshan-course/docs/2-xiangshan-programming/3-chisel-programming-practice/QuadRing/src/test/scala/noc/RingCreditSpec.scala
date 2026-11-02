package noc

import chisel3._
import chisel3.simulator.scalatest.ChiselSim
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

class RingCreditSpec extends AnyFreeSpec with Matchers with ChiselSim {
  private def initialize(dut: RingStop[UInt]): Unit = {
    dut.io.ringIn.valid.poke(false.B)
    dut.io.ringIn.bits.dst.poke(0.U)
    dut.io.ringIn.bits.payload.poke(0.U)
    dut.io.local.inject.valid.poke(false.B)
    dut.io.local.inject.bits.dst.poke(2.U)
    dut.io.local.inject.bits.payload.poke(0.U)
    dut.io.ringOut.ready.poke(true.B)
    dut.io.local.eject.ready.poke(false.B)
    dut.io.creditReturned.poke(false.B)
  }

  private def inject(dut: RingStop[UInt], payload: Int): Unit = {
    dut.io.local.inject.bits.payload.poke(payload.U)
    dut.io.local.inject.valid.poke(true.B)
    dut.io.local.inject.ready.expect(true.B)
    dut.io.ringOut.valid.expect(true.B)
    dut.io.ringOut.bits.payload.expect(payload.U)
    dut.io.creditFreed.expect(false.B)
    dut.clock.step()
    dut.io.local.inject.valid.poke(false.B)
  }

  private def receive(dut: RingStop[UInt], dst: Int, payload: Int): Unit = {
    dut.io.ringIn.bits.dst.poke(dst.U)
    dut.io.ringIn.bits.payload.poke(payload.U)
    dut.io.ringIn.valid.poke(true.B)
    dut.io.ringIn.ready.expect(true.B)
    dut.clock.step()
    dut.io.ringIn.valid.poke(false.B)
  }

  for (depth <- Seq(2, 3, 5)) {
    s"depth $depth reserves the last credit for forwarding and waits for returned credits" in {
      simulate(new RingStop(RingParams(bufferDepth = depth), nodeId = 1, UInt(8.W))) { dut =>
        initialize(dut)
        for (payload <- 1 until depth) {
          inject(dut, payload)
        }

        // One downstream slot remains: injection must wait, but forwarding can use it.
        dut.io.local.inject.bits.payload.poke(100.U)
        dut.io.local.inject.valid.poke(true.B)
        for (_ <- 0 until 3) {
          dut.io.local.inject.ready.expect(false.B)
          dut.io.ringOut.valid.expect(false.B)
          dut.clock.step()
        }
        receive(dut, dst = 3, payload = 101)
        dut.io.local.inject.ready.expect(false.B)
        dut.io.ringOut.valid.expect(true.B)
        dut.io.ringOut.bits.payload.expect(101.U)
        dut.io.creditFreed.expect(true.B)
        dut.clock.step()

        // No downstream slots remain, so a second forward must also wait.
        receive(dut, dst = 0, payload = 102)
        for (_ <- 0 until 3) {
          dut.io.ringOut.valid.expect(false.B)
          dut.io.local.inject.ready.expect(false.B)
          dut.io.creditFreed.expect(false.B)
          dut.clock.step()
        }
        dut.io.creditReturned.poke(true.B)
        dut.io.ringOut.valid.expect(false.B)
        dut.clock.step()
        dut.io.creditReturned.poke(false.B)
        dut.io.ringOut.valid.expect(true.B)
        dut.io.ringOut.bits.payload.expect(102.U)
        dut.io.local.inject.ready.expect(false.B)
        dut.clock.step()

        // Return two slots while the pending injection waits outside the FIFO.
        dut.io.creditReturned.poke(true.B)
        dut.clock.step()
        dut.io.local.inject.ready.expect(false.B)
        dut.io.ringOut.valid.expect(false.B)
        dut.clock.step()
        dut.io.creditReturned.poke(false.B)
        dut.io.local.inject.ready.expect(true.B)
        dut.io.ringOut.valid.expect(true.B)
        dut.io.ringOut.bits.payload.expect(100.U)
        dut.clock.step()
        dut.io.local.inject.valid.poke(false.B)
        dut.io.ringOut.valid.expect(false.B)
      }
    }
  }

  "local ejection frees an upstream credit even when downstream credits are exhausted" in {
    simulate(new RingStop(RingParams(bufferDepth = 2), nodeId = 1, UInt(8.W))) { dut =>
      initialize(dut)
      inject(dut, payload = 10)
      receive(dut, dst = 2, payload = 11)
      dut.io.creditFreed.expect(true.B)
      dut.clock.step()

      receive(dut, dst = 1, payload = 12)
      for (_ <- 0 until 3) {
        dut.io.local.eject.valid.expect(true.B)
        dut.io.local.eject.bits.payload.expect(12.U)
        dut.io.creditFreed.expect(false.B)
        dut.clock.step()
      }
      dut.io.local.eject.ready.poke(true.B)
      dut.io.creditFreed.expect(true.B)
      dut.io.ringOut.valid.expect(false.B)
      dut.clock.step()
      dut.io.creditFreed.expect(false.B)
      dut.io.local.eject.valid.expect(false.B)
    }
  }

  "simultaneous send and credit return preserve the available credits" in {
    simulate(new RingStop(RingParams(bufferDepth = 2), nodeId = 1, UInt(8.W))) { dut =>
      initialize(dut)
      inject(dut, payload = 20)
      // Return the credit for the first transfer before attempting another injection.
      dut.io.creditReturned.poke(true.B)
      dut.clock.step()
      dut.io.creditReturned.poke(false.B)

      // Model a downstream consumer returning one credit for each accepted transfer.
      dut.io.creditReturned.poke(true.B)
      for (payload <- 21 to 28) {
        inject(dut, payload)
      }
      dut.io.creditReturned.poke(false.B)
      inject(dut, payload = 29)
      dut.io.local.inject.ready.expect(false.B)
    }
  }

  "downstream backpressure consumes no credit and returns no upstream credit" in {
    simulate(new RingStop(RingParams(bufferDepth = 2), nodeId = 1, UInt(8.W))) { dut =>
      initialize(dut)
      dut.io.ringOut.ready.poke(false.B)
      receive(dut, dst = 3, payload = 30)
      for (_ <- 0 until 5) {
        dut.io.ringOut.valid.expect(true.B)
        dut.io.ringOut.bits.payload.expect(30.U)
        dut.io.creditFreed.expect(false.B)
        dut.clock.step()
      }
      dut.io.ringOut.ready.poke(true.B)
      dut.io.creditFreed.expect(true.B)
      dut.clock.step()
      dut.io.creditFreed.expect(false.B)
      // Exactly one credit was consumed; the reserved slot still permits a forward.
      receive(dut, dst = 0, payload = 31)
      dut.io.ringOut.valid.expect(true.B)
      dut.io.ringOut.bits.payload.expect(31.U)
      dut.clock.step()
      dut.io.local.inject.ready.expect(false.B)
    }
  }

  "reset restores all downstream credits and clears a blocked forward" in {
    simulate(new RingStop(RingParams(bufferDepth = 2), nodeId = 1, UInt(8.W))) { dut =>
      initialize(dut)
      inject(dut, payload = 40)
      receive(dut, dst = 2, payload = 41)
      dut.clock.step()
      receive(dut, dst = 3, payload = 42)
      dut.io.ringOut.valid.expect(false.B)

      dut.reset.poke(true.B)
      dut.clock.step()
      dut.reset.poke(false.B)
      dut.io.creditFreed.expect(false.B)
      dut.io.ringOut.valid.expect(false.B)
      dut.io.ringIn.ready.expect(true.B)
      inject(dut, payload = 43)
      dut.io.local.inject.ready.expect(false.B)
    }
  }
}
