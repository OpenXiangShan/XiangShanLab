This PR fixed two issues of unaligned handle at the `StoreQueue` : 

[1] when a `cross16B` request split into two request, `deqPtr` move too early. (Reported in Issue #5846)

[2] when `rdataPtr_0` is aligned request, but `rdataPtr_1` is unaligned request (`cross16B`), the `StoreQueue` will write the two requests above at the same time, which doesn't make sense! The second request should be split into two.
