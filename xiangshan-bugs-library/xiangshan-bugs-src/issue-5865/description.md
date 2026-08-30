### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and this is not a RISC-V ISA question. 我已经阅读过 RISC-V 指令集手册，这不是一个指令集本身的问题。
- [x] I have read the [XiangShan Documents](https://xiangshan-doc.readthedocs.io/zh_CN/latest). 我已经阅读过香山文档。
- [x] I have searched the previous issues and did not find anything relevant. 我已经搜索过之前的 issue，并没有找到相关的。
- [x] I have reviewed the commit messages from the relevant commit history. 我已经浏览过相关的提交历史和提交信息。
- [x] I have reproduced the incorrect behaviors using the latest commit on the master branch. 我已经使用 master 分支最新的 commit 复现了不正确的行为。

### Branch

kunminghu-v3

### Describe the bug

I found a reduced RVV testcase where Spike and XiangShan disagree on whether a reserved masked `vmerge.vvm` form should raise `illegal instruction`.

The simplified testcase is as follows:

```asm
vsetvli x8, x0, e16, m4
vmv.v.i v4, 1
vmv.v.i v24, 0
vmv.v.i v0, -1
vmerge.vvm v0, v24, v4, v0
```

The last instruction is the key point:

```asm
vmerge.vvm v0, v24, v4, v0
```

This is the masked `vmerge.vvm` form, so the final operand `v0` is the source mask register. But the destination register group is also `v0`, because `vd = v0`. That means the destination vector register group overlaps the source mask register `v0`. This reserved overlap should raise `illegal instruction`.

Observed behavior:

- Spike raises `trap_illegal_instruction` at `vmerge.vvm v0, v24, v4, v0`
  with `mcause = 0x2`.
- The XiangShan `Apr 14 2026` emu used by the original replay executes the same
  5 user instructions with **no exception**.

This is a structural legality mismatch, not a tail-agnostic or mask-agnostic state-diff issue.

The entire code:

```asm
    .section .text
    .globl  _start

_start:
    la      t0, trap_handler
    csrw    mtvec, t0

    csrr    t0, mstatus
    li      t1, 0x00003600   
    or      t0, t0, t1
    csrw    mstatus, t0
    csrw    fcsr, x0

    j       user_code

user_code:

    vsetvli x8, x0, e16, m4
    vmv.v.i v4, 1
    vmv.v.i v24, 0
    vmv.v.i v0, -1
    vmerge.vvm v0, v24, v4, v0

exit:
    li      t0, 1
    la      t1, skiptrap_store_buf
    sd      t0, 0(t1)

    # DiffTest STATE_GOODTRAP
    li      t0, 0
    .insn   i 0x6b, 0, x0, t0, 0
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
    beq     t1, t3, fetch_inst
    li      t3, 12
    beq     t1, t3, fetch_inst

fetch_inst:
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

    .section .data
    .align  3
skiptrap_store_buf:
    .dword  0

    .align  6
fuzz_mem_pool:
    .space 4096
```

Observed Spike log:

```text
0x0000000080000030  vmv.v.i v0, -1
0x0000000080000034  vmerge.vvm v0, v24, v4, v0
exception trap_illegal_instruction, epc 0x0000000080000034
tval 0x000000005d820057
```

Diff Log:

[diff.log](https://github.com/user-attachments/files/27114433/diff.log)

### Expected behavior

`vmerge.vvm v0, v24, v4, v0` should raise `illegal instruction`.

The reason is that this is the masked `vmerge` form, so `v0` is the source mask register, while `vd = v0` makes the destination vector register group overlap that mask register. This reserved form should not execute normally.

Therefore the expected behavior is:

- Spike: `mcause = 0x2`
- XiangShan: should also raise `mcause = 0x2`


### Environment

[bug-report.tar.gz](https://github.com/user-attachments/files/27114388/bug-report.tar.gz)

### To Reproduce

Run the program.elf to reproduce the mismatch:
> ./build/emu --image program.elf --diff ./ready-to-run/riscv64-spike-so

### Additional context

_No response_
