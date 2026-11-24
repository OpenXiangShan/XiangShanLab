// L452
val bc_masked_clock = ClockGate(te.cgen, stageReq.fire | (!flush && io.refill.valid) | mbistBC.map(_.mbist.req).getOrElse(false.B), clock)
// L503-L515
when (io.refill.valid) {
  bitmapcache.io.w.apply(...)
  bitmapReplace.access(bcRefillIdx, bcVictimWay)
  bcv := bcv | bcRfvOH
}
when (bitmap_cache_clear === 1.U) {
  bcv := 0.U
}
