### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and this is not a RISC-V ISA question. 我已经阅读过 RISC-V 指令集手册，这不是一个指令集本身的问题。
- [x] I have read the [XiangShan Documents](https://xiangshan-doc.readthedocs.io/zh_CN/latest). 我已经阅读过香山文档。
- [x] I have searched the previous issues and did not find anything relevant. 我已经搜索过之前的 issue，并没有找到相关的。
- [x] I have reviewed the commit messages from the relevant commit history. 我已经浏览过相关的提交历史和提交信息。

### Describe the bug

An instruction at pc = 12870000 is not accessable:

![Image](https://github.com/user-attachments/assets/0ebdbdb7-16f7-4902-bc71-5d45fee9c01f)
Xiangshan executes it and gets `t0=1`:

![Image](https://github.com/user-attachments/assets/ec24dd11-77e7-4717-8ebc-e9d99da12ac4)

![Image](https://github.com/user-attachments/assets/39ceda01-2768-4453-bf67-a0d02b51a50a)

### Expected behavior

Throw IAF trap.

### To Reproduce

[testcase.zip](https://github.com/user-attachments/files/19158117/testcase.zip)

### Environment

- XiangShan branch: master
- XiangShan commit id: d6b0a27ff
- ready-to-run commit id: 8c943ff
- SPIKE commit id:


### Additional context

_No response_
