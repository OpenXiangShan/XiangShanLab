This should not happen on a normal MMIO req (mmio is sent only when last instruction is commited, so no redirect will happen).

But on a pbmt=nc area, we can do speculative fetch: #3944, so it can be cancelled by backend/ifu redirects, we should reset MMIO fsm and cancel (or flush) requests from InstrUncache.
