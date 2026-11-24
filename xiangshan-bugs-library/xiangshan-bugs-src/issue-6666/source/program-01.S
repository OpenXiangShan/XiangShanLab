  la      s0, buf                 # a 64-byte-aligned block
  li      t1, 0x1122334455667788
  sd      t1, 0(s0)
  sd      t1, 56(s0)
  li      t0, 0x30                # menvcfg: CBIE = 11
  csrw    menvcfg, t0
  li      t0, 0x10                # senvcfg: CBIE = 01 -> in U mode cbo.inval performs a flush
  csrw    senvcfg, t0
  ...                             # mret to U mode
  cbo.inval (s0)                  # must not change memory
  ld      a0, 0(s0)               # must read 0x1122334455667788
