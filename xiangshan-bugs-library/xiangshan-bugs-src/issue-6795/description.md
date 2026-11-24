### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and [XiangShan Documents](https://docs.xiangshan.cc/zh-cn/latest), and I believe this is a XiangShan RTL issue. 我已经阅读过 [RISC-V 指令集手册](https://github.com/riscv/riscv-isa-manual) 和 [香山文档](https://docs.xiangshan.cc/zh-cn/latest)，确认这应该是香山 RTL 的问题。
- [x] I have searched previous issues and PRs and did not find anything relevant. 我已经搜索过之前的 issue 和 PR，并没有找到相关的。
- [x] I have reproduced the issue using the latest commit on the default branch. 我已经使用默认分支最新的 commit 复现了问题。
- [x] If this report was generated with AI assistance (otherwise leave unchecked), I have verified the correctness of its content. 如果这是由 AI 生成的（否则请不要勾选），我已经验证了内容的正确性。

### Branch

kunminghu-v2

### Describe the bug

On kunminghu-v2, if `sstatus.SDT` is set while `menvcfg.DTE=1` and DTE is subsequently cleared, a write to set `sstatus.SIE` can be silently discarded. Software sees `sstatus.SDT=0`, but `SIE` remains 0 after `csrsi sstatus, 2`.
                                                                                                                                                                                                
The original DTE04 test reaches its explicit self-check failure `fail_013` at `0x800003e4` (`disabled-dte-must-allow-sie`). This address is a deliberate failure loop; reaching it does not by itself mean that the CPU hung. The observed failure is the CSR readback. The effect on interrupt delivery has not been separately tested.

### Expected behavior

The ratified **Ssdbltrp 1.0** specification states that when `menvcfg.DTE=0`, the implementation behaves as though Ssdbltrp is not implemented and `sstatus.SDT`, `vsstatus.SDT`, and `henvcfg.DTE` are read-only zero. It also permits an explicit write to set SIE when SDT is already zero or is being cleared by that write.

Consequently, after DTE is cleared, `csrsi sstatus, 2` should set SIE to 1 and a subsequent read should return `sstatus & (SDT | SIE) == 2`. The original test instead observes 0.

CSR field modulation permits the underlying aliased storage bit to retain its old value while the `sstatus.SDT` field is read-only zero. The defect is that this hidden value still triggers the software-visible SIE-clearing side effect.

### Environment

- CPU RTL: XiangShan kunminghu-v2, commit `1fd6f257469ba99a00004595c92a3e5d482e9619`.
- Simulation: CHI VIP qualification environment, single core. Both a performance binary and a wave-enabled binary were used.
- Test ELF: `kunminghu-v2-dte-disable-after-enable-gates-reads.ELF`.
- ELF MD5: `958bf9843a9c08572ddd0c5ccc966439`.
- Reference model for the complete ELF: NEMU built from `112f2fd1e68d1c8978f964f6a2846104dcd928e8`.
- Specification basis: ratified Ssdbltrp 1.0 (2024-08-23) and the 2024-04-11 privileged ISA CSR field-modulation rules.


### To Reproduce

Run the supplied ELF with reset vector `0x80000000`. Its privileged harness executes the following sequence after initialization and checks of the generated integer kernel. The comments summarize intermediate self-checks; this is an excerpt rather than a complete standalone program.

```asm
li    t0, 1 << 59
csrw  menvcfg, t0       # menvcfg.DTE = 1
csrw  henvcfg, t0       # henvcfg.DTE = 1
li    t0, 1 << 24
csrw  sstatus, t0       # sstatus.SDT = 1; SIE is cleared
csrw  vsstatus, t0      # vsstatus.SDT = 1
li    t0, 0
csrw  menvcfg, t0       # menvcfg.DTE = 0
# Test verifies sstatus.SDT, vsstatus.SDT and henvcfg.DTE read as zero.
li    t0, 0
csrw  menvcfg, t0       # redundant write present in the original ELF
csrsi sstatus, 2        # instruction at 0x80000218: request SIE = 1
csrr  t0, sstatus
# Test checks (sstatus & ((1 << 24) | 2)) == 2.
```

