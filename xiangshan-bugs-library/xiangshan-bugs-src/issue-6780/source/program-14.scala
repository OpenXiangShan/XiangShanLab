val vs3RegHi = emulLoToHi(Reg.emulVs3, vs3RegLo)
val vs3OutOfBound = Reg.useVs3 && (vs3RegHi >= 32.U)
val vdOutOfBound  = Reg.useVd  && (vs3RegHi >= 32.U)
