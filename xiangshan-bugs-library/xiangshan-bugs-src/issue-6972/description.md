### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and [XiangShan Documents](https://docs.xiangshan.cc/zh-cn/latest), and I believe this is a XiangShan RTL issue. 我已经阅读过 [RISC-V 指令集手册](https://github.com/riscv/riscv-isa-manual) 和 [香山文档](https://docs.xiangshan.cc/zh-cn/latest)，确认这应该是香山 RTL 的问题。
- [x] I have searched previous issues and PRs and did not find anything relevant. 我已经搜索过之前的 issue 和 PR，并没有找到相关的。
- [x] I have reproduced the issue using the latest commit on the default branch. 我已经使用默认分支最新的 commit 复现了问题。
- [x] If this report was generated with AI assistance (otherwise leave unchecked), I have verified the correctness of its content. 如果这是由 AI 生成的（否则请不要勾选），我已经验证了内容的正确性。

### Branch

kunminghu-v3

### Describe the bug

With pointer masking enabled (`PMLEN=16`), a load or store through a tagged pointer does not fire a matching `mcontrol6` load/store address trigger. The memory access itself uses the masked address and completes normally; only the trigger comparison uses the unmasked address.

The pointer-masking spec requires the masked address here (norm `pm_debug_trigger`): "Pointer masking is also applied, when applicable, to the memory access address when matching address triggers in debug."

In `NewLoadUnit` and `NewStoreUnit`, `MemTrigger` receives the raw issue address:

