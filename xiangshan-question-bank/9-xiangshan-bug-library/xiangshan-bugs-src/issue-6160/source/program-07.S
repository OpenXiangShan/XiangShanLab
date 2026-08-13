   warm_select:
       andi    t0, s1, 1
       beqz    t0, use_target_a
       mv      s0, s3
       j       indirect_site
   use_target_a:
       mv      s0, s2
   indirect_site:
       jalr    zero, 0(s0)
