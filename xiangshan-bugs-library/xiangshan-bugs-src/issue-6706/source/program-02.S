    # IntrGenerator register file: 0x40070000 + 4*k, k = 0..7
    li      s0, 0x40070000

    li      t1, 0x1
    sw      t1, 0x00(s0)            # intrGenRegs[0] = 0x1
    li      t1, 0x2
    sw      t1, 0x04(s0)            # intrGenRegs[1] = 0x2
    li      t1, 0x12345678
    sw      t1, 0x10(s0)            # intrGenRegs[4] = 0x12345678

    # register writes reach the register file after 1000 cycles (DelayN)
    li      t2, 2000
1:  addi    t2, t2, -1
    bnez    t2, 1b

    lw      s1, 0x00(s0)            # a) word 0
    lw      s2, 0x04(s0)            # b) word 1
    lw      s3, 0x10(s0)            # c) word 4
    ld      s4, 0x00(s0)            # d) 64-bit beat at word 0
    # print s1..s4 over UART

    # second round: rewrite only word 0; word 4 stays 0x12345678
    li      t1, 0xaaaa5555
    sw      t1, 0x00(s0)
    li      t2, 2000
2:  addi    t2, t2, -1
    bnez    t2, 2b
    lwu     s5, 0x10(s0)            # e) word 4
    lwu     s8, 0x04(s0)            # f) word 1
    # print s5, s8 over UART
