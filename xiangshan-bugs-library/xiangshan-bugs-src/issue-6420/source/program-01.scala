// src/main/scala/xiangshan/backend/fu/NewCSR/Debug.scala  (MemTrigger)
val tiggerVaddrHit = Mux(isCacheLine, cacheLineEq,
                     Mux(isVectorStride, hitVecVectorStride, triggerHitVec))

def DcacheLineBitsEq(): (Bool, Vec[Bool]) = (
  io.isCbo.getOrElse(false.B),
  VecInit(tdataVec.zip(tEnableVec).map { case (tdata, en) =>
    !tdata.select && !debugMode && en &&
      tdata.store && io.isCbo.getOrElse(false.B) &&
      (vaddr >> DCacheLineOffset) === (tdata.tdata2 >> DCacheLineOffset)  // no matchType
  })
)
