deqPtrVectorInactiveValid(i) := ctrl.committed && ctrl.vecInactive
...
val deqCount = Cat(deqPtrMoveFromSbuffer.asUInt,
                   deqPtrVectorInactiveMove, uncacheMove)
