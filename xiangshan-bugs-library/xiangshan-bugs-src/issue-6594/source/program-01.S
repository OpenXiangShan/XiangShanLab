  li      t3, -1
  vsetvli  x0, t3, e8, m8, ta, ma    # (1) any LMUL = 8 configuration
  vsetivli x0, 0,  e8, m1, ta, ma    # (2) AVL = 0 as an immediate, LMUL back to 1
  csrr    t1, vtype
