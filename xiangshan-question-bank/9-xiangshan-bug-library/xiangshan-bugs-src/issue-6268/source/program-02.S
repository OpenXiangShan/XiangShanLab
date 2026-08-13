80001c88:  lr.d.aqrl  a7,(t6)         ; sets reservation at (t6)

80001cb0:  sc.d       a0,s0,(t6)      ; first SC. Clears reservation per spec.

80001dcc:  li         s11,62           ; loop count = 62
80001dd0:  <bwd_0>:
     ...                                ; ~50 instructions, no LR
80001e6c:  sc.w.rl    s2,s1,(t6)      ; SC inside loop. No valid reservation.
80001e74:  sc.w.aq    s4,ra,(t6)      ; SC inside loop. No valid reservation.
     ...
80001e90:  bnez       s11,80001dd0    ; 62 iterations × 2 SCs = 124 SCs
