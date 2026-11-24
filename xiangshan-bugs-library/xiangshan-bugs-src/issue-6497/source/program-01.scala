// MachineLevel.scala (mip module, "bit 13 LCOFIP")
when (fromSip.LCOFIP.valid || fromVSip.LCOFIP.valid || wen) {
  reg.LCOFIP := Mux1H(Seq(
    fromSip.LCOFIP.valid  -> fromSip.LCOFIP.bits,
    fromVSip.LCOFIP.valid -> fromVSip.LCOFIP.bits,
    wen -> wdata.LCOFIP,
  ))
}.elsewhen(lcofiReq) {
  reg.LCOFIP := lcofiReq
}
