### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and [XiangShan Documents](https://docs.xiangshan.cc/zh-cn/latest), and I believe this is a XiangShan RTL issue. 我已经阅读过 [RISC-V 指令集手册](https://github.com/riscv/riscv-isa-manual) 和 [香山文档](https://docs.xiangshan.cc/zh-cn/latest)，确认这应该是香山 RTL 的问题。
- [x] I have searched previous issues and PRs and did not find anything relevant. 我已经搜索过之前的 issue 和 PR，并没有找到相关的。
- [x] I have reproduced the issue using the latest commit on the default branch. 我已经使用默认分支最新的 commit 复现了问题。
- [x] If this report was generated with AI assistance (otherwise leave unchecked), I have verified the correctness of its content. 如果这是由 AI 生成的（否则请不要勾选），我已经验证了内容的正确性。

### Branch

kunminghu-v3

### Describe the bug

After a half-precision divide whose result **underflows**, the destination
register does not hold a NaN-boxed half: its upper 48 bits read as zero instead
of all ones. `fmv.x.d` shows it directly, and a later half-precision operation
on the register treats the value as a NaN (Zfh: a narrower value that is not
properly NaN-boxed is treated as the canonical NaN) and raises NV.

```asm
  li      t0, 0x6000
  csrs    mstatus, t0             # FS = Dirty
  li      t0, 0x20                # frm = RTZ, flags clear
  csrw    fcsr, t0
  li      t0, 0x0001              # smallest half subnormal
  fmv.h.x fa1, t0
  li      t0, 0x7bff              # largest finite half
  fmv.h.x fa2, t0
  fdiv.h  fa0, fa1, fa2           # underflows to +0 with UF|NX: fa0 = 0xffffffffffff0000
  fmv.x.d a0, fa0                 # must read 0xffffffffffff0000
```

difftest, at the `fmv.x.d` (left = NEMU, right = XiangShan):

```
     a0 different at pc = ..., right = 0xffffffffffff0000, wrong = 0x0000000000000000
```

The flags are right (`fflags = 0x3`); only the boxing is lost.

A second, self-checking image (`xs_fdiv_h_underflow_selfcheck`, run without
difftest) shows the extent. Each case reads the result back with `fmv.x.d`:

| case | expected `fmv.x.d` | XiangShan |
|---|---|---|
| `fdiv.h` `0x0001 / 0x7bff`, RTZ (underflow) | `0xffffffffffff0000` | **wrong** |
| the same, with 16 `nop`s before the `fmv.x.d` | `0xffffffffffff0000` | **wrong** |
| the same, with RNE | `0xffffffffffff0000` | **wrong** |
| `fdiv.h` `0x3c00 / 0x4000` (1.0 / 2.0) | `0xffffffffffff3800` | correct |
| `fdiv.h` `0x0000 / 0x3c00` (exact zero) | `0xffffffffffff0000` | correct |
| `fsqrt.h` `0x0000` | `0xffffffffffff0000` | correct |
| `fdiv.s` `0x00000001 / 0x7f7fffff`, RTZ (single, underflow) | `0xffffffff00000000` | correct |
| `fdiv.h` underflow read with `fmv.x.h` | `0` | correct |

The case with 16 `nop`s shows that the un-boxed value is what is written to
the register file, not a forwarding artefact. An exact zero result is boxed
correctly, so the problem is specific to the underflow path, and to half
precision: the single-precision underflow is boxed.

### Expected behavior

`fdiv.h` should write the NaN-boxed half `0xffffffffffff0000`, as NEMU and
Spike do.

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
  - Run command: `./build/emu -i xs_fdiv_h_underflow_unboxed.bin --diff ready-to-run/riscv64-nemu-interpreter-so -I 3000`


### To Reproduce

1. Build a difftest emu at kunminghu-v3 (`DefaultConfig`).
2. Run the attached image:
   ```
   ./build/emu -i xs_fdiv_h_underflow_unboxed.bin --diff ready-to-run/riscv64-nemu-interpreter-so -I 3000
   ```
3. Observe the `a0` mismatch at `instrCnt = 49` (the first 32 instructions are
   start-up code that clears x1-x31).
4. Optionally run the self-checking image without a reference:
   ```
   ./build/emu -i xs_fdiv_h_underflow_selfcheck.bin --no-diff -I 3000
   ```
   It ends spinning at `spin + 4*s0`, one bit of `s0` per failing case.
   XiangShan stops at `0x8000223c` = `spin + 4*0x49` (cases 0, 3 and 6 of the
   source); a correct core stops at `spin` = `0x80002118`.

[fdiv_h.zip](https://github.com/user-attachments/files/32987450/fdiv_h.zip)

### Additional context

- The self-checking image passes on **kunminghu-v2** (`e7bab53e66`).
- On kunminghu-v3 the reproducer fails on every build tried: `f09207872d`
  (2026-05-29), `4cf8b8d4ae` (2026-07-24), `e43c569f85` (2026-09-09),
  `e4566c29e2` (2026-09-17), `20fd0d6097` (2026-09-22) and the latest commit.