**Expected:** the check passes, with SDT=0 and SIE=1.  
**Observed:** SIE remains 0 and the test branches to `fail_013` at `0x800003e4`.

The failure was also reproduced with standalone minimal CSR tests using both CSRRSI and CSRRW, including an HS-mode test. Control tests pass when SDT is cleared before DTE is disabled, when the underlying SDT was never set, and when DTE remains enabled and SDT correctly prevents SIE from being set.

### Additional context

**Root cause.** In [`MachineLevel.scala`, lines 578–593](https://github.com/OpenXiangShan/XiangShan/blob/1fd6f257469ba99a00004595c92a3e5d482e9619/src/main/scala/xiangshan/backend/fu/NewCSR/MachineLevel.scala#L578-L593), the `sstatus` alias write selects the underlying `reg.SDT` when DTE is disabled:

```scala
writeMstatusSDT := w.wdataFields.SDT.asBool
writeSstatusSDT := Mux(this.menvcfg.DTE.asBool, wAliasSstatus.wdataFields.SDT.asBool, reg.SDT.asBool)
writeSDT        := Mux(w.wen, writeMstatusSDT, wAliasSstatus.wen && writeSstatusSDT)
// ...
when (writeSDT) {
  reg.SIE := false.B
}
```

When DTE=0 and the retained raw `reg.SDT=1`, an `sstatus` write causes `writeSDT` to assert and overrides the requested SIE value with zero. When the retained raw SDT is zero, the DTE-disabled SIE write succeeds. The `vsstatus` implementation separately gates its SDT write side effect with both DTE controls.

**Waveform evidence.** In a wave-enabled run of the original ELF, immediately before the CSR-module rising edge at 6,026,000 ps, `menvcfg_DTE=0`, raw `reg_SDT=1`, visible `sstatus_SDT=0`, `wAliasSstatus_wen=1`, `w_wen=0`, and alias write data is `0x200000002` (SIE=1, SDT=0). The trap/return valid signals are all zero. SIE remains 0 after the edge.

**Candidate fix and validation.** A localized candidate changes only the DTE-disabled arm of `writeSstatusSDT` to zero, leaving underlying SDT storage intact:

```diff
-  writeSstatusSDT := Mux(this.menvcfg.DTE.asBool, wAliasSstatus.wdataFields.SDT.asBool, reg.SDT.asBool)
+  writeSstatusSDT := this.menvcfg.DTE.asBool && wAliasSstatus.wdataFields.SDT.asBool
```

An isolated test of the actual generated `MStatusModule` covers 16 alias-write combinations and two direct `mstatus` write controls: the original module fails 2 of 18 tests, while the equivalent one-expression generated-SV change passes all 18. The modified Scala full core has not yet been rebuilt or regressed. Gating the entire `writeSDT` expression would also change direct `mstatus` writes and has not been established as an equivalent fix; direct `mstatus` read/write behavior under DTE=0 requires separate investigation.

**Reference-model caveat.** NEMU reaches the same self-check failure because its `sstatus` write logic also uses retained `mstatus.SDT` to clear SIE when DTE=0. RTL and reference model can therefore agree on the wrong result, so difftest alone may miss this bug. FORCE/Handcar was used for the separate integer kernel, not as the complete-ELF reference for this run.

A locally saved patch for [Spike issue #2374](https://github.com/riscv-software-src/riscv-isa-sim/issues/2374), commit [`b6daaa6414`](https://github.com/riscv-software-src/riscv-isa-sim/commit/b6daaa6414df00d3d5ec5a76e9dc01578cf49000), describes a similar hidden-DTE-state problem. Execution of this exact ELF on a patched Spike has not been verified here.
