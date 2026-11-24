Hi!

**Branch:** kunminghu-v3 (893e1580b7)

**Describe the bug**

With pointer masking enabled, faulting stores write the original tagged pointer to `mtval`/`stval`. `cbo.zero` and `cbo.clean` behave like stores here, while loads and AMOs report the masked address. Hardware must apply pointer masking to any address it writes into `stval` (norm `pm_csr_hw_apply` in `src/priv/zpm.adoc`). This rule covers CMOs, which must also respect pointer masking.

**Cause**

`StoreUnitS1` reads the masked address from the TLB response (`src/main/scala/xiangshan/mem/pipeline/NewStoreUnit.scala:339`) but never writes it into its stage data, whose `paddr` and `gpaddr` it updates at lines 473-474. Store exceptions then take their address from the unmasked stage input (`NewStoreUnit.scala:823`, `io.exceptionInfo.bits.vaddr := sxData.fullva`). Load faults avoid this problem because the load unit copies the masked address back (`NewLoadUnit.scala:711`, `stageInfo.fullva := tlbResp.bits.fullva`). The atomics unit also takes its address from the TLB (`AtomicsUnit.scala:258`).

**To reproduce**

In M-mode with `mseccfg.PMM=2` (PMLEN=7), `a0 = 0xff00000080200040` masks to `0x0100000080200040`, which lies beyond the physical address width. All accesses below therefore raise access faults.

| Instruction | mtval on XiangShan | Spike |
|---|---|---|
| `ld a1, 8(a0)` | `0x0100000080200048` | `0x0100000080200048` |
| `amoadd.d`, `sc.d`, `cbo.flush`, `cbo.inval` on `(a0)` | `0x0100000080200040` | `0x0100000080200040` |
| `sd a1, 8(a0)`, `fsd f1, 8(a0)` | `0xff00000080200048` | `0x0100000080200048` |
| `sw a1, 12(a0)` | `0xff0000008020004c` | `0x010000008020004c` |
| `cbo.zero (a0)`, `cbo.clean (a0)` | `0xff00000080200040` | `0x0100000080200040` |

S-mode stores show this problem too. Under Sv39, storing through the tagged pointer `0xab00000080200048` page-faults with `stval = 0xab00000080200048`, where Spike reports the masked `0xff00000080200048`. These results come from full-core Verilator simulations of XSTop (MinimalConfig) at aa6b520. `NewStoreUnit.scala` is identical at 893e158.

**Expected behavior**

Stores and CBOs write the masked address to `mtval`/`stval`, as loads and AMOs do.

**Suggested fix**

Add `stageInfo.fullva := fullva` in `StoreUnitS1` next to the `paddr`/`gpaddr` updates (`NewStoreUnit.scala:473-474`).

Thanks!
Flavien
