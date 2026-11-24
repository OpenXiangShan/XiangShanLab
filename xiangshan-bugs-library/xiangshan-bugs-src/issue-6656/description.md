### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and [XiangShan Documents](https://docs.xiangshan.cc/zh-cn/latest), and I believe this is a XiangShan RTL issue. 我已经阅读过 [RISC-V 指令集手册](https://github.com/riscv/riscv-isa-manual) 和 [香山文档](https://docs.xiangshan.cc/zh-cn/latest)，确认这应该是香山 RTL 的问题。
- [x] I have searched previous issues and PRs and did not find anything relevant. 我已经搜索过之前的 issue 和 PR，并没有找到相关的。
- [x] I have reproduced the issue using the latest commit on the default branch. 我已经使用默认分支最新的 commit 复现了问题。
- [x] If this report was generated with AI assistance (otherwise leave unchecked), I have verified the correctness of its content. 如果这是由 AI 生成的（否则请不要勾选），我已经验证了内容的正确性。

### Branch

kunminghu-v3

### Describe the bug

When a vector integer divide is issued on the wrong path of a mispredicted branch and then squashed, a later divide on the correct path does not complete. With `--no-diff` the core stops committing and the emu ends with `EXCEEDING CYCLE/INSTR LIMIT`. With NEMU difftest the emu aborts at the later divide with a garbage value in the destination register.

Minimal sequence (VLEN=128, e8/m1, all divisors nonzero, workload `t44_vdiv_flush`).

```asm
vsetivli x0, 16, e8, m1, ta, ma
vle8.v   v1, (a1)            # dividends, all 100
vle8.v   v2, (a2)            # divisors 5..33
ld       t0, flag            # flag = 0
beqz     t0, 1f              # taken, but predicted not-taken, and the shadow executes
vdivu.vv v4, v1, v2          # wrong path, squashed by the redirect
vdivu.vv v5, v2, v1
1:
vdivu.vv v3, v1, v2          # correct path: never writes back
vse8.v   v3, (a3)
```

Difftest output, the same on MinimalConfig and DefaultConfig.

```
v3_low  different at pc = 0x0080000048, right = 0x05060608080b1014, wrong = 0x0105cf67c317d6e5
v3_high different at pc = 0x0080000048, right = 0x0303030404040405, wrong = 0xc638ed3eb6b2cc2d
Core 0: ABORT
```

Without difftest the run ends with the cycle limit.

```
Core 0: EXCEEDING CYCLE/INSTR LIMIT at pc = 0x80000048
```

The same binary runs to completion on `kunminghu-v2` (`e7bab53e6`) and on Spike, both giving `14 10 0b 08 08 06 06 05 05 04 04 04 04 03 03 03`.


### Expected behavior

The squashed divides should have no architectural effect.

### Environment

- Hardware
  - CPU: AMD EPYC 9355 32-Core Processor
  - Memory (GB): 245
  - Storage (GB): 877
- Software
  - Operating system: Ubuntu 25.04
  - gcc version: gcc (Ubuntu 14.2.0-19ubuntu2) 14.2.0
  - clang version: not used
  - java version: openjdk version "21.0.12.1" 2026-08-18 LTS (Temurin)
  - mill version: Mill Build Tool version 0.12.17
  - verilator version: Verilator 5.032 2025-01-01 rev (Debian 5.032-1)
- Repo
  - XiangShan commit id: `09453ff8f44e9d2b526e6149c60e1dc3f08c7192`
  - NEMU commit id (if difftest failed with NEMU): `ready-to-run/riscv64-nemu-interpreter-so` from ready-to-run `4cf9983569432035d41956c65e6a58724b5954be`
  - SPIKE commit id (if difftest failed with SPIKE): N/A
- Build & Run
  - Build command: `make emu CONFIG=MinimalConfig EMU_THREADS=8 -j60 OPT_FAST=-O3` (also `CONFIG=DefaultConfig`. `OPT_FAST=-O3` removes the clang-only `-fno-slp-vectorize` flag, which gcc does not accept)
  - Run command: `./build/emu -i t44_vdiv_flush.bin --diff ./ready-to-run/riscv64-nemu-interpreter-so -C 200000`
  - Workload: attached (`workload/`)


### To Reproduce

1. Build the workload with any RV64GCV bare-metal toolchain (`t44_vdiv_flush.S` includes `common_xs.S`, linked with `link.ld`). Prebuilt `.bin`/`.elf` files are attached.
2. Run with NEMU difftest. It aborts at the divide on `v3`.
3. Run with `--no-diff`. The program never reaches its result print and the cycle limit fires.

The branch has to be mispredicted. With `flag` set so that the branch falls through, or with the shadow divides removed, the test passes.

## Environment

- Hardware
  - CPU: AMD EPYC 9355 32-Core Processor
  - Memory (GB): 245
  - Storage (GB): 877
- Software
  - Operating system: Ubuntu 25.04
  - gcc version: gcc (Ubuntu 14.2.0-19ubuntu2) 14.2.0
  - clang version: not used
  - java version: openjdk version "21.0.12.1" 2026-08-18 LTS (Temurin)
  - mill version: Mill Build Tool version 0.12.17
  - verilator version: Verilator 5.032 2025-01-01 rev (Debian 5.032-1)
- Repo
  - XiangShan commit id: `09453ff8f44e9d2b526e6149c60e1dc3f08c7192`
  - NEMU commit id (if difftest failed with NEMU): `ready-to-run/riscv64-nemu-interpreter-so` from ready-to-run `4cf9983569432035d41956c65e6a58724b5954be`
  - SPIKE commit id (if difftest failed with SPIKE): N/A
- Build & Run
  - Build command: `make emu CONFIG=MinimalConfig EMU_THREADS=8 -j60 OPT_FAST=-O3` (also `CONFIG=DefaultConfig`. `OPT_FAST=-O3` removes the clang-only `-fno-slp-vectorize` flag, which gcc does not accept)
  - Run command: `./build/emu -i t44_vdiv_flush.bin --diff ./ready-to-run/riscv64-nemu-interpreter-so -C 200000`
  - Workload: attached (`workload/`)

### Additional context

[xs_vdiv.zip](https://github.com/user-attachments/files/32839124/xs_vdiv.zip)
