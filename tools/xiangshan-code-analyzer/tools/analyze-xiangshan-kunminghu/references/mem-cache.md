# Memory and Cache Reference

Read this file for `src/main/scala/xiangshan/mem` and `src/main/scala/xiangshan/cache` analysis. For every memory request, expand both the memory subsystem (`mem`) and the cache/MMU subsystem (`cache`) when they are on the effective path.

## Mandatory Analysis Scope

For any module under `mem` or `cache`, explain:

- Module boundary and parent instantiation path.
- Instruction categories that can reach it: scalar load, scalar store, FP load/store, vector load/store, AMO/LR/SC, prefetch, fence, CBO, uncache/MMIO, misaligned access, TLB/PTW request, cache probe/refill/writeback.
- Theory-to-code mapping for memory ordering, structural hazards, data hazards, load-store forwarding, replay, precise exceptions, and commit visibility.
- Pipeline stages: list every visible stage and describe exactly what it does, including request accept, address generation, TLB lookup, tag/meta lookup, data access, miss/replay allocation, refill/writeback/probe handling, response/writeback, and commit-visible effects when present.
- Control path: valid/ready/fire, arbiters, mux selects, stalls, flushes, redirects, load cancel, replay, exception, commit release, refill/grant/probe control. For every key control signal, explain why it exists and give a concrete load/store/cache scenario where it changes behavior.
- Data path: uop/address/data/mask/tag/way/set/line/beat/exception metadata movement.
- FSM behavior: explicit `Enum` states and implicit valid/status lifecycles, with reset state, why each state exists, a concrete scenario for each nontrivial state, transition conditions, per-state outputs/actions, and backpressure/cancel/replay behavior.
- Index/allocation algorithms: queue/free-list entry allocation, LSQ/LQ/SQ pointers, replay-entry choice, MSHR/miss entry allocation and merge, TLB/PTW entry selection, cache set/tag/way/bank/beat selection, victim/replacement way selection, and release/free policy.
- Storage structures: queue entries, valid/status bits, pointers, data arrays, tag/meta arrays, miss entries, replay entries, uncache entries, TLB entries, PTW queues.
- Mermaid data-path and module-interface diagrams, plus waveform-draw handshake timing diagrams.

## mem Directory Module Expansion

Top and common:
- `mem/MemBlock.scala`: top-level memory block integration; trace scheduler/exu inputs, LSQ, load/store/atomic units, DCache/TLB connections, writeback/replay/exception outputs.
- `mem/MemCommon.scala`, `mem/Bundles.scala`: shared request/response bundles; inspect fields before explaining signal provenance.
- `mem/MemTrace.scala`: trace/debug only unless connected to functional behavior.
- `mem/MaskedDataModule.scala`: data/mask helper storage; explain when used by store/vector paths.

Pipeline units:
- `mem/pipeline/LoadUnit.scala`: scalar/load address generation, TLB/cache request, exception/replay/writeback path; describe every code-defined stage and what address, mask, uop, TLB, cache, exception, and replay work is performed in that stage.
- `mem/pipeline/StoreUnit.scala`: store address/data path, SQ enqueue/update, address check, commit-visible store handoff; describe store address/data split stages, SQ index allocation/update, mask/data generation, and commit release timing.
- `mem/pipeline/AtomicsUnit.scala`: AMO/LR/SC request generation and ordering; connect to DCache mainpipe AMO path and explain serialization/replay FSM states and index/allocation decisions.
- `mem/pipeline/HybridUnit.scala`: shared or mixed memory execution path; identify which instruction classes use it in the selected branch and split shared stages by work performed.

LSQ:
- `mem/lsqueue/LSQWrapper.scala`: LSQ integration and interfaces to MemBlock, load/store units, ROB/commit, DCache.
- `LoadQueue.scala`, `VirtualLoadQueue.scala`, `LoadQueueData.scala`: load allocation, writeback, load state, data return.
- `LoadQueueRAW.scala`: store-to-load RAW violation/forwarding checks.
- `LoadQueueRAR.scala`: load-load ordering checks when enabled.
- `LoadQueueReplay.scala`: replay reason collection, selection, and request regeneration.
- `LoadQueueUncache.scala`: MMIO/uncached load entry lifecycle and bus request handling.
- `LoadExceptionBuffer.scala`: oldest/priority exception metadata for loads.
- `LoadMisalignBuffer.scala`: split/recombine misaligned scalar/vector loads.
- `StoreQueue.scala`, `StoreQueueData.scala`: store address/data/status, forwarding, commit release, CBO/CMO timing if present.
- `StoreMisalignBuffer.scala`: split/recombine misaligned stores.
- `FreeList.scala`: LSQ entry allocation/free policy; derive the exact free-mask, priority/round-robin/first-free choice, allocated index, release index, wrap behavior, and simultaneous allocate/free handling.

