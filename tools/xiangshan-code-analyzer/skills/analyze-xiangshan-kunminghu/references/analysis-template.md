# XiangShan Module Analysis Template

Use this template when producing the final explanation.

## 1. Scope

- Branch/path analyzed:
- Files read:
- Theory/course docs read:
- Design docs read:
- Main modules/classes:
- Effective instantiation path:
- Subsystem context:
- Load/store instruction categories covered:
- Exception/interrupt/debug/privilege paths covered:
- Queues/buffers capacity logic covered:

## 2. Theory-to-Code Mapping

For key concepts from XiangShanLab superscalar/out-of-order material:

| Theory concept | Course source | Code artifact | Concrete signal/state | How XiangShan implements it | Difference from textbook model |
| --- | --- | --- | --- | --- | --- |

Explain how the requested module embodies pipeline, superscalar, dependency, hazard, speculation, wakeup/select, rename, bypass, ROB, or memory-ordering concepts.

## 3. Design Intent vs Effective Code

Separate:
- Design-doc/course intent:
- Effective source-code behavior:
- Differences or missing documentation:

## 4. Microarchitecture Parameters

For every important parameter:

| Parameter | Defined in | Value/expression | Enters through | Affects what |
| --- | --- | --- | --- | --- |

Include parameters in every `who` explanation when they control updater count, port count, storage depth, width, optional structures, or algorithm behavior.

## 5. Load/Store Instruction Category Coverage

For memory/cache modules, include:

| Category | Examples | Decode/FU marker | mem modules | cache/MMU modules | Special behavior |
| --- | --- | --- | --- | --- | --- |

Cover all load/store-class categories that can reach the requested module: scalar integer, floating-point, vector, AMO/LR/SC, prefetch, fence, CBO/CMO, misaligned, and uncache/MMIO where relevant.

## 6. Boundary and Interfaces

For every important IO bundle or channel:

| Signal or bundle | Direction | From what | To what | Why it exists |
| --- | --- | --- | --- | --- |

Explain ready/valid behavior, arbitration, backpressure, cancellation, redirects, flushes, exceptions, and replay semantics.

## 7. Why This Module Exists

Answer in architectural terms and code terms:

- What pipeline pressure or correctness hazard would exist without it?
- What timing, bandwidth, ordering, or speculation boundary does it create?
- Which neighboring modules rely on its output?

## 8. Index and Address Calculation

For every computed index, address, pointer, bank selector, set selector, way selector, queue entry selector, or one-hot/priority-derived index:

| Index/address | Definition site | Inputs | Calculation | Width/range | Reset/first-use behavior | Consumer/effect |
| --- | --- | --- | --- | --- | --- | --- |

Explain bit slicing, concatenation, truncation/extension, `OHToUInt`, `UIntToOH`, `PriorityMux`, `PopCount` rank, modulo/wraparound, pointer compare logic, and bank/set/way/entry selection. State what happens on reset or the first valid access if the index depends on registered state.

## 9. Algorithms

For each nontrivial algorithm:

| Algorithm | Owner | Inputs/requesters | Initial state | First transaction behavior | Simultaneous-request scenario | Cases/priority/arbitration | Losing request behavior | State update | Output/effect |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |

Explain the operating principle, initialization/reset behavior, first valid/request behavior, every major case or branch, tie handling, simultaneous requester behavior, empty/full/invalid behavior, flush/redirect/replay/miss/exception behavior, commit interaction, and how state evolves after each case. For every arbiter, selector, mux-priority network, grant vector, ready fanout, or request scheduler, enumerate all requesters and state request qualification, priority/fairness rule, grant encoding, selected data, ready/backpressure returned to each requester, losing request behavior, and state updates caused by the grant.

## 10. Exception / Interrupt / Debug / Privilege

When relevant, include:

| Class | Producer | Metadata/signals | Priority rule | Consumer | Architectural effect |
| --- | --- | --- | --- | --- | --- |

Trace trap/debug/privilege metadata from producer to consumer and explain priority versus replay, redirect, commit, and flush.

