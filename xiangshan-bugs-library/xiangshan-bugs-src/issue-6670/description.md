### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and [XiangShan Documents](https://docs.xiangshan.cc/zh-cn/latest), and I believe this is a XiangShan RTL issue. 我已经阅读过 [RISC-V 指令集手册](https://github.com/riscv/riscv-isa-manual) 和 [香山文档](https://docs.xiangshan.cc/zh-cn/latest)，确认这应该是香山 RTL 的问题。
- [x] I have searched previous issues and PRs and did not find anything relevant. 我已经搜索过之前的 issue 和 PR，并没有找到相关的。
- [x] I have reproduced the issue using the latest commit on the default branch. 我已经使用默认分支最新的 commit 复现了问题。
- [x] If this report was generated with AI assistance (otherwise leave unchecked), I have verified the correctness of its content. 如果这是由 AI 生成的（否则请不要勾选），我已经验证了内容的正确性。

### Branch

kunminghu-v2

### Describe the bug


When a leaf PTE's PPN has bits set above XiangShan's physical address width
(here PTE bit 53, i.e. PPN bit 43) **and** the access is not permitted by that
PTE, XiangShan raises an access fault. The translation algorithm checks the
permissions first (U/SUM/MXR in step 6, R/W/X in step 8 of "Virtual Address
Translation Process") and stops with a page fault there; the physical address is
only formed, and checked, once "the translation is successful". The access fault
for the non-existent physical address is therefore only due when the
permissions allow the access.

All four cases of the attached image are S-mode accesses (Sv39, `sstatus.SUM` = 0)
through 4 KiB leaves whose PPN has bit 43 set; each is preceded by `sfence.vma`:

| case | leaf PTE | access | expected (Spike, NEMU) | XiangShan |
|---|---|---|---|---|
| s3 | D A R V | `sd` | 15 store/AMO page fault | 7 store/AMO access fault |
| s4 | D A U X W R V | `ld` | 13 load page fault | 5 load access fault |
| s5 | D A W R V (no X) | `jalr` to it | 12 instruction page fault | 1 instruction access fault |
| s6 | D A X W R V | `ld` | 5 load access fault | 5 load access fault |

difftest stops at the first case (`sd`); the causes of s4 and s5 come from two
variants of the image without the earlier cases (`xs_ppn_high_perm_s4`, `_s5`, attached):

```
[31] exception pc 00000000800020ea inst 00e73423 cause 0000000000000007 <--
 mcause different at pc = ..., right = 0x000000000000000f, wrong = 0x0000000000000007
```

Without difftest the image records the four causes and ends in `res_N`, where
bits 0-3 of N say whether s3, s4, s5, s6 matched; XiangShan ends in `res_8`
(`0x800021c0`): only the control case s6 is right. The same happens at the G
stage: in the program this was reduced from, a VS-mode store through a G-stage
leaf with PPN high bits and no write permission reports store access fault (7)
where Spike and NEMU raise store guest-page fault (23, with `mtval2` set).

### Expected behavior

Store/load/instruction page fault (15 / 13 / 12) for s3 / s4 / s5, as Spike and
NEMU raise; load access fault only for s6.

### Environment

- Software
  - Operating system: Ubuntu 24.04.5 LTS
  - gcc version: gcc (Ubuntu 13.3.0-6ubuntu2~24.04.1) 13.3.0 (emu build)
  - riscv toolchain: riscv-none-elf-gcc (xPack) 15.2.0 (reproducer build)
  - java version: openjdk 17.0.20.1
  - mill version: 0.12.17
  - verilator version: Verilator 5.052 2026-09-05
- Repo
  - XiangShan commit id: `09453ff8f44e9d2b526e6149c60e1dc3f08c7192` (kunminghu-v3, **the latest default-branch commit**)
  - NEMU commit id: the `ready-to-run` interpreter of that commit (`4cf9983`)
  - difftest submodule: `4c64fe86b17e0564232b52bb3800d9b15c45a3a0`
- Build & Run
  - Build command: `make emu CONFIG=DefaultConfig OPT_FAST="-O3 -fno-tree-slp-vectorize"` (g++ does not accept difftest's default `-fno-slp-vectorize`)
  - Run command: `./build/emu -i xs_ppn_high_perm.bin --diff ready-to-run/riscv64-nemu-interpreter-so -I 1500`

### To Reproduce

1. Build a difftest emu at kunminghu-v3 (`DefaultConfig`).
2. Run the attached image:
   ```
   ./build/emu -i xs_ppn_high_perm.bin --diff ready-to-run/riscv64-nemu-interpreter-so -I 1500
   ```
3. Observe the `mcause` mismatch (15 expected, 7 on the DUT) at the first case.
4. Optionally without a reference model:
   ```
   ./build/emu -i xs_ppn_high_perm.bin --no-diff -I 1500
   ```
   It spins in `test_exit` when all four cases match and in `res_N` otherwise
   (`res_0` = `0x800021a0`, 4 bytes per N); XiangShan stops in `res_8`.

[xs_ppn_high_perm.bin.zip](https://github.com/user-attachments/files/33040165/xs_ppn_high_perm.bin.zip)

[xs_ppn.zip](https://github.com/user-attachments/files/33040196/xs_ppn.zip)

### Additional context

### Possible cause

The page-table walker turns non-zero high PPN bits into an access fault
(`ppn_af`, `PteBundle.isAf()`, "access fault will be raised when ppn >> ppnLen
is not zero"; `L2TLB.scala` `contiguous_pte_to_merge_ptwResp`:
`isAf = pte_in.isAf() && ... && !isPf` with `isPf` covering only V/R/W/reserved
encodings). The permission checks (U/SUM/MXR, R/W/X) are made later, in the L1
TLB, and an entry that comes back with `af` set raises the access fault without
them. The priority comment of #3453 ("accessFault > pageFault > ppn_af") is
right for the PTE-level checks, but the permission page faults are not part of
it. This is our reading of the source; we have not verified it by changing the RTL.
