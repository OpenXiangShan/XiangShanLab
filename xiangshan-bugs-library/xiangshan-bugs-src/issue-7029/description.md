### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and [XiangShan Documents](https://docs.xiangshan.cc/zh-cn/latest), and I believe this is a XiangShan RTL issue. 我已经阅读过 [RISC-V 指令集手册](https://github.com/riscv/riscv-isa-manual) 和 [香山文档](https://docs.xiangshan.cc/zh-cn/latest)，确认这应该是香山 RTL 的问题。
- [x] I have searched previous issues and PRs and did not find anything relevant. 我已经搜索过之前的 issue 和 PR，并没有找到相关的。
- [x] I have reproduced the issue using the latest commit on the default branch. 我已经使用默认分支最新的 commit 复现了问题。
- [x] If this report was generated with AI assistance (otherwise leave unchecked), I have verified the correctness of its content. 如果这是由 AI 生成的（否则请不要勾选），我已经验证了内容的正确性。

### Branch

kunminghu-v2

### Describe the bug

An NC load A (PBMT=NC) first goes through the load pipeline without data. This first pass already marks A's VirtualLoadQueue entry as committed, so the entry can leave the queue before A's data returns. When the data returns, A writes back again with its old `lqIdx` and sets `committed` by value only. If that slot has been reallocated in the meantime (72 loads later), the new occupant B is marked committed before it has its data. `ldWbPtr` then moves past B, and LoadQueueRAR releases or never enqueues the younger loads that B must be checked against. A same-address younger load that read before B is not rolled back. With a second core writing that address, the younger load returns an older value than B (a read-read coherence violation).

The first pass (no data yet, `nc` set) is admitted to LoadQueueUncache:

```scala
// src/main/scala/xiangshan/mem/lsqueue/LoadQueueUncache.scala:364
s2_enqueue(w) := s2_valid(w) && !s2_has_exception(w) && !s2_need_replay(w) && (s2_req(w).mmio || s2_req(w).nc)
```

The same pass also writes to VirtualLoadQueue with `updateAddrValid` set, because nothing in it excludes the NC first pass:

```scala
// src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala:1617
io.lsq.ldin.bits.updateAddrValid := !s3_mis_align && (!s3_frm_mabuf || s3_in.isFinalSplit) || s3_exception
```

VirtualLoadQueue sets `committed` by `lqIdx.value`, without checking that the slot still belongs to this load:

```scala
// src/main/scala/xiangshan/mem/lsqueue/VirtualLoadQueue.scala:251-258
val loadWbIndex = io.ldin(i).bits.uop.lqIdx.value
...
when (io.ldin(i).valid) {
  ...
  when (!need_rep && need_valid && !io.ldin(i).bits.isvec) {
    committed(loadWbIndex) := true.B
```

A committed entry leaves the queue, and `ldWbPtr` follows `deqPtr`:

```scala
// src/main/scala/xiangshan/mem/lsqueue/VirtualLoadQueue.scala:138
val deqLookup = VecInit(deqLookupVec.map(ptr => allocated(ptr.value) && committed(ptr.value) && ptr =/= enqPtrExt(0)))
// src/main/scala/xiangshan/mem/lsqueue/VirtualLoadQueue.scala:155
io.ldWbPtr := deqPtr
```

When A's data returns, LoadQueueUncache sends the data pass back with the stored uop, including the old `lqIdx`:

```scala
// src/main/scala/xiangshan/mem/lsqueue/LoadQueueUncache.scala:206-209
io.ncOut.valid := (uncacheState === s_wait) && !needFlush
...
io.ncOut.bits.uop := req.uop
io.ncOut.bits.uop.lqIdx := req.uop.lqIdx
```

The data pass goes through the same `committed(loadWbIndex) := true.B` above. If the slot has been reallocated, it marks the new occupant B. B then leaves VirtualLoadQueue before its own data, and `ldWbPtr` passes B and the loads after it. LoadQueueRAR uses `ldWbPtr` both to decide whether a load must enqueue and to release entries:

