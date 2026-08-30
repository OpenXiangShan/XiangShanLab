timing(memory, prefetch): shorten several critical paths

This series shortens several timing-critical control paths in
the prefetch flow, LSU logic, and the `utility` submodule. It
also raises the default LSQ and SQ capacities.

1. Register TrainFilter reorder results and L2/L3 prefetch
   address outputs in the prefetch flow.
2. Replace RR arbiters with `TwoLevelRRArbiter` in
   `L1PrefetchComponent`, `SMSPrefetcher`, and
   `LoadQueueUncache` to shorten arbitration paths.
3. Reduce long combinational logic in `ExceptionInfoGen`.
4. Replace the `FreeList` `freeSlotCnt` update with a shorter
   delta-based path.
5. Use a coarser RAR/RAW query valid policy in `NewLoadUnit` to
   shorten the load nuke query chain.
6. Delay unalign tail flush in `NewLoadUnit` s1 so tail
   injection is preserved under the extended redirect window.
7. Add an explicit `need_rep` signal in `LoadUnit` and use it
   to simplify replay admission and `blockSqIdx`/`strict`
   write control in `LoadQueueReplay`.
8. Reduce the enqueue write arbitration depth of `blockSqIdx`
   in `LoadQueueReplay`.
9. Increase the default LSQ- and SQ-related queue sizes in
   `Parameters.scala`.
10. Bump the `utility` submodule to shorten the `HwSort`
    compare path for 3- and 4-input cases and remove an
    unused `rankOH` wire.

<img width="1834" height="1710" alt="b7b2b8486a7de327130da714f31388b5" src="https://github.com/user-attachments/assets/6143a4b5-9bc5-4b50-8bf9-542c3d88fa65" />
