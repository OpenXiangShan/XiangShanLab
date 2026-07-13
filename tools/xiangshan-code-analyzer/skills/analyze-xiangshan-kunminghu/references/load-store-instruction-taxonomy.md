# Load/Store Instruction Taxonomy

Use this file whenever the user asks about memory instructions or any module in `mem` or `cache`. The goal is to expand every load/store-class instruction category into its effective XiangShan path.

## Required Output

For all relevant categories, produce a table:

| Category | Example mnemonics | Decode/FU marker to search | mem path | cache/MMU path | Key control/data signals | Corner cases |
| --- | --- | --- | --- | --- | --- | --- |

Then provide per-category prose for categories that reach the requested module.

## Scalar Integer Loads

Examples:
- Signed loads: `LB`, `LH`, `LW`, `LD`
- Unsigned loads: `LBU`, `LHU`, `LWU`
- Possible privileged/custom variants: HLV/HLVX or other branch-specific load variants if present in decode.

Search terms:
- `LB`, `LH`, `LW`, `LD`, `LBU`, `LHU`, `LWU`, `LoadUnit`, `LduCfg`, `fuOpType`, `loadAddrMisaligned`.

Path to explain:
1. Decode and FU type/op type classification.
2. LoadUnit address generation and mask/size/type metadata.
3. TLB/PMP/PMA permission and exception path.
4. DCache LoadPipe hit/miss/data path.
5. LoadQueue allocation, RAW/RAR checks, replay, violation, exception buffer.
6. StoreQueue forwarding and mask matching.
7. Sign/zero extension or data formatting.
8. Writeback and wakeup.
9. Commit-visible exception/replay behavior.

Special cases:
- Misaligned loads: `LoadMisalignBuffer` split and recombine.
- Uncached/MMIO loads: `LoadQueueUncache` and `cache/dcache/Uncache.scala`.
- Page/access faults and load-address misaligned exceptions.

## Scalar Integer Stores

Examples:
- `SB`, `SH`, `SW`, `SD`

Search terms:
- `SB`, `SH`, `SW`, `SD`, `StoreUnit`, `Sta`, `Std`, `storeAddrMisaligned`, `StoreQueue`, `Sbuffer`.

Path to explain:
1. Decode and possible split between store-address and store-data uops.
2. StoreUnit address/data/mask generation.
3. StoreQueue address/data/status allocation and update.
4. Store remains speculative until commit.
5. StoreQueue forwarding to younger loads.
6. SBuffer receives committed store and drains to DCache.
7. DCache StorePipe/MainPipe tag/meta/data write or miss handling.
8. Exception/redirect/flush behavior.

Special cases:
- Misaligned stores: `StoreMisalignBuffer` split and replay/writeback behavior.
- MMIO/uncached stores: uncache path and ordering restrictions.
- CBO/CMO timing if implemented through StoreQueue or cache-control paths.

## Floating-Point Loads and Stores

Examples:
- Loads: `FLW`, `FLD` and any supported `FLH`/other variants in the selected branch.
- Stores: `FSW`, `FSD` and any supported `FSH`/other variants in the selected branch.

Search terms:
- `FLW`, `FLD`, `FSW`, `FSD`, `FPDecoder`, `Fp`, `fpWen`, `LoadUnit`, `StoreUnit`.

Path to explain:
- Address/order/cache path is usually shared with scalar memory operations.
- Register class and writeback/source path differ: FP physical registers and FP writeback/source metadata.
- Explain width conversion, NaN boxing, exception propagation, and store data formatting only when visible in effective code.

## Vector Loads and Stores

Examples to search and classify if present:
- Unit-stride: `VLE8/16/32/64`, `VSE8/16/32/64`
- Strided: `VLSE*`, `VSSE*`
- Indexed unordered/ordered: `VLUXEI*`, `VLOXEI*`, `VSUXEI*`, `VSOXEI*`
- Segment: `VLSEG*`, `VSSEG*`
- Fault-only-first: `VLE*FF`
- Whole-register and mask loads/stores when present.

Search terms:
- `VLE`, `VSE`, `VLSE`, `VSSE`, `VLUX`, `VLOX`, `VSUX`, `VSOX`, `VLSEG`, `VSSEG`, `VLE8FF`, `VfofBuffer`, `VSegmentUnit`, `VSplit`, `VMergeBuffer`.

Path to explain:
1. Decode vector memory instruction and `vtype/vl/vstart/mask` metadata.
2. Split into element/segment memory requests via vector memory modules.
3. LSQ allocation and DCache request generation.
4. Merge responses for vector loads.
5. FOF behavior and exception suppression/truncation rules when present.
6. Store data/mask generation for vector stores.
7. Commit/replay/exception behavior.

## AMO, LR, SC

Examples:
- `LR.W`, `LR.D`, `SC.W`, `SC.D`
- `AMOSWAP`, `AMOADD`, `AMOXOR`, `AMOAND`, `AMOOR`, `AMOMIN`, `AMOMAX`, `AMOMINU`, `AMOMAXU` with W/D variants.

Search terms:
- `AMO`, `LR`, `SC`, `AtomicsUnit`, `AMOALU`, `AtomicsReplayUnit`, `aq`, `rl`.

Path to explain:
- Decode classification and memory ordering bits.
- Atomic unit request path.
- DCache mainpipe serialization and AMOALU data transform.
- LR/SC reservation/success-failure semantics if implemented in selected code.
- Replay and exception behavior.

## Prefetch, Fence, CBO/CMO

Examples:
- Prefetch hints/instructions if decoded.
- `FENCE`, `FENCE.I` when present on memory/cache path.
- CBO/CMO variants such as clean/flush/invalidate/zero if decoded in selected branch.

Search terms:
- `PREFETCH`, `prefetch`, `Fence`, `FENCE`, `CBO`, `CMO`, `CacheInstruction`, `CacheCtrl`, `Sbuffer`, `StoreQueue`.

Path to explain:
- Whether the operation has architectural data result.
- Which ordering point or cache structure it affects.
- Which modules acknowledge completion.
- How it interacts with older stores, younger loads, DCache miss/probe/writeback, and frontend when applicable.

## Per-Category Questions

For each load/store category that reaches the requested module, answer:

- Who creates the uop and operation type?
- Why does this category need special handling?
- How is address, data, mask, size, sign, and exception metadata generated?
- From what module/signals does the request arrive?
- To what module/signals does it go next?
- Which storage structures allocate entries?
- Which control signals stall, replay, cancel, or commit it?
- Which cache/MMU structures are touched?
- What changes for hit, miss, TLB miss, page fault, access fault, misalign, uncache/MMIO, store forwarding, violation, redirect, and commit?
