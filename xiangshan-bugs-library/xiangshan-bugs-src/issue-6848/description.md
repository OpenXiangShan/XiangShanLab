### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and [XiangShan Documents](https://docs.xiangshan.cc/zh-cn/latest), and I believe this is a XiangShan RTL issue. 我已经阅读过 [RISC-V 指令集手册](https://github.com/riscv/riscv-isa-manual) 和 [香山文档](https://docs.xiangshan.cc/zh-cn/latest)，确认这应该是香山 RTL 的问题。
- [x] I have searched previous issues and PRs and did not find anything relevant. 我已经搜索过之前的 issue 和 PR，并没有找到相关的。
- [x] I have reproduced the issue using the latest commit on the default branch. 我已经使用默认分支最新的 commit 复现了问题。
- [x] If this report was generated with AI assistance (otherwise leave unchecked), I have verified the correctness of its content. 如果这是由 AI 生成的（否则请不要勾选），我已经验证了内容的正确性。

### Branch

kunminghu-v2

### Describe the bug

A PageTableCache `stage1Hit` request can reuse `pte_valid=1` and `pageFault=1` left by an earlier, unrelated walk. These stale bits suppress `gvpn_gpf`, although the incoming cached stage-1 translation has GPA `2^41`, outside the Sv39x4 address range. The final G-stage request completes without a guest-page fault and the load returns data from an unrelated physical page.

The reproducer uses a same-VMID `hgatp` Bare-to-Sv39x4 switch followed by an address-specific `HFENCE.GVMA`. **The report is not based solely on requiring the new-mode fault after this insufficient fence.** The observed result is neither the old Bare translation nor the new Sv39x4 result; see Expected behavior.

The relevant sequence in `src/mm007f1b.S` is:

1. With `vsatp=Sv39`, `hgatp=Bare`, VMID 0, access VA `0x140000000`. Its unchanged 4 KiB VS leaf contains PPN `0x20000000`, so GPA is `2^41`. This populates an `onlyStage1` PageTableCache entry. The load returns zero.
2. Write `hgatp=0x8000000000080050` (Sv39x4, VMID 0), then execute `hfence.gvma t3,zero` with `t3=0x1000`. The address operand denotes GPA>>2, not a virtual address.
3. Access an invalid VS root PTE (`V=0`) at VA `0x80000000`, leaving the PTW fault state set.
4. Access VA `0x140000000` again. The cached stage-1 result is reused, the G-stage fault check is suppressed, and the load returns `0xdead000012345678`.

The existing no-diff run records:

| Phase | Operation | Trap record | Load result |
|---|---|---|---|
| 0 | Target under Bare | No trap | `0` |
| 4 | Cold VS leaf mapping to the same out-of-range GPA | `0x15`, load guest-page fault | `0xBAD` sentinel unchanged |
| 5 | Invalid VS PTE walk | `0xd`, load page fault | Sentinel unchanged |
| 6, 7 | Cached target after phase 5 | No trap | `0xdead000012345678` |
| 9, 11 | Target after full `hfence.gvma x0,x0` | `0x15` | Sentinel unchanged |

Phase 11 is also preceded by an invalid-PTE walk. Thus stale fault state alone is insufficient; the surviving cached entry is also necessary in this reproducer.

Full, unedited logs are in `logs/r10_alias_nodiff.log` and `logs/r12_alias_diff.log`. In the diff run, DUT `scause=0xd` is the CSR left by phase 5, **not** a new page-fault trap on the phase-6 load. The no-diff program's zero trap-record field means that its trap handler was not entered for that phase.

### Expected behavior

The target must not acquire a mapping to the magic page at PA `0x80020000`.

