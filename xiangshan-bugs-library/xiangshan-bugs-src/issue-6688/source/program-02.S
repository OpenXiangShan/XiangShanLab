    la      t5, test_memory   # zero initialized region inside the loaded image (t5 = 0x80000ab0)
    fld     f0, 0(t5)            # pc 0x80000078: identical load, retires correctly
    fld     f1, 8(t5)
    fld     f2, 16(t5)
    fmaxm.d f2, f0, f3
    li      s1, 28        
1:
    la      t5, test_memory
    fld     f0, 0(t5)            # pc 0x80000092: <- difftest mismatch here
    ...
    bnez s1, 1b
