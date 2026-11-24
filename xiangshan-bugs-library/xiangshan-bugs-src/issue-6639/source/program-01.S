fld       ft1, 0(a2)
vl1re64.v v2, (a1)
vsetivli x0, 2, e64, m1, tu, mu
vfmv.s.f v2, ft1
