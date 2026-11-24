  li s0, 0x40003000               # v
  li s1, 0x40008000               # v': another 4 KiB page of the same 2 MiB region
  ld a0, 0(s0)                    # walk under R1: the load DTLB caches the global 4 KiB entry of v
  li t0, 0x800010000008000B       # satp R2: Sv39, ASID 1 (same ASID), root PPN 0x8000B
  csrw satp, t0
  li t3, 1
  sfence.vma x0, t3               # flush ASID 1, keep global entries
  ld a1, 0(s1)                    # walk under R2 for v': the DTLB caches the 2 MiB entry, which also covers v
  and t4, a1, x0
  add s2, s0, t4                  # v again, address dependent on the v' load
  ld a2, 0(s2)                    # v now hits both entries
