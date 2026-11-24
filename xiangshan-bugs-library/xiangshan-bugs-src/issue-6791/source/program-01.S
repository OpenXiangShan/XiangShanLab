.section .text
.globl _start
_start:
  li       t0, 0x600
  csrs     mstatus, t0
  vsetivli zero, 4, e64, m1, ta, ma
  vmv.v.i  v1, 7
  vadd.vi  v2, v1, 1
  vmv.x.s  a0, v2
1: j 1b
