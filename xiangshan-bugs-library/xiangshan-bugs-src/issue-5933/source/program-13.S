.section .text.init
.globl  _start

_start:
    la      t0, trap_handler
    csrw    mtvec, t0

    csrr    t0, mstatus
    li      t1, 0x00003600
    or      t0, t0, t1
    csrw    mstatus, t0
    csrw    fcsr, x0

    li      x13, 0xfe7bd4046a923944
    li      x18, 0

    vsetvli x9, x0, e64, m1
    li      x8, 0x75b034a797aff2dc
    vmv.v.x v17, x8
    li      x8, -1
    vmv.v.x v18, x8

    li      x9, 0x8fffffb8
    li      x10, 0x3803444b2551a2ae
    sd      x10, 16(x9)
    li      x10, 0x1000
    sd      x10, 24(x9)

    vfncvt.f.f.w v13, v18
    remuw   x19, x18, x13
    vmv.x.s x15, v17
    li      x12, 0x8fffffbe
    sw      x13, 0(x12)
    fence.i
    sltu    x19, x15, x19
    li      x8, 0x8fffffcd
    vlsseg3e64.v v18, (x8), x19

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
