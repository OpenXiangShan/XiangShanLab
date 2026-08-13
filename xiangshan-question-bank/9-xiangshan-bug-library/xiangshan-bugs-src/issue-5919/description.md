### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and this is not a RISC-V ISA question. 我已经阅读过 RISC-V 指令集手册，这不是一个指令集本身的问题。
- [x] I have read the [XiangShan Documents](https://xiangshan-doc.readthedocs.io/zh_CN/latest). 我已经阅读过香山文档。
- [x] I have searched the previous issues and did not find anything relevant. 我已经搜索过之前的 issue，并没有找到相关的。
- [x] I have reviewed the commit messages from the relevant commit history. 我已经浏览过相关的提交历史和提交信息。
- [x] I have reproduced the incorrect behaviors using the latest commit on the master branch. 我已经使用 master 分支最新的 commit 复现了不正确的行为。

### Branch

kunminghu-v3

### Describe the bug

I found an issue when running `vmv<nr>r.v` instructions. It looks like the current XiangShan implementation may handle some `vmv<nr>r.v` instructions incorrectly. This may lead to incorrect vector register updates and eventually cause architectural divergence.

According to the RISC-V Vector spec:

> The source and destination vector register numbers must be aligned appropriately for the vector register group size, and encodings with other vector register numbers are reserved.


### Expected behavior

Reserved `vmv<nr>r.v` encodings should not be executed as normal valid instructions.


### Environment

Branch: kunminghu-v3
Commit: 0f72de270


### To Reproduce

[testcase.zip](https://github.com/user-attachments/files/27528037/testcase.zip)

### Additional context

_No response_
