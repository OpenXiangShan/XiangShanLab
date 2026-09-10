.section .text.init, "ax", @progbits
.globl _start
.type _start, @function
_start:
    li      t0, 0x8000000a00104a00
    csrw    mstatus, t0
    fsh     ft0, 9(sp)
1:
    j       1b
.size _start, .-_start
