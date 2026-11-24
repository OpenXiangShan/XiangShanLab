// cache/dcache/mainpipe/MissQueue.scala:1631-1632
val is_first_in_group = target_group === i.U
val compress_ready = has_compress && Mux(is_first_in_group, has_merge || has_alloc, (analysis.strategy(target_group) & 3.U) =/= 0.U)
