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

    li      x13, 0
    li      x16, 0x35b8c53e3930a213

    vsetvli x9, x0, e64, m1
    li      x8, -1
    vmv.v.x v14, x8

    li      x9, 0x8fffffb8
    li      x10, 0x9ff597b54a2e1009
    sd      x10, 16(x9)

    li      x14, 0
    li      x2, 0x8fffffbe
    sd      x14, 16(x2)
    sub     x20, x13, x16
    li      x2, 0x8fffffdc
    sd      x20, 0(x2)

    li      x9, 0x8fffffca
    vlseg2e16.v v13, (x9), v0.t

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
