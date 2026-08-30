### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and this is not a RISC-V ISA question. 我已经阅读过 RISC-V 指令集手册，这不是一个指令集本身的问题。
- [x] I have read the [XiangShan Documents](https://xiangshan-doc.readthedocs.io/zh_CN/latest). 我已经阅读过香山文档。
- [x] I have searched the previous issues and did not find anything relevant. 我已经搜索过之前的 issue，并没有找到相关的。
- [x] I have reviewed the commit messages from the relevant commit history. 我已经浏览过相关的提交历史和提交信息。
- [x] I have reproduced the incorrect behaviors using the latest commit on the master branch. 我已经使用 master 分支最新的 commit 复现了不正确的行为。

### Branch

master (kunminghu-v2)

### Describe the bug

Location: https://github.com/OpenXiangShan/YunSuan/blob/955921186e34bb8915806582a238181a6dc3435c/src/main/scala/yunsuan/vector/VectorFloatAdder.scala, Line 2126

In ```VectorFloatAdder.scala```, the overflow rounding path computes an overflow-specific guard bit (```B_guard_overflow_reg```) but never uses it, causing overflow rounding to incorrectly reuse normal-path guard/round/sticky bits.

Steps:
1. Line 2126, ```B_guard_overflow_reg``` is computed but never referenced
2. Lines 2131-2133 — overflow rounding signals are incorrectly assigned from normal-path values:
```scala
val B_round_overflow_reg   = B_guard_normal_reg
val B_sticky_overflow_reg  = B_round_normal_reg | B_sticky_normal_reg
val B_rsticky_overflow_reg = B_round_overflow_reg | B_sticky_overflow_reg
```
3. Line 2156, ```grs_overflow``` uses ```B_guard_normal_reg``` and ```B_rsticky_normal_reg```, instead of overflow-path values

For the far-path overflow case, the guard bit position differs from the normal case due to the extra leading carry-out and left shift. The code correctly computes ```B_guard_overflow_reg``` from the overflow guard position, but then ignores it and continues using normal-path bits

### Expected behavior

Use the overflow-path GRS values in ```grs_overflow``` and related overflow-rounding logic.

### Environment

detected by a static analysis tool.

### To Reproduce

detected by a static analysis tool.

### Additional context

_No response_
