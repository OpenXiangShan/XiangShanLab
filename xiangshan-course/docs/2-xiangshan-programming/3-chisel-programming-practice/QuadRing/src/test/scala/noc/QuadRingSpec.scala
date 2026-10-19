package noc

import chisel3._
import chisel3.simulator.scalatest.ChiselSim
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import scala.collection.mutable
import scala.util.Random

class QuadRingSpec extends AnyFreeSpec with Matchers with ChiselSim {
  private case class Flit(dst: Int, payload: Int)

  "four different payload types transfer simultaneously without changing their contents" in {
    simulate(new QuadRing(RingParams(3), UInt(8.W), Bool(), Vec(2, UInt(16.W)), new Bundle {
      val tag = UInt(4.W)
      val words = Vec(2, UInt(12.W))
    })) { dut =>
      for (port <- dut.io.channel0) {
        port.inject.valid.poke(false.B)
        port.inject.bits.dst.poke(0.U)
        port.inject.bits.payload.poke(0.U)
        port.eject.ready.poke(true.B)
      }
      for (port <- dut.io.channel1) {
        port.inject.valid.poke(false.B)
        port.inject.bits.dst.poke(0.U)
        port.inject.bits.payload.poke(false.B)
        port.eject.ready.poke(true.B)
      }
      for (port <- dut.io.channel2) {
        port.inject.valid.poke(false.B)
        port.inject.bits.dst.poke(0.U)
        port.inject.bits.payload(0).poke(0.U)
        port.inject.bits.payload(1).poke(0.U)
        port.eject.ready.poke(true.B)
      }
      for (port <- dut.io.channel3) {
        port.inject.valid.poke(false.B)
        port.inject.bits.dst.poke(0.U)
        port.inject.bits.payload.tag.poke(0.U)
        port.inject.bits.payload.words(0).poke(0.U)
        port.inject.bits.payload.words(1).poke(0.U)
        port.eject.ready.poke(true.B)
      }

      // Self-addressed messages must travel all the way around each ring.
      val sources = Seq(dut.io.channel0(2).inject, dut.io.channel1(2).inject,
        dut.io.channel2(2).inject, dut.io.channel3(2).inject)
      for (source <- sources) {
        source.bits.dst.poke(2.U)
        source.valid.poke(true.B)
        source.ready.expect(true.B)
      }
      dut.io.channel0(2).inject.bits.payload.poke(165.U)
      dut.io.channel1(2).inject.bits.payload.poke(true.B)
      dut.io.channel2(2).inject.bits.payload(0).poke(0x1234.U)
      dut.io.channel2(2).inject.bits.payload(1).poke(0xabcd.U)
      dut.io.channel3(2).inject.bits.payload.tag.poke(9.U)
      dut.io.channel3(2).inject.bits.payload.words(0).poke(0xabc.U)
      dut.io.channel3(2).inject.bits.payload.words(1).poke(0xdef.U)
      dut.clock.step()
      sources.foreach(_.valid.poke(false.B))

      val deliveries = Array.fill(4)(0)
      for (cycle <- 0 until 16) {
        val channels = Seq(dut.io.channel0, dut.io.channel1, dut.io.channel2, dut.io.channel3)
        for ((ports, channel) <- channels.zipWithIndex; (port, dst) <- ports.zipWithIndex) {
          if (port.eject.valid.peek().litToBoolean) {
            cycle shouldBe 2
            dst shouldBe 2
            deliveries(channel) += 1
            port.eject.bits.dst.expect(2.U)
          }
        }
        if (dut.io.channel0(2).eject.valid.peek().litToBoolean) {
          dut.io.channel0(2).eject.bits.payload.expect(165.U)
          dut.io.channel1(2).eject.bits.payload.expect(true.B)
          dut.io.channel2(2).eject.bits.payload(0).expect(0x1234.U)
          dut.io.channel2(2).eject.bits.payload(1).expect(0xabcd.U)
          dut.io.channel3(2).eject.bits.payload.tag.expect(9.U)
          dut.io.channel3(2).eject.bits.payload.words(0).expect(0xabc.U)
          dut.io.channel3(2).eject.bits.payload.words(1).expect(0xdef.U)
        }
        dut.clock.step()
      }
      deliveries.toSeq shouldBe Seq(1, 1, 1, 1)
    }
  }

