val isNC      = tlbHit && tlbAccessible && Pbmt.isNC(pbmt)
val isMMIO    = tlbHit && tlbAccessible &&
                (Pbmt.isIO(pbmt) || Pbmt.isPMA(pbmt) && pmp.mmio)
val isUncache = isNC || isMMIO     
val afCboUncache = isCbo && isUncache  // CBO + PBMT=NC 被归入 isUncache，进而触发 Store Access Fault
val af = afInaccessible || afVectorUncache || afCboUncache || afUnalignMMIO
stageInfo.uop.exceptionVec(storeAccessFault) := af
