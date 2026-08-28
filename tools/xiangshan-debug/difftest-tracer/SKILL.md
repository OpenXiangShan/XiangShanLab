---
name: difftest-tracer
description: Analyze XiangShan and RISC-V difftest failures from simulator logs plus program BIN, ELF, and objdump/disassembly artifacts. Use when Codex must locate the first bad architectural instruction, distinguish DUT, reference-model, test, and environment faults, perform backward taint analysis, or trace instruction flow, control flow, and data flow from Decode through Commit using logs, commit traces, source, or waveforms.
---

# Difftest Tracer

Locate the earliest proven divergence, then trace its causes backward and its effects forward. Treat the PC printed by difftest as an observation point, not automatically as the root-cause instruction.

Operate in one of two explicit modes:

- **Static preliminary**: logs plus ELF/BIN/disassembly can identify mismatch sinks, dynamic commit candidates present in the log, static dependencies, and missing evidence. Do not claim a cycle-accurate RTL root cause.
- **Dynamic proof**: matching commit/reference traces and/or waveforms plus the exact source revision can establish the earliest divergent event and microarchitectural causal boundary.

## Start the case

1. Pin every supplied artifact by absolute path, size, and SHA-256. Never modify the originals.
2. Record the XiangShan, NEMU/Spike, difftest, workload, and simulator revisions when available. Record ISA string, privilege mode, reset/load address, seed, cycle, hart, and whether the worktree is dirty.
3. Require at least one failure log and one instruction mapping source: an ELF or a disassembly. Every raw BIN requires its exact load address; its ISA/ABI must also be known. Prefer the supplied disassembly when it is known to match the run; otherwise regenerate it from the ELF.
4. Create a separate case directory and run:

```bash
python3 <skill-directory>/scripts/analyze_case.py \
  --log /abs/path/run.log \
  --elf /abs/path/program.elf \
  --bin /abs/path/program.bin \
  --base 0x80000000 \
  --disasm /abs/path/program.txt \
  --out /abs/path/case-analysis
```

Use the exact raw-BIN load address for `--base`; never copy the example value without checking the run configuration. Use only the options matching available artifacts. Add `--pc 0x...` to replace automatic anchor selection when log parsing selects the wrong hart or failure. A `reg:` root drives the static register slice; `mem:`, `csr:`, and `control:` roots collect matching observations and make unresolved dynamic dependencies explicit. Read `case.md` and `case.json`; verify every automatically parsed anchor against the raw log.

Read [references/analysis-method.md](references/analysis-method.md) before substantive analysis. Read [references/xiangshan-flow.md](references/xiangshan-flow.md) when RTL source, commit traces, FST/VCD waves, or pipeline-level claims are involved. Read [references/riscv-taint-rules.md](references/riscv-taint-rules.md) for CSR, exception, memory, atomics, vector, floating-point, or self-modifying-code cases.

## Establish the failure boundary

Build a short event timeline around the first reported mismatch. Separate:

- the last matching architectural state;
- the instruction/event accepted by each side;
- the first differing state field or memory event;
- the reporter PC and cycle;
- later traps, redirects, timeouts, or bad traps.

Determine whether comparison occurs before or after the displayed instruction commits. Confirm this from the local difftest implementation or trace format; do not guess. Keep DUT and reference values labeled exactly as the log labels them because `right`/`wrong` wording varies between harness versions.

If the log dumps a multi-commit window, inspect every lane in log order. A mismatch line may name the first PC in a comparison group while a later lane in that same group writes the bad register. Likewise, an exception event may identify a faulting instruction after the last normally committed PC.

Classify the first divergence as GPR/FPR/vector result, CSR/privilege state, PC/control transfer, exception/interrupt, load/store/AMO, MMIO/uncache, or nondeterministic device/timer state. A later mismatch may only be a propagated symptom.

## Trace three flows

### Instruction flow

Map the candidate instruction from bytes to ELF symbol/source and disassembly. Follow the same dynamic uop through Decode, Rename, Dispatch, Issue, execution, writeback, ROB completion, and Commit. After Rename, use stable identity such as `robIdx` plus wrap flag and uop index; PC alone is insufficient under replay or repeated execution.

For every Decoupled boundary, report `valid`, `ready`, and `fire = valid && ready`. Report a `valid`-only interface as observed valid, never as a transfer. Record redirects, flushes, replay, exception bits, writeback data, and commit lane.

### Control flow

Reconstruct the actually executed predecessor and successor, not merely adjacent disassembly. Include branch operands/result, predicted and resolved targets, redirect cause, trap/return state (`xepc`, `xcause`, `xtval`, delegation and privilege), and relevant indirect target producers. Treat interrupts and exceptions as control dependences.

### Data flow

Start at the first mismatching sink and work backward through register definitions, address generation, memory producers, CSR state, and control predicates. Track value, width, signedness, byte mask, virtual/physical address, and producer identity. For loads, prove forwarding/cache/MMIO source and ordering; for stores/AMOs, track address, data, mask, queue entry, visibility, and difftest event. Do not equate static adjacency with a dynamic dependency.

Create a backward slice table with one row per edge: producer, consumer, value/state, evidence, confidence, and unresolved alias/path conditions. Then trace the bad value forward to explain why difftest first reports where it does.

## Find the root cause

For each candidate, test competing ownership hypotheses:

- XiangShan RTL or generated RTL;
- NEMU/Spike/reference model;
- difftest adapter, skip/refill, commit ordering, or state-copy logic;
- workload, linker/load address, stale ELF/disassembly, self-modifying code, or undefined behavior;
- device, timer, interrupt, initialization, or configuration mismatch.

Prefer the earliest cycle where an input is still correct and an output becomes wrong. Tie that boundary to active source assignments and handshakes. Use A/B revisions or a minimal replay when available. A source diff is a hypothesis until the failing and fixed behaviors are observed under comparable conditions.

## Report the result

Use [assets/analysis-report.md](assets/analysis-report.md) as the output structure. Lead with one of these conclusions:

- **Proven root cause**: instruction and causal boundary have direct dynamic/source evidence.
- **Strong candidate**: architectural slice is closed, but a required microarchitectural observation is missing.
- **Unresolved**: artifacts do not distinguish multiple live hypotheses.

Always state the first bad architectural event, the root-cause instruction if different, the taint chain, the three flows, ownership, proposed repair boundary, regression tests, and evidence gaps. Cite absolute artifact paths and exact log lines, disassembly addresses, source lines, cycles, and signal names. Never invent cycle-accurate behavior from only a log and ELF.

Write the report in the user's language unless they request another language. Preserve instruction mnemonics, signal names, identifiers, and quoted log fields exactly.

Label substantive claims as `E1 OBSERVED` (raw artifact), `E2 DERIVED` (deterministic decode/calculation), `E3 CORRELATED` (independent dynamic sources aligned), `E4 PROVEN` (first wrong boundary or controlled A/B confirmation), or `HYPOTHESIS`.

Before delivery, verify that artifact hashes still match, all PCs map to the same image, instruction bytes agree across log/ELF/disassembly when present, and every `fire` claim has both handshake operands or an explicitly documented always-ready contract.
