package chi

import chisel3._
import chisel3.aop.Select
import chisel3.util.{Cat, log2Ceil}
import chisel3.util.experimental.BoringUtils
import org.chipsalliance.cde.config.Parameters
import org.chipsalliance.diplomacy.lazymodule._

private[chi] class OwnershipEntry(addrWidth: Int) extends Bundle {
  val valid = Bool()
  val addr = UInt(addrWidth.W)
  val state = UInt(2.W)
}

/** Actual resident lines and dirty victims; only SC/SC sharing is permitted. */
private[chi] class CacheOwnershipMonitor(addrWidth: Int, cacheLines: Int) extends Module {
  val io = IO(Input(Vec(2, Vec(cacheLines + 1, new OwnershipEntry(addrWidth)))))
  for (entries <- io; entry <- entries) {
    when(entry.valid) {
      assert(entry.state =/= RnCacheState.I, "valid ownership entry has invalid state")
      assert(entry.addr(5, 0) === 0.U, "ownership address is not line aligned")
    }
  }
  for (entries <- io; first <- entries.indices; second <- first + 1 until entries.size) {
    assert(!(entries(first).valid && entries(second).valid && entries(first).addr === entries(second).addr),
      "RN holds duplicate ownership for one line")
  }
  for (first <- io(0); second <- io(1)) {
    when(first.valid && second.valid && first.addr === second.addr) {
      assert(first.state === RnCacheState.SC && second.state === RnCacheState.SC,
        "conflicting ownership across RNs")
    }
  }
}

/** Observe existing product registers with read-only probes, only in this test fixture. */
private[chi] class OwnershipCheckedSystem(config: ChiSystemParams)(implicit p: Parameters)
  extends ChiSystem({
    require(config.coherent, "ownership checks require coherent mode")
    config
  }) {
  val ownership = InModuleBody {
    val addrWidth = config.network.chi.addrWidth
    val cacheLines = config.cacheLines
    val indexWidth = log2Ceil(cacheLines)
    val observed = IO(Output(Vec(2, Vec(cacheLines + 1, new OwnershipEntry(addrWidth)))))
    for ((rn, index) <- Seq(rn0, rn1).zipWithIndex) {
      val registers = Select.registers(rn.module)
      def read(name: String): Data = {
        val matches = registers.filter(_.instanceName == name)
        require(matches.size == 1, s"expected exactly one RN register named $name")
        BoringUtils.tapAndRead(matches.head)
      }
      val valid = read("cacheValid").asInstanceOf[Vec[Bool]]
      val tags = read("cacheTags").asInstanceOf[Vec[UInt]]
      val states = read("cacheStates").asInstanceOf[Vec[UInt]]
      require(Seq(valid.size, tags.size, states.size).forall(_ == cacheLines),
        "ownership observation must cover every cache entry")
      require(tags.head.getWidth + indexWidth + 6 == addrWidth && states.head.getWidth == 2,
        "ownership observation does not match the RN address/state encoding")
      for (slot <- 0 until cacheLines) {
        observed(index)(slot).valid := valid(slot)
        observed(index)(slot).addr := Cat(tags(slot), slot.U(indexWidth.W), 0.U(6.W))
        observed(index)(slot).state := states(slot)
      }
      // A dirty victim still holds responsibility until its data is sent or taken by a snoop.
      observed(index)(cacheLines).valid := read("victimDirty").asInstanceOf[Bool]
      observed(index)(cacheLines).addr := read("victimAddress").asInstanceOf[UInt]
      observed(index)(cacheLines).state := RnCacheState.UD
    }
    val monitor = Module(new CacheOwnershipMonitor(addrWidth, cacheLines))
    monitor.io := observed
    observed
  }
}
