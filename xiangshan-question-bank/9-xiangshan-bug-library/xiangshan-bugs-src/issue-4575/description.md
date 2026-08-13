### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and this is not a RISC-V ISA question. 我已经阅读过 RISC-V 指令集手册，这不是一个指令集本身的问题。
- [x] I have read the [XiangShan Documents](https://xiangshan-doc.readthedocs.io/zh_CN/latest). 我已经阅读过香山文档。
- [x] I have searched the previous issues and did not find anything relevant. 我已经搜索过之前的 issue，并没有找到相关的。
- [x] I have reviewed the commit messages from the relevant commit history. 我已经浏览过相关的提交历史和提交信息。
- [x] I have reproduced the incorrect behaviors using the latest commit on the master branch. 我已经使用 master 分支最新的 commit 复现了不正确的行为。

### Describe the bug

At the given program counter (`pc = 0x00800004ee`), the value of `a3` differs between the correct and incorrect states. The expected value of `a3` is `0x0000000000000000`, while the actual value is `0x0000000000800000`.
<img width="417" alt="Image" src="https://github.com/user-attachments/assets/1b4f243d-de7f-4e94-a1d6-187fcf98e81a" />
<img width="359" alt="Image" src="https://github.com/user-attachments/assets/a6243fe7-7fb9-4d5a-a9b0-593d3e4e176d" />

### Expected behavior

The value in register `a3` should be `0x0000000000000000` after executing the `amomin.w` instruction.

### To Reproduce

Here is the source code, binary file:
[test.zip](https://github.com/user-attachments/files/19783487/test.zip)

### Environment

- XiangShan branch: master
- XiangShan commit id: c01e75b55fbe706c21954429f2ec968cbc61a2cc
- NEMU commit id: 738ae334edc62237c3d4b49c722882ee1b7324fc

### Additional context

_No response_
