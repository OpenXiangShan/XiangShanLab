package chi

import chisel3._
import chisel3.util._

private[chi] class RetryCounts extends Bundle {
  val first = UInt(32.W)
  val acks = UInt(32.W)
  val grants = UInt(32.W)
  val retries = UInt(32.W)
  val started = UInt(32.W)
  val finished = UInt(32.W)
}

/** Receiver-side ledger, independent of the endpoint's retry table and credit registers. */
private[chi] class RetryProtocolMonitor(params: ChiParams, requesterIds: Seq[Int],
  memory: Boolean = false, maxWait: Int = 1024) extends Module {
  require(requesterIds.nonEmpty && requesterIds.distinct.size == requesterIds.size)
  require(maxWait > 1)
  val io = IO(new Bundle {
    val req = Input(Valid(new ReqFlit(params)))
    val rsp = Input(Valid(new RspFlit(params)))
    val active = Input(Bool())
    val current = Input(new ReqFlit(params))
    val result = Input(Valid(new RspFlit(params))) // First HN result, or final SN result.
    val execute = Input(Bool()) // Actual SN readLine/writeLine cycle.
    val checkDrained = Input(Bool())
    val endpointDrained = Input(Bool())
    val counts = Output(new RetryCounts)
  })
  private val wasActive = RegNext(io.active, false.B)
  private val start = io.active && !wasActive
  private val finish = !io.active && wasActive
  private val running = Reg(new ReqFlit(params))
  private val resultSeen = RegInit(false.B)
  private val executed = RegInit(false.B)
  private val pending = RegInit(VecInit(Seq.fill(requesterIds.size)(false.B)))
  private val saved = Reg(Vec(requesterIds.size, new ReqFlit(params)))
  private val acked = RegInit(VecInit(Seq.fill(requesterIds.size)(false.B)))
  private val granted = RegInit(VecInit(Seq.fill(requesterIds.size)(false.B)))
  private val resent = RegInit(VecInit(Seq.fill(requesterIds.size)(false.B)))

  private def sameRequest(a: ReqFlit, b: ReqFlit): Bool =
    a.srcID === b.srcID && a.tgtID === b.tgtID && a.txnID === b.txnID &&
      a.addr === b.addr && a.opcode === b.opcode && a.pCrdType === b.pCrdType

  private val ack = io.rsp.valid && io.rsp.bits.opcode === ChiOpcode.Rsp.RetryAck
  private val grant = io.rsp.valid && io.rsp.bits.opcode === ChiOpcode.Rsp.PCrdGrant
  when(io.req.valid) {
    assert(requesterIds.map(id => io.req.bits.srcID === id.U).reduce(_ || _), "unknown retry requester")
    assert(io.req.bits.pCrdType === 0.U, "unsupported retry credit type")
  }
  when(ack || grant) {
    assert(requesterIds.map(id => io.rsp.bits.tgtID === id.U).reduce(_ || _), "unknown retry recipient")
    assert(io.rsp.bits.dbID === 0.U && io.rsp.bits.pCrdType === 0.U, "malformed retry response")
  }
  when(grant) { assert(!io.active, "credit granted before the active slot is free") }
  when(start) {
    assert(requesterIds.map(id => io.current.srcID === id.U).reduce(_ || _), "unknown active requester")
    running := io.current
    resultSeen := false.B
    executed := false.B
  }
  when(io.active && wasActive) {
    assert(io.current.asUInt === running.asUInt, "active request changed before completion")
  }
  for ((id, slot) <- requesterIds.zipWithIndex) {
    val starting = start && io.current.srcID === id.U
    val receiving = io.req.valid && io.req.bits.srcID === id.U
    val age = RegInit(0.U(log2Ceil(maxWait + 1).W))
    when(!pending(slot) || starting) { age := 0.U }
      .otherwise { age := age + 1.U }
    assert(age < maxWait.U, "retry waiter did not reach execution within the test bound")

    when(starting) {
      assert(pending(slot) && sameRequest(io.current, saved(slot)), "execution without a matching request")
      assert(io.current.allowRetry === !resent(slot), "execution uses the wrong request attempt")
      assert((!acked(slot) && !granted(slot) && !resent(slot)) ||
        (acked(slot) && granted(slot) && resent(slot)), "rejected request executed before credited retry")
      pending(slot) := false.B
      acked(slot) := false.B
      granted(slot) := false.B
      resent(slot) := false.B
    }
    when(receiving) {
      when(io.req.bits.allowRetry) {
        // A new request may arrive while the previous request starts executing.
        assert(!pending(slot) || starting, "duplicate first attempt")
        pending(slot) := true.B
        saved(slot) := io.req.bits
        acked(slot) := false.B
        granted(slot) := false.B
        resent(slot) := false.B
      }.otherwise {
        assert(pending(slot) && acked(slot) && granted(slot) && !resent(slot),
          "retry without exactly one acknowledged credit")
        assert(sameRequest(io.req.bits, saved(slot)), "retry changed request identity")
        resent(slot) := true.B
      }
    }
    when(ack && io.rsp.bits.tgtID === id.U) {
      assert(pending(slot) && !acked(slot) && !resent(slot) && !starting &&
        io.rsp.bits.txnID === saved(slot).txnID, "unexpected or duplicate RetryAck")
      acked(slot) := true.B
    }
    when(grant && io.rsp.bits.tgtID === id.U) {
      assert(pending(slot) && !granted(slot) && !resent(slot) && !starting &&
        io.rsp.bits.txnID === 0.U, "unexpected or duplicate PCrdGrant")
      granted(slot) := true.B
    }
  }
  assert(PopCount(granted.zip(resent).map { case (credit, used) => credit && !used }) <= 1.U,
    "more than one credit reserves the single active slot")
  when(io.result.valid) {
    assert(io.active && (start || !resultSeen), "unexpected or duplicate transaction result")
    assert(io.result.bits.tgtID === io.current.srcID && io.result.bits.txnID === io.current.txnID,
      "result belongs to another request")
    resultSeen := true.B
  }
  if (memory) {
    when(io.execute) {
      assert(io.active && (start || !executed), "memory request executed more than once")
      executed := true.B
    }
    when(finish) { assert(executed, "memory request completed without execution") }
  }
  when(finish) { assert(resultSeen, "transaction ended without a result") }

  private def count(event: Bool): UInt = {
    val value = RegInit(0.U(32.W))
    when(event) { value := value + 1.U }
    value
  }
  io.counts.first := count(io.req.valid && io.req.bits.allowRetry)
  io.counts.acks := count(ack)
  io.counts.grants := count(grant)
  io.counts.retries := count(io.req.valid && !io.req.bits.allowRetry)
  io.counts.started := count(start)
  io.counts.finished := count(finish)
  when(io.checkDrained) {
    assert(!io.active && !wasActive && !pending.asUInt.orR && io.endpointDrained,
      "retry state or reservation leaked after drain")
    assert(io.counts.first === io.counts.started && io.counts.started === io.counts.finished,
      "request execution count does not balance")
    assert(io.counts.acks === io.counts.grants && io.counts.grants === io.counts.retries,
      "retry credit count does not balance")
  }
}
