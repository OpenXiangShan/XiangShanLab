| Privileged control | MRET/SRET/DRET/WFI/fence, debug CSR access, and CSR side effects | Spec-defined privilege, debug-mode restrictions, and CSR behavior | CSR/debug privilege checker || Redirect priority | Branch, exception, interrupt, debug, replay, halt request, and xRET in same window | Architecturally correct redirect/trap/debug action wins with code-derived priority | Redirect/debug priority checker || Trap/debug/interrupt commit | Oldest exception plus interrupt/debug request, halt request, and trap return | Correct priority, CSR/debug update, and redirect target | Trap/debug checker || Global redirect | Branch mispredict, exception, interrupt, debug entry, and debug resume | Frontend/backend younger state flushed; older/debug state preserved according to code priority | Flush/replay/debug checker || Trap/interrupt/debug priority | Concurrent exception, interrupt, debug trigger, halt request, and xRET | One architecturally highest-priority action updates CSRs, debug state, and redirect target | Trap/debug priority checker |# XiangShan Module Verification Drivers

This file enumerates verification drivers for the module families listed by `analyze-xiangshan-kunminghu`. Each driver follows `skills/xiangshanVerificationDriver.md` and must be refined with exact Chisel line evidence before implementation.

## Top-Level Drivers

### XSCore / XSTile

Architecture Verification:

| Scenario | Stimulus | Expected architectural observation | Checkers |
| --- | --- | --- | --- |
| Reset to first instruction | Reset, boot fetch, first retired instruction | Architectural state starts from reset PC and legal privilege/CSR reset values | Architecture scoreboard, CSR reset checker |
| Trap/interrupt/debug priority | Concurrent exception, interrupt, debug trigger | One architecturally highest-priority action updates CSRs and redirect target | Trap priority checker |
| Privilege/process/VM/domain switch | Switch privilege, `satp`, `hgatp`, `vsatp`, supervisor-domain config | No stale translation, prediction, queue, or permission state is architecturally visible | Context isolation checker |

Microarchitecture Scenario Verification:

| Scenario | Stimulus | Expected microarchitectural observation | Checkers |
| --- | --- | --- | --- |
| Global redirect | Branch mispredict, exception, interrupt, debug entry | Frontend/backend younger state flushed; older state preserved | Flush/replay checker |
| Cross-subsystem contention | Fetch, LSU, cache, CSR, interrupt all active | Shared ports arbitrate by code priority and hold loser requests | Arbiter checker |
| Resource exhaustion | Fill ROB, FTQ, LSQ, MSHR, issue queues | Backpressure propagates to legal upstream boundary | Occupancy checker |
| Performance counter stress | Max concurrent frontend/backend/LSU/cache/trap/debug events with CSR reads/writes, inhibit toggles, overflow, flush, replay, and context switch | Counter deltas, overflow state, and visible CSR values match code/spec with no lost or double-counted event | Performance counter checker |

Shared-resource context switches are mandatory for all top-level drivers.

### Backend

Architecture Verification:

| Scenario | Stimulus | Expected architectural observation | Checkers |
| --- | --- | --- | --- |
| In-order retirement | Mixed ALU/load/store/branch/CSR stream | Commit order matches program order and precise exception rules | Architecture scoreboard, ROB checker |
| CSR/privilege effects | CSR writes, trap return, privilege-changing instructions | Later instructions see updated legal architectural state | CSR/privilege checker |

Microarchitecture Scenario Verification:

| Scenario | Stimulus | Expected microarchitectural observation | Checkers |
| --- | --- | --- | --- |
| Redirect while queues non-empty | Mispredict with rename/dispatch/issue/ROB occupied | Younger state cleared from all backend structures | Flush/replay checker |
| Port contention | Max decode/rename/dispatch/writeback width traffic | Arbiters select legal winners; losers stall or replay | Arbiter checker |
| Resource contention | Fill ROB, issue queues, LSQ, physical registers | Backpressure reaches rename/dispatch without corrupting state | Occupancy checker |

## Backend Pipeline Drivers

### backend/decode

Architecture Verification:

| Scenario | Stimulus | Expected architectural observation | Checkers |
| --- | --- | --- | --- |
| Legal instruction decode | ISA extension mix enabled by config | Decoded operation matches RISC-V encoding and extension legality | Spec decode checker |
| Illegal instruction | Unsupported extension, reserved encoding, privilege-illegal op | Illegal-instruction exception metadata is generated | Exception checker |
| System/CSR decode | ECALL/EBREAK/MRET/SRET/DRET/FENCE/FENCE.I/CBO/CSR | Architectural class and privilege requirement match spec | CSR/system checker |

Microarchitecture Scenario Verification:

| Scenario | Stimulus | Expected microarchitectural observation | Checkers |
| --- | --- | --- | --- |
| Decode handshake stall | Downstream not ready with valid decode input | Decode payload remains stable; no double issue | Handshake checker |
| Redirect during decode | Frontend redirect while decode valid | Younger decoded uops are killed | Flush checker |
| Port contention | Full decode width with mixed instruction lengths/classes | Per-lane decode outputs map to legal downstream lanes | Port checker |

### backend/rename

Architecture Verification:

| Scenario | Stimulus | Expected architectural observation | Checkers |
| --- | --- | --- | --- |
| Register dependency correctness | RAW/WAW/WAR dependency stream | Committed architectural registers match sequential model | Architecture scoreboard |
| Exception precise state | Exception after speculative renames | Architectural map recovers to oldest precise state | Rename recovery checker |

Microarchitecture Scenario Verification:

