    .option norvc
// Negative control for OpenXiangShan/XiangShan#6668.
//
// `csrr x0, vtype` is decoded by the normal CSR path (not the vl/vlenb pseudo
// channel) and is already rd-aware, so it must not trigger the rename
// assertion even on the pre-fix tree.

    .section .text
    .global _start

_start:
    li      t0, 0x200
    csrs    mstatus, t0

    csrr    zero, vtype
    addi    a0, x0, 0
    bnez    a0, fail

    li      a0, 0
    j       test_exit
fail:
    li      a0, 1

test_exit:
    la      t0, tohost
    li      t1, 1
    sd      t1, 0(t0)
1:  j       1b

    .section .tohost, "aw", @progbits
    .align 3
    .global tohost
tohost:
    .dword 0
