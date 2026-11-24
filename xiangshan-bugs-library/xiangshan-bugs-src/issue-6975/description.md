### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and [XiangShan Documents](https://docs.xiangshan.cc/zh-cn/latest), and I believe this is a XiangShan RTL issue. 我已经阅读过 [RISC-V 指令集手册](https://github.com/riscv/riscv-isa-manual) 和 [香山文档](https://docs.xiangshan.cc/zh-cn/latest)，确认这应该是香山 RTL 的问题。
- [x] I have searched previous issues and PRs and did not find anything relevant. 我已经搜索过之前的 issue 和 PR，并没有找到相关的。
- [x] I have reproduced the issue using the latest commit on the default branch. 我已经使用默认分支最新的 commit 复现了问题。
- [x] If this report was generated with AI assistance (otherwise leave unchecked), I have verified the correctness of its content. 如果这是由 AI 生成的（否则请不要勾选），我已经验证了内容的正确性。

### Branch

kunminghu-v3

### Describe the bug

A store miss allocates MSHR `e`, and both GrantData beats of `e` have already arrived. If, before the refill is written, a store and a load to the same block reach MissQueue in the same cycle, the store goes to port 0 and the load to a load port. The store merges into `e`, which is allowed for stores until the refill is written. The load becomes the store's compress follower and is accepted with `handled = 1, id = e`, although a load may only merge before the first beat.

The load then waits in LoadQueueReplay (C_DM) for a D beat or L2 hint of `e`. Both have already passed, and there is no timeout. The load reaches the ROB head, and nothing commits again.

Merge rule: loads may merge only before the first beat; stores may merge until the refill is written.

```scala
// cache/dcache/mainpipe/MissQueue.scala:894-896   (data_not_refilled = !w_grantfirst, :612)
def before_data_refill_can_merge(new_req: MissReqWoStoreData): Bool = {
  data_not_refilled && new_req.isFromLoad ||
  !io.main_pipe_refill_resp && !w_refill_resp && new_req.isFromStore && alloc_is_store
}
```

A follower is ready whenever the leader allocates or merges. The follower's own `can_merge_vec(i)` is not checked:

```scala
// cache/dcache/mainpipe/MissQueue.scala:1631-1632
val is_first_in_group = target_group === i.U
val compress_ready = has_compress && Mux(is_first_in_group, has_merge || has_alloc, (analysis.strategy(target_group) & 3.U) =/= 0.U)
```

The follower gets the leader's MSHR id with `handled = 1`:

```scala
// cache/dcache/mainpipe/MissQueue.scala:1795-1799, 1805-1810
actual_target_mshr_for_group(i) := Mux(group_leader === i.U, analysis.target_mshr(i), analysis.target_mshr(group_leader))
io.resp(i).id := Mux(can_merge_from_pipe(i), can_merge_from_pipe_mshr(i), actual_target_mshr_for_group(i))
io.resp(i).handled := (Cat(req_mshr_handled_vec).orR || can_merge_from_pipe(i)) && query_fire(i)
```

At enqueue, LoadQueueReplay only sees D beats of `e` in the current and previous cycle. After that, only a D beat or an L2 hint of `e` unblocks the entry:

```scala
// mem/lsqueue/LoadQueueReplay.scala:927-934
when (replayInfo.cause(LoadReplayCauses.C_DM) && enq.bits.handledByMSHR) {
  ...
  blocking(enqIndex) := !replayInfo.full_fwd && !tlDHitThisCycle && !tlDHitPrevCycle &&
                        !(replayInfo.rep_from_unalign_head && tlDHitPrevPrevCycle)
// mem/lsqueue/LoadQueueReplay.scala:490-501
when (cause(i)(LoadReplayCauses.C_DM)) {
  blocking(i) := Mux(tlDChannelHit(missMSHRId(i)), false.B, blocking(i))
}
... // or an L2 hint for missMSHRId(i)
```

### Expected behavior

The load is not merged (`handled = 0`), so it replays and hits after the refill, or it is woken up. The program finishes.

### Environment

- XiangShan: kunminghu-v3 `8b15f0652`, DefaultConfig, 1 core, Verilator emu.

### To Reproduce

Attachment: `mq-compress-late-load-repro.zip`. It contains `repro.S` and `attempts.inc`, `repro.bin`, the control `control.bin`, `build.sh` (rebuilds both with LLVM and checks `SHA256SUMS`), and a short README.

```bash
./build/emu -i repro.bin   --no-diff -C 12000
./build/emu -i control.bin --no-diff -C 12000
```

| Image | Result |
|---|---|
| `repro.bin` | `EXCEEDING CYCLE/INSTR LIMIT`, `instrCnt = 126` (still 126 at `-C 200000`). The ROB head is the load at offset 0x1f8; its LoadQueueReplay entry has cause C_DM, is blocking, and `missMSHRId` = the store's MSHR. |
| `control.bin` | `HIT GOOD TRAP` after about 8590 cycles. |

[mq-compress-late-load-repro.zip](https://github.com/user-attachments/files/34877517/mq-compress-late-load-repro.zip)

### Additional context

_No response_
