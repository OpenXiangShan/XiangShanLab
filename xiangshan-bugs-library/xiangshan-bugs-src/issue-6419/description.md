### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and [XiangShan Documents](https://docs.xiangshan.cc/zh-cn/latest), and I believe this is a XiangShan RTL issue. 我已经阅读过 [RISC-V 指令集手册](https://github.com/riscv/riscv-isa-manual) 和 [香山文档](https://docs.xiangshan.cc/zh-cn/latest)，确认这应该是香山 RTL 的问题。
- [x] I have searched previous issues and PRs and did not find anything relevant. 我已经搜索过之前的 issue 和 PR，并没有找到相关的。
- [x] I have reproduced the issue using the latest commit on the default branch. 我已经使用默认分支最新的 commit 复现了问题。
- [ ] If this report was generated with AI assistance (otherwise leave unchecked), I have verified the correctness of its content. 如果这是由 AI 生成的（否则请不要勾选），我已经验证了内容的正确性。

### Branch

kunminghu-v2

### Describe the bug

`dcsr.stopcount` is `RW` and reads back 1. So XiangShan:

- `csrr instret` / `csrr cycle` are gated: they return a snapshot taken when the freeze turned on and stay unchanged for the whole halt.
- `minstret` / `mcycle` are not gated: `minstret` still adds ROB retirements, `mcycle` still adds 1 per cycle. Reading them mid-halt shows the count rising while `instret`/`cycle` sit at the snapshot — the counter and its read-only shadow disagree at the same instant.
- On `dret` the mux switches back to the live counters, so `instret`/`cycle` jump to the already-advanced `minstret`/`mcycle`.

Scenario: halt the hart, set `dcsr.stopcount=1`, let it execute in Debug Mode (debug ROM park loop and/or program buffer). In the same halt, `csrr instret` is stuck at the snapshot while `csrr minstret` (`0xB02`) keeps growing. Resume with `dret`: `instret`/`cycle` jump forward by the instructions / cycles that happened while halted. `stopcount` is supposed to prevent exactly that — the freeze only ever reached the read mux, never the counters.

RTL: freeze signal `debugModeStopCount` (`NewCSR.scala:291-294`) only feeds the unprivileged shadows (`Unprivileged.scala:279-283`):

```scala
when(unprivCountUpdate) { reg := mHPM.instret }   // same for cycle
regOut := Mux(debugModeStopCount, reg.asUInt, mHPM.instret)
```

`minstret` / `mcycle` have no such term (`MachineLevel.scala:342-361`). `minstret` increments on `robCommit.instNum` (`wrapper/CSR.scala:161-162`); `mcycle` increments every cycle unless `mcountinhibit.CY`.

Spec (Debug Spec 1.0): `stopcount=1` means *“Don't increment any hart-local counters while in Debug Mode … These counters include the `instret` CSR.”* On a single-hart core *“`cycle` should be stopped.”* `instret`/`cycle` are the read-only shadows of `minstret`/`mcycle` (Priv ISA), so the counters themselves must not increment. `stoptime` explicitly resyncs `time` to `mtime` on exit; `stopcount` has no such wording.


### Expected behavior

With stopcount=1, minstret and mcycle do not increment in Debug Mode; since instret/cycle are their read-only shadows, they are then unchanged across dret.

### Environment

Found by code audit. To verify the bug I wrote an AI-assisted ChiselTest: `xiangshan-stopcount/DebugSpecStopcountTest.scala` (drives `NewCSR`, injects ROB retirement)



### To Reproduce

1. Enter Debug Mode (`platformIRP.debugIP` + trap).
2. Set `dcsr.stopcount`; bit 10 reads back 1.
3. Inject retirement on `fromRob.commit.instNum`.
4. `dret`.

Result: read mid-halt, `minstret` has increased while `instret` is still at the snapshot (the two disagree at the same instant); after `dret`, `instret` jumps to the advanced `minstret`.

The test asserts current RTL, so it passes while the bug is present.

### How to run the test

Copy `DebugSpecStopcountTest.scala` to `src/test/scala/xiangshan/backend/fu/NewCSR/` on `kunminghu-v2`. Verilator on `PATH`:

```
mill xiangshan.test.testOnly xiangshan.backend.fu.NewCSR.DebugSpecStopcountTest
```

### Environment

- java version: openjdk version "23.0.2" 2025-01-21
- mill version: 0.12.3
- XiangShan branch: kunminghu-v2
- XiangShan commit id: `e12436c7cba86b195deec24981976d78bc263661`

### Additional context
[DebugSpecStopcountTest.scala.zip](https://github.com/user-attachments/files/31431188/DebugSpecStopcountTest.scala.zip)
_No response_
