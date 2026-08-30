[time 0]:    ROB commit a store robIdx = 0x88, flag = 0 (**Store0**); StoreQueue did not write it to sbuffer yet.

             long long ago, The store which was committed at [time 0] not write to Sbuffer.

[time N]:    Dispatch a unalign store, robIdx = 0x7c, flag = 0 (**Store1**)

[time N+x1]: Dispatch a unalign store, robIdx = 0x88, flag = 0 (**Store2**)

[time N+x2]: The store which was committed at [time 0] not write to Sbuffer, deqPtr points to this request (Store0). The store of [time N+x1] (Store2) was issued, then, it enter the StoreMisalignBuffer.

[NOTE]:      Store2 is not oldest store of unalign, but the robIdx which was pointed by deqPtr is same as Store2 at [time N+x2], therefore, Store2 was mistakenly regarded as the oldest, Store2 should not enter the StoreMisalignBuffer.

[time N+x3]: Store1 issued, occupy the StoreMisalignBuffer, but do not write the Store2, which lead to hang.

This Patch use CmtPtr to indicate the oldest store of not committed yet.
