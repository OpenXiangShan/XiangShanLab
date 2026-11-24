### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and [XiangShan Documents](https://docs.xiangshan.cc/zh-cn/latest), and I believe this is a XiangShan RTL issue. 我已经阅读过 [RISC-V 指令集手册](https://github.com/riscv/riscv-isa-manual) 和 [香山文档](https://docs.xiangshan.cc/zh-cn/latest)，确认这应该是香山 RTL 的问题。
- [x] I have searched previous issues and PRs and did not find anything relevant. 我已经搜索过之前的 issue 和 PR，并没有找到相关的。
- [x] I have reproduced the issue using the latest commit on the default branch. 我已经使用默认分支最新的 commit 复现了问题。
- [x] If this report was generated with AI assistance (otherwise leave unchecked), I have verified the correctness of its content. 如果这是由 AI 生成的（否则请不要勾选），我已经验证了内容的正确性。

### Branch

kunminghu-v3

### Describe the bug


A short M-mode program made of Zicbop hints stops the emulator with an RTL
assertion in the ICache meta array. All of its prefetches are hints and must
not change architectural state:

```asm
  li      s0, 0x80800040
  li      t0, 0x1122334455667788
  sd      t0, 0(s0)
  prefetch.w 0(s0)
  prefetch.r 0(s0)
  ld      s1, 0(s0)
  ...
  li      s0, 0x80800000
  prefetch.i 0(s0)
  prefetch.r 0(s0)
  prefetch.w 0(s0)
  prefetch.i 64(s0)
  prefetch.r 64(s0)
  prefetch.w 64(s0)
  ...
  li      s0, 0x90000000
  prefetch.i 0(s0)
  prefetch.r 0(s0)
  prefetch.w 0(s0)
  prefetch.i 128(s0)
  prefetch.r 128(s0)
  prefetch.w 128(s0)
  nop
  j       test_exit
```

```
Assertion failed at .../build/rtl/ICacheMetaArray.sv:209.
The simulation stopped. There might be some assertion failed.
Core 0: ABORT at pc = 0x80002040
Core-0 instrCnt = 47, cycleCnt = 8,333, IPC = 0.005640
Assertion failed: 2 read setIdx must be in the different interleaved bank!
```

No difftest mismatch comes before it; the emulator reports pc `0x80002040`
(the `ld`), so the prefetches after it are still in flight.
Without difftest (`--no-diff`) the run stops at the same assertion in the same
cycle.

The failure depends on timing: the same image with every `prefetch.i` replaced
by a `nop` (same code layout) runs to its end, and so does the image with every
`prefetch.r` / `prefetch.w` replaced by a `nop`, or a few `prefetch.i` alone.
The attached image fails the same way on every run we made, with and without
difftest.

### Expected behavior

The hints retire without effect and the program reaches `test_exit`, as on
NEMU and Spike.

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
  - Run command: `./build/emu -i xs_prefetch_i_prefix.bin --diff ready-to-run/riscv64-nemu-interpreter-so -I 2000`

### To Reproduce


1. Build a difftest emu at kunminghu-v3 (`DefaultConfig`, assertions enabled).
2. Run the attached image:
   ```
   ./build/emu -i xs_prefetch_i_prefix.bin --diff ready-to-run/riscv64-nemu-interpreter-so -I 2000
   ```
3. Observe the assertion at `cycleCnt = 8,333`. Without a reference model
   (`--no-diff -I 2000`) the run stops at the same assertion; when it does not
   fire, the image spins in `test_exit`.

[xs_prefetch_i_prefix.bin.zip](https://github.com/user-attachments/files/33040362/xs_prefetch_i_prefix.bin.zip)

### Additional context

- kunminghu-v2 (`e7bab53e66`), which has no 2-prefetch, runs the image to `test_exit`.
  On kunminghu-v3 `4cf8b8d4ae` (2026-07-24) the image already stops earlier, at its
  first `prefetch.i`, in the no-progress hang of #5960 (fixed by #6230), so that
  commit says nothing about this assertion.
