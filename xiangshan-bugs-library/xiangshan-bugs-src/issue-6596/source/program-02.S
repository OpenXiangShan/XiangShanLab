  li      t3, 2
  vsetvli x0, t3, e32, m1, ta, ma   # SEW = 32, LMUL = 1, vl = 2
  li      t1, 0x90000000            # no memory behind this address
  .word   0x82036407                # vlseg5e32.v v8, (t1)
