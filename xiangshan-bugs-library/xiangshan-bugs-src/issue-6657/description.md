### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and [XiangShan Documents](https://docs.xiangshan.cc/zh-cn/latest), and I believe this is a XiangShan RTL issue. 我已经阅读过 [RISC-V 指令集手册](https://github.com/riscv/riscv-isa-manual) 和 [香山文档](https://docs.xiangshan.cc/zh-cn/latest)，确认这应该是香山 RTL 的问题。
- [x] I have searched previous issues and PRs and did not find anything relevant. 我已经搜索过之前的 issue 和 PR，并没有找到相关的。
- [x] I have reproduced the issue using the latest commit on the default branch. 我已经使用默认分支最新的 commit 复现了问题。
- [x] If this report was generated with AI assistance (otherwise leave unchecked), I have verified the correctness of its content. 如果这是由 AI 生成的（否则请不要勾选），我已经验证了内容的正确性。

### Branch

kunminghu-v3

### Describe the bug

A store or load to a peripheral-range physical address that no device claims completes without an exception. The SoC routes such an access to the TileLink error device, and the response comes back denied. Spike and kunminghu-v2 both raise a store/AMO access fault (`mcause=7`) for the store and a load access fault (`mcause=5`) for the load.

Workloads `t41_mmio_store_unmapped` and `t42_mmio_load_unmapped` run in M mode with no PMP entries configured.

```asm
li  t0, 0x39000000     # between SYSCNT (0x38040000) and PLL (0x3a000000); nothing is mapped here
sd  zero, 0(t0)        # t41: must raise mcause 7
ld  t2, 0(t0)          # t42: must raise mcause 5
li  t1, 0xa5
sb  t1, 0(a3)          # reached when no trap was taken
```

The harness prints the result buffer and the trap cause. On kunminghu-v3 (MinimalConfig and DefaultConfig) both workloads print `RESULT: a5 ...` and no `MCAUSE` line. The marker store after the access was therefore executed. On kunminghu-v2 and Spike they print `MCAUSE: 07` (store) and `MCAUSE: 05` (load) and the marker is never written.

NEMU difftest does not catch this. The bundled NEMU also completes the access without a fault, and the difftest run ends with `HIT GOOD TRAP`. The failure shows up against Spike, against kunminghu-v2, and in the harness self-check.

`cbo.clean` to the same address (workload `t43`) does raise `mcause=7` on kunminghu-v3. The CBO path handles the denied response and the plain load/store path does not.

### Expected behavior

An uncached access whose memory response is denied has to raise the corresponding access fault. The instruction must not retire as if the access had succeeded.

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
  - NEMU commit id (if difftest failed with NEMU): `ready-to-run/riscv64-nemu-interpreter-so` from ready-to-run `4cf9983569432035d41956c65e6a58724b5954be` (difftest does not fail, NEMU has the same behaviour)
  - SPIKE commit id (if difftest failed with SPIKE): N/A (Spike used as an offline reference)
- Build & Run
  - Build command: `make emu CONFIG=MinimalConfig EMU_THREADS=8 -j60 OPT_FAST=-O3` (also `CONFIG=DefaultConfig`)
  - Run command: `./build/emu -i t41_mmio_store_unmapped.bin --no-diff -C 200000`
  - Workload: attached (`workload/`)

### To Reproduce

1. Build `t41_mmio_store_unmapped.S` / `t42_mmio_load_unmapped.S` (they include `common_xs.S`, and `link.ld` is attached), or use the attached `.bin` files.
2. Run on the emu and look at the UART output. `RESULT: a5 ...` with no `MCAUSE:` line means the access retired without a trap. The expected output is `MCAUSE: 07` (t41) or `MCAUSE: 05` (t42) followed by the untouched result buffer.
3. `t43_cbo_clean_unmapped` is the control and prints `MCAUSE: 07` on kunminghu-v3.

### Additional context

[xs_mmio.zip](https://github.com/user-attachments/files/32839257/xs_mmio.zip)
