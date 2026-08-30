// src/main/scala/xiangshan/frontend/ftq/ResolveQueue.scala
when(entry.valid &&
  (backendRedirect.reduce(_ || _) && entry.bits.ftqIdx > backendRedirectPtr ||
    io.bpuEnqueue && entry.bits.ftqIdx.value === io.bpuEnqueuePtr.value)) {
  entry.bits.flushed := true.B
}
