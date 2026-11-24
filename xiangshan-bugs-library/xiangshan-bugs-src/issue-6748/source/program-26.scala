// https://github.com/OpenXiangShan/XiangShan/blob/fe2288cb09361b65afa26be3f403520c4b496dbf/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala#L371-L373
  val nmip = RegInit(new NonMaskableIRPendingBundle, (new NonMaskableIRPendingBundle).init)
  nmip.NMI_43 := nonMaskableIRP.NMI_43
  nmip.NMI_31 := nonMaskableIRP.NMI_31
