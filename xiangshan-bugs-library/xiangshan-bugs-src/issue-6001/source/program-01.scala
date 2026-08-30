when (defaultPrio.U === InterruptNO.SEI.U) {
  iprio.isZero := platformValid || flag
  val stopeiGreaterThan255 = stopei.IPRIO.asUInt(10, 8).orR
  iprio.greaterThan255 := stopeiGreaterThan255
  iprio.prioNum := stopei.IPRIO.asUInt(7, 0)
}
