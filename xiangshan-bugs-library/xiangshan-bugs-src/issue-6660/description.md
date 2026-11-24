### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and [XiangShan Documents](https://docs.xiangshan.cc/zh-cn/latest), and I believe this is a XiangShan RTL issue. 我已经阅读过 [RISC-V 指令集手册](https://github.com/riscv/riscv-isa-manual) 和 [香山文档](https://docs.xiangshan.cc/zh-cn/latest)，确认这应该是香山 RTL 的问题。
- [x] I have searched previous issues and PRs and did not find anything relevant. 我已经搜索过之前的 issue 和 PR，并没有找到相关的。
- [x] I have reproduced the issue using the latest commit on the default branch. 我已经使用默认分支最新的 commit 复现了问题。
- [x] If this report was generated with AI assistance (otherwise leave unchecked), I have verified the correctness of its content. 如果这是由 AI 生成的（否则请不要勾选），我已经验证了内容的正确性。

### Branch

kunminghu-v3

### Describe the bug

With tag ECC enabled (`enableTagEcc = true`, SECDED, both shipped configs) and a tag error injected through the L1D control unit at `0x38022000`, a load that hits the affected set gets wrong data and no exception. There are two cases, both on MinimalConfig and DefaultConfig.

Case 1, two-bit tag error, wrong-address hit (`t46_tagecc_falsehit_def`). Line A (`0x80300000`) holds `aa..` and is dirty in L1D. Line B is `A + 0x5000`. It maps to the same set on both geometries (bits 12 and 14 cancel in the two-bit index hash of DefaultConfig, and MinimalConfig indexes with `vaddr[11:6]`), and its physical tag differs from A's in bits 0 and 2. B holds `bb..` in memory and is not in L1D (`cbo.flush`). With tag mask `0x5` armed, `ld` from B returns `aa..`, because A's line matched B's address.

```
RESULT: aa aa aa aa aa aa aa aa ...          (kunminghu-v3, both configs)
s4 different at pc = ..., right = 0xbbbbbbbbbbbbbbbb, wrong = 0xaaaaaaaaaaaaaaaa   (difftest)
```

Case 2, one-bit tag error on a dirty line, stale data (`t46_tagecc_selfprobe`). Same line A, dirty with `aa..` (L2 still holds the zero-filled copy from the allocation). With tag mask `0x1` armed, `ld` from A returns `0`. The line was treated as a miss and refilled from L2, and the dirty data was neither returned nor written back first.

```
s4 different at pc = ..., right = 0xaaaaaaaaaaaaaaaa, wrong = 0x0000000000000000
```

Neither case raises an exception. A one-bit tag error with the B-address load (`t46_tagecc_falsehit_min`, mask `0x1`, B = A + 0x1000 on MinimalConfig) returns the correct `bb..`.

The injection mechanism works as documented. With a data mask of `0xff` (`t46_dataecc_probe`) the load of A returns the flipped data `55 aa aa ...`, again without an exception, which is by design (`EnableAccurateLoadError` is false in both configs).

Arming sequence (M mode, physical addresses).

```asm
li  s3, 0x38022000
li  t0, 5            # or 1
sd  t0, 0x10(s3)     # tag mask register
li  t0, 0xffffffff3  # ese=1, persist=1, comp=tag, all 32 banks
sd  t0, 0(s3)
fence
ld  s4, 0(s2)        # B (case 1) or A (case 2)
sd  zero, 0(s3)      # disarm
```

### Expected behavior

A tag error must not turn into a hit on a different address, and a detected error on a dirty line must not discard the dirty data. The load of B should miss and return `bb..` from memory (NEMU value `0xbbbbbbbbbbbbbbbb`). The load of A should return the dirty `aa..` or report the error. A detected but uncorrectable tag error should raise an access fault, or at least a bus-error report, before the wrong data reaches the register file. Delivering `aa..` for address B, or `0` for a line the program just wrote, is silent data corruption.

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
  - SPIKE commit id (if difftest failed with SPIKE): N/A (the control unit does not exist on Spike)
- Build & Run
  - Build command: `make emu CONFIG=MinimalConfig EMU_THREADS=8 -j60 OPT_FAST=-O3` (also `CONFIG=DefaultConfig`)
  - Run command: `./build/emu -i t46_tagecc_falsehit_def.bin --diff ./ready-to-run/riscv64-nemu-interpreter-so -C 200000`
  - Workload: attached (`workload/`)

### To Reproduce

1. Build the four `t46_*.S` workloads (they include `common_xs.S`, and `link.ld` is attached) or use the attached binaries. `t46_tagecc_falsehit_def` works on both configs. `t46_tagecc_falsehit_min` is the one-bit variant for MinimalConfig alone.
2. Run with NEMU difftest. `t46_tagecc_falsehit_def` and `t46_tagecc_selfprobe` abort with the register mismatch shown above.
3. Run with `--no-diff`. The UART line `RESULT:` shows the loaded value. Bytes 8..15 hold the control register read back after the load (`f3 ff ff ff 0f`), which confirms that the injection stayed armed.

### Additional context

[xs_ecc.zip](https://github.com/user-attachments/files/32846888/xs_ecc.zip)
