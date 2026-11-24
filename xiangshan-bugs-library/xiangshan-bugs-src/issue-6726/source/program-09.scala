// https://github.com/OpenXiangShan/XiangShan/blob/18657f51ce1ed4acaffdceb38bb79ebae68b6af4/src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/TrapEntryMNEvent.scala#L57-L58
  out.mncause.bits.Interrupt     := isInterrupt
  out.mncause.bits.ExceptionCode := highPrioTrapNO
