### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and [XiangShan Documents](https://docs.xiangshan.cc/zh-cn/latest), and I believe this is a XiangShan RTL issue. 我已经阅读过 [RISC-V 指令集手册](https://github.com/riscv/riscv-isa-manual) 和 [香山文档](https://docs.xiangshan.cc/zh-cn/latest)，确认这应该是香山 RTL 的问题。
- [x] I have searched previous issues and PRs and did not find anything relevant. 我已经搜索过之前的 issue 和 PR，并没有找到相关的。
- [x] I have reproduced the issue using the latest commit on the default branch. 我已经使用默认分支最新的 commit 复现了问题。
- [x] If this report was generated with AI assistance (otherwise leave unchecked), I have verified the correctness of its content. 如果这是由 AI 生成的（否则请不要勾选），我已经验证了内容的正确性。

### Branch

kunminghu-v2

### Describe the bug

Clearing `smblockctl` (`0x5c3`) bit 9 disables hardware misaligned-load handling, but a cacheable scalar load crossing a 16-byte boundary can still retire incorrect data without an exception. The DCache data read is killed, the MisalignBuffer path is disabled, and the final load-address-misaligned exception is cleared, allowing ordinary writeback without a valid DCache response. Bit 9 resets to 1; the failure requires software to clear it.

In the attached `csr0_diff.log`, the commit trace records `ld@0x80000140` writing `t3=0`, then `mv@0x80000144` writing `a0=0`. The addressed bytes independently reconstruct to `0x7899aabbccddeeff`. The load has no skip/delayed marker. A separate no-diff self-check runs the same load with bit 9 **on -> off -> on**: only the middle phase returns an incorrect value, and all three phases complete without an architectural exception.

Attachment: `load_issue_materials.zip`. Paths below are relative to its `load/` directory. Full logs are attached under `repro/logs/` and `evidence/legacy_logs/`; numbered excerpts are in `evidence/legacy_log_excerpts.txt`.


### Expected behavior

