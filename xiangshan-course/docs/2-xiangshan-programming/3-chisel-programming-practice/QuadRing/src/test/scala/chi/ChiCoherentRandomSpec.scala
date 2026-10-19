package chi

import chisel3._
import noc.RingParams
import org.chipsalliance.cde.config.Parameters
import org.chipsalliance.diplomacy.lazymodule.LazyModule
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import scala.collection.mutable
import scala.util.Random

/** Enumerate the legal orders of at most two accesses to one line. */
private[chi] object CoherentReference {
  case class Access(write: Boolean, data: BigInt, result: BigInt, acceptedAt: Int, visibleAt: Int)

  def possibleValues(initial: Set[BigInt], accesses: Seq[Access]): Set[BigInt] = {
    require(accesses.nonEmpty && accesses.size <= 2)
    require(accesses.forall(a => a.acceptedAt >= 0 && a.visibleAt > a.acceptedAt))
    accesses.permutations.flatMap { order =>
      // Reject an order that puts a completed access after a later-starting one.
      val legal = order.size == 1 || order(1).visibleAt > order.head.acceptedAt
      if (!legal) Set.empty[BigInt]
      else initial.flatMap { start =>
        order.foldLeft(Option(start)) { (previous, access) =>
          previous.flatMap { value =>
            if (access.write) Option.when(access.result == 0)(access.data)
            else Option.when(access.result == value)(value)
          }
        }
      }
    }.toSet
  }
}

/** Data is checked at local ports; independent hardware assertions check actual ownership. */
class ChiCoherentRandomSpec extends AnyFreeSpec with Matchers with ChiNodeTestSupport {
  import CoherentReference._
  private implicit val p: Parameters = Parameters.empty
  private case class Command(rn: Int, line: Int, write: Boolean, data: BigInt, txn: BigInt)
  private val longRun = sys.env.get("QUADRING_LONG_TESTS") match {
    case None | Some("0") => false
    case Some("1") => true
    case Some(value) => throw new IllegalArgumentException(s"QUADRING_LONG_TESTS must be 0 or 1, got '$value'")
  }
  private val runCycles = if (longRun) 100000 else 5000
  private val mask = (BigInt(1) << 512) - 1
  private val memory = Seq.tabulate(16)(i => (BigInt(1) << 511) | (BigInt(i) << 256) | BigInt(i))
  private val cases = Seq(
    (ChiSystemParams(initialLines = memory.take(8), coherent = true,
      cacheLines = 2, directoryLines = 8), 1L),
    (ChiSystemParams(initialLines = memory.take(8), coherent = true,
      cacheLines = 2, directoryLines = 8), 0x5eedL),
    (ChiSystemParams(ChiNoCParams(ChiParams(3, 7, 32), RingParams(4, 5)),
      6, 1, 4, 0, memory.take(13), coherent = true, cacheLines = 4, directoryLines = 16), 0xc0ffeeL),
    (ChiSystemParams(ChiNoCParams(ChiParams(2, 1, 8), RingParams(4, 2)),
      initialLines = memory.take(3), coherent = true, cacheLines = 2, directoryLines = 4), 7L),
    (ChiSystemParams(ChiNoCParams(ChiParams(2, 1, 8), RingParams(4, 2)),
      initialLines = memory.take(1), coherent = true, cacheLines = 2, directoryLines = 2), 42L)
  )

  "reference admits either overlapping order and rejects invented data" in {
    val write = Access(write = true, 1, 0, 0, 8)
    val read = Access(write = false, 0, 0, 0, 8)
    possibleValues(Set(BigInt(0)), Seq(write, read)) shouldBe Set(BigInt(1))
    possibleValues(Set(BigInt(0)), Seq(write, read.copy(result = 1))) shouldBe Set(BigInt(1))
    possibleValues(Set(BigInt(0)), Seq(write, read.copy(result = 2))) shouldBe empty
    possibleValues(Set(BigInt(0)), Seq(write.copy(result = 1), read)) shouldBe empty
  }

  "reference respects first result visibility even when CPU consumption is delayed" in {
    val write = Access(write = true, 1, 0, 0, 4)
    val laterRead = Access(write = false, 0, 0, 4, 8)
    possibleValues(Set(BigInt(0)), Seq(write, laterRead)) shouldBe empty
    possibleValues(Set(BigInt(0)), Seq(write, laterRead.copy(result = 1))) shouldBe Set(BigInt(1))
  }

