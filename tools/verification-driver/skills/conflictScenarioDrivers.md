# XiangShan Conflict Scenario Drivers

Use this file when generating conflict-focused verification drivers for XiangShan modules. A conflict scenario is any case where two or more legal events compete for the same state, port, index, queue entry, protocol channel, redirect path, replay path, exception path, or context-owned resource in the same or overlapping cycles.

Each module driver must select the applicable rows from this library and refine them with exact code evidence from the analyzer output. Debug-event conflicts must also select applicable rows from `skills/debugEventDrivers.md`. Deadlock, livelock, and starvation conflicts must also select applicable rows from `skills/forwardProgressDrivers.md`.

Cache-like structures must also use `skills/cacheStructureDrivers.md` so hit, miss, replacement, bank conflict, set conflict, cache full, and flush+reload behavior are covered together instead of as isolated conflicts.

## Conflict Driver Shape

```markdown
## Conflict Scenario Verification
| Conflict ID | Conflict class | Stimulus | Expected winner/priority | Expected loser behavior | State/checkers |
| --- | --- | --- | --- | --- | --- |
```

For every selected conflict, include:

- Source evidence: producer signals, arbiter/mux/FSM/storage lines, and consumer state updates.
- Conflict trigger: same cycle, overlapping cycles, same index, same bank, same queue entry, same redirect epoch, same privilege/context tag, or same protocol channel.
- Winner rule: fixed priority, age priority, oldest-first, round-robin, one-hot select, ready/valid fire order, FSM state priority, or illegal/asserted case.
- Progress rule: whether a losing request is guaranteed eventual service, can be starved by design, is promoted by age, is replayed to another fair path, or must remain valid until service.
- Loser rule: stall, hold valid, retry, replay, drop, merge, squash, poison, exception, or assert.
- State effect: valid bits, pointers, counters, replacement state, replay state, exception metadata, context tags, and payload registers.

## Core Conflict Drivers

| Conflict ID | Conflict class | Stimulus | Expected winner/priority | Expected loser behavior | State/checkers |
| --- | --- | --- | --- | --- | --- |
| `C_SAME_ENTRY_RW` | Same-entry read/write | Read and write the same table/array entry in one cycle | Code-defined bypass, old-data, new-data, or no-read priority | Reader receives defined value, stalls, or replays | Storage checker, data scoreboard |
| `C_MULTI_WRITE_SAME_ENTRY` | Multiple writes same entry | Two writers update same valid bit, table row, queue entry, CSR field, predictor entry, or cache metadata entry | Code priority or assert condition | Losing write masked, stalled, merged, replayed, or illegal | Storage checker, assert checker |
| `C_MULTI_READ_LIMITED_PORT` | Read-port contention | More readers than physical ports for regfile, FTQ, table, cache, TLB, predictor, or queue | Arbiter or banking rule selects serviced readers | Losing readers hold request or replay | Port checker, handshake checker |
| `C_BANK_CONFLICT` | Bank conflict | Multiple accesses map to same bank/set/way/port | Bank conflict policy selects winner or merges compatible ops | Loser stalls, retries, or replays | Conflict checker, replay checker |
| `C_QUEUE_ENQ_DEQ_BOUNDARY` | Queue boundary conflict | Enqueue and dequeue when queue is empty, almost empty, full, or almost full | Pointer/count update follows code rule | Backpressure/valid reflects next-state occupancy | Occupancy checker |
| `C_QUEUE_FLUSH_ENQ_DEQ` | Queue operation versus flush | Enqueue, dequeue, and redirect/flush/cancel in one cycle at empty, almost-empty, one-live-entry, full, almost-full, and wrap boundaries | Flush/cancel priority and pointer/count update are defined by code for each occupancy extreme | Killed entry cannot become visible; legal survivor preserved; empty/full/almost flags remain correct | Flush checker, occupancy checker |
| `C_FLUSH_EXTREME_OCCUPANCY` | Flush at microarchitecture extremes | Assert every code-reachable flush/redirect/cancel/kill while affected queues, buffers, replay slots, MSHRs/PTWs, ROB/LSQ/issue entries, update queues, or protocol trackers are empty, almost empty, one-entry live, full, almost full, wrapped, and simultaneously enq/deq when reachable | Flush priority, drain policy, and survivor policy follow code for each extreme state | Killed state cannot later fire/commit/update; no double-free, lost survivor, stale flag, or permanent backpressure | Flush extreme-state checker, occupancy checker |
| `C_ALLOC_FREE_SAME_RESOURCE` | Allocate/free conflict | Allocation and release target same free-list, ROB, LSQ, MSHR, PTW, issue slot, or predictor entry | Allocation/free order follows code | No double allocation, lost free, or negative occupancy | Resource checker |
| `C_REPLACE_LOOKUP_UPDATE` | Replacement conflict | Lookup, update/train, and replacement choose same predictor/cache/TLB/directory entry | Lookup/update/replace priority follows code | Loser is retried, masked, merged, or sees defined old/new state | Replacement checker |
| `C_ARB_ALL_REQUESTERS` | Arbiter all-requesters conflict | All clients assert valid/request in same cycle | Fixed/age/RR priority grants exactly allowed winners | Losers see ready low or hold request | Arbiter checker |
| `C_ARB_OLD_LOW_NEW_HIGH` | Old low-priority versus new high-priority | Low-priority requester becomes valid first and remains valid while newer high-priority requests arrive | Code priority is observed and forward-progress policy for the older request is checked | Older loser eventually grants, ages/promotes, replays, or is documented as starvable | Arbiter/starvation checker |
| `C_ARB_PERSISTENT_HIGH_STARVE_LOW` | Persistent high-priority traffic | High-priority requester remains continuously valid while low-priority requester stays pending | Fairness guarantee or intentional starvation policy follows code evidence | Low-priority request must not be silently dropped or corrupted | Forward progress checker |
| `C_ARB_RR_AGE_WRAP` | Round-robin/age wrap conflict | All requesters remain valid across RR pointer or age counter wrap | Wrap preserves fairness and eligibility | No requester skipped forever because of wrap | RR/age checker |
| `C_MUX_PRIORITY_INVERSION` | Mux priority inversion | Older payload on low-priority mux input competes with newer payload on high-priority input | Selected input follows code priority; progress for older payload is explicitly checked | Non-selected payload holds stable, retries, or is killed by legal flush | Mux/starvation checker |
| `C_READY_VALID_DROP` | Handshake conflict | Valid is held while ready drops, then redirect/replay/flush arrives | Fire occurs only when both valid and ready are high and not killed | Payload stable under stall; killed payload not accepted | Handshake checker |

