### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and [XiangShan Documents](https://docs.xiangshan.cc/zh-cn/latest), and I believe this is a XiangShan RTL issue. 我已经阅读过 [RISC-V 指令集手册](https://github.com/riscv/riscv-isa-manual) 和 [香山文档](https://docs.xiangshan.cc/zh-cn/latest)，确认这应该是香山 RTL 的问题。
- [x] I have searched previous issues and PRs and did not find anything relevant. 我已经搜索过之前的 issue 和 PR，并没有找到相关的。
- [x] I have reproduced the issue using the latest commit on the default branch. 我已经使用默认分支最新的 commit 复现了问题。
- [x] If this report was generated with AI assistance (otherwise leave unchecked), I have verified the correctness of its content. 如果这是由 AI 生成的（否则请不要勾选），我已经验证了内容的正确性。

### Branch

kunminghu-v3

### Describe the bug

On the current head, vector loads and stores move the whole register group. A unit-stride load writes all 16 bytes of the destination (all 32 for LMUL=2) whatever `vl` says. A strided load returns consecutive bytes as if the stride were 1. A unit-stride store writes all 16 bytes. `vstart` has no effect on which elements are moved and is reset to 0 afterwards. Each workload below fails NEMU difftest at the load or store on MinimalConfig and DefaultConfig. All pass on kunminghu-v2 (`e7bab53e6`) and match Spike.

VLEN=128, e8/m1 unless stated, `src1 = 01 02 .. 10`, old vd = `ff ff ff ff ff ff ff fd ff ..`, all registers preloaded with `vl=16`.

| Workload | Instruction under test | Expected v3 / memory (NEMU, Spike) | kunminghu-v3 |
|---|---|---|---|
| t22_vle_vl4 | `vsetivli x0,4,e8,m1,ta,ma; vle8.v v3,(a1)` | `01 02 03 04` + tail | `01 02 .. 10` (16 bytes loaded) |
| t23_vle_vl4_tu | same with `tu` | `01 02 03 04 ff ff ff fd ff ..` | `01 02 .. 10` (tail overwritten under `tu`) |
| t24_vle32_vl2 | `vsetivli x0,2,e32,m1,ta,ma; vle32.v v3,(a1)` | `01 .. 08` + tail | `01 02 .. 10` |
| t25_vlse8_vl4 | `vsetivli x0,4,e8,m1,ta,ma; vlse8.v v3,(a1),t0` with stride 2 | `01 03 05 07` + tail | `01 02 03 04 05 ..` (stride ignored) |
| t26_vle8_vl4_m2 | `vsetivli x0,4,e8,m2,ta,ma; vle8.v v2,(a1)` | v2 = `01 02 03 04` + tail, v3 untouched | v2 = 16 bytes, v3 = the next 16 bytes of memory |
| t27_vse8_vl4 | `vsetivli x0,4,e8,m1,ta,ma; vse8.v v1,(a3)` into `aa..` | `01 02 03 04 aa aa ..` | 16 bytes stored (`DUT origin ... mask:0xffff`) |
| t17_vle_vstart_eq_vl | `vl=4`, `csrw vstart,4`, `vle8.v v3,(a1)` | v3 unchanged | 16 bytes loaded |
| t19_vle_vstart_in_body | `vl=8`, `vstart=2`, `vle8.v v3,(a1)` | `ff ff 03 04 05 06 07 08` + tail | 16 bytes loaded |
| t20_vse_vstart_eq_vl | `vl=4`, `vstart=4`, `vse8.v v1,(a3)` | nothing stored | 16 bytes stored |
| t21_vse_vstart_in_body | `vl=8`, `vstart=2`, `vse8.v v1,(a3)` | bytes 2..7 stored | 16 bytes stored |
| t39_vle_vl0 / t40_vse_vl0 | `vl=0`, load / store | nothing moved | 16 bytes moved |

Typical difftest output (t23, MinimalConfig) for a load.

```
v3_low  different at pc = ..., right = 0xfdffffff04030201, wrong = 0x0807060504030201
v3_high different at pc = ..., right = 0xffffffffffffffff, wrong = 0x100f0e0d0c0b0a09
Core 0: ABORT
```

The store case (t20) reports a store-commit mismatch.

```
Mismatch for store commits
  REF commits addr 0x0000000080001030, data 0x0000000000000001, mask 0x01, ...
  DUT origin: addr 0x0000000080001030, high:0x100f0e0d0c0b0a09, low 0x0807060504030201, mask:0xffff
```

The `tu` case (t23) and the store cases are the clearest. Tail-undisturbed leaves no freedom about the tail, and a store with `vl=4` may not write byte 4.


### Expected behavior

Body elements (`vstart <= i < vl`) alone are loaded or stored. Tail elements stay unchanged under `tu`, and under `ta` they either keep their value or become all ones. Prestart elements are never touched. When `vstart >= vl` no element is moved and `vstart` is set to zero. A strided access uses the stride. NEMU, Spike and kunminghu-v2 produce the expected column above for each workload.

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
  - SPIKE commit id (if difftest failed with SPIKE): N/A (Spike used as an offline reference)
- Build & Run
  - Build command: `make emu CONFIG=MinimalConfig EMU_THREADS=8 -j60 OPT_FAST=-O3` (also `CONFIG=DefaultConfig`)
  - Run command: `./build/emu -i t23_vle_vl4_tu.bin --diff ./ready-to-run/riscv64-nemu-interpreter-so -C 200000`
  - Workload: attached (`workload/`)

### To Reproduce

1. Build any of the listed `tNN_*.S` (they include `common_xs.S`, and `link.ld` is attached) or use the attached `.bin` files.
2. Run with NEMU difftest. It aborts at the vector load or store.
3. Run with `--no-diff`. The UART line `RESULT:` prints the 16 bytes stored from `v3` afterwards (`VSTART:` prints the `vstart` CSR read right after the instruction), for a direct comparison with the table.

### Additional context

[vlsu.zip](https://github.com/user-attachments/files/32846188/vlsu.zip)
