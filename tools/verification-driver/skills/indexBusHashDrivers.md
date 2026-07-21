# XiangShan Index, Bus Protocol, and Hash Conflict Drivers

Use this file when a module contains index calculations, address slicing, hashing, banking, set selection, way selection, queue pointers, table pointers, or any bus protocol interface.

Each selected driver must be refined from effective code evidence: index expression, parameter width, address bit slice, modulo/hash function, pointer update, bus bundle fields, protocol state, and all consumers.

## Index Boundary Driver Shape

```markdown
## Index Boundary Verification
| Index ID | Owner | Expression/source | Boundary stimulus | Expected index/state | Checkers |
| --- | --- | --- | --- | --- | --- |
```

For every index, include:

- Width and range: parameter source, index bit width, legal min/max, reserved values, and wrap condition.
- Source fields: PC bits, virtual address bits, physical address bits, ROB/FTQ/LSQ pointer, bank id, set id, way id, MSHR id, PTW id, queue head/tail, beat id, lane id, byte mask, or hash result.
- Boundary values: `0`, `1`, `max-1`, `max`, wrap from `max` to `0`, invalid/out-of-range encoding if representable, and all reserved slots.
- Cross-boundary values: cache-line boundary, page boundary, superpage boundary, fetch-block boundary, vector element boundary, beat boundary, bank boundary, set boundary, way boundary, queue pointer wrap, ROB/FTQ snapshot wrap, and burst boundary.
- Consumer checks: selected entry, valid bit, data row, metadata row, replacement state, permission entry, queue entry, replay entry, or protocol beat must match the computed index.

## Index Boundary Drivers

| Index ID | Index class | Boundary stimulus | Expected behavior | Checkers |
| --- | --- | --- | --- | --- |
| `I_MIN_MAX` | Generic index | Access index `0`, `1`, `max-1`, `max` | Only legal entries selected; no off-by-one access | Index checker |
| `I_WRAP_PTR` | Circular pointer | Fill/drain until head/tail/enq/deq pointer wraps | Empty/full/almost flags and pointer phase match code | Pointer/occupancy checker |
| `I_MULTI_ALLOC_WRAP` | Multi-port allocation pointer | Allocate/free across wrap with max dispatch/commit width | Allocation vector contains legal unique entries | Free-list/ROB/LSQ checker |
| `I_PC_FETCH_BLOCK` | PC/fetch index | PCs at fetch-block start/end and crossing boundary | FTQ/IBuffer/predictor/icache index matches code slice | Frontend index checker |
| `I_CACHE_SET_WAY` | Cache set/way index | Addresses mapping to set `0`, `1`, `last`, same set different tag | Tag/data/meta/replacement entry selected correctly | Cache index checker |
| `I_BANK_SELECT` | Bank index | Addresses or lanes mapping to each bank and same bank | Bank select and conflict signal match code | Bank conflict checker |
| `I_BEAT_BYTE_MASK` | Beat/byte index | Access first/last beat, unaligned mask, full-line mask | Data beat, byte mask, and `last` behavior correct | Beat/mask checker |
| `I_PAGE_BOUNDARY` | Page/VPN/PPN index | Addresses around 4 KiB, superpage, guest-page boundaries | TLB/PTW/PMP/PMA/IOPMP index and fault metadata correct | MMU/protection checker |
| `I_QUEUE_ENTRY` | Queue entry index | Enqueue/dequeue/search oldest/youngest entries at boundaries | Valid/search/free result uses intended entry | Queue checker |
| `I_REPLAY_MSHR_PTW` | Replay/MSHR/PTW id | Allocate all entries, hit first/last, merge, free, reallocate | Id reuse only after legal release; no duplicate live id | Resource checker |
| `I_VECTOR_LANE_ELEMENT` | Vector lane/element index | First/last element, mask boundary, segment crossing | Lane/element routing and exception index correct | Vector checker |
| `I_AXI_ID_BURST` | Bus id/beat index | First/last ID, first/last burst beat, max burst length | Outstanding ID, beat counter, and `last` are correct | AXI protocol checker |

## Bus Protocol Driver Shape

```markdown
## Bus Protocol Verification
| Bus ID | Channel/interface | Protocol stimulus | Expected protocol behavior | Checkers |
| --- | --- | --- | --- | --- |
```

For every bus interface, identify master/slave role and all channel directions before writing tests.

Minimum bus protocol coverage:

