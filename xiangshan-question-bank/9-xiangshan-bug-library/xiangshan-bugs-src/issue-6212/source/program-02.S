.equ GPA_VS_ROOT,      0x40000000
.equ ACTUAL_PTE_GPA,   (GPA_VS_ROOT + 8)
.equ EXPECTED_MTVAL2,  (ACTUAL_PTE_GPA >> 2)

/* Prove the precise mtval2 value is representable, then enter VS translation. */
li   t0, EXPECTED_MTVAL2
csrw mtval2, t0
csrr t1, mtval2
bne  t1, t0, mtval2_warl_inconclusive

/* First VS fetch at 0x40000000 faults during root-PTE read at GPA 0x40000008. */
mret

trap_handler:
  csrr t3, mtval2
  li   t4, EXPECTED_MTVAL2
  bne  t3, t4, wrong_mtval2
