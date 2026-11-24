### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and [XiangShan Documents](https://docs.xiangshan.cc/zh-cn/latest), and I believe this is a XiangShan RTL issue. 我已经阅读过 [RISC-V 指令集手册](https://github.com/riscv/riscv-isa-manual) 和 [香山文档](https://docs.xiangshan.cc/zh-cn/latest)，确认这应该是香山 RTL 的问题。
- [x] I have searched previous issues and PRs and did not find anything relevant. 我已经搜索过之前的 issue 和 PR，并没有找到相关的。
- [x] I have reproduced the issue using the latest commit on the default branch. 我已经使用默认分支最新的 commit 复现了问题。
- [x] If this report was generated with AI assistance (otherwise leave unchecked), I have verified the correctness of its content. 如果这是由 AI 生成的（否则请不要勾选），我已经验证了内容的正确性。

### Branch

kunminghu-v3

### Describe the bug


`LoadUnitS2` documents the contract *"A killed request will not produce any side effects on other modules or micro-arch states"* (`NewLoadUnit.scala:897-898`) and gates the other kill-sensitive outputs of the stage on `kill` (write-back `:1145`, `dcacheKill` `:1206`, `prefetchTrainHint` `:765`, dataPathMeta, debugInfo, forward channels). 

The training output is the one exception — `NewLoadUnit.scala:1216-1217`:

```scala
io.prefetchTrain.valid := pipeIn.valid && tlbHit && !exception && !isUncache && !isUncacheReplay &&
  in.isFirstIssue() && !isVector     // no !kill — sibling :765 has it
```

A request killed in the same cycle still asserts `prefetchTrain.valid` and trains the L1D stride prefetcher.

 **Our question: is training on killed requests intended** (confidence counters expected to absorb the noise), **or an oversight** — inconsistent with the sibling `:765` gate? We keep this report to observations plus this one narrow question.


### Expected behavior

Deterministic difftest runs on the emu above (fixed seed, all end `HIT GOOD TRAP`; PoC/parsers attached).

- **Counters**: 800 retiring target loads → `stride_meta_array: s0_valid = 814`; a binary whose bitmap-driven branch kills ~50% of the loads on the wrong path → `s0_valid = 827` with only 400 retiring and `commit_branch_mispredicts = 253` (nail: the wrong path is exercised). Observations only — the counter accepts trains from any load, no per-request ledger derived.
- **Waveform** (VCD, 500-cycle window, 1503 (cycle, load-unit) samples; captured on the `!kill`-gated build — `:1173` on `bc05f3b61a` — provenance matters: without the gate these trains fire, full-run `s0_valid` drops 772 → 699 on the same binary):
  - **19 events** where `pipeIn.valid=1`, `kill=1`, and the observable part of the expression (`isFirstIssue`, `!isHwPrefetch`) holds; the gate suppresses all 19, and **8 are L1 misses** (would pass the upstream miss gate into the table).
  - Late-kill trains still pass the gate: **22/56 accepted trains** carry the vaddr of bitmap-skipped iterations (never-retiring loads; strict address attribution). Chain: c=9012 offered (va=0x80004480) → c=9015 accepted → c=9016 redirect → c=9018 same line enqueued into L2 `vbop_train_buf` (`reqsource=CPULoadData`). ROB-level correspondence not proven.
  - L2: 20/52 BOP training enqueues carry skipped-iteration lines (16 × `CPULoadData`, 4 × `Prefetch2L2SMS`) — observation, per-event causality not established.


### Environment

- Hardware
  - CPU: Intel(R) Xeon(R) 6960P, 288 cores
  - Memory (GB): 2263
  - Storage (GB): 1830 (531 free)
- Software
  - Operating system: Ubuntu 24.04.4 LTS (xs-env container)
  - gcc version: 13.3.0 (`riscv64-linux-gnu-gcc` for the PoC; same tree builds the emu)
  - clang version: not used
  - java version: OpenJDK 21.0.11
  - mill version: 0.12.17
- Repo
  - XiangShan commit id: `a2340a51` (branch `kunminghu-v3`)
  - NEMU commit id: prebuilt `riscv64-nemu-interpreter-so` from `ready-to-run` @ `4cf9983`
  - SPIKE commit id: not used (difftest ran against NEMU)
- Build & Run
  - Build command: `IN_XSDEV_DOCKER=y make emu CONFIG=DefaultConfig NUM_CORES=1 EMU_THREADS=16 -j32`
  - Run command: `NEMU_HOME=/work/nemu-home ./build/verilator-compile/emu -i <poc>.elf -C 5000000`



### To Reproduce

1. Build the emu on `kunminghu-v3` @ `a2340a51` with `DefaultConfig` (`IN_XSDEV_DOCKER=y make emu ... -j32`; needs L1 prefetchers — `MinimalConfig` sets `prefetcher = Nil` and does not reproduce).
2. Compile the two PoC binaries from the attached sources (`-nostdlib -T baremetal.ld -Wl,--build-id=none`; the flag fixes the ELF layout / skip-bitmap address used for attribution).
3. Run each with `NEMU_HOME` set (`emu -i b5{a,b}.elf -C 5000000`). Expected: A `s0_valid = 814`, mispredicts 48; B `827` with 400 retiring target loads, `SKIP=200`, mispredicts 253; `instrCnt` 6413 vs 6418 (equal by construction). The double-trap exit prints a `HIT CRITICAL ERROR` line before `HIT GOOD TRAP` — intended.
4. Optional waveform: rebuild with `EMU_TRACE=1` (a `VM_TRACE=0` emu segfaults on `--dump-wave`), `--dump-wave -b 9000 -e 9500`; parsers attached, full recipe in `REPRODUCE.md`.


### Additional context

[bug5_poc.tar.gz](https://github.com/user-attachments/files/31992311/bug5_poc.tar.gz)
