---
name: analyze-xiangshan-kunminghu
description: Analyze OpenXiangShan XiangShan Kunminghu microarchitecture source code, design documents, and XiangShanLab superscalar/out-of-order microarchitecture course concepts, and XSCache cache-subsystem modules, especially src/main/scala/xiangshan on kunminghu-v2 or a user-specified branch. Use when the user asks for code walkthroughs, theory-to-code mapping, module explanations, signal tracing, algorithm analysis, state-machine analysis, control-path/data-path analysis, Mermaid diagram generation, module-interface diagram generation, storage-structure analysis, exception/interrupt/debug/privilege analysis, queue/buffer full-empty analysis, or pipeline/instruction-flow analysis for XiangShan backend, frontend, cache, or mem modules, including decode/rename/dispatch/issue/execute/writeback/commit, icache/itlb/ftq/ibuffer/branch predictors, and detailed load/store-class memory instruction flows across mem and cache directories, including scalar, floating-point, vector, AMO/LR/SC, prefetch, fence, and CBO flows.
---

# Analyze XiangShan Kunminghu

## Objective

Use this skill to produce code-grounded explanations of XiangShan Kunminghu modules. Default to `OpenXiangShan/XiangShan.git` branch `kunminghu-v2` unless the user explicitly asks for another branch or path. If the prompt contains a conflicting branch/path, state the branch being analyzed before explaining.

Primary source roots:
- XiangShan source: `src/main/scala/xiangshan`
- XiangShan Design Doc: `https://github.com/OpenXiangShan/XiangShan-Design-Doc.git`
- XiangShanLab course repo: `https://github.com/OpenXiangShan/XiangShanLab.git`
- XSCache repo: `https://github.com/OpenXiangShan/XSCache.git`
- Course background: XiangShanLab course system 4 implementation docs, especially superscalar/out-of-order basics and XiangShan high-performance out-of-order pipeline docs, plus course system 5 dynamic instruction execution docs
- Required analysis style: answer every module through `who`, `why`, `how`, `from what`, and `to what`

## Workflow

1. Locate the requested module in the checked-out XiangShan tree or fetch/inspect the requested GitHub branch. Prefer local source if available; otherwise use GitHub.
2. Locate matching foundational theory material before code analysis. Read `references/xiangshanlab-course-map.md` and `references/theory-code-mapping.md`, then inspect relevant XiangShanLab superscalar/out-of-order course markdown when available.
3. Locate matching design documentation before explaining broad intent. Read `references/design-doc-map.md` for Design Doc navigation and then inspect the relevant markdown files when available.
4. Read the real Scala/Chisel files before explaining implementation. Do not infer behavior from names or documentation alone.
5. Identify the enclosing subsystem and load the matching reference:
   - Theory-to-code mapping: read `references/theory-code-mapping.md`
   - XiangShanLab course navigation: read `references/xiangshanlab-course-map.md`
   - Backend pipeline: read `references/backend.md`
   - Frontend fetch and prediction: read `references/frontend.md`
   - Memory/cache instruction behavior: read `references/mem-cache.md`
   - XSCache cache subsystem: read `references/xscache.md`
   - Load/store instruction taxonomy: read `references/load-store-instruction-taxonomy.md`
   - Algorithms, FSMs, control path, and data path: read `references/algorithm-control-dataflow.md`
   - Exceptions, interrupts, debug, and privilege: read `references/exception-debug-privilege.md`
   - Queue and buffer capacity logic: read `references/queue-buffer-capacity.md`
   - Mermaid diagrams: read `references/diagrams.md`
   - Output structure and question checklist: read `references/analysis-template.md`
6. Separate theory, design intent from effective code:
   - Use XiangShanLab course docs to define architecture concepts such as superscalar issue, hazards, register renaming, Tomasulo/scoreboard ideas, issue queues, bypass, physical registers, ROB, and dynamic instruction execution.
   - Use Design Doc/course implementation docs to describe motivation and intended XiangShan architecture.
   - Use active source files, instantiated modules, actual IO connections, and parameter values to describe what the implementation really does.
   - If theory/docs and code disagree or a concept is implemented differently, state the difference and prefer code for behavior.
