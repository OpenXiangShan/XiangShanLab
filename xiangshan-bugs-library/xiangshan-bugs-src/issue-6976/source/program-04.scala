// mem/lsqueue/StoreQueue.scala:1152-1156
when (
  allocated(ptr) &&
  isNotAfter(uop(ptr).robIdx, GatedRegNext(io.rob.pendingPtr)) &&
  !needCancel(ptr) &&
  (!waitStoreS2(ptr) || isVec(ptr))) {
