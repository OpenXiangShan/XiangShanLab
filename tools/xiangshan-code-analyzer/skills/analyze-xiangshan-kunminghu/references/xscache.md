# XSCache Reference

Use this file when the user asks about XSCache, L2/L3 cache subsystem, CHI cache subsystem, CoupledL2, OpenLLC, TileLink-to-CHI, cache coherence, or when a XiangShan memory/cache request reaches an outer cache module.

## Repository

- GitHub: `https://github.com/OpenXiangShan/XSCache.git`
- Main source root: `src/main/scala`
- Major packages: `coupledL2`, `openLLC`, `xscache/chi`, `xscache/common`
- README summary: XSCache is a CHI-only cache subsystem built from `CoupledL2 (tl2chi)` and `OpenLLC`.

## Mandatory Analysis Scope

For XSCache modules, include:

- Module boundary and parent instantiation path.
- Cache level role: L2 slice, LLC slice, CHI bridge, directory, data storage, request buffer, MSHR, prefetcher, link/network layer, or MMIO bridge.
- Protocol role: TileLink side, internal mainpipe side, CHI TX/RX channel, snoop path, grant/response path, data path.
- Theory-to-code mapping for cache hierarchy, coherence, miss handling, MSHR, directory, refill, writeback, probe/snoop, backpressure, and prefetch.
- Pipeline stages: each request/mainpipe/MSHR/refill/probe/response stage, what work it performs, which payload/control registers it owns, and what can stall, retry, replay, merge, or cancel it.
- Control path: valid/ready/fire, channel arbitration, MSHR allocation, mainpipe arbitration, refill/grant response, probe/snoop conflict, retry/replay, sink/source selection. For every key control signal, explain why it exists and give a concrete L2/LLC/CHI scenario where it changes behavior.
- Data path: address/set/tag/way/dir-state/data-line/beat/source-id/txn-id/opcode/param movement.
- FSM behavior: MSHR, mainpipe, request/grant/refill/probe/CHI channel state transitions, including reset state, why each state exists, a concrete scenario for each nontrivial state, entry condition, per-state outputs/actions, and exit condition.
- Index/allocation algorithms: request-buffer slot selection, MSHR entry allocation and secondary-merge lookup, directory set/tag/way lookup, replacement/victim way selection, data-bank/beat selection, source-id/txn-id allocation, grant/response buffer slots, and channel queue slots.
- Queue/buffer capacity: request buffer, MSHR buffer, grant buffer, channel queues, prefetch queues, data storage queues, link-layer buffers.
- Exception/privilege note: outer cache generally handles physical/coherent transactions, but still explain error/denied/corrupt/response status signals when present.
- Mermaid data-path and module-interface diagrams, plus waveform-draw handshake timing diagrams.

## Package Map

### `coupledL2`

Analyze these modules for L2 behavior:

- `CoupledL2.scala`: top-level CoupledL2 integration.
- `Slice.scala`, `BaseSlice.scala`: per-slice organization.
- `SinkA.scala`, `SinkC.scala`, `SourceB.scala`: TileLink-side request/probe/release channels.
- `RequestBuffer.scala`: request buffering and scheduling; must analyze full/empty/backpressure and the slot allocation/free algorithm.
- `RequestArb.scala`: arbitration into mainpipe or MSHR paths.
- `MainPipe.scala`: core L2 pipeline; hit/miss/probe/refill/writeback control and data movement. Produce a stage table with directory/data access, set/tag/way/beat calculation, replacement/victim choice, MSHR interaction, probe/refill/writeback actions, and output handoff.
- `MSHR.scala`, `MSHRCtl.scala`, `MSHRBuffer.scala`: miss state, secondary miss merge, refill/grant, queue capacity, replay/wakeup. Derive the MSHR index allocation policy, conflict/merge lookup, entry FSM, why each state/control signal exists, retry/replay behavior, scenario examples, and release/free timing.
- `Directory.scala`: tag/directory state lookup/update/replacement.
- `DataStorage.scala`: data SRAM/line/beat access.
- `GrantBuffer.scala`: grant/data response buffering.
- CHI-facing modules: `TXREQ.scala`, `TXDAT.scala`, `TXRSP.scala`, `RXRSP.scala`, `RXDAT.scala`, `RXSNP.scala`, `LinkMonitor.scala`.
- `MMIOBridge.scala`: MMIO path when present.
- `CustomL1Hint.scala`: hint/custom L1 interaction if effective.
- `prefetch/*`: L2 prefetch receiver/generator and speculative prefetch path.
- `utils/*`: SRAM, replacement, queues, throttling, overwrite queues; inspect when instantiated by functional modules.

