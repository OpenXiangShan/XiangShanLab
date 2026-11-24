### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and [XiangShan Documents](https://docs.xiangshan.cc/zh-cn/latest), and I believe this is a XiangShan RTL issue. 我已经阅读过 [RISC-V 指令集手册](https://github.com/riscv/riscv-isa-manual) 和 [香山文档](https://docs.xiangshan.cc/zh-cn/latest)，确认这应该是香山 RTL 的问题。
- [x] I have searched previous issues and PRs and did not find anything relevant. 我已经搜索过之前的 issue 和 PR，并没有找到相关的。
- [x] I have reproduced the issue using the latest commit on the default branch. 我已经使用默认分支最新的 commit 复现了问题。
- [x] If this report was generated with AI assistance (otherwise leave unchecked), I have verified the correctness of its content. 如果这是由 AI 生成的（否则请不要勾选），我已经验证了内容的正确性。

### Branch

kunminghu-v3

### Describe the bug

A plain `vslideup.vi` stops the core. Workloads `t13_vslideup_vset` and `t35_vslideup_gap64` (the second has 64 `nop`s between the `vsetivli` and the slide, with the same outcome).

```asm
vsetivli    x0, 16, e8, m1, ta, ma
vle8.v      v1, (a1)
vle8.v      v2, (a2)          # v2 = 01 02 .. 10
vle8.v      v3, (a0)          # v3 = 01 02 .. 10
vsetivli    x0, 16, e8, m1, ta, ma
vslideup.vi v3, v2, 4         # v3[4..15] = v2[0..11], v3[0..3] unchanged
vse8.v      v3, (a3)
```

With `--no-diff` the run ends with

```
Core 0: EXCEEDING CYCLE/INSTR LIMIT at pc = 0x80000048
Core-0 instrCnt = 25, cycleCnt = 200,000
```

The count of 25 is the position of the `vslideup.vi`. Nothing commits after it and no exception is raised. With NEMU difftest the emu aborts at that instruction with the destination unchanged.

```
v3_low  different at pc = ..., right = 0x0403020104030201, wrong = 0x0807060504030201
v3_high different at pc = ..., right = 0x0c0b0a0908070605, wrong = 0x100f0e0d0c0b0a09
Core 0: ABORT
```

Same on MinimalConfig and DefaultConfig. kunminghu-v2 (`e7bab53e6`) and Spike produce `01 02 03 04 01 02 03 04 05 06 07 08 09 0a 0b 0c`.


### Expected behavior

`vslideup.vi v3, v2, 4` with `vl=16` writes `v2[i-4]` to `v3[i]` for `i = 4..15`, leaves `v3[0..3]` as they were, and commits.

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
  - Build command: `make emu CONFIG=MinimalConfig EMU_THREADS=8 -j60 OPT_FAST=-O3` (also `CONFIG=DefaultConfig`)
  - Run command: `./build/emu -i t13_vslideup_vset.bin --no-diff -C 200000`
  - Workload: attached (`workload/`)

### To Reproduce

1. Build `t13_vslideup_vset.S` (includes `common_xs.S`, and `link.ld` is attached) or use the attached `.bin`.
2. Run with `--no-diff`. The cycle limit fires with `instrCnt = 25`.
3. Run with NEMU difftest. It aborts at the `vslideup.vi` with `v3` unchanged.

### Additional context

[vslideup.zip](https://github.com/user-attachments/files/32845863/vslideup.zip)
