// backend/dispatch/NewDispatch.scala:449-450
fromRenameUpdate(i).valid := ... && !fromRename(i).bits.hasException && ...
// backend/dispatch/NewDispatch.scala:690-700
}.elsewhen(isStoreVec(i) || isVStoreVec(i)) {
  enqLsqIO.needAlloc(i) := 2.U // store | vstore
