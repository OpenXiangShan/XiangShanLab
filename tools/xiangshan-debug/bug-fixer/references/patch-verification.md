# Patch and verification protocol

Read this file before the first source edit. The objective is not merely to make the original log disappear; it is to correct the causal logic and demonstrate the intended architectural behavior.

## 1. Freeze the pre-patch state

In the fresh replay checkout, record:

```bash
git rev-parse HEAD
git status --short
git submodule status --recursive
```

Also preserve:

- baseline image SHA-256, emu path/hash or build identity, config, simulator argv, difftest library, seed, and timeout;
- baseline stdout/stderr, per-image return code, disassembly, and exact waveform path;
- the baseline failure predicate and key waveform rows;
- generated or pre-existing dirty files, if any.

Declare an explicit target-file allowlist. Do not reset, clean, stash, or overwrite unrelated changes. Generated build files may change during rebuild; keep source/test diff scope separate from build artifacts.

## 2. Design the patch before editing

Write a short patch contract:

| Field | Required content |
| --- | --- |
| Faulty logic | Exact module/block/predicate/state transition |
| Trigger | Reproduced input state and cycle/identity |
| Current effect | Incorrect request, result, exception, redirect, replay, or commit |
| New semantics | Exact corrected condition/action |
| Preserved cases | Other opcodes, privilege modes, hit/miss paths, flush/replay/reset priorities |
| Test hook | Assertion, unit test, directed test, or waveform predicate |
| Expected fixed trace | Signal/value/event that must replace the faulty baseline event |

Prefer changing the shared semantic boundary that owns the invariant. Avoid scattering symptom-specific guards across downstream consumers. When several paths—hit, miss/refill, replay, redirect, dual-line, vector segments—share the invariant, either centralize the check or prove each path is covered.

Do not “fix” the issue by:

- disabling or weakening difftest/assertions;
- filtering the failure text;
- increasing timeouts without proving a timeout bug;
- unconditionally forcing `ready`, hit, no-exception, or no-replay;
- changing the test's expected result to match faulty hardware;
- adding an elaboration/config change that silently creates different hardware unless that configuration is the actual fix under review.

## 3. Apply a minimal, reviewable change

Edit only allowlisted files. Keep the functional patch small and make any test/assertion addition explicit. Preserve naming, Chisel width/signedness, pipeline timing, and simultaneous-event priority.

After editing:

```bash
git diff --check -- <allowlisted-files>
git diff -- <allowlisted-files>
git status --short
```

Read the final diff in context. Check for accidental generated files, formatting-only churn, hard-coded issue values, incomplete vector/port coverage, and comments that claim more than the logic guarantees.

## 4. Validate at proportional layers

Use the narrowest fast check first, then the expensive original replay:

1. **Static/elaboration check:** syntax, formatting, focused compile/elaboration, width and connection checks.
2. **Focused module or assertion check:** existing unit test, directed microarchitectural test, or a new invariant assertion when available.
3. **Original issue A/B:** identical input/config/seed/command against baseline and patched hardware.
4. **Fixed waveform check:** confirm the predicted internal predicate and architectural outcome, not only final process status.
5. **Neighbor regression:** exercise the closest legal/non-triggering cases and any touched hit/miss/replay/privilege/config variants.
6. **Independent fresh build:** for high confidence, apply the same patch to another fresh checkout at the same base commit and repeat the decisive test.

If a fast check fails, diagnose it before launching a long simulation. A successful compiler does not satisfy the issue oracle.

## 5. Preserve A/B comparability

The fixed run must use the same:

- base commit plus only the recorded patch;
- submodule revisions and generated configuration;
- input image bytes/SHA-256 and initial memory image;
- emulator options, difftest reference, trace setting, seed, and timeout;
- reset/boot path and relevant environment variables.

Write fixed output below a distinct directory such as:

