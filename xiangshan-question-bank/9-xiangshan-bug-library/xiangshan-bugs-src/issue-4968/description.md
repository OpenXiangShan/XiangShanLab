Fix #4962 simv failure.

#4962 seems to have other problem, so instead merging this to it, I'd suggest merging this to kunminghu-v3 first and rebase #4962 later.

The root cause of that failure is we send read requests to mbtb/tage before their sram ready. #4946/kunminghu-v3 can pass CI because we don't have Bpu.io.train before #4962, chisel can opt-out train-related code, including the failure assertion.

From SRAMTemplate:
```scala
  private val singleHold = if(singlePort) io.w.req.valid else false.B
  private val resetHold = if(shouldReset) resetState else false.B
  io.r.req.ready := !resetHold && !singleHold && !conflictStallRead
```

We cannot connect `io.r.req.ready` directly to `s0_fire`, that forms a combinational loop in tage. So we use a resetDone register to block requests until Sram reset is done (`!resetHold`)