  "reference keeps ambiguous writes until a subsequent read resolves them" in {
    val writes = Seq(Access(write = true, 1, 0, 0, 8), Access(write = true, 2, 0, 0, 8))
    val next = possibleValues(Set(BigInt(0)), writes)
    next shouldBe Set(BigInt(1), BigInt(2))
    val read = Access(write = false, 0, 1, 9, 10)
    val resolved = possibleValues(next, Seq(read))
    resolved shouldBe Set(BigInt(1))
    possibleValues(resolved, Seq(read.copy(result = 2))) shouldBe empty
    possibleValues(Set(BigInt(0)), Seq(read.copy(result = 0), read)) shouldBe empty
  }

  for ((params, seed) <- cases) {
    s"seed $seed checks $runCycles coherent cycles with ${params.network.chi}" in {
      val top = LazyModule(new ProtocolCheckedSystem(params))
      simulate(top.module) { dut =>
        val protocol = top.protocol.getWrappedValue
        protocol.checkDrained.poke(false)
        val ports = Seq(dut.io.rn0, dut.io.rn1)
        ports.foreach(initialize)
        val random = new Random(seed)
        // Different lines commute. For one line, preserve all orders compatible with history.
        val reference = params.initialLines.map(value => Set(value)).toArray
        val txnMask = (BigInt(1) << params.network.chi.txnIdWidth) - 1
        val sameLineKinds = mutable.Set.empty[(Boolean, Boolean)]
        val addressed = mutable.Set.empty[Int]
        val payloadKinds = mutable.Set.empty[Int]
        var cycle = 0
        var commandCount = 0
        var acceptedCount = 0
        var completedCount = 0
        var overlappingConflict = false
        var peerCompletedWhileBlocked = false

        def command(rn: Int, line: Int, write: Boolean): Command = {
          val pattern = commandCount % 4
          val value = pattern match {
            case 0 => BigInt(0)
            case 1 => mask
            case 2 => BigInt(1) << 511
            case _ => BigInt(512, random)
          }
          if (write) payloadKinds += pattern
          val result = Command(rn, line, write, value, BigInt(commandCount / 2) & txnMask)
          commandCount += 1
          result
        }

        def step(): Unit = {
          dut.clock.step()
          cycle += 1
          if (cycle % 25000 == 0) println(s"[ChiCoherentRandomSpec] seed=$seed: $cycle cycles")
        }

        def runBatch(commands: Seq[Command], delays: Seq[Int] = Seq(0, 0),
          holds: Seq[Int] = Seq(0, 0)): Unit = {
          require(commands.nonEmpty && commands.size <= 2 && commands.map(_.rn).distinct.size == commands.size)
          val byRn = (0 until 2).map(rn => commands.find(_.rn == rn))
          val acceptedAt = Array.fill(2)(-1)
          val visibleAt = Array.fill(2)(-1)
          val snapshots = Array.fill[Option[(BigInt, BigInt)]](2)(None)
          val done = byRn.map(_.isEmpty).toArray
          val start = cycle
          var lastProgress = cycle
          while (!done.forall(identity) && cycle - start < 1024) {
            val ready = (0 until 2).map { rn =>
              done(rn) || (visibleAt(rn) >= 0 && cycle - visibleAt(rn) >= holds(rn) &&
                (cycle % 16 == 0 || random.nextBoolean()))
            }
            for ((port, rn) <- ports.zipWithIndex) {
              port.response.ready.poke(ready(rn))
              port.request.valid.poke(byRn(rn).nonEmpty && acceptedAt(rn) < 0 && cycle - start >= delays(rn))
              byRn(rn).foreach { cmd =>
                port.request.bits.write.poke(cmd.write)
                port.request.bits.addr.poke(BigInt(cmd.line) << 6)
                port.request.bits.data.poke(cmd.data)
                port.request.bits.txnID.poke(cmd.txn)
              }
            }
            for ((port, rn) <- ports.zipWithIndex) {
              if (port.request.valid.peekBoolean() && port.request.ready.peekBoolean()) {
                acceptedAt(rn) = cycle
                acceptedCount += 1
                addressed += byRn(rn).get.line
                lastProgress = cycle
              }
              val current = if (port.response.valid.peekBoolean()) {
                Some((port.response.bits.txnID.peek().litValue, port.response.bits.data.peek().litValue))
              } else None
              if (!done(rn)) snapshots(rn).foreach { previous =>
                assert(current.contains(previous), s"seed=$seed cycle=$cycle RN$rn changed a blocked result")
              }
              current.foreach { case (txn, _) =>
                assert(!done(rn) && acceptedAt(rn) >= 0 && cycle > acceptedAt(rn),
                  s"seed=$seed cycle=$cycle RN$rn produced an unexpected or duplicate result")
                assert(txn == byRn(rn).get.txn, s"seed=$seed cycle=$cycle RN$rn returned the wrong transaction ID")
                if (visibleAt(rn) < 0) {
                  // A result already exists even if CPU backpressure delays its consumption.
                  visibleAt(rn) = cycle
                  snapshots(rn) = current
                  lastProgress = cycle
                }
                if (ready(rn)) {
                  val peer = 1 - rn
                  if (!done(peer) && visibleAt(peer) >= 0 && !ready(peer)) peerCompletedWhileBlocked = true
                  done(rn) = true
                  completedCount += 1
                  lastProgress = cycle
                }
              }
            }
            assert(cycle - lastProgress < 256, s"seed=$seed cycle=$cycle no progress: $commands")
            step()
          }
          assert(done.forall(identity), s"seed=$seed cycle=$cycle batch timed out: $commands")
          val histories = commands.groupBy(_.line)
          for ((line, accesses) <- histories) {
            val history = accesses.map { cmd =>
              Access(cmd.write, cmd.data, snapshots(cmd.rn).get._2, acceptedAt(cmd.rn), visibleAt(cmd.rn))
            }
            val possible = possibleValues(reference(line), history)
            assert(possible.nonEmpty, s"seed=$seed cycle=$cycle line=$line has no legal order: " +
              s"initial=${reference(line)} history=$history")
            reference(line) = possible
          }
          if (commands.size == 2 && histories.size == 1) {
            sameLineKinds += ((byRn(0).get.write, byRn(1).get.write))
            if (acceptedAt.max < visibleAt.min) overlappingConflict = true
          }
          acceptedCount shouldBe completedCount
        }

        println(s"[ChiCoherentRandomSpec] seed=$seed cycles=$runCycles: start")
        runBatch(Seq(command(0, 0, write = false)))
        // A cached result stays blocked while the peer invalidates its line and finishes a write.
        runBatch(Seq(command(0, 0, write = false), command(1, 0, write = true)),
          delays = Seq(0, 4), holds = Seq(64, 0))
        runBatch(Seq(command(0, 0, write = false), command(1, 0, write = false)))
        runBatch(Seq(command(0, 0, write = true), command(1, 0, write = true)))
        runBatch(Seq(command(0, 0, write = true), command(1, 0, write = false)))

        while (cycle < runCycles) {
          val first = random.nextInt(reference.length)
          val second = if (random.nextBoolean()) first else random.nextInt(reference.length)
          runBatch(Seq(command(0, first, random.nextBoolean()), command(1, second, random.nextBoolean())),
            delays = Seq(random.nextInt(17), random.nextInt(17)),
            holds = Seq(random.nextInt(17), random.nextInt(17)))
        }
        sameLineKinds.toSet shouldBe Set((false, false), (false, true), (true, false), (true, true))
        addressed.toSet shouldBe reference.indices.toSet
        payloadKinds.toSet shouldBe Set(0, 1, 2, 3)
        overlappingConflict shouldBe true
        peerCompletedWhileBlocked shouldBe true

        // The first read resolves ambiguous overlapping writes; the peer must then agree exactly.
        for (line <- random.shuffle(reference.indices.toList)) {
          for (rn <- 0 until 2) runBatch(Seq(command(rn, line, write = false)))
          val conflict = line ^ params.cacheLines
          if (conflict < reference.length) {
            // Drop both copies and read the cleaned SN line through a cache miss.
            for (rn <- 0 until 2) runBatch(Seq(command(rn, conflict, write = false)))
            runBatch(Seq(command(0, line, write = false)))
          }
        }
        reference.forall(_.size == 1) shouldBe true
        acceptedCount shouldBe completedCount
        ports.foreach { port =>
          port.request.valid.poke(false)
          port.response.ready.poke(true)
        }
        for (_ <- 0 until 32) {
          ports.foreach(_.response.valid.expect(false.B))
          step()
        }
        protocol.hn.acks.peek().litValue should be > BigInt(0)
        protocol.checkDrained.poke(true)
        step()
        println(s"[ChiCoherentRandomSpec] seed=$seed: passed, read back, and drained")
      }
    }
  }
}
