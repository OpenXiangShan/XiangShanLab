# End-to-end workflow

Use this file as the mandatory execution sequence. A phase is complete only when its exit gate is satisfied.

## Phase map

| Phase | Main action | Exit gate |
| --- | --- | --- |
| Contract | Freeze issue, baseline, input, expected/actual behavior, and oracle | A positive issue number and an executable failure predicate exist |
| Baseline | Run the current replay driver in a new timestamped parent | Fresh checkout, exact HEAD, emu, input logs, and bound waveform exist |
| Reproduction | Inspect logs and architectural state | The issue-specific failure oracle is observed, not merely a nonzero exit |
| Diagnosis | Correlate log, disassembly, waveform, and replay source | One causal chain survives alternatives and has source plus waveform evidence |
| Patch | Change the smallest source allowlist | The diff implements the stated semantic correction without masking the checker |
| Validation | Rebuild and rerun the identical experiment | Baseline fails, fixed passes, causal waveform behavior changes, regressions run |
| Delivery | Write the evidence report and patch | Every claim carries an achieved validation level and unresolved items |

## 1. Resolve dependencies

Run from any directory using the absolute skill path:

```bash
python3 <skill-dir>/scripts/probe_dependencies.py --json
```

The probe resolves these dependencies and verifies `FstReader` import:

- `tools/xiangshan-bugs-analyzer/xs-bug-replay.py`
- `tools/analyze-xiangshan-wavekit/SKILL.md` and `references/workflow.md`
- the Kunminghu source-analysis course directory
- a WaveKit Python environment and source tree

Honor user-provided overrides. If a dependency moved, pass an explicit path or set the environment variable reported by `--help`; do not silently substitute an unrelated checkout.

Run `xs-bug-replay.py --help` before a costly replay if the file hash or local interface has changed. Treat the live script as the replay contract. The currently observed interface uses `--issue` and optional `--commit_hash`, creates `xs-bug-replay-<issue>` relative to its working directory, builds FST-enabled emu, and replays discovered `.elf`/`.bin` attachments.

## 2. Freeze the issue contract

Validate the issue as a positive integer and any explicit XiangShan SHA as 7–40 hexadecimal digits. Save `issue-context.md` in the outer run directory with:

- canonical issue URL, title, fetch time, and relevant body/comment facts;
- explicit XiangShan commit or the recorded fallback-selection rule;
- attachment URLs and their roles;
- exact reproduction instructions and environment/configuration;
- expected architectural behavior;
- observed failure described by concrete log fields, PC/instruction, address, register, CSR, assertion, or exit marker;
- a baseline failure predicate and a fixed success predicate.

The replay script fetches issue metadata but does not preserve the issue body. Save the contract separately using the available GitHub/web access. Do not accept an arbitrary SHA seen in the issue as a XiangShan baseline; distinguish XiangShan, NEMU, AM, and dependency revisions.

Define the oracle before reading candidate fixes. Examples:

- baseline contains a specific difftest mismatch at `PC + instr`, while fixed reaches `GOOD TRAP` with no mismatch;
- baseline commits a forbidden PC, while fixed raises the expected page fault with exact `cause/epc/tval`;
- baseline triggers a named assertion after an identified request, while fixed completes and the request/response invariant holds;
- baseline returns incorrect register/memory data, while fixed matches the reference and commit event.

Do not use vague predicates such as “log looks normal” or “FST was generated.” For intermittent failures, define trial count, seed preservation, and the acceptable pass/fail rate before patching.

## 3. Create and run a fresh baseline

Use the wrapper so the replay driver's fixed output name cannot collide with history:

```bash
python3 <skill-dir>/scripts/run_replay_fresh.py \
  --issue <issue> \
  --work-root <work-root>
```

Choose a dedicated artifact root with enough space. Do not place multi-gigabyte replay/build output in the skill directory or another source repository unless the user explicitly requests that location.

Add `--commit-hash <sha>` only when the issue or user fixes the baseline. The wrapper creates:

```text
xiangshan-fix-<issue>-<UTC timestamp>/
├── replay-command.json
├── replay-driver.log
└── xs-bug-replay-<issue>/
    ├── attachments and extracted files
    ├── <image>.replay.stdout
    ├── <image>.replay.stderr
    ├── <image>.replay.diasm        # ELF only
    └── xs-env/XiangShan/
```

Never pre-create or reuse the inner directory. Never delete an old run to make the command fit. The wrapper records the exact replay script hash, argv, timestamps, return code, and expected inner directory.

Monitor long clone/build/simulation work and retain the full driver log. Retry a transient network operation only after identifying it as transient. Do not repeat a deterministic compiler, elaboration, or assertion failure without changing the relevant condition.

### Missing runnable attachment

The replay driver only executes `.elf` and `.bin`; it reports but does not compile `.c`. If the issue supplies only source, a command, a seed, or prose:

1. Stay inside the fresh `xs-env` created for this run.
2. Follow the issue's build instructions and record every command/toolchain revision.
3. Prefer a minimal `nexus-am/apps/<issue-specific-name>` reproducer when that matches the issue environment.
4. Disassemble the resulting ELF and run the same FST-enabled emu/difftest configuration.
5. Save stdout, stderr, return code, image hash, and printed waveform path using the same manifest convention.

Label this as a manually completed replay step; do not claim that `xs-bug-replay.py` compiled the source.

## 4. Build the artifact manifest

Run:

