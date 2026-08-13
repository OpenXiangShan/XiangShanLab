When `lsq.req` enters `uncachebuffer` and the entry that meets the merge conditions of `lsq.req` is in the following cases, the entry can not be merge.
1. receiving uncache response
2. waiting return `lsq.resp`

Why? the status of the former entry changes to `waitReturn`. There is no signal to wake up the latter entries whoes state are `waitSame`, because the trigger former entry will not be sent to bus, get response and wake up the latter entry.
