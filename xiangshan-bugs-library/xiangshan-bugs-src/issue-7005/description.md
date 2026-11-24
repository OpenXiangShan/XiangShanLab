### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and [XiangShan Documents](https://docs.xiangshan.cc/zh-cn/latest). 我已经阅读过 [RISC-V 指令集手册](https://github.com/riscv/riscv-isa-manual) 和 [香山文档](https://docs.xiangshan.cc/zh-cn/latest)。
- [x] I have searched previous issues and PRs and did not find anything relevant. 我已经搜索过之前的 issue 和 PR，并没有找到相关的。

### Describe the feature you'd like

In a build with difftest disabled, `Redirect.debug_runahead_checkpoint_id` costs 13,312 flops (4.4 %) and about 108k standard cells (4.1 %) in MemBlock, plus a 64-bit port on MemBlock and on every block the redirect passes through, when the design is synthesized with module hierarchy kept.

The field (`src/main/scala/xiangshan/Bundle.scala`, `class Redirect`) is declared as `UInt(64.W)` unconditionally. As far as I can find, no logic reads it. It is written in `NewLoadUnit.scala`, `LoadQueueRAW.scala` and `LoadQueueUncache.scala` from `perfDebugInfo.runahead_checkpoint_id`; the assignment in `JumpUnit.scala` is commented out.

Inside MemBlock the source value circulates through registers without reset (LoadQueueReplay → load unit → queue write → LoadQueueReplay), so a synthesis tool cannot prove it constant. A flat synthesis removes it as unread logic. A hierarchical one cannot, because the port's only sinks are unused inputs of other kept modules.

Would you consider declaring the field only in debug builds, the way `perfDebugInfo` already is in `backend/Bundles.scala`?

```scala
val debug_runahead_checkpoint_id = Option.when(backendParams.debugEn)(UInt(64.W))
```

with the three writers using `.foreach(_ := ...)`. If the field is no longer used by difftest at all, removing it would be simpler still.

### Additional context

Measured at commit aa6b52033a with this change carried as a patch, in an open-source hierarchical flow (yosys + OpenROAD, ASAP7), MemBlock synthesized as its own block:

| MemBlock | before | after |
|---|---|---|
| standard cells | 2,633,195 | 2,524,731 |
| flops | 303,846 | 290,534 |
| output port bits | 5,334 | 5,270 |

The flops come out of LoadQueueReplay (7,872), LoadQueueRAW (4,608), LoadQueueUncache (256) and MemBlock's own logic (576). The patch, the build setup and the numbers are in https://github.com/The-OpenROAD-Project/bazel-orfs/pull/1640. That tree also carries unrelated structural patches to the ROB and store queue.

On current `kunminghu-v3` (177ec66911) I checked by reading the source that the declaration and the three writers are unchanged; I have not re-measured there. I have not run a difftest build with the change; with `debugEn` true the generated hardware should be the same as today.

AI disclosure: this report and the patch were produced with Claude Code (Anthropic; models Claude Opus 5.5 and Claude Fable 5.2). I have reviewed the content and the measurements.
