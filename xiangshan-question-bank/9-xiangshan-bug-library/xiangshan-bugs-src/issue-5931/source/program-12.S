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
    li x13, 0x60dd92a3ecf5bcd7
    li x14, 0x6a81406fbfd26f3e
    li x15, 0x5d4c83e7a011bab6
    li x16, 0xfffffffffffff800
    li x17, 0x5d512aea8841af0
    li x18, 0xfbe0dbb053f81b37
    li x19, 0xffffffffffffff7f
    li x20, 0xffffffffffffff80
    vsetvli x9, x0, e64, m1
    li x8, 0xaaaaaaaaaaaaaaaa
    vmv.v.x v13, x8
    li x8, 0xe8f875b260d620f1
    vmv.v.x v14, x8
    li x8, 0xaaaaaaaaaaaaaaaa
    vmv.v.x v15, x8
    li x8, 0xf0f0f0f0f0f0f0f
    vmv.v.x v16, x8
    li x8, 0x43a795d7e6d6f1bf
    vmv.v.x v17, x8
    li x8, 0x41ff7079b17b6979
    vmv.v.x v18, x8
    li x8, 0x38a982ab6018bf71
    vmv.v.x v19, x8
    li x8, 0xf0f0f0f0f0f0f0f
    vmv.v.x v20, x8
    li x9, 0x8fffffb8
    li x10, 0xdea0cf615541b173
    sd x10, 0(x9)
    li x10, 0xb89d35a5ad6c0955
    sd x10, 8(x9)
    li x10, 0xc35ee50de2d66c47
    sd x10, 16(x9)
    li x10, 0xc0c7072d21a3ba47
    sd x10, 24(x9)
    li x10, 0x279bee011041d6e8
    sd x10, 32(x9)
    li x10, 0x5a722c65a16d457c
    sd x10, 40(x9)
    li x10, 0x10000
    sd x10, 48(x9)
    li x10, 0x1
    sd x10, 56(x9)
    li x8, 0
    li x9, 0
    li x10, 0
    li x11, 0
    li x12, 0

    li x8, 2415919059
    sd x13, 11(x8)
    mulw x15, x15, x15
    divu x18, x19, x13
    mulhu x14, x20, x17
    li x12, 2415919058
    vlseg2e32ff.v v20, (x12)
    li x11, 2415919058
    sh x14, 15(x11)
    vmaxu.vx v20, v15, x17, v0.t
    li x17, -7265179862056305209
    csrrs x13, mscratch, x17
    fence.i
    vwmacc.vx v14, x13, v19
    li x13, 2276710301682653949
    csrrs x18, mscratch, x13
    srl x16, x17, x18
    fence.i
    vslideup.vx v15, v19, x17, v0.t
    c.mv x16, x18
    li x11, 2415919061
    lh x13, 3(x11)
    li x10, 2415919045
    vssseg8e16.v v16, (x10), x19, v0.t
    c.mv x15, x17
    sub x16, x14, x14
    vasubu.vx v20, v20, x17, v0.t
    li x9, 2415919051
    vsuxseg2ei16.v v20, (x9), v19
    divw x18, x19, x16
    vwsub.vv v13, v13, v13
    fence.i
    fence.i
    vfncvt.xu.f.w v16, v20, v0.t
    vfsqrt.v v16, v20, v0.t
    lui x14, 4096

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
