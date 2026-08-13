li t6, 0x600
csrs mstatus, t6
li a0, 16
vsetvli t1, a0, e8, m1, ta, ma
csrr t2, vl
