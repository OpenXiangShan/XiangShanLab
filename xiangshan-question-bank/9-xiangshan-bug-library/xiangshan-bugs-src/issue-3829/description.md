### Before start

- [X] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and this is not a RISC-V ISA question. 我已经阅读过 RISC-V 指令集手册，这不是一个指令集本身的问题。
- [X] I have read the [XiangShan Documents](https://xiangshan-doc.readthedocs.io/zh_CN/latest). 我已经阅读过香山文档。
- [X] I have searched the previous issues and did not find anything relevant. 我已经搜索过之前的 issue，并没有找到相关的。
- [X] I have reviewed the commit messages from the relevant commit history. 我已经浏览过相关的提交历史和提交信息。

### Describe the bug

1. Run the workload, difftest shows (complete log in the log file):
```
privilegeMode: 3
 mcause different at pc = 0x0080000b80, right= 0x0000000000000004, wrong = 0x0000000000000005
Core 0: ABORT at pc = 0x80000bae
Core-0 instrCnt = 495, cycleCnt = 5,659, IPC = 0.087471
```

2. A trap is `raise intr cause NO: 4, epc: 8000e946`, NO.4 is `EX_LAM, // load address misaligned`. However, spike did not get into the trap, detail please see the tail of log file.
```
0x0000000080000b98:   9b 12 e3 01                 slliw   t0,t1,30
[src/cpu/cpu-exec.c:52,debug_hook] 0x0000000080000b9c:   67 83 04 00                 jalr   t1,s1,0
0x0000000080000b9c:   67 83 04 00                 jalr   t1,s1,0
[src/isa/riscv64/system/intr.c:69,raise_intr] raise intr cause NO: 4, epc: 8000e946
==============  In the last commit group  ==============
the first commit instr pc of DUT is 0x0000000080000b80
the first commit instr pc of REF is 0x0000000080000b80
============== Commit Group Trace (Core 0) ==============
```

### Expected behavior

I have run spike to test it, however the `mcause` value in spike is different from NEMU and Xiangshan. Part of spike log in the end of log file. I do not know whether Xiangshan is right or NEMU is right or spike, because Xiangshan supports some extensions, but spike doesn't.

### To Reproduce

[218.zip](https://github.com/user-attachments/files/17607340/218.zip)


### Environment

- XiangShan branch: master
- XiangShan commit id: 7af39ad2d
- NEMU commit id: 9ccee25c
- SPIKE commit id: 1.1.1-dev


### Additional context

Run spike with: `spike --pc=0x80000000 --isa=rv64gc -d xxx.elf`
