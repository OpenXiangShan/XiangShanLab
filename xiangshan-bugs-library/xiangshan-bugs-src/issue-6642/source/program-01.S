fld      ft1, 0(a2)  # 0x000000003f800000
vsetivli x0, 4, e32, m1, tu, mu
vfadd.vf v2, v3, ft1
