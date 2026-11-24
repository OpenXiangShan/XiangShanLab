### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and [XiangShan Documents](https://docs.xiangshan.cc/zh-cn/latest). 我已经阅读过 [RISC-V 指令集手册](https://github.com/riscv/riscv-isa-manual) 和 [香山文档](https://docs.xiangshan.cc/zh-cn/latest)。
- [x] I have searched previous issues and PRs and did not find anything relevant. 我已经搜索过之前的 issue 和 PR，并没有找到相关的。

### Describe the question

Hello XiangShan maintainers,

Could you please help confirm whether the code path below represents an unintended behavior? This report comes from a static source review, and I may have missed a project-specific invariant, configuration constraint, or design assumption.

 
## Affected code locations

- `XIANGSHAN_SYSCNT_TIMEASYNC_LEVEL_VALID_STALE_MTIME`: `src/main/scala/device/TimeAsync.scala`

## Static analysis for maintainer review

## Flags

```yaml
owner_review_only: true
email_sent: false
online_pr_created: false
dynamic_validation_performed: false
community_confirmed: false
dco_signoff_present: false
```

## Candidate

- Candidate id: `XIANGSHAN_SYSCNT_TIMEASYNC_LEVEL_VALID_STALE_MTIME`
- Source revision audited: `8a42e727cc58db2b00d4bb2de7b397355f89257c`
- Affected area: RISC-V CLINT/SYSCNT-compatible mtime/time source, `TimeAsync` clock-domain crossing, bus-visible `mtime`, and core-visible CSR time path.

### Source evidence

The affected device is RISC-V-specific:

- `src/main/scala/device/SYSCNT.scala:57-65`: `SYSCNT` creates a `SimpleDevice("clint", Seq("riscv,clint0"))`.
- `src/main/scala/system/SoC.scala:604-606`: SoC connects `clintTime := syscnt.module.io.time` and feeds the same time source to the timer.
- `src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala:741-742`: CSR logic receives `io.fromTop.clintTime` as `m.mHPM.time`.
- `src/main/scala/xiangshan/backend/fu/NewCSR/Unprivileged.scala:151-164`: CSR `time`/`stime`/`vstime` update logic consumes `mHPM.time.valid` and `mHPM.time.bits`.

`SYSCNT` produces a persistent level-valid time stream:

- `src/main/scala/device/SYSCNT.scala:140-154`: after stop handling, `time_en` is normally driven true, `time` continues to update, and `io.time.valid := time_en`.
- `src/main/scala/device/SYSCNT.scala:153-154`: `io.time.bits := time` and `io.time.valid := time_en`.
- `src/main/scala/device/SYSCNT.scala:156-159`: the same `io.time` stream is fed into `TimeAsync` for bus-clock `mtime` readback.

`TimeAsync` is an edge/event receiver:

- `src/main/scala/device/TimeAsync.scala:35-39`: `TimeAsync` synchronizes `io.i_time.valid`, detects only the XOR edge against a delayed copy, and captures `io.i_time.bits` only when that edge occurs.
- `src/main/scala/device/TimeAsync.scala:40-41`: it emits `o_time.valid` from the edge pulse and holds `o_time.bits` at the captured value.
- `src/main/scala/device/SYSCNT.scala:177-188`: bus-visible `mtime` reads use `time_rpt_bus`, which is `timeasync.io.o_time.bits`.

### Why this may matter

For a first-year student: `valid` can mean two different things. In one design, `valid=1` means "the data is currently valid; keep sampling as needed." In another design, a change of `valid` is used like a doorbell that says "new data has arrived." Here the sender behaves like the first design, but `TimeAsync` behaves like the second design.

If `time_en` becomes high and stays high, `TimeAsync` sees only the first low-to-high transition. Later `time.bits` changes do not create new valid edges, so `time_o` can keep the old captured value. For bus-visible `mtime`, that means reads can observe stale time through `time_rpt_bus`. For the core path, owner review should confirm whether the chosen `EnableClintAsyncBridge` path already provides a separate safe CDC protocol or whether the same level-valid/event-valid distinction matters.

The high-confidence claim is narrow:

- The source-level producer/consumer contract is inconsistent.
- This is not a dynamic reproduction.
- This is not a CVE or silicon-impact claim.
- This does not say every CDC path is broken; it focuses on `TimeAsync` as wired by `SYSCNT`.

## Public Duplicate Boundary

Bounded public search on 2026-08-12 did not find an exact public issue or PR for `TimeAsync` stale `mtime` caused by level-valid producer and edge/event receiver mismatch.

Search notes are saved under:

- `public_duplicate_search/0812_xiangshan_syscnt_timeasync_mtime_owner_review/`

## Owner Review Questions

- Is `TimeAsync` intended to receive an event/toggle-valid protocol or a level-valid stream?
- Should `SYSCNT` feed `TimeAsync` through `TimeVldGen` or another per-update toggle generator?
- What freshness guarantee is required for bus-visible `mtime` reads?
- Does `EnableClintAsyncBridge` fully cover the core-visible `clintTime` path, or should the SYSCNT output contract be changed at the source?
- Should `TimeAsync` be renamed or type-wrapped to prevent future level-valid misuse?
