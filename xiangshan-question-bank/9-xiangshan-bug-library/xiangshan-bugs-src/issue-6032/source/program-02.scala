// InterruptFilter.scala:287
val Candidate5: Bool = (hvictl.VTI.asUInt === 1.U) && (hvictl.IID.asUInt =/= 9.U)

// InterruptFilter.scala:347
iidOnlyC5 := hvictlReg.IID.asUInt

// InterruptFilter.scala:432-445
io.out.vstopi.IID := Mux(CandidateNoValidReg,
  0.U,
  Mux1H(Seq(
    ...
    onlyC5EnableReg -> iidOnlyC5,   // hvictl.VTI=1 path hits here
    ...
  ))
)
