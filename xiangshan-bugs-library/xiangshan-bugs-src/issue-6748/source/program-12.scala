// https://github.com/OpenXiangShan/XiangShan/blob/6705b63685d301f8c0960f7cbcc8b964660dc58e/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala#L845-L846
  trapEntryMNEvent.valid  := ((hasTrap && nmi) || dbltrpToMN) && !entryDebugMode && !debugMode && mnstatus.regOut.NMIE
  trapEntryMEvent .valid  := hasTrap && entryPrivState.isModeM && !dbltrpToMN && !entryDebugMode && !debugMode && !nmi && mnstatus.regOut.NMIE
