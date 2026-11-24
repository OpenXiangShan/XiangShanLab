main:
  csrwi vcsr, 4
  csrwi vstart, 0
  vsetivli zero, 0, e64, m1, tu, ma

  fmv.x.d s7, fa0
  csrrw t2, sstatus, s7
  vsseg4e32.v v13, (t6), v0.t
