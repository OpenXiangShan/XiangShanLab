### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and [XiangShan Documents](https://docs.xiangshan.cc/zh-cn/latest), and I believe this is a XiangShan RTL issue. 我已经阅读过 [RISC-V 指令集手册](https://github.com/riscv/riscv-isa-manual) 和 [香山文档](https://docs.xiangshan.cc/zh-cn/latest)，确认这应该是香山 RTL 的问题。
- [x] I have searched previous issues and PRs and did not find anything relevant. 我已经搜索过之前的 issue 和 PR，并没有找到相关的。
- [x] I have reproduced the issue using the latest commit on the default branch. 我已经使用默认分支最新的 commit 复现了问题。
- [ ] If this report was generated with AI assistance (otherwise leave unchecked), I have verified the correctness of its content. 如果这是由 AI 生成的（否则请不要勾选），我已经验证了内容的正确性。

### Branch

kunminghu-v2

### Describe the bug

While `mnstatus.NMIE = 0`, any exception raised in S-mode or U-mode whose `medeleg` bit is set — e.g. an `ecall` from S-mode, or a page fault in U-mode — is not delivered to `stvec`. Instead `csr_dbltrp_inMN` fires, `criticalErrorState` is latched and the ROB stops committing forever. No architectural state is updated, so the hart simply freezes at the faulting instruction.

