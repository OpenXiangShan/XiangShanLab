# XiangShan Performance Monitor Counter Stress Drivers

Use this file when a module implements, updates, filters, exposes, virtualizes, snapshots, or consumes performance monitor counters. This includes `mcycle`, `minstret`, `cycle`, `instret`, `mhpmcounter*`, `hpmcounter*`, `mhpmevent*`, inhibit/control CSRs, counter overflow/interrupt logic, privilege filtering, virtualization filtering, debug-mode behavior, commit-event counting, microarchitecture event counting, and any performance-event bus or aggregator.

Every architectural claim must be verified with `riscv-spec`/UDB or an upstream RISC-V privileged specification source when UDB lacks coverage. Every implementation claim must cite effective XiangShan Chisel evidence from the code analyzer: counter width, increment source, event select decode, inhibit mask, privilege/filter mask, overflow behavior, CSR read/write path, snapshot path, arbitration, and update timing.

## Performance Counter Driver Shape

```markdown
## Performance Monitor Counter Stress Verification
| Counter/Event | Spec/code evidence needed | Stress stimulus | Expected behavior | Checkers |
| --- | --- | --- | --- | --- |
```

For every selected counter, include:

- Counter inventory: CSR address, implemented width, reset value, writable mask, read path, write path, increment source, event selector, privilege visibility, virtualization behavior, inhibit gate, overflow behavior, and downstream consumers.
- Event inventory: every event bit or event class, its producer signal, pulse/level semantics, qualification by valid/fire/commit, flush/replay kill rule, privilege/context filter, and aggregation rule.
- Stress construction: maximum legal concurrent events, repeated pulses for many cycles, alternating dense/sparse pulses, event bursts across flush/replay/trap/debug/context-switch windows, and CSR access races.

## Architecture and CSR Access Stress

| Stress ID | Scenario | Stimulus construction | Expected behavior | Checkers |
| --- | --- | --- | --- | --- |
| `PMC_CSR_RESET_MASK` | Reset and writable mask | Read every implemented counter/control CSR after reset, then write all-zero, all-one, walking-one, walking-zero, reserved-bit-one, and WARL edge values | Reset values, writable bits, reserved bits, and WARL legalization match spec/code | CSR mask checker |
| `PMC_CSR_PRIV_ACCESS` | Privilege access legality | Access `cycle`, `time`, `instret`, `hpmcounter*`, `mcycle`, `minstret`, `mhpmcounter*`, and `mhpmevent*` from M/S/U and HS/VS/VU when implemented while sweeping counter-enable/config CSRs | Legal reads/writes succeed; illegal or disabled accesses trap or virtualize as spec/code require | CSR privilege checker |
| `PMC_CSR_READ_WRITE_RACE` | CSR read/write versus increment | Read, write, set, clear, and read-modify-write a counter in the same or adjacent cycles as one or more increments | Read-old/read-new/write-priority behavior follows code and no increment is lost unless code explicitly defines overwrite semantics | CSR race checker |
| `PMC_CSR_SNAPSHOT_ATOMICITY` | Multiword or snapshot read | For counters wider than the architectural read granule, read low/high halves around carry and overflow boundaries | Software-visible value is coherent according to spec/code; carry cannot produce impossible mixed values | Snapshot checker |
| `PMC_INHIBIT_GATE` | Inhibit and enable gate | Toggle inhibit/control bits before, during, and after event pulses, including same-cycle CSR write plus event | Counters increment only when enabled by the code-defined timing rule | Inhibit checker |

## Counting Precision Stress