- Valid/ready payload stability: payload fields remain stable while `valid=1` and `ready=0`.
- Fire rule: transfer occurs exactly when `valid && ready`.
- Backpressure: each channel can stall independently without corrupting other channels.
- Ordering: in-order channels remain ordered; ID-based channels preserve per-ID ordering.
- Outstanding limit: no ID, source, sink, MSHR, or buffer entry is reused while live.
- Burst/beat: `len`, `size`, `burst`, beat count, byte mask/strb, and `last` agree.
- Response: `resp`, error, deny, corrupt, fault, and retry are propagated to the architectural or microarchitectural consumer.
- Reset/flush: bus state returns to idle or drains safely; killed speculative requests cannot commit illegal results.
- Protection/context: privilege, process, VM, and supervisor-domain metadata is stable or rechecked for outstanding protected transactions.

## Bus Protocol Drivers

| Bus ID | Protocol | Stimulus | Expected behavior | Checkers |
| --- | --- | --- | --- | --- |
| `B_AXI_AW_STALL` | AXI AW | Hold `aw.valid`, drop `aw.ready`, vary address/id candidate | AW payload stable; one address accepted on fire | AXI handshake checker |
| `B_AXI_W_STALL` | AXI W | Stall W mid-burst and on last beat | W data/strb/last stable; beat counter advances only on fire | AXI burst checker |
| `B_AXI_B_BACKPRESSURE` | AXI B | Deassert B ready with completed writes | B response held; outstanding write released only on B fire | AXI response checker |
| `B_AXI_AR_STALL` | AXI AR | Multiple read requests with AR backpressure | AR payload stable; outstanding allocation only on fire | AXI outstanding checker |
| `B_AXI_R_BACKPRESSURE` | AXI R | Stall R across multi-beat read | R id/data/resp/last stable; per-ID order preserved | AXI read checker |
| `B_AXI_ID_REUSE` | AXI ID | Exhaust all IDs, attempt reuse before response | Ready deasserts or allocation stalls; no ID alias | AXI ID checker |
| `B_AXI_BURST_BOUNDARY` | AXI burst | Max len, line boundary, page/MMIO boundary, narrow writes | Beat count, `last`, mask, and response match protocol/code | AXI burst checker |
| `B_AXI_XBAR_ROUTE` | AXI xbar | Multiple masters to same slave and one master to multiple slaves | Route select, arbitration, and response return path correct | AXI xbar checker |
| `B_TL_A_D` | TileLink A/D | A request stalls, D response stalls, multibeat grant | Source/sink/opcode/param/size/denied/corrupt stable and ordered | TL checker |
| `B_TL_BCE` | TileLink B/C/E | Probe, release, grantack overlap | Coherence channel ordering and acknowledgement correct | TL coherence checker |
| `B_APB_MMIO` | APB/MMIO | Setup/access phase stalls and error response | PSEL/PENABLE/PREADY/PSLVERR sequencing correct | APB checker |
| `B_CHI_REQ_RSP_DAT_SNP` | CHI | Request/response/data/snoop channel contention | Opcode/id/order/coherence response follows CHI bridge code | CHI checker |

## Hash Conflict Driver Shape

```markdown
## Hash Conflict Verification
| Hash ID | Hash expression | Conflict construction | Expected conflict behavior | Script output |
| --- | --- | --- | --- | --- |
```

Hash drivers are required for any predictor, cache, TLB, prefetcher, memory-dependence predictor, replacement table, bloom/filter-like structure, or directory structure that computes an index from XOR, folded history, folded address bits, modulo, mask, rotate, CRC-like logic, or any custom hash function.

## Hash Conflict Drivers

| Hash ID | Hash class | Construction goal | Expected behavior | Checkers |
| --- | --- | --- | --- | --- |
| `H_SAME_INDEX_DIFF_TAG` | Same hash/index, different tag | Generate two or more addresses/PCs with same index but different tag | Conflict causes replacement, way select, alias handling, or miss as code defines | Hash conflict checker |
| `H_SAME_INDEX_SAME_TAG_DIFF_CONTEXT` | Same index/tag, different context | Same hash key under different ASID/VMID/domain/privilege | Context tag or flush prevents stale hit | Context isolation checker |
| `H_HISTORY_ALIAS` | Folded history alias | Generate branch histories with same folded hash but different real history, including reset, all-zero, all-one, oldest-bit-only, newest-bit-only, alternating, saturated-length, and fold-boundary patterns | Predictor update/lookup conflict follows code and every history consumer sees the exact expected folded and unfolded value | Predictor history checker |
| `H_BANK_ALIAS` | Hash-to-bank alias | Generate addresses mapping to same bank and different banks | Bank conflict and arbitration match code | Bank checker |
| `H_MSHR_MERGE_ALIAS` | Miss merge hash alias | Generate misses with same merge key and same index/different tag | Merge or allocate decision follows code | MSHR checker |
| `H_PREFETCH_ALIAS` | Prefetch table alias | Generate streams that collide in prefetch tables | Training/replacement/throttle follows code | Prefetch checker |
| `H_DIRECTORY_ALIAS` | Directory/set hash alias | Generate coherence lines colliding in directory index | Probe/refill/replacement conflict follows code | Coherence checker |

## Hash Address Generator Script Requirements

When code analysis finds a hash expression, generate a small script next to the generated driver, named:

```text
scripts/gen_<module>_<hash_name>_conflicts.py
```

The script must:

- Encode the exact hash expression from Chisel code as a pure Python function.
- Accept parameters for width, index bits, tag bits, bank bits, page offset bits, ASID/VMID/domain when relevant, and number of conflicts.
- Generate at least these classes:
  - same index, different tag
  - same bank, different set/tag
  - same set, different way candidate
  - same hash, different context
  - boundary addresses around page/cacheline/fetch-block if applicable
  - predictor history patterns when the hash uses history: reset, all-zero, all-one, oldest-bit-only, newest-bit-only, alternating 0101 and 1010, saturated-length, fold-boundary, same-folded-hash/different-real-history, speculative-update, commit-update, redirect-recovery, nested-redirect-recovery, and context-switch/fence
- Avoid illegal architectural addresses unless the test explicitly targets fault behavior.
- Emit JSON and assembly-friendly hex lists:

```json
{
  "hash": "<name>",
  "params": {},
  "groups": [
    {
      "class": "same_index_diff_tag",
      "index": "0x0",
      "values": ["0x...", "0x..."]
    }
  ]
}
```

## Hash Script Template

Use this template as the starting point and replace `hash_fn` with the exact code-derived expression.

```python
#!/usr/bin/env python3
import argparse
import json

def bits(value, hi, lo):
    mask = (1 << (hi - lo + 1)) - 1
    return (value >> lo) & mask

def hash_fn(value, params):
    # Replace with the exact Chisel-derived hash expression.
    index_bits = params["index_bits"]
    return value & ((1 << index_bits) - 1)

def gen_same_index_diff_tag(params, count):
    groups = {}
    step = 1 << params.get("index_lsb", params.get("index_bits", 0))
    limit = params.get("search_limit", 1 << 24)
    for value in range(0, limit, max(step, 1)):
        idx = hash_fn(value, params)
        groups.setdefault(idx, []).append(value)
        if len(groups[idx]) >= count:
            return idx, groups[idx][:count]
    raise RuntimeError("no conflict group found; increase search_limit")

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--index-bits", type=int, required=True)
    parser.add_argument("--index-lsb", type=int, default=0)
    parser.add_argument("--count", type=int, default=4)
    parser.add_argument("--search-limit", type=lambda x: int(x, 0), default=1 << 24)
    args = parser.parse_args()
    params = vars(args)
    idx, values = gen_same_index_diff_tag(params, args.count)
    out = {
        "hash": "code_derived_hash",
        "params": params,
        "groups": [{
            "class": "same_index_diff_tag",
            "index": hex(idx),
            "values": [hex(v) for v in values]
        }]
    }
    print(json.dumps(out, indent=2))

if __name__ == "__main__":
    main()
```

## Completion Checklist

Before a module driver is complete:

- Every computed index has min/max/wrap/boundary tests.
- Every address slice has line/page/superpage/fetch-block/beat boundary tests when applicable.
- Address-derived index and hash tests combine with `skills/virtualizationProtectionDrivers.md` for PMP/PMA/page/IOPMP and two-stage translation boundaries, and `skills/systemVirtualizationPermissionDrivers.md` for leaf/non-leaf page-table and guest/host permission phase boundaries when reachable.
- Cache-derived index and hash tests combine with `skills/cacheStructureDrivers.md` to cover hit, miss, replacement, bank conflict, set conflict, cache full, and flush+reload using generated same-set, same-bank, same-index/different-tag, and same-hash/different-context groups.
- Every pointer has wrap, simultaneous update, and full/empty/almost boundary tests.
- Every bus interface has valid/ready stall, independent channel backpressure, outstanding limit, burst/beat, response/error, and reset/flush tests.
- Every hash expression has a generated conflict-construction script or an explicit reason why conflicts are impossible.
- Every generated hash script cites the source expression and emits machine-readable conflict groups.

