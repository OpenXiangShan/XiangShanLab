  li   a4, 0x80024000      # page boundary; the line at a4 is cached
  li   a3, 0x80420000      # lines that were never touched
  ld   a1, 0(a4)
  ld   t3, 8(a3)           # cold miss: delays the store's commit and the load's address
  sd   s5, -1(a4)          # bytes 0x80023fff..0x80024006, crosses the page
  addi t3, t3, 1           # K times
  andi t3, t3, 0
  add  a5, a4, t3
  lh   a1, 0(a5)           # bytes 0x80024000..0x80024001 of the store
