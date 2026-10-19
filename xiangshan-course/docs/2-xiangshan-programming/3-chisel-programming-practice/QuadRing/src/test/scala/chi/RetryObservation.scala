package chi

import chisel3._
import chisel3.aop.Select
import chisel3.experimental.BaseModule
import chisel3.util.ReadyValidIO
import chisel3.util.experimental.BoringUtils
import noc.Ring
import org.chipsalliance.cde.config.Parameters
import org.chipsalliance.diplomacy.lazymodule._

/** Read-only test probes. Missing/renamed registers fail elaboration instead of skipping checks. */
private[chi] object RetryObservation {
  def read(module: BaseModule, name: String): Data = {
    val matches = Select.registers(module).filter(_.instanceName == name)
    require(matches.size == 1, s"expected exactly one ${module.name} register named $name")
    BoringUtils.tapAndRead(matches.head)
  }
  def flag(module: BaseModule, name: String): Bool = read(module, name).asInstanceOf[Bool]

  private def attach(params: ChiParams, ids: Seq[Int], port: ChiPort, module: BaseModule,
    active: Bool, drained: Bool, execute: Bool, memory: Boolean, checkDrained: Bool): RetryProtocolMonitor = {
    def fire(channel: ReadyValidIO[_]): Bool =
      BoringUtils.tapAndRead(channel.valid) && BoringUtils.tapAndRead(channel.ready)
    val monitor = Module(new RetryProtocolMonitor(params, ids, memory))
    val rsp = BoringUtils.tapAndRead(port.rspTx.bits)
    val dat = BoringUtils.tapAndRead(port.datTx.bits)
    val response = fire(port.rspTx)
    val data = fire(port.datTx) && dat.opcode === ChiOpcode.Dat.CompData
    val result = response && (rsp.opcode === ChiOpcode.Rsp.Comp ||
      (if (memory) false.B else rsp.opcode === ChiOpcode.Rsp.CompDBIDResp))
    monitor.io.req.valid := fire(port.reqRx)
    monitor.io.req.bits := BoringUtils.tapAndRead(port.reqRx.bits)
    monitor.io.rsp.valid := response
    monitor.io.rsp.bits := rsp
    monitor.io.active := active
    monitor.io.current := read(module, "request").asInstanceOf[ReqFlit]
    monitor.io.result.valid := data || result
    monitor.io.result.bits := rsp
    monitor.io.result.bits.tgtID := Mux(data, dat.tgtID, rsp.tgtID)
    monitor.io.result.bits.txnID := Mux(data, dat.txnID, rsp.txnID)
    monitor.io.execute := execute
    monitor.io.endpointDrained := drained
    monitor.io.checkDrained := checkDrained
    monitor
  }

  def hn(node: HnNode, params: ChiParams, ids: Seq[Int], checkDrained: Bool): RetryProtocolMonitor = {
    val module = node.module
    val state = BoringUtils.tapAndRead(module.state)
    val waiting = read(module, "retryWaiting").asInstanceOf[Vec[Bool]]
    require(waiting.size == node.params.retrySlots, "retry observation must cover all HN slots")
    val active = state =/= module.idle && state =/= module.sendGrant && state =/= module.waitRetryReq
    val drained = state === module.idle && !waiting.asUInt.orR && !flag(module, "retryAckPending") &&
      !flag(module, "retryAckSeen") && !flag(module, "creditHeld")
    attach(params, ids, node.node.out.head._1, module, active, drained, false.B, false, checkDrained)
  }

  def sn(node: SnNode, params: ChiParams, ids: Seq[Int], checkDrained: Bool): RetryProtocolMonitor = {
    val module = node.module
    val state = BoringUtils.tapAndRead(module.state)
    val active = state =/= module.idle && state =/= module.sendGrant && state =/= module.waitRetryReq
    val drained = state === module.idle && !flag(module, "retryWaiting") && !flag(module, "retryAckPending")
    val execute = state === module.readLine || state === module.writeLine
    attach(params, ids, node.node.out.head._1, module, active, drained, execute, true, checkDrained)
  }
}

/** All observations stay in this test wrapper; the reference data model still uses only local IO. */
private[chi] class ProtocolCheckedSystem(config: ChiSystemParams)(implicit p: Parameters)
  extends OwnershipCheckedSystem(config) {
  val protocol = InModuleBody {
    val checks = IO(new Bundle {
      val checkDrained = Input(Bool())
      val hn = Output(new RetryCounts)
      val sn = Output(new RetryCounts)
    })
    checks.hn := RetryObservation.hn(hn, config.network.chi, Seq(config.rn0Id, config.rn1Id),
      checks.checkDrained).io.counts
    checks.sn := RetryObservation.sn(sn, config.network.chi, Seq(config.hnId), checks.checkDrained).io.counts
    for (rn <- Seq(rn0, rn1)) {
      val drained = BoringUtils.tapAndRead(rn.module.state) === rn.module.idle &&
        !RetryObservation.flag(rn.module, "retryAckSeen") &&
        !RetryObservation.flag(rn.module, "creditHeld") &&
        !RetryObservation.flag(rn.module, "snoopPending") &&
        !RetryObservation.flag(rn.module, "victimDirty")
      when(checks.checkDrained) {
        assert(drained, "RN state or credit leaked after drain")
      }
    }
    val rings = Select.collectDeep(network.module) { case ring: Ring[_] => ring }.toSeq
    require(rings.size == 4 && rings.forall(_.stops.size == 4), "observe all four rings and all stops")
    for (ring <- rings; (stop, index) <- ring.stops.zipWithIndex) {
      val next = ring.stops((index + 1) % ring.stops.size)
      val credits = BoringUtils.tapAndRead(stop.downstreamCredits)
      val occupied = BoringUtils.tapAndRead(next.buffer.io.count)
      assert((credits +& occupied) === config.network.ring.bufferDepth.U,
        "link credit and downstream occupancy do not balance")
      when(checks.checkDrained) {
        assert(credits === config.network.ring.bufferDepth.U && occupied === 0.U,
          "ring credit or buffered flit leaked after drain")
      }
    }
    checks
  }
}

/** Raw RN drivers can delay a credited retry behind a new request at a one-slot HN. */
private[chi] class RetryCheckedHnSn(config: ChiNoCParams, ids: Seq[Int], initialLines: Seq[BigInt])
  (implicit p: Parameters) extends ChiNodeFixtures.HnSnOnNoC(config, ids, initialLines, retrySlots = 1) {
  val protocol = InModuleBody {
    val checks = IO(new Bundle {
      val checkDrained = Input(Bool())
      val hn = Output(new RetryCounts)
      val sn = Output(new RetryCounts)
    })
    checks.hn := RetryObservation.hn(hn, config.chi, Seq(ids.head, ids(2)), checks.checkDrained).io.counts
    checks.sn := RetryObservation.sn(sn, config.chi, Seq(ids(1)), checks.checkDrained).io.counts
    checks
  }
}

/** The serial product HN cannot contend with itself at SN; drive overlapping requests here. */
private[chi] class RetryCheckedSn(config: ChiNoCParams, params: SnParams)(implicit p: Parameters)
  extends ChiNodeFixtures.Sn(config, params) {
  val protocol = InModuleBody {
    val checks = IO(new Bundle {
      val checkDrained = Input(Bool())
      val counts = Output(new RetryCounts)
    })
    checks.counts := RetryObservation.sn(sn, config.chi, Seq(1), checks.checkDrained).io.counts
    checks
  }
}
