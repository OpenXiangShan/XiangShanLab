Hi!

**Branch:** kunminghu-v3 (893e1580b7)

**Describe the bug**

Both TLB levels decide from `io.csr.priv.virt` whether SFENCE.VMA targets HS-level or VS-level entries. That signal is the data-access virtualization mode after MPRV, which differs from the current V. With MPRV=1, MPV=1 and MPP=S or U, M-mode `sfence.vma` therefore acts on VS-level entries and leaves HS-level entries valid in the L1 TLB and the L2TLB page cache. S-mode and U-mode then keep using remapped or revoked pages after the fence. Debug mode with `dcsr.MPRVEN=1` behaves identically.

Under the H extension, SFENCE.VMA applies to the translation structures of the current satp, which is the HS-level satp because M-mode runs with V=0. MPRV and MPV change explicit loads and stores alone. M-mode software also needs this fence after PMP changes, as the privileged spec describes.

**Cause**

- `src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala:1573-1577` sets `io.tlb.dvirt` to `mstatus.MPV` when MPRV applies (MPRV=1, NMIE=1, MPP≠M) and to V otherwise.
- `backend/fu/wrapper/CSR.scala:312` connects it as `tlb.priv.virt := csrMod.io.tlb.dvirt`.
- `backend/fu/Fence.scala:70-71` sets `hv`/`hg` for HFENCE alone. Plain SFENCE.VMA therefore carries no V of its own.
- `cache/mmu/TLBStorage.scala:209-227`: with `priv.virt=1`, the fence clears entries with `s2xlate =/= noS2xlate` in the current VMID and nothing else.
- `cache/mmu/PageTableCache.scala:1087-1103`: with `priv.virt=1`, the fence selects `onlyStage1` entries.

**To reproduce** (full-core Verilator simulation of XSTop, MinimalConfig)

1. S-mode loads VA `0x40000000`, mapped to page A (data `0x1111`), which fills the L1 TLB and the L2TLB.
2. M-mode remaps the PTE to page B (data `0x2222`), sets MPRV=1 and MPV=1 with MPP=S, then runs `sfence.vma`.
3. M-mode clears MPRV and MPV, then returns to S-mode with `mret`, where S-mode reloads the VA.

| Variant | Reload in S-mode |
|---|---|
| MPRV/MPV = 0/0, 0/1 or 1/0 | `0x2222` (correct) |
| MPRV=1, MPV=1 | `0x1111` (stale) |
| MPRV=1, MPV=1, address-specific `sfence.vma` | `0x1111` (stale) |
| no fence (control) | `0x1111` (stale) |
| MPRV=1, MPV=1, plus second fence after clearing MPRV | `0x2222` (correct) |

These results come from aa6b520. The fence logic listed above is identical at 893e158.

**Expected behavior**

M-mode SFENCE.VMA flushes HS-level translations regardless of MPRV and MPV.

**Suggested fix**

Decode SFENCE.VMA with the current V, either by sending V with fence requests or by adding `fenceVirt = V` to the TLB CSR bundle for the fence logic.

Thanks!
Flavien
