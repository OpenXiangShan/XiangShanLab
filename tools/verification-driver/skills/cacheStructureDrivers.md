# XiangShan Cache Structure Drivers

Use this file when generating verification drivers for any cache-like XiangShan structure: ICache, DCache, L1Cache, L2, LLC, XSCache, directory, tag/data/meta array, replacement table, MSHR, miss/replay/refill queue, writeback buffer, probe/invalidate path, prefetch table, or cache-maintenance/WPU path.

Every selected scenario must be refined with exact code evidence from the analyzer output. Do not infer cache behavior from the module name. Identify the effective tag match, valid/dirty/coherence state, set/bank/way index, replacement policy, MSHR allocation/merge rule, refill/probe/writeback FSM, full/almost-full condition, flush/invalidate trigger, reload/refill completion, and downstream handshake.

## Cache Structure Driver Shape

```markdown
## Cache Structure Verification
| Cache ID | Structure | Code evidence needed | Stimulus | Expected cache observation | Checkers |
| --- | --- | --- | --- | --- | --- |
```

For every cache-like structure, include:

- Hit path: valid tag match, permission/coherence state, way select, data return, bypass/forwarding, and no miss-side allocation.
- Miss path: tag mismatch or invalid line, MSHR allocation or merge, replay/backpressure, refill request, response acceptance, metadata update, and final reload hit.
- Replacement path: victim selection, replacement-state update, dirty writeback, probe/invalidate interaction, refill installation, and lost-victim prevention.
- Bank conflict: multiple legal accesses mapping to the same bank/port/way pipeline; verify winner rule, loser stall/retry/replay, and no payload corruption.
- Set conflict: same set with different tags, same set with different ways, refill/probe/store/fetch hitting one set, and replacement state consistency.
- Cache full: all ways/MSHRs/replay entries/writeback slots/refill buffers full or almost full; verify backpressure, legal drain, no duplicate allocation, and no dropped live request.
- Flush+reload: a flush, invalidate, redirect, CBO, `fence.i`, context switch, probe, domain switch, or cache full recovery triggers a legal flush/invalidate/drain, then a reload/refill proves stale data cannot hit.

## Cache Operation Drivers

| Cache ID | Scenario | Stimulus construction | Expected behavior | Checkers |
| --- | --- | --- | --- | --- |
| `CACHE_HIT_CLEAN` | Clean hit | Install a clean valid line, access same address/context | Hit selects the expected way and returns data without miss allocation | Hit/data checker |
| `CACHE_HIT_DIRTY` | Dirty hit | Store to create dirty line, then load/AMO/fetch when legal | Hit returns latest data and preserves dirty/coherence metadata | Hit/meta checker |
| `CACHE_MISS_INVALID` | Invalid-line miss | Access a set/way whose valid bit is clear | Miss allocates or refills through the code-defined path | Miss/refill checker |
| `CACHE_MISS_DIFF_TAG` | Same-set different-tag miss | Access two addresses with same set and different tag | First line remains valid or is replaced exactly as policy defines | Miss/tag checker |
| `CACHE_MSHR_MERGE` | Miss merge | Issue two misses to the same line while first miss is outstanding | Requests merge or serialize per code; one legal refill satisfies both if merged | MSHR merge checker |
| `CACHE_MSHR_ALLOC_FULL` | MSHR full | Exhaust MSHR entries, then issue one more miss | New miss stalls/replays/backpressures; no duplicate live allocation | Occupancy checker |
| `CACHE_REFILL_RELOAD` | Reload after refill | Miss a line, accept refill, access same line again | Reload hits with refilled data and valid metadata | Reload checker |

## Replacement, Bank, and Set Conflict Drivers

| Cache ID | Scenario | Stimulus construction | Expected behavior | Checkers |
| --- | --- | --- | --- | --- |
| `CACHE_REPLACE_CLEAN` | Clean victim replacement | Fill all ways in a set with clean lines, then miss same set | Code-derived replacement way is invalidated/refilled without writeback | Replacement checker |
| `CACHE_REPLACE_DIRTY` | Dirty victim replacement | Fill all ways, dirty one or more victims, then miss same set | Dirty victim writeback/release completes before stale state is reused | Writeback/replacement checker |
| `CACHE_REPLACE_PROBE_RACE` | Replacement versus probe/invalidate | Probe or invalidate the selected victim during replacement/refill | Coherence state and victim ownership remain legal | Coherence checker |
| `CACHE_BANK_CONFLICT` | Bank conflict | Generate simultaneous accesses mapping to the same bank/port | Winner follows arbiter/FSM priority; losers stall, retry, or replay | Bank conflict checker |
| `CACHE_SET_CONFLICT` | Set conflict | Generate same-set/different-tag accesses across load/store/fetch/refill/probe paths | Set state, way select, replacement metadata, and replay behavior match code | Set conflict checker |
| `CACHE_ARRAY_RW_CONFLICT` | Array read/write conflict | Read tag/data/meta while refill/store/probe writes same set/way | Read-old/read-new/bypass/stall behavior follows code | Array conflict checker |

