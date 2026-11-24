### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and [XiangShan Documents](https://docs.xiangshan.cc/zh-cn/latest), and I believe this is a XiangShan RTL issue. 我已经阅读过 [RISC-V 指令集手册](https://github.com/riscv/riscv-isa-manual) 和 [香山文档](https://docs.xiangshan.cc/zh-cn/latest)，确认这应该是香山 RTL 的问题。
- [x] I have searched previous issues and PRs and did not find anything relevant. 我已经搜索过之前的 issue 和 PR，并没有找到相关的。
- [x] I have reproduced the issue using the latest commit on the default branch. 我已经使用默认分支最新的 commit 复现了问题。
- [x] If this report was generated with AI assistance (otherwise leave unchecked), I have verified the correctness of its content. 如果这是由 AI 生成的（否则请不要勾选），我已经验证了内容的正确性。

### Branch

kunminghu-v3

### Describe the bug

XiangShan implements Smrnmi: `mnscratch`, `mnepc`, `mncause` and `mnstatus` are
present and writable, and the CSR unit has an MNRET path (`mnretEvent`,
`hasLegalMNret`). On kunminghu-v3, however, `MNRET` (`0x70200073`) executed in
M-mode takes an illegal-instruction exception instead of returning to `mnepc`.

```asm
  li      t0, 0x1808              # mnstatus: MNPP = M, NMIE = 1
  csrw    mnstatus, t0            # 0x744
  csrr    a0, mnstatus            # reads 0x1808
  la      t0, back
  csrw    mnepc, t0               # 0x741
  .word   0x70200073              # mnret: must continue at `back`, in M-mode
  j       trapped
back:
  ...
```

difftest, at the `mnret` (pc `0x8000202a`):

```
[29] commit pc 0000000080002022 inst 74129073 wen 0 dst 00 data 0000000080002030 idx 02e
[30] commit pc 0000000080002026 inst 741026f3 wen 1 dst 13 data 0000000080002030 idx 02f
[31] exception pc 000000008000202a inst 70200073 cause 0000000000000002 <--
...
mstatus different at pc = 0x0080002040, right = 0x0000000a00000000, wrong = 0x0000040a00001800
   mepc different at pc = 0x0080002040, right = 0x0000000000000000, wrong = 0x000000008000202a
  mtval different at pc = 0x0080002040, right = 0x0000000000000000, wrong = 0x0000000070200073
 mcause different at pc = 0x0080002040, right = 0x0000000000000000, wrong = 0x0000000000000002
```

The `mnstatus` and `mnepc` accesses before it commit with the same values on
both sides; the reference executes the `MNRET`, XiangShan raises
illegal-instruction with `mtval` = the encoding. Without difftest
(`--no-diff`) the self-checking image ends at its `trapped` label
(`0x80002040`) instead of `test_exit`.

### Expected behavior

`MNRET` in M-mode sets the pc to `mnepc`, the privilege mode to `mnstatus.MNPP`
(M here) and `mnstatus.NMIE` to 1, as NEMU, kunminghu-v2 and Spike with Smrnmi
(`--isa=..._smrnmi`) do; the program then reaches `back`.

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
  - Run command: `./build/emu -i xs_mnret_m.bin --diff ready-to-run/riscv64-nemu-interpreter-so -I 600`

### To Reproduce

1. Build a difftest emu at kunminghu-v3 (`DefaultConfig`).
2. Run the attached image:
   ```
   ./build/emu -i xs_mnret_m.bin --diff ready-to-run/riscv64-nemu-interpreter-so -I 600
   ```
3. Observe the illegal-instruction exception at the `mnret` and the mismatch at
   `instrCnt = 49` (the first 32 instructions are start-up code that clears x1-x31).
4. Optionally without a reference model (`--no-diff -I 600`): the image spins at
   `trapped` (`0x80002040`) when the `MNRET` traps and in `test_exit` when it
   returns to `back`.

[xs_mnret_m.bin.zip](https://github.com/user-attachments/files/33040216/xs_mnret_m.bin.zip)

### Additional context

- On kunminghu-v2 (`e7bab53e66`) the image runs to its end, and on kunminghu-v3
  `4cf8b8d4ae` (2026-07-24) the `MNRET` returns to `back` as well; it raises
  illegal-instruction on `e43c569f85` (2026-09-09) and on the latest commit. The
  new decoder came in with `29f48d8a6` ("feat(vector): new vector initial
  commit", 2026-08-06), in between.
