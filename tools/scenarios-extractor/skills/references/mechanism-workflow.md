# Mechanism Workflow

Use this reference after the main `SKILL.md` workflow when converting a microarchitecture mechanism into scenario descriptions.

## Mechanism Discovery

Search the requested mechanism across:
- XiangShan source paths, especially `src/main/scala/xiangshan`.
- Analyzer references in `tools/xiangshan-code-analyzer/skills/analyze-xiangshan-kunminghu/references`.
- Verification-driver rules in `tools/verification-driver/skills`.
- Existing generated analysis documents when the user points to them.

Search terms should include:
- Original mechanism string and case variants.
- Expanded acronym candidates.
- Neighbor concepts, such as predictor, prefetch, replay, dependence, queue, CAM, table, MSHR, PTW, bank, set, redirect, flush, and backpressure.
- Known module path fragments, such as `mem/mdp` for MDP-like memory dependence prediction.

## Evidence Model

Build this model before writing final scenarios:

| Field | Required content |
| --- | --- |
| Mechanism goal | Performance, correctness, speculation, recovery, resource utilization, or observability problem being addressed |
| Effective modules | Paths, classes, instantiated submodules, parameters, and source revision |
| Inputs | Request sources, valid/ready/fire gates, opcodes, metadata, redirects, flushes, context state, counters |
| State | Tables, queues, valid bits, confidence bits, pointers, FSM state, credits, outstanding IDs, snapshots |
| Algorithm | Lookup, allocation, update, replacement, prediction, selection, replay, recovery, priority, or merge rules |
| Outputs | Grants, predictions, stalls, replays, redirects, updates, difftest, counters, downstream requests |
| Observability | Assertions, coverage, waveforms, counters, scoreboard checks, architectural state, system state |

## Scenario Derivation

Derive scenario families from code facts:
- A queue depth creates empty, one-entry, almost-full, full, wrap, enq+deq, flush-at-extreme, and drain scenarios.
- A table index/hash creates hit, miss, alias, conflict, replacement, invalidation, and distributed-versus-hotspot scenarios.
- An arbiter creates each-requester-alone, all-requesters-valid, low-priority-old-versus-high-priority-new, grant stability, loser backpressure, and fairness scenarios.
- A predictor creates correct prediction, false positive, false negative, alias, training, confidence transition, redirect recovery, and context pollution scenarios.
- A replay path creates first replay, repeated replay, replay plus redirect, replay plus exception, replay queue full, and eventual completion scenarios.
- A flush or redirect creates killed-work isolation, survivor preservation, resource release, stale update prevention, and recovery-throughput scenarios.
- A performance counter creates event-select, inhibit, overflow, privilege filter, sampling precision, and correlation scenarios.

## MDP Starting Point

For an MDP request, treat the likely XiangShan scope as memory dependence prediction unless source evidence proves otherwise. Search for:
- `MDP`, `mdp`, `MemoryDepend`, `MemDep`, `depend`, `wait table`, `store set`, `load violation`, `replay`, `ld`, `st`, and `lsqueue`.
- Source paths under `mem/mdp`, `mem/lsqueue`, load/store pipelines, replay queues, violation detection, and dispatch/issue metadata.

Expected MDP scenario families usually include:
- Predictor disabled or cold-start baseline.
- Predicted-independent load bypassing older stores.
- Predicted-dependent load waiting for older store address/data.
- False-independent case causing violation and replay.
- False-dependent case causing avoidable stall or throughput loss.
- Training/update after violation, squash, commit, or successful execution.
- Table aliasing and replacement under many PCs or load/store pairs.
- Flush/redirect while prediction or update is pending.
- Context switch, privilege, ASID/VMID, or address-space pollution when prediction state can survive across contexts.
- LSQ/store-buffer/cache-miss pressure while MDP is making wait/bypass decisions.

Do not claim any of these are implemented until source lines prove them. Use them as a search and scenario-generation checklist.
