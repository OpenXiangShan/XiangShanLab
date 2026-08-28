# XiangShan Flow Tracing

## Contents

1. Identity and handshake rules
2. Stage-by-stage trace
3. Control-flow trace
4. Memory and exception trace
5. Minimum rerun signals

## Identity and handshake rules

Discover signal names from the exact generated RTL/FST and source revision; names vary across XiangShan versions and configurations. Do not transplant a suffix from another build without checking it exists and has the expected width.

Use PC and instruction bytes to find the instruction in Decode/Rename. Once allocated, track stable identity:

- ROB index value and wrap/flag bit;
- uop index or instruction slot for fused, vector, split, or replayed operations;
- load/store queue index and wrap bit for memory operations;
- FTQ index/offset for frontend control flow when needed.

PC alone is ambiguous in loops, replay, redirects, and multiple in-flight instances.

For a Decoupled interface:

```text
fire = valid && ready
```

Show both operands at the sampled clock edge. If no ready leaf exists, report `valid` and the observed downstream response; call it `fire` only after proving an always-ready or non-Decoupled contract from source.

## Stage-by-stage trace

Use a table with one row per meaningful transfer or state change:

| Cycle/FST time | Stage/port/lane | valid | ready | fire | PC/bytes | ROB/uop identity | Key payload | Flush/replay |
| --- | --- | ---: | ---: | ---: | --- | --- | --- | --- |

Trace these logical boundaries, adapting names to the revision:

1. Decode output: decoded instruction, source/destination logical registers, FU/op type, exception vector.
2. Rename output: physical source/destination mapping and newly allocated ROB identity.
3. Dispatch: actual enqueue to scheduler/LSQ/ROB; stalled `valid` is not transfer.
4. Issue: selected operands, wakeup provenance, FU request acceptance.
5. Execute: stage valid/kill, operands, intermediate address/result, response and replay.
6. Writeback: result, physical destination, ROB identity, exception/redirect metadata.
7. ROB: completion, exception aggregation, commit lane and architectural destination.
8. Difftest event: commit/CSR/trap/store/AMO/CBO event and any skip flag.

For fused instructions, establish whether difftest observes one architectural instruction or multiple uops. For vector instructions, track `uopIdx`, element range, masks, `vl`, `vstart`, `vtype`, and partial completion. For replay, show each attempt but count only accepted architectural completion.

## Control-flow trace

For branches and jumps, collect:

- decoded type, immediate and source operands;
- predicted taken/target and FTQ identity;
- execution result and resolved target;
- redirect valid/ready or documented redirect contract;
- flush range and first correct-path Decode/Commit event.

For traps and returns, collect exception vector priority, interrupt pending/enable, current privilege, delegation, selected cause, `xepc`, `xtval`, trap vector, and return target. Prove whether the faulting instruction commits, completes exceptionally, is flushed, or is skipped by difftest.

## Memory and exception trace

### Loads

Track issue operands -> VA -> DTLB -> PA/PBMT/PMP/PMA -> StoreQueue forwarding -> DCache/MMIO/NC request -> replay/response -> data formatting -> writeback -> commit. Record byte/element mask, size, sign extension, forwarding source identity, and load queue identity.

### Stores and AMOs

Track address and data uops if split, SQ entry, address/data/mask completion, ordering/ROB-head conditions, StoreBuffer or uncache/CMO request, response, architectural/difftest store event, and the point at which memory becomes externally visible. A normal ROB commit does not by itself prove the store data was correct or visible at that cycle.

### Exceptions

Trace the exact bit from its generating check through writeback/exception aggregation into ROB and CSR trap state. Check priority when multiple faults coexist. Keep VA, PA, access type, size, privilege, translation, PMP/PMA/PBMT, and alignment decisions separate.

## Minimum rerun signals

When the original artifacts contain only a failure log and program image, request or generate a narrow commit trace first. If that cannot close the slice, capture a bounded waveform window around the candidate with:

- clock/reset, cycle and hart;
- Decode/Rename/Dispatch/Issue handshakes and identity payloads;
- relevant execution-unit stage valid/kill/replay and operands/results;
- writeback and ROB completion/commit;
- redirect/flush and exception metadata;
- relevant TLB/PMP/PMA/cache/LSQ/store-buffer/uncache interfaces;
- difftest commit/trap/CSR/store events.

Keep one waveform reader open during queries when the tooling supports it. Verify the clock-to-FST-time relation from actual edges. Limit conclusions to exported signals; absence of a leaf is not proof that an event did not occur.
