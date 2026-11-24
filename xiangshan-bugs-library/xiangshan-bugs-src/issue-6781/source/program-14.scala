case _ if vmi.isInstanceOf[VecMemIndex] =>
  val eew = vmi.eewValue
  for {
    sewlmul <- SewLmulPattern.all
    nf <- NfPattern.all
    sew = sewlmul.p1.sewValue
    lmul = sewlmul.p2.lmulValue
    iEmul = lmul * eew / sew
    uopNum = ((iEmul max lmul) max 1.0).toInt * nf.segNum
    if iEmul >= 0.125 && uopNum <= 8
  } yield { vi ## sewlmul ## nf }