7. Trace interfaces first: `IO(...)`, bundle classes, Decoupled/Valid handshakes, redirect/flush signals, wakeup/writeback channels, MMU/cache request-response channels, and ROB/FTQ/LSQ pointers.
8. Trace index and address calculations before summarizing any table, array, queue, cache, bank, entry, pointer, or selector access. For every index, explain exactly how it is computed from fields/signals/parameters, which bits are used, how bank/set/way/entry slices are formed, how wraparound or pointer arithmetic works, and where the computed index is consumed. Include first-cycle or reset-time index behavior when registers or counters are involved.
9. Trace state updates: `RegInit`, `RegEnable`, `RegNext`, `SyncDataModule`, SRAM/data arrays, queues, tables, valid bits, pointers, snapshots, replay queues, FSM state registers, refill/writeback buffers, and replacement metadata. For every valid/status bit, explicitly state reset/initial value, set condition, clear condition, hold condition, flush/cancel condition, and all consumers that observe it. For every storage structure, analyze it through `update`, `release`, `replace`, and `search/read/probe`: the exact timing, enable/fire condition, index calculation, payload fields updated/read/released/replaced, valid-bit effect, conflict priority when multiple operations happen together, and downstream effect. For every read/write port on a storage structure, analyze conflict scenarios: same-cycle read/write same index, multiple writes same index, multiple reads contending for a limited port, read-after-write/write-after-read/write-after-write behavior, bypass/forwarding or assert behavior, and which request wins or stalls. For every queue/buffer, inspect empty/full/almost-full/allow-enqueue/allow-dequeue/backpressure logic.
10. Trace algorithms and paths: replacement, arbitration, selection, prediction, replay, redirect, dependency, forwarding, merge/split, miss handling, permission checking, exception/interrupt/debug/privilege checks, and exception priority algorithms. For each algorithm, describe how it works from initialization/reset, how the first real transaction/request is handled, all major cases/branches, tie or priority behavior, invalid/empty/full behavior, and how state evolves after each case. For every arbiter or priority selector, explicitly analyze the simultaneous-request scenario: all requesters that can assert together, request qualification, priority/age/round-robin rule, grant generation, ready/backpressure, losing request behavior, and same-cycle update effects.
11. Generate diagrams after the trace is understood: one Mermaid data-path diagram and one Mermaid module-interface diagram. Keep diagrams faithful to effective code; include only real modules, ports, queues, arrays, and pipeline stages.
12. Explain only claims supported by code or cited design docs. If a signal source or sink is unclear, say what was found and what file should be inspected next.

## Answer Contract

For each requested module, produce:

- Module role and boundary: what the module owns and what it delegates.
- Theory context: relevant XiangShanLab superscalar/out-of-order concepts and the exact code structures that implement, specialize, or replace those concepts.
- Documentation context: relevant Design Doc/course pages and which claims are theory, design intent, or verified code behavior.
- Effective code path: instantiated modules and live connections that determine actual behavior; mention dead/unused code only as non-effective.
- Microarchitecture parameters: where relevant parameters are defined, how they enter the module, and how they change port counts, entry counts, widths, algorithms, or optional features.
- Interaction interface: key inputs, outputs, handshakes, redirects, flushes, exceptions, interrupts, debug-mode signals, privilege metadata, and performance/debug outputs.
- Theory-to-code mapping: map concepts such as structural/data/control hazards, multi-issue, rename, dependency tracking, wakeup/select, bypass, physical registers, ROB, speculation, precise exception, and memory ordering to concrete modules/signals.
- Why it exists: the pipeline, speculation, ordering, bandwidth, latency, or correctness problem it solves.
- Index and address calculation: for every table/array/queue/cache/bank access, explain how the index is calculated, what parameters determine its width/range, which bits select bank/set/way/entry, how pointer arithmetic or wraparound works, and where the index is consumed.
- Algorithm analysis: selection/replacement/arbitration/update rules, priority order, exception/interrupt/debug/privilege priority, pseudocode-level behavior, initialization/reset behavior, first-transaction behavior, all major cases/branches, invalid/empty/full behavior, tie handling, simultaneous-request arbitration behavior, and corner cases.
- Mermaid diagrams: generate a key data-path diagram and a module-interface diagram for the requested module when the analysis involves multiple modules, stages, queues, arrays, or handshakes.
- State-machine analysis: states, transition conditions, outputs by state, entry/exit conditions, and relation to ready/valid backpressure.
- Control path: focus on mux selects, valid/ready/fire, arbiters, FSM transitions, stalls, redirects, cancels, replays, exceptions, wakeup, commit, and pipeline stage control signals.
- Data path: payload movement, pipeline registers, data transforms, muxes, arrays, bypass/forwarding, queue movement, and writeback/refill paths.
- Storage structures: every important queue/table/array/register group, its owner, reset/initial value, full `update` / `release` / `replace` / `search` behavior, and all read/write port conflict behavior. For each operation, include the exact timing, fire/enable condition, calculated index/address/pointer, payload contents, valid-bit set/clear/hold effect, conflict priority, flush/cancel/replay interaction, empty/full/almost-full condition, and backpressure behavior. For port conflicts, cover same-cycle read/write same index, multiple writes same index, multiple reads with limited ports, RAW/WAR/WAW behavior, bypass/forwarding/assert behavior, and which request wins, stalls, retries, or is dropped.
- Signal provenance: for key signals, list `from what` and `to what`, then explain why the signal exists.
- Dynamic flow: describe normal path, speculative path, and at least one exceptional/replay/redirect/miss path when relevant.
- Code anchors: include file paths, class/module names, and concise line references when available.

Use English for the generated analysis unless the user explicitly asks for another language.