## Pipeline and Recovery Conflict Drivers

| Conflict ID | Conflict class | Stimulus | Expected winner/priority | Expected loser behavior | State/checkers |
| --- | --- | --- | --- | --- | --- |
| `C_REDIRECT_REDIRECT` | Redirect priority | Branch mispredict, exception redirect, replay redirect, interrupt/debug redirect in same window | Code-defined redirect priority selects one target/cause | Lower-priority redirects masked, deferred, or converted to replay | Redirect checker |
| `C_REDIRECT_COMMIT` | Redirect versus commit | Older commit and younger redirect in same cycle | Older architectural commit preserved; younger state killed | Killed younger update cannot commit | ROB/flush checker |
| `C_REDIRECT_WRITEBACK` | Redirect versus writeback | Killed uop reaches writeback or wakeup path | Age/redirect mask controls whether writeback is allowed | Wrong-path writeback masked; legal older writeback preserved | Writeback checker |
| `C_REPLAY_EXCEPTION` | Replay versus exception | Operation becomes replayable and faulting in overlapping cycles | Spec/code priority selects replay, exception, or delayed exception | Non-winning action cannot double-update state | Replay/exception checker |
| `C_REPLAY_REPLAY` | Replay queue contention | Multiple replay sources target limited replay port or queue | Replay selection follows code priority/fairness | Losing replay holds valid or is requeued | Replay queue checker |
| `C_STALL_FLUSH` | Stall versus flush | Pipeline stage stalled while flush arrives, including ready-low, response-pending, full downstream, empty upstream, and same-cycle fire boundaries | Flush priority clears or marks killed state per code at each stall extreme | Stalled killed payload cannot later fire, update state, or hold backpressure forever | Pipeline/flush checker |
| `C_FIRST_REQUEST_RESET_EXIT` | First request after reset | First valid request arrives as reset/initialization deasserts | FSM/valid state accepts only after legal initialization | Early request stalls or is ignored per code | FSM checker |
| `C_EXCEPTION_INTERRUPT_DEBUG` | Trap source priority | Exception, interrupt, debug trigger, halt request, and trap return condition overlap | RISC-V/spec and code priority select architecturally legal action with correct `dcsr`/`dpc` or EPC/cause update | Lower-priority action remains pending, is masked, or is killed per code | Trap/debug priority checker |

