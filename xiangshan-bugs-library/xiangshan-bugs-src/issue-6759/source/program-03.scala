trapEntryMEvent .valid := hasTrap && entryPrivState.isModeM && !dbltrpToMN &&
  !entryDebugMode && !debugMode && !nmi && mnstatus.regOut.NMIE
