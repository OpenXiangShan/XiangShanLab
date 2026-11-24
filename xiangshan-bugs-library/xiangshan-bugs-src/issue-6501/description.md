### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and [XiangShan Documents](https://docs.xiangshan.cc/zh-cn/latest), and I believe this is a XiangShan RTL issue. 我已经阅读过 [RISC-V 指令集手册](https://github.com/riscv/riscv-isa-manual) 和 [香山文档](https://docs.xiangshan.cc/zh-cn/latest)，确认这应该是香山 RTL 的问题。
- [x] I have searched previous issues and PRs and did not find anything relevant. 我已经搜索过之前的 issue 和 PR，并没有找到相关的。
- [x] I have reproduced the issue using the latest commit on the default branch. 我已经使用默认分支最新的 commit 复现了问题。
- [ ] If this report was generated with AI assistance (otherwise leave unchecked), I have verified the correctness of its content. 如果这是由 AI 生成的（否则请不要勾选），我已经验证了内容的正确性。

### Branch

kunminghu-v2

### Describe the bug

An explicit write to `minstret` is applied in the cycle the CSR unit executes the `csrw`. The retirement count of that same `csrw` arrives 2+ cycles later (`Rob.retireCounter` → `CtrlBlock` `RegNext` → `robCommit.instNum`) and is added on top of the written value, so the writing instruction counts itself:

```scala
// MachineLevel.scala, minstret (tested build 2acbf327c and v2 HEAD 5d393413: L353-360; v3 HEAD 14af5521 L366-375)
when(w.wen) {
  reg := w.wdata
}.elsewhen(!this.mcountinhibit.IR && robCommit.instNum.valid) {
  reg := reg.ALL.asUInt + robCommit.instNum.bits
}
```

The spec requires the write to be the *last* thing the writing instruction does (`machine.adoc`, 3.1.11 Hardware Performance Monitor, `norm:mcycle_minstret_wr`):

> Any CSR write takes effect after the writing instruction has otherwise completed.

so the instruction after `csrw minstret, X` must observe exactly `X`. Spike implements this with a `written` flag that makes the post-instruction `bump()` skip once (`csrs.cc:1315-1327`, comment: "The ISA mandates that explicit writes to instret take precedence over the instret, so simply skip the increment").

This cannot be caught by difftest: all accesses to `mcycle..mhpmcounter31` are marked `isPerfCnt` → `skip`, so the reference never executes the `csrw`/`csrr` and simply copies the DUT's result; NEMU also has the same X+1 behaviour.

### Expected behavior

```
li   t0, 0x1000
csrw minstret, t0
csrr t1, minstret      # spec / Spike: t1 == 0x1000.  XiangShan: 0x1001
```

### Environment

- XiangShan: kunminghu-v2, commit `2acbf327c` (2026-06-24, v3.2.2-alpha-1168-g2acbf327c), DefaultConfig (`--issue E.b`). The cited lines are unchanged on kunminghu-v2 HEAD `5d393413` (2026-09-02) and kunminghu-v3 HEAD `a2340a516` (2026-09-06).
- NEMU `67da68a2` (ready-to-run e79a631): same X+1. Spike `611a62b7` (ready-to-run) / upstream `3e7bf30d`: X. difftest `7b5fc3bb2`, utility `fcb82239`.

### To Reproduce

Attached `minstret-write-poc` (nexus-am, M-mode, `mcountinhibit = 0`, `.option norvc` so instruction counts are exact).

```
make ARCH=riscv64-xs
./build/emu -i minstret-write-poc-riscv64-xs.bin --no-diff
```

Relevant output on 2acbf327c:

```
csrw minstret,X ; csrr          -> X+1   (spec: X+0)   <-- MISMATCH
IR=1: csrw minstret,X ; csrr    -> X+0   (spec: X+0)
```

The second line is the control: with `mcountinhibit.IR = 1` the hardware increment is disabled and the same sequence reads exactly X, so the extra 1 is the `csrw`'s own retirement being added to the written value.

(The other lines in the log put N filler instructions between the write and the read; those are additionally affected by a read-side lag — the retire count reaches the CSR ~3 cycles after commit and a `csrr` can read inside that window — which is a separate observation and not part of this report.)

[minstret-write-poc-results.zip](https://github.com/user-attachments/files/31882318/minstret-write-poc-results.zip)

### Additional context

Suggested fix: skip the writing instruction's own contribution, as Spike does — e.g. when `w.wen` fires, remember that the `robCommit.instNum` batch containing this `csrw` must not be added (or subtract 1 from it); alternatively apply the write at commit time.

`mcycle` and `mhpmcounter3-31` use the same `when(w.wen) ... .elsewhen(increment)` structure and likely have the same behaviour; only `minstret` was measured.
