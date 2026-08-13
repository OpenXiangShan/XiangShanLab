.equ CROSS_VA, 0x0000000040000ffe

li   t0, CROSS_VA
csrw mepc, t0
mret

/* 32-bit instruction starts at 0x40000ffe and crosses into the next page. */
cross_page_insn:
  .4byte 0x00000013