| Stress ID | Scenario | Stimulus construction | Expected behavior | Checkers |
| --- | --- | --- | --- | --- |
| `PMC_CYCLE_STRESS` | Cycle counter pressure | Run long idle, full-stall, all-pipeline-active, clock-gated, reset-exit, debug-entry, and low-power/WFI-like windows when reachable | Cycle-like counters advance or stop exactly according to spec/code gates | Cycle checker |
| `PMC_INSTRET_STRESS` | Retired instruction pressure | Retire 0, 1, max commit width, mixed-width, CSR, branch, load/store, vector, trap-return, and serialized instructions across many cycles | `instret`/`minstret` increments once per architecturally retired instruction and never counts killed, replayed, faulting-before-retire, or squashed instructions | Retire counter checker |
| `PMC_EVENT_MAX_CONCURRENCY` | Maximum concurrent event pulses | Assert all legal event producers in the same cycle, including multi-commit, multi-issue, LSU, frontend, cache, branch, replay, exception, interrupt, and debug producers when reachable | Aggregation saturates, sums, prioritizes, or selects exactly as code defines; no event source is silently dropped | Event aggregation checker |
| `PMC_EVENT_BURST` | Long burst and sparse burst | Drive event pulses for 1 cycle, 2 cycles, max pipeline latency, counter-wrap distance when feasible, and alternating 1010/0101 patterns | Counter delta equals the model for pulse/level semantics and enable/filter state | Event delta checker |
| `PMC_MULTI_COUNTER_SAME_EVENT` | One event mapped to many counters | Program multiple counters to the same event and drive dense event bursts | All enabled counters observe identical qualified events unless code documents separate filters or arbitration | Multi-counter checker |
| `PMC_MANY_EVENTS_SAME_COUNTER` | Event select stress | Sweep every event encoding, illegal encoding, reserved encoding, and rapid event-select changes while events are active | Selected event changes according to CSR timing; illegal/reserved encodings follow spec/code | Event select checker |

## Overflow, Boundary, and Saturation Stress

| Stress ID | Scenario | Stimulus construction | Expected behavior | Checkers |
| --- | --- | --- | --- | --- |
| `PMC_COUNTER_BOUNDARY` | Counter numeric boundaries | Initialize counters to `0`, `1`, `max-1`, `max`, sign-bit, carry-boundary, and random high values, then apply 0, 1, and many increments | Wrap, carry, saturation, or overflow flag behavior matches spec/code | Boundary checker |
| `PMC_OVERFLOW_INTERRUPT` | Overflow side effects | Generate overflow with interrupt disabled, enabled, delegated, masked, and concurrent with exception/debug/trap return | Overflow status, interrupt pending, delegation, and trap priority are precise and no double interrupt is produced | Overflow interrupt checker |
| `PMC_OVERFLOW_MULTI_COUNTER` | Simultaneous overflows | Force several counters to overflow in the same cycle while CSR reads/writes and event bursts occur | Per-counter overflow state is not lost; priority/aggregation follows code | Multi-overflow checker |
| `PMC_RESET_FLUSH_OVERFLOW` | Reset/flush around overflow | Assert reset, flush, redirect, context switch, debug entry, or trap entry immediately before/on/after overflow | Counter, pending, and overflow state follows code-defined reset/flush preservation policy | Overflow recovery checker |

## Flush, Replay, Trap, and Debug Stress

| Stress ID | Scenario | Stimulus construction | Expected behavior | Checkers |
| --- | --- | --- | --- | --- |
| `PMC_FLUSH_KILL` | Flush kills wrong-path events | Produce frontend/backend/cache/predictor/LSU events on wrong path, then redirect/flush at every pipeline stage and queue occupancy extreme | Killed wrong-path events do not increment architectural counters unless code explicitly defines speculative counters | Flush/event checker |
| `PMC_REPLAY_DOUBLE_COUNT` | Replay pressure | Force repeated replay of the same instruction or memory op before eventual commit | Commit-visible counters count once; microarchitecture replay counters count exactly the number of qualified replay events | Replay counter checker |
| `PMC_EXCEPTION_INTERRUPT` | Trap pressure | Combine event pulses with exception, interrupt, NMI-like local interrupt, xRET, and trap entry/return windows | Retire, exception, interrupt, and event counters update at precise code-defined boundaries | Trap counter checker |
| `PMC_DEBUG_MODE` | Debug overlap | Enter debug by halt, trigger, `ebreak`, and single-step while counters/events are active; execute debug-mode CSR accesses when implemented | Debug-mode counting, inhibit, CSR legality, and resume behavior match spec/code | Debug counter checker |

