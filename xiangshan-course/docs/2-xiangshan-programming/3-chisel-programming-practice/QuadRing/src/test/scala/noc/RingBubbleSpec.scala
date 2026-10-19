package noc

import chisel3._
import chisel3.simulator.scalatest.ChiselSim
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import scala.collection.mutable

class RingBubbleSpec extends AnyFreeSpec with Matchers with ChiselSim {
  for ((nodeCount, depth) <- Seq((2, 2), (3, 3), (4, 3))) {
    s"a $nodeCount-node depth-$depth ring stops injection under blockage and drains after release" in {
      simulate(new Ring(RingParams(nodeCount, depth), UInt(16.W))) { dut =>
        val pending = mutable.Map.empty[Int, Int] // payload -> expected destination
        val sequence = Array.fill(nodeCount)(0)
        for (port <- dut.io.nodes) {
          port.inject.valid.poke(false.B)
          port.inject.bits.dst.poke(0.U)
          port.inject.bits.payload.poke(0.U)
          port.eject.ready.poke(false.B)
        }

        // Repeat blockage and recovery, varying the destinations to move the bubbles.
        for (round <- 0 until 3) {
          var accepted = 0
          for (_ <- 0 until 80) {
            for ((port, src) <- dut.io.nodes.zipWithIndex) {
              val dst = (src + round) % nodeCount
              val payload = src * 1024 + sequence(src)
              port.inject.valid.poke(true.B)
              port.inject.bits.dst.poke(dst.U)
              port.inject.bits.payload.poke(payload.U)
              if (port.inject.ready.peek().litToBoolean) {
                pending.contains(payload) shouldBe false
                pending(payload) = dst
                sequence(src) += 1
                accepted += 1
              }
            }
            // Nothing can eject; every accepted flit remains in a ring buffer.
            pending.size should be < (nodeCount * depth)
            dut.clock.step()
          }
          accepted should be > 0
          for (port <- dut.io.nodes) {
            port.inject.ready.expect(false.B)
            port.inject.valid.poke(false.B)
            port.eject.ready.poke(true.B)
          }

          // Observe every endpoint until the ring drains; a missing flit times out.
          var cycles = 0
          while (pending.nonEmpty && cycles < nodeCount * depth * nodeCount + 10) {
            for ((port, dst) <- dut.io.nodes.zipWithIndex) {
              if (port.eject.valid.peek().litToBoolean) {
                val payload = port.eject.bits.payload.peek().litValue.toInt
                withClue(s"unexpected or duplicate payload $payload: ") {
                  pending.remove(payload) shouldBe Some(dst)
                }
                port.eject.bits.dst.expect(dst.U)
              }
            }
            dut.clock.step()
            cycles += 1
          }
          withClue(s"round $round did not drain: ") {
            pending shouldBe empty
          }
          for (_ <- 0 until nodeCount * 2) {
            for (port <- dut.io.nodes) {
              port.eject.valid.expect(false.B)
            }
            dut.clock.step()
          }
          for (port <- dut.io.nodes) {
            port.eject.ready.poke(false.B)
          }
        }
      }
    }
  }
}
