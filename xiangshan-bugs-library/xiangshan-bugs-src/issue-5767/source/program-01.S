.section .text.init
.globl _start
_start:
  la t0, trap_handler
  csrw mtvec, t0

  li t6, 0x600
  csrs mstatus, t6

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
  vmv.v.i v16, 0
  vmv.v.i v17, 0

  # Same segment-ff access shape used in the mixed test.
  la a2, deny_start
  addi a2, a2, -12

  vlseg2e8ff.v v16, (a2)
  csrr t4, vl

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
  .byte 0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07
  .byte 0x08, 0x09, 0x0a, 0x0b, 0x0c, 0x0d, 0x0e, 0x0f
deny_start:
  .byte 0xde, 0xad, 0xbe, 0xef, 0xfa, 0xce, 0xba, 0xbe
deny_end:
