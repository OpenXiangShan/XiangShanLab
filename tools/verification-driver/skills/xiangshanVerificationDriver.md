# XiangShan Verification Driver

Generate module-level verification drivers for XiangShan Kunminghu code analysis results.

This driver pack combines two sources:

- Code analysis source: `tools/xiangshan-code-analyzer/skills/analyze-xiangshan-kunminghu`.
- Specification source: `https://github.com/docularxu/openclaw-skills.git`, skill `riscv-spec`.
- Debug/privilege implementation source: effective XiangShan Chisel lines reported by the code-analysis source for debug event producers, privilege guards, CSR fields, trap/redirect priority, and commit state updates.
- Forward-progress implementation source: effective XiangShan Chisel lines reported by the code-analysis source for every mux, arbiter, FSM, queue, replay path, ready/valid path, credit path, and completion condition.
- Performance counter implementation source: effective XiangShan Chisel lines reported by the code-analysis source for every performance counter CSR, event selector, inhibit/control bit, overflow bit, event producer, privilege/virtualization filter, and counter update path.

Each generated driver is split into three required parts:

1. `Architecture Verification`: checks behavior required by RISC-V architectural specification, privilege, virtual memory, debug, interrupt, CSR, cache-management, memory-ordering, and profile requirements.
2. `Microarchitecture Scenario Verification`: checks effective code behavior: queues, valid/ready handshakes, FSMs, arbiters, redirect, replay, flush, port contention, resource contention, structural conflicts, storage update/release/search/replace, and implementation-specific backpressure.
3. `System Verification`: checks system-visible behavior across privilege/virtualization state, system calls/trap ABI paths, page-table permissions, multi-core synchronization, asynchronous events, interrupts, IPIs, timer/external events, memory ordering across cores, and guest/host interactions.

## Required Driver Shape

Every module driver must use this shape.

```markdown
# <Module> Verification Driver

## Scope
- Source module/path:
- Parent/subsystem:
- Required code-analysis input:
- Required specification input:
- Shared-resource class: yes/no

## Architecture Verification
| Scenario | Spec source to verify | Stimulus | Expected architectural observation | Checkers |
| --- | --- | --- | --- | --- |

## Microarchitecture Scenario Verification
| Scenario | Code evidence needed | Stimulus | Expected microarchitectural observation | Checkers |
| --- | --- | --- | --- | --- |

## System Verification
| Scenario | System scope | Code/spec evidence needed | Stimulus | Expected system observation | Checkers |
| --- | --- | --- | --- | --- | --- |

## Queue / Buffer Coverage
| Structure | Empty | Almost empty | Full | Almost full | Simultaneous enq/deq | Flush/replay/redirect |
| --- | --- | --- | --- | --- | --- | --- |

## Handshake Coverage
| Interface | Producer | Consumer | Ready/valid/fire cases | Backpressure path | Payload-stability checks |
| --- | --- | --- | --- | --- | --- |

## FSM / Arbiter Coverage
| Object | Reset/idle | Transitions | Simultaneous requests | Winner rule | Loser behavior | Fairness/starvation check |
| --- | --- | --- | --- | --- | --- | --- |

## Event Coverage
| Event | Stimulus | Expected response | Recovery/check |
| --- | --- | --- | --- |

## Shared Resource Context-Switch Coverage
| Switch class | Stimulus | Required checks |
| --- | --- | --- |
```

## Architecture Verification Rules

Use `riscv-spec` before writing any architectural claim. The driver must state whether the claim came from UDB or from an upstream RISC-V manual repository when UDB lacks coverage.

Minimum architecture scenarios:

Architecture exception drivers must use `skills/architectureExceptionDrivers.md` when instructions, traps, memory accesses, interrupts, debug, privilege, or virtualization are in scope.

Debug event drivers must use `skills/debugEventDrivers.md` when a module can produce, carry, arbitrate, consume, mask, or observe debug events, debug CSRs, trigger matches, single-step, halt/resume, `dret`, trap/debug redirects, or debug-mode privilege restrictions.

