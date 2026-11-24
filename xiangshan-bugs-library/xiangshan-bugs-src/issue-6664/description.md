### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and [XiangShan Documents](https://docs.xiangshan.cc/zh-cn/latest), and I believe this is a XiangShan RTL issue. 我已经阅读过 [RISC-V 指令集手册](https://github.com/riscv/riscv-isa-manual) 和 [香山文档](https://docs.xiangshan.cc/zh-cn/latest)，确认这应该是香山 RTL 的问题。
- [x] I have searched previous issues and PRs and did not find anything relevant. 我已经搜索过之前的 issue 和 PR，并没有找到相关的。
- [x] I have reproduced the issue using the latest commit on the default branch. 我已经使用默认分支最新的 commit 复现了问题。
- [x] If this report was generated with AI assistance (otherwise leave unchecked), I have verified the correctness of its content. 如果这是由 AI 生成的（否则请不要勾选），我已经验证了内容的正确性。

### Branch

kunminghu-v3

### Describe the bug

`fcvt.h.s` of a single-precision value whose half-precision result is an **exactly representable subnormal** returns a signed zero and raises UF and NX. The correct result is the subnormal itself with no flag raised: the conversion is exact, so it is neither inexact nor an underflow.

```asm
  li      t0, 0x6000
  csrs    mstatus, t0             # FS = Dirty
  csrw    fcsr, x0                # RNE, flags clear
  li      t1, 0xb4000000          # -2^-23 as a single
  fmv.w.x ft6, t1
  fcvt.h.s fa4, ft6               # -2^-23 = 2 x 2^-24: the half subnormal 0x8002, exactly
  fmv.x.h a0, fa4
  frflags a1
```

difftest, at the `fcvt.h.s` (left = NEMU, right = XiangShan):

```
    fa4 different at pc = ..., right = 0xffffffffffff8002, wrong = 0xffffffffffff8000
   fcsr different at pc = ..., right = 0x0000000000000000, wrong = 0x0000000000000003
```

A second, self-checking image (`xs_fcvt_h_scope`, run without difftest so that
no reference model is involved) shows which narrowing conversions are affected:

| case | input | expected half, flags | XiangShan |
|---|---|---|---|
| `fcvt.h.s` -2^-23 | `0xb4000000` | `0x8002`, none | **wrong** |
| `fcvt.h.s` +2^-23 | `0x34000000` | `0x0002`, none | **wrong** |
| `fcvt.h.s` +2^-24 (smallest subnormal) | `0x33800000` | `0x0001`, none | **wrong** |
| `fcvt.h.s` +2^-15 | `0x38000000` | `0x0200`, none | **wrong** |
| `fcvt.h.s` +3 x 2^-24 | `0x34400000` | `0x0003`, none | **wrong** |
| `fcvt.h.s` +2^-14 (smallest normal) | `0x38800000` | `0x0400`, none | correct |
| `fcvt.h.d` -2^-23 | `0xbe80000000000000` | `0x8002`, none | correct |
| `fcvt.h.s` +2^-25 (a tie, rounds to 0) | `0x33000000` | `0x0000`, UF\|NX | correct |

So every exact-subnormal result of `fcvt.h.s` is wrong, while the smallest normal, a conversion that rounds to zero and the double-to-half conversion are right.


### Expected behavior

`fcvt.h.s` should return the exact subnormal (`0x8002` above, NaN-boxed) and leave `fflags` unchanged, as NEMU and Spike do.

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
  - Run command: `./build/emu -i xs_fcvt_h_s_subnormal.bin --diff ready-to-run/riscv64-nemu-interpreter-so -I 3000`

### To Reproduce

1. Build a difftest emu at kunminghu-v3 (`DefaultConfig`).
2. Run the attached image:
   ```
   ./build/emu -i xs_fcvt_h_s_subnormal.bin --diff ready-to-run/riscv64-nemu-interpreter-so -I 3000
   ```
3. Observe the `fa4` / `fcsr` mismatch at `instrCnt = 45` (the first 32
   instructions are start-up code that clears x1-x31).
4. Optionally run the self-checking image without a reference:
   ```
   ./build/emu -i xs_fcvt_h_scope.bin --no-diff -I 3000
   ```
   It ends spinning at `spin + 4*s0`, where bit *i* of `s0` is set when case
   *i* of the table is wrong. XiangShan stops at `0x800022bc` = `spin + 4*0x4f`
   (cases 0, 1, 2, 3 and 6); a correct core stops at `spin` = `0x80002180`.

[Archive.zip](https://github.com/user-attachments/files/32987400/Archive.zip)

### Additional context

- The same images run correctly on **kunminghu-v2** (`e7bab53e66`): no mismatch, and the scope test stops at `spin`.
- On kunminghu-v3 the reproducer is clean at `4cf8b8d4ae` (2026-07-24) and fails at `e43c569f85` (2026-09-09), `e4566c29e2` (2026-09-17), `20fd0d6097` (2026-09-22) and the latest commit, so the behaviour appeared between those two dates.
