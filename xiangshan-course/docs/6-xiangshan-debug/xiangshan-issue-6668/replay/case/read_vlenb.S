// Positive control for the rd != 0 path.
//
// `csrr t0, vlenb` must still write t0 after the fix that clears gpWen only
// for rd == x0.  VLEN = 128 on XiangShan (kunminghu-v3 DefaultConfig), so
// vlenb = 16; the value is a constant in the decode immediate path and does
// not depend on the vl read path (issue #6676).
    .option norvc
    .section .text
    .global _start

_start:
    li      t0, 0x200
    csrs    mstatus, t0

    csrr    t0, vlenb
    li      t1, 16
    bne     t0, t1, fail

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
