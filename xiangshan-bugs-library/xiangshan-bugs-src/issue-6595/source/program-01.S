  # PMP entry 0: NAPOT, one 4 KiB block at 0x80600000, locked, no R/W/X
  li    t0, 0x201801ff          # (0x80600000 >> 2) | 0x1ff
  csrw  pmpaddr0, t0
  li    t0, 0x98                # L | NAPOT | no permissions
  csrw  pmpcfg0, t0

  li    t1, 0x80600000
  cbo.clean (t1)                # must raise a store/AMO access fault
