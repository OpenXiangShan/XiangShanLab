Due to the existence of the following timing path:
**sta.io.s0_s1_s2_valid => VSplit.io.out.valid => VSplit.io.vstd.valid => std.io.ready => issueQueueStd.ready**

we choose to decouple VSplit's vsta out from its vstd out.
vstd can preempt std's out and write to the storequeue at any time, without needing to wait for vsta.
