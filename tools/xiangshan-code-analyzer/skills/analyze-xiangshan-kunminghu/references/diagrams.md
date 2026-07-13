# Mermaid Diagram Requirements

Use this file whenever producing an analysis for a module with multiple interfaces, pipeline stages, queues, arrays, arbiters, FSMs, or cross-module interactions.

## Required Diagrams

Generate these Mermaid diagrams unless the user asks for prose only:

1. Key data-path diagram: show payload movement through pipeline stages, queues, arrays, muxes, bypass/forwarding paths, and output/writeback/refill paths.
2. Module-interface diagram: show neighboring modules and major IO bundles/signals, including valid/ready handshakes, redirect/flush/cancel/replay paths, wakeup/writeback paths, and parameter-generated port groups.
3. FSM diagram: generate `stateDiagram-v2` when the module has an explicit FSM or state-like queue entry lifecycle worth visualizing.

## Data-Path Diagram Rules

Use `flowchart LR` by default.

Include:
- Input producers and output consumers.
- Pipeline stage names such as s0/s1/s2 when present in code.
- Queues, tables, SRAM/data arrays, tag arrays, replacement metadata, and registers.
- Mux/select points using node labels like `Mux: source select`, `PriorityMux`, `Mux1H`, or `Arbiter`.
- Main payload fields: pc, uop, psrc/pdest, data, addr, mask, tag, way, set, robIdx, ftqIdx, lqIdx, sqIdx, exception metadata.
- Control qualifiers on edges when important: `valid`, `ready`, `fire`, `wen`, `ren`, `hit`, `miss`, `replay`, `redirect`, `flush`, `cancel`.

Avoid:
- Drawing every temporary wire.
- Drawing documentation-only blocks that are not instantiated in effective code.
- Mixing control-only and data-only edges without labels.

## Module-Interface Diagram Rules

Use `flowchart LR`.

Show:
- The requested module in the center.
- Upstream producers on the left and downstream consumers on the right.
- Feedback/control sources above or below: redirect, commit, wakeup, load cancel, cache miss/refill, TLB exception, predictor update, CSR/trap.
- Interface names and direction: `Decoupled req`, `Valid resp`, `Vec readPorts`, `writePorts`, `wakeup`, `redirect`, `flush`.
- Parameterized multiplicity: label edges with values or expressions such as `RenameWidth`, `LoadPipelineWidth`, `getIntExuRCReadSize`, `nWays`, `nSets` when known.

## FSM Diagram Rules

Use `stateDiagram-v2` for explicit state machines.

Include:
- Reset/initial state.
- State names exactly as code names when possible.
- Transition labels from code conditions.
- Terminal or loop conditions for ready/valid backpressure.
- Cancel/flush/redirect/replay/miss transitions.

If the module has no explicit FSM but uses valid bits/status fields as an implicit state machine, either:
- draw a compact state diagram for an entry lifecycle, or
- state that no explicit FSM exists and describe the valid/status lifecycle in the Storage Structures section.

## Mermaid Syntax Constraints

- Use fenced code blocks with `mermaid` info string.
- Keep node labels short and ASCII-friendly unless quoting exact signal names.
- Quote labels containing punctuation if needed.
- Prefer stable, readable node IDs: `Decode`, `Rename`, `IssueQ`, `DataArray`, `Arbiter`, `RegFile`, `DCache`.
- Do not include line references inside Mermaid nodes; put code anchors in prose.
