class tpMetaEntry(implicit p: Parameters) extends TPBundle {
  val valid      = Bool()
  val triggerTag = UInt((fullAddressBits - blockOffBits - tpTableSetBits).W)
}
