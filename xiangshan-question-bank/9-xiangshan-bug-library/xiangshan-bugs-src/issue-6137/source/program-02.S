csrw    0x5c0, zero       # disable branch prediction controls, including ABTB
la      s0, secret_data
la      t4, flag_zero
ld      t3, 0(t4)         # real branch condition becomes zero

branch_site:
    bnez    t3, gadget    # architecturally not taken in the attack iteration
