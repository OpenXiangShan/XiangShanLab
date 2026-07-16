# XiangShan Module Analysis Template

Use this template when producing the final explanation.

## 1. Scope

- Branch/path analyzed:
- Source commit analyzed:
- Comparison mode: yes/no
- Base branch/commit, if comparison:
- Target branch/commit, if comparison:
- Files read:
- Theory/course docs read:
- Design docs read:
- Main modules/classes:
- Effective instantiation path:
- Subsystem context:
- Load/store instruction categories covered:
- Exception/interrupt/debug/privilege paths covered:
- chiselAIA/chiselIOPMP/AXI paths covered:
- Difftest signal/architectural-state paths covered:
- Queues/buffers capacity logic covered:

## 2. Mandatory Source Evidence

For every algorithm, port, inter-module connection, and datapath segment discussed, include exact Chisel line numbers and a short core code snippet from the analyzed commit. Use this evidence table throughout the analysis, not only at the end:

| Topic | Commit | File:line(s) | Chisel core code | What this code proves |
| --- | --- | --- | --- | --- |

Rules:
- `Commit` is the source commit used for the analysis, such as `git rev-parse HEAD` for local source or the remote commit SHA for GitHub source. In comparison mode, provide one evidence row for the base side and one evidence row for the target side when both sides are needed to prove a changed behavior.
- `File:line(s)` must point to the exact source lines for algorithms, IO definitions, `io.* :=` connections, `<>` bulk connections, module instantiations, mux/select logic, pipeline registers, queues, SRAM/table access, and datapath transforms.
- Keep snippets short: include only the core Chisel lines needed to prove the claim.
- If line numbers cannot be obtained, state why and do not present the claim as fully verified.


## 2A. Branch Comparison Summary

Use this section only for two-branch module comparison.

| Item | Base | Target | Evidence |
| --- | --- | --- | --- |
| Branch/commit |  |  |  |
| Module path(s) |  |  |  |
| Effective instantiation path |  |  |  |
| Public interface status |  |  |  |
| Main behavioral status |  |  |  |

Selected diff command or method:

```bash
```

## 2B. Branch Difference Matrix

Use this section only for two-branch module comparison. Include both sides of source evidence for every behavior-changing claim.

| Change | Category | Base source lines and core code | Target source lines and core code | Current-skill code analysis | Behavioral impact | Compatibility risk | Verification focus |
| --- | --- | --- | --- | --- | --- | --- | --- |

Classify changes as interface/IO, parameter, instantiation, control path, data path, FSM/state, storage, index/address, arbitration/priority, exception/interrupt/debug/privilege, AXI/TL/APB protocol, memory/cache pipeline, predictor algorithm, or mechanical/no effective behavior. The `Current-skill code analysis` cell must use the same principles as normal module analysis: who owns/updates the changed logic, why it exists, how it works, from what signal/source it is derived, to what consumer/effect it flows, and what changed in algorithm/control path/data path/storage/FSM/index/handshake behavior.


## 2C. Diff Hunk Code-Analysis Checklist

Use this section only for two-branch module comparison. For each semantic diff hunk, answer these items before summarizing impact:

- Enclosing context: module/class/function, pipeline stage, storage structure, FSM, arbiter, IO bundle, or algorithm that contains the diff.
- Base behavior: who owns or updates the old logic, why it existed, how it worked, from what source it was derived, and to what consumer/effect it flowed.
- Target behavior: who owns or updates the new logic, why it exists, how it works, from what source it is derived, and to what consumer/effect it flows.
- Changed scenario: one concrete transaction, request conflict, stall, replay, redirect, miss, exception, interrupt, flush, commit, or first-valid/reset case where base and target behave differently.
- Analysis axes touched: interface/IO, parameter, control path, data path, FSM/state lifecycle, storage update/release/replace/search, valid set/clear/hold, index/address calculation, arbitration/priority, protocol channel, memory/cache stage, predictor lookup/update/recovery, or exception/debug/privilege path.
- Verification focus: assertion, unit test, elaboration check, waveform signal group, directed stimulus, or regression class that should prove the changed behavior.

