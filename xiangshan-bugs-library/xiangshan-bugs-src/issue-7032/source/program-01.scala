private val hasExceptionInDmode = debugMode && hasTrap
val debugPc = Mux(hasExceptionInDmode && !breakPoint, DebugException.U, DebugEntry.U)
out.targetPc.valid := RegNext(valid || hasExceptionInDmode)
