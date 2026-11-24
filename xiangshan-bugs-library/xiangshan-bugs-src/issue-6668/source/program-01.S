main:
  li      t0, 0x200          # mstatus.VS = Initial
  csrs    mstatus, t0
  li      a0, 1
  csrr    zero, vl           # 0xc2002073, csrrs x0, vl, x0
  li      a0, 2
  j       test_exit
