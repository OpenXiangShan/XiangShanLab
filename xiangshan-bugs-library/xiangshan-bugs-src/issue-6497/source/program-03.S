csrw  mip, x0              # LCOFIP := 0
li    t0, 4
csrw  mhpmevent11, t0      # OF := 0, EVENT0 = 4, all xINH = 0
li    t1, -43
csrw  mhpmcounter11, t1    # wraps after 43 events
li    a2, 2                # SSIP
csrc  mip, a2              # RMW, written bit13 = value read (0)
csrc  mip, a2
csrc  mip, a2
csrc  mip, a2
csrr  t2, mhpmevent11      # bit 63 (OF)   == 1
csrr  t3, mip              # bit 13 (LCOFIP) == 0   <- bug; stays 0 forever
