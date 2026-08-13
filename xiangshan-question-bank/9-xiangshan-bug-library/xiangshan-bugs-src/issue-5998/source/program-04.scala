// src/main/scala/xiangshan/mem/lsqueue/NewStoreQueue.scala
val s2OutMask     = ParallelLookUp(s2ByteSelectOffset, s2SelectMask) & s2LoadMaskEnd
val s2FullOverlap = (s2SelectDataEntry.byteMask & s2LoadMaskEnd) === s2LoadMaskEnd
val s2SafeForward = !s2MultiMatch || s2FullOverlap
s2Resp.bits.forwardInvalid := !s2SafeForward || s2Cross4KPage
