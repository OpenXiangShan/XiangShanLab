### Before start

- [X] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and this is not a RISC-V ISA question. 我已经阅读过 RISC-V 指令集手册，这不是一个指令集本身的问题。
- [X] I have read the [XiangShan Documents](https://xiangshan-doc.readthedocs.io/zh_CN/latest). 我已经阅读过香山文档。
- [X] I have searched the previous issues and did not find anything relevant. 我已经搜索过之前的 issue，并没有找到相关的。
- [X] I have reviewed the commit messages from the relevant commit history. 我已经浏览过相关的提交历史和提交信息。

### Describe the bug

At 0x00002b30, we trigger load address misaligned and load page fault at the same time, xiangshan throws `raise intr cause NO: 13, epc: 2b30` which is `EX_LPF, // load page fault`. But spike throws `exception trap_load_address_misaligned, epc 0x00002b30`.  Please check the **log files** of xiangshan and spike in zip file.

![image](https://github.com/user-attachments/assets/90782cca-6fb1-4831-b0dc-00f1bb501151)

AFAIK: in Xiangshan, load address misaligned has a higher priority than load page fault, [code](https://github.com/OpenXiangShan/XiangShan/blob/d9c759412f2793aae1bd0c845dec621535db1cde/src/main/scala/xiangshan/package.scala#L864).


### Expected behavior

Xiangshan should raise `load address misaligned` not `load page fault`.

### To Reproduce

[test.zip](https://github.com/user-attachments/files/17565837/test.zip)


### Environment

- XiangShan branch: master
- XiangShan commit id: e11ec86cc
- NEMU commit id: 821ea961
- SPIKE commit id: 1.1.1-dev


### Additional context

My xiangshan env is not the latest version.
