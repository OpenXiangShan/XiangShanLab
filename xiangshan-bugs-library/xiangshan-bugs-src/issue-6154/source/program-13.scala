val lqWriteValid = pipeIn.valid && !doFastReplay && endPipe
io.fastReplay.valid := pipeIn.valid && shouldFastReplay
