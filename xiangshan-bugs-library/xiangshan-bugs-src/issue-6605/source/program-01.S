  # PMP entry 0: NAPOT, one 4 KiB block at 0x80600000, locked, no R/W/X
  li    t0, 0x201801ff          # (0x80600000 >> 2) | 0x1ff
  csrw  pmpaddr0, t0
  li    t0, 0x98                # L | NAPOT, no permissions
  csrw  pmpcfg0, t0

  li    t1, 0x80600000          # inside the denied block
  li    t2, 4
  vsetvli x0, t2, e32, m1, ta, ma
  li    t3, 4                   # stride = element width: the same bytes a
  vsse32.v v8, (t1), t3         # unit-stride store would touch
