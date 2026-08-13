.section .text.init
.globl _start

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
    li x8, 0x56afa69e587a63e9
    li x9, 0x288e055072426941
    li x10, 0x800
    li x11, 0xb48faf357904a93c
    li x12, 0xd543ce86cc21a79a
    li x13, 0xb644d10e860cb62f
    li x14, 0xa98f873ccdb2980e
    li x15, 0x0d4030f6ba627d9a
    li x8, 0x099f59ab97493a89
    fmv.d.x f16, x8
    li x8, 0x4433901561c44feb
    fmv.d.x f17, x8
    li x8, 0xadf33f0c027d3e26
    fmv.d.x f18, x8
    li x8, 0x7ff9c2b3b6c9bcb3
    fmv.d.x f19, x8
    li x8, 0x37858a6f5d6b9024
    fmv.d.x f20, x8
    li x8, 0x7ff0000000000001
    fmv.d.x f21, x8
    li x8, 0xa0f21d663717babd
    fmv.d.x f22, x8
    li x8, 0x1377c689f5b09e42
    fmv.d.x f23, x8
    vsetvli x9, x0, e64, m1
    li x8, 0x5555555555555555
    vmv.v.x v16, x8
    li x8, 0x21d476b61b5ac963
    vmv.v.x v17, x8
    li x8, 0x0f0f0f0f0f0f0f0f
    vmv.v.x v18, x8
    li x8, 0xffffffffffffffff
    vmv.v.x v19, x8
    li x8, 0xffffffffffffffff
    vmv.v.x v20, x8
    li x8, 0x0f0f0f0f0f0f0f0f
    vmv.v.x v21, x8
    li x8, 0x0
    vmv.v.x v22, x8
    li x8, 0x0
    vmv.v.x v23, x8
    li x9, 0x8fffffb8
    li x10, 0x10000
    sd x10, 0(x9)
    li x10, 0x741d51bb851a03db
    sd x10, 8(x9)
    li x10, 0x0
    sd x10, 16(x9)
    li x10, 0xfcfe328c54590753
    sd x10, 24(x9)
    li x10, 0x2
    sd x10, 32(x9)
    li x10, 0x565398a28c4570dc
    sd x10, 40(x9)
    li x10, 0xff
    sd x10, 48(x9)
    li x10, 0x638a4d4a34d03fc9
    sd x10, 56(x9)
    li x8, 0
    li x9, 0
    li x10, 0
    li x11, 0
    li x12, 0
    li x2, 2415919038
    c.sdsp x11, 16(sp)
    vsetivli x8, 10, e8, m8
    li x10, 2415919032
    vl8re64.v v8, (x10)
    and x13, x15, x8
    li x10, 2415919060
    lr.d.aqrl x9, 0(x10)
    vredmin.vs v16, v8, v24, v0.t
    li x11, 2415919032
    vle16.v v16, (x11)

exit:
    li      gp, 1
    la      t1, tohost
    sd      gp, 0(t1)

    la      t1, skiptrap_store_buf
    sd      gp, 0(t1)

    li      t0, 0
    .insn   i 0x6b, 0, x0, t0, 0
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
    beq     t1, t3, fetch_inst
    li      t3, 12
    beq     t1, t3, fetch_inst

fetch_inst:
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

    .section .data
    .align  3
skiptrap_store_buf:
    .dword  0
