# ICache Scenario Extraction

## Scope
- Mechanism: ICache / instruction fetch cache path.
- Interpreted meaning: the frontend instruction-cache delivery path, covering `IFU -> ICache -> ITLB/PMP/PBMT/MMIO/uncache -> WayLookup -> MainPipe/MissUnit/Prefetch -> FTQ/IBuffer`, plus `fence.i`, flush, redirect, context switch, ECC injection, replacement, MSHR pressure, and queue backpressure scenarios.
- XiangShan source revision: evidence-needed. This document is generated from the user-provided `UT_ICACHE_kmh0718.md` summary, scenario list, and local verification-driver rules. Chisel line-level evidence still needs to be added from the effective source tree.
- Primary modules/paths:
  - `xiangshan/frontend/icache/ICache.scala`
  - `xiangshan/frontend/icache/ICacheMainPipe.scala`
  - `xiangshan/frontend/icache/ICacheMissUnit.scala`
  - `xiangshan/frontend/icache/ICacheCtrlUnit.scala`
  - `xiangshan/frontend/icache/ICacheBundle.scala`
  - `xiangshan/frontend/icache/IPrefetch.scala`
  - `xiangshan/frontend/icache/InstrUncache.scala`
  - `xiangshan/frontend/icache/WayLookup.scala`
  - `xiangshan/frontend/Frontend.scala`
  - `xiangshan/frontend/IFU.scala`
  - `xiangshan/frontend/ftq/Ftq.scala`
  - `xiangshan/cache/mmu/TLB.scala`
- Analyzer references used:
  - `tools/xiangshan-code-analyzer/tools/analyze-xiangshan-kunminghu/references/frontend.md`
  - `tools/xiangshan-code-analyzer/tools/analyze-xiangshan-kunminghu/references/verification-special-attention.md`
- Verification-driver rules used:
  - `tools/verification-driver/skills/xiangshanVerificationDriver.md`
  - `tools/verification-driver/skills/cacheStructureDrivers.md`
  - `tools/verification-driver/skills/conflictScenarioDrivers.md`
  - `tools/verification-driver/skills/forwardProgressDrivers.md`
  - `tools/verification-driver/skills/fsmScenarioDrivers.md`
  - `tools/verification-driver/skills/performanceBottleneckDrivers.md`
  - `tools/verification-driver/skills/indexBusHashDrivers.md`
  - `tools/verification-driver/skills/architectureExceptionDrivers.md`
  - `tools/verification-driver/skills/operandBoundaryDrivers.md`
  - `tools/verification-driver/skills/virtualizationProtectionDrivers.md`
  - `tools/verification-driver/skills/systemVirtualizationPermissionDrivers.md`

## Mechanism Model
| Aspect | Description | Source evidence |
| --- | --- | --- |
| Goal | Preserve instruction-fetch correctness, throughput, and recoverability: hits return the correct instruction bytes, misses use miss/refill, uncache/MMIO uses the uncache path, and old instructions cannot be observed after flush/redirect/`fence.i`/context switch. | Functional points from the report summary; the tool rules require ICache to use `Cache Structure Verification` and frontend conflict/progress/exception routing. |
| Inputs | IFU/fetch requests, ITLB results, PMP/PBMT/MMIO classification, WayLookup requests, prefetch requests, `fence.i`, flush, redirect, context switch, ECC injection controls, and cache-maintenance requests. | ICache/ITLB navigation in `frontend.md`; ICache routing in `xiangshanModuleDrivers.md`. |
| Internal state | Tag/meta/data arrays, WayLookup state, miss/refill MSHRs, prefetch queues, uncache queues, control FSMs, replacement state, ECC control registers, and FTQ fetch/prefetch pointers. | `cacheStructureDrivers.md` requires tag/data/meta, replacement, MSHR, and flush+reload coverage. |
| Algorithm/control rule | Translate and classify the request first, then route it to cacheable hit/miss, uncache, prefetch, or flush/reload handling. Same-set, same-bank, and same-way conflicts must follow code-defined arbitration. Full resources must backpressure or replay and recover after drain. | ICache/ITLB guidance in `frontend.md`; conflict and progress rules in `conflictScenarioDrivers.md` and `forwardProgressDrivers.md`. |
| Outputs | Instruction bytes, fetch blocks, miss/replay information, exception/page/access fault information, uncache responses, prefetch hit/occupancy information, performance counters, and FTQ/IFU/IBuffer-visible state. | Fetch flow in `frontend.md`; ICache routing in `verification-special-attention.md`. |
| Observability | Hit/miss/refill, MSHR full, bank/set conflicts, flush/reload, context isolation, exception priority, performance counters, and waveform handshakes. | Required ICache items in `verification-special-attention.md`; hit/miss/replace/full/flush+reload items in `cacheStructureDrivers.md`. |

