### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and [XiangShan Documents](https://docs.xiangshan.cc/zh-cn/latest), and I believe this is a XiangShan RTL issue. 我已经阅读过 [RISC-V 指令集手册](https://github.com/riscv/riscv-isa-manual) 和 [香山文档](https://docs.xiangshan.cc/zh-cn/latest)，确认这应该是香山 RTL 的问题。
- [x] I have searched previous issues and PRs and did not find anything relevant. 我已经搜索过之前的 issue 和 PR，并没有找到相关的。
- [x] I have reproduced the issue using the latest commit on the default branch. 我已经使用默认分支最新的 commit 复现了问题。
- [ ] If this report was generated with AI assistance (otherwise leave unchecked), I have verified the correctness of its content. 如果这是由 AI 生成的（否则请不要勾选），我已经验证了内容的正确性。

### Branch

kunminghu-v3

### Describe the bug

Commit: XiangShan KMHv3 `4cf8b8d`.
The DUT is XiangShan `kunminghu-v3` with `TestkitV3Config`, commit `4cf8b8d4ae343e01dfb54e5ae8febdaeaa9992ae`. The program executes a vector store, `vse32.v v15,(ra)` at `0x800010d0` with `ra = 0x5c0`, which takes a store access fault (cause 7, an exact trap). The M-mode handler adds 4 to `mepc` and executes `mret`. The core then commits `remw s9,t4,a4` at `0x800010d4`, takes an illegal instruction at `0x800010d8` (`0x014f4d23`), handles it, and returns. The next architectural instruction is `amoswap.d.aq a2,s6,(t6)` at `0x800010dc`.

That AMO must execute. Instead the machine stops committing: `instrCnt = 334`, `cycleCnt = 17,072`, and no instruction commits for 15,000 cycles. The faulting vector store marked a physical store-queue slot with `hasException`, the vector merge buffer retired it early, and the redirect recovery pointer advanced past it. The slot can then neither commit (exceptions exclude it from `commitVec`) nor dequeue (all three dequeue sources require a commit or a successful sbuffer write), so the physical queue never becomes empty. The AMO waits in `s_wait_flush_sbuffer_resp` for an sbuffer-empty condition that can never occur.



### Expected behavior

After the precise vector-store fault is handled and `mret` returns, the AMO executes. NEMU, the bundled DiffTest reference, runs the AMO at `0x800010dc` and reports `a2 = 0xc0c0c0c0`. Spike cannot serve as the oracle here: it treats several Zk/vector instructions in this ELF as illegal and diverges before reaching the `vse32.v` at `0x800010d0`.


### Environment

- Hardware
  - CPU:
  - Memory (GB):
  - Storage (GB):
- Software
  - Operating system:
  - gcc version: <!-- run `gcc --version 2>&1 | head -n 1` to get the version -->
  - clang version: <!-- run `clang --version 2>&1 | head -n 1` to get the version, only needed when you use clang -->
  - java version: <!-- run `java -version 2>&1 | head -n 1` to get the version -->
  - mill version: <!-- run `mill -i --version 2>&1 | head -n 1` to get the version -->
- Repo
  - XiangShan commit id: ``
  - NEMU commit id (if difftest failed with NEMU): ``
  - SPIKE commit id (if difftest failed with SPIKE): ``
- Build & Run
  - Build command: ``
  - Run command (if applicable): ``
  - Also upload workload (binary and source code) in "To Reproduce" section if applicable.


### To Reproduce

1. Run the seed ELF on the KMHv3 build with DiffTest against the bundled NEMU reference.
2. Execute `vse32.v v15,(ra)` at `0x800010d0` with `ra = 0x5c0`; observe the store access fault (`cause = 7`) and the handler's `mepc += 4` / `mret`.
3. Continue through `remw s9,t4,a4` at `0x800010d4` and the illegal `0x014f4d23` at `0x800010d8`, handled and returned from.
4. Execute `amoswap.d.aq a2,s6,(t6)` at `0x800010dc`; the DUT does not commit it, and after 15,000 cycles DiffTest lets the reference advance one instruction and reports a register mismatch.

Reproducer ELF SHA-256: `3808618dc52fcb507293e94172b92315b390958534d7882cc75f675dada10cf7`

[xiangshan-vec-store-fault-amo-hang-upload.zip](https://github.com/user-attachments/files/34894087/xiangshan-vec-store-fault-amo-hang-upload.zip)

### Additional context

The physical store-queue slot carrying the fault can never commit — `NewStoreQueue.scala:1924`:

```scala
!ctrlEntries(ptr).hasException && !ctrlEntries(ptr).waitStoreS2 && ctrlEntries(ptr).allValid ||
```

With `hasException = 1`, `commitVec` stays 0 and `committed` never sets (`NewStoreQueue.scala:1934`):

```scala
ctrlEntries(ptr).committed := commitVec(i) || ctrlEntries(ptr).committed
```

The dequeue pointer has three move sources, all unreachable for this slot — `NewStoreQueue.scala:1261,1272`:

```scala
deqPtrVectorInactiveValid(i) := ctrl.committed && ctrl.vecInactive
...
val deqCount = Cat(deqPtrMoveFromSbuffer.asUInt,
                   deqPtrVectorInactiveMove, uncacheMove)
```

`deqPtrMoveFromSbuffer` requires a successful sbuffer write, which an exception entry never performs; `vecInactive` requires `committed`; `uncacheMove` requires `handleFinish && committed`. The dequeue pointer therefore stops at slot 3.

The vector merge buffer retires the vector store early, and the redirect recovery pointer skips it — `VirtualStoreQueue.scala:373,394-396`:

```scala
toPhysicalQueueRetirePtrNext := toPhysicalQueueRetirePtr +
  Mux(retireValidReg, retireReqNumReg, 0.U)
...
when (ctrlEntries(redirectLastValidPtr.value).allocated) {
  redirectRecoverPtr := getEntryEndSqIdx(redirectLastValidPtr)
}.otherwise {
  redirectRecoverPtr := toPhysicalQueueRetirePtrNext
}
```

Because `sqempty` is 0, the flush-empty signal is 0 — `Sbuffer.scala:541,555` and `MemBlock.scala:1356,1400`:

```scala
val all_empty = cmo_empty && io.sqempty
io.flush.empty := GatedValidRegNext(all_empty)
...
val stIsEmpty = sbuffer.io.flush.empty && uncache.io.flush.empty
atomicsUnit.io.flush_sbuffer.empty := stIsEmpty
```

The next AMO enters `s_wait_flush_sbuffer_resp` and waits on that signal — `AtomicsUnit.scala:309,318-320`:

```scala
state := Mux(sbuffer_empty, s_cache_req, s_wait_flush_sbuffer_resp)
...
when (state === s_wait_flush_sbuffer_resp) {
  when (sbuffer_empty) {
    state := s_cache_req
  }
}
```

and asserts `flush_sbuffer.valid` (`AtomicsUnit.scala:501-504`), but the sbuffer never becomes empty, so the state never advances. This is the same early-retire mechanism as issue #6267; there the blocked instruction is `fsw`, here it is the AMO's sbuffer-flush handshake.
