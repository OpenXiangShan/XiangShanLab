/*
 * Software-prefetch encodings used by XiangShan DecodeUnit:
 *   opcode=0010011, funct3=110, rd=0, rs2=1 (prefetch.r).
 * The immediate is an S-type byte offset (the low five bits are zero because
 * rd must be x0).  The constants below therefore encode 0/64/128/192/256/320
 * byte offsets with a1 (x11) as the prefetch base.
 */
.option norvc
.section .text
.align 2

.globl pf_priority_burst
.type pf_priority_burst, @function
/*
 * a0 = demand stream, a1 = prefetch stream, a2 = number of six-line bursts
 * All demand loads precede all prefetch.r instructions in each burst.  This
 * makes the first group older in the ROB while both groups contend for L1D
 * LoadPipe/MSHR resources.
 */
pf_priority_burst:
  li      a3, 0
.Lburst:
  ld      t0, 0(a0)
  ld      t1, 64(a0)
  ld      t2, 128(a0)
  ld      t3, 192(a0)
  ld      t4, 256(a0)
  ld      t5, 320(a0)

  .word   0x0015e013       /* prefetch.r 0(a1)   */
  .word   0x0415e013       /* prefetch.r 64(a1)  */
  .word   0x0815e013       /* prefetch.r 128(a1) */
  .word   0x0c15e013       /* prefetch.r 192(a1) */
  .word   0x1015e013       /* prefetch.r 256(a1) */
  .word   0x1415e013       /* prefetch.r 320(a1) */

  add     a3, a3, t0
  add     a3, a3, t1
  add     a3, a3, t2
  add     a3, a3, t3
  add     a3, a3, t4
  add     a3, a3, t5
  addi    a0, a0, 384
  addi    a1, a1, 384
  addi    a2, a2, -1
  bnez    a2, .Lburst
  mv      a0, a3
  ret
.size pf_priority_burst, .-pf_priority_burst

.globl pf_priority_reverse
.type pf_priority_reverse, @function
/* Control: younger demand loads follow older software prefetch.r requests. */
pf_priority_reverse:
  li      a3, 0
.Lreverse:
  .word   0x0015e013       /* prefetch.r 0(a1)   */
  .word   0x0415e013       /* prefetch.r 64(a1)  */
  .word   0x0815e013       /* prefetch.r 128(a1) */
  .word   0x0c15e013       /* prefetch.r 192(a1) */
  .word   0x1015e013       /* prefetch.r 256(a1) */
  .word   0x1415e013       /* prefetch.r 320(a1) */

  ld      t0, 0(a0)
  ld      t1, 64(a0)
  ld      t2, 128(a0)
  ld      t3, 192(a0)
  ld      t4, 256(a0)
  ld      t5, 320(a0)
  add     a3, a3, t0
  add     a3, a3, t1
  add     a3, a3, t2
  add     a3, a3, t3
  add     a3, a3, t4
  add     a3, a3, t5
  addi    a0, a0, 384
  addi    a1, a1, 384
  addi    a2, a2, -1
  bnez    a2, .Lreverse
  mv      a0, a3
  ret
.size pf_priority_reverse, .-pf_priority_reverse

.globl pf_priority_pair
.type pf_priority_pair, @function
/* Repeated old demand -> younger prefetch.r pairs. */
pf_priority_pair:
  li      a3, 0
.Lpair:
  ld      t0, 0(a0)
  .word   0x0015e013       /* prefetch.r 0(a1) */
  add     a3, a3, t0
  addi    a0, a0, 64
  addi    a1, a1, 64
  addi    a2, a2, -1
  bnez    a2, .Lpair
  mv      a0, a3
  ret
.size pf_priority_pair, .-pf_priority_pair
