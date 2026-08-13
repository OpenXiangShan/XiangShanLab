1. do not use `s2_hits` for `io.perf`, since it will be reset on refill, when `io.perf` is sent to Ifu, `s2_hits` is almost always `true`, so we use a `s2_rawHits` instead
2. re-write perf IOs to reduce complexity
3. fix ICache top PerfEvents
4. add `MshrReadyCnt` to check Mshr occupancy
