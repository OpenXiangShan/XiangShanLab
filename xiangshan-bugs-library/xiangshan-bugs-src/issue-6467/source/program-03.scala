deqPtrVectorInactiveValid(i)   := ctrl.committed && ctrl.vecInactive                        // :1272  (ungated)
rdataPtrVectorInactiveValid(i) := ctrl.committed && dataQueue.io.empty && ctrl.vecInactive  // :1302  (extra gate)
