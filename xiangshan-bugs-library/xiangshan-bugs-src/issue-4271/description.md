### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and this is not a RISC-V ISA question. 我已经阅读过 RISC-V 指令集手册，这不是一个指令集本身的问题。
- [x] I have read the [XiangShan Documents](https://xiangshan-doc.readthedocs.io/zh_CN/latest). 我已经阅读过香山文档。
- [x] I have searched the previous issues and did not find anything relevant. 我已经搜索过之前的 issue，并没有找到相关的。
- [x] I have reviewed the commit messages from the relevant commit history. 我已经浏览过相关的提交历史和提交信息。

### Describe the bug

![Image](https://github.com/user-attachments/assets/6756445a-e730-41e0-ab72-3b3c4769ac1d)
XSCore.scala:66:33: reference to MemBlock is ambiguous;


### Expected behavior

为什么make verilog中xscore.scala文件报错？在运行前我已经更新了子模块。

![Image](https://github.com/user-attachments/assets/467de6fe-aaf3-407b-961f-29d7419dbe0e)

### To Reproduce

在Xiangshan目录下make verilog



### Environment

- XiangShan branch:origin/master
- XiangShan commit id: 
- NEMU commit id: 
- SPIKE commit id:


### Additional context

_No response_
