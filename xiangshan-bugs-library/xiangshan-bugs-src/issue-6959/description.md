### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and [XiangShan Documents](https://docs.xiangshan.cc/zh-cn/latest), and I believe this is a XiangShan RTL issue. 我已经阅读过 [RISC-V 指令集手册](https://github.com/riscv/riscv-isa-manual) 和 [香山文档](https://docs.xiangshan.cc/zh-cn/latest)，确认这应该是香山 RTL 的问题。
- [x] I have searched previous issues and PRs and did not find anything relevant. 我已经搜索过之前的 issue 和 PR，并没有找到相关的。
- [x] I have reproduced the issue using the latest commit on the default branch. 我已经使用默认分支最新的 commit 复现了问题。
- [x] If this report was generated with AI assistance (otherwise leave unchecked), I have verified the correctness of its content. 如果这是由 AI 生成的（否则请不要勾选），我已经验证了内容的正确性。

### Branch

kunminghu-v3

### Describe the bug

When a long-latency instruction sits at the ROB head, younger stores that have already executed cannot drain to the sbuffer early. On the cycle that group retires together with the head instruction, `io.sqEmpty` is 1. On that cycle the stores are still in the PSQ and the sbuffer really is empty. The stores are written into the sbuffer only after the new `retiredPtr` arrives one cycle later.

`preCommitPtr` advances by at most one virtual entry per cycle, and only when that entry's `robIdx` is the current ROB head. Stores behind another instruction never take this path, so the PSQ learns that they have retired only from `retiredPtr`.

```scala
// VirtualStoreQueue.scala:149
private val empty = deqPtrVec.head === enqPtrVec.head

// VirtualStoreQueue.scala:282-290
// precommit store, it will be write to sbuffer before rob retire.
val preCommitEntry = ctrlEntries(preCommitPtr.value)
val preCommitMoveValid = dataEntries(preCommitPtr.value).robIdx.isSameSlot(robHeadPtr) && preCommitEntry.allocated

val preCommitPtrNext = WireInit(preCommitPtr)
when(redirectReg.valid) { // redirect next cycle update preCommitPtr
  preCommitPtrNext := deqPtrVecNext.head
}.elsewhen(preCommitMoveValid && preCommitPtr =/= enqPtrVec.head) { // preCommitPtr advance
  preCommitPtrNext := preCommitPtr + 1.U
```

`io.sqEmpty` is the AND of the two `empty` outputs. The retired pointer sampled by the PSQ is one cycle behind the cycle on which the VSQ becomes empty:

```scala
// NewStoreQueue.scala:2219
io.sqEmpty := virtualStoreQueue.io.empty && physicalQueue.io.empty

// NewStoreQueue.scala:2106
io.empty := deqPtrExt.head === commitPtrExt.head && deqPtrExt.head === virtualStoreQueueRetiredPtr

// NewStoreQueue.scala:1704-1706
private val virtualStoreQueueRedirect = GatedRegNext(io.fromVirtualStoreQueue.redirectPtr.asInstanceOf[Valid[SqPtr]])
private val virtualStoreQueuePreCommitPtr = GatedRegNext(io.fromVirtualStoreQueue.preCommitPtr.asInstanceOf[Valid[SqPtr]])
private val virtualStoreQueueRetiredPtr = GatedRegNext(io.fromVirtualStoreQueue.retiredPtr.asInstanceOf[SqPtr])
```

VSQ `empty` is `deqPtr == enqPtr`, so it becomes 1 on the retire cycle. PSQ `empty` does not look at whether physical entries are still allocated. It is 1 when `deqPtr` equals both `commitPtr` and the delayed `retiredPtr`. These stores were not precommitted, so `commitPtr` has not moved, and the PSQ also reports empty. On the next cycle the new pointer arrives and `sqEmpty` returns to 0.

**Potential problem**

The sbuffer treats that cycle as "both the SQ and the sbuffer have drained":

```scala
// Sbuffer.scala:539-544
val sbuffer_empty = Cat(invalidMask).andR
val sbuffer_mshr_empty = sbuffer_empty && io.mshr_store_empty
val inReq_empty = !Cat(io.in.req.map(_.valid)).orR
val cmo_empty = sbuffer_mshr_empty && inReq_empty
val all_empty = cmo_empty && io.sqempty

// Sbuffer.scala:559
io.flush.empty := GatedValidRegNext(all_empty)
```

