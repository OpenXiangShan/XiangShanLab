toPhysicalQueueRetirePtrNext := toPhysicalQueueRetirePtr +
  Mux(retireValidReg, retireReqNumReg, 0.U)
...
when (ctrlEntries(redirectLastValidPtr.value).allocated) {
  redirectRecoverPtr := getEntryEndSqIdx(redirectLastValidPtr)
}.otherwise {
  redirectRecoverPtr := toPhysicalQueueRetirePtrNext
}
