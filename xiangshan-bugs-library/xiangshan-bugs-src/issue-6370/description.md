### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and [XiangShan Documents](https://docs.xiangshan.cc/zh-cn/latest). 我已经阅读过 [RISC-V 指令集手册](https://github.com/riscv/riscv-isa-manual) 和 [香山文档](https://docs.xiangshan.cc/zh-cn/latest)。
- [x] I have searched previous issues and PRs and did not find anything relevant. 我已经搜索过之前的 issue 和 PR，并没有找到相关的。

### Describe the question

Hello XiangShan maintainers,

Could you please help confirm whether the code path below represents an unintended behavior? This report comes from a static source review, and I may have missed a project-specific invariant, configuration constraint, or design assumption.

 
## Affected code locations

- `XIANGSHAN_MEMENC_KEYTABLE_EARLY_MODE_ENABLE_PARTIAL_ROUND_KEYS`: `MemEncrypt.scala:789-805, MemEncrypt.scala:839-945, MemEncryptUtil.scala:664-733, MemEncrypt.scala:1067-1101, MemEncrypt.scala:1144-1173, Configs.scala:498-517`

## Static analysis for maintainer review

### Summary of the static observation

The MemEncrypt key-management path appears to publish a key id as enabled before
the full 32-round key schedule has been installed in `KeyTable`.

`KeyTable` sets `entry.encdec_mode := true` on each accepted encrypted-key
round write, while that same write updates only one `round_key_data(round_id)`.
The write/read memory datapaths can query `enc.mode` / `dec.mode` and consume
round keys by key id. I did not find a local guard that blocks memory traffic
using the same key id until all 32 round-key entries have been written.

The result is a publication-order risk: a key id can become visible as enabled
while some round-key payloads are still `DontCare` or stale.

## Static Source Evidence

Audited source revision:

`8a42e727cc58db2b00d4bb2de7b397355f89257c`

Relevant source anchors and SHA-256:

- `src/main/scala/device/MemEncrypt.scala`
  `b59202582e1844899f5b344379863048ecff562b08bed6afef8dc6ff67e637f3`
- `src/main/scala/device/MemEncryptUtil.scala`
  `0eb48ea6d2d7e9875e589e00bfa1da155a9e2c63d3c3bcb633f9acf10016336a`
- `src/main/scala/top/Configs.scala`
  `6f204d1fbb30f216903ff58cbbea9f95e76bbc924e377dbc579ce37886a54219`
- `src/main/scala/system/SoC.scala`
  `1aa442f24ec6c73f74b317c967ba3c9e91106a297c4d4764405ce051bf2ffba7`

Source facts:

1. `CVMCompile` and `CVMTestCompile` set `HasMEMencryption = true`.
2. `SoC.scala` instantiates `AXI4MemEncrypt` in the memory path when memory
   encryption is enabled.
3. `KeyTable` initializes `round_key_data` as `DontCare`.
4. On every encrypted-key write, `KeyTable` sets `entry.encdec_mode := true` and
   updates only `entry.round_key_data(round_id)`.
5. `KeyExtender` runs a multi-cycle `keyExpansion` FSM and uses
   `reg_count_round` as the round id.
6. For non-tweak requests, `KeyExtender` drives
   `keyTable.io.write_req.keyid_valid := current`, so writes happen during the
   expansion state before all rounds have completed.
7. The write path derives `enc_keyid` from high physical-address bits and uses
   `key_extender.io.enc.mode` to choose the encrypted route.
8. The encrypted write datapath consumes `key_extender.io.enc_round_keys`.
9. The read path similarly queries `key_extender.io.dec.mode` and consumes
   `key_extender.io.dec_round_keys`.

## Why this is owner-review-only

The source-level mechanism is clear, but the best fix depends on owner policy:

- Publish `encdec_mode=true` only on the final key-expansion round.
- Add a separate per-key `complete` bit and have traffic require
  `encdec_mode && complete`.
- Add a per-key busy/installation state that rejects or stalls memory traffic
  selecting that key id.
- Define a firmware/CSR contract that guarantees quiescence and make that
  guarantee visible/checkable in hardware state.

Because these choices affect software-visible key lifecycle semantics and memory
traffic admission, this draft does not include a patch.

## Review Questions

1. Is memory traffic allowed while a key id's round keys are being installed?
2. Should `encdec_mode` mean "installation has started" or "key is complete and
   usable"?
3. If firmware must quiesce traffic, where is that contract exposed and checked?
4. Should KeyTable use a separate complete/busy bit per key id?
5. Should the encryption/decryption datapaths treat incomplete key ids as
   disabled, stalled, or erroneous?
