vsetvli x8, x0, e32, mf2
li x15, 2415919032
vmv.v.i v19, 7
vloxseg5ei64.v v20, (x15), v19
