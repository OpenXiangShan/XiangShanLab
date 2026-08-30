### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and this is not a RISC-V ISA question. 我已经阅读过 RISC-V 指令集手册，这不是一个指令集本身的问题。
- [x] I have read the [XiangShan Documents](https://xiangshan-doc.readthedocs.io/zh_CN/latest). 我已经阅读过香山文档。
- [x] I have searched the previous issues and did not find anything relevant. 我已经搜索过之前的 issue，并没有找到相关的。
- [x] I have reviewed the commit messages from the relevant commit history. 我已经浏览过相关的提交历史和提交信息。
- [x] I have reproduced the incorrect behaviors using the latest commit on the master branch. 我已经使用 master 分支最新的 commit 复现了不正确的行为。

### Branch

kunminghu-v3 & kunminghu-v2

### Describe the bug

Location: src/main/scala/yunsuan/package.scala, Line 316.

```scala
  313    val srcType1 =  Mux1H(Seq(
  314      isvrgatherei16                     -> "b0001".U,
  315      isvcompress                        -> "b1111".U,
  316      !(isvrgatherei16|isvrgatherei16)   -> Cat(isFp ,isFp,  sew(1,0)),
  317    ))
```
The second operand of OR is isvrgatherei16 (a copy of the first operand), rather than isvcompress.

### Expected behavior

The Line 316 shoule be ```!(isvrgatherei16|isvcompress)   -> Cat(isFp ,isFp,  sew(1,0)),```

### Environment

detected by a static analysis tool.

### To Reproduce

detected by a static analysis tool.

### Additional context

_No response_
