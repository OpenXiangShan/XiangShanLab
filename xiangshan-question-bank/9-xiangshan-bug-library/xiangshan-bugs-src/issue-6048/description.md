### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and this is not a RISC-V ISA question. 我已经阅读过 RISC-V 指令集手册，这不是一个指令集本身的问题。
- [x] I have read the [XiangShan Documents](https://xiangshan-doc.readthedocs.io/zh_CN/latest). 我已经阅读过香山文档。
- [x] I have searched the previous issues and did not find anything relevant. 我已经搜索过之前的 issue，并没有找到相关的。
- [x] I have reviewed the commit messages from the relevant commit history. 我已经浏览过相关的提交历史和提交信息。
- [x] I have reproduced the incorrect behaviors using the latest commit on the master branch. 我已经使用 master 分支最新的 commit 复现了不正确的行为。

### Branch

kunminghu-v3

### Describe the bug

Location: `src/main/scala/xiangshan/frontend/bpu/Bpu.scala` Lines 328-331

```scala
  private val debug_s1UseUbtb      = s1_taken && !useAbtb
  private val debug_s1UseUbtbUtage = s1_taken && !useAbtb
  private val debug_s1UseAbtb      = s1_taken && useAbtb && !s1_utageHitMask.reduce(_ || _)
  private val debug_s1UseAbtbUtage = s1_taken && useAbtb && s1_utageHitMask.reduce(_ || _)
```

`debug_s1UseUbtbUtage` is assigned `s1_taken && !useAbtb`, identical to `debug_s1UseUbtb` on line 328, instead of also gating on `s1_utageHitMask.reduce(_ || _)` like the `Abtb` pair below does. Consequently the `Stage1.UbtbUtage` arm of the `s1_predictionSource` MuxCase is unreachable (Line 585), and the `s1_use perf` counter double-counts `ubtb/ubtb_microTage`.

### Expected behavior

distinguish `debug_s1UseUbtb` and `debug_s1UseUbtbUtage`

### Environment

detected by a static analysis tool.

### To Reproduce

detected by a static analysis tool.

### Additional context

_No response_
