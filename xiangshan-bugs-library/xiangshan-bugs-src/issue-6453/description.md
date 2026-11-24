### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and [XiangShan Documents](https://docs.xiangshan.cc/zh-cn/latest), and I believe this is a XiangShan RTL issue. 我已经阅读过 [RISC-V 指令集手册](https://github.com/riscv/riscv-isa-manual) 和 [香山文档](https://docs.xiangshan.cc/zh-cn/latest)，确认这应该是香山 RTL 的问题。
- [x] I have searched previous issues and PRs and did not find anything relevant. 我已经搜索过之前的 issue 和 PR，并没有找到相关的。
- [x] I have reproduced the issue using the latest commit on the default branch. 我已经使用默认分支最新的 commit 复现了问题。
- [x] If this report was generated with AI assistance (otherwise leave unchecked), I have verified the correctness of its content. 如果这是由 AI 生成的（否则请不要勾选），我已经验证了内容的正确性。

### Branch

kunminghu-v3

### Describe the bug

At commit `e5c70547f3a966accf20a4b065ec1d8e33443180`, the optional
`MPTTableWalker` prepares a PMP read check for each MPT table-entry address, but
leaves `io.pmp.req.valid` as `DontCare`. In `s_mem_req`, the outgoing memory
request is admitted using only the source-ID mask and flush state:

```scala
io.pmp.req.valid := DontCare
...
mem.req.valid := !mem.mask && !flush
```

The parent L2TLB connects this port to a same-cycle `PMPChecker`. The
`io.pmp.resp.ld` and `io.pmp.resp.mmio` result contributes to the later
`accessFault`, but it does not suppress the memory request that fetches the
denied MPT entry.

The narrow claim is an admission-order defect: a denied read can reach the
memory system before the walker reports an access fault. A direct current
reproducer is still required to establish the observable effect, especially
for a side-effecting MMIO address.

MPT is disabled by default (`HasMptCheck=false`); this report does not claim a
failure in `DefaultConfig`.

### Expected behavior

For an MPT entry address denied by PMP/PMA, the walker should not assert a
memory request for that address. It should return the existing access-fault
result without performing the denied read.

### Environment

- XiangShan branch: `kunminghu-v3`
- XiangShan commit: `e5c70547f3a966accf20a4b065ec1d8e33443180`

### To Reproduce

This required step has not yet been executed. A focused test should:

1. enable MPT and configure an MPT table-entry address denied by PMP/PMA;
2. preferably place the denied address in a read-side-effecting MMIO region;
3. trigger an MPT walk;
4. observe both the walker access-fault result and the shared memory request;
5. fail if a TileLink/AXI read is emitted for the denied address.

### Additional context
