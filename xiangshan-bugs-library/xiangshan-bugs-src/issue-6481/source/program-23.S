    .option norvc
    .option norelax

    .section .text.entry, "ax", @progbits
    .globl _start
_start:
    la      t0, unexpected_trap
    csrw    mtvec, t0
    li      s0, 16

subject_loop:
    jal     ra, subject_function
expected_ras_return:
    j       fail

    .section .text.subject, "ax", @progbits
    .globl subject_ret
subject_ret:
    jalr    x0, 0(ra)

    .section .text.unused, "ax", @progbits
    .globl unused_observer_pc
unused_observer_pc:
    nop

    .section .text.rest, "ax", @progbits
subject_function:
    la      ra, subject_landing
    j       subject_ret

    .globl subject_landing
subject_landing:
    addi    s0, s0, -1
    bnez    s0, subject_loop

pass:
    li      a0, 0
    .word   0x0000006b

unexpected_trap:
fail:
    li      a0, 1
    .word   0x0000006b

    .section .tohost, "aw", @progbits
    .p2align 6
    .globl tohost
tohost:
    .dword 0
    .globl fromhost
fromhost:
    .dword 0
