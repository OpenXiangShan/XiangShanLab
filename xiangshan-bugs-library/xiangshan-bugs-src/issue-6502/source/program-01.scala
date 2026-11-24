// trap entries: 2acbf327c L802-805; kunminghu-v2 HEAD 5d393413 L801-804; kunminghu-v3 HEAD a2340a516 L836-839
trapEntryMNEvent.valid := ((hasTrap && nmi) || dbltrpToMN) && ... && mnstatus.regOut.NMIE
trapEntryMEvent .valid := hasTrap && entryPrivState.isModeM  && ... && mnstatus.regOut.NMIE
trapEntryHSEvent.valid := hasTrap && entryPrivState.isModeHS && ... && mnstatus.regOut.NMIE   // <- delegated traps
trapEntryVSEvent.valid := hasTrap && entryPrivState.isModeVS && ... && mnstatus.regOut.NMIE
// criticalErrors: 2acbf327c L1520; v2 HEAD L1519; v3 HEAD L1624 (HEAD adds "|| debugMode" to the exclusion, #6224)
("csr_dbltrp_inMN", !mnstatus.regOut.NMIE && hasTrap && !entryDebugMode)
