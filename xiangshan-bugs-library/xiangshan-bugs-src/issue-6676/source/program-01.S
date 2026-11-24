loop:
  li      a0, 8
  vsetvli t1, a0, e32, m2, tu, mu
  csrr    t3, vl
  bne     t3, t1, bad           # vl must equal what vsetvli returned
  addi    s1, s1, -1
  bnez    s1, loop
