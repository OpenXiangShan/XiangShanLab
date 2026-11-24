  li      t0, 0x6000
  csrs    mstatus, t0             # FS = Dirty
  li      t0, 0x20                # frm = RTZ, flags clear
  csrw    fcsr, t0
  li      t0, 0x0001              # smallest half subnormal
  fmv.h.x fa1, t0
  li      t0, 0x7bff              # largest finite half
  fmv.h.x fa2, t0
  fdiv.h  fa0, fa1, fa2           # underflows to +0 with UF|NX: fa0 = 0xffffffffffff0000
  fmv.x.d a0, fa0                 # must read 0xffffffffffff0000
