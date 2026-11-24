      val enqTriggerActionIsDebugMode = TriggerAction.isDmode(io.enq.req(i).bits.trigger)
      val enqHasException = enqUop.exceptionVec.orR
      when(enqUop.waitForward && !enqHasException && !enqTriggerActionIsDebugMode) {
        hasWaitForward := true.B
      }
