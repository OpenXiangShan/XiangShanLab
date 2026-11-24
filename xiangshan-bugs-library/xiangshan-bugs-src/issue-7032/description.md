### Summary

If an instruction in the Debug Module's program buffer raises an exception, the hart returns to the debug ROM entry (0x800) instead of its exception entry (0x808). The abstract command then completes with `cmderr = 0`, so the debugger cannot tell that the program buffer failed. Debug Spec 1.0 requires `cmderr = 3` (exception).

Version: `kunminghu-v3` ffe2c0f7 (current HEAD).

### Reproducer

Whole-core Verilator model of `XSTop`, with a small scripted JTAG/DMI driver in the testbench (files in the gist):

1. `dmcontrol.haltreq` → hart halted.
2. `progbuf0 = 0x00000000` (illegal instruction), `progbuf1 = ebreak`.
3. Abstract command with `postexec = 1`, `transfer = 0`; wait for `abstractcs.busy = 0`.

| Build | `abstractcs` after step 3 |
|---|---|
| HEAD ffe2c0f7 | `0x10000002` → **cmderr = 0** |
| HEAD + the one-line change below | `0x10000302` → cmderr = 3 |

Program-buffer accesses that do not fault (`csrr`/`csrw` through `t2`) work normally in both builds.

### Cause

`TrapEntryDEvent` computes the exception target for a trap taken in Debug Mode ([TrapEntryDEvent.scala L57-L98](https://github.com/OpenXiangShan/XiangShan/blob/ffe2c0f77e6e88bbe8589e99b404044defac7c6e/src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/TrapEntryDEvent.scala#L57-L98)):

```scala
private val hasExceptionInDmode = debugMode && hasTrap
val debugPc = Mux(hasExceptionInDmode && !breakPoint, DebugException.U, DebugEntry.U)
out.targetPc.valid := RegNext(valid || hasExceptionInDmode)
```

But NewCSR only updates the trap target on `nonDebugTrapEventValid || trapEntryDEvent.valid` ([NewCSR.scala L1210-L1219](https://github.com/OpenXiangShan/XiangShan/blob/ffe2c0f77e6e88bbe8589e99b404044defac7c6e/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala#L1210-L1219)). Both are false for an exception in Debug Mode, so `DataHoldBypass` keeps the value latched at Debug Mode entry (0x800). The debug ROM's `_entry` path then reports "halted", and the DM finishes the command without an error. This looks like it came with 150a7fa36e (split of the trap/xret target paths), which changed the update condition from `trapEntryDEvent.out.targetPc.valid`.

### Possible fix (tested as above)

```diff
-  private val trapTargetUpdate = RegNext(nonDebugTrapEventValid || trapEntryDEvent.valid, false.B)
+  private val trapTargetUpdate = RegNext(nonDebugTrapEventValid || trapEntryDEvent.valid, false.B) || trapEntryDEvent.out.targetPc.valid
```

Reproduced with our own Verilator testbench of `XSTop` (not emu/difftest) and a scripted JTAG/DMI driver. Files: https://gist.github.com/fireceW/8ce5c71e2b375331d423d5456646d058.

This report was prepared with AI assistance (opencode, Claude Opus 5.5).
