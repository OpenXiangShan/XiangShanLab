### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and this is not a RISC-V ISA question. 我已经阅读过 RISC-V 指令集手册，这不是一个指令集本身的问题。
- [x] I have read the [XiangShan Documents](https://xiangshan-doc.readthedocs.io/zh_CN/latest). 我已经阅读过香山文档。
- [x] I have searched the previous issues and did not find anything relevant. 我已经搜索过之前的 issue，并没有找到相关的。
- [x] I have reviewed the commit messages from the relevant commit history. 我已经浏览过相关的提交历史和提交信息。

### Describe the bug

At pc = 0x800004a4 should throw LAF:

![Image](https://github.com/user-attachments/assets/9ca3a1a9-972e-4d32-8db9-7ac1f7db744b)

![Image](https://github.com/user-attachments/assets/055460dd-687e-4659-b627-7f869e2f77e4)

Xiangshan does not throw LAF trap. Although NEMU throw LAF trap, its `mtval` is error:
![Image](https://github.com/user-attachments/assets/213e69b4-4fb7-4ab2-bb0a-200be5405a79)

### Expected behavior

Xiansghan should throw LAF trap, `mtval` should be correct in NEMU.

### To Reproduce

[testcase.zip](https://github.com/user-attachments/files/19158279/testcase.zip)

### Environment

- XiangShan branch: master
- XiangShan commit id: d6b0a27ff
- ready-to-run commit id: 8c943ff
- SPIKE commit id:


### Additional context

_No response_
