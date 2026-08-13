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
    li x8, 0x4117a3144560f91a
    li x9, 0x2ae7c8bf78cd7009
    li x10, 0x4eeea7c6ce70406a
    li x11, 0x80
    li x12, 0xbd486c2b6b72ead9
    li x13, 0xffffffffffffff7f
    li x14, 0xffffffffffffff7f
    li x15, 0x8cb9d90fcee7476e
    li x8, 0xb0aa511753502dba
    fmv.d.x f16, x8
    li x8, 0x7ffaf76b9aa1e809
    fmv.d.x f17, x8
    li x8, 0x7ffb9eccfcc45ea5
    fmv.d.x f18, x8
    li x8, 0xf3434b49bf9cb424
    fmv.d.x f19, x8
    li x8, 0x76fab73a4ad8d4d9
    fmv.d.x f20, x8
    li x8, 0x196ddc9a0035e68f
    fmv.d.x f21, x8
    li x8, 0x8512c7158eef9134
    fmv.d.x f22, x8
    li x8, 0xbff0000000000000
    fmv.d.x f23, x8
    vsetvli x9, x0, e64, m1
    li x8, 0x5555555555555555
    vmv.v.x v16, x8
    li x8, 0xaaaaaaaaaaaaaaaa
    vmv.v.x v17, x8
    li x8, 0xaea08d71eb5e145a
    vmv.v.x v18, x8
    li x8, 0x0
    vmv.v.x v19, x8
    li x8, 0x3a5df9a517eb24b9
    vmv.v.x v20, x8
    li x8, 0xe456d0e1e625c56a
    vmv.v.x v21, x8
    li x8, 0xffffffffffffffff
    vmv.v.x v22, x8
    li x8, 0xffffffffffffffff
    vmv.v.x v23, x8
    li x9, 0x8fffffb8
    li x10, 0x8000
    sd x10, 0(x9)
    li x10, 0x2bedf3b6c558268a
    sd x10, 8(x9)
    li x10, 0xe6de5bfbce979889
    sd x10, 16(x9)
    li x10, 0xa73e1a3bce6e7fd6
    sd x10, 24(x9)
    li x10, 0x6b8d0eef4eeb52e0
    sd x10, 32(x9)
    li x10, 0x778c4b7f9bdecbc5
    sd x10, 40(x9)
    li x10, 0x80000000
    sd x10, 48(x9)
    li x10, 0x1000
    sd x10, 56(x9)
    li x8, 0
    li x9, 0
    li x10, 0
    li x11, 0
    li x12, 0

    vsetvli x8, x0, e32, mf2
    li x15, 2415919032
    vmv.v.i v19, 7
    vloxseg5ei64.v v20, (x15), v19
    vmv.s.x v18, x13
    vfcvt.xu.f.v v20, v19
    vfirst.m x10, v18
    li x12, 2415919035
    vmv.v.i v22, -9
    vsuxseg3ei32.v v21, (x12), v22, v0.t

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