| Scenario | Stimulus | Expected microarchitectural observation | Checkers |
| --- | --- | --- | --- |
| Free-list empty/full | Consume all physical registers, then commit frees | Rename stalls at empty and resumes after free | Occupancy checker |
| Snapshot redirect | Branch snapshot allocated then mispredicts | Rename map and free list recover snapshot state | Flush/replay checker |
| Multi-port allocation conflict | Max rename width and max commit free width | Allocation/free priority and same-cycle behavior match code | Arbiter/storage checker |
| Context switch | Process/VM/domain switch with live speculative maps | No stale physical mapping leaks across context | Context isolation checker |

Queues/buffers: free list, rename snapshots, busy table, map tables. Cover empty, almost empty, full, almost full where implemented.

### backend/dispatch

Architecture Verification:

| Scenario | Stimulus | Expected architectural observation | Checkers |
| --- | --- | --- | --- |
| Dispatch preserves uop semantics | Mixed integer/fp/vector/mem/control stream | Uops enter compatible execution resources without changing architectural intent | Uop scoreboard |

Microarchitecture Scenario Verification:

| Scenario | Stimulus | Expected microarchitectural observation | Checkers |
| --- | --- | --- | --- |
| Issue queue full | Fill each target issue queue | Dispatch ready deasserts only for blocked target classes | Occupancy checker |
| ROB/LSQ contention | Dispatch needs ROB and LSQ while one is full | Allocation is atomic or rolled back per code | Resource checker |
| Redirect during dispatch | Redirect with partially dispatchable bundle | No partially killed uop remains allocated | Flush checker |
| Arbiter conflict | Multiple dispatch lanes target limited ports | Code priority/fairness preserved | Arbiter checker |

### backend/issue

Architecture Verification:

| Scenario | Stimulus | Expected architectural observation | Checkers |
| --- | --- | --- | --- |
| Dependency ordering | Producer/consumer dependency chains | Consumer observes producer value, not stale value | Scoreboard |
| Exception ordering | Exception-producing uop and younger ready uops | Precise exception behavior at commit | ROB/exception checker |

Microarchitecture Scenario Verification:

| Scenario | Stimulus | Expected microarchitectural observation | Checkers |
| --- | --- | --- | --- |
| Issue queue empty/full/almost | Fill and drain integer/fp/vector/mem issue queues | Empty/full/almost flags drive dispatch/issue correctly | Occupancy checker |
| Wakeup/select conflict | Many ready entries, fewer issue ports | Selector grants legal oldest/priority winners; losers remain valid | Arbiter checker |
| Port contention | More ready uops than execution ports | Losing entries hold or retry without duplicated issue | Port checker |
| Replay/redirect | Replayable load or branch redirect hits issued/yet-unissued uops | Killed uops do not execute; replayed uops issue once | Flush/replay checker |

### backend/exu

Architecture Verification:

| Scenario | Stimulus | Expected architectural observation | Checkers |
| --- | --- | --- | --- |
| ALU/branch/mul/div/fp/vector result | Directed instruction streams per enabled extension | Results and flags match ISA model | Architecture scoreboard |
| Exception result | Divide edge, FP exception, illegal/system side effect | Exception metadata and flags match spec | Exception checker |

Microarchitecture Scenario Verification:

| Scenario | Stimulus | Expected microarchitectural observation | Checkers |
| --- | --- | --- | --- |
| Execution port contention | Issue to every execution unit with limited writeback | Port arbiters select winners and backpressure losers | Arbiter checker |
| Long-latency busy | Divide/multiply/vector/FPU pipeline occupied | Busy/backpressure/replay behavior matches code | FSM/handshake checker |
| Redirect while executing | Branch redirect during multi-cycle op | Younger operations killed or allowed according to age rules | Flush checker |

### backend/fu

Architecture Verification:

| Scenario | Stimulus | Expected architectural observation | Checkers |
| --- | --- | --- | --- |
| Functional-unit ISA compliance | Directed op tests for each FU class | Result, exception, flags, CSR effects match spec | Architecture scoreboard |
| Privileged FU ops | CSR/trap-return/fence/cache-management ops | Privilege legality and side effects match spec | Privilege checker |

Microarchitecture Scenario Verification:

| Scenario | Stimulus | Expected microarchitectural observation | Checkers |
| --- | --- | --- | --- |
| FU input/output handshake | Stall FU response or input | Payload stability and no duplicate response | Handshake checker |
| FU FSM transitions | Multi-cycle FU request, cancel, response | FSM legal transitions and cancel behavior | FSM checker |
| Shared FU contention | Multiple pipelines request shared FU | Arbiter winner and loser backpressure match code | Arbiter checker |

### backend/datapath

Architecture Verification:

| Scenario | Stimulus | Expected architectural observation | Checkers |
| --- | --- | --- | --- |
| Bypass correctness | RAW chains across execution latencies | Architectural result equals sequential model | Scoreboard |

Microarchitecture Scenario Verification:

| Scenario | Stimulus | Expected microarchitectural observation | Checkers |
| --- | --- | --- | --- |
| Read/write port conflict | Same physical register read/write same cycle | Bypass or array semantics match code | Storage checker |
| Writeback contention | More writebacks than ports | Arbiter preserves legal winner and replays/stalls losers | Arbiter checker |
| Redirect during writeback | Killed uop reaches writeback path | Killed writeback is masked | Flush checker |

### backend/regcache

Architecture Verification:

| Scenario | Stimulus | Expected architectural observation | Checkers |
| --- | --- | --- | --- |
| Register value visibility | Producer/consumer stream through regcache | Consumer sees correct architectural source value | Scoreboard |

Microarchitecture Scenario Verification:

