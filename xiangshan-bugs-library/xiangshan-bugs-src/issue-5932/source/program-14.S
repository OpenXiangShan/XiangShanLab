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

    vsetvli x9, x0, e64, m1
    li      x8, 0
    vmv.v.x v14, x8
    li      x8, 0x0f0f0f0f0f0f0f0f
    vmv.v.x v15, x8
    li      x8, 0xaaaaaaaaaaaaaaaa
    vmv.v.x v16, x8

    li      x9, 0x8fffffb8
    li      x10, 0xe0672377e7f6c7df
    sd      x10, 32(x9)
    li      x10, 0x416d1e6234c1864f
    sd      x10, 40(x9)

    li      x13, 0x800
    vwadd.vx v17, v15, x13
    li      x2, 0x8fffffc5
    sw      x13, 8(x2)
    vfwredusum.vs v20, v17, v16
    fence.i

    li      x11, 0x8fffffde
    vluxseg5ei32.v v18, (x11), v14

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
