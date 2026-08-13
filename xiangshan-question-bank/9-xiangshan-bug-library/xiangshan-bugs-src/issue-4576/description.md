### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and this is not a RISC-V ISA question. 我已经阅读过 RISC-V 指令集手册，这不是一个指令集本身的问题。
- [x] I have read the [XiangShan Documents](https://xiangshan-doc.readthedocs.io/zh_CN/latest). 我已经阅读过香山文档。
- [x] I have searched the previous issues and did not find anything relevant. 我已经搜索过之前的 issue，并没有找到相关的。
- [x] I have reviewed the commit messages from the relevant commit history. 我已经浏览过相关的提交历史和提交信息。
- [x] I have reproduced the incorrect behaviors using the latest commit on the master branch. 我已经使用 master 分支最新的 commit 复现了不正确的行为。

### Describe the bug

After returning from the `ebreak` instruction, XiangShan fails to correctly recognize the next instruction, misidentifying it as `c.unimp` instead of the expected instruction. The misidentification of the instruction as `c.unimp` triggers an illegal instruction exception. Following this, XiangShan continues to execute instructions, and when an exception is manually triggered (such as `lw a0, 0(s3)`), it leads to a mismatch in the CSR registers.
<img width="430" alt="Image" src="https://github.com/user-attachments/assets/c8c73f04-8c8f-4408-a479-d47820239846" />
<img width="347" alt="Image" src="https://github.com/user-attachments/assets/85469478-773c-43f9-9f72-66e3cb04ee1c" />

### Expected behavior

After returning from `ebreak`, XiangShan should correctly recognize the next instruction.

If the behavior of recognizing `unimp` is correct, the remaining instruction operations should also be consistent with NEMU.

### To Reproduce

Here is the source code, binary file:
[test.zip](https://github.com/user-attachments/files/19783759/test.zip)

### Environment

- XiangShan branch: master
- XiangShan commit id: c01e75b55fbe706c21954429f2ec968cbc61a2cc
- NEMU commit id: 738ae334edc62237c3d4b49c722882ee1b7324fc


### Additional context

_No response_
