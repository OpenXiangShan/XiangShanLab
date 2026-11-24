### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and [XiangShan Documents](https://docs.xiangshan.cc/zh-cn/latest), and I believe this is a XiangShan RTL issue. 我已经阅读过 [RISC-V 指令集手册](https://github.com/riscv/riscv-isa-manual) 和 [香山文档](https://docs.xiangshan.cc/zh-cn/latest)，确认这应该是香山 RTL 的问题。
- [x] I have searched previous issues and PRs and did not find anything relevant. 我已经搜索过之前的 issue 和 PR，并没有找到相关的。
- [x] I have reproduced the issue using the latest commit on the default branch. 我已经使用默认分支最新的 commit 复现了问题。
- [x] If this report was generated with AI assistance (otherwise leave unchecked), I have verified the correctness of its content. 如果这是由 AI 生成的（否则请不要勾选），我已经验证了内容的正确性。

### Branch

kunminghu-v2

### Describe the bug

A synchronous exception from VU mode with `mstatus.MDT=0` and 
`mnstatus.NMIE=0` reaches CSR, but the core enters critical-error instead of
entering the installed M-mode trap handler. The issue also reproduces with a
scalar U-mode ECALL, without paging or vector instructions.

The suspected source defect is the unrestricted NMIE=0 critical-error
predicate in `NewCSR.scala`, together with NMIE gating of normal trap-entry
events. No RTL fix has been applied or validated.

### Configuration and workload

- Core: Kunminghu-v2, commit `1fd6f257`.
- Simulation: single core, CHI E.b VIP memory harness, Verilator 5.051,
  FST tracing enabled; clock period 2 ns. 
- Reset PC: `0x80000000`; NMIE resets to 0.
- Original workload: `kunminghu-v2-vlseg4e32ff-guest-gstage-pagefault-replay.ELF`.
- ELF size: 11960 bytes; SHA-256:   `2a0958c8`.
- The unchanged workload fails to reach its final NOP at `0x40000030`
  within 15000 cycles. Its parking JAL is at `0x40000034`.
- Machine: Intel-class x86-64, 16 cores / 92 GB RAM, Ubuntu 24.04, OpenJDK 21, Verilator 5.051, g++ 13.3.

### Expected and observed behavior

The workload installs an M-mode handler at `mtvec=0x82800000`, clears MDT,
and enters VU mode. A G-stage mapping for a VS page-table page is invalidated.
The ordinary segment load at guest PC `0x4000002c` then incurs a load
guest-page fault while accessing VA `0x40400000`; the faulting VS page-table
GPA is `0x20004000`.

Expected: enter the M-mode handler with `mcause=21`, record the fault state,
repair the mapping, and resume execution.

Observed: the exception reaches CSR while the current mode is VU and MDT=0,
but `criticalErrorStateInCSR` asserts and the sticky critical-error state is
set on the next cycle. The handler is not entered; `mcause` remains at its 
initialization value `0x18`.

### Expected behavior

After the M handler fixes the page table and executes `hfence.gvma`, the replayed `vlseg4e32ff.v` completes, the subsequent `vlseg4e32.v` (crossing a 4K boundary into an already-mapped page) completes, the marker instruction at 0x40000030 retires, and the core keeps executing the idle loop forever. The core must not stop committing. (For the faulting vector instruction this is also the restartability required by the RVV spec v1.0 §3.7: `vstart` records the trapping element and the instruction re-executes correctly after the handler.)

### Waveform evidence

Cycle labels below are FST time divided by 2000 ps, without subtracting reset.
Signal names are relative to the core instance.

| Cycle | Observation |
| --- | --- |
| 5540 | First masked `vlseg4e32ff.v` enters VSegmentUnit: robIdx=61, fuOpType=0x90, isFof=1. |
| 5548 | FOF VL-update micro-op buffered (`fofBufferValid=1`). |
| 6446–6457 | First FOF truncates VL to 3 and writes back normally, without a trap; the core remains in VU. |
| 6489 | Second, ordinary `vlseg4e32.v` enters: robIdx=71, fuOpType=0x80, isFof=0. |
| 6617–6624 | ROB receives valid writebacks for robIdx=71 with exceptionVec[21]=1; ROB state is idle and redirect=0. |
| 6630 | ROB exception.valid=1. |
| **6632** | **CSR trap.valid=1; current PRVM=0, V=1 (VU), MDT=0, NMIE=0. `criticalErrorStateInCSR=1`.** |
| **6633** | **`criticalErrorState=1` is latched.** |