Return the addressed bytes correctly or raise an appropriate exception. Misaligned accesses need not always be handled transparently: the [RISC-V load/store alignment rules](https://docs.riscv.org/reference/isa/v20260120/unpriv/rv32.html) permit address-misaligned or access-fault exceptions in the relevant execution environment. An incorrect value retired as a successful load is the defect.

### Environment

- Hardware
  - CPU: Intel(R) Xeon(R) 6960P; 288 logical CPUs.
  - Memory (GB): 2430.02 GB (2263.14 GiB), host total.
  - Storage (GB): 1887.42 GB (1757.80 GiB), capacity of the filesystem containing the source checkout.
- Software
  - Operating system: Ubuntu 26.04 LTS, kernel `7.0.0-38-generic`.
  - gcc version: host GCC 15.2.0; the probe compiler is `riscv64-unknown-linux-gnu-gcc (g6afcc4f6d) 16.1.0`.
  - clang version: not found on the collector's PATH; not used for the probe rebuild.
  - java version: historical build log records OpenJDK `21.0.10-internal`; current collector reports Temurin OpenJDK `21.0.11`.
  - mill version: checkout pins `0.12.3` in `.mill-version`; no executable was on the collector's PATH, and no Mill bootstrap was run.
  - Verilator: historical build log records `5.048`, conda-forge build 1; not on the collector's current PATH.
- Repo
  - XiangShan commit id: `fe2288cb09361b65afa26be3f403520c4b496dbf`, a detached checkout of the reported `kunminghu-v2` revision; working tree clean.
  - difftest commit id: `3729300ae233816d332d057f170472ebe35147b0`; working tree clean.
  - NEMU commit id (if difftest failed with NEMU): not applicable to the primary `--no-diff` reproducer. Supplementary differential runs used a reference binary with SHA256 `3e9d63c31d2cd68ec68d55fa9df56ec990ce9a9fd3a8e59709ecb92f21176c40`; its source commit was not recorded.
  - SPIKE commit id (if difftest failed with SPIKE): not applicable; SPIKE was not used.
- Build & Run
  - Build command: the existing simulator build script contains `make emu CONFIG=DefaultConfig NUM_CORES=1 EMU_THREADS=16 EMU_TRACE=1 FIRTOOL=/home/haoran/.cache/llvm-firtool/1.62.1/bin/firtool -j32`. That full RTL/emu build was not rerun during the audit. The exact image is identified by SHA256 `8378454e8d10cf24903ab5778498ccbb00697226ec341dbaec814ec270fe4cb9`.
  - Workload build: the attached helper supports `--rebuild --tool-prefix /home/haoran/.local/opt/riscv/bin/riscv64-unknown-linux-gnu-`; it uses the supplied assembly/linker script and requires byte-identical images. Exact compiler/linker flags are documented in `../common/REPRO_README.md`.
  - Run command (if applicable): the complete command is in **To Reproduce**; it forwards `-i IMAGE --no-diff --max-cycles 30000` through the resource-limited wrapper.

Configuration: `DefaultConfig`, one core, M mode, Bare translation, cacheable DRAM, scalar accesses. The host/tool record was collected at `2026-10-28T11:40:16.102646+00:00`; current host tool versions are distinguished from the historical simulator build. See `../common/HOST_ENVIRONMENT.md`, its JSON source, and `../common/build_provenance.json` in the attachment.


### To Reproduce

1. Extract `load_issue_materials.zip` and enter its `load/` directory. The package includes the exact assembly, binary, disassembly, symbols and original logs, with hashes in `repro/manifest.json`.
2. Verify the attachment without simulation:

   ```bash
   python3 ../common/repro.py --group load --verify-only
   ```

3. Replay on the recorded B200 setup:

   ```bash
   python3 ../common/repro.py --group load \
     --runner /home/haoran/kunminghu_v2_arch_audit_handoff_20261020/function_audit_20261021/run_emu_limited.sh
   ```

   Another host needs an equivalent resource-limited runner for its intended emu build. The helper runs explicit successful/failing exit calibrations before the load cases and writes new results separately from the archived evidence. Its optional byte-identical test-program rebuild is documented in `../common/REPRO_README.md`.

The `load_aba` program uses one `ld` instruction at PC `0x800000c8` and address `0x80002009`. It constructs the expected 64-bit value from eight `lbu` operations, preserves all CSR bits except bit 9, verifies full CSR readback, and waits 128 loop iterations after each change. It uses no UART, stack or NEMU.

| Image | Address / bit 9 phases | Recorded outcome |
|---|---|---|
| `load_aba` | `0x80002009`, on/off/on | Exit at `load_middle_only_bad`, PC `0x8000012c`; 930 retired instructions, 9,285 cycles |
| `load_enabled` | `0x80002009`, on/on/on | Normal self-check exit at `0x80000130` |
| `load_within` | `0x80002004`, on/off/on | Normal self-check exit at `0x80000130` |

The dedicated `load_middle_only_bad` exit requires value-error mask `s3=2` and exception mask `s8=0`. It therefore proves that the first and last enabled phases returned the byte-oracle value correctly, only the disabled phase returned an incorrect value, and none raised an architectural exception. The ordinary GOOD endpoint also accepts permitted exceptions; the two standalone GOOD controls alone are not used to claim exception-free completion.

The emu prints `ABORT` at this dedicated exit because self-check code 2, passed through `.word 0x0005006b` (`rs1=a0`), maps to `STATE_ABORT`. It is not a simulation assertion or NEMU comparison failure. The attached `a0=0` and `a0=51` calibration programs establish the success/failure exit convention.

All three load images have identical `.text`. Relative to `load_enabled`, `load_aba` changes only the configuration byte at file offset `0x1011`, from `0x02` to `0x00`. Relevant files are `repro/src/load_aba.S`, `repro/build/load_aba.dis`, and `repro/logs/load_aba.log`.


### Additional context

**Root cause.** Locations below refer to `src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala` at the recorded commit; numbered excerpts are attached as `evidence/source_excerpts.txt`.

- Lines 815 and 939-960 mark the crossing misaligned access and kill its DCache data read.
- Lines 1246-1247 gate `s2_mis_align` with `hd_misalign_ld_enable`.
- Line 1350 clears the final load-address-misaligned exception for the tested cacheable access independently of that enable. Lines 1363-1369 then allow safe wakeup/writeback when the remaining conditions hold.
- The replay/response protection at lines 1316-1323 uses the original exception, which remains set, so it does not prevent this path. Lines 1600 and 1804-1812 allow the operation to bypass MisalignBuffer and reach ordinary writeback.

**Waveform evidence.** The independently parsed QUIET waveform follows `ROB=(0,0x0f)`, address `0x80002009`:

| Timestamp | With bit 9 cleared |
|---|---|
| 9618, S1 | Valid, TLB hit, pipeline kill=0, redirect=0; misalignment kills the DCache data read |
| 9619, S2 | Original exception=1, final exception=0, `s2_mis_align=0`, safe wakeup=1, DCache response valid=0 |
| 9620, output | Same transaction has `ldout.valid && ready`, `rfWen=1`, and no MisalignBuffer enqueue |

With bit 9 enabled, the matching transaction retains `s2_mis_align=1`, suppresses safe wakeup and enters MisalignBuffer. Extracted data and provenance are in `evidence/wave_chain.json` and `evidence/wave_provenance.json`. The QUIET program is distinct from the commit-log program: sampled waveform data is not conflated with the other program's committed value.

**False-positive checks and limits.** The byte oracle and no-diff self-check remove UART/NEMU dependencies; CSR readback, settling and on/off/on recovery establish control-bit causality. The waveform tracks a valid TLB-hit transaction, not an earlier retry, killed instruction or redirected wrong-path operation. Raw-wave hashes, complete signal names, timestamp handling and unknown values were checked; sampled handshake conditions are corroborated by subsequent state changes. The differential error header's `0x80000444` is a stale `trap.pc` fallback; the actual load PC comes from its commit trace. The separate MMIO exception-code difference is not used as evidence for this defect.

The existing emu was not rebuilt or independently re-elaborated for this audit. Findings are scoped to its recorded hash and tested configuration, not current upstream, physical silicon, paging, vector operations or all memory types. Details are in `../common/audit/AUDIT_REPORT.md`.

**Related issue.** The store-side report concerns the analogous exception/enable mismatch for bit 8, but a separate implementation path leaves an unresolved SQ entry and blocks later load replay. The reports should be cross-linked after submission and fixed and regression-tested independently.

**AI assistance.** OpenAI Codex (GPT-6 family) assisted with the reproduction, evidence analysis and drafting. The package records commands, source/disassembly, original logs, hashes and audited signal data for independent review. This assistance does not replace the submitter's verification or the template attestations above.

[load_issue_materials.zip](https://github.com/user-attachments/files/34108979/load_issue_materials.zip)
