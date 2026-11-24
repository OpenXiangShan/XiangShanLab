.option norvc
.section .text.init
.globl _start
_start:
    li t0, -1
    csrw pmpaddr0, t0
    li t0, 0x1f
    csrw pmpcfg0, t0
    la t0, failed
    csrw mtvec, t0
    li t0, 0x600
    csrs mstatus, t0
    vsetivli zero, 2, e64, m1, tu, mu
    vmv.v.i v0, -1
    vmv.v.i v5, 1
    vmv.v.i v6, 2
    li t1, 0x80020940
    li t2, 16
    mv x31, t1
    sd zero, 0(t1)
    sd zero, 8(t1)
    vsetivli zero, 1, e64, m1, tu, mu
    csrwi vstart, 1
.globl target
target:
    vssseg2e64.v v5, (t1), t2, v0.t
    csrr t0, vstart
    bnez t0, failed
    ld t3, 0(t1)
    li t4, 0
    bne t3, t4, failed
    ld t3, 8(t1)
    li t4, 0
    bne t3, t4, failed
    li a0, 0
    .4byte 0x0000006b
1:  j 1b
failed:
    li a0, 1
    .4byte 0x0000006b
2:  j 2b
