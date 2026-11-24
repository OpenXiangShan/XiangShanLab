.option norvc
.section .text.init
.globl _start
_start:
    li t0, -1
    csrw pmpaddr0, t0
    li t0, 0x1f
    csrw pmpcfg0, t0          # temporarily allow access
    csrw medeleg, x0
    la t0, trap_handler
    csrw mtvec, t0
    li t0, 0x20004000
    csrw pmpaddr0, t0         # TOR upper = 0x80010000
    li t0, 0xe3ab             # pmp0: L=1, A=TOR, R=1, W=1, X=0
    csrw pmpcfg0, t0          # lock no-execute over the executing code
    nop                       # fetch here should raise instruction access fault
    nop
    .4byte 0x0000006b
1:  j 1b
trap_handler:
    .4byte 0x0000006b
2:  j 2b