## Scenario Taxonomy
| Family | Why it matters | Applicable driver files |
| --- | --- | --- |
| Baseline fetch | Establish nominal fetch, hit, sequential address, and two-line return behavior. | `xiangshanVerificationDriver.md`, `cacheStructureDrivers.md` |
| Conflict / arbitration | Cover fetch/refill/invalidate conflicts, ITLB/ICache contention, bank/set/way conflicts, and prefetch priority conflicts. | `conflictScenarioDrivers.md`, `fsmScenarioDrivers.md` |
| Forward progress | Cover miss, MSHR full, queue full, backpressure, retry, and recovery behavior. | `forwardProgressDrivers.md`, `performanceBottleneckDrivers.md` |
| Recovery / flush | Cover `fence.i`, flush, BPU redirect, context switch, and invalidate-then-refetch behavior. | `xiangshanVerificationDriver.md`, `forwardProgressDrivers.md` |
| Exception / protection | Cover ITLB page/access/global page faults, PMP faults, MMIO/uncache routing, and permission isolation. | `architectureExceptionDrivers.md`, `virtualizationProtectionDrivers.md`, `systemVirtualizationPermissionDrivers.md` |
| Prefetch | Cover software prefetch, hardware prefetch, priority arbitration, MSHR competition, and prefetch coverage closure. | `performanceBottleneckDrivers.md`, `conflictScenarioDrivers.md` |
| ECC / error injection | Cover meta/data ECC, way masks, ready interference, and error recovery. | `cacheStructureDrivers.md`, `conflictScenarioDrivers.md` |
| Boundary / address coverage | Cover line, bank, page, boundary, cross-line, cross-bank, high-address-bit, and index/hash cases. | `operandBoundaryDrivers.md`, `indexBusHashDrivers.md` |

