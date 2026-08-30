### Before start

- [X] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and this is not a RISC-V ISA question. 我已经阅读过 RISC-V 指令集手册，这不是一个指令集本身的问题。
- [X] I have read the [XiangShan Documents](https://xiangshan-doc.readthedocs.io/zh_CN/latest). 我已经阅读过香山文档。
- [X] I have searched the previous issues and did not find anything relevant. 我已经搜索过之前的 issue，并没有找到相关的。
- [X] I have reviewed the commit messages from the relevant commit history. 我已经浏览过相关的提交历史和提交信息。

### Describe the bug

Execute `sh s6, 0(t1)`, where the value of t1 is `0x6b9`, and an `address misalignment` exception will occur. When the exception occurs, the value of the `mtval` register of Xiangshan and nemu is different.

The log screenshot is as follows：

![image](https://github.com/user-attachments/assets/efdecaab-78c7-46f6-999f-7e703d2365a6)
![image](https://github.com/user-attachments/assets/d2e2b3e7-50f6-4b7f-bc99-db9acd6a5b6a)


### Expected behavior

The riscv specification is as follows：

> If mtval is written with a nonzero value when a breakpoint, address-misaligned, access-fault, or page-fault exception occurs on an instruction fetch, load, or store, then mtval will contain the faulting virtual address


Please let me know if I missed any details. Thanks so much!

### To Reproduce

This is the test program and log information ：[test.zip](https://github.com/user-attachments/files/18138751/test.zip)


### Environment

- XiangShan branch: 
- XiangShan commit id: c7ca40e4d71e157897f43817976971d7cedfa22a (HEAD -> master, origin/master, origin/HEAD)
- NEMU commit id: cc72c9aa97dc2504f807191d03c57242da5aaeda
- SPIKE commit id:
ready-to-run:commit 96f40214d13db437a4aa5b118420cfe91e9c9836

### Additional context

_No response_
