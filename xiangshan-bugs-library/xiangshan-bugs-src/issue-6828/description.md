### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and [XiangShan Documents](https://docs.xiangshan.cc/zh-cn/latest), and I believe this is a XiangShan RTL issue. 我已经阅读过 [RISC-V 指令集手册](https://github.com/riscv/riscv-isa-manual) 和 [香山文档](https://docs.xiangshan.cc/zh-cn/latest)，确认这应该是香山 RTL 的问题。
- [x] I have searched previous issues and PRs and did not find anything relevant. 我已经搜索过之前的 issue 和 PR，并没有找到相关的。
- [x] I have reproduced the issue using the latest commit on the default branch. 我已经使用默认分支最新的 commit 复现了问题。
- [x] If this report was generated with AI assistance (otherwise leave unchecked), I have verified the correctness of its content. 如果这是由 AI 生成的（否则请不要勾选），我已经验证了内容的正确性。

### Branch

kunminghu-v3

### Describe the bug

With `mmpt.MODE` = Smmpt43 and the effective privilege mode not M, the XWR tuple returned by the MPT lookup should decide whether an access is allowed, regardless of `satp`, `vsatp` or `hgatp`.

Correct behavior with `satp` = Sv39:

- Case 1: a store to a page whose leaf MPTE is XWR = 001 (read-only page) raises a store/AMO access-fault exception (cause 7);
- Case 2: an instruction fetch from a page whose leaf MPTE is XWR = 011 (read-write page, X = 0) raises an instruction access-fault exception (cause 1).

XiangShan behavior with `satp` = Sv39:

- Case 1: the store completes and writes the page;
- Case 2: the instruction fetch executes.

XiangShan enforces the W/X tuple on the Bare path. The bug is that `mptCheckOnly` is false as soon as `satp`, `vsatp` or `hgatp` is not `Bare`, so the same check is disabled for translated accesses.

### Root cause

`TLB.scala` computes `mptCheckOnly` as `BareMode && ...`, and `mptValid` is `mptCheckOnly`:

https://github.com/OpenXiangShan/XiangShan/blob/d136732cf9fabcd883d24454d4223ecf47d81c88/src/main/scala/xiangshan/cache/mmu/TLB.scala#L145-L148


The store, load and fetch deny terms are ANDed with `mptValid`:

https://github.com/OpenXiangShan/XiangShan/blob/d136732cf9fabcd883d24454d4223ecf47d81c88/src/main/scala/xiangshan/cache/mmu/TLB.scala#L477-L481

https://github.com/OpenXiangShan/XiangShan/blob/d136732cf9fabcd883d24454d4223ecf47d81c88/src/main/scala/xiangshan/cache/mmu/TLB.scala#L542-L548


With paging enabled, `mptValid` is 0, so the W and X deny rules are disabled. The page-table walker folds only the R bit into `mpt_af`:

https://github.com/OpenXiangShan/XiangShan/blob/d136732cf9fabcd883d24454d4223ecf47d81c88/src/main/scala/xiangshan/cache/mmu/PageTableWalker.scala#L157-L160

### Expected behavior

With `satp` = Sv39, case 1 should raise cause 7 and case 2 should raise cause 1.

### Observed

Case 1, Sv39 store to the XWR = 001 page. The reference model takes the access fault, and the DUT commits the store:

```
[src/isa/riscv64/system/mmu.c:1505,isa_mpt_check_permission] Permission check fail!
NEMU does not commit any store instruction.

==============  Store Commit Event (Core 0)  ==============
Mismatch for store commits
  REF commits addr 0x0000000080010000, data 0x1122334455667788, mask 0xff, pc 0x000000008000008c
  DUT commits addr 0x0000000080010000, data 0x1122334455667788, mask 0xff, pc 0x00000000800000e4, robidx 0x63
Core 0: ABORT at pc = 0x1154d196f0d15
```

Case 2, Sv39 fetch from the XWR = 011 page. The reference model takes the instruction access fault, and the DUT does not:

```
[src/isa/riscv64/system/mmu.c:1505,isa_mpt_check_permission] Permission check fail!
[src/memory/paddr.c:305,check_paddr] isa mpt check failed, vaddr=0x0000000040002000, paddr=0x0000000080018000, len=0x2, type=0x0, mode=0x1
...
   mepc different at pc = 0xfffeafb5bf994c51, right = 0x0000000040002000, wrong = 0x00000000800000f0
  mtval different at pc = 0xfffeafb5bf994c51, right = 0x0000000040002000, wrong = 0x0000000000000000
 mcause different at pc = 0xfffeafb5bf994c51, right = 0x0000000000000001, wrong = 0x0000000000000000
Core 0: ABORT at pc = 0xfffeafb5bf994c51
```

### Environment

- Repo: XiangShan commit `d136732cf9fabcd883d24454d4223ecf47d81c88`, branch `kunminghu-v3`
- DUT build: a configuration with `HasMptCheck = true` and `HasBitmapCheck = false`
- Reference model: NEMU `513786c4` built with `CONFIG_RV_MPT_CHECK=y`

### To Reproduce

1. Build the DUT with `HasMptCheck = true`, `HasBitmapCheck = false`, and NEMU with `CONFIG_RV_MPT_CHECK=y`.
2. Set up an Smmpt43 tree. The leaf MPTE covering PA `0x80010000` is `0x03ffdb6fffc92403`: subpages 0-3 are XWR = 001, subpages 4-7 are XWR = 111, subpages 8-11 are XWR = 011, subpages 12-15 are XWR = 111.
3. Set up Sv39 page tables at PA `0x80020000` with 4 KiB leaves and all PTEs carrying R/W/X/U/A/D:
   - VA `0x40000000` maps to PA `0x80010000`, whose MPT tuple is XWR = 001.
   - VA `0x40002000` maps to PA `0x80018000`, whose MPT tuple is XWR = 011.
4. Set `mmpt = 0x1000000000080025` (Smmpt43, root PPN `0x80025`) and `satp = 0x8000000000080020` (Sv39, root PPN `0x80020`).
5. Copy a stub to PA `0x80018000`. The stub sets a flag and executes `ecall`.
6. Enter S-mode and execute:
   ```asm
   li t0, 0x40000000
   li t1, 0x1122334455667788
   sd t1, 0(t0)      /* store to XWR = 001 */
   li t0, 0x40002000
   jr t0             /* fetch from XWR = 011 */
   ```

### Additional context

_No response_