Store buffer:
- `mem/sbuffer/Sbuffer.scala`: committed store buffering, DCache store request issue, forwarding/merge behavior, drain and fence interaction.
- `DatamoduleResultBuffer.scala`: data-module result staging when connected.
- `StorePrefetchBursts.scala`: store prefetch burst generation when enabled.
- `FakeSbuffer.scala`: nonfunctional substitute only when selected by configuration.

Memory dependence prediction:
- `mem/mdp/StoreSet.scala`, `WaitTable.scala`: memory dependence predictor/training; connect to load/store scheduling and replay if effective.

Prefetch:
- `mem/prefetch/L1PrefetchComponent.scala`, `L1PrefetchInterface.scala`: prefetch request integration.
- `BasePrefecher.scala`, `L1StridePrefetcher.scala`, `L1StreamPrefetcher.scala`, `SMSPrefetcher.scala`, `FDP.scala`: training and candidate-generation algorithms.
- `PrefetcherMonitor.scala`: monitor/debug unless it feeds functional control.

Vector memory:
- `mem/vector/VSplit.scala`: split vector memory operations into micro-requests.
- `VMergeBuffer.scala`: merge vector load responses.
- `VSegmentUnit.scala`: segment load/store control and FSM.
- `VfofBuffer.scala`: fault-only-first vector load behavior.
- `VecBundle.scala`, `VecCommon.scala`: vector memory metadata and helper fields.

## cache Directory Module Expansion

Top/common:
- `cache/L1Cache.scala`: L1 cache top integration for DCache/MMU/control paths.
- `cache/CacheConstants.scala`: constants for cache/TLB/DCache behavior.
- `cache/CacheInstruction.scala`: software/cache-control instruction handling; inspect for CBO/cache ops and CSR-visible status.

DCache:
- `cache/dcache/DCacheWrapper.scala`: DCache top-level wrapper; trace load/store/AMO/uncache/prefetch/probe interfaces.
- `cache/dcache/FakeDCache.scala`: nonfunctional substitute only when selected.
- `cache/dcache/CtrlUnit.scala`: flush/control/error/cache-control handling.
- `cache/dcache/Uncache.scala`: uncached/MMIO request FSM and TileLink/outer-bus behavior.
- `cache/dcache/loadpipe/LoadPipe.scala`: load request stages, TLB result, tag/data access, forwarding response, hit/miss/replay/exception; produce a stage table for s0/s1/s2 or the branch's actual stage names with stage work, payload registers, set/bank/way/tag/index calculation, hit/miss decision, replay/cancel handling, and response timing.
- `cache/dcache/storepipe/StorePipe.scala`: store request stages, data mask, tag/meta/data writes, miss/replay/forwarding implications; produce a stage table with address/mask generation, set/bank/way/tag calculation, write-enable generation, and miss/replay behavior.
- `cache/dcache/mainpipe/MainPipe.scala`: serialized main pipeline for miss/refill/store/AMO/probe/writeback operations; identify arbitration priority, pipeline stage ownership, per-state/FSM actions, and entry/way/set allocation or replacement decisions.
- `cache/dcache/mainpipe/MissQueue.scala`: miss allocation, merge, MSHR-like behavior, refill response.
- `cache/dcache/mainpipe/Probe.scala`: coherence probe request handling and conflict with normal accesses.
- `cache/dcache/mainpipe/WritebackQueue.scala`: dirty eviction/writeback queue lifecycle.
- `cache/dcache/mainpipe/AMOALU.scala`: AMO read-modify-write data transform.
- `cache/dcache/mainpipe/AtomicsReplayUnit.scala`: atomics replay and serialization support.
- `cache/dcache/data/AbstractDataArray.scala`, `BankedDataArray.scala`, `DuplicatedDataArray.scala`: cache-line/beat data storage, banking, read/write ports.
- `cache/dcache/meta/TagArray.scala`, `AsynchronousMetaArray.scala`, `LegacyMetaArray.scala`: tag/meta/ECC/replacement-relevant metadata storage.

