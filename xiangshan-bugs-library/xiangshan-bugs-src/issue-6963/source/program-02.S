.option norvc
.section .text.init
.globl _start
_start:
    li t0, -1
    csrw pmpaddr0, t0
    li t0, 0x1f
    csrw pmpcfg0, t0
    csrr t0, mstatus
    li t1, 0x600
    or t0, t0, t1
    csrw mstatus, t0
    csrw medeleg, x0
    la t0, trap_handler
    csrw mtvec, t0
    vsetivli zero, 2, e64, m1, tu, ma
    li t0, 1
    csrw vstart, t0
    csrr t0, sstatus
    li t1, -1537
    and t0, t0, t1
    li t1, 0x400
    or t0, t0, t1
    csrw sstatus, t0
    li t2, 0x800
    vle64.v v0, (t2)
    csrr t0, sstatus
    li t1, 0x600
    and t0, t0, t1
    li t1, 0x400
    bne t0, t1, 4f
    .4byte 0x0000006b
4:  .4byte 0x0000006b
1:  j 1b
trap_handler:
    csrr t0, sstatus
    li t1, 0x600
    and t0, t0, t1
    li t1, 0x400
    bne t0, t1, 5f
    .4byte 0x0000006b
5:  .4byte 0x0000006b
2:  j 2b
