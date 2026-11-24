val criticalErrors = Seq(
  ("csr_dbltrp_inMN", !mnstatus.regOut.NMIE && hasTrap && !(entryDebugMode || debugMode)),
)
criticalErrorStateInCSR := criticalErrors.map(criticalError => criticalError._2).reduce(_ || _).asBool
