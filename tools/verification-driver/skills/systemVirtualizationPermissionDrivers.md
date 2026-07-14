# XiangShan System Verification Drivers

Use this file for system-level verification scenarios involving privilege state, virtualization, system calls/trap ABI paths, page-table permissions, PMP/PMA/IOPMP, multi-core synchronization, asynchronous events, interrupt delivery, guest/host interaction, and trap-handler save/handle/restore phases.

This skill complements:

- `skills/virtualizationProtectionDrivers.md` for protection source and translation-path coverage.
- `skills/architectureExceptionDrivers.md` for architecture exception and interrupt nesting.
- `skills/operandBoundaryDrivers.md` for address, CSR/control, and protocol operand boundaries.
- `skills/fsmScenarioDrivers.md` for trap-handler, page-walker, interrupt, and synchronization FSM sequencing.
- `skills/conflictScenarioDrivers.md` for same-cycle and overlapping system event conflicts.
- `skills/forwardProgressDrivers.md` for deadlock, livelock, starvation, and fairness of system flows.
- `skills/debugEventDrivers.md` when debug events overlap system calls, traps, interrupts, or virtualization.

Every architecture claim must cite `riscv-spec`/UDB or another upstream spec source when UDB lacks coverage. Every implementation claim must cite effective Chisel evidence from `tools/xiangshan-code-analyzer/skills/analyze-xiangshan-kunminghu`.

## System Verification Driver Shape

```markdown
## System Verification
| System ID | System scope | Code/spec evidence needed | Stimulus construction | Expected system observation | Checkers |
| --- | --- | --- | --- | --- | --- |
```

For every system test, include:

- Current execution context: M, S, U, HS, VS, VU, debug when relevant, hart id, guest id, ASID, VMID, domain, and interrupt delegation state.
- System operation: ECALL/SBI-like trap path, xRET, fence, SFENCE/HFENCE, page-table update, TLB/cache invalidation, interrupt/IPI/timer/external event, LR/SC/AMO synchronization, MMIO/uncache access, or guest/host transition.
- Access type when memory is involved: fetch, load, store, AMO, LR, SC, vector load/store, prefetch, CBO/cache op, MMIO/uncache.
- Translation mode: bare, host single-stage, guest VS-stage, G-stage, two-stage.
- Protection source: page permission, guest page permission, PMP, PMA, IOPMP, cacheability/MMIO attribute, and domain permission if implemented.
- Expected metadata: cause, tval/stval/vstval, guest physical metadata, EPC, privilege/virtualization stack, pending interrupt state, redirect target, coherence/ordering visibility, and no illegal side effect.

## System Call and Trap ABI Drivers

| System ID | Scenario | Stimulus construction | Expected behavior | Checkers |
| --- | --- | --- | --- | --- |
| `SYS_ECALL_U_S_M` | ECALL by privilege | Execute `ecall` from U/S/M and HS/VS/VU when implemented | Cause, EPC, delegation, privilege stack, and redirect target match spec/config | System-call checker |
| `SYS_ECALL_DELEGATION` | ECALL delegation matrix | Sweep medeleg/hedeleg/vsdeleg-like controls when implemented | Trap target mode and cause are selected legally | Delegation checker |
| `SYS_ECALL_PENDING_INT` | ECALL with pending interrupt | Make ECALL reach trap point while enabled/disabled/delegated interrupt is pending | Exception/interrupt priority, pending preservation, and trap state are correct | Trap priority checker |
| `SYS_ECALL_PAGE_FAULT_HANDLER` | System call handler page fault | Handler fetch/load/store touches page with fault candidate | Nested trap state preserves original ECALL metadata and selects legal fault | Nested trap checker |
| `SYS_ECALL_DEBUG` | System call plus debug event | ECALL overlaps debug trigger, halt request, or single-step | Debug/trap priority and `dcsr`/`dpc` or EPC/cause update are precise | Debug/system checker |
| `SYS_XRET_ASYNC_EVENT` | Return from trap with async event | Execute MRET/SRET/HRET-like path with interrupt/debug/timer pending | Return privilege/PC and immediate async event behavior match spec/code | xRET checker |
| `SYS_TRAP_ABI_SAVE_RESTORE` | Trap ABI save/restore | Trap handler saves/restores CSRs and context while nested event arrives | Outer and inner trap state are not corrupted | Trap ABI checker |

