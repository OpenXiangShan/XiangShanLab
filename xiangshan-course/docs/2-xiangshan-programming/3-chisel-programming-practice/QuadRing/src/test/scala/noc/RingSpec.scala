package noc

import chisel3._
import chisel3.simulator.scalatest.ChiselSim
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

class RingSpec extends AnyFreeSpec with Matchers with ChiselSim {
  private def initialize(dut: Ring[UInt]): Unit = {
    for (port <- dut.io.nodes) {
      port.inject.valid.poke(false.B)
      port.inject.bits.dst.poke(0.U)
      port.inject.bits.payload.poke(0.U)
      port.eject.ready.poke(true.B)
    }
  }

  private def expectQuiet(dut: Ring[UInt]): Unit = {
    for (port <- dut.io.nodes) {
      port.eject.valid.expect(false.B)
    }
  }

  private def inject(dut: Ring[UInt], src: Int, dst: Int, payload: Int): Unit = {
    val port = dut.io.nodes(src).inject
    port.bits.dst.poke(dst.U)
    port.bits.payload.poke(payload.U)
    port.valid.poke(true.B)

    var accepted = false
    var cycles = 0
    while (!accepted && cycles < 20) {
      expectQuiet(dut)
      accepted = port.ready.peek().litToBoolean
      dut.clock.step()
      cycles += 1
    }
    withClue(s"injection $src -> $dst timed out: ") {
      accepted shouldBe true
    }
    port.valid.poke(false.B)
  }

  private def expectDelivery(dut: Ring[UInt], dst: Int, payload: Int): Unit = {
    var received = false
    // Keep observing after delivery to detect a duplicate or a flit circulating again.
    for (_ <- 0 until dut.io.nodes.length * 3 + 5) {
      for ((port, nodeId) <- dut.io.nodes.zipWithIndex) {
        if (port.eject.valid.peek().litToBoolean) {
          nodeId shouldBe dst
          received shouldBe false
          port.eject.bits.dst.expect(dst.U)
          port.eject.bits.payload.expect(payload.U)
          received = true
        }
      }
      dut.clock.step()
    }
    withClue(s"delivery to $dst timed out: ") {
      received shouldBe true
    }
  }

  for (nodeCount <- Seq(2, 3, 4)) {
    s"every source reaches every destination exactly once in a $nodeCount-node ring" in {
      simulate(new Ring(RingParams(nodeCount), UInt(16.W))) { dut =>
        initialize(dut)
        for (src <- 0 until nodeCount; dst <- 0 until nodeCount) {
          val payload = src * nodeCount + dst + 1
          inject(dut, src, dst, payload)
          expectDelivery(dut, dst, payload)
        }
      }
    }
  }

  "the ring advances through increasing indices and self-addressed flits make a full loop" in {
    simulate(new Ring(RingParams(), UInt(16.W))) { dut =>
      initialize(dut)
      for ((src, dst, hops) <- Seq((0, 1, 1), (3, 0, 1), (0, 3, 3), (2, 2, 4))) {
        val payload = 50 + src * 4 + dst
        inject(dut, src, dst, payload)
        for (_ <- 1 until hops) {
          expectQuiet(dut)
          dut.clock.step()
        }
        dut.io.nodes(dst).eject.valid.expect(true.B)
        expectDelivery(dut, dst, payload)
      }
    }
  }

  "destination backpressure holds a wrapped flit until it can be consumed" in {
    simulate(new Ring(RingParams(), UInt(16.W))) { dut =>
      initialize(dut)
      val destination = dut.io.nodes(0).eject
      destination.ready.poke(false.B)
      inject(dut, src = 2, dst = 0, payload = 101)

      var cycles = 0
      while (!destination.valid.peek().litToBoolean && cycles < 20) {
        expectQuiet(dut)
        dut.clock.step()
        cycles += 1
      }
      destination.valid.expect(true.B)

      for (_ <- 0 until 10) {
        destination.valid.expect(true.B)
        destination.bits.dst.expect(0.U)
        destination.bits.payload.expect(101.U)
        for (nodeId <- 1 until 4) {
          dut.io.nodes(nodeId).eject.valid.expect(false.B)
        }
        dut.clock.step()
      }

      destination.ready.poke(true.B)
      expectDelivery(dut, dst = 0, payload = 101)
    }
  }

  "reset removes a flit in flight and the ring accepts a new transfer" in {
    simulate(new Ring(RingParams(), UInt(16.W))) { dut =>
      initialize(dut)
      inject(dut, src = 0, dst = 0, payload = 201)
      expectQuiet(dut)

      dut.reset.poke(true.B)
      dut.clock.step()
      dut.reset.poke(false.B)
      for (_ <- 0 until 16) {
        expectQuiet(dut)
        dut.clock.step()
      }

      inject(dut, src = 3, dst = 1, payload = 202)
      expectDelivery(dut, dst = 1, payload = 202)
    }
  }

  "a structured payload returns to its source after traversing the ring" in {
    simulate(new Ring(RingParams(3), new Bundle {
      val tag = UInt(3.W)
      val words = Vec(2, UInt(8.W))
    })) { dut =>
      for (port <- dut.io.nodes) {
        port.inject.valid.poke(false.B)
        port.inject.bits.dst.poke(0.U)
        port.inject.bits.payload.tag.poke(0.U)
        port.inject.bits.payload.words(0).poke(0.U)
        port.inject.bits.payload.words(1).poke(0.U)
        port.eject.ready.poke(true.B)
      }

      val source = dut.io.nodes(2).inject
      source.bits.dst.poke(2.U)
      source.bits.payload.tag.poke(5.U)
      source.bits.payload.words(0).poke(165.U)
      source.bits.payload.words(1).poke(90.U)
      source.valid.poke(true.B)
      source.ready.expect(true.B)
      dut.clock.step()
      source.valid.poke(false.B)

      var received = false
      for (_ <- 0 until 14) {
        for ((port, nodeId) <- dut.io.nodes.zipWithIndex) {
          if (port.eject.valid.peek().litToBoolean) {
            nodeId shouldBe 2
            received shouldBe false
            port.eject.bits.dst.expect(2.U)
            port.eject.bits.payload.tag.expect(5.U)
            port.eject.bits.payload.words(0).expect(165.U)
            port.eject.bits.payload.words(1).expect(90.U)
            received = true
          }
        }
        dut.clock.step()
      }
      received shouldBe true
    }
  }
}
