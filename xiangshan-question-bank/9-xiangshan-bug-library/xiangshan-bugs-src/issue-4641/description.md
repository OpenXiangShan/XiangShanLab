## Bug Description

A represents a normal store, and B represents an NC (Non-Cacheable) store. In the SQ (Store Queue), there exists a sequence like A1 B1 B2 B3 B4 .... A1 enters the `dataBuffer`, and `rdataPtr` moves to B1. Due to a blockage in the `Sbuffer`, A1 is unable to be issued, and `deqPtr` remains at A1.  

At `rdataPtr`, B1 is found and successfully sent to the `UncacheBuffer`, causing `rdataPtr` to move forward. Since the NC handshake succeeds, `deqPtr` also moves forward (from A1 to B1), even though A1 itself has not completed.  

Subsequently, as NC(Bx) handshakes continue to succeed, `deqPtr` keeps advancing. Meanwhile, `enqPtr` continues to insert new entries, eventually overwriting the original A1 entry.  

When the `Sbuffer` finally becomes available, the design attempts to clear (set `false.B`) the allocated status of the dequeueing entry of `dataBuffer` based on its original `sqIdx`. However, at this point, the A1 slot already holds a new entry, leading to incorrect deallocation.  

Later, when `rdataPtr` cycles back to A1’s position, it finds `allocated(A1)` is false, resulting in no action, causing all pointers to stall and ultimately deadlock.

## Bug Analysis

The deadlock occurs because `deqPtr` is not updated in strict order.  

* Before introducing NC support, the design guaranteed ordering due to the sequential nature of `Sbuffer` (for regular stores) and MMIO (head-of-queue processing).  
* After introducing NC, when running mixed tests with both NC and normal stores, the issue arises: there is a cycle gap between when entries complete and when `deqPtr` updates, allowing later entries to complete while earlier ones remain unfinished.

## Solution

The current solution introduces a new `completed` signal. Instead of directly advancing `deqPtr` based on previous conditions, the corresponding entry’s `completed` flag is first set to true. Then, `deqPtrNext` is updated according to whether the entry pointed to by `deqPtr` is completed.  

This approach preserves the characteristic of updating at most two entries per cycle, as in the original PR, and leverages the two-cycle write delay of `Sbuffer` along with registered `completed` signals, ensuring that `deqPtr` still raises in the last two cycles for normal stores, thus maintaining their performance.  

However, for MMIO and NC stores, `deqPtr` will now be delayed by one cycle.
