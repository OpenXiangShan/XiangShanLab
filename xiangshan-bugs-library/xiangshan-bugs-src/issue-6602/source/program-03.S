.option norvc
.section .text.init
.globl _start
_start:
    csrr t0, mstatus
    li   t1, 0x600            # mstatus.VS = Dirty
    or   t0, t0, t1
    csrw mstatus, t0
    csrw medeleg, x0
    la   t0, trap_handler
    csrw mtvec, t0
    vsetivli zero, 0, e64, m1, tu, ma   # vl = 0
    li   t1, 0x80001000
    li   t0, 0x400            # sstatus.VS = Clean (10)
    csrw sstatus, t0
    vle64.v v0, (t1)          # <-- vl=0 load: writes no vector state
    csrr t0, sstatus
    li   t1, 0x600
    and  t0, t0, t1
    li   t1, 0x400            # expected: still Clean
    bne  t0, t1, 4f           # Dirty => bug reproduced
    .4byte 0x0000006b         # exit A (VS stayed Clean)  <- NOT taken when bug present
4:  .4byte 0x0000006b         # exit B (VS became Dirty)
trap_handler:
    csrr t0, mcause
    li   t1, 2
    bne  t0, t1, 5f
    .4byte 0x0000006b         # exit C (illegal-instruction trap taken)
5:  j 5b
