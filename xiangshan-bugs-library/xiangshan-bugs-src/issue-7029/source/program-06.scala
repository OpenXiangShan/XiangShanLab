// src/main/scala/xiangshan/mem/lsqueue/LoadQueueRAR.scala:142
val hasNotWritebackedLoad = io.query.map(_.req.bits.uop.lqIdx).map(lqIdx => isAfter(lqIdx, io.ldWbPtr))
// src/main/scala/xiangshan/mem/lsqueue/LoadQueueRAR.scala:200-205
val deqNotBlock = !isBefore(io.ldWbPtr, uop(i).lqIdx)
val needFlush = uop(i).robIdx.needFlush(io.redirect)
when (allocated(i) && (deqNotBlock || needFlush)) {
  allocated(i) := false.B
