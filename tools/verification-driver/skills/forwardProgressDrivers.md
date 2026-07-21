# XiangShan Forward Progress Drivers

Use this file for every module driver. Deadlock, livelock, and starvation are global verification concerns, not optional stress cases. Every queue, FSM, mux, arbiter, scheduler, replay path, credit tracker, bus bridge, cache miss path, interrupt path, trap path, and shared resource must either select applicable scenarios from this file or provide exact code evidence that the scenario is unreachable.

Every progress claim must cite effective XiangShan Chisel evidence from `tools/xiangshan-code-analyzer/skills/analyze-xiangshan-kunminghu`: request valid source, hold condition, grant/ready rule, state transition, counter/credit update, flush/replay kill rule, and completion condition.

## Forward Progress Driver Shape

```markdown
## Forward Progress Verification
| Progress ID | Scope | Code evidence needed | Stimulus construction | Expected progress property | Failure mode checked | Checkers |
| --- | --- | --- | --- | --- | --- | --- |
```

For every selected scenario, include:

- Progress boundary: request accepted, grant fired, response returned, entry freed, state exited, trap/debug redirect completed, or transaction retired.
- Fairness assumption: which downstream response, ready, credit, memory response, interrupt clear, or environment action is assumed eventually available.
- Bounded expectation when code provides a bound: maximum wait cycles, queue depth, round-robin period, retry count, timeout, or age threshold.
- Unbounded expectation when no bound exists: request remains valid, is not dropped, and is eventually grantable under fair downstream behavior.
- Failure signature: no fire for all clients, valid/ready circular wait, state cycle without useful work, repeated replay without retirement, lower-priority request never served, or old request bypassed forever by newer traffic.

## Global Deadlock Drivers

| Progress ID | Scenario | Stimulus construction | Expected behavior | Checkers |
| --- | --- | --- | --- | --- |
| `P_DEADLOCK_ALL_STALL` | All visible clients stalled | Fill upstream/downstream queues, assert all requesters, then release one legal downstream sink | At least one legal fire or state transition occurs after release | Deadlock checker |
| `P_DEADLOCK_BACKPRESSURE_CYCLE` | Ready/valid circular wait | Create a ring of modules where each waits for another ready/credit/empty condition | Code breaks the cycle by buffering, priority, drain, replay, or explicit backpressure rule | Handshake progress checker |
| `P_DEADLOCK_RESOURCE_FULL` | Full resource prevents its own drain | Fill ROB/LSQ/MSHR/PTW/replay/cache/bus tracker and hold producers valid | Existing entries can drain or a legal flush/error/replay frees space | Occupancy progress checker |
| `P_DEADLOCK_FLUSH_DRAIN` | Flush while blocked | Assert every flush/redirect/cancel/kill path while affected resources are empty, almost empty, one-entry live, full, almost full, wrapped, response-blocked, and backpressured, then release legal sinks | Killed entries are freed or drained according to code and cannot block new legal work forever; empty/full flags recover correctly | Flush progress checker, occupancy progress checker |
| `P_DEADLOCK_CONTEXT_SWITCH` | Context switch with live work | Switch privilege/process/VM/domain while protected transactions are outstanding | Old-context work drains, is killed, or is rechecked; new-context work is not blocked forever | Context progress checker |
| `P_DEADLOCK_TRAP_DEBUG` | Trap/debug entry blocked by pipeline state | Hold commit/trap/debug conditions while queues or redirects are busy | Oldest legal trap/debug event eventually redirects or is explicitly masked by spec/code | Trap/debug progress checker |

## Livelock Drivers

| Progress ID | Scenario | Stimulus construction | Expected behavior | Checkers |
| --- | --- | --- | --- | --- |
| `P_LIVELOCK_REPLAY_LOOP` | Replay without retirement | Force repeated cache/TLB/bank/ordering replay for one old operation while downstream eventually becomes serviceable | Replay count eventually stops for the old operation or escalates to a legal fault/flush | Replay progress checker |
| `P_LIVELOCK_REDIRECT_LOOP` | Redirect without useful commit | Generate repeated branch/replay/exception redirects around the same older request | Older legal work is either committed, trapped, killed by a legal older redirect, or proven unreachable | Redirect progress checker |
| `P_LIVELOCK_INVALIDATE_REFILL` | Refill invalidated repeatedly | Race TLB/cache refill against invalidate/context switch in a sustained pattern | A valid request eventually refills under stable context, or stale refill is killed without blocking future refills | Refill progress checker |
| `P_LIVELOCK_RETRY_NACK` | Retry/NACK loop | Return retry/NACK responses for a request, then stop retrying | Request eventually completes after fair response, without duplicate side effects | Retry progress checker |
| `P_LIVELOCK_FSM_CYCLE` | FSM cycles among nonterminal states | Drive triggers that move the FSM around a cycle without hitting done/error/idle | Under fair completion inputs, the FSM exits the cycle or the cycle is constrained unreachable | FSM progress checker |

## Starvation Drivers

