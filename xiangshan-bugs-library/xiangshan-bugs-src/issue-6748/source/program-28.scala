// https://github.com/OpenXiangShan/XiangShan/blob/fe2288cb09361b65afa26be3f403520c4b496dbf/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala#L801-L802
  trapEntryMNEvent.valid  := ((hasTrap && nmi) || dbltrpToMN) && !entryDebugMode && !debugMode && mnstatus.regOut.NMIE
  trapEntryMEvent .valid  := hasTrap && entryPrivState.isModeM && !dbltrpToMN && !entryDebugMode && !debugMode && !nmi && mnstatus.regOut.NMIE
