  li      t0, 0x600
  csrs    mstatus, t0             # mstatus.VS = dirty
  li      t0, 4
  csrw    vstart, t0
  li      a1, 0
  vsetvli a1, a1, e8, m1, tu, mu  # resets vstart to 0
  li      t0, 0xAA
  vmv.v.x v8, t0                  # must execute (vstart = 0)