## Memory, Cache, and MMU Conflict Drivers

| Conflict ID | Conflict class | Stimulus | Expected winner/priority | Expected loser behavior | State/checkers |
| --- | --- | --- | --- | --- | --- |
| `C_LOAD_STORE_FORWARD_CONFLICT` | LSQ forwarding conflict | One load matches multiple older stores with partial masks | Oldest/latest eligible store rule follows code | Non-selected stores do not corrupt data | LSQ forwarding checker |
| `C_LOAD_VIOLATION_CONFLICT` | Memory-order conflict | Store resolves after younger load already executed | Violation detection selects affected younger load(s) | Younger load/dependents replay once | Memory replay checker |
| `C_LR_SC_CONFLICT` | Reservation conflict | LR/SC overlaps store/probe/context switch | Reservation update/invalidate follows spec and code | Failed SC reports failure without illegal memory update | LR/SC checker |
| `C_AMO_LOAD_STORE_CONFLICT` | Atomic port conflict | AMO competes with load/store/refill/probe | Atomic serialization wins as code requires | Losers stall/replay; atomic appears indivisible | Atomic checker |
| `C_TLB_REFILL_INVALIDATE` | TLB refill versus invalidation | PTW refill and SFENCE/HFENCE/context switch hit same entry | Invalidate/context priority prevents stale translation | Refill killed, tagged, or rechecked | TLB checker |
| `C_PTW_MULTI_MISS` | PTW entry conflict | Multiple TLB misses request limited PTW entries | Allocation/merge rule follows code | Losers stall, merge, or retry | PTW occupancy checker |
| `C_PAGEFAULT_ACCESSFAULT` | Fault priority conflict | Translation page fault and PMA/PMP/IOPMP access fault both possible | Spec/code priority selects one architectural exception | Lower-priority fault not reported | MMU/protection checker |
| `C_MSHR_MERGE_ALLOC` | MSHR conflict | Miss to existing line and new miss allocation happen together | Merge existing miss or allocate free entry by code rule | Losing request stalls/replays | MSHR checker |
| `C_REFILL_PROBE_STORE` | Cache coherence conflict | Refill, probe/invalidate, and store hit same line/set | Coherence/FSM priority preserves legal line state | Loser stalls, retries, or invalidates | Coherence checker |
| `C_WRITEBACK_EVICT_REFILL` | Replacement pipeline conflict | Dirty victim writeback, eviction, and refill overlap | Replacement FSM serializes legal ownership | Metadata/data arrays stay coherent | Replacement checker |
| `C_CACHE_HIT_MISS_SAME_SET` | Hit versus miss same-set conflict | Hit access and miss/refill/replacement access target the same set in overlapping cycles | Hit, miss, or refill path priority follows code and preserves data/meta coherence | Loser stalls, retries, or replays | Cache structure checker |
| `C_CACHE_FULL_FLUSH_RELOAD` | Full cache resource recovery conflict | All ways/MSHRs/replay/writeback/refill entries are full while flush/drain/reload is requested | Flush/drain frees legal state and reload allocates only after stale state is killed or rechecked | New request backpressures, retries, or reloads after recovery | Full flush checker |
| `C_UNCACHE_MMIO_ORDER` | Uncache/MMIO conflict | MMIO/uncache request overlaps cacheable request and fence | Ordering rule serializes as code/spec require | Younger memory op stalls or replays | Memory ordering checker |

## Frontend and Predictor Conflict Drivers

| Conflict ID | Conflict class | Stimulus | Expected winner/priority | Expected loser behavior | State/checkers |
| --- | --- | --- | --- | --- | --- |
| `C_PRED_LOOKUP_UPDATE` | Predictor lookup/update conflict | Lookup and training update same predictor entry | Read-old/read-new/update-first rule follows code | Prediction and update state are deterministic | Predictor storage checker |
| `C_HISTORY_REDIRECT_UPDATE` | History conflict | Predictor history update and redirect recovery overlap | Redirect recovery or commit update priority follows code | Wrong-path history removed | Predictor recovery checker |
| `C_RAS_PUSH_POP_REDIRECT` | RAS conflict | Call push, return pop, and redirect recovery overlap | RAS update/recovery rule follows code | Wrong-path push/pop undone | RAS checker |
| `C_FTQ_ENQ_COMMIT_REDIRECT` | FTQ pointer conflict | FTQ enqueue, commit dequeue, and redirect restore same cycle | Pointer/valid update priority follows code | No stale FTQ metadata consumed | FTQ occupancy checker |
| `C_IBUFFER_DEQ_FLUSH` | IBuffer conflict | Decode dequeue and frontend redirect flush overlap | Flush kills wrong-path instructions | Decode receives no killed instruction | IBuffer checker |
| `C_ICACHE_FETCH_REFILL_INVALIDATE` | ICache conflict | Fetch, refill, and invalidate same set/way | Invalidate/refill/fetch priority follows code | Fetch stalls/retries or returns valid data only | ICache checker |

