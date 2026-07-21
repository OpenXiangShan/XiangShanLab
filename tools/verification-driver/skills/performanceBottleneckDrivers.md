# XiangShan Performance Bottleneck Stress Drivers

Use this file when generating bottleneck-focused stress verification drivers for XiangShan modules. A performance bottleneck is any code-derived condition where throughput, latency, occupancy, fairness, bandwidth, queueing delay, replay rate, stall propagation, or resource utilization can degrade under legal traffic even when architectural behavior is correct.

Every selected scenario must cite effective XiangShan Chisel evidence from `tools/xiangshan-code-analyzer/skills/analyze-xiangshan-kunminghu`: request sources, ready/valid gates, queue depths, arbitration policy, resource counts, credit rules, retry/replay conditions, flush behavior, counter/event signals, and completion points. Do not infer a bottleneck from a module name.

## Performance Bottleneck Driver Shape

```markdown
## Performance Bottleneck Stress Verification
| Bottleneck ID | Scope | Code evidence needed | Stress stimulus | Expected performance property | Failure signature | Checkers / metrics |
| --- | --- | --- | --- | --- | --- | --- |
```

For every selected bottleneck, include:

- Bottleneck boundary: request acceptance, issue/grant, response return, queue drain, commit, refill, replay completion, redirect recovery, or CSR/event update.
- Saturation point: queue depth, number of ports, number of banks, MSHR/PTW/replay entries, issue width, commit width, bus outstanding limit, credit count, or FSM service rate.
- Metric model: expected throughput, latency bound, occupancy trend, stall-cycle count, replay count, service fairness, or event-counter delta.
- Baseline and stress comparison: one low-pressure case plus at least one saturated case using the same checker so degradation is measurable.
- Failure signature: throughput collapse, unbounded latency under fair sinks, head-of-line blocking, backpressure amplification, burst loss, unfair service, duplicate replay, or occupancy that never drains.

## Global Bottleneck Drivers

| Bottleneck ID | Scenario | Stress stimulus | Expected behavior | Checkers / metrics |
| --- | --- | --- | --- | --- |
| `PB_BASELINE_SINGLE_STREAM` | Baseline service rate | Drive one legal requester or one legal stream with no competing pressure until steady state | Latency and throughput match the code-derived nominal path; this becomes the reference for later stress rows | Latency histogram, throughput scoreboard |
| `PB_MAX_SUSTAINED_THROUGHPUT` | Maximum legal sustained traffic | Drive all legal producers every cycle for a long window while downstream remains fair | Accepted/completed work reaches the code-derived width or documented lower bound without illegal drops | Throughput checker, completion counter |
| `PB_BURST_ABSORB_DRAIN` | Burst absorption and drain | Send bursts of length 1, queue depth, depth+1, 2x depth, and long random bursts, then stop producers | Queues absorb up to capacity, apply backpressure when full, and drain to empty within the modeled service rate | Occupancy checker, drain-time checker |
| `PB_BACKPRESSURE_AMPLIFICATION` | Local stall propagation | Hold one downstream sink not-ready, then release it while unrelated paths remain serviceable | Backpressure reaches only code-connected producers; unrelated paths continue or recover as code permits | Stall propagation checker |
| `PB_HEAD_OF_LINE_BLOCKING` | Head entry blocks younger work | Keep the oldest entry blocked while younger independent entries are serviceable, then unblock the head | Bypass, replay, or strict-order behavior matches code; independent work is not unnecessarily frozen unless required | HOL checker, fairness checker |
| `PB_LATENCY_TAIL_PRESSURE` | Long-tail latency under pressure | Mix short and long operations while all queues operate near full and downstream is eventually fair | Old legal operations complete within code-derived/fairness assumptions; tail latency does not grow without cause | Age/latency histogram checker |
| `PB_RECOVERY_THROUGHPUT` | Throughput after flush/replay recovery | Run at saturation, assert redirect/flush/replay/cache invalidation, then resume traffic immediately | Killed work is removed, legal survivors recover, and throughput returns to baseline after code-defined drain/refill | Recovery throughput checker |

## Resource Saturation Drivers

