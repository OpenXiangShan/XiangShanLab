package noc

import chisel3._
import chisel3.simulator.scalatest.ChiselSim
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import scala.collection.mutable
import scala.util.Random

class RingRandomSpec extends AnyFreeSpec with Matchers with ChiselSim {
  private case class Flit(dst: Int, payload: BigInt)
  private val longRun = sys.env.get("QUADRING_LONG_TESTS") match {
    case None | Some("0") => false
    case Some("1") => true
    case Some(value) => throw new IllegalArgumentException(s"QUADRING_LONG_TESTS must be 0 or 1, got '$value'")
  }
  private val runCycles = if (longRun) 100000 else 5000
  private val cases = Seq(
    (4, 3, 1L),
    (4, 3, 0x5eedL),
    (4, 3, 0xc0ffeeL),
    (2, 2, 7L),
    (3, 5, 42L)
  )

  for ((nodeCount, depth, seed) <- cases) {
    s"seed $seed runs $runCycles cycles in a $nodeCount-node depth-$depth ring without loss or deadlock" in {
      simulate(new Ring(RingParams(nodeCount, depth), UInt(64.W))) { dut =>
        val random = new Random(seed)
        val offers = Array.fill[Option[Flit]](nodeCount)(None)
        val stalledEjections = Array.fill[Option[Flit]](nodeCount)(None)
        val pending = mutable.Map.empty[BigInt, Int] // payload -> expected destination
        val routes = mutable.Set.empty[(Int, Int)]
        var nextId = 1L
        var cycle = 0
        var idleCycles = 0
        var acceptedCount = 0
        var deliveredCount = 0
        var sawInjectionStall = false
        var sawEjectionStall = false
        var sawFullBlockage = false

        def tick(generate: Boolean, draining: Boolean): Unit = {
          val phase = cycle % 4096
          val blockAll = !draining && phase >= 1024 && phase < 1088
          sawFullBlockage ||= blockAll
          val forceReady = draining || cycle % 16 == 0
          val injectionChance = if (phase < 512) 1.0 else if (phase < 1024) 0.25 else 0.75
          val ready = Array.tabulate(nodeCount) { _ =>
            !blockAll && (forceReady || random.nextBoolean())
          }

          // Hold each offered flit unchanged until the injection handshake succeeds.
          for ((port, src) <- dut.io.nodes.zipWithIndex) {
            if (generate && offers(src).isEmpty && random.nextDouble() < injectionChance) {
              val payload = (BigInt(nextId) << 32) | BigInt(random.nextLong() & 0xffffffffL)
              offers(src) = Some(Flit(random.nextInt(nodeCount), payload))
              nextId += 1
            }
            port.inject.valid.poke(offers(src).nonEmpty)
            port.inject.bits.dst.poke(BigInt(offers(src).fold(0)(_.dst)))
            port.inject.bits.payload.poke(offers(src).fold(BigInt(0))(_.payload))
            port.eject.ready.poke(ready(src))
          }

          var progressed = false
          for ((port, src) <- dut.io.nodes.zipWithIndex) {
            offers(src).foreach { flit =>
              if (port.inject.ready.peekBoolean()) {
                assert(!pending.contains(flit.payload), s"cycle $cycle: payload ID reused")
                pending(flit.payload) = flit.dst
                routes += ((src, flit.dst))
                offers(src) = None
                acceptedCount += 1
                progressed = true
              } else {
                sawInjectionStall = true
              }
            }
          }

          for ((port, dst) <- dut.io.nodes.zipWithIndex) {
            val current = if (port.eject.valid.peekBoolean()) {
              Some(Flit(port.eject.bits.dst.peekValue().asBigInt.toInt, port.eject.bits.payload.peekValue().asBigInt))
            } else None

            stalledEjections(dst).foreach { previous =>
              assert(current.contains(previous), s"cycle $cycle: stalled ejection changed at node $dst")
            }
            current.foreach { flit =>
              assert(flit.dst == dst, s"cycle $cycle: wrong destination at node $dst")
              assert(pending.get(flit.payload).contains(dst), s"cycle $cycle: unknown or duplicate payload ${flit.payload}")
              if (ready(dst)) {
                pending.remove(flit.payload)
                deliveredCount += 1
                progressed = true
              } else {
                sawEjectionStall = true
              }
            }
            stalledEjections(dst) = if (ready(dst)) None else current
          }

          // External handshakes bound progress without exposing internal hardware signals.
          // Every receiver is periodically ready; intentional full blockage lasts only 64 cycles.
          idleCycles = if (pending.nonEmpty && !progressed) idleCycles + 1 else 0
          assert(idleCycles < 256, s"cycle $cycle: no progress for $idleCycles cycles, pending=$pending")
          assert(pending.size < nodeCount * depth, s"cycle $cycle: no bubble remains")
          dut.clock.step()
          cycle += 1
        }

        println(s"[RingRandomSpec] seed=$seed nodes=$nodeCount depth=$depth: start")
        for (_ <- 0 until runCycles) {
          tick(generate = true, draining = false)
          if (cycle % 25000 == 0) {
            println(s"[RingRandomSpec] seed=$seed: $cycle/$runCycles cycles")
          }
        }

        // Finish outstanding source offers, then require every accepted flit to drain.
        val drainLimit = nodeCount * depth * nodeCount * 4 + 256
        var drainCycles = 0
        while ((pending.nonEmpty || offers.exists(_.nonEmpty)) && drainCycles < drainLimit) {
          tick(generate = false, draining = true)
          drainCycles += 1
        }
        pending shouldBe empty
        offers.forall(_.isEmpty) shouldBe true
        acceptedCount shouldBe deliveredCount
        routes.size shouldBe nodeCount * nodeCount
        sawInjectionStall shouldBe true
        sawEjectionStall shouldBe true
        sawFullBlockage shouldBe true

        // Continue observing the empty ring to detect late duplicates.
        for (_ <- 0 until nodeCount * depth * 2) {
          tick(generate = false, draining = true)
        }
        println(s"[RingRandomSpec] seed=$seed: passed and drained")
      }
    }
  }
}
