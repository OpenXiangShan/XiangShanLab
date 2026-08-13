### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and this is not a RISC-V ISA question. 我已经阅读过 RISC-V 指令集手册，这不是一个指令集本身的问题。
- [x] I have read the [XiangShan Documents](https://xiangshan-doc.readthedocs.io/zh_CN/latest). 我已经阅读过香山文档。
- [x] I have searched the previous issues and did not find anything relevant. 我已经搜索过之前的 issue，并没有找到相关的。
- [x] I have reviewed the commit messages from the relevant commit history. 我已经浏览过相关的提交历史和提交信息。
- [x] I have reproduced the incorrect behaviors using the latest commit on the master branch. 我已经使用 master 分支最新的 commit 复现了不正确的行为。

### Branch

master (kunminghu-v2)

### Describe the bug

The reserved `vmv<nr>r.v` register-alignment case does not raise illegal instruction in DUT.

Instruction sequence:

```asm
vsetivli zero, 4, e32, m1, ta, ma
vmv2r.v v3, v5
```

For `vmv2r.v`, source and destination register numbers must be aligned for a two-register group. This testcase uses unaligned registers.

NEMU REF traps:

```text
mcause = 0x2
mepc   = 0x800000c4
mtval  = 0x9e50b1d7
```

DUT does not trap:

```text
mcause = 0x0
mepc   = 0x0
mtval  = 0x0
```

Key mismatch:

```text
mepc different at pc = 0x008000012c, right = 0x00000000800000c4, wrong = 0x0000000000000000
mtval different at pc = 0x008000012c, right = 0x000000009e50b1d7, wrong = 0x0000000000000000
mcause different at pc = 0x008000012c, right = 0x0000000000000002, wrong = 0x0000000000000000
Core 0: ABORT at pc = 0x8000012c
```

### Expected behavior

`vmv2r.v v3, v5` should raise illegal instruction.

Actual Behavior:
DUT executes past the instruction without taking the illegal-instruction exception, then diverges from NEMU REF.

### Environment

XiangShan commit id: https://github.com/OpenXiangShan/XiangShan/commit/f3cc750109cc2a0ff6c12a920221f1a5a324bc75
NEMU commit id (if difftest failed with NEMU): 4330f15c192f6add96e7e5190c51598aaf7728fc
ready-to-run commit: e745a730a26626ca2f3ab6ecb0d7ca8087b6f8b2
message: Bump nemu ref in ready-to-run
date: 2026-04-02


### To Reproduce

[r087_vmvnr_unaligned_regs_bundle.zip](https://github.com/user-attachments/files/28336751/r087_vmvnr_unaligned_regs_bundle.zip)

### Additional context

_No response_
