# Memory and Cache Reference

Use for `mem`, `cache`, MMU/TLB, load/store/AMO, prefetch, fence/CBO, MMIO/uncache, or XSCache analysis. Read only the path-specific sections that apply.

## 1. Mandatory Trace

Trace the effective path from decode/FU to memory/cache and back to writeback/commit:

1. Instruction classification and uop fields.
2. Address generation, alignment/misalignment, mask/data formation.
3. Dependency/order check, LSQ allocation, forwarding, replay/cancel.
4. TLB/PTW and permission/PMP/PMA/PBMT/MMIO classification.
5. Cache request, tag/meta/data lookup, hit/miss, MSHR/refill/writeback/probe.
6. Response assembly, exception propagation, writeback, commit, and release.

For every stage/state/queue, cite exact source lines and state valid/ready/fire, payload registers, stall/replay/flush, index/allocation, and downstream handoff. Use `algorithm-control-dataflow.md`, `queue-buffer-capacity.md`, `cross-boundary-analysis.md`, and `diagrams.md` for shared rules.

## 2. Module Map

| Function | Inspect first | Must explain |
| --- | --- | --- |
| Memory entry | `mem/MemBlock.scala`, `mem/pipeline`, `mem/lsqueue`, `mem/sbuffer` | issue, address/data paths, ordering, replay, commit |
| DCache | `cache/dcache`, main pipe, meta/data arrays | arbitration, hit/miss, bank/way/beat, refill, writeback, probe |
| TLB/MMU | `cache/mmu/TLB.scala`, `L2TLB.scala`, `PageTableWalker.scala` | lookup, PTW, replacement, permission, context flush |
| Uncache/MMIO | `InstrUncache`, `Uncache`, MMIO bridges | entry allocation, handshake, ordering, side effects, response/error |
| XSCache | `xscache.md`, XSCache checkout | L2/L3/CHI request, MSHR, directory, probe, grant/response |

## 3. Instruction-Class Matrix

Use the rows that are present in the analyzed commit; do not assume every class exists.

| Class | Required path-specific checks |
| --- | --- |
| Scalar load | AGU → LQ → TLB/DCache → forwarding/replay → writeback/commit |
| Scalar store | AGU/data → SQ → forwarding/order → commit authorization → cache/SBuffer drain |
| FP load/store | FP decode/FU and width/data conversion plus scalar memory ordering |
| Vector load/store | element/segment split, VL/FOF, merge buffer, per-lane exceptions, LSQ/cache requests |
| AMO/LR/SC | atomic read-modify-write, reservation/success, serialization, retry/replay, commit result |
| Prefetch | producer/training, speculative classification, request filtering/drop, cache backpressure, no architectural writeback |
| Fence/CBO | decode variant, ordering/drain/ack, affected cache structure, interaction with stores/loads/fence.i |

For each present class, state the exact instruction marker, effective module path, speculative versus committed effects, and source evidence.

## 4. Address, Translation, and Boundary Rules

Analyze address formation before cache behavior:

- Derive virtual/physical address, page offset, line offset, set/bank/way/beat, mask, and alignment.
- For page crossing, perform independent translation, permission, ASID/VMID/PBMT/PMP/PMA checks and merge exception state.
- For cache-line crossing, split requests, independently resolve hit/miss and MSHR/refill resources, then prove response ordering/assembly.
- For MMIO/uncache, trace memory-type classification, uncache entry, request/response, ordering/commit gate, resend, error, and redirect cancel.
- For misaligned accesses, identify split/recombine buffers, partial masks, exception priority, and replay/commit behavior.

Load `cross-boundary-analysis.md`; do not describe a split access as one atomic transaction unless code proves it.

## 5. Ordering, Replay, and Exceptions

Cover only behavior reachable in the path:

- LSQ dependency/violation, forwarding, load cancel, replay selection, and livelock/progress.
- Store visibility, SQ/SBuffer drain, commit ordering, fence serialization.
- TLB page/guest/access fault, misalignment, PMP/PMA/PBMT/MMIO error, debug/trigger, and commit priority.
- Flush/redirect/context switch: affected entries, stale translation/data prevention, retry target, and release condition.

Use `exception-debug-privilege.md` and `difftest.md` to separate speculative state from architectural state.

## 6. Cache and MMU Checklist

For each cache-like structure, explain:

| Area | Questions |
| --- | --- |
| Lookup | tag/meta/data timing, valid/permission, set/bank/way/beat index |
| Hit/miss | hit response, miss allocation, merge, full behavior, requester backpressure |
| Refill/evict | refill beats, victim/replacement, dirty/writeback, ECC/corrupt/error |
| Probe/coherence | request priority, response/grant, invalidation, retry, ordering |
| TLB/PTW | entry lookup/replacement, PTW allocation, refill, fence/context invalidation |
| Capacity | MSHR/queue/entry empty/full/almost-full, simultaneous allocate/free, wrap |
| Maintenance | flush, fence.i, CBO, WFI, prefetch/demand priority |

## 7. Required Output

Include:

- `Who / Why / How / From / To` for each major block and signal group.
- Pipeline stage table with work, payload/registers, valid/ready, stall/replay/flush, output, and source lines.
- Algorithm/FSM/scenario tables from `analysis-template.md`.
- Mermaid data/interface/FSM diagrams and `waveform-draw` handshake timing when nontrivial.
- `跨边界代码解析` and `验证特别注意` when reachable.
- Separate latency from throughput; state fixed, best-case, miss-dependent, and commit/serialization contributors.

## 8. XSCache Expansion

When the path leaves XiangShan L1/MMU, read `xscache.md` and expand only effective XSCache modules: CoupledL2/OpenLLC, CHI channels, directory/data arrays, request buffers/MSHRs, grant/response, probes/snoops, prefetch, MMIO bridge, capacity, retry, and speculative behavior.
