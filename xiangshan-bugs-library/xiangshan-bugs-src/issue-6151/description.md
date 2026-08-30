### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and this is not a RISC-V ISA question. 我已经阅读过 RISC-V 指令集手册，这不是一个指令集本身的问题。
- [x] I have read the [XiangShan Documents](https://xiangshan-doc.readthedocs.io/zh_CN/latest). 我已经阅读过香山文档。
- [x] I have searched the previous issues and did not find anything relevant. 我已经搜索过之前的 issue，并没有找到相关的。
- [x] I have reviewed the commit messages from the relevant commit history. 我已经浏览过相关的提交历史和提交信息。
- [x] I have reproduced the incorrect behaviors using the latest commit on the master branch. 我已经使用 master 分支最新的 commit 复现了不正确的行为。

### Branch

master (kunminghu-v2)

### Describe the bug

### Observation:

A vl=0 segment load/store instruction (e.g. vlseg2e8.v) consumes ~540 cycles per execution, while I think it should retire almost immediately like other vl=0 vector instructions.

**Observed test results** (kunminghu-v2, commit 45eeffeb8):
3 groups × 256 iterations, measured via mcycle:
```
(A) vl=0 vlseg2e8.v : 541 cyc/iter   // Since vl=0, it seems incorrect for the execution of the vlseg2e8.v to consume such a substantial number of cycles.
(B) vl=2 vlseg2e8.v :  61 cyc/iter   // control: normal execution
(C) vl=0 vle8.v     :   9 cyc/iter   // control: non-segment vl=0, correctly fast
```

#### Suspected root cause: 

VSegmentUnit.scala:187
val maxSegIdx = instMicroOp.vl - 1.U

maxSegIdx is 8-bit unsigned. So when vl=0 this evaluates to 255, and the following FSM uses maxSegIdx as the termination bound. Since vl=0 means no element is active, the FSM does no real work on any iteration — but still walks index 0→255 before finishing.

### Expected behavior

vl = 0 segment instructions should retire immediately (like non-segment vector loads do).

### Environment

  - XiangShan commit id: `45eeffeb8`


### To Reproduce

[vseg_vl0_nodiff.log](https://github.com/user-attachments/files/29380633/vseg_vl0_nodiff.log)

[main.c](https://github.com/user-attachments/files/29380641/main.c)

### Additional context

Affects all nf≥2 segment instructions.
