when(fromCSR.claim) {
  val index  = toCSR.topei(params.imsicIntSrcWidth - 1, params.xlenWidth)
  val offset = toCSR.topei(params.xlenWidth - 1, 0)
  eips(index) := eips(index) & ~UIntToOH(offset)   // clears THIS file's own top
}
