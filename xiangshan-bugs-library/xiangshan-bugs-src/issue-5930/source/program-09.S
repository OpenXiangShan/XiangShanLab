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
    li x13, 0x80
    li x14, 0x1490534e200a47ba
    li x15, 0xfdf7618d2496ba28
    li x16, 0xc7be21a994d6aa8f
    li x17, 0xcffdb58628089f8a
    li x18, 0xfffffffffffffffe
    li x19, 0x2210dc3ced2682e5
    li x20, 0xd9288d531a6e8a27
    vsetvli x9, x0, e64, m1
    li x8, 0x0
    vmv.v.x v13, x8
    li x8, 0xaaaaaaaaaaaaaaaa
    vmv.v.x v14, x8
    li x8, 0xf0f0f0f0f0f0f0f
    vmv.v.x v15, x8
    li x8, 0x0
    vmv.v.x v16, x8
    li x8, 0xf0f0f0f0f0f0f0f
    vmv.v.x v17, x8
    li x8, 0x5555555555555555
    vmv.v.x v18, x8
    li x8, 0xf0f0f0f0f0f0f0f
    vmv.v.x v19, x8
    li x8, 0xffffffffffffffff
    vmv.v.x v20, x8
    li x9, 0x8fffffb8
    li x10, 0x7fff
    sd x10, 0(x9)
    li x10, 0x100
    sd x10, 8(x9)
    li x10, 0x1000
    sd x10, 16(x9)
    li x10, 0x2c92056bca470fac
    sd x10, 24(x9)
    li x10, 0x666adfe29621898f
    sd x10, 32(x9)
    li x10, 0x2b07c73b2b2eeb90
    sd x10, 40(x9)
    li x10, 0xbe60ebff7a3af395
    sd x10, 48(x9)
    li x10, 0x9d41418aadb35b20
    sd x10, 56(x9)
    li x8, 0
    li x9, 0
    li x10, 0
    li x11, 0
    li x12, 0

    srai x14, x19, 10
    c.add x20, x19
    subw x15, x13, x15
    li x2, 2415919067
    c.sdsp x16, 0(sp)
    addw x17, x17, x16
    li x9, 2415919039
    vsoxseg6ei16.v v13, (x9), v18, v0.t
    fence.i
    mulh x14, x13, x17
    ori x18, x16, -1816
    fence.i
    li x8, 2415919058
    vle8ff.v v14, (x8), v0.t
    srlw x15, x18, x19
    c.andi x14, 31
    mulhsu x14, x16, x19

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
