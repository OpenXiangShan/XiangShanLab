// NewStoreQueue.scala:2219
io.sqEmpty := virtualStoreQueue.io.empty && physicalQueue.io.empty

// NewStoreQueue.scala:2106
io.empty := deqPtrExt.head === commitPtrExt.head && deqPtrExt.head === virtualStoreQueueRetiredPtr

// NewStoreQueue.scala:1704-1706
private val virtualStoreQueueRedirect = GatedRegNext(io.fromVirtualStoreQueue.redirectPtr.asInstanceOf[Valid[SqPtr]])
private val virtualStoreQueuePreCommitPtr = GatedRegNext(io.fromVirtualStoreQueue.preCommitPtr.asInstanceOf[Valid[SqPtr]])
private val virtualStoreQueueRetiredPtr = GatedRegNext(io.fromVirtualStoreQueue.retiredPtr.asInstanceOf[SqPtr])