Performance monitor counter drivers must use `skills/performanceMonitorCounterDrivers.md` when a module implements, updates, filters, exposes, virtualizes, snapshots, or consumes `cycle`, `instret`, `mcycle`, `minstret`, `hpmcounter*`, `mhpmcounter*`, `mhpmevent*`, inhibit/control CSRs, overflow/pending state, event buses, event aggregation, or performance-monitor interrupt logic.


- Instruction legality and encoding for module-visible instruction classes.
- CSR read/write legality, field mask behavior, WARL/WLRL behavior, privilege access, and side effects.
- Performance monitor counter CSR legality, event selector legality, inhibit/control behavior, overflow/pending behavior, and privilege/virtualization visibility when implemented.
- Exception priority and trap metadata: cause, tval, epc, interrupt bit, debug entry, and redirect target.
- Per-instruction exception closure: every implemented instruction or instruction class must enumerate and test all architecturally reachable exceptions.
- Memory-instruction exception priority: one memory instruction must be tested with multiple candidate exceptions in the same execution path, including misalign, page fault, guest page fault, access fault, PMP/PMA/IOPMP deny, replay, LR/SC reservation, vector element fault, CBO fault, and MMIO error when applicable.
- Exception plus interrupt nesting: exception tests must include simultaneous interrupt-pending variants and nested trap scenarios covering trap entry, handler re-enable, trap return, delegation, debug, and virtualization when implemented.
- Privilege transitions: M/U/S, HS/VS/VU when hypervisor is enabled, and debug mode when relevant.
- Debug event transitions: `ebreak`, trigger match, single-step, external halt request, resume/`dret`, debug CSR legality, and debug-mode restrictions when implemented.
- Virtual memory transitions: `satp`, `hgatp`, `vsatp`, TLB flush, page-fault, guest-page-fault, access-fault, and address-translation metadata.
- Interrupt paths: timer/software/external/local, AIA/APLIC/IMSIC when present, interrupt delegation, and virtualization routing.
- Memory ordering: load/store/AMO/LR/SC, fence, fence.i, CBO/CMO, MMIO/uncache, misaligned behavior, and exception timing.
- Profile/config parameters: MXLEN, supported extensions, PMP entry count, page modes, interrupt extensions, vector/FP enablement, and implementation-visible architectural knobs.

## Microarchitecture Verification Rules

Use the code-analysis skill to identify effective code, source lines, parameters, and instantiated paths. A driver must not assume behavior from a module name.

Minimum microarchitecture scenarios:

- Queue empty, almost empty, full, almost full, simultaneous enqueue/dequeue, wraparound, multi-enqueue/multi-dequeue, flush/cancel/replay, and backpressure propagation.
- Decoupled/Valid handshake: producer-valid-only, consumer-ready-only, both fire, stalled valid with stable payload, ready drops, response stall, and flush during pending transaction.
- FSM: reset state, first request, each state transition, output action by state, stuck-state prevention, deadlock wait-state, livelock retry-cycle, starvation of low-priority transitions, cancel/redirect/replay/exception transition, and protocol violation protection.
- Arbiter: all requesters valid in the same cycle, each requester alone, older low-priority request versus newer high-priority request, priority or round-robin rotation, ready returned to losers, grant stability, and starvation bound.
- Microarchitecture events: redirect, replay, port contention, resource contention, structural conflict, bank conflict, same-entry conflict, MSHR/replay-entry conflict, CSR/ROB/LSQ ordering conflict, and exception versus redirect priority.
- Debug microarchitecture events: debug valid/cause/PC metadata generation, queueing, kill, replay preservation, redirect selection, commit/trap arbitration, CSR update timing, and resume state restoration.
- Storage: update, release, replace, search/read/probe, read/write same index, multiple writes same index, RAW/WAR/WAW, bypass/forwarding, and assert/error behavior.
- Performance counters: maximum concurrent event pulses, CSR read/write versus increment races, inhibit/event-select changes during active events, overflow boundaries, queue full/empty update pressure, flush/replay kill behavior, trap/debug/context-switch filtering, and no lost or double-counted events.

