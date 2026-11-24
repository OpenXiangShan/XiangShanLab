### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and [XiangShan Documents](https://docs.xiangshan.cc/zh-cn/latest). 我已经阅读过 [RISC-V 指令集手册](https://github.com/riscv/riscv-isa-manual) 和 [香山文档](https://docs.xiangshan.cc/zh-cn/latest)。
- [x] I have searched previous issues and PRs and did not find anything relevant. 我已经搜索过之前的 issue 和 PR，并没有找到相关的。

### Describe the question

Hello XiangShan maintainers,

Could you please help confirm whether the code path below represents an unintended behavior? This report comes from a static source review, and I may have missed a project-specific invariant, configuration constraint, or design assumption.

 
## Affected code locations

- `XIANGSHAN_MEMENC_TWEAKTABLE_AXI_ID_CONTEXT_OVERWRITE`: `MemEncryptUtil.scala:557-656, MemEncrypt.scala:349-424, MemEncrypt.scala:1138-1169, SoC.scala:330-342`

## Static analysis for maintainer review

### Summary of the static observation

The MemEncrypt read path appears to use the AXI id as a single-slot namespace
for tweak/decryption context.

`TweakEncrptyTable` writes read-side context into `TweakTable` when an AR
request is accepted. Later, when R-channel data returns, `AXI4ReadMachine`
queries the table by the returned R id and receives the key id and tweak used
for decryption. However, `TweakTable` stores only one context per id and sets
`io.write.ready := true.B`. I did not find a per-id FIFO, busy bit,
duplicate-id rejection, outstanding counter, epoch tag, or other local guard
that prevents a later same-id read request from overwriting the earlier
context before the earlier R response consumes it.

The downstream `AXI4Deinterleaver` can preserve response ordering, but that is
not the same as protecting the upstream context table from accepting multiple
live contexts for the same reusable id.

## Static Source Evidence

Audited source revision:

`8a42e727cc58db2b00d4bb2de7b397355f89257c`

Relevant source anchors and SHA-256:

- `src/main/scala/device/MemEncrypt.scala`
  `b59202582e1844899f5b344379863048ecff562b08bed6afef8dc6ff67e637f3`
- `src/main/scala/device/MemEncryptUtil.scala`
  `0eb48ea6d2d7e9875e589e00bfa1da155a9e2c63d3c3bcb633f9acf10016336a`
- `src/main/scala/system/SoC.scala`
  `1aa442f24ec6c73f74b317c967ba3c9e91106a297c4d4764405ce051bf2ffba7`

Source facts:

1. `SoC.scala` inserts `AXI4MemEncrypt` into the memory AXI path when
   `HasMEMencryption` is enabled.
2. `SoC.scala` connects `AXI4Deinterleaver(L3BlockSize)` after the
   memory-encryption block.
3. `TweakEncrptyTable` passes each accepted AR id into `TweakTable` through
   `tweak_table.io.write.bits.id`.
4. `TweakTable` defines `tweak_table` and `tweak_mode_table` as register
   vectors indexed by `id`.
5. `TweakTable` updates `tweak_table(io.write.bits.id)` whenever
   `io.write.valid` is true.
6. `TweakTable` assigns `io.write.ready := true.B`.
7. `TweakTable` reads context from `tweak_table(io.req.bits.read_id)`.
8. `AXI4ReadMachine` later sends `io.kt_req.bits.id := s1_r_req.id` to look
   up read-response context by R-channel id.
9. I did not find a local same-id outstanding counter, context FIFO, busy bit,
   duplicate-id assertion, or explicit proof that only one same-id read can be
   live inside this block.

## Why this is owner-review-only

The static review suggests a contract risk, but the appropriate repair may depend on the
intended AXI policy for the confidential-memory block:

- Serialize encrypted AR requests per id before writing `TweakTable`.
- Add a per-id FIFO or small context queue and pop it as R beats consume the
  corresponding context.
- Track per-id outstanding count plus an epoch/tag so table entries cannot be
  confused across same-id transactions.
- Move the context capture point to a place where same-id ordering is already
  proven by construction.
- Add an assertion/proof if the intended system contract already forbids
  multiple live same-id contexts before `TweakTable`.

Because these choices affect throughput, buffering, and the precise owner
contract for AXI ids, this draft does not include a patch.

## Review Questions

1. Can the MemEncrypt block accept two encrypted AR requests with the same AXI
   id before the first response has consumed its `TweakTable` context?
2. Is same-id single-outstanding intended to be guaranteed before
   `TweakTable`, and if so where should that contract be asserted?
3. Should `TweakTable` be a per-id FIFO/context queue rather than a single
   register slot?
4. Does `AXI4Deinterleaver` downstream provide only response ordering, or is it
   also intended to limit same-id context admission upstream?
5. If this is intentionally single-outstanding-per-id, should the design expose
   a local assertion to prevent future integration from violating that
   assumption?
