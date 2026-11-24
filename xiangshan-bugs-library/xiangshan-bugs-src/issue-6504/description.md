### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and [XiangShan Documents](https://docs.xiangshan.cc/zh-cn/latest), and I believe this is a XiangShan RTL issue. 我已经阅读过 [RISC-V 指令集手册](https://github.com/riscv/riscv-isa-manual) 和 [香山文档](https://docs.xiangshan.cc/zh-cn/latest)，确认这应该是香山 RTL 的问题。
- [x] I have searched previous issues and PRs and did not find anything relevant. 我已经搜索过之前的 issue 和 PR，并没有找到相关的。
- [ ] I have reproduced the issue using the latest commit on the default branch. 我已经使用默认分支最新的 commit 复现了问题。
- [ ] If this report was generated with AI assistance (otherwise leave unchecked), I have verified the correctness of its content. 如果这是由 AI 生成的（否则请不要勾选），我已经验证了内容的正确性。

### Branch

kunminghu-v2

### Describe the bug

Debug Spec 1.0 §4.5.1 (Step): "If the instruction that is executed causes the PC to change to an address where an instruction fetch causes an exception, that exception does not occur until the next time the hart is resumed. **Similarly, a trigger at the new address does not fire until the hart actually attempts to execute that instruction.**" `cause=2` is reserved for a trigger fired by the stepped instruction itself.
XiangShan handles the fetch-exception half correctly but not the trigger half. Single-stepping `A: jal P` where an execute trigger (`mcontrol6{execute=1, action=1}`, `tdata2=P`) is armed:
- `frontend/PreDecode.scala:481-484` — the frontend trigger hit is masked only by `!select && !debugMode`; nothing about single-step. The uop for `P` therefore enters the pipeline carrying `trigger=DebugMode`.
- The same uop is the one marked `singleStep` (the instruction after the stepped one, `NewDispatch.scala`), so at the ROB head both `triggerEnterDebugMode` and `hasSingleStep` are asserted.
- `backend/fu/NewCSR/CSREvents/TrapEntryDEvent.scala:60-66` orders `triggerEnterDebugMode -> Trigger` before `hasSingleStep -> Step`, so `dcsr.cause = 2`.
The hart never attempted to execute `P` during this step (it re-enters Debug Mode before `P` executes), so per spec the trigger must not have fired yet; Debug Mode should be entered with `cause=4`, and the trigger should fire on the next resume. Same code on kunminghu-v3 (`frontend/ifu/FrontendTrigger.scala:75`, same `TrapEntryDEvent` ordering).


### Expected behavior

After the step: `dcsr.cause = 4 (step)`, `dpc = P`. On the following `resume`, the execute trigger at `P` fires and Debug Mode is re-entered with `cause = 2`, `dpc = P`. Currently the first entry already reports `cause = 2`.


### Environment

- XiangShan branch: kunminghu-v2 (also present on kunminghu-v3)
- XiangShan commit id: 5d3934132 (v2), a2340a516 (v3)

### To Reproduce

Found by code audit; the sequence below is the expected trigger. We are still working on an OpenOCD reproduction and will attach it when ready.

1. `halt`; program trigger 0: `tdata1 = mcontrol6{type=6, dmode=1, execute=1, action=1, m=1, match=EQ}`, `tdata2 = P`.
2. `dpc = A`, where `A: jal P` (or any taken branch/jump to `P`).
3. Set `dcsr.step = 1`, `resume`.
4. Read `dcsr.cause`. Expected 4 (step); observed 2 (trigger). Control: with the trigger disabled, `cause = 4` and `dpc = P`.

### Additional context

_No response_
