// https://github.com/OpenXiangShan/XiangShan/blob/6705b63685d301f8c0960f7cbcc8b964660dc58e/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala#L388-L390
  val nmip = RegInit(new NonMaskableIRPendingBundle, (new NonMaskableIRPendingBundle).init)
  nmip.NMI_43 := nonMaskableIRP.NMI_43
  nmip.NMI_31 := nonMaskableIRP.NMI_31
