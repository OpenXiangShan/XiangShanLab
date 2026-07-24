---
name: xiangshan-scenario-wave-test
description: Generate and iterate XiangShan/Kunminghu microarchitectural scenario tests from a user-described behavior and a XiangShan source path. Use when Codex needs to analyze XiangShan source, create Nexus-AM or similar RISC-V test programs, build images, run XiangShan emu with FST/FSDB wave dumping, verify the requested hardware scenario with wavekit, iterate until the scenario is reproduced, and produce a waveform analysis report. Triggers include 香山, XiangShan, Kunminghu, nexus-am, emu, dump-wave, wavekit, FST, waveform, MDP, StoreSet, LSQ, replay, redirect, predictor, or microarchitecture testcase generation.
---

# XiangShan Scenario Wave Test

## Overview

Use this skill to turn a requested XiangShan microarchitectural scenario into a real test program, prove it with emu waveforms, and write a source-backed waveform analysis.

Keep the loop evidence-driven: do not claim the scenario reproduced until wavekit-visible signals satisfy the scenario criteria.

For the detailed loop and templates, read [references/xiangshan-wave-loop.md](references/xiangshan-wave-loop.md) when starting implementation or when the first waveform does not match expectations.

## Required Inputs

Require these inputs, inferring only when local context makes them obvious:

- Scenario: the behavior or microarchitectural case the user wants to trigger.
- XiangShan source path: the root containing `src/main/scala`.
- Test location/build flow: use the user-provided app path; otherwise infer an existing Nexus-AM app path from the repository.
- Emu binary path: infer `<repo>/XiangShan/build/emu` when the XiangShan root is inside the workspace.

Ask a concise question only if the scenario or XiangShan source root is missing and cannot be discovered locally.

## Workflow

1. Analyze the scenario and source.
   - Use `rg`/`sed` to locate the relevant XiangShan modules, parameters, update paths, performance counters, debug prints, and signal names.
   - Identify the externally controllable instruction pattern that should cause the requested state transition.
   - Define waveform success criteria before writing the test.

2. Generate or edit a focused test.
   - Prefer the repository's existing app/test pattern.
   - Use `volatile`, inline assembly, fixed alignment, compiler flags, and observable sinks when compiler optimization or PC stability matters.
   - Keep the test narrow: one scenario sequence plus any warmup needed to make the hardware state deterministic.

3. Build and inspect the image.
   - Run the repo's build command, usually `make ARCH=riscv64-xs` in the test directory.
   - Use objdump/readelf when PCs, encodings, sections, or folded indices matter.

4. Run emu with wave dumping.
   - Use the user's command style, normally `./XiangShan/build/emu --no-diff --dump-wave-full -i IMAGE_PATH`.
   - Add a scoped `--wave-path` when helpful, but always record the wave path printed by emu at startup.
   - Confirm the program reaches the intended trap or completion condition.

5. Analyze the waveform.
   - Use the existing wavekit analysis skill when available, especially `/nfs/home/yanyusong/XiangShanLab/tools/analyze-xiangshan-wavekit/SKILL.md`.
   - Load only the needed wavekit instructions, then query the FST/FSDB for the predefined success signals.
   - Correlate cause and effect signals, excluding reset-time or uninitialized pulses unless the scenario is explicitly about reset.

6. Iterate until reproduced.
   - If any required event is absent or ambiguous, modify the test or signal query and rerun build, emu, and wavekit.
   - Change one major variable at a time: instruction spacing, dependent chains, warmup, PC placement, data addresses, branch path, iteration count, or CSR/setup state.
   - Preserve intermediate findings enough to explain why the final version is shaped the way it is.

7. Write the analysis.
   - Create or update a report file requested by the user; if unspecified, use a scenario-specific `*-deep-analysis.md`.
   - Include commands, image path, recorded wave path, source references, test structure, waveform signal list, event table, and final pass/fail conclusion.
   - State explicitly if the scenario could not be reproduced within the available run budget.

## Rules

- Never treat a successful build as proof of reproduction; require waveform evidence.
- Never omit the exact FST/FSDB path emitted by emu.
- Prefer direct source citations and local waveform facts over architectural guesses.
- Keep generated tests deterministic and small enough to rerun.
- Work with existing dirty-tree changes; do not revert unrelated files.