## Flush Extreme-State Rules

Every code-reachable flush, redirect, cancel, kill, invalidate, drain, recovery, fence, context-switch flush, exception flush, interrupt/debug flush, replay flush, or cache/TLB invalidation path must be tested in microarchitecture extreme states, not only in a nominal partially-filled pipeline.

Minimum flush extreme-state coverage:

- Queue and buffer occupancy: assert each flush source while every affected queue, skid buffer, replay queue, update queue, FTQ/IBuffer-like structure, MSHR/PTW entry pool, store/load queue, issue queue, ROB, writeback buffer, refill buffer, and protocol tracker is empty, almost empty, exactly one entry live, full, almost full, wrapped, and simultaneously enqueueing/dequeueing when reachable.
- Pipeline and handshake pressure: assert flush while valid is stalled by ready low, while response valid is pending, while producer and consumer fire in the same cycle, while payload is held stable, and while backpressure propagates across adjacent stages.
- Resource extremes: assert flush while all relevant ports, banks, ways, entries, credits, IDs, source/sink IDs, replacement candidates, replay slots, and update ports are exhausted or contended.
- State-machine extremes: assert flush from idle, first-request, every busy/wait/retry state, terminal response state, error state when reachable, and same-cycle competing transition states.
- Boundary timing: assert flush before accept, on accept/fire, after accept before write, on response, after response before commit, during commit, during retry/replay, during replacement/refill, and during pointer wrap.
- Required checks: killed entries cannot later fire, commit, wake up consumers, update predictors/CSRs/memory/cache metadata, leak context, consume grants, free resources twice, lose surviving older state, corrupt occupancy flags, or block new legal work after the code-defined drain/recovery point.
- Coverage accounting: if any extreme state is unreachable, the generated driver must cite exact code evidence or constraints. Otherwise it must include a row and checker for that state.


## Debug Event and Privilege Rules

Every module driver that touches debug mode, debug CSRs, trigger CSRs, `ebreak`, single-step, external halt/resume, `dret`, trap/debug redirects, commit trap priority, or privileged instruction legality must select applicable scenarios from `skills/debugEventDrivers.md`.

Minimum debug/privilege coverage:

- Enumerate all code-derived debug event producers and consumers, including source module/path, enabling parameters, valid condition, cause encoding, PC source, privilege/virtualization metadata, and downstream consumer.
- Verify `ebreak`, trigger, single-step, halt request, resume/`dret`, and debug CSR access legality for M/S/U and HS/VS/VU modes when implemented.
- Verify debug priority against exception, interrupt, replay, redirect, memory/protection fault, and trap return at the exact code-derived arbitration point.
- Verify debug-mode privilege restrictions: non-debug access to debug CSRs, debug-mode-only `dret`, privileged instruction legality, virtualized mode interaction, and legal restoration of privilege on resume.
- Check `dcsr`, `dpc`, debug cause, EPC/cause/tval non-corruption, pending bits, redirect target, no younger commit, and no illegal memory or CSR side effect.
- Cite RISC-V debug/privileged spec evidence for architecture behavior and cite exact XiangShan Chisel evidence from the code analyzer for every implementation-specific priority or mask rule.

## System Verification Rules

Every generated driver whose module touches privilege, virtualization, page translation, interrupts, traps, debug system interactions, system calls, multi-core synchronization, asynchronous events, or shared system resources must include `System Verification` using `skills/systemVirtualizationPermissionDrivers.md`.

Minimum system coverage:

- System calls and trap ABI paths: ECALL from implemented modes, delegation, pending interrupt/debug interaction, handler page faults, nested trap state, and xRET return behavior.
- Virtualization and guest/host interaction: guest page fault plus host trap/fault/interrupt, host page fault plus guest exception/trap/fault/interrupt, VM/context switch with pending events, and same-cycle plus staged-arrival event ordering.
- Page-table permissions: read/write/execute matrices for leaf PTEs, non-leaf PTE illegal encodings, reserved bits, A/D behavior, superpage alignment, VS-stage, G-stage, PMP, PMA, and IOPMP.
- Multi-core synchronization: LR/SC contention, AMO ordering, fence visibility, IPI delivery, TLB shootdown, cache maintenance visibility, and stale response isolation across harts.
- Asynchronous events: timer, software/IPI, external, local/NMI-like, debug halt, bus/MMIO error, and device events at decode, execute, memory replay, PTW walk, cache miss, commit, trap entry, trap return, and debug entry points.
- Trap phases: guest and host save-context, handle-exception, and restore-context phases with incoming fault/trap/interrupt/debug/system-call events.
- System checkers validate cause, tval/stval/vstval, guest physical metadata, EPC, privilege/virtualization stacks, pending bits, redirect target, memory ordering, coherence visibility, side effects, and stale context isolation.

## Virtualization and Protection Rules

Every module driver that touches virtualization, address translation, page tables, PMP, PMA, IOPMP, MMIO/uncache, privilege permissions, ASID, VMID, domain, `satp`, `vsatp`, or `hgatp` must select applicable scenarios from `skills/virtualizationProtectionDrivers.md`.

Minimum virtualization/protection coverage:

- Virtualization state: M/S/U, HS/VS/VU, debug overlap, `satp`, `vsatp`, `hgatp`, ASID, VMID, MPRV, SUM, MXR, state-enable legality, and context switches while requests are live.
- Page translation: valid/invalid PTE, R/W/X/U/A/D/G permissions, superpage alignment, two-stage translation, guest-page faults, TLB refill/invalidation races, and page-boundary accesses.
- PMP: OFF/TOR/NA4/NAPOT, priority, lock, R/W/X matrix, effective privilege via MPRV/MPP, page/PMP interaction, and live-request context switch.
- PMA: cacheable/uncache/MMIO region boundary, atomic support, misalignment support, execute permission, side-effect region speculation, and bus error response.
- IOPMP: master ID, entry priority, config lookup race, deny response, and domain switch.
- Combined priorities: page + PMP + PMA, guest page + host page/access, misalign + page + PMP, interrupt + fault, replay + fault, and debug + fault.

## Operand Boundary Rules

Every module driver that consumes instruction operands, functional-unit operands, floating-point operands, vector operands, addresses, immediates, masks, CSR fields, privilege fields, or bus protocol fields must select applicable scenarios from `skills/operandBoundaryDrivers.md`.

Minimum operand boundary coverage:

- Integer operands: zero, one, negative one, signed min/max, unsigned max, carry/borrow pairs, shift amount edges, mul/div edges, branch compare edges, and bit-pattern edges.
- Floating operands: signed zero, infinities, quiet/signaling NaNs, NaN payloads, subnormal/normal boundaries, smallest subnormal, largest subnormal, smallest normal, largest finite, underflow boundaries, overflow boundaries, rounding halfway cases, conversion boundaries, FMA cancellation, fflags, frm, FS legality, and NaN boxing when applicable.
- Address operands: alignment, page, superpage, guest-page, canonicality, PMP/PMA/IOPMP boundary, cacheline, bank/set, MMIO/uncache, and fetch target boundaries.
- Vector operands: `vl`, `vstart`, masks, SEW/LMUL, stride/index addresses, reductions, faulting element boundaries, and per-lane FP special operand coverage for every implemented vector lane when vector FP is implemented.
- CSR/control operands: reset values, writable masks, reserved bits, WARL edge values, privilege modes, status stack fields, interrupt masks, and protection config boundaries.
- Bus protocol fields: AXI/TL/APB/CHI IDs, source/sink, burst length, size, masks/strobes, response, and boundary beat fields.

Boundary operand tests must be combined with exception, interrupt, conflict, FSM, bus backpressure, index/hash, and context-switch scenarios when reachable.

## Forward Progress Rules