## Page Table Permission Drivers

All address/protection drivers must traverse read/write/execute permission matrices for host and guest page tables. Leaf and non-leaf PTE behavior must be tested separately.

| System ID | Protection source | Permission traversal | Required checks |
| --- | --- | --- | --- |
| `SYS_PAGE_RWX_LEAF` | Host leaf PTE | Sweep R/W/X/U/A/D/G for fetch/load/store/AMO/LR/SC/vector | Allow/fault, A/D behavior, tval, no illegal write side effect |
| `SYS_PAGE_RWX_NONLEAF` | Host non-leaf PTE | Sweep reserved or illegal R/W/X encodings on non-leaf PTEs | Page fault priority and page-walk stop/continue condition |
| `SYS_PAGE_AD_RW` | A/D bits | Load with A=0, store/AMO with D=0, combinations with writable leaf | Hardware update or page fault policy verified |
| `SYS_GPAGE_RWX_LEAF` | Guest leaf PTE | Sweep VS-stage and G-stage R/W/X/U/A/D permissions | Guest-page/page fault selection and metadata |
| `SYS_GPAGE_NONLEAF` | Guest non-leaf PTE | Illegal non-leaf permission encodings at VS and G stages | Correct guest-page fault or page fault |
| `SYS_SUPERPAGE_PERM` | Superpage PTE | Aligned and misaligned superpage PPNs with R/W/X permissions | Legal superpage translates; misaligned or illegal permission faults |
| `SYS_PMP_RWX` | PMP | Sweep R/W/X across OFF/TOR/NA4/NAPOT entries | Access fault/allow, entry priority, lock semantics |
| `SYS_PMA_RW_EXEC` | PMA | Sweep readable, writable, executable, atomic-capable, cacheable attributes | Access fault, instruction access fault, or allowed access |
| `SYS_IOPMP_RW` | IOPMP | Sweep read/write permission per master/domain | Deny response, access fault, no stale allow |
| `SYS_MIXED_PAGE_PMP_PMA` | Combined | Page allows while PMP/PMA denies; page denies while PMP/PMA allows | Winning fault source and metadata priority |

Leaf/non-leaf requirements:

- A leaf PTE permission test must cover all legal leaf combinations for the implemented page modes.
- A non-leaf test must cover invalid leaf-like permission encodings on intermediate PTEs, reserved bit settings, misaligned superpage PPNs, and pointer-to-next-level behavior.
- Non-leaf tests must verify the page walker does not treat an invalid non-leaf as a legal leaf.
- Page-table update tests must combine PTE writes with SFENCE/HFENCE and outstanding translation requests.

## Virtualization and Guest/Host Drivers

| System ID | Scenario | Stimulus construction | Expected behavior | Checkers |
| --- | --- | --- | --- | --- |
| `SYS_GUEST_GPF_HOST_TRAP` | Guest page fault plus host trap | VS/VU memory access triggers guest page fault while HS/M interrupt, debug, or trap trigger is pending | Guest-page fault and host trap priority/routing verified | Guest/host checker |
| `SYS_GUEST_GPF_HOST_FAULT` | Guest page fault plus host fault | VS-stage fault candidate plus G-stage/PMP/PMA host fault candidate | Guest-page fault versus host page/access fault priority verified | Two-stage checker |
| `SYS_GUEST_GPF_HOST_INT` | Guest page fault plus host interrupt | Enabled HS/M interrupt pending as guest page fault reaches trap point | Correct exception-vs-interrupt priority and delegation | Interrupt checker |
| `SYS_HOST_PF_GUEST_EXCEPTION` | Host page fault plus guest exception | G-stage/host page fault candidate plus guest illegal/ecall/breakpoint exception candidate | Correct selected trap mode and cause | Trap checker |
| `SYS_HOST_PF_GUEST_TRAP` | Host page fault plus guest trap entry | Guest is entering trap while host translation/protection fault occurs | Trap-state save and selected fault metadata are precise | Trap phase checker |
| `SYS_HOST_PF_GUEST_FAULT` | Host page fault plus guest fault | VS-stage and G-stage faults overlap in one memory operation | Correct guest/host fault priority and tval/gpa metadata | Fault priority checker |
| `SYS_HOST_PF_GUEST_INT` | Host page fault plus guest interrupt | VS/VU virtual interrupt pending while host page/access fault candidate occurs | Correct virtual interrupt versus host fault priority | Virtual interrupt checker |
| `SYS_HOST_TRAP_GUEST_INT` | Host trap plus guest interrupt | HS/M trap handling overlaps guest pending interrupt delivery | Guest interrupt is held, delegated, or delivered per state | Delegation checker |
| `SYS_VM_SWITCH_ASYNC` | VM switch with async event | Change hgatp/VMID or VS-stage context while interrupt/debug/page fault is pending | Event routes to the legal guest/host context and stale state is blocked | VM context checker |