MMU/TLB:
- `cache/mmu/TLB.scala`: L1 TLB lookup, permission/PMP/PMA/page-fault/access-fault outputs.
- `L2TLB.scala`, `L2TLBMissQueue.scala`: L2 TLB miss flow, refill, replacement.
- `TLBStorage.scala`: TLB entry storage and replacement.
- `PageTableWalker.scala`, `PageTableCache.scala`: PTW walk FSM, page-cache behavior, refill.
- `BitmapCheck.scala`, `MMUBundle.scala`, `MMUConst.scala`, `Repeater.scala`, `L2TlbPrefetch.scala`: permission/checking/bundle/repeater/prefetch support.

WPU:
- `cache/wpu/WPU.scala`, `WPUWrapper.scala`, `VictimList.scala`: way prediction or victim prediction support when enabled; map prediction update and hit/miss correction.

## Instruction-Type Lens

When the user asks about load/store/AMO/prefetch/fence/CBO, trace the operation across decode, backend issue/execute, mem pipeline, LSQ, DCache/MMU, cache-control path, and commit. Also read `load-store-instruction-taxonomy.md`.

For each instruction category, provide:

| Instruction category | Decode/FU marker | mem modules | cache/MMU modules | Pipeline stages and work | FSM/replay states | Index/allocation algorithm | Commit/exception behavior | Special cases |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |

## Scalar Load Path

Trace:

1. Decode classifies LB/LH/LW/LD/LBU/LHU/LWU or hypervisor/custom load variants when present.
2. Dispatch/issue sends load uop to memory scheduler/execution.
3. LoadUnit computes effective address and creates TLB/DCache request metadata.
4. LSQ allocates/tracks ordering metadata; LoadQueueRAW/RAR detect violations and forwarding opportunities.
5. DCache LoadPipe performs TLB/cache/tag/data path; uncache/miss/replay/exception paths split as needed.
6. Load data returns, is sign/zero extended or formatted, writes back, wakes dependents, and updates load queue.
7. Commit or replay/redirect finalizes ordering and exception visibility.

## Scalar Store Path

Trace:

1. Decode classifies SB/SH/SW/SD and store address/data uops if split.
2. StoreUnit computes address/data/mask and updates StoreQueue/StoreQueueData.
3. Store remains speculative until commit authorizes visibility.
4. SBuffer drains committed stores to DCache StorePipe/MainPipe.
5. StoreQueue forwards older store data to younger loads and detects violations.
6. DCache handles tag/meta/data write, miss, refill, writeback, probe conflicts, and uncache/MMIO path.

## Floating-Point Load/Store Path

Trace FLW/FLD and any supported FLH/FSH/quad variants by reading decode/FU config in the selected branch.

Explain:
- Same address/order/cache path as scalar memory where effective.
- Different destination/source register class and writeback path.
- Data formatting, NaN boxing, width conversion, and exception metadata if implemented in code.

## Vector Load/Store Path

Trace vector memory classes from decode through vector memory modules:

- Unit-stride loads/stores: VLE*/VSE*.
- Strided loads/stores: VLSE*/VSSE* when present.
- Indexed loads/stores: VLUXEI/VLOXEI/VSUXEI/VSOXEI when present.
- Segment loads/stores: VLSEG*/VSSEG*.
- Fault-only-first loads: VLE*FF and `VfofBuffer`.
- Whole-register/mask loads/stores when present.

For each vector class, explain splitting, per-element/segment metadata, merge buffers, exception/FOF behavior, LSQ/DCache request generation, and writeback/commit semantics.

## AMO, LR, SC Path

Trace:
- Decode/FU classification for AMO/LR/SC.
- `mem/pipeline/AtomicsUnit.scala`.
- DCache mainpipe atomics and `AMOALU.scala`.
- Replay path through `AtomicsReplayUnit.scala`.
- Ordering, reservation, success/failure result, and commit constraints.

