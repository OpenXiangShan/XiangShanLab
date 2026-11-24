// https://github.com/OpenXiangShan/XiangShan/blob/18657f51ce1ed4acaffdceb38bb79ebae68b6af4/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala#L1673-L1677
    val interrupt = trapHandleMod.io.out.causeNO.Interrupt.asBool
    val trapNO = Mux(virtualInterruptIsHvictlInject && interrupt, hvictl.regOut.IID.asUInt, trapHandleMod.io.out.causeNO.ExceptionCode.asUInt)
    val hasNMI = nmi && hasTrap
    val interruptNO = Mux(interrupt, trapNO, 0.U)
    val exceptionNO = Mux(!interrupt, trapNO, 0.U)
