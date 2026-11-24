  li       t0, 0x600                 # mstatus.VS = 3
  csrw     mstatus, t0
  vsetivli x0, 0, e16, m1, tu, mu    # legal: vsew = e16, vlmul = m1
  csrr     a1, vtype