Every generated driver must include `Forward Progress Verification` using `skills/forwardProgressDrivers.md`. Deadlock, livelock, and starvation coverage is mandatory for all nontrivial modules and especially for queues, FSMs, muxes, arbiters, schedulers, replay paths, bus bridges, cache/MMU miss paths, trap/debug paths, and shared resources.

Minimum forward-progress coverage:

- Deadlock: construct all-stall, backpressure-cycle, full-resource, flush-drain, context-switch, and trap/debug blocked-progress scenarios when reachable.
- Livelock: construct repeated replay, redirect loop, refill/invalidate race, retry/NACK loop, and FSM nonterminal-cycle scenarios when reachable.
- Starvation: construct fixed-priority loser, old-low-priority versus new-high-priority, round-robin wrap, age-priority wrap, head-blocking, and credit-return scenarios when reachable.
- For every mux/arbiter/select path, create a scenario where an older request arrives on a lower-priority input and stays valid while newer requests arrive on higher-priority inputs; check whether code eventually serves, promotes, replays, kills, or intentionally starves the older request.
- For every FSM, create potential starvation and livelock scenarios by holding lower-priority transitions true while pulsing higher-priority transitions, and by cycling retry/replay/nonterminal states before providing fair completion.
- Every progress claim must state fairness assumptions, code-derived bound when present, failure signature, and exact XiangShan code evidence for request hold, grant, state exit, and completion.

## FSM Scenario Rules

Every module driver that contains an explicit or implicit state machine must select applicable scenarios from `skills/fsmScenarioDrivers.md`.

Minimum FSM coverage:

- Construct an entry sequence, hold sequence, exit sequence, and output check for every state.
- Construct a transition sequence for every legal state transition.
- Construct same-cycle or overlapping-cycle trigger sequences where different input sequences would request different next states, then verify the code-defined priority.
- Construct potential deadlock, livelock, and starvation sequences for every wait/busy/retry state and every lower-priority transition.
- Test request versus flush, response versus flush, response versus replay, exception versus replay, grant versus cancel, enqueue/dequeue versus flush, allocation/free versus redirect, commit versus interrupt/exception/debug, context switch while busy, wait-state deadlock, retry-cycle livelock, and low-priority transition starvation when reachable.
- Include a transition matrix with from-state, to-state, required sequence, competing triggers, priority rule, expected outputs, and illegal transitions.
- Verify illegal or unreachable transitions through assertions, constraints, or exact code evidence.

## Index, Bus Protocol, and Hash Rules

Every module driver that contains computed indexes, address slicing, pointers, banking, hashing, or bus interfaces must select applicable scenarios from `skills/indexBusHashDrivers.md`.

Minimum required coverage:

- Index boundary values: `0`, `1`, `max-1`, `max`, wraparound, reserved/invalid encodings when representable, and simultaneous update at the boundary.
- Address boundaries: cache line, fetch block, page, superpage, guest page, beat, byte mask, vector element, bank, set, way, queue entry, MSHR/PTW/replay id, and AXI/TL source/id boundaries when applicable.
- Bus protocol tests: ready/valid payload stability, independent channel backpressure, fire-only state update, outstanding limit, ID/source/sink reuse prevention, burst beat count, `last`, byte strobes/masks, response/error propagation, reset/flush behavior, and protection/context metadata stability.
- Hash conflict generation: for every code-derived hash, index fold, XOR, modulo, mask, bank hash, folded history, predictor table index, prefetch hash, cache/directory hash, or MSHR merge key, generate conflict inputs and, when possible, a script that constructs same-index/different-tag and same-hash/different-context address groups.

The generated hash script must encode the exact code-derived expression. It must not use a guessed hash function.

## Predictor Coverage Rules

Every module driver for a predictor, predictor table, predictor meta path, or predictor-like trainer must cover both correct prediction and incorrect prediction scenarios. This includes branch direction predictors, target predictors, indirect-target predictors, statistical correctors, base predictors, return-address stacks, loop or stride predictors, prefetch predictors, memory-dependence predictors, and any local predictor instantiated under a larger BPU or frontend module.

