    .section .text
    .globl  _start

_start:
    la      t0, trap_handler
    csrw    mtvec, t0

    csrr    t0, mstatus
    li      t1, 0x00003600       # set FS+VS to Dirty (0b11) so FP/RVV regs are usable
    or      t0, t0, t1
    csrw    mstatus, t0
    csrw    fcsr, x0

    j       user_code

user_code:

    vsetivli x0, 4, e16, m1, ta, ma
    vmv.v.i v1, -1
    vsrl.vi v1, v1, 8
    vsetivli x0, 4, e8, m1, ta, ma
    li x10, 0xC8
    vsetvl x0, x0, x10
    vadd.vi v1, v1, 1
    vmv.x.s x20, v1
    csrr x21, vtype
    csrr x22, vl
    li t1, 0
    li t2, 0x100
    bne x20, t2, 1f
    li t2, 0xC8
    bne x21, t2, 2f
    li t2, 4
    bne x22, t2, 3f
    li x31, 0
    j 9f
    1:
    ori t1, t1, 1
    j 8f
    2:
    ori t1, t1, 2
    j 8f
    3:
    ori t1, t1, 4
    8:
    mv x31, t1
    9:
    nop


exit:
    li      t0, 1
    la      t1, skiptrap_store_buf
    sd      t0, 0(t1)

    # DiffTest STATE_GOODTRAP
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

    .section .data
    .align  3
skiptrap_store_buf:
    .dword  0

    .align  6
fuzz_mem_pool:
    .space 4096
