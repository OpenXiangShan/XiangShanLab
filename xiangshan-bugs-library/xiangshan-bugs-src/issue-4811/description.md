In the previous design, both the L1 TLB and L2 TLB did not consider the global bit when checking for hits—they always matched both ASID and VMID. The correct matching logic should be:

1. noS2: Match ASID (or global)
2. onlyStage1: Match ASID (or global) and VMID (always need match)
3. onlyStage2: Match VMID (always need match; global bit in G-stage PTEs must be ignored by hardware)
4. allStage: Match ASID (or global) and VMID (always need match); only L1 TLB stores allStage entries

The implementation logic of sfence and hfence has also been confirmed:
1. sfence (non-virt): Match ASID (conditional based on parameters)
2. sfence (virt): Match ASID (conditional) and VMID (always need match)
3. hfence.vvma: Match ASID (conditional) and VMID (always need match)
4. hfence.gvma: Match VMID (conditional based on parameters)

Additionally, a bug was fixed where, in G-stage mode, the global bit in page table entries was not ignored by hardware when filling entries into the PageCache.