Minimum predictor coverage:

- Predictor inventory: enumerate every code-instantiated predictor component, table, history register, folded-history object, meta field, trainer, update queue, recovery snapshot, and consumer. Do not collapse multiple predictors into one generic row when their indexes, histories, replacement rules, or update rules differ.
- Correct prediction: for each predictor, construct taken/not-taken, target-hit, return-hit, indirect-target-hit, no-violation, or no-prefetch-miss cases as applicable, then check selected prediction, metadata, confidence/counter update, and no spurious redirect/replay.
- Incorrect prediction: for each predictor, construct wrong direction, wrong target, wrong return address, wrong indirect target, false positive, false negative, alias-hit, and stale-entry cases when reachable, then check redirect/replay, architectural recovery, wrong-path state removal, and correct retraining.
- History completeness: for every history used by a predictor, cover reset value, all-zero history, all-one history, single-bit oldest/newest positions, alternating patterns, saturated length, fold-boundary patterns, shift-in on prediction, commit update, speculative update, redirect recovery, nested redirect recovery, and context switch/fence handling when reachable.
- History consumers: every table index, tag, confidence path, chooser path, replacement path, RAS operation, update decision, and recovery snapshot that consumes history must be checked against the exact code-derived history value before and after update/recovery.
- Lookup/update timing: include lookup-only, update-only, same-entry lookup+update, lookup+redirect, update+redirect, multi-update same cycle, queue full/almost-full, and reset/flush while update is pending.
- Context isolation: train the predictor under one privilege/process/VM/domain context and probe it under another. The driver must prove entries are tagged, flushed, rechecked, or harmlessly speculative according to code.
- Scoreboard rule: a correct prediction must not hide an illegal state update, and an incorrect prediction must not cause architectural corruption after recovery.

## Cache Structure Rules

Every module driver for ICache, DCache, L1Cache, L2, LLC, XSCache, directory, tag/data/meta array, MSHR, miss/replay/refill queue, writeback buffer, prefetch table, or cache-maintenance/WPU path must include `Cache Structure Verification` using `skills/cacheStructureDrivers.md`.

Minimum cache coverage:

- Hit: clean hit, dirty hit, valid/tag/permission/coherence-state match, same-index/different-context non-aliasing, and array update/bypass behavior when reachable.
- Miss: invalid-line miss, same-set/different-tag miss, MSHR allocate, MSHR merge, MSHR full, refill response, replay/backpressure, and reload-after-refill.
- Replace: clean victim, dirty victim, replacement-state update, writeback/release, refill installation, and probe/invalidate race.
- Bank conflict: simultaneous legal accesses to the same bank/port; winner rule, loser stall/retry/replay, and starvation/fairness behavior.
- Set conflict: same-set/different-tag and same-set/different-way conflicts across fetch/load/store/refill/probe/cache-op paths.
- Cache full: all ways or all MSHR/replay/writeback/refill/protocol-tracker entries full or almost full; backpressure, drain, no duplicate allocation, and no lost live request.
- Flush+reload: every code-reachable flush/invalidate/redirect/context/probe/cache-op/full-recovery path must be followed by reload/refill checks proving stale data, stale tags, stale permissions, and stale miss responses cannot be observed.

## Conflict Scenario Rules

Every nontrivial module driver must include a `Conflict Scenario Verification` section. Select applicable scenarios from `skills/conflictScenarioDrivers.md`, then refine each selected conflict with exact code evidence.

Minimum conflict coverage:

- Same-entry and same-index conflicts: read/write same entry, multiple writes same entry, lookup/update/replace same entry, same set/way/bank, same physical register, same ROB/LSQ/MSHR/PTW/replay entry.
- Port conflicts: more requesters than read/write ports, response ports, wakeup ports, issue ports, writeback ports, predictor ports, cache/TLB ports, bus channels, or CSR/config ports; include older low-priority request versus newer high-priority request for every mux/arbiter/select path.
- Queue conflicts: enqueue/dequeue at empty/full/almost boundaries, simultaneous multi-enqueue/multi-dequeue, and enqueue/dequeue racing with flush, cancel, replay, or redirect.
- Pipeline conflicts: stall versus flush, redirect versus writeback, redirect versus commit, replay versus exception, exception versus interrupt/debug, and first request after reset.
- Memory/cache/MMU conflicts: load-store forwarding, load violation, LR/SC reservation invalidation, AMO serialization, TLB refill versus invalidation, PTW allocation, page-fault versus access-fault, MSHR merge/allocation, refill/probe/store, writeback/evict/refill, and uncache/MMIO ordering.
- Frontend/predictor conflicts: predictor lookup/update, history redirect/update, RAS push/pop/redirect, FTQ enqueue/commit/redirect, IBuffer dequeue/flush, and ICache fetch/refill/invalidate.
- Bus/protection/interrupt conflicts: AXI channel and crossbar contention, AIA interrupt priority, AIA MMIO update versus interrupt arrival, IOPMP lookup versus config write, and deny/error response versus normal response.
- Context-switch conflicts: privilege, process, VM, and supervisor-domain switch while resource entries, requests, responses, predictions, translations, permissions, or misses are live.
- Forward-progress conflicts: all-stall deadlock, backpressure-cycle deadlock, replay/redirect livelock, fixed-priority starvation, old-low-priority versus new-high-priority mux/arbiter starvation, and FSM retry-cycle/lower-transition starvation.

For every conflict, state the winner rule, loser behavior, affected state, and checker. If code treats the conflict as illegal, the driver must check the assertion or unreachable condition.

## Shared Resource Context-Switch Rules

For every shared-resource module, add all of these scenarios:

- Privilege switch: U/S/M and HS/VS/VU if hypervisor is enabled. Check tags, permission state, flush requirements, exception routing, and no stale privilege leak.
- Process switch: `satp` or ASID change. Check TLB/cache/predictor/queue entries, outstanding misses, replay entries, and commit-visible ordering.
- Virtual machine switch: `hgatp`, VS-stage context, VMID, guest page faults, nested translation state, and AIA/IMSIC virtualization routing.
- Supervisor domain switch: supervisor-domain or security-domain CSR/config switch when implemented by the target branch. Check domain-tagged queues, permission tables, cacheability/protection state, redirects, and stale-entry invalidation.

Apply these to shared structures such as predictors, FTQ, IBuffer, rename maps/free lists, ROB, issue queues, LSQ, store buffer, TLB/PTW, caches, MSHR, replay queues, prefetchers, AIA/IMSIC/APLIC, IOPMP, AXI/TL bridges, and shared register/cache datapaths.

## Common Event Library

Use these event names consistently in all module drivers:

