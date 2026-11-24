when(unprivCountUpdate) { reg := mHPM.instret }   // same for cycle
regOut := Mux(debugModeStopCount, reg.asUInt, mHPM.instret)
