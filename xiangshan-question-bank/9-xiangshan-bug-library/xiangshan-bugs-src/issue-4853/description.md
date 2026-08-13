In the previous design, AtomicsUnit sends out sbuffer flush request only under `s_tlb_and_flush_sbuffer_req` state. The request sets sbuffer under `x_drain_all` state. Sbuffer returns to `x_idle` state when it is empty. However StoreQueue may not be fully cleared at this point because there could be committed stores that haven't yet entered sbuffer. After these stores eventually enter sbuffer, sbuffer remains in `x_idle` state and will not flush them into DCache. This results in sbuffer being unable to drain completely, therefore the atomic instruction gets into deadlock.

This commit fixes this bug by continuously request sbuffer flush until sbuffer is fully drained.

---

I directly copied the description of the previous relevant modifications.
See as follows: 
https://github.com/OpenXiangShan/XiangShan/pull/4487