Do not leave a semantic diff at the level of "code was added/removed". Reconstruct the effective microarchitecture behavior on both branches and compare it using the same source-evidence standard as the rest of this template.

## 3. Theory-to-Code Mapping

For key concepts from XiangShanLab superscalar/out-of-order material:

| Theory concept | Course source | Code artifact | Concrete signal/state | How XiangShan implements it | Difference from textbook model |
| --- | --- | --- | --- | --- | --- |

Explain how the requested module embodies pipeline, superscalar, dependency, hazard, speculation, wakeup/select, rename, bypass, ROB, or memory-ordering concepts.

## 4. Design Intent vs Effective Code

Separate:
- Design-doc/course intent:
- Effective source-code behavior:
- Differences or missing documentation:

## 5. Microarchitecture Parameters

For every important parameter:

| Parameter | Defined in | Value/expression | Enters through | Affects what |
| --- | --- | --- | --- | --- |

Include parameters in every `who` explanation when they control updater count, port count, storage depth, width, optional structures, or algorithm behavior.

## 6. Load/Store Instruction Category Coverage

For memory/cache modules, include:

| Category | Examples | Decode/FU marker | mem modules | cache/MMU modules | Special behavior |
| --- | --- | --- | --- | --- | --- |

Cover all load/store-class categories that can reach the requested module: scalar integer, floating-point, vector, AMO/LR/SC, prefetch, fence, CBO/CMO, misaligned, and uncache/MMIO where relevant.

## 7. Boundary and Interfaces

For every important IO bundle or channel:

| Signal or bundle | Direction | From what | To what | Source lines | Core code | Why it exists |
| --- | --- | --- | --- | --- | --- | --- |

Explain ready/valid behavior, AXI master/slave role when present, AW/W/B/AR/R channel direction, arbitration, backpressure, cancellation, redirects, flushes, exceptions, and replay semantics.

## 8. Why This Module Exists

Answer in architectural terms and code terms:

- What pipeline pressure or correctness hazard would exist without it?
- What timing, bandwidth, ordering, or speculation boundary does it create?
- Which neighboring modules rely on its output?

## 9. Index and Address Calculation

For every computed index, address, pointer, bank selector, set selector, way selector, queue entry selector, or one-hot/priority-derived index:

| Index/address | Definition site | Inputs | Calculation | Width/range | Reset/first-use behavior | Consumer/effect |
| --- | --- | --- | --- | --- | --- | --- |

Explain bit slicing, concatenation, truncation/extension, `OHToUInt`, `UIntToOH`, `PriorityMux`, `PopCount` rank, modulo/wraparound, pointer compare logic, and bank/set/way/entry selection. State what happens on reset or the first valid access if the index depends on registered state.

## 10. Algorithms

For each nontrivial algorithm:

| Algorithm | Owner | Source lines | Core code | Inputs/requesters | Initial state | First transaction behavior | Simultaneous-request scenario | Cases/priority/arbitration | Losing request behavior | State update | Output/effect |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |

Explain the operating principle, initialization/reset behavior, first valid/request behavior, every major case or branch, tie handling, simultaneous requester behavior, empty/full/invalid behavior, flush/redirect/replay/miss/exception behavior, commit interaction, and how state evolves after each case. For every arbiter, selector, mux-priority network, grant vector, ready fanout, or request scheduler, enumerate all requesters and state request qualification, priority/fairness rule, grant encoding, selected data, ready/backpressure returned to each requester, losing request behavior, and state updates caused by the grant.

## 10A. Replay Redirect Conflict Contention Resource Scenarios

Use this section for every nontrivial module and every behavior-changing diff. If a category is not present, state `searched, not present` with the files/signals checked.

| Scenario class | Trigger condition | Involved resource/requesters | Source lines | Core code | Priority/arbitration rule | Winner behavior | Loser/blocked behavior | State update | Retry/redirect/replay path | Downstream effect |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |

Required scenario classes: replay, redirect, structural conflict, data/ordering conflict, port/bank/MSHR/queue contention, resource empty, resource full/almost-full, and simultaneous valid requests. Explain concrete transaction examples using real signal names; avoid generic statements such as "backpressure happens" without naming the resource and consumer.

