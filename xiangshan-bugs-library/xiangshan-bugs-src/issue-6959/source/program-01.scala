// VirtualStoreQueue.scala:149
private val empty = deqPtrVec.head === enqPtrVec.head

// VirtualStoreQueue.scala:282-290
// precommit store, it will be write to sbuffer before rob retire.
val preCommitEntry = ctrlEntries(preCommitPtr.value)
val preCommitMoveValid = dataEntries(preCommitPtr.value).robIdx.isSameSlot(robHeadPtr) && preCommitEntry.allocated

val preCommitPtrNext = WireInit(preCommitPtr)
when(redirectReg.valid) { // redirect next cycle update preCommitPtr
  preCommitPtrNext := deqPtrVecNext.head
}.elsewhen(preCommitMoveValid && preCommitPtr =/= enqPtrVec.head) { // preCommitPtr advance
  preCommitPtrNext := preCommitPtr + 1.U