| Scenario | Stimulus | Expected microarchitectural observation | Checkers |
| --- | --- | --- | --- |
| Cache hit/miss | Access resident and non-resident physical registers | Hit supplies data; miss fetches/fills according to code | Storage checker |
| Port contention | Multiple reads/writes exceed regcache ports | Conflict is arbitrated or replayed | Port checker |
| Context switch | Process/VM/domain switch with cached physical entries | Stale entries invalidated or tagged as required | Context isolation checker |

### backend/rob

Architecture Verification:

| Scenario | Stimulus | Expected architectural observation | Checkers |
| --- | --- | --- | --- |
| Precise commit | Out-of-order completion with in-order commit | Architectural state changes in program order | ROB scoreboard |
| Trap/debug/interrupt commit | Oldest exception plus interrupt/debug request | Correct priority, CSR update, and redirect target | Trap checker |

Microarchitecture Scenario Verification:

| Scenario | Stimulus | Expected microarchitectural observation | Checkers |
| --- | --- | --- | --- |
| ROB empty/full/almost | Fill ROB, drain by commit, simultaneous enqueue/commit | Empty/full/almost flags and head/tail wrap match code | Occupancy checker |
| Commit port contention | Many ready commit entries, limited commit width | Oldest legal entries commit; others remain | Arbiter checker |
| Redirect recovery | Mispredict or exception with younger entries | Younger entries cleared; head/commit state preserved | Flush checker |
| Resource conflict | ROB allocate but LSQ/issue allocation fails | Atomicity and rollback match code | Resource checker |
| Retire counter pressure | Retire 0, 1, max commit width, trapped, replayed, flushed, debug-entering, and context-switching instruction windows | `instret`/`minstret` and commit-event counters count only code-defined retired instructions and do not count killed/replayed work twice | Performance counter checker |

### backend/ctrlblock

Architecture Verification:

| Scenario | Stimulus | Expected architectural observation | Checkers |
| --- | --- | --- | --- |
| Redirect priority | Branch, exception, interrupt, debug, replay in same window | Architecturally correct redirect/trap/debug action wins | Redirect priority checker |
| Privileged control | MRET/SRET/DRET/WFI/fence and CSR side effects | Spec-defined privilege and CSR behavior | CSR/privilege checker |

Microarchitecture Scenario Verification:

| Scenario | Stimulus | Expected microarchitectural observation | Checkers |
| --- | --- | --- | --- |
| Redirect conflict | Multiple redirect producers in one cycle | Code priority selects one and masks losers | Arbiter checker |
| Replay storm | Repeated replay requests under backpressure | Replay queue/control remains live and no double commit | Replay checker |
| Context switch | Privilege/process/VM/domain switch with pending redirect | Correct target and flush domain selected | Context isolation checker |

## Frontend Drivers

### Frontend / IFU.scala

Architecture Verification:

| Scenario | Stimulus | Expected architectural observation | Checkers |
| --- | --- | --- | --- |
| Fetch legality | Aligned, compressed, misaligned, page-faulting fetch | Correct instruction stream or instruction exception | IFU architecture checker |
| Fence.i and translation switch | Fence.i, `satp`/`hgatp`/`vsatp` changes | Stale fetched instructions/translations are not used | Context checker |

Microarchitecture Scenario Verification:

| Scenario | Stimulus | Expected microarchitectural observation | Checkers |
| --- | --- | --- | --- |
| Fetch queue/pipe empty-full | Stall backend, then release | Fetch queues backpressure and drain correctly | Occupancy checker |
| Redirect into fetch | Branch/trap/replay redirect during fetch request | Wrong-path fetch killed and target request issued | Flush checker |
| ICache/ITLB port contention | Miss, refill, TLB walk, redirect overlap | Request arbitration and cancellation match code | Arbiter checker |

### frontend/NewFtq.scala

Architecture Verification:

| Scenario | Stimulus | Expected architectural observation | Checkers |
| --- | --- | --- | --- |
| Program-order fetch metadata | Branch stream with exceptions | Commit/redirect metadata maps to correct fetch block | FTQ checker |

Microarchitecture Scenario Verification:

| Scenario | Stimulus | Expected microarchitectural observation | Checkers |
| --- | --- | --- | --- |
| FTQ empty/full/almost | Fill FTQ with backend stalled, then commit | Full/almost backpressure frontend; empty blocks dequeue | Occupancy checker |
| Redirect recovery | Mispredict to older FTQ entry | Pointers and valid bits recover correct entry | Flush checker |
| Multi-reader contention | Predictor, backend, commit read FTQ metadata | Read-port arbitration or replication behavior matches code | Port checker |
| Context switch | Process/VM/domain switch with FTQ entries | Stale prediction/translation metadata is not reused | Context isolation checker |

### frontend/IBuffer.scala

Architecture Verification:

| Scenario | Stimulus | Expected architectural observation | Checkers |
| --- | --- | --- | --- |
| Instruction ordering | Mixed 16/32-bit instruction stream | Decode receives architecturally ordered instructions | Instruction stream checker |

Microarchitecture Scenario Verification:

| Scenario | Stimulus | Expected microarchitectural observation | Checkers |
| --- | --- | --- | --- |
| IBuffer empty/full/almost | Backend stall and frontend burst | Valid/count/full/empty drive ready/valid correctly | Occupancy checker |
| Redirect flush | Redirect while buffer holds wrong-path entries | Wrong-path entries invalidated | Flush checker |
| Lane conflict | Boundary crossing or compressed expansion | Lane packing and dequeue priority match code | Data-path checker |

### frontend/BPU.scala and Predictors

