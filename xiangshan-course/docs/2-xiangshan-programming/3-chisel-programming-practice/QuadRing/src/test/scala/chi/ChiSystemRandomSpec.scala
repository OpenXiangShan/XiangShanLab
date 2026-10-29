package chi

import chisel3._
import noc.RingParams
import org.chipsalliance.cde.config.Parameters
import org.chipsalliance.diplomacy.lazymodule.LazyModule
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import scala.collection.mutable
import scala.util.Random

/** Non-coherent traffic checked only at the product system's local RN interfaces. */
class ChiSystemRandomSpec extends AnyFreeSpec with Matchers with ChiNodeTestSupport {
  private implicit val p: Parameters = Parameters.empty
  private case class Command(line: Int, write: Boolean, data: BigInt, txn: BigInt)
  private case class Pending(command: Command, expected: BigInt, acceptedAt: Int)
  private val longRun = sys.env.get("QUADRING_LONG_TESTS") match {
    case None | Some("0") => false
    case Some("1") => true
    case Some(value) => throw new IllegalArgumentException(s"QUADRING_LONG_TESTS must be 0 or 1, got '$value'")
  }
  private val runCycles = if (longRun) 100000 else 5000
  private val mask = (BigInt(1) << 512) - 1
  private val memory = Seq.tabulate(8)(i => (BigInt(1) << 511) | (BigInt(i) << 256) | BigInt(i))
  private val cases = Seq(
    (ChiSystemParams(initialLines = memory), 1L),
    (ChiSystemParams(initialLines = memory), 0x5eedL),
    (ChiSystemParams(ChiNoCParams(ChiParams(3, 7, 32), RingParams(4, 5)),
      6, 1, 4, 0, memory.take(7)), 0xc0ffeeL),
    (ChiSystemParams(ChiNoCParams(ChiParams(2, 1, 8), RingParams(4, 2)),
      initialLines = memory.take(3)), 7L),
    (ChiSystemParams(ChiNoCParams(ChiParams(2, 1, 6), RingParams(4, 2)),
      initialLines = memory.take(1)), 42L)
  )

