### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and this is not a RISC-V ISA question. 我已经阅读过 RISC-V 指令集手册，这不是一个指令集本身的问题。
- [x] I have read the [XiangShan Documents](https://xiangshan-doc.readthedocs.io/zh_CN/latest). 我已经阅读过香山文档。
- [x] I have searched the previous issues and did not find anything relevant. 我已经搜索过之前的 issue，并没有找到相关的。
- [x] I have reviewed the commit messages from the relevant commit history. 我已经浏览过相关的提交历史和提交信息。
- [x] I have reproduced the incorrect behaviors using the latest commit on the master branch. 我已经使用 master 分支最新的 commit 复现了不正确的行为。

### Describe the bug

Mismatch between Xiangshan and NEMU in a random generated program. The Return-address stack prediction hints encoded in the register operands of a JALR instruction is the main aspect that this test program is testing.I have no idea about the cause of the mismatch.The last few instructions that XiangShan/NEMU executed are :
_l444:  ebreak                                    ;
_l445:  la x16, d_0_15                            ;
        addi x16, x16, 0                          ;
        sc.w x1, x7, (x16)                        ;
_l446:  la x30, _l447                             ;
        jalr x13, 0(x30)                          ;
_l447:  la x5, _l448                              ;
        jalr x5, 0(x5)                            ;
_l448:  la x5, _l450                              ;
        jalr x1, 0(x5)                            ;
_l449:  la x5, _l451                              ;
        jalr x5, 0(x5)                            ;
_l450:  la x27, _l451                             ;
        jalr x5, 0(x27)                           ;
the diff log:
---------------- Triggers ----------------
 tselect: 0x0000000000000000
 0: tdata1: 0xf000000000000000 tdata2: 0x00007fcf1bbe7d40
 1: tdata1: 0xf000000000000000 tdata2: 0x0000000000000000
 2: tdata1: 0xf000000000000000 tdata2: 0x0000000000000000
 3: tdata1: 0xf000000000000000 tdata2: 0x0000000000000000
 4: tdata1: 0x0000000000000000 tdata2: 0x0000000000000000
privilegeMode: 0
     t0 different at pc = 0x0080001714, right= 0x000000008000170c, wrong = 0x0000000080001718
   mode different at pc = 0x0080001714, right= 0x0000000000000003, wrong = 0x0000000000000000
mstatus different at pc = 0x0080001714, right= 0x8000040a00006020, wrong = 0x8000000a000060a0
   mepc different at pc = 0x0080001714, right= 0x0000000080001714, wrong = 0x00000000800016d4
  mtval different at pc = 0x0080001714, right= 0x00000000afeb1c0b, wrong = 0x0000000000000000


### Expected behavior

paa

### To Reproduce

[testcase_1275.tar.gz](https://github.com/user-attachments/files/21965907/testcase_1275.tar.gz)

### Environment

- XiangShan branch: master / kunminghu-v3
- XiangShan commit id: ef913a6ad6
- XiangShan config: KunminghuV2Config
- NEMU commit id: bbeddeac1d589852ccac9fb99cdcb1477e25b97e
- SPIKE commit id:


### Additional context

_No response_
