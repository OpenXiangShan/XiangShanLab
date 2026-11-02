package chi

import chisel3._
import chisel3.simulator.Exceptions.AssertionFailed
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

class RetryProtocolSpec extends AnyFreeSpec with Matchers with ChiNodeTestSupport {
  private val params = ChiParams(3, 4, 32)

  private def initialize(dut: RetryProtocolMonitor): Unit = {
    dut.io.req.valid.poke(false)
    dut.io.rsp.valid.poke(false)
    dut.io.result.valid.poke(false)
    dut.io.active.poke(false)
    dut.io.execute.poke(false)
    dut.io.checkDrained.poke(false)
    dut.io.endpointDrained.poke(true)
    request(dut.io.req.bits, 1, 0, 1, 0)
    request(dut.io.current, 1, 0, 1, 0)
    response(dut.io.rsp.bits, 0, 1, 1, ChiOpcode.Rsp.RetryAck)
    response(dut.io.result.bits, 0, 1, 1, ChiOpcode.Rsp.Comp)
  }

  private def offer(dut: RetryProtocolMonitor, rn: Int = 0, credited: Boolean = false): Unit = {
    request(dut.io.req.bits, 1, rn, 1, BigInt(rn * 64))
    dut.io.req.bits.allowRetry.poke(!credited)
    dut.io.req.valid.poke(true)
    dut.clock.step()
    dut.io.req.valid.poke(false)
  }

  private def start(dut: RetryProtocolMonitor, rn: Int = 0, credited: Boolean = false): Unit = {
    request(dut.io.current, 1, rn, 1, BigInt(rn * 64))
    dut.io.current.allowRetry.poke(!credited)
    dut.io.active.poke(true)
    dut.clock.step()
  }

  private def retryResponse(dut: RetryProtocolMonitor, grant: Boolean, rn: Int = 0): Unit = {
    response(dut.io.rsp.bits, rn, 1, if (grant) 0 else 1,
      if (grant) ChiOpcode.Rsp.PCrdGrant else ChiOpcode.Rsp.RetryAck)
    dut.io.rsp.valid.poke(true)
    dut.clock.step()
    dut.io.rsp.valid.poke(false)
  }

  private def result(dut: RetryProtocolMonitor, rn: Int = 0): Unit = {
    response(dut.io.result.bits, rn, 1, 1, ChiOpcode.Rsp.Comp)
    dut.io.result.valid.poke(true)
    dut.clock.step()
    dut.io.result.valid.poke(false)
  }

  private def finish(dut: RetryProtocolMonitor): Unit = {
    dut.io.active.poke(false)
    dut.clock.step()
  }

  for (grantFirst <- Seq(false, true)) {
    s"ledger balances overlapping requests, ID reuse and retry responses, grantFirst=$grantFirst" in {
      simulate(new RetryProtocolMonitor(params, Seq(0, 2))) { dut =>
        initialize(dut)
        offer(dut)
        start(dut)
        offer(dut, rn = 2)
        if (!grantFirst) retryResponse(dut, grant = false, rn = 2)
        result(dut)
        finish(dut)
        retryResponse(dut, grant = true, rn = 2)
        if (grantFirst) retryResponse(dut, grant = false, rn = 2)
        offer(dut, rn = 2, credited = true)
        start(dut, rn = 2, credited = true)
        result(dut, rn = 2)
        finish(dut)
        offer(dut) // The same source and txnID can be used again after completion.
        start(dut)
        result(dut)
        finish(dut)
        dut.io.checkDrained.poke(true)
        dut.clock.step(2)
        dut.io.counts.first.expect(3.U)
        dut.io.counts.started.expect(3.U)
        dut.io.counts.finished.expect(3.U)
        dut.io.counts.acks.expect(1.U)
        dut.io.counts.grants.expect(1.U)
        dut.io.counts.retries.expect(1.U)
      }
    }
  }

