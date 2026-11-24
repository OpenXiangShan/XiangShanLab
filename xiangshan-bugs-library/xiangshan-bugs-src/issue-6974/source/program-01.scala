val notIssuedStoreCount =
  PopCount(validVec(io.dispatch.req(i).bits.ssid)) +
  PopCount(hitInDispatchBundleVec)
val notIssuedStoreGt1 = notIssuedStoreCount > 1.U

io.dispatch.resp(i).bits.strictShouldWait :=
  io.dispatch.req(i).valid &&
  !io.dispatch.req(i).bits.isstore &&
  notIssuedStoreGt1
