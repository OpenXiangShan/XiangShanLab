# Kunminghu architecture routing

Use the course notes as a mechanism index after the failure domain is known. The default root reported by `probe_dependencies.py` is:

```text
<XiangShanLab>/xiangshan-course/docs/4-xiangshan-microarchitecture-analysis/3-xiangshan-source-code-analysis
```

These documents may describe a different Kunminghu revision or configuration. Use them to choose signals and source areas, then prove every issue-specific claim against the replay checkout and waveform.

## Symptom-to-document map

| Symptom/domain | Read first | Typical source/waveform focus |
| --- | --- | --- |
| Wrong fetch PC, prediction, fall-through, redirect recovery | `frontend/Frontend-Overview-and-End-to-End-Signal-Analysis.md`, `Frontend-BPU.md`, `Frontend-FTQ.md` | FTQ identity, predicted/actual target, redirect producer/consumer, younger flush |
| ITLB/PTW, instruction page/access fault, canonical address | `frontend/Frontend-IFU-and-Predecode-Deep-Dive.md`, `frontend/Frontend-ICache.md`, `memory/LoadStore-TLB.md`, `memory/LoadStore-MMU.md` | request VA/VPN, translation mode, PTW response, PF/GPF/AF, PMP/PMA, exception propagation |
| ICache miss, refill, fetch corruption/backpressure | `frontend/Frontend-ICache.md` | request fire, miss/refill identity, meta/data path, exception and IFU response |
| Decode or instruction classification | `backend/Decode.md`, `backend/Decode-Fusion.md` | instruction bits, decode table, uop split/fusion, exception bits, lane handshake |
| Rename/free-list/move elimination | `backend/Rename.md`, `backend/Move-elimination.md`, `backend/RegCache.md` | full ROB identity, architectural/physical registers, allocation/free/walk/redirect |
| Dispatch/backpressure/wait-forward | `backend/Dispatch.md` | ROB/IQ/LSQ acceptance, waitForward, blockBackward, per-lane fire |
| Load result, replay, forwarding, ordering violation | `memory/LoadStore-LoadPipe.md`, `LoadStore-LoadQueue.md`, `LoadStore-LoadQueueReplay.md`, `LoadStore-LoadQueueRAW.md`, `LoadStore-LoadQueueRAR.md` | LQ identity, address/data/mask, DTLB/DCache response, nack/replay cause, RAW/RAR redirect |
| Store data/commit/drain/forwarding | `memory/LoadStore-StoreQueue.md`, `LoadStore-StoreBuffer.md`, `LoadStore-StorePipe.md` | SQ identity, address/data readiness, commit, SBuffer merge/forward/drain, MainPipe request |
| Misaligned or uncached access | `memory/LoadStore-MissalignBuffer.md`, `LoadStore-UncacheBuffer.md`, `LoadStore-ExceptionBuffer.md` | split fragments, logical/physical IDs, response merge, exception/flush drain |
| Memory dependence prediction | `memory/LoadStore-MDP.md`, `memory/mdp-ref.md`, `memory/Memory_SSIT.md`, `memory/Memory_LFST.md` | SSIT/LFST identity, wait/train path, store-set conflict, replay/redirect |
| DCache hit/miss/replay | `memory/LoadStore-DCache.md`, `cache/Cache-MainPipe.md`, `Cache-MSHRCtl.md`, `Cache-DataStorage.md` | request fire, bank/tag/meta, MSHR allocation, refill, nack/replay, response ownership |
| L2/CHI request, response, data, snoop | `cache/Cache-RequestArb.md`, `Cache-RequestBuffer.md`, `Cache-GrantBuffer.md`, `Cache-RXRSP.md`, `Cache-RXDAT.md`, `Cache-RXSNP.md`, `Cache-TXREQ.md`, `Cache-TXRSP.md`, `Cache-TXDAT.md` | source/sink/transaction IDs, queue state, CHI opcode, beats, GrantAck, probe/snoop ordering |
| Page-table cache | `page-table-cache.md` | VPN/level/tag, hit/refill, invalidate/fence, PTW request/response |

If no row matches, search filenames and contents rather than reading the whole corpus:

```bash
rg -n -i '<module|signal|mechanism|exception>' <course-root>
```

Open the root `README.md` for the current index. Some Backend topics—ROB, issue queues, execution units, writeback, CSR—and some coherence paths may not have a dedicated course chapter; go directly to replay source and use the closest boundary document only as orientation.

## Effective-path rule

Never infer the active implementation from a class name or document alone. In the replay checkout:

1. Find the top-level instantiation and current parameters/config fragments.
2. Follow mux/arbiter selection and `valid/ready/fire` to the real consumer.
3. Distinguish hard-disabled, optional, prefetch-only, debug-only, and legacy paths.
4. Preserve pointer flag/wrap bits and transaction IDs across queues.
5. Treat document diagrams as design intent until source and waveform confirm them.

Important recurring traps include:

- LoadQueue dispatch backpressure may come from the virtual allocation queue rather than the replay queue.
- StoreBuffer is a merge/forward/drain structure, not a simple FIFO.
- StorePipe may be a probe/prefetch boundary rather than the default store-write correctness path; trace StoreQueue → SBuffer → DCache MainPipe.
- TLB response `valid` or delayed request `valid` does not alone prove a successful translation or request acceptance.
- Generic cache/LSQ requests can be valid without allocating the resource relevant to the target instruction.

## Optional local analyzers

When present and relevant, read these local skills for extra domain checklists without replacing the bug-fixer gates:

- `<XiangShanLab>/tools/xiangshan-bugs-analyzer/xiangshan-bugs-analysis/SKILL.md`
- `<XiangShanLab>/tools/xiangshan-code-analyzer/skills/analyze-xiangshan-kunminghu/SKILL.md`

Use their references selectively. Their default source paths or output conventions do not override the issue's fresh replay checkout, exact output path, or this skill's A/B validation requirements.

## Source search order

Within `<replay>/xs-env/XiangShan`, search:

1. the waveform field name and nearby bundle field;
2. module/class/instance components from the waveform hierarchy;
3. assertion text or error message;
4. opcode/exception/replay enumeration;
5. producer assignment, then downstream consumer;
6. configuration fragments and submodule revision when the path crosses coupledL2/huancun/difftest.

Record the XiangShan HEAD and relevant submodule HEADs in the report. Absolute paths make local evidence clickable; the commit IDs make it reproducible elsewhere.