## Detailed Scenarios
| ID | Scenario | Initial state | Stimulus sequence | Concurrent pressure | Expected observation | Failure signature | Checkers / coverage | Source evidence |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `BS-001` | Single-cacheline fetch with address sweep, including boundaries | Hit and miss states for the target line are both covered; ITLB hits; PMP allows access. | Step the PC continuously across line-internal and line-boundary addresses. | No extra conflict. | Hits return correct instruction bytes; boundary accesses preserve correct slicing or split-fetch behavior. | Wrong instruction bytes, dropped boundary bytes, or duplicated bytes. | Hit/data checker; boundary coverage. | User report: `tc_icache_basic` |
| `BS-002` | Single-cacheline fetch with random non-contiguous addresses | Some lines are resident and others are not; accesses are non-sequential. | Randomly access multiple single-line addresses. | Light fetch pressure. | Hit/miss behavior, index selection, replacement state, and WayLookup updates are consistent. | Replacement/LRU state corruption or false hit. | Storage conflict checker; index coverage. | `tc_icache_single_reorder` |
| `BS-003` | Two-cacheline fetch with sequential addresses | Two adjacent lines are accessible. | Use sequential PCs that require two-line fetch handling. | Concurrent IFU/FTQ fetch flow. | Two lines are merged or split according to code-defined fetch-block semantics. | Wrong merge, wrong order, or cross-line corruption. | Cache structure checker; flush/reload checker. | `tc_icache_2chn_basic` |
| `BS-004` | Two-cacheline fetch with random non-contiguous addresses | Two-line fetch points are randomized. | Random two-line accesses. | Bank/set contention. | Multi-channel arbitration is correct and winner/loser behavior is predictable. | Lost request or wrong data under dual-channel conflict. | Arbiter checker; bank conflict coverage. | `tc_icache_2chn_reorder` |
| `BS-005` | Two-cacheline fetch with reversed address order | Forward and reverse orders are both reachable. | Access the two lines in reverse order. | WayLookup/FTQ concurrency. | Address reversal and channel switching preserve correct line selection and response order. | Wrong concatenation or wrong line chosen under reverse order. | Index/boundary checker. | `tc_icache_2chn_reverse` |
| `BS-006` | Single-cacheline fetch with `io_respStall` asserted | The response path can be backpressured. | Hold the response request valid while the consumer is not ready. | Downstream stall. | Handshake retries legally and payload remains stable. | Payload loss, double accept, or payload mutation during stall. | Handshake checker; progress checker. | `tc_icache_basic_0` |
| `ERR-001` | ITLB returns `af` for instruction access | ITLB can respond with an access fault. | Generate an address that produces an instruction access fault. | None. | Fetch terminates and reports the exception. | Exception not reported or fetch continues as a normal cache access. | Exception scoreboard; page/access fault coverage. | `tc_icache_itlb_af_instr` |
| `ERR-002` | ITLB returns `pf` for instruction access | Page-walk fault path is reachable. | Trigger an instruction page fault. | None. | Page fault is reported with the correct cause. | Fault type mismatch. | Exception checker. | `tc_icache_itlb_pf_instr` |
| `ERR-003` | ITLB returns `gpf` for instruction access | Global page fault path is reachable. | Trigger a global page fault. | None. | Global page fault is reported as the visible exception. | Misreported as page fault or access fault. | Exception checker. | `tc_icache_itlb_gpf_instr` |
| `ERR-004` | ITLB randomly returns af/pf/PBMT combinations | Multiple exception candidates can overlap. | Randomize ITLB exception and PBMT classification. | Multiple fault candidates. | Exception type and priority are selected correctly. | Multi-exception priority mismatch. | Exception priority coverage. | `tc_icache_itlb_instr_rand` |
| `ERR-005` | PMP returns instruction access violation | PMP can deny fetch. | Trigger an instruction access violation. | Hit/miss cases run in parallel subcases. | Access violation is reported and fetch is terminated. | PMP violation treated as miss or replay. | Protection checker. | `tc_icache_pmp_instr` |
| `ERR-006` | PMP returns MMIO classification | Instruction address is classified as MMIO. | Fetch from an MMIO instruction region. | Cacheable requests are also present in subcases. | Request uses the uncache/MMIO path and does not enter normal cache data handling. | MMIO fetch is cached as a normal line. | MMIO/uncache checker. | `tc_icache_pmp_mmio` |
| `ERR-007` | Random ITLB + PMP exception combinations | Translation and protection faults may both be candidates. | Randomize combined ITLB/PMP faults. | Combined exception pressure. | Exception priority and reported fields are correct. | Combined exception ordering mismatch. | Exception/protection coverage. | `tc_icache_itlb_pmp_instr_rand` |
| `COH-001` | `fence.i` trigger | Old instruction lines are resident in ICache. | Issue `fence.i`, then refetch the same address. | Redirect/FTQ update subcases. | Old instructions are not architecturally visible after the fence. | Old instruction still hits after `fence.i`. | Fence.i checker; flush+reload coverage. | `tc_icache_fencei` |
| `COH-002` | Explicit flush request | Some or all cache lines are valid. | Issue an ICache flush request. | Miss/refill overlap. | Full or partial cache invalidation completes; later access reloads legally. | Old line still hits after flush. | Flush/reload checker. | `tc_icache_flush` |
| `COH-003` | BPU redirect flush at s2/s3 | Prediction/fetch path contains wrong-path work. | Trigger BPU redirect flush. | Fetch/WayLookup overlap. | Wrong-path prediction/fetch state is flushed and remains invisible. | Wrong-path line is consumed by IFU/IBuffer. | Redirect checker; FTQ recovery. | `tc_icache_bpuflush` / `tc_icache_bpuflush_s1` |
| `PRE-001` | Continuous software `prefetch.i` requests | Software prefetch entry path is available. | Issue consecutive `prefetch.i` requests. | Normal fetch in parallel. | Prefetch requests enqueue legally and update hit/occupancy state. | Prefetch request dropped or incorrectly merged. | Prefetch checker; occupancy coverage. | `tc_icache_softprefetch` |
| `PRE-002` | Software and hardware prefetch arrive together with different addresses | Software and hardware prefetch can both be valid in the same cycle. | Assert both prefetch sources with different addresses. | Arbitration contention. | Priority arbitration is correct and the winning request enters the downstream path. | Lower-priority request is silently dropped or both requests fire illegally. | Arbiter checker; priority coverage. | `tc_icache_softprefetch_priority_select` |
| `PRE-003` | Software/hardware prefetch arbitration under two-channel fetch | Both fetch channels are valid. | Assert dual-channel prefetch pressure. | Multi-channel competition. | Per-channel priority and shared-resource arbitration are correct. | Channel interference or wrong grant. | Multi-port arbiter checker. | `tc_icache_2chn_softprefetch_priority_select` |
| `PRE-004` | Basic hardware prefetch FDIP flow | Hardware prefetch is enabled. | Drive a stable address stream that can train/trigger hardware prefetch. | Miss/refill overlap. | FDIP trigger, update, and retirement behavior match expectations. | Prefetch does not trigger or incorrectly overrides demand fetch. | Prefetch coverage-hit checker. | `tc_icache_prefetch_coverage_hit` |
| `PRE-005` | Full prefetch MSHR scenario coverage for fetch/MSHR3 | MSHR occupancy and release vary over time. | Run long random prefetch and demand-fetch traffic. | MSHR pressure. | Allocation, release, wait, and full-pressure cases are covered. | Missing index coverage or deadlock after full pressure. | MSHR occupancy checker. | `tc_icache_fetchmshr3` / `tc_icache_fetchmshr3_rand` |
| `ECC-001` | MetaArray single-bit ECC injection on one line | Meta ECC injection is available. | Inject a single-bit ECC error on one line, then refetch. | Hit/replacement overlap. | Error is detected and recovery/refetch completes. | Error is not detected or recovery fails. | ECC checker; refetch coverage. | `tc_icache_meta_ecc` |
| `ECC-002` | All `eccctrl` configuration combinations | ECC control register space can be swept. | Iterate all injection-control configurations. | None. | All control combinations are covered. | One or more control configurations remain uncovered. | ECC control coverage. | `tc_icache_meta_ecc_all_situations` |
| `ECC-003` | ECC injection with random `ctrlopt_ready` | Control-unit ready can toggle. | Inject ECC while randomly toggling ready. | Handshake pressure. | Recovery remains correct under ready interference. | Ready interference causes lost state or incomplete recovery. | Handshake/ECC checker. | `tc_icache_ecc_ready_random` |
| `ECC-004` | ECC injection coverage for all 4 ways | Each way can be targeted by injection. | Inject errors into all four ways one by one. | Set/way conflicts. | All way-specific recovery paths are covered. | Any way remains uncovered or unrecoverable. | Way coverage checker. | `tc_icache_ecc_waymask_toggle_coverage` |
| `ECC-005` | DataArray ECC error injection | Data-array ECC injection is reachable. | Inject data ECC and trigger a readback. | Hit/miss overlap. | Data error is detected and recovered. | Data error passes silently. | Data ECC checker. | Partially covered by existing tests. |
| `ECC-006` | Cross-line MetaArray ECC error injection | Two-line/cross-line path is reachable. | Inject meta ECC while crossing lines. | Two-line access pressure. | Cross-line ECC error is covered and recoverable. | Cross-line error is missed. | Cross-line ECC checker. | New test needed. |
| `REP-001` | Basic configurable replacement flow | Replacement logic is active. | Trigger replacement and observe the victim. | Miss/refill overlap. | Pseudo-LRU/random replacement path is consistent with the configured policy. | Wrong victim selected. | Replacement checker. | Implicit in baseline/replacement tests. |
| `REP-002` | Allocate a new line when waymask is zero | All-miss allocation path is reachable. | Trigger an all-miss condition and observe allocation. | Refill contention. | New-line allocation follows code-defined rules. | Allocation fails under all-miss state. | Allocation checker. | Implicit in baseline/replacement tests. |
| `ADV-001` | Backend redirect plus random exceptions | Fetch and backend feedback overlap. | Mix backend redirect with fetch-side exceptions. | Heavy flush pressure. | Wrong-path work remains invisible when redirect and exception interleave. | Redirect/exception priority mismatch. | Redirect/exception checker. | `tc_icache_ftq_backend_instr_rand` |
| `ADV-002` | Cross-bank address interval coverage for min/max bank | Bank boundaries can be generated. | Sweep minimum and maximum bank addresses and nearby ranges. | Concurrent fetch pressure. | Bank index coverage is complete. | Wrong bank selection or boundary wrap error. | Index/bank coverage. | `tc_icache_itlb_instr_rand_new` |
| `ADV-003` | High-address coverage for `3_ffff_ffff_ffbe` | High-address bits are reachable. | Access a high address and continue fetching. | Mixed normal/high-address traffic. | High address bits are preserved through translation/indexing. | High address is truncated or misindexed. | Operand boundary checker. | `tc_icache_2chn_corrupt` |
| `ADV-004` | CtrlUnitOpt toggle coverage closure | Control-unit options can toggle. | Sweep CtrlUnitOpt-related control combinations. | Fetch contention. | Control-unit signal coverage is closed. | Control option remains uncovered. | CtrlUnit toggle coverage. | `tc_icache_ctrlunit_toggle_coverage` |