Applies to BPU, FTB, Tage, ITTAGE, SC, Bim, RAS, and every code-instantiated local predictor, predictor table, folded-history object, trainer, update queue, meta path, and recovery snapshot under the frontend predictor hierarchy. Generated drivers must list each concrete predictor separately and must not rely on this family heading as proof that all predictors were covered.

Architecture Verification:

| Scenario | Stimulus | Expected architectural observation | Checkers |
| --- | --- | --- | --- |
| Prediction transparency | Any prediction stream | Wrong predictions recover with correct architectural commit | Architecture scoreboard |
| Correct prediction architectural transparency | For every predictor, drive a code-reachable correct direction/target/return/indirect/chooser/confidence prediction | No architectural redirect is required and commit matches the non-speculative instruction stream | Architecture scoreboard |
| Incorrect prediction architectural recovery | For every predictor, drive a code-reachable wrong direction, wrong target, wrong return, wrong indirect target, false positive, false negative, alias hit, or stale-entry prediction | Redirect/replay repairs the stream and final commit remains architecturally correct | Architecture scoreboard, redirect checker |
| Fence/context switch | Fence.i, process/VM/domain switch | No stale predictor state causes architectural corruption | Context checker |

Microarchitecture Scenario Verification:

| Scenario | Stimulus | Expected microarchitectural observation | Checkers |
| --- | --- | --- | --- |
| Predictor inventory closure | Enumerate BPU, FTB, Tage, ITTAGE, SC, Bim, RAS, and every discovered local predictor/table/history/trainer/meta path | Each component has its own lookup, update, replacement, recovery, and checker rows | Coverage closure checker |
| Predictor correct cases | For each predictor, train and lookup correct taken, correct not-taken, correct target, correct return, correct indirect target, correct chooser/SC override, and correct no-override cases when reachable | Prediction bits, target, meta, confidence, counter, provider/alternate provider, and no-redirect behavior match code | Predictor functional checker |
| Predictor wrong cases | For each predictor, train or alias wrong direction, wrong target, wrong return, wrong indirect target, stale entry, false positive, false negative, and wrong chooser/SC override cases when reachable | Redirect/replay cause, recovery target, retraining update, and wrong-path state removal match code | Predictor recovery checker |
| History complete coverage | Drive every history source used by indexes, tags, chooser, SC, RAS, provider selection, update, and replacement through reset, all-zero, all-one, oldest-bit-only, newest-bit-only, alternating, saturated-length, and fold-boundary patterns | Folded and unfolded history observed by every consumer equals the exact code-derived value before lookup, after speculative update, after commit update, after redirect recovery, and after nested redirect recovery | History checker |
| Predictor table conflict | Aliasing PCs, same index, simultaneous update/lookup | Read/write/replace priority matches code | Storage checker |
| Update queue full/almost | Train faster than predictor can update | Backpressure/drop/retry follows code | Occupancy checker |
| Redirect/recovery | Mispredict updates history and target | History/RAS/FTB/TAGE/ITTAGE/SC/Bim state recovers correctly | Flush/replay checker |
| Arbiter conflict | Multiple update sources or prediction consumers | Winner/loser behavior follows code | Arbiter checker |
| Context switch | Priv/process/VM/domain switch with trained predictor | Tagging, flush, or harmless stale prediction policy verified | Context isolation checker |

Predictor drivers must use paper-backed algorithm context when generated by the code analyzer. Correct and incorrect prediction scenarios are mandatory for every predictor in the inventory. Any predictor history that is not covered by reset, boundary, alias, speculative update, commit update, redirect recovery, nested redirect recovery, and context-switch/fence cases must be reported as an explicit coverage gap, not silently omitted.

### frontend/icache

Frontend ICache drivers must also include `Cache Structure Verification` using `skills/cacheStructureDrivers.md`.

Architecture Verification:

| Scenario | Stimulus | Expected architectural observation | Checkers |
| --- | --- | --- | --- |
| Instruction fetch hit/miss | Cacheable and uncacheable instruction fetches | Correct instruction bytes or access/page fault | ICache architecture checker |
| Fence.i/CBO | Instruction cache maintenance operation | Stale instruction is not architecturally used | Cache maintenance checker |

Microarchitecture Scenario Verification:

| Scenario | Stimulus | Expected microarchitectural observation | Checkers |
| --- | --- | --- | --- |
| Hit/miss/reload | Clean hit, miss, refill, reload same line | Hit/miss decision and reload-after-refill match tag/data/meta code | Cache structure checker |
| Miss queue/MSHR full | Many instruction misses | Full/almost-full backpressure fetch | Occupancy checker |
| Bank/set conflict | Fetch/refill/invalidate map to same bank/set | Winner/loser behavior and replay policy match code | Conflict checker |
| Flush+reload | `fence.i`, CBO, redirect, context switch, or invalidate then reload | Stale instruction bytes cannot hit after required invalidation | Flush+reload checker |
| Refill/probe conflict | Refill, fetch, invalidate same set/way | Conflict priority and data validity match code | Storage checker |
| ITLB/ICache contention | Translation miss and cache miss overlap | Arbiter/FSM transitions match code | FSM/arbiter checker |
| Context switch | `satp`/VM/domain switch during miss/refill | Stale translation/cache line not used illegally | Context isolation checker |

## Memory and Cache Drivers

### MemBlock

Architecture Verification:

| Scenario | Stimulus | Expected architectural observation | Checkers |
| --- | --- | --- | --- |
| Load/store/AMO/LR/SC correctness | Directed memory streams | Memory results, reservations, exceptions match spec | Memory scoreboard |
| Memory ordering | Fence, dependency, MMIO, atomics | Ordering matches RISC-V memory model and platform rules | Ordering checker |
| Translation/protection | Page, guest-page, PMP/PMA/IOPMP faults | Correct exception type and priority | MMU/protection checker |