## 11. Exception / Interrupt / Debug / Privilege

When relevant, include:

| Class | Producer | Metadata/signals | Priority rule | Consumer | Architectural effect |
| --- | --- | --- | --- | --- | --- |

Trace trap/debug/privilege metadata from producer to consumer and explain priority versus replay, redirect, commit, and flush.

## 12. Predictor Paper Context

For predictor modules, include:

| Predictor | Paper/source searched via paper-search-agent-mcp | Algorithm principle | XiangShan source lines | XiangShan core code | Lookup state/path | Update/training path | Recovery path | XiangShan code mapping | Scenario example | Difference/uncertainty |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |

State the paper title/identifier when available, then separate the paper algorithm from the effective XiangShan implementation. Explain how lookup, update/training, allocation/replacement, and redirect recovery map to real code signals and tables, with exact Chisel source lines and short core code snippets from the analyzed commit.

## 13. chiselAIA / chiselIOPMP / AXI Bus

When relevant, include:

| Item | Commit | Source lines | Core Chisel code | Master/slave role | Protocol/control behavior | Why it exists | Scenario |
| --- | --- | --- | --- | --- | --- | --- | --- |

For chiselAIA, cover APLIC/IMSIC boundary, interrupt source, MSI/register access, CSR privilege/virtualization interaction, priority, pending/enable/claim/complete or delivery behavior. For chiselIOPMP, cover protected path, config port, permission inputs, match algorithm, allow/deny response, bypass, and access-fault/error behavior. For AXI, cover master and slave roles plus AW/W/B/AR/R channel signals: `valid`, `ready`, `addr`, `id`, `len`, `size`, `burst`, `data`, `strb`, `last`, `resp`, and backpressure.

## 14. Difftest Signal Coverage

Use this section when difftest signals, architectural-state dumps, cache state, memory address events, exception/interrupt events, or per-queue state are relevant. Read `references/difftest.md` first.

| Difftest signal/event | State class | Producer lines | Enable/timing | Payload fields | From what | To what/reference meaning | Speculative or committed | Corner case |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |

State class must be one of: RISC-V architectural state, reference-model-visible difftest event, or microarchitectural debug state. Cover int/fp/vector registers, CSR, exception/trap, interrupt, memory virtual/physical address, cache state, and requested queue/buffer state. Explain uncovered or ambiguous signals explicitly.

## 15. State Machines

For explicit or implicit FSMs/state-like entries:

| State | Meaning | Why it exists | Example scenario | Entry condition | Exit condition | Outputs/actions | Cancel/flush behavior |
| --- | --- | --- | --- | --- | --- | --- | --- |

If no FSM exists, state that and identify the closest state-like valid/status structure. For every state or state-like value, explain why it exists and give a concrete transaction scenario that sets, holds, or clears it.

## 16. Control Path

Trace mux selects, valid/ready/fire, arbitration, FSM transitions, pipeline stage controls, stalls, cancels, redirects, replay, exceptions, commit, and update conditions.

For key control signals:
- Who produces it?
- Which parameter controls its width/count/existence?
- Why is it needed? Name the hazard, ordering rule, resource conflict, protocol phase, or recovery behavior it handles.
- Example scenario: describe a concrete request/response/stall/replay/flush/miss/conflict case where the signal changes behavior.
- How is it computed?
- From what source?
- To what consumer/effect?

## 17. Pipeline Signals

For pipelined modules, include:

| Stage | Source lines | Core code | Valid/control | Payload registers | Work done | Index/allocation computed here | FSM/valid state effect | Stall/flush/replay behavior | Output to |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |

If no pipeline exists, state that and explain the closest combinational/registered boundary. For `mem`, `cache`, and XSCache content, this section is mandatory: describe what each stage specifically does, including TLB/tag/meta/data access, set/bank/way/beat selection, miss/replay/refill/writeback handling, response/writeback timing, and commit-visible effects when present.

## 18. Data Path

