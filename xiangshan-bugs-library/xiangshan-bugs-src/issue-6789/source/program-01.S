.section .text
.globl _start
_start:
  li      t0, 0x600
  csrs    mstatus, t0
  vsetivli zero, 8, e16, m1, tu, mu
  la      a0, memx
  li      gp, 4
  vlsseg2e16.v v4, (a0), gp
1: j 1b
  .section .data
memx:
  .dword 0xab15000000000000, 0xab15000100010001
  .dword 0xab17000200020002, 0xab17000300030003