- [`NewLoadUnit.scala#L634`](https://github.com/OpenXiangShan/XiangShan/blob/8b15f06520bc7fc264095b11ece1d0e268153de7/src/main/scala/xiangshan/mem/pipeline/NewLoadUnit.scala#L634): `loadTrigger.io.fromLoadStore.vaddr := vaddr`
- [`NewStoreUnit.scala#L361`](https://github.com/OpenXiangShan/XiangShan/blob/8b15f06520bc7fc264095b11ece1d0e268153de7/src/main/scala/xiangshan/mem/pipeline/NewStoreUnit.scala#L361): `storeTrigger.io.fromLoadStore.vaddr := vaddr`

The masked address is computed in the TLB pre-check ([`TLB.scala#L680-L693`](https://github.com/OpenXiangShan/XiangShan/blob/8b15f06520bc7fc264095b11ece1d0e268153de7/src/main/scala/xiangshan/cache/mmu/TLB.scala#L680-L693), `effectiveVa`) and returned as `tlbResp.bits.fullva`, which both units already have at that point.

`TriggerCmp` compares bits `[VAddrBits-1:0]` = `[49:0]` in `DefaultConfig` ([`Trigger.scala#L240-L242`](https://github.com/OpenXiangShan/XiangShan/blob/8b15f06520bc7fc264095b11ece1d0e268153de7/src/main/scala/xiangshan/backend/fu/util/Trigger.scala#L240-L242)). With `PMLEN=16` the tag occupies bits `[63:48]`, so the trigger is missed whenever tag bit 48 or 49 differs from the masked address. Tags with bits `[49:48] = 0` (for example `0x1234 << 48`) still match, which is consistent with our results below.

Observed result on the whole core (log: `WHOLECORE-ORIG-vs-FIX.log` in https://gist.github.com/fireceW/2b7739c9e96a1287c8f4ab24ad58d558):

| test | original | with the fix below |
|---|---|---|
| `pmtrig.bin` (tagged `sd`, tag = bit 48) | FAIL: breakpoint not taken (`tohost = 0x221`, missed mask `0x10`) | PASS: all 5 breakpoints taken |
| `pmtrig_ld.bin` (tagged `ld`, tag = bit 48) | FAIL: breakpoint not taken (`tohost = 0x221`, missed mask `0x10`) | PASS: all 5 breakpoints taken |

### Expected behavior

A load or store whose pointer-masked address matches `tdata2` raises the configured breakpoint exception (`mcause = 3`), independent of the tag bits.

### Environment

- Hardware
  - CPU: Intel Xeon Platinum 8163 @ 2.50GHz (96 threads)
  - Memory (GB): 629
- Software
  - Operating system: Ubuntu 22.04.5 LTS
  - gcc version: gcc 11.4.0 (host); riscv64-unknown-elf-gcc 10.2 for the test programs
  - java version: OpenJDK 21.0.12.1
  - mill version: not used directly (sources compiled with scalac using the classpath of the project's mill build; Chisel 7.13.0, firtool 1.149.0)
  - Verilator 5.052
- Repo
  - XiangShan commit id: `b3f903bf2e7630e582500599629e7b4c5c981711`. The only later commit on `kunminghu-v3`, `8b15f06520bc7fc264095b11ece1d0e268153de7` (current HEAD), changes CI workflow files only; the RTL is identical.
  - NEMU / Spike: not used for this result (see below).
- Build & Run
  - Config: `DefaultConfig`, 1 core, generated with `top.TopMain --config DefaultConfig --num-cores 1 --target systemverilog --split-verilog`.
  - Simulator: whole-core `XSTop` built with Verilator and our own AXI memory testbench. It detects the end of the test from the committed store to `tohost`. This is not the upstream `emu` + difftest flow.
  - The simulator banner prints `Commit SHA is: a253c11639`; that is a stale `BuildInfo` string on our classpath, not the design revision.

### To Reproduce

Sources (`pmtrig.S`, `pmtrig_ld.S`, `crt.S`, `link.ld`) and the whole-core log: https://gist.github.com/fireceW/2b7739c9e96a1287c8f4ab24ad58d558

Binaries used for the log (rebuild with the command below; the output is byte-identical):
- `pmtrig.bin` sha256 `6ad6f94255e1de0dd121a9f69468c88a63099dc51f5d39422171cc82fec3eff9`
- `pmtrig_ld.bin` sha256 `332cb53c79fc0b6293188633631c7603b284209117408515e745697f7a8b8ff2`

The test runs in M-mode without paging. It sets `mseccfg.PMM = PMLEN16`, sets `mstatus.MIE = 1`, and programs trigger 0 as `mcontrol6` with `M=1`, `load=1`, `store=1`, `match=EQ`, `action=0` (breakpoint exception), `tdata2 = &target`. It then performs:

| step | access | expected |
|---|---|---|
| T0 | untagged `sd` to `&target` | breakpoint |
| T1 | untagged `ld` from `&target` | breakpoint |
| T2 | `sd` to `&target \| (0x1234 << 48)` | breakpoint |
| T3 | `ld` from `&target \| (0x1234 << 48)` | breakpoint |
| T4 | `sd` (`pmtrig.S`) / `ld` (`pmtrig_ld.S`) to `&target \| (1 << 48)` | breakpoint |

The breakpoint handler records which steps trapped. At the end the program writes `tohost = 1` if all five trapped, otherwise `tohost = ((0x100 | missed_mask) << 1) | 1`.

```
riscv64-unknown-elf-gcc -march=rv64gc -mabi=lp64d -mno-relax -nostdlib -nostartfiles -static -T link.ld crt.S pmtrig.S -o pmtrig.elf
riscv64-unknown-elf-objcopy -O binary pmtrig.elf pmtrig.bin
```

(gcc 10.2.0. With these flags the output is byte-identical to the `pmtrig.bin` / `pmtrig_ld.bin` that produced the log (hashes above). The program is linked at `0x80000000` and `tohost` is at `0x80001000`.)

On the original RTL only T4 is missed (`tohost = 0x221`). T2/T3 are taken because bits `[49:48]` of `0x1234 << 48` are zero.

Fix used for the "with the fix" column (pass the masked `fullva` to the triggers):

```diff
--- a/src/main/scala/xiangshan/mem/pipeline/NewLoadUnit.scala
+++ b/src/main/scala/xiangshan/mem/pipeline/NewLoadUnit.scala
@@ -634 +634 @@
-  loadTrigger.io.fromLoadStore.vaddr := vaddr
+  loadTrigger.io.fromLoadStore.vaddr := fullva
--- a/src/main/scala/xiangshan/mem/pipeline/NewStoreUnit.scala
+++ b/src/main/scala/xiangshan/mem/pipeline/NewStoreUnit.scala
@@ -361 +361 @@
-  storeTrigger.io.fromLoadStore.vaddr := vaddr
+  storeTrigger.io.fromLoadStore.vaddr := fullva
```

### Additional context

- The same problem was reported for `kunminghu-v2` in #6214 and fixed there by #6311 (`LoadUnit.scala` / `StoreUnit.scala`, merged into `kunminghu-v2` only). The `kunminghu-v3` units `NewLoadUnit` / `NewStoreUnit` did not get that change, and we found no issue or PR covering them.
- #6742 already made `NewStoreUnit` report the masked address in `tval`; the trigger input was not changed.
- Open PR #6942 reworks breakpoint/trigger handling on `kunminghu-v3` but does not change this input.
- We only tested scalar `ld`/`sd` in M-mode with `mseccfg.PMM = PMLEN16`.
- This report was generated with AI assistance: the opencode agent framework with the models Claude Opus 5.5 and Gemini 4 Carbon was used for the source analysis, the test programs, running the simulations and writing this text. The submitter has reviewed the content.
