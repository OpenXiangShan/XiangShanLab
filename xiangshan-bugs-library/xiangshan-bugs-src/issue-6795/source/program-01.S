li    t0, 1 << 59
csrw  menvcfg, t0       # menvcfg.DTE = 1
csrw  henvcfg, t0       # henvcfg.DTE = 1
li    t0, 1 << 24
csrw  sstatus, t0       # sstatus.SDT = 1; SIE is cleared
csrw  vsstatus, t0      # vsstatus.SDT = 1
li    t0, 0
csrw  menvcfg, t0       # menvcfg.DTE = 0
# Test verifies sstatus.SDT, vsstatus.SDT and henvcfg.DTE read as zero.
li    t0, 0
csrw  menvcfg, t0       # redundant write present in the original ELF
csrsi sstatus, 2        # instruction at 0x80000218: request SIE = 1
csrr  t0, sstatus
# Test checks (sstatus & ((1 << 24) | 2)) == 2.
