### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and this is not a RISC-V ISA question. 我已经阅读过 RISC-V 指令集手册，这不是一个指令集本身的问题。
- [x] I have read the [XiangShan Documents](https://xiangshan-doc.readthedocs.io/zh_CN/latest). 我已经阅读过香山文档。
- [x] I have searched the previous issues and did not find anything relevant. 我已经搜索过之前的 issue，并没有找到相关的。
- [x] I have reviewed the commit messages from the relevant commit history. 我已经浏览过相关的提交历史和提交信息。

### Describe the bug

![Image](https://github.com/user-attachments/assets/71ec02e0-6f77-49dd-a612-1830af0ac7b2)

![Image](https://github.com/user-attachments/assets/5e18037f-eda9-49d3-9892-4f44813988f2)
NEMU throw `mcause=1`, and the `s9 = 0` not `1`.

### Expected behavior

NEMU execute this instruction correctly.

### To Reproduce

[testcase.zip](https://github.com/user-attachments/files/19157899/testcase.zip)

### Environment

- XiangShan branch: master
- XiangShan commit id: d6b0a27ff
- ready-to-run commit id: 8c943ff
- SPIKE commit id:


### Additional context

_No response_