Microarchitecture Scenario Verification:

| Scenario | Stimulus | Expected microarchitectural observation | Checkers |
| --- | --- | --- | --- |
| LSQ/SQ/SBuffer/MSHR full | Saturate each memory queue | Backpressure, replay, and commit interaction match code | Occupancy checker |
| Replay path | Load violation, TLB miss, cache miss, bank conflict | Replay once until success; no double commit | Replay checker |
| Port/resource contention | Load/store/cache/TLB/PTW ports oversubscribed | Arbiter grants legal winners and stalls/replays losers | Arbiter checker |
| Context switch | Priv/process/VM/domain switch with outstanding memory ops | Stale translation/permission/data is blocked | Context isolation checker |

### mem/pipeline

Architecture Verification:

| Scenario | Stimulus | Expected architectural observation | Checkers |
| --- | --- | --- | --- |
| Load/store class behavior | Integer, FP, vector, AMO, LR/SC, prefetch, fence, CBO | Result, ordering, and exception match spec | Memory architecture checker |

Microarchitecture Scenario Verification:

| Scenario | Stimulus | Expected microarchitectural observation | Checkers |
| --- | --- | --- | --- |
| Stage valid stalls | Stall each memory pipeline stage | Valid/payload hold and bubble rules match code | Pipeline checker |
| Bank/port conflict | Same bank/set/index accesses | Conflict response is stall/replay/priority per code | Conflict checker |
| Redirect/replay | Wrong-path memory op and replayable miss | Killed op suppressed; replayed op reissued once | Flush/replay checker |

### mem/lsqueue

Architecture Verification:

| Scenario | Stimulus | Expected architectural observation | Checkers |
| --- | --- | --- | --- |
| Load/store ordering | RAW/RAR/WAW, store-to-load forwarding, fences | Results and memory order match architectural model | LSQ scoreboard |
| Exception timing | Faulting load/store with younger ops | Precise exception at commit where required | Exception checker |

Microarchitecture Scenario Verification:

| Scenario | Stimulus | Expected microarchitectural observation | Checkers |
| --- | --- | --- | --- |
| LQ/SQ empty/full/almost | Fill/drain load queue and store queue | Capacity flags, pointers, and allocation/free match code | Occupancy checker |
| Forwarding conflict | Multiple older stores match one load | Priority/age forwarding selects correct store data | Arbiter/storage checker |
| Violation replay | Store resolves after younger load | Younger load and dependents replay | Replay checker |
| Context switch | Process/VM/domain switch with LQ/SQ entries | Entries flushed, drained, or isolated correctly | Context isolation checker |

### mem/sbuffer

Architecture Verification:

| Scenario | Stimulus | Expected architectural observation | Checkers |
| --- | --- | --- | --- |
| Store visibility | Store stream, fence, MMIO, exceptions | Stores become visible in legal order | Memory ordering checker |

Microarchitecture Scenario Verification:

| Scenario | Stimulus | Expected microarchitectural observation | Checkers |
| --- | --- | --- | --- |
| SBuffer empty/full/almost | Fill store buffer and drain to cache/MMIO | Full blocks enqueue; empty blocks dequeue | Occupancy checker |
| Merge/conflict | Stores to same line/beat/mask | Merge or conflict priority matches code | Storage checker |
| Probe/refill/contention | Store drain contends with load/refill/probe | Arbiter/FSM preserves ordering | Arbiter checker |
| Context switch | Switch process/VM/domain while stores pending | Stores drain or are ordered before context change as required | Context checker |

### mem/mdp

Architecture Verification:

| Scenario | Stimulus | Expected architectural observation | Checkers |
| --- | --- | --- | --- |
| Prediction transparency | Memory dependence prediction correct or wrong | Wrong prediction recovers without architectural corruption | Scoreboard |

Microarchitecture Scenario Verification:

| Scenario | Stimulus | Expected microarchitectural observation | Checkers |
| --- | --- | --- | --- |
| Predictor table conflicts | Aliasing load/store PCs and simultaneous update/lookup | Table read/write/replace priority follows code | Storage checker |
| Replay training | Violation trains predictor | Later scheduling changes but commit remains correct | Replay/training checker |
| Context switch | Process/VM/domain switch with trained entries | Stale dependence state is flushed, tagged, or harmless | Context isolation checker |

### mem/prefetch

Architecture Verification:

| Scenario | Stimulus | Expected architectural observation | Checkers |
| --- | --- | --- | --- |
| Prefetch transparency | Legal/illegal prefetch addresses | Prefetch must not change architectural state except allowed faults if implemented | Architecture checker |

Microarchitecture Scenario Verification:

| Scenario | Stimulus | Expected microarchitectural observation | Checkers |
| --- | --- | --- | --- |
| Prefetch queue full/almost | Generate aggressive prefetch stream | Throttle/drop/backpressure follows code | Occupancy checker |
| Demand conflict | Demand miss and prefetch miss compete | Demand priority or policy follows code | Arbiter checker |
| Context switch | Address-space/domain switch with queued prefetches | Stale prefetches killed or permission-rechecked | Context isolation checker |

### mem/vector

Architecture Verification:

| Scenario | Stimulus | Expected architectural observation | Checkers |
| --- | --- | --- | --- |
| Vector memory ops | Unit-stride, strided, indexed, mask, fault-only-first | Results, exceptions, and vstart behavior match spec | Vector memory checker |

