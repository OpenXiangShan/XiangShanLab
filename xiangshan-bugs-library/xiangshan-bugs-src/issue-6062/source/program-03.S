  .option push
  .option norelax

  .equ CAUSE_STORE_ACCESS_FAULT, 7
  .equ MSTATUS_MPP, 0x1800
  .equ MSTATUS_MPP_S, 0x0800
  .equ MENVCFG, 0x30a
  .equ PMP_NAPOT, 0x18
  .equ PMP_RWX_NAPOT, 0x1f

  .macro XS_EXIT
    .word 0x0005006b
  .endm

  .section .text.init, "ax"
  .globl _start
_start:
  la t0, trap_entry
  csrw mtvec, t0
  csrw medeleg, zero
  csrw mideleg, zero
  csrw satp, zero
  sfence.vma
  li t0, 0xf0
  csrw MENVCFG, t0

  la t0, target_data
  srli t0, t0, 2
  li t1, 0x1ff
  or t0, t0, t1
  csrw pmpaddr0, t0
  li t0, -1
  csrw pmpaddr1, t0
  li t0, (PMP_RWX_NAPOT << 8) | PMP_NAPOT
  csrw pmpcfg0, t0
  fence

  csrr t0, mstatus
  li t1, ~MSTATUS_MPP
  and t0, t0, t1
  li t1, MSTATUS_MPP_S
  or t0, t0, t1
  csrw mstatus, t0
  la t0, smode_probe
  csrw mepc, t0
  mret

smode_probe:
  la t0, target_data
  .word 0x0012a00f       # cbo.clean 0(t0)
  li a0, 80              # should not reach here
  XS_EXIT

trap_entry:
  csrr t5, mcause
  li t0, CAUSE_STORE_ACCESS_FAULT
  beq t5, t0, pass
  addi a0, t5, 1
  XS_EXIT

pass:
  li a0, 0
  XS_EXIT

  .section .data, "aw"
  .align 12
target_data:
  .dword 0x1122334455667788
  .zero 128

  .option pop
