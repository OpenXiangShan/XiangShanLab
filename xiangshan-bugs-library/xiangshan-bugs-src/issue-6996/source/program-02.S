  sw    t1, 0x0c(s1)          # older store, synonym VA (t1 = 0x33fbb8ce)
  sh    x0, 0x22(s0)          # store to L, primary VA
  cbo.zero (s0)               # younger: zero L, primary VA
  fence
  # every byte of L must be 0