Microarchitecture Scenario Verification:

| Scenario | Stimulus | Expected microarchitectural observation | Checkers |
| --- | --- | --- | --- |
| Element queue full/almost | Long vector memory ops under cache stalls | Element queues backpressure and drain legally | Occupancy checker |
| Lane/bank conflict | Many elements target same bank/cache port | Conflict policy and replay match code | Conflict checker |
| Redirect/exception | Fault mid-vector or branch redirect | Partial completion and replay/flush match code | Replay/exception checker |

### cache/L1Cache.scala and cache/dcache

L1/DCache drivers must also include `Cache Structure Verification` using `skills/cacheStructureDrivers.md`.

Architecture Verification:

| Scenario | Stimulus | Expected architectural observation | Checkers |
| --- | --- | --- | --- |
| Load/store/cache maintenance | Hits, misses, AMO, LR/SC, CBO, fence | Data, ordering, reservation, and maintenance effects match spec | Cache architecture checker |
| Protection/translation | PMP/PMA/TLB/IOPMP failures | Correct fault type and no illegal data return | Protection checker |

Microarchitecture Scenario Verification:

| Scenario | Stimulus | Expected microarchitectural observation | Checkers |
| --- | --- | --- | --- |
| Hit/miss/reload | Clean hit, dirty hit, miss, refill, reload same line | Hit/miss decision, data return, and reload-after-refill match code | Cache structure checker |
| MSHR/replay/refill queues full | Many cache misses and replays | Full/almost flags throttle requesters | Occupancy checker |
| Cache full flush+reload | Fill ways/MSHR/replay/writeback resources, trigger flush/drain, reload | Full condition recovers without stale hit or dropped request | Full flush checker |
| Bank/set conflict | Load/store/refill/probe/cache op map to same bank/set | Winner rule, loser replay/stall, and fairness match code | Conflict checker |
| Tag/data array conflict | Load/store/refill/probe same set/way/bank | Port conflict and priority match code | Storage/port checker |
| Replacement conflict | Miss replacement under probe/writeback | Victim selection and metadata update match code | Replacement checker |
| Context switch | Process/VM/domain switch with outstanding misses | Stale translation/permission state not reused | Context isolation checker |

### cache/mmu

Architecture Verification:

| Scenario | Stimulus | Expected architectural observation | Checkers |
| --- | --- | --- | --- |
| Address translation | Page modes, ASID, VMID, permissions | Physical address or page/access/guest fault matches spec | MMU checker |
| SFENCE/VFENCE/context switch | `sfence.vma`, `hfence`, `satp`, `hgatp`, `vsatp` | Stale TLB/PTW entries invalidated or tagged correctly | TLB context checker |

Microarchitecture Scenario Verification:

| Scenario | Stimulus | Expected microarchitectural observation | Checkers |
| --- | --- | --- | --- |
| TLB/PTW queue full | Many misses and walks | Miss queue/PTW entries full/almost-full backpressure | Occupancy checker |
| Permission conflict | Privilege/VM/domain changes during walk | Permission is checked with correct context | Context isolation checker |
| Port contention | I/D TLB, PTW, refill, invalidation compete | Arbiter/FSM winner and loser behavior match code | Arbiter checker |

### cache/wpu and CacheInstruction.scala

Architecture Verification:

| Scenario | Stimulus | Expected architectural observation | Checkers |
| --- | --- | --- | --- |
| Cache-management ops | CBO clean/flush/inval, fence.i, custom cache ops if implemented | Legal privilege, ordering, and side effects match spec/config | Cache op checker |

Microarchitecture Scenario Verification:

| Scenario | Stimulus | Expected microarchitectural observation | Checkers |
| --- | --- | --- | --- |
| WPU queue/FSM | Issue cache op while cache busy | FSM states and queue capacity control operation progress | FSM/occupancy checker |
| Conflict with demand access | Cache op, load/store/refill/probe same set | Conflict priority and blocking match code | Conflict checker |
| Context switch | Domain/process switch during cache op | Operation applies to intended context/domain only | Context isolation checker |

## XSCache / L2 / LLC / CHI Drivers

Applies to `coupledL2`, `openLLC`, `xscache/chi`, `xscache/common`, `coupledL2/prefetch`, `coupledL2/utils`, `openLLC/chi`, and `openLLC/utils`.

XSCache, L2, LLC, directory, and coherent-cache drivers must also include `Cache Structure Verification` using `skills/cacheStructureDrivers.md`.

Architecture Verification:

| Scenario | Stimulus | Expected architectural observation | Checkers |
| --- | --- | --- | --- |
| Coherent memory visibility | Multi-master load/store/AMO streams | Coherence and memory ordering preserve architectural visibility | Coherence scoreboard |
| Cache maintenance and protection | CBO/fence, PMP/PMA/IOPMP, MMIO | Correct data, ordering, and faults | Protection/order checker |
| Context switches | Process/VM/domain switch under shared cache residency | No stale permission or domain data leaks | Context isolation checker |

Microarchitecture Scenario Verification:

