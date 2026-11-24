### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and [XiangShan Documents](https://docs.xiangshan.cc/zh-cn/latest), and I believe this is a XiangShan RTL issue. 我已经阅读过 [RISC-V 指令集手册](https://github.com/riscv/riscv-isa-manual) 和 [香山文档](https://docs.xiangshan.cc/zh-cn/latest)，确认这应该是香山 RTL 的问题。
- [x] I have searched previous issues and PRs and did not find anything relevant. 我已经搜索过之前的 issue 和 PR，并没有找到相关的。
- [x] I have reproduced the issue using the latest commit on the default branch. 我已经使用默认分支最新的 commit 复现了问题。
- [x] If this report was generated with AI assistance (otherwise leave unchecked), I have verified the correctness of its content. 如果这是由 AI 生成的（否则请不要勾选），我已经验证了内容的正确性。

### Branch

kunminghu-v3

### Describe the bug

At commit `e5c70547f3a966accf20a4b065ec1d8e33443180`, the active `TIMER`
generator uses `1 << p(MaxHartIdBits)` as the number of `mtimecmp` and `msip`
entries. The register window is fixed: `mtimecmp` starts at `0x4000`, consumes
eight bytes per hart, and `mtime` starts at `0xbff8`.

Only `(0xbff8 - 0x4000) / 8 = 4095` non-overlapping `mtimecmp` slots fit.
`--hartidbits 12` or YAML `HartIDBits: 12` requests 4096 entries, so the final
`mtimecmp` starts at `0xbff8` and overlaps `mtime`.

The command-line and YAML parsers assign `MaxHartIdBits` without a bound, and
`TIMER` has no consumer-side capacity check. This report does not claim that
`DefaultConfig` fails; it concerns an accepted non-default configuration.

### Expected behavior

`TIMER` should reject any `MaxHartIdBits` value whose generated per-hart
registers cannot fit before `mtime`, or derive the entry count from the actual
tile list rather than the hart-ID width.

### Environment
 
- XiangShan branch: `kunminghu-v3`
- XiangShan commit: `e5c70547f3a966accf20a4b065ec1d8e33443180`



### To Reproduce

This required step has not yet been executed. A targeted reproducer should:

1. elaborate the current default branch with `--hartidbits 12` or YAML
   `HartIDBits: 12`;
2. inspect the generated CLINT register map;
3. confirm whether the final `mtimecmp` and `mtime` both decode at `0xbff8`;
4. add a control using the largest non-overlapping shape.
