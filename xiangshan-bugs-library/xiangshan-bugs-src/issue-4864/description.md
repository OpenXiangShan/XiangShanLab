### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and this is not a RISC-V ISA question. 我已经阅读过 RISC-V 指令集手册，这不是一个指令集本身的问题。
- [x] I have read the [XiangShan Documents](https://xiangshan-doc.readthedocs.io/zh_CN/latest). 我已经阅读过香山文档。
- [x] I have searched the previous issues and did not find anything relevant. 我已经搜索过之前的 issue，并没有找到相关的。
- [x] I have reviewed the commit messages from the relevant commit history. 我已经浏览过相关的提交历史和提交信息。
- [x] I have reproduced the incorrect behaviors using the latest commit on the master branch. 我已经使用 master 分支最新的 commit 复现了不正确的行为。

### Describe the bug

I have no idea why it happened:
```
[28] commit pc 0000000080001000 inst 0280006f wen 0 dst 00 data 0000000000000000 idx 017 j       pc + 0x28
[29] commit pc 0000000080001028 inst b850d617 wen 1 dst 12 data 000000003850e028 idx 018 auipc   a2, 0xb850d
[30] commit pc 000000008000102c inst bee03893 wen 1 dst 17 data 0000000000000001 idx 019 sltiu   a7, zero, -1042
[31] exception pc 0000000080001030 inst 000fb083 cause 0000000000000013 ld      ra, 0(t6) <--
......
privilegeMode: 3
 mcause different at pc = 0x0080001028, right= 0x0000000000000005, wrong = 0x0000000000000013
Core 0: [31mABORT at pc = 0x8000104c
```
Please check the log file for more information.

### Expected behavior

The `mcause` should be 0x05.

### To Reproduce

[test.zip](https://github.com/user-attachments/files/21061361/test.zip)

### Environment

- XiangShan branch: master
- XiangShan commit id: c25dcc68
- XiangShan config: MinimalConfig
- Ready2run: 31254e6


### Additional context

If I missed something, or I made mistakes, please tell me. Thanks.