| Scenario | Stimulus | Expected microarchitectural observation | Checkers |
| --- | --- | --- | --- |
| Hit/miss/reload | Shared/private line hit, miss, refill, reload | Coherence/tag/data/meta state produces legal hit/miss/reload behavior | Cache structure checker |
| MSHR/source/sink queues full | Saturate misses, probes, releases, grants | Full/almost-full drives protocol backpressure | Occupancy checker |
| Cache full flush+reload | Fill ways, directory entries, MSHRs, source/sink IDs, writeback/release buffers, then flush/drain and reload | Full resources recover without stale coherence or data visibility | Full flush checker |
| Bank/set conflict | Demand, prefetch, probe, refill, writeback map to same bank/set | Conflict priority and loser behavior follow code | Conflict checker |
| CHI/TL channel contention | Req/Rsp/Dat/Snp or TL A/B/C/D/E channels all active | Channel arbiters follow code priority/fairness | Arbiter checker |
| Directory conflict | Same set/way/line concurrent read/write/probe/refill | Directory/data/meta conflict resolved per code | Storage checker |
| Replacement/writeback conflict | Victim selection with dirty line and probe | Replacement FSM preserves coherence | FSM/replacement checker |
| Prefetch contention | Prefetch and demand miss collide | Demand/prefetch priority follows code | Arbiter checker |

Shared-resource context-switch scenarios are mandatory for all XSCache drivers.

## chiselAIA / APLIC / IMSIC Drivers

Architecture Verification:

| Scenario | Stimulus | Expected architectural observation | Checkers |
| --- | --- | --- | --- |
| Interrupt delivery | MSI, external, software, timer, local interrupt | Pending/enable/priority/delegation behavior matches spec | Interrupt checker |
| Privilege/virtualization routing | M/S/VS/VU interrupt targets and delegation | Correct privilege and virtual interrupt delivery | AIA/privilege checker |
| CSR/MMIO legality | APLIC/IMSIC/CSR accesses from modes | Legal accesses succeed; illegal accesses fault | CSR/MMIO checker |

Microarchitecture Scenario Verification:

| Scenario | Stimulus | Expected microarchitectural observation | Checkers |
| --- | --- | --- | --- |
| Interrupt queue/file full | Burst MSI/external events | Pending bits/queues handle full/almost-full or drop policy as code defines | Occupancy checker |
| Arbitration conflict | Multiple interrupts same priority/cycle | Priority encoder selects correct interrupt | Arbiter checker |
| Context switch | VM/privilege/domain switch with pending interrupt | Interrupt state routes to correct guest/domain | Context isolation checker |
| Bus contention | MMIO access overlaps interrupt update | Port/FSM conflict behavior matches code | Port/FSM checker |

## chiselIOPMP / IOPMP Drivers

Architecture Verification:

| Scenario | Stimulus | Expected architectural observation | Checkers |
| --- | --- | --- | --- |
| Permission match | Master ID, address, access type, lock bits | Allow/deny/error response matches IOPMP configuration | IOPMP checker |
| Privilege/domain switch | Reconfigure entries, switch domain/process/VM | Permission applies to correct context and no stale allow remains | Context checker |

Microarchitecture Scenario Verification:

| Scenario | Stimulus | Expected microarchitectural observation | Checkers |
| --- | --- | --- | --- |
| Entry table conflict | Config write and permission lookup same entry | Read/write priority and lock behavior match code | Storage checker |
| Request queue full | Many protected requests under response stall | Backpressure or outstanding tracking follows code | Occupancy checker |
| AXI/TL response contention | Deny/error/data responses collide | Response arbiter preserves protocol | Arbiter checker |

## AXI / AXI4 Bus Drivers

Applies to `AXI4MasterNode`, `AXI4SlaveNode`, `AXI4Bundle`, `AXI4Xbar`, `AXI4Buffer`, `AXI4ToTL`, `TLToAXI4`, and `AXI4Memory`.

Architecture Verification:

| Scenario | Stimulus | Expected architectural observation | Checkers |
| --- | --- | --- | --- |
| Memory access through bus | Load/store/AMO/MMIO over AXI path | Architectural data and fault behavior match memory model/platform rules | Bus memory scoreboard |
| Protection response | IOPMP/PMA/MMIO deny/error | Correct access fault or response handling | Protection checker |

Microarchitecture Scenario Verification:

| Scenario | Stimulus | Expected microarchitectural observation | Checkers |
| --- | --- | --- | --- |
| AW/W/B/AR/R handshake stalls | Stall each AXI channel independently | Payload stable under valid stall; fire exactly once | AXI handshake checker |
| Outstanding full/almost | Exhaust IDs, buffers, reorder entries | Ready deasserts and no ID reuse violation | Occupancy checker |
| Read/write arbitration | Concurrent reads/writes from multiple masters | Xbar/buffer grants by code rule; losers hold | Arbiter checker |
| Burst conflict | Unaligned/long burst, backpressure, last beat stall | `last`, `resp`, `id`, and beat count stay consistent | AXI protocol checker |
| Context/domain switch | Protected master changes process/VM/domain while requests outstanding | Tags/ordering prevent stale permission or response misdelivery | Context isolation checker |

## Driver Completion Checklist

Before a generated driver is considered complete:

