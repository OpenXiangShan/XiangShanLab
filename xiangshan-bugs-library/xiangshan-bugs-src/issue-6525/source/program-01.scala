io.prefetchTrain.valid := pipeIn.valid && tlbHit && !exception && !isUncache && !isUncacheReplay &&
  in.isFirstIssue() && !isVector     // no !kill — sibling :765 has it