## Flush+Reload Drivers

Flush+reload means the driver first proves a line or miss state is live and observable, then triggers the code-derived flush/invalidate/drain mechanism, and finally reloads the same address or conflict group to prove the stale state was removed or safely revalidated.

| Cache ID | Flush+reload trigger | Stimulus construction | Expected behavior | Checkers |
| --- | --- | --- | --- | --- |
| `CACHE_FR_FLUSH_HIT` | Explicit flush/invalidate on hit | Create a hit line, trigger CBO/cache op/flush/invalidate, then reload same address | Old line cannot hit unless code proves it was legally revalidated; reload returns correct data | Flush+reload checker |
| `CACHE_FR_MISS` | Flush during miss | Start a miss/refill, trigger redirect/flush/context switch before response, then reload | Stale response is killed, tagged, or rechecked; reload belongs to the live context | Miss flush checker |
| `CACHE_FR_REPLACE` | Flush during replacement | Start victim replacement/writeback/refill, trigger flush, then reload victim and new line | No lost dirty data, no stale victim hit, replacement FSM reaches legal state | Replacement flush checker |
| `CACHE_FR_BANK_CONFLICT` | Flush after bank conflict | Hold a losing bank-conflict request pending, flush, then reload both winner and loser addresses | Killed loser cannot complete stale; surviving request reloads legally | Bank flush checker |
| `CACHE_FR_SET_CONFLICT` | Flush after set conflict | Drive same-set/different-tag conflict, flush/invalidate set, then reload conflict group | Set contains only code-legal survivors; stale tags do not hit | Set flush checker |
| `CACHE_FR_FULL` | Cache/resource full | Fill all ways or all MSHR/replay/writeback entries, trigger flush/drain/recovery, then reload | Full condition is cleared or drains; new reload can allocate and complete | Full flush checker |
| `CACHE_FR_CONTEXT` | Context/domain switch | Create cache residency or outstanding miss, switch ASID/VMID/privilege/domain, then reload same index/tag | Tags, flush, or recheck prevent stale permission/data leak | Context isolation checker |
| `CACHE_FR_PROBE` | Coherence probe/invalidate | Create a shared/dirty line, deliver probe/invalidate, then reload | Probe result and subsequent reload preserve coherence and visibility | Coherence reload checker |

## Required Cross-References

- Use `skills/indexBusHashDrivers.md` to generate same-index/different-tag, same-bank, same-set, page/cacheline boundary, and same-hash/different-context address groups from exact code-derived index/hash expressions.
- Use `skills/conflictScenarioDrivers.md` for winner/loser behavior in bank, set, refill/probe/store, replacement, MSHR, and writeback conflicts.
- Use `skills/forwardProgressDrivers.md` for full-resource deadlock, repeated replay livelock, replacement fairness, bank-conflict starvation, and flush-drain progress.
- Use `skills/virtualizationProtectionDrivers.md` and `skills/systemVirtualizationPermissionDrivers.md` when cache behavior depends on translation, PMP/PMA/IOPMP, MMIO/uncache, ASID, VMID, privilege, or supervisor-domain state.
- Use `skills/architectureExceptionDrivers.md` for architectural legality and exception behavior of load/store/fetch/AMO/LR/SC/CBO/cache-management instructions.

## Completion Checklist

Before a cache structure driver is complete:

- Every cache-like structure has explicit hit, miss, replacement, bank-conflict, set-conflict, full/almost-full, and flush+reload coverage, or exact code evidence that the scenario is unreachable.
- Hit tests cover clean, dirty, permission/coherence-state, same-index/different-context, and same-cycle array update cases when reachable.
- Miss tests cover invalid line, different tag, MSHR allocate, MSHR merge, MSHR full, refill response, replay, and reload-after-refill.
- Replacement tests cover clean victim, dirty victim, writeback/release, replacement-state update, probe/invalidate race, and refill installation.
- Bank and set conflict tests state the address group, winner rule, loser behavior, replay/stall policy, affected arrays/FSMs, and fairness assumption.
- Full-resource tests fill all relevant ways, MSHRs, replay entries, writeback buffers, refill buffers, source/sink IDs, or protocol trackers, then prove drain or flush recovery.
- Flush+reload tests are triggered by every code-reachable flush/invalidate/redirect/context/probe/cache-op/full-recovery path and prove stale data, stale permissions, stale tags, and stale miss responses cannot be observed.
- All cache claims cite effective Chisel source evidence before the driver relies on them.
