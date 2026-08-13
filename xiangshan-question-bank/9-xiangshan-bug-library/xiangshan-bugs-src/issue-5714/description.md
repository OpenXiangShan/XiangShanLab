### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and this is not a RISC-V ISA question. 我已经阅读过 RISC-V 指令集手册，这不是一个指令集本身的问题。
- [x] I have read the [XiangShan Documents](https://xiangshan-doc.readthedocs.io/zh_CN/latest). 我已经阅读过香山文档。
- [x] I have searched the previous issues and did not find anything relevant. 我已经搜索过之前的 issue，并没有找到相关的。
- [x] I have reviewed the commit messages from the relevant commit history. 我已经浏览过相关的提交历史和提交信息。
- [x] I have reproduced the incorrect behaviors using the latest commit on the master branch. 我已经使用 master 分支最新的 commit 复现了不正确的行为。

### Branch

kunminghu-v3

### Describe the bug

According to the RISC-V External Debug Support Version 0.13.2, Sdtrig Extension, Section 5.1 — Priority of Simultaneous Trigger Actions

> When multiple triggers in the same priority fire at once, `hit` (if implemented) is set for all of them. [...] If one of these triggers has the "enter Debug Mode" action (1) and another trigger has the "raise a breakpoint exception" action (0), the preferred behavior is to have both actions take place. It is implementation-dependent which of the two happens first. This ensures both that the presence of an external debugger doesn't affect execution and that a trigger set by user code doesn't affect the external debugger. **If this is not implemented, then the hart must enter Debug Mode and ignore the breakpoint exception.

<img width="976" height="175" alt="Image" src="https://github.com/user-attachments/assets/0a302fb6-24b6-4ded-abc9-318ea54b79a7" />

triggerActionGen` (in `DebugLevel.scala:372-387`) uses `PriorityEncoderOH` to select only the lowest-indexed firing trigger and uses that single trigger's action. It does not scan all firing triggers to check whether any has `action=1` (DebugMode).

It seems that when a lower-indexed trigger has `action=0` and a higher-indexed trigger has `action=1`, and both fire on the same instruction/address, the result violates the spec.

### Expected behavior

1: Both actions take place.
or
2: Enter Debug Mode, not breakpoint exception.

### Environment

XiangShan config:DefaultConfig
XiangShan branch:kunminghu-v3

### To Reproduce

N/A —sorry for no minimal reproducer yet. The issue was found via code audit.

### Additional context

_No response_