| Bottleneck ID | Scenario | Stress stimulus | Expected behavior | Checkers / metrics |
| --- | --- | --- | --- | --- |
| `PB_QUEUE_SATURATION` | Queue/buffer capacity pressure | Fill every queue, skid buffer, replay queue, update queue, or pipeline register to empty, almost-empty, one-live-entry, full, almost-full, and wrap states | Ready/full/almost flags and acceptance rate follow code; no lost entry, duplicate entry, or permanent full state | Occupancy, acceptance-rate checker |
| `PB_PORT_BANDWIDTH` | Port bandwidth pressure | Drive more read/write/issue/wakeup/writeback/CSR/cache/TLB/bus requests than physical ports in every legal combination | Arbiter chooses legal winners, losers hold/retry, and aggregate throughput matches available ports | Port utilization checker |
| `PB_BANK_CONFLICT_RATE` | Bank/set conflict pressure | Generate same-bank/same-set and evenly-distributed traffic groups from exact code-derived index/hash expressions | Conflict traffic shows expected stalls/replays; distributed traffic approaches higher bandwidth | Bank conflict metric checker |
| `PB_OUTSTANDING_LIMIT` | MSHR/PTW/bus/source ID exhaustion | Allocate all outstanding entries, credits, source IDs, sink IDs, or transaction IDs, then issue one more request | New work backpressures or merges legally; existing work drains after fair responses | Outstanding scoreboard |
| `PB_CREDIT_STARVATION` | Credit exhaustion and return latency | Exhaust credits for one source while other sources continue, then return credits in different orders | Credit accounting preserves fairness policy; returned credits enable the intended source without leaks | Credit checker, service latency |
| `PB_FSM_SERVICE_RATE` | FSM service bottleneck | Keep every FSM input eligible while responses alternate between fastest, slowest, retry, and fair-completion patterns | FSM exits busy/wait/retry states at the code-defined rate and does not serialize unrelated work unnecessarily | FSM rate checker |

## Pipeline and Memory-System Bottleneck Drivers

| Bottleneck ID | Scenario | Stress stimulus | Expected behavior | Checkers / metrics |
| --- | --- | --- | --- | --- |
| `PB_FRONTEND_FETCH_BANDWIDTH` | Fetch/IBuffer/FTQ pressure | Drive sequential fetch, taken branches, predicted redirects, ICache miss/refill, and IBuffer full/empty transitions | Fetch bandwidth, redirect recovery, and buffer occupancy follow code-derived limits | Fetch bandwidth checker |
| `PB_DECODE_RENAME_DISPATCH` | Decode/rename/dispatch width pressure | Feed max-width instruction groups with mixed destinations, CSR/system ops, branch, load/store, vector/FP when implemented | Width limits, free-list pressure, and dispatch backpressure match code without losing uops | Width/rename checker |
| `PB_ISSUE_WAKEUP_SELECT` | Issue select and wakeup pressure | Keep many entries ready, wake multiple operands, and contend for same FU classes and writeback paths | Select priority/fairness and wakeup timing match code; ready entries are not hidden indefinitely | Issue utilization checker |
| `PB_LSQ_STORE_BUFFER` | Load/store queue bottleneck | Fill load queue, store queue, store buffer, forwarding CAM, and violation/replay paths with mixed cache hit/miss traffic | LSU throughput, ordering stalls, replay count, and drain behavior match code | LSQ throughput checker |
| `PB_CACHE_MISS_BURST` | Cache miss and refill pressure | Generate MSHR merge hits, independent misses, same-set misses, dirty replacements, probes, and refill backpressure | MSHR allocation/merge, refill bandwidth, writeback pressure, and replay policy follow code | Miss latency checker |
| `PB_TLB_PTW_WALK_PRESSURE` | TLB/PTW translation pressure | Miss multiple TLBs, exhaust PTW walkers/entries, mix page sizes, faults, guest walks, and invalidations | PTW allocation, merge, walk ordering, and fault/retry throughput match code | Translation pressure checker |
| `PB_BUS_INTERCONNECT_BANDWIDTH` | Bus/crossbar/channel pressure | Saturate AXI/TL/NoC channels with read/write bursts, mixed IDs, response backpressure, and same-slave contention | Channel utilization, outstanding order, arbitration, and response drain follow protocol and code | Bus bandwidth checker |
| `PB_COMMIT_RETIRE_WIDTH` | Commit/retire pressure | Retire max-width groups, serialized instructions, traps, debug entry, redirects, and long-latency completions | Commit width, precise trap priority, and retire counter deltas follow code without throughput collapse | Commit throughput checker |

