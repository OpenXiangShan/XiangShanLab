// https://github.com/OpenXiangShan/XiangShan/blob/18657f51ce1ed4acaffdceb38bb79ebae68b6af4/src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/TrapEntryMNEvent.scala#L30-L31
  private val highPrioTrapNO = in.causeNO.ExceptionCode.asUInt
  private val isInterrupt = in.causeNO.Interrupt.asBool
