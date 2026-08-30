### Before start

- [X] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and this is not a RISC-V ISA question. 我已经阅读过 RISC-V 指令集手册，这不是一个指令集本身的问题。
- [X] I have read the [XiangShan Documents](https://xiangshan-doc.readthedocs.io/zh_CN/latest). 我已经阅读过香山文档。
- [X] I have searched the previous issues and did not find anything relevant. 我已经搜索过之前的 issue，并没有找到相关的。
- [X] I have reviewed the commit messages from the relevant commit history. 我已经浏览过相关的提交历史和提交信息。

### Describe the bug

When executing the `csrrwi a4, sstatus, 8` instruction, the `mstatus` register values ​​of the Xiangshan and reference models are inconsistent, as shown in the `sdt` field.

Note: This issue is related to https://github.com/OpenXiangShan/XiangShan/issues/3934 and https://github.com/OpenXiangShan/NEMU/issues/695, but the latest version is used and both issues have been fixed

### Expected behavior

The log screenshot is as follows：

![image](https://github.com/user-attachments/assets/8473c857-5086-4a35-8f3a-7d8190ae88ec)
![image](https://github.com/user-attachments/assets/b126792e-38b6-46df-a67b-7bc9dccc7197)




The log information of nemu and spike is the same

### To Reproduce

This is the test program and log information [test.zip](https://github.com/user-attachments/files/18140568/test.zip)


### Environment

- XiangShan branch: 
- XiangShan commit id: c7ca40e4d71e157897f43817976971d7cedfa22a (HEAD -> master, origin/master, origin/HEAD)
- NEMU commit id:  cc72c9aa97dc2504f807191d03c57242da5aaeda
- SPIKE commit id:
ready-to-run:commit 96f40214d13db437a4aa5b118420cfe91e9c9836

### Additional context

_No response_
