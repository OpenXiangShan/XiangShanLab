li    t0, (1 << 24)
csrrs zero, mstatus, t0

li    t0, (1 << 1)
csrrs zero, sstatus, t0

csrr  t1, sstatus
csrr  t2, mstatus
