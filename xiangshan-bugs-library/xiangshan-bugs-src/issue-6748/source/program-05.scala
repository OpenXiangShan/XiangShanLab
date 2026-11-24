// https://github.com/OpenXiangShan/XiangShan/blob/6705b63685d301f8c0960f7cbcc8b964660dc58e/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala#L430-L435
  val intrVec = RegEnable(intrMod.io.out.interruptVec.bits, 0.U, intrMod.io.out.interruptVec.valid)
  val debug = RegEnable(intrMod.io.out.debug, false.B, intrMod.io.out.interruptVec.valid)
  val nmi = intrMod.io.out.nmi
  val virtualInterruptIsHvictlInject = intrMod.io.out.virtualInterruptIsHvictlInject
  val irToHS = RegEnable(intrMod.io.out.irToHS, false.B, intrMod.io.out.interruptVec.valid)
  val irToVS = RegEnable(intrMod.io.out.irToVS, false.B, intrMod.io.out.interruptVec.valid)
