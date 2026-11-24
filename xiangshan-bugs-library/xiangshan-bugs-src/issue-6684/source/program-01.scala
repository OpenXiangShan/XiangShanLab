vlFreeList.io.walk := io.vlCommits.isWalk
vlFreeList.io.doAllocate := intFreeList.io.canAllocate && fpFreeList.io.canAllocate && vecFreeList.io.canAllocate && io.toLsqEnqCtrl.canAccept && dispatchCanAcc || io.vlCommits.isWalk
vlFreeList.io.allocateReq(i) := needVlDest(i)
