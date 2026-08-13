        vsetvli t0, x0, e8, m4       # SEW=8, LMUL=4  -> vl = VLMAX = 64
        auipc   t1, 0x5e
        addi    t1, t1, -1600        # t1 = 0x60020  (VA whose page is NOT mapped)
        vle8.v  v16, (t1)            # unit-stride load, 64 bytes 0x60020..0x6005f
                                     #   -> load page fault on element 0
