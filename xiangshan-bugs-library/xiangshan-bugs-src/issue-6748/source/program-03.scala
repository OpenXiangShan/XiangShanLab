// https://github.com/OpenXiangShan/XiangShan/blob/6705b63685d301f8c0960f7cbcc8b964660dc58e/src/main/scala/xiangshan/backend/fu/NewCSR/InterruptFilter.scala#L537-L541
  val nmiReg = RegNext(io.in.nmi, false.B)
  val nmiVecReg = RegNext(nmiVec, 0.U.asTypeOf(nmiVec))

  val normalIntrVecReg = mIRVec | hsIRVec | vsMapHostIRVec
  val intrVecReg = Mux(disableAllIntrReg, 0.U, Mux(nmiReg, nmiVecReg, normalIntrVecReg))
