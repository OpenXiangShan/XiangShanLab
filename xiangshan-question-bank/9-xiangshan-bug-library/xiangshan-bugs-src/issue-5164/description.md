Previously, lowPagePaddr/lowPageGPaddr will be set when `state == s_wait_tlb_resp && isMisalignReg && !notCross16ByteReg`, but `isMisalignReg` and `notCross16ByteReg` will be set when `state == s_pm`. Currently, the `s_pm` is the next state of the `s_wait_tlb_resp`. Therefore, when unaligned element is first split, the `Paddr` of first split requestor will not to be latched, which lead to send a zero Acquire. 

This PR relaxed the situtation of latch lowPagePaddr/highPagePaddr will now be latched based on `curPtr` (`curPtr` indicates whether it is the first split element).
