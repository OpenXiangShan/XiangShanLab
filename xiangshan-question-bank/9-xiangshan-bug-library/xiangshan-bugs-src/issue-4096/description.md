**Old design**:
When enqueuing, it is in the order of ldu0-1, i.e. ldu0 is allocated first.

**Bug scene:**
LQUncacheBuffer is small. The enqueue `robIdx` of ldu0-1 is [57, 56, 55], the [57, 56] can enqueue, and [55] can not because buffer is full. 57/56 send the `NC` request after enqueuing. 55 is rollbacked. In principle, 57 and 56 need be flushed. But to ensure the correspondence between requests and responses of uncache, 57 is flushed when getting the uncache response. So when the same sequence [57, 56, 55] is coming, there is still no space to allocate 55, which causes that it is rollbacked again. Then a deadblock emerged.
This bug is triggered after cutting `LoadUncacheBufferSize` from 20 to 4.

**One way to fix**:
When enqueuing, it is in the order of `robIdx`, i.e. the oldest is allocated first.
