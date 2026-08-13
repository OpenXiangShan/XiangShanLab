        vsetvli t0, x0, e32, m2      # 0x011072d7  (SEW=32, LMUL=2, vl=VLMAX=8 @ VLEN=128)
        lui     t1, 0x40             # t1 = 0x40000
        addi    t1, t1, 0x20         # t1 = 0x40020  (VA whose page is NOT mapped)
        vlseg4e32.v v24, (t1)        # 0x62036c07  unit-stride segment load, nf=4  -> HANGS here