## Directed Scenario Descriptions

### `BS-001` - Single-Cacheline Fetch Address Sweep
- Intent: prove that normal cacheable fetch remains correct inside a cache line and at cacheline boundaries.
- Code-derived trigger: `ITLB hit + PMP allow + cacheable path`.
- Preconditions: the target line is resident or can miss/refill; no redirect or flush interference.
- Cycle-level stimulus: increment the PC continuously across line-internal addresses, the line tail, and the next-line head.
- Expected state transitions: fetch enters WayLookup/MainPipe; hit returns directly; miss uses the miss/refill path.
- Expected outputs: correct instruction bytes, correct fetch block, and correct line-boundary slicing.
- Negative checks: no duplication, no dropped bytes, and no cross-line mismerge.
- Metrics: fetch hit rate, reload-after-refill, boundary hit rate.
- Coverage bins: line-internal, line tail, line head, cross-line.
- Debug/waveform signals: fetch req/resp, WayLookup index, hit/miss, FTQ block PC.
- Source evidence: user report `tc_icache_basic`; source line evidence is still needed.
- Evidence gaps: line-numbered index and handshake rules in `ICacheMainPipe` and `WayLookup`.

### `ERR-001` - ITLB Access Fault
- Intent: prove that a translated instruction access fault terminates fetch with the correct exception.
- Code-derived trigger: ITLB returns `af`.
- Preconditions: the address is translatable but fetch access is illegal.
- Cycle-level stimulus: generate an instruction-fetch address that violates access permission.
- Expected state transitions: ICache must not continue this request as an ordinary cache miss.
- Expected outputs: access fault enters the frontend/backend exception path.
- Negative checks: no false miss classification and no refill allocation.
- Metrics: access fault count.
- Coverage bins: cacheable/uncacheable, hit/miss, single-line/two-line.
- Debug/waveform signals: ITLB response cause, ICache kill/exception, FTQ flush.
- Source evidence: user report `tc_icache_itlb_af_instr`.
- Evidence gaps: line-level source for ITLB/PMP merge priority.

