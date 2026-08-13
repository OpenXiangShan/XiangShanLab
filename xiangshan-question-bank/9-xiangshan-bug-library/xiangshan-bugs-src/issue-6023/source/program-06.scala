// TrapEntryMEvent.scala, TrapEntryHSEvent.scala
val isStoreGPF = in.memExceptionIsForVSnonLeafPTE && highPrioTrapNO === ExceptionNO.EX_SGPF.U
out.mtinst.bits.ALL := Mux(
  isFetchGuestExcp && in.trapIsForVSnonLeafPTE ||
  isLSGuestExcp && in.memExceptionIsForVSnonLeafPTE,
  Mux(isStoreGPF, 0x3020.U, 0x3000.U),
  0.U
)
