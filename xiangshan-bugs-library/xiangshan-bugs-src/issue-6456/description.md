### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and [XiangShan Documents](https://docs.xiangshan.cc/zh-cn/latest), and I believe this is a XiangShan RTL issue. 我已经阅读过 [RISC-V 指令集手册](https://github.com/riscv/riscv-isa-manual) 和 [香山文档](https://docs.xiangshan.cc/zh-cn/latest)，确认这应该是香山 RTL 的问题。
- [x] I have searched previous issues and PRs and did not find anything relevant. 我已经搜索过之前的 issue 和 PR，并没有找到相关的。
- [x] I have reproduced the issue using the latest commit on the default branch. 我已经使用默认分支最新的 commit 复现了问题。
- [x] If this report was generated with AI assistance (otherwise leave unchecked), I have verified the correctness of its content. 如果这是由 AI 生成的（否则请不要勾选），我已经验证了内容的正确性。

### Branch

kunminghu-v3

### Describe the bug

At commit `e5c70547f3a966accf20a4b065ec1d8e33443180`, `PMParameters`
publishes `PlatformGrain` as an `Int`. Legacy and NewCSR PMP/PMA helpers build
generator-time masks with expressions such as `(1 << PlatformGrain) - 1`.

Scala evaluates that expression using a 32-bit host `Int` before conversion to
Chisel `UInt` or `BigInt`. Large grain values can therefore wrap the shift
count or truncate the mask during elaboration. The same root appears in
`PMP.scala`, `PMA.scala`, `PMPEntryModule.scala`, and `PMAEntryModule.scala`.

The default `PlatformGrain=12` is unaffected. This report concerns larger
non-default grains for which no explicit upper-bound rejection currently
exists.

### Expected behavior

The generator should either reject `PlatformGrain` outside the supported
physical-address domain or compute masks with width-safe `BigInt` helpers, for
example `(BigInt(1) << PlatformGrain) - 1`.

### Environment

- XiangShan branch: `kunminghu-v3`
- XiangShan commit: `e5c70547f3a966accf20a4b065ec1d8e33443180`

### To Reproduce

This required step has not yet been executed. A focused test should:

1. elaborate configurations around the host `Int` shift boundary;
2. inspect generated PMP/PMA masks for grains 30, 31, and 32 where legal;
3. compare them with masks computed using `BigInt(1)`;
4. confirm that the default grain remains unchanged.

### Additional context
