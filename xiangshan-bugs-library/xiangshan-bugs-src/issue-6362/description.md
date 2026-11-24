### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and [XiangShan Documents](https://docs.xiangshan.cc/zh-cn/latest). 我已经阅读过 [RISC-V 指令集手册](https://github.com/riscv/riscv-isa-manual) 和 [香山文档](https://docs.xiangshan.cc/zh-cn/latest)。
- [x] I have searched previous issues and PRs and did not find anything relevant. 我已经搜索过之前的 issue 和 PR，并没有找到相关的。

### Describe the question

Hello XiangShan maintainers,

Could you please help confirm whether the code path below represents an unintended behavior? This report comes from a static source review, and I may have missed a project-specific invariant, configuration constraint, or design assumption.

I have not reproduced this dynamically, so I am reporting it as a potential bug for maintainer confirmation rather than as a confirmed defect.

## Candidate coverage

This proposed Issue covers one potential bug candidate.

## Affected code locations

- `XIANGSHAN_CACHEINSTRUCTION_CSR_UPDATE_ERROR_OVERWRITE`: `src/main/scala/xiangshan/cache/CacheInstruction.scala`

## Static analysis for maintainer review

### Summary of the static observation

`CSRCacheOpDecoder` appears to let the normal cache-op response path and the
cache-error reporting path drive the same `io.csr.update.w` sink without an
explicit arbiter or loser-preservation state.

The local interface contract in `DistributedCSRUpdateReq` says that at most one
update request is valid per cycle. In `CacheInstruction.scala`, however:

- normal cache-op readback / finish state drives `io.csr.update.w.valid` and
  `io.csr.update.w.bits`;
- delayed `io.error` can later overwrite the same address/data/valid fields and
  force `io.csr.update.w.valid`;
- the normal response state can still observe `io.csr.update.w.fire` and advance
  as though its own update was accepted.

If both producers are active in the same cycle, one CSR-visible event may be
lost or falsely acknowledged. The affected state includes cache readback data,
`OP_FINISH`, and `CACHE_ERROR`.

## Static Evidence

Source revision used for this audit:

```text
OpenXiangShan/XiangShan HEAD 8a42e727cc58db2b00d4bb2de7b397355f89257c
```

Relevant source facts:

- `src/main/scala/xiangshan/Bundle.scala` defines `DistributedCSRUpdateReq` and
  documents: "For each cycle, no more than 1 DistributedCSRUpdateReq is valid".
- `src/main/scala/xiangshan/cache/CacheInstruction.scala` defines
  `CACHE_ERROR` in the cache-control CSR register map.
- `CSRCacheOpDecoder` schedules normal cache-op response updates with
  `schedule_csr_op_resp_data` and `schedule_csr_op_resp_finish`.
- The same module assigns `io.csr.update.w.valid :=
  schedule_csr_op_resp_data || schedule_csr_op_resp_finish`.
- When the delayed `io.error` is valid and `report_to_beu` is set, the module
  assigns the same update sink to `CACHE_ERROR` and forces valid.
- `L1CacheErrorInfo` is documented as also encoding the customized
  `CACHE_ERROR` CSR.
- The legacy CSR path decodes `cacheopRegs("CACHE_ERROR")`, confirming that the
  error value is intended as a visible CSR/debug report, not just an internal
  temporary signal.

## Why this is not a direct patch draft

The safe repair depends on the intended owner contract:

- Should `CACHE_ERROR` have priority over normal readback / `OP_FINISH`?
- If error has priority, should the normal response update be buffered and
  retried?
- If `OP_FINISH` has priority, should the error report be buffered instead?
- Should cache-op completion and asynchronous cache-error reports use separate
  CSR update queues or separate visible registers?
- Are any simultaneous events architecturally impossible by design, and if so,
  should the source encode that invariant as an assertion?

A local one-line priority rewrite would be unsafe because it may simply choose a
different lost event. The mechanically safe shape is an explicit one-update
arbiter plus preservation/retry for every non-winning producer, or an owner
assertion proving that the simultaneous state is impossible.

## Suggested Owner Review Questions

1. Can a delayed `io.error` arrive while `schedule_csr_op_resp_data` or
   `schedule_csr_op_resp_finish` is still pending?
2. Does the CSR update consumer accept only one update per cycle as the Bundle
   comment states?
3. Which CSR-visible event should win when a cache-op response and cache error
   collide?
4. Should the losing event be replayed, merged, or explicitly dropped?
5. Should the intended invariant be documented with an assertion if collision is
   impossible?

## Questions for maintainers

1. Is the contract interpretation above consistent with the intended XiangShan behavior?
2. Is there a local invariant, configuration restriction, or upstream design decision that prevents the described path?
3. If the behavior is unintended, which semantic contract should a future patch implement?
