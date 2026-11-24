### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and [XiangShan Documents](https://docs.xiangshan.cc/zh-cn/latest), and I believe this is a XiangShan RTL issue. 我已经阅读过 [RISC-V 指令集手册](https://github.com/riscv/riscv-isa-manual) 和 [香山文档](https://docs.xiangshan.cc/zh-cn/latest)，确认这应该是香山 RTL 的问题。
- [x] I have searched previous issues and PRs and did not find anything relevant. 我已经搜索过之前的 issue 和 PR，并没有找到相关的。
- [x] I have reproduced the issue using the latest commit on the default branch. 我已经使用默认分支最新的 commit 复现了问题。
- [x] If this report was generated with AI assistance (otherwise leave unchecked), I have verified the correctness of its content. 如果这是由 AI 生成的（否则请不要勾选），我已经验证了内容的正确性。

### Branch

kunminghu-v3

### Describe the bug

This is related to #6215, where the unmasked VPN was reported for the onlyStage2 + Sv48x4 case. The reply there said other scenarios should not be affected, and #6309 accordingly limits its change to onlyStage2 (`useEffectiveVpnIn = ... req_in_s2xlate(i) === onlyStage2 && pmm(i) === PMLEN16`). We found that the ordinary case is affected too: without virtualization (Sv39, S-mode), the L1 TLB lookup key itself is unmasked, so an entry filled through a tagged pointer survives an address-specific `SFENCE.VMA`, and tagged accesses keep the old permissions after they were revoked.

With pointer masking at `PMLEN=16`, the L1 TLB is looked up, refilled and fenced with a VPN taken from the **unmasked** address. In `DefaultConfig` `req.vaddr` is `VAddrBits` = 50 bits wide, so its bits 49:48 are tag bits and end up in the VPN:

- [`TLB.scala#L658`](https://github.com/OpenXiangShan/XiangShan/blob/8b15f06520bc7fc264095b11ece1d0e268153de7/src/main/scala/xiangshan/cache/mmu/TLB.scala#L658): `io.resp.query.vpn := get_pn(req.vaddr)` (lookup key)
- [`TLB.scala#L80`](https://github.com/OpenXiangShan/XiangShan/blob/8b15f06520bc7fc264095b11ece1d0e268153de7/src/main/scala/xiangshan/cache/mmu/TLB.scala#L80): `req_out(i) := req(i).bits`; `req_out.vaddr` is then used for the PTW request (`io.ptw.req(idx).bits.vpn := get_pn(req_out(idx).vaddr)`, L414) and the refill/hit checks (L387, L390).

The masked address is computed a few lines below L658 ([`TLB.scala#L680-L693`](https://github.com/OpenXiangShan/XiangShan/blob/8b15f06520bc7fc264095b11ece1d0e268153de7/src/main/scala/xiangshan/cache/mmu/TLB.scala#L680-L693), `effectiveVa`), but it is only used for the address checks and `fullva`.

As a result, an access through a pointer whose tag has bit 48 or 49 set creates a TLB entry under a different VPN than the untagged address of the same page. An `SFENCE.VMA` with the page's (untagged) virtual address does not remove it, so after software changes the PTE and fences the page, tagged accesses keep using the old translation and permissions:

- a store through a tagged pointer still writes a page that was made read-only;
- a load through a tagged pointer still reads a page whose PTE was made invalid.

The effective address of these accesses is the masked one, and after the fence the access must use the new PTE. Spike raises the page faults in all cases below.

Whole-core results (`DefaultConfig`, 1 core), standalone test `pm_tlb_alias.S` (log `WHOLECORE-ORIG-vs-FIX.log` in the gist below):

| VARIANT | sequence | expected | original | with the fix below |
|---|---|---|---|---|
| 0 | `sd` via tagged ptr; PTE := read-only; `sfence.vma va, x0`; `sd` via tagged ptr | store page fault | **FAIL**: no fault, the read-only page is written (`tohost = 0xb`) | PASS |
| 1 | as 0, last `sd` via untagged ptr | store page fault | PASS | PASS |
| 2 | as 0, `sfence.vma x0, x0` | store page fault | PASS | PASS |
| 3 | as 0, first `sd` via untagged ptr | store page fault | PASS | PASS |
| 4 | `ld` via tagged ptr; PTE := invalid (V=0); `sfence.vma va, x0`; `ld` via tagged ptr | load page fault | **FAIL**: no fault, data returned from the unmapped page (`tohost = 0x13`) | PASS |

Variants 1-3 show that the stale entry is the one filled through the tagged pointer and that only the address-specific fence misses it. Tags whose bits 49:48 are zero (for example `0x1234 << 48`) do not show the problem.

The same defect was also seen in randomized differential testing against Spike under the hypervisor profile: programs using tagged LR/SC pointers looped on spurious guest page faults (the core kept committing but never finished). With the fix below they match Spike.

### Expected behavior

The TLB is looked up, refilled and fenced with the pointer-masked address, so a tagged and an untagged pointer to the same page share one entry and `SFENCE.VMA` with the page's virtual address removes it. In variants 0 and 4 the second access raises the page fault.

### Environment

- Hardware: Intel Xeon Platinum 8163 @ 2.50GHz (96 threads), 629 GB memory
- Software: Ubuntu 22.04.5 LTS; gcc 11.4.0 (host), riscv64-unknown-elf-gcc 10.2 (test program); OpenJDK 21.0.12.1; Chisel 7.13.0, firtool 1.149.0 (sources compiled with scalac using the classpath of the project's mill build; mill not used directly); Verilator 5.052
- XiangShan: original model built from `e716c765`, fixed model from `8b15f06520bc7fc264095b11ece1d0e268153de7` plus the change below. The commits between them change docs and CI only. The current HEAD `4f6da52143b64b7a5a5419d309c79c4bf5e3794e` additionally changes only `NewStoreQueue.scala`, `LoadQueueRAR.scala` and `LoadQueueUncache.scala` (#6770, #6773, #6822); `TLB.scala` is unchanged and L658 is still present.
- Simulator: whole-core `XSTop` built with Verilator and our own AXI memory testbench; it detects the end of the test from the committed store to `tohost`. This is not the upstream `emu` + difftest flow.

### To Reproduce

`pm_tlb_alias.S`, `link.ld` and the whole-core log `WHOLECORE-ORIG-vs-FIX.log`: https://gist.github.com/ttkxclr/3c71487a59f91505fb9c7c2c471370e9

S-mode, Sv39, `menvcfg.PMM = 3` (PMLEN=16); the tagged pointer is the page address with bit 48 set. Self-contained (own page tables, no crt).

```
riscv64-unknown-elf-gcc -march=rv64gc -mabi=lp64d -mno-relax -nostdlib -nostartfiles -static -T link.ld -DVARIANT=<n> pm_tlb_alias.S -o pm_tlb_alias.elf
riscv64-unknown-elf-objcopy -O binary pm_tlb_alias.elf pm_tlb_alias.bin
```

The program writes `tohost = (a0 << 1) | 1`; `a0 = 0` is a pass, bit 0 = expected fault not taken, bit 2 = the read-only page was written, bit 3 = the load returned data.

Fix used for the last column: look up the TLB with the masked VPN, and keep `req_out.vaddr` (PTW request VPN, refill hit check, need_gpa tracking) consistent with it. Changing only the lookup key is not enough: the PTW request and the refilled entry would still use the unmasked VPN, so a tagged access then misses forever.

```diff
--- a/src/main/scala/xiangshan/cache/mmu/TLB.scala
+++ b/src/main/scala/xiangshan/cache/mmu/TLB.scala
@@ class TLB
     when (req(i).fire) {
       req_out(i) := req(i).bits
       req_out(i).fullva := preCheck(i).effectiveVa
+      req_out(i).vaddr := Cat(preCheck(i).query.vpn, get_off(req(i).bits.vaddr))
     }
@@ class TlbRequestPrepare
-  io.resp.query.vpn := get_pn(req.vaddr)
+  val maskedVaddr = Wire(UInt(VAddrBits.W))
+  maskedVaddr := req.vaddr
+  when (pmm === PMLEN16) {
+    maskedVaddr := Mux(prevmEnable || (pres2xlateEnable && csr.vsatp.mode =/= 0.U),
+      SignExt(req.vaddr(47, 0), VAddrBits), ZeroExt(req.vaddr(47, 0), VAddrBits))
+  }
+  io.resp.query.vpn := get_pn(maskedVaddr)
```

(The `pmm` value is computed after L658 in the current file, so the new lines go after the `pmm` selection.) The iTLB is unaffected (its `pmm` is 0). With this change, 24 previously passing random programs (including PMLEN=16 hypervisor and page-crossing ones) still match Spike.

### Additional context

- Related: #6215 / #6309 (see the first paragraph); #6972 (pointer masking not applied to load/store address triggers, a different path).
- We tested Sv39 S-mode with `menvcfg.PMM = 3` directly; the hypervisor cases were covered only by the random tests above.
- This report was prepared with AI assistance (opencode, Claude Opus 5.5).
