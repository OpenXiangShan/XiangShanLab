### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and this is not a RISC-V ISA question. 我已经阅读过 RISC-V 指令集手册，这不是一个指令集本身的问题。
- [x] I have read the [XiangShan Documents](https://xiangshan-doc.readthedocs.io/zh_CN/latest). 我已经阅读过香山文档。
- [x] I have searched the previous issues and did not find anything relevant. 我已经搜索过之前的 issue，并没有找到相关的。
- [x] I have reviewed the commit messages from the relevant commit history. 我已经浏览过相关的提交历史和提交信息。
- [x] I have reproduced the incorrect behaviors using the latest commit on the master branch. 我已经使用 master 分支最新的 commit 复现了不正确的行为。

### Describe the bug

When executing a `jalr` instruction to an invalid PC (0x12345678), XiangShan continues to fetch and report illegal instructions, instead of raising an instruction fetch exception. In contrast, NEMU correctly identifies and reports the exception.
<img width="414" alt="Image" src="https://github.com/user-attachments/assets/9dd81e42-f102-4d24-8da1-1c9b1666e52d" />
<img width="343" alt="Image" src="https://github.com/user-attachments/assets/9e770d6b-ab3a-4901-b2ca-8827b223c9c2" />

### Expected behavior

- XiangShan should raise an **Instruction Access Fault** (`mcause = 1`) upon fetching from an invalid PC.
- The `mtval` register should contain the faulting PC value (`0x12345678`).
- Execution should not continue from an invalid PC.

### To Reproduce

Here is the testcase.
[test.zip](https://github.com/user-attachments/files/20148359/test.zip)

### Environment

- XiangShan branch: master
- XiangShan commit id: 0c97e1df8323fdab545e7d336867b444745299bd
- NEMU commit id: 16e9c675a07886f46cbbd48cf69e2e13eb919f9f


### Additional context

_No response_
