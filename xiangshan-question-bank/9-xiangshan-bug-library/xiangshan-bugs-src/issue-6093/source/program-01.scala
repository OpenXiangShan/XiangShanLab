val B_round_overflow_reg   = B_guard_normal_reg
val B_sticky_overflow_reg  = B_round_normal_reg | B_sticky_normal_reg
val B_rsticky_overflow_reg = B_round_overflow_reg | B_sticky_overflow_reg
