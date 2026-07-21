# Difftest Signal and State Analysis

Use this file when analyzing XiangShan difftest instrumentation, reference-model-visible events, architectural state dumps, cache-state dumps, memory-address events, or per-queue state exposed for verification/debug.

## Discovery

Search the analyzed XiangShan commit for these terms before explaining difftest behavior:

- `difftest`, `DiffTest`, `Difftest`, `DifftestModule`, `DifftestBundle`, difftest package imports.
- Architectural state: `ArchIntRegState`, `ArchFpRegState`, `ArchVecRegState`, `CSRState`, `mstatus`, `sstatus`, `satp`, `hgatp`, `vsatp`, `vl`, `vtype`, `fflags`, `frm`, `fcsr`.
- Commit/trap events: `InstrCommit`, `TrapEvent`, `Exception`, `Interrupt`, `Debug`, `Rob`, `Commit`, `Redirect`, `TrapHandle`.
- Memory address events: `LoadEvent`, `StoreEvent`, `Atomic`, `MMIO`, `paddr`, `vaddr`, `gpaddr`, `addr`, `mask`, `data`, `exception`.
- Cache state: `L1`, `L2`, `Cache`, `DCache`, `ICache`, `meta`, `tag`, `data`, `dirty`, `valid`, `state`, `way`, `set`, `bank`, `MSHR`, `refill`, `writeback`.
- Queue state: `Queue`, `Buffer`, `ROB`, `FTQ`, `IssueQueue`, `LoadQueue`, `StoreQueue`, `ReplayQueue`, `MissQueue`, `MSHR`, `SBuffer`, `WBuffer`, `StoreBuffer`, `Ibuffer`.

## Architectural State Boundary

Separate three classes of state:

1. RISC-V specification architectural state: committed integer, floating-point, and vector register state; CSR state; privilege/debug state; trap/exception/interrupt state; and architecturally visible memory effects.
2. Reference-model-visible difftest events: commit, trap, interrupt, load/store/address, CSR, and cache-state events that the harness consumes even when they are not architectural registers.
3. Microarchitectural state: speculative queues, pipeline registers, cache replacement metadata, MSHR state, replay buffers, predictor state, and debug-only signals. Treat this as architectural only if the code proves the difftest harness compares it architecturally.

Always state which class each signal belongs to and why.

## Required Trace Axes

For every difftest signal or event, answer:

- Producer: module/class and exact Chisel lines creating or assigning it.
- Enable: valid, fire, commit, trap, interrupt, flush, replay, reset, or plusarg/config gating condition.
- Payload: fields, widths, encoding, and any parameter dependence.
- Timing: same cycle as commit/trap/load/store/cache event, delayed by registers, or sampled from a queue/state array.
- Provenance: source architectural/microarchitectural signal, queue entry, cache array, CSR register, memory pipeline, or exception metadata.
- Consumer meaning: what the difftest harness or reference model is expected to compare or record.
- Speculation boundary: whether the signal is pre-commit speculative, commit-visible, trap-visible, interrupt-visible, or debug/cache-state-only.

## RISC-V Architectural State Checklist

| State class | Must trace |
| --- | --- |
| Integer registers | x0 handling, writeback/commit source, physical-to-architectural mapping if applicable, valid mask, commit timing |
| Floating-point registers | FP writeback source, NaN-boxing or width formatting, `fflags/frm/fcsr`, valid/dirty conditions |
| Vector registers | vector writeback source, element width/grouping metadata, `vl/vtype/vstart/vxsat/vxrm`, mask/tail policy fields when present |
| CSR state | CSR module ownership, trap-updated CSRs, privilege/virtualization CSRs, counter/debug CSRs, legality/filtering |
| Exception/trap | exception source, priority, `cause/tval/tinst/gva`, ROB/commit selection, trap redirect and CSR update timing |
| Interrupt | pending/enable/delegation/filtering path, AIA/APLIC/IMSIC source when present, priority versus exception/debug, sampled cycle |
| Memory address | load/store/AMO virtual address, physical address, guest physical address when present, mask/size/data, exception suppression or replay |

## Cache-State Checklist

When difftest exposes cache state, do not summarize as hit/miss only. Trace:

- Which cache level/module owns the state: ICache, DCache, L2/XSCache, LLC, or queue around it.
- Set/bank/way/beat index calculation and source address bits.
- Tag/meta/data/valid/dirty/coherence/replacement fields and reset values.
- Update/release/replace/search behavior for fills, hits, evictions, writebacks, probes, invalidations, fences, and CBO/CMO.
- Which state is sampled for difftest, when it is sampled, and whether it is architectural/reference-model-visible or only verification debug.

## Per-Queue State Checklist

For each queue whose state is requested or feeds difftest, include:

| Queue | Owner | Entry payload | Valid/status states | Enqueue/update | Dequeue/release | Search/probe | Empty/full/backpressure | Flush/replay/trap effect | Difftest relevance |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |

Cover ROB, FTQ, dispatch/issue queues, wakeup queues, load/store queues, replay queues, miss/MSHR queues, store buffers, writeback queues, interrupt/trap queues, and cache channel queues when present. For every queue state, distinguish a normal live entry, speculative entry, replayed entry, exception-bearing entry, committed entry, and invalid/free entry if the code represents those states.

## Output Requirements

In the final analysis, include a dedicated `Difftest Signal Coverage` section with:

| Difftest signal/event | State class | Producer lines | Enable/timing | Payload fields | From what | To what/reference meaning | Speculative or committed | Corner case |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |

Then include:

- One Mermaid diagram from architectural producers to difftest modules/events.
- One waveform-draw timing diagram for commit/trap/load-store/cache-state sampling if the timing spans multiple cycles.
- A risk list naming any uncovered architectural state, ambiguous enable condition, untraced queue/cache state, or signal that appears debug-only rather than reference-model-visible.
