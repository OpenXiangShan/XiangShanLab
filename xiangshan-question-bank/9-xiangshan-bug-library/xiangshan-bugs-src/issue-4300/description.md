**Bug trigger point:**

The flush occurs during the `s_wait` phase. The entry has already passed the flush trigger condition of `io.uncache.resp.fire`, leading to no flush. As a result, `needFlushReg` remains in the register until the next new entry's `io.uncache.resp.fire`, at which point the normal entry is flushed, causing the program to stuck.

**Bug analysis:** The granularity of flush handling is too coarse.

In the original calculation:
```
val flush = (needFlush && uncacheState === s_idle) || (io.uncache.resp.fire && needFlushReg)
```
Flush is only handled in two states: `s_idle` and non-`s_idle`. This distinction makes the handling of the other three non-`s_idle` states very coarse. In fact, for the remaining three states, there needs to be corresponding feedback based on when `needFlush` is generated and when `NeedFlushReg` is delayed in the register.
1. In the `s_req` state, before the uncache request is sent, the flush can be performed in time, using `needFlush` to prevent the request from being sent.
2. If the request has been sent and the state reaches `s_resp`, to avoid mismatch between the uncache request and response, the flush can be only performed after receiving the uncache response, i.e., use `needFlush || needFlushReg` to flush when `io.uncache.resp.fire`.
3. If a flush occurs during the `s_wait` state, it can also prevent a write-back and use `needFlush` to flush in time.

**Bug Fix:** 

For better code readability, the `uncacheState` state machine update is used here to update the `wire` `flush`. Where `flush` refers to executing the flush, `needFlush` refers to the signal that triggers the flush, and `needFlushReg` refers to the flush signal stored for delayed processing flush.