### `COH-001` - Refetch After `fence.i`
- Intent: prove that software-visible instruction modification is not polluted by stale ICache lines.
- Code-derived trigger: `fence.i`.
- Preconditions: the old instruction has already been hit or prefetched into ICache.
- Cycle-level stimulus: hit the old instruction, issue `fence.i`, then refetch the same address.
- Expected state transitions: old line is flushed or invalidated; later access must be re-evaluated.
- Expected outputs: reload returns the new instruction bytes or legally misses/refills first.
- Negative checks: stale data must not hit after `fence.i`.
- Metrics: `fence.i` flush latency, reload hit rate.
- Coverage bins: hit before `fence.i`, miss during `fence.i`, reload after `fence.i`.
- Debug/waveform signals: flush valid, set/way invalidation, FTQ recovery.
- Source evidence: user report `tc_icache_fencei`.
- Evidence gaps: exact flush effect on tag/meta/data arrays.

### `PRE-002` - Same-Cycle Software/Hardware Prefetch Competition
- Intent: prove prefetch priority and shared-resource arbitration.
- Code-derived trigger: soft prefetch and hardware prefetch are valid in the same cycle with different addresses.
- Preconditions: prefetch path is reachable and at least one MSHR or queue slot is available.
- Cycle-level stimulus: assert both prefetch sources in the same cycle.
- Expected state transitions: only one winner occupies the shared resource; the loser remains valid or enters retry/wait.
- Expected outputs: prefetch behavior matches the priority rule.
- Negative checks: no illegal dual fire, no wrong merge, and no dropped request.
- Metrics: prefetch accept rate, priority bin.
- Coverage bins: same address, different address, MSHR/full, ready low.
- Debug/waveform signals: prefetch valid/ready, winner select, MSHR allocation.
- Source evidence: user report `tc_icache_softprefetch_priority_select`.
- Evidence gaps: source evidence for the real soft/hard prefetch priority.