Explain why atomicity requires serialization or replay and where the read-modify-write data transform happens.

## Prefetch Path

Trace:
- Software prefetch instructions/hints if decoded.
- Hardware prefetcher training and generation under `mem/prefetch`.
- Store prefetch bursts when enabled.
- Cache request path and drop/filter/backpressure conditions.

Explain who trains, who issues, what is speculative, and what has no architectural writeback.

## Fence and CBO Path

Search across:
- `backend/decode/Instructions.scala` and related decode/FU config
- `backend/fu/Fence.scala`
- `cache/CacheInstruction.scala`
- `mem/MemBlock.scala`, `mem/pipeline`, `mem/lsqueue`, `mem/sbuffer`, `cache/dcache`, and `cache/wpu`

For fence:
- Explain ordering point, older/younger memory constraints, store queue/sbuffer drain, and frontend/cache effects such as fence.i if present.

For CBO/CMO:
- Identify the exact instruction variant in decode.
- Trace request representation, issue point, cache-control path, affected cache structures, completion/ack, and ordering with stores/loads.
- Do not assume all CBO variants share one path.

## DCache and MMU Structures

For DCache:
- Data arrays: duplicated or banked data array files.
- Meta/tag arrays: tag/meta files.
- Main pipe: arbitration among load/store/AMO/probe/refill/writeback.
- MissQueue: miss allocation, merge, refill response.
- WritebackQueue: dirty eviction.
- Probe: coherence requests.
- Uncache: MMIO or uncached accesses.

For MMU/TLB:
- L1/L2 TLB storage and replacement.
- Page table walker and page table cache.
- Permission, page fault, access fault, PMP/PMA-related checks when connected.

Always separate speculative memory dependence tracking from architecturally committed memory effects.

## Extra Memory/Cache Analysis Requirements

For mem/cache modules, algorithm analysis must cover load-store ordering, forwarding, replay selection, miss merging, replacement, refill, writeback, probes, TLB/PTW walks, permission checks, vector split/merge, misaligned split/recombine, uncache/MMIO, AMO serialization, and CBO/fence ordering when relevant. For every behavior-changing control signal or FSM state, include why it exists and an example scenario such as a TLB miss, DCache miss, full replay queue, store-load forwarding hit, load cancel, probe conflict, refill response, commit release, or exception. Pipeline stage analysis is mandatory for every mem/cache/XSCache path: identify each stage, the work performed in that stage, the payload/control registers, the valid/ready or enable condition, stall/flush/replay behavior, and output handoff. FSM analysis is mandatory for uncache, atomics, misalign buffers, miss queues, writeback queues, PTW, vector segment/FOF buffers, cache-control units, and cache mainpipe/probe handling when state exists. Index/allocation analysis is mandatory for LSQ/LQ/SQ/free-list entries, replay entries, MSHR/miss entries, PTW entries, TLB entries, cache set/bank/way/beat selectors, victim/replacement ways, store-buffer slots, vector split/merge slots, and CBO/fence queue slots when present.


## Exception/Interrupt/Debug/Privilege Checks

For memory/cache modules, always inspect exception and privilege metadata paths:

- load/store/AMO address misalignment
- TLB page fault, guest page fault, and access fault
- PMP/PMA permission checks
- uncache/MMIO non-data errors
- debug trigger/breakpoint/dmode metadata if carried through memory bundles
- privilege mode fields that affect TLB, PMP/PMA, CSR/cache-control, or hypervisor memory behavior
- exception buffering and commit priority for load/store operations

## Queue/Buffer Capacity Checks

For LSQ, LQ/SQ, replay queues, uncache buffers, misalign buffers, SBuffer, MissQueue, WritebackQueue, ProbeQueue, PTW queues, prefetch queues, and vector merge/split buffers, explicitly derive empty/full/almost-full and ready/backpressure conditions from code.


## XSCache / Outer Cache Expansion

When the effective path leaves XiangShan L1 DCache/MMU and reaches L2/L3/CHI cache subsystem code, read `xscache.md` and expand the XSCache modules. Cover CoupledL2, OpenLLC, CHI channels, directory, data storage, request buffers, MSHRs, grant/response paths, probe/snoop paths, prefetchers, MMIO bridges, queue/buffer capacity, and speculative/retry behavior.
