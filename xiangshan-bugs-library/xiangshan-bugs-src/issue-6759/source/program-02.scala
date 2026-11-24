val criticalErrorState = RegEnable(true.B, false.B,
  io.fromTop.criticalErrorState || criticalErrorStateInCSR)
