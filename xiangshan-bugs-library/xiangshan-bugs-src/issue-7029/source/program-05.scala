// src/main/scala/xiangshan/mem/lsqueue/LoadQueueUncache.scala:206-209
io.ncOut.valid := (uncacheState === s_wait) && !needFlush
...
io.ncOut.bits.uop := req.uop
io.ncOut.bits.uop.lqIdx := req.uop.lqIdx
