.section .text.init
.globl _start
_start:
  la t0, trap_handler
  csrw mtvec, t0

  # Enable vector state so vector CSRs/instructions are legal.
  li t6, 0x600
  csrs mstatus, t6

  # Minimal reproducer:
  # Spike updates vl to 16 here, while XiangShan currently exposes 0
  # to the immediately following csrr vl.
  li a0, 16
  vsetvli t1, a0, e8, m1, ta, ma
  csrr t2, vl

  li t3, 16
  bne t2, t3, bug

pass:
  j pass

bug:
  j bug

trap_handler:
  j bug
