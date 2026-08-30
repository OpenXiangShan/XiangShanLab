---
name: xiangshan-bug-fixer
description: Reproduce, diagnose, fix, and validate OpenXiangShan/XiangShan bugs from a GitHub issue number. Use when an agent must drive a fresh xs-bug-replay.py run, bind error logs and disassembly to the exact FST/VCD/FSDB, analyze the failure with analyze-xiangshan-wavekit, trace the causal Chisel path using Kunminghu microarchitecture notes and the replay checkout, propose or apply a minimal source-level patch, and perform baseline-versus-fixed validation.
---

# XiangShan Bug Fixer

Build an evidence-preserving loop from an issue number to a code-level diagnosis and, when authorized, a verified patch. Write the working notes and final report in Chinese unless the user requests another language.

## Core contract

- Treat the issue number, user-supplied commit, checkout, test image, output path, and validation command as binding.
- Create every baseline replay below a new timestamped parent directory. Never delete, overwrite, or reuse an old `xs-bug-replay-<issue>` directory to claim a fresh reproduction.
- Modify only the XiangShan checkout created for this run unless the user explicitly names another checkout. Preserve all unrelated and pre-existing changes.
- If the user asks only for diagnosis or recommendations, stop before editing source. If the user asks to fix or validate, apply the smallest justified patch and continue through A/B validation.
- Do not push, open a PR, comment on an issue, or rewrite remote state without an explicit request.
- Never equate a zero exit code, a successful build, or a plausible source reading with bug reproduction or repair. Prove the baseline failure and the fixed outcome with an explicit oracle.

## Load resources progressively

1. Read [references/workflow.md](references/workflow.md) for every run.
2. Read [references/evidence-and-root-cause.md](references/evidence-and-root-cause.md) before correlating logs, waveforms, and source.
3. After classifying the failing subsystem, read only the relevant entries in [references/architecture-routing.md](references/architecture-routing.md).
4. Before changing source, read [references/patch-verification.md](references/patch-verification.md).
5. Use [assets/bug-fix-report.md](assets/bug-fix-report.md) as the final report skeleton.

Resolve this skill directory from the loaded `SKILL.md`; invoke bundled scripts by absolute path. Start with:

```bash
python3 <skill-dir>/scripts/probe_dependencies.py
```

Use `--json` when a machine-readable dependency record is useful. Honor explicit path overrides instead of the defaults reported by the probe.

## Required inputs and inference

Require one positive XiangShan GitHub issue number. Accept an optional XiangShan commit, expected failure signature, work root, or known target PC.

Infer missing PC, instruction, address, expected state, and success condition from the issue, downloaded logs, replay logs, and disassembly before asking the user. Do not invent a test oracle. If the issue remains ambiguous after inspecting available evidence, state the competing interpretations and request the single choice that changes the experiment.

## Execute the closed loop

1. **Freeze the issue contract.** Save the issue URL, baseline commit selection, attachments, reproduction instructions, expected behavior, observed failure, and a precise baseline/fixed oracle in the new run directory.
2. **Run a fresh baseline.** Use `scripts/run_replay_fresh.py --issue <N> --work-root <artifact-root>` and optionally `--commit-hash <SHA>`. This calls the current `xs-bug-replay.py` as an independent subprocess, captures its driver log, and isolates its fixed-name output directory.
3. **Verify artifacts and failure.** Run `scripts/collect_replay_evidence.py --case-dir <run-dir> --issue <N> --require-reproduction`. Inspect the manifest, every replay return message, stdout/stderr, disassembly, exact source HEAD, emu, and the FST path printed by the matching stdout. The artifact gate does not prove the bug; separately show that the baseline oracle fails.
4. **Anchor log to waveform.** Read the resolved `analyze-xiangshan-wavekit/SKILL.md` and its `references/workflow.md` completely. Override its fallback source root with this run's `xs-env/XiangShan`. Track PC before rename and the full ROB pointer after rename; carry FTQ/LQ/SQ and physical-register identities where applicable.
5. **Prove the causal chain.** For every material transfer, require same-edge `valid && ready`; for every stall or missing action, identify the controlling predicate or mark it unresolved. Correlate log values with commit/difftest values before declaring that a waveform event is the logged failure. Trace each important dumped signal to its producer, gating logic, consumer, and exact source lines in the replay checkout.
6. **Challenge the diagnosis.** Maintain at least one alternative hypothesis until waveform and source evidence falsify it. Use the course documents only to navigate mechanisms; re-check configuration, instantiated path, and logic at the replay commit.
7. **Design or apply the patch.** State the faulty predicate/state transition, why it produces the observed output, the exact files/functions to change, preserved semantics, and likely corner cases. Prefer a narrow functional fix plus a focused assertion or regression over symptom masking.
8. **Validate A/B.** Preserve baseline logs and waveform. Rebuild from the patched checkout, rerun the identical image/configuration/command into a distinct fixed-results directory, and check all gates in `patch-verification.md`: the baseline signature appears, the fixed signature disappears, the expected result appears, the causal waveform predicate changes as predicted, and focused regressions pass.
9. **Report the achieved level.** Distinguish reproduced, diagnosed, patch-proposed, patch-build-checked, original-test-fixed, waveform-validated, and regression-checked. List every missing gate and never upgrade the claim beyond executed evidence.

## WaveKit compatibility guardrails

- Prefer the Python and `PYTHONPATH` returned by `probe_dependencies.py`; system Python may not contain `pylibfst`.
- Keep one reader alive for a large FST, discover hierarchy first, then load only narrow signals and cycle windows.
- Do not use `get_matched_signals("TOP.SimTop.*")` as a glob with current WaveKit. Enumerate FST signals through the reader hierarchy or `_signal_by_name`, then use exact names, brace expansion, or `@` regex and assert that matches are nonempty.
- Set `sample_on_posedge=True` explicitly for the normal XiangShan flow, verify it against handshakes, and report the actual edge. Treat `end_cycle` as exclusive.
- Do not assign physical time units until the waveform timescale is verified. Use `load_unknown_mask` when X/Z bits could affect a control decision.
- Do not infer `fire`, allocation, redirect, replay, or FSM ownership from a valid-only signal or a concurrent shared-module state.

## Stop conditions

Continue through recoverable in-scope failures, but stop and report a concrete blocker when the issue lacks a runnable input and no faithful reproducer can be derived, the required baseline cannot be selected, the build or replay repeatedly fails for the same external reason, no usable waveform can be generated, or a needed edit would affect a checkout outside the authorized run. A blocker does not turn an unverified recommendation into a verified fix.
