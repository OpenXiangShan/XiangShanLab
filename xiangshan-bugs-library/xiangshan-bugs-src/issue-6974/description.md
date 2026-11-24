### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and [XiangShan Documents](https://docs.xiangshan.cc/zh-cn/latest), and I believe this is a XiangShan RTL issue. 我已经阅读过 [RISC-V 指令集手册](https://github.com/riscv/riscv-isa-manual) 和 [香山文档](https://docs.xiangshan.cc/zh-cn/latest)，确认这应该是香山 RTL 的问题。
- [x] I have searched previous issues and PRs and did not find anything relevant. 我已经搜索过之前的 issue 和 PR，并没有找到相关的。
- [x] I have reproduced the issue using the latest commit on the default branch. 我已经使用默认分支最新的 commit 复现了问题。
- [ ] If this report was generated with AI assistance (otherwise leave unchecked), I have verified the correctness of its content. 如果这是由 AI 生成的（否则请不要勾选），我已经验证了内容的正确性。

### Branch

kunminghu-v3

### Describe the bug

The LFST computes the number of not-yet-issued stores associated with a dispatch request by adding:

1. the valid entries already stored in the LFST; and
2. older stores with the same SSID in the current dispatch bundle.

`LFSTWidth` is 2 and the relevant code in `src/main/scala/xiangshan/mem/mdp/StoreSet.scala` is:

```scala
val notIssuedStoreCount =
  PopCount(validVec(io.dispatch.req(i).bits.ssid)) +
  PopCount(hitInDispatchBundleVec)
val notIssuedStoreGt1 = notIssuedStoreCount > 1.U

io.dispatch.resp(i).bits.strictShouldWait :=
  io.dispatch.req(i).valid &&
  !io.dispatch.req(i).bits.isstore &&
  notIssuedStoreGt1
```

Chisel UInt + does not widen the result to preserve the carry. For zero-based dispatch slot i = 2, both PopCount results are two bits wide, so their sum is also two bits wide.

A reachable case is:
1.the selected SSID already has two valid LFST entries;
2.dispatch slots 0 and 1 contain two older stores with the same SSID;
3.dispatch slot 2 contains a strict-predicted load with that SSID.
The mathematical count is four, but the two-bit addition produces zero:
```scala
2 + 2 = 4
3'b100 truncated to 2'b00
notIssuedStoreGt1 = false
```
The same problem exists at slot `i = 3`: mathematical totals of four and five are truncated to zero and one.

`shouldWait` is computed independently and normally remains set. However, `Dispatch.scala` filters the original strict prediction with `strictShouldWait`:
```scala
fromRenameUpdate(i).bits.loadWaitStrict :=
  fromRename(i).bits.loadWaitStrict &&
  io.lfst.resp(i).bits.shouldWait &&
  io.lfst.resp(i).bits.strictShouldWait
```
Consequently, the load is incorrectly changed from strict waiting to ordinary single-target waiting.

In `NewStoreQueue`, strict waiting checks all older stores with unresolved addresses, while ordinary waiting checks only the store identified by `waitSqIdx`. Since a younger store can calculate its address before an older store, waiting for this single store does not preserve the intended strict-wait behavior.

The existing `LoadQueueRAW` mechanism appears to detect an actual late store-load alias and request a rollback. Therefore, this report does not claim a silent architectural correctness failure. The confirmed effects are incorrect memory-dependence prediction, execution that should have been blocked by the strict predictor, and potentially unnecessary memory-order rollbacks and performance loss.


### Expected behavior

`notIssuedStoreCount` must represent the complete sum of the two PopCount results.

Whenever more than one matching store is not yet issued, `notIssuedStoreGt1` and `strictShouldWait` should remain asserted. In particular:

- LFST count 2 plus bundle count 2 must produce 4, not 0.
- LFST count 2 plus bundle count 3 must produce 5, not 1.

A strict-predicted load in these cases should use the StoreQueue strict path and wait until all relevant older store addresses have been calculated.

### Environment

detected by a static analysis tool.

### To Reproduce

detected by a static analysis tool.

### Additional context

_No response_
