.section .text
.globl _start
_start:
  li      t0, 0x600
  csrs    mstatus, t0
  vsetivli zero, 2, e16, mf4, tu, mu
  vmv.v.i v0, -1
  la      a0, w
  vlseg6e32ff.v v7, (a0), v0.t
1: j 1b
  .section .data
w:
  .word 0xa5a50000, 0xa5a50001, 0, 0, 0, 0, 0xa5a50006
