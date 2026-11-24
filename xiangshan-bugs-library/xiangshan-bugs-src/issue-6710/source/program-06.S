# vsha2ch.vv v4, v2, v1 at SEW 32 in U mode, with the machine timer armed, with nothing reading v4 afterwards.
# Each end of the run is a trap word 0x0005006b at its own pc: finish, end_exception,
# end_interrupt_at_subject or end_interrupt_elsewhere.
    .section .text
    .globl _start
_start:
    la      t0, on_trap
    csrw    mtvec, t0
    li      t0, 0x600               # mstatus.VS = 3: the vector unit is on
    csrs    mstatus, t0
    li      t1, 0x3800bff8         # mtime
    ld      t2, 0(t1)
    addi    t2, t2, 100             # due in 100 ticks, about 10,000 cycles
    li      t1, 0x38004000         # mtimecmp
    sd      t2, 0(t1)
    li      t0, 0x80                # mie.MTIE
    csrs    mie, t0
    li      t0, -1                  # PMP entry 0 covers all memory, RWX, so U mode may run
    csrw    pmpaddr0, t0
    li      t0, 0x1f
    csrw    pmpcfg0, t0
    li      t0, 0x1800              # mstatus.MPP = U
    csrc    mstatus, t0
    la      t0, user
    csrw    mepc, t0
    mret
user:
    vsetivli zero, 4, e32, m1, ta, ma
    vmv.v.i v1, 1
    vmv.v.i v2, 2
    vmv.v.i v4, 3
subject:
    vsha2ch.vv v4, v2, v1
spin:
    j       spin                    # waits for the timer
finish:
    .word   0x0005006b              # the program's end
on_trap:
    csrr    a1, mcause
    bltz    a1, on_interrupt
    li      t0, 8                   # ecall from U mode
    beq     a1, t0, finish
    li      a0, 1
end_exception:
    .word   0x0005006b              # the end when an exception is taken
on_interrupt:
    csrr    t1, mepc
    la      t2, subject
    bne     t1, t2, interrupted_elsewhere
    li      a0, 2
end_interrupt_at_subject:
    .word   0x0005006b              # an interrupt taken with mepc at the subject
interrupted_elsewhere:
    li      a0, 3
end_interrupt_elsewhere:
    .word   0x0005006b              # an interrupt taken anywhere else
