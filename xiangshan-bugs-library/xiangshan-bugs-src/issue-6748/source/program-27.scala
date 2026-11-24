// https://github.com/OpenXiangShan/XiangShan/blob/fe2288cb09361b65afa26be3f403520c4b496dbf/src/main/scala/xiangshan/backend/fu/NewCSR/InterruptFilter.scala#L557-L559
  val delayedIntrVec = DelayN(intrVecReg, 5)
  val delayedDebugIntr = DelayN(debugIntrReg, 5)
  val delayedNMI = DelayN(nmiReg, 5)