For each guest/host cross-product, test both same-cycle and staged arrival:

- Guest event first, host event next.
- Host event first, guest event next.
- Both events visible at the same trap-decision point.

## Multi-Core Synchronization Drivers

Use these when the module or subsystem can observe more than one hart, shared cache, coherent memory, atomic operation, interrupt file, IPI path, shared TLB/cache maintenance, or shared MMIO/device state.

| System ID | Scenario | Stimulus construction | Expected behavior | Checkers |
| --- | --- | --- | --- | --- |
| `SYS_MC_LRSC_CONTENTION` | LR/SC across harts | Two or more harts contend on the same reservation granule with intervening stores/probes | Reservation success/failure and memory visibility match spec/code | LR/SC sync checker |
| `SYS_MC_AMO_ORDER` | AMO synchronization | Concurrent AMO/load/store from multiple harts to same line/word | Atomicity, serialization, and coherence visibility are correct | AMO/coherence checker |
| `SYS_MC_FENCE_ORDER` | Fence cross-hart visibility | Producer hart stores then fences; consumer hart loads or polls | Required memory ordering and visibility are preserved | Memory-order checker |
| `SYS_MC_SFENCE_SHOOTDOWN` | Page-table shootdown | Hart updates PTE and sends IPI/SFENCE/HFENCE sequence while other hart has stale TLB entry | Stale translation cannot authorize later access after required synchronization | TLB shootdown checker |
| `SYS_MC_CACHE_INVALIDATE` | Cache maintenance across harts | One hart performs CBO/fence.i/cache op while another fetches/loads same line | Maintenance visibility and ordering match spec/platform/code | Cache sync checker |
| `SYS_MC_IPI_DELIVERY` | Inter-processor interrupt | Hart A sends IPI/MSI to hart B while B is stalled, trapping, or switching context | Pending/enable/delegation state delivers exactly once or remains pending legally | IPI checker |
| `SYS_MC_SHARED_MMU_CONTEXT` | Shared context switch | Multiple harts switch ASID/VMID/domain with outstanding PTW/TLB/cache requests | Stale response cannot update or authorize wrong context | Context isolation checker |

## Asynchronous Event Drivers

Asynchronous events include timer, software, external, local, NMI-like, AIA/APLIC/IMSIC, IPI/MSI, debug halt, bus error response, and asynchronous device/MMIO events.

| System ID | Scenario | Stimulus construction | Expected behavior | Checkers |
| --- | --- | --- | --- | --- |
| `SYS_ASYNC_TIMER` | Timer interrupt arrival | Timer interrupt arrives during normal execution, trap entry, xRET, page walk, and memory replay | Pending, enable, delegation, priority, and redirect behavior are precise | Timer checker |
| `SYS_ASYNC_EXTERNAL` | External interrupt arrival | External/APLIC/IMSIC event arrives during cache miss, PTW walk, or commit boundary | Interrupt delivery, masking, and pending preservation are correct | External interrupt checker |
| `SYS_ASYNC_IPI` | Software/IPI interrupt | IPI arrives during privilege/VM/domain switch and trap handler phases | Event routes to correct hart/context and is not lost | IPI checker |
| `SYS_ASYNC_DEBUG_HALT` | Halt request async event | Halt request arrives while exception, interrupt, replay, or xRET is pending | Debug/system priority and state update are precise | Debug/system checker |
| `SYS_ASYNC_BUS_ERROR` | Bus/MMIO async error | Error response returns after requester context changed or request was killed | Error is attributed to correct live request or suppressed if killed | Bus error checker |
| `SYS_ASYNC_NMI_LOCAL` | NMI/local event if implemented | NMI/local interrupt overlaps trap/debug/system call/page fault | Implementation-specific priority is checked against code/spec evidence | NMI checker |

