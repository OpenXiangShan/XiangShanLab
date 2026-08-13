// src/main/scala/xiangshan/mem/lsqueue/LoadQueueUncache.scala
s2_enqValidVec(w) := s2_enqueue(w) && freeList.io.canAllocate(offset)

val reqNeedCheck = VecInit((0 until LoadPipelineWidth).map(w =>
  s2_enqueue(w) && !s2_enqValidVec(w)
))
...
io.rollback.valid := GatedValidRegNext(oldestRedirect.valid &&
                    !oldestRedirect.bits.robIdx.needFlush(io.redirect) &&
                    !oldestRedirect.bits.robIdx.needFlush(lastCycleRedirect) &&
                    !oldestRedirect.bits.robIdx.needFlush(lastLastCycleRedirect))
io.rollback.bits := RegEnable(oldestRedirect.bits, oldestRedirect.valid)
