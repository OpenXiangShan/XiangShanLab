# Evidence and root-cause rules

Use these rules to turn a simulator symptom into a code-level causal explanation. Keep facts, interpretations, and proposals separate.

## Evidence labels

Label material claims in working notes and the report:

| Label | Meaning |
| --- | --- |
| `LOG-OBSERVED` | Exact value/event appears in the bound baseline stdout/stderr or issue attachment |
| `WAVE-OBSERVED` | Exact signal/value/cycle appears in the bound baseline waveform |
| `SOURCE-PROVEN` | Replay-checkout source directly defines the stated logic, wiring, or state transition |
| `CORRELATED` | Log and waveform identities/values establish that they describe the same transaction |
| `INFERRED` | Best explanation consistent with evidence, but an internal edge is not directly dumped/proven |
| `UNRESOLVED` | Required signal, source origin, configuration, or experiment is missing |
| `FIX-VALIDATED` | The stated A/B oracle and requested validation gates passed after the patch |

Do not turn `INFERRED` into `SOURCE-PROVEN`, or a plausible patch into `FIX-VALIDATED`, through wording alone.

## Artifact identity

Build one row per replayed image:

```text
issue -> source HEAD/status -> emu/config -> image SHA-256
      -> stdout + stderr + return code -> disassembly -> exact waveform
```

Bind the waveform using the `dump wave to ...` line in that image's stdout. File timestamp proximity or “newest FST” is insufficient. Record ambiguity when one log names multiple waveforms or several candidates share a basename.

Record the exact replay-script hash because its behavior may evolve independently of this skill. Record source dirty state both before and after the patch so generated or pre-existing edits cannot silently enter the diagnosis.

## Derive a stable failure signature

Extract the smallest signature that distinguishes the issue from unrelated failures:

- PC plus instruction bits, not PC alone;
- full ROB pointer where wrap/flag exists, not only the low index;
- assertion instance/message and simulation time;
- difftest field, DUT value, reference value, and commit lane;
- architectural destination/value or CSR/trap tuple;
- virtual/physical address, data, mask, command, and LQ/SQ identity;
- seed/input hash and relevant configuration.

Separate the first architectural mismatch from later cascading mismatches. Work backward from the first divergence unless evidence proves an earlier assertion is causal.

## Log-to-wave correlation

Require at least two independent identity fields when practical. Useful joins are:

| Log anchor | Waveform join |
| --- | --- |
| commit PC/instr | difftest or ROB commit lane PC/instr, then full ROB pointer |
| register mismatch | commit destination/write enable/data plus physical writeback provenance |
| trap tuple | exception/redirect valid, cause, epc, tval, privilege, and commit/flush identity |
| assertion time | nearest verified clock edge plus assertion inputs/state |
| memory address mismatch | uop PC/ROB plus LQ/SQ/request ID, address, mask, data, response |
| branch failure | PC/instr plus FTQ/ROB, predicted target/taken, resolved target, redirect payload |

If log and waveform PC/instruction/value do not agree, keep multiple candidates. Do not force a correlation to preserve a hypothesis.

## Handshake and ownership rules

- A transfer occurs only when the same sampled edge has `valid=1 && ready=1`.
- `valid=1 && ready=0` proves a presented, blocked item; it does not prove acceptance.
- `ready=1 && valid=0` proves an upstream bubble, not backpressure.
- A generic LSQ request with `needAlloc=0` is not an LQ/SQ allocation.
- A state register in a shared module belongs to the target transaction only after correlation by PC, full ROB, FTQ/LQ/SQ, address, source/sink ID, or request/response metadata.
- A queue `full`, replay, redirect, kill, exception, or cache hit/miss must be tied to its actual producer and consumer wiring before using it as the cause.
- Rename onward, use full ROB identity as primary. Carry FTQ and physical registers; for memory operations carry LQ/SQ and memory request identity in parallel.

When ready or an internal gate is not dumped, state “request presented; acceptance unresolved” rather than synthesizing `fire` from a nearby signal.

## Find the earliest wrong predicate

Build two timelines:

1. **Expected:** what ISA semantics and the issue's oracle require.
2. **Observed:** what log and waveform prove.

Find the earliest cycle where they differ. Then answer:

1. Which input/state made the module choose this value or transition?
2. Was that input accepted, held, replayed, killed, redirected, or flushed?
3. Which source assignment/gating expression computes the wrong or missing predicate?
4. Which downstream consumer turns it into the visible architectural error?
5. Which alternative mechanism could produce the same symptom, and what evidence rejects it?

A useful root-cause statement has this form:

```text
At cycle C, transaction I fired at boundary B. Predicate P was value V because
source expression E omitted/miscomputed condition K. Consumer D therefore
performed/suppressed action A, leading at cycle C2 to architectural mismatch M.
Wave signals S1..Sn and source locations L1..Ln prove the chain; alternative H
is rejected by evidence R.
```

“The bug is in the TLB/ROB/cache” is a subsystem classification, not a root cause.

## Trace source faithfully

Use only the replay checkout for line-level proof. For each important waveform signal:

1. Resolve the generated hierarchy to the source module/instance.
2. Find the bundle or field definition.
3. Read surrounding assignments, pipeline registers, and gating—not only the matching line.
4. Follow the producer and consumer across module boundaries.
5. Check configuration parameters, feature enables, and instantiated implementation.
6. Check reset, flush, redirect, replay, exception, and simultaneous-event priority.
7. Cite an absolute source path and line near the relevant logic.

If generated signal spelling has no exact Chisel match, describe the hierarchy/neighbor-field mapping and label it inferred. Module presence does not prove an effective path; follow actual `valid`, `ready`, `fire`, mux selection, and consumer wiring.

## Hypothesis table

Keep this table until the diagnosis closes:

| Hypothesis | Predicted waveform | Supporting evidence | Contradicting evidence | Status |
| --- | --- | --- | --- | --- |
| Primary causal predicate | Exact signal/value and cycle relation | Log + wave + source | Any missing internal edge | open/proven/rejected |
| Alternative upstream cause | What would differ earlier | Evidence | Evidence | open/proven/rejected |
| Alternative downstream/checker issue | What architectural/checker signals would show | Evidence | Evidence | open/proven/rejected |

Actively search for counterexamples: older transactions, wrong-path instructions, delayed difftest wrappers, aliasing indices, shared cache/MSHR state, and stale configuration are common false correlations.

## Code-level recommendation standard

A complete recommendation states:

- exact file, class/module, method/block, and predicate or state transition;
- current behavior and why it is wrong for the reproduced input;
- proposed logic and why it preserves other commands/modes/privilege states;
- hit/miss, replay, redirect, flush, exception, reset, and simultaneous-event implications;
- parameter/configuration scope and affected Kunminghu version;
- focused assertion/test and expected fixed waveform;
- evidence gaps and confidence.

Do not recommend disabling difftest/assertions, filtering the failing log, increasing a timeout, forcing ready, or suppressing an exception unless the checker or timeout itself is source-and-waveform-proven faulty.

## Confidence rule

- **High:** baseline oracle reproduced; log and waveform correlated; causal predicate and source path proven; alternative rejected.
- **Medium:** baseline reproduced and source path is strong, but an internal waveform edge or configuration fact is missing.
- **Low:** issue/log/source suggest a fix but the baseline, exact waveform, or causal identity is unavailable.

Confidence in diagnosis and validation level are separate. A high-confidence diagnosis can still have an unbuilt patch.