There is an important fence qualification. The [H specification, Hypervisor Memory-Management Fence Instructions](https://docs.riscv.org/reference/isa/v20260120/priv/hypervisor.html) requires `HFENCE.GVMA` with `rs1=x0` to order a same-VMID MODE change, including a change involving Bare. Therefore NEMU's new-mode GPF is not, by itself, a complete oracle for this under-fenced sequence.

However, omitting a fence does not authorize arbitrary mappings: the [translation-cache rules](https://docs.riscv.org/reference/isa/v20260120/priv/supervisor.html) constrain historical translations, and the [maintainer's clarification](https://github.com/riscv/riscv-isa-manual/issues/2181#issuecomment-3149461486) explicitly distinguishes an unspecified choice of results from undefined behavior.

For this test's fixed translation history:

| Translation state | Result for the target GPA |
|---|---|
| Old `hgatp=Bare` | Identity mapping to PA `2^41`; the old access reads zero |
| New `hgatp=Sv39x4` | Guest-page fault because GPA bit 41 is set |
| Observed DUT | Successful load of the magic value associated with PA `0x80020000` |

The VS PTEs and target data are not modified. The VS page-table pages themselves are identically mapped under both G-stage states. The magic bytes occur only at binary offset `0x20000`, and the simulated RAM size keeps PA `2^41` in range without wrapping to that offset. Thus the returned value cannot be explained by retaining the old translation. The wrong physical-page attribution follows from the source, memory layout, and unique data; it is not presented as a direct final-bus-PA waveform observation.

After a full fence, the same existing run correctly raises cause 21, with `htval=0x8000000000`. The general H specification also permits zero `htval`; zero alone is not the bug criterion.

### Environment

- Hardware, observed on the evidence host during review: Intel Xeon 6960P; approximately 2263 GiB RAM; approximately 1758 GiB root filesystem.
- Software, observed during review: Ubuntu 26.04 LTS; workload GCC `riscv64-unknown-linux-gnu-gcc (g6afcc4f6d) 16.1.0`. These observations are not claimed to reconstruct every historical emulator-build dependency.
- XiangShan: `fe2288cb09361b65afa26be3f403520c4b496dbf`, `kunminghu-v2`, `DefaultConfig`; the checked source tree was clean.
- difftest: `3729300ae233816d332d057f170472ebe35147b0`.
- Emulator SHA-256: `8378454e8d10cf24903ab5778498ccbb00697226ec341dbaec814ec270fe4cb9`.
- NEMU: existing `ready-to-run/riscv64-nemu-interpreter-so`, SHA-256 `3e9d63c31d2cd68ec68d55fa9df56ec990ce9a9fd3a8e59709ecb92f21176c40`; the NEMU source commit is not recorded in the supplied run evidence.
- Positive image SHA-256: `715389c8b0ba80825608690bc96324da7c38a99cca19331e282c410005001caf`.
- The existing emulator is trace-enabled and reports build time October 19, 2026. Its full historical build command/tool versions are not asserted here. The workload build and exact run arguments are provided in the package.
- A static-only read of current `kunminghu-v2` HEAD `7b16188f7f18a97be69c9b1a2dae8a446eec3b47` on 2026-11-03 still shows the relevant gate and missing clear. That is not a latest-HEAD dynamic result.

### To Reproduce

The accompanying `mm007-f1-verification.zip` contains source, existing BIN/ELF files, full positive/control logs, the complete positive waveform in FST, and a focused PTW FST with conversion checks. Attach that archive when submitting this draft.

1. Unpack and check file integrity: `sha256sum -c CHECKSUMS.sha256` from the `mm007-f1` directory.
2. Use `bin/mm007f1b.bin`, or build a separate copy with `CROSS_COMPILE=/path/to/riscv64-unknown-linux-gnu- bash tools/build.sh`.
3. Run with the identified emulator/reference, or with a separately built emulator whose revision is explicitly recorded:

   ```sh
   "$EMU" --image=bin/mm007f1b.bin --no-diff --max-cycles=300000
   "$EMU" --image=bin/mm007f1b.bin --diff="$NEMU" --max-cycles=300000
   ```

   These are reproduction instructions; no new simulation was used to prepare this report. `tools/run.sh` records commands and exit status in a new directory and applies a time limit.

4. Check phase 6/7 for the magic value and phase 9/11 for the full-fence controls. The no-diff program prints `MM007F1B DONE` and then deliberately spins; a cycle-limit exit is not an assertion-based pass. The diff run aborts after the execution paths diverge; the reported ABORT PC is not itself the positive load's PC.
5. Open `waves/w4_alias_ptw.fst` in GTKWave for the focused PTW view, or `waves/w4_alias_full.fst` for all signals in the original positive capture. The focused file is a lossless FST conversion of PTW signals selected directly from the original VCD, not a waveform reconstructed from the report's text trace. The selected events and key handshake timestamps are checked after conversion. Large source VCDs and unrelated waveform windows are omitted from the compact package.

The direct PTW scope is `TOP.SimTop.cpu.l_soc.core_with_l2.core.memBlock.inner_ptw.ptw.ptw`. The recorded timestamp chain, preserved in the FST, is:

| Recorded timestamp | Evidence |
|---|---|
| 11789 | `io_req_valid && io_req_ready`, `io_req_bits_stage1Hit=1` |
| 11790 | `io_hptw_req_valid && io_hptw_req_ready`, `io_hptw_req_bits_gvpn=0x20000000` |
| 11809 | `io_resp_valid && io_resp_ready`, `io_resp_bits_h_resp_gpf=0` |

Throughout this chain, `pte_valid=1`, `pageFault=1`, `level=2`, `full_gvpn_reg=0x20000000`, and `gvpn_gpf=0`. The original file declares a 1 ps timescale; these numbers are preserved VCD timestamps, not a newly inferred physical clock period.

### Additional context

[mm007-f1-verification.zip](https://github.com/user-attachments/files/34308057/mm007-f1-verification.zip)