```bash
python3 <skill-dir>/scripts/collect_replay_evidence.py \
  --case-dir <outer-run-dir> \
  --issue <issue> \
  --output <outer-run-dir>/baseline-manifest.json \
  --require-reproduction
```

The collector verifies artifact completeness and records anchors. Inspect its warnings. In particular:

- verify `source.git_head`, source dirty state, and `build/emu`;
- inspect each per-image emu return code printed in `replay-driver.log`;
- bind an image, its stdout/stderr, optional `.replay.diasm`, and the exact waveform path printed by that stdout;
- reject a convenient “latest FST” when the log-to-wave mapping is absent or ambiguous;
- verify every selected waveform exists and is nonempty.

`artifact_gate_passed=true` does not prove reproduction. Apply the issue-specific oracle to stdout/stderr and, where needed, commit/difftest waveform state. If the baseline does not exhibit the expected failure, first check commit, configuration, test input/hash, seed, simulator arguments, reset, timeout, and nondeterminism. Do not patch a non-reproduced symptom as though it were confirmed.

## 5. Turn the failure into waveform anchors

Read the resolved `analyze-xiangshan-wavekit` skill and its workflow completely. Always override its source fallback with:

```text
<outer-run>/xs-bug-replay-<issue>/xs-env/XiangShan
```

Normalize the failing instruction or transaction from logs and disassembly:

- PC and instruction bits;
- architectural sources/destination and expected side effect;
- mismatch/assertion time or nearby commit;
- address, data, mask, privilege, CSR/trap state, or vector state;
- expected and actual values.

Choose the first unambiguous anchor:

- difftest mismatch: match commit PC **and instruction**, then retain the full ROB identity;
- assertion: map the logged simulation time to nearby clock edges and call it a log-time anchor until a dumped condition confirms it;
- memory bug: track PC/uop to full ROB pointer, then LQ/SQ/request-response identity and address;
- repeated PC: disambiguate with instruction, lane, FTQ, ROB flag/value, LQ/SQ, and sequence context.

Do not merge ROB commit and top-level difftest commit into one cycle without evidence; wrapper registration may offset them.

## 6. Query WaveKit safely

Use the Python and source path from the probe:

```bash
env PYTHONDONTWRITEBYTECODE=1 \
  PYTHONPATH=<wavekit-src> \
  <wavekit-python> <analysis-script.py>
```

For FST, keep one `FstReader` open. Enumerate `reader._signal_by_name.values()` or recursively walk scopes to resolve exact signal names. Current matching supports exact names, brace forms such as `{0..5}`, and `@` regex; `*` is not a glob. Assert that every expected match set is nonempty before loading waveforms.

Set `sample_on_posedge=True` explicitly, then verify the edge against known `valid/ready` transfers. Use a narrow absolute-cycle window around the anchor; `begin_cycle` is inclusive and `end_cycle` is exclusive. Before combining waveforms, verify identical clock, edge, window, `.clock`, and `.time` axes.

Build the causal window backward from the visible failure to the earliest wrong predicate or missing event, then forward to the architectural effect. At every boundary record:

```text
cycle/time | identity | valid | ready | fire | value | producer | consumer | source lines
```

Use `load_unknown_mask` when X/Z could change a decision. State waveform time values without units until the file timescale is established.

## 7. Diagnose against the replay source

Follow the evidence rules in `evidence-and-root-cause.md`. Classify the subsystem, open the matching course documents from `architecture-routing.md`, then inspect the replay checkout itself in this order:

1. bundle/IO definition and instantiated configuration;
2. producer assignment and local pipeline registers;
3. ready/valid gating, state/FSM transition, arbitration, replay/redirect/flush conditions;
4. consumer and architectural/difftest boundary;
5. reset, exception, kill, and parameterized corner cases.

Search current source with `rg -n`; cite absolute replay-checkout paths and exact lines. The course documents are mechanism maps, not proof for this historical commit.

Maintain an evidence table for the primary and alternative hypotheses. A root cause is ready only when the observed faulty output follows from a waveform-proven control/data predicate and the replay source explains that predicate. If an internal signal is not dumped, mark the missing link and use a targeted assertion or extra trace in a new experiment instead of inventing it.

## 8. Patch and validate

If source edits are authorized, read `patch-verification.md`, snapshot HEAD/status, declare a target-file allowlist, and apply a minimal patch in the replay checkout. Preserve the baseline evidence before rebuilding.

Rebuild the FST-enabled emu from the patched source. Rerun the identical image hash, difftest library, emu arguments, configuration, seed, and sufficient timeout, but write fixed stdout/stderr/return code and waveform into a distinct results directory. Never overwrite baseline logs or relabel a baseline FST as fixed.

Apply all relevant A/B gates:

1. baseline failure predicate present;
2. fixed failure predicate absent;
3. fixed success predicate present;
4. no new difftest/assert/panic failure;
5. fixed waveform shows the predicted control/data change and correct architectural outcome;
6. focused regression or assertion passes;
7. final diff contains only the allowlisted fix/test files.

If time or environment prevents a gate, report the highest completed validation level rather than guessing.

## 9. Deliver

Copy `assets/bug-fix-report.md` into the run results and fill every applicable section. Include the source diff or exact suggested patch, absolute evidence paths, commands, return codes, hashes, key cycle/time rows, source links, alternatives rejected, tests, residual risks, and a concise upstream-ready repair recommendation.
