Hi!

**Branch:** kunminghu-v3 (893e1580b7)

**Describe the bug**

Sdtrig reentrancy option 1 prevents triggers with action=0 from firing in M-mode while `mstatus.MIE=0`. With `medeleg[3]=1`, it also blocks them in S-mode while `sstatus.SIE=0`. XiangShan applies this gate when instructions are fetched. Because CSR writes that change only MIE or SIE do not flush the pipeline, instructions fetched before such writes keep the old gate.

Breakpoints therefore misbehave in both directions. Breakpoints right after `csrsi mstatus, 8` are missed, while breakpoints after `csrci sstatus, 2` still fire, for example in handler epilogues before `sret`. These late breakpoints overwrite `sepc` and `scause`, which is the reentrancy the rule exists to prevent.

**Cause**

- `src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala:1257-1259` computes `triggerCanRaiseBpExp` from the current status registers.
- The frontend applies this gate at fetch. `Debug.scala:139` drives `frontendTrigger.triggerCanRaiseBpExp`, which `frontend/ifu/FrontendTrigger.scala:84` passes to `TriggerUtil.triggerActionGen` (`DebugLevel.scala:458`). The backend never checks it again.
- `flushPipe` (`NewCSR.scala:1050-1052`) covers writes to `tdata*` through `triggerFrontendChange`. Writes to the gate inputs, such as MIE or SIE, do not flush.

Load and store triggers behave correctly because they are gated when the access executes.

**To reproduce**

Tests run in M-mode with `mstatus.MDT` cleared and `mie=0`, using one execute trigger with `tdata1 = 0x6000000000000044` (mcontrol6, m=1, execute, match EQ, action 0) and `tdata2 = target`. Trap handlers check `mcause == 3` and `mepc == target`.

| Sequence | XiangShan | Expected |
|---|---|---|
| MIE=1 throughout, then `target` | breakpoint | breakpoint |
| MIE=0 throughout, then `target` | no breakpoint | no breakpoint |
| `csrsi mstatus, 8`, up to 32 `nop`, `target` | no breakpoint | breakpoint |
| `csrci mstatus, 8`, up to 32 `nop`, `target` | breakpoint with MIE=0 | no breakpoint |
| either write, 64 or more `nop`, `target` | follows MIE | follows MIE |
| S-mode, `medeleg[3]=1`, `s=1`: `csrci sstatus, 2`, `target` | breakpoint, `scause=3`, `sepc=target` | no breakpoint |
| S-mode: `csrsi sstatus, 2`, `target` | no breakpoint | breakpoint |

These results come from full-core Verilator simulations of XSTop (MinimalConfig) at aa6b520. All files listed above are identical at 893e158.

**Expected behavior**

Breakpoint gating follows the MIE/SIE value in effect when each instruction executes.

**Suggested fix**

Flush the pipeline on CSR writes that change `triggerCanRaiseBpExp`, at least while execute triggers with action=0 are enabled. Applying the gate in the backend to the raw action=0 match from the IFU would also work.

Thanks!
Flavien
