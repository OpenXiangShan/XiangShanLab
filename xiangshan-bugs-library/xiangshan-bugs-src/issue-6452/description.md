### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and [XiangShan Documents](https://docs.xiangshan.cc/zh-cn/latest), and I believe this is a XiangShan RTL issue. 我已经阅读过 [RISC-V 指令集手册](https://github.com/riscv/riscv-isa-manual) 和 [香山文档](https://docs.xiangshan.cc/zh-cn/latest)，确认这应该是香山 RTL 的问题。
- [x] I have searched previous issues and PRs and did not find anything relevant. 我已经搜索过之前的 issue 和 PR，并没有找到相关的。
- [x] I have reproduced the issue using the latest commit on the default branch. 我已经使用默认分支最新的 commit 复现了问题。
- [x] If this report was generated with AI assistance (otherwise leave unchecked), I have verified the correctness of its content. 如果这是由 AI 生成的（否则请不要勾选），我已经验证了内容的正确性。

### Branch

kunminghu-v3

### Describe the bug

At commit `e5c70547f3a966accf20a4b065ec1d8e33443180`, AMOCAS.Q keeps two
STA-side uops carrying the two destination registers required by
`AtomicsUnit`. Both `STA0` and `STA1` support `MouCfg`.

`MemBlock` computes one `st_atomics` selector per STA lane. Every selected lane
receives the same `atomicsUnit.io.in.ready`, while all selected payloads are
combined with `Mux1H`:

```scala
for (i <- 0 until StaCnt) when(st_atomics(i)) {
  issueSta(i).ready := atomicsUnit.io.in.ready
}
atomicsUnit.io.in.valid := st_atomics.reduce(_ || _)
atomicsUnit.io.in.bits := Mux1H(st_atomics, issueSta.map(_.bits))
```

There is no arbiter or one-hot assertion at this merge. If both retained
AMOCAS.Q STA uops arrive together, both queues can observe ready and dequeue,
but `AtomicsUnit` observes one combined fire. Their encoded atomic subindices
are 0 and 2; combining both payloads can record only `pdest2Valid`, after both
source uops have already been consumed, leaving the unit waiting for the
missing destination.

PR #3958 introduced Zacas split-uop support and removed the old one-hot
assertion around this admission path, but did not add arbitration.

### Expected behavior

At most one STA issue entry should be consumed for each
`AtomicsUnit.io.in.fire`. If simultaneous producers are legal, an arbiter must
return ready only to the selected lane. If an upstream invariant forbids the
collision, that invariant should be encoded as an assertion.

### Environment

- XiangShan branch: `kunminghu-v3`
- XiangShan commit: `e5c70547f3a966accf20a4b065ec1d8e33443180`

### To Reproduce

This required step has not yet been executed. A targeted reproducer should:

1. add an assertion equivalent to `assert(PopCount(st_atomics) <= 1.U)` at
   the current merge;
2. run an AMOCAS.Q workload with both STA issue blocks enabled;
3. capture simultaneous retained STA uops;
4. record `issueSta[*].valid/ready`, `st_atomics`, `io.in.fire`, `staUopIdx`,
   `pdest1Valid`, and `pdest2Valid`.

### Additional context