## 11. State Machines

For explicit or implicit FSMs/state-like entries:

| State | Meaning | Entry condition | Exit condition | Outputs/actions | Cancel/flush behavior |
| --- | --- | --- | --- | --- | --- |

If no FSM exists, state that and identify the closest state-like valid/status structure.

## 12. Control Path

Trace mux selects, valid/ready/fire, arbitration, FSM transitions, pipeline stage controls, stalls, cancels, redirects, replay, exceptions, commit, and update conditions.

For key control signals:
- Who produces it?
- Which parameter controls its width/count/existence?
- Why is it needed?
- How is it computed?
- From what source?
- To what consumer/effect?

## 13. Pipeline Signals

For pipelined modules, include:

| Stage | Valid/control | Payload registers | Work done | Stall/flush/replay behavior | Output to |
| --- | --- | --- | --- | --- | --- |

If no pipeline exists, state that and explain the closest combinational/registered boundary.

## 14. Data Path

Trace payload movement:
- Input fields:
- Pipeline/register/queue/array stages:
- Muxes/transforms:
- Output fields:
- Flush/cancel/replay/miss behavior:

## 15. Mermaid Diagrams

Include a key data-path diagram and module-interface diagram when the module is nontrivial. Use `references/diagrams.md`.

Data-path diagram:

```mermaid
flowchart LR
```

Module-interface diagram:

```mermaid
flowchart LR
```

FSM diagram when useful:

```mermaid
stateDiagram-v2
```

## 16. Storage Structures

For each table, queue, buffer, array, register group, and pointer:

| Structure | Who owns/updates | Reset/initial value | Update timing/condition | Update index calculation | Updated content | Release timing/condition | Release index calculation | Released/replaced/cleared content | Search/read/probe timing | Search/read/probe index calculation | Search result/content | Read/write port conflict scenarios | Valid set/clear/hold | Collision priority | Empty/full/almost-full | Flush/cancel/replay effect | Purpose |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |

Call out valid bits, allocation/free logic, pointers, index calculations, capacity parameters, empty/full/almost-full logic, ready/backpressure targets, snapshots, data/tag arrays, replay/miss/exception metadata, and replacement state. For every storage structure, describe behavior through `update`, `release`, `replace`, and `search/read/probe` even when the code calls them enqueue/dequeue/read/write/allocate/free/lookup/match. For each operation, state the exact timing, enable/fire condition, index/address/pointer calculation, payload fields updated/released/replaced/searched, valid-bit set/clear/hold behavior, same-cycle update/release/replace/search collision priority, and downstream consumers. For every read/write port conflict, explicitly analyze same-cycle read/write same index, multiple writes same index, multiple reads with limited ports, RAW/WAR/WAW behavior, bypass/forwarding/assert behavior, and whether the losing request wins, stalls, retries, replays, is masked, is dropped, or is illegal. For every valid/status bit, explicitly state when it is set, when it is cleared, when it holds its value, what flush/cancel/replay affects it, and what downstream logic observes it.

## 17. Critical Signal Deep Dives

Pick the signals that determine behavior, not every wire. For each:

- Definition site:
- Producer:
- Consumers:
- Meaning:
- Timing or handshake condition:
- Parameter dependence:
- Why the design needs this signal:

## 18. Dynamic Operation

Describe:
- Normal path: request enters, state is allocated/read, result leaves.
- Speculative path: what state/request is created before commit/final permission/final coherence response, how it is validated, canceled, replayed, merged, or dropped.
- Recovery path: redirect, exception, replay, miss, flush, cancel, coherence retry, or invalidation.

Use the philosophy axis explicitly:
- Who updates this?
- Why is it needed?
- How does it work?
- From what signal/source?
- To what consumer/effect?

## 19. Summary

End with a compact mental model:
- One sentence for the module responsibility.
- One sentence for its main state.
- One sentence for the key control path.
- One sentence for the key data path.
- One sentence for the most important corner case.
