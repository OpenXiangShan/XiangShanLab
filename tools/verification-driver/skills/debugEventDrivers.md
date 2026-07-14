# XiangShan Debug Event Drivers

Use this file when a module can create, carry, arbitrate, consume, mask, or observe a debug event. This includes decode, trigger match, CSR, ROB/commit, trap/redirect control, frontend redirect, LSU/MMU fault paths, interrupt logic, debug module interfaces, and any queue or FSM that stores exception/trap metadata.

Every debug architecture claim must be verified with `riscv-spec`/UDB or an upstream RISC-V Debug/Privileged specification source when UDB lacks coverage. Every XiangShan-specific event source, priority rule, CSR field, redirect path, and side effect must cite effective Chisel lines from `tools/xiangshan-code-analyzer/skills/analyze-xiangshan-kunminghu`.

## Debug Event Driver Shape

```markdown
## Debug Event Verification
| Debug ID | Event source | Code evidence needed | Stimulus construction | Expected architectural result | Expected microarchitectural result | Checkers |
| --- | --- | --- | --- | --- | --- | --- |
```

For every selected debug scenario, include:

- Code-derived event source: signal name, producing module, valid condition, privilege/debug-mode guard, and source lines.
- Code-derived priority: exact `when/.elsewhen`, `Mux`, `PriorityMux`, arbiter, FSM, or commit/trap selection lines.
- Code-derived state updates: `dcsr`, `dpc`, debug cause, privilege state, trap CSRs that must not be updated, pending bits, redirect target, valid bits, queue metadata, and kill/replay masks.
- Specification source: RISC-V debug and privileged rules for `ebreak`, trigger match, single-step, halt entry, `dret`, debug CSRs, and debug-mode restrictions.
- Expected side effects: no younger architectural commit, no illegal memory/CSR side effect, precise PC in `dpc`, legal debug cause, and legal privilege restoration on `dret`.

## Debug Event Source Drivers

| Debug ID | Scenario | Stimulus construction | Expected behavior | Checkers |
| --- | --- | --- | --- | --- |
| `DBG_EBREAK_MODE` | `ebreak` debug entry by privilege | Execute `ebreak` from M/S/U and HS/VS/VU when implemented while sweeping `dcsr.ebreak*` controls | Enabled modes enter debug with legal `dpc`/cause; disabled modes take breakpoint exception | Debug/privilege checker |
| `DBG_TRIGGER_EXEC` | Execute trigger match | Program trigger CSRs for fetch/execute match, then execute matching and nonmatching PCs | Matching instruction enters debug at the precise instruction boundary | Trigger checker |
| `DBG_TRIGGER_LOAD_STORE` | Memory trigger match | Program load/store trigger, then issue matching load/store with hit, miss, replay, and fault variants | Trigger/debug/fault priority and memory side effects match spec/code | Debug/memory checker |
| `DBG_SINGLE_STEP` | Single-step entry | Set step control, execute one legal instruction, branch, CSR op, and faulting op variants | Exactly the architecturally selected step boundary enters debug or reports the higher-priority event | Step checker |
| `DBG_HALTREQ` | External halt request | Assert halt request while core is idle, busy, stalled, replaying, and retiring | Core enters debug at a precise code-defined boundary without losing older state | Halt checker |
| `DBG_RESUME_DRET` | Resume and `dret` | Enter debug, update `dpc`/`dcsr`, execute `dret`, and resume into normal code | Privilege and PC restore legally; debug-mode-only effects do not leak | DRET checker |
| `DBG_DEBUG_CSR` | Debug CSR access legality | Access debug CSRs from non-debug and debug mode, legal and illegal privilege states | Illegal access traps or is blocked; legal access follows field masks/WARL behavior | CSR legality checker |
| `DBG_NESTED_BLOCK` | No nested debug corruption | Present another debug event while already in debug mode | State is ignored, held, or asserted as code/spec defines; `dpc`/cause not corrupted | Debug state checker |

## Debug Priority Drivers