  for ((params, seed) <- cases) {
    s"seed $seed checks $runCycles non-coherent cycles with ${params.network.chi}" in {
      simulate(LazyModule(new ChiSystem(params)).module) { dut =>
        val ports = Seq(dut.io.rn0, dut.io.rn1)
        ports.foreach(initialize)
        val random = new Random(seed)
        val reference = params.initialLines.toArray
        val offers = Array.fill[Option[Command]](2)(None)
        val pending = Array.fill[Option[Pending]](2)(None)
        // Reserve a line from offer creation until result consumption. Different lines may overlap;
        // same-line accesses then have a known order without observing HN's internal arbitration.
        val reserved = mutable.Set.empty[Int]
        val stalledResults = Array.fill[Option[(BigInt, BigInt)]](2)(None)
        val kinds = mutable.Set.empty[(Int, Boolean)]
        val addressed = mutable.Set.empty[Int]
        val payloadKinds = mutable.Set.empty[Int]
        val txnMask = (BigInt(1) << params.network.chi.txnIdWidth) - 1
        var cycle = 0
        var idleCycles = 0
        var nextCommand = 0
        var accepted = 0
        var completed = 0
        var sawRequestStall = false
        var sawResponseStall = false

        def busy: Boolean = offers.exists(_.nonEmpty) || pending.exists(_.nonEmpty)

        def randomCommand(): Command = {
          val available = reference.indices.filterNot(reserved)
          val line = available(random.nextInt(available.size))
          val write = random.nextBoolean()
          val pattern = nextCommand % 4
          val data = pattern match {
            case 0 => BigInt(0)
            case 1 => mask
            case 2 => BigInt(1) << 511
            case _ => BigInt(512, random)
          }
          if (write) payloadKinds += pattern
          // Deliberately reuse IDs, including equal IDs at both RNs and the one-bit boundary.
          val txn = BigInt(nextCommand / 2) & txnMask
          nextCommand += 1
          Command(line, write, data, txn)
        }

        def tick(generate: Boolean, draining: Boolean): Unit = {
          if (generate) {
            val first = random.nextInt(2)
            for (rn <- Seq(first, 1 - first)) {
              if (offers(rn).isEmpty && reserved.size < reference.length && random.nextInt(4) != 0) {
                val cmd = randomCommand()
                offers(rn) = Some(cmd)
                reserved += cmd.line
              }
            }
          }
          val blocked = !draining && cycle % 1024 >= 256 && cycle % 1024 < 288
          val ready = Array.fill(2)(draining || (!blocked && (cycle % 16 == 0 || random.nextBoolean())))
          for ((port, rn) <- ports.zipWithIndex) {
            port.response.ready.poke(ready(rn))
            port.request.valid.poke(offers(rn).nonEmpty)
            offers(rn).foreach { cmd =>
              port.request.bits.write.poke(cmd.write)
              port.request.bits.addr.poke(BigInt(cmd.line) << 6)
              port.request.bits.data.poke(cmd.data)
              port.request.bits.txnID.poke(cmd.txn)
            }
          }

          var progressed = false
          for ((port, rn) <- ports.zipWithIndex) {
            offers(rn).foreach { cmd =>
              if (port.request.ready.peekBoolean()) {
                assert(pending(rn).isEmpty, s"cycle $cycle RN$rn accepted a second command")
                pending(rn) = Some(Pending(cmd, if (cmd.write) BigInt(0) else reference(cmd.line), cycle))
                offers(rn) = None
                accepted += 1
                kinds += ((rn, cmd.write))
                addressed += cmd.line
                progressed = true
              } else sawRequestStall = true
            }
          }

          for ((port, rn) <- ports.zipWithIndex) {
            val current = if (port.response.valid.peekBoolean()) {
              Some((port.response.bits.txnID.peekValue().asBigInt, port.response.bits.data.peekValue().asBigInt))
            } else None
            stalledResults(rn).foreach { previous =>
              assert(current.contains(previous), s"cycle $cycle RN$rn changed a blocked result")
            }
            current.foreach { case (txn, data) =>
              val entry = pending(rn).getOrElse(fail(s"cycle $cycle RN$rn produced an unexpected or duplicate result"))
              assert(txn == entry.command.txn && data == entry.expected,
                s"seed=$seed cycle=$cycle RN$rn ${entry.command}: expected txn=${entry.command.txn} " +
                  s"data=0x${entry.expected.toString(16)}, got txn=$txn data=0x${data.toString(16)}")
              if (ready(rn)) {
                if (entry.command.write) reference(entry.command.line) = entry.command.data
                reserved -= entry.command.line
                pending(rn) = None
                completed += 1
                progressed = true
              } else sawResponseStall = true
            }
            stalledResults(rn) = if (ready(rn)) None else current
            pending(rn).foreach { entry =>
              assert(cycle - entry.acceptedAt < 1024, s"cycle $cycle RN$rn command timed out: $entry")
            }
          }
          idleCycles = if (busy && !progressed) idleCycles + 1 else 0
          assert(idleCycles < 512, s"seed=$seed cycle=$cycle: no progress, pending=${pending.toSeq}, offers=${offers.toSeq}")
          dut.clock.step()
          cycle += 1
        }

        println(s"[ChiSystemRandomSpec] seed=$seed cycles=$runCycles lines=${reference.length}: start")
        for (_ <- 0 until runCycles) {
          tick(generate = true, draining = false)
          if (cycle % 25000 == 0) println(s"[ChiSystemRandomSpec] seed=$seed: $cycle/$runCycles cycles")
        }
        var drainCycles = 0
        while (busy && drainCycles < 2048) {
          tick(generate = false, draining = true)
          drainCycles += 1
        }
        busy shouldBe false
        reserved shouldBe empty
        accepted shouldBe completed
        kinds.toSet shouldBe Set((0, false), (0, true), (1, false), (1, true))
        addressed.toSet shouldBe reference.indices.toSet
        payloadKinds.toSet shouldBe Set(0, 1, 2, 3)
        sawResponseStall shouldBe true
        if (reference.length > 1) sawRequestStall shouldBe true

        // Drain the final writes to SN by reading every line through both real RNs.
        for (line <- random.shuffle(reference.indices.toList); rn <- 0 until 2) {
          offers(rn) = Some(Command(line, write = false, BigInt(0), txnMask))
          reserved += line
          var waited = 0
          while (busy && waited < 1024) {
            tick(generate = false, draining = true)
            waited += 1
          }
          busy shouldBe false
        }
        accepted shouldBe completed
        reserved shouldBe empty
        // Empty observation also rejects late duplicates, including after transaction ID reuse.
        for (_ <- 0 until 32) tick(generate = false, draining = true)
        println(s"[ChiSystemRandomSpec] seed=$seed: passed, audited, and drained")
      }
    }
  }
}