- `Q_EMPTY`: no valid entry; dequeue must not fire or must report invalid.
- `Q_ALMOST_EMPTY`: occupancy at low watermark; consumer behavior and refill trigger checked when implemented.
- `Q_FULL`: no free entry; upstream ready/canAccept must deassert or allocation must be blocked.
- `Q_ALMOST_FULL`: high watermark; early backpressure, redirect prevention, or throttling checked when implemented.
- `HS_VALID_STALL`: valid high and ready low; payload must remain stable.
- `HS_READY_IDLE`: ready high and valid low; no state update.
- `HS_FIRE`: valid and ready high; exactly one transaction accepted.
- `FSM_RESET`: reset reaches documented idle/empty state.
- `F_FIRST_REQUEST`: first legal request drives idle state into the first active state.
- `F_HOLD_BACKPRESSURE`: backpressure or missing response holds the current FSM state.
- `F_REQ_AND_FLUSH`: request acceptance and flush/redirect compete in the same cycle.
- `F_RESP_AND_REPLAY`: response completion and replay request compete in the same cycle.
- `F_CONTEXT_SWITCH_BUSY`: privilege/process/VM/domain switch arrives while FSM is busy.
- `ARB_ALL_REQ`: all requesters assert together; winner and losers follow code priority/fairness.
- `ARB_OLD_LOW_NEW_HIGH`: older low-priority request remains valid while newer high-priority requests arrive.
- `P_DEADLOCK_ALL_STALL`: all visible clients stall until one legal sink is released.
- `P_LIVELOCK_REPLAY_LOOP`: replay or retry repeats without useful completion.
- `P_STARVE_OLD_LOW_NEW_HIGH`: older low-priority request risks starvation behind newer high-priority traffic.
- `REDIRECT`: younger speculative state is killed and recovery pointer/target is used.
- `REPLAY`: replayable operation is reissued without architectural double commit.
- `PORT_CONTENTION`: more clients than physical ports; losing clients stall, retry, or replay as code defines.
- `RESOURCE_CONTENTION`: table/queue/MSHR/buffer/free-list exhausted.
- `CONFLICT`: same bank/set/entry/index or incompatible operation pair in one cycle.
- `C_SAME_ENTRY_RW`: read and write target the same storage entry in one cycle.
- `C_MULTI_WRITE_SAME_ENTRY`: multiple writers target the same storage entry in one cycle.
- `C_BANK_CONFLICT`: multiple legal accesses map to the same bank/set/way/port.
- `C_REDIRECT_REDIRECT`: multiple redirect sources compete in one recovery window.
- `C_REPLAY_EXCEPTION`: replay and exception actions overlap for the same or related operation.
- `DBG_EBREAK_MODE`: `ebreak` debug-entry enable controls are swept across implemented privilege modes.
- `DBG_EXC_INT_SAME_POINT`: debug, exception, and interrupt are visible at the same trap decision point.
- `DBG_REPLAY_REDIRECT`: debug event overlaps replay or wrong-path redirect and remains precise only when legal.
- `C_TLB_REFILL_INVALIDATE`: TLB/PTW refill races an invalidation or context switch.
- `C_AXI_XBAR_MULTI_MASTER`: multiple AXI masters target the same slave/channel.
- `I_WRAP_PTR`: index or pointer wraps from maximum value to zero.
- `B_AXI_ID_REUSE`: an AXI ID is requested again while still outstanding.
- `H_SAME_INDEX_DIFF_TAG`: two addresses or PCs produce the same hash/index with different tags.
- `H_SAME_INDEX_SAME_TAG_DIFF_CONTEXT`: same hash/index/tag appears under different ASID/VMID/domain/privilege context.
- `CTX_PRIV_SWITCH`: privilege mode change while resource has live entries.
- `CTX_PROCESS_SWITCH`: ASID/process switch while resource has live entries.
- `CTX_VM_SWITCH`: VMID or guest context switch while resource has live entries.
- `CTX_SD_SWITCH`: supervisor-domain switch while resource has live entries.

## Checker Requirements

Generated drivers should instantiate or request these checker types:

- Occupancy checker: tracks queue count or valid-vector model and compares empty/full/almost flags.
- Handshake checker: checks ready/valid/fire, payload stability under stall, and no double accept.
- FSM checker: checks legal transitions, output permissions per state, wait-state exit, retry-cycle exit, and low-priority transition starvation.
- Forward progress checker: checks deadlock, livelock, starvation, fairness assumptions, code-derived wait bounds, and old-request progress under newer high-priority traffic.
- Arbiter checker: checks grant one-hot, priority/fairness, ready feedback, and loser persistence.
- Flush/replay checker: checks killed entries do not commit and replayed entries commit once.
- Context isolation checker: checks privilege/process/VM/domain tags block stale data and stale permissions.
- Debug checker: checks `dcsr`, `dpc`, debug cause, debug-mode restrictions, resume privilege/PC, trigger/step/halt behavior, and debug priority against trap/interrupt/replay/redirect sources.
- Architecture scoreboard: compares commit, exception, CSR, interrupt, memory-order, and translation effects with the spec-derived model.

