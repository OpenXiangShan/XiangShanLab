vsetvli x8, x0, e16, m4
vmv.v.i v4, 1
vmv.v.i v24, 0
vmv.v.i v0, -1
vmerge.vvm v0, v24, v4, v0
