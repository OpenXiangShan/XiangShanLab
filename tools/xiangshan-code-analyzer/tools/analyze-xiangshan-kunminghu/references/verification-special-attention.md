# Verification Special Attention

Use this reference for every generated XiangShan analysis document. Derive verification concerns from the XiangShanLab-relative path `tools/verification-driver/skills`.

The code analysis remains the source of effective microarchitectural behavior. Verification-driver skills define how to turn that behavior into stimulus, expected observations, cross coverage, and checkers.

## Required Output Section

Every module document must include a section named `验证特别注意`:

| Verification ID | Risk / invariant | Directed stimulus | Expected observation | Required checker / coverage |
| --- | --- | --- | --- | --- |

Do not write generic advice. Name exact signals, pointers, entries, tables, states, arbiters, ports, and exception fields. Cite effective Chisel lines. State producer, consumer, acceptance condition, simultaneous-event priority, state change, recovery, and forward progress.

## Common Required Scenarios

| ID | Required construction | Expected check |
| --- | --- | --- |
| `F_RESET_IDLE` | Hold/release reset and issue first legal request | Reset state, pointers, counters, valid bits, and first transition |
| `F_FIRST_REQUEST` | First request after reset/empty | No stale payload and correct active transition |
| `F_HOLD_BACKPRESSURE` | Producer valid while consumer ready is low | State/payload hold and no double accept |
| `F_REQ_AND_FLUSH` | Acceptance competes with flush/redirect | Code-derived priority; killed work stays invisible |
| `F_RESP_AND_REPLAY` | Completion competes with replay/retry | One legal completion or replay; no duplicate update |
| `C_SAME_ENTRY_RW` | Read/write same entry or index | Read-old/read-new/bypass/stall matches code |
| `C_MULTI_WRITE_SAME_ENTRY` | Multiple writers target one entry | Priority/assert/mask behavior matches code |
| `C_BANK_CONFLICT` | Multiple requests target one bank/port | Winner, loser persistence, retry, backpressure, fairness |
| `C_REDIRECT_REDIRECT` | Multiple redirect sources overlap | One code-derived recovery target and state |
| `RESOURCE_CONTENTION` | Fill all queue/table/MSHR/buffer entries | Full/almost-full and allocation blocking |
| `I_WRAP_PTR` | Circular pointer moves max to zero | Flag/phase preserves age and full/empty |
| `H_SAME_INDEX_DIFF_TAG` | Same index, different tags | Hit/miss/replace and alias behavior |
| `H_SAME_INDEX_SAME_TAG_DIFF_CONTEXT` | Same index/tag, different context | No stale prediction/data/permission leakage |
| `P_DEADLOCK_ALL_STALL` | Stall sinks, then release one | Legal drain and eventual completion |
| `P_LIVELOCK_REPLAY_LOOP` | Repeated replay/retry/conflict | Eventual useful progress under stated fairness |
| `P_STARVE_OLD_LOW_NEW_HIGH` | Old low priority plus new high priority | Old request progress or documented starvation |
| `PB_BURST_ABSORB_DRAIN` | Burst-fill, stop producers, drain | Expected capacity and return to empty |
| `PB_BACKPRESSURE_AMPLIFICATION` | Block one sink | Exact upstream propagation and recovery boundary |
| `PB_RECOVERY_THROUGHPUT` | Saturate, recover, resume | No stale completion and throughput recovery |

## Required Checkers

- `Handshake checker`: ready/valid/fire, payload stability, no accept without fire, no double accept.
- `Occupancy checker`: count/valid-vector model, empty/full/almost, simultaneous enqueue/dequeue.
- `Pointer-age checker`: circular pointer value/phase, wrap ordering, redirect restoration.
- `FSM checker`: legal transitions, output permissions, hold/exit conditions, flush priority.
- `Arbiter checker`: one-hot grant, priority/fairness, loser persistence, ready feedback.
- `Storage conflict checker`: same-index read/write, multiple writes, bank conflict, bypass behavior.
- `Flush/replay checker`: killed work never commits/trains; replayed work completes once.
- `Predictor metadata scoreboard`: prediction meta matches the same FTQ entry at update/recovery.
- `History/RAS recovery checker`: history, folded history, pointers, tops, and snapshots restore exactly.
- `Forward-progress checker`: deadlock, livelock, starvation, drain, retry exit, old-request progress.
- `Architecture exception scoreboard`: fetch cause, PC/tval/gpa, priority, and commit visibility.
- `Context isolation checker`: context change cannot expose stale translation, permission, prediction, or data.
- `Performance checker`: occupancy, stalls, redirect latency, throughput, useful/wasted prefetch, miss/replay rate.

## Predictor Routing

For `Bim`, `FauFTB`, `FTB`, `Tage`, `SC`, and `ITTAGE`, cover reset/first lookup, exact index/hash alias groups, lookup/update conflicts, counter saturation, provider/alternate selection, allocation/replacement failure, useful-bit aging, multi-hit/false-hit, wrong-path training suppression, and metadata alignment.

Minimum IDs: `F_RESET_IDLE`, `F_FIRST_REQUEST`, `C_SAME_ENTRY_RW`, `H_SAME_INDEX_DIFF_TAG`, `C_MULTI_WRITE_SAME_ENTRY`, `F_REQ_AND_FLUSH`, `P_LIVELOCK_REPLAY_LOOP`, `PB_RECOVERY_THROUGHPUT`.

## BPU / Composer Routing

