# Theory-to-Code Mapping Guide

Use this file to map superscalar/out-of-order microarchitecture concepts from XiangShanLab courses onto effective XiangShan Kunminghu source code.

## Required Mapping Discipline

For every important theory term, provide a code mapping table:

| Theory concept | Course source | Code artifact | Concrete signal/state | How XiangShan implements it | Difference from textbook model |
| --- | --- | --- | --- | --- | --- |

A useful answer must connect theory to at least one of:
- Module/class name
- IO bundle or Decoupled/Valid channel
- Microarchitecture parameter
- Queue/table/array/register state
- Control signal or mux select
- Data-path edge or Mermaid node
- Algorithm/FSM transition

## Core Concepts to Map

### Pipeline and Superscalar Width

Theory: pipelining raises throughput; superscalar/multi-issue tries to execute multiple independent instructions per cycle.

Map to code:
- `DecodeWidth`, `RenameWidth`, dispatch/enqueue widths, issue queue `numEnq`/`numDeq`, commit widths.
- Vectorized IO such as `Vec(width, ...)`, `MixedVec`, and generated port groups.
- Stage valid bits, stage registers, stall/flush logic.

Explain:
- Which width controls the analyzed module.
- Which parameter owner sets it.
- Which ready/valid path backpressures it.

### Structural Hazards

Theory: multiple operations compete for finite ports, queues, arrays, functional units, writeback buses, cache ports, or TLB ports.

Map to code:
- Arbiters, `PriorityMux`, `Mux1H`, port-count parameters, queue full/empty checks.
- FU busy tables, writeback arbiters, read-port arbiters, cache mainpipe arbiters.
- `ready` deassertion or `stall` signals.

Explain:
- The resource being contended.
- The arbitration/priority rule.
- The losing request behavior: stall, replay, retry, drop, or redirect.

### Data Hazards and Dependencies

Theory: RAW/WAR/WAW dependencies restrict scheduling; renaming removes false dependencies while wakeup/select tracks true dependencies.

Map to code:
- Rename tables, free lists, physical register IDs, busy table, source status, wakeup buses.
- Issue queue operand readiness and `srcStatus` structures.
- Load-store dependency tracking, load cancel, replay, forwarding.

Explain:
- Who produces dependency metadata.
- Who consumes it for scheduling or replay.
- How the code distinguishes readiness from data availability.

### Control Hazards and Speculation

Theory: branch/control speculation keeps frontend/backend busy; misprediction requires redirect and recovery.

Map to code:
- Predictor modules, FTQ, redirect generators, ROB exception/redirect priority, flush/cancel signals.
- Rename snapshots, ROB walk, frontend redirect IO.

Explain:
- What speculative state is allocated.
- What metadata enables recovery.
- Which signal kills wrong-path work.

### Tomasulo / Scoreboard / Dynamic Scheduling

Theory: scoreboard tracks resource/operand readiness; Tomasulo-style wakeup/select and renaming enable out-of-order execution.

Map to code:
- BusyTable, IssueQueue entries, wakeup queues, age/select logic, FU busy table, physical register file, ROB.
- Dispatch allocation into issue queues and ROB.

Explain:
- Which part resembles scoreboard readiness tracking.
- Which part resembles Tomasulo wakeup/select.
- Which part is XiangShan-specific and parameterized.

### Register Renaming and Physical Registers

Theory: rename maps architectural registers to physical registers to remove WAR/WAW and maintain precise state.

Map to code:
- RenameTable, FreeList, Snapshot, BusyTable, physical register params, old-pdest handling, commit freeing.
- Move elimination when relevant.

Explain:
- Allocation path, old mapping retention, commit release, redirect recovery.
- Parameterized register counts and index widths.

### Bypass, Forwarding, and RegCache

Theory: bypass/forwarding reduces producer-consumer latency; local caches or forwarding paths reduce register file pressure.

Map to code:
- BypassNetwork, DataPath source selection, regfile read/write, RegCache, wakeup data paths.
- `DataSource` selectors, `readBypass`, `readForward`, `readReg`, `readRegCache`, mux logic.

Explain:
- What data is forwarded versus read from storage.
- Which mux selects the source.
- What happens if the producer is canceled or replayed.

### ROB and Precise Commit

Theory: out-of-order execution needs in-order retirement to maintain precise exceptions and architectural state.

Map to code:
- ROB enqueue/dequeue, completion bits, exception metadata, commit width, redirect priority, CSR/trap interaction.
- Free-list commit interface and store commit release.

Explain:
- Who marks done.
- Who decides commit.
- How exceptions/redirects are prioritized.

### Memory Ordering and LSQ

Theory: loads/stores must handle address dependencies, forwarding, ordering, replay, and exceptions while still allowing speculation.

Map to code:
- LoadQueue, StoreQueue, LoadQueueRAW/RAR, LoadQueueReplay, StoreQueueData, SBuffer, DCache/TLB request paths.
- Load cancel, store-to-load forwarding, violation detection, replay conditions.

Explain:
- Which dependencies are speculative.
- Which effects wait until commit.
- What triggers replay or cancel.

## Output Requirements

When using theory mapping, add a section titled `Theory-to-Code Mapping` before detailed signal analysis.

Do not stop at saying "this is like Tomasulo" or "this solves data hazards". Always name the exact code artifacts and signals that realize the concept.