The exception writeback is not lost before CSR. In particular,
`writebackNeedFlush` denotes a writeback carrying an exception/flush/replay
condition; it is not the writeback rejection predicate. See
[CtrlBlock.scala, lines 124–143](https://github.com/OpenXiangShan/XiangShan/blob/1fd6f257469ba99a00004595c92a3e5d482e9619/src/main/scala/xiangshan/backend/CtrlBlock.scala#L124-L143), where
`killedByOlder` filters writeback valid separately.

### Additional context

Suspected root cause (from code inspection and wave debug):

All links below are pinned to the tested commit.

#### 1. Overbroad critical-error predicate

[`src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala`, lines 1518–1521](https://github.com/OpenXiangShan/XiangShan/blob/1fd6f257469ba99a00004595c92a3e5d482e9619/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala#L1518-L1521):

```scala
val criticalErrors = Seq(
  ("csr_dbltrp_inMN", !mnstatus.regOut.NMIE && hasTrap && !(entryDebugMode || debugMode)),
)
criticalErrorStateInCSR := criticalErrors.map(criticalError => criticalError._2).reduce(_ || _).asBool
```

This condition does not check whether the hart was executing in M-mode before
the exception. It therefore matches the observed ordinary VU exception.

The resulting state is sticky at [line 297](https://github.com/OpenXiangShan/XiangShan/blob/1fd6f257469ba99a00004595c92a3e5d482e9619/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala#L296-L297):

```scala
val criticalErrorState = RegEnable(true.B, false.B,
  io.fromTop.criticalErrorState || criticalErrorStateInCSR)
```

#### 2. Normal trap entry is also blocked by NMIE

The M-mode trap-entry event at [line 802](https://github.com/OpenXiangShan/XiangShan/blob/1fd6f257469ba99a00004595c92a3e5d482e9619/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala#L801-L804) is gated by NMIE:

```scala
trapEntryMEvent .valid := hasTrap && entryPrivState.isModeM && !dbltrpToMN &&
  !entryDebugMode && !debugMode && !nmi && mnstatus.regOut.NMIE
```

The HS/VS entry events at lines 803–804 have the same gate. Removing only the
critical-error predicate would therefore leave normal entry disabled.
Also, `entryPrivState.isModeM` is the trap's destination mode, not the mode
executing before the trap; it cannot substitute for that distinction.
Any fix needs to preserve genuine MDT/double-trap, RNMI and debug behavior.

#### Specification basis

The relevant restriction exists in 2023 and later
extension revisions, before the corresponding XiangShan implementations:

| Date | Historical source |
| --- | --- |
| 2023-03-20 | Official [Smrnmi clarification](https://github.com/riscv/riscv-isa-manual/commit/6df80fc2170219e94f54de4063f39bcb806870dd), titled “Smrnmi: clarify NMIE only affects M-mode synchronous exception behavior.” |
| 2024-04-11 | Official [Smrnmi 0.5 frozen specification](https://docs.riscv.org/reference/isa/v20240411/priv/rnmi.html), RNMI Operation: the NMIE=0 exception rule is restricted to execution in M-mode. MRET/SRET remain unaffected. |
| 2024-07-03 | Official [Smrnmi ratified 1.0 source](https://github.com/riscv/riscv-isa-manual/blob/4ba8da3ca53a186860aa441d2c11da4f3f702fea/src/rnmi.adoc#L156-L165) retains that restriction. |
| 2024-08-23 | Official [Double Trap Extensions 1.0 ratified snapshot](https://github.com/riscv/riscv-double-trap/blob/639509348415f8d585647353268eb8b53490e499/src/riscv-double-trap.adoc#L187-L219) specifies normal delivery when MDT=0 and qualifies the additional NMIE=0 unexpected-trap condition by execution in M-mode. |

The tested revision includes XiangShan's [Smrnmi implementation, merged
2024-09-05](https://github.com/OpenXiangShan/XiangShan/commit/c2a2229d405d725d960325a4a1899af7603d82af),
[Ss/Smdbltrp implementation, merged
2024-10-29](https://github.com/OpenXiangShan/XiangShan/commit/6808b8030a5ec6cf43824f1d575b050426f1c427),
and [critical-error implementation, merged
2024-11-01](https://github.com/OpenXiangShan/XiangShan/commit/85a8d7ca95be7636399af9f3c39382ab20231da7).
All three commits were verified as ancestors of the tested commit.
The [Kunminghu-v2 instruction-set list](https://docs.xiangshan.cc/projects/user-guide/en/kunminghu-v2/instruction-set/)
also lists Smrnmi and Smdbltrp.

For the observed U/VU exception, MDT=0, so the ordinary M-mode-targeted trap
should be delivered. The extra NMIE=0 unexpected-trap condition does not 
apply merely because the destination is M-mode. NMIE resets to 0 and may be
explicitly set to 1; MRET/SRET do not change it. Entering U/VU with NMIE=0 is
therefore not, by itself, an illegal software state.

An explicit complete-manual snapshot pin was not found in the checked source                                                                                                                              
revision. Maintainers can confirm the intended extension revision, but the
restriction cited here is present in both the 2024 frozen and ratified
specifications, not introduced only in the 20260120 release.

The experimentally checked source modes are U and VU; other modes have not
been tested in this report.
