  la      t0, mh
  csrw    mtvec, t0
  li      t1, 0x0123456789abcdef
  .word   0x31b31393              # aes64ks1i t2, t1, 0xB  (reserved rnum)
  j       test_exit
mh:
  csrr    a0, mcause              # 2 expected
  csrr    a1, mtval
  j       test_exit