```scala
// src/main/scala/xiangshan/mem/lsqueue/LoadQueueRAR.scala:142
val hasNotWritebackedLoad = io.query.map(_.req.bits.uop.lqIdx).map(lqIdx => isAfter(lqIdx, io.ldWbPtr))
// src/main/scala/xiangshan/mem/lsqueue/LoadQueueRAR.scala:200-205
val deqNotBlock = !isBefore(io.ldWbPtr, uop(i).lqIdx)
val needFlush = uop(i).robIdx.needFlush(io.redirect)
when (allocated(i) && (deqNotBlock || needFlush)) {
  allocated(i) := false.B
```

So when B finally queries LoadQueueRAR, the younger same-address load C that read before B is no longer there, and no ld-ld violation is raised.

### Expected behavior

An NC load's VirtualLoadQueue entry stays allocated until its data pass (or an exception), and a writeback never marks a slot that now belongs to another load. The younger same-address load is rolled back as in the non-wrapping case.

### Environment

- Single core: XiangShan kunminghu-v2 `9c53db086a`, KunminghuV2Config, 1 core, Verilator emu, difftest with NEMU.
- Dual core: XiangShan kunminghu-v2 `f7e05ec27c`, KunminghuV2Config, `NUM_CORES=2`, Verilator emu, no difftest. 

### To Reproduce

[vlq-nc-stale-commit.zip](https://github.com/user-attachments/files/35227978/vlq-nc-stale-commit.zip)

Attachment: `vlq-nc-stale-commit.zip`. It contains `repro.S` (single core) and `repro-2core.S` (dual core), the images `repro-fill71.bin` and `repro-2core-fill71.bin`, the controls `control-fill70.bin` and `control-2core-fill70.bin`, `build.sh` (rebuilds all four with LLVM and checks `SHA256SUMS`), and a short README.

**Single core.** Per iteration, A is an NC load, followed by FILL independent loads, B (older, waits for A's data), C (younger, same line as B, executes early), and eight loads that evict the line so that C is marked released. With FILL = 71, B gets the same `lqIdx.value` as A. With FILL = 70 (control), it does not. The program runs 100 iterations.

```bash
./build/emu -i repro-fill71.bin   -C 400000
./build/emu -i control-fill70.bin -C 400000
```

| Image | Result |
|---|---|
| `repro-fill71.bin` | `HIT GOOD TRAP` at pc `0x80000338`, 26857 cycles; `[PERF] loadQueueRAR: ld_ld_violation, 0` |
| `control-fill70.bin` | `HIT GOOD TRAP` at pc `0x80000334`, 30526 cycles; `[PERF] loadQueueRAR: ld_ld_violation, 171` |

**Dual core.** Hart 0 runs the same sequence for 1500 iterations; B and C both read Z. Hart 1 increments Z once every few hundred cycles. Hart 0 counts the iterations where C (younger) read a smaller value than B (older) and prints it as `v`. `d` counts iterations with B != C; `z` is the last value of C.

```bash
./build/emu -i repro-2core-fill71.bin   --no-diff -C 2000000
./build/emu -i control-2core-fill70.bin --no-diff -C 2000000
```

| Image | Result |
|---|---|
| `repro-2core-fill71.bin` | both cores `HIT GOOD TRAP`, 227828 cycles; hart 0 prints `v cd` (205 violations), `d cd`, `z d8`; core 0 `[PERF] loadQueueRAR: ld_ld_violation, 0` |
| `control-2core-fill70.bin` | both cores `HIT GOOD TRAP`, 227834 cycles; hart 0 prints `v 0`, `d 0`, `z d8`; core 0 `[PERF] loadQueueRAR: ld_ld_violation, 211` |

`HIT GOOD TRAP` only means the program finished; it always exits with code 0 after printing.

### Additional context

_No response_
