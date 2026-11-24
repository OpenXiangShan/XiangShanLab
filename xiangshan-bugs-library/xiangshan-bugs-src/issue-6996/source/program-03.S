  # x2 = 0x80080000, x9 = 0xfffffffffffffffe, low half of f25 = 0
  addi  x15, x2, 56
  amomax.d.aq x24, x27, (x15)
  li    x12, 0x2000
  add   x12, x12, x2
  sw    x9, -169(x12)         # older, synonym VA 0x80081f57 -> PA 0x8007ff57
  fsh   f25, -168(x2)         # younger, primary VA 0x8007ff58 -> PA 0x8007ff58
