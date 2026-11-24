### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and [XiangShan Documents](https://docs.xiangshan.cc/zh-cn/latest). 我已经阅读过 [RISC-V 指令集手册](https://github.com/riscv/riscv-isa-manual) 和 [香山文档](https://docs.xiangshan.cc/zh-cn/latest)。
- [x] I have searched previous issues and PRs and did not find anything relevant. 我已经搜索过之前的 issue 和 PR，并没有找到相关的。

### Describe the question

Hello XiangShan maintainers,

Could you please help confirm whether the code path below represents an unintended behavior? This report comes from a static source review, and I may have missed a project-specific invariant, configuration constraint, or design assumption.


## Affected code locations

- `XIANGSHAN_MEMENC_KEYEXTENDER_LIVE_CSR_MODE0_CLOSE`: `MemEncrypt.scala:691-758, MemEncrypt.scala:800-809, MemEncrypt.scala:878-908, MemEncrypt.scala:924-945, SoC.scala:330-342, Configs.scala:498-517`

## Static analysis for maintainer review

### Summary of the static observation

The MemEncrypt key-management path appears to accept a `mode=0` key-init
request as a close/disable command, but the accepted request does not reach the
`KeyTable` close branch that would clear `encdec_mode` for the selected key id.

The issue is scoped to checked-in memory-encryption-enabled configurations such
as `CVMCompile` and `CVMTestCompile`. It is a static source-level control-state
contract candidate, not a dynamic reproduction.

## Static Source Evidence

Audited source revision:

`8a42e727cc58db2b00d4bb2de7b397355f89257c`

Relevant source anchors and SHA-256:

- `src/main/scala/device/MemEncrypt.scala`
  `b59202582e1844899f5b344379863048ecff562b08bed6afef8dc6ff67e637f3`
- `src/main/scala/top/Configs.scala`
  `6f204d1fbb30f216903ff58cbbea9f95e76bbc924e377dbc579ce37886a54219`
- `src/main/scala/system/SoC.scala`
  `1aa442f24ec6c73f74b317c967ba3c9e91106a297c4d4764405ce051bf2ffba7`

Source facts:

1. `CVMCompile` and `CVMTestCompile` set `HasMEMencryption = true`.
2. `SoC.scala` instantiates `AXI4MemEncrypt` when `HasMEMencryption` is enabled
   and inserts it into the memory AXI path.
3. `MemEncryptCSR` accepts `key_init_req` when `req_leagl` is true; that
   predicate rejects `mode === 3` but does not reject `mode === 0`.
4. The outgoing request sets `enc_mode := mode =/= 0.U`, so a mode0 request is
   issued with `enc_mode=false`.
5. `KeyExtender` comments define `enc_mode` as `1:this keyid open enc` and
   `0:this keyid close enc`.
6. `KeyExtender` only enters `keyExpansion` when `io.keyextend_req.valid` and
   `io.keyextend_req.bits.enc_mode` are true.
7. `KeyTable` has a close branch when
   `io.write_req.keyid_valid && !io.write_req.enc_mode`.
8. For non-tweak requests, `KeyExtender` drives
   `keyTable.io.write_req.keyid_valid := current`; while idle, `current` is the
   idle enum value and does not assert the valid write required by the close
   branch.

Static conclusion: software can see the request accepted at the CSR/Decoupled
boundary, while the final table state need not change.

## Why this is owner-review-only

The source strongly suggests a dropped close request, but the repair is a design
choice:

- If `mode=0` close is intended and supported, a close request should generate a
  one-cycle `KeyTable` write with `keyid_valid=true` and `enc_mode=false`, or an
  equivalent explicit close path.
- If `mode=0` close is not supported through this CSR request, the CSR path
  should reject it or expose a failure/unsupported status instead of accepting
  it.
- If firmware is expected to use a different close sequence, that sequence
  should be documented and the current accepted-but-no-table-update path should
  be made unambiguous.

Because these choices affect software-visible control semantics, this draft does
not include a patch.

## Review Questions

1. Is `mode=0` intended to close/disable encryption for the selected key id?
2. Should `key_init_req` with `mode=0` be accepted by the CSR path?
3. If accepted, where should the corresponding `KeyTable` close write be
   generated?
4. If unsupported, should `last_req_accepted` report failure and should
   `keyextend_req.valid` remain low?
5. Should close be serialized with in-flight key expansion and traffic using
   the same key id?
