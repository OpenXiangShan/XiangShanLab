In this pr(https://github.com/OpenXiangShan/XiangShan/pull/4660), we introduce additional logic for redirect on vector exceptions.

Previously, it was because, when `vecExceptionFlag` was high, we would need to wait for `lastflow` deq, in order to clear `vecExceptionFlag`, so we might need to prevent this one instruction from being flushed.

---

However, with the previous modification, when a vector store has not yet deq, but needs to redirect itself, it will fail to cancel due to the use of `isAfter`. 
This leads to ptr exceptions in the storequeue, which can lead to stuckness.

---

The solution, which has always been simple, is to prevent canceling itself only when the exception's directive deqs, i.e., when `vecExceptionFlag` is set.