```text
<outer-run>/validation/fixed-01/
├── command.json
├── replay.stdout
├── replay.stderr
├── return-code.txt
├── waveform-path.txt
└── fixed-manifest.json
```

Do not overwrite the baseline `.replay.stdout`, `.replay.stderr`, `.replay.diasm`, or waveform. If emu always creates waves in `XiangShan/build`, immediately record and bind the newly printed path to the fixed log.

Verify the patched emu was actually rebuilt after the source edit. Record the build command/return code and inspect timestamps or build output; when incremental-build provenance is doubtful, perform a safe clean rebuild inside the disposable replay checkout or use an independent fresh patched checkout. Never clean a user-owned tree implicitly.

## 6. Apply the A/B gates

Fill this table with commands and evidence paths:

| Gate | Baseline | Fixed | Pass rule |
| --- | --- | --- | --- |
| Same experiment | commit/config/image/argv/seed | same plus patch | No uncontrolled difference |
| Failure signature | exact signature present | exact signature absent | Required |
| Success signature | absent or not reached | exact expected outcome present | Required |
| Process/checkers | recorded return and checker state | no new unexpected difftest/assert/panic | Required |
| Architectural state | wrong commit/register/memory/CSR/trap | expected state | Required for functional repair |
| Causal waveform | wrong/missing predicate/event | predicted corrected event | Required for waveform-validated claim |
| Focused regression | recorded | pass | Required for regression-checked claim |

The absence of the original signature is insufficient if the fixed run timed out earlier, took a different path, skipped the test, disabled a checker, or failed differently.

For nondeterministic bugs, compare enough identical-seed or controlled-seed trials to support the claim. Report counts and confidence; one lucky pass does not prove repair.

## 7. Check the fixed waveform causally

Repeat only the decisive baseline queries first:

- same instruction/transaction identity and corresponding cycle window;
- corrected predicate or state transition;
- request/response/redirect/replay/exception `valid/ready/fire`;
- downstream architectural or difftest outcome;
- absence of the old illegal event for the target identity.

Cycle numbers may shift after the patch. Join by PC/instruction and transaction identity, not absolute cycle equality. Explain latency changes rather than treating them as mismatches.

Then inspect likely side effects: neighboring transactions, killed/wrong-path work, queue occupancy, fault priority, refill/replay behavior, and reset/flush races touched by the changed condition.

## 8. Assign the validation level honestly

Use the highest level whose gates actually passed:

| Level | Meaning |
| --- | --- |
| `R0 Not reproduced` | Environment/input was attempted but the issue oracle was not observed |
| `R1 Reproduced` | Fresh baseline exhibits the exact failure signature |
| `D1 Diagnosed` | Log-wave-source causal chain is established; no patch or patch untested |
| `P1 Proposed` | Concrete source patch is described but not applied/built |
| `V1 Build checked` | Patch applied and relevant build/static check passes |
| `V2 Original test fixed` | Same original experiment changes from baseline fail to fixed success |
| `V3 Waveform validated` | Fixed waveform proves the predicted causal and architectural correction |
| `V4 Regression checked` | Focused neighbor regressions pass; optionally repeated in an independent fresh build |

Report multiple labels when useful, for example `R1 + D1 + P1` when the diagnosis is strong but build resources are unavailable. Never call `V2` or above from source reasoning alone.

## 9. Final scope audit

Before delivery, verify:

- final HEAD/base commit and submodule revisions;
- `git status --short` and the complete source/test diff;
- only allowlisted files are part of the proposed patch;
- baseline and fixed artifacts have distinct paths and correct bindings;
- report links point to the same replay checkout analyzed;
- commands, return codes, oracle results, cycles, and validation level match the retained evidence;
- unresolved risks and unexecuted tests are explicit.

Provide the patch as a diff or leave it in the authorized replay checkout, according to the user's requested output. Do not imply that an ephemeral replay-tree edit has been applied to the user's main XiangShan checkout.