## Backend Conflict Drivers

| Conflict ID | Conflict class | Stimulus | Expected winner/priority | Expected loser behavior | State/checkers |
| --- | --- | --- | --- | --- | --- |
| `C_RENAME_ALLOC_FREE_REDIRECT` | Rename conflict | Rename allocates, commit frees, redirect recovers snapshot | Redirect/free/alloc order follows code | No leaked or double-allocated physical register | Rename checker |
| `C_DISPATCH_MULTI_TARGET_FULL` | Dispatch resource conflict | Bundle contains uops for multiple targets and one target queue is full | Atomic dispatch or per-target acceptance follows code | Unaccepted uops remain valid and unallocated | Dispatch checker |
| `C_ISSUE_WAKEUP_SELECT` | Issue conflict | Wakeup and select same entry in one cycle with many ready entries | Wakeup/select timing follows code | Entry selected once or waits | Issue checker |
| `C_WAKEUP_REDIRECT` | Wakeup versus redirect | Wakeup arrives for killed uop or killed consumer | Redirect mask prevents wrong-path readiness | Killed entry cleared | Issue/flush checker |
| `C_WRITEBACK_MULTI_PORT` | Writeback conflict | More FU results than writeback/regfile ports | Writeback arbiter grants legal winners | Losers stall/replay; no lost result | Writeback checker |
| `C_ROB_COMMIT_REDIRECT_EXCEPTION` | ROB conflict | Commit, branch redirect, exception, interrupt/debug overlap | Oldest precise architectural event wins; debug entry updates only legal debug state | Younger events killed or deferred | ROB/trap/debug checker |
| `C_CSR_WRITE_TRAP_INTERRUPT` | CSR conflict | CSR write, trap entry, interrupt update, and xRET overlap | CSR update priority follows spec and code | Non-winning CSR update masked/deferred | CSR checker |
| `C_PMC_CSR_EVENT_OVERFLOW` | Performance counter conflict | Counter CSR read/write/RMW, event increment, inhibit/event-select write, overflow update, trap/debug entry, and interrupt pending update overlap | Counter write/increment/overflow/trap priority follows spec and code | Non-winning update is held, merged, masked, or explicitly overwritten by code; no lost event, double count, impossible CSR read, or duplicate overflow interrupt | Performance counter checker |

## Bus, AIA, and IOPMP Conflict Drivers

| Conflict ID | Conflict class | Stimulus | Expected winner/priority | Expected loser behavior | State/checkers |
| --- | --- | --- | --- | --- | --- |
| `C_AXI_AW_W_B_CONFLICT` | AXI write-channel conflict | AW accepted without W, W stalls, B backpressured | Outstanding and beat state remain consistent | Channel payload held; no ID/order violation | AXI checker |
| `C_AXI_AR_R_CONFLICT` | AXI read-channel conflict | Multiple AR IDs outstanding and R backpressured | Response ID/data/last order follows protocol bridge code | Ready deasserts or buffers fill legally | AXI checker |
| `C_AXI_XBAR_MULTI_MASTER` | AXI crossbar conflict | Multiple masters target same slave | Xbar arbitration grants legal winner | Losers hold valid and payload stable | AXI arbiter checker |
| `C_AIA_INTERRUPT_PRIORITY` | Interrupt conflict | Multiple APLIC/IMSIC pending interrupts with same/different priority | Highest priority/delegation route selected | Lower interrupt remains pending if required | AIA checker |
| `C_AIA_MMIO_INTERRUPT_UPDATE` | AIA state conflict | MMIO/CSR write races interrupt pending update | Code-defined state update priority | No lost pending/enable state | AIA storage checker |
| `C_IOPMP_LOOKUP_CONFIG_WRITE` | IOPMP config conflict | Permission lookup and config write same entry | Lock/config priority follows code | Lookup sees defined old/new policy or stalls | IOPMP checker |
| `C_IOPMP_DENY_RESPONSE_DATA` | IOPMP response conflict | Deny/error response competes with normal data response | Deny/error priority and protocol response match code | Normal response masked or delayed | Protection checker |