On current kunminghu-v2/v3, `NMIE` resets to 0 and cannot be cleared by software (#6100), so this is the behaviour of the core from reset until firmware executes `csrs mnstatus, NMIE` — any firmware or bare-metal environment that does not know Smrnmi and drops to S/U-mode will stop at the first delegated exception.

**Spec.** The critical-error rule for `NMIE = 0` is restricted to traps taken while executing in M-mode; `NMIE = 0` by itself only masks interrupts:

- `smrnmi.adoc`, `norm:mnstatus_nmie_disable`: "When NMIE=0, *all interrupts* are disabled." — nothing about exceptions.
- `machine.adoc` (Smdbltrp), `norm:trap_unexp_mnstatus_nmie_0`: "a trap that occurs **when executing in M-mode** with mnstatus.NMIE set to 0 is an *unexpected trap*" → critical error (`norm:trap_unexp_hndl_no_rnmi`). The paragraph is introduced by "When a trap is to be taken **into M-mode** ..." (`norm:trap_exp`).
- `smrnmi.adoc`, `norm:rnmi_exception_nmie0`: "If the hart encounters an exception **while executing in M-mode** with the mnstatus.NMIE bit clear, the actions taken are the same as if the exception had occurred while NMIE were set, except that the pc is set to the RNMI exception trap handler address."

A delegated exception is neither raised in M-mode nor taken into M-mode; it is handled entirely in S-mode (`sepc`/`scause`/`sstatus`) and touches none of the state an RNMI handler is protecting. Nothing in the spec changes its handling when `NMIE = 0`. (The M-mode half of the rule — a trap raised in M-mode with `NMIE = 0` → critical error — is implemented correctly and should stay.)

**Root cause**, `NewCSR.scala`: all four trap-entry events are gated by `NMIE`, and the critical-error predicate only checks `NMIE && hasTrap` — neither looks at the privilege mode the trap comes from or goes to.

```scala
// trap entries: 2acbf327c L802-805; kunminghu-v2 HEAD 5d393413 L801-804; kunminghu-v3 HEAD a2340a516 L836-839
trapEntryMNEvent.valid := ((hasTrap && nmi) || dbltrpToMN) && ... && mnstatus.regOut.NMIE
trapEntryMEvent .valid := hasTrap && entryPrivState.isModeM  && ... && mnstatus.regOut.NMIE
trapEntryHSEvent.valid := hasTrap && entryPrivState.isModeHS && ... && mnstatus.regOut.NMIE   // <- delegated traps
trapEntryVSEvent.valid := hasTrap && entryPrivState.isModeVS && ... && mnstatus.regOut.NMIE
// criticalErrors: 2acbf327c L1520; v2 HEAD L1519; v3 HEAD L1624 (HEAD adds "|| debugMode" to the exclusion, #6224)
("csr_dbltrp_inMN", !mnstatus.regOut.NMIE && hasTrap && !entryDebugMode)
```

`TrapHandleModule` has already classified the trap correctly as an HS entry (`handleTrapUnderHS = !privState.isModeM && hsHasTrap`, L70); the classification is simply overridden by the `NMIE` gate, and `Rob.scala` blocks commit (`blockCommit := ... || criticalErrorState`, L770). The same gate on `trapEntryMEvent` also turns a *non-delegated* S/U → M exception with `NMIE = 0` into a critical error, although per `norm:trap_exp` it should be delivered normally (MDT 0 → 1); the attached PoC exercises only the delegated case.

Spike agrees with the spec: `take_trap` decides delegation from `medeleg`/`hsdeleg` first (`processor.cc:431-475`) and consults `nmie` only for M-mode-bound traps (`:510-524`). NEMU has the same deviation as XiangShan (`intr.c:114`: `!nmie && trap` → critical error before the delegation check), so difftest cannot catch this.

### Expected behavior

`mnstatus.NMIE = 0`, `medeleg[9] = 1`, `stvec = S_handler`, `ecall` executed in S-mode:

- Expected (spec / Spike `3e7bf30d`, `--isa=rv64gc_smrnmi_smdbltrp`): trap to `S_handler`, `scause = 9`, `sepc = &ecall`; `sret` returns normally.
- XiangShan: no trap entry, `criticalErrorState = 1`, commit blocked, the hart never executes another instruction.

How `NMIE = 0` is reached during S/U execution:

- Current v2/v3 (since #6100): it is the reset value and software cannot clear it once set, so the hart is in this state until firmware writes `mnstatus.NMIE = 1`. Upstream OpenSBI does so when it detects Smrnmi (`sbi_hart.c`), so a normal Linux boot is fine; an OpenSBI without `smrnmi` in the DT, a vendor bootloader, or any bare-metal/test framework that does not know Smrnmi is not.
- Any version: an RNMI handler that returns with `mret` instead of `mnret` leaves `NMIE = 0` (spec: interrupts stay masked; XiangShan: first S/U exception stops the hart).
- Tested build 2acbf327c: `NMIE` resets to 1 (`MachineLevel.scala:606`) and the write-0 guard is commented out (`:429-434`, "allow software to set nmie close for testing"), so `csrc mnstatus, 8` reaches the state directly.

### Environment

- XiangShan kunminghu-v2, commit `2acbf327c` (2026-06-24, v3.2.2-alpha-1168-g2acbf327c), DefaultConfig (`--issue E.b`). The cited gating is unchanged on kunminghu-v2 HEAD `5d393413` and kunminghu-v3 HEAD `a2340a516` (2026-09-06); the only edits to these lines since 2acbf327c are `&& !nmi` on the HS/VS entries (#6258, v2) and `|| debugMode` in the critical-error exclusion (#6224), neither of which qualifies the rule by privilege mode.
- NEMU `67da68a2` (ready-to-run e79a631) — same deviation; Spike upstream `3e7bf30d` — reference; difftest `7b5fc3bb2`.

### To Reproduce

Minimal sequence:

```
# M-mode
li t0, 1<<9 ; csrw medeleg, t0            # delegate ecall-from-S
la t0, s_handler ; csrw stvec, t0
# NMIE is 0 out of reset on current v2/v3; on 2acbf327c: csrc mnstatus, 8
li t0, 1<<11 ; csrs mstatus, t0 ; li t0, 1<<12 ; csrc mstatus, t0   # MPP = S
la t0, s_code ; csrw mepc, t0 ; mret
s_code:
ecall            # expected: s_handler, scause=9.  XiangShan: critical error, hart stops here
```

Attached `nmie0-deleg-poc` (nexus-am, M-mode start): PMP allow-all, `mtvec`/`stvec`, `medeleg = 1<<9`. If `NMIE` reads 1 at reset it first runs a control pass (NMIE=1: S `ecall` → S handler → `ebreak` → M) and then clears `NMIE`; if `NMIE` reads 0 at reset it goes straight to the test. The test pass enters S-mode and executes the delegated `ecall`; the S-mode code prints the verdict itself since it can never return to M with `NMIE = 0`.

```
make ARCH=riscv64-xs
./build/emu -i nmie0-deleg-poc-riscv64-xs.bin --no-diff -C 300000
```

Output on 2acbf327c — nothing is printed after the last `[phase2]` line; the emu spins until the cycle cap with the instruction count frozen:

```
=== nmie0-deleg-poc: delegated S-mode exception while mnstatus.NMIE=0 ===
mnstatus at reset = 0x8 (NMIE=1)
[phase1] NMIE=1: S ecall -> S handler count=1 scause=9, then ebreak -> M: OK
[phase2] after csrc mnstatus.NMIE: mnstatus=0x0 NMIE=0
[phase2] entering S-mode and executing a delegated ecall with NMIE=0 ...
[phase2] (if nothing is printed after this line, the hart stopped = critical error)
Core 0: EXCEEDING CYCLE/INSTR LIMIT
Core-0 instrCnt = 12,074, cycleCnt = 300,000, IPC = 0.040247
```

(`rob: commitInstr = 12074` in the PERF dump — the hart committed ~12k instructions up to the `ecall` and nothing for the remaining ~288k cycles.)

Spike reference — same sequence as `spike_ref_test.S`, `spike --isa=rv64gc_smrnmi_smdbltrp -l --log-commits` (`spike_trace.log` in the attachment). `mnstatus` reads 0 (NMIE=0 at reset), the trap is still delivered to S-mode and execution continues:

```
core   0: 0x0000000080000030 (0x744025f3) csrr    a1, mnstatus
core   0: 3 0x0000000080000030 (0x744025f3) x11 0x0000000000000000
...
core   0: 0x0000000080000060 (0x00000073) ecall
core   0: exception trap_supervisor_ecall, epc 0x0000000080000060
core   0: >>>>  s_trap
core   0: 0x0000000080000070 (0x14202673) csrr    a2, scause
core   0: 1 0x0000000080000070 (0x14202673) x12 0x0000000000000009
...
core   0: 0x0000000080000088 (0x10200073) sret
core   0: 0x0000000080000064 (0x00006537) lui     a0, 0x6
```

[nmie0-deleg-poc-results.zip](https://github.com/user-attachments/files/31883175/nmie0-deleg-poc-results.zip)

### Additional context

Suggested fix: qualify the rule by the mode the trap is raised in — `csr_dbltrp_inMN := !NMIE && hasTrap && privState.isModeM`, and replace the `mnstatus.regOut.NMIE` term in the M/HS/VS entry valids by `(!privState.isModeM || mnstatus.regOut.NMIE)`. Interrupts are already masked when `NMIE = 0`, so only exceptions reach these entries; an M-mode exception with `NMIE = 0` still ends in a critical error, and a non-delegated S/U → M exception follows the normal MDT rule (`norm:trap_exp`). NEMU needs the same change for difftest to be able to check this.

Files: `backend/fu/NewCSR/NewCSR.scala` (trap entry valids, `criticalErrors`), `backend/fu/NewCSR/TrapHandleModule.scala` (`dbltrpToMN`).

Not the same as #6303: that report stays in M-mode with no delegation, and an M-mode trap with `NMIE = 0` *is* supposed to end in a critical error.
