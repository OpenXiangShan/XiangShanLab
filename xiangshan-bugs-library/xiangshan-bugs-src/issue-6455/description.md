### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and [XiangShan Documents](https://docs.xiangshan.cc/zh-cn/latest), and I believe this is a XiangShan RTL issue. 我已经阅读过 [RISC-V 指令集手册](https://github.com/riscv/riscv-isa-manual) 和 [香山文档](https://docs.xiangshan.cc/zh-cn/latest)，确认这应该是香山 RTL 的问题。
- [x] I have searched previous issues and PRs and did not find anything relevant. 我已经搜索过之前的 issue 和 PR，并没有找到相关的。
- [x] I have reproduced the issue using the latest commit on the default branch. 我已经使用默认分支最新的 commit 复现了问题。
- [x] If this report was generated with AI assistance (otherwise leave unchecked), I have verified the correctness of its content. 如果这是由 AI 生成的（否则请不要勾选），我已经验证了内容的正确性。

### Branch

kunminghu-v3

### Describe the bug

At commit `e5c70547f3a966accf20a4b065ec1d8e33443180`,
`TlbSectorEntry.apply()` computes `inner_level` to preserve the translation
range used by address-specific SFENCE matching. When MPT is enabled, the code
then stores `inner_level min item.mpt.get.mptLevel` into the same `level`
field.

A finer MPT permission granularity can therefore make a large translation look
smaller to the TLB invalidation matcher. An address-specific SFENCE targeting
another address within the original large translation may fail to match the
entry if the stored level has been reduced.

MPT is disabled by default. This report concerns an MPT-enabled configuration
and does not claim a `DefaultConfig` failure.

### Expected behavior

The translation level used by TLB identity and SFENCE matching should remain
separate from the MPT permission granularity. A finer MPT permission level must
not reduce the address range over which a translation is invalidated.

### Environment

**- XiangShan branch: `kunminghu-v3`
- XiangShan commit: `e5c70547f3a966accf20a4b065ec1d8e33443180`**

### To Reproduce

This required step has not yet been executed. A focused test should:

1. enable MPT and install a large-page translation;
2. return a finer MPT permission level for that translation;
3. issue an address-specific SFENCE for another address inside the large page;
4. verify whether the old TLB entry still matches a subsequent access;
5. compare against an entry whose MPT and translation levels are equal.

### Additional context
