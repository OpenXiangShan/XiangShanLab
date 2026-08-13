li x9, 0x8fffffb8
li x10, 0x7f
sd x10, 0(x9)

vsetivli x8, 27, e16, m4
li x8, 2415919032
vmv.v.i v24, 0
vloxei32.v v16, (x8), v24
vmsbf.m v0, v16
li x8, 2415919032
vl1re64.v v18, (x8)
li x11, 2415919032
vlseg4e8.v v24, (x11), v0.t
