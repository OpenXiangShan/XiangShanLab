# Reduced reproducer for XiangShan illegal-instruction mtval bug.
#
# Bug: when an older CSR illegal instruction (csrrs frm) traps,
# mtval is incorrectly written with the encoding of a younger
# FP instruction (fcvt.l.s at PC+8) instead of the faulting instruction.
#
# Expected mtval: 0x00000000002026f3
# Observed mtval: 0x00000000c02cf5d3 (= encoding of fcvt.l.s at PC+8)
#
# Suspected cause: TrapInstMod holds a younger decode-side illegal
# instruction, which is consumed by the older CSR trap entry
# instead of the actual ROB trap instruction.

.globl _start
.section .text

_start:
  li x13, 0x800000000084112d
  csrw 0x301, x13                # MISA

  la x13, other_exp
  csrw 0x305, x13                # MTVEC: M-mode trap handler

  la x13, main
  csrw 0x341, x13                # MEPC: start at main via mret

  li x26, 0x000000a900362240
  csrw 0x300, x26                # MSTATUS (FS=Off, causes FP CSR to be illegal)

  li x16, -0x1
  csrw 0x3b0, x16                # PMPADDR0
  li x16, 0x1f
  csrw 0x3a0, x16                # PMPCFG0: RWX NAPOT

  mret

# Trap handler at 0x80001000. Skips the faulting instruction (mepc += 4).
.align 12
other_exp:
.option norvc
  csrr x13, 0x341
  addi x13, x13, 4
  csrw 0x341, x13
  mret
.option rvc

# Core instruction window at 0x80001010.
#
#  0x80001010: fmv.x.d  s2, fs7          first illegal trap
#  0x80001014: rem      s7, s2, t1
#  0x80001018: subw     s1, t1, t4
#  0x8000101c: csrrs    a3, frm, zero    <-- FAULTING INSTRUCTION (mepc, mcause=2)
#  0x80001020: rolw     t2, s1, t2
#  0x80001024: fcvt.l.s a1, fs9, dyn     <-- encoding WRONGLY written to mtval
#  0x80001028: csrrs    s3, frm, zero
#  0x8000102c: beq      s11, zero, .+28
#
.align 2
.option norvc
main:
  fmv.x.d s2, fs7
  rem s7, s2, t1
  subw s1, t1, t4
  csrrs a3, frm, zero             # faulting instruction: 0x002026f3
  rolw t2, s1, t2
  fcvt.l.s a1, fs9, dyn           # PC+8 canary: 0xc02cf5d3, WRONG mtval
  csrrs s3, frm, zero
  beq s11, zero, . + 28
.option rvc

write_tohost:
  la t1, tohost
  li t2, 0x1
  sw t2, 0(t1)
_exit:
  j _exit

.section .data
.align 6
.global tohost
tohost:
.dword 0x0
.align 6
.global fromhost
fromhost:
.dword 0x0
