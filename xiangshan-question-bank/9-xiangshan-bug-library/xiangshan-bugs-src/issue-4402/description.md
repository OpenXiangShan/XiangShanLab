### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and this is not a RISC-V ISA question. 我已经阅读过 RISC-V 指令集手册，这不是一个指令集本身的问题。
- [x] I have read the [XiangShan Documents](https://xiangshan-doc.readthedocs.io/zh_CN/latest). 我已经阅读过香山文档。
- [x] I have searched the previous issues and did not find anything relevant. 我已经搜索过之前的 issue，并没有找到相关的。
- [x] I have reviewed the commit messages from the relevant commit history. 我已经浏览过相关的提交历史和提交信息。

### Describe the bug

The diff results show that XiangShan has incorrect values in the `mepc` and `mcause` registers, whereas NEMU has an incorrect value in the `mtval` register.

![Image](https://github.com/user-attachments/assets/50d7a5d5-d6e4-4c3f-8f9d-dbe6b157fd52)
![Image](https://github.com/user-attachments/assets/365d6207-c826-4a88-93f1-7e1d71c223a8)
![Image](https://github.com/user-attachments/assets/f989d1bc-2503-4d58-afb7-1dc9015cccbb)

![Image](https://github.com/user-attachments/assets/3c0f7a8a-8f28-4e58-b30e-89dbdfe9da7b)

### Expected behavior

These register values should be consistent.

### To Reproduce

[testcase.zip](https://github.com/user-attachments/files/19196841/testcase.zip)

### Environment

- XiangShan branch: master
- XiangShan commit id: d6b0a27ff
- NEMU commit id: 2235c04
- ready-to-run commit id: 8c943ff


### Additional context

_No response_
