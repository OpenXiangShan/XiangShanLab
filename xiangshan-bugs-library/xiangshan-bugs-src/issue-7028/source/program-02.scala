// src/main/scala/xiangshan/mem/lsqueue/StoreMisalignBuffer.scala:641-645
val flush = req_valid && req.uop.robIdx.needFlush(io.redirect)
...
when (flush || s2_needRevoke) {
  bufferState := s_idle
  req_valid := false.B