  for (blockedChannel <- 0 until 4) {
    s"blocking channel $blockedChannel preserves traffic on the other three channels" in {
      val params = RingParams()
      simulate(new QuadRing(params, UInt(32.W), UInt(40.W), UInt(48.W), UInt(64.W))) { dut =>
        val channels = Seq(dut.io.channel0, dut.io.channel1, dut.io.channel2, dut.io.channel3)
        val random = new Random(0x5eedL + blockedChannel)
        val offers = Array.fill[Option[Flit]](4, params.nodeCount)(None)
        val pending = Seq.fill(4)(mutable.Map.empty[Int, Int])
        val routes = Seq.fill(4)(mutable.Set.empty[(Int, Int)])
        val delivered = Array.fill(4)(0)
        var nextId = 1
        var cycle = 0
        var blockedInjectionStalled = false

        for (ports <- channels; port <- ports) {
          port.inject.valid.poke(false.B)
          port.inject.bits.dst.poke(0.U)
          port.inject.bits.payload.poke(0.U)
          port.eject.ready.poke(false.B)
        }

        def tick(generate: Boolean, blocked: Boolean): Unit = {
          val ready = Array.tabulate(4, params.nodeCount) { (channel, _) =>
            !(blocked && channel == blockedChannel) && (!blocked || cycle % 8 == 0 || random.nextBoolean())
          }
          for ((ports, channel) <- channels.zipWithIndex; (port, src) <- ports.zipWithIndex) {
            if (generate && offers(channel)(src).isEmpty) {
              offers(channel)(src) = Some(Flit(random.nextInt(params.nodeCount), nextId))
              nextId += 1
            }
            val offer = offers(channel)(src)
            port.inject.valid.poke(offer.nonEmpty.B)
            port.inject.bits.dst.poke(offer.fold(0)(_.dst).U)
            port.inject.bits.payload.poke(offer.fold(0)(_.payload).U)
            port.eject.ready.poke(ready(channel)(src).B)
          }

          for ((ports, channel) <- channels.zipWithIndex; (port, src) <- ports.zipWithIndex) {
            offers(channel)(src).foreach { flit =>
              if (port.inject.ready.peek().litToBoolean) {
                pending(channel).contains(flit.payload) shouldBe false
                pending(channel)(flit.payload) = flit.dst
                routes(channel) += ((src, flit.dst))
                offers(channel)(src) = None
              } else if (channel == blockedChannel) {
                blockedInjectionStalled = true
              }
            }
          }
          for ((ports, channel) <- channels.zipWithIndex; (port, dst) <- ports.zipWithIndex) {
            if (port.eject.valid.peek().litToBoolean) {
              val payload = port.eject.bits.payload.peek().litValue.toInt
              port.eject.bits.dst.expect(dst.U)
              withClue(s"cycle $cycle channel $channel: unknown, duplicate, or corrupt payload: ") {
                pending(channel).get(payload) shouldBe Some(dst)
              }
              if (ready(channel)(dst)) {
                pending(channel).remove(payload)
                delivered(channel) += 1
              }
            }
          }
          for (scoreboard <- pending) {
            scoreboard.size should be < (params.nodeCount * params.bufferDepth)
          }
          dut.clock.step()
          cycle += 1
        }

        // Keep the selected ring fully blocked while the others continuously transfer.
        for (_ <- 0 until 10) {
          val before = delivered.clone()
          for (_ <- 0 until 100) {
            tick(generate = true, blocked = true)
          }
          for (channel <- 0 until 4 if channel != blockedChannel) {
            withClue(s"cycle $cycle: channel $channel stopped while channel $blockedChannel was blocked: ") {
              delivered(channel) should be > before(channel)
            }
          }
        }
        delivered(blockedChannel) shouldBe 0
        pending(blockedChannel) should not be empty
        blockedInjectionStalled shouldBe true
        for (channel <- 0 until 4 if channel != blockedChannel) {
          delivered(channel) should be > 0
          routes(channel).size shouldBe params.nodeCount * params.nodeCount
        }

        // Stop creating messages, finish source offers, and drain all four rings.
        var drainCycles = 0
        while ((pending.exists(_.nonEmpty) || offers.exists(_.exists(_.nonEmpty))) && drainCycles < 256) {
          tick(generate = false, blocked = false)
          drainCycles += 1
        }
        for (scoreboard <- pending) {
          scoreboard shouldBe empty
        }
        offers.forall(_.forall(_.isEmpty)) shouldBe true
        delivered(blockedChannel) should be > 0
        for (_ <- 0 until params.nodeCount * params.bufferDepth * 2) {
          tick(generate = false, blocked = false)
        }
      }
    }
  }
}
