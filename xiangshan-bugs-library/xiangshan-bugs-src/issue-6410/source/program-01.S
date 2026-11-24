li      a1, 32
vsetvli a0, a1, e32, m2, ta, ma
vmv.v.v v3, v0        # vd = 3 is not a multiple of EMUL=2 -> reserved encoding
