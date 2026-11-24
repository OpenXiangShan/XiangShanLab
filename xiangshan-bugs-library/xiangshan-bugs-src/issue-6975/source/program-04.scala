// mem/lsqueue/LoadQueueReplay.scala:927-934
when (replayInfo.cause(LoadReplayCauses.C_DM) && enq.bits.handledByMSHR) {
  ...
  blocking(enqIndex) := !replayInfo.full_fwd && !tlDHitThisCycle && !tlDHitPrevCycle &&
                        !(replayInfo.rep_from_unalign_head && tlDHitPrevPrevCycle)
// mem/lsqueue/LoadQueueReplay.scala:490-501
when (cause(i)(LoadReplayCauses.C_DM)) {
  blocking(i) := Mux(tlDChannelHit(missMSHRId(i)), false.B, blocking(i))
}
... // or an L2 hint for missMSHRId(i)
