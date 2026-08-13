private val adjustinterruptNO = Mux(
  InterruptNO.getVS.map(_.U === interruptNO).reduce(_ || _) && vsHasIR,
  interruptNO - 1.U, // map VSSIP, VSTIP, VSEIP to SSIP, STIP, SEIP
  interruptNO,
)
private val pcFromXtvec = Cat(
  xtvec.addr.asUInt + Mux(xtvec.mode === XtvecMode.Vectored && hasIR,
                          adjustinterruptNO(5, 0), 0.U),
  0.U(2.W)
)
