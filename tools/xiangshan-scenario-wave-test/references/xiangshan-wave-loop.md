# XiangShan Scenario Wave Loop

## Source Analysis Checklist

Start by translating the user scenario into concrete XiangShan state:

- Find the owner module and update path with `rg`.
- Identify relevant request/response bundles, valid bits, state registers, table entries, counters, debug prints, and top-level IO wiring.
- Trace the source of the update back to an instruction-visible event such as commit, replay, redirect, exception, CSR write, cache miss, or memory violation.
- Record parameters that affect observability: table size, folded PC width, queue depth, issue/rename width, fetch block size, RVC setting, and reset policy.
- Pick wave signals before running emu. Include at least one upstream cause signal and one downstream state/update signal.

For PC-indexed structures, confirm how PCs are transformed. For example, if source uses `XORFold(pc, width)`, compute the folded index from objdump PCs rather than assuming low bits.

## Test Generation Patterns

Use a minimal app in the existing repository style.

When instruction identity or ordering matters:

- Use file-scope inline assembly for fixed labels and stable PCs.
- Use `.option norvc` when 4-byte instruction spacing matters.
- Use `.balign` to make labels easy to inspect and to shape folded PC indices.
- Use `volatile` shared data and a volatile sink to prevent dead-code removal.
- Use compiler flags such as `-O0`, `-fno-inline`, `-fno-reorder-functions`, and `-fno-reorder-blocks`.
- Use dependent ALU chains, fences, branches, or warmup loops only when they serve the scenario.

For memory-ordering or predictor scenarios:

- Use distinct static load/store PCs and a shared data address when testing PC-indexed predictors.
- Delay store-address readiness with a dependent address chain to encourage speculative load execution.
- Add warmup calls if the first cold-path execution is dominated by frontend/cache fill behavior.
- Keep the trigger sequence short enough that waveform event tables remain easy to inspect.

After building, inspect the image:

```sh
make ARCH=riscv64-xs
riscv64-unknown-elf-objdump -d build/<name>-riscv64-xs.elf
```

Record relevant PCs, instruction labels, and transformed indices in the analysis notes.

## Emu Run Pattern

Use the user's exact run style when provided. A typical full-wave run is:

```sh
./XiangShan/build/emu --no-diff --dump-wave-full --wave-path <test-dir>/build/<scenario>.fst -C <limit> -i <test-dir>/build/<image>.bin
```

Always capture and preserve:

- the exact command
- the image path
- the wave path printed by emu, usually in a `dump wave to ...` line
- the trap/completion line
- instruction count and cycle count when available

If the run fails before the target code, inspect image layout, entry path, unsupported instructions, and timeout before changing the scenario logic.

## Wavekit Analysis Pattern

Use the local wavekit skill first when the user asks for it:

```text
/nfs/home/yanyusong/XiangShanLab/tools/analyze-xiangshan-wavekit/SKILL.md
```

If that skill only points to the local Python package, use the repository it identifies, commonly `/nfs/home/yanyusong/wavekit`, and parse the FST with `FstReader`.

Recommended waveform workflow:

1. Discover the top scope and relevant signal names.
2. Load waveforms sampled on the design clock, usually `TOP.clock`.
3. Print all valid/update events for the chosen signals.
4. Filter reset-time events unless reset is part of the scenario.
5. Correlate upstream cause events with downstream state updates.
6. Convert raw signal values into the source-level cases the user asked for.

Useful event table columns:

- cycle and waveform time
- upstream valid bits and identifiers
- downstream valid bits and state bits
- PC or folded PC
- old/new table values
- case classification
- reason the event does or does not count

## Iteration Heuristics

If the waveform does not reproduce the scenario:

- If the upstream cause is missing, strengthen the instruction pattern: extend dependent chains, adjust addresses, add/remove fences, alter branch path, or increase warmup.
- If the upstream cause appears but downstream state does not, trace source gating and pipeline latency.
- If only some cases appear, reorder the sequence so earlier cases create the exact table/predictor state needed by later cases.
- If PC aliases interfere, change alignment or insert padding and recompute folded indices.
- If predictor state suppresses later violations, add a new static PC, a new address, or a controlled warmup path.
- If reset garbage appears, filter early cycles and prove real events with upstream valid signals.

Rerun the complete loop after each meaningful change:

```text
edit test -> build image -> inspect PCs if needed -> run emu -> record wave path -> wavekit query -> compare criteria
```

## Report Template

A final analysis report should contain:

- Objective and final conclusion.
- Test files changed and why the instruction pattern triggers the scenario.
- Build command and image path.
- Emu command, printed wave path, trap/completion status, and cycle/instruction counts.
- Key source references with file paths and line numbers.
- Key instruction PCs and derived indices/IDs if relevant.
- Wavekit signal list.
- Event table showing every required scenario case.
- Explanation of excluded events such as reset pulses or unrelated updates.
- Iteration notes explaining failed attempts that shaped the final test.
