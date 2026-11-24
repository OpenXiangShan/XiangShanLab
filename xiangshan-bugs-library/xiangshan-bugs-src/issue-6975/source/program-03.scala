// cache/dcache/mainpipe/MissQueue.scala:1795-1799, 1805-1810
actual_target_mshr_for_group(i) := Mux(group_leader === i.U, analysis.target_mshr(i), analysis.target_mshr(group_leader))
io.resp(i).id := Mux(can_merge_from_pipe(i), can_merge_from_pipe_mshr(i), actual_target_mshr_for_group(i))
io.resp(i).handled := (Cat(req_mshr_handled_vec).orR || can_merge_from_pipe(i)) && query_fire(i)