### `ECC-001` - MetaArray Single-Bit ECC Injection
- Intent: prove meta ECC detection and recovery.
- Code-derived trigger: ECC injection is enabled for a resident line.
- Preconditions: the target line is present in the meta array.
- Cycle-level stimulus: inject ECC, immediately access the line, and observe recovery.
- Expected state transitions: the error is detected and refetch/rebuild is triggered when required.
- Expected outputs: corrupted metadata does not pass silently.
- Negative checks: bad metadata must not be treated as a clean hit.
- Metrics: ECC detect/recover count.
- Coverage bins: way 0-3, single-line/two-line, ready toggling.
- Debug/waveform signals: `eccctrl`, meta read/write, refetch.
- Source evidence: user report `tc_icache_meta_ecc`.
- Evidence gaps: ECC control-register fields and FSM source line numbers.

## Checker Plan
| Checker | Type | Watches | Pass condition | Failure message |
| --- | --- | --- | --- | --- |
| `icache_handshake_checker` | Handshake | req/ready/valid/resp/stall | Payload remains stable when ready is low, and fire happens exactly once. | `ICache handshake violated` |
| `icache_cache_hit_miss_checker` | Cache structure | tag/meta/data, hit/miss, reload | Hit/miss/refill/reload behavior matches code-defined cache semantics. | `ICache hit/miss mismatch` |
| `icache_bank_set_conflict_checker` | Conflict | same-bank, same-set, and same-way requests | Winner/loser behavior matches arbitration priority. | `ICache conflict priority wrong` |
| `icache_flush_reload_checker` | Flush/recovery | `fence.i`, flush, redirect, context switch | Old lines cannot be observed after the required flush/reload boundary. | `ICache stale line survived flush` |
| `icache_mshr_occupancy_checker` | Occupancy | MSHR/queue full and almost-full state | Full pressure backpressures correctly and recovers after release. | `ICache MSHR full progress failed` |
| `icache_exception_scoreboard` | Exception | af/pf/gpf/instruction fault/MMIO | Exception type and priority are correct. | `ICache exception classification wrong` |
| `icache_prefetch_checker` | Prefetch | soft/hard prefetch, priority, merge | Prefetch hit/occupancy behavior matches design intent. | `ICache prefetch path mismatch` |
| `icache_ecc_checker` | ECC | meta/data ECC, way mask, ready | Detection and recovery paths remain stable. | `ICache ECC recovery failed` |

