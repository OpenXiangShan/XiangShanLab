### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and [XiangShan Documents](https://docs.xiangshan.cc/zh-cn/latest), and I believe this is a XiangShan RTL issue. 我已经阅读过 [RISC-V 指令集手册](https://github.com/riscv/riscv-isa-manual) 和 [香山文档](https://docs.xiangshan.cc/zh-cn/latest)，确认这应该是香山 RTL 的问题。
- [x] I have searched previous issues and PRs and did not find anything relevant. 我已经搜索过之前的 issue 和 PR，并没有找到相关的。
- [x] I have reproduced the issue using the latest commit on the default branch. 我已经使用默认分支最新的 commit 复现了问题。
- [ ] If this report was generated with AI assistance (otherwise leave unchecked), I have verified the correctness of its content. 如果这是由 AI 生成的（否则请不要勾选），我已经验证了内容的正确性。

### Branch

kunminghu-v3

### Describe the bug

In the default `kunminghu-v3` configuration, the TXDAT admission logic can undercount pipeline reservations when all four s2-s5 predicates are true.

The affected expression is in:

`XSCache/src/main/scala/coupledL2/TXDAT.scala`

```scala
val inflightCnt =
  PopCount(Cat(pipeStatus_s4_s5.map(/* ... */))) +
  PopCount(Cat(pipeStatus_s2_s3.map(/* ... */))) +
  queueCnt
```
Each PopCount counts two bits and therefore has a 2-bit result. Chisel's ordinary UInt + does not preserve the carry bit. Because the expression is left-associative, the first addition is evaluated as a 2-bit addition:
```2 + 2 = 2'b00```
The generated SystemVerilog contains an explicit 2-bit cast around this intermediate sum before it is extended and added to the queue count.

For example, with four matching s2-s5 entries and `queueCnt = 12`:
```scala
expected inflightCnt = 12 + 4 = 16
implemented inflightCnt = 12 + 0 = 12
```
Consequently, both admission signals remain deasserted:
```scala
noSpaceForSinkBReq = inflightCnt >= 16
noSpaceForMSHRReq  = inflightCnt >= 14
```
The four pipeline predicates are reachable for back-to-back data-returning snoops. Such requests have unavailable data in s3, are recorded as pending TXDAT in s4, and produce TXDAT in s5. Requests to alternating data banks can enter on consecutive cycles.

If TXDAT output is stalled, the task queue stops dequeuing. The incorrect count permits additional SinkB requests until the queue becomes full. At that point io.in.ready is low while MainPipe may still present a valid s5 TXDAT task. This triggers:
```scala
assert(!io.in.valid || io.in.ready, "TXDAT should never be full")
```
Without assertions, MainPipe does not retain an unaccepted s5 task under this backpressure condition, so a CHI DAT response may be lost.

`TXRSP.scala` contains the same narrow addition. However, I have not established a legal integrated trace in the current implementation where all four TXRSP predicates are simultaneously true. I therefore consider TXRSP a related latent width bug rather than a confirmed queue-overflow path.



### Expected behavior

The pipeline reservation count must preserve the full range 0 through 4 before being added to `queueCnt`.

With four matching pipeline entries and `queueCnt = 12`, `inflightCnt` must be 16. SinkB and MSHR admission must then be blocked before the TXDAT queue can become oversubscribed.

A valid TXDAT task must never be discarded because the TXDAT queue is full.


### Environment

detected by a static analysis tool.

### To Reproduce

detected by a static analysis tool.

### Additional context

_No response_
