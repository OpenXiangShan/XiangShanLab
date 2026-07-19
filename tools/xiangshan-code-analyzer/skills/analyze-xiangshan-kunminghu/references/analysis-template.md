# XiangShan Analysis Template

Use this as the compact output skeleton. Load specialized references only when the analyzed path needs them; do not duplicate their full checklists.

## 1. Scope and Evidence

```text
Branch/path:
Source commit:
Design Doc baseline: URL + commit/branch, or `not consulted`
XiangShan source baseline: URL + commit/branch
Comparison: no | base -> target
Files/modules read:
Theory/course/design docs read:
Effective instantiation path:
Subsystem:
Special paths covered: load/store, timing, exception, AIA/IOPMP/AXI, difftest, queue capacity
```

For every behavior claim, cite the exact source file and line range plus a short Chisel snippet. Evidence must cover instantiation, IO/connection, algorithm, state update, index/address calculation, pipeline register, queue/SRAM access, and downstream consumer. If exact lines are unavailable, label the claim unverified and name the next artifact to inspect.

| Topic | Commit | Source lines | Core code | What it proves |
| --- | --- | --- | --- | --- |
|  |  |  |  |  |

## 2. Branch Comparison

Use only when two branches/commits are requested.

| Item | Base | Target | Evidence |
| --- | --- | --- | --- |
| Branch/commit |  |  |  |
| Module/path |  |  |  |
| Instantiation/IO |  |  |  |
| Effective behavior |  |  |  |
| Compatibility risk |  |  |  |

For each semantic diff hunk, explain: owner, purpose, inputs, outputs, changed state/control/data path, one concrete changed scenario, and verification focus. Do not summarize a hunk as only “added” or “removed.”

## 3. Theory, Intent, and Effective Code

Separate these layers:

| Layer | Required content |
| --- | --- |
| Theory | Course concept or paper principle; avoid generic textbook-only explanation. |
| Design intent | Motivation, expected pipeline/resource behavior, and design-doc evidence. |
| Effective code | Instantiated/configured module, real signals, parameters, states, and behavior. |

Use `theory-code-mapping.md`, `xiangshanlab-course-map.md`, and `design-doc-map.md` as navigation references. Prefer effective code when theory and implementation differ.


When Design Doc content is used, also record both repository baselines and map every load-bearing claim before describing it as behavior:

| ID | Design Doc file/heading/figure | Atomic claim | XiangShan source:lines | Relationship | Status/discrepancy |
| --- | --- | --- | --- | --- | --- |
| D1 |  |  |  | instantiation / connection / state / algorithm / parameter | Verified / Partial / Not found / Version mismatch / Design-only |

Use `design-doc-code-trace.md` for the line-by-line mapping procedure. An unmapped Design Doc statement remains intent-only.

Add a `Design Doc Discrepancies` subsection listing every `Partially verified`, `Not found`, `Version mismatch`, and `Design-only/pseudocode` claim.

## 4. Module Contract: Who / Why / How / From / To

Answer these five questions for every major module, signal group, queue, table, FSM, and datapath:

- **Who:** owns, produces, updates, consumes, or clears it?
- **Why:** what hazard, bandwidth limit, ordering rule, protocol phase, or recovery need does it solve?
- **How:** what combinational, registered, pipelined, arbitrated, or stateful rule implements it?
- **From what:** exact upstream signal, field, parameter, or storage entry?
- **To what:** exact downstream consumer, state update, architectural effect, or recovery path?

## 5. Interfaces and Dataflow

Trace first:

- `IO`, bundles, `Decoupled`, `Valid`, `ready/valid/fire`, `<>`, `io.* :=`.
- Request/response, enqueue/dequeue, wakeup/writeback, redirect/flush/cancel/replay, exception/commit.
- AXI/TL/APB master/slave roles and channel direction when present.
- Payload movement through registers, queues, SRAM/data arrays, muxes, bypass, refill, writeback, and commit.

For every important edge, state payload, control qualifier, stall condition, and consumer. Use `algorithm-control-dataflow.md` for detailed arbitration/FSM/data-path rules.

## 6. Parameters, Indices, and Storage

For each parameterized width/count/depth, identify owner, value/source, affected vector/port/resource, and behavior when changed.

For each index, pointer, address, set/bank/way/beat, allocation slot, free-list slot, MSHR/PTW/replay entry, or `OHToUInt`/`PriorityMux` result, explain:

1. Source fields and bit slices.
2. Width/parameter and wrap behavior.
3. Allocation/free or replacement rule.
4. Consumer and downstream effect.
5. Same-cycle read/write or multi-request conflict.

For every storage structure cover `search/read`, `update`, `release`, and `replace`, including valid set/clear/hold, collision priority, flush/replay/redirect, and empty/full/almost-full behavior. Use `queue-buffer-capacity.md` for queue-specific derivation.

## 7. Algorithm, FSM, and Scenario

### Algorithm

