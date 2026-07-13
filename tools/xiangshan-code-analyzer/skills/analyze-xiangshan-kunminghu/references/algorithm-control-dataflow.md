# Algorithm, FSM, Control Path, and Data Path Analysis

Use this file whenever the user asks for detailed algorithms or when a module contains nontrivial arbitration, replacement, replay, prediction, ordering, or state-machine behavior.

## Effective Code First

Define "effective code" as code that is instantiated, connected, and reachable in the selected branch/configuration.

To identify it:
- Start from a top module or parent module instantiation path.
- Follow `Module(new ...)`, `LazyModule`, `io.* :=`, `<>`, `Flipped`, `Decoupled`, `Valid`, and parameter-conditioned `OptionWrapper`/`Option.when`/`if` blocks.
- Check whether a module is only a helper, debug-only structure, unused alternative, fake module, or test artifact.
- Record parameter conditions that enable/disable code.

## Algorithm Analysis

For every algorithm, provide:

| Algorithm | Code owner | Inputs | State used | Rule/priority | Output/effect |
| --- | --- | --- | --- | --- | --- |

Then explain in prose:
- What problem the algorithm solves.
- The exact priority order or selection rule.
- Whether it is combinational, registered, pipelined, speculative, replayable, or commit-time.
- How it handles ties, invalid entries, full/empty conditions, mispredicts, misses, exceptions, and flushes.
- Which microarchitecture parameters change the algorithm width or behavior.

Common XiangShan algorithms to look for:
- Age selection and oldest-first issue/replacement.
- Free-list allocation and deallocation.
- Rename snapshot and redirect recovery.
- Wakeup/select and bypass/forwarding selection.
- Branch predictor table lookup/update and history recovery.
- Cache tag match, replacement, miss merging, refill, writeback, and probe handling.
- LSQ forwarding, RAW/RAR violation detection, replay selection, and commit release.
- TLB lookup, permission checking, PTW miss handling, and page-cache replacement.

## State Machine Analysis

Detect FSMs by searching for:
- `Enum`, `ChiselEnum`, `s_`, `state`, `nextState`, `switch`, `is`, `when(state === ...)`
- Ready/valid loops that behave like implicit FSMs even without explicit `Enum`
- Queue-entry valid/status fields that encode state

For every FSM or state-like structure, provide:

| State | Meaning | Entry condition | Exit condition | Outputs/actions | Backpressure/cancel behavior |
| --- | --- | --- | --- | --- | --- |

Also explain:
- Reset state and initialization.
- State transitions caused by redirect, flush, cancel, replay, miss, grant/refill, commit, or exception.
- Whether outputs are Mealy-style from current inputs or Moore-style from registered state.
- Which parameter controls state count, queue depth, outstanding count, or timeout.

## Control Path Analysis

Control path means decisions that move, stall, cancel, select, replay, redirect, train, or commit data.

Trace:
- `valid`, `ready`, `fire`, `wen`, `ren`, `enq`, `deq`, `flush`, `cancel`, `redirect`, `replay`, `exception`, `commit`, `grant`, `miss`, `hit`, `stall`, `block`, `select`, `arb`, `priority`, `mask`.
- Arbitration modules and `PriorityMux`, `Mux1H`, one-hot masks, age comparisons, and grant vectors.
- Parameter-generated port counts and vector widths.

For each important control signal, answer:
- Who produces it?
- Which parameter controls its width/count/existence?
- Why is it needed?
- How is it computed?
- From what upstream condition?
- To what downstream module or state update?

## Data Path Analysis

Data path means payload movement and transformation.

Trace:
- Input payload bundles and fields.
- Register stages and queue/data-array movement.
- Muxes and data-source selection.
- Tag/index/address/data/mask transformations.
- Bypass/forward/refill/writeback paths.
- Data width, physical-register width, cache-line width, beat width, mask width, and parameterized vector widths.

For each important data path, answer:
- What payload enters?
- Which stage/register/array holds it?
- What transform is applied?
- Which control signal qualifies it?
- Where does it leave?
- What happens on flush, cancel, replay, or miss?

## Output Discipline

Do not describe every assignment. Prioritize behavior-changing logic:
- State updates
- Arbitration/priority
- Ready/valid conditions
- Replay/flush/redirect/cancel rules
- Parameterized structural generation
- Interface conversions
- Payload muxes and array accesses

## Control Signal Focus

When explaining control signals, prioritize these categories:

- Mux controls: `Mux`, `Mux1H`, `PriorityMux`, one-hot vectors, select masks, source selectors, way selectors, issue selectors, writeback selectors.
- Handshake controls: `valid`, `ready`, `fire`, enqueue/dequeue fire, request/response valid, grant/accept.
- Arbitration controls: `Arbiter`, `RRArbiter`, priority encoders, grant vectors, age-based select, oldest-first select.
- FSM controls: state registers, next-state logic, transition conditions, outputs qualified by state.
- Pipeline controls: stage valid bits, stage registers, stall/flush/cancel/replay, stage-to-stage enable, bubble injection, redirect kill, load cancel.

For each important control signal, name the controlling condition and the controlled effect. Example structure:

| Control signal | Kind | Producer | Selects/enables/stalls what | Key condition | Parameter dependence |
| --- | --- | --- | --- | --- | --- |

## Pipeline Signal Focus

For pipelined modules, produce a stage table:

| Stage | Valid/control | Payload registers | Work done | Stall/flush/replay behavior | Output to |
| --- | --- | --- | --- | --- | --- |

Trace key signals across stages, especially:
- request valid/ready and stage valid bits
- address/tag/data/uop/metadata pipeline registers
- hit/miss/exception/replay/cancel signals
- write enables and array read enables
- response valid and writeback valid
