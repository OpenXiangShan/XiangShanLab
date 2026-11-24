### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and [XiangShan Documents](https://docs.xiangshan.cc/zh-cn/latest), and I believe this is a XiangShan RTL issue. 我已经阅读过 [RISC-V 指令集手册](https://github.com/riscv/riscv-isa-manual) 和 [香山文档](https://docs.xiangshan.cc/zh-cn/latest)，确认这应该是香山 RTL 的问题。
- [x] I have searched previous issues and PRs and did not find anything relevant. 我已经搜索过之前的 issue 和 PR，并没有找到相关的。
- [x] I have reproduced the issue using the latest commit on the default branch. 我已经使用默认分支最新的 commit 复现了问题。
- [x] If this report was generated with AI assistance (otherwise leave unchecked), I have verified the correctness of its content. 如果这是由 AI 生成的（否则请不要勾选），我已经验证了内容的正确性。

### Branch

kunminghu-v2

### Describe the bug

After a vector store takes an exception, the StoreQueue keeps `vecExceptionFlag` set until the store's remaining flows leave. During that time, a store-class uop that already has an exception at decode can reach the ROB head and flush itself. Examples are `fsd` with `mstatus.FS = Off` and a store with an execute breakpoint. Its SQ entry is not cancelled. The entry never executes, so it is never committed. All later stores stop, and the next `fence` waits forever.

The uop is never issued, but it still gets an SQ entry:

```scala
// backend/dispatch/NewDispatch.scala:449-450
fromRenameUpdate(i).valid := ... && !fromRename(i).bits.hasException && ...
// backend/dispatch/NewDispatch.scala:690-700
}.elsewhen(isStoreVec(i) || isVStoreVec(i)) {
  enqLsqIO.needAlloc(i) := 2.U // store | vstore
```

The entry waits for its own store-address writeback, which never comes:

```scala
// mem/lsqueue/StoreQueue.scala:413 (enqueue); cleared only at :579 (own STA writeback)
waitStoreS2(i) := true.B
```

While the flag is set, the cancel check uses `isAfter`. A flush that targets the uop itself does not cancel its own entry:

```scala
// mem/lsqueue/StoreQueue.scala:1496-1500
needCancel(i) := allocated(i) && !committed(i) && Mux(
    vecExceptionFlag.valid,
    isAfter(uop(i).robIdx, io.brqRedirect.bits.robIdx) && io.brqRedirect.valid,
    uop(i).robIdx.needFlush(io.brqRedirect)
  )
```

The commit loop stops at this entry for good:

```scala
// mem/lsqueue/StoreQueue.scala:1152-1156
when (
  allocated(ptr) &&
  isNotAfter(uop(ptr).robIdx, GatedRegNext(io.rob.pendingPtr)) &&
  !needCancel(ptr) &&
  (!waitStoreS2(ptr) || isVec(ptr))) {
```

`fence` waits for the sbuffer and SQ to be empty (`backend/fu/Fence.scala:82`, `mem/sbuffer/Sbuffer.scala:612`), which never happens.

### Expected behavior

The flush cancels the flushed uop's own SQ entry, as `needFlush` does when the flag is clear, and the program finishes.

### Environment

- XiangShan: kunminghu-v2 `f7e05ec27c`, KunminghuV2Config, 1 core, Verilator emu.

### To Reproduce

Attachment: `sq-vecexc-selfflush-repro.zip`. It contains `repro.S`, `repro.bin`, the control `control.bin`, `build.sh` (rebuilds both with LLVM and checks `SHA256SUMS`), and a short README.

```bash
./build/emu -i repro.bin   --no-diff -C 60000
./build/emu -i control.bin --no-diff -C 60000
```

| Image | Result |
|---|---|
| `repro.bin` | `EXCEEDING CYCLE/INSTR LIMIT`, `instrCnt = 72`. The ROB head is the final `fence`, and the SQ commit pointer stays on the `fsd`'s entry. |
| `control.bin` | `HIT GOOD TRAP` after 4882 cycles. |

[sq-vecexc-selfflush-repro.zip](https://github.com/user-attachments/files/34877649/sq-vecexc-selfflush-repro.zip)

### Additional context

_No response_
