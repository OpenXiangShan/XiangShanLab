# XiangShan FSM Scenario Drivers

Use this file for every explicit or implicit state machine in XiangShan modules. This includes `Enum`/`ChiselEnum` FSMs, valid-bit lifecycles, queue-entry status machines, protocol phases, replay/refill/writeback states, interrupt delivery states, debug entry/resume states, PTW/TLB states, cache miss states, bus channel trackers, and any state encoded by `valid`, `busy`, `pending`, `sent`, `wait`, `done`, or entry status fields.

Each FSM driver must construct sequences that drive every state and every legal transition. It must also construct simultaneous-trigger scenarios where different input sequences would request different next states in the same cycle, then verify the code-defined transition priority. Deadlock, livelock, and starvation checks must use `skills/forwardProgressDrivers.md`.

## FSM Driver Shape

```markdown
## FSM Scenario Verification
| FSM ID | Current state | Trigger sequence | Competing trigger sequence | Expected next state | Priority/checker |
| --- | --- | --- | --- | --- | --- |
```

For every FSM, include:

- State inventory: reset state, idle state, busy/wait states, terminal states, error/assert states, and implicit valid/status states.
- Entry sequence: the shortest legal stimulus sequence that reaches each state from reset.
- Exit sequence: the shortest legal stimulus sequence that leaves each state.
- Hold sequence: stimulus that keeps the FSM in the same state, usually backpressure, resource full, missing response, or disabled grant.
- Progress sequence: stimulus that first creates a deadlock/livelock/starvation candidate, then provides fair completion inputs and checks exit or documented unreachable behavior.
- Multi-trigger sequence: same-cycle or overlapping-cycle events where different conditions request different next states.
- Priority: exact code order from `when/.elsewhen`, `switch/is`, `Mux`, `PriorityMux`, arbiter grant, fire condition, or FSM helper.
- Output checks: outputs allowed in the current state, outputs forbidden in the current state, ready/valid behavior, state-update timing, and side effects.

## State Entry and Transition Drivers

| FSM ID | Scenario | Stimulus construction | Expected behavior | Checkers |
| --- | --- | --- | --- | --- |
| `F_RESET_IDLE` | Reset path | Assert reset, deassert reset, keep all requests idle | FSM reaches documented reset/idle state; all valid/status bits initialized | FSM reset checker |
| `F_FIRST_REQUEST` | First legal request | From idle, assert exactly one request with downstream ready | FSM enters first busy/request state; request side effect occurs once | FSM transition checker |
| `F_HOLD_BACKPRESSURE` | Hold current state | Enter busy/wait state, then deassert downstream ready or withhold response | State holds; payload and side effects remain stable | FSM hold checker |
| `F_NORMAL_COMPLETE` | Normal completion | Drive expected response/grant/ack while no flush/replay/exception | FSM advances to next phase or idle; output fires once | FSM completion checker |
| `F_TIMEOUT_OR_RETRY` | Retry/timeout path | Withhold grant/response until retry condition, or inject retry response | FSM enters retry/replay state and preserves required payload | Retry checker |
| `F_ERROR_RESPONSE` | Error path | Inject bus error, deny, fault, corrupt, access fault, or illegal response | FSM enters error/trap/replay/drain state as code defines | Error checker |
| `F_ILLEGAL_TRIGGER` | Illegal transition guard | Assert trigger that is not legal in current state | FSM ignores, stalls, or fires assertion according to code | Assertion checker |
| `F_TERMINAL_CLEAR` | Terminal clear | Reach done/error/valid terminal state, then consume clear/deq/commit | State clears exactly once and can accept new request afterward | Clear checker |
| `F_DEADLOCK_WAIT_EXIT` | Wait-state progress | Enter each wait/busy state, hold missing response/backpressure, then provide legal response/ready/grant | FSM exits the wait state within code-derived bound or next legal transition | FSM deadlock checker |
| `F_LIVELOCK_RETRY_CYCLE` | Retry-cycle progress | Force retry/replay/error-recover transitions repeatedly, then provide success response | FSM reaches done/idle/error without duplicate side effects | FSM livelock checker |
| `F_STARVE_LOW_PRIORITY_TRANSITION` | Low-priority transition starvation | Hold a low-priority transition true while pulsing higher-priority transitions for longer than code-derived fairness bound | Low-priority transition eventually fires, is promoted, or is documented as starvable/unreachable | FSM starvation checker |

## Simultaneous Trigger Drivers

These drivers are mandatory when more than one condition can influence `nextState` in the same cycle.

