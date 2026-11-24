        li      t0, MSTATUS_VS
        csrs    mstatus, t0             # enable the vector unit
        vsetvli t0, x0, e64, m8, ta, ma
        vmv.v.i v0, 0                   # zero v0..v31
        vmv.v.i v8, 0
        vmv.v.i v16, 0
        vmv.v.i v24, 0
        csrwi   vstart, 0
        li      t0, 40
1:      addi    t0, t0, -1              # ~120 scalar instructions of spacing
        bnez    t0, 1b
        vsetvli t0, x0, e8, m4, tu, mu  # LMUL = 4
        la      t1, buf
        vsseg2e8.v v0, (t1)             # 2-field segmented store, 8 registers of data
        la      t2, buf2
        amomin.d t3, t2, (t2)           # any AMO right after -> VirtualStoreQueue assertion
        RVTEST_PASS
