### Before start

- [X] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and this is not a RISC-V ISA question. 我已经阅读过 RISC-V 指令集手册，这不是一个指令集本身的问题。
- [X] I have read the [XiangShan Documents](https://xiangshan-doc.readthedocs.io/zh_CN/latest). 我已经阅读过香山文档。
- [X] I have searched the previous issues and did not find anything relevant. 我已经搜索过之前的 issue，并没有找到相关的。
- [X] I have reviewed the commit messages from the relevant commit history. 我已经浏览过相关的提交历史和提交信息。

### Describe the bug

When xiangshan execute an illegal instruction `fld     fa1, 160(a1)`, xiangshan gets wrong `mstatus & mtval` comparing with `ready-to-run/spike-so`, `mtval` is certainly wrong because it is also different from the result of NEMU. Please check the two log files for details.

### Expected behavior

mstatus & mtval is right value.

### To Reproduce

[test.zip](https://github.com/user-attachments/files/17702527/test.zip)


### Environment

- XiangShan branch: master
- XiangShan commit id: 4376b5255
- NEMU commit id: a4815f99
- SPIKE commit id: 1.1.0-dev


### Additional context

_No response_
