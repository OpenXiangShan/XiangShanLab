out.mtinst.bits.ALL := Mux(
  isFetchGuestExcp && in.trapIsForVSnonLeafPTE ||
  isLSGuestExcp && in.memExceptionIsForVSnonLeafPTE,
  0x3000.U,   // always load encoding; 0x3020.U for store
  0.U
)
