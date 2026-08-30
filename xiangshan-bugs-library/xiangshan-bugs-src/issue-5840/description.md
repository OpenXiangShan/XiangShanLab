### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and this is not a RISC-V ISA question. 我已经阅读过 RISC-V 指令集手册，这不是一个指令集本身的问题。
- [x] I have read the [XiangShan Documents](https://xiangshan-doc.readthedocs.io/zh_CN/latest). 我已经阅读过香山文档。
- [x] I have searched the previous issues and did not find anything relevant. 我已经搜索过之前的 issue，并没有找到相关的。
- [x] I have reviewed the commit messages from the relevant commit history. 我已经浏览过相关的提交历史和提交信息。
- [x] I have reproduced the incorrect behaviors using the latest commit on the master branch. 我已经使用 master 分支最新的 commit 复现了不正确的行为。

### Branch

kunminghu-v3

### Describe the bug


In the current XiangShan RVV implementation, `vzext.vf8` can compute the wrong zero-extended result for an active destination element.

Minimal reproducer:

```asm
.section .text
.globl  _start

_start:
    la      t0, trap_handler
    csrw    mtvec, t0

    csrr    t0, mstatus
    li      t1, 0x00003600       # set FS+VS to Dirty (0b11) so FP/RVV regs are usable
    or      t0, t0, t1
    csrw    mstatus, t0
    csrw    fcsr, x0

    j       user_code

user_code:
    li x9, 0xa04f
    vsetivli x8, 1, e16, mf4
    vmv.s.x v28, x9
    vsetivli x8, 27, e64, m1
    vzext.vf8 v14, v28
    j exit

exit:
    li      t0, 1
    la      t1, tohost
    sd      t0, 0(t1)
1:
    j       1b

    .align  2
trap_handler:
    csrr    t0, mepc
    csrr    t1, mcause
    csrr    t4, mtval

    slli    t5, t1, 1
    srli    t1, t5, 1

    li      t2, 2
    li      t3, 2
    beq     t1, t3, use_mtval
    li      t3, 1
    beq     t1, t3, update_mepc
    li      t3, 12
    beq     t1, t3, update_mepc

    lhu     t4, 0(t0)
    j       decode_length

use_mtval:
    j       decode_length

decode_length:
    andi    t4, t4, 3
    li      t3, 3
    bne     t4, t3, compressed_len
    li      t2, 4
    j       update_mepc

compressed_len:
    li      t2, 2

update_mepc:
    add     t0, t0, t2
    csrw    mepc, t0
    csrw    mcause, x0
    csrw    mtval, x0
    mret

    .section .tohost,"aw",@progbits
    .align  6
    .globl  tohost
    .globl  fromhost
tohost:
    .dword  0
fromhost:
    .dword  0
```

Relevant minimized disassembly:

```asm
80000024: li x9, 0xa04f
8000002a: vsetivli x8, 1, e16, mf4
8000002e: vmv.s.x v28, x9
80000032: vsetivli x8, 27, e64, m1
80000036: vzext.vf8 v14, v28
```

Observed XiangShan difftest mismatch:

```text
v14_high different at pc = 0x0080000036, right = 0x00000000000000a0, wrong = 0x0000000000000029
```

The same bug in the original fuzz case appeared at:

```text
v14_high different at pc = 0x008000034a, right = 0x00000000000000a0, wrong = 0x0000000000000029
```

Diff log: 

[diff.log](https://github.com/user-attachments/files/26956800/diff.log)

### Expected behavior

`vzext.vf8` should zero-extend 8-bit source elements into wider destination elements.

In this testcase:

- `vmv.s.x v28, x9` writes the low element with `x9 = 0xa04f`
- the low two bytes in `v28` are therefore `0x4f` and `0xa0`
- after switching to `SEW=64`, `vzext.vf8 v14, v28` should produce:
  - element 0 = `0x000000000000004f`
  - element 1 = `0x00000000000000a0`

Instead, XiangShan produces `0x29` for the high observed element where the reference model produces `0xa0`.

### Environment

[bug-report.tar.gz](https://github.com/user-attachments/files/26956802/bug-report.tar.gz)

### To Reproduce

1. Run XiangShan with the same command shape as the original fuzz campaign:
```bash
build/verilator-compile/emu --image program.elf --diff ready-to-run/riscv64-spike-so
```

2. Observe the mismatch at the `vzext.vf8` PC:
```text
v14_high different at pc = 0x0080000036, right = 0x00000000000000a0, wrong = 0x0000000000000029
```

### Additional context

_No response_
