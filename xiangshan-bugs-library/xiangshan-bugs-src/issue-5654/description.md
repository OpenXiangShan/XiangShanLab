### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and this is not a RISC-V ISA question. 我已经阅读过 RISC-V 指令集手册，这不是一个指令集本身的问题。
- [x] I have read the [XiangShan Documents](https://xiangshan-doc.readthedocs.io/zh_CN/latest). 我已经阅读过香山文档。
- [x] I have searched the previous issues and did not find anything relevant. 我已经搜索过之前的 issue，并没有找到相关的。
- [x] I have reviewed the commit messages from the relevant commit history. 我已经浏览过相关的提交历史和提交信息。
- [x] I have reproduced the incorrect behaviors using the latest commit on the master branch. 我已经使用 master 分支最新的 commit 复现了不正确的行为。

### Branch

kunminghu-v3

### Describe the bug

In the FTQ, the `ifuWbPtr` pointer is initialized at https://github.com/OpenXiangShan/XiangShan/blob/ab5c6ce36326ff5a915808865f1fc52b5730cc94/src/main/scala/xiangshan/frontend/ftq/Ftq.scala#L88 but never updated throughout the entire module. This pointer represents the write-back pointer for the IFU. 

The backend exception handling logic https://github.com/OpenXiangShan/XiangShan/blob/ab5c6ce36326ff5a915808865f1fc52b5730cc94/src/main/scala/xiangshan/frontend/ftq/Ftq.scala#L128-L138 depends on `ifuWbPtr` to correctly identify where exceptions occurred.

### Expected behavior

Add an update mechanism for `ifuWbPtr` when the IFU completes write-back operations.

### Environment

- Repo
  - XiangShan commit id: `ab5c6ce36326ff5a915808865f1fc52b5730cc94`

### To Reproduce

No reliable reproduction steps available yet. Issue was found through code analysis. Perhaps can be reproduced using testcases with backend exceptions.

### Additional context

_No response_