  private val failures: Seq[(String, String, RetryProtocolMonitor => Unit)] = Seq(
    ("uncredited retry", "retry without exactly one", dut => offer(dut, credited = true)),
    ("duplicate first attempt", "duplicate first attempt", dut => { offer(dut); offer(dut) }),
    ("duplicate RetryAck", "duplicate RetryAck", dut => {
      offer(dut); retryResponse(dut, false); retryResponse(dut, false)
    }),
    ("duplicate grant", "duplicate PCrdGrant", dut => {
      offer(dut); retryResponse(dut, true); retryResponse(dut, true)
    }),
    ("two credits for one slot", "more than one credit reserves", dut => {
      offer(dut); offer(dut, rn = 2)
      retryResponse(dut, true); retryResponse(dut, true, rn = 2); dut.clock.step()
    }),
    ("grant before resource release", "credit granted before the active slot is free", dut => {
      offer(dut); start(dut); offer(dut, rn = 2); retryResponse(dut, true, rn = 2)
    }),
    ("changed retry identity", "retry changed request identity", dut => {
      offer(dut); retryResponse(dut, false); retryResponse(dut, true)
      dut.io.req.bits.allowRetry.poke(false)
      dut.io.req.bits.addr.poke(64.U)
      dut.io.req.valid.poke(true)
      dut.clock.step()
    }),
    ("duplicate credited retry", "retry without exactly one", dut => {
      offer(dut); retryResponse(dut, false); retryResponse(dut, true)
      offer(dut, credited = true); offer(dut, credited = true)
    }),
    ("rejected request starts early", "rejected request executed before", dut => {
      offer(dut); retryResponse(dut, false); start(dut)
    }),
    ("execution without request", "execution without a matching", dut => start(dut)),
    ("duplicate execution", "execution without a matching", dut => {
      offer(dut); start(dut); result(dut); finish(dut); start(dut)
    }),
    ("duplicate result", "duplicate transaction result", dut => {
      offer(dut); start(dut); result(dut); result(dut)
    }),
    ("changed active context", "active request changed", dut => {
      offer(dut); start(dut); dut.io.current.addr.poke(64.U); dut.clock.step()
    }),
    ("completion without result", "transaction ended without a result", dut => {
      offer(dut); start(dut); finish(dut)
    }),
    ("lost grant or starvation", "retry waiter did not reach execution", dut => {
      offer(dut); retryResponse(dut, false); dut.clock.step(20)
    }),
    ("hidden reservation after drain", "reservation leaked", dut => {
      dut.io.endpointDrained.poke(false); dut.io.checkDrained.poke(true); dut.clock.step()
    })
  )
  for ((name, message, inject) <- failures) {
    s"ledger rejects $name" in {
      val error = intercept[AssertionFailed] {
        simulate(new RetryProtocolMonitor(params, Seq(0, 2), maxWait = 16)) { dut =>
          initialize(dut)
          inject(dut)
        }
      }
      error.getMessage should include(message)
    }
  }

  "SN execution is required exactly once, including execution in the starting cycle" in {
    simulate(new RetryProtocolMonitor(params, Seq(0), memory = true)) { dut =>
      initialize(dut)
      offer(dut)
      dut.io.execute.poke(true)
      start(dut)
      dut.io.execute.poke(false)
      result(dut)
      finish(dut)
      dut.io.checkDrained.poke(true)
      dut.clock.step()
    }
  }
  for (duplicate <- Seq(false, true)) {
    s"SN ledger rejects missing/duplicate physical execution, duplicate=$duplicate" in {
      val error = intercept[AssertionFailed] {
        simulate(new RetryProtocolMonitor(params, Seq(0), memory = true)) { dut =>
          initialize(dut)
          offer(dut)
          dut.io.execute.poke(duplicate)
          start(dut)
          if (duplicate) dut.clock.step()
          else { result(dut); finish(dut) }
        }
      }
      error.getMessage should include(if (duplicate) "executed more than once" else "without execution")
    }
  }
}
