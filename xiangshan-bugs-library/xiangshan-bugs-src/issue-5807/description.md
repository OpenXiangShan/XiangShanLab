### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and this is not a RISC-V ISA question. 我已经阅读过 RISC-V 指令集手册，这不是一个指令集本身的问题。
- [x] I have read the [XiangShan Documents](https://xiangshan-doc.readthedocs.io/zh_CN/latest). 我已经阅读过香山文档。
- [x] I have searched the previous issues and did not find anything relevant. 我已经搜索过之前的 issue，并没有找到相关的。
- [x] I have reviewed the commit messages from the relevant commit history. 我已经浏览过相关的提交历史和提交信息。
- [x] I have reproduced the incorrect behaviors using the latest commit on the master branch. 我已经使用 master 分支最新的 commit 复现了不正确的行为。

### Branch

kunminghu-v3

### Describe the bug

### Description
An error was encountered during difftest execution. When an exception is raised simultaneously in the XiangShan RTL design and the NEMU reference model, the CSR register values between the two sides are inconsistent.

Specifically, the exception code (index) in the `mcause` CSR from the XiangShan RTL is incorrect, which does not align with the expected value output by NEMU.

### Relevant Logs
- difftest runtime error log: [difftest.log](https://github.com/user-attachments/files/26693657/difftest.log)
- NEMU debug mode runtime log:[nemu_lw.log](https://github.com/user-attachments/files/26693647/nemu_lw.log)




### Expected behavior

I hope the difftest passes.

### Environment

[bug-report.tar.gz](https://github.com/user-attachments/files/26693562/bug-report.tar.gz)

- Hardware
  - CPU:
  - Memory (GB):
  - Storage (GB):
- Software
  - Operating system:
  - gcc version: <!-- run `gcc --version 2>&1 | head -n 1` to get the version -->
  - clang version: <!-- run `clang --version 2>&1 | head -n 1` to get the version, only needed when you use clang -->
  - java version: <!-- run `java -version 2>&1 | head -n 1` to get the version -->
  - mill version: <!-- run `mill -i --version 2>&1 | head -n 1` to get the version -->
- Repo
  - XiangShan commit id: ``
  - NEMU commit id (if difftest failed with NEMU): ``
  - SPIKE commit id (if difftest failed with SPIKE): ``
- Build & Run
  - Build command: ``
  - Run command (if applicable): ``
  - Also upload workload (binary and source code) in "To Reproduce" section if applicable.


### To Reproduce

[lw_45.c](https://github.com/user-attachments/files/26693511/lw_45.c)

### Additional context

_No response_
