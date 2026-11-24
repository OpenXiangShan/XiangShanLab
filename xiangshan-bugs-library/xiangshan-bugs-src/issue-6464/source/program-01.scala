private val rwStimecmp_EX_II = !privState.isModeM &&
                              (!mcounterenTM && (addr === CSRs.vstimecmp.U || addr === CSRs.stimecmp.U) ||
                               !menvcfgSTCE && (addr === CSRs.stimecmp.U))
