### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and this is not a RISC-V ISA question. 我已经阅读过 RISC-V 指令集手册，这不是一个指令集本身的问题。
- [x] I have read the [XiangShan Documents](https://xiangshan-doc.readthedocs.io/zh_CN/latest). 我已经阅读过香山文档。
- [x] I have searched the previous issues and did not find anything relevant. 我已经搜索过之前的 issue，并没有找到相关的。
- [x] I have reviewed the commit messages from the relevant commit history. 我已经浏览过相关的提交历史和提交信息。

### Describe the bug

Is the `amoswap.w` instruction unsupported in NEMU? It raises an illegal instruction exception.

![Image](https://github.com/user-attachments/assets/a7b378d4-fb05-4e3e-a617-1843bf1df108)
![Image](https://github.com/user-attachments/assets/2c2f2dc1-6978-4b38-ba4d-e07b5df3b4e4)

### Expected behavior

NEMU should raise an exception related to AMO.

### To Reproduce

[testcase.zip](https://github.com/user-attachments/files/19191190/testcase.zip)

### Environment

- XiangShan branch: master
- XiangShan commit id: d6b0a27
- ready-to-run commit id: 8c943ff
- SPIKE commit id:


### Additional context

_No response_