Cover S0-S3 valid/ready/fire alignment, FTQ-full payload hold, component-ready aggregation, S2/S3/backend redirect priority, one-time history update, metadata split, repeated redirect progress, and recovery throughput.

Minimum IDs: `F_HOLD_BACKPRESSURE`, `C_REDIRECT_REDIRECT`, `F_REQ_AND_FLUSH`, `PB_BACKPRESSURE_AMPLIFICATION`, `P_LIVELOCK_REPLAY_LOOP`, `PB_RECOVERY_THROUGHPUT`.

## FTQ Routing

Model `bpuPtr`, `ifuPtr`, `pfPtr`, `ifuWbPtr`, `commPtr`, and `robCommPtr`; cover empty/full, same-cycle allocate/reclaim, pointer wrap, BPU overwrite versus backend redirect, all entry-status transitions, `pdWb` races, predictor update ordering, fill/drain, and forward progress.

Minimum IDs: `RESOURCE_CONTENTION`, `I_WRAP_PTR`, `F_REQ_AND_FLUSH`, `C_REDIRECT_REDIRECT`, `C_SAME_ENTRY_RW`, `PB_BURST_ABSORB_DRAIN`, `P_DEADLOCK_ALL_STALL`.

## IBuffer / Queue Routing

Cover empty/one/almost-full/full/wrap, variable enqueue/dequeue counts, simultaneous dequeue/enqueue, empty bypass, per-lane payload hold, flush races, bank rotation, age order, and backpressure amplification.

Minimum IDs: `F_FIRST_REQUEST`, `F_HOLD_BACKPRESSURE`, `RESOURCE_CONTENTION`, `I_WRAP_PTR`, `F_REQ_AND_FLUSH`, `C_BANK_CONFLICT`, `PB_BURST_ABSORB_DRAIN`, `PB_BACKPRESSURE_AMPLIFICATION`.

## RAS Routing

Cover empty pop, near overflow, recursive counter saturation, push/pop/cancel/redirect/commit overlap, pointer/top snapshots, spec queue wrap, commit mismatch repair, and wrong-path isolation.

Minimum IDs: `F_FIRST_REQUEST`, `RESOURCE_CONTENTION`, `I_WRAP_PTR`, `F_REQ_AND_FLUSH`, `C_REDIRECT_REDIRECT`, `C_SAME_ENTRY_RW`, `PB_RECOVERY_THROUGHPUT`.

## ICache Routing

Cover hit/miss/replace/refill, two-line combinations, MSHR allocate/merge/full/source routing, WayLookup empty/full/bypass/wrap, ITLB/PMP/PMA/PBMT/MMIO, array conflicts, fence.i and flush+reload, fetch exception priority, context switches, demand/prefetch priority, and WFI drain.

Minimum IDs: `CACHE_HIT`, `CACHE_MISS_INVALID`, `CACHE_MSHR_MERGE`, `CACHE_MSHR_FULL`, `CACHE_ARRAY_RW_CONFLICT`, `CACHE_FR_MISS`, `CACHE_FR_FULL`, `C_TLB_REFILL_INVALIDATE`, `E_MEM_PAGE_ACCESS`, `CTX_VM_SWITCH`, `P_DEADLOCK_ALL_STALL`.

## Decode Routing

Cover Rename backpressure, simple/complex selection, complex-uop expansion count and order, multiple-complex oldest selection, redirect versus vtype update, illegal/virtual-instruction exception priority, default-safe decoding, and recovery throughput.

## Rename Routing

Cover all physical-register classes atomically: free-list empty/almost/full, simultaneous allocate/free/walk, pointer wrap, same-cycle map read/write bypass, duplicate logical destinations, snapshot redirect recovery, allocation uniqueness, no leak/double-free, and forward progress.

## Move-Elimination Routing

Cover legal move qualification, source readiness, destination-map sharing, reference-count increment/decrement overflow and underflow, same-pdest multi-update, redirect rollback, eliminated-uop execution suppression, commit/difftest visibility, and recovery throughput.

## Dispatch Routing

Cover ROB/LSQ/dispatch-queue resource crosses, prefix-order partial dispatch, queue/port arbitration, ready/valid hold, redirect races across every sink, exception/single-step/eliminated-move routing, no lost/duplicated uops, starvation, drain, and throughput recovery.

## Memory-Dependence Prediction Routing

Cover SSIT same-index aliases and lookup/update conflicts, store-set merge, LFST empty/full/wrap and allocate/release races, redirect-stale dependencies, WaitTable training, false positives/negatives, replay livelock, unnecessary serialization, and recovery throughput.

## RegCache Routing

Cover invalid/stale reads, same-entry multi-write, tag/data pipeline alignment, read/write/replace priority, tag multi-hit, slot reuse after cancel, age ordering and replacement uniqueness, slot/port pressure, architectural correctness on miss, and hit-rate recovery.

## Exception / Virtualization Routing

Construct page-fault plus access-fault candidates, sweep implemented privilege/virtualization modes, cross address boundaries, switch ASID/VMID/privilege/domain with live entries, prevent stale permissions/exceptions, and verify debug/trigger versus redirect/exception priority.

## Quality Gate

- Include at least six module-specific rows for nontrivial modules.
- Cover every FSM reset/transition/hold/simultaneous-event/flush/progress case.
- Cover every queue/buffer/stack empty/full/almost/simultaneous/wrap/flush case.
- Cover every predictor table alias/conflict/saturation/allocation/replacement/wrong-path update case.
- Cover every cache-like hit/miss/replace/conflict/full/flush+reload case.
- Name expected observations and checker(s), and cite exact effective source lines.
