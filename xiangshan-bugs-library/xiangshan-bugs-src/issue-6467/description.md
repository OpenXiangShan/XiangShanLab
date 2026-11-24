### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and [XiangShan Documents](https://docs.xiangshan.cc/zh-cn/latest). 我已经阅读过 [RISC-V 指令集手册](https://github.com/riscv/riscv-isa-manual) 和 [香山文档](https://docs.xiangshan.cc/zh-cn/latest)。
- [x] I have searched previous issues and PRs and did not find anything relevant. 我已经搜索过之前的 issue 和 PR，并没有找到相关的。

### Describe the question

**StoreQueue `deqPtr > rdataPtr` at the physical-queue wrap under vector stores —
bug or intended? (found at `68901834`; the logic is byte-identical at the current
tip `1df189187`)**

While checking kunminghu-v3 (`SimTop`, rv64gcv, VLEN=128) against
NEMU and hit what looks like a StoreQueue pointer-management bug. Before filing a
formal bug report we would like to confirm whether it is known/intended, and get
advice on reproducing it on the latest mainline.

## What we observe (at commit `68901834`)

A short user-mode vector-store workload trips, in `NewStoreQueue.scala`:
```
storeQueue.physicalQueue: deqPtr shouldn't exceed rdataPtr!   (XSError)
storeQueue.physicalQueue: double deq! index: 3
storeQueue.physicalQueue: pointer update error!
ABORT at pc = 0x8000044a   instrCnt = 274   cycleCnt = 5468
```

This is not just an assertion. Re-running with assertions disabled
(`+verilator+noassert`, so the sim continues past the XSError) makes difftest —
with NEMU as the golden reference — catch the architectural consequence at the
same cycle, after 274 instructions of clean lockstep:
```
Mismatch for store commits
  REF commits addr 0x80060050, mask 0x0c, pc 0x80000414   <- NEMU (correct)
  DUT commits addr 0x80060052, mask 0x03, pc 0x80000414   <- XS (wrong)
```
The faulting instruction is `vse16.v v10,(t1)` with `t1 = 0x80060050`. Decoding
(addr aligned to the 8-byte dword, mask = per-byte enables): NEMU writes physical
bytes `0x52..0x53`; the DUT writes `0x50..0x51` — **the vector element is
committed 2 bytes low, into the wrong element slot** (a silent mis-addressed
store once the assertion is compiled out).

## Root cause we traced: asymmetric `dataQueue.io.empty` gate

`deqPtr` and `rdataPtr` both advance past committed vector-INACTIVE (masked-off)
entries, but the two skips are gated differently:
```scala
deqPtrVectorInactiveValid(i)   := ctrl.committed && ctrl.vecInactive                        // :1272  (ungated)
rdataPtrVectorInactiveValid(i) := ctrl.committed && dataQueue.io.empty && ctrl.vecInactive  // :1302  (extra gate)
```
When a committed inactive entry coincides with a **non-empty** `dataQueue` near
the physical-queue wrap boundary, `deqPtr` skips it while `rdataPtr` is blocked,
so `deqPtr` overruns `rdataPtr`. Per-cycle waveform at the failing cycle (5466):
`sqDeqCnt = deqPtrVectorInactiveMove = 2` (deqPtr advances purely by the
inactive-skip, `deqPtrMoveFromSbuffer = 0`), `dataQueue.io_empty = 0` so
`rdataPtr` does not advance; `deqPtr` then dequeues an unallocated slot (the
`double deq`). A trimmed VCD window (attached) shows deqPtr cross rdataPtr:
`@5466 deqPtr=rdataPtr=1 → @5467 deqPtr=3 > rdataPtr=1`.

## The situation at the latest mainline

Lines 1272/1302 are **byte-identical** at the current kunminghu-v3 tip
`1df189187` (2026-09-02) — `git blame` shows both were last written by #5834
(2026-07-14) and untouched by the StoreQueue refactors since `68901834`. So the
logic is present at tip. However, our specific reproducer does **not** reach the store-queue path at tip: it commits ~150
instructions, then derails to a garbage PC and livelocks with no difftest
divergence — a fragility of the input under the tip's different timing, not
evidence of a fix. We are still hunting for a tip-native reproducer.

## Our questions

1. Is this a **known issue** (vector-store StoreQueue wrap `deqPtr > rdataPtr` /
   mis-addressed store), or is there a pending PR that addresses it?
2. Is the **asymmetric `dataQueue.io.empty` gate** between the `deqPtr` (`:1272`)
   and `rdataPtr` (`:1302`) inactive-skips **intentional**, or a defect? If a fix
   is wanted, should the `dataQueue.io.empty` condition be added to the `deqPtr`
   skip, or removed from the `rdataPtr` skip?
3. Given the logic is byte-identical at tip but our input does not reach the path
   there, **would you accept a formal bug report against `68901834`** with the
   attached evidence, or do you **require a tip-native reproducer** first?
4. Any guidance on constructing a **directed test** that forces the precondition
   (committed vector-inactive entries at the SQ wrap with a non-empty dataQueue)?

## Notes to pre-empt two likely responses

- We believe this is an **LSU/StoreQueue pointer bug**, not a "vector unit
  correctness" issue — the vector store is only the stimulus that exposes an LSU
  pointer race in `mem/lsqueue/NewStoreQueue.scala`.
- The faulting store targets a **data** symbol (`d_5_3` at `0x80060050`), not the
  code section, and the workload has no self-modifying code — so this is not the
  `fence.i`/ICache-coherence difftest caveat noted in CONTRIBUTING.

## Environment / evidence

- XiangShan commit: `68901834` (kunminghu-v3). NEMU: shipped `ready-to-run`
  interpreter. firtool 1.144.0; Verilator 5.028; difftest submodule `45638f51`.
- Attachments (tarball): `id_0_1142.elf` (reproducer), `id_0_1142.si` /
  `.disasm` (source/disassembly), `stock_repro_assert.log` (assertion run),
  `noassert_storecommit_mismatch.log` (the store-commit divergence),
  `sq_window_5455_5475.vcd` (trimmed waveform showing the crossing).

[xs_v3_storequeue_deqptr_rdataptr_wrap_attachments.tar.gz](https://github.com/user-attachments/files/31725941/xs_v3_storequeue_deqptr_rdataptr_wrap_attachments.tar.gz)