## Privilege, Virtualization, and Context Stress

| Stress ID | Scenario | Stimulus construction | Expected behavior | Checkers |
| --- | --- | --- | --- | --- |
| `PMC_PRIV_FILTER` | Privilege filtering | Run identical event streams in M/S/U and HS/VS/VU modes while sweeping filter/control bits | Counters include or exclude events exactly according to privilege and virtualization filters | Privilege filter checker |
| `PMC_CONTEXT_SWITCH` | Context switch pressure | Switch `satp`, ASID, VMID, domain, privilege, and debug state while counters are live and events are pending | Counters are preserved, virtualized, filtered, or rechecked by code policy; no stale context leaks into visible counters | Context checker |
| `PMC_GUEST_HOST` | Guest/host event separation | Run guest events, host trap handling, nested guest/host faults, and virtual interrupts while counters are enabled | Guest-visible and host-visible counters/overflows/pending bits are routed and filtered correctly | Virtualization counter checker |

## Microarchitecture Resource Stress

| Stress ID | Scenario | Stimulus construction | Expected behavior | Checkers |
| --- | --- | --- | --- | --- |
| `PMC_EVENT_BUS_FULL` | Event bus or aggregator pressure | Drive more event producers than physical event ports, all event bits active, and back-to-back event packets if the design has an event bus | Arbitration, OR/sum reduction, valid/ready, and dropped-event policy follow code | Event bus checker |
| `PMC_QUEUE_FULL_EMPTY` | Counter update queue extremes | Fill, empty, almost-fill, almost-empty, wrap, and simultaneous enqueue/dequeue any counter update queue, snapshot queue, or overflow queue | Occupancy flags, backpressure, and update delivery are correct; no lost or duplicated counter update | Occupancy checker |
| `PMC_CSR_PORT_CONTENTION` | CSR port contention | Concurrent counter read/write, event update, trap CSR update, debug CSR access, and interrupt pending update | Port arbitration and update priority follow code; losing updates are held, merged, or explicitly masked | CSR conflict checker |
| `PMC_POWER_RESET_STRESS` | Reset and gating pressure | Toggle reset, clock-gate-like enables, low-power entry/exit, and flush while events are active | Counter state and event qualification follow reset/gating policy and recover cleanly | Reset/gating checker |

## Required Combinations

Every performance counter driver must include these cross-products when reachable:

- Counter type x access mode: `cycle`, `instret`, every implemented HPM counter, every event-select CSR, and every inhibit/control/overflow CSR across legal and illegal privilege modes.
- Event x pipeline outcome: commit, flush, replay, exception, interrupt, debug entry, context switch, and reset.
- Event density x resource state: no events, single event, all events, back-to-back events, alternating events, queue empty, queue full, queue almost-full, queue almost-empty, and pointer wrap.
- CSR operation x event timing: read before event, read on event, read after event, write before event, write on event, write after event, RMW on event, inhibit on event, and event-select change on event.
- Boundary x side effect: counter near wrap, overflow disabled/enabled, overflow interrupt masked/unmasked, simultaneous multi-counter overflow, and trap/debug priority.

## Completion Checklist

Before a performance monitor counter driver is complete:

- Every implemented counter, event selector, inhibit/control bit, overflow bit, privilege filter, virtualization filter, and event producer is inventoried with exact code evidence.
- Every architectural CSR claim cites `riscv-spec`/UDB or an upstream privileged specification source.
- Counter deltas are checked against a scoreboard model that accounts for valid/fire, commit, flush, replay, privilege filter, inhibit, event select timing, and overflow behavior.
- Stress tests include maximum concurrent events, long bursts, sparse bursts, CSR races, queue full/empty/almost states, reset, flush, replay, trap, interrupt, debug, and context switch.
- Illegal/reserved CSR and event encodings are tested or cited as unreachable by exact code evidence.
- If a counter is intentionally speculative or imprecise, the driver must state the code evidence and check the documented speculation/recovery policy instead of assuming architectural precision.
