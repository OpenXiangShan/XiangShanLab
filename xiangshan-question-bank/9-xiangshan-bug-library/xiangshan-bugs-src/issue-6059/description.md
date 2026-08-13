### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and this is not a RISC-V ISA question. 我已经阅读过 RISC-V 指令集手册，这不是一个指令集本身的问题。
- [x] I have read the [XiangShan Documents](https://xiangshan-doc.readthedocs.io/zh_CN/latest). 我已经阅读过香山文档。
- [x] I have searched the previous issues and did not find anything relevant. 我已经搜索过之前的 issue，并没有找到相关的。
- [x] I have reviewed the commit messages from the relevant commit history. 我已经浏览过相关的提交历史和提交信息。
- [x] I have reproduced the incorrect behaviors using the latest commit on the master branch. 我已经使用 master 分支最新的 commit 复现了不正确的行为。

### Branch

kunminghu-v3

### Describe the bug

Location: src/main/scala/xiangshan/cache/mmu/Repeater.scala, Line: 290

```scala
val inflight_counter = RegInit(0.U(log2Up(Size).W))
```

`inflight_counter` is declared as `RegInit(0.U(log2Up(Size).W))`, but the code compares/uses it up to Size.U.

There is correct usage on line 443 of the current file.
```scala
val inflight_counter = RegInit(0.U(log2Up(Size + 1).W))
``` 

### Expected behavior

```scala
val inflight_counter = RegInit(0.U(log2Up(Size + 1).W))
```

### Environment

detected by a static analysis tool

### To Reproduce

detected by a static analysis tool

### Additional context

_No response_
