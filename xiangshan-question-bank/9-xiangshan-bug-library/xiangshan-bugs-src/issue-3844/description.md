### Before start

- [X] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and this is not a RISC-V ISA question. 我已经阅读过 RISC-V 指令集手册，这不是一个指令集本身的问题。
- [X] I have read the [XiangShan Documents](https://xiangshan-doc.readthedocs.io/zh_CN/latest). 我已经阅读过香山文档。
- [X] I have searched the previous issues and did not find anything relevant. 我已经搜索过之前的 issue，并没有找到相关的。
- [X] I have reviewed the commit messages from the relevant commit history. 我已经浏览过相关的提交历史和提交信息。

### Describe the bug

When I use nemu as the reference model to test Xiangshan, there is a difference in the mcause value when executing `fsh ft0, 9(sp)` or`flh ft0, 9(sp)`instructions.

For more details, please refer to https://github.com/OpenXiangShan/NEMU/issues/644 and https://github.com/OpenXiangShan/XiangShan/issues/3839#issuecomment-2459855478

### Expected behavior

 Execute `fsh ft0, 9(sp)` command with --diff /xs-env/NEMU/build/riscv64-nemu-interpreter-so parameter
![image](https://github.com/user-attachments/assets/1069ccc6-582e-49a7-a37d-57fa75a479d7)
![image](https://github.com/user-attachments/assets/aa4b9f12-3379-416a-b8e3-d64ee3b29b1d)


### To Reproduce

`fsh     ft0, 9(sp)   #flh     ft0, 9(sp) `

### Environment

- XiangShan branch: 
- XiangShan commit id: 7af39ad2ddb1305b2c4ddf4c3a9663a7c3615fa6 (HEAD -> master, origin/master, origin/HEAD)
- NEMU commit id: 34ba2259558ed89f5a042179ef0a9131e53ce037 (HEAD, origin/master, origin/HEAD)
- SPIKE commit id:


### Additional context

_No response_
