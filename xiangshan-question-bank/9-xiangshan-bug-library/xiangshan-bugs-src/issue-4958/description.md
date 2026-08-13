### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and this is not a RISC-V ISA question. 我已经阅读过 RISC-V 指令集手册，这不是一个指令集本身的问题。
- [x] I have read the [XiangShan Documents](https://xiangshan-doc.readthedocs.io/zh_CN/latest). 我已经阅读过香山文档。
- [x] I have searched the previous issues and did not find anything relevant. 我已经搜索过之前的 issue，并没有找到相关的。
- [x] I have reviewed the commit messages from the relevant commit history. 我已经浏览过相关的提交历史和提交信息。
- [x] I have reproduced the incorrect behaviors using the latest commit on the master branch. 我已经使用 master 分支最新的 commit 复现了不正确的行为。

### Describe the bug

--------------- Triggers ----------------
 tselect: 0x0000000000000000
 0: tdata1: 0xf000000000000000 tdata2: 0x0000000000000000
 1: tdata1: 0xf000000000000000 tdata2: 0x0000000000000000
 2: tdata1: 0xf000000000000000 tdata2: 0x0000000000000000
 3: tdata1: 0xf000000000000000 tdata2: 0x0000000000000000
 4: tdata1: 0x0000000000000000 tdata2: 0x0000000000000000
privilegeMode: 1
     a2 different at pc = 0x008000348c, right= 0x00000000800034d4, wrong = 0x0000000003aa9c4e
     s8 different at pc = 0x008000348c, right= 0xffffffff7fe20084, wrong = 0x0000000000000000
     t6 different at pc = 0x008000348c, right= 0xd41708ee8b4f0050, wrong = 0x0000000080020000
Core 0: [31mABORT at pc = 0x80001e86
[0m[35mCore-0 instrCnt = 400, cycleCnt = 7,814, IPC = 0.051190
[0m[34mSeed=0 Guest cycle spent: 7,818 (this will be different from cycleCnt if emu loads a snapshot)
[0m[34mHost time spent: 78,397ms
[0m

### Expected behavior

pass

### To Reproduce

[testcase_587.tar.gz](https://github.com/user-attachments/files/21847502/testcase_587.tar.gz)
[testcase_829.tar.gz](https://github.com/user-attachments/files/21847501/testcase_829.tar.gz)

### Environment

- XiangShan branch: master / kunminghu-v3
- XiangShan commit id: ef913a6ad6
- XiangShan config: KunminghuV2Config
- NEMU commit id: bbeddeac1d589852ccac9fb99cdcb1477e25b97e
- SPIKE commit id:


### Additional context

_No response_
