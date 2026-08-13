.section .text
.globl _start

_start:
    la      t0, trap_handler
    csrw    mtvec, t0

    csrr    t0, mstatus
    li      t1, 0x00003600       # set FS+VS to Dirty so FP/RVV state is usable
    or      t0, t0, t1
    csrw    mstatus, t0
    csrw    fcsr, x0

    j       user_code

user_code:
    vsetivli x0, 2, e64, m1, ta, ma
    vmv.v.i  v9, 0

    vsetivli x0, 1, e32, m1, tu, mu
    la       x10, data_word
    vle32.v  v9, (x10)
    vmv.x.s  x15, v9

    li       x16, 0xffffffffe01d7b92
    li       gp, 1
    beq      x15, x16, exit
    li       gp, 2

exit:
    la      t1, tohost
    sd      gp, 0(t1)

    la      t1, skiptrap_store_buf
    sd      gp, 0(t1)

    li      t0, 0                # DiffTest STATE_GOODTRAP
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

    .align  2
data_word:
    .word   0xe01d7b92
