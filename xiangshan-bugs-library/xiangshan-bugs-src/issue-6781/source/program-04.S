  .option norvc
  .section .text.init,"ax",@progbits
  .align 3
  .globl _start

  /* XiangShan testharness custom trap: rs1 carries the result code. */
  .macro XSTRAP result_reg
    .insn r 0x6b, 0, 0, x0, \result_reg, x0
  .endm

_start:
  la      t0, trap_handler
  csrw    mtvec, t0
  csrw    medeleg, zero
  csrw    mideleg, zero
  csrw    mie, zero

  /* Enable vector state in mstatus.VS. */
  li      t0, (3 << 9)
  csrs    mstatus, t0

  /* Data: SEW=8, LMUL=1. Index: EEW=64, hence index EMUL=8. */
  vsetivli zero, 0, e8, m1, ta, ma
  la      a0, test_data

  .globl tested_instruction
tested_instruction:
  /* Legal: index group v16-v23; two data fields v8-v9. */
  vluxseg2ei64.v v8, (a0), v16

  /* The legal instruction completed without an exception. */
  li      a0, 0
  j       finish

trap_handler:
  /* Any exception means that the legal instruction was rejected. */
  li      a0, 1

finish:
  /* riscv-tests/Spike tohost convention: odd value, status in upper bits. */
  slli    t0, a0, 1
  ori     t0, t0, 1
  la      t1, tohost
  sd      t0, 0(t1)
  fence   rw, rw

  /* Only a successful self-check may terminate as a good trap. */
  bnez    a0, test_failed
  XSTRAP  a0
test_failed:
1:
  j       1b

  .section .data,"aw",@progbits
  .align 6
test_data:
  .zero 64

  .section .tohost,"aw",@progbits
  .align 6
  .globl tohost
tohost: .dword 0
  .align 6
  .globl fromhost
fromhost: .dword 0
