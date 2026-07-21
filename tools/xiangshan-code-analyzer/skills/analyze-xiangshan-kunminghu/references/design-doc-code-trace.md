# Design Doc to Source Traceability

Use this reference whenever `XiangShan-Design-Doc` is read or cited. Design documentation explains intent; active `XiangShan` Chisel/Scala code proves effective behavior.

## Repositories and Versions

Record both revisions independently:

```text
Design Doc URL: https://github.com/OpenXiangShan/XiangShan-Design-Doc.git
Design Doc commit/branch:
XiangShan URL: https://github.com/OpenXiangShan/XiangShan.git
XiangShan source commit/branch:
```

Never assume the Design Doc branch and source branch match. If versions differ, state the risk and classify mappings as version-aligned, partially aligned, or version-mismatched.

## Required Mapping Procedure

1. **Locate:** record the exact Design Doc file, heading, paragraph, table, figure, or caption.
2. **Atomize:** split prose into checkable claims: module, purpose, stage, input/output, algorithm, FSM/state, parameter, timing, resource, exception, or interface assumption.
3. **Search source:** find the corresponding module/class, IO bundle, instantiation, `io.* :=`, `<>`, parameter, state register, queue/table, and signal consumer in `XiangShan`.
4. **Trace lines:** read the source with line numbers and follow the claim from producer to consumer. Include registers, muxes, `valid/ready/fire`, state transitions, and downstream effects rather than citing only a class declaration.
5. **Classify:** mark each claim `Verified`, `Partially verified`, `Not found`, `Version mismatch`, or `Design-only/pseudocode`.
6. **Explain discrepancies:** state whether the difference comes from a stale document, configuration/parameter guard, renamed module, commented/dead code, omitted implementation detail, or an actual implementation deviation.

## Traceability Matrix

Every design-derived claim must appear in a matrix like this before it is used in the narrative:

| ID | Design Doc location | Design claim | Source file:lines | Code relationship | Status | Discrepancy/risk |
| --- | --- | --- | --- | --- | --- | --- |
| D1 | `docs/zh/...:heading` |  | `src/main/scala/...:L-L` | instantiation / connection / state / algorithm / parameter |  |  |

`Code relationship` must say exactly how the source realizes the claim: module instantiation, IO connection, signal transform, state transition, table access, arbitration, stage register, or architectural effect. A filename-only citation is insufficient.

## Line-by-Line Explanation

For each `Verified` or `Partially verified` claim, explain in order:

1. Design sentence or figure element.
2. Source declaration and owning module.
3. Input/producer and exact line.
4. Transform, mux, register, state, table, or handshake lines.
5. Output/consumer and exact line.
6. Reset, stall, flush, redirect, replay, exception, commit, and parameter conditions when applicable.
7. Concrete transaction showing the relationship.
8. What the Design Doc omits, simplifies, or describes differently.

Use short source snippets; do not paste whole files. Do not infer a connection from matching names alone.

## Figures and Tables

Treat every Design Doc figure edge and table row as a claim:

- Map each node to an instantiated/configured source module or explicitly mark it conceptual.
- Map each edge to a source connection or signal path with line evidence.
- Map stage labels to actual pipeline registers/valid controls.
- Map capacity/width/timing numbers to parameters or source-proven behavior.
- If a figure contains a block absent from effective code, mark it `Not found` or `Design-only`.

Do not reproduce a figure as if it were effective behavior until its nodes and edges are mapped.

## Output Gate

Do not present Design Doc intent as implementation fact unless the matrix contains source evidence. Every generated document that uses Design Doc material must include:

- `Design Doc baseline` with URL and commit/branch.
- `XiangShan source baseline` with URL and commit/branch.
- A design-to-source traceability matrix.
- A line-by-line explanation for every load-bearing claim.
- A discrepancy section listing `Partially verified`, `Not found`, `Version mismatch`, and `Design-only` claims.

When no matching source implementation exists, say so explicitly and do not invent a replacement mapping.
