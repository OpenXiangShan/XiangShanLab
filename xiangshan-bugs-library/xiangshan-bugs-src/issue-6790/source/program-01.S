.section .text
.globl _start
_start:
  li      t0, 0x6600
  csrw    mstatus, t0
  li      t0, 0x3c00
  .insn r 0x53, 0, 0x7a, ft11, t0, zero   # fmv.h.x ft11, t0
  vsetvli tp, zero, e16, m2, tu, mu
  la      a0, neq14
  vle16.v v30, (a0)
  vmfne.vf v26, v30, ft11
1: j 1b
  .section .data
neq14:
  .word 0x42004000, 0xbc003800
