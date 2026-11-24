// src/main/scala/xiangshan/mem/lsqueue/VirtualLoadQueue.scala:138
val deqLookup = VecInit(deqLookupVec.map(ptr => allocated(ptr.value) && committed(ptr.value) && ptr =/= enqPtrExt(0)))
// src/main/scala/xiangshan/mem/lsqueue/VirtualLoadQueue.scala:155
io.ldWbPtr := deqPtr