## Coverage Plan
| Coverpoint | Bins | Crosses | Source rationale |
| --- | --- | --- | --- |
| `fetch_kind` | hit, miss, reload, uncache, MMIO, exception | context, flush, prefetch | Covers the main ICache routing paths. |
| `line_boundary` | within-line, tail, head, cross-line | fetch width, bank index | Covers sequential and two-line fetch behavior. |
| `conflict_class` | bank, set, way, refill/fetch, invalidate/fetch | ready low, redirect, flush | Required by `C_ICACHE_FETCH_REFILL_INVALIDATE`. |
| `mshr_state` | empty, partial, full, merge, reject | demand/prefetch | Cache-structure and forward-progress coverage. |
| `flush_event` | `fence.i`, flush, redirect, backend redirect, context switch | hit/miss, outstanding miss | Flush+reload requirement. |
| `exception_kind` | af, pf, gpf, instruction fault, MMIO, access fault | hit/miss, cacheable/uncacheable | Exception/protection routing. |
| `ecc_mode` | meta, data, one-way, four-way, ready random | hit/reload, conflict | ECC injection and recovery. |
| `address_boundary` | min bank, max bank, high-bit, cross-page evidence-needed | line/bank/set | Address boundary coverage. |

## Verification Special Attention
| Verification ID | Risk / invariant | Directed stimulus | Expected observation | Required checker / coverage |
| --- | --- | --- | --- | --- |
| `F_RESET_IDLE` | After reset, ICache must start clean, with no stale valid state, old miss, or old prefetch state. | Release reset and issue the first legal fetch. | The first request follows the initialized code-defined state. | `icache_handshake_checker` / reset coverage |
| `F_FIRST_REQUEST` | The first request must not consume stale payload or a wrong line. | Issue a cacheable fetch immediately after reset release. | The first fire occurs once and the response matches the current PC. | `icache_cache_hit_miss_checker` |
| `F_HOLD_BACKPRESSURE` | Payload must remain stable when ready is low. | Apply backpressure to fetch, refill, and prefetch paths. | Valid is held, payload is stable, and no double accept occurs. | `icache_handshake_checker` |
| `F_REQ_AND_FLUSH` | Request accept competing with flush/redirect must follow code-defined kill priority. | Trigger `fence.i`, flush, or redirect during fetch. | Killed requests do not become visible. | `icache_flush_reload_checker` / `C_REDIRECT_REDIRECT` |
| `F_RESP_AND_REPLAY` | Completion competing with replay/retry must not update state twice. | Apply retry to miss/refill/uncache paths. | Exactly one legal state update occurs. | `icache_mshr_occupancy_checker` / replay coverage |
| `C_SAME_ENTRY_RW` | Same-index/way read-write conflict must follow code-defined behavior. | Make fetch and refill/invalidate hit the same set/way. | Read-old, read-new, bypass, or wait behavior matches code. | `icache_cache_hit_miss_checker` / `C_ICACHE_FETCH_REFILL_INVALIDATE` |
| `C_MULTI_WRITE_SAME_ENTRY` | Multiple writes to one entry must not overwrite state in the wrong order. | Trigger refill, invalidate, and ECC write in the same cycle. | Priority and write mask behavior match code. | `icache_cache_hit_miss_checker` |
| `C_BANK_CONFLICT` | Same-bank requests must have explicit winner/loser behavior. | Point two fetch/refill requests at the same bank. | Loser stalls, retries, or replays legally. | `icache_bank_set_conflict_checker` |
| `RESOURCE_CONTENTION` | Full MSHRs/queues must not drop live requests. | Fill miss/refill/prefetch resources and keep issuing requests. | Backpressure is correct and recovers after release. | `icache_mshr_occupancy_checker` |
| `I_WRAP_PTR` | Pointer wrap must not corrupt full/empty semantics. | Drive queue, FTQ, and prefetch pointers for a long run. | Age and occupancy remain correct after wrap. | occupancy + pointer coverage |
| `H_SAME_INDEX_DIFF_TAG` | Same-index/different-tag accesses must miss or replace correctly. | Construct same-set/different-tag address pairs. | Hit/miss/replacement behavior remains legal. | `icache_cache_hit_miss_checker` / `indexBusHash` |
| `H_SAME_INDEX_SAME_TAG_DIFF_CONTEXT` | Different contexts must not observe stale lines. | Switch ASID, VMID, privilege, or domain, then revisit the same address. | Old line cannot hit illegally in the new context. | `icache_flush_reload_checker` / context isolation |
| `P_DEADLOCK_ALL_STALL` | The system must not deadlock permanently when all sinks stall. | Fill miss/MSHR/queue resources and hold ready low. | After one sink is released, legal progress resumes. | `icache_mshr_occupancy_checker` / progress |
| `P_LIVELOCK_REPLAY_LOOP` | Repeated replay must eventually stop or become a legal exception. | Sustain bank/miss/redirect/retry replay causes. | Under fairness, the old request completes or reaches a legal fault. | replay/progress coverage |
| `P_STARVE_OLD_LOW_NEW_HIGH` | An old low-priority request must not be starved forever unless code documents that policy. | Hold old low-priority fetch/prefetch/refill work while injecting newer high-priority traffic. | Old request is eventually served, or intentional starvation is proven by code. | arbiter/fairness coverage |
| `PB_BURST_ABSORB_DRAIN` | Burst fill must be recoverable. | Burst fetch/miss/prefetch traffic for a short interval. | Resources fill and then drain without loss. | occupancy coverage |
| `PB_BACKPRESSURE_AMPLIFICATION` | Blocking one sink must not corrupt state. | Hold ready low on one sink for a long interval. | Backpressure propagates upstream and payloads remain stable. | handshake coverage |
| `PB_RECOVERY_THROUGHPUT` | Throughput should recover after flush/reload. | Repeat flush/reload, then resume sequential fetch. | Throughput returns to the code-allowed steady-state bound. | performance coverage |

