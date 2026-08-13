Cut critical path prefetchPipe s2 -> toMSHRArbiter.valid(i) -> toMSHR.paddr -> missUnit hit -> missUnit.req.ready -> prefetchPipe toMSHRArbiter.ready ***-> s2_finish ->*** s2_ready -> s1_ready -> toFtq.ready
 for timing.

This can be thought of as adding 1 cycle to the prefetchPipe s2_finish, but only a minor performance change is expected, since the timing of issuing the first miss request is unchanged, and the additional waiting delay for subsequent miss requests can be hidden by the l2 cache access delay.