## Workload Mix Drivers

| Bottleneck ID | Scenario | Stress stimulus | Expected behavior | Checkers / metrics |
| --- | --- | --- | --- | --- |
| `PB_MIX_INT_FP_VEC_MEM` | Mixed execution pressure | Mix integer, branch, FP, vector, load/store, CSR, fence, and system operations according to implemented units | Shared resources are saturated according to code while independent resources continue to make progress | Per-unit utilization checker |
| `PB_MIX_SHORT_LONG_LATENCY` | Short versus long operation interference | Interleave one-cycle operations with divides, cache misses, PTW walks, atomics, vector/FP long ops, and serialized CSRs | Long ops do not starve short legal ops unless ordering/code policy requires it; short ops do not starve long ops | Latency class checker |
| `PB_MIX_SPEC_CORRECT_WRONG` | Speculation pressure | Alternate correct predictions with wrong-path bursts, redirects, replays, and recovery snapshots | Wrong-path work is killed and recovery bandwidth returns to steady state without leaking stale state | Speculation recovery checker |
| `PB_MIX_CONTEXT_SWITCH` | Context-switch throughput pressure | Saturate resources while switching privilege, ASID, VMID, domain, debug state, and interrupt/trap state | Context filtering/flush/recheck policy prevents stale work and recovers throughput after legal drain | Context performance checker |
| `PB_MIX_COUNTER_OBSERVABILITY` | Performance counter observability | Run bottleneck stress while sampling implemented performance counters and event buses | Counter deltas correlate with the bottleneck model and do not perturb the measured path unless code says so | Counter correlation checker |

## Required Cross-References

- Use `skills/forwardProgressDrivers.md` for every bottleneck that can become deadlock, livelock, starvation, head-of-line blocking, or unbounded latency.
- Use `skills/conflictScenarioDrivers.md` for port, bank, queue, same-entry, redirect, replay, memory-order, and context conflicts that cause the bottleneck.
- Use `skills/indexBusHashDrivers.md` to generate same-bank, same-set, same-index, hash-conflict, and distributed-control address groups from exact code-derived expressions.
- Use `skills/cacheStructureDrivers.md` for cache/MSHR/refill/writeback/probe bottlenecks.
- Use `skills/performanceMonitorCounterDrivers.md` when bottleneck metrics rely on `cycle`, `instret`, `hpmcounter*`, event buses, overflow, inhibit, or privilege/virtualization counter filters.
- Use `skills/fsmScenarioDrivers.md` when a bottleneck is caused by a state machine service rate, retry loop, wait state, or low-priority transition.
- Use `skills/virtualizationProtectionDrivers.md` and `skills/systemVirtualizationPermissionDrivers.md` when pressure crosses privilege, VM, ASID, VMID, domain, translation, PMP/PMA/IOPMP, interrupt, trap, or debug boundaries.

## Completion Checklist

Before a performance bottleneck driver is complete:

- Every throughput or latency claim has exact code evidence for width, queue depth, port count, arbitration, credit, retry, and completion rules.
- At least one baseline row and one saturated row exist for every bottlenecked path so the checker can distinguish expected pressure from a regression.
- Queue, port, bank, MSHR/PTW, replay, bus, issue, writeback, commit, flush/recovery, and context-switch pressure are included when reachable.
- Each stress row defines measurable metrics: accepted requests, completed requests, stall cycles, occupancy, drain time, replay count, latency distribution, fairness/service interval, or counter/event delta.
- Stress construction includes short bursts, long bursts, all-producer saturation, sparse traffic, mixed-latency traffic, distributed traffic, conflict-heavy traffic, and recovery after flush/replay.
- Performance counters are used as observations only after their own legality, precision, event select, inhibit, overflow, and privilege/virtualization filters are verified or cross-referenced.
- If the implementation intentionally serializes or starves a path, the driver must cite code evidence and check the documented behavior rather than assuming ideal throughput.