## Context-Switch Conflict Drivers

| Conflict ID | Conflict class | Stimulus | Expected winner/priority | Expected loser behavior | State/checkers |
| --- | --- | --- | --- | --- | --- |
| `C_PRIV_SWITCH_LIVE_ENTRY` | Privilege switch conflict | Privilege changes while queues/TLB/predictor/cache entries are live | Entries are flushed, tagged, rechecked, or proven harmless | Stale privilege state cannot authorize access | Context isolation checker |
| `C_PROCESS_SWITCH_OUTSTANDING` | Process switch conflict | `satp`/ASID changes while misses/replays/fetches are outstanding | Old process entries killed, tagged, or revalidated | Stale response cannot commit to new process | Context isolation checker |
| `C_VM_SWITCH_OUTSTANDING` | VM switch conflict | `hgatp`/VMID/VS-stage state changes while transactions are outstanding | Guest context tags or flush prevent stale guest state | Stale guest response cannot update new VM | VM isolation checker |
| `C_SD_SWITCH_PERMISSION` | Supervisor-domain conflict | Supervisor-domain/security-domain changes while protected resource has live entries | Domain permission and tags are updated before next visible access | Stale domain permission cannot leak data | Domain isolation checker |

## Conflict Completion Checklist

Before a module conflict driver is complete:

- All storage structures have same-entry read/write and multi-write conflict coverage.
- All performance monitor counters have CSR read/write versus event update, inhibit/event-select versus event, overflow versus trap/debug/interrupt, and multi-counter simultaneous overflow conflict coverage when reachable.
- All arrays/tables/caches/TLBs/predictors have lookup/update/replace conflict coverage.
- All queues have enqueue/dequeue boundary and flush/enqueue/dequeue conflict coverage at empty, almost-empty, one-live-entry, full, almost-full, and wrap states.
- Every flush/redirect/cancel/kill path has microarchitecture extreme-state coverage across affected queues, buffers, FSM states, handshakes, full resources, empty resources, and same-cycle accept/response/commit boundaries.
- All arbiters and muxes have all-requesters-valid, each-requester-alone, older-low-priority versus newer-high-priority, persistent high-priority starvation, and pointer/age wrap coverage where applicable.
- All ready/valid interfaces have stalled-valid plus flush/replay conflict coverage.
- All pipelines have stall-versus-flush and redirect-versus-writeback/commit coverage where reachable.
- All memory modules have load/store/AMO/LR/SC, TLB/PTW, MSHR, refill/probe, and ordering conflict coverage where reachable.
- All cache-like structures use `skills/cacheStructureDrivers.md` and include hit, miss, replacement, bank conflict, set conflict, cache full, and flush+reload conflicts where reachable.
- All shared resources have privilege, process, VM, and supervisor-domain switch conflict coverage.
- Each conflict states winner rule, loser behavior, affected state, progress rule, fairness assumption, and checker.
- Hash-derived conflicts use `skills/indexBusHashDrivers.md` to generate same-index/different-tag, same-bank, same-context/different-context, and boundary address groups from the exact code-derived hash expression.
- FSM-related conflicts use `skills/fsmScenarioDrivers.md` to construct entry/hold/exit sequences, same-cycle competing-trigger priority tests, potential deadlock wait states, livelock cycles, and starvation of low-priority transitions for each state.
- Forward-progress conflicts use `skills/forwardProgressDrivers.md` to construct deadlock, livelock, starvation, and old-low-priority versus new-high-priority scenarios.
- Architecture exception conflicts use `skills/architectureExceptionDrivers.md` to construct per-instruction exception closure, memory multi-exception priority cases, and exception-plus-interrupt nesting scenarios.
- Debug event conflicts use `skills/debugEventDrivers.md` to construct debug versus exception, interrupt, replay, redirect, protection fault, and xRET priority cases from exact code-derived arbitration evidence.
- Operand-boundary conflicts use `skills/operandBoundaryDrivers.md` to sweep integer, floating-point, address, vector/mask, CSR/control, and bus protocol boundary values while conflict triggers are active.
- Virtualization/protection conflicts use `skills/virtualizationProtectionDrivers.md` to combine page, guest-page, PMP, PMA, IOPMP, interrupt, replay, debug, and context-switch candidates.
- System permission conflicts use `skills/systemVirtualizationPermissionDrivers.md` to combine permission traversal, leaf/non-leaf page-table behavior, guest/host trap interactions, and save/handle/restore phases.