- It has `Architecture Verification`, `Microarchitecture Scenario Verification`, and `System Verification`.
- It has `Per-Instruction Exception Verification`, `Memory Exception Priority Verification`, and `Exception Interrupt Nesting Verification` sections using `skills/architectureExceptionDrivers.md` when architecture exceptions are in scope.
- It has an `Operand Boundary Verification` section using `skills/operandBoundaryDrivers.md` when instructions, operands, addresses, CSR fields, masks, or protocol fields are in scope.
- It has a `Virtualization Protection Verification` section using `skills/virtualizationProtectionDrivers.md` when virtualization, PMP, page translation, PMA, IOPMP, privilege permissions, or MMIO/uncache protection are in scope.
- It has a `Debug Event Verification` section using `skills/debugEventDrivers.md` when debug mode, debug CSRs, trigger match, `ebreak`, single-step, halt/resume, `dret`, trap/debug redirect, or debug privilege restrictions are in scope.
- It has `Performance Monitor Counter Stress Verification` using `skills/performanceMonitorCounterDrivers.md` when performance counters, event selectors, inhibit/control CSRs, overflow/pending state, event buses, commit counters, privilege/virtualization filters, or performance-monitor interrupts are in scope.
- It has a `System Verification` section using `skills/systemVirtualizationPermissionDrivers.md` when privilege, virtualization, system calls, page-table permissions, multi-core synchronization, asynchronous events, guest/host trap interaction, or trap-handler save/handle/restore phases are in scope.
- It has a `Conflict Scenario Verification` section using applicable rows from `skills/conflictScenarioDrivers.md`.
- It has an `FSM Scenario Verification` section using applicable rows from `skills/fsmScenarioDrivers.md` when the module contains explicit or implicit state machines.
- It has a `Forward Progress Verification` section using `skills/forwardProgressDrivers.md` for deadlock, livelock, starvation, mux/arbiter fairness, and FSM progress scenarios.
- It has `Index Boundary Verification`, `Bus Protocol Verification`, and `Hash Conflict Verification` sections when the module contains indexes, bus interfaces, or hash expressions, using `skills/indexBusHashDrivers.md`.
- It has `Cache Structure Verification` using `skills/cacheStructureDrivers.md` when the module contains any cache-like structure, including ICache, DCache, L1/L2/LLC, directory, tag/data/meta array, MSHR, miss/replay/refill queue, writeback buffer, prefetch table, or cache-maintenance path.
- Every queue-like structure has empty, almost empty, full, almost full, simultaneous enqueue/dequeue, and flush/replay/redirect coverage.
- Every Decoupled/Valid or bus channel has valid-only stall, ready-only idle, fire, payload-stability, and response-backpressure coverage.
- Every FSM has reset, first request, each transition, deadlock wait-state exit, livelock retry-cycle exit, low-priority transition starvation, cancel/redirect/replay/exception, and illegal transition checks.
- Every FSM state has entry, hold, exit, output checks, and a transition matrix with same-cycle competing-trigger priority tests.
- FSM tests construct aligned sequences so different trigger sequences request different next states at the same cycle, then check winner priority and loser behavior.
- Every mux and arbiter has all-requesters-valid, each requester alone, older low-priority request versus newer high-priority request, persistent high-priority starvation, pointer/age wrap, winner rule, loser behavior, and starvation/fairness checks.
- Redirect, replay, port contention, resource contention, deadlock, livelock, starvation, and same-index/bank/entry conflict are covered when reachable.
- Conflict scenarios state trigger timing, winner rule, loser behavior, affected state, progress rule, fairness assumption, and checker.
- All computed indexes include min, max, max-1, wrap, reserved/invalid, and simultaneous-update boundary tests.
- All bus interfaces include protocol tests for channel backpressure, payload stability, fire-only state update, outstanding IDs, burst beats, `last`, masks/strobes, and response/error propagation.
- All hash-derived indexes include generated same-index/different-tag and same-hash/different-context conflict inputs; where possible, include a script under `scripts/gen_<module>_<hash_name>_conflicts.py`.
- Cache-like modules include hit, miss, replacement, bank conflict, set conflict, cache full, and flush+reload coverage for every cache structure when reachable.
- Memory/cache/MMU modules include load-store, LR/SC, AMO, TLB/PTW, MSHR, refill/probe, replacement, and ordering conflicts when reachable.
- Frontend/predictor modules include lookup/update, history recovery, RAS, FTQ, IBuffer, and ICache refill/invalidate conflicts when reachable.
- Bus/protection/interrupt modules include AXI channel, crossbar, AIA pending/priority, IOPMP lookup/config, and deny/error response conflicts when reachable.
- Shared resources include privilege switch, process switch, VM switch, and supervisor-domain switch scenarios.
- Architecture claims are verified through `riscv-spec`/UDB or explicitly marked as outside UDB coverage.
- Architecture-side instruction drivers enumerate every reachable exception for each implemented instruction/class.
- Operand boundary drivers traverse integer, floating-point, address, vector/mask, CSR/control, and bus protocol field boundaries for every involved operand.
- Boundary operand tests are combined with exception, interrupt, conflict, FSM, bus, index/hash, and context-switch scenarios when reachable.
- Memory instruction drivers include same-instruction multi-exception priority tests and interrupt-pending variants.
- Virtualization/protection drivers cover page, guest page, two-stage translation, PMP, PMA, IOPMP, MMIO/uncache, context switches, and combined fault priorities.
- System verification drivers cover system calls/trap ABI paths, read/write/execute permission traversal, leaf/non-leaf PTEs, virtualization, guest/host cross faults/traps/interrupts, multi-core synchronization, asynchronous events, and save/handle/restore trap phases.
- Exception tests include nested interrupt/trap scenarios across trap entry, handler execution, trap return, delegation, debug, and virtualization when implemented.
- Debug event tests enumerate code-derived producers/consumers, priority arbitration, CSR updates, privilege/debug-mode legality, and precise side-effect checks from XiangShan analyzer evidence.
- Performance monitor counter tests enumerate every implemented counter, event producer, selector, inhibit/control bit, overflow bit, privilege/virtualization filter, and update path, then stress CSR races, max concurrent events, flush/replay/trap/debug/context-switch windows, queue full/empty states, and overflow boundaries.
- Forward-progress tests cover deadlock, livelock, starvation, old-low-priority versus new-high-priority mux/arbiter cases, persistent high-priority traffic, pointer/age wrap, and FSM starvation/livelock scenarios for every reachable structure.
- Microarchitecture claims cite effective Chisel source lines from the analyzer output before implementation.

