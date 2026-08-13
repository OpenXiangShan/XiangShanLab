    .section .text
    .globl  _start

_start:
    la      t0, trap_handler
    csrw    mtvec, t0

    csrr    t0, mstatus
    li      t1, 0x00003600       # set FS+VS to Dirty (0b11) so FP/RVV regs are usable
    or      t0, t0, t1
    csrw    mstatus, t0
    csrw    fcsr, x0             # clear floating-point status

    j       user_code

user_code:
    li x9, 0x8fffffb8
    li x10, 0x7f
    sd x10, 0(x9)
    vsetivli x8, 27, e16, m4
    li x8, 2415919032
    vmv.v.i v24, 0
    vloxei32.v v16, (x8), v24
    vmsbf.m v0, v16
    li x8, 2415919032
    vl1re64.v v18, (x8)
    li x11, 2415919032
    vlseg4e8.v v24, (x11), v0.t
    j exit

exit:
    li      t0, 1                # report success
    la      t1, tohost
    sd      t0, 0(t1)
1:
    j       1b

    .align  2
trap_handler:
    csrr    t0, mepc             # faulting PC
    csrr    t1, mcause           # trap cause
    csrr    t4, mtval

    slli    t5, t1, 1            # strip interrupt bit
    srli    t1, t5, 1

    li      t2, 2                # default length assumes compressed
    li      t3, 2                # illegal instruction -> mtval holds encoding
    beq     t1, t3, use_mtval
    li      t3, 1                # instruction access fault
    beq     t1, t3, update_mepc
    li      t3, 12               # instruction page fault
    beq     t1, t3, update_mepc

    lhu     t4, 0(t0)
    j       decode_length

use_mtval:
    j       decode_length

decode_length:
    andi    t4, t4, 3
    li      t3, 3
    bne     t4, t3, compressed_len
    li      t2, 4                # standard 32-bit instruction
    j       update_mepc

compressed_len:
    li      t2, 2                # compressed instruction

update_mepc:
    add     t0, t0, t2           # skip offending instruction
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
