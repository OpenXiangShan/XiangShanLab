li  t0, 0x39000000     # between SYSCNT (0x38040000) and PLL (0x3a000000); nothing is mapped here
sd  zero, 0(t0)        # t41: must raise mcause 7
ld  t2, 0(t0)          # t42: must raise mcause 5
li  t1, 0xa5
sb  t1, 0(a3)          # reached when no trap was taken