## Risks And Recommendations
- Timing verification remains the main risk. The report notes that the reference model is not pipeline-timed, so multi-cycle backpressure, redirect, replay, and miss/refill overlap need stronger coverage.
- If FSM coverage is low, prioritize state-transition closure for `ICacheCtrlUnit`, `ICacheMissUnit`, `InstrUncache`, and `WayLookup`.
- `waymask`, replacement, and full MSHR coverage should use directed tests rather than relying only on random regression.
- `io_metaArrayFlush_1_valid`, same-address waits for `fetchmshr_2/3`, and full-index coverage for `prefetchMSHRs` should remain explicit coverage-closure items.
- If `dataArray mbist` is tested only at the top level, keep the local ICache item waived rather than duplicating it in the ICache unit test plan.

## Delivery Recommendation
- This version is suitable as the ICache scenario catalog and regression-entry document.
- The next version should add source line evidence, exception-priority ordering, FSM tables for `ICacheMainPipe` and `ICacheMissUnit`, and detailed `WayLookup` bank/set/way conflict rules.

## Evidence Gaps
| Gap | Next file/search/action |
| --- | --- |
| Effective ICache Chisel line numbers | Read `xiangshan/frontend/icache/*.scala` and add line evidence section by section. |
| ITLB/PMP/PBMT/MMIO path priority | Trace `cache/mmu/TLB.scala` and frontend ICache interfaces. |
| Replacement / MSHR / refill FSM | Expand `ICacheMainPipe.scala`, `ICacheMissUnit.scala`, and `ICacheCtrlUnit.scala`. |
| Prefetch priority and merge rules | Trace `IPrefetch.scala` and the request launch points in `Ftq.scala`. |
| `fence.i` / flush / redirect details | Trace `Frontend.scala`, `Ftq.scala`, and `ICacheCtrlUnit.scala`. |
| ECC control and error recovery | Trace `ICache.scala`, `ICacheDataBank.scala`, and the ICache meta-array definitions. |
