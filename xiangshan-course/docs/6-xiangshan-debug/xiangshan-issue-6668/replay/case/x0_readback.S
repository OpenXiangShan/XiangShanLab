    .option norvc
// Secondary check for OpenXiangShan/XiangShan#6668.
//
// `csrr x0, vl` / `csrr x0, vlenb` must read the CSR and write nothing.  In the
// buggy design the pseudo decode channel takes gpWen straight from the uop
// table, so rename allocates a physical register for x0 and writes the RAT
// entry of x0.  Reading x0 afterwards then returns the CSR value.
//
// With vl = 8 before the read, a corrupted x0 reads back as 8 instead of 0.

    .section .text
    .global _start

_start:
    // mstatus.VS = Initial so vector instructions are legal.
    li      t0, 0x200
    csrs    mstatus, t0

    // vl = 4 (rd = x0: legal, writes nothing)
    vsetivli zero, 4, e32, m1, ta, ma

    // Trigger: csrrs x0, vl, x0
    csrr    zero, vl
    // x0 must still read 0; a rename-table corruption returns vl (= 4).
    addi    a0, x0, 0
    bnez    a0, fail

    // Control: csrrs x0, vlenb, x0
    csrr    zero, vlenb
    addi    a1, x0, 0
    bnez    a1, fail

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
