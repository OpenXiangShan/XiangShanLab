---
name: scenarios-extractor
description: Generate detailed, code-grounded XiangShan verification scenario descriptions from a microarchitecture optimization mechanism name, feature name, module name, or short prompt such as MDP, branch prediction, prefetch, replay, queue arbitration, cache miss handling, or CSR/control optimization. Use when Codex must combine tools/verification-driver scenario-driver rules with tools/xiangshan-code-analyzer/skills/analyze-xiangshan-kunminghu source analysis to produce concrete scenario descriptions, stimuli, expected observations, failure signatures, checkers, coverage points, and evidence requirements.
---

# Scenarios Extractor

## Overview

Use this skill to turn a microarchitecture mechanism into verification-ready scenario descriptions. Start from the user's mechanism name, locate the effective XiangShan implementation through `analyze-xiangshan-kunminghu`, then select applicable scenario-driver rules from `verification-driver` and emit concrete scenarios tied to source evidence.

Primary source packs:
- Code analysis: `/nfs/home/yuanmiaomiao/XiangShanLab/tools/xiangshan-code-analyzer/skills/analyze-xiangshan-kunminghu`
- Verification driver rules: `/nfs/home/yuanmiaomiao/XiangShanLab/tools/verification-driver/skills`
- Output template: `references/scenario-output-template.md`
- Mechanism workflow: `references/mechanism-workflow.md`

## Workflow

1. Normalize the request into a mechanism target.
   - Expand acronyms only after checking code/docs. For `MDP`, first search for `MDP`, `mdp`, `Memory Dependence`, `MemDep`, and memory-dependence predictor names in XiangShan source and analysis references.
   - If the mechanism maps to multiple modules, keep a top-level scenario set and one subsection per effective module.
   - If the mechanism is ambiguous, state the candidate meanings and continue with the most code-supported XiangShan interpretation unless the user provided a different scope.

2. Load the source-analysis skill before making code claims.
   - Read the `analyze-xiangshan-kunminghu` `SKILL.md`.
   - Read only the relevant analyzer references for the mechanism, such as `mem-cache.md`, `backend.md`, `frontend.md`, `queue-buffer-capacity.md`, `algorithm-control-dataflow.md`, `instruction-latency-throughput.md`, `cross-boundary-analysis.md`, `difftest.md`, or `verification-special-attention.md`.
   - Inspect real XiangShan Scala/Chisel source and record branch/commit, paths, classes/modules, parameters, line numbers, and load-bearing snippets.

3. Load the verification-driver rule files that match the code evidence.
   - Always read `xiangshanVerificationDriver.md` for the global driver shape and minimum coverage.
   - Read `performanceBottleneckDrivers.md` for optimization mechanisms that affect throughput, latency, queueing, replay, resource utilization, or recovery bandwidth.
   - Read `conflictScenarioDrivers.md` for contention, replay, redirect, same-entry, bank/set, ordering, or context conflicts.
   - Read `forwardProgressDrivers.md` for deadlock, livelock, starvation, retry loops, arbiters, FSMs, queues, and backpressure.
   - Read `fsmScenarioDrivers.md`, `cacheStructureDrivers.md`, `indexBusHashDrivers.md`, `virtualizationProtectionDrivers.md`, `systemVirtualizationPermissionDrivers.md`, `architectureExceptionDrivers.md`, `debugEventDrivers.md`, `performanceMonitorCounterDrivers.md`, or `operandBoundaryDrivers.md` only when the mechanism touches those concerns.

4. Build a mechanism model before writing scenarios.
   - Define the optimization goal: what bottleneck, hazard, correctness risk, or recovery cost the mechanism is intended to reduce.
   - Identify effective code boundaries: producers, consumers, storage structures, predictors/tables, queues, arbiters, FSMs, counters, redirects, replays, flushes, and commit-visible outcomes.
   - Identify state lifecycle: reset, allocation/update, lookup/search, release/clear, replacement, replay, flush/cancel, context switch, exception/debug interaction, and recovery.
   - Identify observability: difftest signals, performance counters, assertions, coverage points, waveforms, scoreboard state, and visible architectural/system effects.

5. Generate concrete scenario descriptions using the template.
   - Read `references/scenario-output-template.md`.
   - Every scenario must name trigger conditions, initial state, stimulus sequence, concurrent pressure, expected observations, failure signatures, checkers, coverage, and exact evidence still required.
   - Prefer scenario families over generic prose. A useful scenario row should be implementable as a directed test, constrained random seed, assertion set, or waveform debug target.

## Scenario Selection Rules

For every mechanism, include these scenario families when reachable:
- Baseline behavior: one low-pressure legal path that establishes nominal latency, throughput, state update, and completion.
- Saturation behavior: all legal producers, full or almost-full resources, same-cycle competitors, and fair downstream completion.
- Conflict behavior: same-entry, same-bank, same-set, same-port, same-FSM-transition, same-queue-slot, ordering, replay, redirect, exception, or context conflict.
- Recovery behavior: redirect, replay, flush, exception, interrupt, debug entry, invalidate, fence, context switch, or refill under an extreme state.
- Forward progress: deadlock, livelock, starvation, head-of-line blocking, retry loop, and fairness-bound cases.
- Boundary behavior: pointer wrap, queue empty/full, table invalid/valid, index/hash collision, operand/address boundary, cross-page/cache-line/MMIO boundary when applicable.
- Observability behavior: counter/event correlation, difftest state, assertion failure signature, coverage closure, and waveform checkpoints.

## Evidence Rules

Do not infer behavior from a mechanism name. Each implementation-specific claim must cite:
- XiangShan branch/commit or local source revision.
- File path, class/module name, and exact line numbers.
- Signal, parameter, queue/table/FSM/arbiter/counter, or connection name.
- Short explanation of who produces the signal, who consumes it, why it exists, and which scenario proves it.

If source evidence is not yet available, mark the row as `evidence-needed` and state exactly what to inspect next. Do not present a missing behavior as fact.

## Output Requirements

Use the user's language for final prose when practical. Keep identifiers, file paths, signal names, checker names, and table headers stable in English unless the user asks otherwise.

The final output must include:
- Mechanism summary and scope.
- Code evidence map.
- Scenario taxonomy.
- Detailed scenario table.
- Checker and coverage plan.
- Waveform/debug observation plan when handshakes, queues, FSMs, redirects, or replays are involved.
- Open evidence gaps and next source files to inspect.

When the user asks to save the scenario document, save Markdown under the user-specified path. If no path is specified, create it in the current working directory with a concise mechanism stem such as `MDP-scenarios.md`.
