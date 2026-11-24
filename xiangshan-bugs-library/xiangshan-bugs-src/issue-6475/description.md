### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and [XiangShan Documents](https://docs.xiangshan.cc/zh-cn/latest), and I believe this is a XiangShan RTL issue. 我已经阅读过 [RISC-V 指令集手册](https://github.com/riscv/riscv-isa-manual) 和 [香山文档](https://docs.xiangshan.cc/zh-cn/latest)，确认这应该是香山 RTL 的问题。
- [x] I have searched previous issues and PRs and did not find anything relevant. 我已经搜索过之前的 issue 和 PR，并没有找到相关的。
- [x] I have reproduced the issue using the latest commit on the default branch. 我已经使用默认分支最新的 commit 复现了问题。
- [x] If this report was generated with AI assistance (otherwise leave unchecked), I have verified the correctness of its content. 如果这是由 AI 生成的（否则请不要勾选），我已经验证了内容的正确性。

### Branch

kunminghu-v3

### Describe the bug

A **legal** segmented vector store causes the ROB's per-entry uop counter to
**underflow**, tripping the assertion in `Rob.scala` and aborting the core:

```
Assertion failed: robEntries 174 uopNum is overflow!
Core-0 instrCnt = 168, cycleCnt = 9,372
```

The assertion site (`src/main/scala/xiangshan/backend/rob/Rob.scala:1085`):
```scala
}.elsewhen(robEntries(i).valid) {
  // update by writing back
  robEntries(i).uopNum := robEntries(i).uopNum - wbCnt
  assert(!(robEntries(i).uopNum - wbCnt > robEntries(i).uopNum), s"robEntries $i uopNum is overflow!")
}
```
`robEntries(i).uopNum` (allocated at enqueue = `enqWBNum`) is decremented by
`wbCnt` (writebacks) each cycle; the assert catches the unsigned underflow when
`wbCnt` exceeds the remaining `uopNum`. In other words, the segmented store
**writes back more uops than were counted at enqueue** — an enqueue/decode
uop-count miscalculation for segment stores.

The faulting instruction and its active `vtype` (from the disassembly):
```
800002d8: vsetvli t0, zero, e32, m1, tu, mu     ; SEW=32, LMUL=1
800002e4: vsseg3e32.v v6, (t1)                   ; <- the ROB entry that underflows
```

This instruction is **fully legal**: for `vsseg3e32.v` under `e32/m1`,
`EMUL = EEW/SEW * LMUL = 32/32 * 1 = 1`, and `nf*EMUL = 3*1 = 3 <= 8`
(touches v6,v7,v8; `vl = VLMAX = 4` for VLEN=128). It is not a reserved/illegal
encoding. NEMU executes the workload in lockstep for all 168 committed
instructions before the DUT's internal ROB assertion fires; the workload has no
self-modifying code. Every other segmented store in the reproducer is likewise
legal (`vsseg2e32`@m2 → nf*EMUL=4; `vsseg4e16`@m2 → 8; `vsseg4e8`@m2 → 8).

### Expected behavior

A legal segmented store should retire without underflowing the ROB uop counter
and without any `robEntries ... uopNum is overflow!` assertion, matching the
reference model. The enqueued `uopNum` should equal the number of uops the
segment store actually writes back.

### Environment

- Hardware
  - CPU: x86_64 server
  - Memory (GB): 512
  - Storage (GB): (n/a)
- Software
  - Operating system: Linux
  - gcc version: gcc (conda-forge gcc 16.1.0-1) 16.1.0   (emu build compiler)
  - clang version: (not used)
  - java version: openjdk 20.0.2-internal (mill)
  - mill version: 0.12.17 (from .mill-version)
  - firtool version: 1.144.0
  - verilator version: Verilator 5.028 2024-08-21
- Repo
  - XiangShan commit id: `50cdcfc2c` (kunminghu-v3; **reproduced on this,
    the latest default-branch commit**, with a STOCK / non-instrumented emu and
    an unmodified difftest submodule)
  - NEMU commit id (if difftest failed with NEMU): shipped `ready-to-run`
    interpreter `e884041`
  - difftest submodule: `45638f51`
- Build & Run
  - Build command: `make emu` for `SimTop` at kunminghu-v3, rv64gcv, VLEN=128
  - Run command: `./build/emu -i id_0_145.elf -C 20000 --diff <nemu-so>`

### To Reproduce

1. Build a difftest emu of `SimTop` at kunminghu-v3 `50cdcfc2c` (rv64gcv,
   VLEN=128) — a stock `make emu` build reproduces it (no instrumentation).
2. Run the attached workload:
   ```
   ./build/emu -i id_0_145.elf -C 20000 --diff ready-to-run/riscv64-nemu-interpreter-so
   ```
3. Observe: `Assertion failed: robEntries N uopNum is overflow!` and the core
   aborts around `instrCnt = 168`, `cycleCnt = 9,372`. Deterministic (same
   `robEntries 174` every run).

Attachments (tarball `xs_v3_rob_uopnum_overflow_attachments.tar.gz`):
- `id_0_145.elf` — reproducer binary
- `id_0_145.si` / `id_0_145.disasm` — source instruction listing / disassembly
- `stock_50cdcfc2c_replay.log` — the stock-emu run at `50cdcfc2c` showing the
  assertion and abort

The workload is a ~200-instruction user-mode test found by differential fuzzing
against NEMU; it is not yet hand-minimized. The relevant trigger is a legal
segmented store `vsseg3e32.v` under `vsetvli e32,m1` (the first one committed,
around pc `0x800002e4`).

[xs_v3_rob_uopnum_overflow_attachments.tar.gz](https://github.com/user-attachments/files/31776720/xs_v3_rob_uopnum_overflow_attachments.tar.gz)

### Additional context

- **Likely module**: `mem` / backend ROB — the per-entry `uopNum` accounting
  in `Rob.scala` (assert at `:1085`), exercised by the vector segmented-store
  uop expansion. The enqueued `uopNum` (`enqWBNum`) appears to undercount the
  writebacks a segment store produces, so `uopNum - wbCnt` underflows.
- **Root cause / bisect**: a regression introduced between `aee742c9` (clean) and
  `1df189187` (present). The vector-decode/issue changes in that range are the
  suspects — `IssuePipe` (`is0FlushNext`), `LatDecoder`, `VsetDecoder`,
  `DecodeChannels`. `Rob.scala` itself is byte-identical across `aee742c9` →
  `50cdcfc2c`, so the miscount is upstream of the ROB, in the segment-store uop
  generation. Not fixed at the current tip `50cdcfc2c` (the implicated files are
  untouched by the commits after `1df189187`).
- **Related issues**: several confirmed-open issues exist in the vector-segment
  area (#5934 masked segment-load lane corruption, #5931 segment-load field data,
  #5769 / #6296 segment-store exception reporting), but none match this
  ROB uop-count underflow / assertion.
- **Not a self-modifying-code artifact**: the workload does no self-modifying
  stores; the difftest failure is a genuine internal ROB assertion, not the
  `fence.i`/ICache-coherence caveat noted in CONTRIBUTING.