Trace payload movement. Every datapath edge/transform must include Chisel source line numbers and core code from the analyzed commit:
- Input fields:
- Pipeline/register/queue/array stages:
- Muxes/transforms:
- Output fields:
- Flush/cancel/replay/miss behavior:

## 19. Diagrams

Include a key data-path diagram, module-interface diagram, and handshake timing diagram when the module is nontrivial. Use `references/diagrams.md`.

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

Handshake timing diagram:

```waveform-draw
{
  "signal": [
    { "name": "clk", "wave": "p......" },
    { "name": "valid", "wave": "01..0.." },
    { "name": "ready", "wave": "0.10..." },
    { "name": "fire", "wave": "0..10.." },
    { "name": "bits", "wave": "x=..x..", "data": ["payload"] },
    { "name": "flush/cancel", "wave": "0......" }
  ],
  "config": { "hscale": 1 }
}
```

For each important handshake or valid-like timing path, explain what `fire` means in code, when payload must remain stable, what signal creates backpressure, and what flush/cancel/replay does to an already accepted transfer. If the module has no `ready`, draw the closest valid/enable timing and state that there is no Decoupled backpressure.


## 20. Storage Structures

For each table, queue, buffer, array, register group, and pointer:

| Structure | Who owns/updates | Reset/initial value | Update timing/condition | Update index calculation | Updated content | Release timing/condition | Release index calculation | Released/replaced/cleared content | Search/read/probe timing | Search/read/probe index calculation | Search result/content | Read/write port conflict scenarios | Valid set/clear/hold | Collision priority | Empty/full/almost-full | Flush/cancel/replay effect | Purpose |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |

Call out valid bits, allocation/free logic, pointers, index calculations, capacity parameters, empty/full/almost-full logic, ready/backpressure targets, snapshots, data/tag arrays, replay/miss/exception metadata, and replacement state. For every storage structure, describe behavior through `update`, `release`, `replace`, and `search/read/probe` even when the code calls them enqueue/dequeue/read/write/allocate/free/lookup/match. For each operation, state the exact timing, enable/fire condition, index/address/pointer calculation, payload fields updated/released/replaced/searched, valid-bit set/clear/hold behavior, same-cycle update/release/replace/search collision priority, and downstream consumers. For every read/write port conflict, explicitly analyze same-cycle read/write same index, multiple writes same index, multiple reads with limited ports, RAW/WAR/WAW behavior, bypass/forwarding/assert behavior, and whether the losing request wins, stalls, retries, replays, is masked, is dropped, or is illegal. For every valid/status bit, explicitly state when it is set, when it is cleared, when it holds its value, what flush/cancel/replay affects it, and what downstream logic observes it.

## 21. Critical Signal Deep Dives

Pick the signals that determine behavior, not every wire. For each:

- Definition site:
- Producer:
- Consumers:
- Meaning:
- Timing or handshake condition:
- Parameter dependence:
- Why the design needs this signal:
- Example scenario where this signal matters:

## 22. Dynamic Operation

Describe:
- Normal path: request enters, state is allocated/read, result leaves.
- Speculative path: what state/request is created before commit/final permission/final coherence response, how it is validated, canceled, replayed, merged, or dropped.
- Recovery path: redirect, exception, replay, miss, flush, cancel, coherence retry, or invalidation.
- Conflict/contention path: simultaneous requesters, limited port/bank/entry/MSHR/queue resources, priority or fairness decision, loser behavior, and retry or backpressure path.
- Empty/full path: exact empty/full/almost-full signal, who observes it, what state holds or clears, and what transaction resumes progress.

Use the philosophy axis explicitly:
- Who updates this?
- Why is it needed?
- How does it work?
- From what signal/source?
- To what consumer/effect?

## 23. Summary

For branch comparison, start with a compact verdict: unchanged behavior, interface-compatible behavior change, interface-breaking change, or unclear without elaboration/tests. Then list the semantic diff count, mechanical diff count, main changed analysis axes, migration requirements, and verification priorities.

End with a compact mental model:
- One sentence for the module responsibility.
- One sentence for its main state.
- One sentence for the key control path.
- One sentence for the key data path.
- One sentence for the most important corner case.