| Progress ID | Scenario | Stimulus construction | Expected behavior | Checkers |
| --- | --- | --- | --- | --- |
| `P_STARVE_OLD_LOW_NEW_HIGH` | Old request low priority, new request high priority | Hold an older request on a low-priority input while injecting newer requests on higher-priority inputs for longer than queue/age/RR depth | Older request is eventually granted, aged, promoted, replayed to a fair path, or a documented starvation policy is checked | Starvation checker |
| `P_STARVE_FIXED_PRIORITY` | Fixed-priority loser | Keep high-priority requester continuously valid while low-priority requester remains valid | Low-priority requester eventually fires if fairness is promised, or driver marks starvation as an intentional design property with evidence | Arbiter fairness checker |
| `P_STARVE_ROUND_ROBIN_WRAP` | Round-robin fairness boundary | Assert all requesters valid across pointer wrap and backpressure | Every requester receives service within the code-derived rotation bound | RR checker |
| `P_STARVE_AGE_PRIORITY` | Age priority boundary | Create old/new requests with same resource target and vary age metadata around min/max/wrap | Oldest eligible request wins; age wrap cannot make an old request permanently young | Age checker |
| `P_STARVE_QUEUE_HEAD_BLOCK` | Head blocks younger or younger bypasses head | Keep head entry blocked and younger entries serviceable, then unblock head | Head entry eventually completes or legal bypass policy cannot starve it | Queue fairness checker |
| `P_STARVE_CREDIT_RETURN` | Credit starvation | Exhaust credits for one source while other sources receive credits/responses | Credit return and grant policy eventually allow the starved source under fair sink behavior | Credit checker |

## Mux and Arbiter Priority Drivers

Every mux, `Mux1H`, `PriorityMux`, `Arbiter`, `RRArbiter`, ready/valid select, grant vector, scheduler select, oldest-select, first-one select, encoder, port arbiter, crossbar arbiter, replay arbiter, writeback arbiter, commit arbiter, and replacement selector must include these scenarios when it selects among competing requesters.

| Progress ID | Scenario | Stimulus construction | Expected behavior | Checkers |
| --- | --- | --- | --- | --- |
| `ARB_OLD_LOW_NEW_HIGH` | Older low-priority request versus newer high-priority request | Make low-priority request valid first and keep it valid; inject high-priority request later at the same selection point | Code priority is observed, and progress/fairness result for the older request is explicitly checked | Arbiter/starvation checker |
| `ARB_AGE_INVERSION_BOUND` | Repeated age inversion | Repeat newer high-priority arrivals while older low-priority request remains pending | Older request is eventually served, promoted, replayed, or documented as starvable by code policy | Age/fairness checker |
| `ARB_ALL_VALID_PERSISTENT` | All requesters persistent | Keep all requesters valid under downstream ready for multiple selection periods | Grant sequence matches fixed/RR/age rule and no requester with fairness guarantee is skipped forever | Arbiter checker |
| `ARB_READY_DROP_AFTER_GRANT` | Ready drops after winner selected | Select a winner, then drop downstream ready while losers remain valid | Winner/loser valid and payload remain stable; no requester loses its pending work | Handshake fairness checker |
| `ARB_GRANT_MASK_FLUSH_REPLAY` | Grant races kill/replay mask | Select a winner while flush, replay, redirect, or context mask changes eligibility | Killed winner cannot consume grant; next legal requester progresses | Arbiter/flush checker |
| `ARB_PRIORITY_WRAP` | Priority pointer or age wrap | Drive requester IDs, pointers, or age counters across min/max/wrap | Wrap does not starve a requester or grant an ineligible request | Boundary fairness checker |

## FSM Progress Drivers

Every explicit or implicit FSM must combine `skills/fsmScenarioDrivers.md` with these progress tests.

| Progress ID | Scenario | Stimulus construction | Expected behavior | Checkers |
| --- | --- | --- | --- | --- |
| `FSM_DEADLOCK_WAIT_NO_EXIT` | Wait state cannot exit | Enter each wait/busy state, then provide the legal response/grant/ready | FSM exits within code-derived bound or next legal cycle | FSM deadlock checker |
| `FSM_LIVELOCK_RETRY_CYCLE` | Retry cycle repeats | Force retry/replay transitions several times, then provide a successful response | FSM reaches done/idle/error without duplicate side effects | FSM livelock checker |
| `FSM_STARVE_LOW_TRIGGER` | Low-priority transition starved | Hold a low-priority transition condition true while repeatedly pulsing higher-priority conditions | Low-priority transition eventually fires if promised, or starvation is documented with code evidence | FSM starvation checker |
| `FSM_FLUSH_BUSY_PROGRESS` | Flush competes with busy hold | Keep busy hold condition true while asserting flush/cancel/redirect | FSM reaches killed/drain/idle state and does not preserve blocked wrong-path work forever | FSM flush progress checker |
| `FSM_CONTEXT_BUSY_PROGRESS` | Context switch while busy | Hold live request in FSM and switch privilege/process/VM/domain | FSM drains, kills, tags, or rechecks the request and accepts new legal context work | FSM context progress checker |

## Completion Checklist

Before any generated driver is complete:

- Deadlock, livelock, and starvation scenarios are included for every queue, FSM, mux, arbiter, scheduler, replay path, bus bridge, cache/MMU miss path, and shared resource.
- Every mux/arbiter has an `ARB_OLD_LOW_NEW_HIGH` test where an older low-priority request is held while newer high-priority requests arrive.
- Every fixed-priority selector either proves low-priority progress under assumptions or documents the exact code evidence that starvation is an accepted policy.
- Every round-robin or age-based selector tests pointer/age wrap and persistent all-requester-valid traffic.
- Every FSM has deadlock wait-state, livelock retry-cycle, low-priority transition starvation, flush-while-busy, and context-switch-while-busy coverage when reachable.
- Every progress assertion states the fairness assumptions required from the environment and the code-derived bound when one exists.
- Checkers distinguish legal backpressure from deadlock, legal repeated retry from livelock, and intentional priority from unintended starvation.
