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

- `XIANGSHAN_ICACHE_ECC_INJECT_INFLIGHT_ADDR_TARGET_MUTATION`: `src/main/scala/xiangshan/frontend/icache/ICacheCtrlUnit.scala`

## Static analysis for maintainer review

### Summary of the static observation

`ICacheCtrlUnit` implements a software-triggered ECC injection command as a
multi-cycle FSM:

1. software writes the injection address register;
2. software writes the control register with `inject`;
3. the FSM reads ICache metadata to find the target way;
4. it writes either metadata or data array with poison enabled.

The current source appears to keep using the live MMIO registers
`eccIAddr.pAddr` and `eccCtrl.iTarget` after the injection has started. A later
MMIO write while the FSM is in `Working` state can therefore mutate the address
or target used by later FSM stages.

## Static Evidence

Source revision used for this audit:

```text
OpenXiangShan/XiangShan HEAD 8a42e727cc58db2b00d4bb2de7b397355f89257c
```

Relevant source facts:

- `Parameters.scala` defaults `EnableCtrlUnit` to true.
- `ICache.scala` instantiates `ICacheCtrlUnit` when `EnableCtrlUnit` is true.
- `ICacheCtrlUnit.scala` exposes a TileLink register node for ECC control and
  ECC injection address registers.
- `ICacheImp.scala` routes real ICache meta/data array ports to `ctrlUnit`
  while `ctrlUnit.io.injecting` is true.
- `ICacheCtrlUnit.scala` derives `iVSetIdx`, `iPAddr`, and `iPTag` directly
  from live `eccIAddr.pAddr`.
- The meta read request uses live `iVSetIdx`.
- The meta response tag comparison uses live `iPTag`, then stores `iWaymask`.
- The final meta/data write uses live address-derived set fields and the stored
  waymask.
- The control register write handler ignores a new `inject` request unless
  `iStatus` is `Idle`, but it still assigns `x.iTarget := req.iTarget` on every
  valid write.
- The ECC injection address register is a direct `RegField` over `eccIAddr`,
  with no visible `Working`-state write guard.

## Why this matters

This is not only a stale status-register issue. While injecting, `ICacheImp`
connects the control unit to the real ICache meta/data arrays. A write between
the meta read request, meta response, and final write could combine:

- an old meta-array request address;
- a new tag comparison address;
- an old waymask;
- a new final write set;
- a changed target array selection.

Depending on timing and owner intent, this can make the injection fail with a
false `NotFound`, inject the wrong array, or write a poisoned entry selected by
a mixed old/new command identity.

## Suggested Owner Review Questions

1. Are MMIO writes to `ecc_iaddr` and `ecc_control.iTarget` intended to be
   accepted while an injection is `Working`?
2. If such writes are accepted, are they meant to update only the next command,
   or are they allowed to affect the current command?
3. Should the injection FSM snapshot `pAddr` and `iTarget` at command
   acceptance?
4. Should busy-period writes be rejected, ignored, or accepted into a separate
   next-command shadow register?
5. If in-flight mutation is impossible by construction, should an assertion be
   added to encode that invariant?

 
## Questions for maintainers

1. Is the contract interpretation above consistent with the intended XiangShan behavior?
2. Is there a local invariant, configuration restriction, or upstream design decision that prevents the described path?
3. If the behavior is unintended, which semantic contract should a future patch implement?
