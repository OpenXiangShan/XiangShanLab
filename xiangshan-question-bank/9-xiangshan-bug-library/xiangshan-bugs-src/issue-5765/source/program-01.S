.section .text.init
.globl _start
_start:
  la t0, trap_handler
  csrw mtvec, t0

  li t6, 0x600
  csrs mstatus, t6

  # Minimal deterministic vlm.v -> v0 test.
  # First zero the full v0 register with vl=16, then switch to vl=10 and
  # execute vlm.v. This avoids tail-undisturbed garbage in the old v0 state.
  li a0, 16
  vsetvli t0, a0, e8, m1, tu, mu
  vmv.v.i v0, 0

  li a0, 10
  vsetvli t0, a0, e8, m1, tu, mu

  la a1, mask_data
  vlm.v v0, (a1)

  la a2, out_data
  vsm.v v0, (a2)

  la t1, out_data
  la t2, expected_data
  li t3, 2
1:
  lbu t4, 0(t1)
  lbu t5, 0(t2)
  bne t4, t5, bug
  addi t1, t1, 1
  addi t2, t2, 1
  addi t3, t3, -1
  bnez t3, 1b

pass:
  la t0, result_code
  li t1, 1
  sw t1, 0(t0)
  j done

bug:
  la t0, result_code
  li t1, 2
  sw t1, 0(t0)
  j done

trap_handler:
  la t0, result_code
  li t1, 0xdead
  sw t1, 0(t0)
  csrr t2, mepc
  addi t2, t2, 4
  csrw mepc, t2
  mret

done:
  fence
  wfi

.section .data
.align 3
result_code:
  .word 0
mask_data:
  .byte 0x80, 0x03
expected_data:
  .byte 0x80, 0x03
out_data:
  .space 4
