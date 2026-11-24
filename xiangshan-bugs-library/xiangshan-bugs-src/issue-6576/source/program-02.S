        li      t0, MSTATUS_VS
        csrs    mstatus, t0             # enable the vector unit
        li      t0, -1
        csrw    pmpaddr0, t0            # pmp0: NAPOT over the whole address space,
        li      t0, 0x1c
        csrw    pmpcfg0, t0             #   X only -> in U-mode fetch is allowed, loads/stores fault
        la      s2, target
        li      t4, 4
        li      t0, MSTATUS_MPP
        csrc    mstatus, t0             # MPP = U
        la      t0, umode
        csrw    mepc, t0
        mret
umode:
        vsetivli t0, 4, e32, m1, tu, mu
        vlse32.v v3, (s2), t4           # load access fault (5); mtval must be s2
        ...                             # mtvec_handler records mcause/mtval, re-opens PMP, mepc += 4, mret
        bne     s6, s2, fail            # s6 = recorded mtval  <-- XiangShan takes this branch
        RVTEST_PASS
