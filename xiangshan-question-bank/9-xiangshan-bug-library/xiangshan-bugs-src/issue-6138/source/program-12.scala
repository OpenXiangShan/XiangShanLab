// src/main/scala/xiangshan/frontend/ftq/Ftq.scala
when(resolveQueue.io.bpuTrain.fire) {
  trainCache.bits.meta     := metaQueueResolve(resolveQueue.io.bpuTrain.bits.ftqIdx.value)
  trainCache.bits.startPc  := resolveQueue.io.bpuTrain.bits.startPc
  trainCache.bits.branches := resolveQueue.io.bpuTrain.bits.branches
  trainCache.bits.perfMeta := perfQueue(resolveQueue.io.bpuTrain.bits.ftqIdx.value).bpuPerf
  trainCache.valid         := true.B
}

io.toBpu.train.valid := trainCache.valid
io.toBpu.train.bits  := trainCache.bits
