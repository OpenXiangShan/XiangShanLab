// mem/lsqueue/StoreQueue.scala:1496-1500
needCancel(i) := allocated(i) && !committed(i) && Mux(
    vecExceptionFlag.valid,
    isAfter(uop(i).robIdx, io.brqRedirect.bits.robIdx) && io.brqRedirect.valid,
    uop(i).robIdx.needFlush(io.brqRedirect)
  )
