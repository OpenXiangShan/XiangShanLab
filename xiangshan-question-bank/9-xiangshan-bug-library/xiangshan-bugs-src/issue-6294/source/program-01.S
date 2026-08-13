        la    x9, d_1_0            # x9 = &d_1_0
        addi  x9, x9, -24          # x9 = 0x20008  (address with no store permission)
        amoxor.w x9, x13, (x9)     # -> store/AMO page fault (scause=0x0f, stval=0x20008)
