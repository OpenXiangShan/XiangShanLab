li       a0, 32
vsetvli  t3, a0, e8, m1, tu, mu
li       a1, 0x40001000 - 15
vle8ff.v v8, (a1)
csrr     s3, vl
