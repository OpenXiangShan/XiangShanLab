### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and [XiangShan Documents](https://docs.xiangshan.cc/zh-cn/latest). 我已经阅读过 [RISC-V 指令集手册](https://github.com/riscv/riscv-isa-manual) 和 [香山文档](https://docs.xiangshan.cc/zh-cn/latest)。
- [x] I have searched previous issues and PRs and did not find anything relevant. 我已经搜索过之前的 issue 和 PR，并没有找到相关的。

### Describe the question

Hello XiangShan maintainers,

Could you please help confirm whether the code path below represents an unintended behavior? This report comes from a static source review, and I may have missed a project-specific invariant, configuration constraint, or design assumption.

 
## Affected code locations

- `XIANGSHAN_ICACHE_PREFETCH_RAW_VALID_GHOST_ITLB_META_REQ`: `src/main/scala/xiangshan/frontend/icache/ICachePrefetchPipe.scala`

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

- Candidate id: `XIANGSHAN_ICACHE_PREFETCH_RAW_VALID_GHOST_ITLB_META_REQ`
- Source revision audited: `8a42e727cc58db2b00d4bb2de7b397355f89257c`
- Affected area: RISC-V frontend ICache prefetch pipeline, ITLB exec-translation request fanout, metadata-array request fanout.

### Source evidence

In `ICachePrefetchPipe`, stage 0 receives an FTQ prefetch request and fans it out to ITLB and metadata array:

- `src/main/scala/xiangshan/frontend/icache/ICachePrefetchPipe.scala:45-51`: the pipe has one `fromFtq` parent input plus `itlb` and `metaRead` child interfaces.
- `src/main/scala/xiangshan/frontend/icache/ICachePrefetchPipe.scala:81-101`: `s0_valid` is raw `io.fromFtq.valid`, while the parent ready requires `s1_ready && toItlb.ready && toMeta.ready && !s0_flush`.
- `src/main/scala/xiangshan/frontend/icache/ICachePrefetchPipe.scala:101-103`: the owner transfer is `s0_fire := io.fromFtq.fire`.
- `src/main/scala/xiangshan/frontend/icache/ICachePrefetchPipe.scala:163-170`: the ITLB child request is driven with `toItlb.valid := s1_needItlb || s0_valid` and `TlbCmd.exec`.
- `src/main/scala/xiangshan/frontend/icache/ICachePrefetchPipe.scala:212-215`: the meta-array child request is driven with `toMeta.valid := s1_needMeta || s0_valid`.

The child interfaces are real Decoupled transactions:

- `src/main/scala/xiangshan/cache/mmu/MMUBundle.scala:667-670`: `TlbRequestIO.req` is `DecoupledIO(new TlbReq)`.
- `src/main/scala/xiangshan/cache/mmu/TLB.scala:77-83`: the TLB latches `req_out(i)` on `req(i).fire`, and `req_out_v` is driven from `req_in(i).fire`.
- `src/main/scala/xiangshan/cache/mmu/TLB.scala:542-545`: the non-blocking TLB drives `req.ready` from `resp.ready`, with an assertion that non-blocking `resp.ready` should be true.
- `src/main/scala/xiangshan/frontend/icache/Bundles.scala:139-145`: `MetaReadBundle.req` is also a Decoupled request.
- `src/main/scala/xiangshan/frontend/icache/ICacheMetaArray.scala:54-64`: the meta-array read side drives per-bank valid from `io.read.req.valid` and accepts through `io.read.req.ready`.
- `src/main/scala/xiangshan/frontend/icache/ICacheImp.scala:140-145`: in the normal non-injection path, the prefetcher is connected directly to the meta-array read port.

## Static Concern

The parent FTQ request is accepted only on `io.fromFtq.fire`. The ITLB and meta-array children can see raw `s0_valid` before that parent accepted event. If one child is ready while the other child or stage is not ready, a child `fire` can occur without the owner `fromFtq.fire`.

The current audited source does not show:

- per-child accepted bits that suppress duplicate ITLB/meta requests;
- a transaction owner token that associates every child response with one accepted FTQ owner;
- a kill path that cancels a child request if the parent is not accepted or is flushed;
- an assertion proving child `fire` is equivalent to the parent owner `fire`.

This is a fanout-ownership risk rather than a claim that a specific dynamic trace has already been reproduced.

### Why this may matter

The ITLB child request is an instruction-fetch translation request with `TlbCmd.exec`. Even when the frontend request is speculative, the microarchitecture still needs a clear owner relation between the FTQ request and any ITLB/meta side effect. Otherwise the implementation can accept duplicate or orphan child transactions when the parent is stalled, retried, or flushed.

The most reviewable contract is:

```text
for each FTQ-owned prefetch transaction,
ITLB and metadata requests are either accepted atomically with the owner,
or each accepted child is tracked until the full fanout transaction completes or is killed.
```

## Suggested Owner Decisions

Please clarify which contract is intended:

1. Should the initial ITLB/meta child valid signals be gated by the parent owner fire condition?
2. Should the pipe support partial child acceptance with explicit accepted-state tracking?
3. Can ITLB/meta child requests be harmlessly retried from raw `s0_valid`, and if so where is that invariant documented?
4. Should there be an assertion that child `fire` never happens unless the FTQ owner request also fires for the same transaction?
