Previously, t0_hitT1Update only checked t1_valid && t0_tag === t1_tag. However, in reality, t0_hitT1Update needs to include (t1_hit || t1_allocate) to avoid false hits in the t0 stage.
