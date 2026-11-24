### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and [XiangShan Documents](https://docs.xiangshan.cc/zh-cn/latest), and I believe this is a XiangShan RTL issue. 我已经阅读过 [RISC-V 指令集手册](https://github.com/riscv/riscv-isa-manual) 和 [香山文档](https://docs.xiangshan.cc/zh-cn/latest)，确认这应该是香山 RTL 的问题。
- [x] I have searched previous issues and PRs and did not find anything relevant. 我已经搜索过之前的 issue 和 PR，并没有找到相关的。
- [x] I have reproduced the issue using the latest commit on the default branch. 我已经使用默认分支最新的 commit 复现了问题。
- [x] If this report was generated with AI assistance (otherwise leave unchecked), I have verified the correctness of its content. 如果这是由 AI 生成的（否则请不要勾选），我已经验证了内容的正确性。

### Branch

kunminghu-v3

### Describe the bug

With `EnableTagEcc` on (default, Parameters.scala:285; also set in MinimalConfig, Configs.scala:185-198), every load reads all `nWays` (=4 in DefaultConfig, `WithNKBL1D(64, ways=4)`) tags with per-way ECC check:

- LoadPipe.scala:212 — `s1_tag_errors = wayMap(w => meta_resp(w).coh.isValid() && dcacheParameters.tagCode.decode(s1_enctag_resp(w)).error).asUInt` (per-way vector, only isValid-gated)
- LoadPipe.scala:417-419 — `s2_tag_error := s2_tag_errors.orR` — **the per-way errors are OR-ed into one set-wide flag, with no way masking**
- LoadPipe.scala:432 — `io.miss_req.bits.cancel := ... || s2_tag_error` — a load that misses in a set containing *any* bad-tag way has its miss request cancelled
- LoadPipe.scala:461 + LoadQueueReplay — the cancelled load is replayed (cause C_DM on this path, NewLoadUnit.scala:1114; C_DR is the mshr-nack case), re-misses, is cancelled again: **the load never completes**


### Expected behavior

Deterministic difftest runs on the emu above (fixed seed). Two binaries differing by exactly one immediate (tagMask = 1 vs 0; byte-verified: the loaded images differ in 1 byte at vaddr 0x8000003e — the immediate of the `li t1` at 0x8000003c, `li t1,1` vs `li t1,0` — the two .elf files additionally differ in 6 bytes of .strtab gcc temp-object names):

- control (tagMask=0): `[pre][inj][post] DONE TRAPCNT=0000000000000000` (with trailing newline), HIT GOOD TRAP, instrCnt = 522, cycleCnt = 12443 — every load completes
- injected (tagMask=1): `[pre][inj][post] DONE TRAPCNT=0000000000000000` **without the trailing newline**, then the difftest watchdog fires — "No instruction of core 0 commits for 15000 cycles, maybe get stuck" — ABORT at pc = 0x800000e4 (inside the `puts` loop, at the load fetching the final `'\n'` character), instrCnt = 508, cycleCnt = 27298 (no-commit onset ≈ cycle 12298 = 27298 − 15000)


### Environment

- Hardware
  - CPU: Intel(R) Xeon(R) 6960P, 288 cores
  - Memory (GB): 2263
  - Storage (GB): 1830 (531 free)
- Software
  - Operating system: Ubuntu 24.04.4 LTS (xs-env container)
  - gcc version: 13.3.0 (riscv64-linux-gnu-gcc for the PoC; same tree builds the emu)
  - clang version: not used
  - java version: OpenJDK 21.0.11
  - mill version: 0.12.17
- Repo
  - XiangShan commit id: `9d4e20172` (branch kunminghu-v3, current HEAD at reporting time; the attached VCD was captured on the equivalent a2340a51 build — LoadPipe/MainPipe unchanged between the two; `dirty: 1` from XSCache submodule build artifacts)
  - NEMU commit id (if difftest failed with NEMU): prebuilt riscv64-nemu-interpreter-so from ready-to-run, placed at `$NEMU_HOME/build/riscv64-nemu-interpreter-so` (the run logs show the resolved path /work/nemu-home-v/build/..., a symlink to the ready-to-run binary)
  - SPIKE commit id (if difftest failed with SPIKE): not used (difftest ran against NEMU)
- Build & Run
  - Build command: `IN_XSDEV_DOCKER=y make emu CONFIG=DefaultConfig NUM_CORES=1 EMU_THREADS=16 -j64`
  - Run command (if applicable): `NEMU_HOME=<nemu> ./build/verilator-compile/emu -i <poc>.elf -C 100000` (the injected run reaches the watchdog at ~27.3k cycles in ~32 s; `-C 100000` bounds the run at ~2 min)

### To Reproduce

1. Build the emu on kunminghu-v3 @ 9d4e20172 (the current HEAD) with DefaultConfig. The injection uses the L1DCacheCtrl TL device at 0x38022000 — instantiated in DefaultConfig and MinimalConfig (Configs.scala:185-198); M-mode software can write it (the PMA grants 0x38022000 w/r; we did not test S/U/VS triggerability, but PMP/G-stage translation should block it).
2. Compile the two PoC variants from the attached sources (`-nostdlib -T baremetal.ld -Wl,--build-id=none`; baremetal.ld attached). The only source difference is one immediate: the tagMask value written to the CtrlUnit (1 vs 0).
3. Each binary: (a) prints markers over UART; (b) writes tagMask=1 (or 0) to 0x38022010 and ctrl=0x13 to 0x38022000 — in the ctrl word bit0 = ese (error signaling enable), bit1 = persist, bit3 = comp (0 = tag), bit4 = bank0 enable — this arms the CtrlUnit's read-path tag toggle `pseudo_enc := Cat(ecc, real_tag ^ mask)` (LoadPipe.scala:193-207); (c) executes one victim load from DRAM (0x80010000).
4. Run each with NEMU_HOME set (`emu -i b5f{_ctrl,}.elf -C 100000`, ~2 min bound).
   Expected: control → `[pre][inj][post] DONE TRAPCNT=0000000000000000`, HIT GOOD TRAP, instrCnt = 522; injected → the same markers, then the watchdog "No instruction of core 0 commits for 15000 cycles, maybe get stuck", ABORT, instrCnt = 508, cycleCnt = 27298. The exit convention prints a "HIT CRITICAL ERROR ... double trap" line before HIT GOOD TRAP in the control — intended.
5. Optional waveform: the emu used already has EMU_TRACE=1 (`a VM_TRACE=0 emu segfaults on --dump-wave`); append `--dump-wave -b 11000 -e 13500 --wave-path=...` and parse per-cycle (parser attached): ldu_0/1/2 `s2_tag_errors != 0` / `io_miss_req.valid = 1` / `io_miss_req_bits.cancel = 1` asserted in 2500 of 2501 sampled cycles (2501/2501 under an after-change sampling) — the cancel/replay livelock directly observed.


### Additional context

https://drive.google.com/file/d/18qrc0nuffMaS_nJPm-nK_zXAtx9vocXO/view?usp=drive_link
