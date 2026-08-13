.section .text.init
.globl _start
_start:
  la t0, trap_handler
  csrw mtvec, t0

  li t6, 0x600
  csrs mstatus, t6

  # Same PMP shape as the double-fixvl test, but only one vle8ff.
  la t0, deny_start
  srli t0, t0, 2
  csrw pmpaddr0, t0
  la t1, deny_end
  srli t1, t1, 2
  csrw pmpaddr1, t1
  li t2, 0x8800
  csrw pmpcfg0, t2

  li a0, 8
  vsetvli t3, a0, e8, m1, tu, mu
  vmv.v.i v8, 0

  la a1, deny_start
  addi a1, a1, -7

  vle8ff.v v8, (a1)
  csrr t4, vl

  # Record the observed vl so we can compare against the double-fixvl case.
  la t0, observed_vl
  sw t4, 0(t0)

pass:
  li gp, 1
  ebreak

trap_handler:
  csrr t0, mcause
  li t1, 3
  bne t0, t1, unexpected_trap

  la t2, result_code
  sw gp, 0(t2)
  la t3, done
  csrw mepc, t3
  mret

unexpected_trap:
  la t2, result_code
  li t3, 0xdead
  sw t3, 0(t2)
  la t4, done
  csrw mepc, t4
  mret

done:
  fence
1:
  j 1b

.section .data
.align 3
result_code:
  .word 0
observed_vl:
  .word 0
pre_deny:
  .byte 0x10, 0x11, 0x12, 0x13, 0x14, 0x15, 0x16, 0x17
deny_start:
  .byte 0xde, 0xad, 0xbe, 0xef, 0xfa, 0xce, 0xba, 0xbe
deny_end:
