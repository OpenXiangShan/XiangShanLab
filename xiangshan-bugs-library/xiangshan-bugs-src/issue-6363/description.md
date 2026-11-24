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

- `XIANGSHAN_ICACHE_DOUBLELINE_SINGLE_PMP_CHECK`: `src/main/scala/xiangshan/frontend/icache/ICacheMainPipe.scala`

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

- Candidate id: `XIANGSHAN_ICACHE_DOUBLELINE_SINGLE_PMP_CHECK`
- Source revision audited: `8a42e727cc58db2b00d4bb2de7b397355f89257c`
- Affected area: RISC-V frontend ICache instruction fetch, doubleline fetch, PMP/PMA/PBMT permission and attribute coverage.

### Source evidence

The ICache parameter layer explicitly supports two consecutive cacheline accesses:

- `src/main/scala/xiangshan/frontend/icache/Parameters.scala:68-72`: `PortNumber` is fixed to `2`, with a source comment saying it allows concurrent access to consecutive cachelines.
- `src/main/scala/xiangshan/frontend/ftq/Bundles.scala:137-153`: `FtqFetchReq` carries `startVAddr`, `nextLineVAddr`, `isCrossLine`, `bankSel`, and per-line `vSetIdx`.
- `src/main/scala/xiangshan/frontend/icache/Bundles.scala:240-249`: `WayLookupToMainPipeBundle` forwards `Vec[FetchPorts]` request information into `ICacheMainPipe`, and each request has `startVAddr`, `nextLineVAddr`, and `isCrossLine`.

In `ICacheMainPipe`, the second line can participate in real cache/miss side effects:

- `src/main/scala/xiangshan/frontend/icache/ICacheMainPipe.scala:165-167`: `s1_lineValid` marks line 1 valid when `s1_req(i).isCrossLine` is true.
- `src/main/scala/xiangshan/frontend/icache/ICacheMainPipe.scala:309`: `s1_vAddr` is built from each request's `startVAddr` and `nextLineVAddr`.
- `src/main/scala/xiangshan/frontend/icache/ICacheMainPipe.scala:362-368`: `s1_shouldFetch` is generated for every request/line slot.
- `src/main/scala/xiangshan/frontend/icache/ICacheMainPipe.scala:387-389`: the miss arbiter can issue line `i` using `getBlkAddrFromPTag(s1_vAddr(i), s1_pTag)`.

However, the PMP/PMA decision is made once from the first request address:

- `src/main/scala/xiangshan/frontend/icache/ICacheMainPipe.scala:264-268`: the PMP request is valid for stage 1 and uses `getPAddrFromPTag(s1_req(0).startVAddr, s1_pTag)`, `size := 3.U`, and `cmd := TlbCmd.exec`.
- `src/main/scala/xiangshan/frontend/icache/ICacheMainPipe.scala:269-273`: the code forms one `s1_pmpException`, one `s1_pmpMmio`, and one merged `s1_exception`.
- `src/main/scala/xiangshan/frontend/icache/ICacheMainPipe.scala:360-368`: the shared `s1_isMmio` combines `s1_pmpMmio` with line-0 PBMT and gates every `s1_shouldFetch` entry.
- `src/main/scala/xiangshan/frontend/icache/ICacheMainPipe.scala:432-435`: the IFU-visible metadata also carries the shared `s1_pmpMmio`.

The same first-request PMP/PMA query pattern appears in `ICachePrefetchPipe`:

- `src/main/scala/xiangshan/frontend/icache/ICachePrefetchPipe.scala:345-355`: the prefetch path drives PMP with `s1_req(0).startVAddr`, `size := 3.U`, and one `s1_pmpMmio`.

### Why this may matter

For a RISC-V instruction fetch, PMP/PMA/PBMT attributes are part of the authorization and memory-attribute contract. If the first cacheline is executable and cacheable but the adjacent second cacheline is denied, uncacheable, or MMIO, a first-line-only check does not by itself authorize a second-line cache fill or miss request.

The static source-level concern is not "cross page" by itself. The stronger condition is checked extent versus executed extent: every line that can trigger a physical cache/miss side effect must either be covered by the same protection/attribute decision or be proven homogeneous by an authoritative policy partition. Same-page fetch does not automatically prove same PMP/PMA/MMIO/PBMT attributes, because PMP/PMA boundaries can be smaller or differently aligned than pages.

## Public Duplicate / Related Issue Boundary

Bounded public search on 2026-08-12 did not find an exact issue or PR for `ICacheMainPipe` doubleline fetch reusing a first-line `s1_pmpMmio` / `s1_pmpException` result for the second cacheline miss path.

Related same-family public discussions:

- <https://github.com/OpenXiangShan/XiangShan/issues/6161>
- <https://github.com/OpenXiangShan/XiangShan/issues/6162>

Those issues discuss PMP extent coverage for PTW/data accesses. They are useful same-family evidence that protection extent matters, but they do not directly cover the frontend ICache doubleline path.

## Why No Patch Is Included Here

I do not want to force this candidate into a local one-line patch. The appropriate repair may depend on the intended ICache frontend contract:

1. Per-line authorization: check PMP/PMA/PBMT for every line that can produce a cache/miss side effect.
2. Combined-range authorization: check one range that conservatively covers the full doubleline fetch extent.
3. Explicit homogeneity invariant: prove and assert that a valid doubleline fetch window cannot cross PMP/PMA/MMIO/PBMT boundaries.
4. Split-path behavior: if the second line cannot be proven homogeneous, split the fetch so the second line is fetched only after its own protection/attribute decision.

Because this choice affects frontend timing, PMP/PMA ports, and the intended ICache fetch contract, this artifact is an owner-review issue/design draft rather than a patch.

## Owner Review Questions

- Is the intended invariant that any accepted ICache doubleline fetch is homogeneous with respect to PMP, PMA, MMIO, and PBMT?
- If that invariant exists, where should it be documented or asserted so future changes do not silently break it?
- If that invariant does not exist, should `ICacheMainPipe` perform per-line protection/attribute checks, a combined range check, or split the second line into a separately authorized request?
- Should `ICachePrefetchPipe` use the same rule as `ICacheMainPipe`, given the same first-request PMP/PMA query pattern?
- Is `size := 3.U` intended to mean one 8-byte instruction-fetch protection probe, or is it intended as a proxy for a wider cacheline fetch? If the latter, where is that contract enforced?

## Questions for maintainers

1. Is the contract interpretation above consistent with the intended XiangShan behavior?
2. Is there a local invariant, configuration restriction, or upstream design decision that prevents the described path?
3. If the behavior is unintended, which semantic contract should a future patch implement?
