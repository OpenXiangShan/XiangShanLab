when `io.dcache.req.ready` is false, misalign load will be stall, but `wakeup`  still work normally and is not canceled in `s3`, which will cause the backend to get wrong data.
