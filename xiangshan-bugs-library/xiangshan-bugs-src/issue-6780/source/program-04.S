  .equ CAUSE_ILLEGAL_INSTRUCTION, 2

  /* XiangShan testharness custom trap: rs1 carries the result code. */
  .macro XSTRAP result_reg
    .insn r 0x6b, 0, 0, x0, \result_reg, x0
  .endm

  .option norvc
  .section .text.init,"ax",@progbits
  .align 3
  .globl _start

_start:
  la      t0, trap_handler
  csrw    mtvec, t0
  csrw    medeleg, zero
  csrw    mideleg, zero
  csrw    mie, zero

  /* Enable vector architectural state. */
  li      t0, (3 << 9)
  csrs    mstatus, t0

  /* vl=0 prevents memory accesses. Instruction legality is independent of vl. */
  vsetivli zero, 0, e8, m1, ta, ma
  la      a0, test_data

  .globl faulting_instruction
faulting_instruction:
  /* nf=1 selects two fields: v31 and nonexistent v32. This is reserved. */
  vlseg2e8.v v31, (a0)

  /* Failure 1: the processor accepted the reserved register group. */
  li      a0, 1
  j       finish

trap_handler:
  csrr    t0, mcause
  li      t1, CAUSE_ILLEGAL_INSTRUCTION
  bne     t0, t1, wrong_cause

  csrr    t0, mepc
  la      t1, faulting_instruction
  bne     t0, t1, wrong_epc

  li      a0, 0
  j       finish

wrong_cause:
  li      a0, 2
  j       finish

wrong_epc:
  li      a0, 3

finish:
  /* riscv-tests result protocol for Spike, followed by XiangShan's trap. */
  slli    t0, a0, 1
  ori     t0, t0, 1
  la      t1, tohost
  sd      t0, 0(t1)
  fence   rw, rw
  bnez    a0, test_failed
  XSTRAP  a0
1:
  j       1b
test_failed:
  j       test_failed

  .section .data,"aw",@progbits
  .align 6
test_data:
  .zero 64

  .section .tohost,"aw",@progbits
  .align 6
  .globl tohost
tohost:
  .dword 0
  .align 6
  .globl fromhost
fromhost:
  .dword 0