| Debug ID | Candidate events | Stimulus construction | Expected priority | Lower-priority handling | Checkers |
| --- | --- | --- | --- | --- | --- |
| `DBG_EXC_INT_SAME_POINT` | Debug, exception, interrupt | Make a debug event, synchronous exception, and enabled interrupt visible at the same commit/trap point | Spec/code-selected action wins with correct `dpc` or EPC/cause | Losing event remains pending, is masked, or is killed per code | Trap priority checker |
| `DBG_EBREAK_EXCEPTION` | `ebreak` debug versus breakpoint exception | Sweep `dcsr.ebreak*` controls and current privilege for `ebreak` | Debug entry only when enabled for current mode; otherwise breakpoint exception | Non-selected path has no CSR side effect | Debug/exception checker |
| `DBG_TRIGGER_FAULT` | Trigger versus page/PMP/PMA/IOPMP fault | Trigger match on an instruction or memory op that also faults | Priority and reported metadata match spec/code | Store/AMO side effects suppressed on fault/debug as required | Debug/protection checker |
| `DBG_STEP_TRAP_RETURN` | Step versus `mret`/`sret`/`dret` | Step control active while executing trap-return instructions | Return privilege/PC and step/debug entry order match spec/code | Lower-priority redirect is not double-applied | Return checker |
| `DBG_INTERRUPT_PENDING` | Debug entry with interrupt pending | Enabled interrupt is pending before and during debug entry/resume | Debug entry/resume and interrupt delivery order matches spec/code | Pending interrupt preserved or consumed legally | Interrupt/debug checker |
| `DBG_REPLAY_REDIRECT` | Debug with replay/redirect | Trigger debug event while operation is replayed or wrong-path redirect arrives | Wrong-path debug is killed; right-path debug remains precise | Replay/redirect does not duplicate debug entry | Replay/redirect checker |

## Privilege and Debug Cross-Products

Use directed cross-products, not blind full explosion:

- Current mode x debug source: M/S/U and HS/VS/VU when implemented over `ebreak`, trigger, step, halt request, and debug CSR access.
- Debug state x privileged instruction: normal mode and debug mode over `mret`, `sret`, `dret`, `wfi`, CSR access, `fence.i`, CBO/CMO, and virtualized instructions.
- Effective privilege x memory debug event: MPRV/MPP, SUM, MXR, page permission, PMP, PMA, IOPMP, and MMIO over load/store/fetch trigger cases.
- Trap point x pipeline state: decode, execute, memory replay, writeback, ROB commit, trap entry, trap return, and frontend redirect when those paths are implemented.
- Context switch x pending debug event: privilege, ASID/process, VMID/guest, and supervisor-domain changes while debug metadata is live in queues, ROB, replay, frontend, or memory structures.

## Code Evidence Requirements

Before implementing a debug event test, the generated driver must list exact analyzer evidence for:

- Which modules produce each debug source and which configuration parameters enable it.
- Which signal carries the debug valid bit, cause, PC, privilege, virtualized mode, and age/ROB identity.
- Which mux, FSM, arbiter, or commit rule chooses debug versus exception, interrupt, redirect, replay, and xRET.
- Which CSR fields are read or written on debug entry, resume, and debug CSR access.
- Which pipeline stages kill wrong-path debug metadata and which stages preserve older precise debug events.
- Which assertions or constraints declare a debug combination unreachable.

## Completion Checklist

Before a debug event driver is complete:

- All implemented debug event sources are enumerated from XiangShan code evidence.
- `ebreak`, trigger, single-step, halt request, resume/`dret`, and debug CSR legality are covered when implemented.
- Debug priority is tested against exception, interrupt, replay, redirect, memory/protection fault, and trap return when reachable.
- M/S/U and HS/VS/VU privilege modes are covered according to branch configuration.
- Debug-mode restrictions and non-debug illegal accesses are checked for privileged/debug CSRs and privileged instructions.
- Checkers validate `dcsr`, `dpc`, debug cause, privilege restoration, EPC/cause/tval non-corruption, redirect target, pending bits, no younger commit, and no illegal side effect.
- Every architecture expectation cites a spec source, and every implementation expectation cites exact XiangShan Chisel evidence from the analyzer output.
