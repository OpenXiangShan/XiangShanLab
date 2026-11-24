  li      t0, 0x6000
  csrs    mstatus, t0             # FS = Dirty
  csrw    fcsr, x0                # RNE, flags clear
  li      t1, 0xb4000000          # -2^-23 as a single
  fmv.w.x ft6, t1
  fcvt.h.s fa4, ft6               # -2^-23 = 2 x 2^-24: the half subnormal 0x8002, exactly
  fmv.x.h a0, fa4
  frflags a1
