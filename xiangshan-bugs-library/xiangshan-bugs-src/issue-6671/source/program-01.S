  li      t0, 0x1808              # mnstatus: MNPP = M, NMIE = 1
  csrw    mnstatus, t0            # 0x744
  csrr    a0, mnstatus            # reads 0x1808
  la      t0, back
  csrw    mnepc, t0               # 0x741
  .word   0x70200073              # mnret: must continue at `back`, in M-mode
  j       trapped
back:
  ...
