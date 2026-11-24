# vsha2ch.vv v4, v2, v1, then vmv.x.s reads v4.
# Loaded at 0x80000000, where the emulator's flash jumps after its own six instructions. The word
# 0x0005006b is the emulator's trap instruction; the pc it ends at says which end was reached.
    .section .text
    .globl _start
_start:
    la      t0, on_trap
    csrw    mtvec, t0
    li      t0, 0x600               # mstatus.VS = 3: the vector unit is on
    csrs    mstatus, t0
    vsetivli zero, 4, e32, m1, ta, ma
    vmv.v.i v1, 1
    vmv.v.i v2, 2
    vmv.v.i v4, 3
subject:
    vsha2ch.vv v4, v2, v1
    vmv.x.s a1, v4                  # reads the result
    li      a0, 0
finish:
    .word   0x0005006b              # the end when no exception is taken
on_trap:
    csrr    a1, mcause              # any exception comes here: a1 = mcause
    li      a0, 1
    .word   0x0005006b              # the end when an exception is taken