## Module Navigation

Use these starting points for Kunminghu v2/v3 style trees:

- Top level: `XSCore.scala`, `XSTile.scala`, `Backend.scala`, `Frontend.scala`, `MemBlock.scala`, `L1Cache.scala`
- Backend: `backend/decode`, `backend/rename`, `backend/dispatch`, `backend/issue`, `backend/exu`, `backend/fu`, `backend/datapath`, `backend/regcache`, `backend/rob`, `backend/ctrlblock`
- Frontend: `frontend/IFU.scala`, `frontend/Frontend.scala`, `frontend/NewFtq.scala`, `frontend/IBuffer.scala`, `frontend/BPU.scala`, `frontend/FTB.scala`, `frontend/Tage.scala`, `frontend/ITTAGE.scala`, `frontend/SC.scala`, `frontend/Bim.scala`, `frontend/RAS.scala`, `frontend/icache`
- Memory/cache: `mem/MemBlock.scala`, `mem/pipeline`, `mem/lsqueue`, `mem/sbuffer`, `mem/mdp`, `mem/prefetch`, `mem/vector`, `cache/L1Cache.scala`, `cache/CacheInstruction.scala`, `cache/dcache`, `cache/mmu`, `cache/wpu`
- XSCache: `coupledL2`, `openLLC`, `xscache/chi`, `xscache/common`, `coupledL2/prefetch`, `coupledL2/utils`, `openLLC/chi`, `openLLC/utils`

## Guardrails

- Treat XiangShanLab theory docs as concept definitions, and Design Doc as design intent; neither proves implementation. Verify with effective source code.
- Always connect theory terms to exact code artifacts: module/class, IO bundle, parameter, storage structure, control signal, data path, algorithm, or FSM.
- Distinguish `backend` execution pipelines from `mem` pipeline units and `cache/dcache` data arrays/mainpipe.
- Treat `cache/mmu/TLB.scala` and related files as the likely ITLB/DTLB implementation area; frontend may instantiate or connect instruction-side TLB signals through fetch/icache bundles.
- When explaining load/store-class memory instructions, always expand both `mem` modules and `cache` modules on the effective path; when the request reaches XSCache/L2/L3/CHI, also expand XSCache modules.
- When explaining CBO or fence behavior, search both decode/FU files and cache/memory files; the behavior may be split across decode classification, FU control, memory ordering, and cache request handling.
- For branch prediction, separate predictor storage update from prediction response generation and redirect/commit training.
- For every `who` answer, include the parameter owner when parameters determine the updater count, width, entries, or optional behavior.
- Whenever code computes an index, address, pointer, bank selector, set selector, way selector, queue entry selector, `OHToUInt`, `UIntToOH`, `PriorityMux` index, `PopCount` rank, `wrap`, `head`, `tail`, or `ptr`, explain the full calculation and the consumer. Do not only name the signal.
- Whenever code implements an algorithm, explain the algorithm's operating principle, initialization/reset state, first valid/request behavior, all major cases, priority/tie behavior, simultaneous-request arbitration behavior, and how state changes for each case.
- For every `valid`, `ready-valid` payload valid, table valid bit, queue entry valid bit, FSM-valid state, or status-valid bit, explicitly document when it is set, when it is cleared, when it holds, what flush/cancel/replay redirects affect it, and which downstream logic observes it.
- For every storage structure, organize behavior around `update`, `release`, `replace`, and `search/read/probe` even if the code names them enqueue/dequeue/read/write/allocate/free/lookup/match. For each operation, state when it happens, which index is used and how that index is calculated, what content is updated/released/replaced/searched, how valid/status bits change, and which operation wins if update/release/replace/search collide in the same cycle. Also analyze read/write port conflicts: same-cycle read and write to the same index, two writes to the same index, more requesters than physical ports, bypass/forwarding/assert behavior, and whether the losing request stalls, retries, replays, is masked, or is illegal.
- For every arbiter, selector, mux-priority network, grant vector, ready fanout, or request scheduler, enumerate all requesters and analyze what happens when different requesters arrive simultaneously. State request qualification, priority or fairness rule, grant encoding, data selected, ready/backpressure returned to each requester, losing request behavior, and state updates caused by the grant.
- For every nontrivial module, include Mermaid diagrams unless the user asks for prose only. Use `flowchart LR` for data/interface paths and `stateDiagram-v2` for explicit FSMs when useful.
- For exception, interrupt, debug, and privilege behavior, identify the exact priority and propagation path; do not collapse them into a generic flush explanation.
- For every queue or buffer, explicitly explain empty/full checks and what upstream/downstream logic observes them.
- In Dynamic Operation, always include speculative path when the module participates in prediction, out-of-order execution, replayable memory operations, cache prefetch, coherence speculation, or permission checks before commit.
- Avoid generic CPU textbook summaries unless they are tied to a concrete XiangShan/XSCache signal, queue, table, state machine, algorithm, or module.
