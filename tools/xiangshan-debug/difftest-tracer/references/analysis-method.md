# Analysis Method

## Contents

1. Input contract
2. Failure-boundary reconstruction
3. Taint workflow
4. Hypothesis testing
5. Evidence and stopping rules

## Input contract

Record an immutable manifest before interpreting the failure.

| Input | Required facts | Main use |
| --- | --- | --- |
| Difftest/simulator logs | full file, not only the final line; hart; cycle; seed | first mismatch, state dumps, commit order |
| ELF | build identity, entry, sections, symbols, ISA attributes | authoritative address and symbol mapping |
| BIN | load address, length, image-generation relation to ELF | bytes actually loaded by simulator |
| Disassembly | generating command/tool/version and matched ELF | instruction text and static CFG |
| Commit trace | format and comparison phase | dynamic instruction sequence |
| FST/VCD | clock, time-to-cycle mapping, hierarchy root | precise pipeline and state boundary |
| DUT/reference source | exact commit and local diff | implementation ownership |

If artifacts disagree, stop causal analysis and resolve identity first. Compare instruction bytes at the candidate PC. For a raw BIN, translate `PC - load_base` and bounds-check before reading bytes. ELF virtual addresses and BIN file offsets are not interchangeable.

Check that every reporter/candidate PC falls in an executable ELF segment. A garbage, sign-extended, relocated, or unmapped PC is evidence about the harness or address mapping, not permission to choose the nearest instruction.

Do not execute a supplied workload on the host. Treat ELF, BIN, logs, and disassembly as untrusted inputs. Use inspection tools with argument arrays and never construct shell commands from artifact contents.

## Failure-boundary reconstruction

### Parse the harness semantics

Inspect the local difftest/commit-log producer when possible and answer:

1. Is the printed PC the instruction just compared, the next PC, a trap PC, or the reporter's current PC?
2. Does comparison happen before or after DUT/reference execution?
3. Are register dumps pre-state or post-state?
4. Does a skipped instruction copy DUT state into the reference?
5. Can multiple instructions commit in the failure cycle, and in what lane/order?
6. Are delayed store, AMO, CBO, MMIO, exception, and interrupt events compared separately?

Preserve the log's own side labels. Some versions print reference as `right`; other wrappers reverse or rename the roles.

### Build the timeline

Capture at least the final matching event and all commit/event lanes through the first mismatch. Use this schema:

| Order/cycle | Hart | Dynamic identity | PC/bytes | Event | DUT | Reference | Match? | Evidence |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |

Keep dynamic events in log/retirement order. Never sort them by PC. For a multi-commit cycle, preserve commit-lane order. A ROB index can wrap and a PC can recur, so neither is a globally unique identity.

Distinguish three PCs:

- **Reporter PC**: printed where the harness stops.
- **First divergent instruction/event**: first architectural transition whose post-state differs.
- **Root-cause instruction**: earliest dynamic operation that created the wrong control/data state. It may precede the divergence, especially for address, predicate, forwarding, interrupt, and CSR bugs.

### Classify before slicing

| Mismatch | Initial sink | Required extra facts |
| --- | --- | --- |
| GPR/FPR/vector | destination architectural register | width, sign/NaN boxing, element mask/VL/VTYPE |
| PC | resolved next PC | branch operands, prediction, redirect, trap return |
| CSR | named CSR field | privilege, CSR op, trap/return, WARL/WPRI rules |
| Exception | cause/tval/epc | priority, delegation, faulting VA/PA, suppression |
| Load data | destination plus memory version | VA/PA, mask, forwarding source, cache/MMIO response |
| Store/AMO | memory/event | address/data/mask, ordering, visibility, event timing |
| Interrupt | pending/enable/delegation state | timer/device inputs and sampling boundary |

## Taint workflow

### Backward slice

1. Seed taint from the first differing bits, not the whole machine state.
2. Identify the dynamic instruction/event that wrote or should have written them.
3. Add explicit data inputs: source registers, immediates, CSRs, memory bytes, forwarded data, exception metadata, and privilege/configuration state.
4. Add address dependencies for every memory access: base/index, translation state, PMP/PMA/PBMT, alignment, mask, and queue aliases.
5. Add control dependencies: branch predicate, redirect, trap/interrupt decision, replay/kill, and valid/ready acceptance.
6. Continue to known-equal values, constants, initialized state, or external inputs. Mark unresolved memory aliases and path joins instead of choosing one silently.

Use static disassembly only to enumerate possible producers and CFG edges. Confirm the executed producer using commit trace or waveform when loops, calls, branches, replay, interrupts, aliases, or self-modifying code are possible.

### Forward propagation

After finding an incorrect producer, follow the taint forward until the reporter:

- result bus to physical register to architectural commit;
- wrong address to TLB/cache/MMIO to exception or data;
- wrong branch predicate/target to redirect and next committed PC;
- wrong CSR/privilege state to later translation, permission, or interrupt;
- wrong store/AMO event to memory and a later load/reference comparison.

This explains why the root-cause PC can differ from the mismatch PC and helps choose the repair boundary.

### Slice table

| Edge | Producer | Consumer | Value/state | Dynamic identity | Evidence | Confidence |
| --- | --- | --- | --- | --- | --- | --- |

Use `Proven`, `Strong inference`, `Candidate`, or `Unknown`. A static reaching definition is at most a candidate until the dynamic path is established.

## Hypothesis testing

Maintain competing explanations until evidence eliminates them.

| Hypothesis | Discriminating observation |
| --- | --- |
| DUT execution bug | DUT input correct, functional-unit/pipeline output wrong before commit |
| Reference bug | DUT agrees with ISA/independent model and reference transition is wrong |
| Difftest adapter bug | DUT/reference internal states agree but compared/copied event differs |
| Stale/mismatched image | bytes or symbol mapping disagree at the same PC |
| Undefined/nondeterministic workload | behavior depends on uninitialized state, races, devices, or unspecified fields |
| Timing/config mismatch | interrupt, timer, MMIO, privilege, ISA, or memory map differs |

Useful experiments include a minimal replay, same seed A/B source revisions, reference cross-check with a second independent model, trace-on only near the failing commit count, and assertions at the earliest wrong boundary. Keep configuration identical and report anything not held constant.

## Evidence and stopping rules

Evidence strength, highest first:

1. Same-run dynamic handshake/state transition tied by stable identity.
2. Active source assignment plus same-run observed inputs and output.
3. Architectural log/commit trace with matching bytes and explicit comparison semantics.
4. Static source/disassembly inference.
5. Name-based intuition or historical similarity.

Use these report labels consistently:

- `E1 OBSERVED`: copied from a pinned raw artifact with location.
- `E2 DERIVED`: deterministic decode, arithmetic, mask, or address mapping.
- `E3 CORRELATED`: independently observed DUT/reference/pipeline events aligned by identity.
- `E4 PROVEN`: the same inputs first produce different outputs at this boundary, or a controlled A/B run closes causality.
- `HYPOTHESIS`: one or more causal leaves remain unresolved.

Stop at **Strong candidate** rather than **Proven root cause** when any necessary identity, executed path, handshake, memory producer, comparison phase, or artifact match is missing. State the smallest additional trace needed to close the gap.