### `openLLC`

Analyze these modules for LLC/L3 behavior:

- `OpenLLC.scala`: top-level LLC integration.
- `Slice.scala`: slice organization.
- `RequestBuffer.scala`, `RequestArb.scala`: request buffering/arbitration and capacity logic.
- `MainPipe.scala`: LLC pipeline and directory/data coordination. Produce a stage table with directory/data access, set/tag/way/beat calculation, replacement/victim choice, miss/refill/writeback/probe actions, and output handoff.
- `Directory.scala`: LLC directory/tag/coherence state.
- `DataStorage.scala`: LLC data SRAM.
- `MemUnit.scala`, `RefillUnit.scala`, `ResponseUnit.scala`, `SnoopUnit.scala`: downstream memory/refill/response/snoop behavior.
- `DummyLLC.scala`: fake/substitute only when selected.
- `chi/*`: CHI TX/RX channels and snoop/data/response flows.
- `utils/*`: CHI crossbar, MMIO bridge, non-cache buffer, target binding.

### `xscache/chi`

Analyze these modules for CHI protocol plumbing:

- `CHIChannel.scala`: channel bundle definitions.
- `Opcode.scala`, `Message.scala`: opcode/message taxonomy.
- `NetworkLayer.scala`, `LinkLayer.scala`, `AsyncBridge.scala`: channel transport, buffering, link behavior.
- `CHILogger.scala`: debug/logging unless used functionally.

### `xscache/common`

- `CacheCommon.scala`, `BundleFields.scala`: common fields and constants.
- `CustomAnnotations.scala`: annotations; usually not functional unless affecting generated hardware.

## Request-Type Lens

For each XSCache request path, classify:

| Request class | Entry channel | Main modules | Pipeline stages and work | FSM/retry states | Index/allocation algorithm | Directory/data action | Response channel | Speculative/retry behavior |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |

Classes to consider:
- L1 load miss / acquire / read shared.
- L1 store miss / acquire for permission / writeback/release.
- AMO/atomic request if supported on path.
- Prefetch / hint request.
- Probe/snoop request from outer coherence fabric.
- Refill/data response from memory or lower cache.
- Writeback/release to lower cache/memory.
- MMIO/uncache bridge request when present.

## Coherence and Directory Analysis

Always identify:

- Directory state fields and valid/tag/way information.
- Who reads directory and who updates it.
- Hit/miss/replacement rule.
- Probe/snoop generation and response collection.
- MSHR ownership of outstanding miss/coherence transaction.
- DataStorage read/write and writeback conditions.
- CHI/TL opcode mapping when crossing protocol boundaries.

## Speculative Path Requirements

For XSCache, speculative path may include:

- Prefetch request accepted before architectural demand.
- Secondary miss merge before refill completes.
- Probe/snoop race with demand miss.
- MSHR allocation before data arrives.
- Directory replacement candidate selection before final grant.
- Retry/replay when a conflict, full queue, or coherence hazard blocks progress.

State what is speculative, how it is validated or canceled, and what state is rolled back or simply dropped.

## Queue/Buffer Capacity Requirements

For every XSCache queue/buffer/MSHR/channel buffer, derive:

- Capacity parameter.
- Occupancy representation.
- Empty/full/almost-full condition.
- Enqueue/dequeue fire conditions.
- Backpressure target.
- Interaction with channel ready/valid.
- Flush/retry/reset behavior.
