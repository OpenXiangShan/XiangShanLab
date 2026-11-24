  li      s0, 0x80800040
  li      t0, 0x1122334455667788
  sd      t0, 0(s0)
  prefetch.w 0(s0)
  prefetch.r 0(s0)
  ld      s1, 0(s0)
  ...
  li      s0, 0x80800000
  prefetch.i 0(s0)
  prefetch.r 0(s0)
  prefetch.w 0(s0)
  prefetch.i 64(s0)
  prefetch.r 64(s0)
  prefetch.w 64(s0)
  ...
  li      s0, 0x90000000
  prefetch.i 0(s0)
  prefetch.r 0(s0)
  prefetch.w 0(s0)
  prefetch.i 128(s0)
  prefetch.r 128(s0)
  prefetch.w 128(s0)
  nop
  j       test_exit