| FSM ID | Conflict class | Stimulus construction | Expected priority | Loser behavior | Checkers |
| --- | --- | --- | --- | --- | --- |
| `F_REQ_AND_FLUSH` | Request versus flush | From idle or busy, assert request/fire and redirect/flush in same cycle | Code-defined flush/request priority | Losing request is killed, held, or not accepted | FSM/flush checker |
| `F_RESP_AND_FLUSH` | Response versus flush | In wait-response state, assert response valid and redirect/flush in same cycle | Code-defined response/flush priority | Killed response cannot commit illegal state | FSM/flush checker |
| `F_RESP_AND_REPLAY` | Response versus replay | In wait-response state, assert success response and replay condition together | Code-defined success/replay priority | Non-winning action does not double-update state | FSM/replay checker |
| `F_EXCEPTION_AND_REPLAY` | Exception versus replay | Generate fault metadata and replay trigger for same operation | Exception/replay priority follows spec and code | Lower-priority action deferred or masked | Trap/replay checker |
| `F_GRANT_AND_CANCEL` | Grant versus cancel | Resource grant/arbiter winner and cancel arrive same cycle | Code-defined grant/cancel priority | Grant does not leak to killed transaction | Arbiter/FSM checker |
| `F_ENQ_DEQ_FLUSH` | Queue FSM conflict | Enqueue, dequeue, and flush same cycle while queue state is empty, almost empty, one-entry live, full, almost full, and pointer-wrapped when reachable | Pointer/status update priority follows code for every occupancy extreme | Killed entry not visible; survivor preserved; empty/full/almost flags remain correct | Occupancy/FSM checker |
| `F_FLUSH_EXTREME_STATE` | Flush from FSM extremes | Assert flush/cancel/redirect from idle, first request, every busy/wait/retry state, response-valid state, error state when reachable, full-resource state, empty-resource state, and same-cycle competing transition state | Code-defined flush priority reaches killed/drain/idle or documented survivor state | No killed transaction completes, no resource is double-freed or leaked, and new legal work can enter after recovery | FSM flush extreme checker |
| `F_ALLOC_FREE_REDIRECT` | Allocation lifecycle conflict | Allocate, free, and redirect/recover same cycle | Redirect/free/alloc priority follows code | No duplicate live resource or leaked free | Resource/FSM checker |
| `F_COMMIT_INTERRUPT_EXCEPTION` | Commit/trap conflict | Commit, interrupt, exception, debug trigger, halt request, or xRET overlap | Architecturally legal trap/debug/commit priority with correct `dcsr`/`dpc` or EPC/cause update | Lower-priority event pending, masked, killed, or deferred per code | Trap/debug FSM checker |
| `F_CONTEXT_SWITCH_BUSY` | Context switch while busy | Privilege/process/VM/domain switch while FSM has live request | Flush, tag recheck, drain, or block policy follows code | Stale context cannot complete visibly | Context checker |
| `F_BUSY_PROGRESS_AFTER_FLUSH` | Busy hold versus flush progress | Keep busy/hold condition true while asserting flush/cancel/redirect, then release downstream | FSM reaches killed/drain/idle and accepts new legal work | FSM progress checker |

## Same-Time Multi-Sequence Construction

For the requirement that different sequences trigger different FSM states at the same time, use this method:

1. Build a prefix sequence for each target state.
   - `prefix_A` reaches state A.
   - `prefix_B` reaches state B.
   - `prefix_C` reaches state C.
2. Align the final cycle of each prefix so the selected trigger cycle is cycle `T`.
3. At cycle `T`, drive the competing triggers together in one test when the FSM can observe them together, or in parallel randomized lanes/entries when the FSM is replicated per entry.
4. Check the code-defined priority:
   - If the FSM is single-instance, exactly one `nextState` wins.
   - If the FSM is per-entry, each entry may transition independently, but shared arbiters and ports must select legal winners.
   - If two triggers are declared mutually exclusive by code assertions, verify the assertion or add constrained-random assumptions.

## Transition Matrix Requirement

Every FSM driver must include a transition matrix:

| From state | To state | Required sequence | Competing triggers at same cycle | Priority rule | Expected outputs | Illegal transitions |
| --- | --- | --- | --- | --- | --- | --- |

The matrix must cover:

- Reset to idle.
- Idle to every request state.
- Every request state to wait/response/hold states.
- Every wait state to complete/retry/error states.
- Every busy state to flush/cancel/replay states.
- Every terminal state back to idle or release.
- Every explicitly unreachable transition and its assertion/constraint.

## Coverage Goals

The generated testbench or verification driver should expose these coverage points:

- `cover_state_<state>`: each state reached.
- `cover_transition_<from>_<to>`: each legal transition fired.
- `cover_hold_<state>`: each hold/stall condition observed.
- `cover_priority_<state>_<trigger_a>_<trigger_b>`: simultaneous trigger priority observed.
- `cover_illegal_<state>_<trigger>`: illegal trigger assertion or constrained unreachable case observed.
- `cover_context_<state>_<priv/process/vm/domain>`: context switch while state is live.
- `cover_deadlock_wait_<state>`: wait/busy state exits after fair completion input.
- `cover_livelock_retry_<state>`: retry/replay cycle exits after fair success input.
- `cover_starve_low_priority_<state>`: low-priority transition remains pending under high-priority pulses and then progresses or is documented starvable.

## Completion Checklist

Before an FSM driver is complete:

- Every state has an entry, hold, exit, and output check.
- Every legal transition has a concrete sequence.
- Every transition priority has a same-cycle or overlapping-cycle competing-trigger test, including low-priority transition starvation under repeated higher-priority triggers.
- Every illegal transition is asserted, constrained, or explicitly unreachable by code evidence.
- Flush, redirect, replay, exception, interrupt/debug, debug entry/resume, reset, and context switch are tested while the FSM is idle, first-request, busy, waiting, retrying, response-valid, full-resource, empty-resource, and non-idle when reachable. Debug entry/resume FSMs also use `skills/debugEventDrivers.md`.
- Every wait/busy state has a deadlock-exit test, every retry/replay loop has a livelock-exit test, and every lower-priority transition has a starvation test using `skills/forwardProgressDrivers.md`.
- Replicated per-entry FSMs test independent entry transitions plus shared-port contention.