## Trap Handler Phase Drivers

Guest and host may be in save-context, handle-exception, or restore-context phases. System drivers must test fault/interrupt/debug/system-call arrival in each phase.

| System ID | Phase | Stimulus construction | Expected behavior |
| --- | --- | --- | --- |
| `SYS_HOST_SAVE_CONTEXT_EVENT` | Host save context | Host trap entry starts CSR/context save; guest fault/interrupt/debug event arrives | Host save remains precise; guest event pending/routed legally |
| `SYS_HOST_HANDLE_EVENT` | Host handling exception | Host handler executes memory/CSR/system-call operations while guest interrupt/fault pending | Nested trap rules and delegation preserved |
| `SYS_HOST_RESTORE_EVENT` | Host restore context | MRET/SRET/HRET-like return path with pending guest/host async event | Return state and immediate trap/interrupt behavior correct |
| `SYS_GUEST_SAVE_CONTEXT_EVENT` | Guest save context | Guest trap entry saves VS/VU state while host fault/interrupt arrives | Guest saved state is not corrupted; host routing correct |
| `SYS_GUEST_HANDLE_EVENT` | Guest handling exception | Guest handler re-enables interrupts, executes ECALL, or touches faulting page | Nested guest trap, host trap, or virtual interrupt priority correct |
| `SYS_GUEST_RESTORE_EVENT` | Guest restore context | Guest trap return with host interrupt/fault pending | Restored guest state and host trap decision correct |
| `SYS_DUAL_SAVE_RESTORE` | Guest and host phased overlap | Guest save/restore overlaps host save/restore due to nested trap | CSR stacks, EPC/cause/tval, and pending bits remain precise |

Phase checks:

- EPC/cause/tval for outer and inner traps are preserved.
- xPP/xPIE/xIE and virtualization stack fields are correct.
- Guest CSRs and host CSRs are not cross-corrupted.
- Pending interrupt bits are consumed, preserved, or masked according to delegation and enable state.
- No faulting instruction retires twice.
- Store/AMO/MMIO side effects are blocked when the selected exception requires no side effect.

## Directed Cross-Products

Use directed cross-products, not blind full explosion:

- System call source mode x delegation target x pending async event.
- Trap phase x event source: system call, guest fault, guest trap, guest interrupt, host fault, host trap, host interrupt, debug halt, bus error.
- Access type x page leaf permission.
- Access type x page non-leaf illegal encoding.
- Access type x PMP/PMA/IOPMP permission.
- Guest mode x VS-stage permission x G-stage permission.
- Guest mode x host PMP/PMA permission.
- Boundary address x permission source.
- Hart pair x synchronization primitive: LR/SC, AMO, fence, SFENCE/HFENCE, IPI, cache maintenance.
- Async event x pipeline point: decode, execute, memory replay, PTW walk, cache miss, commit, trap entry, trap return, debug entry.

## Completion Checklist

Before a system verification driver is complete:

- A `System Verification` section exists for every generated driver whose module touches privilege, virtualization, page translation, interrupts, traps, debug system interactions, system calls, multi-core synchronization, or shared system resources.
- System call paths cover ECALL from implemented modes, delegation, pending interrupt/debug interaction, trap handler page faults, and xRET return behavior.
- Every read/write/execute permission matrix is traversed for page, guest page, PMP, PMA, and IOPMP when implemented.
- Leaf and non-leaf PTEs are tested separately, including illegal non-leaf permission encodings, reserved bits, A/D behavior, and misaligned superpage PPNs.
- Guest page fault, host trap, host fault, host interrupt, guest exception, guest trap, guest fault, and guest interrupt are tested in both directions.
- Multi-core tests cover LR/SC, AMO, fence ordering, IPI delivery, TLB shootdown, cache maintenance visibility, and shared-context stale response isolation when reachable.
- Asynchronous event tests cover timer, software/IPI, external, local/NMI-like, debug halt, and bus/MMIO error events when implemented.
- Guest/host save-context, handle-exception, and restore-context phases are each tested with incoming fault/trap/interrupt/debug/system-call events.
- Same-cycle and staged-arrival guest/host/system events are both covered.
- Checkers validate cause, tval/stval/vstval, guest physical metadata, EPC, privilege/virtualization stacks, pending bits, redirect target, memory ordering, coherence visibility, side effects, and stale context isolation.