```scala
// MemBlock.scala:1197-1198
val stIsEmpty = sbuffer.io.flush.empty && uncache.io.flush.empty
io.mem_to_ooo.sbIsEmpty := RegNext(stIsEmpty)
```

The Fence unit leaves `s_wait` the first time it sees `sbIsEmpty`, and does not sample it again:

```scala
// Fence.scala:82-87
when (state === s_idle && io.in.valid) { state := s_wait }
when (state === s_wait && func === FenceOpType.fencei && sbEmpty) { state := s_icache }
when (state === s_wait && ((func === FenceOpType.sfence || func === FenceOpType.hfence_g || func === FenceOpType.hfence_v || (if (HasMptCheck) (func === FenceOpType.mfence) else false.B)) && sbEmpty)) { state := s_tlb }
when (state === s_wait && func === FenceOpType.fence  && sbEmpty) { state := s_fence }
when (state === s_wait && func === FenceOpType.nofence  && sbEmpty) { state := s_nofence }
when (state =/= s_idle && state =/= s_wait) { state := s_idle }
```

The two register stages deliver the one-cycle false empty to the Fence unit on the cycle it enters `s_wait`. The stores are still in the PSQ.

Every instruction that waits in `s_wait` (`fence`, `fence.i`, `sfence.vma`, `hfence.*`, Svinval) can therefore complete before the older stores have been written out. The stores are not dropped: once the sbuffer is in `x_drain_all` it looks at the current `sqempty` and does write them. What can run early is the operation after the fence:

- a fetch after `fence.i` can observe the old instruction;
- a page-table walk after `sfence.vma` can observe the old PTE;
- stores before and after `fence` can become visible to another hart in the wrong order.

### Expected behavior

- When `io.sqEmpty` is 1, the SQ must not hold a retired store that has not yet been written to the sbuffer. The PSQ empty check has to account for the `retiredPtr` that the VSQ is driving on this cycle and that the PSQ has not sampled yet.
- The Fence unit should then leave `s_wait` only after every retired store has left the SQ and the sbuffer and store MSHRs have drained:
  - `fence.i`: a later fetch on this hart sees earlier stores from this hart (Zifencei);
  - `sfence.vma`: earlier stores to page tables on this hart happen before later implicit page-table reads (privileged spec);
  - `fence`: ordered by pred/succ.

### Environment

- Hardware: AMD EPYC
- Software: Ubuntu 24.04. The emu is generated by Verilator 5.052 and compiled with clang 21.1.8. Chisel 7.13.0, firtool 1.149.0, mill 0.12.17, OpenJDK 21.0.12.1.
- Repo
  - XiangShan commit: `e716c765824900ff78816b92ac827fd4aa2cab35` (kunminghu-v3 HEAD, 2026-11-12)
  - NEMU: difftest uses the companion so `riscv64-nemu-interpreter-so`
- Build & Run
  - Build: `make sim-verilog CONFIG=DefaultConfig NUM_CORES=1 WITH_CHISELDB=1 WITH_CONSTANTIN=1`, then `make emu NUM_CORES=1 EMU_THREADS=8 EMU_TRACE=fst WITH_CHISELDB=1 WITH_CONSTANTIN=1`
  - Run: `./build/emu -i fence_sq_fresh_pf.bin --no-diff -C 40000` (difftest: `--diff ready-to-run/riscv64-nemu-interpreter-so`)
- The same result was also seen on `980935fd3` (DefaultConfig, single-thread emu, g++) and `b7cdbd1441` (CI 8-thread emu).

### To Reproduce

Attachment `lsqe-e716c76.tar.gz`: binaries & how to reproduce. 

[lsqe-e716c76.tar.gz](https://github.com/user-attachments/files/34783362/lsqe-e716c76.tar.gz)

One scenario:

<img width="2240" height="984" alt="lsqe-sqempty-window" src="https://github.com/user-attachments/assets/9d5a0dc6-1f22-4ae0-af64-a7033278f8e7" />

### Additional context

_No response_
