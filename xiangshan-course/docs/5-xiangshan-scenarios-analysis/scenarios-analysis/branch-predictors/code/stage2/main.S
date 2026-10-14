    .section .text
    .globl _start
    .globl loop, branch_even, even_path
    .option norvc
    .option norelax
    .balign 64
_start:
    addi t0, zero, 0          # i = 0
    addi t3, zero, 0          # Count odd iterations

    .balign 64
loop:
    andi t2, t0, 1
branch_even:
    beq  t2, zero, even_path  # T, N, T, N, ...
    addi t3, t3, 1
even_path:
    addi t0, t0, 1
    jal  zero, loop          # Unconditional return; stop via emu -C

    # Padding for speculative fetch beyond the loop.
    .rept 64
    nop
    .endr
