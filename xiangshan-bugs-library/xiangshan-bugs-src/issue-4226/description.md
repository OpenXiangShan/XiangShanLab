`prefetch.w` sends a write request to `TLB/PMA/PMP`.
As a result, `PMA/PMP` returns a permission check (`io.pmp.st`) for the write request.

---

Previously, we only handled the case where `prefetch.r` did not have read permissions, not handled  the case where  `prefetch.w` did not have write permissions.
**So, when `prefetch.w` has an address without write permissions, the request will still be sent to `Dcache`, which generates an error.**

**This pr fixes that, when `PMA/PMP` returns `io.pmp.st`, we generate `dcache.s2_kill`.**
