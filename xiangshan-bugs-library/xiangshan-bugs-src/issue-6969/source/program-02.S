# s0 = VA_P (0x40000000)   s1 = VA_Q (0x40200000)   s2 = &W (PA 0x100010020)
# s3 = k   s4 = 640   s5 = stale hits   s8 = MPRV (1 << 17)
# MBMC_CLR = 0x100000006: BMA = TestBMA, BME = 1, CMODE = 0, plus BCLEAR
loop:
    # 1. P is a non-secure page; clear the bitmap cache and the translations
    sd    zero, 0(s2)
    fence rw, rw
    li    t0, MBMC_CLR
    csrw  0xbc2, t0                 # mbmc
    sfence.vma
    # 2. ld P through MPRV (MPP = S): the bitmap check misses and caches W (P allowed) in way 0
    li    t0, 3 << 11
    csrc  mstatus, t0
    li    t0, (1 << 11) | MPRV
    csrs  mstatus, t0
    ld    t1, 0(s0)
    csrc  mstatus, s8
    li    t2, 0x1111
    bne   t1, t2, end_setup
    # 3. revoke P in the documented refresh order
    li    t0, 1
    sd    t0, 0(s2)                 # W = 1: P becomes a secure page
    fence rw, rw
    sfence.vma x0, x0
    hfence.vvma x0, x0
    hfence.gvma x0, x0
    li    t0, MBMC_CLR
    csrw  0xbc2, t0                 # BCLEAR
    # 4. after the revocation: the walk for Q reads W; one more sfence.vma after k addi
    csrs  mstatus, s8
    prefetch.r 0(s1)                # retires on the TLB miss, the walk continues
    mv    t3, s3
    beqz  t3, 3f
2:  addi  t3, t3, -1
    bnez  t3, 2b
3:  sfence.vma                      # u1: li t4, VA_U; sfence.vma t4, x0
    # 5. ld P must raise a load access fault (the handler sets s7 and skips the load)
    li    s7, 0
check_ld:
    ld    t1, 0(s0)
    csrc  mstatus, s8
    bnez  s7, next
    addi  s5, s5, 1                 # stale hit: the secure page was read (d0 exits, d1 prints H<k>)
    ...
next:
    addi  s3, s3, 1
    bltu  s3, s4, loop

mtrap:                              # accepts only a load access fault on check_ld
    csrr  t0, mcause
    li    t1, 5
    bne   t0, t1, end_other
    csrr  t0, mepc
    lla   t1, check_ld
    bne   t0, t1, end_other
    li    s7, 1
    addi  t0, t0, 4
    csrw  mepc, t0
    mret