| Algorithm | Owner | Inputs | State | Priority/selection | Output | Evidence |
| --- | --- | --- | --- | --- | --- | --- |
|  |  |  |  |  |  |  |

Explain reset/initialization, first transaction, normal cases, ties, simultaneous requesters, invalid/empty/full, miss/hit, replay, redirect, exception, commit, and state evolution.

### FSM or implicit lifecycle

| State/status | Meaning | Entry | Exit/hold | Outputs | Block/recovery | Evidence |
| --- | --- | --- | --- | --- | --- | --- |
|  |  |  |  |  |  |  |

### Scenario matrix

| Scenario | Trigger | Resource/requesters | Winner/loser | State update | Retry/redirect/replay | Consumer | Evidence |
| --- | --- | --- | --- | --- | --- | --- | --- |
| replay |  |  |  |  |  |  |  |
| redirect/flush |  |  |  |  |  |  |  |
| conflict/contention |  |  |  |  |  |  |  |
| empty/full |  |  |  |  |  |  |  |
| simultaneous valid |  |  |  |  |  |  |  |

If a requested scenario is absent, state `searched, not present` and list the files/signals checked.

## 8. Timing and Pipeline

Use `instruction-latency-throughput.md` for timing analysis. Always state start/end events and distinguish fixed, parameter-derived, best-case, variable/miss-dependent, and unknown timing.

| Path/class | Start | End | Fixed/best-case | Variable contributors | Initiation interval/bottleneck | Evidence |
| --- | --- | --- | --- | --- | --- | --- |
|  |  |  |  |  |  |  |

For every effective pipeline stage, state input, work, registers/queues, valid/ready/fire, output, stall/flush/replay, and source lines. For Frontend top-level output, include `F0/F1/F2/F3` when present; for Backend use actual Decode/Rename/Dispatch/Issue/Execute/Writeback/Commit boundaries. Use `frontend.md` and `backend.md` for subsystem-specific stage rules.

## 9. Exceptions, Privilege, and Architectural Visibility

Trace exact priority and propagation for page/access/guest faults, misalignment, PMP/PMA/PBMT/MMIO, debug/trigger, interrupts, CSR/fence, redirect, and commit. State whether each signal is speculative, commit-visible, trap-visible, or debug/cache-state-only.

Load `exception-debug-privilege.md` for general rules, `aia-iopmp-axi.md` for AIA/IOPMP/AXI, and `difftest.md` for architectural-state boundaries.

## 10. Predictor, Memory, and Special Paths

Load only applicable references:

- Predictors: `predictor-papers.md`, `frontend.md`.
- Memory/cache/uncache/MMIO/XSCache: `mem-cache.md`, `cross-boundary-analysis.md`, `xscache.md`.
- Load/store/AMO/LR/SC/prefetch/fence/CBO taxonomy: `load-store-instruction-taxonomy.md`.
- Queue/buffer/ROB/FTQ/LSQ/free-list capacity: `queue-buffer-capacity.md`.

Keep prediction lookup, response, update/training, and recovery separate. Keep speculative memory state separate from commit-visible effects.

## 11. Diagrams

Load `diagrams.md` before drawing. A nontrivial module normally needs:

1. Data path (`flowchart LR`).
2. Module interface (`flowchart LR`).
3. FSM/status lifecycle (`stateDiagram-v2`) when useful.
4. Handshake timing (`waveform-draw`) with strict JSON and `clk` first.

For top-level/full-chain output, add two separate Mermaid diagrams:

- `Top-Level Module Connectivity`: bundled interfaces; no more than three edges between a module pair; split crowded graphs into subgraphs.
- `Frontend/Backend Pipeline Stages`: source-proven Frontend `F0/F1/F2/F3` and actual Backend stages.

Use real signal names. Put source links in prose, not inside Mermaid nodes or WaveDrom JSON. Preview waveforms with the configured VS Code Markdown Preview extension.

## 12. Verification and Cross-Boundary Checks

Every generated document includes `验证特别注意` with module-specific verification ID, invariant, stimulus, expected observation, checker/coverage, and source evidence. Load `verification-special-attention.md`.

Every address/instruction-stream path includes `跨边界代码解析` for reachable:

- Virtual-page crossing: split translation, permission, context, exception, and recovery.
- Cache-line crossing: split line/set/beat requests, hit/miss/MSHR/refill, merge/order, and assembly.
- MMIO/uncache crossing: memory type, uncache entry, handshake, ordering/commit, resend, exception, and cancel.

Load `cross-boundary-analysis.md` for the required table and evidence checklist.

## 13. Dynamic Operation and Summary

Describe one normal path and one blocked/recovery path from input to output, including speculative state, resource occupancy, backpressure, replay/redirect/exception, and commit effects. End with:

- Confirmed behavior and source commit.
- Key parameters/resources/latency/throughput.
- Main conflict, boundary, exception, and recovery risks.
- Verification focus and open questions requiring elaboration, generated Verilog, waveform, or test evidence.
