### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and [XiangShan Documents](https://docs.xiangshan.cc/zh-cn/latest), and I believe this is a XiangShan RTL issue. 我已经阅读过 [RISC-V 指令集手册](https://github.com/riscv/riscv-isa-manual) 和 [香山文档](https://docs.xiangshan.cc/zh-cn/latest)，确认这应该是香山 RTL 的问题。
- [x] I have searched previous issues and PRs and did not find anything relevant. 我已经搜索过之前的 issue 和 PR，并没有找到相关的。
- [ ] I have reproduced the issue using the latest commit on the default branch. 我已经使用默认分支最新的 commit 复现了问题。
- [ ] If this report was generated with AI assistance (otherwise leave unchecked), I have verified the correctness of its content. 如果这是由 AI 生成的（否则请不要勾选），我已经验证了内容的正确性。

### Branch

kunminghu-v2

### Describe the bug

Debug Spec 1.0 §4.6 (Reset): "If the halt signal (driven by the hart's halt request bit in the Debug Module) or hasresethaltreq are asserted when a hart comes out of reset, the hart **must enter Debug Mode before executing any instructions**, but after performing any initialization that would usually happen before the first instruction is executed."

The core has no reset-halt path. `debugMode` is `RegInit(false.B)` in `backend/fu/NewCSR/NewCSR.scala` and is only changed by `dretEvent`/`trapEntryDEvent`; `DcsrCause.Resethaltreq` has no producer. Halt-on-reset relies entirely on the Debug Module keeping `debugInt` asserted after reset (rocket-chip `hrDebugIntReg`), which then travels the ordinary interrupt path: MemBlock `RegNext` → `InterruptFilter` (≈6 cycles) → `NewCSR` → ROB `intrBitSetReg`, and is taken on the first `interrupt_safe` instruction at the ROB head (`backend/rob/Rob.scala:575`).

Two consequences:
1. `Rob.scala:1153-1168` excludes load/store, fence, CSR, vset and AMO from `interrupt_safe`. If the reset vector begins with e.g. `csrr t0, mhartid` or `sd` (common in boot ROMs), that instruction **commits** before the halt: `dpc` is the second instruction, the CSR side effect has happened, `minstret = 1`.
2. Even for `interrupt_safe` first instructions, "before executing any instructions" is a race between the interrupt path and the first commit, not a structural guarantee.

Reporting `cause = 3` instead of 5 is allowed by the spec; the problem is only the instructions executed before the halt. Same on kunminghu-v3.



### Expected behavior

With `haltreq` or the hart's halt-on-reset bit set when reset is deasserted, the hart enters Debug Mode with `dpc` = reset vector and no architectural side effects from the first instruction (`minstret = 0`, no CSR written).

### Environment

- XiangShan branch: kunminghu-v2 (also present on kunminghu-v3)
- XiangShan commit id: 5d3934132 (v2), a2340a516 (v3)

### To Reproduce

Found by code audit; the sequence below is the expected trigger. We are still working on an OpenOCD reproduction (needs `--enable-jtag` and DM `setresethaltreq` / `hartreset`) and will attach it when ready.

1. Reset vector: `csrw mscratch, x0` (or `sd x0, 0(sp)`), then `addi x1, x0, 1`, `addi x2, x0, 2`, ...
2. Via OpenOCD: select the hart, write `dmcontrol.setresethaltreq = 1` (or hold `haltreq = 1`), then assert `hartreset`/`ndmreset` and release it.
3. After the hart halts, read `dpc`, `mscratch`, `minstret`.
   Expected: `dpc` = reset vector, `mscratch` unchanged, `minstret = 0`.
   Predicted: `dpc` = reset vector + 4, `mscratch` written, `minstret = 1` (the CSR instruction is not `interrupt_safe`, so it commits before the debug interrupt is taken).

### Additional context

_No response_
