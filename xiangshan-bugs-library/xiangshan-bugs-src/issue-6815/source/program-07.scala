      when(enqUop.waitForward) {
        hasWaitForward := true.B
      }
      val enqTriggerActionIsDebugMode = TriggerAction.isDmode(io.enq.req(i).bits.trigger)
      val enqHasException = enqUop.exceptionVec.orR
      when(enqUop.isWFI && !enqHasException && !enqTriggerActionIsDebugMode) {
        hasWFI := true.B
      }
