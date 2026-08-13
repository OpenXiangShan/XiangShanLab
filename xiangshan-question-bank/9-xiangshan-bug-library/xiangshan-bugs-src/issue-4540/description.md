When `pte_valid` is true and a page fault or guest page fault occurs, the original design only treated `ppn_af` as invalid (without checking whether the higher bits of ppn are zero). However, in this case, the PMP check would still be performed, potentially raising the `accessFault` signal.

This commit fixes the bug by ensuring that if a PMP check fails, only `accessFault` is raised, and pf or gpf will not be incorrectly asserted. Therefore, when either pf or gpf is valid, any `accessFault` resulting from PMP should be ignored.
