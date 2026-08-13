If the `storeMisalignBuffer(SMB)` is full, the backend must be notified to resend. Previously, if the `SMB` was full but triggered an exception, it would revoke the enq storeMisalignBuffer and notify the backend that resending was unnecessary.

This resulted in the following timing path:
**pmp => exception => revoke => feedback_slow.bits.hit => issueQueueSta**

We will modify this to:
When notifying the backend to resend, we will not check whether `revoke` is required. Even if it is sent to the pipeline again, it will theoretically be flushed due to the exception redirect.
