* In order to ensure timing, the RAR enqueue conditions need to be compromised, worst source of timing from `pmp` and `missQueue`.

    * if `LoadQueueRARSize` == `VirtualLoadQueueSize`,  just need to exclude prefetching.
    
    * if `LoadQueueRARSize` < `VirtualLoadQueueSize`, need to consider the situation of `s2_can_query`
