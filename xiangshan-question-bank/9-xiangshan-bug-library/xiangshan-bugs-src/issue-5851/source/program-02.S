  # s2 = pagebuf + 4080
  li    t1, 0x8877665544332211
  sd    t1, 12(s2)        # cross-page store at offset 4092 (bytes 4092..4099)
  lbu   a0, 12(s2)        # alias load from same address
  li    a1, 0x11
  bne   a0, a1, fail      # expects 0x11, gets stale data
