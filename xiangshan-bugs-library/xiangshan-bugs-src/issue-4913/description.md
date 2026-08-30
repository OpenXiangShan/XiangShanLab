In the previous design, vaddr was sign-extended to PAddrBits to prevent cases where the physical address width exceeds the virtual address width. However, in the SV48x4 mode, the actual width of vaddr is 50 bits, which ended up being truncated to 48 bits.

In the onlyStage2 case, the generated guest physical address (gpaddr) should match vaddr. But due to the truncation, gpaddr was also limited to 48 bits, and the upper 2 bits were lost (set to zero).

To fix this bug, and to better support future extensions—vaddr is now extended to the maximum physical address width (PAddrBitsMax) as defined by the RISC-V specification.
