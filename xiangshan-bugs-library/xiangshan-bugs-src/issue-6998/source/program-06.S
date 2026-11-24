.option norvc
.section .text.init
.globl _start
_start:
  li   t0, 0x600
  csrw mstatus, t0
  vsetivli x0, 0, e16, m1, tu, mu
  csrr a1, vtype
  .word 0x0000006b        # difftest GOOD TRAP
