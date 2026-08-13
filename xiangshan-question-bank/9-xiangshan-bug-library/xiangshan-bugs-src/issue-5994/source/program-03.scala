def getPcOffset() = {
  val ftqOffset = (this.ftqOffset << instOffsetBits).asUInt
  val rvcOffset = Mux(this.isRVC, 0.U, 2.U)
  val thisPcOffset = SignExt(ftqOffset -& rvcOffset, VAddrBits)
  thisPcOffset
}
