### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and [XiangShan Documents](https://docs.xiangshan.cc/zh-cn/latest). 我已经阅读过 [RISC-V 指令集手册](https://github.com/riscv/riscv-isa-manual) 和 [香山文档](https://docs.xiangshan.cc/zh-cn/latest)。
- [x] I have searched previous issues and PRs and did not find anything relevant. 我已经搜索过之前的 issue 和 PR，并没有找到相关的。

### Describe the question

Hello XiangShan maintainers,

Could you please help confirm whether the code path below represents an unintended behavior? This report comes from a static source review, and I may have missed a project-specific invariant, configuration constraint, or design assumption.

I have not reproduced this dynamically, so I am reporting it as a potential bug for maintainer confirmation rather than as a confirmed defect.

## Candidate coverage

This proposed Issue intentionally groups 2 related potential bug candidates. The current static audit groups them because they share an implementation family, contract boundary, or repair boundary. Please let me know if maintainers would prefer them to be split into independently reviewed reports.

## Affected code locations

- `XIANGSHAN_BITMAPCHECK_MBMC_POLICY_STALE_STATE`: `src/main/scala/xiangshan/cache/mmu/BitmapCheck.scala`
- `XIANGSHAN_PTW_MBMC_BITMAP_INFLIGHT_STALE_STATE`: `src/main/scala/xiangshan/cache/mmu/PageTableWalker.scala`

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

- Candidate id: `XIANGSHAN_MBMC_POLICY_EPOCH_INFLIGHT_BITMAP_STATE`
- Covered source-audit candidates:
  - `XIANGSHAN_PTW_MBMC_BITMAP_INFLIGHT_STALE_STATE`
  - `XIANGSHAN_BITMAPCHECK_MBMC_POLICY_STALE_STATE`
- Source revision audited: `8a42e727cc58db2b00d4bb2de7b397355f89257c`
- Affected area: RISC-V MMU PTW/LLPTW/HPTW, BitmapCheck, BitmapCache, PageTableCache, and MBMC CSR policy lifecycle.

### Source evidence

MBMC is a policy CSR for bitmap-check behavior:

- `src/main/scala/xiangshan/backend/fu/NewCSR/MachineLevel.scala:25-37`: the `Mbmc` CSR module updates `BME`, `CMODE`, `BMA`, `BCLEAR`, and `KEYIDEN`.
- `src/main/scala/xiangshan/backend/fu/NewCSR/MachineLevel.scala:558-562`: field descriptions identify `BMA` as bitmap access-policy encoding, `KEYIDEN` as key-ID checking, `BME` as bitmap checking enable, `BCLEAR` as bitmap-state clear, and `CMODE` as bitmap-checking mode selector.
- `src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala:981-986`: when `HasBitmapCheck` is enabled, writes to `mbmc` are included in `resetSatp`, the same pipeline-flush family as `satp`, `vsatp`, and `hgatp`.

PTW and HPTW hold multi-cycle state while using live MBMC policy:

- `src/main/scala/xiangshan/cache/mmu/PageTableWalker.scala:113-128`: PTW reads `io.csr.mbmc`, computes `bitmap_enable`, and defines a local `flush` from `sfence`, `satp.changed`, `vsatp.changed`, `hgatp.changed`, `priv.virt_changed`, and optional `mmpt.changed`; it does not include an MBMC effective-policy-change event.
- `src/main/scala/xiangshan/cache/mmu/PageTableWalker.scala:139-142`: PTW snapshots selected request-side BitmapCheck fields, but not an MBMC policy epoch or effective MBMC field bundle.
- `src/main/scala/xiangshan/cache/mmu/PageTableWalker.scala:290-309` and `src/main/scala/xiangshan/cache/mmu/PageTableWalker.scala:927-962`: accepted PTW/LLPTW work can enter bitmap-check states and later move through memory/cache/bitmap paths.
- `src/main/scala/xiangshan/cache/mmu/PageTableWalker.scala:1387-1412`: HPTW has the same shape: local flush omits MBMC policy change, while bitmap-check request state is accepted and held across cycles.

BitmapCheck also uses live MBMC policy while retaining state:

- `src/main/scala/xiangshan/cache/mmu/BitmapCheck.scala:105-126`: `getRealPPN` and `getBitmapAddr` use live `csr.mbmc.KEYIDEN` and `csr.mbmc.BMA` to derive bitmap metadata addresses.
- `src/main/scala/xiangshan/cache/mmu/BitmapCheck.scala:126`: `flush` covers `sfence`, `satp.changed`, `vsatp.changed`, `hgatp.changed`, and `priv.virt_changed`, but not MBMC policy changes.
- `src/main/scala/xiangshan/cache/mmu/BitmapCheck.scala:413-520`: `BitmapCache` has resident valid state and accepted pipeline stages. `BCLEAR` clears the resident valid vector, but the local flush for Bitmap/BitmapCache still omits a general MBMC effective-policy-change event.

This candidate is intentionally separated from the existing `PageTableCache` owner-review draft. That draft focuses on resident `l0BitmapReg` bitmap-success state. This draft focuses on accepted in-flight PTW/HPTW/BitmapCheck state and asks whether both should share one MBMC policy-epoch API.

### Why this may matter

For a first-year student: MBMC is like a rulebook that says how the processor should check bitmap protection metadata during page-table walks. PTW and BitmapCheck are like workers that start a multi-step job using that rulebook. If software changes the rulebook while a job is halfway done, the worker must either keep using the old complete rulebook snapshot, stop and restart the job, or attach a version number to prove the result still belongs to the same rulebook.

Current source shows that MBMC writes are serious enough to flush the pipeline, but the PTW/BitmapCheck local lifecycle does not carry an explicit MBMC policy-change signal. Several pieces of accepted state can continue through memory, bitmap-cache, bitmap-response, and final response stages while later logic reads the current live MBMC fields.

The high-confidence claim is narrow:

- This is a source-level owner-review candidate about policy-epoch consistency.
- It does not claim silicon manifestation, exploitable security impact, CVE eligibility, or dynamic reproduction.
- It does not claim every MBMC write must kill all state; unchanged or locked writes may be no-ops.





 

 

## Questions for maintainers

1. Is the contract interpretation above consistent with the intended XiangShan behavior?
2. Is there a local invariant, configuration restriction, or upstream design decision that prevents the described path?
3. If the behavior is unintended, which semantic contract should a future patch implement?
