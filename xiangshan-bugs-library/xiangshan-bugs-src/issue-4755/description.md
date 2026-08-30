Bug descriptions:
`MainPipe`  has two requests, `req0 (probe)`, `req1 (sbuffer)`

`t0`: `req0` arrives first and to `s3`.  At this time, `WritebackQueue` cannot accept the request, thus blocking `req0`, and `s3_data_error=1`
`t1`: `req1` arrives at `s0`,
`t2`: `req1` arrives at `s1`, and `s1_fire=1`,
`t3`: `req1` arrives at `s2`, at this time `s2_may_report_data_error = 0`
`t4`: `req1` returns to sbuffer
`t5`: `WritebackQueue` can accept the request, but at this time, `GatedValidRegNextN(s1_fire, 2) = 1`, at this time `s3_data_error` will be updated to the result of `req1`, and then latched.

How to fix:
* `s3_data_error` use `s2_can_fire_to_s3` instead of `s2_fire`
