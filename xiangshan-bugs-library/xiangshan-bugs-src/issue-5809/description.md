### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and this is not a RISC-V ISA question. 我已经阅读过 RISC-V 指令集手册，这不是一个指令集本身的问题。
- [x] I have read the [XiangShan Documents](https://xiangshan-doc.readthedocs.io/zh_CN/latest). 我已经阅读过香山文档。
- [x] I have searched the previous issues and did not find anything relevant. 我已经搜索过之前的 issue，并没有找到相关的。
- [x] I have reviewed the commit messages from the relevant commit history. 我已经浏览过相关的提交历史和提交信息。
- [x] I have reproduced the incorrect behaviors using the latest commit on the master branch. 我已经使用 master 分支最新的 commit 复现了不正确的行为。

### Branch

kunminghu-v3

### Describe the bug

I found a minimized RVV testcase that triggers an illegal-instruction trap mismatch between XiangShan and Spike after a `vsetvli` produces an illegal vector configuration.

The key instruction sequence in the rebuilt minimized reproducer is:

```asm
vsetvli x21, x21, 992
csrr x23, vl
csrr x22, vtype
vmv.x.s x21, v16
```

In the direct XiangShan difftest run, Spike enters an illegal-instruction trap on `vmv.x.s x21, v16`, but XiangShan does not. Instead, XiangShan commits the instruction and writes back `x21`.

The mismatch is:

```text
s5 different at pc = 0x008000021e, right = 0x0000000000000000, wrong = 0x0f0f0f0f0f0f0f55
mstatus different at pc = 0x008000021e, right = 0x8000040a00007e00, wrong = 0x8000000a00006600
mepc different at pc = 0x008000021e, right = 0x000000008000021e, wrong = 0x0000000000000000
mtval different at pc = 0x008000021e, right = 0x0000000043002ad7, wrong = 0x0000000000000000
mcause different at pc = 0x008000021e, right = 0x0000000000000002, wrong = 0x0000000000000000
```

From the same commit log, the immediately preceding CSR reads show:
```text
[151] commit pc 0000000080000216 inst c2002bf3 wen 1 dst 23 data 0000000000000000
[152] commit pc 000000008000021a inst c2102b73 wen 1 dst 22 data 8000000000000000
[153] commit pc 000000008000021e inst 43002ad7 wen 1 dst 21 data 0f0f0f0f0f0f0f55
```

So after `vsetvli x21, x21, 992`, Spike-visible architectural state is:
- `vl = 0`
- `vtype = 0x8000000000000000` (`vill` set)

and then the following `vmv.x.s` should trap as illegal instruction. XiangShan instead executes it and commits the scalar writeback.

The whole log: 

[skiptrap.commit.log](https://github.com/user-attachments/files/26699446/skiptrap.commit.log)

### Expected behavior

After `vsetvli` produces `vtype.vill = 1`, a following vector instruction such as `vmv.x.s` should raise an illegal-instruction trap instead of executing normally.

Spike reports:
- `mcause = 2`
- `mepc = 0x8000021e`
- `mtval = 0x43002ad7`

XiangShan should match this architectural behavior.

### Environment

[bug-report.tar.gz](https://github.com/user-attachments/files/26699288/bug-report.tar.gz)

### To Reproduce

1. Run XiangShan with diff:
```bash
./build/verilator-compile/emu --image program.elf --diff ./ready-to-run/riscv64-spike-so 
```

2. Observed:
```text
s5 different at pc = 0x008000021e, right = 0x0000000000000000, wrong = 0x0f0f0f0f0f0f0f55
mstatus different at pc = 0x008000021e, right = 0x8000040a00007e00, wrong = 0x8000000a00006600
mepc different at pc = 0x008000021e, right = 0x000000008000021e, wrong = 0x0000000000000000
mtval different at pc = 0x008000021e, right = 0x0000000043002ad7, wrong = 0x0000000000000000
mcause different at pc = 0x008000021e, right = 0x0000000000000002, wrong = 0x0000000000000000
Core 0: ABORT ...
```

### Additional context

_No response_
