// PR #3399 minimal reproduction, constructed from the diff of
// aff5ef05de463d14bed375e76dda971b6b204ea8 (VFALU fflagsRedMask must come
// from the registered outVecCtrl, not from the ctrl at the FU input).
//
// vfredmax/vfredmin over [1.0, sNaN, sNaN, sNaN] with vl=1: only element 0 is
// active, so NV must not be raised for the tail sNaN lanes.  The neighbouring
// masked vfadd (non-fold ctrl at the FU input) makes the buggy RTL build the
// wrong all-ones fflagsRedMask and OR a folded-away lane's NV into the
// reduction's result.  Correct result: NV=0.  Buggy: NV=1 (fflags=0x10).
#include "common.inc"

.macro CHECK idx, red
  csrw fflags, zero
  \red v16, v8, v24
  vfadd.vv v12, v11, v10, v0.t
  csrr s6, fflags
  andi s7, s6, 0x1f
  bnez s7, 1f
  la a0, msg_pass
  call puts
  li a0, \idx
  call putdec
  j 2f
1:
  la a0, msg_bug
  call puts
  li a0, \idx
  call putdec
  la a0, msg_f
  call puts
  mv a0, s6
  call puthex
2:
.endm

.section .text.init,"ax",@progbits
.globl _start
_start:
  la t0, m_trap_default
  csrw mtvec, t0
  li t0, 0x6600
  csrs mstatus, t0

  la s2, nan4
  li a0, 4
  vsetvli t0, a0, e32, m1, ta, ma
  vle32.v v8, (s2)
  la s3, z4
  vle32.v v24, (s3)
  vle32.v v10, (s3)
  vle32.v v0, (s3)          // all lanes masked
  la s4, snan4
  vle32.v v11, (s4)
  li a0, 1
  vsetvli t0, a0, e32, m1, ta, ma

  CHECK 1, vfredmax.vs
  CHECK 2, vfredmin.vs

  la a0, msg_done
  call puts
  j halt

.section .data
.balign 64
z4:    .word 0,0,0,0
nan4:  .word 0x3F800000, 0x7F800001, 0x7F800001, 0x7F800001
snan4: .word 0x7F800001, 0x7F800001, 0x7F800001, 0x7F800001

.section .rodata
msg_pass: .asciz "PASS case"
msg_bug:  .asciz "BUG case"
msg_f:    .asciz " f="
msg_done: .asciz "DONE pr3399\n"
