.section .text
.globl _start
_start:
  li      t0, 0x6600
  csrw    mstatus, t0
  vsetivli zero, 8, e16, m1, tu, mu
  la      a0, imgA16
  vle16.v v4, (a0)
  la      a0, imgB16
  vle16.v v6, (a0)
  vsetivli zero, 6, e16, m1, tu, mu
  vfwsub.vv v10, v4, v6
1: j 1b
  .section .data
imgA16: .word 0x40003c00, 0xbc003800, 0x7bffc000, 0x7c000001
imgB16: .word 0x38003800, 0x3c003400, 0x00003c00, 0x00000000
