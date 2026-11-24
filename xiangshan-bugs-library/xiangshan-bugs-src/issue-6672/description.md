### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and [XiangShan Documents](https://docs.xiangshan.cc/zh-cn/latest), and I believe this is a XiangShan RTL issue. 我已经阅读过 [RISC-V 指令集手册](https://github.com/riscv/riscv-isa-manual) 和 [香山文档](https://docs.xiangshan.cc/zh-cn/latest)，确认这应该是香山 RTL 的问题。
- [x] I have searched previous issues and PRs and did not find anything relevant. 我已经搜索过之前的 issue 和 PR，并没有找到相关的。
- [x] I have reproduced the issue using the latest commit on the default branch. 我已经使用默认分支最新的 commit 复现了问题。
- [x] If this report was generated with AI assistance (otherwise leave unchecked), I have verified the correctness of its content. 如果这是由 AI 生成的（否则请不要勾选），我已经验证了内容的正确性。

### Branch

kunminghu-v3

### Describe the bug


The reads of VS-level page tables are implicit accesses translated by the G
stage, and their permissions "are checked as though for an implicit load or
store" (hypervisor chapter, G-stage translation). MXR is the bit "which makes
execute-only pages readable by explicit loads" (same chapter); Spike and NEMU
do not apply it to the implicit reads, and #6099 ("fix(RVH): ignore MXR for
implicit G-stage reads": "the RISC-V manual limits MXR to explicit memory
accesses") made XiangShan do the same, but it was merged into kunminghu-v2 only; on
kunminghu-v3 a VS-mode load whose VS-stage walk has to read a page that the G
stage maps execute-only completes when `mstatus.MXR` = 1.

Setup of the attached image (M-mode, then VS-mode):

```
G stage (Sv39x4): identity, U|X|W|R leaves, except the page `vsl1` (0x8000b000): U|X only
VS stage (Sv39):  vsroot[2] = 1 GiB leaf for the code; vsroot[6] -> vsl1; vsl1[0] = 2 MiB leaf
                  VA 0x180000000 -> GPA 0x80000000
test 1: mstatus.MXR = 0, in VS-mode  ld a0, 0(0x180000000)   -> load guest-page fault (21)
test 2: mstatus.MXR = 1, same load                            -> load guest-page fault (21) expected
```

Test 1 behaves; in test 2 XiangShan completes the load. difftest (left = NEMU,
right = XiangShan):

```
[31] commit pc 00000000800021cc inst 0005b503 wen 1 dst 10 data 4201418141014081 idx 0ff (01) <--
     a0 different at pc = 0x00800020a4, right = 0x0000000080004000, wrong = 0x4201418141014081
   mode different at pc = 0x00800020a4, right = 0x0000000000000003, wrong = 0x0000000000000001
mstatus different at pc = 0x00800020a4, right = 0x000004ca00080880, wrong = 0x0000000a00080088
   mepc different at pc = 0x00800020a4, right = 0x00000000800021cc, wrong = 0x00000000800021bc
  mtval different at pc = 0x00800020a4, right = 0x0000000180000000, wrong = 0x0000000000000000
 mcause different at pc = 0x00800020a4, right = 0x0000000000000015, wrong = 0x000000000000000a
 mtval2 different at pc = 0x00800020a4, right = 0x0000000020002c00, wrong = 0x0000000000000000
 mtinst different at pc = 0x00800020a4, right = 0x0000000000003000, wrong = 0x0000000000000000
```

NEMU (and Spike) take the load guest-page fault with `mtval2` = the GPA of
`vsl1` >> 2 and `mtinst` = 0x3000 (an implicit VS-stage page-table read); XiangShan
loads the data. Without difftest the self-checking image ends in `res_1` (`0x80002218`: test 1
right, test 2 wrong) instead of `test_exit`.

### Expected behavior

A load guest-page fault (cause 21) in both tests, as on Spike, NEMU and kunminghu-v2.


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
  - Run command: `./build/emu -i xs_mxr_implicit_gstage.bin --diff ready-to-run/riscv64-nemu-interpreter-so -I 7000`

### To Reproduce

1. Build a difftest emu at kunminghu-v3 (`DefaultConfig`).
2. Run the attached image:
   ```
   ./build/emu -i xs_mxr_implicit_gstage.bin --diff ready-to-run/riscv64-nemu-interpreter-so -I 7000
   ```
3. Observe the mismatch at `instrCnt = 4,842` (most instructions before it fill the page tables).
4. Optionally without a reference model (`--no-diff -I 7000`): the image spins in
   `test_exit` when both loads fault and in `res_N` otherwise (`res_0` =
   `0x80002214`, 4 bytes per N; bit 0 = test 1 right, bit 1 = test 2 right).

[xs_mxr_implicit_gstage.bin.zip](https://github.com/user-attachments/files/33040272/xs_mxr_implicit_gstage.bin.zip)

### Additional context

- kunminghu-v2 `e7bab53e66` (which contains #6099) runs the image to `test_exit` under difftest.
